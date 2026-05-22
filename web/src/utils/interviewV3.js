/** 正式/模拟面试大 JSON：岗位 + 多轮 + 题目（难度/分数） */

export const PREFIX_V2 = "MM_INTERVIEW_V2::";
export const PREFIX_V3 = "MM_INTERVIEW_V3::";

export function newRoundId() {
  return `r_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`;
}

export function newQuestionId() {
  return `q_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`;
}

function clampDifficulty(n) {
  const x = Number(n);
  if (!Number.isFinite(x)) return 2;
  return Math.min(3, Math.max(1, Math.round(x)));
}

/**
 * 从语音轮 Agent 的 evaluation JSON（schema_version=2、dimensions[].score）提取 0–100 的综合分；
 * 若存在 overall_score 则优先使用。与 Consumer {@code extractVoiceTurnScoreFromEvaluationJson} 对齐。
 * @param {unknown} evStr
 * @returns {number}
 */
export function extractVoiceTurnScoreFromEvaluationJson(evStr) {
  if (typeof evStr !== "string" || !evStr.trim()) {
    return 0;
  }
  try {
    const j = JSON.parse(evStr);
    const overall = Number(j?.overall_score);
    if (Number.isFinite(overall) && overall >= 0 && overall <= 100) {
      return Math.round(overall);
    }
    const dims = j?.dimensions;
    if (!Array.isArray(dims) || dims.length === 0) {
      return 0;
    }
    let sum = 0;
    let c = 0;
    for (const d of dims) {
      const sc = Number(d?.score);
      if (Number.isFinite(sc) && sc >= 0 && sc <= 100) {
        sum += sc;
        c++;
      }
    }
    if (c === 0) {
      return 0;
    }
    return Math.min(100, Math.max(0, Math.round(sum / c)));
  } catch {
    return 0;
  }
}

export function defaultQuestion(index = 0) {
  return {
    id: newQuestionId(),
    label: `题目${index + 1}`,
    title: "",
    questionRecord: "",
    answerRecord: "",
    pros: "",
    cons: "",
    improvementPlan: "",
    standardAnswer: "",
    difficulty: 2,
    score: 85,
    /** 来源：空为手工复盘；video_turn 为语音模拟面试落库逐轮 */
    source: "",
    videoTurnId: "",
    videoSessionId: "",
    /** 同轮多次语音时第几场（1-based）；非语音题为 0 */
    videoSessionOrdinal: 0,
    /** 综合评价给出的本题得分权重（0–1），仅语音题；写入 summary 供回显 */
    scoreWeight: 0,
    /** 已收藏至标准题库时对应 answerCards[].key */
    answerBankCardKey: ""
  };
}

export function defaultRound(index = 0) {
  return {
    id: newRoundId(),
    roundTitle: `第${index + 1}轮面试`,
    timeText: "",
    locationMode: "线上",
    category: "技术面",
    /** 内置：builtin_general | builtin_strict | builtin_friendly | builtin_stress；自定义：后端返回的 styleId */
    interviewerStyleKey: "builtin_general",
    interviewers: [{ role: "HR", name: "" }],
    resultUi: "待评估",
    resultComment: "",
    /** 本轮面试结论（写入 summary.rounds[i]，与 resultUi 一致） */
    interviewConclusion: defaultInterviewConclusion(),
    questions: []
  };
}

/**
 * 语音会话按 roundIndex 落库时，若 summary 中 rounds 尚未有那么多项，则补齐占位轮，
 * 避免 Consumer 跳过 merge、详情页也拉不到「下一轮」复盘。
 */
export function ensureRoundsCoverVideoRoundIndex(rounds, roundIndex) {
  const ri = Math.floor(Number(roundIndex));
  if (!Array.isArray(rounds) || !Number.isFinite(ri) || ri < 0) {
    return;
  }
  while (rounds.length <= ri) {
    const dr = defaultRound(rounds.length);
    rounds.push({
      ...dr,
      interviewers: (dr.interviewers || []).map((x) => ({ ...x })),
      interviewConclusion: { ...(dr.interviewConclusion || defaultInterviewConclusion()) },
      questions: []
    });
  }
}

