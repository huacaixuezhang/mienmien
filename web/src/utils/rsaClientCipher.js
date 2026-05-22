/** 与 business RsaAsymmetricCryptoService 对齐：RSA-OAEP SHA-256，密文前缀 RSA1: */

const BIZ = import.meta.env.VITE_BUSINESS_API_BASE || "/api/v1/business";

let cachedPublicKey = null;

/**
 * @returns {Promise<{ algorithm: string, cipherPrefix: string, publicKeySpkiBase64: string }>}
 */
export async function fetchBusinessCryptoPublicKey() {
  if (cachedPublicKey) {
    return cachedPublicKey;
  }
  const res = await fetch(`${BIZ}/crypto/public-key`);
  if (!res.ok) {
    throw new Error(`获取公钥失败 (${res.status})`);
  }
  cachedPublicKey = await res.json();
  return cachedPublicKey;
}

function spkiToCryptoKey(spkiBase64) {
  const binary = atob(spkiBase64.replace(/\s+/g, ""));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return crypto.subtle.importKey(
    "spki",
    bytes.buffer,
    { name: "RSA-OAEP", hash: "SHA-256" },
    false,
    ["encrypt"]
  );
}

/**
 * @param {string} plaintext
 * @returns {Promise<string>} RSA1:Base64
 */
export async function sealSecretForBusiness(plaintext) {
  const plain = String(plaintext ?? "");
  if (!plain) {
    return "";
  }
  const meta = await fetchBusinessCryptoPublicKey();
  const key = await spkiToCryptoKey(meta.publicKeySpkiBase64);
  const data = new TextEncoder().encode(plain);
  const cipher = await crypto.subtle.encrypt({ name: "RSA-OAEP" }, key, data);
  const b64 = btoa(String.fromCharCode(...new Uint8Array(cipher)));
  const prefix = meta.cipherPrefix || "RSA1:";
  return `${prefix}${b64}`;
}

export function isMaskedApiKeyPlaceholder(value) {
  const t = String(value ?? "").trim();
  return !t || t === "********" || t === "******";
}
