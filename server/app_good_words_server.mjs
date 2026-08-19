#!/usr/bin/env node
import { createHash, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { mkdir, readFile, readdir, rename, stat, writeFile } from "node:fs/promises";
import { createReadStream } from "node:fs";
import { dirname, extname, join, normalize, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const appName = "오늘의 글귀";
const schemaVersion = 13;
/** 이 기간보다 오래 꺼져 있던 기기가 다시 붙으면, 그 사이 지운 항목이 되살아날 수 있다. */
const deletionRetentionDays = 90;
/**
 * 일기 물음의 답을 이어 붙일 때 쓰는 구분자.
 * 답에는 쉼표도 줄바꿈도 들어가므로 글자가 아닌 것을 쓴다.
 * 앱 `SyncDeduplicator.ANSWER_SEPARATOR`와 같은 값이어야 지문이 어긋나지 않는다.
 */
const answerSeparator = "\u001F";
const here = dirname(fileURLToPath(import.meta.url));
const webRoot = join(here, "web");
const defaultDbPath = join(here, "app-good-words.db.json");

/**
 * 첨부 파일 규칙.
 *
 * 파일은 내용 해시로 이름 짓습니다. 같은 사진을 여러 번 올려도 한 벌만 남고, 한 번 올라간 파일은
 * 내용이 바뀌지 않아 캐시가 안전합니다. 기기와 웹이 가리키는 주소는
 * `appgoodwords://attachment/{sha256}.{확장자}`이고, 실제 파일은 DB 옆 `attachments/`에 있습니다.
 *
 * 사진·동영상·소리만 받습니다. 아무 파일이나 받으면 서버가 파일 창고가 되고,
 * 브라우저에서 열리는 형식(SVG·HTML)은 스크립트를 품을 수 있어 막습니다.
 */
const attachmentScheme = "appgoodwords://attachment/";
const attachmentIdPattern = /^[a-f0-9]{64}\.[a-z0-9]{1,5}$/;
const attachmentMaxBytes = Number(process.env.APP_GOOD_WORDS_MAX_UPLOAD || 100 * 1024 * 1024);
const attachmentExtensions = new Map([
  ["image/jpeg", "jpg"],
  ["image/png", "png"],
  ["image/gif", "gif"],
  ["image/webp", "webp"],
  ["image/heic", "heic"],
  ["image/heif", "heif"],
  ["video/mp4", "mp4"],
  ["video/webm", "webm"],
  ["video/quicktime", "mov"],
  ["video/3gpp", "3gp"],
  ["audio/mpeg", "mp3"],
  ["audio/mp4", "m4a"],
  ["audio/aac", "aac"],
  ["audio/ogg", "ogg"],
  ["audio/wav", "wav"],
  ["audio/x-wav", "wav"],
  ["audio/webm", "weba"],
  ["audio/amr", "amr"],
]);
// 확장자 하나에 형식이 여럿인 경우(wav)에는 먼저 적은 것을 씁니다.
const attachmentMimes = new Map();
for (const [mime, ext] of attachmentExtensions) {
  if (!attachmentMimes.has(ext)) attachmentMimes.set(ext, mime);
}

const contentTypes = new Set(["QUOTE", "LINK", "VIDEO"]);
const eventTypes = new Set(["SURFACED", "SHOWN", "CONFIRMED"]);
const triggers = new Set([
  "APP_LAUNCH",
  "MANUAL_REFRESH",
  "REMINDER_NOTIFICATION",
  "DETAIL_OPEN",
  "NOTIFICATION_TAP",
  "DETAIL_CHECK",
  "TEST_NOTIFICATION",
  "WIDGET_REFRESH",
]);
const deletionEntityTypes = new Set([
  "CONTENT_ITEM",
  "EXPOSURE_EVENT",
  "ROUTINE",
  "ROUTINE_CHECK",
  "ROUTINE_MEMO",
  "DIARY",
  "TODO",
  "BOOK",
]);

const bookStatuses = new Set(["READING", "FINISHED"]);
/** 책에서 뽑은 글귀에 붙는 카테고리. 앱 AppRepository.BOOK_CATEGORY와 같아야 한다. */
const bookCategory = "독서";

const defaultSettings = {
  remindersEnabled: true,
  intervalMinutes: 360,
  preferredHour: 9,
  preferredMinute: 0,
  repeatEndHour: 22,
  repeatEndMinute: 0,
  categoryFilter: "",
  showOnLaunch: true,
  lockScreenVisible: true,
  notificationSoundEnabled: true,
  dailySummaryEnabled: true,
  summaryHour: 21,
  summaryMinute: 0,
};

const config = parseArgs(process.argv.slice(2));
let writeQueue = Promise.resolve();

createServer((request, response) => {
  route(request, response).catch((error) => {
    const statusCode = error instanceof HttpError ? error.statusCode : 500;
    sendJson(response, statusCode, { error: error.message || "서버 오류가 발생했습니다." });
  });
}).listen(config.port, config.host, async () => {
  if (config.seed) {
    await withDb((db) => {
      if (db.items.length > 0) return { seeded: false };
      const item = saveContent(db, {
        type: "QUOTE",
        title: "시작",
        body: "작게 남긴 문장이 하루의 방향을 바꿀 수 있습니다.",
        author: "AppGoodWords",
        category: "동기부여",
        tags: ["시작", "기록"],
        isFavorite: true,
      });
      saveContent(db, {
        type: "LINK",
        title: "Atomic Habits Summary",
        body: "작은 습관을 시스템으로 만드는 방법",
        author: "James Clear",
        sourceUrl: "https://jamesclear.com/atomic-habits",
        category: "습관",
        tags: ["습관", "시스템"],
      });
      saveRoutine(db, {
        title: "오늘 읽은 글귀 정리",
        note: "읽음으로 표시한 항목 중 하나를 메모로 남깁니다.",
        category: "기록",
        reminderEnabled: true,
      });
      recordContentEvent(db, item.id, "SURFACED", "MANUAL_REFRESH", false);
      return { seeded: true };
    });
  }
  console.log(`AppGoodWords server running at http://${config.host}:${config.port}`);
  console.log(`DB file: ${config.dbPath}`);
  console.log(`Attachments: ${config.attachmentsDir}`);
  if (config.apiKey) console.log("API key protection is enabled.");
});

async function route(request, response) {
  const url = new URL(request.url, `http://${request.headers.host || "localhost"}`);
  if (request.method === "OPTIONS") {
    sendNoContent(response);
    return;
  }
  if (!url.pathname.startsWith("/api")) {
    await serveStatic(response, url.pathname);
    return;
  }
  if (!isAuthorized(request, url.pathname)) {
    sendJson(response, 401, { error: "API key가 필요합니다." });
    return;
  }

  const parts = url.pathname.split("/").filter(Boolean);
  const method = request.method || "GET";

  if (method === "GET" && url.pathname === "/api/health") {
    sendJson(response, 200, { ok: true, schemaVersion });
    return;
  }
  if (method === "GET" && url.pathname === "/api/snapshot") {
    sendJson(
      response,
      200,
      snapshot(await loadDb(), url.searchParams.get("since"), url.searchParams.get("epoch"))
    );
    return;
  }
  if (method === "PUT" && url.pathname === "/api/snapshot") {
    const payload = await readJson(request);
    const saved = await withDb((db) => replaceSnapshot(db, payload));
    sendJson(response, 200, snapshot(saved));
    return;
  }
  // 교체가 아니라 합치기. 여러 기기에서 각각 편집해도 한쪽이 사라지지 않는다.
  if (method === "POST" && url.pathname === "/api/sync") {
    const payload = await readJson(request);
    // `since`를 주면 그 뒤에 바뀐 것만 돌려줍니다. 보내는 쪽도 바뀐 것만 보내면 됩니다.
    const since = url.searchParams.get("since") ?? payload.since;
    const epoch = url.searchParams.get("epoch") ?? payload.epoch;
    const merged = await withDb((db) => mergeSnapshot(db, payload));
    sendJson(response, 200, snapshot(merged, since, epoch ?? null));
    return;
  }
  if (method === "GET" && url.pathname === "/api/content") {
    sendJson(response, 200, { items: sortDesc((await loadDb()).items, "createdAt") });
    return;
  }
  if (method === "POST" && url.pathname === "/api/content") {
    const payload = await readJson(request);
    const item = await withDb((db) => saveContent(db, payload));
    sendJson(response, 201, item);
    return;
  }
  if (method === "GET" && url.pathname === "/api/events") {
    sendJson(response, 200, { events: sortDesc((await loadDb()).exposureEvents, "occurredAt") });
    return;
  }
  if (method === "DELETE" && url.pathname === "/api/events") {
    const ids = parseIds(url.searchParams.get("ids"));
    const result = await withDb((db) => deleteWithTombstone(db, db.exposureEvents, ids, "EXPOSURE_EVENT"));
    sendJson(response, 200, { deleted: result.deleted });
    return;
  }
  if (method === "GET" && url.pathname === "/api/routines") {
    sendJson(response, 200, { routines: sortDesc((await loadDb()).routines, "createdAt") });
    return;
  }
  if (method === "POST" && url.pathname === "/api/routines") {
    const payload = await readJson(request);
    const routine = await withDb((db) => saveRoutine(db, payload));
    sendJson(response, 201, routine);
    return;
  }
  if (method === "GET" && url.pathname === "/api/diaries") {
    sendJson(response, 200, { diaries: sortDiaries((await loadDb()).diaries) });
    return;
  }
  if (method === "POST" && url.pathname === "/api/diaries") {
    const payload = await readJson(request);
    const diary = await withDb((db) => saveDiary(db, payload));
    sendJson(response, 201, diary);
    return;
  }
  if (method === "GET" && url.pathname === "/api/todos") {
    sendJson(response, 200, { todos: sortTodos((await loadDb()).todos) });
    return;
  }
  if (method === "POST" && url.pathname === "/api/todos") {
    const payload = await readJson(request);
    const todo = await withDb((db) => saveTodo(db, payload));
    sendJson(response, 201, todo);
    return;
  }
  if (method === "GET" && url.pathname === "/api/books") {
    sendJson(response, 200, { books: sortDesc((await loadDb()).books, "updatedAt") });
    return;
  }
  if (method === "POST" && url.pathname === "/api/books") {
    const payload = await readJson(request);
    const book = await withDb((db) => saveBook(db, payload));
    sendJson(response, 201, book);
    return;
  }
  if (method === "POST" && url.pathname === "/api/attachments") {
    sendJson(response, 201, await saveAttachment(request));
    return;
  }
  if (method === "GET" && url.pathname === "/api/attachments") {
    sendJson(response, 200, await attachmentUsage(await loadDb()));
    return;
  }
  if (method === "GET" && url.pathname === "/api/summary/today") {
    sendJson(response, 200, todaySummary(await loadDb()));
    return;
  }
  if (method === "GET" && url.pathname === "/api/categories") {
    const categories = [...new Set((await loadDb()).items.map((item) => item.category).filter(Boolean))].sort();
    sendJson(response, 200, { categories });
    return;
  }

  if (parts[0] === "api" && parts[1] === "content" && parts[2]) {
    await routeContentMember(method, parts, request, response);
    return;
  }
  if (parts[0] === "api" && parts[1] === "routines" && parts[2]) {
    await routeRoutineMember(method, parts, request, response);
    return;
  }
  if (parts[0] === "api" && parts[1] === "routine-memos" && parts[2]) {
    await routeMemoMember(method, parts, response);
    return;
  }
  if (parts[0] === "api" && parts[1] === "diaries" && parts[2]) {
    await routeDiaryMember(method, parts, request, response);
    return;
  }
  if (parts[0] === "api" && parts[1] === "todos" && parts[2]) {
    await routeTodoMember(method, parts, request, response);
    return;
  }
  if (parts[0] === "api" && parts[1] === "books" && parts[2]) {
    await routeBookMember(method, parts, request, response);
    return;
  }
  if (parts[0] === "api" && parts[1] === "attachments" && parts[2]) {
    await sendAttachment(method, parts[2], response);
    return;
  }

  sendJson(response, 404, { error: "엔드포인트를 찾을 수 없습니다." });
}

async function routeContentMember(method, parts, request, response) {
  const itemId = Number(parts[2]);
  const action = parts[3] || "";
  if (!Number.isFinite(itemId) || itemId <= 0) {
    sendJson(response, 400, { error: "항목 ID가 올바르지 않습니다." });
    return;
  }

  if (method === "GET" && !action) {
    const item = (await loadDb()).items.find((candidate) => candidate.id === itemId);
    sendJson(response, item ? 200 : 404, item || { error: "항목을 찾을 수 없습니다." });
    return;
  }
  if (method === "PUT" && !action) {
    const payload = await readJson(request);
    const item = await withDb((db) => saveContent(db, payload, itemId));
    sendJson(response, 200, item);
    return;
  }
  if (method === "DELETE" && !action) {
    const result = await withDb((db) => deleteWithTombstone(db, db.items, [itemId], "CONTENT_ITEM"));
    sendJson(response, 200, { deleted: result.deleted });
    return;
  }
  if (method === "POST" && action === "favorite") {
    const payload = await readJson(request);
    const item = await withDb((db) => {
      const found = requireItem(db, itemId);
      found.isFavorite = Boolean(payload.isFavorite);
      return found;
    });
    sendJson(response, 200, item);
    return;
  }
  if (method === "POST" && action === "surface") {
    const payload = await readJson(request, true);
    const event = await withDb((db) =>
      recordContentEvent(db, itemId, "SURFACED", normalizeEnum(payload.trigger, triggers, "MANUAL_REFRESH"), false),
    );
    sendJson(response, 201, event);
    return;
  }
  if (method === "POST" && action === "view") {
    const payload = await readJson(request, true);
    const event = await withDb((db) =>
      recordContentEvent(db, itemId, "SHOWN", normalizeEnum(payload.trigger, triggers, "DETAIL_OPEN"), false),
    );
    sendJson(response, 201, event);
    return;
  }
  if (method === "POST" && action === "confirm") {
    const payload = await readJson(request, true);
    const result = await withDb((db) => ({
      confirmed: markConfirmedOnce(db, itemId, normalizeEnum(payload.trigger, triggers, "DETAIL_CHECK")),
      confirmedTodayIds: todayConfirmedIds(db),
    }));
    sendJson(response, 200, result);
    return;
  }
  if (method === "POST" && action === "toggle-confirm") {
    const payload = await readJson(request, true);
    const result = await withDb((db) => ({
      confirmed: toggleConfirmed(db, itemId, normalizeEnum(payload.trigger, triggers, "DETAIL_CHECK")),
      confirmedTodayIds: todayConfirmedIds(db),
    }));
    sendJson(response, 200, result);
    return;
  }
  sendJson(response, 404, { error: "엔드포인트를 찾을 수 없습니다." });
}

async function routeRoutineMember(method, parts, request, response) {
  const routineId = Number(parts[2]);
  const action = parts[3] || "";
  if (!Number.isFinite(routineId) || routineId <= 0) {
    sendJson(response, 400, { error: "루틴 ID가 올바르지 않습니다." });
    return;
  }

  if (method === "GET" && !action) {
    const routine = (await loadDb()).routines.find((candidate) => candidate.id === routineId);
    sendJson(response, routine ? 200 : 404, routine || { error: "루틴을 찾을 수 없습니다." });
    return;
  }
  if (method === "PUT" && !action) {
    const payload = await readJson(request);
    const routine = await withDb((db) => saveRoutine(db, payload, routineId));
    sendJson(response, 200, routine);
    return;
  }
  if (method === "DELETE" && !action) {
    const result = await withDb((db) => {
      const deleted = deleteWithTombstone(db, db.routines, [routineId], "ROUTINE").deleted;
      deleteWithTombstone(
        db,
        db.routineChecks,
        db.routineChecks.filter((check) => check.routineId === routineId).map((check) => check.id),
        "ROUTINE_CHECK",
      );
      deleteWithTombstone(
        db,
        db.routineMemos,
        db.routineMemos.filter((memo) => memo.routineId === routineId).map((memo) => memo.id),
        "ROUTINE_MEMO",
      );
      return { deleted };
    });
    sendJson(response, 200, result);
    return;
  }
  if (method === "POST" && action === "check") {
    const result = await withDb((db) => {
      const routine = requireRoutine(db, routineId);
      const check = saveRoutineCheck(db, {
        routineId: routine.id,
        routineTitle: routine.title,
        checkedAt: nowMs(),
      });
      const [start, end] = todayRange();
      const todayCount = db.routineChecks.filter(
        (candidate) => candidate.routineId === routineId && candidate.checkedAt >= start && candidate.checkedAt <= end,
      ).length;
      return { check, todayCount };
    });
    sendJson(response, 201, result);
    return;
  }
  if (method === "POST" && action === "memos") {
    const payload = await readJson(request);
    const memo = await withDb((db) => {
      const routine = requireRoutine(db, routineId);
      const savedMemo = saveRoutineMemo(db, {
        ...payload,
        routineId: routine.id,
        routineTitle: routine.title,
      });
      saveRoutineCheck(db, {
        routineId: routine.id,
        routineTitle: routine.title,
        checkedAt: nowMs(),
      });
      return savedMemo;
    });
    sendJson(response, 201, memo);
    return;
  }
  sendJson(response, 404, { error: "엔드포인트를 찾을 수 없습니다." });
}

async function routeMemoMember(method, parts, response) {
  const memoId = Number(parts[2]);
  if (method !== "DELETE") {
    sendJson(response, 405, { error: "지원하지 않는 메서드입니다." });
    return;
  }
  const result = await withDb((db) => deleteWithTombstone(db, db.routineMemos, [memoId], "ROUTINE_MEMO"));
  sendJson(response, 200, { deleted: result.deleted });
}

async function routeDiaryMember(method, parts, request, response) {
  const diaryId = Number(parts[2]);
  if (!Number.isFinite(diaryId) || diaryId <= 0) {
    sendJson(response, 400, { error: "일기 ID가 올바르지 않습니다." });
    return;
  }

  if (method === "GET") {
    const diary = (await loadDb()).diaries.find((candidate) => candidate.id === diaryId);
    sendJson(response, diary ? 200 : 404, diary || { error: "일기를 찾을 수 없습니다." });
    return;
  }
  if (method === "PUT") {
    const payload = await readJson(request);
    const diary = await withDb((db) => saveDiary(db, payload, diaryId));
    sendJson(response, 200, diary);
    return;
  }
  if (method === "DELETE") {
    const result = await withDb((db) => deleteWithTombstone(db, db.diaries, [diaryId], "DIARY"));
    sendJson(response, 200, { deleted: result.deleted });
    return;
  }
  sendJson(response, 405, { error: "지원하지 않는 메서드입니다." });
}

async function routeBookMember(method, parts, request, response) {
  const bookId = Number(parts[2]);
  const action = parts[3] || "";
  if (!Number.isFinite(bookId) || bookId <= 0) {
    sendJson(response, 400, { error: "책 ID가 올바르지 않습니다." });
    return;
  }

  if (method === "GET" && !action) {
    const book = (await loadDb()).books.find((candidate) => candidate.id === bookId);
    sendJson(response, book ? 200 : 404, book || { error: "책을 찾을 수 없습니다." });
    return;
  }
  if (method === "PUT" && !action) {
    const payload = await readJson(request);
    const book = await withDb((db) => saveBook(db, payload, bookId));
    sendJson(response, 200, book);
    return;
  }
  if (method === "DELETE" && !action) {
    // 뽑아 둔 글귀는 남긴다. 책을 정리했다고 밑줄 그은 문장까지 사라지면 안 된다.
    const result = await withDb((db) => deleteWithTombstone(db, db.books, [bookId], "BOOK"));
    sendJson(response, 200, { deleted: result.deleted });
    return;
  }
  if (method === "POST" && action === "quotes") {
    const payload = await readJson(request);
    const quote = await withDb((db) => extractQuoteFromBook(db, bookId, payload));
    sendJson(response, 201, quote);
    return;
  }
  sendJson(response, 404, { error: "엔드포인트를 찾을 수 없습니다." });
}

async function routeTodoMember(method, parts, request, response) {
  const todoId = Number(parts[2]);
  const action = parts[3] || "";
  if (!Number.isFinite(todoId) || todoId <= 0) {
    sendJson(response, 400, { error: "할 일 ID가 올바르지 않습니다." });
    return;
  }

  if (method === "GET" && !action) {
    const todo = (await loadDb()).todos.find((candidate) => candidate.id === todoId);
    sendJson(response, todo ? 200 : 404, todo || { error: "할 일을 찾을 수 없습니다." });
    return;
  }
  if (method === "PUT" && !action) {
    const payload = await readJson(request);
    const todo = await withDb((db) => saveTodo(db, payload, todoId));
    sendJson(response, 200, todo);
    return;
  }
  if (method === "DELETE" && !action) {
    const result = await withDb((db) => deleteWithTombstone(db, db.todos, [todoId], "TODO"));
    sendJson(response, 200, { deleted: result.deleted });
    return;
  }
  if (method === "POST" && action === "toggle-done") {
    const todo = await withDb((db) => {
      const existing = db.todos.find((candidate) => candidate.id === todoId);
      if (!existing) throw new HttpError(404, "할 일을 찾을 수 없습니다.");
      // 완료 시각으로 켜고 끕니다. 앱도 같은 방식이라 병합할 때 최근에 누른 쪽이 남습니다.
      return saveTodo(db, { doneAt: existing.doneAt ? null : nowMs() }, todoId);
    });
    sendJson(response, 200, todo);
    return;
  }
  sendJson(response, 404, { error: "엔드포인트를 찾을 수 없습니다." });
}

/**
 * 올라온 파일을 내용 해시 이름으로 저장하고 주소를 돌려줍니다.
 *
 * 본문은 파일 그대로입니다. multipart를 쓰지 않는 이유는 표준 라이브러리만으로 파싱하려면
 * 경계 처리를 직접 짜야 하고, 어차피 한 번에 한 파일만 올리기 때문입니다.
 */
async function saveAttachment(request) {
  const mime = String(request.headers["content-type"] || "").split(";")[0].trim().toLowerCase();
  const extension = attachmentExtensions.get(mime);
  if (!extension) {
    throw new HttpError(415, `보낼 수 없는 형식입니다(${mime || "형식 없음"}). 사진·동영상·소리만 됩니다.`);
  }

  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    // 다 받고 나서 재면 그때는 이미 메모리를 다 쓴 뒤입니다.
    if (size > attachmentMaxBytes) {
      throw new HttpError(413, `파일이 너무 큽니다. ${Math.floor(attachmentMaxBytes / 1024 / 1024)}MB까지 됩니다.`);
    }
    chunks.push(chunk);
  }
  if (size === 0) throw new HttpError(400, "빈 파일입니다.");

  const body = Buffer.concat(chunks);
  const hash = createHash("sha256").update(body).digest("hex");
  const id = `${hash}.${extension}`;
  const filePath = join(config.attachmentsDir, id);

  await mkdir(config.attachmentsDir, { recursive: true });
  // 같은 파일이 이미 있으면 다시 쓰지 않습니다. 내용이 같으면 해시도 같기 때문입니다.
  const exists = await stat(filePath).then(() => true).catch(() => false);
  if (!exists) {
    const tmpPath = `${filePath}.${randomUUID()}.tmp`;
    await writeFile(tmpPath, body);
    await rename(tmpPath, filePath);
  }

  return { id, uri: `${attachmentScheme}${id}`, mime, size, reused: exists };
}

/**
 * 첨부가 디스크를 얼마나 쓰는지 알려 줍니다.
 *
 * **지우지는 않습니다.** 아직 서버에 올라오지 않은 기기가 그 파일을 가리키는 기록을 들고 있을 수
 * 있어서, 어디서도 안 쓰는 것처럼 보여도 실제로는 쓰이는 중일 수 있습니다.
 * 사용자가 보고 직접 판단하도록 숫자만 냅니다.
 */
async function attachmentUsage(db) {
  const files = await readdir(config.attachmentsDir).catch(() => []);
  const used = referencedAttachmentIds(db);
  let bytes = 0;
  let unusedCount = 0;
  let unusedBytes = 0;
  for (const name of files) {
    if (!attachmentIdPattern.test(name)) continue;
    const fileStat = await stat(join(config.attachmentsDir, name)).catch(() => null);
    if (!fileStat) continue;
    bytes += fileStat.size;
    if (!used.has(name)) {
      unusedCount += 1;
      unusedBytes += fileStat.size;
    }
  }
  return { count: files.filter((name) => attachmentIdPattern.test(name)).length, bytes, unusedCount, unusedBytes };
}

/** 어떤 기록이든 가리키고 있는 첨부 id를 모읍니다. */
function referencedAttachmentIds(db) {
  const ids = new Set();
  const collect = (uris) => {
    for (const uri of uris || []) {
      if (String(uri).startsWith(attachmentScheme)) ids.add(String(uri).slice(attachmentScheme.length));
    }
  };
  for (const item of db.items) {
    collect(item.imageUris);
    collect(item.videoUris);
  }
  for (const diary of db.diaries) {
    collect(diary.imageUris);
    collect(diary.videoUris);
    collect(diary.audioUris);
  }
  return ids;
}

async function sendAttachment(method, id, response) {
  if (method !== "GET" && method !== "HEAD") {
    sendJson(response, 405, { error: "지원하지 않는 메서드입니다." });
    return;
  }
  // 이름이 곧 경로라서, 형식을 먼저 확인하지 않으면 상위 디렉터리로 빠져나갈 수 있습니다.
  if (!attachmentIdPattern.test(id)) {
    sendJson(response, 400, { error: "첨부 주소가 올바르지 않습니다." });
    return;
  }
  const filePath = join(config.attachmentsDir, id);
  const fileStat = await stat(filePath).catch(() => null);
  if (!fileStat) {
    sendJson(response, 404, { error: "첨부를 찾을 수 없습니다." });
    return;
  }

  response.writeHead(200, {
    "Content-Type": attachmentMimes.get(extname(id).slice(1)) || "application/octet-stream",
    "Content-Length": fileStat.size,
    // 내용이 바뀌지 않는 주소라 오래 캐시해도 됩니다.
    "Cache-Control": "public, max-age=31536000, immutable",
    // 서버가 정한 형식으로만 열리게 합니다. 브라우저가 다시 추측하면 막은 보람이 없습니다.
    "X-Content-Type-Options": "nosniff",
    "Access-Control-Allow-Origin": "*",
  });
  if (method === "HEAD") {
    response.end();
    return;
  }
  createReadStream(filePath).pipe(response);
}

async function loadDb() {
  try {
    const text = await readFile(config.dbPath, "utf8");
    return normalizeDb(JSON.parse(text));
  } catch (error) {
    if (error.code === "ENOENT") return emptyDb();
    throw error;
  }
}

async function saveDb(db) {
  const normalized = normalizeDb(db);
  const tmpPath = `${config.dbPath}.tmp`;
  await writeFile(tmpPath, `${JSON.stringify(normalized, null, 2)}\n`, "utf8");
  await rename(tmpPath, config.dbPath);
}

/** 리비전이 붙는 레코드 종류. 설정은 레코드가 아니라 따로 셉니다. */
const revisionedCollections = [
  "items",
  "exposureEvents",
  "routines",
  "routineChecks",
  "routineMemos",
  "deletions",
  "diaries",
  "todos",
  "books",
];

async function withDb(mutator) {
  const run = writeQueue.catch(() => undefined).then(async () => {
    const db = await loadDb();
    // 쓰기 경로가 여럿이라 각자 번호를 매기게 하면 언젠가 빠뜨립니다. 여기서 한 번에 봅니다.
    const before = contentBySyncId(db);
    const result = mutator(db);
    stampRevisions(db, before);
    await saveDb(db);
    return result;
  });
  writeQueue = run.catch(() => undefined);
  return run;
}

/**
 * 지금 내용을 종류·syncId별로 적어 둡니다.
 *
 * `updatedAt`만 보면 부족합니다. id를 다시 매기거나 자식이 부모를 다시 잇는 것처럼
 * `updatedAt`이 그대로인데 기기가 알아야 하는 변화가 있습니다. 그래서 내용을 통째로 비교합니다.
 */
function contentBySyncId(db) {
  const map = new Map();
  for (const collection of revisionedCollections) {
    for (const record of db[collection] || []) {
      map.set(`${collection}:${record.syncId}`, contentOf(record));
    }
  }
  return map;
}

function contentOf(record) {
  const { rev, ...rest } = record;
  return JSON.stringify(rest);
}

/**
 * 바뀐 레코드에만 새 번호를 붙입니다.
 *
 * 안 바뀐 레코드가 번호를 새로 받으면 증분 동기화가 매번 전부를 보내게 되므로,
 * 내용이 정말 달라진 것만 올립니다.
 */
function stampRevisions(db, before) {
  let rev = Math.max(0, positiveInt(db.rev));
  for (const collection of revisionedCollections) {
    const records = db[collection];
    if (!Array.isArray(records)) continue;
    for (let index = 0; index < records.length; index += 1) {
      const record = records[index];
      const key = `${collection}:${record.syncId}`;
      const now = contentOf(record);
      if (before.get(key) === now && positiveInt(record.rev) > 0) continue;
      rev += 1;
      records[index] = { ...record, rev };
    }
  }
  db.rev = rev;
  return db;
}

function emptyDb() {
  return {
    appName,
    schemaVersion,
    // 바뀔 때마다 하나씩 오르는 번호. 기기는 "내가 본 번호 다음부터"만 받아 갑니다.
    rev: 0,
    // 통째로 교체할 때마다 오릅니다. 세대가 다르면 리비전 번호를 믿을 수 없어 전체를 보냅니다.
    epoch: 0,
    settings: { ...defaultSettings },
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
  };
}

function normalizeDb(db) {
  return {
    appName,
    schemaVersion,
    rev: Math.max(0, positiveInt(db?.rev)),
    epoch: Math.max(0, positiveInt(db?.epoch)),
    settings: { ...defaultSettings, ...(db?.settings || {}) },
    settingsUpdatedAt: integer(db?.settingsUpdatedAt, 0),
    items: normalizeList(db?.items, normalizeContent),
    exposureEvents: normalizeList(db?.exposureEvents, normalizeEvent),
    routines: normalizeList(db?.routines, normalizeRoutine),
    routineChecks: normalizeList(db?.routineChecks, normalizeRoutineCheck),
    routineMemos: normalizeList(db?.routineMemos, normalizeRoutineMemo),
    deletions: normalizeList(db?.deletions, normalizeDeletion),
    diaries: normalizeList(db?.diaries, normalizeDiary),
    todos: normalizeList(db?.todos, normalizeTodo),
    books: normalizeList(db?.books, normalizeBook),
  };
}

/**
 * 정리하면서 리비전 번호를 함께 지킵니다.
 *
 * `rev`는 서버가 매기는 장부라 각 normalize 함수가 알 필요가 없습니다.
 * 여기서 한 번에 옮겨 두면 종류를 새로 추가할 때 빠뜨릴 곳이 하나 줄어듭니다.
 * 기기가 보낸 레코드에는 `rev`가 없어 0이 되고, 병합 뒤에 새 번호를 받습니다.
 */
function normalizeList(records, normalize) {
  if (!Array.isArray(records)) return [];
  return records
    .map((record) => {
      const normalized = normalize(record);
      if (!normalized) return null;
      normalized.rev = Math.max(0, positiveInt(record?.rev));
      return normalized;
    })
    .filter(Boolean);
}

/**
 * 서버 상태를 내보냅니다.
 *
 * `since`를 주면 그 번호 뒤에 바뀐 레코드만 담습니다. 이력이 수천 건 쌓이면
 * 전체 스냅샷이 1MB에 가까워지는데, 대부분은 지난번과 똑같은 내용입니다.
 * 0이면 전체입니다. 처음 붙는 기기와 백업 내려받기가 이 경우입니다.
 */
function snapshot(db, since = 0, epoch = null) {
  const normalized = normalizeDb(db);
  // 서버 DB를 새로 깔면 번호가 0부터 다시 시작합니다. 그때 기기가 들고 있던 큰 번호를 그대로 믿으면
  // 아무것도 안 바뀐 것처럼 보여 영영 못 받습니다. 앞선 번호를 받으면 전체를 보냅니다.
  const asked = Math.max(0, positiveInt(since));
  // 통째로 교체된 뒤라면 예전 번호는 다른 세대의 것이라 뜻이 없습니다.
  const sameEpoch = epoch === null || epoch === undefined || Number(epoch) === normalized.epoch;
  const from = asked > normalized.rev || !sameEpoch ? 0 : asked;
  const only = (records) => (from > 0 ? records.filter((record) => positiveInt(record.rev) > from) : records);
  return {
    appName,
    schemaVersion,
    // 기기는 이 두 값을 적어 두었다가 다음번에 since·epoch로 돌려줍니다.
    rev: normalized.rev,
    epoch: normalized.epoch,
    // 부분만 담았는지 알려 줍니다. 기기는 이때 DB를 통째로 갈아엎으면 안 됩니다.
    partial: from > 0,
    since: from,
    exportedAt: new Date().toISOString(),
    itemCount: normalized.items.length,
    eventCount: normalized.exposureEvents.length,
    routineCount: normalized.routines.length,
    routineCheckCount: normalized.routineChecks.length,
    routineMemoCount: normalized.routineMemos.length,
    diaryCount: normalized.diaries.length,
    todoCount: normalized.todos.length,
    bookCount: normalized.books.length,
    settings: normalized.settings,
    settingsUpdatedAt: normalized.settingsUpdatedAt,
    items: only(sortDesc(normalized.items, "createdAt")),
    exposureEvents: only(sortDesc(normalized.exposureEvents, "occurredAt")),
    routines: only(sortDesc(normalized.routines, "createdAt")),
    routineChecks: only(sortDesc(normalized.routineChecks, "checkedAt")),
    routineMemos: only(sortDesc(normalized.routineMemos, "createdAt")),
    deletions: only(sortDesc(normalized.deletions, "deletedAt")),
    diaries: only(sortDesc(normalized.diaries, "createdAt")),
    todos: only(sortDesc(normalized.todos, "createdAt")),
    books: only(sortDesc(normalized.books, "updatedAt")),
  };
}

/**
 * 들어온 스냅샷을 서버 DB에 레코드 단위로 합칩니다.
 *
 * 앱의 SyncMerger와 같은 규칙입니다. syncId로 짝지어 updatedAt이 최신인 쪽을 남기고,
 * 삭제 표식이 레코드의 updatedAt보다 나중이면 삭제를 유지합니다.
 * 병합을 서버에서 하는 이유는 withDb가 쓰기를 직렬화해 두 기기가 동시에 보내도 안전하기 때문입니다.
 */
function mergeSnapshot(db, payload) {
  const incoming = normalizeDb({
    settings: payload?.settings,
    settingsUpdatedAt: payload?.settingsUpdatedAt,
    items: payload?.items,
    exposureEvents: payload?.exposureEvents,
    routines: payload?.routines,
    routineChecks: payload?.routineChecks,
    routineMemos: payload?.routineMemos,
    deletions: payload?.deletions,
    diaries: payload?.diaries,
    todos: payload?.todos,
    books: payload?.books,
  });
  const current = normalizeDb(db);

  // 먼저 적용하고 나서 정리한다. 반대로 하면 늦게 도착한 표식이 한 번도 쓰이지 못하고 사라진다.
  const deletions = mergeDeletions(current.deletions, incoming.deletions);
  const deletedAt = new Map(deletions.map((entry) => [entry.syncId, entry.deletedAt]));

  db.items = mergeMutable(current.items, incoming.items, deletedAt);
  db.routines = mergeMutable(current.routines, incoming.routines, deletedAt);
  db.routineMemos = mergeMutable(current.routineMemos, incoming.routineMemos, deletedAt);
  db.exposureEvents = mergeAppendOnly(current.exposureEvents, incoming.exposureEvents, deletedAt);
  db.routineChecks = mergeAppendOnly(current.routineChecks, incoming.routineChecks, deletedAt);
  // 일기와 할 일은 고칠 수 있다. 특히 할 일의 완료 표시는 한쪽에서 눌러도 양쪽에 반영되어야 한다.
  db.diaries = mergeMutable(current.diaries, incoming.diaries, deletedAt);
  db.todos = mergeMutable(current.todos, incoming.todos, deletedAt);
  // 책도 고칠 수 있다. 읽은 쪽수는 기기마다 달라져서 나중에 넘긴 쪽이 남아야 한다.
  db.books = mergeMutable(current.books, incoming.books, deletedAt);
  db.deletions = pruneDeletions(deletions);

  if (incoming.settingsUpdatedAt > current.settingsUpdatedAt) {
    db.settings = { ...defaultSettings, ...incoming.settings };
    db.settingsUpdatedAt = incoming.settingsUpdatedAt;
  } else {
    db.settings = { ...defaultSettings, ...current.settings };
    db.settingsUpdatedAt = current.settingsUpdatedAt;
  }

  // 짝지은 뒤에 합친다. 먼저 합치면 아직 짝을 못 찾은 레코드가 남는다.
  deduplicate(db);
  reindex(db);
  return db;
}

/**
 * 같은 내용인데 syncId만 다른 레코드를 하나로 합칩니다.
 *
 * 앱과 서버는 각자 기본 글귀를 심습니다. 그 둘은 내용이 같아도 syncId가 달라서,
 * 처음 서버를 붙이면 같은 글귀가 두 벌이 됩니다.
 *
 * 사라진 쪽을 가리키던 이력·체크·메모는 남은 쪽으로 옮겨 붙입니다. 옮기지 않으면 부모를 잃습니다.
 * 이력과 체크 자체는 합치지 않습니다. 같은 글귀를 두 번 본 것은 진짜로 두 번 본 것입니다.
 *
 * 앱의 SyncDeduplicator와 규칙이 같아야 합니다. 한쪽만 바꾸면 병합할 때마다 결과가 달라집니다.
 */
function deduplicate(db) {
  const items = resolveDuplicates(db.items, itemFingerprint);
  const routines = resolveDuplicates(db.routines, routineFingerprint);
  const diaries = resolveDuplicates(db.diaries, diaryFingerprint);
  const todos = resolveDuplicates(db.todos, todoFingerprint);
  const books = resolveDuplicates(db.books, bookFingerprint);

  // 사라진 책을 가리키던 글귀는 남은 책으로 옮겨 붙인다. 안 옮기면 출처를 잃는다.
  db.items = items.kept.map((item) =>
    item.bookSyncId ? { ...item, bookSyncId: books.movedTo.get(item.bookSyncId) || item.bookSyncId } : item,
  );
  db.routines = routines.kept;
  db.diaries = diaries.kept;
  db.todos = todos.kept;
  db.books = books.kept;

  // 합쳐서 사라진 쪽에 삭제 표식을 남긴다.
  // 전체를 주고받을 때는 결과만 보면 됐지만, 바뀐 것만 받는 기기는 사라졌다는 사실을 따로 들어야 한다.
  const collapsedAt = nowMs();
  for (const [gone, entityType] of [
    [items.movedTo, "CONTENT_ITEM"],
    [routines.movedTo, "ROUTINE"],
    [diaries.movedTo, "DIARY"],
    [todos.movedTo, "TODO"],
    [books.movedTo, "BOOK"],
  ]) {
    for (const syncId of gone.keys()) {
      if (db.deletions.some((entry) => entry.syncId === syncId)) continue;
      db.deletions.push({ syncId, entityType, deletedAt: collapsedAt });
    }
  }

  db.exposureEvents = db.exposureEvents.map((event) => ({
    ...event,
    contentItemSyncId: items.movedTo.get(event.contentItemSyncId) || event.contentItemSyncId,
  }));
  db.routineChecks = db.routineChecks.map((check) => ({
    ...check,
    routineSyncId: routines.movedTo.get(check.routineSyncId) || check.routineSyncId,
  }));
  db.routineMemos = db.routineMemos.map((memo) => ({
    ...memo,
    routineSyncId: routines.movedTo.get(memo.routineSyncId) || memo.routineSyncId,
  }));

  return db;
}

/**
 * 내용이 같은 것끼리 묶어 최근에 손댄 쪽을 남깁니다.
 * 같은 시각이면 syncId 순으로 정해, 앱에서 돌려도 서버에서 돌려도 결과가 같게 합니다.
 */
function resolveDuplicates(records, fingerprint) {
  const winners = new Map();
  for (const record of records) {
    // 내용이 비어 있으면 무엇과도 같아 보이므로 묶지 않는다.
    const key = fingerprint(record);
    if (!key) continue;
    const current = winners.get(key);
    if (!current || record.updatedAt > current.updatedAt) {
      winners.set(key, record);
    } else if (record.updatedAt === current.updatedAt && record.syncId > current.syncId) {
      winners.set(key, record);
    }
  }

  const movedTo = new Map();
  const kept = records.filter((record) => {
    const winner = winners.get(fingerprint(record));
    if (!winner || winner.syncId === record.syncId) return true;
    movedTo.set(record.syncId, winner.syncId);
    return false;
  });

  return { kept, movedTo };
}

// 글만 같고 첨부가 다르면 다른 기록이다. 첨부까지 넣지 않으면
// 글 없이 사진만 올린 두 기록이 하나로 합쳐지면서 한쪽 사진이 사라진다.
function itemFingerprint(item) {
  return [
    item.type,
    norm(item.title),
    norm(item.body),
    norm(item.author),
    norm(item.sourceUrl),
    item.imageUris.join(","),
    item.videoUris.join(","),
  ].join("|");
}

function routineFingerprint(routine) {
  return norm(routine.title);
}

// 날씨와 기분도 함께 본다. 앱의 diaryFingerprint와 같은 항목이어야 한다.
// 종류와 답도 같은 이유로 본다. 같은 날 쓴 감사 일기와 반성 일기는 서로 다른 기록이다.
function diaryFingerprint(diary) {
  return [
    text(diary.entryDate),
    norm(diary.title),
    norm(diary.body),
    text(diary.weather),
    text(diary.mood),
    diary.kind,
    // 답에는 쉼표도 줄바꿈도 들어가므로 글자가 아닌 구분자로 잇는다.
    // 앱 SyncDeduplicator.ANSWER_SEPARATOR와 같은 값이어야 한다.
    diary.answers.map(norm).join(answerSeparator),
    diary.imageUris.join(","),
    diary.videoUris.join(","),
    diary.audioUris.join(","),
  ].join("|");
}

function todoFingerprint(todo) {
  return [text(todo.dueDate), norm(todo.title), norm(todo.note)].join("|");
}

/**
 * 같은 책은 제목과 저자로 본다.
 *
 * 읽은 쪽수는 넣지 않는다. 두 기기에서 같은 책을 각자 담으면 진도가 다른 것이 당연한데,
 * 쪽수까지 보면 서로 다른 책이 되어 목록에 같은 책이 두 벌 남는다. 앱의 bookFingerprint와 같아야 한다.
 */
function bookFingerprint(book) {
  return [norm(book.title), norm(book.author)].join("|");
}

/** 띄어쓰기와 대소문자만 다른 것도 같은 내용으로 본다. */
function norm(value) {
  return text(value).toLowerCase().replace(/\s+/g, " ");
}

/**
 * 겹치는 숫자 id만 다시 매깁니다.
 *
 * 병합 결과에는 서로 다른 기기에서 온 같은 숫자 id가 함께 들어옵니다.
 * 그대로 두면 /api/content/{id} 같은 경로가 둘 중 아무거나 집게 되고,
 * 이벤트도 엉뚱한 항목에 붙습니다.
 *
 * **멀쩡한 id는 그대로 둡니다.** 예전에는 1부터 전부 다시 매겼는데, 그러면 한 건만 추가돼도
 * 뒤쪽 번호가 모두 밀려 전 레코드가 "바뀐 것"이 됩니다. 바뀐 것만 주고받으려면
 * 안 바뀐 레코드는 정말로 안 바뀌어야 합니다.
 */
function reindex(db) {
  // 9 이전 레코드는 부모를 숫자 id로만 가리킨다. 번호를 바꾸기 전에 syncId로 옮겨 둔다.
  const itemSyncIdByOldId = oldIdToSyncId(db.items);
  const routineSyncIdByOldId = oldIdToSyncId(db.routines);
  const parentOf = (child, ownField, oldIdField, byOldId) =>
    text(child[ownField]) || byOldId.get(child[oldIdField]) || "";

  const events = db.exposureEvents.map((event) => ({
    ...event,
    contentItemSyncId: parentOf(event, "contentItemSyncId", "contentItemId", itemSyncIdByOldId),
  }));
  const checks = db.routineChecks.map((check) => ({
    ...check,
    routineSyncId: parentOf(check, "routineSyncId", "routineId", routineSyncIdByOldId),
  }));
  const memos = db.routineMemos.map((memo) => ({
    ...memo,
    routineSyncId: parentOf(memo, "routineSyncId", "routineId", routineSyncIdByOldId),
  }));

  db.items = withStableIds(db.items);
  db.routines = withStableIds(db.routines);

  const itemIds = new Map(db.items.map((item) => [item.syncId, item.id]));
  const routineIds = new Map(db.routines.map((routine) => [routine.syncId, routine.id]));

  // 이력은 항목이 지워진 뒤에도 남으므로, 부모를 못 찾아도 버리지 않고 0으로 끊는다.
  db.exposureEvents = withStableIds(events).map((event) => ({
    ...event,
    contentItemId: itemIds.get(event.contentItemSyncId) || 0,
  }));
  db.routineChecks = withStableIds(checks).map((check) => ({
    ...check,
    routineId: routineIds.get(check.routineSyncId) || 0,
  }));
  // 메모는 루틴 안에서만 보이므로, 붙을 루틴이 없으면 남겨도 볼 방법이 없다.
  db.routineMemos = withStableIds(memos.filter((memo) => routineIds.has(memo.routineSyncId))).map((memo) => ({
    ...memo,
    routineId: routineIds.get(memo.routineSyncId),
  }));
  // 일기·할 일·책은 딸린 자식이 없어 번호만 보면 된다.
  // 글귀가 책을 가리키지만 숫자 id가 아니라 bookSyncId로 가리켜서 번호가 바뀌어도 그대로다.
  db.diaries = withStableIds(db.diaries);
  db.todos = withStableIds(db.todos);
  db.books = withStableIds(db.books);

  return db;
}

/**
 * 쓸 수 있는 id는 그대로 두고, 겹치거나 없는 것만 빈 번호로 채웁니다.
 *
 * 먼저 나온 쪽이 자기 번호를 지킵니다. 순서가 바뀌면 안 바뀐 레코드까지 새 번호를 받아
 * 증분 동기화가 매번 전부를 보내게 됩니다.
 */
function withStableIds(records) {
  const taken = new Set();
  const needsId = [];
  const assigned = records.map((record) => {
    const id = positiveInt(record.id);
    if (id > 0 && !taken.has(id)) {
      taken.add(id);
      return record;
    }
    needsId.push(record);
    return record;
  });

  let next = 1;
  const nextFreeId = () => {
    while (taken.has(next)) next += 1;
    taken.add(next);
    return next;
  };
  const fresh = new Map(needsId.map((record) => [record, nextFreeId()]));

  return assigned.map((record) => (fresh.has(record) ? { ...record, id: fresh.get(record) } : record));
}

/** 같은 숫자 id가 여러 번 나오면 어느 쪽인지 알 수 없으므로 아예 잇지 않는다. */
function oldIdToSyncId(records) {
  const seen = new Map();
  const ambiguous = new Set();
  for (const record of records) {
    if (seen.has(record.id)) ambiguous.add(record.id);
    seen.set(record.id, record.syncId);
  }
  for (const id of ambiguous) seen.delete(id);
  return seen;
}

function mergeMutable(current, incoming, deletedAt) {
  const merged = new Map();
  for (const record of current) merged.set(record.syncId, record);
  for (const record of incoming) {
    const existing = merged.get(record.syncId);
    // 같은 시각이면 서버에 있던 쪽을 남겨 결과가 흔들리지 않게 한다.
    if (!existing || record.updatedAt > existing.updatedAt) merged.set(record.syncId, record);
  }

  return [...merged.values()].filter((record) => {
    const removedAt = deletedAt.get(record.syncId);
    if (removedAt === undefined) return true;
    // 지운 뒤 다시 고쳤다면 그 수정이 이긴다.
    return removedAt < record.updatedAt;
  });
}

function mergeAppendOnly(current, incoming, deletedAt) {
  const merged = new Map();
  for (const record of current) merged.set(record.syncId, record);
  for (const record of incoming) {
    if (!merged.has(record.syncId)) merged.set(record.syncId, record);
  }
  return [...merged.values()].filter((record) => !deletedAt.has(record.syncId));
}

/**
 * 삭제 표식은 지운 항목이 되살아나지 않게 하려고 남기는 것이라 계속 쌓입니다.
 * 앱과 같은 기간을 써야 합니다. 한쪽만 지우면 다른 쪽이 매번 되돌려 줍니다.
 * (앱: SyncCoordinator.DELETION_RETENTION_DAYS)
 */
function pruneDeletions(deletions) {
  const before = nowMs() - deletionRetentionDays * 24 * 60 * 60 * 1000;
  return deletions.filter((entry) => entry.deletedAt >= before);
}

function mergeDeletions(current, incoming) {
  const merged = new Map();
  for (const entry of [...current, ...incoming]) {
    const existing = merged.get(entry.syncId);
    if (!existing || entry.deletedAt > existing.deletedAt) merged.set(entry.syncId, entry);
  }
  return [...merged.values()];
}

function replaceSnapshot(db, payload) {
  // 세대를 넘긴다. 바뀐 것만 받던 기기는 세대가 다르면 전체를 다시 받는다.
  //
  // 사라진 레코드마다 삭제 표식을 남기는 방법도 있지만 그렇게 하지 않는다.
  // 업로드는 이미 "통째로 교체"라고 경고하는 동작인데, 표식까지 남기면 실수로 빠뜨린 기록을
  // 다른 기기에서 되살릴 길까지 없어진다. 세대만 넘기면 다른 기기는 서버 상태를 그대로 받아
  // 같은 결과에 이르면서도, 표식이 없으니 그 기기의 기록은 다음 병합에서 서버로 올라간다.
  db.epoch = Math.max(0, positiveInt(db.epoch)) + 1;

  db.settings = { ...defaultSettings, ...(payload.settings || {}) };
  db.settingsUpdatedAt = integer(payload.settingsUpdatedAt, nowMs());
  db.deletions = Array.isArray(payload.deletions) ? payload.deletions.map(normalizeDeletion).filter(Boolean) : [];
  db.items = Array.isArray(payload.items) ? payload.items.map(normalizeContent).filter(Boolean) : [];
  db.exposureEvents = Array.isArray(payload.exposureEvents)
    ? payload.exposureEvents.map(normalizeEvent).filter(Boolean)
    : [];
  db.routines = Array.isArray(payload.routines) ? payload.routines.map(normalizeRoutine).filter(Boolean) : [];
  db.routineChecks = Array.isArray(payload.routineChecks)
    ? payload.routineChecks.map(normalizeRoutineCheck).filter(Boolean)
    : [];
  db.routineMemos = Array.isArray(payload.routineMemos)
    ? payload.routineMemos.map(normalizeRoutineMemo).filter(Boolean)
    : [];
  // 업로드는 서버를 기기 데이터로 통째로 바꾸는 동작이다.
  // 여기서 빠뜨리면 그 종류만 서버에 남아, 사용자가 지운 일기가 다음 병합에 되살아난다.
  db.diaries = Array.isArray(payload.diaries) ? payload.diaries.map(normalizeDiary).filter(Boolean) : [];
  db.todos = Array.isArray(payload.todos) ? payload.todos.map(normalizeTodo).filter(Boolean) : [];
  db.books = Array.isArray(payload.books) ? payload.books.map(normalizeBook).filter(Boolean) : [];
  return db;
}

function saveContent(db, payload, itemId = null) {
  const existing = itemId ? db.items.find((item) => item.id === itemId) : null;
  const normalized = normalizeContent({
    ...existing,
    ...payload,
    id: itemId || payload.id || nextId(db.items),
    // 웹에서 고쳤다는 사실을 병합이 알아야 합니다. 안 올리면 기기의 옛 사본이 이겨 되돌아갑니다.
    updatedAt: nowMs(),
    createdAt: payload.createdAt || existing?.createdAt || nowMs(),
    lastShownAt: Object.hasOwn(payload, "lastShownAt") ? payload.lastShownAt : existing?.lastShownAt,
    showCount: Object.hasOwn(payload, "showCount") ? payload.showCount : existing?.showCount || 0,
    isFavorite: Object.hasOwn(payload, "isFavorite") ? payload.isFavorite : existing?.isFavorite || false,
  });
  if (!normalized.body && !normalized.sourceUrl && normalized.imageUris.length === 0 && normalized.videoUris.length === 0) {
    throw new HttpError(400, "본문, 링크, 사진, 영상 중 하나는 필요합니다.");
  }
  if (!normalized.title) {
    normalized.title = (normalized.body || normalized.sourceUrl || "새 게시글").slice(0, 24);
  }
  upsert(db.items, normalized);
  return normalized;
}

function saveRoutine(db, payload, routineId = null) {
  const existing = routineId ? db.routines.find((routine) => routine.id === routineId) : null;
  const normalized = normalizeRoutine({
    ...existing,
    ...payload,
    id: routineId || payload.id || nextId(db.routines),
    updatedAt: nowMs(),
    createdAt: payload.createdAt || existing?.createdAt || nowMs(),
  });
  if (!normalized.title) throw new HttpError(400, "루틴 이름을 입력해 주세요.");
  upsert(db.routines, normalized);
  return normalized;
}

/**
 * 웹에서 만든 일기를 저장합니다.
 *
 * `updatedAt`을 반드시 지금으로 올립니다. 그대로 두면 기기에 있는 옛 사본이 병합에서 이겨
 * 웹에서 고친 내용이 조용히 되돌아갑니다.
 */
function saveDiary(db, payload, diaryId = null) {
  const existing = diaryId ? db.diaries.find((diary) => diary.id === diaryId) : null;
  const normalized = normalizeDiary({
    ...existing,
    ...payload,
    id: diaryId || payload.id || nextId(db.diaries),
    updatedAt: nowMs(),
    createdAt: payload.createdAt || existing?.createdAt || nowMs(),
  });
  if (!normalized) throw new HttpError(400, "날짜를 입력해 주세요.");
  // 사진만 올리는 날도 있어서 본문만 보고 판단하지 않습니다. 앱의 hasSomethingToSave와 같은 기준입니다.
  const hasSomething =
    normalized.title ||
    normalized.body ||
    normalized.weather ||
    normalized.mood ||
    normalized.answers.some(Boolean) ||
    normalized.imageUris.length ||
    normalized.videoUris.length ||
    normalized.audioUris.length;
  if (!hasSomething) throw new HttpError(400, "내용이나 날씨·기분 중 하나는 필요합니다.");
  upsert(db.diaries, normalized);
  return normalized;
}

/** 할 일도 같은 이유로 `updatedAt`을 지금으로 올립니다. */
function saveTodo(db, payload, todoId = null) {
  const existing = todoId ? db.todos.find((todo) => todo.id === todoId) : null;
  const merged = {
    ...existing,
    ...payload,
    id: todoId || payload.id || nextId(db.todos),
    updatedAt: nowMs(),
    createdAt: payload.createdAt || existing?.createdAt || nowMs(),
  };
  // normalizeTodo는 둘 중 하나만 없어도 null을 돌려주므로, 무엇이 빠졌는지 먼저 알려 줍니다.
  if (!text(merged.title)) throw new HttpError(400, "할 일 이름을 입력해 주세요.");
  if (!text(merged.dueDate)) throw new HttpError(400, "마감 날짜를 입력해 주세요.");
  const normalized = normalizeTodo(merged);
  upsert(db.todos, normalized);
  return normalized;
}

/** 책도 같은 이유로 `updatedAt`을 지금으로 올립니다. */
function saveBook(db, payload, bookId = null) {
  const existing = bookId ? db.books.find((book) => book.id === bookId) : null;
  const merged = {
    ...existing,
    ...payload,
    id: bookId || payload.id || nextId(db.books),
    updatedAt: nowMs(),
    createdAt: payload.createdAt || existing?.createdAt || nowMs(),
    startedAt: existing?.startedAt ?? payload.startedAt ?? nowMs(),
  };
  if (!text(merged.title)) throw new HttpError(400, "책 제목을 입력해 주세요.");

  const normalized = normalizeBook(merged);
  // 다 읽었다고 하면 완독 시각을 남기고, 다시 읽는 중이면 지운다.
  if (normalized.status === "FINISHED") {
    normalized.finishedAt = normalized.finishedAt || nowMs();
    if (normalized.totalPages > 0) normalized.currentPage = normalized.totalPages;
  } else {
    normalized.finishedAt = null;
  }
  upsert(db.books, normalized);
  return normalized;
}

/**
 * 읽고 있는 책에서 글귀를 바로 뽑아 보관함에 넣습니다.
 *
 * 저자와 제목을 책에서 채워 주므로 사용자는 문장과 쪽수만 적으면 됩니다.
 * 적은 쪽수가 지금 읽는 쪽보다 뒤면 진도도 함께 옮깁니다. 뽑았다는 것은 거기까지 읽었다는 뜻입니다.
 */
function extractQuoteFromBook(db, bookId, payload) {
  const book = db.books.find((candidate) => candidate.id === bookId);
  if (!book) throw new HttpError(404, "책을 찾을 수 없습니다.");
  const body = text(payload?.body);
  if (!body) throw new HttpError(400, "뽑아낼 글귀를 입력해 주세요.");
  const page = Math.max(0, positiveInt(payload?.page));

  const quote = saveContent(db, {
    type: "QUOTE",
    title: book.title,
    body,
    author: book.author,
    category: bookCategory,
    bookSyncId: book.syncId,
    bookPage: page,
  });

  if (page > book.currentPage) {
    saveBook(db, { currentPage: page }, book.id);
  }
  return quote;
}

/** 최근 날짜가 위로. 같은 날이면 나중에 쓴 것이 위로 옵니다. */
function sortDiaries(diaries) {
  return [...diaries].sort(
    (a, b) => String(b.entryDate).localeCompare(String(a.entryDate)) || Number(b.createdAt || 0) - Number(a.createdAt || 0),
  );
}

/** 마감이 빠른 것부터. 앱의 오늘 목록과 읽는 순서를 맞춥니다. */
function sortTodos(todos) {
  return [...todos].sort(
    (a, b) => String(a.dueDate).localeCompare(String(b.dueDate)) || Number(a.createdAt || 0) - Number(b.createdAt || 0),
  );
}

function saveRoutineCheck(db, payload) {
  const routine = db.routines.find((entry) => entry.id === positiveInt(payload.routineId));
  const normalized = normalizeRoutineCheck({
    ...payload,
    id: payload.id || nextId(db.routineChecks),
    routineSyncId: payload.routineSyncId || routine?.syncId,
    checkedAt: payload.checkedAt || nowMs(),
  });
  upsert(db.routineChecks, normalized);
  return normalized;
}

function saveRoutineMemo(db, payload) {
  const routine = db.routines.find((entry) => entry.id === positiveInt(payload.routineId));
  const normalized = normalizeRoutineMemo({
    ...payload,
    id: payload.id || nextId(db.routineMemos),
    routineSyncId: payload.routineSyncId || routine?.syncId,
    createdAt: payload.createdAt || nowMs(),
  });
  if (normalized.routineId <= 0 || !normalized.body) {
    throw new HttpError(400, "메모를 저장할 루틴과 내용이 필요합니다.");
  }
  upsert(db.routineMemos, normalized);
  return normalized;
}

function recordContentEvent(db, itemId, eventType, trigger, incrementRead) {
  const item = requireItem(db, itemId);
  const occurredAt = nowMs();
  if (incrementRead) {
    item.lastShownAt = occurredAt;
    item.showCount = Number(item.showCount || 0) + 1;
  }
  const event = normalizeEvent({
    id: nextId(db.exposureEvents),
    contentItemId: item.id,
    contentItemSyncId: item.syncId,
    contentTitle: item.title || item.body.slice(0, 24),
    contentType: item.type,
    eventType,
    trigger,
    occurredAt,
  });
  db.exposureEvents.push(event);
  return event;
}

function markConfirmedOnce(db, itemId, trigger) {
  const [start, end] = todayRange();
  const exists = db.exposureEvents.some(
    (event) =>
      event.contentItemId === itemId &&
      event.eventType === "CONFIRMED" &&
      event.occurredAt >= start &&
      event.occurredAt <= end,
  );
  if (exists) return false;
  recordContentEvent(db, itemId, "CONFIRMED", trigger, true);
  return true;
}

function toggleConfirmed(db, itemId, trigger) {
  const [start, end] = todayRange();
  const before = db.exposureEvents.length;
  db.exposureEvents = db.exposureEvents.filter(
    (event) =>
      !(
        event.contentItemId === itemId &&
        event.eventType === "CONFIRMED" &&
        event.occurredAt >= start &&
        event.occurredAt <= end
      ),
  );
  if (db.exposureEvents.length < before) return false;
  recordContentEvent(db, itemId, "CONFIRMED", trigger, true);
  return true;
}

function todayConfirmedIds(db) {
  const [start, end] = todayRange();
  return [
    ...new Set(
      db.exposureEvents
        .filter((event) => event.eventType === "CONFIRMED" && event.occurredAt >= start && event.occurredAt <= end)
        .map((event) => event.contentItemId),
    ),
  ];
}

function todaySummary(db) {
  const [start, end] = todayRange();
  const events = db.exposureEvents.filter((event) => event.occurredAt >= start && event.occurredAt <= end);
  const summarize = (eventType) => {
    const counts = new Map();
    for (const event of events) {
      if (event.eventType !== eventType) continue;
      counts.set(event.contentTitle, (counts.get(event.contentTitle) || 0) + 1);
    }
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .map(([title, count]) => ({ title, count }));
  };
  const shownItems = summarize("SHOWN");
  const confirmedItems = summarize("CONFIRMED");
  return {
    shownItems,
    confirmedItems,
    totalShown: shownItems.reduce((sum, item) => sum + item.count, 0),
    totalConfirmed: confirmedItems.reduce((sum, item) => sum + item.count, 0),
    confirmedTodayIds: todayConfirmedIds(db),
  };
}

function normalizeContent(item) {
  if (!item) return null;
  return {
    id: positiveInt(item.id),
    // 구버전 앱이 보낸 레코드에는 syncId가 없다. 여기서 채워 두면 이후 병합에 참여할 수 있다.
    syncId: syncId(item.syncId),
    updatedAt: integer(item.updatedAt, integer(item.createdAt, nowMs())),
    lastSurfacedAt: nullableInteger(item.lastSurfacedAt),
    type: normalizeEnum(item.type, contentTypes, "QUOTE"),
    title: text(item.title),
    body: text(item.body),
    author: text(item.author),
    sourceUrl: text(item.sourceUrl),
    thumbnailUrl: text(item.thumbnailUrl),
    category: text(item.category),
    tags: stringList(item.tags),
    imageUris: stringList(item.imageUris),
    videoUris: stringList(item.videoUris),
    // 어느 책 몇 쪽에서 뽑았는지. 책은 숫자 id가 아니라 syncId로 가리킨다.
    bookSyncId: text(item.bookSyncId),
    bookPage: Math.max(0, positiveInt(item.bookPage)),
    createdAt: integer(item.createdAt, nowMs()),
    lastShownAt: nullableInteger(item.lastShownAt),
    showCount: integer(item.showCount, 0),
    isFavorite: Boolean(item.isFavorite),
  };
}

function normalizeEvent(event) {
  if (!event) return null;
  return {
    id: positiveInt(event.id),
    syncId: syncId(event.syncId),
    contentItemId: positiveInt(event.contentItemId),
    // 숫자 id는 기기마다 따로 증가하므로, 기기 간에는 이 값으로 항목을 가리킨다.
    contentItemSyncId: text(event.contentItemSyncId),
    contentTitle: text(event.contentTitle),
    contentType: normalizeEnum(event.contentType, contentTypes, "QUOTE"),
    eventType: normalizeEnum(event.eventType, eventTypes, "SHOWN"),
    trigger: normalizeEnum(event.trigger, triggers, "MANUAL_REFRESH"),
    occurredAt: integer(event.occurredAt, nowMs()),
  };
}

function normalizeRoutine(routine) {
  if (!routine) return null;
  return {
    id: positiveInt(routine.id),
    syncId: syncId(routine.syncId),
    updatedAt: integer(routine.updatedAt, integer(routine.createdAt, nowMs())),
    title: text(routine.title),
    note: text(routine.note),
    category: text(routine.category),
    reminderEnabled: routine.reminderEnabled !== false,
    createdAt: integer(routine.createdAt, nowMs()),
  };
}

function normalizeRoutineCheck(check) {
  if (!check) return null;
  return {
    id: positiveInt(check.id),
    syncId: syncId(check.syncId),
    routineId: positiveInt(check.routineId),
    routineSyncId: text(check.routineSyncId),
    routineTitle: text(check.routineTitle),
    checkedAt: integer(check.checkedAt, nowMs()),
  };
}

function normalizeRoutineMemo(memo) {
  if (!memo) return null;
  return {
    id: positiveInt(memo.id),
    syncId: syncId(memo.syncId),
    updatedAt: integer(memo.updatedAt, integer(memo.createdAt, nowMs())),
    routineId: positiveInt(memo.routineId),
    routineSyncId: text(memo.routineSyncId),
    routineTitle: text(memo.routineTitle),
    body: text(memo.body),
    createdAt: integer(memo.createdAt, nowMs()),
  };
}

function normalizeDiary(diary) {
  if (!diary) return null;
  const entryDate = text(diary.entryDate);
  // 날짜가 없으면 어느 날 일기인지 알 수 없어 화면에 놓을 자리가 없다.
  if (!entryDate) return null;
  return {
    id: positiveInt(diary.id),
    syncId: syncId(diary.syncId),
    updatedAt: integer(diary.updatedAt, integer(diary.createdAt, nowMs())),
    entryDate,
    title: text(diary.title),
    body: text(diary.body),
    // 날씨와 기분은 앱의 DiaryWeather·DiaryMood 이름이다.
    // 서버는 값을 검사하지 않는다. 앱이 선택지를 늘려도 서버를 같이 고칠 필요가 없게 하려는 것이다.
    weather: text(diary.weather),
    mood: text(diary.mood),
    // 종류도 앱의 DiaryKind 이름이다. 옛 기기가 보낸 일기에는 없어서 자유 일기로 본다.
    kind: text(diary.kind) || "FREE",
    answers: normalizeAnswers(diary.answers),
    // 첨부는 URI 문자열만 오간다. 파일 자체는 기기에 있고 서버로 올라오지 않는다.
    imageUris: stringList(diary.imageUris),
    videoUris: stringList(diary.videoUris),
    audioUris: stringList(diary.audioUris),
    createdAt: integer(diary.createdAt, nowMs()),
  };
}

function normalizeTodo(todo) {
  if (!todo) return null;
  const title = text(todo.title);
  const dueDate = text(todo.dueDate);
  if (!title || !dueDate) return null;
  return {
    id: positiveInt(todo.id),
    syncId: syncId(todo.syncId),
    updatedAt: integer(todo.updatedAt, integer(todo.createdAt, nowMs())),
    title,
    note: text(todo.note),
    dueDate,
    // 알람과 완료 시각은 없을 수 있다. 0으로 바꾸면 1970년에 끝낸 일처럼 보인다.
    remindAt: nullableInteger(todo.remindAt),
    doneAt: nullableInteger(todo.doneAt),
    createdAt: integer(todo.createdAt, nowMs()),
  };
}

function normalizeBook(book) {
  if (!book) return null;
  const title = text(book.title);
  // 제목이 없으면 목록에서 무엇인지 알 수 없어 놓을 자리가 없다.
  if (!title) return null;
  const totalPages = Math.max(0, positiveInt(book.totalPages));
  const currentPage = Math.max(0, positiveInt(book.currentPage));
  return {
    id: positiveInt(book.id),
    syncId: syncId(book.syncId),
    updatedAt: integer(book.updatedAt, integer(book.createdAt, nowMs())),
    title,
    author: text(book.author),
    totalPages,
    // 전체 쪽수를 넘는 진도는 있을 수 없다. 화면에서 100%를 넘겨 그린다.
    currentPage: totalPages > 0 ? Math.min(currentPage, totalPages) : currentPage,
    status: normalizeEnum(book.status, bookStatuses, "READING"),
    note: text(book.note),
    // 시작·완독 시각은 없을 수 있다. 0으로 바꾸면 1970년에 읽은 책이 된다.
    startedAt: nullableInteger(book.startedAt),
    finishedAt: nullableInteger(book.finishedAt),
    createdAt: integer(book.createdAt, nowMs()),
  };
}

function normalizeDeletion(deletion) {
  if (!deletion) return null;
  const id = text(deletion.syncId);
  const entityType = normalizeEnum(deletion.entityType, deletionEntityTypes, null);
  if (!id || !entityType) return null;
  return {
    syncId: id,
    entityType,
    deletedAt: integer(deletion.deletedAt, nowMs()),
  };
}

/** 앱과 같은 규칙으로 식별자를 채운다. 없으면 새로 만든다. */
function syncId(value) {
  const given = text(value);
  return given || randomUUID();
}

function requireItem(db, itemId) {
  const item = db.items.find((candidate) => candidate.id === itemId);
  if (!item) throw new HttpError(404, "항목을 찾을 수 없습니다.");
  return item;
}

function requireRoutine(db, routineId) {
  const routine = db.routines.find((candidate) => candidate.id === routineId);
  if (!routine) throw new HttpError(404, "루틴을 찾을 수 없습니다.");
  return routine;
}

function upsert(items, item) {
  const index = items.findIndex((candidate) => candidate.id === item.id);
  if (index >= 0) {
    items[index] = item;
  } else {
    items.push(item);
  }
}

function deleteByIds(items, ids) {
  const idSet = new Set(ids);
  const before = items.length;
  const kept = items.filter((item) => !idSet.has(item.id));
  items.length = 0;
  items.push(...kept);
  return { deleted: before - items.length };
}

/**
 * 지우면서 삭제 표식을 함께 남깁니다.
 *
 * 표식이 없으면 웹에서 지워도 기기에는 그 레코드가 그대로 있어서, 다음 병합에 서버로 다시
 * 올라옵니다. 사용자가 보기에는 지웠는데 잠시 뒤 되살아나는 셈입니다.
 */
function deleteWithTombstone(db, collection, ids, entityType) {
  const idSet = new Set(ids);
  const removed = collection.filter((record) => idSet.has(record.id));
  const result = deleteByIds(collection, ids);
  const deletedAt = nowMs();
  for (const record of removed) {
    if (!record.syncId) continue;
    db.deletions = db.deletions.filter((entry) => entry.syncId !== record.syncId);
    db.deletions.push({ syncId: record.syncId, entityType, deletedAt });
  }
  return result;
}

function nextId(items) {
  return items.reduce((max, item) => Math.max(max, Number(item.id) || 0), 0) + 1;
}

function sortDesc(items, key) {
  return [...items].sort((a, b) => Number(b[key] || 0) - Number(a[key] || 0));
}

function nowMs() {
  return Date.now();
}

function todayRange() {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return [start.getTime(), end.getTime() - 1];
}

function text(value) {
  return String(value || "").trim();
}

function stringList(value) {
  return Array.isArray(value) ? value.map(text).filter(Boolean) : [];
}

/**
 * 일기 물음의 답. [stringList]와 달리 빈칸을 버리지 않는다.
 *
 * 답은 물음과 자리를 맞춰 둔 목록이라, 가운데 빈칸을 버리면 뒤의 답이 다른 물음의 답이 된다.
 * 뒤쪽 빈칸만 떼어 낸다. 앱 DiaryAnswers.normalize와 같은 규칙이어야 지문이 어긋나지 않는다.
 */
function normalizeAnswers(value) {
  if (!Array.isArray(value)) return [];
  const answers = value.map(text);
  while (answers.length && !answers[answers.length - 1]) answers.pop();
  return answers;
}

function integer(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.trunc(parsed) : fallback;
}

function nullableInteger(value) {
  if (value === null || value === undefined) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.trunc(parsed) : null;
}

function positiveInt(value) {
  return Math.max(0, integer(value, 0));
}

function normalizeEnum(value, allowed, fallback) {
  const normalized = text(value).toUpperCase();
  return allowed.has(normalized) ? normalized : fallback;
}

function parseIds(value) {
  return String(value || "")
    .split(",")
    .map((part) => Number(part.trim()))
    .filter((id) => Number.isFinite(id) && id > 0);
}

async function readJson(request, optional = false) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  const textBody = Buffer.concat(chunks).toString("utf8");
  if (!textBody.trim()) return optional ? {} : {};
  try {
    const parsed = JSON.parse(textBody);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("JSON 객체가 필요합니다.");
    }
    return parsed;
  } catch (error) {
    throw new HttpError(400, error.message || "JSON 형식이 올바르지 않습니다.");
  }
}