export function normalizeQuestion(q, idx) {
  const source = q.source === "video_turn" ? "video_turn" : "";
  let label = (q.label || "").trim() || `题目${idx + 1}`;
  if (source === "video_turn") {
    const m = /^语音\s*Q\s*(\d+)\s*$/i.exec(label);
    if (m) {
      const n = Math.floor(Number(m[1]));
      label = `语音第${Number.isFinite(n) && n > 0 ? n : 1}题`;
    }
  }
  return {
    id: q.id || newQuestionId(),
    label,
    title: q.title || "",
    questionRecord: q.questionRecord || "",
    answerRecord: q.answerRecord || "",
    pros: q.pros || "",
    cons: q.cons || "",
    improvementPlan: q.improvementPlan || "",
    standardAnswer: q.standardAnswer || "",
    difficulty: clampDifficulty(q.difficulty),
    score: Number(q.score) || 0,
    source,
    videoTurnId: q.videoTurnId || "",
    videoSessionId: q.videoSessionId || "",
    videoSessionOrdinal: Number(q.videoSessionOrdinal) > 0 ? Math.floor(Number(q.videoSessionOrdinal)) : 0,
    scoreWeight:
      source === "video_turn"
        ? Math.min(1, Math.max(0, Number(q.scoreWeight) || 0))
        : 0,
    answerBankCardKey: String(q.answerBankCardKey ?? "").trim()
  };
}

/** 单场语音：统一为「语音第 n 题」（无 Q、无半角 Q 前缀）。 */
export function formatVoiceTurnQuestionLabelSimple(displayNum) {
  const qn = Number(displayNum);
  const qi = Number.isFinite(qn) && qn > 0 ? Math.floor(qn) : 1;
  return `语音第${qi}题`;
}

/** 与 Consumer / App 合并逻辑一致：第 N 场 + 本场题号（全角竖线）。 */
export function formatVoiceTurnQuestionLabel(displayNum, sessionOrdinal) {
  const bout = Number(sessionOrdinal);
  const n = Number.isFinite(bout) && bout > 0 ? Math.floor(bout) : 1;
  const qn = Number(displayNum);
  const qi = Number.isFinite(qn) && qn > 0 ? Math.floor(qn) : 1;
  return `第${n}场｜语音第${qi}题`;
}

/**
 * 规范化语音题展示：多场为「第 n 场｜语音第 m 题」；单场（含仅 1 个会话 id、或旧数据无 id）为「语音第 k 题」，
 * 按本题在当轮题目列表中的顺序编号，覆盖旧版「语音 Qn」「语音Qn」「第 1 场｜…」等不一致写法。
 * @param {Array<Record<string, unknown>>} questions 已 normalize 的题目列表（就地更新）
 */
export function enrichVoiceTurnLabelsWhenMultiSession(questions) {
  if (!Array.isArray(questions) || questions.length === 0) {
    return questions;
  }
  const orderSids = [];
  const seenSid = new Set();
  for (const q of questions) {
    if (q.source !== "video_turn") continue;
    const sid = String(q.videoSessionId || "").trim();
    if (!sid) continue;
    if (seenSid.has(sid)) continue;
    seenSid.add(sid);
    orderSids.push(sid);
  }
  if (orderSids.length <= 1) {
    let seq = 0;
    for (const q of questions) {
      if (q.source !== "video_turn") continue;
      seq += 1;
      q.videoSessionOrdinal = 1;
      q.label = formatVoiceTurnQuestionLabelSimple(seq);
    }
    return questions;
  }
  const sidToOrdinal = new Map(orderSids.map((sid, i) => [sid, i + 1]));
  const perSidCount = new Map();
  for (const q of questions) {
    if (q.source !== "video_turn") continue;
    const sid = String(q.videoSessionId || "").trim();
    if (!sid) continue;
    const ord = sidToOrdinal.get(sid);
    if (ord == null) continue;
    const prev = perSidCount.get(sid) || 0;
    const displayNum = prev + 1;
    perSidCount.set(sid, displayNum);
    q.videoSessionOrdinal = ord;
    q.label = formatVoiceTurnQuestionLabel(displayNum, ord);
  }
  return questions;
}

