<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
  USER_SESSION_STORAGE_KEY,
  listVideoInterviewEvents,
  listVideoInterviewTurns,
  transcribeVideoInterviewAudio
} from "../api";
import { InterviewState, mapServerStateToFsm, fsmLabelZh } from "../utils/mockInterviewFsm.js";
import { interviewerStyleLabel } from "../utils/interviewerStyleResolve.js";
import { encodeMonoWavFromPcm16 } from "../utils/encodeMonoWavFromBlob.js";
import {
  cancelInterviewerSpeech,
  ensureSpeechVoicesLoaded,
  speakInterviewerQuestion
} from "../utils/interviewerBrowserTts.js";
import { readVoiceprintPrefs } from "../utils/voiceprintGlobalPref.js";
import {
  cosineSimilarity,
  DEFAULT_SIM_THRESHOLD,
  liveVectorFromTimeDomain,
  loadGlobalEnergyTemplate
} from "../utils/voiceprintBrowser.js";
import { mergeAsrTranscriptSegments } from "../utils/mergeAsrTranscriptSegments.js";

const VOICE_SIM_THRESHOLD = DEFAULT_SIM_THRESHOLD;
/** 录音分段转写：REST 路径下每隔多少毫秒取一片送 ASR（PCM 重叠窗见 REST_ASR_OVERLAP_MS） */
const ASR_SLICE_MS = 2200;
/** REST 分段与上一片末尾重叠的毫秒数，减轻切片边界的断句与叠字问题 */
const REST_ASR_OVERLAP_MS = 300;
/** 非末次切片：至少本长度（约 100ms@16k）才发起识别，避免极短空窗刷请求 */
const MIN_REST_SLICE_SAMPLES = 1600;

/** 服务端 Omni 流式 ASR 可用（由 pong.realtimeAsrEnabled 决定） */
const serverRealtimeAsrEnabled = ref(false);
const realtimeStreamCommitted = ref("");
const realtimeStreamPartial = ref("");
let pcmSourceNode = null;
let pcmScriptNode = null;
let pcmMuteGainNode = null;
let asrRealtimeClosedResolver = null;

const props = defineProps({
  session: { type: Object, required: true },
  interviewerCustomStyles: { type: Array, default: () => [] },
  context: {
    type: Object,
    default: () => ({ forMock: true, roundIndex: 0, roundTitle: "", interviewerStyleKey: "" })
  }
});

const emit = defineEmits(["close"]);

/** 模拟面试不启用浏览器端声纹门控（正式面试保留） */
const skipVoiceprint = computed(() => props.context?.forMock === true);

const tab = ref("room");
const logLines = ref([]);
const events = ref([]);
const turns = ref([]);
const wsStatus = ref("未连接");
const lastSpeak = ref("");
const streamingQuestion = ref("");
const liveAsrPreview = ref("");
const asrHint = ref("");
const questionGenNotice = ref("");
const lastTurnResult = ref(null);
/** 侧栏当前查看的题；与 currentTurnId 一致时可作答 */
const selectedTurnId = ref("");
const micReady = ref(false);
const fsmUi = ref(InterviewState.IDLE);
const currentTurnId = ref("");
const voiceprintTemplate = ref(null);
const voiceSim = ref(1);
const voiceFilterOn = ref(false);
/** 终局 `ended` 事件中的总评摘要，用于「生成完成」区块展示 */
const lastEndedSummary = ref("");

let ws = null;
let audioCtx = null;
let mediaStream = null;
let analyser = null;
let rafId = 0;
/** REST 分段：16k PCM 累积 + 重叠窗送 ASR（替代 MediaRecorder 无重叠 webm 片） */
let restAsrSourceNode = null;
let restAsrScriptNode = null;
let restAsrMuteGainNode = null;
let restSliceIntervalId = null;
let restAccumBuffer = null;
let restAccumLength = 0;
/** 上一段已送 ASR 的累积样本右端（不含）；下一段从 max(0, 此处 − overlap) 起算 */
let restLastFlushEnd = 0;
let asrDrainChain = Promise.resolve();
let asrRecordedBytes = 0;
/** 每轮作答递增，丢弃上一轮尚未完成的转写回调，避免串台 */
let asrStreamGen = 0;
let voiceSimPeak = 0;

function clearQuestionGenNotice() {
  questionGenNotice.value = "";
}

function readSessionToken() {
  try {
    const raw = localStorage.getItem(USER_SESSION_STORAGE_KEY);
    if (!raw) return "";
    const p = JSON.parse(raw);
    return p?.sessionToken || "";
  } catch {
    return "";
  }
}

function buildWsUrl() {
  const base = props.session.consumerHttpBaseUrl || "http://localhost:8081";
  const path = props.session.videoInterviewWebSocketPath || "";
  const u = new URL(base);
  const origin = u.protocol === "https:" ? `wss://${u.host}` : `ws://${u.host}`;
  const token = readSessionToken();
  return `${origin}${path}?sessionToken=${encodeURIComponent(token)}`;
}

function pushLog(line) {
  logLines.value = [...logLines.value, `${new Date().toLocaleTimeString()} ${line}`].slice(-200);
}

function reloadVoiceprint() {
  if (skipVoiceprint.value) {
    voiceprintTemplate.value = null;
    voiceFilterOn.value = false;
    voiceSim.value = 1;
    return;
  }
  voiceprintTemplate.value = loadGlobalEnergyTemplate();
  const p = readVoiceprintPrefs();
  voiceFilterOn.value = p.filterOn;
}

function sendJson(obj) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(obj));
  }
}

function mergeRealtimeAsrPreview() {
  const c = realtimeStreamCommitted.value.trim();
  const p = realtimeStreamPartial.value.trim();
  if (!p) {
    liveAsrPreview.value = c;
    return;
  }
  if (!c) {
    liveAsrPreview.value = p;
    return;
  }
  liveAsrPreview.value = mergeAsrTranscriptSegments(c, p);
}

/** 读题 TTS：优选中文音色、语速；长题分段朗读（见 interviewerBrowserTts.js） */
function speakBrowserTts(text) {
  void speakInterviewerQuestion(text, { delayMs: 100 });
}

