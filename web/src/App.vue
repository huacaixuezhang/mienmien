<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import {
  aggregateRoundResults,
  averageQuestionScore,
  defaultQuestion,
  defaultRound,
  firstRoundInterviewType,
  migrateV2ToV3,
  parseInterviewPayload,
  serializeV3
} from "./utils/interviewV3";
import {
  createSpace,
  listSpaces,
  renameSpace,
  recycleSpace,
  restoreSpace,
  listRecycleBinSpaces,
  createResume,
  listResumes,
  createInterview,
  listInterview,
  createJobPosition,
  updateJobPosition,
  listJobPositions,
  closeJobPosition,
  getAnswerBank,
  saveAnswerBank,
  getModelConfig,
  saveModelConfig,
  registerByPhone,
  loginByPhone,
  logoutSession,
  USER_SESSION_STORAGE_KEY
} from "./api";
import InterviewRoundsPanel from "./components/InterviewRoundsPanel.vue";
import {
  JOB_TYPE_OPTIONS,
  decodeJobBaseRange,
  encodeJobBaseRange,
  jobTypeBadgeClass,
  jobTypeLabel
} from "./utils/jobMeta";

/** 侧栏「当前空间」：题库与面试 */
const sidebarSpaceNav = [
  { key: "answer", iconClass: "fa-solid fa-book", label: "标准题库" },
  { key: "mock", iconClass: "fa-solid fa-circle-play", label: "模拟面试" },
  { key: "interview", iconClass: "fa-solid fa-calendar-check", label: "正式面试" }
];

const sidebarResourceNav = [
  { key: "resume", iconClass: "fa-solid fa-file-lines", label: "简历管理" },
  { key: "job", iconClass: "fa-solid fa-briefcase", label: "岗位管理" }
];

/** 侧栏「系统功能」分组 */
const sidebarSystemNav = [
  { key: "dashboard", iconClass: "fa-solid fa-gauge-high", label: "仪表盘" },
  { key: "recycle", iconClass: "fa-solid fa-trash", label: "回收站" },
  { key: "user", iconClass: "fa-solid fa-user", label: "用户管理" }
];

const platformNavOpen = ref(false);

const activeTab = ref("resume");

/** 平台配置父行：在「系统设置」页或展开子菜单时给予弱/强高亮 */
const platformParentButtonClass = computed(() => {
  const onConfig = activeTab.value === "config";
  const open = platformNavOpen.value;
  const base =
    "flex w-full items-center px-3 py-2.5 text-left text-sm rounded-md transition-colors border-l-4";
  if (onConfig) {
    return `${base} border-primary bg-blue-50 font-medium text-primary`;
  }
  if (open) {
    return `${base} border-transparent bg-gray-50 text-gray-800 hover:bg-gray-100`;
  }
  return `${base} border-transparent text-gray-600 hover:bg-gray-100 hover:text-primary`;
});

const platformChevronClass = computed(() => {
  const emphasize = activeTab.value === "config" || platformNavOpen.value;
  return [
    "fa-solid shrink-0 text-xs",
    platformNavOpen.value ? "fa-chevron-up" : "fa-chevron-down",
    emphasize ? "text-primary" : "text-gray-400"
  ].join(" ");
});
const spaces = ref([]);
const recycleBinSpaces = ref([]);
const currentSpaceId = ref("");
const newSpaceName = ref("");
/** 创建空间时从当前空间复制的可选资源 */
const bindSourceResumes = ref([]);
const bindSourceJobs = ref([]);
const newSpaceBindResumeId = ref("");
const newSpaceBindJobIds = ref([]);
const renameSpaceName = ref("");
const addingSpace = ref(false);
const showAddSpaceModal = ref(false);
const showRenameSpaceModal = ref(false);
const renameTargetSpaceId = ref("");

const currentSpace = computed(() => spaces.value.find((x) => x.spaceId === currentSpaceId.value) || null);

const resumeBlocks = reactive([]);
const resumeVersion = ref(1);
const draggingResumeIndex = ref(-1);
const newResumeBlockTitle = ref("");
const deletedBlockBackup = ref(null);
const deletedBlockBackupIndex = ref(-1);
const undoRemainSeconds = ref(0);
let undoTimer = null;

const jobForm = reactive({
  title: "",
  company: "",
  location: "",
  baseRange: ""
});

function defaultAnswerCards() {
  return [];
}
const answerCards = reactive(defaultAnswerCards());
const newAnswerCardTitle = ref("");
const draggingAnswerIndex = ref(-1);

/** 编辑岗位弹窗内：粘贴完整 JD 仅用于一键拆解考点，不单独入库 */
const jobModalJdPaste = ref("");
const jobModalJdAnalyzing = ref(false);

const jobSearchQuery = ref("");
const jobModalOpen = ref(false);
const jobModalMode = ref("add");
const jobModalId = ref("");
const jobModalDirty = ref(false);
const jobModalSaving = ref(false);
/** 各居中弹窗共用：标题栏拖拽位移（同时只会有一个弹窗打开） */
const panelModalOffset = ref({ x: 0, y: 0 });
const panelModalDragging = ref(false);
let panelModalDragSession = null;
const jobModalDraft = reactive({
  title: "",
  company: "",
  location: "",
  jobType: "fulltime",
  description: "",
  jdDetail: "",
  salary: "",
  focusPoints: ""
});
const jobRichEditorRef = ref(null);
const jobDeleteConfirmId = ref("");
const jobDeleteLoading = ref(false);

const jobDetailModalOpen = ref(false);
const jobDetailView = reactive({
  title: "",
  company: "",
  location: "",
  jobType: "fulltime",
  description: "",
  jdDetailHtml: "",
  salary: "",
  focusPoints: ""
});
const toasts = ref([]);
let toastSeq = 0;

/** 正式面试：岗位信息 + 多轮流程（存 MM_INTERVIEW_V3） */
const realJobProfile = reactive({
  title: "",
  company: "",
  location: "",
  jdText: ""
});
const realInterviewRounds = reactive([]);
/** 模拟面试：与正式同结构 */
const mockJobProfile = reactive({
  title: "",
  company: "",
  location: "",
  jdText: ""
});
const mockInterviewRounds = reactive([]);

const addInterviewModalOpen = ref(false);
const addQuestionModalOpen = ref(false);
const interviewModalForMock = ref(false);
const questionModalForMock = ref(false);
const editingRoundIndex = ref(-1);
const questionTargetRoundIndex = ref(0);
const editingQuestionId = ref("");
const interviewDraft = reactive({
  roundTitle: "",
  timeText: "",
  locationMode: "线上",
  category: "技术面",
  interviewers: [{ role: "HR", name: "" }]
});
const questionDraft = reactive({
  label: "",
  title: "",
  questionRecord: "",
  answerRecord: "",
  pros: "",
  cons: "",
  improvementPlan: "",
  standardAnswer: "",
  difficulty: 2,
  score: 85
});

const sidebarOpen = ref(false);

watch(activeTab, (k) => {
  if (k === "config") platformNavOpen.value = true;
});

const resumes = ref([]);
const jobs = ref([]);

const activeJobsList = computed(() =>
  (jobs.value || []).filter((j) => (j.status || "ACTIVE") === "ACTIVE")
);

const filteredJobsList = computed(() => {
  const list = activeJobsList.value;
  const q = jobSearchQuery.value.trim().toLowerCase();
  if (!q) return list;
  return list.filter((j) => {
    const d = decodeJobBaseRange(j.baseRange);
    return [j.title, j.company, j.location, d.description, d.salary, d.focusPoints]
      .join(" ")
      .toLowerCase()
      .includes(q);
  });
});

const interviews = ref([]);
const modelConfig = reactive({
  provider: "aliyun-bailian",
  baseUrl: "",
  apiKey: "",
  modelName: "",
  testPrompt: "请输出一句“连接测试成功”"
});
const testingModelConfig = ref(false);
const modelConfigTestResult = ref("");
const userForm = reactive({
  registerPhone: "",
  registerPassword: "",
  registerConfirmPassword: "",
  loginPhone: "",
  loginPassword: ""
});
const currentUser = ref(null);
const showAuthModal = ref(false);
const authMode = ref("login");
/** 在「用户管理」内使用「使用其他账号」时，不弹全局登录窗，仅在本页展示注册/登录表单 */
const switchAccountInline = ref(false);

function resetTransientDrafts() {
  jobModalJdPaste.value = "";
  realJobProfile.title = "";
  realJobProfile.company = "";
  realJobProfile.location = "";
  realJobProfile.jdText = "";
  realInterviewRounds.splice(0, realInterviewRounds.length);
  mockJobProfile.title = "";
  mockJobProfile.company = "";
  mockJobProfile.location = "";
  mockJobProfile.jdText = "";
  mockInterviewRounds.splice(0, mockInterviewRounds.length);
}

function mergeJobFormIntoProfile(profile) {
  profile.title = profile.title || jobForm.title || "";
  profile.company = profile.company || jobForm.company || "";
  profile.location = profile.location || jobForm.location || "";
}

function hydrateInterviewFromLatestReal() {
  realInterviewRounds.splice(0, realInterviewRounds.length);
  const latestReal = interviews.value.find((x) => x.type === "real");
  const raw = latestReal?.summary || "";
  const parsed = parseInterviewPayload(raw);
  if (parsed.kind === "v3") {
    Object.assign(realJobProfile, parsed.jobProfile);
    parsed.rounds.forEach((r) => realInterviewRounds.push({ ...r, interviewers: r.interviewers.map((x) => ({ ...x })) }));
  } else if (parsed.kind === "v2" && parsed.v2) {
    const migrated = migrateV2ToV3(parsed.v2);
    Object.assign(realJobProfile, migrated.jobProfile);
    mergeJobFormIntoProfile(realJobProfile);
    migrated.rounds.forEach((r) =>
      realInterviewRounds.push({
        ...r,
        interviewers: r.interviewers.map((x) => ({ ...x })),
        questions: r.questions.map((q) => ({ ...q }))
      })
    );
  } else {
    Object.assign(realJobProfile, { title: "", company: "", location: "", jdText: "" });
    mergeJobFormIntoProfile(realJobProfile);
    if (parsed.kind === "plain" && parsed.text) {
      const r0 = defaultRound(0);
      r0.resultComment = String(parsed.text).slice(0, 2000);
      realInterviewRounds.push(r0);
    }
  }
  if (!realInterviewRounds.length) {
    realInterviewRounds.push(defaultRound(0));
  }
}

function hydrateMockFromLatestMock() {
  mockInterviewRounds.splice(0, mockInterviewRounds.length);
  const latestMock = interviews.value.find((x) => x.type === "mock");
  const raw = latestMock?.summary || "";
  const parsed = parseInterviewPayload(raw);
  if (parsed.kind === "v3") {
    Object.assign(mockJobProfile, parsed.jobProfile);
    parsed.rounds.forEach((r) => mockInterviewRounds.push({ ...r, interviewers: r.interviewers.map((x) => ({ ...x })) }));
  } else if (parsed.kind === "v2" && parsed.v2) {
    const migrated = migrateV2ToV3(parsed.v2);
    Object.assign(mockJobProfile, migrated.jobProfile);
    mergeJobFormIntoProfile(mockJobProfile);
    migrated.rounds.forEach((r) =>
      mockInterviewRounds.push({
        ...r,
        interviewers: r.interviewers.map((x) => ({ ...x })),
        questions: r.questions.map((q) => ({ ...q }))
      })
    );
  } else {
    Object.assign(mockJobProfile, { title: "", company: "", location: "", jdText: "" });
    mergeJobFormIntoProfile(mockJobProfile);
    if (parsed.kind === "plain" && parsed.text) {
      const r0 = defaultRound(0);
      r0.resultComment = String(parsed.text).slice(0, 2000);
      mockInterviewRounds.push(r0);
    }
  }
  if (!mockInterviewRounds.length) {
    mockInterviewRounds.push(defaultRound(0));
  }
}

function resetInterviewDraft() {
  interviewDraft.roundTitle = "";
  interviewDraft.timeText = "";
  interviewDraft.locationMode = "线上";
  interviewDraft.category = "技术面";
  interviewDraft.interviewers = [{ role: "HR", name: "" }];
}

function resetQuestionDraft() {
  questionDraft.label = "";
  questionDraft.title = "";
  questionDraft.questionRecord = "";
  questionDraft.answerRecord = "";
  questionDraft.pros = "";
  questionDraft.cons = "";
  questionDraft.improvementPlan = "";
  questionDraft.standardAnswer = "";
  questionDraft.difficulty = 2;
  questionDraft.score = 85;
}

function openAddInterviewModal(forMock) {
  resetPanelModalDrag();
  interviewModalForMock.value = forMock;
  editingRoundIndex.value = -1;
  resetInterviewDraft();
  addInterviewModalOpen.value = true;
}

function openEditInterviewModal(forMock, roundIndex) {
  resetPanelModalDrag();
  interviewModalForMock.value = forMock;
  editingRoundIndex.value = roundIndex;
  const rounds = forMock ? mockInterviewRounds : realInterviewRounds;
  const r = rounds[roundIndex];
  if (!r) return;
  interviewDraft.roundTitle = r.roundTitle || "";
  interviewDraft.timeText = r.timeText || "";
  interviewDraft.locationMode = r.locationMode || "线上";
  interviewDraft.category = r.category || "技术面";
  interviewDraft.interviewers =
    (r.interviewers && r.interviewers.length ? r.interviewers : [{ role: "HR", name: "" }]).map((x) => ({
      role: x.role || "HR",
      name: x.name || ""
    }));
  addInterviewModalOpen.value = true;
}

function closeAddInterviewModal() {
  resetPanelModalDrag();
  addInterviewModalOpen.value = false;
  editingRoundIndex.value = -1;
}

function submitInterviewModal() {
  const title = (interviewDraft.roundTitle || "").trim();
  if (!title) {
    alert("请填写面试轮次名称");
    return;
  }
  const rounds = interviewModalForMock.value ? mockInterviewRounds : realInterviewRounds;
  const row = defaultRound(rounds.length);
  row.roundTitle = title;
  row.timeText = interviewDraft.timeText || "";
  row.locationMode = interviewDraft.locationMode || "线上";
  row.category = interviewDraft.category || "技术面";
  row.interviewers = interviewDraft.interviewers
    .map((x) => ({ role: x.role || "HR", name: (x.name || "").trim() }))
    .filter((x) => x.name);
  if (!row.interviewers.length) {
    row.interviewers = [{ role: "HR", name: "未设置" }];
  }
  if (editingRoundIndex.value >= 0) {
    const prev = rounds[editingRoundIndex.value];
    row.id = prev.id;
    row.questions = prev.questions || [];
    row.resultUi = prev.resultUi;
    row.resultComment = prev.resultComment;
    rounds.splice(editingRoundIndex.value, 1, row);
  } else {
    rounds.push(row);
  }
  closeAddInterviewModal();
}

function addInterviewerRow() {
  interviewDraft.interviewers.push({ role: "P", name: "" });
}

