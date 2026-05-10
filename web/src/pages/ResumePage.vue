<script setup>
import { computed, reactive, ref } from "vue";
import { createResumeDocumentMine, listAllResumeDocuments, listResumeDocuments } from "../api";
import { useWorkspace } from "../composables/useWorkspace";

const list = ref([]);
const { currentSpaceId } = useWorkspace();
const draggingIndex = ref(-1);

const blocks = reactive([
  { id: "b1", title: "基础信息", text: "姓名：xxx｜年限：3年｜城市：杭州" },
  { id: "b2", title: "技能栈", text: "Java、SpringBoot、MySQL、Redis" },
  { id: "b3", title: "工作经历", text: "2022-至今 xxx公司 Java后端开发" },
  { id: "b4", title: "项目经历", text: "订单系统、权限系统、日志平台" }
]);

const displayName = ref("演示简历");

const payload = computed(() => ({
  name: (displayName.value || "").trim() || "未命名简历",
  modules: blocks.map((b) => ({
    id: String(b.id),
    title: b.title ?? "",
    text: b.text ?? ""
  }))
}));

async function submit() {
  const body = { ...payload.value };
  if (currentSpaceId.value) body.spaceId = currentSpaceId.value;
  await createResumeDocumentMine(body);
  if (currentSpaceId.value) {
    list.value = await listResumeDocuments(currentSpaceId.value);
  } else {
    list.value = await listAllResumeDocuments();
  }
}

async function query() {
  if (!currentSpaceId.value) {
    list.value = await listAllResumeDocuments();
    return;
  }
  list.value = await listResumeDocuments(currentSpaceId.value);
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
  const data = { name: displayName.value, blocks: blocks.map((x) => ({ ...x })) };
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
      if (typeof parsed.name === "string") displayName.value = parsed.name;
      if (Array.isArray(parsed.blocks) && parsed.blocks.length > 0) {
        blocks.splice(0, blocks.length, ...parsed.blocks);
      }
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
      <input v-model="displayName" type="text" placeholder="简历名称" class="flex-1 min-w-[12rem]" />
      <button @click="submit" :disabled="!currentSpaceId">保存简历</button>
      <button @click="query" :disabled="!currentSpaceId">查询</button>
      <button class="ghost" @click="exportJson">导出JSON</button>
      <label class="import-label">导入JSON<input type="file" accept="application/json" @change="importJson" /></label>
    </div>
    <div class="grid-2">
      <div
        v-for="(block, idx) in blocks"
        :key="block.id"
        class="card block-card"
        draggable="true"
        @dragstart="onDragStart(idx)"
        @dragover.prevent
        @drop="onDrop(idx)"
      >
        <div class="row">
          <input v-model="block.title" class="font-semibold flex-1" />
          <button class="ghost" @click="moveBlock(idx, -1)">上移</button>
          <button class="ghost" @click="moveBlock(idx, 1)">下移</button>
        </div>
        <textarea v-model="block.text" rows="4" />
      </div>
    </div>
    <ul>
      <li v-for="item in list" :key="item.resumeId">
        {{ item.name }} — {{ (item.modules || []).length }} 个模块
      </li>
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