function handleServerPayload(obj) {
  const t = obj?.type;
  if (t === "pong") {
    serverRealtimeAsrEnabled.value = Boolean(obj?.payload?.realtimeAsrEnabled);
    return;
  }
  if (t === "displaced") {
    pushLog("本页连接已被其他标签页顶替，请刷新或关闭重复标签。");
    wsStatus.value = "已顶替";
    return;
  }
  if (t === "error") {
    const msg = obj?.payload?.message || JSON.stringify(obj);
    pushLog(`服务端：${msg}`);
    if (obj?.payload?.context === "question_generation") {
      questionGenNotice.value = String(msg || "").trim();
    }
    return;
  }
  if (t === "question_delta") {
    const chunk = obj?.payload?.textChunk || "";
    const seq = Number(obj?.payload?.seq);
    if (seq === 1) {
      streamingQuestion.value = "";
      questionGenNotice.value = "";
    }
    streamingQuestion.value += chunk;
    lastSpeak.value = streamingQuestion.value;
    return;
  }
  if (t === "question_done") {
    const full = obj?.payload?.fullText || "";
    questionGenNotice.value = "";
    streamingQuestion.value = full;
    lastSpeak.value = full;
    if (obj?.payload?.turnId) currentTurnId.value = String(obj.payload.turnId);
    pushLog("题目流式输出完成");
    void refreshTurns();
    return;
  }
  if (t === "speak") {
    const text = obj?.payload?.text || "";
    lastSpeak.value = text;
    if (fsmUi.value === InterviewState.RECORDING) {
      pushLog("已在录音中，跳过读题语音，避免与作答拾音冲突。");
      return;
    }
    speakBrowserTts(text);
    pushLog(`面试官（读题）：${text.slice(0, 120)}${text.length > 120 ? "…" : ""}`);
    return;
  }
  if (t === "replay") {
    pushLog(`回放 seq=${obj?.payload?.seq} ${obj?.payload?.eventType}`);
    const inner = obj?.payload?.payload;
    if (obj?.payload?.eventType === "state" && inner && typeof inner === "object" && inner.state) {
      fsmUi.value = mapServerStateToFsm(inner.state);
      if (inner.turnId) currentTurnId.value = String(inner.turnId);
    }
    return;
  }
  if (t === "state") {
    const st = obj?.payload?.state;
    fsmUi.value = mapServerStateToFsm(st);
    if (obj?.payload?.turnId) currentTurnId.value = String(obj.payload.turnId);
    pushLog(`状态：${st}`);
    return;
  }
  if (t === "asr_ready") {
    asrHint.value = "（实时转写已就绪）";
    return;
  }
  if (t === "asr_partial") {
    const tid = String(obj?.payload?.turnId || "").trim();
    if (tid && tid !== String(currentTurnId.value || "").trim()) return;
    realtimeStreamPartial.value = String(obj?.payload?.text || "");
    mergeRealtimeAsrPreview();
    return;
  }
  if (t === "asr_commit") {
    const tid = String(obj?.payload?.turnId || "").trim();
    if (tid && tid !== String(currentTurnId.value || "").trim()) return;
    const seg = String(obj?.payload?.text || "").trim();
    if (seg) {
      const cur = realtimeStreamCommitted.value.trim();
      realtimeStreamCommitted.value = mergeAsrTranscriptSegments(cur, seg);
    }
    realtimeStreamPartial.value = "";
    mergeRealtimeAsrPreview();
    return;
  }
  if (t === "asr_realtime_closed") {
    const fn = asrRealtimeClosedResolver;
    asrRealtimeClosedResolver = null;
    if (typeof fn === "function") fn();
    return;
  }
  if (t === "turn_result") {
    lastTurnResult.value = obj?.payload ?? null;
    liveAsrPreview.value = "";
    realtimeStreamCommitted.value = "";
    realtimeStreamPartial.value = "";
    asrHint.value = "";
    pushLog(`本轮评价已生成 shouldEnd=${obj?.payload?.shouldEndInterview}`);
    void refreshTurns();
    return;
  }
  if (t === "ended") {
    lastTurnResult.value = null;
    fsmUi.value = InterviewState.END;
    asrHint.value = "";
    liveAsrPreview.value = "";
    realtimeStreamCommitted.value = "";
    realtimeStreamPartial.value = "";
    lastEndedSummary.value = String(obj?.payload?.evaluation ?? "").trim();
    pushLog(`结束：${obj?.payload?.reason} 总评=${(obj?.payload?.evaluation || "").slice(0, 100)}…`);
    return;
  }
}

function connectWs() {
  const url = buildWsUrl();
  wsStatus.value = "连接中…";
  ws = new WebSocket(url);
  ws.onopen = () => {
    wsStatus.value = "已连接";
    fsmUi.value = InterviewState.READY;
    sendJson({ type: "ping" });
    void ensureSpeechVoicesLoaded();
  };
  ws.onclose = () => {
    wsStatus.value = "已断开";
  };
  ws.onerror = () => {
    wsStatus.value = "错误";
  };
  ws.onmessage = (ev) => {
    try {
      const obj = JSON.parse(ev.data);
      handleServerPayload(obj);
    } catch {
      pushLog(`非 JSON：${String(ev.data).slice(0, 120)}`);
    }
  };
}

async function refreshEvents() {
  try {
    const sid = props.session.sessionId;
    const list = await listVideoInterviewEvents(sid);
    events.value = Array.isArray(list) ? list : [];
    pushLog(`已刷新事件 ${events.value.length} 条`);
  } catch (e) {
    pushLog(`拉取事件失败：${e?.message || e}`);
  }
}

async function refreshTurns() {
  try {
    const sid = props.session.sessionId;
    const list = await listVideoInterviewTurns(sid);
    turns.value = Array.isArray(list) ? list : [];
    ensureSelectedTurnAfterRefresh();
  } catch (e) {
    pushLog(`拉取轮次失败：${e?.message || e}`);
  }
}

function selectTurn(turnId) {
  const id = String(turnId || "").trim();
  if (!id) return;
  selectedTurnId.value = id;
}

/** 服务端推进到新一题时，侧栏跟随当前题 */
watch(currentTurnId, (tid) => {
  const id = String(tid || "").trim();
  if (id) selectedTurnId.value = id;
});

function ensureSelectedTurnAfterRefresh() {
  const list = turns.value || [];
  if (!list.length) return;
  const cur = String(currentTurnId.value || "").trim();
  const sel = String(selectedTurnId.value || "").trim();
  const hasSel = sel && list.some((t) => t.turnId === sel);
  if (hasSel) return;
  if (cur && list.some((t) => t.turnId === cur)) {
    selectedTurnId.value = cur;
    return;
  }
  const sorted = [...list].sort((a, b) => (a.turnIndex || 0) - (b.turnIndex || 0));
  selectedTurnId.value = sorted[0].turnId;
}

function resetAsrStreamState() {
  asrStreamGen += 1;
  asrDrainChain = Promise.resolve();
  asrRecordedBytes = 0;
  restAccumBuffer = null;
  restAccumLength = 0;
  restLastFlushEnd = 0;
}

/** float32 输入线性重采样为 16kHz 单声道 Int16（与 Omni 上行、REST 重叠切片共用） */
function float32ToInt16At16kHz(float32, inputRate) {
  const ratio = inputRate / 16000;
  if (!Number.isFinite(ratio) || ratio < 1) {
    return new Int16Array(0);
  }
  const outLen = Math.floor(float32.length / ratio);
  if (outLen < 1) {
    return new Int16Array(0);
  }
  const i16 = new Int16Array(outLen);
  for (let i = 0; i < outLen; i++) {
    const srcPos = i * ratio;
    const j0 = Math.floor(srcPos);
    const j1 = Math.min(j0 + 1, float32.length - 1);
    const f = srcPos - j0;
    let s = (1 - f) * float32[j0] + f * float32[j1];
    s = Math.max(-1, Math.min(1, s));
    i16[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
  }
  return i16;
}

/** 将 float32 输入重采样为 16kHz PCM16 并 base64（供 Omni 上行）。 */
function floatTo16kPcmBase64(float32, inputRate) {
  const i16 = float32ToInt16At16kHz(float32, inputRate);
  if (!i16.length) return "";
  const bytes = new Uint8Array(i16.buffer);
  let binary = "";
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary);
}