/**
 * 解析并归一化终局 Agent 输出的题目权重（与 videoTurnId 对齐，和为 1）。
 * @param {unknown} raw
 * @returns {{ videoTurnId: string; weight: number }[]}
 */
function normalizeQuestionWeightEntries(raw) {
  if (!Array.isArray(raw) || raw.length === 0) {
    return [];
  }
  const tmp = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") continue;
    const tid = String(item.videoTurnId ?? item.turnId ?? "").trim();
    const w = Number(item.weight);
    if (!tid || !Number.isFinite(w) || w < 0) continue;
    tmp.push({ videoTurnId: tid, weight: w });
  }
  if (!tmp.length) return [];
  let sum = tmp.reduce((a, x) => a + x.weight, 0);
  if (sum > 1e-9 && Math.abs(sum - 1) > 0.02) {
    for (const x of tmp) {
      x.weight = x.weight / sum;
    }
  }
  return tmp;
}

/**
 * 为参与综合分计算的语音题解析权重：优先用 interviewConclusion.questionWeights；
 * 缺权重的题目平分剩余权重；最终保证参与集权重和为 1。
 * @param {{ questionWeights?: unknown }} ic
 * @param {unknown[]} includedQs
 * @returns {Map<string, number>}
 */
function buildResolvedVoiceQuestionWeightMap(ic, includedQs) {
  const entries = normalizeQuestionWeightEntries(ic?.questionWeights);
  const fromAgent = new Map(entries.map((e) => [e.videoTurnId, e.weight]));
  const tids = [];
  for (const q of includedQs) {
    const tid = String(q?.videoTurnId || "").trim();
    if (tid) tids.push(tid);
  }
  if (!tids.length) return new Map();
  let sumKnown = 0;
  let missing = 0;
  for (const tid of tids) {
    const w = fromAgent.get(tid);
    if (Number.isFinite(w) && w >= 0) {
      sumKnown += w;
    } else {
      missing++;
    }
  }
  const fill = missing > 0 ? Math.max(0, 1 - sumKnown) / missing : 0;
  const out = new Map();
  let total = 0;
  for (const tid of tids) {
    const w0 = fromAgent.get(tid);
    const w = Number.isFinite(w0) && w0 >= 0 ? w0 : fill;
    out.set(tid, w);
    total += w;
  }
  if (total <= 1e-9) {
    const u = 1 / tids.length;
    for (const tid of tids) out.set(tid, u);
    return out;
  }
  if (Math.abs(total - 1) > 0.02) {
    for (const tid of tids) {
      out.set(tid, out.get(tid) / total);
    }
  }
  return out;
}

/**
 * 面试综合分：0.65 × Σ(题目得分 × 题目权重) + 0.35 × 原综合分（综合评价 Agent 给出的 overallScore）。
 * 题目权重来自 interviewConclusion.questionWeights（和为 1）；缺省则对参与集均分权重。
 * 多场次时仅「最新一场」内的语音题参与 Σ。
 * @param {{ questions?: unknown[]; interviewConclusion?: Record<string, unknown> }} round
 */
