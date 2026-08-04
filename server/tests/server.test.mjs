import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { after, before, describe, it } from "node:test";
import { fileURLToPath } from "node:url";

const serverScript = join(dirname(fileURLToPath(import.meta.url)), "..", "app_good_words_server.mjs");
const apiKey = "test-api-key";

let child;
let workDir;
let baseUrl;

/** 서버가 요청을 받을 준비가 될 때까지 /api/health를 짧게 재시도한다. */
async function waitForServer(url, timeoutMs = 15_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${url}/api/health`);
      if (response.ok) return;
    } catch {
      // 아직 리스닝 전이면 다시 시도한다.
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("서버가 시간 안에 시작되지 않았습니다.");
}

function api(path, { method = "GET", body, key = apiKey } = {}) {
  const headers = {};
  if (key) headers["X-API-Key"] = key;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  return fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

function emptySnapshot(overrides = {}) {
  return {
    schemaVersion: 8,
    // 앱은 항상 값을 보낸다. 여기서 빠뜨리면 서버가 현재 시각으로 채워
    // 이후 병합의 작은 타임스탬프가 절대 이기지 못한다.
    settingsUpdatedAt: 0,
    items: [],
    exposureEvents: [],
    routines: [],
    routineChecks: [],
    routineMemos: [],
    deletions: [],
    ...overrides,
  };
}

before(async () => {
  workDir = await mkdtemp(join(tmpdir(), "appgoodwords-test-"));
  // 포트 0으로 띄우면 실제 포트를 알 수 없으므로 충돌 가능성이 낮은 고정 포트를 쓴다.
  const port = 8700 + (process.pid % 90);
  baseUrl = `http://127.0.0.1:${port}`;
  child = spawn(
    process.execPath,
    [serverScript, "--host", "127.0.0.1", "--port", String(port), "--db", join(workDir, "test.db.json"), "--api-key", apiKey],
    { stdio: ["ignore", "pipe", "pipe"] },
  );
  child.stderr.on("data", (chunk) => console.error(`[server] ${chunk}`));
  await waitForServer(baseUrl);
});

after(async () => {
  if (child) child.kill();
  if (workDir) await rm(workDir, { recursive: true, force: true });
});

describe("인증", () => {
  it("health는 API 키 없이도 열려 있다", async () => {
    const response = await fetch(`${baseUrl}/api/health`);
    const payload = await response.json();

    assert.equal(response.status, 200);
    assert.equal(payload.ok, true);
    assert.equal(typeof payload.schemaVersion, "number");
  });

  it("키가 없으면 snapshot은 401을 준다", async () => {
    const response = await api("/api/snapshot", { key: "" });

    assert.equal(response.status, 401);
  });

  it("키가 틀리면 snapshot은 401을 준다", async () => {
    const response = await api("/api/snapshot", { key: "wrong-key" });

    assert.equal(response.status, 401);
  });

  it("Bearer 토큰으로도 인증된다", async () => {
    const response = await fetch(`${baseUrl}/api/snapshot`, {
      headers: { Authorization: `Bearer ${apiKey}` },
    });

    assert.equal(response.status, 200);
  });
});

describe("스냅샷 동기화", () => {
  it("PUT은 서버 데이터를 통째로 교체한다", async () => {
    const uploaded = emptySnapshot({
      items: [
        {
          id: 1,
          type: "QUOTE",
          title: "교체된 항목",
          body: "업로드한 본문",
          category: "테스트",
          tags: ["태그"],
          createdAt: 1_700_000_000_000,
          showCount: 2,
          isFavorite: true,
        },
      ],
      routines: [{ id: 5, title: "교체된 루틴", note: "", category: "", reminderEnabled: true, createdAt: 1_700_000_000_000 }],
    });

    const putResponse = await api("/api/snapshot", { method: "PUT", body: uploaded });
    assert.equal(putResponse.status, 200);

    const stored = await (await api("/api/snapshot")).json();
    assert.equal(stored.items.length, 1);
    assert.equal(stored.items[0].title, "교체된 항목");
    assert.equal(stored.items[0].isFavorite, true);
    assert.deepEqual(stored.items[0].tags, ["태그"]);
    assert.equal(stored.routines.length, 1);
    assert.equal(stored.routines[0].title, "교체된 루틴");
  });

  it("빈 스냅샷을 올리면 서버가 비워진다", async () => {
    await api("/api/snapshot", {
      method: "PUT",
      body: emptySnapshot({ items: [{ id: 1, type: "QUOTE", title: "지워질 항목", body: "본문" }] }),
    });

    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });

    const stored = await (await api("/api/snapshot")).json();
    assert.equal(stored.items.length, 0);
    assert.equal(stored.routines.length, 0);
  });

  it("스냅샷 응답은 앱이 읽는 키를 모두 포함한다", async () => {
    const stored = await (await api("/api/snapshot")).json();

    for (const key of ["schemaVersion", "settings", "items", "exposureEvents", "routines", "routineChecks", "routineMemos"]) {
      assert.ok(key in stored, `${key}가 응답에 없습니다.`);
    }
  });
});