function sendJson(response, statusCode, payload) {
  const body = JSON.stringify(payload, null, 2);
  response.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-API-Key",
  });
  response.end(body);
}

function sendNoContent(response) {
  response.writeHead(204, {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-API-Key",
  });
  response.end();
}

async function serveStatic(response, pathname) {
  const relative = pathname === "/" ? "index.html" : pathname.replace(/^\/+/, "");
  const target = resolve(webRoot, normalize(relative));
  if (!target.startsWith(resolve(webRoot))) {
    sendJson(response, 403, { error: "허용되지 않은 경로입니다." });
    return;
  }
  let filePath = target;
  try {
    const fileStat = await stat(filePath);
    if (fileStat.isDirectory()) filePath = join(filePath, "index.html");
  } catch {
    filePath = join(webRoot, "index.html");
  }
  response.writeHead(200, { "Content-Type": contentType(filePath) });
  createReadStream(filePath).pipe(response);
}

function contentType(filePath) {
  return (
    {
      ".html": "text/html; charset=utf-8",
      ".css": "text/css; charset=utf-8",
      ".js": "text/javascript; charset=utf-8",
      ".mjs": "text/javascript; charset=utf-8",
      ".svg": "image/svg+xml",
      ".json": "application/json; charset=utf-8",
    }[extname(filePath).toLowerCase()] || "application/octet-stream"
  );
}