export function syncInterviewConclusionOverallScoreFromQuestions(round) {
  if (!round || typeof round !== "object") {
    return;
  }
  const qs = round.questions;
  if (!Array.isArray(qs) || qs.length === 0) {
    return;
  }
  const sessionOrderSids = [];
  const seenSid = new Set();
  for (const q of qs) {
    if (q.source !== "video_turn") continue;
    const sid = String(q.videoSessionId || "").trim();
    if (!sid || seenSid.has(sid)) continue;
    seenSid.add(sid);
    sessionOrderSids.push(sid);
  }
  let maxOrdinal = 0;
  for (const q of qs) {
    if (q.source !== "video_turn") continue;
    const o = Number(q.videoSessionOrdinal);
    if (Number.isFinite(o) && o > maxOrdinal) maxOrdinal = o;
  }
  const multiSession = sessionOrderSids.length > 1;
  const latestSid =
    sessionOrderSids.length > 0 ? sessionOrderSids[sessionOrderSids.length - 1] : "";

  function includeVoiceScoreForOverall(q) {
    if (!multiSession) {
      return true;
    }
    if (maxOrdinal > 0) {
      const qo = Number(q.videoSessionOrdinal);
      const qsid = String(q.videoSessionId || "").trim();
      return (
        (Number.isFinite(qo) && qo === maxOrdinal) ||
        (!(Number.isFinite(qo) && qo > 0) && qsid !== "" && qsid === latestSid)
      );
    }
    return String(q.videoSessionId || "").trim() === latestSid;
  }

  let allVoice = true;
  const includedQs = [];
  for (const q of qs) {
    if (q.source !== "video_turn") {
      allVoice = false;
      continue;
    }
    if (!includeVoiceScoreForOverall(q)) {
      q.scoreWeight = 0;
      continue;
    }
    const n = Number(q.score);
    if (Number.isFinite(n) && n >= 0 && n <= 100) {
      includedQs.push(q);
    } else {
      q.scoreWeight = 0;
    }
  }
  if (!includedQs.length) {
    return;
  }
  if (!round.interviewConclusion || typeof round.interviewConclusion !== "object") {
    round.interviewConclusion = defaultInterviewConclusion();
  }
  const ic = round.interviewConclusion;
  const llmOverall = Number(ic.overallScore);
  const curClamped = Number.isFinite(llmOverall) ? Math.min(100, Math.max(0, llmOverall)) : 0;
  ic.questionWeights = normalizeQuestionWeightEntries(ic.questionWeights);
  const wMap = buildResolvedVoiceQuestionWeightMap(ic, includedQs);
  let weighted = 0;
  for (const q of includedQs) {
    const tid = String(q.videoTurnId || "").trim();
    const w = wMap.get(tid) ?? 0;
    q.scoreWeight = Math.min(1, Math.max(0, w));
    const sc = Number(q.score);
    weighted += sc * w;
  }
  for (const q of qs) {
    if (q.source === "video_turn" && !includedQs.includes(q)) {
      q.scoreWeight = 0;
    }
  }
  const blended = 0.65 * weighted + 0.35 * curClamped;
  ic.overallScore = Math.min(100, Math.max(0, Math.round(blended)));
}

const BUILTIN_STYLE_KEYS = new Set(["builtin_general", "builtin_strict", "builtin_friendly", "builtin_stress"]);

function normalizeInterviewerStyleKey(raw) {
  const k = raw == null ? "" : String(raw).trim();
  if (!k) return "builtin_general";
  if (BUILTIN_STYLE_KEYS.has(k)) return k;
  if (/^st[0-9a-f]{8}$/i.test(k)) return k;
  return "builtin_general";
}

function normalizeRound(r, idx) {
  const iv = Array.isArray(r.interviewers) && r.interviewers.length ? r.interviewers : [{ role: "HR", name: "" }];
  const ic = normalizeInterviewConclusion(r.interviewConclusion);
  const hasStoredConclusion = r.interviewConclusion != null && typeof r.interviewConclusion === "object";
  const resultUi = hasStoredConclusion
    ? assessmentToResultUi(ic.resultAssessment)
    : normalizeResultUi(r.resultUi, r.result);
  return {
    id: r.id || newRoundId(),
    roundTitle: (r.roundTitle || "").trim() || `第${idx + 1}轮面试`,
    timeText: r.timeText || "",
    locationMode: r.locationMode === "线下" ? "线下" : "线上",
    category: r.category || "技术面",
    interviewerStyleKey: normalizeInterviewerStyleKey(r.interviewerStyleKey),
    interviewers: iv.map((x) => ({ role: x.role || "HR", name: x.name || "" })),
    resultUi,
    resultComment: r.resultComment || "",
    interviewConclusion: ic,
    questions: (() => {
      const qs = Array.isArray(r.questions) ? r.questions.map((q, qi) => normalizeQuestion(q, qi)) : [];
      enrichVoiceTurnLabelsWhenMultiSession(qs);
      const roundRef = { questions: qs, interviewConclusion: ic };
      syncInterviewConclusionOverallScoreFromQuestions(roundRef);
      return qs;
    })()
  };
}

