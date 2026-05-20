<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";

const props = defineProps({
  idleSrc: { type: String, default: "" },
  speakingSrc: { type: String, default: "" },
  /** 与面试官 TTS 播报对齐：true 时优先展示「说话」轨 */
  isSpeaking: { type: Boolean, default: false }
});

const idleEl = ref(null);
const speakEl = ref(null);
const idleBroken = ref(false);
const speakBroken = ref(false);

const idleUrl = computed(() => (props.idleSrc || "").trim());
const speakUrl = computed(() => (props.speakingSrc || "").trim());

const useSpeakTrack = computed(
  () => !idleBroken.value && !speakBroken.value && speakUrl.value && speakUrl.value !== idleUrl.value
);

const showVideo = computed(() => Boolean(idleUrl.value) && !idleBroken.value);

function onIdleError() {
  idleBroken.value = true;
}

function onSpeakError() {
  speakBroken.value = true;
}

function pauseEl(el) {
  try {
    el?.pause();
  } catch {
    /* ignore */
  }
}

async function playEl(el) {
  if (!el) return;
  try {
    el.muted = true;
    await el.play();
  } catch {
    /* 自动播放策略等 */
  }
}

async function syncPlayback() {
  await nextTick();
  const idle = idleEl.value;
  const sp = speakEl.value;
  if (!showVideo.value || !idle) return;

  if (!useSpeakTrack.value) {
    pauseEl(sp);
    idle.loop = true;
    await playEl(idle);
    return;
  }

  if (props.isSpeaking) {
    pauseEl(idle);
    if (sp) {
      sp.loop = true;
      await playEl(sp);
    }
  } else {
    pauseEl(sp);
    idle.loop = true;
    await playEl(idle);
  }
}

watch(
  () => [idleUrl.value, speakUrl.value],
  async () => {
    idleBroken.value = false;
    speakBroken.value = false;
    await nextTick();
    await nextTick();
    idleEl.value?.load();
    if (useSpeakTrack.value) speakEl.value?.load();
    void syncPlayback();
  }
);

watch(
  () => props.isSpeaking,
  () => {
    void syncPlayback();
  }
);

watch(useSpeakTrack, () => {
  void syncPlayback();
});

watch([idleBroken, speakBroken], () => {
  void syncPlayback();
});

onMounted(() => {
  void syncPlayback();
});

onBeforeUnmount(() => {
  pauseEl(idleEl.value);
  pauseEl(speakEl.value);
});
</script>

<template>
  <div
    class="relative overflow-hidden rounded-xl bg-gradient-to-b from-violet-600 to-indigo-900 aspect-[3/4] w-full max-w-[10.5rem] shadow-inner ring-1 ring-white/15 shrink-0"
    role="img"
    :aria-label="isSpeaking ? '虚拟面试官 · 发言中' : '虚拟面试官 · 待机'"
  >
    <template v-if="showVideo">
      <video
        ref="idleEl"
        class="absolute inset-0 h-full w-full object-cover object-top transition-opacity duration-300"
        :class="isSpeaking && useSpeakTrack ? 'opacity-0 pointer-events-none' : 'opacity-100'"
        :src="idleUrl"
        muted
        playsinline
        loop
        preload="auto"
        @error="onIdleError"
      />
      <video
        v-if="useSpeakTrack"
        ref="speakEl"
        class="absolute inset-0 h-full w-full object-cover object-top transition-opacity duration-300"
        :class="isSpeaking ? 'opacity-100' : 'opacity-0 pointer-events-none'"
        :src="speakUrl"
        muted
        playsinline
        loop
        preload="metadata"
        @error="onSpeakError"
      />
    </template>

    <div
      class="absolute inset-0 flex flex-col items-center justify-center gap-1 p-2 text-center text-white"
      :class="showVideo ? 'pointer-events-none opacity-0' : 'opacity-100'"
    >
      <span class="text-2xl font-bold tracking-tight">AI</span>
      <span v-if="idleBroken && idleUrl" class="text-[10px] leading-snug text-white/85 px-1">
        虚拟形象视频未加载（404 或格式不支持）。请将 idle.webm / speaking.webm 放到站点
        <code class="rounded bg-black/25 px-0.5">public/virtual-avatar/</code>
        或配置 VITE_VIRTUAL_AVATAR_*_URL。
      </span>
    </div>
  </div>
</template>
