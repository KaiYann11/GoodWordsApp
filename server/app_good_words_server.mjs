#!/usr/bin/env node
import { randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { readFile, rename, stat, writeFile } from "node:fs/promises";
import { createReadStream } from "node:fs";
import { dirname, extname, join, normalize, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const appName = "오늘의 글귀";
const schemaVersion = 9;
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
    const result = await withDb((db) => deleteByIds(db.exposureEvents, ids));
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
    const result = await withDb((db) => deleteByIds(db.items, [itemId]));
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
      const deleted = deleteByIds(db.routines, [routineId]).deleted;
      deleteByIds(
        db.routineChecks,
        db.routineChecks.filter((check) => check.routineId === routineId).map((check) => check.id),
      );
      deleteByIds(
        db.routineMemos,
        db.routineMemos.filter((memo) => memo.routineId === routineId).map((memo) => memo.id),
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
  const result = await withDb((db) => deleteByIds(db.routineMemos, [memoId]));
  sendJson(response, 200, { deleted: result.deleted });
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
    settings: normalized.settings,
    settingsUpdatedAt: normalized.settingsUpdatedAt,
    items: sortDesc(normalized.items, "createdAt"),
    exposureEvents: sortDesc(normalized.exposureEvents, "occurredAt"),
    routines: sortDesc(normalized.routines, "createdAt"),
    routineChecks: sortDesc(normalized.routineChecks, "checkedAt"),
    routineMemos: sortDesc(normalized.routineMemos, "createdAt"),
    deletions: sortDesc(normalized.deletions, "deletedAt"),
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
  });
  const current = normalizeDb(db);

  const deletions = mergeDeletions(current.deletions, incoming.deletions);
  const deletedAt = new Map(deletions.map((entry) => [entry.syncId, entry.deletedAt]));

  db.items = mergeMutable(current.items, incoming.items, deletedAt);
  db.routines = mergeMutable(current.routines, incoming.routines, deletedAt);
  db.routineMemos = mergeMutable(current.routineMemos, incoming.routineMemos, deletedAt);
  db.exposureEvents = mergeAppendOnly(current.exposureEvents, incoming.exposureEvents, deletedAt);
  db.routineChecks = mergeAppendOnly(current.routineChecks, incoming.routineChecks, deletedAt);
  db.deletions = deletions;

  if (incoming.settingsUpdatedAt > current.settingsUpdatedAt) {
    db.settings = { ...defaultSettings, ...incoming.settings };
    db.settingsUpdatedAt = incoming.settingsUpdatedAt;
  } else {
    db.settings = { ...defaultSettings, ...current.settings };
    db.settingsUpdatedAt = current.settingsUpdatedAt;
  }

  reindex(db);
  return db;
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
  return db;
}

function saveContent(db, payload, itemId = null) {
  const existing = itemId ? db.items.find((item) => item.id === itemId) : null;
  const normalized = normalizeContent({
    ...existing,
    ...payload,
    id: itemId || payload.id || nextId(db.items),
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
    createdAt: payload.createdAt || existing?.createdAt || nowMs(),
  });
  if (!normalized.title) throw new HttpError(400, "루틴 이름을 입력해 주세요.");
  upsert(db.routines, normalized);
  return normalized;
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
