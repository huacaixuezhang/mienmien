/**
 * 将简历名称转为安全的 PDF 文件名（不含路径分隔符等）。
 * @param {string} name
 * @returns {string}
 */
export function buildResumePdfFilename(name) {
  const raw = (name || "").trim() || "简历";
  const safe = raw.replace(/[/\\?%*:|"<>]/g, "-").replace(/\s+/g, " ").trim().slice(0, 80) || "简历";
  return `${safe}.pdf`;
}

/**
 * 无 HTML 时的纯文本回退（避免 DOMPurify 清空后模块完全无字）。
 * @param {string} raw
 * @returns {string}
 */
function plainTextFallback(raw) {
  if (!raw || typeof raw !== "string") return "";
  const div = document.createElement("div");
  div.innerHTML = raw;
  const t = (div.textContent || div.innerText || "").replace(/\s+/g, " ").trim();
  return t;
}

/**
 * 等待布局与字体就绪（html2canvas 对离屏/未绘制节点常得到白页）。
 * @returns {Promise<void>}
 */
function waitForPaint() {
  return new Promise((resolve) => {
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        setTimeout(resolve, 80);
      });
    });
  });
}

/**
 * 在浏览器内将简历文档渲染为 A4 PDF 并触发下载。
 * @param {{ name?: string, modules?: Array<{ title?: string, text?: string }> }} doc
 * @returns {Promise<void>}
 */
export async function exportResumeDocumentToPdf(doc) {
  const [{ default: DOMPurify }, { default: html2pdf }] = await Promise.all([import("dompurify"), import("html2pdf.js")]);

  if (!doc || typeof document === "undefined") {
    throw new Error("无效的简历数据");
  }
  const name = (doc.name || "").trim() || "未命名简历";
  const modules = Array.isArray(doc.modules) ? doc.modules : [];

  const backdrop = document.createElement("div");
  backdrop.setAttribute("data-resume-pdf-backdrop", "1");
  backdrop.style.cssText =
    "position:fixed;inset:0;z-index:2147483640;display:flex;align-items:flex-start;justify-content:center;padding:24px;overflow:auto;background:rgba(248,250,252,0.92);box-sizing:border-box;";

  const hint = document.createElement("p");
  hint.textContent = "正在生成 PDF…";
  hint.style.cssText =
    "position:fixed;top:16px;left:50%;transform:translateX(-50%);z-index:2147483641;margin:0;padding:8px 16px;border-radius:8px;background:#1e293b;color:#fff;font-size:13px;font-family:system-ui,sans-serif;pointer-events:none;";
  backdrop.appendChild(hint);

  const container = document.createElement("div");
  container.setAttribute("data-resume-pdf-export", "1");
  /** 必须在视口内且可绘制；负坐标离屏时 html2canvas 常见整页空白 */
  container.style.cssText =
    'position:relative;flex-shrink:0;box-sizing:border-box;width:794px;max-width:calc(100vw - 48px);padding:28px;font-family:"PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;font-size:14px;color:#111827;line-height:1.65;background:#ffffff;box-shadow:0 12px 40px rgba(15,23,42,0.12);border-radius:8px;';

  const h1 = document.createElement("h1");
  h1.style.cssText =
    "font-size:22px;margin:0 0 22px;font-weight:700;border-bottom:2px solid #2563eb;padding-bottom:10px;color:#111827;";
  h1.textContent = name;
  container.appendChild(h1);

  if (modules.length === 0) {
    const p = document.createElement("p");
    p.style.cssText = "margin:0;color:#6b7280;font-size:13px;";
    p.textContent = "（暂无模块）";
    container.appendChild(p);
  } else {
    modules.forEach((m, i) => {
      const sec = document.createElement("section");
      sec.style.cssText = "margin-bottom:22px;page-break-inside:avoid;";
      const h2 = document.createElement("h2");
      h2.style.cssText = "font-size:16px;margin:0 0 10px;color:#1d4ed8;font-weight:600;";
      const title = ((m && m.title) || "").trim() || `模块${i + 1}`;
      h2.textContent = title;
      const body = document.createElement("div");
      body.style.cssText =
        "font-size:13px;color:#374151;word-break:break-word;border-left:3px solid #e5e7eb;padding-left:12px;";
      const raw = m && typeof m.text === "string" ? m.text : "";
      const clean = DOMPurify.sanitize(raw, { USE_PROFILES: { html: true } });
      if (clean.trim()) {
        body.innerHTML = clean;
      } else {
        const plain = plainTextFallback(raw);
        body.textContent = plain || "（本模块暂无正文）";
      }
      sec.appendChild(h2);
      sec.appendChild(body);
      container.appendChild(sec);
    });
  }

  backdrop.appendChild(container);
  document.body.appendChild(backdrop);

  try {
    await waitForPaint();
    await html2pdf()
      .set({
        margin: [12, 12, 14, 12],
        filename: buildResumePdfFilename(name),
        image: { type: "jpeg", quality: 0.92 },
        html2canvas: {
          scale: 2,
          useCORS: true,
          logging: false,
          letterRendering: true,
          scrollX: 0,
          scrollY: 0,
          backgroundColor: "#ffffff"
        },
        jsPDF: { unit: "mm", format: "a4", orientation: "portrait" },
        pagebreak: { mode: ["avoid-all", "css", "legacy"] }
      })
      .from(container)
      .save();
  } finally {
    document.body.removeChild(backdrop);
  }
}
