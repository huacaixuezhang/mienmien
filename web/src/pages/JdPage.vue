<script setup>
import { ref } from "vue";
import { createJd, listJd } from "../api";
import { useWorkspace } from "../composables/useWorkspace";

const rawText = ref("岗位描述...");
const focusPoints = ref("业务理解,沟通能力");
const list = ref([]);
const { currentSpaceId } = useWorkspace();

async function submit() {
  if (!currentSpaceId.value) return;
  await createJd({ spaceId: currentSpaceId.value, rawText: rawText.value, focusPoints: focusPoints.value });
  list.value = await listJd(currentSpaceId.value);
}

async function query() {
  if (!currentSpaceId.value) return;
  list.value = await listJd(currentSpaceId.value);
}
</script>

<template>
  <section class="card">
    <div class="page-title-row">
      <h2>JD 管理与考点拆解</h2>
      <span class="badge">space: {{ currentSpaceId || "未选择" }}</span>
    </div>
    <div class="row">
      <input v-model="rawText" placeholder="JD 文本" />
      <input v-model="focusPoints" placeholder="重点项" />
      <button @click="submit" :disabled="!currentSpaceId">保存 JD</button>
      <button @click="query" :disabled="!currentSpaceId">查询</button>
    </div>
    <ul>
      <li v-for="item in list" :key="item.jdId">{{ item.rawText }} | {{ item.focusPoints }}</li>
    </ul>
  </section>
</template>
