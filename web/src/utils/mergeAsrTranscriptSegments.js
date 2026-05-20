/**
 * 相邻两次语音识别结果拼接时，去掉上一段末尾与下一段开头的重复子串。
 * 常见于：切片时间窗重叠、VAD 断点两侧各带半句、流式多次定稿边界重复。
 *
 * @param {string} prev 已有文本
 * @param {string} next 新识别片段（trim 后参与匹配）
 * @param {{ minOverlap?: number, maxOverlapScan?: number }} [opts]
 * @returns {string}
 */
export function mergeAsrTranscriptSegments(prev, next, opts = {}) {
  const minOverlap = opts.minOverlap ?? 2;
  const maxOverlapScan = opts.maxOverlapScan ?? 96;
  let a = String(prev ?? "");
  const b0 = String(next ?? "").trim();
  if (!b0) {
    return a;
  }
  if (!a) {
    return b0;
  }
  a = a.replace(/\s+$/, "");
  const tailLen = Math.min(maxOverlapScan, a.length, b0.length);
  const tail = a.slice(-tailLen);
  for (let k = tailLen; k >= minOverlap; k--) {
    if (tail.slice(-k) === b0.slice(0, k)) {
      return a + b0.slice(k);
    }
  }
  return a + b0;
}
