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
    diaries: [],
    todos: [],
    books: [],
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

  it("두 기기가 같은 숫자 id를 써도 서로 덮어쓰지 않는다", async () => {
    // 숫자 id는 기기마다 따로 증가하므로 A기기 id=1과 B기기 id=1은 다른 항목이다.
    await resetServer();
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [item({ id: 1, syncId: "a", title: "A기기" })] }) });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({ items: [item({ id: 1, syncId: "b", title: "B기기" })] }),
      })
    ).json();

    assert.equal(merged.items.length, 2);
    assert.equal(new Set(merged.items.map((entry) => entry.id)).size, 2, "id가 겹치면 안 된다");
  });

  it("id를 다시 매겨도 이벤트는 원래 항목을 가리킨다", async () => {
    await resetServer();
    await api("/api/sync", {
      method: "POST",
      body: emptySnapshot({
        items: [item({ id: 1, syncId: "a", title: "A기기" })],
        exposureEvents: [
          {
            id: 1,
            syncId: "event-a",
            contentItemId: 1,
            contentItemSyncId: "a",
            contentTitle: "A기기",
            contentType: "QUOTE",
            eventType: "SURFACED",
            trigger: "MANUAL_REFRESH",
            occurredAt: 1000,
          },
        ],
      }),
    });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          items: [item({ id: 1, syncId: "b", title: "B기기" })],
          exposureEvents: [
            {
              id: 1,
              syncId: "event-b",
              contentItemId: 1,
              contentItemSyncId: "b",
              contentTitle: "B기기",
              contentType: "QUOTE",
              eventType: "SURFACED",
              trigger: "MANUAL_REFRESH",
              occurredAt: 2000,
            },
          ],
        }),
      })
    ).json();

    const itemIdBySyncId = new Map(merged.items.map((entry) => [entry.syncId, entry.id]));
    for (const event of merged.exposureEvents) {
      assert.equal(event.contentItemId, itemIdBySyncId.get(event.contentItemSyncId), "이벤트가 다른 항목에 붙었다");
    }
  });

  it("구버전 레코드의 항목 참조를 잃지 않는다", async () => {
    // 9 이전 앱은 contentItemSyncId 없이 숫자 id로만 항목을 가리킨다.
    await resetServer();
    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          items: [item({ id: 5, syncId: "a", title: "구버전 항목" })],
          exposureEvents: [
            {
              id: 1,
              syncId: "event-old",
              contentItemId: 5,
              contentTitle: "구버전 항목",
              contentType: "QUOTE",
              eventType: "SURFACED",
              trigger: "MANUAL_REFRESH",
              occurredAt: 1000,
            },
          ],
        }),
      })
    ).json();

    assert.equal(merged.exposureEvents[0].contentItemSyncId, "a", "참조를 syncId로 옮겨 둬야 한다");
    assert.equal(merged.exposureEvents[0].contentItemId, merged.items[0].id);
  });

  it("루틴을 잃은 메모는 남기지 않는다", async () => {
    await resetServer();
    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          routineMemos: [
            {
              id: 1,
              syncId: "memo-1",
              updatedAt: 1000,
              routineId: 9,
              routineSyncId: "사라진-루틴",
              routineTitle: "루틴",
              body: "메모",
              createdAt: 1000,
            },
          ],
        }),
      })
    ).json();

    assert.equal(merged.routineMemos.length, 0);
  });

  it("오래된 삭제 표식은 정리하고 최근 것은 남긴다", async () => {
    // 표식을 그냥 두면 끝없이 쌓이고, 너무 일찍 지우면 지운 항목이 되살아난다.
    await resetServer();
    const day = 24 * 60 * 60 * 1000;
    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          deletions: [
            { syncId: "오래됨", entityType: "CONTENT_ITEM", deletedAt: Date.now() - 91 * day },
            { syncId: "최근", entityType: "CONTENT_ITEM", deletedAt: Date.now() - day },
          ],
        }),
      })
    ).json();

    assert.deepEqual(merged.deletions.map((entry) => entry.syncId), ["최근"]);
  });

  it("정리된 표식은 항목을 더 이상 막지 않는다", async () => {
    await resetServer();
    const day = 24 * 60 * 60 * 1000;
    await api("/api/sync", {
      method: "POST",
      body: emptySnapshot({
        deletions: [{ syncId: "a", entityType: "CONTENT_ITEM", deletedAt: Date.now() - 91 * day }],
      }),
    });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({ items: [item({ syncId: "a", title: "다시 만든 항목", updatedAt: 1000 })] }),
      })
    ).json();

    assert.equal(merged.items.length, 1);
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
          // 정리 기간 안이어야 표식이 남는다. 1970년대 값을 쓰면 오래된 표식으로 걸러진다.
          deletions: [{ syncId: "a", entityType: "CONTENT_ITEM", deletedAt: Date.now() }],
        }),
      })
    ).json();

    assert.equal(merged.items.length, 0);
    assert.equal(merged.deletions.length, 1);
  });

  it("지운 뒤의 수정은 삭제를 이긴다", async () => {
    await resetServer();
    // 표식이 정리 기간 안에 살아 있어야 "수정이 이긴다"를 실제로 확인할 수 있다.
    const deletedAt = Date.now() - 60_000;
    await api("/api/sync", {
      method: "POST",
      body: emptySnapshot({ deletions: [{ syncId: "a", entityType: "CONTENT_ITEM", deletedAt }] }),
    });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({ items: [item({ syncId: "a", title: "되살림", updatedAt: deletedAt + 1000 })] }),
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

describe("일기와 할 일 API", () => {
  async function resetServer() {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
  }

  it("웹에서 만든 일기를 다시 읽고 고치고 지운다", async () => {
    await resetServer();

    const created = await (
      await api("/api/diaries", {
        method: "POST",
        body: { entryDate: "2026-08-17", title: "웹에서 쓴 일기", body: "본문", weather: "RAIN", mood: "GOOD" },
      })
    ).json();
    assert.ok(created.id > 0);
    assert.equal(created.weather, "RAIN");
    assert.equal(created.mood, "GOOD");
    assert.ok(created.syncId, "기기와 짝지을 syncId가 없습니다.");

    const listed = await (await api("/api/diaries")).json();
    assert.equal(listed.diaries.length, 1);

    const updated = await (
      await api(`/api/diaries/${created.id}`, { method: "PUT", body: { mood: "SAD" } })
    ).json();
    // 고친 항목만 바뀌고 나머지는 남아야 한다.
    assert.equal(updated.mood, "SAD");
    assert.equal(updated.body, "본문");
    assert.equal(updated.syncId, created.syncId, "고칠 때 syncId가 바뀌면 다른 일기가 됩니다.");

    await api(`/api/diaries/${created.id}`, { method: "DELETE" });
    const afterDelete = await (await api("/api/diaries")).json();
    assert.equal(afterDelete.diaries.length, 0);
  });

  it("웹에서 지운 일기는 삭제 표식을 남겨 되살아나지 않는다", async () => {
    await resetServer();
    const created = await (
      await api("/api/diaries", { method: "POST", body: { entryDate: "2026-08-17", body: "지울 일기" } })
    ).json();
    await api(`/api/diaries/${created.id}`, { method: "DELETE" });

    // 아직 그 일기를 들고 있는 기기가 병합을 걸어 온 상황.
    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [
            { syncId: created.syncId, updatedAt: 1000, entryDate: "2026-08-17", body: "지울 일기", createdAt: 1000 },
          ],
        }),
      })
    ).json();

    assert.equal(merged.diaries.length, 0, "웹에서 지운 일기가 다음 병합에 되살아났습니다.");
  });

  it("웹에서 고친 내용이 기기의 옛 사본에 밀리지 않는다", async () => {
    await resetServer();
    const created = await (
      await api("/api/diaries", { method: "POST", body: { entryDate: "2026-08-17", body: "웹에서 고친 본문" } })
    ).json();

    // 기기에는 같은 일기의 예전 사본이 남아 있다.
    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [
            { syncId: created.syncId, updatedAt: 1000, entryDate: "2026-08-17", body: "기기의 옛 본문", createdAt: 1000 },
          ],
        }),
      })
    ).json();

    assert.equal(merged.diaries[0].body, "웹에서 고친 본문");
  });

  it("웹에서 만든 할 일을 완료하고 되돌린다", async () => {
    await resetServer();

    const created = await (
      await api("/api/todos", { method: "POST", body: { title: "우체국 가기", dueDate: "2026-08-17" } })
    ).json();
    assert.equal(created.doneAt, null);

    const done = await (await api(`/api/todos/${created.id}/toggle-done`, { method: "POST" })).json();
    assert.ok(done.doneAt > 0);

    const undone = await (await api(`/api/todos/${created.id}/toggle-done`, { method: "POST" })).json();
    // 0으로 돌아오면 1970년에 끝낸 일이 된다.
    assert.equal(undone.doneAt, null);
  });

  it("이름이나 마감이 없는 할 일은 무엇이 빠졌는지 알려 준다", async () => {
    await resetServer();

    const noTitle = await api("/api/todos", { method: "POST", body: { dueDate: "2026-08-17" } });
    assert.equal(noTitle.status, 400);
    assert.match((await noTitle.json()).error, /이름/);

    const noDueDate = await api("/api/todos", { method: "POST", body: { title: "마감 없음" } });
    assert.equal(noDueDate.status, 400);
    assert.match((await noDueDate.json()).error, /날짜/);
  });

  it("아무것도 안 적은 일기는 저장하지 않는다", async () => {
    await resetServer();

    const response = await api("/api/diaries", { method: "POST", body: { entryDate: "2026-08-17" } });

    assert.equal(response.status, 400);
  });

  it("날씨나 기분만 골라도 일기가 저장된다", async () => {
    await resetServer();

    // 글 쓸 기운은 없어도 기분만 남기고 싶은 날이 있다. 앱과 같은 기준이어야 한다.
    const created = await (
      await api("/api/diaries", { method: "POST", body: { entryDate: "2026-08-17", mood: "TIRED" } })
    ).json();

    assert.equal(created.mood, "TIRED");
  });

  it("할 일 목록은 마감이 빠른 것부터 준다", async () => {
    await resetServer();
    await api("/api/todos", { method: "POST", body: { title: "나중", dueDate: "2026-08-20" } });
    await api("/api/todos", { method: "POST", body: { title: "먼저", dueDate: "2026-08-18" } });

    const listed = await (await api("/api/todos")).json();

    assert.deepEqual(
      listed.todos.map((todo) => todo.title),
      ["먼저", "나중"],
    );
  });
});

