import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import vm from "node:vm";

const here = dirname(fileURLToPath(import.meta.url));

/** 기분 화면의 실제 렌더링으로 날짜 묶음·시각·읽기 전용을 함께 봅니다. */
async function renderMoods(logs) {
  const source = await readFile(join(here, "../web/app.js"), "utf8");
  const app = { innerHTML: "" };
  const context = vm.createContext({ app, state: { snapshot: { moodLogs: logs } }, empty: value => value });
  vm.runInContext(source.slice(source.indexOf("const moodOptions ="), source.indexOf("const app =")), context);
  vm.runInContext(source.slice(source.indexOf("function escapeHtml("), source.indexOf("function attr(")), context);
  vm.runInContext(source.slice(source.indexOf("function renderMoods("), source.indexOf("function renderGrowth(")), context);
  context.renderMoods();
  return { html: app.innerHTML, days: context.moodDays(logs), time: context.moodTime };
}

test("기분은 날짜별로 묶고 하루 안은 수정 시각 순으로 읽기만 한다", async () => {
  const at = (time) => new Date(`2026-10-29T${time}:00`).getTime();
  const first = { syncId: "first", entryDate: "2026-10-29", mood: "GOOD", createdAt: at("09:00"), updatedAt: at("09:00") };
  const old = { ...first, syncId: "old", mood: "TIRED", updatedAt: at("18:30") };
  const yesterday = { ...first, syncId: "yesterday", entryDate: "2026-10-28" };
  const { html, days, time } = await renderMoods([old, yesterday, first]);

  assert.match(html, /기분 2일/);
  assert.equal((html.match(/<h3>2026-10-29<\/h3>/g) || []).length, 1);
  assert.ok(html.indexOf("2026-10-29") < html.indexOf("2026-10-28"));
  assert.ok(html.indexOf("09:00") < html.indexOf("18:30"));
  assert.match(html, /🙂 좋음/);
  assert.match(html, /😪 지침/);
  assert.equal(time(old), "18:30");
  assert.equal(days[0][1].length, 2);
  assert.doesNotMatch(html, /<button|<input|<form/);
});

test("같은 시각의 기분 차례도 앱과 같고 알 수 없는 값은 HTML로 실행되지 않는다", async () => {
  const first = { syncId: "a", entryDate: "2026-10-29", mood: "<script>bad</script>", createdAt: 1_000, updatedAt: 2_000 };
  const last = { ...first, syncId: "z", mood: "GOOD" };
  const { html, days } = await renderMoods([last, first]);
  assert.equal(days[0][1][0].syncId, "a");
  assert.equal(days[0][1][1].syncId, "z");
  assert.match(html, /&lt;script&gt;bad&lt;\/script&gt;/);
  assert.doesNotMatch(html, /<script>/);
  assert.match((await renderMoods([])).html, /아직 찍어 둔 기분이 없습니다/);
});

/**
 * 웹의 루틴 셈을 떼어 와 그대로 돌려 봅니다.
 *
 * 앱 `RoutineStatsCalculator`(와 그 시험 `RoutineStatsTest`)와 **같은 규칙**이어야 합니다.
 * 한쪽만 고치면 같은 기록을 두고 폰과 웹이 다른 날수를 말하게 됩니다.
 */
async function loadRoutineStats() {
  const source = await readFile(join(here, "../web/app.js"), "utf8");
  const start = source.indexOf("/** 요일 이름.");
  const end = source.indexOf("function routineCard(");
  assert.ok(start >= 0 && end > start, "루틴 셈 토막을 찾지 못했습니다.");
  const context = vm.createContext({});
  vm.runInContext(source.slice(start, end), context);
  return {
    routineStatsOf: context.routineStatsOf,
    // const 선언은 컨텍스트 객체에 올라오지 않아 안에서 꺼냅니다.
    weekdayLabels: vm.runInContext("weekdayLabels", context),
  };
}

/** 2026-10-16은 금요일입니다. */
const today = "2026-10-16";

function checksOn(...days) {
  return days.map(([iso, hour = 9]) => ({ checkedAt: new Date(`${iso}T${String(hour).padStart(2, "0")}:00:00`).getTime() }));
}

test("하루에 세 번 눌러도 하루로 센다", async () => {
  const { routineStatsOf } = await loadRoutineStats();
  const stats = routineStatsOf(checksOn(["2026-10-16", 7], ["2026-10-16", 13], ["2026-10-16", 21]), today);

  assert.equal(stats.totalChecks, 3);
  assert.equal(stats.doneDays, 1);
  assert.equal(stats.currentStreakDays, 1);
});

