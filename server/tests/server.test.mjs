import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { chmod, mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { delimiter, dirname, join } from "node:path";
import { after, before, describe, it } from "node:test";
import { fileURLToPath } from "node:url";

const serverScript = join(dirname(fileURLToPath(import.meta.url)), "..", "app_good_words_server.mjs");
const apiKey = "test-api-key";

let child;
let workDir;
let baseUrl;
/** 가짜 `claude`·`codex`가 놓인 폴더. 진짜 CLI를 돌리면 시험마다 값이 들고 답이 매번 다릅니다. */
let fakeCliDir;
let serverCount = 0;

/**
 * 서버를 띄우고 붙은 주소를 돌려줍니다.
 *
 * 포트는 OS가 고릅니다(`--port 0`). 고정 포트를 쓰면 이 PC에 설치된 서버(8765)와 겹칠 수
 * 있고, 그러면 시험이 남의 DB에 요청을 보냅니다. 환경 변수를 달리 주어야 하는 시험(CLI를
 * 못 찾는 경우 등)은 하나 더 띄우고 다 쓰면 `stop()`을 부릅니다.
 */
async function startServer(env = {}) {
  const label = `server${serverCount++ || ""}`;
  const process_ = spawn(
    process.execPath,
    [serverScript, "--host", "127.0.0.1", "--port", "0", "--db", join(workDir, `${label}.db.json`), "--api-key", apiKey],
    { stdio: ["ignore", "pipe", "pipe"], env: { ...process.env, ...env } },
  );
  process_.stderr.on("data", (chunk) => console.error(`[${label}] ${chunk}`));
  const url = await new Promise((resolvePort, rejectPort) => {
    let output = "";
    process_.stdout.on("data", (chunk) => {
      output += chunk;
      const match = output.match(/running at (http:\/\/127\.0\.0\.1:\d+)/);
      if (match) resolvePort(match[1]);
    });
    process_.on("exit", (code) => rejectPort(new Error(`서버가 먼저 끝났습니다(${code}).`)));
  });
  await waitForServer(url);
  return { url, stop: () => process_.kill() };
}

/**
 * 진짜 CLI 대신 돌 가짜를 만듭니다.
 *
 * 서버가 CLI에 무엇을 어떻게 건네는지(표준 입력·파일·인자)를 그대로 되돌려 주어 시험이 볼 수
 * 있게 합니다. Windows에서는 `.cmd`가 있어야 서버가 찾고 `cmd.exe`를 거쳐 돌기 때문에,
 * 실제 배포와 같은 길을 밟습니다.
 */
async function writeFakeClis(dir) {
  await mkdir(dir, { recursive: true });
  const fakeClaude = `
const fs = require("node:fs");
const args = process.argv.slice(2);
const stdin = fs.readFileSync(0, "utf8");
const flag = (name) => { const index = args.indexOf(name); return index >= 0 ? args[index + 1] : undefined; };
const model = flag("--model") || "";
if (model === "broken-model") {
  console.log(JSON.stringify({ type: "result", is_error: true, result: "모델을 찾을 수 없습니다" }));
  process.exit(1);
}
// 답을 영영 주지 않는 CLI. 서버가 기다리다 끝내는지 본다.
if (model === "slow-model") {
  setTimeout(() => {}, 60_000);
} else {
  const system = flag("--system-prompt-file") ? fs.readFileSync(flag("--system-prompt-file"), "utf8") : "";
  console.log(JSON.stringify({
    type: "result", subtype: "success", is_error: false,
    result: JSON.stringify({ system, user: stdin, model, args, cwd: process.cwd() }),
  }));
}
`;
  const fakeCodex = `
const fs = require("node:fs");
const args = process.argv.slice(2);
const stdin = fs.readFileSync(0, "utf8");
const flag = (name) => { const index = args.indexOf(name); return index >= 0 ? args[index + 1] : undefined; };
const model = flag("--model") || "";
if (model === "broken-model") {
  process.stderr.write("OpenAI Codex v0.0.0\\n--------\\n");
  process.stderr.write('ERROR: {"type":"error","error":{"message":"이 모델은 쓸 수 없습니다"}}\\n');
  process.exit(1);
}
process.stderr.write("OpenAI Codex v0.0.0\\n--------\\nmodel: " + model + "\\n");
fs.writeFileSync(flag("--output-last-message"), JSON.stringify({ input: stdin, model, args, cwd: process.cwd() }));
console.log("done");
`;
  for (const [name, script] of [["claude", fakeClaude], ["codex", fakeCodex]]) {
    await writeFile(join(dir, `${name}.cjs`), script, "utf8");
    if (process.platform === "win32") {
      await writeFile(join(dir, `${name}.cmd`), `@echo off\r\nnode "%~dp0${name}.cjs" %*\r\n`, "utf8");
    } else {
      const launcher = join(dir, name);
      await writeFile(launcher, `#!/bin/sh\nexec node "$(dirname "$0")/${name}.cjs" "$@"\n`, "utf8");
      await chmod(launcher, 0o755);
    }
  }
}

function fakeCliPath(name) {
  return join(fakeCliDir, process.platform === "win32" ? `${name}.cmd` : name);
}

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
    contentMemos: [],
    deletions: [],
    diaries: [],
    todos: [],
    books: [],
    growthReports: [],
    moodLogs: [],
    bookReadingLogs: [],
    ...overrides,
  };
}

before(async () => {
  workDir = await mkdtemp(join(tmpdir(), "appgoodwords-test-"));
  fakeCliDir = join(workDir, "fake-cli");
  await writeFakeClis(fakeCliDir);
  child = await startServer({
    // 이 PC에 진짜 CLI가 깔려 있어도 가짜를 쓴다.
    APP_GOOD_WORDS_CLAUDE_CLI: fakeCliPath("claude"),
    APP_GOOD_WORDS_CODEX_CLI: fakeCliPath("codex"),
  });
  baseUrl = child.url;
});

