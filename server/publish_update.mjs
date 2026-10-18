#!/usr/bin/env node
/**
 * 휴대폰이 받아 갈 설치 파일을 게시합니다.
 *
 * 앱은 `/update/latest.json`을 읽고 거기 적힌 이름으로 APK를 받아, **해시를 맞춰 본 뒤에만**
 * 설치 화면으로 넘깁니다. 그래서 여기서 쓰는 꼴과 앱 `AppUpdateJson`이 읽는 꼴이 같아야
 * 합니다 — 한쪽만 바꾸면 업데이트가 영영 안 보입니다.
 *
 * **Node 표준 라이브러리만 씁니다.** 서버는 Windows에서도 macOS에서도 돌아가야 하는데,
 * 게시가 PowerShell에만 있으면 맥에 올려 둔 서버는 새 버전을 내놓을 수 없습니다.
 * `publish_update.ps1`은 이 파일을 부르기만 합니다 — 같은 일을 두 벌 적어 두면
 * 한쪽만 고쳐졌을 때 두 운영체제가 서로 다른 `latest.json`을 씁니다.
 *
 * ```sh
 * node server/publish_update.mjs                        # 저장소에서 바로 돌릴 때
 * node server/publish_update.mjs --web-root <설치한 web 폴더>
 * ```
 */
import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { copyFile, mkdir, readFile, rename, stat, writeFile } from "node:fs/promises";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

/** 앱이 받아 주는 이름입니다(`AppUpdateJson.FILE_NAME`). 여기서 먼저 지킵니다. */
const fileNamePattern = /^app-good-words-[A-Za-z0-9._-]+\.apk$/;
/** 이름에 그대로 들어가는 값이라 셸과 주소에서 뜻이 달라질 글자는 들이지 않습니다. */
const versionNamePattern = /^[A-Za-z0-9._-]+$/;
const applicationId = "com.codex.appgoodwords";

export const defaultApkPath = join(here, "..", "app", "build", "outputs", "apk", "debug", "app-debug.apk");
export const defaultWebRoot = join(here, "web");

/**
 * APK 한 벌을 `<webRoot>/update/`에 두고 목록을 갈아 끼웁니다.
 *
 * @returns 적어 넣은 `latest.json`의 내용
 */
export async function publishUpdate({ apkPath = defaultApkPath, webRoot = defaultWebRoot } = {}) {
  const apk = resolve(apkPath);
  const apkStat = await stat(apk).catch(() => null);
  if (!apkStat?.isFile()) {
    throw new Error(`설치 파일을 찾지 못했습니다: ${apk}\n먼저 빌드해 주세요(gradlew assembleDebug).`);
  }

  const release = await readRelease(apk);
  const sha256 = await hashFile(apk);
  const fileName = `app-good-words-${release.versionName}-${release.versionCode}-${sha256.slice(0, 8)}.apk`;
  if (!fileNamePattern.test(fileName)) {
    throw new Error(`앱이 받아 주지 않을 이름입니다: ${fileName}`);
  }

  const updateRoot = resolve(join(webRoot, "update"));
  await mkdir(updateRoot, { recursive: true });
  await copyFile(apk, join(updateRoot, fileName));

  const manifest = {
    versionName: release.versionName,
    versionCode: release.versionCode,
    publishedAt: new Date().toISOString(),
    fileName,
    sizeBytes: apkStat.size,
    sha256,
  };

  // 파일을 다 옮긴 뒤에 목록을 바꿉니다. 받는 중인 사람에게 아직 없는 파일을 안내하지 않습니다.
  const manifestPath = join(updateRoot, "latest.json");
  const temporaryPath = join(updateRoot, "latest.json.tmp");
  await writeFile(temporaryPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  await rename(temporaryPath, manifestPath);

  // 이전 파일은 지우지 않습니다. 지금 내려받는 중인 사람의 주소가 사라집니다.
  return manifest;
}

/**
 * 빌드가 남긴 쪽지에서 버전을 읽습니다.
 *
 * APK 이름만 보고 짐작하지 않습니다. 디버그와 릴리스가 같은 이름이고, 버전 번호는 이름에
 * 들어 있지도 않습니다. 다른 앱의 APK를 잘못 가리켰을 때도 여기서 걸립니다.
 */
async function readRelease(apk) {
  const metadataPath = join(dirname(apk), "output-metadata.json");
  const raw = await readFile(metadataPath, "utf8").catch(() => null);
  if (raw === null) throw new Error(`빌드 정보를 찾지 못했습니다: ${metadataPath}`);

  let metadata;
  try {
    metadata = JSON.parse(raw);
  } catch {
    throw new Error(`빌드 정보를 읽지 못했습니다: ${metadataPath}`);
  }
  if (metadata?.applicationId !== applicationId) {
    throw new Error(`${applicationId} APK가 아닙니다.`);
  }

  const elements = (metadata.elements || []).filter((element) => element?.outputFile === basename(apk));
  if (elements.length !== 1) throw new Error("APK의 버전 정보를 확인할 수 없습니다.");

  const versionName = String(elements[0].versionName ?? "");
  const versionCode = Number(elements[0].versionCode);
  if (!versionNamePattern.test(versionName)) throw new Error(`버전 이름을 믿을 수 없습니다: ${versionName}`);
  if (!Number.isInteger(versionCode) || versionCode <= 0) throw new Error("버전 번호가 올바르지 않습니다.");
  return { versionName, versionCode };
}

/** 25MB를 통째로 메모리에 올리지 않고 흘려보내며 셉니다. */
function hashFile(path) {
  return new Promise((resolvePromise, rejectPromise) => {
    const hash = createHash("sha256");
    createReadStream(path)
      .on("data", (chunk) => hash.update(chunk))
      .on("error", rejectPromise)
      .on("end", () => resolvePromise(hash.digest("hex")));
  });
}

function parseArgs(args) {
  const parsed = { apkPath: defaultApkPath, webRoot: defaultWebRoot };
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--apk") parsed.apkPath = args[++index];
    else if (arg === "--web-root") parsed.webRoot = args[++index];
    else throw new Error(`모르는 인자입니다: ${arg}`);
  }
  return parsed;
}

// 직접 돌렸을 때만 게시합니다. 시험은 함수만 불러다 씁니다.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const manifest = await publishUpdate(parseArgs(process.argv.slice(2)));
    console.log(`게시 완료: 버전 ${manifest.versionName}(${manifest.versionCode}), ${manifest.fileName}`);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
