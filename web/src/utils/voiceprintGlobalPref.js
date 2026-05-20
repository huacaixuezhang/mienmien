/** 全局声纹偏好（本机 localStorage，全账号/全空间共用浏览器配置） */

const LS_PREF = "mienmien_voiceprint_pref_v1";

/**
 * @returns {{ variant: 'energy', filterOn: boolean }}
 */
export function readVoiceprintPrefs() {
  try {
    const raw = localStorage.getItem(LS_PREF);
    const o = raw ? JSON.parse(raw) : {};
    return {
      variant: "energy",
      filterOn: Boolean(o.filterOn)
    };
  } catch {
    return { variant: "energy", filterOn: false };
  }
}

/** @param {Partial<{ variant: 'energy', filterOn: boolean }>} partial */
export function writeVoiceprintPrefs(partial) {
  const cur = readVoiceprintPrefs();
  const next = { ...cur, ...partial };
  localStorage.setItem(LS_PREF, JSON.stringify(next));
  try {
    window.dispatchEvent(new CustomEvent("mienmien-voiceprint-global-updated"));
  } catch {
    /* ignore */
  }
}
