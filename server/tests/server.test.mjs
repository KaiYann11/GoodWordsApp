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