function removeInterviewerRow(index) {
  if (interviewDraft.interviewers.length <= 1) {
    alert("至少保留一位面试官");
    return;
  }
  interviewDraft.interviewers.splice(index, 1);
}

function openAddQuestionModal(forMock, roundIndex) {
  resetPanelModalDrag();
  questionModalForMock.value = forMock;
  questionTargetRoundIndex.value = roundIndex;
  editingQuestionId.value = "";
  resetQuestionDraft();
  const rounds = forMock ? mockInterviewRounds : realInterviewRounds;
  const r = rounds[roundIndex];
  const n = (r?.questions || []).length;
  questionDraft.label = `题目${n + 1}`;
  addQuestionModalOpen.value = true;
}

function openEditQuestionModal(forMock, roundIndex, q) {
  resetPanelModalDrag();
  questionModalForMock.value = forMock;
  questionTargetRoundIndex.value = roundIndex;
  editingQuestionId.value = q.id;
  questionDraft.label = q.label || "";
  questionDraft.title = q.title || "";
  questionDraft.questionRecord = q.questionRecord || "";
  questionDraft.answerRecord = q.answerRecord || "";
  questionDraft.pros = q.pros || "";
  questionDraft.cons = q.cons || "";
  questionDraft.improvementPlan = q.improvementPlan || "";
  questionDraft.standardAnswer = q.standardAnswer || "";
  questionDraft.difficulty = q.difficulty || 2;
  questionDraft.score = q.score ?? 85;
  addQuestionModalOpen.value = true;
}

function closeAddQuestionModal() {
  resetPanelModalDrag();
  addQuestionModalOpen.value = false;
  editingQuestionId.value = "";
}

function submitQuestionModal() {
  if (!(questionDraft.questionRecord || "").trim() && !(questionDraft.title || "").trim()) {
    alert("请填写题目标题或原题记录");
    return;
  }
  const rounds = questionModalForMock.value ? mockInterviewRounds : realInterviewRounds;
  const r = rounds[questionTargetRoundIndex.value];
  if (!r) return;
  if (!r.questions) r.questions = [];
  if (editingQuestionId.value) {
    const idx = r.questions.findIndex((x) => x.id === editingQuestionId.value);
    if (idx >= 0) {
      const prev = r.questions[idx];
      Object.assign(prev, {
        label: (questionDraft.label || "").trim() || prev.label,
        title: questionDraft.title || "",
        questionRecord: questionDraft.questionRecord || "",
        answerRecord: questionDraft.answerRecord || "",
        pros: questionDraft.pros || "",
        cons: questionDraft.cons || "",
        improvementPlan: questionDraft.improvementPlan || "",
        standardAnswer: questionDraft.standardAnswer || "",
        difficulty: questionDraft.difficulty,
        score: Number(questionDraft.score) || 0
      });
    }
  } else {
    const q = defaultQuestion(r.questions.length);
    q.label = (questionDraft.label || "").trim() || q.label;
    q.title = questionDraft.title || "";
    q.questionRecord = questionDraft.questionRecord || "";
    q.answerRecord = questionDraft.answerRecord || "";
    q.pros = questionDraft.pros || "";
    q.cons = questionDraft.cons || "";
    q.improvementPlan = questionDraft.improvementPlan || "";
    q.standardAnswer = questionDraft.standardAnswer || "";
    q.difficulty = questionDraft.difficulty;
    q.score = Number(questionDraft.score) || 0;
    r.questions.push(q);
  }
  closeAddQuestionModal();
}

function removeInterviewRound(forMock, index) {
  if (!confirm("确认删除该轮面试？")) return;
  const rounds = forMock ? mockInterviewRounds : realInterviewRounds;
  rounds.splice(index, 1);
  if (!rounds.length) {
    rounds.push(defaultRound(0));
  }
}

function removeQuestionFromRound(forMock, roundIndex, qid) {
  if (!confirm("确认删除该题目？")) return;
  const rounds = forMock ? mockInterviewRounds : realInterviewRounds;
  const r = rounds[roundIndex];
  if (!r?.questions) return;
  const idx = r.questions.findIndex((x) => x.id === qid);
  if (idx >= 0) r.questions.splice(idx, 1);
}

const dashboardStats = computed(() => {
  const list = interviews.value || [];
  const total = list.length;
  const passed = list.filter((x) => x.result === "passed").length;
  const pending = list.filter((x) => x.result === "pending").length;
  const rate = total ? ((passed / total) * 100).toFixed(1) : "0.0";
  return { total, passed, pending, rate };
});

const recentInterviews = computed(() => (interviews.value || []).slice(0, 8));

function tabTitle(key) {
  const m = {
    dashboard: "仪表盘",
    resume: "简历管理",
    job: "岗位管理",
    answer: "标准题库",
    mock: "模拟面试",
    interview: "正式面试",
    recycle: "回收站",
    config: "系统设置",
    user: "用户管理"
  };
  return m[key] || "";
}

function sidebarNavButtonClass(key, opts = {}) {
  const active = activeTab.value === key;
  const pad = opts.sub ? "pl-2 pr-3" : "px-3";
  const tone = active
    ? "bg-blue-50 text-primary font-medium border-primary"
    : "text-gray-600 border-transparent hover:bg-gray-100 hover:text-primary";
  return `flex w-full items-center gap-0 ${pad} py-2.5 text-left text-sm rounded-md transition-colors border-l-4 ${tone}`;
}

function togglePlatformNav() {
  platformNavOpen.value = !platformNavOpen.value;
}

function showToast(message, type = "info") {
  const id = ++toastSeq;
  toasts.value.push({ id, message, type });
  setTimeout(() => {
    toasts.value = toasts.value.filter((t) => t.id !== id);
  }, 3200);
}

function syncJobFormFromFirstJob() {
  const j = jobs.value.find((x) => (x.status || "ACTIVE") === "ACTIVE");
  if (!j) {
    jobForm.title = "";
    jobForm.company = "";
    jobForm.location = "";
    jobForm.baseRange = "";
    return;
  }
  jobForm.title = j.title || "";
  jobForm.company = j.company || "";
  jobForm.location = j.location || "";
  jobForm.baseRange = j.baseRange || "";
}

function buildJobBaseRangeFromDraft() {
  const jdHtml = jobRichEditorRef.value ? jobRichEditorRef.value.innerHTML : jobModalDraft.jdDetail || "";
  let encoded = encodeJobBaseRange({
    jobType: jobModalDraft.jobType,
    description: jobModalDraft.description,
    jdDetail: jdHtml,
    salary: jobModalDraft.salary,
    focusPoints: jobModalDraft.focusPoints
  });
  const max = 7900;
  if (encoded.length <= max) return encoded;
  try {
    const o = JSON.parse(encoded);
    while (JSON.stringify(o).length > max && o.jdDetail) {
      o.jdDetail = o.jdDetail.slice(0, Math.max(0, o.jdDetail.length - 400));
    }
    while (JSON.stringify(o).length > max && o.focusPoints) {
      o.focusPoints = o.focusPoints.slice(0, Math.max(0, o.focusPoints.length - 120));
    }
    let s = JSON.stringify(o);
    if (s.length > max) {
      o.jdDetail = "";
      o.description = (o.description || "").slice(0, 800);
      o.focusPoints = (o.focusPoints || "").slice(0, 400);
      s = JSON.stringify(o);
    }
    encoded = s.length > max ? s.slice(0, max) : s;
  } catch {
    encoded = encoded.slice(0, max);
  }
  return encoded;
}

function openJobDetailModal(row) {
  if (!row) return;
  resetPanelModalDrag();
  const d = decodeJobBaseRange(row.baseRange);
  jobDetailView.title = row.title || "";
  jobDetailView.company = row.company || "";
  jobDetailView.location = row.location || "";
  jobDetailView.jobType = d.jobType || "fulltime";
  jobDetailView.description = (d.description || "").trim() || "—";
  jobDetailView.jdDetailHtml = d.jdDetail || "";
  jobDetailView.salary = (d.salary || "").trim() || "—";
  jobDetailView.focusPoints = (d.focusPoints || "").trim();
  jobDetailModalOpen.value = true;
}

function closeJobDetailModal() {
  resetPanelModalDrag();
  jobDetailModalOpen.value = false;
}

function openAddJobModal() {
  resetPanelModalDrag();
  if (jobDetailModalOpen.value) {
    jobDetailModalOpen.value = false;
  }
  jobModalMode.value = "add";
  jobModalId.value = "";
  jobModalDirty.value = false;
  jobModalJdPaste.value = "";
  Object.assign(jobModalDraft, {
    title: "",
    company: "",
    location: "",
    jobType: "fulltime",
    description: "",
    jdDetail: "",
    salary: "",
    focusPoints: ""
  });
  jobModalOpen.value = true;
}

function openEditJobModal(row) {
  if (!row) return;
  resetPanelModalDrag();
  if (jobDetailModalOpen.value) {
    jobDetailModalOpen.value = false;
  }
  jobModalMode.value = "edit";
  jobModalId.value = row.positionId || "";
  const d = decodeJobBaseRange(row.baseRange);
  jobModalJdPaste.value = "";
  Object.assign(jobModalDraft, {
    title: row.title || "",
    company: row.company || "",
    location: row.location || "",
    jobType: d.jobType,
    description: d.description,
    jdDetail: d.jdDetail,
    salary: d.salary,
    focusPoints: d.focusPoints || ""
  });
  jobModalDirty.value = false;
  jobModalOpen.value = true;
  nextTick(() => {
    if (jobModalMode.value === "edit" && jobRichEditorRef.value) {
      jobRichEditorRef.value.innerHTML = jobModalDraft.jdDetail || "";
    }
  });
}

function jobModalMarkDirty() {
  jobModalDirty.value = true;
}

function removePanelModalDragListeners() {
  document.removeEventListener("pointermove", onPanelModalPointerMove);
  document.removeEventListener("pointerup", onPanelModalPointerUp);
  panelModalDragSession = null;
  panelModalDragging.value = false;
}

function onPanelModalPointerMove(e) {
  if (!panelModalDragSession) return;
  const dx = e.clientX - panelModalDragSession.px;
  const dy = e.clientY - panelModalDragSession.py;
  const max = 280;
  panelModalOffset.value = {
    x: Math.max(-max, Math.min(max, panelModalDragSession.ox + dx)),
    y: Math.max(-max, Math.min(max, panelModalDragSession.oy + dy))
  };
}

function onPanelModalPointerUp() {
  removePanelModalDragListeners();
}

function resetPanelModalDrag() {
  removePanelModalDragListeners();
  panelModalOffset.value = { x: 0, y: 0 };
}

function onPanelModalHeaderPointerDown(e) {
  if (e.button !== 0 || e.target.closest("button") || e.target.closest("a")) return;
  panelModalDragging.value = true;
  panelModalDragSession = {
    px: e.clientX,
    py: e.clientY,
    ox: panelModalOffset.value.x,
    oy: panelModalOffset.value.y
  };
  document.addEventListener("pointermove", onPanelModalPointerMove);
  document.addEventListener("pointerup", onPanelModalPointerUp, { once: true });
}

function focusRichEditor() {
  jobRichEditorRef.value?.focus();
}

function rtCommand(cmd, value = null) {
  focusRichEditor();
  try {
    document.execCommand(cmd, false, value);
  } catch {
    /* ignore */
  }
  jobModalMarkDirty();
}

function rtInsertLink() {
  const url = window.prompt("请输入链接地址", "https://");
  if (url) rtCommand("createLink", url);
}

async function requestCloseJobModal() {
  if (!jobModalOpen.value) return;
  if (!jobModalDirty.value) {
    closeJobModal();
    return;
  }
  if (confirm("有未保存的更改，是否保存后关闭？")) {
    await submitJobModal();
    return;
  }
  if (confirm("确定不保存并关闭？")) {
    closeJobModal();
  }
}

function closeJobModal() {
  resetPanelModalDrag();
  jobModalOpen.value = false;
  jobModalDirty.value = false;
}

async function submitJobModal() {
  if (!currentSpaceId.value) {
    showToast("请先选择工作空间", "error");
    return;
  }
  const title = (jobModalDraft.title || "").trim();
  const company = (jobModalDraft.company || "").trim();
  const location = (jobModalDraft.location || "").trim();
  if (!title || !company || !location) {
    showToast("请填写岗位名称、所属公司与工作地点", "warning");
    return;
  }
  if (jobModalSaving.value) return;
  jobModalSaving.value = true;
  const baseRange = buildJobBaseRangeFromDraft();
  try {
    if (jobModalMode.value === "edit" && jobModalId.value) {
      await updateJobPosition(jobModalId.value, { title, company, location, baseRange });
      showToast("岗位已更新", "success");
    } else {
      await createJobPosition({
        spaceId: currentSpaceId.value,
        title,
        company,
        location,
        baseRange
      });
      showToast("岗位已创建", "success");
    }
    jobs.value = await listJobPositions(currentSpaceId.value);
    syncJobFormFromFirstJob();
    closeJobModal();
  } catch (e) {
    showToast(e?.message || "保存失败", "error");
  } finally {
    jobModalSaving.value = false;
  }
}

function formatJobDate(iso) {
  if (!iso || typeof iso !== "string") return "—";
  return iso.split("T")[0] || "—";
}

function openImportJd() {
  const list = activeJobsList.value;
  if (list.length === 0) {
    showToast("请先添加岗位，再在「编辑岗位」中粘贴 JD 或使用一键拆解考点。", "info");
    return;
  }
  if (list.length === 1) {
    showToast("已打开编辑：请在「JD 详细内容」或上方拆解粘贴区粘贴完整 JD 后保存。", "info");
    openEditJobModal(list[0]);
    return;
  }
  showToast("请打开对应岗位的「编辑」，在 JD 详细内容或拆解粘贴区粘贴完整 JD 后保存。", "info");
}

function requestDeleteJob(row) {
  if (!row?.positionId) return;
  jobDeleteConfirmId.value = row.positionId;
}

function cancelDeleteJob() {
  jobDeleteConfirmId.value = "";
}

async function confirmDeleteJob() {
  const id = jobDeleteConfirmId.value;
  if (!id || jobDeleteLoading.value) return;
  jobDeleteLoading.value = true;
  try {
    await closeJobPosition(id);
    jobs.value = await listJobPositions(currentSpaceId.value);
    syncJobFormFromFirstJob();
    showToast("岗位已删除", "success");
  } catch (e) {
    showToast(e?.message || "删除失败", "error");
  } finally {
    jobDeleteLoading.value = false;
    jobDeleteConfirmId.value = "";
  }
}

async function refreshSpaces() {
  spaces.value = await listSpaces();
  recycleBinSpaces.value = await listRecycleBinSpaces();
  if (!currentSpaceId.value && spaces.value.length > 0) {
    currentSpaceId.value = spaces.value[0].spaceId;
  }
}

function resumeBindLabel(row) {
  if (!row) return "简历";
  const raw = (row.content || "").trim();
  if (!raw) return `简历 · v${row.version || 1}`;
  const first = raw.split(/\n/)[0].replace(/^【|】$/g, "").trim();
  return first.length > 40 ? `${first.slice(0, 40)}…` : first || `简历 · v${row.version || 1}`;
}