function stopRealtimePcmCapture() {
  try {
    if (pcmScriptNode) {
      pcmScriptNode.onaudioprocess = null;
      pcmScriptNode.disconnect();
    }
  } catch {
    /* ignore */
  }
  pcmScriptNode = null;
  try {
    pcmSourceNode?.disconnect();
  } catch {
    /* ignore */
  }
  pcmSourceNode = null;
  try {
    pcmMuteGainNode?.disconnect();
  } catch {
    /* ignore */
  }
  pcmMuteGainNode = null;
}

function startRealtimePcmCapture() {
  stopRealtimePcmCapture();
  if (!mediaStream || !audioCtx) {
    pushLog("实时转写需要麦克风与音频上下文已就绪。");
    return;
  }
  const inRate = audioCtx.sampleRate;
  pcmSourceNode = audioCtx.createMediaStreamSource(mediaStream);
  const bufferSize = 2048;
  pcmScriptNode = audioCtx.createScriptProcessor(bufferSize, 1, 1);
  pcmMuteGainNode = audioCtx.createGain();
  pcmMuteGainNode.gain.value = 0;
  pcmScriptNode.onaudioprocess = (e) => {
    if (fsmUi.value !== InterviewState.RECORDING || !serverRealtimeAsrEnabled.value) return;
    const input = e.inputBuffer.getChannelData(0);
    const b64 = floatTo16kPcmBase64(input, inRate);
    if (b64) sendJson({ type: "asr_pcm_base64", base64: b64 });
  };
  pcmSourceNode.connect(pcmScriptNode);
  pcmScriptNode.connect(pcmMuteGainNode);
  pcmMuteGainNode.connect(audioCtx.destination);
}

function appendRestPcm16Chunk(chunk) {
  if (!chunk?.length) return;
  const need = restAccumLength + chunk.length;
  if (!restAccumBuffer || restAccumBuffer.length < need) {
    const nextSize = Math.max(need * 2, restAccumBuffer ? Math.ceil(restAccumBuffer.length * 1.5) : 0, 32768, need);
    const nb = new Int16Array(nextSize);
    if (restAccumBuffer && restAccumLength > 0) {
      nb.set(restAccumBuffer.subarray(0, restAccumLength), 0);
    }
    restAccumBuffer = nb;
  }
  restAccumBuffer.set(chunk, restAccumLength);
  restAccumLength += chunk.length;
}

function restOverlapSamples16k() {
  return Math.floor((16000 * REST_ASR_OVERLAP_MS) / 1000);
}

function scheduleRestOverlappingTranscribe(slice16Copy, capturedGen) {
  const sid = String(props.session?.sessionId || "").trim();
  if (!sid || !slice16Copy?.length) return;
  asrRecordedBytes += slice16Copy.byteLength;
  asrDrainChain = asrDrainChain.catch(() => {}).then(async () => {
    if (capturedGen !== asrStreamGen) return;
    try {
      asrHint.value = "（REST 分段：含约 " + REST_ASR_OVERLAP_MS + "ms 重叠窗…）";
      const wav = encodeMonoWavFromPcm16(slice16Copy);
      const data = await transcribeVideoInterviewAudio(sid, wav);
      if (capturedGen !== asrStreamGen) return;
      const t = (data?.text || "").trim();
      if (t) {
        const cur = String(liveAsrPreview.value || "").trim();
        liveAsrPreview.value = mergeAsrTranscriptSegments(cur, t);
      }
    } catch (e) {
      pushLog(`分段转写失败：${e?.message || e}`);
    }
  });
}

function tryFlushRestOverlappingSlice(isFinal) {
  if (serverRealtimeAsrEnabled.value) return;
  const end = restAccumLength;
  if (end <= 0) return;
  if (!isFinal && end <= restLastFlushEnd) return;
  const overlap = restOverlapSamples16k();
  const start = restLastFlushEnd === 0 ? 0 : Math.max(0, restLastFlushEnd - overlap);
  if (end <= start) return;
  const sliceLen = end - start;
  if (!isFinal && sliceLen < MIN_REST_SLICE_SAMPLES) return;
  if (!restAccumBuffer) return;
  const slice = restAccumBuffer.subarray(start, end);
  const copy = new Int16Array(slice.length);
  copy.set(slice);
  const gen = asrStreamGen;
  restLastFlushEnd = end;
  scheduleRestOverlappingTranscribe(copy, gen);
}

function stopRestOverlappingPcmCapture() {
  if (restSliceIntervalId != null) {
    clearInterval(restSliceIntervalId);
    restSliceIntervalId = null;
  }
  try {
    if (restAsrScriptNode) {
      restAsrScriptNode.onaudioprocess = null;
      restAsrScriptNode.disconnect();
    }
  } catch {
    /* ignore */
  }
  restAsrScriptNode = null;
  try {
    restAsrSourceNode?.disconnect();
  } catch {
    /* ignore */
  }
  restAsrSourceNode = null;
  try {
    restAsrMuteGainNode?.disconnect();
  } catch {
    /* ignore */
  }
  restAsrMuteGainNode = null;
  tryFlushRestOverlappingSlice(true);
}

function startRestOverlappingPcmCapture() {
  stopRestOverlappingPcmCapture();
  stopRealtimePcmCapture();
  if (!mediaStream || !audioCtx) {
    pushLog("REST 分段转写需要麦克风与音频上下文已就绪。");
    return;
  }
  const inRate = audioCtx.sampleRate;
  restAsrSourceNode = audioCtx.createMediaStreamSource(mediaStream);
  const bufferSize = 2048;
  restAsrScriptNode = audioCtx.createScriptProcessor(bufferSize, 1, 1);
  restAsrMuteGainNode = audioCtx.createGain();
  restAsrMuteGainNode.gain.value = 0;
  restAsrScriptNode.onaudioprocess = (e) => {
    if (fsmUi.value !== InterviewState.RECORDING || serverRealtimeAsrEnabled.value) return;
    const input = e.inputBuffer.getChannelData(0);
    const chunk = float32ToInt16At16kHz(input, inRate);
    appendRestPcm16Chunk(chunk);
  };
  restAsrSourceNode.connect(restAsrScriptNode);
  restAsrScriptNode.connect(restAsrMuteGainNode);
  restAsrMuteGainNode.connect(audioCtx.destination);
  restSliceIntervalId = window.setInterval(() => {
    tryFlushRestOverlappingSlice(false);
  }, ASR_SLICE_MS);
}

function maybeStartAsrRecorder() {
  if (!mediaStream) return;
  if (restSliceIntervalId != null || restAsrScriptNode) return;
  resetAsrStreamState();
  realtimeStreamCommitted.value = "";
  realtimeStreamPartial.value = "";
  liveAsrPreview.value = "";
  startRestOverlappingPcmCapture();
}

