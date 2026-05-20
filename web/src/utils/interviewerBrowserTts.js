/**
 * 面试间读题：基于 Web Speech API，优选中文音色并处理 voices 异步加载。
 */

function scoreVoice(v) {
  const name = (v.name || "").toLowerCase();
  const lang = (v.lang || "").toLowerCase();
  let s = 0;
  if (lang.startsWith("zh")) s += 10;
  if (lang.includes("cn") || lang.includes("hk") || lang.includes("tw")) s += 2;
  // 常见相对自然的离线/在线中文音色（不同系统命名差异大，仅作弱偏好）
  if (
    /ting|xiao|yaoyun|yunxi|xiaoxiao|yaoyao|huihui|kangkang|hanhan|li|mei|lili|google|microsoft|neural|premium|enhanced/.test(
      name
    )
  ) {
    s += 4;
  }
  if (v.localService) s += 1;
  return s;
}

function pickBestZhVoice(voices) {
  if (!voices || !voices.length) return null;
  const zh = voices.filter((v) => {
    const l = (v.lang || "").toLowerCase();
    return l.startsWith("zh") || l.includes("cmn") || l.includes("yue");
  });
  const pool = zh.length ? zh : voices;
  return [...pool].sort((a, b) => scoreVoice(b) - scoreVoice(a))[0] || null;
}

/** 长文本按句切分，避免单次 utterance 过长被浏览器截断 */
function splitLongTextForTts(raw) {
  if (raw.length <= 280) return [raw];
  const chunks = [];
  let start = 0;
  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i];
    const isSentenceEnd = "。！？!?；;".includes(ch);
    if (isSentenceEnd && i - start >= 24) {
      chunks.push(raw.slice(start, i + 1).trim());
      start = i + 1;
    }
  }
  const tail = raw.slice(start).trim();
  if (tail) chunks.push(tail);
  return chunks.length ? chunks : [raw];
}

let voicesReadyPromise = null;

export function ensureSpeechVoicesLoaded() {
  if (typeof window === "undefined" || !window.speechSynthesis) {
    return Promise.resolve();
  }
  if (window.speechSynthesis.getVoices().length > 0) {
    return Promise.resolve();
  }
  if (voicesReadyPromise) return voicesReadyPromise;
  voicesReadyPromise = new Promise((resolve) => {
    const done = () => {
      window.speechSynthesis.removeEventListener("voiceschanged", done);
      resolve();
    };
    window.speechSynthesis.addEventListener("voiceschanged", done);
    window.setTimeout(done, 800);
  });
  return voicesReadyPromise;
}

/**
 * @param {string} text
 * @param {{ delayMs?: number }} [opts]
 * @returns {Promise<void>}
 */
export function speakInterviewerQuestion(text, opts = {}) {
  const delayMs = opts.delayMs ?? 120;
  const raw = String(text || "").trim();
  if (!raw || typeof window === "undefined" || !window.speechSynthesis) {
    return Promise.resolve();
  }

  return ensureSpeechVoicesLoaded().then(
    () =>
      new Promise((resolve) => {
        window.speechSynthesis.cancel();
        window.setTimeout(() => {
          const voice = pickBestZhVoice(window.speechSynthesis.getVoices());
          const chunks = splitLongTextForTts(raw);
          const speakOne = (i) => {
            if (i >= chunks.length) {
              resolve();
              return;
            }
            const u = new SpeechSynthesisUtterance(chunks[i].trim());
            u.lang = "zh-CN";
            if (voice) u.voice = voice;
            u.rate = 0.92;
            u.pitch = 1;
            u.volume = 1;
            u.onend = () => speakOne(i + 1);
            u.onerror = () => speakOne(i + 1);
            window.speechSynthesis.speak(u);
          };
          speakOne(0);
        }, delayMs);
      })
  );
}

export function cancelInterviewerSpeech() {
  if (typeof window !== "undefined" && window.speechSynthesis) {
    window.speechSynthesis.cancel();
  }
}
