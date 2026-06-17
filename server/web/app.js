const state = {
  activeTab: "home",
  snapshot: emptySnapshot(),
  editingContentId: null,
  editingRoutineId: null,
  query: "",
  typeFilter: "ALL",
  categoryFilter: "",
};

const app = document.querySelector("#app");
const toast = document.querySelector("#toast");
const connectionText = document.querySelector("#connectionText");
const snapshotFile = document.querySelector("#snapshotFile");

document.querySelectorAll("[data-tab]").forEach((button) => {
  button.addEventListener("click", () => {
    state.activeTab = button.dataset.tab;
    state.editingContentId = null;
    state.editingRoutineId = null;
    syncTabs();
    render();
  });
});

document.querySelector("#refreshButton").addEventListener("click", () => loadSnapshot());

app.addEventListener("input", (event) => {
  const target = event.target;
  if (!(target instanceof HTMLElement)) return;
  if (target.id === "libraryQuery") {
    state.query = target.value;
    renderLibraryListOnly();
  }
  if (target.id === "typeFilter") {
    state.typeFilter = target.value;
    renderLibraryListOnly();
  }
  if (target.id === "categoryFilter") {
    state.categoryFilter = target.value;
    renderLibraryListOnly();
  }
});

app.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.target;
  if (!(form instanceof HTMLFormElement)) return;
  try {
    if (form.dataset.form === "content") {
      await submitContent(form);
    } else if (form.dataset.form === "routine") {
      await submitRoutine(form);
    } else if (form.dataset.form === "memo") {
      await submitMemo(form);
    }
  } catch (error) {
    showToast(error.message || "요청에 실패했습니다.");
  }
});

app.addEventListener("click", async (event) => {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const action = button.dataset.action;
  const id = Number(button.dataset.id || 0);
  try {
    if (action === "edit-content") {
      state.editingContentId = id;
      render();
    } else if (action === "cancel-content") {
      state.editingContentId = null;
      render();
    } else if (action === "delete-content") {
      await api(`/api/content/${id}`, { method: "DELETE" });
      showToast("항목을 삭제했습니다.");
      await loadSnapshot(false);
    } else if (action === "favorite") {
      const item = findById(state.snapshot.items, id);
      await api(`/api/content/${id}/favorite`, {
        method: "POST",
        body: { isFavorite: !item?.isFavorite },
      });
      await loadSnapshot(false);
    } else if (action === "confirm") {
      await api(`/api/content/${id}/toggle-confirm`, { method: "POST" });
      await loadSnapshot(false);
    } else if (action === "edit-routine") {
      state.editingRoutineId = id;
      render();
    } else if (action === "cancel-routine") {
      state.editingRoutineId = null;
      render();
    } else if (action === "delete-routine") {
      await api(`/api/routines/${id}`, { method: "DELETE" });
      showToast("루틴을 삭제했습니다.");
      await loadSnapshot(false);
    } else if (action === "check-routine") {
      const result = await api(`/api/routines/${id}/check`, { method: "POST" });
      showToast(`오늘 ${result.todayCount}회`);
      await loadSnapshot(false);
    } else if (action === "delete-memo") {
      await api(`/api/routine-memos/${id}`, { method: "DELETE" });
      await loadSnapshot(false);
    } else if (action === "delete-event") {
      await api(`/api/events?ids=${id}`, { method: "DELETE" });
      await loadSnapshot(false);
    } else if (action === "download-snapshot") {
      downloadSnapshot();
    } else if (action === "upload-snapshot") {
      snapshotFile.click();
    } else if (action === "save-api-key") {
      const input = document.querySelector("#apiKey");
      localStorage.setItem("appGoodWordsApiKey", input?.value || "");
      showToast("API 키를 저장했습니다.");
    }
  } catch (error) {
    showToast(error.message || "요청에 실패했습니다.");
  }
});

snapshotFile.addEventListener("change", async () => {
  const file = snapshotFile.files?.[0];
  if (!file) return;
  try {
    const payload = JSON.parse(await file.text());
    await api("/api/snapshot", { method: "PUT", body: payload });
    snapshotFile.value = "";
    showToast("서버 DB를 가져온 JSON으로 교체했습니다.");
    await loadSnapshot(false);
  } catch (error) {
    showToast(error.message || "가져오기에 실패했습니다.");
  }
});

loadSnapshot();

function emptySnapshot() {
  return {
    items: [],
    exposureEvents: [],
    routines: [],
    routineChecks: [],
    routineMemos: [],
    settings: {},
  };
}

