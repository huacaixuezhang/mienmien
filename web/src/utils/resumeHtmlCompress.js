/** 允许保留的 style 声明（contenteditable 常写入大量浏览器默认值，入库前剔除） */
const ALLOWED_STYLE_KEYS = new Set([
  "font-weight",
  "font-style",
  "text-decoration",
  "text-decoration-line",
  "text-decoration-style",
  "text-decoration-color",
  "text-underline-offset",
  "text-underline-position",
  "color",
  "background-color",
  "text-align",
  "font-size",
  "vertical-align",
  "white-space"
]);

/**
 * @param {string} key
 * @param {string} val
 * @returns {boolean}
 */
function isRedundantStyleValue(key, val) {
  const v = val.replace(/\s+/g, " ").trim().toLowerCase();
  switch (key) {
    case "font-weight":
      return v === "normal" || v === "400";
    case "font-style":
      return v === "normal";
    case "text-decoration":
    case "text-decoration-line":
      return v === "none" || v === "none solid rgb(0, 0, 0)";
    case "text-decoration-style":
      return v === "solid";
    case "color":
      return v === "rgb(0, 0, 0)" || v === "#000" || v === "#000000";
    case "background-color":
      return v === "transparent" || v === "rgba(0, 0, 0, 0)" || v === "rgb(0, 0, 0)";
    case "text-align":
      return v === "start" || v === "left";
    case "font-size":
      return v === "16px" || v === "1rem";
    case "vertical-align":
      return v === "baseline";
    case "white-space":
      return v === "normal" || v === "pre-wrap";
    default:
      return false;
  }
}

/**
 * @param {string} styleStr
 * @returns {string | null}
 */
function cleanStyleAttribute(styleStr) {
  if (!styleStr || typeof styleStr !== "string") return null;
  const parts = styleStr
    .split(";")
    .map((s) => s.trim())
    .filter(Boolean);
  const kept = [];
  for (const p of parts) {
    const idx = p.indexOf(":");
    if (idx === -1) continue;
    const key = p.slice(0, idx).trim().toLowerCase();
    const val = p.slice(idx + 1).trim();
    if (!ALLOWED_STYLE_KEYS.has(key)) continue;
    if (isRedundantStyleValue(key, val)) continue;
    kept.push(`${key}: ${val}`);
  }
  return kept.length ? kept.join("; ") : null;
}

/**
 * 去掉事件属性、清洗 style，并移除无属性 span（减少嵌套）。
 * @param {ParentNode} root
 */
function walkAndClean(root) {
  root.querySelectorAll("script, iframe, object, embed").forEach((n) => n.remove());

  root.querySelectorAll("*").forEach((el) => {
    for (const name of [...el.getAttributeNames()]) {
      if (/^on/i.test(name)) el.removeAttribute(name);
    }
    if (el.hasAttribute("style")) {
      const cleaned = cleanStyleAttribute(el.getAttribute("style") || "");
      if (cleaned) el.setAttribute("style", cleaned);
      else el.removeAttribute("style");
    }
  });

  const unwrapBareSpans = () => {
    const spans = Array.from(root.querySelectorAll("span"));
    for (const span of spans) {
      if (span.attributes.length === 0) {
        const parent = span.parentNode;
        if (!parent) continue;
        while (span.firstChild) parent.insertBefore(span.firstChild, span);
        parent.removeChild(span);
      }
    }
  };
  unwrapBareSpans();
  unwrapBareSpans();
}

/**
 * 压缩单个模块 HTML，便于入库与回显（去掉冗长默认 style）。
 * @param {string} html
 * @returns {string}
 */
export function compressResumeModuleHtml(html) {
  const raw = (html || "").trim();
  if (!raw) return "";
  if (typeof document === "undefined") return raw;

  try {
    const doc = new DOMParser().parseFromString(raw, "text/html");
    const container = doc.body;
    if (!container) return raw;
    walkAndClean(container);
    return container.innerHTML.trim();
  } catch {
    return raw;
  }
}

/**
 * 就地压缩简历保存请求体中的模块正文。
 * @param {{ modules?: Array<{ text?: string }> }} body
 * @returns {typeof body}
 */
export function compressResumePayload(body) {
  if (!body || !Array.isArray(body.modules)) return body;
  for (const m of body.modules) {
    if (m && typeof m.text === "string") {
      m.text = compressResumeModuleHtml(m.text);
    }
  }
  return body;
}