function isAuthorized(request, pathname) {
  if (!config.apiKey || pathname === "/api/health") return true;
  return (
    request.headers["x-api-key"] === config.apiKey ||
    request.headers.authorization === `Bearer ${config.apiKey}`
  );
}

function parseArgs(args) {
  const parsed = {
    host: process.env.APP_GOOD_WORDS_HOST || "127.0.0.1",
    port: Number(process.env.APP_GOOD_WORDS_PORT || 8765),
    dbPath: process.env.APP_GOOD_WORDS_DB || defaultDbPath,
    apiKey: process.env.APP_GOOD_WORDS_API_KEY || "",
    seed: false,
  };
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--host") parsed.host = args[++index];
    else if (arg === "--port") parsed.port = Number(args[++index]);
    else if (arg === "--db") parsed.dbPath = args[++index];
    else if (arg === "--api-key") parsed.apiKey = args[++index];
    else if (arg === "--attachments") parsed.attachmentsDir = args[++index];
    else if (arg === "--seed") parsed.seed = true;
  }
  parsed.dbPath = resolve(parsed.dbPath);
  // 첨부는 DB JSON 밖에 둡니다. 안에 넣으면 스냅샷을 주고받을 때마다 사진과 영상이 통째로 오갑니다.
  parsed.attachmentsDir = resolve(parsed.attachmentsDir || join(dirname(parsed.dbPath), "attachments"));
  return parsed;
}

class HttpError extends Error {
  constructor(statusCode, message) {
    super(message);
    this.statusCode = statusCode;
  }
}

process.on("unhandledRejection", (error) => {
  if (error instanceof HttpError) {
    console.error(`${error.statusCode}: ${error.message}`);
  } else {
    console.error(error);
  }
});
