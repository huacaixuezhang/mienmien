#!/usr/bin/env node
/**
 * 验证 business RSA 非对称加密：公钥接口、加密登录、API Key 落库掩码。
 */
import crypto from "node:crypto";

const BIZ = process.env.BIZ_BASE || "http://localhost:8080/api/v1/business";

async function fetchJson(url, options = {}) {
  const res = await fetch(url, options);
  const text = await res.text();
  let body;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = text;
  }
  if (!res.ok) {
    throw new Error(`${res.status} ${url} -> ${typeof body === "string" ? body : JSON.stringify(body)}`);
  }
  return body;
}

function sealSecret(plain, spkiBase64, prefix = "RSA1:") {
  const key = crypto.createPublicKey({
    key: Buffer.from(spkiBase64, "base64"),
    format: "der",
    type: "spki"
  });
  const cipher = crypto.publicEncrypt(
    { key, padding: crypto.constants.RSA_PKCS1_OAEP_PADDING, oaepHash: "sha256" },
    Buffer.from(plain, "utf8")
  );
  return `${prefix}${cipher.toString("base64")}`;
}

async function main() {
  console.log("[1] GET /crypto/public-key");
  const meta = await fetchJson(`${BIZ}/crypto/public-key`);
  console.log("    algorithm:", meta.algorithm, "ephemeral:", meta.ephemeralKeypair);

  const testPhone = `139${String(Date.now()).slice(-8)}`;
  const testPassword = "TestRsa@2026";
  const encPwd = sealSecret(testPassword, meta.publicKeySpkiBase64, meta.cipherPrefix || "RSA1:");
  console.log("[2] POST /auth/register (RSA password)", testPhone);
  const reg = await fetchJson(`${BIZ}/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ phone: testPhone, password: encPwd })
  });
  if (!reg.sessionToken) throw new Error("register missing sessionToken");
  console.log("    userId:", reg.userId);

  console.log("[3] POST /auth/login (RSA password)");
  const login = await fetchJson(`${BIZ}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ phone: testPhone, password: encPwd })
  });
  if (!login.sessionToken) throw new Error("login missing sessionToken");
  const auth = { Authorization: `Bearer ${login.sessionToken}` };

  const apiKeyPlain = "sk-test-rsa-verify-key";
  const encKey = sealSecret(apiKeyPlain, meta.publicKeySpkiBase64, meta.cipherPrefix || "RSA1:");
  console.log("[4] PUT /model-configs (RSA apiKey)");
  await fetchJson(`${BIZ}/model-configs`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...auth },
    body: JSON.stringify({
      provider: "aliyun-bailian",
      baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
      apiKey: encKey,
      modelName: "qwen-plus"
    })
  });

  console.log("[5] GET /model-configs/me (masked)");
  const cfg = await fetchJson(`${BIZ}/model-configs/me`, { headers: auth });
  if (!cfg.apiKeyConfigured) throw new Error("expected apiKeyConfigured=true");
  if (cfg.apiKey && !cfg.apiKey.includes("*")) {
    throw new Error(`apiKey should be masked, got: ${cfg.apiKey}`);
  }
  console.log("    apiKey:", cfg.apiKey, "apiKeyConfigured:", cfg.apiKeyConfigured);

  console.log("[6] reject plain password when require-client-cipher (optional skip)");
  try {
    await fetchJson(`${BIZ}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ phone: testPhone, password: testPassword })
    });
    console.log("    plain login succeeded (require-client-cipher=false, OK)");
  } catch (e) {
    console.log("    plain login rejected:", String(e.message).slice(0, 80));
  }

  console.log("\n全部 RSA 验证通过。");
}

main().catch((e) => {
  console.error("验证失败:", e.message || e);
  process.exit(1);
});
