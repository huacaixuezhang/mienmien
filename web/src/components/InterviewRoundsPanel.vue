<script setup>
defineProps({
  jobProfile: { type: Object, required: true },
  rounds: { type: Array, required: true }
});

const emit = defineEmits([
  "add-round",
  "edit-round",
  "remove-round",
  "add-question",
  "edit-question",
  "remove-question"
]);

function interviewerBadgeClass(role) {
  const s = String(role || "");
  if (s === "HR") return "bg-blue-500";
  if (s === "P" || s === "Peer") return "bg-emerald-500";
  if (s === "+1" || s.includes("+1")) return "bg-violet-500";
  if (s === "+2" || s.includes("+2")) return "bg-red-500";
  return "bg-gray-500";
}

function categoryBadgeClass(cat) {
  const c = String(cat || "");
  if (c.includes("技术")) return "bg-green-100 text-green-800";
  if (c.includes("业务")) return "bg-purple-100 text-purple-800";
  if (c.includes("HR")) return "bg-amber-100 text-amber-800";
  return "bg-slate-100 text-slate-800";
}

function resultSelectClass(ui) {
  if (ui === "通过") return "bg-green-100 text-green-800";
  if (ui === "拒绝") return "bg-red-100 text-red-800";
  return "bg-amber-100 text-amber-800";
}
</script>