function stopAsrRecorderAndWaitDrain() {
  if (serverRealtimeAsrEnabled.value) {
    return asrDrainChain.catch(() => {});
  }
  stopRestOverlappingPcmCapture();
  return asrDrainChain.catch(() => {});
}

/** 麦克风 +（正式面试）声纹能量轨比对；模拟面试不做相似度计算 */
async function startMicVoiceprintOnly() {
  mediaStream = await navigator.mediaDevices.getUserMedia({
    audio: {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
      channelCount: 1
    },
    video: false
  });
  micReady.value = true;
  audioCtx = new AudioContext();
  try {
    await audioCtx.resume();
  } catch {
    /* ignore */
  }
  const src = audioCtx.createMediaStreamSource(mediaStream);
  analyser = audioCtx.createAnalyser();
  analyser.fftSize = 2048;
  src.connect(analyser);
  const data = new Uint8Array(analyser.fftSize);
  const loop = () => {
    analyser.getByteTimeDomainData(data);
    if (skipVoiceprint.value) {
      voiceSim.value = 1;
    } else {
      const tpl = voiceprintTemplate.value;
      if (tpl && tpl.length) {
        const live = liveVectorFromTimeDomain(data);
        voiceSim.value = cosineSimilarity(live, tpl);
      } else {
        voiceSim.value = 1;
      }
    }
    if (fsmUi.value === InterviewState.RECORDING) {
      voiceSimPeak = Math.max(voiceSimPeak, voiceSim.value);
    }
    rafId = requestAnimationFrame(loop);
  };
  rafId = requestAnimationFrame(loop);
}

async function onStartAnswer() {
  if (fsmUi.value !== InterviewState.AWAITING_ANSWER) {
    pushLog("当前不可开始作答（请等题目流式输出完成）。");
    return;
  }
  cancelInterviewerSpeech();
  voiceSimPeak = 0;
  realtimeStreamCommitted.value = "";
  realtimeStreamPartial.value = "";
  liveAsrPreview.value = "";
  sendJson({ type: "record_start" });
  if (serverRealtimeAsrEnabled.value) {
    startRealtimePcmCapture();
    pushLog("已开始录音（服务端实时转写）。");
  } else {
    maybeStartAsrRecorder();
    pushLog("已开始录音，说完后点「结束回答」。");
  }
}

async function onStopAnswer() {
  if (fsmUi.value !== InterviewState.RECORDING) {
    pushLog("当前未在录音。");
    return;
  }
  if (serverRealtimeAsrEnabled.value) {
    asrHint.value = "（正在结束实时转写…）";
    stopRealtimePcmCapture();
    await new Promise((r) => setTimeout(r, 60));
    const waitClosed = new Promise((resolve) => {
      const t = setTimeout(() => resolve(), 2200);
      asrRealtimeClosedResolver = () => {
        clearTimeout(t);
        resolve();
      };
    });
    sendJson({ type: "asr_realtime_end" });
    await waitClosed;
    asrRealtimeClosedResolver = null;
  } else {
    asrHint.value = "（正在结束录音并等待末段识别…）";
    await stopAsrRecorderAndWaitDrain();
    await asrDrainChain.catch(() => {});
  }

  const simBlend = Math.max(voiceSimPeak, voiceSim.value);
  const snapFilter = voiceFilterOn.value;
  let text = String(liveAsrPreview.value || "").trim();
  if (!text && !serverRealtimeAsrEnabled.value && asrRecordedBytes < 500) {
    asrHint.value = "录音过短或未识别到内容，请重试。";
    pushLog("作答过短或无识别文本");
    return;
  }
  if (!text) {
    asrHint.value = "未识别到有效中文内容，请重试或检查麦克风与 ASR 配置。";
    pushLog("整段转写为空");
    return;
  }
  const raw = text;
  const gate =
    !skipVoiceprint.value &&
    snapFilter &&
    voiceprintTemplate.value?.length &&
    simBlend < VOICE_SIM_THRESHOLD &&
    raw;
  if (gate) {
    asrHint.value = `声纹门控：与模板匹配不足（约 ${(simBlend * 100).toFixed(0)}%），未提交。`;
    pushLog("声纹门控丢弃本句");
    return;
  }
  asrHint.value = "";
  const tid = (currentTurnId.value || "").trim();
  if (!tid) {
    pushLog("缺少 turnId");
    return;
  }
  sendJson({ type: "answer_submit", turnId: tid, text });
  pushLog(
    serverRealtimeAsrEnabled.value
      ? "已提交作答（服务端实时转写优先，空则使用本页合并文本）"
      : "已提交作答文本（分段实时转写已合并，交由服务端 Agent）"
  );
  void refreshTurns();
}

function onContinueNext() {
  sendJson({ type: "continue_next" });
  lastTurnResult.value = null;
  streamingQuestion.value = "";
  pushLog("请求下一题");
  void refreshTurns();
}

function onFinishSession() {
  sendJson({ type: "finish_session", reason: "user_ack" });
  pushLog("已确认结束面试：服务端将异步生成全场总评、维度打分与改进建议。");
}

/** 页眉「结束面试」：在 AI 已建议收尾时走 finish_session，否则走 end_interview；均触发终局总评流程。 */
function onHeaderEndInterview() {
  if (canFinishSession.value) {
    onFinishSession();
  } else {
    userEnd();
  }
}

function onRetrySame() {
  const tid = (currentTurnId.value || "").trim();
  if (!tid) return;
  stopRealtimePcmCapture();
  stopRestOverlappingPcmCapture();
  sendJson({ type: "turn_retry_same", turnId: tid });
  lastTurnResult.value = null;
  liveAsrPreview.value = "";
  realtimeStreamCommitted.value = "";
  realtimeStreamPartial.value = "";
  resetAsrStreamState();
  pushLog("请求重新作答本题");
}

function teardown() {
  stopRealtimePcmCapture();
  stopRestOverlappingPcmCapture();
  asrRealtimeClosedResolver = null;
  resetAsrStreamState();
  if (rafId) cancelAnimationFrame(rafId);
  rafId = 0;
  try {
    mediaStream?.getTracks().forEach((t) => t.stop());
  } catch {
    /* ignore */
  }
  mediaStream = null;
  micReady.value = false;
  liveAsrPreview.value = "";
  asrHint.value = "";
  lastTurnResult.value = null;
  selectedTurnId.value = "";
  try {
    audioCtx?.close();
  } catch {
    /* ignore */
  }
  audioCtx = null;
  try {
    ws?.close();
  } catch {
    /* ignore */
  }
  ws = null;
  fsmUi.value = InterviewState.IDLE;
  cancelInterviewerSpeech();
}

function onClose() {
  teardown();
  emit("close");
}

function userEnd() {
  sendJson({ type: "end_interview" });
  pushLog("已请求结束本场面试：服务端将异步生成全场总评、维度打分与改进建议。");
}

