<script setup>
defineProps({
  open: { type: Boolean, default: false },
  question: { type: Object, default: null },
  roundTitle: { type: String, default: "" },
  panelModalDragging: { type: Boolean, default: false },
  panelModalOffset: { type: Object, default: () => ({ x: 0, y: 0 }) }
});

defineEmits(["close", "edit", "collect-to-answer-bank", "header-pointerdown"]);

function isQuestionInAnswerBank(q) {
  return !!String(q?.answerBankCardKey ?? "").trim();
}

function voiceTurnSessionSubtitle(q) {
  if (q?.source !== "video_turn") return "";
  const sid = String(q?.videoSessionId || "").trim();
  if (!sid) return "";
  const ord = Number(q?.videoSessionOrdinal);
  const ordPart =
    Number.isFinite(ord) && ord > 0
      ? `当轮第 ${ord} 次语音练习`
      : "旧数据未记录场次序号";
  const mid = sid.length > 36 ? `${sid.slice(0, 16)}…${sid.slice(-12)}` : sid;
  return `${ordPart} · 会话 ${mid}`;
}

function formatScoreWeight(w) {
  const n = Number(w);
  if (!Number.isFinite(n) || n <= 0) return "";
  return `${Math.round(n * 1000) / 10}%`;
}
</script>

<template>
  <div
    v-if="open && question"
    class="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 p-4"
    @click.self="$emit('close')"
  >
    <div
      class="bg-white rounded-xl shadow-2xl border border-gray-100 w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden slide-in-modal"
      :class="panelModalDragging ? 'cursor-grabbing' : ''"
      :style="{ transform: `translate(${panelModalOffset.x}px, ${panelModalOffset.y}px)` }"
      @click.stop
    >
      <div
        class="shrink-0 px-6 py-4 border-b border-gray-200 cursor-move flex items-start justify-between gap-3"
        @pointerdown="$emit('header-pointerdown', $event)"
      >
        <div class="min-w-0">
          <div class="flex items-center flex-wrap gap-2">
            <span
              class="text-xs font-semibold bg-blue-100 text-blue-800 px-2 py-0.5 rounded"
              :title="question.source === 'video_turn' && question.videoSessionId ? String(question.videoSessionId) : ''"
            >
              {{ question.label || "题目" }}
            </span>
            <span
              v-if="question.source === 'video_turn'"
              class="text-[10px] font-medium uppercase tracking-wide bg-emerald-100 text-emerald-800 px-2 py-0.5 rounded"
            >
              语音
            </span>
            <span
              v-if="isQuestionInAnswerBank(question)"
              class="text-[10px] font-medium bg-amber-100 text-amber-900 px-2 py-0.5 rounded"
            >
              已收藏
            </span>
          </div>
          <h3 class="text-lg font-bold text-gray-900 mt-2 leading-snug">
            {{ question.title || "未命名题目" }}
          </h3>
          <p v-if="roundTitle" class="text-xs text-gray-500 mt-1">所属：{{ roundTitle }}</p>
          <p v-if="voiceTurnSessionSubtitle(question)" class="text-[11px] text-slate-500 mt-1">
            {{ voiceTurnSessionSubtitle(question) }}
          </p>
        </div>
        <button
          type="button"
          class="text-gray-400 hover:text-gray-700 p-2 rounded shrink-0"
          aria-label="关闭"
          @click="$emit('close')"
        >
          <i class="fa-solid fa-xmark text-lg"></i>
        </button>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto p-6 space-y-5 text-sm">
        <section v-if="question.questionRecord">
          <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">原题记录</h4>
          <p class="text-gray-800 leading-relaxed whitespace-pre-wrap">{{ question.questionRecord }}</p>
        </section>

        <section v-if="question.answerRecord">
          <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">作答记录</h4>
          <p class="text-gray-700 leading-relaxed whitespace-pre-wrap">{{ question.answerRecord }}</p>
        </section>

        <section v-if="question.pros">
          <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">作答优点</h4>
          <p class="text-green-700 leading-relaxed whitespace-pre-wrap">{{ question.pros }}</p>
        </section>

        <section v-if="question.cons">
          <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">作答缺点</h4>
          <p class="text-red-600 leading-relaxed whitespace-pre-wrap">{{ question.cons }}</p>
        </section>

        <section v-if="question.improvementPlan">
          <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">后续优化</h4>
          <p class="text-violet-700 leading-relaxed whitespace-pre-wrap">{{ question.improvementPlan }}</p>
        </section>

        <section v-if="question.standardAnswer">
          <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">标准答案</h4>
          <p class="text-gray-700 leading-relaxed whitespace-pre-wrap">{{ question.standardAnswer }}</p>
        </section>

        <section
          v-if="!question.questionRecord && !question.answerRecord && !question.pros && !question.cons && !question.improvementPlan && !question.standardAnswer"
          class="text-center py-8 text-gray-400"
        >
          <i class="fa-solid fa-file-lines text-3xl mb-2"></i>
          <p>暂无详细内容</p>
        </section>
      </div>

      <div class="shrink-0 px-6 py-4 border-t border-gray-200 bg-gray-50/80 flex flex-wrap items-center justify-between gap-3">
        <div class="flex flex-wrap items-center gap-4 text-sm">
          <div class="flex items-center gap-1">
            <span class="text-gray-500">难度：</span>
            <span class="flex gap-0.5">
              <i
                v-for="si in 3"
                :key="`detail-d-${si}`"
                :class="
                  si <= (question.difficulty || 1)
                    ? 'fa-solid fa-star text-yellow-400'
                    : 'fa-regular fa-star text-gray-300'
                "
              ></i>
            </span>
          </div>
          <div>
            <span class="text-gray-500 mr-1">分数：</span>
            <span class="font-semibold text-primary">{{ question.score ?? 0 }}</span>
          </div>
          <div v-if="formatScoreWeight(question.scoreWeight)">
            <span class="text-gray-500 mr-1">综合权重：</span>
            <span class="font-medium text-slate-700">{{ formatScoreWeight(question.scoreWeight) }}</span>
          </div>
        </div>
        <div class="flex gap-2 shrink-0">
          <button
            type="button"
            class="px-4 py-2 border rounded-md text-sm"
            :class="
              isQuestionInAnswerBank(question)
                ? 'border-amber-300 text-amber-900 bg-amber-50 hover:bg-amber-100'
                : 'border-amber-300 text-amber-900 hover:bg-amber-50'
            "
            @click="$emit('collect-to-answer-bank')"
          >
            <i
              :class="
                isQuestionInAnswerBank(question)
                  ? 'fa-solid fa-bookmark mr-1'
                  : 'fa-regular fa-bookmark mr-1'
              "
            ></i>{{ isQuestionInAnswerBank(question) ? "取消收藏" : "转为标准题库" }}
          </button>
          <button
            type="button"
            class="px-4 py-2 border border-gray-300 rounded-md text-sm text-gray-700 hover:bg-gray-100"
            @click="$emit('edit')"
          >
            <i class="fa-solid fa-pencil mr-1"></i>编辑
          </button>
          <button
            type="button"
            class="px-4 py-2 bg-primary hover:bg-blue-700 text-white rounded-md text-sm"
            @click="$emit('close')"
          >
            关闭
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