function normalizeResultUi(resultUi, apiResult) {
  if (resultUi === "拒绝") return "未通过";
  if (resultUi === "通过" || resultUi === "未通过" || resultUi === "待评估") return resultUi;
  if (apiResult === "passed") return "通过";
  if (apiResult === "failed") return "未通过";
  return "待评估";
}

/** 单轮面试结论字段模板（持久化在 {@code summary.rounds[i].interviewConclusion}） */
export function defaultInterviewConclusion() {
  return {
    resultAssessment: "待评估",
    overallScore: 0,
    comment: "",
    candidatePortrait: "",
    nextRoundAdvice: "",
    /** 无 / 有下一轮 / 待定 */
    nextRoundStatus: "no",
    /** 与 nextRoundStatus===yes 一致，便于旧数据与 Consumer 兼容 */
    hasNextRound: false,
    /** 终局综合评价 Agent：各语音题得分权重，与 videoTurnId 对应，和为 1 */
    questionWeights: []
  };
}

/** @param {unknown} raw */
function normalizeNextRoundStatus(raw) {
  if (!raw || typeof raw !== "object") return "no";
  const s = String(raw.nextRoundStatus ?? "").trim().toLowerCase();
  if (s === "yes" || s === "pending" || s === "no") {
    return s;
  }
  const hn = raw.hasNextRound;
  if (hn === true || hn === 1) return "yes";
  if (hn === false || hn === 0) return "no";
  if (typeof hn === "string") {
    const t = hn.trim().toLowerCase();
    if (t === "true" || t === "1" || t === "yes") return "yes";
    if (t === "pending" || t === "待定") return "pending";
    if (t === "false" || t === "0" || t === "no" || t === "") return "no";
  }
  return "no";
}

export function normalizeInterviewConclusion(raw) {
  const d = defaultInterviewConclusion();
  if (!raw || typeof raw !== "object") return d;
  const ra = String(raw.resultAssessment ?? "").trim();
  if (ra === "通过" || ra === "未通过" || ra === "待评估") d.resultAssessment = ra;
  else if (ra === "拒绝") d.resultAssessment = "未通过";
  const sc = Number(raw.overallScore);
  if (Number.isFinite(sc)) d.overallScore = Math.min(100, Math.max(0, Math.round(sc)));
  d.comment = String(raw.comment ?? "").trim();
  d.candidatePortrait = String(raw.candidatePortrait ?? "").trim();
  d.nextRoundStatus = normalizeNextRoundStatus(raw);
  d.nextRoundAdvice = String(raw.nextRoundAdvice ?? "").trim();
  if (d.nextRoundStatus === "no") {
    d.nextRoundAdvice = "";
  }
  d.hasNextRound = d.nextRoundStatus === "yes";
  d.questionWeights = normalizeQuestionWeightEntries(raw.questionWeights);
  return d;
}

/** 与 round.resultUi 选项一致 */
export function assessmentToResultUi(assessment) {
  const s = String(assessment ?? "").trim();
  if (s === "通过") return "通过";
  if (s === "未通过" || s === "拒绝") return "未通过";
  return "待评估";
}

export function applyInterviewConclusionToLastRound(rounds, conclusion) {
  if (!rounds?.length || !conclusion) return;
  const last = rounds[rounds.length - 1];
  last.interviewConclusion = normalizeInterviewConclusion({
    ...last.interviewConclusion,
    ...conclusion
  });
  last.resultUi = assessmentToResultUi(last.interviewConclusion.resultAssessment);
}

