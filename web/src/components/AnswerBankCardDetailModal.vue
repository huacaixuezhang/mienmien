<script setup>
import { computed } from "vue";
import { parseAnswerBankCardTextToQuestionFields } from "../utils/interviewQuestionToAnswerBank.js";

const props = defineProps({
  open: { type: Boolean, default: false },
  card: { type: Object, default: null },
  panelModalDragging: { type: Boolean, default: false },
  panelModalOffset: { type: Object, default: () => ({ x: 0, y: 0 }) }
});

defineEmits(["close", "edit", "header-pointerdown"]);

const fields = computed(() => parseAnswerBankCardTextToQuestionFields(props.card?.text));

const hasStructured = computed(() =>
  Boolean(
    fields.value.questionRecord ||
      fields.value.answerRecord ||
      fields.value.pros ||
      fields.value.cons ||
      fields.value.improvementPlan ||
      fields.value.standardAnswer
  )
);
</script>

<template>
  <div
    v-if="open && card"
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
          <span class="text-xs font-semibold bg-blue-100 text-blue-800 px-2 py-0.5 rounded"> 标准题库 </span>
          <h3 class="text-lg font-bold text-gray-900 mt-2 leading-snug">
            {{ card.title || "未命名卡片" }}
          </h3>
          <p v-if="card.sourceQuestionId" class="text-[11px] text-slate-500 mt-1">
            来源面试题 ID：{{ card.sourceQuestionId }}
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
        <template v-if="hasStructured">
          <section v-if="fields.questionRecord">
            <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">题干</h4>
            <p class="text-gray-800 leading-relaxed whitespace-pre-wrap">{{ fields.questionRecord }}</p>
          </section>
          <section v-if="fields.answerRecord">
            <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">作答</h4>
            <p class="text-gray-700 leading-relaxed whitespace-pre-wrap">{{ fields.answerRecord }}</p>
          </section>
          <section v-if="fields.standardAnswer">
            <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">标准答案</h4>
            <p class="text-gray-700 leading-relaxed whitespace-pre-wrap">{{ fields.standardAnswer }}</p>
          </section>
          <section v-if="fields.pros">
            <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">优点</h4>
            <p class="text-green-700 leading-relaxed whitespace-pre-wrap">{{ fields.pros }}</p>
          </section>
          <section v-if="fields.cons">
            <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">缺点</h4>
            <p class="text-red-600 leading-relaxed whitespace-pre-wrap">{{ fields.cons }}</p>
          </section>
          <section v-if="fields.improvementPlan">
            <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">后续优化</h4>
            <p class="text-violet-700 leading-relaxed whitespace-pre-wrap">{{ fields.improvementPlan }}</p>
          </section>
        </template>
        <section v-else-if="card.text">
          <p class="text-gray-800 leading-relaxed whitespace-pre-wrap">{{ card.text }}</p>
        </section>
        <section v-else class="text-center py-8 text-gray-400">
          <i class="fa-solid fa-file-lines text-3xl mb-2"></i>
          <p>暂无内容</p>
        </section>
      </div>

      <div class="shrink-0 px-6 py-4 border-t border-gray-200 bg-gray-50/80 flex justify-end gap-2">
        <button
          type="button"
          class="px-4 py-2 border border-gray-300 rounded-md text-sm text-gray-700 hover:bg-gray-100"
          @click="$emit('edit')"
        >
          <i class="fa-solid fa-pencil mr-1"></i>修改
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
</template>
