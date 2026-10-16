import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import vm from "node:vm";

const here = dirname(fileURLToPath(import.meta.url));

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