after(async () => {
  if (child) child.stop();
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

describe("루틴 API", () => {
  async function resetServer() {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
  }

  async function createRoutine(title) {
    return (await api("/api/routines", { method: "POST", body: { title } })).json();
  }

  function titlesOf(routines) {
    return routines.map((routine) => routine.title);
  }

  it("새 루틴은 하루의 맨 뒤에 붙는다", async () => {
    await resetServer();
    await createRoutine("기상 후 물 한 컵");
    await createRoutine("스트레칭");
    await createRoutine("책 한 쪽");

    const listed = await (await api("/api/routines")).json();

    assert.deepEqual(titlesOf(listed.routines), ["기상 후 물 한 컵", "스트레칭", "책 한 쪽"]);
    assert.deepEqual(listed.routines.map((routine) => routine.orderIndex), [0, 1, 2]);
  });

  it("한 칸 위로 옮기면 앞 루틴과 자리를 바꾼다", async () => {
    await resetServer();
    await createRoutine("가");
    await createRoutine("나");
    const third = await createRoutine("다");

    const moved = await (
      await api(`/api/routines/${third.id}/move`, { method: "POST", body: { direction: "up" } })
    ).json();
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(moved.moved, true);
    assert.deepEqual(titlesOf(moved.routines), ["가", "다", "나"]);
    // 스냅샷도 같은 줄이어야 기기가 같은 차례를 받는다.
    assert.deepEqual(titlesOf(stored.routines), ["가", "다", "나"]);
    assert.deepEqual(stored.routines.map((routine) => routine.orderIndex), [0, 1, 2]);
  });

  it("맨 위에서 더 올려도 줄이 흐트러지지 않는다", async () => {
    await resetServer();
    const first = await createRoutine("가");
    await createRoutine("나");

    const response = await api(`/api/routines/${first.id}/move`, {
      method: "POST",
      body: { direction: "up" },
    });
    const moved = await response.json();
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(response.status, 200);
    assert.equal(moved.moved, false);
    assert.deepEqual(titlesOf(stored.routines), ["가", "나"]);
  });

  it("차례가 그대로인 루틴은 새 리비전을 받지 않는다", async () => {
    // 안 바뀐 것까지 번호가 오르면 증분 동기화가 매번 루틴 전부를 실어 나른다.
    await resetServer();
    await createRoutine("가");
    await createRoutine("나");
    await createRoutine("다");
    const fourth = await createRoutine("라");
    const before = await (await api("/api/snapshot")).json();
    const revOf = (snapshot, title) => snapshot.routines.find((routine) => routine.title === title).rev;

    await api(`/api/routines/${fourth.id}/move`, { method: "POST", body: { direction: "up" } });
    const after = await (await api("/api/snapshot")).json();

    assert.equal(revOf(after, "가"), revOf(before, "가"));
    assert.equal(revOf(after, "나"), revOf(before, "나"));
    assert.ok(revOf(after, "다") > revOf(before, "다"));
    assert.ok(revOf(after, "라") > revOf(before, "라"));
  });

  it("루틴 이름을 고쳐도 차례는 그대로다", async () => {
    await resetServer();
    await createRoutine("가");
    const second = await createRoutine("나");

    await api(`/api/routines/${second.id}`, { method: "PUT", body: { title: "나(고침)" } });
    const stored = await (await api("/api/snapshot")).json();

    assert.deepEqual(titlesOf(stored.routines), ["가", "나(고침)"]);
    assert.equal(stored.routines[1].orderIndex, 1);
  });

  it("맨 위로는 한 번에 첫 자리로 올린다", async () => {
    // 한 칸씩만 되면 서른 번째 루틴을 맨 위로 올리는 데 스물아홉 번을 불러야 한다.
    await resetServer();
    await createRoutine("가");
    await createRoutine("나");
    await createRoutine("다");
    const fourth = await createRoutine("라");

    const moved = await (
      await api(`/api/routines/${fourth.id}/move`, { method: "POST", body: { direction: "top" } })
    ).json();
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(moved.moved, true);
    assert.deepEqual(titlesOf(stored.routines), ["라", "가", "나", "다"]);
    assert.deepEqual(stored.routines.map((routine) => routine.orderIndex), [0, 1, 2, 3]);
  });

  it("맨 아래로는 한 번에 끝자리로 내린다", async () => {
    await resetServer();
    const first = await createRoutine("가");
    await createRoutine("나");
    await createRoutine("다");

    await api(`/api/routines/${first.id}/move`, { method: "POST", body: { direction: "bottom" } });
    const stored = await (await api("/api/snapshot")).json();

    assert.deepEqual(titlesOf(stored.routines), ["나", "다", "가"]);
  });

  it("position으로 화면에 적힌 자리를 그대로 부를 수 있다", async () => {
    await resetServer();
    await createRoutine("가");
    await createRoutine("나");
    await createRoutine("다");
    const fourth = await createRoutine("라");

    // 1부터 세는 자리다. 2를 부르면 두 번째가 된다.
    await api(`/api/routines/${fourth.id}/move`, { method: "POST", body: { position: 2 } });
    const stored = await (await api("/api/snapshot")).json();

    assert.deepEqual(titlesOf(stored.routines), ["가", "라", "나", "다"]);
  });

  it("줄 밖의 자리는 끝으로 당겨 붙인다", async () => {
    await resetServer();
    const first = await createRoutine("가");
    await createRoutine("나");

    await api(`/api/routines/${first.id}/move`, { method: "POST", body: { position: 999 } });
    const stored = await (await api("/api/snapshot")).json();

    assert.deepEqual(titlesOf(stored.routines), ["나", "가"]);
  });

  it("맨 위 루틴을 맨 위로 올려도 리비전이 오르지 않는다", async () => {
    await resetServer();
    const first = await createRoutine("가");
    await createRoutine("나");
    const before = await (await api("/api/snapshot")).json();

    const moved = await (
      await api(`/api/routines/${first.id}/move`, { method: "POST", body: { direction: "top" } })
    ).json();
    const after = await (await api("/api/snapshot")).json();

    assert.equal(moved.moved, false);
    assert.deepEqual(
      after.routines.map((routine) => routine.rev),
      before.routines.map((routine) => routine.rev),
    );
  });

  it("direction이 없거나 모르는 값이면 옮기지 않는다", async () => {
    await resetServer();
    const routine = await createRoutine("가");

    const missing = await api(`/api/routines/${routine.id}/move`, { method: "POST", body: {} });
    const unknown = await api(`/api/routines/${routine.id}/move`, {
      method: "POST",
      body: { direction: "sideways" },
    });
    const zero = await api(`/api/routines/${routine.id}/move`, { method: "POST", body: { position: 0 } });

    assert.equal(missing.status, 400);
    assert.equal(unknown.status, 400);
    // 자리는 1부터 센다. 0을 조용히 첫 자리로 받아 주면 앱과 셈이 어긋난다.
    assert.equal(zero.status, 400);
  });

  it("메모를 저장하면 수행도 한 번 기록한다", async () => {
    await resetServer();
    const routine = await (
      await api("/api/routines", { method: "POST", body: { title: "물 마시기" } })
    ).json();

    const response = await api(`/api/routines/${routine.id}/memos`, {
      method: "POST",
      body: { body: "한 컵 마심" },
    });
    const memo = await response.json();
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(response.status, 201);
    assert.equal(memo.body, "한 컵 마심");
    assert.equal(stored.routineMemos.length, 1);
    assert.equal(stored.routineChecks.length, 1);
    assert.equal(stored.routineMemos[0].routineId, routine.id);
    assert.equal(stored.routineChecks[0].routineId, routine.id);
    assert.equal(stored.routineChecks[0].routineSyncId, routine.syncId);
    assert.equal(stored.routineChecks[0].routineTitle, routine.title);
  });

  it("빈 메모는 메모도 수행도 기록하지 않는다", async () => {
    await resetServer();
    const routine = await (
      await api("/api/routines", { method: "POST", body: { title: "물 마시기" } })
    ).json();

    const response = await api(`/api/routines/${routine.id}/memos`, {
      method: "POST",
      body: { body: "   " },
    });
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(response.status, 400);
    assert.equal(stored.routineMemos.length, 0);
    assert.equal(stored.routineChecks.length, 0);
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

  it("감사·반성 일기의 종류와 답이 기기 사이를 오간다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [
            {
              syncId: "diary-gratitude",
              updatedAt: 1000,
              entryDate: "2026-08-18",
              kind: "GRATITUDE",
              // 가운데 빈칸. 자리를 잃으면 마지막 답이 첫 물음의 답이 된다.
              answers: ["따뜻한 커피", "", "비 그친 하늘"],
              createdAt: 1000,
            },
          ],
        }),
      })
    ).json();

    assert.equal(merged.diaries[0].kind, "GRATITUDE");
    assert.deepEqual(merged.diaries[0].answers, ["따뜻한 커피", "", "비 그친 하늘"]);
  });

  it("종류가 없던 시절의 일기는 자유 일기가 된다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [{ syncId: "diary-old", updatedAt: 1000, entryDate: "2026-08-17", body: "예전 일기", createdAt: 1000 }],
        }),
      })
    ).json();

    // 빈 값으로 두면 앱에서 아무 종류도 고르지 않은 일기가 된다.
    assert.equal(merged.diaries[0].kind, "FREE");
    assert.deepEqual(merged.diaries[0].answers, []);
  });

  it("서버가 모르는 일기 종류도 지우지 않고 그대로 둔다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [
            {
              syncId: "diary-future-kind",
              updatedAt: 1000,
              entryDate: "2026-08-17",
              kind: "MORNING_PAGE",
              answers: ["새 앱이 보낸 답"],
              createdAt: 1000,
            },
          ],
        }),
      })
    ).json();

    assert.equal(merged.diaries[0].kind, "MORNING_PAGE");
  });

  it("같은 날 쓴 감사 일기와 반성 일기는 합쳐지지 않는다", async () => {
    await resetServer();

    // 앱 SyncDeduplicator와 같은 기준이어야 한다. 종류를 안 보면 한쪽이 사라진다.
    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          diaries: [
            {
              syncId: "diary-g",
              updatedAt: 1000,
              entryDate: "2026-08-18",
              kind: "GRATITUDE",
              answers: ["커피"],
              createdAt: 1000,
            },
            {
              syncId: "diary-r",
              updatedAt: 2000,
              entryDate: "2026-08-18",
              kind: "REFLECTION",
              answers: ["커피"],
              createdAt: 2000,
            },
          ],
        }),
      })
    ).json();

    assert.equal(merged.diaries.length, 2);
  });

  it("답만 적은 일기도 저장한다", async () => {
    await resetServer();

    // 감사 일기는 본문 없이 답만 적는 날이 흔하다. 앱의 hasSomethingToSave와 같은 기준이어야 한다.
    const created = await (
      await api("/api/diaries", {
        method: "POST",
        body: { entryDate: "2026-08-18", kind: "GRATITUDE", answers: ["", "고마운 사람"] },
      })
    ).json();

    assert.deepEqual(created.answers, ["", "고마운 사람"]);
  });

  it("답이 모두 비어 있으면 저장하지 않는다", async () => {
    await resetServer();

    const response = await api("/api/diaries", {
      method: "POST",
      body: { entryDate: "2026-08-18", kind: "GRATITUDE", answers: ["", "  "] },
    });

    assert.equal(response.status, 400);
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

describe("AI 돌아보기", () => {
  function report(overrides = {}) {
    return {
      id: 1,
      syncId: "report-1",
      updatedAt: 1_000,
      period: "WEEKLY",
      periodStart: "2026-08-17",
      periodEnd: "2026-08-23",
      model: "gpt-4o-mini",
      strengths: ["이레 내내 물을 마셨습니다."],
      improvements: [],
      suggestedQuote: "작게 시작하라.",
      suggestedQuoteAuthor: "",
      suggestedRoutines: ["자기 전 스트레칭"],
      guide: "천천히 가도 됩니다.",
      createdAt: 1_000,
      ...overrides,
    };
  }

  it("받아 둔 돌아보기가 스냅샷에 그대로 남는다", async () => {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot({ growthReports: [report()] }) });

    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.growthReports.length, 1);
    assert.equal(stored.growthReports[0].guide, "천천히 가도 됩니다.");
    assert.deepEqual(stored.growthReports[0].suggestedRoutines, ["자기 전 스트레칭"]);
    assert.equal(stored.growthReportCount, 1);
  });

  it("알맹이가 없는 것은 받아 두지 않는다", async () => {
    // 화면에 놓아도 빈 카드만 보인다.
    await api("/api/snapshot", {
      method: "PUT",
      body: emptySnapshot({
        growthReports: [report({ strengths: [], improvements: [], suggestedRoutines: [], suggestedQuote: "", guide: "" })],
      }),
    });

    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.growthReports.length, 0);
  });

  it("같은 기간을 두고 두 기기가 각각 받은 것은 하나로 합친다", async () => {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot({ growthReports: [report()] }) });

    // 글은 달라도 같은 주를 두고 쓴 것이다. 본문까지 견주면 늘 다른 것이 되어 쌓이기만 한다.
    await api("/api/sync", {
      method: "POST",
      body: emptySnapshot({
        growthReports: [
          report({ id: 9, syncId: "report-2", updatedAt: 2_000, createdAt: 2_000, guide: "다르게 쓴 글" }),
        ],
      }),
    });
    await api("/api/deduplicate", { method: "POST" });
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.growthReports.length, 1);
    // 최신 updatedAt이 남는다.
    assert.equal(stored.growthReports[0].guide, "다르게 쓴 글");
    // 사라진 쪽에는 삭제 표식을 남겨야, 바뀐 것만 받는 기기도 사라진 사실을 안다.
    assert.ok(stored.deletions.some((entry) => entry.entityType === "GROWTH_REPORT"));
  });

  it("서버에 AI 열쇠가 없으면 왜 안 되는지 알려 준다", async () => {
    const response = await api("/api/growth-feedback", {
      method: "POST",
      body: { system: "코치입니다", user: "기록입니다", model: "gpt-4o-mini" },
    });
    const body = await response.json();

    assert.equal(response.status, 503);
    assert.ok(body.error.includes("OPENAI_API_KEY"), body.error);
  });

  it("물어볼 내용이 비어 있으면 부르지 않는다", async () => {
    const response = await api("/api/growth-feedback", { method: "POST", body: { system: "코치입니다" } });

    // 열쇠를 보기 전에 막는다. 빈 물음은 열쇠가 있어도 보낼 것이 없다.
    assert.equal(response.status, 400);
  });

  it("클로드로 물으면 클로드 열쇠를 찾는다", async () => {
    // 열쇠가 둘로 나뉘어 있다. 어느 쪽을 넣어야 하는지 사유에 적히지 않으면 사용자가 알 수 없다.
    const response = await api("/api/growth-feedback", {
      method: "POST",
      body: { system: "코치입니다", user: "기록입니다", provider: "anthropic", model: "claude-opus-5" },
    });
    const body = await response.json();

    assert.equal(response.status, 503);
    assert.ok(body.error.includes("ANTHROPIC_API_KEY"), body.error);
  });

  it("모르는 공급자는 조용히 다른 곳으로 보내지 않는다", async () => {
    // OpenAI로 슬쩍 돌리면 사용자가 고른 곳이 아닌 데로 기록이 나간다.
    const response = await api("/api/growth-feedback", {
      method: "POST",
      body: { system: "코치입니다", user: "기록입니다", provider: "gemini" },
    });

    assert.equal(response.status, 400);
  });

  it("공급자를 안 보내는 옛 앱은 OpenAI로 읽는다", async () => {
    const response = await api("/api/growth-feedback", {
      method: "POST",
      body: { system: "코치입니다", user: "기록입니다" },
    });
    const body = await response.json();

    assert.equal(response.status, 503);
    assert.ok(body.error.includes("OPENAI_API_KEY"), body.error);
  });

  it("Claude CLI에는 물음을 표준 입력과 파일로 건네고 도구 없이 돌린다", async () => {
    // 물음을 인자로 넘기면 ps로 남에게 보이고, 따옴표·줄바꿈이 셸에서 깨진다.
    const system = "코치입니다.\n\"따옴표\"와 %PATH% 같은 글자도 그대로 가야 합니다.";
    const user = "기록입니다.\n둘째 줄 & 셋째 | 줄";
    const response = await api("/api/growth-feedback", {
      method: "POST",
      body: { system, user, provider: "claude_cli", model: "claude-opus-5" },
    });
    const body = await response.json();

    assert.equal(response.status, 200, JSON.stringify(body));
    const seen = JSON.parse(body.text);
    assert.equal(seen.system, system);
    assert.equal(seen.user, user);
    assert.equal(seen.model, "claude-opus-5");
    assert.ok(seen.args.includes("-p"), seen.args.join(" "));
    assert.ok(seen.args.includes("--safe-mode"), seen.args.join(" "));
    assert.ok(seen.args.includes("--no-session-persistence"), seen.args.join(" "));
    // 도구를 끄는 빈 인자가 cmd.exe를 거쳐도 살아 있어야 한다. 사라지면 다음 옵션이 도구 이름이 된다.
    assert.equal(seen.args[seen.args.indexOf("--tools") + 1], "", seen.args.join(" "));
    // 서버 폴더가 아니라 빈 임시 폴더에서 돈다. 서버 DB 옆에서 돌면 CLAUDE.md든 DB든 손에 닿는다.
    assert.ok(seen.cwd.includes("appgoodwords-ai-"), seen.cwd);
  });

  it("Codex CLI에는 일러 주는 말을 앞에 붙여 표준 입력으로 건네고 답은 파일로 받는다", async () => {
    const response = await api("/api/growth-feedback", {
      method: "POST",
      body: { system: "코치입니다", user: "기록입니다", provider: "codex_cli", model: "gpt-5.5" },
    });
    const body = await response.json();

    assert.equal(response.status, 200, JSON.stringify(body));
    const seen = JSON.parse(body.text);
    assert.equal(seen.input, "코치입니다\n\n기록입니다");
    assert.equal(seen.model, "gpt-5.5");
    assert.equal(seen.args[0], "exec");
    assert.ok(seen.args.includes("--ephemeral"), seen.args.join(" "));
    assert.ok(seen.args.includes("--ignore-user-config"), seen.args.join(" "));
    assert.equal(seen.args.at(-1), "-");
    assert.ok(seen.cwd.includes("appgoodwords-ai-"), seen.cwd);
  });

  it("모델을 비워 보내면 CLI의 기본 모델을 쓴다", async () => {
    const response = await api("/api/growth-feedback", {
      method: "POST",
      body: { system: "코치입니다", user: "기록입니다", provider: "claude_cli", model: "" },
    });
    const body = await response.json();

    assert.equal(response.status, 200, JSON.stringify(body));
    const seen = JSON.parse(body.text);
    assert.ok(!seen.args.includes("--model"), seen.args.join(" "));
  });

  it("셸이 다르게 읽을 글자가 든 모델 이름은 CLI에 넘기지 않는다", async () => {
    // 모델 이름은 인자로 나가는 유일한 사용자 값이다.
    const response = await api("/api/growth-feedback", {
      method: "POST",
      body: { system: "코치입니다", user: "기록입니다", provider: "claude_cli", model: 'opus" && echo pwned' },
    });

    assert.equal(response.status, 400);
  });

  it("CLI가 실패하면 그 사유를 돌려준다", async () => {
    const claude = await api("/api/growth-feedback", {
      method: "POST",
      body: { system: "코치입니다", user: "기록입니다", provider: "claude_cli", model: "broken-model" },
    });
    const claudeBody = await claude.json();
    assert.equal(claude.status, 502);
    assert.ok(claudeBody.error.includes("모델을 찾을 수 없습니다"), claudeBody.error);

    // Codex는 사유를 stderr에 `ERROR: {json}`으로 찍는다. 배너에 묻히지 않게 message만 뽑는다.
    const codex = await api("/api/growth-feedback", {
      method: "POST",
      body: { system: "코치입니다", user: "기록입니다", provider: "codex_cli", model: "broken-model" },
    });
    const codexBody = await codex.json();
    assert.equal(codex.status, 502);
    assert.ok(codexBody.error.includes("이 모델은 쓸 수 없습니다"), codexBody.error);
  });

  it("서버에 CLI가 없으면 어디에 넣어야 하는지 알려 준다", async () => {
    const server = await startServer({ APP_GOOD_WORDS_CLAUDE_CLI: join(workDir, "no-such-claude") });
    try {
      const response = await fetch(`${server.url}/api/growth-feedback`, {
        method: "POST",
        headers: { "X-API-Key": apiKey, "Content-Type": "application/json" },
        body: JSON.stringify({ system: "코치입니다", user: "기록입니다", provider: "claude_cli" }),
      });
      const body = await response.json();

      assert.equal(response.status, 503);
      assert.ok(body.error.includes("APP_GOOD_WORDS_CLAUDE_CLI"), body.error);
    } finally {
      server.stop();
    }
  });

  it("CLI가 답을 주지 않으면 기다리다 끝내고 504를 돌려준다", async () => {
    // 영영 붙들고 있으면 앱은 사유 없이 시간 초과만 보고, 서버에는 CLI 프로세스가 쌓인다.
    const server = await startServer({
      APP_GOOD_WORDS_CLAUDE_CLI: fakeCliPath("claude"),
      APP_GOOD_WORDS_AI_TIMEOUT_MS: "1500",
    });
    try {
      const startedAt = Date.now();
      const response = await fetch(`${server.url}/api/growth-feedback`, {
        method: "POST",
        headers: { "X-API-Key": apiKey, "Content-Type": "application/json" },
        body: JSON.stringify({ system: "코치입니다", user: "기록입니다", provider: "claude_cli", model: "slow-model" }),
      });
      const body = await response.json();

      assert.equal(response.status, 504, JSON.stringify(body));
      assert.ok(body.error.includes("답하지 않았습니다"), body.error);
      assert.ok(Date.now() - startedAt < 20_000, "시간 안에 끝내지 않았다");
    } finally {
      server.stop();
    }
  });

  it("경로를 따로 주지 않으면 PATH에서 CLI를 찾는다", async () => {
    // Windows에서는 `claude.cmd`처럼 확장자가 붙어 있어 spawn이 알아서 찾지 못한다.
    // Windows의 환경 변수 이름은 `Path`일 수 있어, 있는 이름 그대로 덮어쓴다.
    const pathKey = Object.keys(process.env).find((key) => key.toUpperCase() === "PATH") || "PATH";
    const server = await startServer({
      APP_GOOD_WORDS_CLAUDE_CLI: "",
      [pathKey]: `${fakeCliDir}${delimiter}${process.env[pathKey] || ""}`,
    });
    try {
      const response = await fetch(`${server.url}/api/growth-feedback`, {
        method: "POST",
        headers: { "X-API-Key": apiKey, "Content-Type": "application/json" },
        body: JSON.stringify({ system: "코치입니다", user: "기록입니다", provider: "claude_cli" }),
      });
      const body = await response.json();

      assert.equal(response.status, 200, JSON.stringify(body));
      assert.equal(JSON.parse(body.text).user, "기록입니다");
    } finally {
      server.stop();
    }
  });
});

