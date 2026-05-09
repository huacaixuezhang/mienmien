<script setup>
import { computed, reactive, ref } from "vue";
import { createResume, listResumes } from "../api";
import { useWorkspace } from "../composables/useWorkspace";

const version = ref(1);
const list = ref([]);
const { currentSpaceId } = useWorkspace();
const draggingIndex = ref(-1);

const blocks = reactive([
  { key: "basic", title: "基础信息", text: "姓名：xxx｜年限：3年｜城市：杭州" },
  { key: "skills", title: "技能栈", text: "Java、SpringBoot、MySQL、Redis" },
  { key: "work", title: "工作经历", text: "2022-至今 xxx公司 Java后端开发" },
  { key: "project", title: "项目经历", text: "订单系统、权限系统、日志平台" }
]);

const content = computed(() =>
  blocks.map((b) => `【${b.title}】\n${b.text}`).join("\n\n")
);

async function submit() {
  if (!currentSpaceId.value) return;
  await createResume({ spaceId: currentSpaceId.value, content: content.value, version: String(version.value) });
  list.value = await listResumes(currentSpaceId.value);
}

async function query() {
  if (!currentSpaceId.value) return;
  list.value = await listResumes(currentSpaceId.value);
}

function moveBlock(index, offset) {
  const next = index + offset;
  if (next < 0 || next >= blocks.length) return;
  const item = blocks[index];
  blocks.splice(index, 1);
  blocks.splice(next, 0, item);
}

function onDragStart(index) {
  draggingIndex.value = index;
}

function onDrop(index) {
  if (draggingIndex.value < 0 || draggingIndex.value === index) return;
  const item = blocks[draggingIndex.value];
  blocks.splice(draggingIndex.value, 1);
  blocks.splice(index, 0, item);
  draggingIndex.value = -1;
}

function exportJson() {
  const data = { version: version.value, blocks: blocks.map((x) => ({ ...x })) };
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `resume-blocks-${currentSpaceId.value || "default"}.json`;
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
      if (Array.isArray(parsed.blocks) && parsed.blocks.length > 0) {
        blocks.splice(0, blocks.length, ...parsed.blocks);
      }
      if (parsed.version) version.value = Number(parsed.version) || 1;
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
      <h2>模块化简历管理</h2>
      <span class="badge">space: {{ currentSpaceId || "未选择" }}</span>
    </div>
    <div class="row">
      <input v-model.number="version" type="number" />
      <button @click="submit" :disabled="!currentSpaceId">保存简历</button>
      <button @click="query" :disabled="!currentSpaceId">查询</button>
      <button class="ghost" @click="exportJson">导出JSON</button>
      <label class="import-label">导入JSON<input type="file" accept="application/json" @change="importJson" /></label>
    </div>
    <div class="grid-2">
      <div
        v-for="(block, idx) in blocks"
        :key="block.key"
        class="card block-card"
        draggable="true"
        @dragstart="onDragStart(idx)"
        @dragover.prevent
        @drop="onDrop(idx)"
      >
        <div class="row">
          <strong>{{ block.title }}</strong>
          <button class="ghost" @click="moveBlock(idx, -1)">上移</button>
          <button class="ghost" @click="moveBlock(idx, 1)">下移</button>
        </div>
        <textarea v-model="block.text" rows="4" />
      </div>
    </div>
    <ul>
      <li v-for="item in list" :key="item.resumeId">v{{ item.version }} - {{ item.content }}</li>
    </ul>
  </section>
</template>

<style scoped>
.block-card {
  padding: 12px;
}
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