describe("바뀐 것만 주고받기", () => {
  async function resetServer() {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
  }

  function quote(syncId, title, updatedAt = 1000) {
    return { syncId, updatedAt, type: "QUOTE", title, body: `${title} 본문`, createdAt: updatedAt };
  }

  it("처음 붙는 기기는 전체를 받는다", async () => {
    await resetServer();
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("a", "첫 글귀")] }) });

    const full = await (await api("/api/sync", { method: "POST", body: emptySnapshot() })).json();

    assert.equal(full.partial, false);
    assert.equal(full.items.length, 1);
    assert.ok(full.rev > 0, "리비전 번호가 없습니다.");
  });

  it("본 번호 뒤에 바뀐 것만 돌려준다", async () => {
    await resetServer();
    const first = await (
      await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("a", "예전 글귀")] }) })
    ).json();

    const delta = await (
      await api(`/api/sync?since=${first.rev}`, {
        method: "POST",
        body: emptySnapshot({ items: [quote("b", "새 글귀", 2000)] }),
      })
    ).json();

    assert.equal(delta.partial, true);
    // 예전 글귀는 그대로라 다시 보낼 이유가 없다.
    assert.deepEqual(delta.items.map((item) => item.syncId), ["b"]);
    // 전체 개수는 알려 준다. 기기가 얼마나 맞았는지 확인할 수 있어야 한다.
    assert.equal(delta.itemCount, 2);
  });

  it("아무것도 안 바뀌었으면 빈 목록을 준다", async () => {
    await resetServer();
    const first = await (
      await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("a", "글귀")] }) })
    ).json();

    const delta = await (
      await api(`/api/sync?since=${first.rev}`, { method: "POST", body: emptySnapshot() })
    ).json();

    assert.equal(delta.items.length, 0);
    assert.equal(delta.exposureEvents.length, 0);
    assert.equal(delta.rev, first.rev, "바뀐 게 없는데 번호가 올랐습니다.");
  });

  it("안 바뀐 레코드는 번호를 그대로 지킨다", async () => {
    await resetServer();
    const first = await (
      await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("a", "그대로 둘 글귀")] }) })
    ).json();
    const before = first.items.find((item) => item.syncId === "a").rev;

    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("b", "새 글귀", 2000)] }) });

    const stored = await (await api("/api/snapshot")).json();
    const after = stored.items.find((item) => item.syncId === "a").rev;
    // 여기서 번호가 오르면 안 바뀐 레코드까지 매번 다시 보내게 된다.
    assert.equal(after, before);
  });

  it("한 건이 늘어도 앞선 레코드의 숫자 id가 밀리지 않는다", async () => {
    await resetServer();
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("a", "먼저 담은 글귀")] }) });
    const first = await (await api("/api/snapshot")).json();
    const idBefore = first.items.find((item) => item.syncId === "a").id;

    // 목록 정렬상 새 글귀가 앞에 오는 상황. 예전에는 이때 뒤쪽 번호가 전부 밀렸다.
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("b", "나중 글귀", 9000)] }) });

    const stored = await (await api("/api/snapshot")).json();
    assert.equal(stored.items.find((item) => item.syncId === "a").id, idBefore);
  });

  it("이력이 쌓여도 새로 생긴 것만 보낸다", async () => {
    await resetServer();
    const seeded = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          items: [quote("a", "글귀")],
          exposureEvents: Array.from({ length: 50 }, (_, index) => ({
            syncId: `event-${index}`,
            contentItemSyncId: "a",
            contentTitle: "글귀",
            contentType: "QUOTE",
            eventType: "SHOWN",
            trigger: "APP_LAUNCH",
            occurredAt: 1000 + index,
          })),
        }),
      })
    ).json();
    assert.equal(seeded.exposureEvents.length, 50);

    const delta = await (
      await api(`/api/sync?since=${seeded.rev}`, {
        method: "POST",
        body: emptySnapshot({
          exposureEvents: [
            {
              syncId: "event-new",
              contentItemSyncId: "a",
              contentTitle: "글귀",
              contentType: "QUOTE",
              eventType: "SHOWN",
              trigger: "APP_LAUNCH",
              occurredAt: 9999,
            },
          ],
        }),
      })
    ).json();

    // 이력이 수천 건 쌓여도 매번 전부 실어 보내면 안 된다.
    assert.deepEqual(delta.exposureEvents.map((event) => event.syncId), ["event-new"]);
    assert.equal(delta.eventCount, 51);
  });

  it("지운 것은 삭제 표식으로 따라온다", async () => {
    await resetServer();
    const first = await (
      await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("a", "지울 글귀")] }) })
    ).json();
    const created = (await (await api("/api/snapshot")).json()).items.find((item) => item.syncId === "a");

    await api(`/api/content/${created.id}`, { method: "DELETE" });

    const delta = await (
      await api(`/api/sync?since=${first.rev}`, { method: "POST", body: emptySnapshot() })
    ).json();

    // 부분 응답에는 남은 것만 오므로, 사라졌다는 사실은 표식으로 들어야 한다.
    assert.ok(delta.deletions.some((entry) => entry.syncId === "a"), "삭제 표식이 오지 않았습니다.");
  });

  it("합쳐서 사라진 쪽도 삭제 표식을 남긴다", async () => {
    await resetServer();
    const first = await (
      await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("a", "같은 글귀")] }) })
    ).json();

    // 다른 기기가 같은 내용을 다른 syncId로 보내 오면 하나로 합쳐진다.
    const delta = await (
      await api(`/api/sync?since=${first.rev}`, {
        method: "POST",
        body: emptySnapshot({ items: [quote("b", "같은 글귀", 2000)] }),
      })
    ).json();

    assert.equal(delta.itemCount, 1);
    // 기기에는 아직 a가 있다. 표식이 없으면 영영 남는다.
    assert.ok(delta.deletions.some((entry) => entry.syncId === "a"), "합쳐서 사라진 쪽의 표식이 없습니다.");
  });

  it("서버가 통째로 교체되면 전체를 다시 보낸다", async () => {
    await resetServer();
    const first = await (
      await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("a", "글귀")] }) })
    ).json();

    // 업로드는 서버를 한 기기 데이터로 통째로 바꾼다. 그 뒤 번호는 다른 세대의 것이라 뜻이 없다.
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot({ items: [quote("b", "새 기준 글귀")] }) });

    const delta = await (
      await api(`/api/sync?since=${first.rev}&epoch=${first.epoch}`, { method: "POST", body: emptySnapshot() })
    ).json();

    assert.equal(delta.partial, false, "세대가 바뀌었는데 부분만 보냈습니다.");
    assert.deepEqual(delta.items.map((item) => item.syncId), ["b"]);
  });

  it("같은 세대면 계속 부분만 보낸다", async () => {
    await resetServer();
    const first = await (
      await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("a", "글귀")] }) })
    ).json();

    const delta = await (
      await api(`/api/sync?since=${first.rev}&epoch=${first.epoch}`, { method: "POST", body: emptySnapshot() })
    ).json();

    assert.equal(delta.partial, true);
    assert.equal(delta.items.length, 0);
  });

  it("서버 번호보다 앞선 번호를 받으면 전체를 보낸다", async () => {
    await resetServer();
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("a", "글귀")] }) });

    // 서버 DB를 새로 깔면 번호가 0부터 다시 시작한다. 그때 기기가 들고 있던 큰 번호가 이 경우다.
    const delta = await (
      await api("/api/sync?since=999999", { method: "POST", body: emptySnapshot() })
    ).json();

    assert.equal(delta.partial, false);
    assert.equal(delta.items.length, 1);
  });

  it("스냅샷 내려받기도 since를 받는다", async () => {
    await resetServer();
    const first = await (
      await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("a", "글귀")] }) })
    ).json();

    const delta = await (await api(`/api/snapshot?since=${first.rev}`)).json();

    assert.equal(delta.partial, true);
    assert.equal(delta.items.length, 0);
  });
});