describe("임시저장소와 메모", () => {
  it("분류할 때 원문과 식별자가 유지된다", async () => {
    const created = await (await api("/api/content", {
      method: "POST",
      body: { type: "INBOX", title: "수집한 용어", body: "첫 줄\n둘째 줄", sourceUrl: "https://example.com/inbox", tags: ["참고"] },
    })).json();
    assert.equal(created.type, "INBOX");
    const edited = await (await api(`/api/content/${created.id}`, {
      method: "PUT", body: { ...created, type: "MEMO" },
    })).json();
    assert.equal(edited.type, "MEMO");
    assert.equal(edited.syncId, created.syncId);
    assert.equal(edited.body, created.body);
    assert.equal(edited.sourceUrl, created.sourceUrl);
    assert.deepEqual(edited.tags, created.tags);
    const confirmed = await (await api(`/api/content/${created.id}/toggle-confirm`, { method: "POST" })).json();
    assert.equal(confirmed.confirmed, false);
    const stored = await (await api("/api/snapshot")).json();
    assert.equal(stored.items.find(item => item.syncId === created.syncId).type, "MEMO");
    assert.equal(stored.items.find(item => item.syncId === created.syncId).showCount, 0);
  });

  it("스냅샷을 교체해도 개인 기록 종류를 글귀로 바꾸지 않는다", async () => {
    const items = ["INBOX", "MEMO", "CONCEPT"].map((type, index) => ({
      id: index + 1, syncId: `collection-${type}`, type, title: type, body: `원문 ${index}`,
    }));
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot({ schemaVersion: 20, items }) });
    const stored = await (await api("/api/snapshot")).json();
    // 올린 것이 옛 형식이라도 돌려주는 것은 서버의 형식이다.
    assert.equal(stored.schemaVersion, 22);
    assert.deepEqual(new Set(stored.items.map(item => item.type)), new Set(["INBOX", "MEMO", "CONCEPT"]));
  });

  it("개념은 원문과 AI 설명 메모를 함께 동기화하고 읽음으로 세지 않는다", async () => {
    const concept = await (await api("/api/content", {
      method: "POST",
      body: { type: "CONCEPT", title: "인지 부하", body: "원래 저장한 문장", sourceUrl: "https://example.com/context" },
    })).json();
    const memo = await (await api(`/api/content/${concept.id}/memos`, {
      method: "POST", body: { body: "AI 설명\n한꺼번에 처리해야 하는 정보의 양입니다." },
    })).json();
    const confirmed = await (await api(`/api/content/${concept.id}/toggle-confirm`, { method: "POST" })).json();
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(concept.type, "CONCEPT");
    assert.equal(stored.items.find(item => item.syncId === concept.syncId).body, "원래 저장한 문장");
    assert.equal(stored.contentMemos.find(item => item.syncId === memo.syncId).contentItemSyncId, concept.syncId);
    assert.equal(confirmed.confirmed, false);
  });
});