const titleText = computed(() => {
  const phase = props.context.forMock ? "模拟" : "正式";
  const rt = (props.context.roundTitle || "").trim() || `第${props.context.roundIndex + 1}轮`;
  return `${phase} · ${rt} · 语音模拟面试`;
});

/** 解析服务端 turn_result.evaluation（Json 对象或字符串） */
function formatEvaluationForUi(ev) {
  if (ev == null) {
    return { summary: "", strengths: "", risks: "", dimensionsLines: [] };
  }
  if (typeof ev === "string") {
    const t = ev.trim();
    return { summary: t, strengths: "", risks: "", dimensionsLines: [] };
  }
  if (typeof ev === "object") {
    const summary = String(ev.overall_summary ?? ev.overallSummary ?? "").trim();
    let strengths = "";
    if (Array.isArray(ev.strengths)) {
      strengths = ev.strengths.map((x) => String(x).trim()).filter(Boolean).join("；");
    } else {
      strengths = String(ev.strengths ?? "").trim();
    }
    let risks = "";
    if (Array.isArray(ev.risks)) {
      risks = ev.risks.map((x) => String(x).trim()).filter(Boolean).join("；");
    } else {
      risks = String(ev.risks ?? "").trim();
    }
    const schemaVersion = Number(ev.schema_version);
    const legacyDecile = !Number.isFinite(schemaVersion) || schemaVersion < 2;
    const dims = Array.isArray(ev.dimensions) ? ev.dimensions : [];
    const dimensionsLines = dims.map((d) => {
      const name = d?.name != null ? String(d.name) : "";
      const raw = d?.score ?? d?.score0;
      let pts = typeof raw === "number" ? raw : Number.parseFloat(String(raw));
      if (!Number.isFinite(pts)) {
        const comment = d?.comment != null ? String(d.comment) : "";
        const head = name.trim();
        return head ? `${head}：${comment}`.trim() : comment;
      }
      if (legacyDecile && pts >= 0 && pts <= 10) {
        pts = Math.round(pts * 10);
      }
      pts = Math.min(100, Math.max(0, Math.round(pts)));
      const comment = d?.comment != null ? String(d.comment) : "";
      const sc = ` ${pts}分`;
      const head = `${name}${sc}`.trim();
      return head ? `${head}：${comment}`.trim() : comment;
    });
    return { summary, strengths, risks, dimensionsLines };
  }
  return { summary: "", strengths: "", risks: "", dimensionsLines: [] };
}

const sortedTurns = computed(() => {
  const arr = [...(turns.value || [])];
  arr.sort((a, b) => (Number(a.turnIndex) || 0) - (Number(b.turnIndex) || 0));
  return arr;
});

const selectedTurn = computed(() => {
  const id = String(selectedTurnId.value || "").trim();
  if (!id) return null;
  return sortedTurns.value.find((t) => t.turnId === id) ?? null;
});

const isViewingActiveTurn = computed(() => {
  const a = String(selectedTurnId.value || "").trim();
  const b = String(currentTurnId.value || "").trim();
  return Boolean(a && b && a === b);
});

function parseJsonSafe(raw) {
  if (raw == null) return null;
  if (typeof raw === "object") return raw;
  const s = String(raw).trim();
  if (!s) return null;
  try {
    return JSON.parse(s);
  } catch {
    return raw;
  }
}

const selectedPanelEvalUi = computed(() => {
  const sel = String(selectedTurnId.value || "").trim();
  const ltr = lastTurnResult.value;
  if (ltr && String(ltr.turnId || "") === sel) {
    return formatEvaluationForUi(ltr.evaluation);
  }
  const st = selectedTurn.value;
  if (!st?.evaluationJson) return formatEvaluationForUi(null);
  const parsed = parseJsonSafe(st.evaluationJson);
  if (parsed !== null && typeof parsed === "object") {
    return formatEvaluationForUi(parsed);
  }
  return formatEvaluationForUi(st.evaluationJson);
});

const selectedPanelStandardAnswer = computed(() => {
  const sel = String(selectedTurnId.value || "").trim();
  const ltr = lastTurnResult.value;
  if (ltr && String(ltr.turnId || "") === sel && ltr.standardAnswer) {
    return String(ltr.standardAnswer);
  }
  return String(selectedTurn.value?.standardAnswer || "");
});

const selectedPanelNextHint = computed(() => {
  const sel = String(selectedTurnId.value || "").trim();
  const ltr = lastTurnResult.value;
  if (ltr && String(ltr.turnId || "") === sel) {
    return String(ltr.nextQuestionHint || "");
  }
  return "";
});

const selectedPanelBridgingUtterance = computed(() => {
  const sel = String(selectedTurnId.value || "").trim();
  const ltr = lastTurnResult.value;
  if (ltr && String(ltr.turnId || "") === sel && ltr.bridgingUtterance) {
    return String(ltr.bridgingUtterance).trim();
  }
  return String(selectedTurn.value?.bridgingUtterance || "").trim();
});

function playBridgingUtterance() {
  const t = selectedPanelBridgingUtterance.value.trim();
  if (t) speakBrowserTts(t);
}

const selectedPanelMeta = computed(() => {
  const sel = String(selectedTurnId.value || "").trim();
  const ltr = lastTurnResult.value;
  if (ltr && String(ltr.turnId || "") === sel) {
    return {
      shouldEnd: ltr.shouldEndInterview,
      endReason: String(ltr.endReason || "")
    };
  }
  return { shouldEnd: null, endReason: "" };
});

const displayQuestionText = computed(() => {
  const st = selectedTurn.value;
  const sel = String(selectedTurnId.value || "").trim();
  const cur = String(currentTurnId.value || "").trim();
  if (sel && cur && sel === cur) {
    const live = String(lastSpeak.value || streamingQuestion.value || "").trim();
    if (live) return live;
  }
  return String(st?.questionText || "").trim() || "（题目尚未生成或仍在流式输出，请稍候）";
});

const displayAnswerText = computed(() => {
  const sel = String(selectedTurnId.value || "").trim();
  const cur = String(currentTurnId.value || "").trim();
  if (sel && cur && sel === cur) {
    const live = String(liveAsrPreview.value || "").trim();
    const hint = String(asrHint.value || "").trim();
    if (fsmUi.value === InterviewState.RECORDING || hint) {
      const rtHint = serverRealtimeAsrEnabled.value
        ? "（录音中：服务端流式转写；点「结束回答」后提交评价）"
        : `（录音中：浏览器端约每 ${ASR_SLICE_MS / 1000}s 取 PCM 窗送 ASR，窗与窗含约 ${REST_ASR_OVERLAP_MS}ms 重叠；点「结束回答」后整段提交评价）`;
      return live || hint || rtHint;
    }
  }
  return String(selectedTurn.value?.answerText || "").trim() || "（尚未提交作答）";
});

const hasSelectedReviewContent = computed(() => {
  const ev = selectedPanelEvalUi.value;
  const hasEv =
    Boolean(ev.summary) || ev.dimensionsLines.length > 0 || Boolean(ev.strengths) || Boolean(ev.risks);
  return (
    hasEv ||
    Boolean(selectedPanelStandardAnswer.value) ||
    Boolean(selectedPanelNextHint.value) ||
    Boolean(selectedPanelBridgingUtterance.value) ||
    selectedPanelMeta.value.shouldEnd != null
  );
});

