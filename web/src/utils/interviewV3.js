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
    score: 85
  };
}

export function defaultRound(index = 0) {
  return {
    id: newRoundId(),
    roundTitle: `第${index + 1}轮面试`,
    timeText: "",
    locationMode: "线上",
    category: "技术面",
    interviewers: [{ role: "HR", name: "" }],
    resultUi: "待评估",
    resultComment: "",
    questions: []
  };
}

function normalizeQuestion(q, idx) {
  return {
    id: q.id || newQuestionId(),
    label: (q.label || "").trim() || `题目${idx + 1}`,
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
}

function normalizeRound(r, idx) {
  const iv = Array.isArray(r.interviewers) && r.interviewers.length ? r.interviewers : [{ role: "HR", name: "" }];
  return {
    id: r.id || newRoundId(),
    roundTitle: (r.roundTitle || "").trim() || `第${idx + 1}轮面试`,
    timeText: r.timeText || "",
    locationMode: r.locationMode === "线下" ? "线下" : "线上",
    category: r.category || "技术面",
    interviewers: iv.map((x) => ({ role: x.role || "HR", name: x.name || "" })),
    resultUi: normalizeResultUi(r.resultUi, r.result),
    resultComment: r.resultComment || "",
    questions: Array.isArray(r.questions) ? r.questions.map((q, qi) => normalizeQuestion(q, qi)) : []
  };
}

function normalizeResultUi(resultUi, apiResult) {
  if (resultUi === "通过" || resultUi === "拒绝" || resultUi === "待评估") return resultUi;
  if (apiResult === "passed") return "通过";
  if (apiResult === "failed") return "拒绝";
  return "待评估";
}

export function parseInterviewPayload(rawSummary) {
  if (!rawSummary || typeof rawSummary !== "string") {
    return { kind: "empty" };
  }
  if (rawSummary.startsWith(PREFIX_V3)) {
    try {
      const obj = JSON.parse(rawSummary.slice(PREFIX_V3.length));
      const jobProfile = {
        title: obj.jobProfile?.title || "",
        company: obj.jobProfile?.company || "",
        location: obj.jobProfile?.location || "",
        jdText: obj.jobProfile?.jdText || ""
      };
      const rounds = Array.isArray(obj.rounds) ? obj.rounds.map((r, i) => normalizeRound(r, i)) : [];
      const meta = obj.meta && typeof obj.meta === "object" ? obj.meta : {};
      return { kind: "v3", jobProfile, rounds, meta };
    } catch {
      return { kind: "empty" };
    }
  }
  if (rawSummary.startsWith(PREFIX_V2)) {
    try {
      const parsed = JSON.parse(rawSummary.slice(PREFIX_V2.length));
      return { kind: "v2", v2: parsed };
    } catch {
      return { kind: "plain", text: rawSummary };
    }
  }
  return { kind: "plain", text: rawSummary };
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
  return {
    jobProfile: { title: "", company: "", location: "", jdText: "" },
    rounds: [round],
    meta: { finalResult: v2?.finalResult || "" }
  };
}

export function serializeV3(jobProfile, rounds, meta = {}) {
  const payload = {
    jobProfile: {
      title: (jobProfile?.title || "").trim(),
      company: (jobProfile?.company || "").trim(),
      location: (jobProfile?.location || "").trim(),
      jdText: jobProfile?.jdText || ""
    },
    rounds: (rounds || []).map((r, ri) => ({
      id: r.id || newRoundId(),
      roundTitle: (r.roundTitle || "").trim() || `第${ri + 1}轮面试`,
      timeText: r.timeText || "",
      locationMode: r.locationMode === "线下" ? "线下" : "线上",
      category: r.category || "技术面",
      interviewers: (r.interviewers || [])
        .map((x) => ({ role: x.role || "HR", name: (x.name || "").trim() }))
        .filter((x) => x.name),
      resultUi: r.resultUi || "待评估",
      resultComment: r.resultComment || "",
      questions: (r.questions || []).map((q, qi) => ({
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
      }))
    })),
    meta: meta && typeof meta === "object" ? meta : {}
  };
  return `${PREFIX_V3}${JSON.stringify(payload)}`;
}

export function resultUiToApi(ui) {
  if (ui === "通过") return "passed";
  if (ui === "拒绝") return "failed";
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