describe("하지 않을 일", () => {
  it("제목만 저장하고 메모를 수정해도 같은 기록으로 남는다", async () => {
    const response = await api("/api/content", {
      method: "POST", body: { type: "NOT_TO_DO", title: "잠들기 전 숏폼 오래 보기" },
    });
    assert.equal(response.status, 201);
    const created = await response.json();
    assert.equal(created.type, "NOT_TO_DO");
    assert.equal(created.body, "");
    const edited = await (await api(`/api/content/${created.id}`, {
      method: "PUT", body: { body: "휴대폰은 책상에 두기\n잠을 먼저 챙기기" },
    })).json();
    assert.equal(edited.syncId, created.syncId);
    assert.equal(edited.type, "NOT_TO_DO");
    assert.equal(edited.title, created.title);
    assert.equal(edited.body, "휴대폰은 책상에 두기\n잠을 먼저 챙기기");
    const confirmed = await (await api(`/api/content/${created.id}/toggle-confirm`, { method: "POST" })).json();
    assert.equal(confirmed.confirmed, false);
    const stored = await (await api("/api/snapshot")).json();
    assert.equal(stored.items.find(item => item.id === created.id).showCount, 0);
    assert.equal(stored.exposureEvents.filter(event => event.contentItemSyncId === created.syncId).length, 0);
  });

  it("빈 제목은 저장하지 않는다", async () => {
    const response = await api("/api/content", {
      method: "POST", body: { type: "NOT_TO_DO", title: "   ", body: "제목 없는 메모" },
    });
    assert.equal(response.status, 400);
  });

  it("백업 교체와 병합에 종류를 보존하고 삭제한 기준을 되살리지 않는다", async () => {
    const rule = { id: 1, syncId: "not-to-do-sync", type: "NOT_TO_DO", title: "충동 구매", body: "하루 기다리기", updatedAt: 100 };
    const quote = { ...rule, id: 2, syncId: "same-text-quote", type: "QUOTE" };
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot({ items: [rule, quote] }) });
    const backup = await (await api("/api/snapshot")).json();
    assert.equal(backup.items.length, 2);
    assert.equal(backup.items.find(item => item.syncId === rule.syncId).type, "NOT_TO_DO");
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [{ ...rule, body: "일주일 기다리기", updatedAt: 200 }] }) });
    const merged = await (await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [rule] }) })).json();
    const saved = merged.items.find(item => item.syncId === rule.syncId);
    assert.equal(saved.body, "일주일 기다리기");
    await api(`/api/content/${saved.id}`, { method: "DELETE" });
    const deleted = await (await api("/api/sync", { method: "POST", body: backup })).json();
    assert.ok(deleted.items.every(item => item.syncId !== rule.syncId));
    assert.ok(deleted.deletions.some(entry => entry.syncId === rule.syncId && entry.entityType === "CONTENT_ITEM"));
    assert.ok(deleted.items.some(item => item.syncId === quote.syncId));
  });
});