/**
 * 载入后：把旧版 {@code meta.interviewConclusion} 合并到末轮，删除 meta 键；规范化每轮结论并同步各轮 {@code resultUi}。
 * @param {{ value: Record<string, unknown> }} metaRef Vue ref 或兼容对象
 */
export function finalizeInterviewRoundsAfterLoad(metaRef, rounds) {
  if (!Array.isArray(rounds)) return;
  const legacy =
    metaRef?.value?.interviewConclusion && typeof metaRef.value.interviewConclusion === "object"
      ? normalizeInterviewConclusion(metaRef.value.interviewConclusion)
      : null;
  if (legacy && rounds.length > 0) {
    const last = rounds[rounds.length - 1];
    last.interviewConclusion = normalizeInterviewConclusion({
      ...defaultInterviewConclusion(),
      ...last.interviewConclusion,
      ...legacy
    });
  }
  if (metaRef?.value && Object.prototype.hasOwnProperty.call(metaRef.value, "interviewConclusion")) {
    delete metaRef.value.interviewConclusion;
  }
  for (const r of rounds) {
    r.interviewConclusion = normalizeInterviewConclusion(r.interviewConclusion);
    r.resultUi = assessmentToResultUi(r.interviewConclusion.resultAssessment);
  }
}

/**
 * 保存前规范化各轮结论：固化 nextRoundStatus、仅「否」时清空下轮建议、同步 hasNextRound 与 resultUi。
 */
export function prepareInterviewRoundsForPersist(rounds) {
  if (!Array.isArray(rounds)) return;
  for (const r of rounds) {
    r.interviewConclusion = normalizeInterviewConclusion(r.interviewConclusion);
    if (r.interviewConclusion.nextRoundStatus === "no") {
      r.interviewConclusion.nextRoundAdvice = "";
    }
    r.interviewConclusion.hasNextRound = r.interviewConclusion.nextRoundStatus === "yes";
    r.resultUi = assessmentToResultUi(r.interviewConclusion.resultAssessment);
    r.questions = (r.questions || []).map((q, qi) => normalizeQuestion(q, qi));
    enrichVoiceTurnLabelsWhenMultiSession(r.questions);
    syncInterviewConclusionOverallScoreFromQuestions(r);
  }
}