describe("독서", () => {
  async function resetServer() {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
  }

  async function addBook(body) {
    return (await (await api("/api/books", { method: "POST", body })).json());
  }

  it("책을 담고 진도를 고친다", async () => {
    await resetServer();

    const created = await addBook({ title: "아주 작은 습관의 힘", author: "제임스 클리어", totalPages: 320 });
    assert.ok(created.id > 0);
    assert.ok(created.syncId, "기기와 짝지을 syncId가 없습니다.");
    assert.equal(created.status, "READING");
    assert.equal(created.currentPage, 0);

    const updated = await (
      await api(`/api/books/${created.id}`, { method: "PUT", body: { currentPage: 120 } })
    ).json();

    assert.equal(updated.currentPage, 120);
    assert.equal(updated.title, "아주 작은 습관의 힘", "고칠 때 다른 값이 지워졌습니다.");
    assert.equal(updated.syncId, created.syncId, "고칠 때 syncId가 바뀌면 다른 책이 됩니다.");
  });

  it("전체 쪽수를 넘는 진도는 마지막 쪽으로 맞춘다", async () => {
    await resetServer();
    const created = await addBook({ title: "쪽수 있는 책", totalPages: 100 });

    const updated = await (
      await api(`/api/books/${created.id}`, { method: "PUT", body: { currentPage: 999 } })
    ).json();

    // 넘겨 두면 화면이 100%를 넘겨 그린다.
    assert.equal(updated.currentPage, 100);
  });

  it("다 읽음으로 바꾸면 완독 시각이 남는다", async () => {
    await resetServer();
    const created = await addBook({ title: "다 읽을 책", totalPages: 200, currentPage: 50 });

    const finished = await (
      await api(`/api/books/${created.id}`, { method: "PUT", body: { status: "FINISHED" } })
    ).json();
    assert.ok(finished.finishedAt > 0);
    assert.equal(finished.currentPage, 200, "다 읽었는데 진도가 중간에 멈춰 있습니다.");

    const reopened = await (
      await api(`/api/books/${created.id}`, { method: "PUT", body: { status: "READING" } })
    ).json();
    // 다시 읽는 중인데 완독 시각이 남아 있으면 목록에서 다 읽은 책으로 샙니다.
    assert.equal(reopened.finishedAt, null);
  });

  it("제목 없는 책은 받지 않는다", async () => {
    await resetServer();

    const response = await api("/api/books", { method: "POST", body: { author: "저자만" } });

    assert.equal(response.status, 400);
  });

  it("책에서 뽑은 글귀가 보관함에 들어가고 출처가 남는다", async () => {
    await resetServer();
    const book = await addBook({ title: "출처 있는 책", author: "지은이", totalPages: 300, currentPage: 10 });

    const quote = await (
      await api(`/api/books/${book.id}/quotes`, {
        method: "POST",
        body: { body: "행동이 먼저다.", page: 42 },
      })
    ).json();

    assert.equal(quote.type, "QUOTE");
    assert.equal(quote.body, "행동이 먼저다.");
    // 저자와 제목을 사용자가 다시 적지 않아도 되게 책에서 채운다.
    assert.equal(quote.author, "지은이");
    assert.equal(quote.title, "출처 있는 책");
    assert.equal(quote.bookSyncId, book.syncId);
    assert.equal(quote.bookPage, 42);

    // 뽑았다는 것은 거기까지 읽었다는 뜻이다.
    const after = await (await api(`/api/books/${book.id}`)).json();
    assert.equal(after.currentPage, 42);
  });

  it("지금 진도보다 앞쪽 글귀를 뽑아도 진도가 뒤로 가지 않는다", async () => {
    await resetServer();
    const book = await addBook({ title: "되돌아가지 않는 책", totalPages: 300, currentPage: 200 });

    await api(`/api/books/${book.id}/quotes`, { method: "POST", body: { body: "앞쪽 문장", page: 30 } });

    const after = await (await api(`/api/books/${book.id}`)).json();
    assert.equal(after.currentPage, 200);
  });

  it("빈 글귀는 뽑지 않는다", async () => {
    await resetServer();
    const book = await addBook({ title: "빈 글귀 책" });

    const response = await api(`/api/books/${book.id}/quotes`, { method: "POST", body: { body: "   " } });

    assert.equal(response.status, 400);
  });

  it("책을 지워도 뽑아 둔 글귀는 남는다", async () => {
    await resetServer();
    const book = await addBook({ title: "지울 책" });
    await api(`/api/books/${book.id}/quotes`, { method: "POST", body: { body: "남아야 하는 문장" } });

    await api(`/api/books/${book.id}`, { method: "DELETE" });

    const stored = await (await api("/api/snapshot")).json();
    assert.equal(stored.books.length, 0);
    // 책을 정리했다고 밑줄 그은 문장까지 사라지면 안 된다.
    assert.equal(stored.items.filter((item) => item.body === "남아야 하는 문장").length, 1);
  });

  it("웹에서 지운 책은 삭제 표식을 남겨 되살아나지 않는다", async () => {
    await resetServer();
    const book = await addBook({ title: "되살아나면 안 되는 책" });
    await api(`/api/books/${book.id}`, { method: "DELETE" });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          books: [{ syncId: book.syncId, updatedAt: 1000, title: "되살아나면 안 되는 책", createdAt: 1000 }],
        }),
      })
    ).json();

    assert.equal(merged.books.length, 0);
  });

  it("두 기기가 같은 책을 담으면 하나로 합치고 진도는 최신을 따른다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          books: [
            { syncId: "b1", updatedAt: 1000, title: "같은 책", author: "같은 저자", currentPage: 30, createdAt: 1000 },
            { syncId: "b2", updatedAt: 2000, title: "같은 책", author: "같은 저자", currentPage: 80, createdAt: 2000 },
          ],
        }),
      })
    ).json();

    // 진도가 다르다고 다른 책이 되면 목록에 같은 책이 두 벌 남는다.
    assert.equal(merged.books.length, 1);
    assert.equal(merged.books[0].currentPage, 80);
  });

  it("사라진 책을 가리키던 글귀는 남은 책으로 옮겨 붙는다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          books: [
            { syncId: "b1", updatedAt: 1000, title: "합쳐질 책", author: "저자", createdAt: 1000 },
            { syncId: "b2", updatedAt: 2000, title: "합쳐질 책", author: "저자", createdAt: 2000 },
          ],
          items: [
            {
              syncId: "q1",
              updatedAt: 1000,
              type: "QUOTE",
              title: "합쳐질 책",
              body: "출처를 잃으면 안 되는 문장",
              bookSyncId: "b1",
              bookPage: 12,
              createdAt: 1000,
            },
          ],
        }),
      })
    ).json();

    assert.equal(merged.books.length, 1);
    const quote = merged.items.find((item) => item.syncId === "q1");
    assert.equal(quote.bookSyncId, merged.books[0].syncId, "글귀가 출처를 잃었습니다.");
    assert.equal(quote.bookPage, 12);
  });

  it("업로드는 책도 통째로 교체한다", async () => {
    await api("/api/snapshot", {
      method: "PUT",
      body: emptySnapshot({
        books: [{ syncId: "book-old", updatedAt: 1000, title: "예전 책", createdAt: 1000 }],
      }),
    });

    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });

    // 여기서 남으면 사용자가 지운 책이 다음 병합에 서버에서 되살아난다.
    const stored = await (await api("/api/snapshot")).json();
    assert.equal(stored.books.length, 0);
  });

  it("여러 기기에서 온 같은 숫자 id를 서로 덮어쓰지 않는다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          books: [
            { id: 1, syncId: "device-a", updatedAt: 1000, title: "A기기 책", createdAt: 1000 },
            { id: 1, syncId: "device-b", updatedAt: 1000, title: "B기기 책", createdAt: 1000 },
          ],
        }),
      })
    ).json();

    assert.equal(merged.books.length, 2);
    assert.deepEqual(merged.books.map((book) => book.id).sort(), [1, 2]);
  });
});