describe("번뜩인 아이디어", () => {
  it("아이디어 종류가 그대로 남는다", async () => {
    // 서버가 모르는 종류로 보면 글귀로 바꿔 버려, 보관함에서 아이디어만 골라 볼 수 없게 된다.
    const created = await (
      await api("/api/content", {
        method: "POST",
        body: { type: "IDEA", title: "자기 전 5분 회고", body: "하루를 닫는 말 한 줄" },
      })
    ).json();

    assert.equal(created.type, "IDEA");

    const stored = await (await api("/api/snapshot")).json();
    assert.equal(stored.items.find((item) => item.id === created.id).type, "IDEA");
  });

  it("모르는 종류는 여전히 글귀로 읽는다", async () => {
    const created = await (
      await api("/api/content", { method: "POST", body: { type: "NOTE", title: "무엇", body: "내용" } })
    ).json();

    assert.equal(created.type, "QUOTE");
  });
});

describe("오늘 기분", () => {
  function moodLog(overrides = {}) {
    return {
      id: 1,
      syncId: "mood-1",
      updatedAt: 1_000,
      entryDate: "2026-08-26",
      mood: "TIRED",
      createdAt: 1_000,
      ...overrides,
    };
  }

  it("찍어 둔 기분이 스냅샷에 그대로 남는다", async () => {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot({ moodLogs: [moodLog()] }) });

    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.moodLogs.length, 1);
    assert.equal(stored.moodLogs[0].entryDate, "2026-08-26");
    assert.equal(stored.moodLogs[0].mood, "TIRED");
    assert.equal(stored.moodLogCount, 1);
  });

  it("업로드가 기분만 남겨 두지 않는다", async () => {
    // replaceSnapshot을 빠뜨리면 그 종류만 서버에 남아, 사용자가 지운 기분이 다음 병합에 되살아난다.
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot({ moodLogs: [moodLog()] }) });
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot({ moodLogs: [] }) });

    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.moodLogs.length, 0);
  });

  it("날짜나 기분이 없으면 받아 두지 않는다", async () => {
    // 어느 날의 무엇인지 알 수 없어 그래프에 놓을 수 없다.
    await api("/api/snapshot", {
      method: "PUT",
      body: emptySnapshot({
        moodLogs: [moodLog({ entryDate: "" }), moodLog({ id: 2, syncId: "mood-2", mood: "" })],
      }),
    });

    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.moodLogs.length, 0);
  });

  it("같은 날 두 기기에서 찍은 기분은 하나로 합친다", async () => {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot({ moodLogs: [moodLog()] }) });

    await api("/api/sync", {
      method: "POST",
      body: emptySnapshot({
        moodLogs: [moodLog({ id: 9, syncId: "mood-2", updatedAt: 2_000, createdAt: 2_000, mood: "GOOD" })],
      }),
    });
    await api("/api/deduplicate", { method: "POST" });
    const stored = await (await api("/api/snapshot")).json();

    // 그날 기분이 둘이면 겹쳐 보는 자리가 그날을 통째로 버린다.
    assert.equal(stored.moodLogs.length, 1);
    assert.equal(stored.moodLogs[0].mood, "GOOD");
    assert.ok(stored.deletions.some((entry) => entry.entityType === "MOOD_LOG"));
  });

  it("다른 날 기분은 합치지 않는다", async () => {
    await api("/api/snapshot", {
      method: "PUT",
      body: emptySnapshot({
        moodLogs: [moodLog(), moodLog({ id: 2, syncId: "mood-2", entryDate: "2026-08-25", mood: "GOOD" })],
      }),
    });
    await api("/api/deduplicate", { method: "POST" });

    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.moodLogs.length, 2);
  });
});