describe("병합 동기화", () => {
  function item(overrides) {
    return {
      id: 1,
      syncId: "item-1",
      updatedAt: 1000,
      type: "QUOTE",
      title: "제목",
      body: "본문",
      createdAt: 1000,
      ...overrides,
    };
  }

  async function resetServer() {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot({ deletions: [] }) });
  }

  it("양쪽에만 있는 레코드를 모두 남긴다", async () => {
    await resetServer();
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [item({ syncId: "a", title: "서버쪽" })] }) });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({ items: [item({ syncId: "b", title: "기기쪽" })] }),
      })
    ).json();

    assert.deepEqual(new Set(merged.items.map((entry) => entry.syncId)), new Set(["a", "b"]));
  });

  it("같은 레코드는 updatedAt이 최신인 쪽이 남는다", async () => {
    await resetServer();
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [item({ title: "예전", updatedAt: 1000 })] }) });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({ items: [item({ title: "최신", updatedAt: 5000 })] }),
      })
    ).json();

    assert.equal(merged.items.length, 1);
    assert.equal(merged.items[0].title, "최신");
  });

  it("더 오래된 수정은 최신 레코드를 덮지 않는다", async () => {
    await resetServer();
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [item({ title: "최신", updatedAt: 5000 })] }) });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({ items: [item({ title: "예전", updatedAt: 1000 })] }),
      })
    ).json();

    assert.equal(merged.items[0].title, "최신");
  });

  it("삭제 표식이 있으면 서버에 남아 있어도 지워진다", async () => {
    await resetServer();
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [item({ syncId: "a", updatedAt: 1000 })] }) });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          deletions: [{ syncId: "a", entityType: "CONTENT_ITEM", deletedAt: 4000 }],
        }),
      })
    ).json();

    assert.equal(merged.items.length, 0);
    assert.equal(merged.deletions.length, 1);
  });

  it("지운 뒤의 수정은 삭제를 이긴다", async () => {
    await resetServer();
    await api("/api/sync", {
      method: "POST",
      body: emptySnapshot({ deletions: [{ syncId: "a", entityType: "CONTENT_ITEM", deletedAt: 1000 }] }),
    });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({ items: [item({ syncId: "a", title: "되살림", updatedAt: 9000 })] }),
      })
    ).json();

    assert.equal(merged.items.length, 1);
    assert.equal(merged.items[0].title, "되살림");
  });

  it("이벤트는 합집합으로 쌓이고 중복되지 않는다", async () => {
    await resetServer();
    const event = (id) => ({
      syncId: id,
      contentItemId: 1,
      contentTitle: "제목",
      contentType: "QUOTE",
      eventType: "CONFIRMED",
      trigger: "MANUAL_REFRESH",
      occurredAt: 1000,
    });

    await api("/api/sync", { method: "POST", body: emptySnapshot({ exposureEvents: [event("e1"), event("e2")] }) });
    const merged = await (
      await api("/api/sync", { method: "POST", body: emptySnapshot({ exposureEvents: [event("e2"), event("e3")] }) })
    ).json();

    assert.deepEqual(new Set(merged.exposureEvents.map((entry) => entry.syncId)), new Set(["e1", "e2", "e3"]));
  });

  it("syncId 없이 온 구버전 레코드도 받아들인다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({ items: [{ id: 7, type: "QUOTE", title: "구버전", body: "본문", createdAt: 1000 }] }),
      })
    ).json();

    assert.equal(merged.items.length, 1);
    assert.ok(merged.items[0].syncId, "서버가 syncId를 채워야 합니다.");
    assert.equal(merged.items[0].updatedAt, 1000);
  });

  it("동시에 들어온 병합 요청이 서로를 덮어쓰지 않는다", async () => {
    await resetServer();

    await Promise.all(
      ["p1", "p2", "p3", "p4", "p5"].map((id) =>
        api("/api/sync", { method: "POST", body: emptySnapshot({ items: [item({ syncId: id, title: id })] }) }),
      ),
    );

    const stored = await (await api("/api/snapshot")).json();
    assert.deepEqual(
      new Set(stored.items.map((entry) => entry.syncId)),
      new Set(["p1", "p2", "p3", "p4", "p5"]),
    );
  });

  it("설정은 최근에 손댄 쪽을 따른다", async () => {
    await resetServer();
    await api("/api/sync", {
      method: "POST",
      body: emptySnapshot({ settings: { intervalMinutes: 60 }, settingsUpdatedAt: 1000 }),
    });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({ settings: { intervalMinutes: 240 }, settingsUpdatedAt: 9000 }),
      })
    ).json();

    assert.equal(merged.settings.intervalMinutes, 240);
  });
});

describe("콘텐츠 API", () => {
  it("POST로 만든 항목을 GET으로 다시 읽고 DELETE로 지운다", async () => {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });

    const created = await (
      await api("/api/content", {
        method: "POST",
        body: { type: "QUOTE", title: "새 항목", body: "본문", category: "테스트" },
      })
    ).json();
    assert.ok(created.id > 0);

    const listed = await (await api("/api/content")).json();
    assert.equal(listed.items.length, 1);
    assert.equal(listed.items[0].title, "새 항목");

    const deleted = await api(`/api/content/${created.id}`, { method: "DELETE" });
    assert.equal(deleted.status, 200);

    const afterDelete = await (await api("/api/content")).json();
    assert.equal(afterDelete.items.length, 0);
  });
});

describe("라우팅", () => {
  it("없는 엔드포인트는 404를 준다", async () => {
    const response = await api("/api/nope");

    assert.equal(response.status, 404);
  });
});