function jobBindLabel(row) {
  if (!row) return "";
  const parts = [row.title, row.company].filter(Boolean);
  return parts.join(" · ") || row.title || "岗位";
}

function toggleNewSpaceBindJob(positionId) {
  const i = newSpaceBindJobIds.value.indexOf(positionId);
  if (i >= 0) {
    newSpaceBindJobIds.value.splice(i, 1);
  } else {
    newSpaceBindJobIds.value.push(positionId);
  }
}

async function loadBindSourceResources() {
  bindSourceResumes.value = [];
  bindSourceJobs.value = [];
  const src = currentSpaceId.value;
  if (!src || !currentUser.value) return;
  try {
    bindSourceResumes.value = await listResumes(src);
    const list = await listJobPositions(src);
    bindSourceJobs.value = (list || []).filter((j) => (j.status || "ACTIVE") === "ACTIVE");
  } catch {
    /* 列表失败时仍可仅创建空空间 */
  }
}

async function addSpace() {
  const name = newSpaceName.value.trim();
  if (!name) {
    showToast("请输入空间名称", "warning");
    return;
  }
  if (addingSpace.value) return;
  const sourceSpace = currentSpaceId.value;
  addingSpace.value = true;
  try {
    const created = await createSpace({ name });
    const newId = created?.spaceId;
    if (!newId) {
      throw new Error("创建空间未返回 spaceId");
    }
    if (newSpaceBindResumeId.value && sourceSpace) {
      const r = bindSourceResumes.value.find((x) => x.resumeId === newSpaceBindResumeId.value);
      if (r) {
        await createResume({
          spaceId: newId,
          content: r.content || "",
          version: 1
        });
      }
    }
    for (const jid of [...newSpaceBindJobIds.value]) {
      const j = bindSourceJobs.value.find((x) => x.positionId === jid);
      if (j) {
        await createJobPosition({
          spaceId: newId,
          title: (j.title || "").trim() || "未命名岗位",
          company: j.company || "",
          location: j.location || "",
          baseRange: j.baseRange || ""
        });
      }
    }
    newSpaceName.value = "";
    newSpaceBindResumeId.value = "";
    newSpaceBindJobIds.value = [];
    resetPanelModalDrag();
    showAddSpaceModal.value = false;
    await refreshSpaces();
    currentSpaceId.value = newId;
    resetTransientDrafts();
    await loadSpaceData();
    showToast("空间已创建", "success");
  } catch (e) {
    showToast(e?.message || "创建失败", "error");
  } finally {
    addingSpace.value = false;
  }
}

async function doRenameSpace() {
  const targetId = renameTargetSpaceId.value || currentSpaceId.value;
  if (!targetId) return;
  const name = renameSpaceName.value.trim();
  if (!name) {
    alert("请输入新的空间名称");
    return;
  }
  await renameSpace(targetId, { name });
  renameSpaceName.value = "";
  renameTargetSpaceId.value = "";
  resetPanelModalDrag();
  showRenameSpaceModal.value = false;
  await refreshSpaces();
}

async function openAddSpaceModal() {
  resetPanelModalDrag();
  newSpaceName.value = "";
  newSpaceBindResumeId.value = "";
  newSpaceBindJobIds.value = [];
  showAddSpaceModal.value = true;
  await loadBindSourceResources();
}

function closeAddSpaceModal() {
  if (addingSpace.value) return;
  resetPanelModalDrag();
  showAddSpaceModal.value = false;
  newSpaceName.value = "";
  newSpaceBindResumeId.value = "";
  newSpaceBindJobIds.value = [];
}

function openRenameSpaceModal() {
  if (!currentSpace.value) {
    alert("请先选择空间");
    return;
  }
  resetPanelModalDrag();
  renameTargetSpaceId.value = currentSpace.value.spaceId;
  renameSpaceName.value = currentSpace.value.name || "";
  showRenameSpaceModal.value = true;
}

function closeRenameSpaceModal() {
  resetPanelModalDrag();
  showRenameSpaceModal.value = false;
  renameTargetSpaceId.value = "";
  renameSpaceName.value = "";
}

function switchSpace(spaceId) {
  currentSpaceId.value = spaceId;
  sidebarOpen.value = false;
  resetTransientDrafts();
  loadSpaceData();
}

async function moveCurrentSpaceToRecycleBin() {
  if (!currentSpaceId.value) return;
  if (!confirm("确认删除当前空间？删除后将进入回收站，30天后自动清除。")) return;
  await recycleSpace(currentSpaceId.value);
  currentSpaceId.value = "";
  await refreshSpaces();
  activeTab.value = "recycle";
  await loadSpaceData();
}

async function restoreFromRecycleBin(spaceId) {
  if (!confirm("确认还原该空间？")) return;
  await restoreSpace(spaceId);
  currentSpaceId.value = spaceId;
  await refreshSpaces();
  await loadSpaceData();
}

function switchTab(key) {
  if (!currentUser.value && key !== "user") {
    resetPanelModalDrag();
    showAuthModal.value = true;
    switchAccountInline.value = false;
    return;
  }
  activeTab.value = key;
  sidebarOpen.value = false;
  loadSpaceData();
}

function openConfigPage() {
  switchTab("config");
}

/** 关闭全局登录弹窗（点击遮罩或关闭按钮）；未登录时仍可点「用户管理」进入登录页 */
function dismissAuthModal() {
  resetPanelModalDrag();
  showAuthModal.value = false;
}

function loadUserSession() {
  currentUser.value = null;
  try {
    const raw = localStorage.getItem(USER_SESSION_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed?.userId && parsed?.phone && parsed?.sessionToken) {
        currentUser.value = {
          userId: parsed.userId,
          phone: parsed.phone,
          sessionToken: parsed.sessionToken
        };
      }
    }
  } catch {
    currentUser.value = null;
  }
  const needAuthModal = !currentUser.value && !switchAccountInline.value;
  if (needAuthModal) {
    resetPanelModalDrag();
  }
  showAuthModal.value = needAuthModal;
  authMode.value = "login";
}

function persistUserSession() {
  if (!currentUser.value) {
    localStorage.removeItem(USER_SESSION_STORAGE_KEY);
    return;
  }
  localStorage.setItem(USER_SESSION_STORAGE_KEY, JSON.stringify(currentUser.value));
}

async function registerUser() {
  if (userForm.registerPassword !== userForm.registerConfirmPassword) {
    alert("两次输入的密码不一致");
    return;
  }
  try {
    const res = await registerByPhone({
      phone: userForm.registerPhone,
      password: userForm.registerPassword
    });
    if (!res?.sessionToken) {
      alert(
        "注册接口未返回 sessionToken，当前 B 端接口需要登录令牌。\n请重新编译并重启 business 服务，并执行 scripts/migrate-mm-business-session.sql 创建会话表后再试。"
      );
      return;
    }
    currentUser.value = {
      userId: res.userId,
      phone: res.phone,
      sessionToken: res.sessionToken
    };
    persistUserSession();
    resetPanelModalDrag();
    showAuthModal.value = false;
    userForm.registerPhone = "";
    userForm.registerPassword = "";
    userForm.registerConfirmPassword = "";
    try {
      await refreshSpaces();
    } catch (e) {
      alert(`账号已创建，但加载空间列表失败：${e?.message || e}\n请确认 business 已重启且会话鉴权与库表一致。`);
      return;
    }
    switchAccountInline.value = false;
    alert("注册成功");
  } catch (e) {
    alert(`注册失败：${e?.message || e}`);
  }
}

async function loginUser() {
  try {
    const res = await loginByPhone({
      phone: userForm.loginPhone,
      password: userForm.loginPassword
    });
    if (!res?.sessionToken) {
      alert(
        "登录接口未返回 sessionToken。\n请重新编译并重启 business，并确认已执行 mm_business_session 相关迁移。"
      );
      return;
    }
    currentUser.value = {
      userId: res.userId,
      phone: res.phone,
      sessionToken: res.sessionToken
    };
    persistUserSession();
    resetPanelModalDrag();
    showAuthModal.value = false;
    userForm.loginPassword = "";
    try {
      await refreshSpaces();
    } catch (e) {
      alert(`已登录，但加载空间列表失败：${e?.message || e}`);
      return;
    }
    switchAccountInline.value = false;
    alert("登录成功");
  } catch (e) {
    alert(`登录失败：${e?.message || e}`);
  }
}

async function logoutUser() {
  try {
    await logoutSession();
  } catch {
    /* 忽略网络错误，仍清理本地态 */
  }
  currentUser.value = null;
  persistUserSession();
  spaces.value = [];
  recycleBinSpaces.value = [];
  currentSpaceId.value = "";
  switchAccountInline.value = false;
  sidebarOpen.value = false;
  resetPanelModalDrag();
  showAuthModal.value = true;
  authMode.value = "login";
}

function maskSessionTail(token) {
  if (!token || typeof token !== "string") return "—";
  const t = token.trim();
  if (t.length <= 6) return "••••••";
  return `…${t.slice(-6)}`;
}

/** 留在用户管理页，仅清会话与本地态，便于在本页用另一手机号注册/登录 */
async function switchToOtherAccount() {
  try {
    await logoutSession();
  } catch {
    /* ignore */
  }
  currentUser.value = null;
  persistUserSession();
  spaces.value = [];
  recycleBinSpaces.value = [];
  currentSpaceId.value = "";
  switchAccountInline.value = true;
  resetPanelModalDrag();
  showAuthModal.value = false;
  activeTab.value = "user";
}

async function loadBailianConfig() {
  if (!currentSpaceId.value) return;
  const config = await getModelConfig(currentSpaceId.value);
  modelConfig.provider = config.provider || "aliyun-bailian";
  modelConfig.baseUrl = config.baseUrl || "";
  modelConfig.apiKey = config.apiKey || "";
  modelConfig.modelName = config.modelName || "";
}

async function saveBailianConfig() {
  if (!currentSpaceId.value) {
    alert("请先选择空间");
    return;
  }
  await saveModelConfig({
    spaceId: currentSpaceId.value,
    provider: modelConfig.provider || "aliyun-bailian",
    baseUrl: modelConfig.baseUrl.trim(),
    apiKey: modelConfig.apiKey.trim(),
    modelName: modelConfig.modelName.trim()
  });
  alert("百炼连接配置已保存到后端");
}

function resolveChatCompletionsUrl(baseUrl) {
  const trimmed = baseUrl.trim().replace(/\/+$/, "");
  if (!trimmed) return "";
  if (trimmed.endsWith("/chat/completions")) return trimmed;
  if (trimmed.endsWith("/v1")) return `${trimmed}/chat/completions`;
  return `${trimmed}/chat/completions`;
}

function ensureBailianConfigReady() {
  const chatUrl = resolveChatCompletionsUrl(modelConfig.baseUrl);
  const apiKey = modelConfig.apiKey.trim();
  const modelName = modelConfig.modelName.trim();
  if (!chatUrl || !apiKey || !modelName) {
    throw new Error("请先在平台配置中完整填写 Base URL / API密钥 / 模型名称");
  }
  return { chatUrl, apiKey, modelName };
}