describe("독서 기록", () => {
  function readingLog(overrides = {}) {
    return {
      id: 1,
      syncId: "reading-1",
      updatedAt: 1_000,
      bookId: 1,
      bookSyncId: "book-1",
      bookTitle: "아주 작은 습관의 힘",
      readDate: "2026-09-23",
      startPage: 10,
      endPage: 42,
      createdAt: 1_000,
      ...overrides,
    };
  }

  it("날짜별 독서 기록이 스냅샷에 그대로 남는다", async () => {
    // 여기서 버리면 앱의 독서 걸음이 올릴 때마다 사라져, 연속 날수가 기기마다 달라진다.
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot({ bookReadingLogs: [readingLog()] }) });

    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.bookReadingLogs.length, 1);
    assert.equal(stored.bookReadingLogs[0].readDate, "2026-09-23");
    assert.equal(stored.bookReadingLogs[0].endPage, 42);
    assert.equal(stored.bookReadingLogCount, 1);
  });

  it("날짜가 없는 기록은 버린다", async () => {
    // 어느 날 읽은 것인지 모르면 걸음에도 그래프에도 놓을 수 없다.
    await api("/api/snapshot", {
      method: "PUT",
      body: emptySnapshot({ bookReadingLogs: [readingLog({ readDate: "" })] }),
    });

    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.bookReadingLogs.length, 0);
  });

  it("두 기기가 같은 날 같은 책을 적으면 한 줄로 합치고 사라진 쪽에 표식을 남긴다", async () => {
    // 쪽수까지 견주면 둘 다 남아 그날 읽은 쪽수가 겹쳐 세진다.
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          bookReadingLogs: [
            readingLog({ id: 1, syncId: "reading-a", updatedAt: 1_000, endPage: 42 }),
            readingLog({ id: 2, syncId: "reading-b", updatedAt: 2_000, endPage: 77 }),
          ],
        }),
      })
    ).json();

    assert.equal(merged.bookReadingLogs.length, 1);
    assert.equal(merged.bookReadingLogs[0].syncId, "reading-b");
    assert.equal(merged.bookReadingLogs[0].endPage, 77);
    // 바뀐 것만 받는 기기도 사라진 사실을 들어야 한다.
    assert.ok(merged.deletions.some((entry) => entry.entityType === "BOOK_READING_LOG"));
  });

  it("다른 날이거나 다른 책이면 합치지 않는다", async () => {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          bookReadingLogs: [
            readingLog({ id: 1, syncId: "reading-a" }),
            readingLog({ id: 2, syncId: "reading-b", readDate: "2026-09-24" }),
            readingLog({ id: 3, syncId: "reading-c", bookSyncId: "book-2" }),
          ],
        }),
      })
    ).json();

    assert.equal(merged.bookReadingLogs.length, 3);
  });

  it("웹에서 읽은 쪽을 올리면 그날 읽은 것으로 남는다", async () => {
    // 앱과 웹이 같은 날을 세야 독서 걸음이 기기마다 달라지지 않는다.
    const book = await (await api("/api/books", { method: "POST", body: { title: "읽는 중인 책", currentPage: 10 } })).json();
    await api(`/api/books/${book.id}`, { method: "PUT", body: { ...book, currentPage: 55 } });

    const stored = await (await api("/api/snapshot")).json();
    const logs = stored.bookReadingLogs.filter((log) => log.bookSyncId === book.syncId);

    assert.equal(logs.length, 1);
    assert.equal(logs[0].endPage, 55);
    // 하루에 책 하나당 한 줄이다. 한 번 더 올려도 줄이 늘지 않고 마지막 쪽만 오른다.
    await api(`/api/books/${book.id}`, { method: "PUT", body: { ...book, currentPage: 80 } });
    const again = await (await api("/api/snapshot")).json();
    const sameDay = again.bookReadingLogs.filter((log) => log.bookSyncId === book.syncId);
    assert.equal(sameDay.length, 1);
    assert.equal(sameDay[0].endPage, 80);
  });

  it("제목만 고친 날은 읽은 날로 세지 않는다", async () => {
    const book = await (await api("/api/books", { method: "POST", body: { title: "안 읽은 책", currentPage: 0 } })).json();
    await api(`/api/books/${book.id}`, { method: "PUT", body: { ...book, title: "이름만 고친 책" } });

    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.bookReadingLogs.filter((log) => log.bookSyncId === book.syncId).length, 0);
  });
});

