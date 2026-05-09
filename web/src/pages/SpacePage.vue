<script setup>
import { ref, onMounted } from "vue";
import { createSpace, listSpaces, renameSpace, archiveSpace } from "../api";
import { useWorkspace } from "../composables/useWorkspace";

const name = ref("求职空间A");
const renameMap = ref({});
const { spaces, currentSpaceId, setCurrentSpace, refreshSpaces } = useWorkspace();

async function refresh() {
  spaces.value = await listSpaces();
  if (!currentSpaceId.value && spaces.value.length > 0) {
    setCurrentSpace(spaces.value[0].spaceId);
  }
}

async function submit() {
  await createSpace({ name: name.value });
  await refresh();
}

async function doRename(spaceId) {
  const nm = renameMap.value[spaceId]?.trim();
  if (!nm) return;
  await renameSpace(spaceId, { name: nm });
  renameMap.value[spaceId] = "";
  await refresh();
}

async function doArchive(spaceId) {
  if (!confirm(`归档空间 ${spaceId}？须无简历/JD/面试/在招岗位。`)) return;
  await archiveSpace(spaceId);
  await refresh();
}

onMounted(refresh);
</script>

<template>
  <section class="card">
    <div class="page-title-row">
      <h2>多工作空间管理</h2>
      <span class="badge">当前: {{ currentSpaceId || "未选择" }}</span>
    </div>
    <div class="row">
      <input v-model="name" placeholder="空间名（需先在主工作台登录以写入会话）" />
      <button @click="submit">创建空间</button>
      <button @click="refreshSpaces">刷新</button>
    </div>
    <ul>
      <li v-for="item in spaces" :key="item.spaceId" class="space-row">
        <button class="ghost" @click="setCurrentSpace(item.spaceId)">
          {{ item.spaceId }} — {{ item.name }} ({{ item.status }})
        </button>
        <input v-model="renameMap[item.spaceId]" placeholder="新名称" />
        <button @click="doRename(item.spaceId)">重命名</button>
        <button @click="doArchive(item.spaceId)">归档</button>
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
.space-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.ghost {
  background: #eef2ff;
  color: #3730a3;
}
</style>