async function api(path, options = {}) {
  const headers = { Accept: "application/json" };
  const apiKey = localStorage.getItem("appGoodWordsApiKey") || "";
  if (apiKey) headers["X-API-Key"] = apiKey;
  const init = {
    method: options.method || "GET",
    headers,
  };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json; charset=utf-8";
    init.body = JSON.stringify(options.body);
  }
  const response = await fetch(path, init);
  const text = await response.text();
  const payload = text ? JSON.parse(text) : {};
  if (!response.ok) {
    throw new Error(payload.error || `HTTP ${response.status}`);
  }
  return payload;
}

async function loadSnapshot(showMessage = true) {
  try {
    state.snapshot = await api("/api/snapshot");
    connectionText.textContent = `DB ${state.snapshot.itemCount || 0}개 항목`;
    render();
    if (showMessage) showToast("서버 DB를 불러왔습니다.");
  } catch (error) {
    connectionText.textContent = "서버 연결 실패";
    app.innerHTML = `<div class="empty">${escapeHtml(error.message || "서버에 연결할 수 없습니다.")}</div>`;
  }
}

function render() {
  syncTabs();
  if (state.activeTab === "home") renderHome();
  if (state.activeTab === "library") renderLibrary();
  if (state.activeTab === "routines") renderRoutines();
  if (state.activeTab === "history") renderHistory();
  if (state.activeTab === "settings") renderSettings();
}

function syncTabs() {
  document.querySelectorAll("[data-tab]").forEach((button) => {
    button.classList.toggle("active", button.dataset.tab === state.activeTab);
  });
}

function renderHome() {
  const summary = todaySummary();
  const recent = [...state.snapshot.items]
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
    .slice(0, 8);
  app.innerHTML = `
    <section class="stats">
      ${stat("저장 항목", state.snapshot.items.length)}
      ${stat("오늘 확인", summary.confirmedIds.size)}
      ${stat("루틴", state.snapshot.routines.length)}
      ${stat("이력", state.snapshot.exposureEvents.length)}
    </section>
    <section class="grid two" style="margin-top:16px">
      <div class="panel">
        <h2>오늘</h2>
        <div class="itemList">
          ${summary.lines.length ? summary.lines.map(summaryLine).join("") : empty("오늘 기록이 없습니다.")}
        </div>
      </div>
      <div>
        <h2 class="sectionTitle">최근 항목</h2>
        <div class="itemList">${recent.length ? recent.map(contentCard).join("") : empty("저장된 항목이 없습니다.")}</div>
      </div>
    </section>
  `;
}

function renderLibrary() {
  const editing = findById(state.snapshot.items, state.editingContentId) || {};
  const categories = categoryOptions();
  app.innerHTML = `
    <section class="grid two">
      <form class="panel" data-form="content">
        <h2>${editing.id ? "항목 수정" : "항목 추가"}</h2>
        <div class="formGrid">
          <div class="field">
            <label for="contentType">유형</label>
            <select id="contentType" name="type">
              ${option("QUOTE", "글귀", editing.type)}
              ${option("LINK", "링크", editing.type)}
              ${option("VIDEO", "영상", editing.type)}
            </select>
          </div>
          <div class="field">
            <label for="contentCategory">카테고리</label>
            <input id="contentCategory" name="category" value="${attr(editing.category)}">
          </div>
          <div class="field full">
            <label for="contentTitle">제목</label>
            <input id="contentTitle" name="title" value="${attr(editing.title)}">
          </div>
          <div class="field full">
            <label for="contentBody">본문</label>
            <textarea id="contentBody" name="body">${escapeHtml(editing.body)}</textarea>
          </div>
          <div class="field">
            <label for="contentAuthor">작성자</label>
            <input id="contentAuthor" name="author" value="${attr(editing.author)}">
          </div>
          <div class="field">
            <label for="contentTags">태그</label>
            <input id="contentTags" name="tags" value="${attr((editing.tags || []).join(", "))}">
          </div>
          <div class="field full">
            <label for="contentSourceUrl">링크</label>
            <input id="contentSourceUrl" name="sourceUrl" value="${attr(editing.sourceUrl)}">
          </div>
        </div>
        <div class="actions" style="margin-top:14px">
          <button class="primary" type="submit">${editing.id ? "수정 완료" : "저장"}</button>
          ${editing.id ? `<button type="button" data-action="cancel-content">취소</button>` : ""}
        </div>
      </form>
      <section>
        <div class="toolbar">
          <div class="field">
            <label for="libraryQuery">검색</label>
            <input id="libraryQuery" value="${attr(state.query)}">
          </div>
          <div class="field">
            <label for="typeFilter">유형</label>
            <select id="typeFilter">
              ${option("ALL", "전체", state.typeFilter)}
              ${option("QUOTE", "글귀", state.typeFilter)}
              ${option("LINK", "링크", state.typeFilter)}
              ${option("VIDEO", "영상", state.typeFilter)}
            </select>
          </div>
          <div class="field">
            <label for="categoryFilter">카테고리</label>
            <select id="categoryFilter">
              ${option("", "전체", state.categoryFilter)}
              ${categories.map((category) => option(category, category, state.categoryFilter)).join("")}
            </select>
          </div>
        </div>
        <div id="libraryList" class="itemList"></div>
      </section>
    </section>
  `;
  renderLibraryListOnly();
}