describe("원칙", () => {
  it("원칙을 저장하고 스냅샷에 그대로 둔다", async () => {
    const created = await (
      await api("/api/content", {
        method: "POST",
        body: { type: "PRINCIPLE", title: "화가 난 채로 답하지 않는다", body: "하루 자고 나서 다시 읽기" },
      })
    ).json();

    assert.equal(created.type, "PRINCIPLE");

    const stored = await (await api("/api/snapshot")).json();
    const principle = stored.items.find((item) => item.syncId === created.syncId);
    // 서버가 모르는 종류로 보면 글귀로 바꿔 버려, 원칙 탭에서 사라진다.
    assert.equal(principle.type, "PRINCIPLE");
    assert.equal(principle.body, "하루 자고 나서 다시 읽기");
  });

  it("제목이 없으면 거절한다", async () => {
    const response = await api("/api/content", { method: "POST", body: { type: "PRINCIPLE", title: "   " } });

    assert.equal(response.status, 400);
    assert.match((await response.json()).error, /원칙/);
  });

  it("본문이 없어도 저장된다", async () => {
    // 기준은 한 줄이면 충분하다. 글귀처럼 본문을 요구하면 적을 수 없다.
    const response = await api("/api/content", { method: "POST", body: { type: "PRINCIPLE", title: "먼저 듣는다" } });

    assert.equal(response.status, 201);
  });

  it("읽음으로 세지 않는다", async () => {
    const created = await (
      await api("/api/content", { method: "POST", body: { type: "PRINCIPLE", title: "몸이 힘들면 하루 쉰다" } })
    ).json();

    await api(`/api/content/${created.id}/toggle-confirm`, { method: "POST" });

    const stored = await (await api("/api/snapshot")).json();
    // 내가 정한 기준을 읽음으로 세면 "오늘 읽은 글귀"가 저절로 늘어난다.
    assert.equal(stored.exposureEvents.filter((event) => event.contentItemId === created.id).length, 0);
  });
});

describe("독후감", () => {
  it("책에 적어 둔 독후감이 스냅샷에 남는다", async () => {
    const book = await (
      await api("/api/books", { method: "POST", body: { title: "독후감 쓰는 책", currentPage: 10 } })
    ).json();
    const review = "첫 문단입니다.\n\n빈 줄을 사이에 둔 둘째 문단입니다.";

    await api(`/api/books/${book.id}`, { method: "PUT", body: { ...book, review } });

    const stored = await (await api("/api/snapshot")).json();
    // 가운데 빈 줄은 사용자가 문단을 나눈 자리다. 다듬으면 글의 모양이 달라진다.
    assert.equal(stored.books.find((one) => one.syncId === book.syncId).review, review);
  });

  it("독후감을 싣지 않은 수정은 써 둔 글을 지우지 않는다", async () => {
    // 앱의 책 수정 폼은 독후감을 싣지 않는다. 여기서 비우면 제목만 고쳐도 글이 사라진다.
    const book = await (
      await api("/api/books", { method: "POST", body: { title: "고칠 책", review: "읽고 남긴 글" } })
    ).json();

    await api(`/api/books/${book.id}`, { method: "PUT", body: { title: "고친 책" } });

    const stored = await (await api("/api/snapshot")).json();
    const saved = stored.books.find((one) => one.syncId === book.syncId);
    assert.equal(saved.title, "고친 책");
    assert.equal(saved.review, "읽고 남긴 글");
  });

  it("독후감이 달라도 같은 책은 하나로 합친다", async () => {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          books: [
            { id: 1, syncId: "book-a", updatedAt: 1_000, title: "같은 책", author: "같은 저자", review: "먼저 쓴 글", createdAt: 1_000 },
            { id: 2, syncId: "book-b", updatedAt: 2_000, title: "같은 책", author: "같은 저자", review: "나중에 쓴 글", createdAt: 2_000 },
          ],
        }),
      })
    ).json();

    // 합치는 기준은 제목·저자다. 독후감까지 견주면 같은 책이 두 벌 남는다.
    assert.equal(merged.books.length, 1);
    assert.equal(merged.books[0].review, "나중에 쓴 글");
  });
});

describe("날짜 없는 할 일", () => {
  it("마감일을 비워 둔 할 일도 서버에 남는다", async () => {
    // 여기서 버리면 기기에서 만든 "언젠가" 할 일이 첫 업로드에 조용히 사라진다.
    await api("/api/snapshot", {
      method: "PUT",
      body: emptySnapshot({
        todos: [
          { id: 1, syncId: "todo-1", updatedAt: 10, title: "언젠가 배우기", note: "", dueDate: "", createdAt: 10 },
        ],
      }),
    });

    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.todos.length, 1);
    assert.equal(stored.todos[0].dueDate, "");
    assert.equal(stored.todos[0].title, "언젠가 배우기");
  });

  it("제목이 없으면 여전히 버린다", async () => {
    await api("/api/snapshot", {
      method: "PUT",
      body: emptySnapshot({
        todos: [{ id: 1, syncId: "todo-1", updatedAt: 10, title: "  ", dueDate: "2026-08-26", createdAt: 10 }],
      }),
    });

    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.todos.length, 0);
  });
});

