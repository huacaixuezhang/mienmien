<script setup>
import { assessmentToResultUi, defaultInterviewConclusion } from "../utils/interviewV3.js";
import { BUILTIN_INTERVIEWER_ROLE_OPTIONS } from "../constants/interviewerRolePresets.js";

const props = defineProps({
  jobProfile: { type: Object, required: true },
  rounds: { type: Array, required: true },
  /** 内置 + 自定义面试官风格下拉选项 { value, label }[] */
  styleOptions: { type: Array, default: () => [] },
  /** 用户在后端维护的面试官角色卡片（用于提示与悬浮说明） */
  interviewerRoleCatalog: { type: Array, default: () => [] },
  /** 为 false 时不渲染「岗位信息」整块（仅保留面试流程），用于模拟面试详情只展示绑定岗位摘要时 */
  showJobSection: { type: Boolean, default: true },
  /** 是否展示「开始语音模拟面试」入口（由父级发起麦克风 + 转写 + AI 面试官） */
  showVideoStartButton: { type: Boolean, default: false },
  /** 业务 summary.meta.videoInterviewMeta：终局总评与会话 id（只读展示） */
  recordVideoInterviewMeta: { type: Object, default: null }
});

const emit = defineEmits([
  "add-round",
  "edit-round",
  "remove-round",
  "add-question",
  "edit-question",
  "remove-question",
  "start-video-interview"
]);

function interviewerBadgeClass(role) {
  const s = String(role || "");
  const u = s.toLowerCase();
  if (s === "HR" || u === "hr") return "bg-blue-500";
  if (s === "P" || s === "Peer" || u === "peer") return "bg-emerald-500";
  if (u === "ld") return "bg-amber-600";
  if (s === "+1" || s.includes("+1")) return "bg-violet-500";
  if (s === "+2" || s.includes("+2")) return "bg-red-500";
  return "bg-gray-500";
}

function interviewerRoleTooltip(role) {
  const code = String(role || "").trim();
  if (!code) return "";
  const fromApi = (props.interviewerRoleCatalog || []).find(
    (x) => String(x?.roleCode || "").toLowerCase() === code.toLowerCase()
  );
  if (fromApi) {
    const bits = [
      fromApi.roleName,
      fromApi.interviewContent ? `内容：${String(fromApi.interviewContent).slice(0, 160)}` : "",
      fromApi.focusPoints ? `侧重：${String(fromApi.focusPoints).slice(0, 160)}` : ""
    ].filter(Boolean);
    return bits.join("\n");
  }
  const presetKey = code.toLowerCase() === "p" ? "peer" : code.toLowerCase();
  const preset = BUILTIN_INTERVIEWER_ROLE_OPTIONS.find((x) => x.code.toLowerCase() === presetKey);
  if (!preset) return "";
  const bits = [
    preset.name,
    preset.interviewContent ? `内容：${String(preset.interviewContent).slice(0, 160)}` : "",
    preset.focusPoints ? `侧重：${String(preset.focusPoints).slice(0, 160)}` : ""
  ].filter(Boolean);
  return bits.join("\n");
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
  if (ui === "拒绝" || ui === "未通过") return "bg-red-100 text-red-800";
  return "bg-amber-100 text-amber-800";
}

function ensureRoundConclusion(round) {
  if (!round.interviewConclusion || typeof round.interviewConclusion !== "object") {
    round.interviewConclusion = defaultInterviewConclusion();
  }
  return round.interviewConclusion;
}

function onRoundConclusionAssessmentChange(round) {
  ensureRoundConclusion(round);
  round.resultUi = assessmentToResultUi(round.interviewConclusion.resultAssessment);
}

function onNextRoundStatusChange(round, status) {
  ensureRoundConclusion(round);
  const s = status === "yes" || status === "pending" || status === "no" ? status : "no";
  round.interviewConclusion.nextRoundStatus = s;
  round.interviewConclusion.hasNextRound = s === "yes";
  if (s === "no") {
    round.interviewConclusion.nextRoundAdvice = "";
  }
}

/** 语音题：在标题旁补充「第几次练习 + 会话 id」，便于同轮多场区分与排查。 */
function voiceTurnSessionSubtitle(q) {
  if (q?.source !== "video_turn") return "";
  const sid = String(q?.videoSessionId || "").trim();
  if (!sid) return "";
  const ord = Number(q?.videoSessionOrdinal);
  const ordPart =
    Number.isFinite(ord) && ord > 0
      ? `当轮第 ${ord} 次语音练习`
      : "旧数据未记录场次序号";
  const mid = sid.length > 36 ? `${sid.slice(0, 16)}…${sid.slice(-12)}` : sid;
  return `${ordPart} · 会话 ${mid}`;
}
</script>

