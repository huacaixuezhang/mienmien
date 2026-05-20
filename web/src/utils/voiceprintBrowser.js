/**
 * 浏览器端「声纹」近似：基于短时能量轮廓的模板 + 余弦相似度。
 * 非生物特征级声纹；模板存 **localStorage 全局键**，全站视频面试间共用。
 */

const TEMPLATE_DIM = 64;
const LS_ENERGY = "mienmien_voiceprint_global_energy_v1";

/** @param {Float32Array} a @param {Float32Array} b */
export function cosineSimilarity(a, b) {
  if (!a || !b || a.length !== b.length || a.length === 0) return 0;
  let dot = 0;
  let na = 0;
  let nb = 0;
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    na += a[i] * a[i];
    nb += b[i] * b[i];
  }
  const d = Math.sqrt(na) * Math.sqrt(nb);
  return d < 1e-10 ? 0 : dot / d;
}

/**
 * 去掉首尾无有效讲话的片段（短时窗 RMS + 自适应阈值），仅处理 channel 0。
 * @param {AudioBuffer} buffer
 * @param {{ minSec?: number, padSec?: number }} [opts]
 * @returns {AudioBuffer | null} 有效语音过短或未检出语音时返回 null
 */
export function trimSilenceFromAudioBuffer(buffer, opts = {}) {
  const minSec = opts.minSec ?? 0.35;
  const padSec = opts.padSec ?? 0.06;
  const sr = buffer.sampleRate;
  if (buffer.numberOfChannels < 1) return null;
  const ch0 = buffer.getChannelData(0);
  const n = ch0.length;
  if (n < sr * minSec) return null;

  const win = Math.max(256, Math.floor(0.02 * sr));
  const hop = Math.max(128, Math.floor(win / 2));
  /** @type {{ i: number, rms: number }[]} */
  const frames = [];
  for (let i = 0; i + win <= n; i += hop) {
    let sum = 0;
    for (let j = 0; j < win; j++) {
      const v = ch0[i + j];
      sum += v * v;
    }
    frames.push({ i, rms: Math.sqrt(sum / win) });
  }
  if (frames.length === 0) return null;

  const sorted = frames.map((f) => f.rms).sort((a, b) => a - b);
  const p10 = sorted[Math.max(0, Math.floor(sorted.length * 0.1) - 1)];
  const p90 = sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * 0.9))];
  const threshold = Math.max(p10 * 3.5, p90 * 0.12, 0.006);

  let firstK = -1;
  let lastK = -1;
  for (let k = 0; k < frames.length; k++) {
    if (frames[k].rms >= threshold) {
      if (firstK < 0) firstK = k;
      lastK = k;
    }
  }
  if (firstK < 0) return null;

  let startSample = frames[firstK].i;
  let endSample = frames[lastK].i + win;
  const pad = Math.floor(padSec * sr);
  startSample = Math.max(0, startSample - pad);
  endSample = Math.min(n, endSample + pad);
  const len = endSample - startSample;
  if (len < sr * minSec) return null;

  const slice = ch0.subarray(startSample, endSample);
  try {
    const out = new AudioBuffer({ numberOfChannels: 1, length: len, sampleRate: sr });
    out.copyToChannel(new Float32Array(slice), 0, 0);
    return out;
  } catch {
    try {
      const ac = new AudioContext({ sampleRate: sr });
      const out = ac.createBuffer(1, len, sr);
      out.copyToChannel(new Float32Array(slice), 0, 0);
      void ac.close();
      return out;
    } catch {
      return null;
    }
  }
}

/**
 * 从 AudioBuffer 单声道提取归一化能量轮廓（固定 TEMPLATE_DIM 维）。
 * @param {AudioBuffer} buffer
 * @returns {Float32Array}
 */
export function templateFromAudioBuffer(buffer) {
  const ch = buffer.numberOfChannels > 0 ? buffer.getChannelData(0) : new Float32Array(0);
  const n = ch.length;
  const out = new Float32Array(TEMPLATE_DIM);
  if (n < TEMPLATE_DIM) {
    return out;
  }
  const chunk = Math.floor(n / TEMPLATE_DIM);
  for (let i = 0; i < TEMPLATE_DIM; i++) {
    let sum = 0;
    const start = i * chunk;
    for (let j = 0; j < chunk; j++) {
      const v = ch[start + j];
      sum += v * v;
    }
    out[i] = Math.sqrt(sum / chunk) + 1e-8;
  }
  let norm = 0;
  for (let i = 0; i < TEMPLATE_DIM; i++) norm += out[i] * out[i];
  norm = Math.sqrt(norm) || 1;
  for (let i = 0; i < TEMPLATE_DIM; i++) out[i] /= norm;
  return out;
}

/**
 * 从 AnalyserNode 的时域字节数据构造与模板同维的轮廓（当前帧附近能量分布）。
 * @param {Uint8Array} timeDomainByte256 typical analyser.fftSize 256..2048
 * @returns {Float32Array}
 */
export function liveVectorFromTimeDomain(timeDomainByte256) {
  const raw = timeDomainByte256;
  const len = raw.length;
  const out = new Float32Array(TEMPLATE_DIM);
  if (len < TEMPLATE_DIM) return out;
  const chunk = Math.floor(len / TEMPLATE_DIM);
  for (let i = 0; i < TEMPLATE_DIM; i++) {
    let sum = 0;
    const start = i * chunk;
    for (let j = 0; j < chunk; j++) {
      const v = (raw[start + j] - 128) / 128;
      sum += v * v;
    }
    out[i] = Math.sqrt(sum / chunk) + 1e-8;
  }
  let norm = 0;
  for (let i = 0; i < TEMPLATE_DIM; i++) norm += out[i] * out[i];
  norm = Math.sqrt(norm) || 1;
  for (let i = 0; i < TEMPLATE_DIM; i++) out[i] /= norm;
  return out;
}

/** @returns {Float32Array | null} */
export function loadGlobalEnergyTemplate() {
  try {
    const raw = localStorage.getItem(LS_ENERGY);
    if (!raw) return null;
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr) || arr.length !== TEMPLATE_DIM) return null;
    const f = new Float32Array(TEMPLATE_DIM);
    for (let i = 0; i < TEMPLATE_DIM; i++) f[i] = Number(arr[i]) || 0;
    return f;
  } catch {
    return null;
  }
}

/** @param {Float32Array} template */
export function saveGlobalEnergyTemplate(template) {
  const arr = Array.from(template);
  localStorage.setItem(LS_ENERGY, JSON.stringify(arr));
  try {
    window.dispatchEvent(new CustomEvent("mienmien-voiceprint-global-updated"));
  } catch {
    /* ignore */
  }
}

export function clearGlobalEnergyTemplate() {
  localStorage.removeItem(LS_ENERGY);
  try {
    window.dispatchEvent(new CustomEvent("mienmien-voiceprint-global-updated"));
  } catch {
    /* ignore */
  }
}

export const VOICEPRINT_DIM = TEMPLATE_DIM;

/** 低于该余弦相似度则丢弃本句 Whisper 转写（与模板差异大，多为串音/环境声） */
export const DEFAULT_SIM_THRESHOLD = 0.38;