async function callBailianChat(userPrompt) {
  const { chatUrl, apiKey, modelName } = ensureBailianConfigReady();
  const res = await fetch(chatUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`
    },
    body: JSON.stringify({
      model: modelName,
      stream: false,
      messages: [{ role: "user", content: userPrompt }]
    })
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data?.message || data?.error?.message || `HTTP ${res.status}`);
  }
  return data?.choices?.[0]?.message?.content || "";
}

async function testBailianConfigConnection() {
  if (testingModelConfig.value) return;
  testingModelConfig.value = true;
  modelConfigTestResult.value = "";
  try {
    const answer = await callBailianChat(modelConfig.testPrompt || "连接测试");
    modelConfigTestResult.value = `调用成功：${answer}`;
  } catch (e) {
    modelConfigTestResult.value = `调用失败：${e?.message || "未知错误"}`;
  } finally {
    testingModelConfig.value = false;
  }
}

async function loadSpaceData() {
  if (activeTab.value === "recycle") {
    recycleBinSpaces.value = await listRecycleBinSpaces();
    return;
  }
  if (!currentSpaceId.value) return;
  if (activeTab.value === "dashboard") {
    interviews.value = await listInterview(currentSpaceId.value);
    return;
  }
  if (activeTab.value === "resume") {
    resumes.value = await listResumes(currentSpaceId.value);
    resumeBlocks.splice(0, resumeBlocks.length);
    if (resumes.value[0]?.content) {
      const chunks = resumes.value[0].content.split("\n\n");
      chunks.forEach((chunk, idx) => {
        const lines = chunk.split("\n");
        const titleLine = (lines[0] || "").trim();
        const title = titleLine.startsWith("【") && titleLine.endsWith("】")
          ? titleLine.slice(1, -1)
          : `卡片${idx + 1}`;
        const text = lines.slice(1).join("\n");
        resumeBlocks.push({ title, text });
      });
    }
  }
  if (activeTab.value === "job") {
    jobs.value = await listJobPositions(currentSpaceId.value);
    syncJobFormFromFirstJob();
  }
  if (activeTab.value === "answer") {
    const bank = await getAnswerBank(currentSpaceId.value);
    if (bank.cardsJson) {
      try {
        const parsed = JSON.parse(bank.cardsJson);
        if (Array.isArray(parsed) && parsed.length > 0) {
          answerCards.splice(0, answerCards.length, ...parsed);
        }
      } catch {
        answerCards.splice(0, answerCards.length, ...defaultAnswerCards());
      }
    } else {
      answerCards.splice(0, answerCards.length);
      const legacy = [
        { key: "intro", title: "intro", text: bank.intro || "" },
        { key: "reason", title: "reason", text: bank.reason || "" },
        { key: "strengths", title: "strengths", text: bank.strengths || "" },
        { key: "project", title: "project", text: bank.project || "" },
        { key: "hr", title: "hr", text: bank.hr || "" }
      ].filter((x) => x.text.trim());
      if (legacy.length > 0) {
        answerCards.push(...legacy);
      }
    }
  }
  if (activeTab.value === "mock" || activeTab.value === "interview") {
    interviews.value = await listInterview(currentSpaceId.value);
    jobs.value = await listJobPositions(currentSpaceId.value);
    syncJobFormFromFirstJob();
    if (activeTab.value === "interview") {
      hydrateInterviewFromLatestReal();
      mergeJobFormIntoProfile(realJobProfile);
    }
    if (activeTab.value === "mock") {
      hydrateMockFromLatestMock();
      mergeJobFormIntoProfile(mockJobProfile);
    }
  }
  if (activeTab.value === "config") {
    await loadBailianConfig();
  }
}

async function saveSpaceConfig() {
  if (!currentSpaceId.value) return;
  syncJobFormFromFirstJob();
  if (activeTab.value === "interview") {
    Object.assign(jobForm, {
      title: realJobProfile.title,
      company: realJobProfile.company,
      location: realJobProfile.location
    });
  }
  if (activeTab.value === "mock") {
    Object.assign(jobForm, {
      title: mockJobProfile.title,
      company: mockJobProfile.company,
      location: mockJobProfile.location
    });
  }
  await saveResume();
  await upsertPrimaryJobFromForm();
  await saveAnswer();
}

async function saveResume() {
  if (!currentSpaceId.value) return;
  const content = resumeBlocks.map((b) => `【${b.title}】\n${b.text}`).join("\n\n");
  await createResume({ spaceId: currentSpaceId.value, version: String(resumeVersion.value), content });
  resumes.value = await listResumes(currentSpaceId.value);
}

function onResumeDragStart(index) {
  draggingResumeIndex.value = index;
}

function onResumeDrop(targetIndex) {
  const fromIndex = draggingResumeIndex.value;
  if (fromIndex < 0 || fromIndex === targetIndex) return;
  const item = resumeBlocks[fromIndex];
  resumeBlocks.splice(fromIndex, 1);
  resumeBlocks.splice(targetIndex, 0, item);
  draggingResumeIndex.value = -1;
}

function addResumeBlock() {
  const title = newResumeBlockTitle.value.trim() || `自定义模块${resumeBlocks.length + 1}`;
  resumeBlocks.push({ title, text: "" });
  newResumeBlockTitle.value = "";
}

function normalizeResumeBlockTitle(block, index) {
  const next = (block.title || "").trim();
  block.title = next || `自定义模块${index + 1}`;
}

function removeResumeBlock(index) {
  if (resumeBlocks.length <= 1) {
    alert("至少保留一个简历模块");
    return;
  }
  if (!confirm("确认删除这个简历卡片吗？")) {
    return;
  }
  deletedBlockBackup.value = { ...resumeBlocks[index] };
  deletedBlockBackupIndex.value = index;
  resumeBlocks.splice(index, 1);
  startUndoCountdown();
}

function undoRemoveResumeBlock() {
  if (!deletedBlockBackup.value || deletedBlockBackupIndex.value < 0) return;
  const insertIndex = Math.min(deletedBlockBackupIndex.value, resumeBlocks.length);
  resumeBlocks.splice(insertIndex, 0, deletedBlockBackup.value);
  clearUndoState();
}

function startUndoCountdown() {
  clearUndoTimer();
  undoRemainSeconds.value = 8;
  undoTimer = setInterval(() => {
    undoRemainSeconds.value -= 1;
    if (undoRemainSeconds.value <= 0) {
      clearUndoState();
    }
  }, 1000);
}

function clearUndoTimer() {
  if (undoTimer) {
    clearInterval(undoTimer);
    undoTimer = null;
  }
}

function clearUndoState() {
  clearUndoTimer();
  deletedBlockBackup.value = null;
  deletedBlockBackupIndex.value = -1;
  undoRemainSeconds.value = 0;
}

async function upsertPrimaryJobFromForm() {
  if (!currentSpaceId.value) return;
  const title = (jobForm.title || "").trim();
  if (!title) return;
  const activeList = jobs.value.filter((j) => (j.status || "ACTIVE") === "ACTIVE");
  const first = activeList[0];
  const body = {
    title,
    company: (jobForm.company || "").trim(),
    location: (jobForm.location || "").trim(),
    baseRange: jobForm.baseRange || ""
  };
  if (first?.positionId) {
    await updateJobPosition(first.positionId, body);
  } else {
    await createJobPosition({ spaceId: currentSpaceId.value, ...body });
  }
  jobs.value = await listJobPositions(currentSpaceId.value);
  syncJobFormFromFirstJob();
}

async function saveAnswer() {
  if (!currentSpaceId.value) return;
  const byKey = Object.fromEntries(answerCards.map((c) => [c.key, c.text || ""]));
  await saveAnswerBank({
    spaceId: currentSpaceId.value,
    intro: byKey.intro || "",
    reason: byKey.reason || "",
    strengths: byKey.strengths || "",
    project: byKey.project || "",
    hr: byKey.hr || "",
    cardsJson: JSON.stringify(answerCards)
  });
}

function addAnswerCard() {
  const title = newAnswerCardTitle.value.trim() || `自定义题库${answerCards.length + 1}`;
  answerCards.push({ key: `custom_${Date.now()}`, title, text: "" });
  newAnswerCardTitle.value = "";
}

function normalizeAnswerCardTitle(card, index) {
  const next = (card.title || "").trim();
  card.title = next || `自定义题库${index + 1}`;
}

function removeAnswerCard(index) {
  if (answerCards.length <= 1) {
    alert("至少保留一张题库卡片");
    return;
  }
  if (!confirm("确认删除该题库卡片吗？")) return;
  answerCards.splice(index, 1);
}

function onAnswerDragStart(index) {
  draggingAnswerIndex.value = index;
}

function onAnswerDrop(targetIndex) {
  const fromIndex = draggingAnswerIndex.value;
  if (fromIndex < 0 || fromIndex === targetIndex) return;
  const item = answerCards[fromIndex];
  answerCards.splice(fromIndex, 1);
  answerCards.splice(targetIndex, 0, item);
  draggingAnswerIndex.value = -1;
}

async function runJobModalAnalyzeFocusPoints() {
  if (!jobModalJdPaste.value.trim()) {
    alert("请先在「粘贴完整 JD」中填写内容后再拆解");
    return;
  }
  if (jobModalJdAnalyzing.value) return;
  jobModalJdAnalyzing.value = true;
  try {
    const prompt = [
      "你是面试官助手。请对下面JD做考点拆解。",
      "输出要求：仅输出一行逗号分隔的考点关键词，不要额外解释。",
      `JD内容：${jobModalJdPaste.value}`
    ].join("\n");
    const answer = await callBailianChat(prompt);
    jobModalDraft.focusPoints = (answer || "").trim();
    jobModalMarkDirty();
    showToast("考点已写入「考点关键词」，保存岗位后生效", "success");
  } catch (e) {
    alert(e?.message || "百炼调用失败");
  } finally {
    jobModalJdAnalyzing.value = false;
  }
}

async function saveMock() {
  if (!currentSpaceId.value) return;
  Object.assign(jobForm, {
    title: mockJobProfile.title || jobForm.title,
    company: mockJobProfile.company || jobForm.company,
    location: mockJobProfile.location || jobForm.location
  });
  await upsertPrimaryJobFromForm();
  const roundNum = Math.max(1, mockInterviewRounds.length);
  const summary = serializeV3(mockJobProfile, mockInterviewRounds, {});
  await createInterview("mock", {
    spaceId: currentSpaceId.value,
    round: roundNum,
    interviewType: firstRoundInterviewType(mockInterviewRounds),
    score: averageQuestionScore(mockInterviewRounds),
    summary,
    result: aggregateRoundResults(mockInterviewRounds)
  });
  interviews.value = await listInterview(currentSpaceId.value);
  hydrateMockFromLatestMock();
}

async function saveInterview() {
  if (!currentSpaceId.value) return;
  Object.assign(jobForm, {
    title: realJobProfile.title || jobForm.title,
    company: realJobProfile.company || jobForm.company,
    location: realJobProfile.location || jobForm.location
  });
  await upsertPrimaryJobFromForm();
  const roundNum = Math.max(1, realInterviewRounds.length);
  const summary = serializeV3(realJobProfile, realInterviewRounds, {});
  await createInterview("real", {
    spaceId: currentSpaceId.value,
    round: roundNum,
    interviewType: firstRoundInterviewType(realInterviewRounds),
    score: averageQuestionScore(realInterviewRounds),
    summary,
    result: aggregateRoundResults(realInterviewRounds)
  });
  interviews.value = await listInterview(currentSpaceId.value);
  hydrateInterviewFromLatestReal();
}

function onGlobalEscape(e) {
  if (e.key !== "Escape") return;
  if (jobDeleteConfirmId.value) {
    cancelDeleteJob();
    return;
  }
  if (jobDetailModalOpen.value) {
    closeJobDetailModal();
    return;
  }
  if (jobModalOpen.value) {
    requestCloseJobModal();
    return;
  }
  if (addQuestionModalOpen.value) {
    closeAddQuestionModal();
    return;
  }
  if (addInterviewModalOpen.value) {
    closeAddInterviewModal();
    return;
  }
  if (showAddSpaceModal.value) {
    closeAddSpaceModal();
    return;
  }
  if (showRenameSpaceModal.value) {
    closeRenameSpaceModal();
    return;
  }
  if (showAuthModal.value) {
    dismissAuthModal();
  }
}

onMounted(async () => {
  document.addEventListener("keydown", onGlobalEscape);
  loadUserSession();
  if (currentUser.value) {
    await refreshSpaces();
    await loadSpaceData();
  }
});

onBeforeUnmount(() => {
  document.removeEventListener("keydown", onGlobalEscape);
  removePanelModalDragListeners();
  clearUndoTimer();
});
</script>

<template>
  <div class="min-h-screen flex bg-gray-50 font-sans text-gray-800">
    <div
      v-if="sidebarOpen"
      class="fixed inset-0 bg-black/50 z-30 lg:hidden"
      aria-hidden="true"
      @click="sidebarOpen = false"
    />
    <aside
      class="w-64 bg-white shadow-lg fixed left-0 top-0 z-40 flex h-screen max-h-screen min-h-0 flex-col overflow-hidden border-r border-gray-200 transform transition-transform duration-300 ease-in-out lg:translate-x-0 supports-[height:100dvh]:h-[100dvh] supports-[height:100dvh]:max-h-[100dvh]"
      :class="sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'"
    >
      <div class="min-h-0 flex-1 overflow-y-auto overscroll-y-contain [-webkit-overflow-scrolling:touch]">
        <div class="p-4 border-b border-gray-200">
          <div class="flex items-center gap-3">
            <div
              class="w-10 h-10 rounded-lg bg-primary text-white flex items-center justify-center text-lg font-bold shrink-0 tracking-tight"
              aria-hidden="true"
            >
              M
            </div>
            <div>
              <h1 class="text-lg font-bold text-gray-800 leading-tight">面面MienMien</h1>
              <p class="text-xs text-gray-500">专业面试管理</p>
            </div>
          </div>
        </div>

        <div class="p-4 border-b border-gray-200">
          <label class="block text-xs font-medium text-gray-500 mb-1">当前工作空间</label>
          <div class="flex gap-2 mb-2">
            <select
              class="flex-1 min-w-0 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent bg-white"
              :value="currentSpaceId"
              @change="switchSpace($event.target.value)"
            >
              <option value="" disabled>选择空间…</option>
              <option v-for="s in spaces" :key="s.spaceId" :value="s.spaceId">{{ s.name || s.spaceId }}</option>
            </select>
          </div>
          <div class="flex gap-2">
            <button
              type="button"
              class="flex-1 bg-primary hover:bg-blue-700 text-white text-xs px-2 py-2 rounded flex items-center justify-center gap-1 transition-colors"
              @click="openAddSpaceModal"
            >
              <i class="fa-solid fa-plus"></i>新建
            </button>
            <button
              type="button"
              class="flex-1 bg-gray-100 hover:bg-gray-200 text-gray-700 text-xs px-2 py-2 rounded flex items-center justify-center gap-1 transition-colors"
              @click="openRenameSpaceModal"
            >
              <i class="fa-solid fa-pencil"></i>重命名
            </button>
          </div>
        </div>

        <nav class="pb-3">
          <div class="px-2 pt-2">
            <div class="rounded-md bg-gray-100 px-3 py-2 text-xs font-medium text-gray-500">当前空间</div>
          </div>
          <ul class="mt-1 px-0 list-none m-0 p-0">
            <li v-for="item in sidebarSpaceNav" :key="item.key" class="mb-0.5 px-2">
              <button type="button" :class="sidebarNavButtonClass(item.key)" @click="switchTab(item.key)">
                <i :class="item.iconClass" class="w-7 text-center shrink-0 opacity-90"></i>
                <span class="min-w-0 flex-1 truncate">{{ item.label }}</span>
              </button>
            </li>
          </ul>

          <div class="mx-2 my-3 border-t border-gray-200"></div>

          <div class="px-2 pt-1">
            <div class="rounded-md bg-gray-100 px-3 py-2 text-xs font-medium text-gray-500">资源管理</div>
          </div>
          <ul class="mt-1 px-0 list-none m-0 p-0">
            <li v-for="item in sidebarResourceNav" :key="item.key" class="mb-0.5 px-2">
              <button type="button" :class="sidebarNavButtonClass(item.key)" @click="switchTab(item.key)">
                <i :class="item.iconClass" class="w-7 text-center shrink-0 opacity-90"></i>
                <span class="min-w-0 flex-1 truncate">{{ item.label }}</span>
                <span
                  v-if="item.key === 'job' && activeTab === 'job'"
                  class="shrink-0 rounded-full bg-primary px-2 py-0.5 text-[10px] font-medium text-white leading-none"
                >
                  当前
                </span>
              </button>
            </li>
          </ul>

          <div class="mx-2 my-3 border-t border-gray-200"></div>

          <div class="px-2">
            <div class="rounded-md bg-gray-100 px-3 py-2 text-xs font-medium text-gray-500">系统功能</div>
          </div>
          <ul class="mt-1 px-0 list-none m-0 p-0">
            <li v-for="item in sidebarSystemNav" :key="item.key" class="mb-0.5 px-2">
              <button type="button" :class="sidebarNavButtonClass(item.key)" @click="switchTab(item.key)">
                <i :class="item.iconClass" class="w-7 text-center shrink-0 opacity-90"></i>
                <span class="min-w-0 flex-1 truncate">{{ item.label }}</span>
              </button>
            </li>
            <li class="mb-0.5 px-2">
              <button type="button" :class="platformParentButtonClass" @click="togglePlatformNav">
                <i
                  class="fa-solid fa-gear w-7 text-center shrink-0 opacity-90"
                  :class="activeTab === 'config' ? 'text-primary' : ''"
                ></i>
                <span class="min-w-0 flex-1 truncate">平台配置</span>
                <i :class="platformChevronClass"></i>
              </button>
              <ul v-show="platformNavOpen" class="list-none m-0 mt-0.5 border-l-2 border-gray-200 pl-2 ml-5 mr-2 space-y-0.5">
                <li>
                  <button type="button" :class="sidebarNavButtonClass('config', { sub: true })" @click="switchTab('config')">
                    <i class="fa-solid fa-sliders w-7 text-center shrink-0 opacity-90"></i>
                    <span class="min-w-0 flex-1 truncate text-left">系统设置</span>
                  </button>
                </li>
                <li>
                  <button
                    type="button"
                    class="flex w-full items-center gap-0 pl-2 pr-3 py-2.5 text-left text-sm rounded-md transition-colors border-l-4 border-transparent text-gray-600 hover:bg-gray-100 hover:text-red-600"
                    @click="logoutUser"
                  >
                    <i class="fa-solid fa-right-from-bracket w-7 text-center shrink-0 opacity-90"></i>
                    <span class="min-w-0 flex-1 truncate text-left">退出登录</span>
                  </button>
                </li>
              </ul>
            </li>
          </ul>
        </nav>
      </div>

      <div class="shrink-0 border-t border-gray-200 bg-white px-3 py-3 text-center text-[11px] leading-relaxed text-gray-400">
        <div>© 2026 面面MienMien</div>
        <div>专业面试管理系统</div>
      </div>
    </aside>

    <div class="flex-1 flex flex-col min-w-0 lg:ml-64">
      <header class="bg-white shadow-sm sticky top-0 z-20 border-b border-gray-100">
        <div class="max-w-7xl mx-auto px-4 py-3 flex flex-wrap items-center justify-between gap-3">
          <div class="flex items-center gap-3 min-w-0">
            <button
              type="button"
              class="lg:hidden p-2 text-gray-600 hover:text-primary rounded-md -ml-1"
              aria-label="打开菜单"
              @click="sidebarOpen = true"
            >
              <i class="fa-solid fa-bars text-xl"></i>
            </button>
            <div class="min-w-0">
              <h2 class="text-lg font-semibold text-gray-800 truncate">{{ currentSpace?.name || "未选择空间" }}</h2>
              <p class="text-xs text-gray-500">{{ tabTitle(activeTab) }}</p>
            </div>
          </div>
          <div class="flex flex-wrap items-center gap-2 sm:gap-3">
            <button
              type="button"
              class="text-gray-600 hover:text-primary text-sm flex items-center gap-1 disabled:opacity-40"
              :disabled="!currentSpaceId"
              @click="saveSpaceConfig"
            >
              <i class="fa-solid fa-floppy-disk"></i><span class="hidden sm:inline">保存配置</span>
            </button>
            <button
              type="button"
              class="text-gray-600 hover:text-red-600 text-sm flex items-center gap-1 disabled:opacity-40"
              :disabled="!currentSpaceId"
              @click="moveCurrentSpaceToRecycleBin"
            >
              <i class="fa-solid fa-trash"></i><span class="hidden sm:inline">删除空间</span>
            </button>
            <button type="button" class="text-gray-600 hover:text-primary text-sm flex items-center gap-1" @click="openConfigPage">
              <i class="fa-solid fa-wand-magic-sparkles"></i><span class="hidden sm:inline">AI</span>
            </button>
            <button
              type="button"
              class="w-9 h-9 rounded-full border border-gray-200 text-gray-600 hover:border-primary hover:text-primary text-sm font-medium"
              title="平台配置"
              @click="openConfigPage"
            >
              U
            </button>
            <div v-if="currentUser" class="flex items-center gap-2 pl-1 border-l border-gray-200 ml-1">
              <div
                class="w-9 h-9 rounded-full bg-primary text-white flex items-center justify-center text-xs font-semibold shrink-0"
              >
                {{ (currentUser.phone || "—").slice(0, 3) }}
              </div>
              <div class="hidden sm:block min-w-0">
                <div class="text-sm text-gray-700 truncate max-w-[8rem]">{{ currentUser.phone }}</div>
                <div class="text-xs text-gray-500">已登录</div>
              </div>
            </div>
          </div>
        </div>
      </header>

      <main class="flex-1 max-w-7xl w-full mx-auto px-4 py-6">
        <section v-if="activeTab !== 'recycle' && !currentSpaceId" class="bg-white rounded-lg shadow-card p-8 text-center fade-in">
          <i class="fa-solid fa-location-dot text-4xl text-gray-300 mb-3"></i>
          <h2 class="text-lg font-semibold text-gray-800 mb-2">请选择工作空间</h2>
          <p class="text-gray-500 text-sm">在左侧选择或新建空间后再编辑业务数据。</p>
        </section>

        <section v-if="activeTab === 'dashboard' && currentSpaceId" class="fade-in space-y-6">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div class="bg-white rounded-lg shadow-card p-6">
              <div class="flex items-center justify-between mb-2">
                <h3 class="text-base font-semibold text-gray-800">总面试记录</h3>
                <span class="text-2xl font-bold text-primary">{{ dashboardStats.total }}</span>
              </div>
              <p class="text-sm text-gray-500">当前空间下全部模拟与正式记录条数</p>
            </div>
            <div class="bg-white rounded-lg shadow-card p-6">
              <div class="flex items-center justify-between mb-2">
                <h3 class="text-base font-semibold text-gray-800">通过</h3>
                <span class="text-2xl font-bold text-success">{{ dashboardStats.passed }}</span>
              </div>
              <p class="text-sm text-gray-500">推断结果为通过的占比 {{ dashboardStats.rate }}%</p>
            </div>
            <div class="bg-white rounded-lg shadow-card p-6">
              <div class="flex items-center justify-between mb-2">
                <h3 class="text-base font-semibold text-gray-800">待评估</h3>
                <span class="text-2xl font-bold text-warning">{{ dashboardStats.pending }}</span>
              </div>
              <p class="text-sm text-gray-500">result 为 pending 的记录数</p>
            </div>
          </div>
          <div class="bg-white rounded-lg shadow-card p-6">
            <h3 class="text-lg font-semibold text-gray-800 mb-4">最近面试记录</h3>
            <div v-if="recentInterviews.length === 0" class="text-center text-gray-500 py-8 text-sm">暂无数据</div>
            <div v-else class="space-y-3">
              <div
                v-for="row in recentInterviews"
                :key="row.recordId"
                class="border-l-4 pl-4 py-2"
                :class="row.result === 'passed' ? 'border-success' : row.result === 'failed' ? 'border-danger' : 'border-primary'"
              >
                <div class="flex justify-between gap-2 flex-wrap">
                  <span class="font-medium text-gray-800">{{ row.type === "mock" ? "模拟" : "正式" }} · {{ row.interviewType || "—" }}</span>
                  <span class="text-xs text-gray-500 shrink-0">{{ row.recordId?.slice(0, 8) }}…</span>
                </div>
                <p class="text-sm text-gray-600 mt-1">轮次 {{ row.round }} · 分数 {{ row.score }} · {{ row.result }}</p>
              </div>
            </div>
          </div>
        </section>

        <section v-if="activeTab === 'resume' && currentSpaceId" class="fade-in space-y-6">
          <div class="bg-white rounded-lg shadow-card p-6">
            <h2 class="text-lg font-semibold text-gray-800 mb-1 flex items-center gap-2">
              <i class="fa-solid fa-file-lines text-primary"></i>简历管理
            </h2>
            <p class="text-sm text-gray-500 mb-4">支持拖拽调整模块顺序，自由编辑各模块内容。</p>
            <div class="flex flex-wrap gap-2 items-center mb-3">
              <input
                v-model="newResumeBlockTitle"
                type="text"
                placeholder="新模块标题（可选）"
                class="flex-1 min-w-[10rem] max-w-xs px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
              <button
                type="button"
                class="inline-flex items-center gap-2 px-4 py-2 bg-primary hover:bg-blue-700 text-white text-sm rounded-md transition-colors"
                @click="addResumeBlock"
              >
                <i class="fa-solid fa-plus"></i>增加卡片
              </button>
              <button
                type="button"
                class="inline-flex items-center gap-2 px-4 py-2 border border-gray-300 rounded-md text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
                :disabled="!deletedBlockBackup"
                @click="undoRemoveResumeBlock"
              >
                <i class="fa-solid fa-rotate-left"></i>撤销删除<span v-if="undoRemainSeconds > 0">（{{ undoRemainSeconds }}s）</span>
              </button>
            </div>
            <p v-if="undoRemainSeconds > 0" class="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-md px-3 py-2 mb-4">
              最近删除可在 {{ undoRemainSeconds }} 秒内撤销，超时后将无法恢复。
            </p>
            <div
              v-if="resumeBlocks.length === 0"
              class="border-2 border-dashed border-gray-300 rounded-lg p-10 text-center"
            >
              <i class="fa-regular fa-file-lines text-4xl text-gray-300 mb-3"></i>
              <h3 class="text-base font-medium text-gray-700 mb-1">暂无简历模块</h3>
              <p class="text-sm text-gray-500 mb-4">点击下方按钮添加第一个模块</p>
              <button
                type="button"
                class="inline-flex items-center gap-2 px-4 py-2 bg-primary hover:bg-blue-700 text-white text-sm rounded-md transition-colors"
                @click="addResumeBlock"
              >
                <i class="fa-solid fa-plus"></i>添加简历模块
              </button>
            </div>
            <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div
                v-for="(b, idx) in resumeBlocks"
                :key="b.title + idx"
                class="bg-gray-50 rounded-lg border border-gray-200 p-4 hover:shadow-card transition-shadow cursor-grab active:cursor-grabbing"
                draggable="true"
                @dragstart="onResumeDragStart(idx)"
                @dragover.prevent
                @drop="onResumeDrop(idx)"
              >
                <div class="flex items-center justify-between gap-2 mb-2">
                  <input
                    v-model="b.title"
                    type="text"
                    class="flex-1 min-w-0 px-3 py-1.5 border border-gray-300 rounded-md text-sm font-medium focus:ring-2 focus:ring-primary"
                    placeholder="模块名称"
                    @blur="normalizeResumeBlockTitle(b, idx)"
                  />
                  <button
                    type="button"
                    class="text-gray-400 hover:text-red-600 p-2 shrink-0"
                    title="删除"
                    @click="removeResumeBlock(idx)"
                  >
                    <i class="fa-solid fa-trash"></i>
                  </button>
                </div>
                <textarea
                  v-model="b.text"
                  rows="5"
                  class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary resize-y min-h-[6rem]"
                />
              </div>
            </div>
          </div>
        </section>

        <section v-if="activeTab === 'job' && currentSpaceId" class="fade-in space-y-6">
          <div class="bg-white rounded-lg shadow-md p-6">
            <div class="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-4 mb-6">
              <div>
                <h2 class="text-xl font-semibold text-gray-800 flex items-center gap-2">
                  <i class="fa-solid fa-briefcase text-primary"></i>岗位管理
                </h2>
                <p class="text-sm text-gray-500 mt-1">
                  管理当前空间下的岗位：添加时仅需基础信息；岗位描述、JD 详细内容与考点请在「编辑」中补充。卡片右下角「查看详情」为只读，右上角为编辑与删除；顶栏「保存配置」会同步首条活跃岗位到简历/面试上下文。
                </p>
              </div>
              <div class="flex flex-wrap gap-2 shrink-0">
                <button
                  type="button"
                  class="inline-flex items-center justify-center gap-2 min-h-[40px] px-4 py-2 bg-primary hover:bg-blue-700 text-white text-sm font-medium rounded-lg shadow-sm transition-colors"
                  @click="openAddJobModal"
                >
                  <i class="fa-solid fa-plus"></i>添加岗位
                </button>
                <button
                  type="button"
                  class="inline-flex items-center justify-center gap-2 min-h-[40px] px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-medium rounded-lg shadow-sm transition-colors"
                  @click="openImportJd"
                >
                  <i class="fa-solid fa-file-import"></i>导入 JD
                </button>
              </div>
            </div>
            <div class="mb-6">
              <input
                v-model="jobSearchQuery"
                type="search"
                class="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="搜索岗位…"
              />
            </div>
            <div v-if="filteredJobsList.length === 0" class="border-2 border-dashed border-gray-200 rounded-xl p-12 text-center">
              <i class="fa-solid fa-briefcase text-4xl text-gray-300 mb-3"></i>
              <p class="text-gray-600 font-medium">暂无岗位</p>
              <p class="text-sm text-gray-500 mt-1">点击「添加岗位」创建第一条记录</p>
              <p class="text-xs text-gray-400 mt-3 max-w-md mx-auto">
                有岗位后，卡片底部会出现「查看详情」（只读弹窗）与右上角铅笔编辑。
              </p>
            </div>
            <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <article
                v-for="row in filteredJobsList"
                :key="row.positionId"
                class="group relative rounded-xl border border-gray-200 bg-white p-5 shadow-sm hover:shadow-md transition-shadow duration-200"
              >
                <div class="absolute top-3 right-3 flex gap-1">
                  <button
                    type="button"
                    class="p-2 rounded-lg text-gray-400 hover:text-primary hover:bg-blue-50 transition-colors"
                    title="编辑"
                    @click="openEditJobModal(row)"
                  >
                    <i class="fa-solid fa-pen"></i>
                  </button>
                  <button
                    type="button"
                    class="p-2 rounded-lg text-gray-400 hover:text-red-600 hover:bg-red-50 transition-colors"
                    title="删除"
                    @click="requestDeleteJob(row)"
                  >
                    <i class="fa-solid fa-trash"></i>
                  </button>
                </div>
                <div class="flex flex-wrap items-center gap-2 pr-16 mb-2">
                  <h3 class="text-lg font-semibold text-gray-900">{{ row.title || "未命名岗位" }}</h3>
                  <span
                    class="inline-flex px-2 py-0.5 rounded-full text-xs font-medium"
                    :class="jobTypeBadgeClass(decodeJobBaseRange(row.baseRange).jobType)"
                  >
                    {{ jobTypeLabel(decodeJobBaseRange(row.baseRange).jobType) }}
                  </span>
                </div>
                <div class="flex flex-wrap gap-3 text-sm text-gray-600 mb-2">
                  <span class="inline-flex items-center gap-1"><i class="fa-solid fa-building text-gray-400"></i>{{ row.company || "—" }}</span>
                  <span class="inline-flex items-center gap-1"><i class="fa-solid fa-location-dot text-gray-400"></i>{{ row.location || "—" }}</span>
                </div>
                <p class="text-sm text-gray-600 line-clamp-2 mb-4">
                  {{ decodeJobBaseRange(row.baseRange).description || "暂无岗位描述" }}
                </p>
                <div class="flex items-center justify-between text-xs text-gray-500 pt-3 border-t border-gray-100">
                  <span>创建于: {{ formatJobDate(row.createdAt) }}</span>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-primary hover:text-blue-800 font-medium transition-colors"
                    @click="openJobDetailModal(row)"
                  >
                    <i class="fa-solid fa-eye" aria-hidden="true"></i>
                    查看详情
                  </button>
                </div>
              </article>
            </div>
            <div class="mt-6 rounded-lg border border-blue-100 bg-blue-50/60 px-4 py-3 text-sm text-blue-900">
              <p class="font-semibold text-blue-950 mb-1">岗位管理说明</p>
              <p>
                支持「添加岗位」快速建档，「导入 JD」将引导你在编辑弹窗中粘贴；岗位描述、JD 富文本与考点关键词均保存在该岗位记录中。编辑弹窗可拖拽标题栏移动；点击遮罩关闭时若有未保存更改将询问是否保存。
              </p>
            </div>
          </div>
        </section>

        <section v-if="activeTab === 'answer' && currentSpaceId" class="fade-in space-y-6">
          <div class="bg-white rounded-lg shadow-card p-6">
            <h2 class="text-lg font-semibold text-gray-800 mb-1 flex items-center gap-2">
              <i class="fa-solid fa-clipboard-list text-primary"></i>标准题库
            </h2>
            <p class="text-sm text-gray-500 mb-4">按卡片管理标准答案片段，可拖拽排序。</p>
            <div class="flex flex-wrap gap-2 items-center mb-4">
              <input
                v-model="newAnswerCardTitle"
                type="text"
                placeholder="新卡片标题（可选）"
                class="flex-1 min-w-[10rem] max-w-xs px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
              <button
                type="button"
                class="inline-flex items-center gap-2 px-4 py-2 bg-primary hover:bg-blue-700 text-white text-sm rounded-md transition-colors"
                @click="addAnswerCard"
              >
                <i class="fa-solid fa-plus"></i>增加卡片
              </button>
              <button
                type="button"
                class="inline-flex items-center gap-2 px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-sm rounded-md transition-colors"
                @click="saveAnswer"
              >
                <i class="fa-solid fa-floppy-disk"></i>保存题库
              </button>
            </div>
            <div
              v-if="answerCards.length === 0"
              class="border-2 border-dashed border-gray-300 rounded-lg p-10 text-center"
            >
              <i class="fa-solid fa-clipboard text-4xl text-gray-300 mb-3"></i>
              <h3 class="text-base font-medium text-gray-700 mb-1">暂无题库内容</h3>
              <p class="text-sm text-gray-500 mb-4">点击下方按钮添加题目卡片</p>
              <button
                type="button"
                class="inline-flex items-center gap-2 px-4 py-2 bg-primary hover:bg-blue-700 text-white text-sm rounded-md transition-colors"
                @click="addAnswerCard"
              >
                <i class="fa-solid fa-plus"></i>添加题目
              </button>
            </div>
            <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div
                v-for="(card, idx) in answerCards"
                :key="card.key"
                class="bg-gray-50 rounded-lg border border-gray-200 p-4 hover:shadow-card transition-shadow cursor-grab active:cursor-grabbing"
                draggable="true"
                @dragstart="onAnswerDragStart(idx)"
                @dragover.prevent
                @drop="onAnswerDrop(idx)"
              >
                <div class="flex items-center justify-between gap-2 mb-2">
                  <input
                    v-model="card.title"
                    type="text"
                    class="flex-1 min-w-0 px-3 py-1.5 border border-gray-300 rounded-md text-sm font-medium focus:ring-2 focus:ring-primary"
                    placeholder="卡片名称"
                    @blur="normalizeAnswerCardTitle(card, idx)"
                  />
                  <button type="button" class="text-gray-400 hover:text-red-600 p-2 shrink-0" @click="removeAnswerCard(idx)">
                    <i class="fa-solid fa-trash"></i>
                  </button>
                </div>
                <textarea
                  v-model="card.text"
                  rows="5"
                  class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary resize-y min-h-[6rem]"
                />
              </div>
            </div>
          </div>
        </section>

        <section v-if="activeTab === 'mock' && currentSpaceId" class="fade-in space-y-8">
          <div class="flex justify-between items-center flex-wrap gap-3">
            <h2 class="text-xl font-bold text-gray-800 flex items-center gap-2">
              <i class="fa-solid fa-circle-play text-primary"></i>模拟面试（结构与正式一致）
            </h2>
            <button
              type="button"
              class="bg-primary hover:bg-blue-700 text-white px-6 py-2.5 rounded-md text-sm font-medium shadow-sm"
              @click="saveMock"
            >
              保存模拟面试
            </button>
          </div>
          <InterviewRoundsPanel
            :job-profile="mockJobProfile"
            :rounds="mockInterviewRounds"
            @add-round="openAddInterviewModal(true)"
            @edit-round="(i) => openEditInterviewModal(true, i)"
            @remove-round="(i) => removeInterviewRound(true, i)"
            @add-question="(i) => openAddQuestionModal(true, i)"
            @edit-question="(i, q) => openEditQuestionModal(true, i, q)"
            @remove-question="(i, id) => removeQuestionFromRound(true, i, id)"
          />
        </section>

        <section v-if="activeTab === 'interview' && currentSpaceId" class="fade-in space-y-8">
          <InterviewRoundsPanel
            :job-profile="realJobProfile"
            :rounds="realInterviewRounds"
            @add-round="openAddInterviewModal(false)"
            @edit-round="(i) => openEditInterviewModal(false, i)"
            @remove-round="(i) => removeInterviewRound(false, i)"
            @add-question="(i) => openAddQuestionModal(false, i)"
            @edit-question="(i, q) => openEditQuestionModal(false, i, q)"
            @remove-question="(i, id) => removeQuestionFromRound(false, i, id)"
          />
          <div class="flex justify-end">
            <button
              type="button"
              class="bg-primary hover:bg-blue-700 text-white px-6 py-2.5 rounded-md text-sm font-medium shadow-sm"
              @click="saveInterview"
            >
              保存正式面试
            </button>
          </div>
        </section>

        <section v-if="activeTab === 'config'" class="fade-in space-y-6">
          <div
            v-if="!currentSpaceId"
            class="border border-amber-200 bg-amber-50 text-amber-900 text-sm rounded-lg px-4 py-3 flex items-start gap-2"
          >
            <i class="fa-solid fa-triangle-exclamation mt-0.5 shrink-0"></i>
            <span>请先在左侧选择一个工作空间，平台配置按空间保存与加载。</span>
          </div>
          <div class="bg-white rounded-lg shadow-card p-6">
            <div class="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-4">
              <div>
                <h2 class="text-lg font-semibold text-gray-800 flex items-center gap-2">
                  <i class="fa-solid fa-gear text-primary"></i>阿里云百炼连接配置
                </h2>
                <p class="text-sm text-gray-500 mt-1">配置 Base URL、API Key 与模型名称，供 JD 拆解等能力调用。</p>
              </div>
              <div class="flex flex-wrap gap-2 shrink-0">
                <button
                  type="button"
                  class="inline-flex items-center gap-2 px-4 py-2 bg-primary hover:bg-blue-700 text-white text-sm rounded-md transition-colors disabled:opacity-50"
                  :disabled="!currentSpaceId"
                  @click="saveBailianConfig"
                >
                  <i class="fa-solid fa-floppy-disk"></i>保存配置
                </button>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 px-4 py-2 border border-gray-300 rounded-md text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                  :disabled="testingModelConfig || !currentSpaceId"
                  @click="testBailianConfigConnection"
                >
                  <i class="fa-solid fa-plug"></i>{{ testingModelConfig ? "测试中…" : "测试调用" }}
                </button>
              </div>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
              <label class="flex flex-col gap-1.5 text-gray-700 md:col-span-2">
                <span class="font-medium">提供商</span>
                <select
                  v-model="modelConfig.provider"
                  class="px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-primary max-w-xs"
                >
                  <option value="aliyun-bailian">aliyun-bailian</option>
                </select>
              </label>
              <label class="flex flex-col gap-1.5 text-gray-700 md:col-span-2">
                <span class="font-medium">Base URL</span>
                <input
                  v-model="modelConfig.baseUrl"
                  type="text"
                  class="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1"
                />
              </label>
              <label class="flex flex-col gap-1.5 text-gray-700 md:col-span-2">
                <span class="font-medium">API 密钥</span>
                <input
                  v-model="modelConfig.apiKey"
                  type="password"
                  class="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder="阿里云百炼 API Key"
                />
              </label>
              <label class="flex flex-col gap-1.5 text-gray-700 md:col-span-2">
                <span class="font-medium">模型名称</span>
                <input
                  v-model="modelConfig.modelName"
                  type="text"
                  class="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary max-w-md"
                  placeholder="例如 qwen-plus"
                />
              </label>
              <label class="flex flex-col gap-1.5 text-gray-700 md:col-span-2">
                <span class="font-medium">测试提示词</span>
                <textarea
                  v-model="modelConfig.testPrompt"
                  rows="2"
                  class="px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-primary resize-y"
                />
              </label>
              <label class="flex flex-col gap-1.5 text-gray-700 md:col-span-2">
                <span class="font-medium">调用结果</span>
                <textarea
                  :value="modelConfigTestResult"
                  rows="3"
                  readonly
                  class="px-3 py-2 border border-gray-200 rounded-md bg-gray-50 text-gray-700 resize-y"
                />
              </label>
            </div>
            <p class="mt-6 text-xs text-gray-500 leading-relaxed">
              配置写入当前空间；切换空间会自动加载对应配置。文档：
              <a
                class="text-primary hover:underline"
                href="https://bailian.console.aliyun.com/cn-beijing?spm=5176.12818093_47.resourceCenter.1.408916d04lcOfi&tab=doc#/doc/?type=model&url=2840915"
                target="_blank"
                rel="noreferrer"
                >百炼控制台</a
              >。
            </p>
          </div>
        </section>

        <section v-if="activeTab === 'user'" class="fade-in space-y-6">
          <div class="bg-white rounded-lg shadow-card p-6">
            <h2 class="text-lg font-semibold text-gray-800 mb-2 flex items-center gap-2">
              <i class="fa-solid fa-user text-primary"></i>用户管理
            </h2>
            <p v-if="currentUser" class="text-sm text-gray-500 mb-6">
              你已登录。下方为当前账号摘要；更换手机号可使用「使用其他账号」在本页切换。密码在后端以安全摘要存储。
            </p>
            <p v-else class="text-sm text-gray-500 mb-6">
              使用<strong class="text-gray-700">手机号 + 密码</strong>注册与登录（11 位中国大陆号码）。未登录时访问其他模块会提示登录。
            </p>

            <div v-if="currentUser" class="rounded-lg border border-gray-200 bg-gray-50/80 p-5 space-y-4">
              <h3 class="text-base font-semibold text-gray-800">当前账号信息</h3>
              <dl class="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
                <div>
                  <dt class="text-gray-500 mb-1">手机号</dt>
                  <dd class="font-medium text-gray-900">{{ currentUser.phone }}</dd>
                </div>
                <div>
                  <dt class="text-gray-500 mb-1">用户 ID</dt>
                  <dd><code class="text-xs bg-white px-2 py-1 rounded border border-gray-200">{{ currentUser.userId }}</code></dd>
                </div>
                <div>
                  <dt class="text-gray-500 mb-1">会话状态</dt>
                  <dd class="text-green-700 font-medium">已登录</dd>
                </div>
                <div>
                  <dt class="text-gray-500 mb-1">令牌尾缀</dt>
                  <dd>
                    <code class="text-xs bg-white px-2 py-1 rounded border border-gray-200">{{ maskSessionTail(currentUser.sessionToken) }}</code>
                    <span class="text-gray-400 text-xs ml-1">不完整展示</span>
                  </dd>
                </div>
              </dl>
              <ul class="text-xs text-gray-600 space-y-1 list-disc pl-4">
                <li>退出登录将注销服务端会话并清除本机令牌。</li>
                <li>业务数据按空间隔离，请勿共用账号。</li>
              </ul>
              <div class="flex flex-wrap gap-3 pt-2">
                <button
                  type="button"
                  class="inline-flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-sm rounded-md transition-colors"
                  @click="logoutUser"
                >
                  <i class="fa-solid fa-right-from-bracket"></i>退出登录
                </button>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 px-4 py-2 border border-gray-300 rounded-md text-sm text-gray-700 hover:bg-white"
                  @click="switchToOtherAccount"
                >
                  <i class="fa-solid fa-user-plus"></i>使用其他账号
                </button>
              </div>
            </div>

            <div
              v-if="!currentUser"
              class="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center mb-6"
            >
              <i class="fa-regular fa-user text-4xl text-gray-300 mb-3"></i>
              <h3 class="text-base font-medium text-gray-700 mb-2">未登录</h3>
              <p class="text-sm text-gray-500 max-w-md mx-auto">
                {{
                  switchAccountInline
                    ? "你已退出当前账号。请使用下方表单登录或注册后再访问其他模块。"
                    : "请使用下方表单注册或登录；也可在弹窗中完成登录。"
                }}
              </p>
            </div>

            <div v-if="!currentUser" class="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div class="rounded-lg border border-gray-200 p-5 space-y-3">
                <h3 class="text-base font-semibold text-gray-800 flex items-center gap-2">
                  <i class="fa-solid fa-user-plus text-primary"></i>注册
                </h3>
                <label class="flex flex-col gap-1 text-sm text-gray-700">
                  <span>手机号</span>
                  <input
                    v-model="userForm.registerPhone"
                    type="tel"
                    class="px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-primary"
                    placeholder="11 位手机号"
                  />
                </label>
                <label class="flex flex-col gap-1 text-sm text-gray-700">
                  <span>密码</span>
                  <input
                    v-model="userForm.registerPassword"
                    type="password"
                    class="px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-primary"
                    placeholder="请输入密码"
                  />
                </label>
                <label class="flex flex-col gap-1 text-sm text-gray-700">
                  <span>确认密码</span>
                  <input
                    v-model="userForm.registerConfirmPassword"
                    type="password"
                    class="px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-primary"
                    placeholder="请再次输入密码"
                  />
                </label>
                <button
                  type="button"
                  class="w-full mt-2 py-2.5 bg-primary hover:bg-blue-700 text-white text-sm font-medium rounded-md transition-colors"
                  @click="registerUser"
                >
                  注册
                </button>
              </div>
              <div class="rounded-lg border border-gray-200 p-5 space-y-3">
                <h3 class="text-base font-semibold text-gray-800 flex items-center gap-2">
                  <i class="fa-solid fa-right-to-bracket text-primary"></i>登录
                </h3>
                <label class="flex flex-col gap-1 text-sm text-gray-700">
                  <span>手机号</span>
                  <input
                    v-model="userForm.loginPhone"
                    type="tel"
                    class="px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-primary"
                    placeholder="11 位手机号"
                  />
                </label>
                <label class="flex flex-col gap-1 text-sm text-gray-700">
                  <span>密码</span>
                  <input
                    v-model="userForm.loginPassword"
                    type="password"
                    class="px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-primary"
                    placeholder="请输入密码"
                  />
                </label>
                <button
                  type="button"
                  class="w-full mt-2 py-2.5 bg-primary hover:bg-blue-700 text-white text-sm font-medium rounded-md transition-colors"
                  @click="loginUser"
                >
                  登录
                </button>
              </div>
            </div>
          </div>
        </section>

        <section v-if="activeTab === 'recycle'" class="fade-in">
          <div class="bg-white rounded-lg shadow-card p-6">
            <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 mb-6">
              <h2 class="text-lg font-semibold text-gray-800 flex items-center gap-2">
                <i class="fa-solid fa-trash text-primary"></i>回收站
              </h2>
              <span class="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-full px-3 py-1 w-fit">超过 30 天将自动清除</span>
            </div>
            <div
              v-if="recycleBinSpaces.length === 0"
              class="border-2 border-dashed border-gray-300 rounded-lg p-12 text-center"
            >
              <i class="fa-solid fa-trash text-4xl text-gray-300 mb-3"></i>
              <h3 class="text-base font-medium text-gray-700 mb-1">回收站为空</h3>
              <p class="text-sm text-gray-500">删除的空间将显示在这里，可随时还原。</p>
            </div>
            <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div
                v-for="s in recycleBinSpaces"
                :key="s.spaceId"
                class="rounded-lg border border-gray-200 p-4 hover:border-primary/40 hover:shadow-card transition-all bg-gray-50/50"
              >
                <div class="font-semibold text-gray-800 mb-2">{{ s.name || s.spaceId }}</div>
                <p class="text-xs text-gray-500 mb-1">ID：{{ s.spaceId }}</p>
                <p class="text-xs text-gray-500 mb-4">删除时间：{{ s.deletedAt || "—" }}</p>
                <button
                  type="button"
                  class="w-full py-2 text-sm font-medium rounded-md bg-white border border-primary text-primary hover:bg-blue-50 transition-colors"
                  @click="restoreFromRecycleBin(s.spaceId)"
                >
                  还原空间
                </button>
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>

    <div
      v-if="showAddSpaceModal"
      class="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 p-4"
      @click.self="closeAddSpaceModal"
    >
      <div
        class="bg-white rounded-xl shadow-2xl border border-gray-100 w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden slide-in-modal"
        :class="panelModalDragging ? 'cursor-grabbing' : ''"
        :style="{ transform: `translate(${panelModalOffset.x}px, ${panelModalOffset.y}px)` }"
        @click.stop
      >
        <div
          class="shrink-0 px-6 py-4 border-b border-gray-100 flex items-center justify-between gap-2 cursor-move"
          @pointerdown="onPanelModalHeaderPointerDown"
        >
          <h3 class="text-lg font-bold text-gray-900">创建新空间</h3>
          <button type="button" class="p-2 text-gray-400 hover:text-gray-700 rounded-lg" aria-label="关闭" @click="closeAddSpaceModal">
            <i class="fa-solid fa-xmark text-lg"></i>
          </button>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto px-6 py-4 space-y-4">
          <label class="block text-sm text-gray-700">
            <span class="font-medium">空间名称 <span class="text-red-500">*</span></span>
            <input
              v-model="newSpaceName"
              type="text"
              class="mt-1 w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              placeholder="请输入空间名称"
              @keydown.enter.prevent="addSpace"
              autofocus
            />
          </label>
          <div class="border-t border-gray-100 pt-4">
            <h4 class="text-sm font-semibold text-gray-800 mb-2">绑定资源</h4>
            <p v-if="!currentSpaceId" class="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-3">
              请先在侧栏选择「当前工作空间」，创建新空间时才能从该空间复制简历与岗位。
            </p>
            <p v-else-if="bindSourceResumes.length === 0 && bindSourceJobs.length === 0" class="text-xs text-gray-500 mb-3">
              当前空间暂无简历或岗位数据，将创建空白空间；之后可在各模块中补充。
            </p>
            <div class="space-y-4">
              <div>
                <p class="text-xs font-medium text-gray-600 mb-2">选择简历（单选）</p>
                <div class="border border-gray-200 rounded-lg p-2 max-h-40 overflow-y-auto space-y-1 bg-gray-50/50">
                  <label
                    class="flex items-start gap-2 rounded-md px-2 py-2 text-sm cursor-pointer hover:bg-white transition-colors"
                  >
                    <input v-model="newSpaceBindResumeId" type="radio" name="newSpaceResume" value="" class="mt-1 text-primary" />
                    <span class="text-gray-700">不复制简历</span>
                  </label>
                  <label
                    v-for="r in bindSourceResumes"
                    :key="r.resumeId"
                    class="flex items-start gap-2 rounded-md px-2 py-2 text-sm cursor-pointer hover:bg-white transition-colors"
                  >
                    <input
                      v-model="newSpaceBindResumeId"
                      type="radio"
                      name="newSpaceResume"
                      :value="r.resumeId"
                      class="mt-1 text-primary"
                    />
                    <span class="text-gray-800">{{ resumeBindLabel(r) }}</span>
                  </label>
                </div>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-600 mb-2">选择岗位（多选）</p>
                <div class="border border-gray-200 rounded-lg p-2 max-h-48 overflow-y-auto space-y-1 bg-gray-50/50">
                  <p v-if="bindSourceJobs.length === 0" class="text-xs text-gray-500 px-2 py-2">暂无活跃岗位</p>
                  <label
                    v-for="j in bindSourceJobs"
                    :key="j.positionId"
                    class="flex items-start gap-2 rounded-md px-2 py-2 text-sm cursor-pointer hover:bg-white transition-colors"
                  >
                    <input
                      type="checkbox"
                      class="mt-1 rounded border-gray-300 text-primary focus:ring-primary"
                      :checked="newSpaceBindJobIds.includes(j.positionId)"
                      @change="toggleNewSpaceBindJob(j.positionId)"
                    />
                    <span class="text-gray-800">{{ jobBindLabel(j) }}</span>
                  </label>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="shrink-0 px-6 py-4 border-t border-gray-100 flex justify-end gap-3 bg-gray-50/80">
          <button
            type="button"
            class="px-4 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-white transition-colors min-h-[40px]"
            :disabled="addingSpace"
            @click="closeAddSpaceModal"
          >
            取消
          </button>
          <button
            type="button"
            class="px-4 py-2 bg-primary hover:bg-blue-700 text-white text-sm font-medium rounded-lg disabled:opacity-40 disabled:cursor-not-allowed transition-colors min-h-[40px]"
            :disabled="addingSpace || !newSpaceName.trim()"
            @click="addSpace"
          >
            {{ addingSpace ? "创建中…" : "创建" }}
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="showRenameSpaceModal"
      class="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 p-4"
      @click.self="closeRenameSpaceModal"
    >
      <div
        class="bg-white rounded-xl shadow-xl w-full max-w-md slide-in-modal border border-gray-100 flex flex-col max-h-[90vh] overflow-hidden"
        :class="panelModalDragging ? 'cursor-grabbing' : ''"
        :style="{ transform: `translate(${panelModalOffset.x}px, ${panelModalOffset.y}px)` }"
        @click.stop
      >
        <div
          class="shrink-0 p-5 border-b border-gray-100 flex items-center justify-between gap-2 cursor-move"
          @pointerdown="onPanelModalHeaderPointerDown"
        >
          <h3 class="text-lg font-bold text-gray-900">重命名空间</h3>
          <button type="button" class="p-2 text-gray-400 hover:text-gray-700 rounded-lg" aria-label="关闭" @click="closeRenameSpaceModal">
            <i class="fa-solid fa-xmark text-lg"></i>
          </button>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <label class="block text-sm font-medium text-gray-700 mb-2">新名称</label>
          <input
            v-model="renameSpaceName"
            type="text"
            class="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            placeholder="请输入新的空间名称"
            @keydown.enter.prevent="doRenameSpace"
            autofocus
          />
        </div>
        <div class="shrink-0 p-5 border-t border-gray-100 flex justify-end gap-3">
          <button
            type="button"
            class="px-4 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50"
            @click="closeRenameSpaceModal"
          >
            取消
          </button>
          <button
            type="button"
            class="px-4 py-2 bg-primary hover:bg-blue-700 text-white text-sm font-medium rounded-lg disabled:opacity-40 transition-colors"
            :disabled="!renameSpaceName.trim()"
            @click="doRenameSpace"
          >
            确认重命名
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="addInterviewModalOpen"
      class="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 p-4"
      @click.self="closeAddInterviewModal"
    >
      <div
        class="bg-white rounded-xl shadow-2xl border border-gray-100 w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden slide-in-modal"
        :class="panelModalDragging ? 'cursor-grabbing' : ''"
        :style="{ transform: `translate(${panelModalOffset.x}px, ${panelModalOffset.y}px)` }"
        @click.stop
      >
        <div class="shrink-0 p-6 border-b border-gray-200 cursor-move" @pointerdown="onPanelModalHeaderPointerDown">
          <h3 class="text-lg font-bold text-gray-800">{{ editingRoundIndex >= 0 ? "编辑面试" : "添加新面试" }}</h3>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto p-6 space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">面试轮次</label>
            <input
              v-model="interviewDraft.roundTitle"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              placeholder="例如：第三轮面试"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">面试时间</label>
            <input
              v-model="interviewDraft.timeText"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              placeholder="例如：2026-05-10 14:00-15:00"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">面试地点</label>
            <select
              v-model="interviewDraft.locationMode"
              class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="线上">线上</option>
              <option value="线下">线下</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">面试分类</label>
            <select
              v-model="interviewDraft.category"
              class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="HR面">HR面</option>
              <option value="技术面">技术面</option>
              <option value="业务面">业务面</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">面试官信息</label>
            <div class="space-y-2">
              <div v-for="(row, ri) in interviewDraft.interviewers" :key="ri" class="flex gap-2 items-center">
                <select
                  v-model="row.role"
                  class="flex-1 min-w-0 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="HR">HR</option>
                  <option value="P">Peer</option>
                  <option value="+1">+1LD</option>
                  <option value="+2">+2LD</option>
                </select>
                <input
                  v-model="row.name"
                  type="text"
                  class="flex-1 min-w-0 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder="面试官姓名"
                />
                <button type="button" class="text-red-500 p-2 shrink-0" @click="removeInterviewerRow(ri)">
                  <i class="fa-solid fa-circle-xmark"></i>
                </button>
              </div>
            </div>
            <button type="button" class="mt-2 text-primary text-sm flex items-center gap-1 hover:text-blue-800" @click="addInterviewerRow">
              <i class="fa-solid fa-circle-plus"></i>
              添加面试官
            </button>
          </div>
        </div>
        <div class="shrink-0 p-6 border-t border-gray-200 flex justify-end gap-3 bg-gray-50/80">
          <button type="button" class="px-4 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50 text-sm" @click="closeAddInterviewModal">
            取消
          </button>
          <button type="button" class="px-4 py-2 bg-primary hover:bg-blue-700 text-white rounded-md text-sm" @click="submitInterviewModal">
            {{ editingRoundIndex >= 0 ? "保存" : "添加" }}
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="addQuestionModalOpen"
      class="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 p-4"
      @click.self="closeAddQuestionModal"
    >
      <div
        class="bg-white rounded-xl shadow-2xl border border-gray-100 w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden slide-in-modal"
        :class="panelModalDragging ? 'cursor-grabbing' : ''"
        :style="{ transform: `translate(${panelModalOffset.x}px, ${panelModalOffset.y}px)` }"
        @click.stop
      >
        <div class="shrink-0 p-6 border-b border-gray-200 cursor-move" @pointerdown="onPanelModalHeaderPointerDown">
          <h3 class="text-lg font-bold text-gray-800">{{ editingQuestionId ? "编辑面试题目" : "添加面试题目" }}</h3>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto p-6 space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">题目标题</label>
            <input
              v-model="questionDraft.title"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              placeholder="题目标题"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">题目序号</label>
            <input
              v-model="questionDraft.label"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              placeholder="例如：题目1"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">原题记录</label>
            <textarea
              v-model="questionDraft.questionRecord"
              rows="3"
              class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              placeholder="完整题目内容"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">作答记录</label>
            <textarea
              v-model="questionDraft.answerRecord"
              rows="3"
              class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">作答优点</label>
            <textarea v-model="questionDraft.pros" rows="2" class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-primary" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">作答缺点</label>
            <textarea v-model="questionDraft.cons" rows="2" class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-primary" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">后续优化方案</label>
            <textarea v-model="questionDraft.improvementPlan" rows="2" class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-primary" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">标准答案</label>
            <textarea v-model="questionDraft.standardAnswer" rows="3" class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-primary" />
          </div>
          <div>
            <span class="block text-sm font-medium text-gray-700 mb-2">题目难度</span>
            <div class="flex flex-wrap gap-4 text-sm">
              <label class="inline-flex items-center gap-2 cursor-pointer">
                <input v-model.number="questionDraft.difficulty" type="radio" :value="1" class="text-primary" />
                简单
              </label>
              <label class="inline-flex items-center gap-2 cursor-pointer">
                <input v-model.number="questionDraft.difficulty" type="radio" :value="2" class="text-primary" />
                中等
              </label>
              <label class="inline-flex items-center gap-2 cursor-pointer">
                <input v-model.number="questionDraft.difficulty" type="radio" :value="3" class="text-primary" />
                困难
              </label>
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">题目分数（0-100）</label>
            <input
              v-model.number="questionDraft.score"
              type="number"
              min="0"
              max="100"
              class="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-primary"
            />
          </div>
        </div>
        <div class="shrink-0 p-6 border-t border-gray-200 flex justify-end gap-3 bg-gray-50/80">
          <button type="button" class="px-4 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50 text-sm" @click="closeAddQuestionModal">
            取消
          </button>
          <button type="button" class="px-4 py-2 bg-primary hover:bg-blue-700 text-white rounded-md text-sm" @click="submitQuestionModal">
            {{ editingQuestionId ? "保存" : "添加" }}
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="showAuthModal"
      class="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 p-4"
      @click.self="dismissAuthModal"
    >
      <div
        class="bg-white rounded-xl shadow-xl w-full max-w-md slide-in-modal border border-gray-100 max-h-[90vh] flex flex-col overflow-hidden"
        :class="panelModalDragging ? 'cursor-grabbing' : ''"
        :style="{ transform: `translate(${panelModalOffset.x}px, ${panelModalOffset.y}px)` }"
        @click.stop
      >
        <div
          class="shrink-0 p-5 border-b border-gray-100 flex items-start justify-between gap-3 cursor-move"
          @pointerdown="onPanelModalHeaderPointerDown"
        >
          <div>
            <h3 class="text-lg font-bold text-gray-900">欢迎使用 MienMien</h3>
            <p class="text-sm text-gray-500 mt-1">请先登录或注册后继续使用 B 端能力</p>
          </div>
          <button type="button" class="p-2 text-gray-400 hover:text-gray-700 rounded-lg shrink-0" aria-label="关闭" @click="dismissAuthModal">
            <i class="fa-solid fa-xmark text-lg"></i>
          </button>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <div class="grid grid-cols-2 gap-2 mb-4">
            <button
              type="button"
              class="py-2.5 text-sm font-semibold rounded-lg border transition-colors"
              :class="
                authMode === 'login'
                  ? 'border-primary bg-blue-50 text-primary'
                  : 'border-gray-200 bg-gray-50 text-gray-600 hover:bg-gray-100'
              "
              @click="authMode = 'login'"
            >
              登录
            </button>
            <button
              type="button"
              class="py-2.5 text-sm font-semibold rounded-lg border transition-colors"
              :class="
                authMode === 'register'
                  ? 'border-primary bg-blue-50 text-primary'
                  : 'border-gray-200 bg-gray-50 text-gray-600 hover:bg-gray-100'
              "
              @click="authMode = 'register'"
            >
              注册
            </button>
          </div>

          <div v-if="authMode === 'login'" class="rounded-lg border border-gray-200 bg-gray-50/80 p-4 space-y-3">
            <label class="block text-sm text-gray-700">
              <span class="font-medium mb-1 block">手机号</span>
              <input
                v-model="userForm.loginPhone"
                type="tel"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary"
                placeholder="请输入手机号"
              />
            </label>
            <label class="block text-sm text-gray-700">
              <span class="font-medium mb-1 block">密码</span>
              <input
                v-model="userForm.loginPassword"
                type="password"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary"
                placeholder="请输入密码"
                @keydown.enter.prevent="loginUser"
              />
            </label>
            <button
              type="button"
              class="w-full py-2.5 mt-1 bg-primary hover:bg-blue-700 text-white text-sm font-semibold rounded-lg transition-colors"
              @click="loginUser"
            >
              立即登录
            </button>
            <p class="text-center text-xs text-gray-500">
              还没有账号？
              <button type="button" class="text-primary font-semibold hover:underline" @click="authMode = 'register'">去注册</button>
            </p>
          </div>

          <div v-else class="rounded-lg border border-gray-200 bg-gray-50/80 p-4 space-y-3">
            <label class="block text-sm text-gray-700">
              <span class="font-medium mb-1 block">手机号</span>
              <input
                v-model="userForm.registerPhone"
                type="tel"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary"
                placeholder="请输入手机号"
              />
            </label>
            <label class="block text-sm text-gray-700">
              <span class="font-medium mb-1 block">密码</span>
              <input
                v-model="userForm.registerPassword"
                type="password"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary"
                placeholder="请输入密码"
              />
            </label>
            <label class="block text-sm text-gray-700">
              <span class="font-medium mb-1 block">确认密码</span>
              <input
                v-model="userForm.registerConfirmPassword"
                type="password"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary"
                placeholder="请再次输入密码"
                @keydown.enter.prevent="registerUser"
              />
            </label>
            <button
              type="button"
              class="w-full py-2.5 mt-1 bg-primary hover:bg-blue-700 text-white text-sm font-semibold rounded-lg transition-colors"
              @click="registerUser"
            >
              注册并登录
            </button>
            <p class="text-center text-xs text-gray-500">
              已有账号？
              <button type="button" class="text-primary font-semibold hover:underline" @click="authMode = 'login'">去登录</button>
            </p>
          </div>
          <p class="text-xs text-gray-400 text-center mt-4 px-2">登录即表示你同意仅使用手机号与密码进行基础身份验证。</p>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="jobModalOpen"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4"
        @click.self="requestCloseJobModal"
      >
      <div
        class="bg-white rounded-xl shadow-2xl border border-gray-100 w-full max-w-2xl max-h-[90vh] min-h-0 flex flex-col overflow-hidden slide-in-modal"
        :class="panelModalDragging ? 'cursor-grabbing' : ''"
        :style="{ transform: `translate(${panelModalOffset.x}px, ${panelModalOffset.y}px)` }"
        @click.stop
      >
        <div
          class="shrink-0 px-6 py-4 border-b border-gray-100 flex items-start justify-between gap-3 cursor-move"
          @pointerdown="onPanelModalHeaderPointerDown"
        >
          <h3 class="text-lg font-bold text-gray-900 pr-4">
            {{ jobModalMode === 'edit' ? '编辑岗位信息' : '添加新岗位' }}
          </h3>
          <button
            type="button"
            class="p-2 text-gray-400 hover:text-gray-700 rounded-lg shrink-0 transition-colors"
            aria-label="关闭"
            @click="requestCloseJobModal"
          >
            <i class="fa-solid fa-xmark text-lg"></i>
          </button>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto px-6 py-4 space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <label class="block text-sm text-gray-700">
              <span class="font-medium">岗位名称 <span class="text-red-500">*</span></span>
              <input
                v-model="jobModalDraft.title"
                type="text"
                class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="请输入岗位名称"
                @input="jobModalMarkDirty"
              />
            </label>
            <label class="block text-sm text-gray-700">
              <span class="font-medium">所属公司 <span class="text-red-500">*</span></span>
              <input
                v-model="jobModalDraft.company"
                type="text"
                class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="请输入公司名称"
                @input="jobModalMarkDirty"
              />
            </label>
            <label class="block text-sm text-gray-700">
              <span class="font-medium">工作地点 <span class="text-red-500">*</span></span>
              <input
                v-model="jobModalDraft.location"
                type="text"
                class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="请输入工作地点"
                @input="jobModalMarkDirty"
              />
            </label>
            <label class="block text-sm text-gray-700">
              <span class="font-medium">岗位类型</span>
              <select
                v-model="jobModalDraft.jobType"
                class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary bg-white"
                @change="jobModalMarkDirty"
              >
                <option v-for="opt in JOB_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
            </label>
          </div>
          <label class="block text-sm text-gray-700">
            <span class="font-medium">期望薪资 / 备注</span>
            <input
              v-model="jobModalDraft.salary"
              type="text"
              class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              placeholder="如：25k-35k · 14 薪"
              @input="jobModalMarkDirty"
            />
          </label>
          <div v-if="jobModalMode === 'edit'" class="space-y-4 border-t border-gray-100 pt-4">
            <div class="rounded-lg border border-dashed border-gray-200 bg-gray-50/80 p-4 space-y-3">
              <label class="block text-sm font-medium text-gray-700">
                粘贴完整 JD（仅用于一键拆解，可不写入岗位正文）
              </label>
              <textarea
                v-model="jobModalJdPaste"
                rows="4"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary resize-y bg-white"
                placeholder="将招聘 JD 粘贴到此处后点击拆解，结果写入下方「考点关键词」"
              />
              <button
                type="button"
                class="inline-flex items-center gap-2 px-4 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-white disabled:opacity-50"
                :disabled="jobModalJdAnalyzing"
                @click="runJobModalAnalyzeFocusPoints"
              >
                <i class="fa-solid fa-wand-magic-sparkles"></i>{{ jobModalJdAnalyzing ? "拆解中…" : "一键拆解考点" }}
              </button>
            </div>
            <label class="block text-sm text-gray-700">
              <span class="font-medium">考点关键词（逗号分隔，可选）</span>
              <textarea
                v-model="jobModalDraft.focusPoints"
                rows="3"
                class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary resize-y"
                placeholder="例如：JVM调优, 分布式事务, MySQL索引"
                @input="jobModalMarkDirty"
              />
            </label>
            <label class="block text-sm text-gray-700">
              <span class="font-medium">岗位描述</span>
              <textarea
                v-model="jobModalDraft.description"
                rows="4"
                class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary resize-y"
                placeholder="简要描述岗位职责与要求"
                @input="jobModalMarkDirty"
              />
            </label>
            <div>
              <span class="block text-sm font-medium text-gray-700 mb-2">JD 详细内容</span>
              <div class="rounded-lg border border-gray-200 overflow-hidden bg-white">
                <div
                  class="flex flex-wrap gap-1 p-2 border-b border-gray-200 bg-gray-50 sticky top-0 z-10"
                  @mousedown.prevent
                >
                  <button
                    type="button"
                    class="p-2 rounded text-gray-600 hover:bg-gray-200 hover:text-gray-900 transition-colors text-sm font-bold"
                    title="加粗"
                    @click="rtCommand('bold')"
                  >
                    B
                  </button>
                  <button
                    type="button"
                    class="p-2 rounded text-gray-600 hover:bg-gray-200 hover:text-gray-900 transition-colors text-sm italic"
                    title="斜体"
                    @click="rtCommand('italic')"
                  >
                    I
                  </button>
                  <button
                    type="button"
                    class="p-2 rounded text-gray-600 hover:bg-gray-200 hover:text-gray-900 transition-colors text-sm underline"
                    title="下划线"
                    @click="rtCommand('underline')"
                  >
                    U
                  </button>
                  <button
                    type="button"
                    class="p-2 rounded text-gray-600 hover:bg-gray-200 transition-colors"
                    title="删除线"
                    @click="rtCommand('strikeThrough')"
                  >
                    <i class="fa-solid fa-strikethrough text-xs"></i>
                  </button>
                  <button
                    type="button"
                    class="p-2 rounded text-gray-600 hover:bg-gray-200 transition-colors"
                    title="无序列表"
                    @click="rtCommand('insertUnorderedList')"
                  >
                    <i class="fa-solid fa-list-ul text-xs"></i>
                  </button>
                  <button
                    type="button"
                    class="p-2 rounded text-gray-600 hover:bg-gray-200 transition-colors"
                    title="有序列表"
                    @click="rtCommand('insertOrderedList')"
                  >
                    <i class="fa-solid fa-list-ol text-xs"></i>
                  </button>
                  <button
                    type="button"
                    class="p-2 rounded text-gray-600 hover:bg-gray-200 transition-colors"
                    title="链接"
                    @click="rtInsertLink"
                  >
                    <i class="fa-solid fa-link text-xs"></i>
                  </button>
                </div>
                <div
                  ref="jobRichEditorRef"
                  contenteditable="true"
                  class="min-h-[12rem] px-3 py-2 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-inset focus:ring-primary/30"
                  @input="jobModalMarkDirty"
                />
              </div>
            </div>
          </div>
        </div>
        <div class="shrink-0 px-6 py-4 border-t border-gray-100 flex justify-end gap-3 bg-gray-50/80">
          <button
            type="button"
            class="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-white text-sm font-medium transition-colors min-h-[40px]"
            :disabled="jobModalSaving"
            @click="requestCloseJobModal"
          >
            取消
          </button>
          <button
            type="button"
            class="px-4 py-2 bg-primary hover:bg-blue-700 text-white rounded-lg text-sm font-medium transition-colors min-h-[40px] disabled:opacity-50"
            :disabled="jobModalSaving"
            @click="submitJobModal"
          >
            {{ jobModalSaving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div
        v-if="jobDetailModalOpen"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4"
        @click.self="closeJobDetailModal"
      >
      <div
        class="bg-white rounded-xl shadow-2xl border border-gray-100 w-full max-w-2xl max-h-[90vh] min-h-0 flex flex-col overflow-hidden slide-in-modal"
        :class="panelModalDragging ? 'cursor-grabbing' : ''"
        :style="{ transform: `translate(${panelModalOffset.x}px, ${panelModalOffset.y}px)` }"
        @click.stop
      >
        <div
          class="shrink-0 px-6 py-4 border-b border-gray-100 flex items-start justify-between gap-3 cursor-move"
          @pointerdown="onPanelModalHeaderPointerDown"
        >
          <h3 class="text-lg font-bold text-gray-900 pr-4">
            {{ (jobDetailView.title || "岗位").trim() }} - 详情
          </h3>
          <button
            type="button"
            class="p-2 text-gray-400 hover:text-gray-700 rounded-lg shrink-0 transition-colors"
            aria-label="关闭"
            @click="closeJobDetailModal"
          >
            <i class="fa-solid fa-xmark text-lg"></i>
          </button>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto px-6 py-4 space-y-5 text-sm">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <p class="text-xs font-medium text-gray-500 mb-1">岗位名称</p>
              <p class="text-gray-900 font-medium">{{ jobDetailView.title || "—" }}</p>
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 mb-1">岗位类型</p>
              <span
                class="inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium"
                :class="jobTypeBadgeClass(jobDetailView.jobType)"
              >
                {{ jobTypeLabel(jobDetailView.jobType) }}
              </span>
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 mb-1">所属公司</p>
              <p class="text-gray-800">{{ jobDetailView.company || "—" }}</p>
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 mb-1">工作地点</p>
              <p class="text-gray-800">{{ jobDetailView.location || "—" }}</p>
            </div>
            <div class="sm:col-span-2">
              <p class="text-xs font-medium text-gray-500 mb-1">期望薪资 / 备注</p>
              <p class="text-gray-800">{{ jobDetailView.salary }}</p>
            </div>
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 mb-1">岗位描述</p>
            <p class="text-gray-800 whitespace-pre-wrap leading-relaxed">{{ jobDetailView.description }}</p>
          </div>
          <div v-if="jobDetailView.focusPoints">
            <p class="text-xs font-medium text-gray-500 mb-2">核心考点</p>
            <div class="flex flex-wrap gap-2">
              <span
                v-for="p in jobDetailView.focusPoints.split(',').map((x) => x.trim()).filter(Boolean)"
                :key="p"
                class="inline-flex px-2.5 py-1 rounded-full text-xs bg-blue-100 text-blue-800"
                >{{ p }}</span
              >
            </div>
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 mb-2">JD 详细内容</p>
            <div
              v-if="jobDetailView.jdDetailHtml"
              class="rounded-lg border border-gray-200 bg-white px-4 py-3 text-gray-800 leading-relaxed overflow-x-auto [&_ul]:list-disc [&_ul]:pl-5 [&_ol]:list-decimal [&_ol]:pl-5 [&_li]:my-0.5 [&_p]:my-1"
              v-html="jobDetailView.jdDetailHtml"
            />
            <p v-else class="rounded-lg border border-dashed border-gray-200 bg-gray-50 px-4 py-6 text-center text-gray-400">
              暂无 JD 详细内容
            </p>
          </div>
        </div>
        <div class="shrink-0 px-6 py-4 border-t border-gray-100 flex justify-end bg-gray-50/80">
          <button
            type="button"
            class="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-white text-sm font-medium transition-colors min-h-[40px]"
            @click="closeJobDetailModal"
          >
            关闭
          </button>
        </div>
      </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div
        v-if="jobDeleteConfirmId"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4"
        @click.self="cancelDeleteJob"
      >
      <div class="bg-white rounded-xl shadow-2xl border border-red-100 w-full max-w-md p-6" @click.stop>
        <div class="flex items-start gap-3 mb-4">
          <div class="w-10 h-10 rounded-full bg-red-50 text-red-600 flex items-center justify-center shrink-0">
            <i class="fa-solid fa-triangle-exclamation"></i>
          </div>
          <div>
            <h3 class="text-lg font-bold text-red-700">删除岗位</h3>
            <p class="text-sm text-gray-600 mt-1">此操作将把岗位标记为已关闭且不可再编辑，确定继续吗？</p>
          </div>
        </div>
        <div class="flex justify-end gap-3">
          <button
            type="button"
            class="px-4 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50 transition-colors min-h-[40px]"
            :disabled="jobDeleteLoading"
            @click="cancelDeleteJob"
          >
            取消
          </button>
          <button
            type="button"
            class="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-sm font-medium transition-colors min-h-[40px] disabled:opacity-50"
            :disabled="jobDeleteLoading"
            @click="confirmDeleteJob"
          >
            {{ jobDeleteLoading ? '删除中…' : '确认删除' }}
          </button>
        </div>
      </div>
      </div>
    </Teleport>

    <div class="fixed bottom-4 right-4 z-[110] flex flex-col gap-2 items-end pointer-events-none">
      <div
        v-for="t in toasts"
        :key="t.id"
        class="pointer-events-auto max-w-sm rounded-lg shadow-lg border px-4 py-3 text-sm font-medium transition-all"
        :class="
          t.type === 'success'
            ? 'bg-emerald-50 text-emerald-900 border-emerald-200'
            : t.type === 'error'
              ? 'bg-red-50 text-red-900 border-red-200'
              : t.type === 'warning'
                ? 'bg-amber-50 text-amber-900 border-amber-200'
                : 'bg-white text-gray-800 border-gray-200'
        "
      >
        {{ t.message }}
      </div>
    </div>
  </div>
</template>