describe("첨부 파일", () => {
  // 1x1 PNG. 내용이 무엇이든 상관없지만 진짜 바이트여야 해시가 의미 있다.
  const onePixelPng = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    "base64",
  );

  function upload(body, mime) {
    const headers = { "Content-Type": mime };
    if (apiKey) headers["X-API-Key"] = apiKey;
    return fetch(`${baseUrl}/api/attachments`, { method: "POST", headers, body });
  }

  it("올린 사진을 그대로 다시 받는다", async () => {
    const created = await (await upload(onePixelPng, "image/png")).json();

    assert.match(created.id, /^[a-f0-9]{64}\.png$/);
    assert.equal(created.uri, `appgoodwords://attachment/${created.id}`);
    assert.equal(created.size, onePixelPng.length);

    const fetched = await api(`/api/attachments/${created.id}`);
    assert.equal(fetched.status, 200);
    assert.equal(fetched.headers.get("content-type"), "image/png");
    // 형식을 브라우저가 다시 추측하면 막아 둔 보람이 없다.
    assert.equal(fetched.headers.get("x-content-type-options"), "nosniff");
    assert.deepEqual(Buffer.from(await fetched.arrayBuffer()), onePixelPng);
  });

  it("같은 파일을 다시 올려도 한 벌만 쌓인다", async () => {
    const first = await (await upload(onePixelPng, "image/png")).json();
    const second = await (await upload(onePixelPng, "image/png")).json();

    assert.equal(first.id, second.id);
    assert.equal(second.reused, true);
  });

  it("사진·동영상·소리가 아니면 받지 않는다", async () => {
    // 아무 파일이나 받으면 서버가 파일 창고가 된다.
    const response = await upload(Buffer.from("#!/bin/sh\necho hi\n"), "application/x-sh");

    assert.equal(response.status, 415);
  });

  it("브라우저에서 스크립트가 도는 형식은 막는다", async () => {
    const svg = Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>');

    const response = await upload(svg, "image/svg+xml");

    assert.equal(response.status, 415);
  });

  it("빈 파일은 받지 않는다", async () => {
    const response = await upload(Buffer.alloc(0), "image/png");

    assert.equal(response.status, 400);
  });

  it("이상한 주소로 다른 파일을 꺼내갈 수 없다", async () => {
    for (const id of ["..%2F..%2Fapp-good-words.db.json", "not-a-hash.png", "a".repeat(64)]) {
      const response = await api(`/api/attachments/${id}`);
      assert.equal(response.status, 400, `${id}가 통과했습니다.`);
    }
  });

  it("키 없이는 첨부를 꺼내갈 수 없다", async () => {
    const created = await (await upload(onePixelPng, "image/png")).json();

    // 주소만 알면 누구나 사진을 볼 수 있으면 안 된다.
    const response = await api(`/api/attachments/${created.id}`, { key: "" });

    assert.equal(response.status, 401);
  });

  it("없는 첨부는 404를 준다", async () => {
    const response = await api(`/api/attachments/${"0".repeat(64)}.png`);

    assert.equal(response.status, 404);
  });

  it("어느 기록도 안 가리키는 첨부를 세어 준다", async () => {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
    const created = await (await upload(onePixelPng, "image/png")).json();

    const before = await (await api("/api/attachments")).json();
    assert.ok(before.unusedCount >= 1, "안 쓰는 첨부를 세지 못했습니다.");

    await api("/api/diaries", {
      method: "POST",
      body: { entryDate: "2026-08-17", body: "사진 붙인 일기", imageUris: [created.uri] },
    });

    const after = await (await api("/api/attachments")).json();
    assert.equal(after.unusedCount, before.unusedCount - 1);
  });

  it("첨부 주소는 병합을 그대로 통과한다", async () => {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
    const created = await (await upload(onePixelPng, "image/png")).json();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [
            {
              syncId: "diary-attach",
              updatedAt: 1000,
              entryDate: "2026-08-17",
              body: "기기에서 올린 사진",
              imageUris: [created.uri],
              createdAt: 1000,
            },
          ],
        }),
      })
    ).json();

    // 주소가 바뀌면 다른 기기에서 첨부를 못 찾는다.
    assert.deepEqual(merged.diaries[0].imageUris, [created.uri]);
  });
});

