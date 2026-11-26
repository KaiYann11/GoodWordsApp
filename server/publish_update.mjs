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
 * 게시할 때마다 다른 시스템에 넘길 **배포 이력**(`releases.json`·`releases.xml`)도 다시 씁니다 —
 * 아래 [writeReleaseHistory].
 *
 * ```sh
 * node server/publish_update.mjs                        # 저장소에서 바로 돌릴 때
 * node server/publish_update.mjs --web-root <설치한 web 폴더>
 * node server/publish_update.mjs --history-only         # APK 없이 배포 이력만 다시 씀
 * ```
 */
import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { copyFile, mkdir, readdir, readFile, rename, stat, writeFile } from "node:fs/promises";
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
  await replaceFile(join(updateRoot, "latest.json"), `${JSON.stringify(manifest, null, 2)}\n`);

  // 휴대폰이 받을 것은 위에서 다 놓였습니다. 여기서 실패하면 게시는 된 채로, 무엇이 안 됐는지만 알립니다.
  try {
    const { notes } = await writeReleaseHistory({ webRoot });
    for (const note of notes) console.warn(note);
  } catch (error) {
    throw new Error(`게시는 끝났지만 배포 이력을 쓰지 못했습니다: ${error.message}\n고친 뒤 --history-only로 다시 쓰세요.`);
  }

  // 이전 파일은 지우지 않습니다. 지금 내려받는 중인 사람의 주소가 사라집니다.
  return manifest;
}

/**
 * 다른 시스템에 넘길 배포 이력 — `<webRoot>/update/releases.json`과 같은 값의 `releases.xml`.
 *
 * 사용자가 정함(2026-11-26): "수신 배포 이력을 타 시스템에 전달하기 위한 파일"을 물어, **휴대폰에 내놓은 판 목록**을
 * JSON과 XML 두 꼴로 두기로 했습니다. 휴대폰이 무엇을 언제 받았는지는 담지 않습니다 — 앱이 알려 주지 않아 서버가 모릅니다.
 *
 * - **이력은 `releases.json` 그 자체입니다.** 부를 때마다 읽어 새 판을 더하고 두 파일을 다시 씁니다. 한 번 적은 판은
 *   고치지 않습니다 — 같은 파일을 다시 게시해도 처음 내놓은 시각이 남고, APK를 지워도 판은 남습니다.
 * - **빈 곳은 `update/`에 남은 APK로 채웁니다.** 이력 파일이 없거나(처음) 읽을 수 없거나 이력에 없는 APK가 있으면, 이름에서
 *   버전·번호를 읽고 해시·크기를 재어 더합니다. 이름 속 해시 앞 8자가 잰 해시와 다르면 넣지 않습니다. 게시 시각은 그 파일의
 *   고친 시각입니다 — 게시는 APK를 새로 복사해 두므로 복사를 마친 때가 곧 게시한 때입니다(1.26.1은 밀리초까지 같았음). 지금
 *   `latest.json`이 가리키는 판은 거기 적힌 시각을 씁니다.
 * - **다른 시스템이 읽는 꼴입니다.** 칸 이름을 바꾸거나 빼지 말고 더하기만 하세요. 바꿔야 하면 `formatVersion`을 올립니다.
 *   XML은 JSON 키와 같은 이름의 요소로 같은 값을 담습니다(JSON의 null은 요소를 두지 않습니다).
 * - `latest.json`처럼 키 없이 내줍니다(서버는 tailnet 안에서만 열려 있습니다). 개인 기록은 하나도 없습니다.
 *
 * @returns 적어 넣은 이력과, 빼거나 다시 만든 까닭(`notes`)
 */
