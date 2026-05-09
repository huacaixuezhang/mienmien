<script setup>
import { ref } from "vue";
import { createInterview, listInterview } from "../api";
import { useWorkspace } from "../composables/useWorkspace";

const type = ref("real");
const interviewType = ref("technical");
const score = ref(80);
const summary = ref("回答结构清晰");
const result = ref("pass");
const list = ref([]);
const { currentSpaceId } = useWorkspace();

function exportJson() {
  const payload = {
    interviewType: interviewType.value,
    score: score.value,
    summary: summary.value,
    result: result.value,
    rows: list.value.filter((x) => x.type === "real")
  };
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `real-interview-${currentSpaceId.value || "default"}.json`;
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
      interviewType.value = parsed.interviewType || interviewType.value;
      score.value = Number(parsed.score || score.value);
      summary.value = parsed.summary || summary.value;
      result.value = parsed.result || result.value;
    } catch {
      alert("JSON 格式无效");
    }
  };
  reader.readAsText(file);
}

async function submit() {
  if (!currentSpaceId.value) return;
  await createInterview(type.value, {
    spaceId: currentSpaceId.value,
    interviewType: interviewType.value,
    score: String(score.value),
    summary: summary.value,
    result: result.value,
    round: "1"
  });
  list.value = await listInterview(currentSpaceId.value);
}

async function query() {
  if (!currentSpaceId.value) return;
  list.value = await listInterview(currentSpaceId.value);
}
</script>

<template>
  <section class="card">
    <div class="page-title-row">
      <h2>正式面试管理</h2>
      <span class="badge">space: {{ currentSpaceId || "未选择" }}</span>
    </div>
    <div class="row">
      <select v-model="type">
        <option value="real">real</option>
      </select>
      <input v-model="interviewType" placeholder="面试类型" />
      <input v-model.number="score" type="number" />
      <input v-model="result" placeholder="结果" />
      <input v-model="summary" placeholder="总结" />
      <button @click="submit" :disabled="!currentSpaceId">保存记录</button>
      <button @click="query" :disabled="!currentSpaceId">查询</button>
      <button class="ghost" @click="exportJson">导出JSON</button>
      <label class="import-label">导入JSON<input type="file" accept="application/json" @change="importJson" /></label>
    </div>
    <ul>
      <li v-for="item in list.filter((x) => x.type === 'real')" :key="item.recordId">{{ item.type }} | {{ item.score }} | {{ item.summary }}</li>
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