test("오늘이 아직 비어 있어도 어제까지 이어졌으면 이어 온 것으로 본다", async () => {
  const { routineStatsOf } = await loadRoutineStats();
  const stats = routineStatsOf(checksOn(["2026-10-14"], ["2026-10-15"]), today);

  assert.equal(stats.currentStreakDays, 2);
});

test("하루가 통째로 비면 끊긴다", async () => {
  const { routineStatsOf } = await loadRoutineStats();
  const stats = routineStatsOf(checksOn(["2026-10-13"], ["2026-10-14"]), today);

  assert.equal(stats.currentStreakDays, 0);
  assert.equal(stats.doneDays, 2);
});

test("으뜸 요일은 유일할 때만 말한다", async () => {
  const { routineStatsOf, weekdayLabels } = await loadRoutineStats();

  // 10월 5일·12일이 월요일, 6일이 화요일입니다.
  const named = routineStatsOf(checksOn(["2026-10-05"], ["2026-10-12"], ["2026-10-06"]), today);
  assert.equal(weekdayLabels[named.topWeekday], "월");

  // 월·화가 나란히 둘이면 그중 하나를 집어 말할 수 없습니다.
  const tied = routineStatsOf(
    checksOn(["2026-10-05"], ["2026-10-12"], ["2026-10-06"], ["2026-10-13"]),
    today,
  );
  assert.equal(tied.topWeekday, null);
});

test("한 번뿐인 요일은 으뜸이라 부르지 않는다", async () => {
  const { routineStatsOf } = await loadRoutineStats();
  const stats = routineStatsOf(checksOn(["2026-10-12"]), today);

  assert.equal(stats.topWeekday, null);
  assert.equal(stats.doneDays, 1);
});

test("기록이 하나도 없으면 아무것도 지어내지 않는다", async () => {
  const { routineStatsOf } = await loadRoutineStats();
  const stats = routineStatsOf([], today);

  assert.equal(stats.totalChecks, 0);
  assert.equal(stats.doneDays, 0);
  assert.equal(stats.currentStreakDays, 0);
  assert.equal(stats.topWeekday, null);
});

/**
 * 카드에 실제로 붙는지까지 봅니다.
 *
 * 셈이 맞아도 틀을 잘못 쓰면 화면에는 아무것도 안 뜹니다. 틀이 쓰는 몇 가지 도우미만
 * 바깥에서 끼워 넣고 `routineCard`를 그대로 불러 봅니다.
 */
async function loadRoutineCard() {
  const source = await readFile(join(here, "../web/app.js"), "utf8");
  const start = source.indexOf("/** 요일 이름.");
  const end = source.indexOf("function memoCard(");
  assert.ok(start >= 0 && end > start, "루틴 카드 토막을 찾지 못했습니다.");
  const context = vm.createContext({
    escapeHtml: (value) => String(value ?? ""),
    memoCard: () => "",
    isToday: () => false,
    // 이 토막 밖에 있는 도우미입니다. `web/app.js`의 것과 같은 규칙으로 끼워 넣습니다.
    todayIso: () => {
      const now = new Date();
      const month = String(now.getMonth() + 1).padStart(2, "0");
      const day = String(now.getDate()).padStart(2, "0");
      return `${now.getFullYear()}-${month}-${day}`;
    },
  });
  vm.runInContext(source.slice(start, end), context);
  return context.routineCard;
}

test("루틴 카드에 연속 날수와 으뜸 요일이 붙는다", async () => {
  const routineCard = await loadRoutineCard();
  // 오늘을 끼지 않고도 보여야 합니다 — 오늘이 비었다고 어제까지의 날수를 숨기지 않습니다.
  const today = new Date();
  const dayBefore = (back) => {
    const date = new Date(today);
    date.setDate(date.getDate() - back);
    date.setHours(9, 0, 0, 0);
    return { checkedAt: date.getTime() };
  };
  const checks = [dayBefore(1), dayBefore(2), dayBefore(3), dayBefore(8), dayBefore(15)];

  const html = routineCard({ id: 1, title: "아침 산책" }, new Map([[1, checks]]), new Map(), 0, 1);

  assert.match(html, /3일 연속/);
  assert.match(html, /실천한 날 5일/);
  assert.match(html, /전체 5회/);
  // 1·8·15일 전은 같은 요일입니다. 셋이면 으뜸이 유일합니다.
  assert.match(html, /요일에 많이/);
});
