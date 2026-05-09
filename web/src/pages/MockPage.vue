<script setup>
import { reactive, ref } from "vue";
import { createInterview, listInterview } from "../api";
import { useWorkspace } from "../composables/useWorkspace";

const { currentSpaceId } = useWorkspace();
const rows = ref([]);

const form = reactive({
  interviewType: "technical",
  score: 80,
  round: "1",
  summary: "",
  result: "pending"
});

function exportJson() {
  const payload = {
    form: { ...form },
    rows: rows.value.filter((x) => x.type === "mock")
  };
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `mock-interview-${currentSpaceId.value || "default"}.json`;
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
      if (parsed.form) {
        Object.assign(form, parsed.form);
      }
    } catch {
      alert("JSON 格式无效");
    }
  };
  reader.readAsText(file);
}

async function refresh() {
  if (!currentSpaceId.value) return;
  rows.value = await listInterview(currentSpaceId.value);
}

async function submit() {
  if (!currentSpaceId.value) return;
  await createInterview("mock", {
    spaceId: currentSpaceId.value,
    interviewType: form.interviewType,
    score: String(form.score),
    round: form.round,
    summary: form.summary,
    result: form.result
  });
  await refresh();
}
</script>

<template>
  <section class="card">
    <div class="page-title-row">
      <h2>模拟面试台账</h2>
      <span class="badge">space: {{ currentSpaceId || "未选择" }}</span>
    </div>
    <div class="grid-3">
      <label class="field">面试类型 <input v-model="form.interviewType" /></label>
      <label class="field">轮次 <input v-model="form.round" /></label>
      <label class="field">评分 <input v-model.number="form.score" type="number" /></label>
      <label class="field grid-span-2">总结 <textarea v-model="form.summary" rows="3" /></label>
      <label class="field">结果 <input v-model="form.result" /></label>
    </div>
    <div class="row">
      <button @click="submit" :disabled="!currentSpaceId">保存模拟记录</button>
      <button @click="refresh" :disabled="!currentSpaceId">刷新</button>
      <button class="ghost" @click="exportJson">导出JSON</button>
      <label class="import-label">导入JSON<input type="file" accept="application/json" @change="importJson" /></label>
    </div>

    <ul class="list">
      <li v-for="item in rows.filter((x) => x.type === 'mock')" :key="item.recordId">
        {{ item.recordId }} | {{ item.interviewType }} | {{ item.score }} | {{ item.result }}
      </li>
    </ul>
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
