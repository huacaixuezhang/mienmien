/**
 * 频谱轮廓声纹（加强）：离线段用 FFT 能量带，实时帧用 Analyser 频域 bin 聚合。
 * 模板存 **localStorage 全局键**，与能量轨分库存储。
 */

const SPEC_DIM = 64;
const LS_SPECTRAL = "mienmien_voiceprint_global_spectral_v1";

function normalizeL2(out) {
  let norm = 0;
  for (let i = 0; i < out.length; i++) norm += out[i] * out[i];
  norm = Math.sqrt(norm) || 1;
  for (let i = 0; i < out.length; i++) out[i] /= norm;
}

/** 原地 radix-2 Cooley–Tukey，n 须为 2 的幂 */
function fftInPlace(re, im) {
  const n = re.length;
  let j = 0;
  for (let i = 0; i < n - 1; i++) {
    if (i < j) {
      let t = re[i];
      re[i] = re[j];
      re[j] = t;
      t = im[i];
      im[i] = im[j];
      im[j] = t;
    }
    let k = n >> 1;
    while (k <= j) {
      j -= k;
      k >>= 1;
    }
    j += k;
  }
  for (let size = 2; size <= n; size <<= 1) {
    const half = size >> 1;
    const ang = (-2 * Math.PI) / size;
    const wrStep = Math.cos(ang);
    const wiStep = Math.sin(ang);
    for (let i = 0; i < n; i += size) {
      let wr = 1;
      let wi = 0;
      for (let j2 = 0; j2 < half; j2++) {
        const k = i + j2 + half;
        const tr = wr * re[k] - wi * im[k];
        const ti = wr * im[k] + wi * re[k];
        re[k] = re[i + j2] - tr;
        im[k] = im[i + j2] - ti;
        re[i + j2] += tr;
        im[i + j2] += ti;
        const nwr = wr * wrStep - wi * wiStep;
        wi = wr * wiStep + wi * wrStep;
        wr = nwr;
      }
    }
  }
}

/**
 * @param {AudioBuffer} buffer
 * @returns {Float32Array}
 */
export function templateSpectralFromAudioBuffer(buffer) {
  const ch = buffer.numberOfChannels > 0 ? buffer.getChannelData(0) : new Float32Array(0);
  const n = ch.length;
  const N = 1024;
  const out = new Float32Array(SPEC_DIM);
  if (n < N) return out;

  const re = new Float32Array(N);
  const im = new Float32Array(N);
  const frame = Math.min(Math.floor(n * 0.15), n - N);
  for (let i = 0; i < N; i++) {
    const x = ch[frame + i] || 0;
    const w = 0.5 * (1 - Math.cos((2 * Math.PI * i) / (N - 1)));
    re[i] = x * w;
    im[i] = 0;
  }
  fftInPlace(re, im);
  const mag = new Float32Array(N / 2);
  for (let k = 0; k < N / 2; k++) {
    mag[k] = Math.sqrt(re[k] * re[k] + im[k] * im[k]) + 1e-10;
  }
  const per = N / 2 / SPEC_DIM;
  for (let b = 0; b < SPEC_DIM; b++) {
    let s = 0;
    const lo = Math.floor(b * per);
    const hi = Math.floor((b + 1) * per);
    for (let k = lo; k < hi; k++) s += mag[k];
    out[b] = Math.log(1 + s / Math.max(1, hi - lo));
  }
  normalizeL2(out);
  return out;
}

/**
 * @param {Uint8Array} fftByteFreq analyser.getByteFrequencyData
 * @returns {Float32Array}
 */
export function liveSpectralFromByteFrequency(fftByteFreq) {
  const n = fftByteFreq.length;
  const out = new Float32Array(SPEC_DIM);
  if (n < SPEC_DIM) return out;
  const per = n / SPEC_DIM;
  for (let b = 0; b < SPEC_DIM; b++) {
    let s = 0;
    const lo = Math.floor(b * per);
    const hi = Math.floor((b + 1) * per);
    for (let k = lo; k < hi; k++) {
      const v = (fftByteFreq[k] / 255) * 60 - 100;
      s += Math.pow(10, v / 20);
    }
    out[b] = Math.log(1 + s / Math.max(1, hi - lo) + 1e-8);
  }
  normalizeL2(out);
  return out;
}

/** @returns {Float32Array | null} */
export function loadGlobalSpectralTemplate() {
  try {
    const raw = localStorage.getItem(LS_SPECTRAL);
    if (!raw) return null;
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr) || arr.length !== SPEC_DIM) return null;
    const f = new Float32Array(SPEC_DIM);
    for (let i = 0; i < SPEC_DIM; i++) f[i] = Number(arr[i]) || 0;
    return f;
  } catch {
    return null;
  }
}

/** @param {Float32Array} template */
export function saveGlobalSpectralTemplate(template) {
  localStorage.setItem(LS_SPECTRAL, JSON.stringify(Array.from(template)));
  try {
    window.dispatchEvent(new CustomEvent("mienmien-voiceprint-global-updated"));
  } catch {
    /* ignore */
  }
}

export function clearGlobalSpectralTemplate() {
  localStorage.removeItem(LS_SPECTRAL);
  try {
    window.dispatchEvent(new CustomEvent("mienmien-voiceprint-global-updated"));
  } catch {
    /* ignore */
  }
}

export const SPECTRAL_DIM = SPEC_DIM;

/** 频谱余弦门控可略低于能量轨（经验值） */
export const DEFAULT_SPECTRAL_SIM_THRESHOLD = 0.32;
