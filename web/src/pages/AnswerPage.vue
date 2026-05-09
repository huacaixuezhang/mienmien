<script setup>
import { reactive } from "vue";
import { useWorkspace } from "../composables/useWorkspace";

const { currentSpaceId } = useWorkspace();

const defaults = {
  intro: "标准结构化自我介绍，可结合岗位动态调整。",
  reason: "离职原因保持客观正向，避免情绪化表达。",
  strengths: "优点贴岗、缺点可改，回答要真实可落地。",
  project: "项目难点与解决方案用 STAR 结构讲清楚。",
  hr: "HR 通用问题统一沉淀，面试前反复演练。"
};

const form = reactive({ ...defaults });

function exportJson() {
  const blob = new Blob([JSON.stringify(form, null, 2)], { type: "application/json" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `answer-bank-${currentSpaceId.value || "default"}.json`;
  a.click();
  URL.revokeObjectURL(a.href);
}

function importJson(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => {
    try {
      const parsed = JSON.parse(String(reader.result || "{}"));
      Object.assign(form, defaults, parsed);
    } catch {
      alert("JSON 格式无效");
    }
  };
  reader.readAsText(file);
}
</script>

<template>
  <section class="card">
    <div class="page-title-row">
      <h2>标准题库（空间隔离草稿）</h2>
      <span class="badge">space: {{ currentSpaceId || "未选择" }}</span>
    </div>
    <div class="row">
      <button class="ghost" @click="exportJson">导出JSON</button>
      <label class="import-label">导入JSON<input type="file" accept="application/json" @change="importJson" /></label>
    </div>
    <p class="muted">当前仅展示编辑能力；最终以 B 端数据库接口落库为准。</p>
    <div class="grid-2">
      <label class="field">自我介绍 <textarea v-model="form.intro" rows="4" /></label>
      <label class="field">离职/入职原因 <textarea v-model="form.reason" rows="4" /></label>
      <label class="field">优缺点 <textarea v-model="form.strengths" rows="4" /></label>
      <label class="field">项目难点与方案 <textarea v-model="form.project" rows="4" /></label>
      <label class="field grid-span-2">HR 通用题库 <textarea v-model="form.hr" rows="4" /></label>
    </div>
  </section>
</template>

<style scoped>
.ghost {
  background: #eef2ff;
  color: #3730a3;
}
.import-label {
  background: #eef2ff;
  color: #3730a3;
  border-radius: 8px;
  padding: 8px;
  cursor: pointer;
}
.import-label input {
  display: none;
}
</style>
