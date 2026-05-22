<script setup>
defineProps({
  open: { type: Boolean, default: false },
  draft: { type: Object, default: () => ({ title: "", text: "" }) },
  saving: { type: Boolean, default: false },
  panelModalDragging: { type: Boolean, default: false },
  panelModalOffset: { type: Object, default: () => ({ x: 0, y: 0 }) }
});

defineEmits(["close", "save", "header-pointerdown"]);
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-[75] flex items-center justify-center bg-black/50 p-4"
    @click.self="$emit('close')"
  >
    <div
      class="bg-white rounded-xl shadow-2xl border border-gray-100 w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden slide-in-modal"
      :class="panelModalDragging ? 'cursor-grabbing' : ''"
      :style="{ transform: `translate(${panelModalOffset.x}px, ${panelModalOffset.y}px)` }"
      @click.stop
    >
      <div
        class="shrink-0 px-6 py-4 border-b border-gray-200 cursor-move"
        @pointerdown="$emit('header-pointerdown', $event)"
      >
        <h3 class="text-lg font-bold text-gray-900">修改题库卡片</h3>
        <p class="text-xs text-gray-500 mt-1">保存时可选择是否同步更新关联的面试复盘题目</p>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto p-6 space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">卡片标题</label>
          <input
            v-model="draft.title"
            type="text"
            class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-primary"
            placeholder="如：第4场｜语音第1题"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">内容</label>
          <textarea
            v-model="draft.text"
            rows="14"
            class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-primary resize-y min-h-[12rem] leading-relaxed"
            placeholder="【题干】&#10;...&#10;&#10;【作答】&#10;..."
          />
        </div>
      </div>

      <div class="shrink-0 px-6 py-4 border-t border-gray-200 bg-gray-50/80 flex justify-end gap-2">
        <button
          type="button"
          class="px-4 py-2 border border-gray-300 rounded-md text-sm text-gray-700 hover:bg-gray-100"
          :disabled="saving"
          @click="$emit('close')"
        >
          取消
        </button>
        <button
          type="button"
          class="px-4 py-2 bg-primary hover:bg-blue-700 text-white rounded-md text-sm disabled:opacity-60"
          :disabled="saving"
          @click="$emit('save')"
        >
          <i class="fa-solid fa-floppy-disk mr-1"></i>{{ saving ? "保存中…" : "保存" }}
        </button>
      </div>
    </div>
  </div>
</template>