function renderLibraryListOnly() {
  const list = document.querySelector("#libraryList");
  if (!list) return;
  const filtered = filteredItems();
  list.innerHTML = filtered.length ? filtered.map(contentCard).join("") : empty("조건에 맞는 항목이 없습니다.");
}

function renderRoutines() {
  const editing = findById(state.snapshot.routines, state.editingRoutineId) || {};
  const checksByRoutine = groupBy(state.snapshot.routineChecks, "routineId");
  const memosByRoutine = groupBy(state.snapshot.routineMemos, "routineId");
  app.innerHTML = `
    <section class="grid two">
      <form class="panel" data-form="routine">
        <h2>${editing.id ? "루틴 수정" : "루틴 추가"}</h2>
        <div class="formGrid">
          <div class="field full">
            <label for="routineTitle">이름</label>
            <input id="routineTitle" name="title" value="${attr(editing.title)}" required>
          </div>
          <div class="field full">
            <label for="routineNote">메모</label>
            <textarea id="routineNote" name="note">${escapeHtml(editing.note)}</textarea>
          </div>
          <div class="field">
            <label for="routineCategory">카테고리</label>
            <input id="routineCategory" name="category" value="${attr(editing.category)}">
          </div>
          <div class="field">
            <label for="routineReminder">알림</label>
            <select id="routineReminder" name="reminderEnabled">
              ${option("true", "사용", String(editing.reminderEnabled ?? true))}
              ${option("false", "사용 안 함", String(editing.reminderEnabled ?? true))}
            </select>
          </div>
        </div>
        <div class="actions" style="margin-top:14px">
          <button class="primary" type="submit">${editing.id ? "수정 완료" : "저장"}</button>
          ${editing.id ? `<button type="button" data-action="cancel-routine">취소</button>` : ""}
        </div>
      </form>
      <section>
        <h2 class="sectionTitle">루틴 ${state.snapshot.routines.length}개</h2>
        <div class="itemList">
          ${state.snapshot.routines.length
            ? state.snapshot.routines.map((routine) => routineCard(routine, checksByRoutine, memosByRoutine)).join("")
            : empty("저장된 루틴이 없습니다.")}
        </div>
      </section>
    </section>
  `;
}

function renderHistory() {
  app.innerHTML = `
    <section>
      <h2 class="sectionTitle">이력 ${state.snapshot.exposureEvents.length}개</h2>
      <div class="itemList">
        ${state.snapshot.exposureEvents.length
          ? state.snapshot.exposureEvents.map(eventCard).join("")
          : empty("기록된 이력이 없습니다.")}
      </div>
    </section>
  `;
}

function renderSettings() {
  const apiKey = localStorage.getItem("appGoodWordsApiKey") || "";
  app.innerHTML = `
    <section class="grid two">
      <div class="panel">
        <h2>API</h2>
        <div class="field">
          <label for="apiKey">API 키</label>
          <input id="apiKey" type="password" value="${attr(apiKey)}" autocomplete="off">
        </div>
        <div class="actions" style="margin-top:14px">
          <button class="primary" type="button" data-action="save-api-key">저장</button>
          <button type="button" data-action="download-snapshot">JSON 다운로드</button>
          <button type="button" data-action="upload-snapshot">JSON 가져오기</button>
        </div>
      </div>
      <div class="panel">
        <h2>서버 상태</h2>
        <div class="stats">
          ${stat("항목", state.snapshot.items.length)}
          ${stat("루틴", state.snapshot.routines.length)}
          ${stat("체크", state.snapshot.routineChecks.length)}
          ${stat("메모", state.snapshot.routineMemos.length)}
        </div>
      </div>
    </section>
  `;
}

