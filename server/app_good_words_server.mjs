#!/usr/bin/env node
import { randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { readFile, rename, stat, writeFile } from "node:fs/promises";
import { createReadStream } from "node:fs";
import { dirname, extname, join, normalize, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const appName = "오늘의 글귀";
const schemaVersion = 11;
/** 이 기간보다 오래 꺼져 있던 기기가 다시 붙으면, 그 사이 지운 항목이 되살아날 수 있다. */
const deletionRetentionDays = 90;
const here = dirname(fileURLToPath(import.meta.url));
const webRoot = join(here, "web");
const defaultDbPath = join(here, "app-good-words.db.json");

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
]);

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
    sendJson(response, 200, snapshot(await loadDb()));
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
    const merged = await withDb((db) => mergeSnapshot(db, payload));
    sendJson(response, 200, snapshot(merged));
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
      return saveRoutineMemo(db, {
        ...payload,
        routineId: routine.id,
        routineTitle: routine.title,
      });
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

async function withDb(mutator) {
  const run = writeQueue.catch(() => undefined).then(async () => {
    const db = await loadDb();
    const result = mutator(db);
    await saveDb(db);
    return result;
  });
  writeQueue = run.catch(() => undefined);
  return run;
}

function emptyDb() {
  return {
    appName,
    schemaVersion,
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
  };
}

function normalizeDb(db) {
  return {
    appName,
    schemaVersion,
    settings: { ...defaultSettings, ...(db?.settings || {}) },
    settingsUpdatedAt: integer(db?.settingsUpdatedAt, 0),
    items: Array.isArray(db?.items) ? db.items.map(normalizeContent).filter(Boolean) : [],
    exposureEvents: Array.isArray(db?.exposureEvents) ? db.exposureEvents.map(normalizeEvent).filter(Boolean) : [],
    routines: Array.isArray(db?.routines) ? db.routines.map(normalizeRoutine).filter(Boolean) : [],
    routineChecks: Array.isArray(db?.routineChecks) ? db.routineChecks.map(normalizeRoutineCheck).filter(Boolean) : [],
    routineMemos: Array.isArray(db?.routineMemos) ? db.routineMemos.map(normalizeRoutineMemo).filter(Boolean) : [],
    deletions: Array.isArray(db?.deletions) ? db.deletions.map(normalizeDeletion).filter(Boolean) : [],
    diaries: Array.isArray(db?.diaries) ? db.diaries.map(normalizeDiary).filter(Boolean) : [],
    todos: Array.isArray(db?.todos) ? db.todos.map(normalizeTodo).filter(Boolean) : [],
  };
}

function snapshot(db) {
  const normalized = normalizeDb(db);
  return {
    appName,
    schemaVersion,
    exportedAt: new Date().toISOString(),
    itemCount: normalized.items.length,
    eventCount: normalized.exposureEvents.length,
    routineCount: normalized.routines.length,
    routineCheckCount: normalized.routineChecks.length,
    routineMemoCount: normalized.routineMemos.length,
    diaryCount: normalized.diaries.length,
    todoCount: normalized.todos.length,
    settings: normalized.settings,
    settingsUpdatedAt: normalized.settingsUpdatedAt,
    items: sortDesc(normalized.items, "createdAt"),
    exposureEvents: sortDesc(normalized.exposureEvents, "occurredAt"),
    routines: sortDesc(normalized.routines, "createdAt"),
    routineChecks: sortDesc(normalized.routineChecks, "checkedAt"),
    routineMemos: sortDesc(normalized.routineMemos, "createdAt"),
    deletions: sortDesc(normalized.deletions, "deletedAt"),
    diaries: sortDesc(normalized.diaries, "createdAt"),
    todos: sortDesc(normalized.todos, "createdAt"),
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

  db.items = items.kept;
  db.routines = routines.kept;
  db.diaries = diaries.kept;
  db.todos = todos.kept;

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
function diaryFingerprint(diary) {
  return [
    text(diary.entryDate),
    norm(diary.title),
    norm(diary.body),
    text(diary.weather),
    text(diary.mood),
    diary.imageUris.join(","),
    diary.videoUris.join(","),
    diary.audioUris.join(","),
  ].join("|");
}

function todoFingerprint(todo) {
  return [text(todo.dueDate), norm(todo.title), norm(todo.note)].join("|");
}

/** 띄어쓰기와 대소문자만 다른 것도 같은 내용으로 본다. */
function norm(value) {
  return text(value).toLowerCase().replace(/\s+/g, " ");
}

/**
 * 숫자 id를 서버 안에서 다시 매깁니다.
 *
 * 병합 결과에는 서로 다른 기기에서 온 같은 숫자 id가 함께 들어옵니다.
 * 그대로 두면 /api/content/{id} 같은 경로가 둘 중 아무거나 집게 되고,
 * 이벤트도 엉뚱한 항목에 붙습니다. 그래서 저장 직전에 1부터 다시 부여하고
 * 자식 레코드의 참조는 syncId로 다시 잇습니다.
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

  db.items = db.items.map((item, index) => ({ ...item, id: index + 1 }));
  db.routines = db.routines.map((routine, index) => ({ ...routine, id: index + 1 }));

  const itemIds = new Map(db.items.map((item) => [item.syncId, item.id]));
  const routineIds = new Map(db.routines.map((routine) => [routine.syncId, routine.id]));

  // 이력은 항목이 지워진 뒤에도 남으므로, 부모를 못 찾아도 버리지 않고 0으로 끊는다.
  db.exposureEvents = events.map((event, index) => ({
    ...event,
    id: index + 1,
    contentItemId: itemIds.get(event.contentItemSyncId) || 0,
  }));
  db.routineChecks = checks.map((check, index) => ({
    ...check,
    id: index + 1,
    routineId: routineIds.get(check.routineSyncId) || 0,
  }));
  // 메모는 루틴 안에서만 보이므로, 붙을 루틴이 없으면 남겨도 볼 방법이 없다.
  db.routineMemos = memos
    .filter((memo) => routineIds.has(memo.routineSyncId))
    .map((memo, index) => ({ ...memo, id: index + 1, routineId: routineIds.get(memo.routineSyncId) }));
  // 일기와 할 일은 딸린 자식이 없어 번호만 다시 매기면 된다.
  db.diaries = db.diaries.map((diary, index) => ({ ...diary, id: index + 1 }));
  db.todos = db.todos.map((todo, index) => ({ ...todo, id: index + 1 }));

  return db;
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
    else if (arg === "--seed") parsed.seed = true;
  }
  parsed.dbPath = resolve(parsed.dbPath);
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