const resolvedStyleKey = computed(() => {
  const s = props.session?.interviewerStyleKey ?? props.context?.interviewerStyleKey ?? "";
  return String(s).trim();
});

const resolvedStyleLabel = computed(() =>
  interviewerStyleLabel(resolvedStyleKey.value, props.interviewerCustomStyles || [])
);

const fsmLine = computed(() => fsmLabelZh(fsmUi.value));

const canContinueNext = computed(
  () => fsmUi.value === InterviewState.POST_TURN_REVIEW && lastTurnResult.value && !lastTurnResult.value.shouldEndInterview
);
const canFinishSession = computed(
  () => fsmUi.value === InterviewState.POST_TURN_REVIEW && lastTurnResult.value && lastTurnResult.value.shouldEndInterview
);
const canRetrySame = computed(() => fsmUi.value === InterviewState.POST_TURN_REVIEW);

const isSessionClosing = computed(() => fsmUi.value === InterviewState.SESSION_CLOSING);

const canContinueNextPanel = computed(() => isViewingActiveTurn.value && canContinueNext.value);

/** 合并「开始回答 / 结束回答并提交 / 重新作答本题」为单一主按钮：随状态切换样式与行为 */
const mergedAnswerControl = computed(() => {
  const pickTitle = (extra) => (!isViewingActiveTurn.value ? "请先在左侧选择带「当前」标记的题目" : extra || "");
  if (!isViewingActiveTurn.value) {
    return {
      action: "none",
      label: "作答",
      hint: "请选当前题",
      title: pickTitle(""),
      disabled: true,
      buttonClass: "bg-slate-100 text-slate-500 border border-slate-200"
    };
  }
  if (fsmUi.value === InterviewState.RECORDING) {
    return {
      action: "stop",
      label: "结束回答并提交",
      hint: "",
      title: pickTitle("停止录音并提交转写，触发服务端评价"),
      disabled: false,
      buttonClass: "bg-amber-600 text-white hover:bg-amber-700 shadow-sm"
    };
  }
  if (fsmUi.value === InterviewState.AWAITING_ANSWER) {
    return {
      action: "start",
      label: "开始回答",
      hint: "",
      title: pickTitle("开始拾音与转写"),
      disabled: false,
      buttonClass: "bg-emerald-600 text-white hover:bg-emerald-700 shadow-sm"
    };
  }
  if (fsmUi.value === InterviewState.POST_TURN_REVIEW && canRetrySame.value) {
    return {
      action: "retry",
      label: "重新作答本题",
      hint: "",
      title: pickTitle("清空本轮作答并回到可录音状态"),
      disabled: false,
      buttonClass: "border-2 border-orange-500 text-orange-900 bg-white hover:bg-orange-50 shadow-sm"
    };
  }
  if (fsmUi.value === InterviewState.QUESTION_STREAMING) {
    return {
      action: "none",
      label: "开始回答",
      hint: "题目生成中…",
      title: pickTitle("请等待题目流式输出完成"),
      disabled: true,
      buttonClass: "bg-slate-100 text-slate-600 border border-slate-200"
    };
  }
  if (fsmUi.value === InterviewState.AGENT_PROCESSING) {
    return {
      action: "none",
      label: "开始回答",
      hint: "评价生成中…",
      title: pickTitle("请稍候"),
      disabled: true,
      buttonClass: "bg-slate-100 text-slate-600 border border-slate-200"
    };
  }
  return {
    action: "none",
    label: "开始回答",
    hint: "当前不可作答",
    title: pickTitle(""),
    disabled: true,
    buttonClass: "bg-slate-100 text-slate-500 border border-slate-200"
  };
});

function onMergedAnswerPrimary() {
  const a = mergedAnswerControl.value.action;
  if (a === "start") void onStartAnswer();
  else if (a === "stop") void onStopAnswer();
  else if (a === "retry") onRetrySame();
}

watch(
  () => tab.value,
  (v) => {
    if (v === "timeline") {
      refreshEvents();
    }
  }
);

watch(
  () => props.context?.forMock,
  () => {
    reloadVoiceprint();
  }
);

watch(
  () => props.session?.sessionId,
  () => {
    selectedTurnId.value = "";
    lastEndedSummary.value = "";
    reloadVoiceprint();
    void refreshTurns();
  },
  { immediate: true }
);

function onVoiceprintGlobalUpdated(ev) {
  if (skipVoiceprint.value) return;
  if (ev && ev.type === "storage" && ev.key && !String(ev.key).includes("voiceprint")) return;
  reloadVoiceprint();
}

onMounted(() => {
  window.addEventListener("storage", onVoiceprintGlobalUpdated);
  window.addEventListener("mienmien-voiceprint-global-updated", onVoiceprintGlobalUpdated);
  connectWs();
  startMicVoiceprintOnly()
    .then(() => {
      pushLog(
        skipVoiceprint.value
          ? "流程（模拟）：流式出题 → 主作答按钮（开始/结束提交/重答）→ 回顾后下一题；结束请用页眉「结束面试并生成总评」。模拟面试不使用声纹门控。"
          : "流程：流式出题 → 主作答按钮录音与提交 → 回顾后「下一题」；结束整场请点页眉「结束面试并生成总评」（异步总评/打分/建议）。同轮可重答。正式面试可配合全局声纹门控。"
      );
      void refreshTurns();
    })
    .catch((e) => pushLog(`麦克风：${e?.message || e}`));
});

onBeforeUnmount(() => {
  window.removeEventListener("storage", onVoiceprintGlobalUpdated);
  window.removeEventListener("mienmien-voiceprint-global-updated", onVoiceprintGlobalUpdated);
  teardown();
});
</script>