describe("라우팅", () => {
  it("없는 엔드포인트는 404를 준다", async () => {
    const response = await api("/api/nope");

    assert.equal(response.status, 404);
  });
});

describe("일기와 할 일 병합", () => {
  async function resetServer() {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
  }

  it("일기를 보내면 서버에 남고 첨부 목록도 함께 온다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [
            {
              syncId: "diary-1",
              updatedAt: 1000,
              entryDate: "2026-08-12",
              title: "오늘",
              body: "적어 둔 내용",
              imageUris: ["content://photo/1"],
              audioUris: ["content://audio/1"],
              createdAt: 1000,
            },
          ],
        }),
      })
    ).json();

    assert.equal(merged.diaries.length, 1);
    assert.equal(merged.diaries[0].entryDate, "2026-08-12");
    assert.deepEqual(merged.diaries[0].imageUris, ["content://photo/1"]);
    assert.deepEqual(merged.diaries[0].audioUris, ["content://audio/1"]);
    assert.deepEqual(merged.diaries[0].videoUris, []);
  });

  it("일기의 날씨와 기분도 함께 오간다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [
            {
              syncId: "diary-weather",
              updatedAt: 1000,
              entryDate: "2026-08-17",
              body: "비 오는 날",
              weather: "RAIN",
              mood: "GOOD",
              createdAt: 1000,
            },
          ],
        }),
      })
    ).json();

    assert.equal(merged.diaries[0].weather, "RAIN");
    assert.equal(merged.diaries[0].mood, "GOOD");
  });

  it("고르지 않은 날씨와 기분은 빈 값으로 남는다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [{ syncId: "diary-plain", updatedAt: 1000, entryDate: "2026-08-17", body: "그냥 하루", createdAt: 1000 }],
        }),
      })
    ).json();

    // null이나 undefined가 되면 앱이 읽을 때 "null"이라는 날씨가 생긴다.
    assert.equal(merged.diaries[0].weather, "");
    assert.equal(merged.diaries[0].mood, "");
  });

  it("서버가 모르는 날씨 값도 지우지 않고 그대로 둔다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [
            {
              syncId: "diary-future",
              updatedAt: 1000,
              entryDate: "2026-08-17",
              body: "새 앱이 보낸 일기",
              weather: "AURORA",
              createdAt: 1000,
            },
          ],
        }),
      })
    ).json();

    // 서버가 선택지를 검사하면, 앱이 새 날씨를 추가할 때마다 서버도 같이 고쳐야 한다.
    assert.equal(merged.diaries[0].weather, "AURORA");
  });

  it("날짜가 없는 일기는 놓을 자리가 없어 버린다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [{ syncId: "diary-bad", updatedAt: 1000, body: "날짜 없음", createdAt: 1000 }],
        }),
      })
    ).json();

    assert.equal(merged.diaries.length, 0);
  });

  it("할 일의 완료 표시는 최근에 누른 쪽을 따른다", async () => {
    await resetServer();
    await api("/api/sync", {
      method: "POST",
      body: emptySnapshot({
        todos: [
          {
            syncId: "todo-1",
            updatedAt: 1000,
            title: "우체국 가기",
            dueDate: "2026-08-12",
            doneAt: null,
            createdAt: 1000,
          },
        ],
      }),
    });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          todos: [
            {
              syncId: "todo-1",
              updatedAt: 5000,
              title: "우체국 가기",
              dueDate: "2026-08-12",
              doneAt: 4900,
              createdAt: 1000,
            },
          ],
        }),
      })
    ).json();

    assert.equal(merged.todos.length, 1);
    assert.equal(merged.todos[0].doneAt, 4900);
  });

  it("알람과 완료 시각이 없으면 0이 아니라 null로 남는다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          todos: [{ syncId: "todo-2", updatedAt: 1000, title: "물 사기", dueDate: "2026-08-12", createdAt: 1000 }],
        }),
      })
    ).json();

    // 0으로 바뀌면 1970년에 울린 알람, 1970년에 끝낸 일이 된다.
    assert.equal(merged.todos[0].remindAt, null);
    assert.equal(merged.todos[0].doneAt, null);
  });

  it("삭제 표식을 보내면 일기와 할 일도 지워진다", async () => {
    await resetServer();
    await api("/api/sync", {
      method: "POST",
      body: emptySnapshot({
        diaries: [{ syncId: "diary-del", updatedAt: 1000, entryDate: "2026-08-12", body: "지울 일기", createdAt: 1000 }],
        todos: [{ syncId: "todo-del", updatedAt: 1000, title: "지울 할 일", dueDate: "2026-08-12", createdAt: 1000 }],
      }),
    });

    const now = Date.now();
    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          deletions: [
            { syncId: "diary-del", entityType: "DIARY", deletedAt: now },
            { syncId: "todo-del", entityType: "TODO", deletedAt: now },
          ],
        }),
      })
    ).json();

    assert.equal(merged.diaries.length, 0);
    assert.equal(merged.todos.length, 0);
  });

  it("업로드는 일기와 할 일도 통째로 교체한다", async () => {
    await api("/api/snapshot", {
      method: "PUT",
      body: emptySnapshot({
        diaries: [{ syncId: "diary-old", updatedAt: 1000, entryDate: "2026-08-01", body: "예전 일기", createdAt: 1000 }],
        todos: [{ syncId: "todo-old", updatedAt: 1000, title: "예전 할 일", dueDate: "2026-08-01", createdAt: 1000 }],
      }),
    });

    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });

    // 여기서 남으면 사용자가 지운 일기가 다음 병합에 서버에서 되살아난다.
    const stored = await (await api("/api/snapshot")).json();
    assert.equal(stored.diaries.length, 0);
    assert.equal(stored.todos.length, 0);
  });

  it("여러 기기에서 온 같은 숫자 id를 서로 덮어쓰지 않는다", async () => {
    await resetServer();
    await api("/api/sync", {
      method: "POST",
      body: emptySnapshot({
        todos: [{ id: 1, syncId: "todo-a", updatedAt: 1000, title: "A기기 할 일", dueDate: "2026-08-12", createdAt: 1000 }],
      }),
    });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          todos: [{ id: 1, syncId: "todo-b", updatedAt: 1000, title: "B기기 할 일", dueDate: "2026-08-12", createdAt: 1000 }],
        }),
      })
    ).json();

    assert.equal(merged.todos.length, 2);
    assert.deepEqual(
      merged.todos.map((todo) => todo.id).sort(),
      [1, 2],
      "번호를 다시 매기지 않으면 둘 다 id=1이라 하나가 사라진다"
    );
  });
});