<template>
  <section v-if="showJobSection" class="bg-white rounded-lg shadow-card p-6">
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
    <p class="px-6 py-2 text-xs text-gray-500 border-b border-gray-100 bg-gray-50/60 leading-relaxed">
      多轮面试按顺序展示。若语音场次对应「第 2 轮及以后」而尚未在左侧添加过轮次，系统会按会话轮次自动补齐空轮卡片，便于展示各轮语音复盘；也可提前点击「添加面试」维护多轮。
    </p>

    <div
      v-if="recordVideoInterviewMeta && (recordVideoInterviewMeta.evaluation || recordVideoInterviewMeta.sessionId)"
      class="px-6 py-4 border-b border-emerald-100 bg-emerald-50/50 text-sm"
    >
      <div class="font-semibold text-emerald-900 flex items-center gap-2 mb-2">
        <i class="fa-solid fa-microphone"></i>
        语音模拟面试总评
      </div>
      <p class="text-xs text-emerald-800/90 mb-2 leading-relaxed">
        以下总评基于本轮语音模拟问答生成，与各轮下方「面试结论」可对照使用。
      </p>
      <p v-if="recordVideoInterviewMeta.sessionId" class="text-xs text-gray-600 mb-2 break-all">
        会话 ID：<code class="bg-white/90 px-1.5 py-0.5 rounded text-gray-800">{{ recordVideoInterviewMeta.sessionId }}</code>
      </p>
      <p v-if="recordVideoInterviewMeta.evaluation" class="whitespace-pre-wrap leading-relaxed text-gray-800">
        {{ recordVideoInterviewMeta.evaluation }}
      </p>
      <p v-else class="text-xs text-gray-500">暂无总评正文；结束会话后由服务端生成，若长期为空请检查 Consumer 模型配置。</p>
    </div>

    <div class="divide-y divide-gray-200">
      <div
        v-for="(round, ri) in rounds"
        :key="`${round.id || 'round'}-${ri}`"
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
              v-if="showVideoStartButton"
              type="button"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium bg-emerald-600 hover:bg-emerald-700 text-white shadow-sm shrink-0"
              title="使用当前轮配置开始 AI 语音模拟面试（不采集视频）"
              @click="emit('start-video-interview', ri)"
            >
              <i class="fa-solid fa-microphone"></i>
              开始语音模拟面试
            </button>
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
          <h4 class="text-sm font-medium text-gray-500 mb-2">面试官风格（AI 语音模拟）</h4>
          <select
            v-model="round.interviewerStyleKey"
            class="w-full max-w-md px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary bg-white"
          >
            <option v-for="opt in styleOptions" :key="`${round.id}-${opt.value}`" :value="opt.value">{{ opt.label }}</option>
          </select>
          <p class="text-xs text-gray-400 mt-1.5 leading-relaxed">
            自定义风格在侧栏 <strong>面试管理 → 面试官风格管理</strong> 中维护；「面试官信息」里的角色代号可在
            <strong>面试官角色管理</strong> 中配置面试内容与侧重点，便于团队对齐。
            内置四类风格为通用话术模版，正文中的简历、岗位、JD 等占位由调用方在对接 AI 语音模拟面试前替换为实际内容。
          </p>
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
                :title="interviewerRoleTooltip(iv.role)"
                >{{ iv.role }}</span
              >
              <span>{{ iv.name }}</span>
            </div>
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
                    <span
                      class="text-xs font-semibold bg-blue-100 text-blue-800 px-2 py-0.5 rounded"
                      :title="q.source === 'video_turn' && q.videoSessionId ? String(q.videoSessionId) : ''"
                      >{{ q.label || `题目${qi + 1}` }}</span
                    >
                    <span
                      v-if="q.source === 'video_turn'"
                      class="text-[10px] font-medium uppercase tracking-wide bg-emerald-100 text-emerald-800 px-2 py-0.5 rounded"
                      :title="q.videoSessionId ? String(q.videoSessionId) : ''"
                      >语音</span
                    >
                    <h5 class="font-medium text-gray-800">{{ q.title || "未命名题目" }}</h5>
                  </div>
                  <p
                    v-if="voiceTurnSessionSubtitle(q)"
                    class="text-[11px] text-slate-500 mt-1 leading-snug"
                  >
                    {{ voiceTurnSessionSubtitle(q) }}
                  </p>
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

        <div
          v-if="ensureRoundConclusion(round)"
          class="mt-6 pt-5 border-t border-slate-200 bg-slate-50/90 rounded-lg p-4 text-sm space-y-3"
        >
          <h4 class="text-sm font-semibold text-gray-900 flex items-center gap-2">
            <i class="fa-solid fa-clipboard-check text-primary"></i>
            本轮面试结论
          </h4>
          <p class="text-xs text-gray-500 leading-relaxed">
            仅针对本栏「{{ round.roundTitle }}」；与上方复盘同属该轮。点击详情「保存」写入 summary；列表中的总结果仍取最后一轮的面试结果评估。
          </p>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
            <label class="block space-y-1.5 min-w-0">
              <span class="text-xs font-medium text-gray-600">本轮面试结果评估</span>
              <select
                v-model="round.interviewConclusion.resultAssessment"
                class="w-full max-w-xs px-3 py-2 border border-gray-300 rounded-md text-sm bg-white focus:ring-2 focus:ring-primary"
                :class="resultSelectClass(round.interviewConclusion.resultAssessment)"
                @change="onRoundConclusionAssessmentChange(round)"
              >
                <option>通过</option>
                <option>未通过</option>
                <option>待评估</option>
              </select>
            </label>
            <label class="block space-y-1.5 min-w-0">
              <span class="text-xs font-medium text-gray-600">面试综合分数（0–100）</span>
              <input
                v-model.number="round.interviewConclusion.overallScore"
                type="number"
                min="0"
                max="100"
                class="w-full max-w-xs px-3 py-2 border border-gray-300 rounded-md text-sm bg-white"
              />
            </label>
          </div>
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-gray-600">面试评语</span>
            <textarea
              v-model="round.interviewConclusion.comment"
              rows="3"
              class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm bg-white resize-y"
              placeholder="综合表现、关键优劣与录用建议等"
            />
          </label>
          <label class="block space-y-1.5">
            <span class="text-xs font-medium text-gray-600">对面试者的画像</span>
            <textarea
              v-model="round.interviewConclusion.candidatePortrait"
              rows="2"
              class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm bg-white resize-y"
              placeholder="能力特点、沟通风格、潜力与风险等"
            />
          </label>
          <div class="space-y-2">
            <span class="text-xs font-medium text-gray-600">是否有下一轮面试</span>
            <div
              class="flex flex-col sm:flex-row sm:flex-wrap gap-2 sm:gap-6 text-sm text-gray-800"
              role="radiogroup"
              :aria-label="'是否有下一轮-' + (round.id || ri)"
            >
              <label class="inline-flex items-center gap-2 cursor-pointer select-none">
                <input
                  type="radio"
                  class="text-primary border-gray-300 focus:ring-primary"
                  :name="'next-round-' + (round.id || ri)"
                  value="no"
                  :checked="round.interviewConclusion.nextRoundStatus === 'no'"
                  @change="onNextRoundStatusChange(round, 'no')"
                />
                <span>否</span>
              </label>
              <label class="inline-flex items-center gap-2 cursor-pointer select-none">
                <input
                  type="radio"
                  class="text-primary border-gray-300 focus:ring-primary"
                  :name="'next-round-' + (round.id || ri)"
                  value="pending"
                  :checked="round.interviewConclusion.nextRoundStatus === 'pending'"
                  @change="onNextRoundStatusChange(round, 'pending')"
                />
                <span>待定</span>
              </label>
              <label class="inline-flex items-center gap-2 cursor-pointer select-none">
                <input
                  type="radio"
                  class="text-primary border-gray-300 focus:ring-primary"
                  :name="'next-round-' + (round.id || ri)"
                  value="yes"
                  :checked="round.interviewConclusion.nextRoundStatus === 'yes'"
                  @change="onNextRoundStatusChange(round, 'yes')"
                />
                <span>是</span>
              </label>
            </div>
          </div>
          <label
            v-if="
              round.interviewConclusion.nextRoundStatus === 'yes' ||
              round.interviewConclusion.nextRoundStatus === 'pending'
            "
            class="block space-y-1.5"
          >
            <span class="text-xs font-medium text-gray-600">下轮面试建议</span>
            <textarea
              v-model="round.interviewConclusion.nextRoundAdvice"
              rows="2"
              class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm bg-white resize-y"
              placeholder="可写对候选人或面试官的侧重建议、准备要点等"
            />
          </label>
        </div>
      </div>
    </div>
  </section>
</template>
