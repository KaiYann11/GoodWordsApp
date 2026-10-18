import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

import { publishUpdate } from "../publish_update.mjs";

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