export function parseInterviewPayload(rawSummary) {
  if (rawSummary == null) {
    return { kind: "empty" };
  }
  const raw =
    typeof rawSummary === "string"
      ? rawSummary.replace(/^\uFEFF/, "").trim()
      : String(rawSummary).replace(/^\uFEFF/, "").trim();
  if (!raw) {
    return { kind: "empty" };
  }
  if (raw.startsWith(PREFIX_V3)) {
    try {
      const obj = JSON.parse(raw.slice(PREFIX_V3.length));
      const jobProfile = {
        title: obj.jobProfile?.title || "",
        company: obj.jobProfile?.company || "",
        location: obj.jobProfile?.location || "",
        jdText: obj.jobProfile?.jdText || ""
      };
      const rounds = Array.isArray(obj.rounds) ? obj.rounds.map((r, i) => normalizeRound(r, i)) : [];
      let meta = obj.meta && typeof obj.meta === "object" ? { ...obj.meta } : {};
      if (
        !meta.videoInterviewMeta &&
        obj.videoInterviewMeta &&
        typeof obj.videoInterviewMeta === "object"
      ) {
        meta = { ...meta, videoInterviewMeta: obj.videoInterviewMeta };
      }
      return { kind: "v3", jobProfile, rounds, meta };
    } catch {
      return { kind: "empty" };
    }
  }
  if (raw.startsWith(PREFIX_V2)) {
    try {
      const parsed = JSON.parse(raw.slice(PREFIX_V2.length));
      return { kind: "v2", v2: parsed };
    } catch {
      return { kind: "plain", text: raw };
    }
  }
  try {
    const obj = JSON.parse(raw);
    if (obj && typeof obj === "object") {
      const leg = obj.legacySummary;
      if (typeof leg === "string" && leg.trim().startsWith(PREFIX_V3)) {
        try {
          const inner = JSON.parse(leg.trim().slice(PREFIX_V3.length));
          const jobProfile = {
            title: inner.jobProfile?.title || "",
            company: inner.jobProfile?.company || "",
            location: inner.jobProfile?.location || "",
            jdText: inner.jobProfile?.jdText || ""
          };
          const rounds = Array.isArray(inner.rounds) ? inner.rounds.map((r, i) => normalizeRound(r, i)) : [];
          const baseMeta = inner.meta && typeof inner.meta === "object" ? { ...inner.meta } : {};
          if (obj.videoInterviewMeta && typeof obj.videoInterviewMeta === "object") {
            baseMeta.videoInterviewMeta = obj.videoInterviewMeta;
          } else if (
            !baseMeta.videoInterviewMeta &&
            inner.videoInterviewMeta &&
            typeof inner.videoInterviewMeta === "object"
          ) {
            baseMeta.videoInterviewMeta = inner.videoInterviewMeta;
          }
          return { kind: "v3", jobProfile, rounds, meta: baseMeta };
        } catch {
          /* fall through */
        }
      }
      if (Array.isArray(obj.rounds) || (obj.jobProfile && typeof obj.jobProfile === "object")) {
        const jobProfile = {
          title: obj.jobProfile?.title || "",
          company: obj.jobProfile?.company || "",
          location: obj.jobProfile?.location || "",
          jdText: obj.jobProfile?.jdText || ""
        };
        const rounds = Array.isArray(obj.rounds) ? obj.rounds.map((r, i) => normalizeRound(r, i)) : [];
        let meta = obj.meta && typeof obj.meta === "object" ? { ...obj.meta } : {};
        if (
          !meta.videoInterviewMeta &&
          obj.videoInterviewMeta &&
          typeof obj.videoInterviewMeta === "object"
        ) {
          meta = { ...meta, videoInterviewMeta: obj.videoInterviewMeta };
        }
        return { kind: "v3", jobProfile, rounds, meta };
      }
    }
  } catch {
    /* not JSON */
  }
  return { kind: "plain", text: raw };
}

export function migrateV2ToV3(v2) {
  const cards = Array.isArray(v2?.reviewCards) ? v2.reviewCards : [];
  const questions = cards.map((c, idx) => ({
    ...defaultQuestion(idx),
    id: c.id || newQuestionId(),
    label: (c.title || "").trim() || `题目${idx + 1}`,
    title: (c.title || "").trim(),
    questionRecord: c.questionRecord || "",
    answerRecord: c.answerRecord || "",
    pros: c.pros || "",
    cons: c.cons || "",
    improvementPlan: c.improvementPlan || "",
    standardAnswer: c.standardAnswer || "",
    difficulty: 2,
    score: 0
  }));
  const round = defaultRound(0);
  round.roundTitle = "第一轮面试";
  round.questions = questions;
  const summaryText = (v2?.summaryText || "").trim();
  if (summaryText) {
    round.resultComment = summaryText.slice(0, 2000);
  }
  const meta = { finalResult: v2?.finalResult || "" };
  if (v2?.videoInterviewMeta && typeof v2.videoInterviewMeta === "object") {
    meta.videoInterviewMeta = v2.videoInterviewMeta;
  }
  return {
    jobProfile: { title: "", company: "", location: "", jdText: "" },
    rounds: [round],
    meta
  };
}