describe("글귀 메모와 루틴 뽑기", () => {
  async function resetServer() {
    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
  }

  async function createQuote(title = "오늘의 기준", body = "행동은 감정을 기다리지 않는다.") {
    return (await api("/api/content", { method: "POST", body: { type: "QUOTE", title, body } })).json();
  }

  function quote(syncId, title, body, updatedAt = 1000) {
    return { syncId, updatedAt, type: "QUOTE", title, body, createdAt: updatedAt };
  }

  it("글귀에 메모를 달면 부모를 syncId로 가리킨다", async () => {
    await resetServer();
    const item = await createQuote();

    const response = await api(`/api/content/${item.id}/memos`, {
      method: "POST",
      body: { body: "세 번째 읽으니 다르게 들린다." },
    });
    const memo = await response.json();
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(response.status, 201);
    assert.equal(memo.body, "세 번째 읽으니 다르게 들린다.");
    assert.equal(stored.contentMemos.length, 1);
    assert.equal(stored.contentMemos[0].contentItemId, item.id);
    assert.equal(stored.contentMemos[0].contentItemSyncId, item.syncId);
    // 루틴 메모와 달리 실천으로 세지 않는다. 여기 적는 것은 생각이다.
    assert.equal(stored.routineChecks.length, 0);
  });

  it("빈 메모는 저장하지 않는다", async () => {
    await resetServer();
    const item = await createQuote();

    const response = await api(`/api/content/${item.id}/memos`, { method: "POST", body: { body: "   " } });
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(response.status, 400);
    assert.equal(stored.contentMemos.length, 0);
  });

  it("메모를 지우면 표식을 남겨 다음 병합에 되살아나지 않는다", async () => {
    await resetServer();
    const item = await createQuote();
    const memo = await (
      await api(`/api/content/${item.id}/memos`, { method: "POST", body: { body: "지울 메모" } })
    ).json();

    const deleted = await api(`/api/content-memos/${memo.id}`, { method: "DELETE" });
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(deleted.status, 200);
    assert.equal(stored.contentMemos.length, 0);
    assert.ok(stored.deletions.some((entry) => entry.entityType === "CONTENT_MEMO"));

    // 옛 사본을 들고 있던 기기가 붙어도 되살아나면 안 된다.
    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          contentMemos: [
            {
              id: 1,
              syncId: memo.syncId,
              updatedAt: memo.updatedAt,
              contentItemId: item.id,
              contentItemSyncId: item.syncId,
              contentTitle: item.title,
              body: "지울 메모",
              createdAt: memo.createdAt,
            },
          ],
        }),
      })
    ).json();

    assert.equal(merged.contentMemos.length, 0);
  });

  it("글귀를 지우면 달아 둔 메모도 표식과 함께 사라진다", async () => {
    await resetServer();
    const item = await createQuote();
    await api(`/api/content/${item.id}/memos`, { method: "POST", body: { body: "첫 메모" } });
    await api(`/api/content/${item.id}/memos`, { method: "POST", body: { body: "둘째 메모" } });

    await api(`/api/content/${item.id}`, { method: "DELETE" });
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.contentMemos.length, 0, "주인 없는 메모가 남았습니다.");
    assert.equal(stored.deletions.filter((entry) => entry.entityType === "CONTENT_MEMO").length, 2);
  });

  it("업로드가 서버의 글귀 메모까지 갈아엎는다", async () => {
    // 여기서 빠뜨리면 그 종류만 서버에 남아, 사용자가 지운 메모가 다음 병합에 되살아난다.
    await resetServer();
    const item = await createQuote();
    await api(`/api/content/${item.id}/memos`, { method: "POST", body: { body: "지워질 메모" } });

    await api("/api/snapshot", { method: "PUT", body: emptySnapshot() });
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.contentMemos.length, 0);
  });

  it("붙을 글귀가 없는 메모는 버린다", async () => {
    await resetServer();

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          contentMemos: [
            {
              id: 1,
              syncId: "떠도는-메모",
              updatedAt: 1000,
              contentItemId: 7,
              contentItemSyncId: "사라진-글귀",
              contentTitle: "제목",
              body: "메모",
              createdAt: 1000,
            },
          ],
        }),
      })
    ).json();

    assert.equal(merged.contentMemos.length, 0);
  });

  it("사라진 쪽을 가리키던 메모는 남은 글귀로 옮겨 붙는다", async () => {
    await resetServer();
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("server-1", "제목", "본문", 2000)] }) });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          items: [quote("app-1", "제목", "본문", 1000)],
          contentMemos: [
            {
              id: 1,
              syncId: "memo-1",
              updatedAt: 1000,
              contentItemId: 1,
              contentItemSyncId: "app-1",
              contentTitle: "제목",
              body: "메모",
              createdAt: 1000,
            },
          ],
        }),
      })
    ).json();

    const survivor = merged.items.find((entry) => entry.syncId === "server-1");
    assert.ok(survivor, "최근에 손댄 쪽이 남아야 합니다.");
    assert.equal(merged.contentMemos[0].contentItemSyncId, "server-1");
    assert.equal(merged.contentMemos[0].contentItemId, survivor.id);
  });

  it("글귀에서 루틴을 뽑으면 출처와 본문이 함께 남는다", async () => {
    await resetServer();
    const item = await createQuote();

    const response = await api(`/api/content/${item.id}/routines`, { method: "POST", body: {} });
    const routine = await response.json();

    assert.equal(response.status, 201);
    // 이름은 글귀에서 뽑고, 본문은 루틴 메모로 옮긴다. 앱의 extractRoutineFromContent와 같아야 한다.
    assert.equal(routine.title, "오늘의 기준");
    assert.equal(routine.note, "행동은 감정을 기다리지 않는다.");
    assert.equal(routine.category, "글귀에서");
    assert.equal(routine.sourceContentSyncId, item.syncId);
  });

  it("뽑을 때 이름을 직접 줄 수 있다", async () => {
    await resetServer();
    const item = await createQuote();

    const routine = await (
      await api(`/api/content/${item.id}/routines`, {
        method: "POST",
        body: { title: "아침에 한 가지 먼저 하기" },
      })
    ).json();

    assert.equal(routine.title, "아침에 한 가지 먼저 하기");
    assert.equal(routine.sourceContentSyncId, item.syncId);
  });

  it("뽑아낸 루틴은 하루의 맨 뒤에 붙는다", async () => {
    await resetServer();
    await api("/api/routines", { method: "POST", body: { title: "이미 밟고 있는 것" } });
    const item = await createQuote();

    await api(`/api/content/${item.id}/routines`, { method: "POST", body: {} });
    const listed = await (await api("/api/routines")).json();

    assert.deepEqual(listed.routines.map((routine) => routine.title), ["이미 밟고 있는 것", "오늘의 기준"]);
  });

  it("글귀를 지워도 뽑아낸 루틴은 남는다", async () => {
    // 밟기로 한 것은 그 글귀와 별개로 이미 내 것이다.
    await resetServer();
    const item = await createQuote();
    await api(`/api/content/${item.id}/routines`, { method: "POST", body: {} });

    await api(`/api/content/${item.id}`, { method: "DELETE" });
    const stored = await (await api("/api/snapshot")).json();

    assert.equal(stored.items.length, 0);
    assert.equal(stored.routines.length, 1);
  });

  it("사라진 쪽을 가리키던 루틴의 출처도 남은 글귀로 옮겨 붙는다", async () => {
    await resetServer();
    await api("/api/sync", { method: "POST", body: emptySnapshot({ items: [quote("server-1", "제목", "본문", 2000)] }) });

    const merged = await (
      await api("/api/sync", {
        method: "POST",
        body: emptySnapshot({
          items: [quote("app-1", "제목", "본문", 1000)],
          routines: [
            {
              id: 1,
              syncId: "routine-1",
              updatedAt: 1000,
              title: "물 마시기",
              note: "",
              category: "글귀에서",
              orderIndex: 0,
              sourceContentSyncId: "app-1",
              reminderEnabled: true,
              createdAt: 1000,
            },
          ],
        }),
      })
    ).json();

    assert.equal(merged.routines[0].sourceContentSyncId, "server-1");
  });

  it("직접 만든 루틴의 출처는 빈 채로 둔다", async () => {
    await resetServer();
    const routine = await (await api("/api/routines", { method: "POST", body: { title: "손으로 만든 것" } })).json();

    assert.equal(routine.sourceContentSyncId, "");
  });
});
