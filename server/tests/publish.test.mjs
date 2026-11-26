import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtemp, mkdir, readFile, rm, utimes, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

import { publishUpdate, releaseHistoryXml, writeReleaseHistory } from "../publish_update.mjs";

/**
 * 설치 파일 게시.
 *
 * **Node로 적어 둔 이유가 여기에 있습니다.** 게시가 PowerShell에만 있으면 맥에 올려 둔
 * 서버는 새 버전을 내놓을 수 없고, 이 시험도 Windows에서만 돌 수 있습니다.
 *
 * 지키려는 것은 하나입니다 — 앱 `AppUpdateJson`이 읽을 수 있는 `latest.json`이 나와야 합니다.
 */
async function workspace(callback) {
  const root = await mkdtemp(join(tmpdir(), "agw-publish-"));
  try {
    return await callback(root);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}

async function fakeApk(root, { versionName = "1.9.0", versionCode = 12, applicationId = "com.codex.appgoodwords" } = {}) {
  const apkDir = join(root, "build");
  await mkdir(apkDir, { recursive: true });
  const apkPath = join(apkDir, "app-debug.apk");
  await writeFile(apkPath, "설치 파일인 척하는 바이트들", "utf8");
  await writeFile(
    join(apkDir, "output-metadata.json"),
    JSON.stringify({
      version: 3,
      applicationId,
      elements: [{ type: "SINGLE", outputFile: "app-debug.apk", versionName, versionCode }],
    }),
    "utf8",
  );
  return apkPath;
}

test("게시하면 버전과 해시가 담긴 이름으로 두고 latest.json을 바꾼다", async () => {
  await workspace(async (root) => {
    const apkPath = await fakeApk(root);
    const webRoot = join(root, "web");

    const manifest = await publishUpdate({ apkPath, webRoot });

    const expectedHash = createHash("sha256").update(await readFile(apkPath)).digest("hex");
    assert.equal(manifest.sha256, expectedHash);
    assert.equal(manifest.versionName, "1.9.0");
    assert.equal(manifest.versionCode, 12);
    assert.equal(manifest.fileName, `app-good-words-1.9.0-12-${expectedHash.slice(0, 8)}.apk`);
    assert.equal(manifest.sizeBytes, (await readFile(apkPath)).length);

    // 앱이 읽는 자리에 그대로 있어야 합니다.
    const stored = JSON.parse(await readFile(join(webRoot, "update", "latest.json"), "utf8"));
    assert.deepEqual(stored, manifest);
    const copied = await readFile(join(webRoot, "update", manifest.fileName));
    assert.equal(createHash("sha256").update(copied).digest("hex"), expectedHash);
  });
});

test("앱이 받아 주는 이름과 꼴로만 적는다", async () => {
  await workspace(async (root) => {
    const apkPath = await fakeApk(root);
    const manifest = await publishUpdate({ apkPath, webRoot: join(root, "web") });

    // 앱 `AppUpdateJson`이 그대로 들고 있는 규칙입니다. 한쪽만 바뀌면 업데이트가 안 보입니다.
    assert.match(manifest.fileName, /^app-good-words-[A-Za-z0-9._-]+\.apk$/);
    assert.match(manifest.sha256, /^[a-f0-9]{64}$/);
    assert.ok(manifest.sizeBytes > 0);
    assert.ok(manifest.versionCode > 0);
    assert.ok(!Number.isNaN(Date.parse(manifest.publishedAt)));
  });
});

test("먼저 올린 것은 지우지 않는다", async () => {
  await workspace(async (root) => {
    const webRoot = join(root, "web");
    const first = await publishUpdate({ apkPath: await fakeApk(root), webRoot });

    const apkPath = await fakeApk(root, { versionName: "1.9.1", versionCode: 13 });
    await writeFile(apkPath, "다음 판", "utf8");
    const second = await publishUpdate({ apkPath, webRoot });

    // 지금 내려받는 중인 사람의 주소가 사라지면 안 됩니다.
    await readFile(join(webRoot, "update", first.fileName));
    await readFile(join(webRoot, "update", second.fileName));
    const stored = JSON.parse(await readFile(join(webRoot, "update", "latest.json"), "utf8"));
    assert.equal(stored.versionCode, 13);
  });
});

test("다른 앱의 APK는 게시하지 않는다", async () => {
  await workspace(async (root) => {
    const apkPath = await fakeApk(root, { applicationId: "com.example.other" });
    await assert.rejects(() => publishUpdate({ apkPath, webRoot: join(root, "web") }), /APK가 아닙니다/);
  });
});

test("버전 정보가 없으면 게시하지 않는다", async () => {
  await workspace(async (root) => {
    const apkDir = join(root, "build");
    await mkdir(apkDir, { recursive: true });
    const apkPath = join(apkDir, "app-debug.apk");
    await writeFile(apkPath, "bytes", "utf8");

    await assert.rejects(() => publishUpdate({ apkPath, webRoot: join(root, "web") }), /빌드 정보를 찾지 못했습니다/);
  });
});

test("빌드하지 않았으면 무엇을 해야 하는지 알려 준다", async () => {
  await workspace(async (root) => {
    await assert.rejects(
      () => publishUpdate({ apkPath: join(root, "없는.apk"), webRoot: join(root, "web") }),
      /assembleDebug/,
    );
  });
});

/*
 * 배포 이력(사용자가 정함, 2026-11-26) — 다른 시스템에 넘기는 `update/releases.json`과 같은 값의 `releases.xml`.
 * 지키려는 것: 게시마다 판이 하나씩 쌓이고 한 번 적은 판은 바뀌지 않으며, 이력이 생기기 전에 게시한 판은 남은 APK로 채웁니다.
 */
const sha256Of = (bytes) => createHash("sha256").update(bytes).digest("hex");
const readHistory = async (webRoot) => JSON.parse(await readFile(join(webRoot, "update", "releases.json"), "utf8"));
const readHistoryXml = (webRoot) => readFile(join(webRoot, "update", "releases.xml"), "utf8");

/** 이력 파일이 생기기 전에 게시해 둔 APK처럼 둡니다. [sha8]을 주면 이름 속 해시를 일부러 틀리게 짓습니다. */
async function publishedApk(webRoot, { versionName, versionCode, body, mtime, sha8 }) {
  const bytes = Buffer.from(body, "utf8");
  const sha256 = sha256Of(bytes);
  const fileName = `app-good-words-${versionName}-${versionCode}-${sha8 ?? sha256.slice(0, 8)}.apk`;
  await mkdir(join(webRoot, "update"), { recursive: true });
  const path = join(webRoot, "update", fileName);
  await writeFile(path, bytes);
  if (mtime) await utimes(path, mtime, mtime);
  return { fileName, sha256, sizeBytes: bytes.length };
}

test("게시하면 배포 이력을 JSON과 같은 값의 XML로 함께 쓴다", async () => {
  await workspace(async (root) => {
    const webRoot = join(root, "web");
    const manifest = await publishUpdate({ apkPath: await fakeApk(root), webRoot });

    const history = await readHistory(webRoot);
    assert.equal(history.formatVersion, 1);
    assert.equal(history.app, "com.codex.appgoodwords");
    assert.ok(!Number.isNaN(Date.parse(history.generatedAt)));
    assert.equal(history.latestVersionCode, 12);
    // 판 한 줄은 latest.json과 같은 칸·같은 값입니다(게시 시각까지).
    assert.deepEqual(history.releases, [manifest]);
    assert.deepEqual(Object.keys(history.releases[0]), Object.keys(manifest));

    const xml = await readHistoryXml(webRoot);
    assert.equal(xml, releaseHistoryXml(history));
    assert.match(xml, /<latestVersionCode>12<\/latestVersionCode>/);
    assert.match(xml, new RegExp(`<publishedAt>${manifest.publishedAt}</publishedAt>`));
    assert.match(xml, new RegExp(`<sha256>${manifest.sha256}</sha256>`));
  });
});

test("다음 게시는 앞의 판을 그대로 둔 채 맨 앞에 더한다", async () => {
  await workspace(async (root) => {
    const webRoot = join(root, "web");
    const first = await publishUpdate({ apkPath: await fakeApk(root), webRoot });
    const apkPath = await fakeApk(root, { versionName: "1.9.1", versionCode: 13 });
    await writeFile(apkPath, "다음 판", "utf8");
    const second = await publishUpdate({ apkPath, webRoot });

    const history = await readHistory(webRoot);
    assert.equal(history.latestVersionCode, 13);
    assert.deepEqual(history.releases, [second, first]);
    assert.equal((await readHistoryXml(webRoot)).match(/<release>/g).length, 2);
  });
});

test("같은 파일을 다시 게시해도 처음 내놓은 시각을 남긴다", async () => {
  await workspace(async (root) => {
    const webRoot = join(root, "web");
    const apkPath = await fakeApk(root);
    const first = await publishUpdate({ apkPath, webRoot });
    await new Promise((done) => setTimeout(done, 5));
    const again = await publishUpdate({ apkPath, webRoot });

    assert.equal(again.fileName, first.fileName);
    assert.notEqual(again.publishedAt, first.publishedAt);
    assert.deepEqual((await readHistory(webRoot)).releases, [first]);
  });
});

test("APK를 지워도 이력의 판은 남는다", async () => {
  await workspace(async (root) => {
    const webRoot = join(root, "web");
    const first = await publishUpdate({ apkPath: await fakeApk(root), webRoot });
    await rm(join(webRoot, "update", first.fileName));
    const apkPath = await fakeApk(root, { versionName: "1.9.1", versionCode: 13 });
    await writeFile(apkPath, "다음 판", "utf8");
    const second = await publishUpdate({ apkPath, webRoot });

    assert.deepEqual((await readHistory(webRoot)).releases, [second, first]);
  });
});

test("이력 파일이 없으면 남아 있는 APK로 지난 판을 채운다", async () => {
  await workspace(async (root) => {
    const webRoot = join(root, "web");
    const old = await publishedApk(webRoot, {
      versionName: "1.10.0",
      versionCode: 13,
      body: "옛 판",
      mtime: new Date("2026-10-18T15:36:00.000Z"),
    });
    const current = await publishedApk(webRoot, {
      versionName: "1.11.0",
      versionCode: 14,
      body: "지금 판",
      mtime: new Date("2026-10-19T14:30:00.000Z"),
    });
    // 지금 내놓은 판은 파일 시각이 아니라 latest.json에 적힌 시각을 씁니다.
    const latest = {
      versionName: "1.11.0",
      versionCode: 14,
      publishedAt: "2026-10-19T14:30:00.250Z",
      fileName: current.fileName,
      sizeBytes: current.sizeBytes,
      sha256: current.sha256,
    };
    await writeFile(join(webRoot, "update", "latest.json"), `${JSON.stringify(latest, null, 2)}\n`, "utf8");

    const { history, notes } = await writeReleaseHistory({ webRoot, now: new Date("2026-11-26T03:10:00.000Z") });

    assert.deepEqual(notes, []);
    assert.equal(history.generatedAt, "2026-11-26T03:10:00.000Z");
    assert.equal(history.latestVersionCode, 14);
    assert.deepEqual(history.releases, [
      latest,
      {
        versionName: "1.10.0",
        versionCode: 13,
        publishedAt: "2026-10-18T15:36:00.000Z",
        fileName: old.fileName,
        sizeBytes: old.sizeBytes,
        sha256: old.sha256,
      },
    ]);
    assert.deepEqual(await readHistory(webRoot), history);
    assert.equal(await readHistoryXml(webRoot), releaseHistoryXml(history));
  });
});

test("이름 속 해시가 내용과 다른 APK는 이력에 넣지 않는다", async () => {
  await workspace(async (root) => {
    const webRoot = join(root, "web");
    await publishedApk(webRoot, { versionName: "1.10.0", versionCode: 13, body: "바뀐 내용", sha8: "00000000" });

    const { history, notes } = await writeReleaseHistory({ webRoot });

    assert.deepEqual(history.releases, []);
    assert.equal(notes.length, 1);
    assert.match(notes[0], /app-good-words-1\.10\.0-13-00000000\.apk/);
    // 내놓은 판이 없으면 JSON은 null, XML은 그 요소를 두지 않습니다.
    assert.equal(history.latestVersionCode, null);
    assert.doesNotMatch(await readHistoryXml(webRoot), /latestVersionCode/);
  });
});

test("읽을 수 없는 이력 파일은 남은 APK로 다시 만든다", async () => {
  await workspace(async (root) => {
    const webRoot = join(root, "web");
    const kept = await publishedApk(webRoot, { versionName: "1.10.0", versionCode: 13, body: "옛 판" });
    await writeFile(join(webRoot, "update", "releases.json"), "{ 반쯤 쓴", "utf8");

    const { history, notes } = await writeReleaseHistory({ webRoot });

    assert.deepEqual(
      history.releases.map((release) => release.fileName),
      [kept.fileName],
    );
    assert.match(notes.join("\n"), /releases\.json을 읽을 수 없어/);
    assert.deepEqual(await readHistory(webRoot), history);
  });
});

test("XML은 JSON 키와 같은 이름의 요소에 같은 값을 담고 글자를 이스케이프한다", () => {
  const xml = releaseHistoryXml({
    formatVersion: 1,
    app: "a&b<c>\"d'",
    generatedAt: "2026-11-26T03:10:00.000Z",
    latestVersionCode: 39,
    releases: [
      {
        versionName: "1.26.1",
        versionCode: 39,
        publishedAt: "2026-11-25T07:08:56.650Z",
        fileName: "app-good-words-1.26.1-39-628eab2b.apk",
        sizeBytes: 27933525,
        sha256: "628eab2b05fa6bc6bef641b321edee8a772398c156e5bd88d6dd38b3f43399b9",
      },
    ],
  });

  assert.equal(
    xml,
    [
      '<?xml version="1.0" encoding="UTF-8"?>',
      "<releaseHistory>",
      "  <formatVersion>1</formatVersion>",
      "  <app>a&amp;b&lt;c&gt;&quot;d&apos;</app>",
      "  <generatedAt>2026-11-26T03:10:00.000Z</generatedAt>",
      "  <latestVersionCode>39</latestVersionCode>",
      "  <releases>",
      "    <release>",
      "      <versionName>1.26.1</versionName>",
      "      <versionCode>39</versionCode>",
      "      <publishedAt>2026-11-25T07:08:56.650Z</publishedAt>",
      "      <fileName>app-good-words-1.26.1-39-628eab2b.apk</fileName>",
      "      <sizeBytes>27933525</sizeBytes>",
      "      <sha256>628eab2b05fa6bc6bef641b321edee8a772398c156e5bd88d6dd38b3f43399b9</sha256>",
      "    </release>",
      "  </releases>",
      "</releaseHistory>",
      "",
    ].join("\n"),
  );
});

test("--history-only는 APK 없이 배포 이력만 다시 쓴다", async () => {
  await workspace(async (root) => {
    const webRoot = join(root, "web");
    const kept = await publishedApk(webRoot, { versionName: "1.10.0", versionCode: 13, body: "옛 판" });
    const script = fileURLToPath(new URL("../publish_update.mjs", import.meta.url));

    const { stdout } = await promisify(execFile)(process.execPath, [script, "--history-only", "--web-root", webRoot]);

    assert.match(stdout, /배포 이력을 썼습니다: 판 1개, 지금 내놓은 판 없음/);
    assert.deepEqual(
      (await readHistory(webRoot)).releases.map((release) => release.fileName),
      [kept.fileName],
    );
    assert.match(await readHistoryXml(webRoot), new RegExp(`<fileName>${kept.fileName}</fileName>`));
  });
});
