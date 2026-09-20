import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import vm from "node:vm";

const here = dirname(fileURLToPath(import.meta.url));

test("평문 서버를 LAN 전체에 열 수 없다", async () => {
  const child = spawn(process.execPath, [join(here, "../app_good_words_server.mjs"), "--host", "0.0.0.0"], {
    windowsHide: true, stdio: ["ignore", "ignore", "pipe"]
  });
  let error = "";
  child.stderr.on("data", chunk => { error += chunk; });
  const code = await new Promise(resolve => child.once("exit", resolve));
  assert.notEqual(code, 0);
  assert.match(error, /HTTPS/);
});

test("웹은 평문 LAN에서 키를 읽거나 요청하기 전에 차단한다", async () => {
  const source = await readFile(join(here, "../web/app.js"), "utf8");
  const functions = source.slice(source.indexOf("function requireSecureConnection()"), source.indexOf("function emptyDiaryUris()"));
  let reads = 0;
  let requests = 0;
  const context = vm.createContext({
    location: { protocol: "http:", hostname: "192.168.1.10" },
    localStorage: { getItem: () => { reads++; return "synthetic-key"; } },
    fetch: async () => { requests++; throw new Error("요청해서는 안 됩니다."); }
  });
  vm.runInContext(functions, context);
  await assert.rejects(vm.runInContext('api("/api/snapshot")', context), /HTTPS/);
  assert.equal(reads, 0);
  assert.equal(requests, 0);
});

test("HTTPS 웹 요청은 인증 키를 붙이되 리다이렉트를 허용하지 않는다", async () => {
  const source = await readFile(join(here, "../web/app.js"), "utf8");
  const functions = source.slice(source.indexOf("function requireSecureConnection()"), source.indexOf("function emptyDiaryUris()"));
  let observed;
  const context = vm.createContext({
    location: { protocol: "https:", hostname: "sync.example.com" },
    localStorage: { getItem: () => "synthetic-key" },
    fetch: async (path, init) => { observed = { path, init }; return { ok: true, text: async () => "{}" }; }
  });
  vm.runInContext(functions, context);
  await vm.runInContext('api("/api/snapshot")', context);
  assert.equal(observed.init.redirect, "error");
  assert.equal(observed.init.headers["X-API-Key"], "synthetic-key");
});
