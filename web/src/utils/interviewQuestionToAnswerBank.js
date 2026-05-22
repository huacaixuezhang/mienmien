/** 面试复盘题目 → 标准题库卡片（复制，不修改原题） */

import { parseInterviewPayload } from "./interviewV3.js";

const TEXT_MAX = 12000;
const TITLE_MAX = 80;

function pushBlock(parts, heading, body) {
  const t = String(body ?? "").trim();
  if (!t) return;
  parts.push(`${heading}\n${t}`);
}

/**
 * 将复盘题目裁剪为 standard answer bank 卡片的 text 正文。
 * @param {Record<string, unknown>} q
 * @returns {string}
 */
export function buildAnswerBankCardTextFromQuestion(q) {
  const parts = [];
  const stem = String(q?.questionRecord ?? "").trim() || String(q?.title ?? "").trim();
  pushBlock(parts, "【题干】", stem);
  pushBlock(parts, "【作答】", q?.answerRecord);
  pushBlock(parts, "【标准答案】", q?.standardAnswer);
  pushBlock(parts, "【优点】", q?.pros);
  pushBlock(parts, "【缺点】", q?.cons);
  pushBlock(parts, "【后续优化】", q?.improvementPlan);
  const joined = parts.join("\n\n").trim();
  if (joined.length <= TEXT_MAX) return joined;
  return `${joined.slice(0, TEXT_MAX)}…`;
}

/**
 * @param {Record<string, unknown>} q
 * @returns {{ key: string; title: string; text: string; sourceQuestionId: string }}
 */
export function buildAnswerBankCardFromQuestion(q) {
  const qid = String(q?.id ?? "").trim() || `q_${Date.now()}`;
  const label = String(q?.label ?? "").trim();
  const titleField = String(q?.title ?? "").trim();
  const title = (label || titleField || "未命名题目").slice(0, TITLE_MAX);
  return {
    key: `bank_${qid}`,
    title,
    text: buildAnswerBankCardTextFromQuestion(q),
    sourceQuestionId: qid
  };
}

/**
 * @param {unknown} cardsJson
 * @returns {Array<Record<string, unknown>>}
 */
export function parseAnswerBankCards(cardsJson) {
  if (!cardsJson || typeof cardsJson !== "string" || !cardsJson.trim()) {
    return [];
  }
  try {
    const parsed = JSON.parse(cardsJson);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/**
 * 是否已有来自该面试题的卡片。
 * @param {Array<Record<string, unknown>>} cards
 * @param {string} questionId
 * @param {string} cardKey
 */
export function findAnswerBankCardForQuestion(cards, questionId, cardKey) {
  const qid = String(questionId ?? "").trim();
  const key = String(cardKey ?? "").trim();
  for (const c of cards || []) {
    if (!c || typeof c !== "object") continue;
    if (qid && String(c.sourceQuestionId ?? "").trim() === qid) return c;
    if (key && String(c.key ?? "").trim() === key) return c;
  }
  return null;
}

/**
 * 从题库卡片列表中移除与面试题关联的卡片。
 * @param {Array<Record<string, unknown>>} cards
 * @param {string} questionId
 * @param {string} cardKey
 */
const SECTION_HEADING_TO_FIELD = {
  题干: "questionRecord",
  作答: "answerRecord",
  标准答案: "standardAnswer",
  优点: "pros",
  缺点: "cons",
  后续优化: "improvementPlan"
};

/**
 * 将题库卡片 text 解析为面试题目字段（与 buildAnswerBankCardTextFromQuestion 对应）。
 * @param {string} text
 */
export function parseAnswerBankCardTextToQuestionFields(text) {
  const out = {
    questionRecord: "",
    answerRecord: "",
    standardAnswer: "",
    pros: "",
    cons: "",
    improvementPlan: ""
  };
  const raw = String(text ?? "").trim();
  if (!raw) return out;

  const chunks = raw.split(/\n\n(?=【)/);
  for (const chunk of chunks) {
    const part = chunk.trim();
    if (!part) continue;
    const m = /^【([^】]+)】\s*\n?([\s\S]*)$/u.exec(part);
    if (!m) {
      if (!out.questionRecord) out.questionRecord = part;
      continue;
    }
    const field = SECTION_HEADING_TO_FIELD[m[1].trim()];
    const body = m[2].trim();
    if (field) out[field] = body;
    else if (!out.questionRecord) out.questionRecord = part;
  }
  return out;
}

/** @param {string} text */
export function previewAnswerBankCardText(text, maxLen = 160) {
  const fields = parseAnswerBankCardTextToQuestionFields(text);
  const stem = fields.questionRecord || String(text ?? "").trim();
  if (stem.length <= maxLen) return stem;
  return `${stem.slice(0, maxLen)}…`;
}

/**
 * 题库卡片内容写回面试题对象（不修改 id / 语音来源等）。
 * @param {Record<string, unknown>} q
 * @param {{ title?: string; text?: string }} card
 */
export function applyAnswerBankCardToQuestion(q, card) {
  if (!q || typeof q !== "object") return;
  const fields = parseAnswerBankCardTextToQuestionFields(card.text);
  Object.assign(q, fields);
  const t = String(card.title ?? "").trim();
  if (t) {
    if (String(q.label ?? "").trim()) {
      q.label = t;
    }
    q.title = t;
  }
}

/**
 * @param {Array<Record<string, unknown>>} interviewRows
 * @param {Record<string, unknown>} card
 */
export function findInterviewQuestionLinksForAnswerCard(interviewRows, card) {
  const sourceId = String(card?.sourceQuestionId ?? "").trim();
  const cardKey = String(card?.key ?? "").trim();
  if (!sourceId && !cardKey) return [];

  const hits = [];
  for (const row of interviewRows || []) {
    const summary = row?.summary;
    if (summary == null || summary === "") continue;
    const parsed = parseInterviewPayload(summary);
    if (parsed.kind !== "v3" || !Array.isArray(parsed.rounds)) continue;
    const recordId = String(row.recordId ?? "").trim();
    const category = String(row.type ?? row.interviewType ?? "").trim().toLowerCase();
    for (let ri = 0; ri < parsed.rounds.length; ri++) {
      const round = parsed.rounds[ri];
      const questions = round?.questions || [];
      for (const q of questions) {
        const qid = String(q?.id ?? "").trim();
        const abk = String(q?.answerBankCardKey ?? "").trim();
        const match =
          (sourceId && qid === sourceId) || (cardKey && abk === cardKey) || (cardKey && qid && `bank_${qid}` === cardKey);
        if (!match) continue;
        hits.push({
          recordId,
          category,
          roundIndex: ri,
          questionId: qid
        });
      }
    }
  }
  return hits;
}

export function removeAnswerBankCardsForQuestion(cards, questionId, cardKey) {
  const qid = String(questionId ?? "").trim();
  const key = String(cardKey ?? "").trim();
  return (cards || []).filter((c) => {
    if (!c || typeof c !== "object") return true;
    if (qid && String(c.sourceQuestionId ?? "").trim() === qid) return false;
    if (key && String(c.key ?? "").trim() === key) return false;
    return true;
  });
}
