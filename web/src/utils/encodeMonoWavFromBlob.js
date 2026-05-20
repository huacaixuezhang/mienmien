/**
 * 将浏览器录制的 webm/opus 等解码为单声道 PCM16，再封装为 WAV，便于服务端 ASR（如百炼 Qwen-ASR）识别。
 * 输出固定 16kHz 单声道：与多数 ASR 模型期望一致，减少高采样率带来的不稳定。
 */

async function resampleMonoTo16kHz(float32, sourceRate) {
  if (!float32.length || Math.abs(sourceRate - 16000) < 1) {
    return { data: float32, rate: sourceRate < 1 ? 16000 : sourceRate };
  }
  const outFrames = Math.max(1, Math.ceil((float32.length * 16000) / sourceRate));
  const offline = new OfflineAudioContext(1, outFrames, 16000);
  const tmp = offline.createBuffer(1, float32.length, sourceRate);
  tmp.copyToChannel(float32, 0);
  const src = offline.createBufferSource();
  src.buffer = tmp;
  src.connect(offline.destination);
  src.start(0);
  const rendered = await offline.startRendering();
  return { data: rendered.getChannelData(0), rate: 16000 };
}
export async function encodeMonoWavFromBlob(audioBlob) {
  const ctx = new AudioContext();
  try {
    const arr = await audioBlob.arrayBuffer();
    const audioBuf = await ctx.decodeAudioData(arr.slice(0));
    let ch0 = audioBuf.getChannelData(0);
    if (audioBuf.numberOfChannels > 1) {
      const ch1 = audioBuf.getChannelData(1);
      const mix = new Float32Array(ch0.length);
      for (let i = 0; i < ch0.length; i++) {
        mix[i] = (ch0[i] + ch1[i]) * 0.5;
      }
      ch0 = mix;
    }
    const sampleRate = audioBuf.sampleRate;
    const { data: pcm, rate: sr } = await resampleMonoTo16kHz(ch0, sampleRate);
    ch0 = pcm;
    const int16 = new Int16Array(ch0.length);
    for (let i = 0; i < ch0.length; i++) {
      const s = Math.max(-1, Math.min(1, ch0[i]));
      int16[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
    }
    const dataSize = int16.length * 2;
    const buffer = new ArrayBuffer(44 + dataSize);
    const view = new DataView(buffer);
    const writeStr = (off, s) => {
      for (let i = 0; i < s.length; i++) view.setUint8(off + i, s.charCodeAt(i));
    };
    writeStr(0, "RIFF");
    view.setUint32(4, 36 + dataSize, true);
    writeStr(8, "WAVE");
    writeStr(12, "fmt ");
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, 1, true);
    view.setUint32(24, sr, true);
    view.setUint32(28, sr * 2, true);
    view.setUint16(32, 2, true);
    view.setUint16(34, 16, true);
    writeStr(36, "data");
    view.setUint32(40, dataSize, true);
    new Int16Array(buffer, 44, int16.length).set(int16);
    return new Blob([buffer], { type: "audio/wav" });
  } finally {
    try {
      await ctx.close();
    } catch {
      /* ignore */
    }
  }
}

/**
 * 将已是单声道 PCM16 的数据同步封装为 WAV（不经过 decodeAudioData）。
 * @param {Int16Array} int16
 * @param {number} [sampleRate]
 * @returns {Blob}
 */
export function encodeMonoWavFromPcm16(int16, sampleRate = 16000) {
  const sr = sampleRate | 0;
  const n = int16 && int16.length ? int16.length : 0;
  const dataSize = n * 2;
  const buffer = new ArrayBuffer(44 + dataSize);
  const view = new DataView(buffer);
  const writeStr = (off, s) => {
    for (let i = 0; i < s.length; i++) view.setUint8(off + i, s.charCodeAt(i));
  };
  writeStr(0, "RIFF");
  view.setUint32(4, 36 + dataSize, true);
  writeStr(8, "WAVE");
  writeStr(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sr, true);
  view.setUint32(28, sr * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeStr(36, "data");
  view.setUint32(40, dataSize, true);
  if (n > 0) {
    new Int16Array(buffer, 44, n).set(int16);
  }
  return new Blob([buffer], { type: "audio/wav" });
}
