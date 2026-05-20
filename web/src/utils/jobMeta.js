/**
 * 岗位扩展信息序列化到后端 `base_range`（需列长度足够，见仓库 scripts/migrate-mm-job-position-base-range-expand.sql）。
 * 兼容历史：非 JSON 整段视为「期望薪资/备注」纯文本。
 */

export const JOB_TYPE_OPTIONS = [
  { value: "fulltime", label: "全职" },
  { value: "campus", label: "校招" },
  { value: "intern", label: "实习" }
];

export function decodeJobBaseRange(baseRange) {
  const raw = baseRange == null ? "" : String(baseRange).trim();
  if (!raw) {
    return { jobType: "fulltime", description: "", jdDetail: "", salary: "", focusPoints: "" };
  }
  try {
    const o = JSON.parse(raw);
    if (o && typeof o === "object" && o.v === 1) {
      return {
        jobType: o.jobType === "campus" || o.jobType === "intern" ? o.jobType : "fulltime",
        description: typeof o.description === "string" ? o.description : "",
        jdDetail: typeof o.jdDetail === "string" ? o.jdDetail : "",
        salary: typeof o.salary === "string" ? o.salary : "",
        focusPoints: typeof o.focusPoints === "string" ? o.focusPoints : ""
      };
    }
  } catch {
    /* legacy */
  }
  return { jobType: "fulltime", description: "", jdDetail: "", salary: raw, focusPoints: "" };
}

export function encodeJobBaseRange({ jobType, description, jdDetail, salary, focusPoints }) {
  return JSON.stringify({
    v: 1,
    jobType: jobType === "campus" || jobType === "intern" ? jobType : "fulltime",
    description: description == null ? "" : String(description),
    jdDetail: jdDetail == null ? "" : String(jdDetail),
    salary: salary == null ? "" : String(salary),
    focusPoints: focusPoints == null ? "" : String(focusPoints)
  });
}

export function jobTypeBadgeClass(jobType) {
  if (jobType === "campus") return "bg-emerald-100 text-emerald-800 border border-emerald-200";
  if (jobType === "intern") return "bg-violet-100 text-violet-800 border border-violet-200";
  return "bg-blue-100 text-blue-800 border border-blue-200";
}

export function jobTypeLabel(jobType) {
  const m = { fulltime: "全职", campus: "校招", intern: "实习" };
  return m[jobType] || "全职";
}

/** 从模型返回文本中提取 JSON 对象（支持裸 JSON、```json 围栏、或正文中的第一段 {...}）。 */
export function extractAssistantJsonObject(text) {
  const raw = String(text || "").trim();
  if (!raw) return null;
  const tryParse = (s) => {
    try {
      return JSON.parse(String(s).trim());
    } catch {
      return null;
    }
  };
  let o = tryParse(raw);
  if (o && typeof o === "object" && !Array.isArray(o)) return o;
  const fence = raw.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fence) {
    o = tryParse(fence[1]);
    if (o && typeof o === "object" && !Array.isArray(o)) return o;
  }
  const i = raw.indexOf("{");
  const j = raw.lastIndexOf("}");
  if (i >= 0 && j > i) {
    o = tryParse(raw.slice(i, j + 1));
    if (o && typeof o === "object" && !Array.isArray(o)) return o;
  }
  return null;
}

/** 解析结果中的岗位类型 → fulltime | campus | intern */
export function normalizeParsedJobType(v) {
  const s = String(v || "")
    .trim()
    .toLowerCase();
  if (s === "campus" || s === "校招") return "campus";
  if (s === "intern" || s === "实习") return "intern";
  if (s === "fulltime" || s === "全职") return "fulltime";
  return "fulltime";
}

/** 将粘贴的纯文本 JD 存为简单 HTML（段落），供 base_range JSON 中的 jdDetail 入库。 */
export function jdPlainToSimpleHtml(plain) {
  const t = plain == null ? "" : String(plain);
  if (!t.trim()) return "";
  const esc = (s) =>
    s
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  return t
    .split(/\r?\n/)
    .map((line) => (line.trim() === "" ? "<br/>" : `<p>${esc(line)}</p>`))
    .join("");
}