async function submitContent(form) {
  const data = new FormData(form);
  const payload = {
    type: data.get("type"),
    title: data.get("title"),
    body: data.get("body"),
    author: data.get("author"),
    sourceUrl: data.get("sourceUrl"),
    category: data.get("category"),
    tags: splitCsv(data.get("tags")),
  };
  const id = state.editingContentId;
  await api(id ? `/api/content/${id}` : "/api/content", {
    method: id ? "PUT" : "POST",
    body: payload,
  });
  state.editingContentId = null;
  showToast(id ? "항목을 수정했습니다." : "항목을 저장했습니다.");
  await loadSnapshot(false);
}

async function submitRoutine(form) {
  const data = new FormData(form);
  const payload = {
    title: data.get("title"),
    note: data.get("note"),
    category: data.get("category"),
    reminderEnabled: data.get("reminderEnabled") === "true",
  };
  const id = state.editingRoutineId;
  await api(id ? `/api/routines/${id}` : "/api/routines", {
    method: id ? "PUT" : "POST",
    body: payload,
  });
  state.editingRoutineId = null;
  showToast(id ? "루틴을 수정했습니다." : "루틴을 저장했습니다.");
  await loadSnapshot(false);
}

async function submitMemo(form) {
  const data = new FormData(form);
  const routineId = Number(data.get("routineId"));
  await api(`/api/routines/${routineId}/memos`, {
    method: "POST",
    body: { body: data.get("body") },
  });
  showToast("메모를 저장했습니다.");
  await loadSnapshot(false);
}

function contentCard(item) {
  const confirmed = todaySummary().confirmedIds.has(item.id);
  return `
    <article class="item">
      <div class="itemHeader">
        <div>
          <h3>${escapeHtml(item.title || "제목 없음")}</h3>
          <div class="meta">
            <span class="chip">${labelForType(item.type)}</span>
            ${item.category ? `<span class="chip">${escapeHtml(item.category)}</span>` : ""}
            ${item.isFavorite ? `<span class="chip favorite">즐겨찾기</span>` : ""}
            ${confirmed ? `<span class="chip">오늘 확인</span>` : ""}
          </div>
        </div>
        <button type="button" data-action="favorite" data-id="${item.id}" title="즐겨찾기">${item.isFavorite ? "★" : "☆"}</button>
      </div>
      ${item.body ? `<p>${escapeHtml(item.body)}</p>` : ""}
      ${item.sourceUrl ? `<p><a href="${attr(item.sourceUrl)}" target="_blank" rel="noreferrer">${escapeHtml(item.sourceUrl)}</a></p>` : ""}
      ${item.tags?.length ? `<div class="chips">${item.tags.map((tag) => `<span class="chip">#${escapeHtml(tag)}</span>`).join("")}</div>` : ""}
      <div class="actions">
        <button type="button" data-action="confirm" data-id="${item.id}">${confirmed ? "확인 취소" : "오늘 확인"}</button>
        <button type="button" data-action="edit-content" data-id="${item.id}">수정</button>
        <button class="danger" type="button" data-action="delete-content" data-id="${item.id}">삭제</button>
      </div>
    </article>
  `;
}

function routineCard(routine, checksByRoutine, memosByRoutine) {
  const checks = checksByRoutine.get(routine.id) || [];
  const memos = memosByRoutine.get(routine.id) || [];
  const todayCount = checks.filter((check) => isToday(check.checkedAt)).length;
  return `
    <article class="item">
      <div class="itemHeader">
        <div>
          <h3>${escapeHtml(routine.title)}</h3>
          <div class="meta">
            ${routine.category ? `<span class="chip">${escapeHtml(routine.category)}</span>` : ""}
            <span class="chip">오늘 ${todayCount}회</span>
            <span class="chip">전체 ${checks.length}회</span>
          </div>
        </div>
        <button type="button" data-action="check-routine" data-id="${routine.id}">체크</button>
      </div>
      ${routine.note ? `<p>${escapeHtml(routine.note)}</p>` : ""}
      <form class="formGrid" data-form="memo">
        <input type="hidden" name="routineId" value="${routine.id}">
        <div class="field full">
          <label for="memo-${routine.id}">메모</label>
          <textarea id="memo-${routine.id}" name="body" required></textarea>
        </div>
        <div class="actions field full">
          <button type="submit">메모 저장</button>
          <button type="button" data-action="edit-routine" data-id="${routine.id}">수정</button>
          <button class="danger" type="button" data-action="delete-routine" data-id="${routine.id}">삭제</button>
        </div>
      </form>
      ${memos.length ? `<div class="itemList">${memos.slice(0, 4).map(memoCard).join("")}</div>` : ""}
    </article>
  `;
}