<template>
  <div class="fixed inset-0 z-[120] flex items-center justify-center bg-black/60 p-3" @click.self="onClose">
    <div
      class="bg-white rounded-xl shadow-2xl border border-gray-100 w-full max-w-6xl max-h-[92vh] flex flex-col overflow-hidden"
      @click.stop
    >
      <header class="flex items-center justify-between px-4 py-3 border-b border-gray-100 bg-slate-50">
        <div>
          <h2 class="text-lg font-bold text-gray-900">{{ titleText }}</h2>
          <p class="text-xs text-gray-500 mt-0.5">
            会话 {{ session.sessionId }} · 风格 {{ resolvedStyleLabel || "默认" }}（{{ resolvedStyleKey || "—" }}） · 麦克风
            {{ micReady ? "已就绪" : "未就绪" }} · WS {{ wsStatus }} · {{ fsmLine }}
          </p>
        </div>
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="inline-flex items-center justify-center gap-2 px-3 py-1.5 text-sm rounded-lg text-white transition-colors"
            :class="
              fsmUi === InterviewState.END
                ? 'bg-emerald-600 cursor-default disabled:opacity-100'
                : isSessionClosing
                  ? 'bg-amber-600 cursor-wait disabled:opacity-100'
                  : 'bg-slate-800 hover:bg-slate-900 disabled:opacity-40 disabled:cursor-not-allowed'
            "
            :disabled="isSessionClosing || fsmUi === InterviewState.END"
            title="结束整场模拟面试。服务端将综合各轮问答异步生成总评、维度打分与改进建议；进行中也可随时结束。"
            @click="onHeaderEndInterview"
          >
            <template v-if="isSessionClosing">
              <i class="fa-solid fa-spinner fa-spin" aria-hidden="true"></i>
              <span>生成中…</span>
            </template>
            <template v-else-if="fsmUi === InterviewState.END">
              <i class="fa-solid fa-circle-check" aria-hidden="true"></i>
              <span>生成完成</span>
            </template>
            <template v-else>
              <span>结束面试并生成总评</span>
            </template>
          </button>
          <button type="button" class="px-3 py-1.5 text-sm rounded-lg bg-primary text-white hover:bg-blue-700" @click="onClose">
            关闭
          </button>
        </div>
      </header>

      <div class="flex border-b border-gray-100 text-sm">
        <button
          type="button"
          class="px-4 py-2"
          :class="tab === 'room' ? 'border-b-2 border-primary font-semibold text-primary' : 'text-gray-500'"
          @click="tab = 'room'"
        >
          面试间
        </button>
        <button
          type="button"
          class="px-4 py-2"
          :class="tab === 'timeline' ? 'border-b-2 border-primary font-semibold text-primary' : 'text-gray-500'"
          @click="tab = 'timeline'"
        >
          事件时间线（调试）
        </button>
      </div>

      <div v-if="tab === 'room'" class="flex-1 min-h-0 flex flex-col">
        <div class="flex-1 min-h-0 flex flex-row overflow-hidden">
          <!-- 左侧：题目导航（随「下一题」自动增加） -->
          <aside
            class="w-52 shrink-0 border-r border-gray-200 bg-slate-50/95 flex flex-col min-h-0 min-w-[13rem]"
          >
            <div class="px-3 py-2 border-b border-gray-200 flex items-center justify-between gap-2">
              <span class="text-xs font-semibold text-gray-800">题目导航</span>
              <button type="button" class="text-xs text-primary hover:underline shrink-0" @click="refreshTurns">
                刷新
              </button>
            </div>
            <ul class="flex-1 overflow-y-auto p-2 space-y-1.5">
              <li v-if="!sortedTurns.length" class="text-xs text-gray-500 px-2 py-3">暂无题目，等待第一题流式生成…</li>
              <li v-for="t in sortedTurns" :key="t.turnId" class="list-none">
                <button
                  type="button"
                  class="rounded-lg border p-2.5 cursor-pointer text-left transition-colors w-full"
                  :class="
                    selectedTurnId === t.turnId
                      ? 'border-primary bg-primary/5 ring-1 ring-primary/25'
                      : 'border-gray-200 bg-white hover:border-gray-300 hover:bg-gray-50/80'
                  "
                  @click="selectTurn(t.turnId)"
                >
                <div class="flex items-center justify-between gap-1 mb-1">
                  <span class="text-sm font-semibold text-gray-900">第 {{ t.turnIndex }} 题</span>
                  <span
                    v-if="String(currentTurnId) === String(t.turnId)"
                    class="text-[10px] px-1.5 py-0.5 rounded bg-violet-100 text-violet-800 shrink-0"
                    >当前</span
                  >
                  <span
                    v-else-if="t.answerText"
                    class="text-[10px] px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-800 shrink-0"
                    >已答</span
                  >
                </div>
                <p class="text-[11px] text-gray-600 line-clamp-3 leading-snug">
                  {{ t.questionText || "（题目生成中…）" }}
                </p>
                </button>
              </li>
            </ul>
          </aside>

          <!-- 右侧：本题详情 + 作答与回顾 -->
          <div class="flex-1 min-w-0 flex flex-col min-h-0">
            <div class="flex-1 overflow-y-auto p-4 space-y-4 min-h-0">
              <div
                v-if="!isViewingActiveTurn && selectedTurn"
                class="rounded-lg border border-amber-200 bg-amber-50/90 px-3 py-2 text-xs text-amber-950"
              >
                正在查看历史题目；下方<strong>主作答按钮</strong>与「下一题」仅在选择<strong>当前题</strong>（侧栏带「当前」标记）时可用；结束整场面试请用<strong>页眉「结束面试并生成总评」</strong>。
              </div>

              <div
                v-if="questionGenNotice"
                class="rounded-lg border border-amber-400 bg-amber-50/95 p-3 text-sm text-amber-950 flex justify-between gap-3 items-start"
                role="alert"
              >
                <span class="min-w-0">{{ questionGenNotice }}</span>
                <button
                  type="button"
                  class="shrink-0 text-xs text-amber-900 underline hover:no-underline"
                  @click="clearQuestionGenNotice"
                >
                  关闭
                </button>
              </div>

              <div
                v-if="isSessionClosing"
                class="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-950"
                role="status"
              >
                <div class="flex items-start gap-2.5">
                  <i class="fa-solid fa-spinner fa-spin text-amber-700 mt-0.5 shrink-0" aria-hidden="true"></i>
                  <div class="min-w-0">
                    <p class="font-medium text-amber-950">生成中</p>
                    <p class="mt-1 text-amber-900/90 leading-relaxed">
                      终局总评正在后台生成，请稍候；完成后将自动显示总评摘要。此期间不可再作答或重答。
                    </p>
                  </div>
                </div>
              </div>

              <div
                v-else-if="fsmUi === InterviewState.END"
                class="rounded-lg border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-950"
                role="status"
              >
                <div class="flex items-start gap-2.5">
                  <i class="fa-solid fa-circle-check text-emerald-600 mt-0.5 shrink-0 text-lg" aria-hidden="true"></i>
                  <div class="min-w-0 flex-1">
                    <p class="font-medium text-emerald-900">生成完成</p>
                    <p
                      v-if="lastEndedSummary"
                      class="mt-2 text-emerald-950/95 whitespace-pre-wrap leading-relaxed border-t border-emerald-200/80 pt-2"
                    >
                      {{ lastEndedSummary }}
                    </p>
                    <p v-else class="mt-1 text-xs text-emerald-800/90 leading-relaxed">
                      会话已结束；总评已写入业务侧面试记录，关闭本窗口后可在面试详情页查看完整内容。
                    </p>
                  </div>
                </div>
              </div>

              <div>
                <h3 class="text-sm font-semibold text-gray-700 mb-2">题目详情</h3>
                <p class="text-sm text-gray-900 whitespace-pre-wrap min-h-[3rem] rounded-lg border border-gray-200 bg-white p-3">
                  {{ displayQuestionText }}
                </p>
              </div>

              <div>
                <h3 class="text-sm font-semibold text-gray-700 mb-2">作答记录（转写 / 已提交）</h3>
                <p
                  class="text-sm text-gray-800 whitespace-pre-wrap min-h-[3rem] rounded-lg border border-emerald-100 bg-emerald-50/50 p-3"
                >
                  {{ displayAnswerText }}
                </p>
              </div>

              <div
                v-if="selectedPanelBridgingUtterance"
                class="rounded-lg border border-sky-200 bg-sky-50/80 p-3 text-sm text-sky-950 space-y-2"
              >
                <div class="flex items-start justify-between gap-2 flex-wrap">
                  <p class="font-semibold text-sky-900 shrink-0">面试官衔接语</p>
                  <button
                    type="button"
                    class="text-xs text-sky-800 hover:underline shrink-0"
                    @click="playBridgingUtterance"
                  >
                    播放衔接语
                  </button>
                </div>
                <p class="text-sm text-sky-950 whitespace-pre-wrap leading-relaxed">{{ selectedPanelBridgingUtterance }}</p>
              </div>

              <div
                v-if="hasSelectedReviewContent"
                class="rounded-lg border border-indigo-100 bg-indigo-50/60 p-3 text-sm text-indigo-950 space-y-3"
              >
                <p class="font-semibold text-indigo-900">评价与建议</p>
                <div
                  v-if="
                    selectedPanelEvalUi.summary ||
                    selectedPanelEvalUi.dimensionsLines.length ||
                    selectedPanelEvalUi.strengths ||
                    selectedPanelEvalUi.risks
                  "
                >
                  <p class="text-xs font-semibold text-indigo-800 mb-1">评价</p>
                  <p
                    v-if="selectedPanelEvalUi.summary"
                    class="text-sm text-indigo-950 whitespace-pre-wrap leading-relaxed"
                  >
                    {{ selectedPanelEvalUi.summary }}
                  </p>
                  <ul
                    v-if="selectedPanelEvalUi.dimensionsLines.length"
                    class="mt-2 space-y-1 text-xs list-disc pl-4 text-indigo-900/95"
                  >
                    <li v-for="(line, i) in selectedPanelEvalUi.dimensionsLines" :key="i">{{ line }}</li>
                  </ul>
                  <p v-if="selectedPanelEvalUi.strengths" class="mt-2 text-xs text-indigo-900/95">
                    <span class="font-semibold">亮点：</span>{{ selectedPanelEvalUi.strengths }}
                  </p>
                  <p v-if="selectedPanelEvalUi.risks" class="mt-2 text-xs text-indigo-900/95">
                    <span class="font-semibold">待加强：</span>{{ selectedPanelEvalUi.risks }}
                  </p>
                </div>
                <div v-if="selectedPanelStandardAnswer" class="pt-2 border-t border-indigo-200/60">
                  <p class="text-xs font-semibold text-indigo-800 mb-1">参考标答</p>
                  <p class="text-sm text-indigo-950 whitespace-pre-wrap leading-relaxed">
                    {{ selectedPanelStandardAnswer }}
                  </p>
                </div>
                <div v-if="selectedPanelNextHint" class="pt-2 border-t border-indigo-200/60">
                  <p class="text-xs font-semibold text-indigo-800 mb-1">改进 / 下一题方向建议</p>
                  <p class="text-sm text-indigo-950 whitespace-pre-wrap leading-relaxed">{{ selectedPanelNextHint }}</p>
                </div>
                <p v-if="selectedPanelMeta.shouldEnd != null" class="text-xs text-indigo-800/90 pt-1">
                  建议结束本场面试：<strong>{{ selectedPanelMeta.shouldEnd ? "是" : "否" }}</strong>
                  <span v-if="selectedPanelMeta.endReason">（原因：{{ selectedPanelMeta.endReason }}）</span>
                </p>
              </div>
              <div
                v-else-if="selectedTurn && (selectedTurn.answerText || selectedTurn.answeredAt)"
                class="rounded-lg border border-gray-200 bg-gray-50 p-3 text-xs text-gray-600"
              >
                本题已有作答记录，但暂无结构化评价（可稍后点「刷新」或检查模型配置）。
              </div>

              <div
                v-if="!skipVoiceprint"
                class="rounded-lg border border-teal-200 bg-teal-50/80 p-3 text-xs text-teal-950 space-y-2"
              >
                <p class="font-semibold text-teal-900">全局声纹（只读）</p>
                <p class="text-teal-900">
                  门控：<strong>{{ voiceFilterOn ? "开" : "关" }}</strong> · 相似度
                  <strong>{{ (voiceSim * 100).toFixed(0) }}%</strong>
                </p>
              </div>
            </div>

            <!-- 底部操作条：与当前选中题联动 -->
            <div
              class="shrink-0 border-t border-gray-200 bg-white px-4 py-3 flex flex-wrap gap-2 items-center shadow-[0_-4px_12px_rgba(0,0,0,0.04)]"
            >
              <button
                type="button"
                class="px-4 py-2 rounded-lg text-sm font-medium min-w-[11rem] text-center transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                :class="mergedAnswerControl.buttonClass"
                :disabled="mergedAnswerControl.disabled"
                :title="mergedAnswerControl.title"
                @click="onMergedAnswerPrimary"
              >
                <span class="block leading-snug">{{ mergedAnswerControl.label }}</span>
                <span v-if="mergedAnswerControl.hint" class="block text-[10px] font-normal opacity-90 mt-0.5 leading-tight">{{
                  mergedAnswerControl.hint
                }}</span>
              </button>
              <button
                type="button"
                class="px-4 py-2 rounded-lg text-sm font-medium bg-blue-600 text-white disabled:opacity-40"
                :disabled="!canContinueNextPanel"
                :title="!isViewingActiveTurn ? '请先在左侧选择「当前」题目' : ''"
                @click="onContinueNext"
              >
                下一题
              </button>
            </div>
          </div>
        </div>

        <div class="p-3 border-t border-gray-100 bg-slate-50/60 font-mono text-xs text-gray-800 max-h-36 overflow-y-auto shrink-0">
          <p class="text-gray-500 mb-2 font-sans text-xs">运行日志</p>
          <div v-for="(line, i) in logLines" :key="i" class="mb-1">{{ line }}</div>
        </div>
      </div>

      <div v-else class="flex-1 min-h-0 overflow-y-auto p-4">
        <div class="flex justify-between items-center mb-3">
          <h3 class="text-sm font-semibold text-gray-800">mm_video_interview_event</h3>
          <button
            type="button"
            class="text-sm px-3 py-1 rounded-lg border border-gray-300 hover:bg-gray-50"
            @click="refreshEvents"
          >
            刷新
          </button>
        </div>
        <table class="w-full text-xs border-collapse">
          <thead>
            <tr class="bg-gray-100 text-left">
              <th class="p-2 border border-gray-200">seq</th>
              <th class="p-2 border border-gray-200">type</th>
              <th class="p-2 border border-gray-200">payload</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="e in events" :key="e.eventId">
              <td class="p-2 border border-gray-200 align-top">{{ e.seq }}</td>
              <td class="p-2 border border-gray-200 align-top">{{ e.type }}</td>
              <td class="p-2 border border-gray-200 align-top whitespace-pre-wrap break-all">{{ e.payloadJson }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
