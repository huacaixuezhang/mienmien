/**
 * 电脑端虚拟形象视频：循环 WebM/MP4 等，与 TTS 同步切换「待机 / 说话」片源。
 * 素材放 web/public/virtual-avatar/ 下即可被默认路径命中；也可用会话字段或 Vite 环境变量覆盖。
 */

export const FALLBACK_AVATAR_IDLE_PATH = "/virtual-avatar/idle.webm";
export const FALLBACK_AVATAR_SPEAKING_PATH = "/virtual-avatar/speaking.webm";

function pickFirstNonEmpty(...candidates) {
  for (const c of candidates) {
    const s = typeof c === "string" ? c.trim() : "";
    if (s) return s;
  }
  return "";
}

/**
 * @param {Record<string, unknown> | null | undefined} session
 * @param {Record<string, unknown> | null | undefined} context
 * @returns {{ idle: string, speaking: string }}
 */
export function resolveVirtualAvatarUrls(session, context) {
  const idle = pickFirstNonEmpty(
    session?.virtualAvatarIdleUrl,
    session?.virtual_avatar_idle_url,
    context?.virtualAvatarIdleUrl,
    typeof import.meta.env?.VITE_VIRTUAL_AVATAR_IDLE_URL === "string"
      ? import.meta.env.VITE_VIRTUAL_AVATAR_IDLE_URL
      : "",
    FALLBACK_AVATAR_IDLE_PATH
  );
  const speaking = pickFirstNonEmpty(
    session?.virtualAvatarSpeakingUrl,
    session?.virtual_avatar_speaking_url,
    context?.virtualAvatarSpeakingUrl,
    typeof import.meta.env?.VITE_VIRTUAL_AVATAR_SPEAKING_URL === "string"
      ? import.meta.env.VITE_VIRTUAL_AVATAR_SPEAKING_URL
      : "",
    FALLBACK_AVATAR_SPEAKING_PATH
  );
  return { idle, speaking: pickFirstNonEmpty(speaking, idle) };
}