<template>
  <section class="bg-white rounded-lg shadow-card p-6">
    <h2 class="text-xl font-bold text-gray-800 mb-4 flex items-center gap-2">
      <i class="fa-solid fa-briefcase text-primary"></i>
      岗位信息
    </h2>
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <div class="space-y-2">
        <label class="text-sm font-medium text-gray-500">职位名称</label>
        <input
          v-model="jobProfile.title"
          type="text"
          class="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent text-sm"
        />
      </div>
      <div class="space-y-2">
        <label class="text-sm font-medium text-gray-500">所属公司</label>
        <input
          v-model="jobProfile.company"
          type="text"
          class="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent text-sm"
        />
      </div>
      <div class="space-y-2">
        <label class="text-sm font-medium text-gray-500">办公地点</label>
        <input
          v-model="jobProfile.location"
          type="text"
          class="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent text-sm"
        />
      </div>
    </div>
    <div class="mt-6 space-y-2">
      <label class="text-sm font-medium text-gray-500">职位描述 (JD)</label>
      <textarea
        v-model="jobProfile.jdText"
        rows="6"
        class="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent text-sm"
        placeholder="请输入职位描述"
      />
    </div>
  </section>

  <section class="bg-white rounded-lg shadow-card overflow-hidden">
    <div class="p-6 border-b border-gray-200 flex justify-between items-center flex-wrap gap-3">
      <h2 class="text-xl font-bold text-gray-800 flex items-center gap-2">
        <i class="fa-solid fa-calendar-check text-primary"></i>
        面试流程
      </h2>
      <button
        type="button"
        class="bg-primary hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm flex items-center gap-2 transition-colors shrink-0"
        @click="emit('add-round')"
      >
        <i class="fa-solid fa-plus"></i>
        添加面试
      </button>
    </div>

    <div class="divide-y divide-gray-200">
      <div
        v-for="(round, ri) in rounds"
        :key="round.id"
        class="p-6 hover:bg-gray-50/80 transition-colors"
      >
        <div class="flex justify-between items-start gap-2 mb-4 flex-wrap">
          <div class="min-w-0">
            <h3 class="text-lg font-semibold text-gray-800">{{ round.roundTitle }}</h3>
            <p class="text-sm text-gray-500">{{ round.timeText || "未填写时间" }}</p>
          </div>
          <div class="flex items-center gap-2 flex-wrap shrink-0">
            <span class="px-2 py-1 bg-blue-100 text-blue-800 text-xs rounded-full">{{ round.locationMode || "线上" }}</span>
            <span class="px-2 py-1 text-xs rounded-full" :class="categoryBadgeClass(round.category)">{{
              round.category || "技术面"
            }}</span>
            <button
              type="button"
              class="text-primary hover:text-blue-800 p-1.5 rounded"
              title="编辑本轮"
              @click="emit('edit-round', ri)"
            >
              <i class="fa-solid fa-pencil"></i>
            </button>
            <button
              type="button"
              class="text-gray-400 hover:text-red-600 p-1.5 rounded"
              title="删除本轮"
              @click="emit('remove-round', ri)"
            >
              <i class="fa-solid fa-trash"></i>
            </button>
          </div>
        </div>

        <div class="mb-4">
          <h4 class="text-sm font-medium text-gray-500 mb-2">面试官信息</h4>
          <div class="flex flex-wrap gap-2">
            <div
              v-for="(iv, ii) in round.interviewers || []"
              :key="ii"
              class="flex items-center bg-gray-100 px-3 py-1 rounded-full text-sm"
            >
              <span
                class="w-6 h-6 rounded-full text-white flex items-center justify-center text-[10px] mr-2 shrink-0 font-medium"
                :class="interviewerBadgeClass(iv.role)"
                >{{ iv.role }}</span
              >
              <span>{{ iv.name }}</span>
            </div>
          </div>
        </div>

        <div class="mb-4">
          <h4 class="text-sm font-medium text-gray-500 mb-2">面试结果</h4>
          <div class="flex flex-wrap items-center gap-2">
            <select
              v-model="round.resultUi"
              class="px-3 py-1.5 rounded-full text-sm border-0 cursor-pointer focus:ring-2 focus:ring-primary"
              :class="resultSelectClass(round.resultUi)"
            >
              <option>通过</option>
              <option>拒绝</option>
              <option>待评估</option>
            </select>
            <input
              v-model="round.resultComment"
              type="text"
              class="flex-1 min-w-[12rem] text-sm border border-gray-200 rounded-md px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary"
              placeholder="评价备注…"
            />
          </div>
        </div>

        <div>
          <div class="flex justify-between items-center mb-2 gap-2">
            <h4 class="text-sm font-medium text-gray-500">面试复盘</h4>
            <button
              type="button"
              class="text-primary text-sm flex items-center gap-1 hover:text-blue-800 shrink-0"
              @click="emit('add-question', ri)"
            >
              <i class="fa-solid fa-circle-plus"></i>
              添加题目
            </button>
          </div>
          <div class="space-y-3">
            <div
              v-if="!(round.questions && round.questions.length)"
              class="bg-gray-50 p-6 rounded-lg border border-dashed border-gray-300 flex flex-col items-center justify-center text-center"
            >
              <i class="fa-solid fa-clipboard text-gray-400 text-2xl mb-2"></i>
              <p class="text-sm text-gray-500">暂无面试题目，点击「添加题目」</p>
            </div>

            <div
              v-for="(q, qi) in round.questions || []"
              :key="q.id"
              class="bg-gray-50 p-4 rounded-lg border border-gray-200 hover:shadow-card transition-shadow"
            >
              <div class="flex justify-between items-start gap-2">
                <div class="min-w-0">
                  <div class="flex items-center flex-wrap gap-2">
                    <span class="text-xs font-semibold bg-blue-100 text-blue-800 px-2 py-0.5 rounded">{{ q.label || `题目${qi + 1}` }}</span>
                    <h5 class="font-medium text-gray-800">{{ q.title || "未命名题目" }}</h5>
                  </div>
                  <p v-if="q.questionRecord" class="text-sm text-gray-600 mt-1">{{ q.questionRecord }}</p>
                </div>
                <div class="flex gap-1 shrink-0">
                  <button
                    type="button"
                    class="text-gray-400 hover:text-gray-700 p-1"
                    title="编辑"
                    @click="emit('edit-question', ri, q)"
                  >
                    <i class="fa-solid fa-pencil"></i>
                  </button>
                  <button
                    type="button"
                    class="text-gray-400 hover:text-red-600 p-1"
                    title="删除"
                    @click="emit('remove-question', ri, q.id)"
                  >
                    <i class="fa-solid fa-trash"></i>
                  </button>
                </div>
              </div>
              <div class="mt-3 space-y-2 text-sm">
                <div v-if="q.answerRecord">
                  <span class="font-medium text-gray-700">作答记录：</span>
                  <p class="text-gray-600 mt-0.5">{{ q.answerRecord }}</p>
                </div>
                <div v-if="q.pros">
                  <span class="font-medium text-gray-700">作答优点：</span>
                  <p class="text-green-600 mt-0.5">{{ q.pros }}</p>
                </div>
                <div v-if="q.cons">
                  <span class="font-medium text-gray-700">作答缺点：</span>
                  <p class="text-red-600 mt-0.5">{{ q.cons }}</p>
                </div>
                <div v-if="q.improvementPlan">
                  <span class="font-medium text-gray-700">后续优化：</span>
                  <p class="text-violet-600 mt-0.5">{{ q.improvementPlan }}</p>
                </div>
              </div>
              <div class="mt-2 flex items-center justify-between flex-wrap gap-2 text-xs">
                <div class="flex items-center gap-1">
                  <span class="text-gray-500">难度：</span>
                  <span class="flex gap-0.5">
                    <i
                      v-for="si in 3"
                      :key="`${q.id}-d-${si}`"
                      :class="
                        si <= (q.difficulty || 1)
                          ? 'fa-solid fa-star text-yellow-400'
                          : 'fa-regular fa-star text-gray-300'
                      "
                    ></i>
                  </span>
                </div>
                <div>
                  <span class="text-gray-500 mr-1">分数：</span>
                  <span class="font-semibold text-primary">{{ q.score ?? 0 }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