export function serializeV3(jobProfile, rounds, meta = {}) {
  const metaOut = meta && typeof meta === "object" ? { ...meta } : {};
  delete metaOut.interviewConclusion;
  const payload = {
    jobProfile: {
      title: (jobProfile?.title || "").trim(),
      company: (jobProfile?.company || "").trim(),
      location: (jobProfile?.location || "").trim(),
      jdText: jobProfile?.jdText || ""
    },
    rounds: (rounds || []).map((r, ri) => {
      const ru = r.resultUi || "待评估";
      const resultCode =
        ru === "通过" ? "passed" : ru === "拒绝" || ru === "未通过" ? "failed" : "pending";
      const ic = normalizeInterviewConclusion(r.interviewConclusion);
      return {
      id: r.id || newRoundId(),
      roundIndex: ri,
      roundTitle: (r.roundTitle || "").trim() || `第${ri + 1}轮面试`,
      timeText: r.timeText || "",
      locationMode: r.locationMode === "线下" ? "线下" : "线上",
      category: r.category || "技术面",
      interviewerStyleKey: normalizeInterviewerStyleKey(r.interviewerStyleKey),
      interviewers: (r.interviewers || [])
        .map((x) => ({
          role: String(x.role || "").trim(),
          name: String(x.name || "").trim()
        }))
        .filter((x) => x.role || x.name)
        .map((x) => ({ role: x.role || "HR", name: x.name })),
      resultUi: ru,
      resultCode,
      resultComment: r.resultComment || "",
      interviewConclusion: {
        resultAssessment: ic.resultAssessment,
        overallScore: ic.overallScore,
        comment: ic.comment,
        candidatePortrait: ic.candidatePortrait,
        nextRoundAdvice: ic.nextRoundAdvice,
        nextRoundStatus: ic.nextRoundStatus,
        hasNextRound: ic.hasNextRound,
        ...(Array.isArray(ic.questionWeights) && ic.questionWeights.length > 0
          ? { questionWeights: ic.questionWeights }
          : {})
      },
      questions: (r.questions || []).map((q, qi) => {
        const base = {
          id: q.id || newQuestionId(),
          label: (q.label || "").trim() || `题目${qi + 1}`,
          title: q.title || "",
          questionRecord: q.questionRecord || "",
          answerRecord: q.answerRecord || "",
          pros: q.pros || "",
          cons: q.cons || "",
          improvementPlan: q.improvementPlan || "",
          standardAnswer: q.standardAnswer || "",
          difficulty: clampDifficulty(q.difficulty),
          score: Number(q.score) || 0
        };
        const abk = String(q.answerBankCardKey ?? "").trim();
        const abkOut = abk ? { answerBankCardKey: abk } : {};
        if (q.source === "video_turn" || q.videoTurnId || q.videoSessionId) {
          const ord = Number(q.videoSessionOrdinal);
          const vo = Number.isFinite(ord) && ord > 0 ? Math.floor(ord) : 0;
          const sw = Number(q.scoreWeight);
          const swOut = Number.isFinite(sw) && sw > 0 ? Math.round(sw * 10000) / 10000 : 0;
          return {
            ...base,
            source: q.source === "video_turn" ? "video_turn" : q.source || "",
            videoTurnId: q.videoTurnId || "",
            videoSessionId: q.videoSessionId || "",
            ...(vo > 0 ? { videoSessionOrdinal: vo } : {}),
            ...(swOut > 0 ? { scoreWeight: swOut } : {}),
            ...abkOut
          };
        }
        return abk ? { ...base, ...abkOut } : base;
      })
      };
    }),
    meta: metaOut
  };
  return `${PREFIX_V3}${JSON.stringify(payload)}`;
}

export function resultUiToApi(ui) {
  if (ui === "通过") return "passed";
  if (ui === "拒绝" || ui === "未通过") return "failed";
  return "pending";
}

export function aggregateRoundResults(rounds) {
  if (!rounds || !rounds.length) return "pending";
  const last = rounds[rounds.length - 1];
  return resultUiToApi(last.resultUi || "待评估");
}

export function averageQuestionScore(rounds) {
  const scores = [];
  (rounds || []).forEach((r) => {
    (r.questions || []).forEach((q) => {
      const n = Number(q.score);
      if (Number.isFinite(n)) scores.push(n);
    });
  });
  if (!scores.length) return 0;
  return Math.round(scores.reduce((a, b) => a + b, 0) / scores.length);
}

export function firstRoundInterviewType(rounds) {
  const c = rounds?.[0]?.category;
  if (!c) return "business";
  if (c.includes("HR")) return "HR";
  if (c.includes("技术")) return "技术";
  if (c.includes("业务")) return "业务";
  return c;
}