export async function writeReleaseHistory({ webRoot = defaultWebRoot, now = new Date() } = {}) {
  const updateRoot = resolve(join(webRoot, "update"));
  await mkdir(updateRoot, { recursive: true });
  const notes = [];
  const byFileName = new Map();
  const add = (entry) => {
    if (entry && !byFileName.has(entry.fileName)) byFileName.set(entry.fileName, entry);
  };

  const stored = await readJsonFile(join(updateRoot, "releases.json"));
  if (stored !== undefined) {
    const list = Array.isArray(stored?.releases) ? stored.releases : null;
    if (!list) notes.push("releases.json을 읽을 수 없어 남아 있는 APK로 다시 만들었습니다.");
    for (const item of list || []) {
      const entry = releaseEntry(item);
      if (entry) add(entry);
      else notes.push(`releases.json의 판 하나를 읽을 수 없어 뺐습니다(${typeof item?.fileName === "string" ? item.fileName : "이름 없음"}).`);
    }
  }

  const latest = releaseEntry(await readJsonFile(join(updateRoot, "latest.json")));
  add(latest);

  for (const name of (await readdir(updateRoot)).sort()) {
    const match = releaseFileNamePattern.exec(name);
    if (!match || byFileName.has(name)) continue;
    const path = join(updateRoot, name);
    const fileStat = await stat(path).catch(() => null);
    if (!fileStat?.isFile()) continue;
    const sha256 = await hashFile(path);
    if (!sha256.startsWith(match[3])) {
      notes.push(`이름 속 해시와 내용이 다른 파일은 넣지 않았습니다: ${name}`);
      continue;
    }
    add({
      versionName: match[1],
      versionCode: Number(match[2]),
      publishedAt: fileStat.mtime.toISOString(),
      fileName: name,
      sizeBytes: fileStat.size,
      sha256,
    });
  }

  const history = {
    formatVersion: releaseHistoryFormatVersion,
    app: applicationId,
    generatedAt: now.toISOString(),
    latestVersionCode: latest ? latest.versionCode : null,
    // 새 판이 앞입니다. 같은 번호를 다시 빌드해 올렸으면 나중 것이 앞입니다.
    releases: [...byFileName.values()].sort(
      (a, b) =>
        b.versionCode - a.versionCode ||
        Date.parse(b.publishedAt) - Date.parse(a.publishedAt) ||
        (a.fileName < b.fileName ? -1 : 1),
    ),
  };
  // 이력의 본체인 JSON을 나중에 바꿉니다. 그사이 멈추면 다음에 부를 때 둘 다 다시 씁니다.
  await replaceFile(join(updateRoot, "releases.xml"), releaseHistoryXml(history));
  await replaceFile(join(updateRoot, "releases.json"), `${JSON.stringify(history, null, 2)}\n`);
  return { history, notes };
}

/** JSON과 같은 값을 같은 이름의 요소로 옮깁니다. */
export function releaseHistoryXml(history) {
  const lines = ['<?xml version="1.0" encoding="UTF-8"?>', "<releaseHistory>"];
  lines.push(`  <formatVersion>${xmlText(history.formatVersion)}</formatVersion>`);
  lines.push(`  <app>${xmlText(history.app)}</app>`);
  lines.push(`  <generatedAt>${xmlText(history.generatedAt)}</generatedAt>`);
  if (history.latestVersionCode != null) {
    lines.push(`  <latestVersionCode>${xmlText(history.latestVersionCode)}</latestVersionCode>`);
  }
  lines.push("  <releases>");
  for (const release of history.releases) {
    lines.push("    <release>");
    for (const field of releaseFields) lines.push(`      <${field}>${xmlText(release[field])}</${field}>`);
    lines.push("    </release>");
  }
  lines.push("  </releases>", "</releaseHistory>", "");
  return lines.join("\n");
}

/** 판 하나에 적는 칸과 그 차례입니다. `latest.json`과 같은 이름·같은 차례입니다. */
const releaseFields = ["versionName", "versionCode", "publishedAt", "fileName", "sizeBytes", "sha256"];
export const releaseHistoryFormatVersion = 1;
/** [publishUpdate]가 짓는 이름: `app-good-words-<versionName>-<versionCode>-<해시 앞 8자>.apk`. */
const releaseFileNamePattern = /^app-good-words-([A-Za-z0-9._-]+)-([1-9][0-9]*)-([0-9a-f]{8})\.apk$/;
const sha256Pattern = /^[a-f0-9]{64}$/;

