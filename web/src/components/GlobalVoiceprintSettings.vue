<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
  clearGlobalEnergyTemplate,
  loadGlobalEnergyTemplate,
  saveGlobalEnergyTemplate,
  templateFromAudioBuffer,
  trimSilenceFromAudioBuffer
} from "../utils/voiceprintBrowser.js";
import { readVoiceprintPrefs, writeVoiceprintPrefs } from "../utils/voiceprintGlobalPref.js";
import { clearGlobalSpectralTemplate } from "../utils/voiceprintSpectralBrowser.js";

/** 录制模板时的固定朗读稿（请自然语速朗读，勿耳语或刻意变声） */
const ENROLL_READING_TEXT =
  "您好，我申请本次岗位。下面我想简要介绍自己的学习经历与实习成果，并说明我与岗位的匹配点。在上一份实习里，我常负责需求整理和排期沟通，习惯先对齐目标再推进。我期待在贵司继续成长，谢谢。";

/** 单次录制最长时长（毫秒），到时自动停止 */
const ENROLL_MAX_MS = 45000;

const filterOn = ref(false);
const status = ref("");

const hasEnergy = ref(false);

/** 正在采集麦克风 */
const recording = ref(false);
/** 解码 / 去静默 / 写模板 */
const processing = ref(false);
/** 距离自动停止还剩多少毫秒（录制中更新） */
const remainingMs = ref(0);

let enrollStream = null;
let enrollRecorder = null;
/** @type {BlobPart[]} */
let enrollChunks = [];
let recordStartedAt = 0;
/** @type {ReturnType<typeof setInterval> | null} */
let tickId = null;
/** @type {ReturnType<typeof setTimeout> | null} */
let maxStopId = null;
let finalizeOnce = false;

const remainingLabel = computed(() => {
  const s = Math.ceil(remainingMs.value / 1000);
  return `${s} 秒`;
});

function refreshHasTemplates() {
  hasEnergy.value = !!loadGlobalEnergyTemplate();
}

function syncFromStorage() {
  const p = readVoiceprintPrefs();
  filterOn.value = p.filterOn;
  refreshHasTemplates();
}

function onStorage(ev) {
  const k = ev.key || "";
  if (k.includes("voiceprint")) syncFromStorage();
}

function clearRecordTimers() {
  if (tickId != null) {
    clearInterval(tickId);
    tickId = null;
  }
  if (maxStopId != null) {
    clearTimeout(maxStopId);
    maxStopId = null;
  }
}

function tickRemaining() {
  const elapsed = Date.now() - recordStartedAt;
  remainingMs.value = Math.max(0, ENROLL_MAX_MS - elapsed);
}

function clearRecordSession() {
  clearRecordTimers();
  if (enrollStream) {
    try {
      enrollStream.getTracks().forEach((t) => t.stop());
    } catch {
      /* ignore */
    }
  }
  enrollStream = null;
  enrollRecorder = null;
  enrollChunks = [];
}

async function finalizeRecording() {
  if (finalizeOnce) return;
  finalizeOnce = true;
  clearRecordTimers();
  recording.value = false;
  remainingMs.value = 0;

  const rec = enrollRecorder;
  const stream = enrollStream;
  const mimeType = rec?.mimeType || "audio/webm";
  enrollRecorder = null;
  enrollStream = null;

  if (!rec) {
    if (stream) {
      try {
        stream.getTracks().forEach((t) => t.stop());
      } catch {
        /* ignore */
      }
    }
    return;
  }

  await new Promise((resolve) => {
    rec.onstop = () => resolve();
    try {
      if (rec.state !== "inactive") rec.stop();
      else resolve();
    } catch {
      resolve();
    }
  });

  if (stream) {
    try {
      stream.getTracks().forEach((t) => t.stop());
    } catch {
      /* ignore */
    }
  }

  processing.value = true;
  status.value = "";
  try {
    const blob = new Blob(enrollChunks, { type: mimeType });
    enrollChunks = [];
    if (!blob.size) {
      status.value = "未采集到有效音频，请重试。";
      return;
    }
    const dec = new AudioContext();
    const ab = await blob.arrayBuffer();
    const rawBuf = await dec.decodeAudioData(ab.slice(0));
    await dec.close();

    const trimmed = trimSilenceFromAudioBuffer(rawBuf);
    if (!trimmed) {
      status.value =
        "未检测到足够有效语音（已尝试去掉首尾静音）。请靠近麦克风、减小环境噪声后重新录制。";
      return;
    }
    saveGlobalEnergyTemplate(templateFromAudioBuffer(trimmed));
    filterOn.value = true;
    refreshHasTemplates();
    status.value = "已保存全局能量声纹模板（已自动去掉首尾未讲话片段）。";
  } catch (e) {
    status.value = `处理录音失败：${e?.message || e}`;
  } finally {
    processing.value = false;
  }
}