describe("같은 내용 합치기", () => {
  async function resetServer() {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
  }

  function quote(syncId, title, body, updatedAt = 1000) {
    return { syncId, updatedAt, type: "QUOTE", title, body, createdAt: updatedAt };
  }

  it("처음 붙일 때 앱과 서버의 같은 기본 글귀가 두 벌이 되지 않는다", async () => {
    await resetServer();
    await api("/api/sync", {
      method: "POST",
      body: emptySnapshot({ items: [quote("server-1", "오늘의 기준", "행동은 감정이 따라올 때까지 기다리면 늘 늦다.")] }),
    });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({ items: [quote("app-1", "오늘의 기준", "행동은 감정이 따라올 때까지 기다리면 늘 늦다.")] }),
      })
    ).json();

    assert.equal(merged.items.length, 1);
  });

  it("사라진 쪽을 가리키던 이력은 남은 쪽으로 옮겨 붙는다", async () => {
    await resetServer();
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("server-1", "제목", "본문", 2000)] }) });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          items: [quote("app-1", "제목", "본문", 1000)],
          exposureEvents: [
            {
              syncId: "event-1",
              contentItemId: 1,
              contentItemSyncId: "app-1",
              contentTitle: "제목",
              contentType: "QUOTE",
              eventType: "SURFACED",
              trigger: "MANUAL_REFRESH",
              occurredAt: 1000,
            },
          ],
        }),
      })
    ).json();

    const survivor = merged.items.find((entry) => entry.syncId === "server-1");
    assert.ok(survivor, "최근에 손댄 쪽이 남아야 합니다.");
    // 부모를 잃으면 이력이 어느 글귀 것인지 알 수 없게 된다.
    assert.equal(merged.exposureEvents[0].contentItemSyncId, "server-1");
    assert.equal(merged.exposureEvents[0].contentItemId, survivor.id);
  });

  it("이력 자체는 합치지 않는다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          items: [quote("item-1", "제목", "본문")],
          exposureEvents: ["e1", "e2"].map((syncId) => ({
            syncId,
            contentItemId: 1,
            contentItemSyncId: "item-1",
            contentTitle: "제목",
            contentType: "QUOTE",
            eventType: "SURFACED",
            trigger: "MANUAL_REFRESH",
            occurredAt: 1000,
          })),
        }),
      })
    ).json();

    // 같은 글귀를 두 번 본 것은 진짜로 두 번 본 것이다.
    assert.equal(merged.exposureEvents.length, 2);
  });

  it("내용이 다르면 합치지 않는다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          items: [quote("a", "오늘의 기준", "본문 하나"), quote("b", "오늘의 기준", "본문 둘")],
        }),
      })
    ).json();

    assert.equal(merged.items.length, 2);
  });

  it("서버가 고른 승자는 앱이 고른 승자와 같다", async () => {
    // 시각이 같으면 순서만 뒤집혀도 승자가 달라질 수 있다.
    // 그러면 두 기기가 병합할 때마다 서로를 고쳐 영원히 끝나지 않는다.
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({ items: [quote("aaa", "제목", "본문", 1000), quote("bbb", "제목", "본문", 1000)] }),
      })
    ).json();

    assert.equal(merged.items.length, 1);
    // 앱의 SyncDeduplicator도 같은 시각이면 syncId가 큰 쪽을 남긴다.
    assert.equal(merged.items[0].syncId, "bbb");
  });

  it("같은 날 글 없이 사진만 올린 일기는 각각 남는다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [
            { syncId: "d1", updatedAt: 1000, entryDate: "2026-08-12", imageUris: ["content://a"], createdAt: 1000 },
            { syncId: "d2", updatedAt: 2000, entryDate: "2026-08-12", imageUris: ["content://b"], createdAt: 2000 },
          ],
        }),
      })
    ).json();

    // 합치면 한쪽 사진이 사라진다.
    assert.equal(merged.diaries.length, 2);
  });

  it("같은 날 기분만 다른 일기는 각각 남는다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [
            { syncId: "d1", updatedAt: 1000, entryDate: "2026-08-17", mood: "GOOD", createdAt: 1000 },
            { syncId: "d2", updatedAt: 2000, entryDate: "2026-08-17", mood: "SAD", createdAt: 2000 },
          ],
        }),
      })
    ).json();

    // 합치면 한쪽 기분이 사라진다. 앱의 SyncDeduplicator와 같은 판정이어야 한다.
    assert.equal(merged.diaries.length, 2);
  });

  it("날씨와 기분까지 같은 일기는 하나로 합친다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [
            { syncId: "d1", updatedAt: 1000, entryDate: "2026-08-17", body: "같은 하루", weather: "RAIN", mood: "GOOD", createdAt: 1000 },
            { syncId: "d2", updatedAt: 2000, entryDate: "2026-08-17", body: "같은 하루", weather: "RAIN", mood: "GOOD", createdAt: 2000 },
          ],
        }),
      })
    ).json();

    assert.equal(merged.diaries.length, 1);
    assert.equal(merged.diaries[0].syncId, "d2");
  });

  it("두 기기가 같은 할 일을 만들면 하나로 합친다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          todos: [
            { syncId: "t1", updatedAt: 1000, title: "우체국 가기", dueDate: "2026-08-12", createdAt: 1000 },
            { syncId: "t2", updatedAt: 2000, title: "우체국 가기", dueDate: "2026-08-12", createdAt: 2000 },
          ],
        }),
      })
    ).json();

    assert.equal(merged.todos.length, 1);
  });

  it("합친 뒤 다시 보내도 결과가 흔들리지 않는다", async () => {
    await resetServer();
    const body = emptySnapshot({
      items: [quote("server-1", "제목", "본문", 2000), quote("app-1", "제목", "본문", 1000)],
    });

    const first = await (await api("/api/sync", { method: "POST", body })).json();
    const second = await (await api("/api/sync", { method: "POST", body })).json();

    // 매번 결과가 달라지면 두 기기가 영원히 서로를 고친다.
    assert.equal(first.items.length, 1);
    assert.equal(second.items.length, 1);
    assert.equal(first.items[0].syncId, second.items[0].syncId);
  });
});