/** 믿을 수 있는 판 한 줄로 고쳐 담습니다. 칸 하나라도 어긋나면 null입니다. */
function releaseEntry(item) {
  if (!item || typeof item !== "object") return null;
  const { versionName, versionCode, publishedAt, fileName, sizeBytes, sha256 } = item;
  const time = typeof publishedAt === "string" ? Date.parse(publishedAt) : Number.NaN;
  const valid =
    typeof versionName === "string" &&
    versionNamePattern.test(versionName) &&
    Number.isSafeInteger(versionCode) &&
    versionCode > 0 &&
    !Number.isNaN(time) &&
    typeof fileName === "string" &&
    fileNamePattern.test(fileName) &&
    Number.isSafeInteger(sizeBytes) &&
    sizeBytes > 0 &&
    typeof sha256 === "string" &&
    sha256Pattern.test(sha256);
  if (!valid) return null;
  return { versionName, versionCode, publishedAt: new Date(time).toISOString(), fileName, sizeBytes, sha256 };
}

/** 없으면 undefined, 읽을 수 없는 글이면 null입니다. 열 수 없는 것(권한 등)은 그대로 실패합니다. */
async function readJsonFile(path) {
  let raw;
  try {
    raw = await readFile(path, "utf8");
  } catch (error) {
    if (error.code === "ENOENT") return undefined;
    throw error;
  }
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

/** 값이 패턴으로 걸러져 있어도 이스케이프합니다. XML 1.0에 둘 수 없는 제어 문자는 뺍니다. */
function xmlText(value) {
  return String(value)
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F￾￿]/g, "")
    .replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&apos;" })[character]);
}

/** 다 쓴 뒤에 이름을 바꿉니다. 읽는 쪽이 반쯤 쓴 파일을 받지 않습니다. */
async function replaceFile(path, content) {
  const temporaryPath = `${path}.tmp`;
  await writeFile(temporaryPath, content, "utf8");
  await rename(temporaryPath, path);
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
  const parsed = { apkPath: defaultApkPath, webRoot: defaultWebRoot, historyOnly: false };
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--apk") parsed.apkPath = args[++index];
    else if (arg === "--web-root") parsed.webRoot = args[++index];
    else if (arg === "--history-only") parsed.historyOnly = true;
    else throw new Error(`모르는 인자입니다: ${arg}`);
  }
  return parsed;
}

// 직접 돌렸을 때만 게시합니다. 시험은 함수만 불러다 씁니다.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const { historyOnly, apkPath, webRoot } = parseArgs(process.argv.slice(2));
    if (historyOnly) {
      // 이력 파일이 생기기 전에 게시한 판을 채우거나, 지운 이력 파일을 다시 만들 때 씁니다. APK는 건드리지 않습니다.
      const { history, notes } = await writeReleaseHistory({ webRoot });
      for (const note of notes) console.warn(note);
      const latest = history.releases.find((release) => release.versionCode === history.latestVersionCode);
      console.log(
        `배포 이력을 썼습니다: 판 ${history.releases.length}개` +
          (latest ? `, 지금 내놓은 판 ${latest.versionName}(${latest.versionCode})` : ", 지금 내놓은 판 없음") +
          ` → ${join(resolve(webRoot), "update")}/releases.json · releases.xml`,
      );
    } else {
      const manifest = await publishUpdate({ apkPath, webRoot });
      console.log(`게시 완료: 버전 ${manifest.versionName}(${manifest.versionCode}), ${manifest.fileName}`);
      console.log("배포 이력(update/releases.json · releases.xml)도 다시 썼습니다.");
    }
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