async function startRecording() {
  if (recording.value || processing.value) return;
  status.value = "";
  finalizeOnce = false;
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true },
      video: false
    });
    enrollStream = stream;
    enrollChunks = [];
    const mime = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
      ? "audio/webm;codecs=opus"
      : MediaRecorder.isTypeSupported("audio/webm")
        ? "audio/webm"
        : "";
    const rec = mime ? new MediaRecorder(stream, { mimeType: mime }) : new MediaRecorder(stream);
    enrollRecorder = rec;
    rec.ondataavailable = (e) => {
      if (e.data && e.data.size > 0) enrollChunks.push(e.data);
    };
    rec.start(250);
    recording.value = true;
    recordStartedAt = Date.now();
    remainingMs.value = ENROLL_MAX_MS;
    tickId = window.setInterval(tickRemaining, 200);
    tickRemaining();
    maxStopId = window.setTimeout(() => {
      maxStopId = null;
      void finalizeRecording();
    }, ENROLL_MAX_MS);
  } catch (e) {
    clearRecordSession();
    status.value = `无法开始录制：${e?.message || e}`;
  }
}

function stopRecording() {
  if (!recording.value) return;
  void finalizeRecording();
}

function clearTemplates() {
  clearGlobalEnergyTemplate();
  clearGlobalSpectralTemplate();
  filterOn.value = false;
  writeVoiceprintPrefs({ filterOn: false, variant: "energy" });
  refreshHasTemplates();
  status.value = "已清除声纹模板，并已关闭门控（含历史频谱模板）。";
}

onMounted(() => {
  syncFromStorage();
  window.addEventListener("storage", onStorage);
  window.addEventListener("mienmien-voiceprint-global-updated", syncFromStorage);
});

onBeforeUnmount(() => {
  window.removeEventListener("storage", onStorage);
  window.removeEventListener("mienmien-voiceprint-global-updated", syncFromStorage);
  if (recording.value) {
    void finalizeRecording();
  } else {
    clearRecordSession();
  }
});

watch(filterOn, () => {
  writeVoiceprintPrefs({ filterOn: filterOn.value, variant: "energy" });
});
</script>

<template>
  <div class="bg-white rounded-lg shadow-card p-6 space-y-4">
    <div>
      <h2 class="text-lg font-semibold text-gray-800 flex items-center gap-2">
        <i class="fa-solid fa-fingerprint text-primary"></i>全局声纹（语音面试间共用）
      </h2>
      <p class="text-sm text-gray-500 mt-1 max-w-2xl">
        使用<strong>能量轮廓</strong>模板：在本页配置一次后，所有视频/语音模拟面试间共用同一套门控；数据保存在本机
        <code class="text-xs bg-gray-100 px-1 rounded">localStorage</code>，非服务端生物声纹。
      </p>
    </div>

    <div class="rounded-lg border border-teal-200 bg-teal-50/80 p-4 text-sm text-teal-950 space-y-3">
      <label class="inline-flex items-center gap-2 cursor-pointer select-none">
        <input v-model="filterOn" type="checkbox" class="rounded border-teal-500" :disabled="recording || processing" />
        <span>在面试间启用声纹门控（无能量模板时不会生效）</span>
      </label>
      <p class="text-xs text-teal-900/90">当前状态：能量模板 {{ hasEnergy ? "已配置" : "未配置" }}</p>

      <div class="rounded-md border border-teal-300/80 bg-white/90 p-3 space-y-2">
        <p class="text-xs font-medium text-teal-900">录制时请朗读以下文字（自然语速即可）</p>
        <p class="text-sm text-teal-950 leading-relaxed select-text" role="text" aria-label="声纹录制朗读稿">
          {{ ENROLL_READING_TEXT }}
        </p>
      </div>

      <p v-if="recording" class="text-sm font-semibold text-teal-800">
        录制中 · 自动停止还剩 <span class="tabular-nums">{{ remainingLabel }}</span>（最长
        {{ Math.round(ENROLL_MAX_MS / 1000) }} 秒，读完后可点「结束录制」）
      </p>
      <p v-else-if="processing" class="text-sm font-medium text-teal-700">正在处理录音（去首尾静音并生成模板）…</p>

      <div class="flex flex-wrap items-center gap-2">
        <button
          type="button"
          class="px-4 py-2 rounded-lg bg-teal-700 text-white text-sm font-medium hover:bg-teal-800 disabled:opacity-50"
          :disabled="recording || processing"
          @click="startRecording"
        >
          开始录制
        </button>
        <button
          type="button"
          class="px-4 py-2 rounded-lg border border-teal-600 bg-white text-teal-900 text-sm font-medium hover:bg-teal-100 disabled:opacity-50"
          :disabled="!recording || processing"
          @click="stopRecording"
        >
          结束录制
        </button>
        <button
          type="button"
          class="px-4 py-2 rounded-lg border border-teal-500 text-teal-900 text-sm hover:bg-teal-100 disabled:opacity-50"
          :disabled="!hasEnergy || recording || processing"
          @click="clearTemplates"
        >
          清除模板并重置门控
        </button>
      </div>
      <p v-if="status" class="text-xs text-teal-900 whitespace-pre-wrap">{{ status }}</p>
    </div>
  </div>
</template>