function memoCard(memo) {
  return `
    <div class="item">
      <p>${escapeHtml(memo.body)}</p>
      <div class="actions">
        <span class="chip">${formatDate(memo.createdAt)}</span>
        <button class="danger" type="button" data-action="delete-memo" data-id="${memo.id}">삭제</button>
      </div>
    </div>
  `;
}

function eventCard(event) {
  return `
    <article class="item">
      <div class="itemHeader">
        <div>
          <h3>${escapeHtml(event.contentTitle || "제목 없음")}</h3>
          <div class="meta">
            <span class="chip">${labelForEvent(event.eventType)}</span>
            <span class="chip">${labelForType(event.contentType)}</span>
            <span class="chip">${formatDate(event.occurredAt)}</span>
          </div>
        </div>
        <button class="danger" type="button" data-action="delete-event" data-id="${event.id}">삭제</button>
      </div>
    </article>
  `;
}

function todaySummary() {
  const confirmedIds = new Set();
  const counts = new Map();
  for (const event of state.snapshot.exposureEvents) {
    if (!isToday(event.occurredAt)) continue;
    if (event.eventType === "CONFIRMED") confirmedIds.add(event.contentItemId);
    if (event.eventType === "SHOWN" || event.eventType === "CONFIRMED") {
      const key = event.contentTitle || "제목 없음";
      counts.set(key, (counts.get(key) || 0) + 1);
    }
  }
  const lines = [...counts.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .map(([title, count]) => ({ title, count }));
  return { confirmedIds, lines };
}

function summaryLine(line) {
  return `
    <div class="item">
      <div class="itemHeader">
        <h3>${escapeHtml(line.title)}</h3>
        <span class="chip">${line.count}회</span>
      </div>
    </div>
  `;
}

function filteredItems() {
  const query = state.query.trim().toLowerCase();
  return [...state.snapshot.items]
    .filter((item) => state.typeFilter === "ALL" || item.type === state.typeFilter)
    .filter((item) => !state.categoryFilter || item.category === state.categoryFilter)
    .filter((item) => {
      if (!query) return true;
      return [item.title, item.body, item.author, item.category, ...(item.tags || [])]
        .join(" ")
        .toLowerCase()
        .includes(query);
    })
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
}

function categoryOptions() {
  return [...new Set(state.snapshot.items.map((item) => item.category).filter(Boolean))].sort((a, b) =>
    a.localeCompare(b),
  );
}

function groupBy(items, key) {
  const grouped = new Map();
  for (const item of items) {
    const value = item[key];
    if (!grouped.has(value)) grouped.set(value, []);
    grouped.get(value).push(item);
  }
  return grouped;
}

function findById(items, id) {
  return items.find((item) => Number(item.id) === Number(id));
}

function stat(label, value) {
  return `<div class="stat"><strong>${escapeHtml(String(value))}</strong><span>${escapeHtml(label)}</span></div>`;
}

function empty(message) {
  return `<div class="empty">${escapeHtml(message)}</div>`;
}

function option(value, label, selected) {
  return `<option value="${attr(value)}" ${String(value) === String(selected ?? "") ? "selected" : ""}>${escapeHtml(label)}</option>`;
}

function splitCsv(value) {
  return String(value || "")
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean);
}

function isToday(timestamp) {
  const date = new Date(timestamp || 0);
  const now = new Date();
  return (
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  );
}

function formatDate(timestamp) {
  if (!timestamp) return "";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(timestamp));
}

function labelForType(type) {
  return { QUOTE: "글귀", LINK: "링크", VIDEO: "영상" }[type] || type || "";
}

function labelForEvent(type) {
  return { SURFACED: "노출", SHOWN: "열람", CONFIRMED: "확인" }[type] || type || "";
}

function downloadSnapshot() {
  const blob = new Blob([JSON.stringify(state.snapshot, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `app-good-words-server-${new Date().toISOString().slice(0, 10)}.json`;
  anchor.click();
  URL.revokeObjectURL(url);
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.add("show");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 1800);
}

function escapeHtml(value = "") {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function attr(value = "") {
  return escapeHtml(value);
}
