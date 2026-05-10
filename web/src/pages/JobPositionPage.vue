<script setup>
/** @deprecated B 端岗位在 `App.vue`（activeTab === job）实现；本文件未接入路由，勿当作入口。 */
import { ref, onMounted } from "vue";
import { createJobPosition, listAllJobPositions, listJobPositions, closeJobPosition } from "../api";
import { useWorkspace } from "../composables/useWorkspace";

const title = ref("示例岗位");
const company = ref("");
const location = ref("");
const baseRange = ref("");
const rows = ref([]);
const { currentSpaceId } = useWorkspace();

async function refresh() {
  if (!currentSpaceId.value) {
    rows.value = await listAllJobPositions();
    return;
  }
  rows.value = await listJobPositions(currentSpaceId.value);
}

async function submit() {
  const payload = {
    title: title.value,
    company: company.value,
    location: location.value,
    baseRange: baseRange.value
  };
  if (currentSpaceId.value) payload.spaceId = currentSpaceId.value;
  await createJobPosition(payload);
  await refresh();
}

async function closeRow(id) {
  await closeJobPosition(id);
  await refresh();
}

onMounted(refresh);
</script>

<template>
  <section class="card">
    <div class="page-title-row">
      <h2>岗位管理</h2>
      <span class="badge">space: {{ currentSpaceId || "未选择" }}</span>
    </div>
    <p class="hint">独立岗位实体，与目标 JD 并存；关闭后不占「在招」名额。</p>
    <div class="row">
      <input v-model="title" placeholder="岗位标题" />
      <input v-model="company" placeholder="公司" />
      <input v-model="location" placeholder="地点" />
      <input v-model="baseRange" placeholder="薪资范围" />
      <button @click="submit" :disabled="!currentSpaceId">新增</button>
      <button @click="refresh" :disabled="!currentSpaceId">刷新</button>
    </div>
    <ul>
      <li v-for="p in rows" :key="p.positionId">
        {{ p.positionId }} | {{ p.title }} | {{ p.status }}
        <button v-if="p.status === 'ACTIVE'" @click="closeRow(p.positionId)">关闭</button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.hint {
  color: #555;
  font-size: 14px;
}
</style>
