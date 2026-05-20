import { BUILTIN_INTERVIEWER_STYLES } from "../constants/interviewerBuiltinPrompts.js";

/** 根据 styleKey 解析完整 Prompt：内置 key 或用户自定义 styleId（如 stxxxxxxxx） */
export function resolveInterviewerStylePrompt(styleKey, customStyles = []) {
  const k = styleKey == null ? "" : String(styleKey).trim();
  if (!k) {
    return BUILTIN_INTERVIEWER_STYLES[0]?.prompt || "";
  }
  const builtin = BUILTIN_INTERVIEWER_STYLES.find((x) => x.key === k);
  if (builtin) return builtin.prompt;
  const row = (customStyles || []).find((x) => String(x.styleId) === k);
  return row?.promptBody || BUILTIN_INTERVIEWER_STYLES[0]?.prompt || "";
}

export function interviewerStyleLabel(styleKey, customStyles = []) {
  const k = styleKey == null ? "" : String(styleKey).trim();
  if (!k) return BUILTIN_INTERVIEWER_STYLES[0]?.label || "";
  const builtin = BUILTIN_INTERVIEWER_STYLES.find((x) => x.key === k);
  if (builtin) return builtin.label;
  const row = (customStyles || []).find((x) => String(x.styleId) === k);
  return row?.title ? `${row.title}（自定义）` : k;
}
