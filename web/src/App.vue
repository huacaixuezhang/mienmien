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
  createResumeDocumentMine,
  listResumeDocuments,
  listAllResumeDocuments,
  getResumeDocument,
  getResumeDocumentById,
  updateResumeDocument,
  updateResumeDocumentById,
  deleteResumeDocument,
  deleteResumeDocumentEntire,
  linkResumeToSpace,
  createInterview,
  listInterview,
  createJobPosition,
  updateJobPosition,
  listJobPositions,
  listAllJobPositions,
  linkJobToSpace,
  unlinkJobFromSpace,
  closeJobPosition,
  getAnswerBank,
  saveAnswerBank,
  getModelConfig,
  saveModelConfig,
  testModelConfig,
  listDbInspectorTables,
  listDbInspectorTableRows,
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
  { key: "job", iconClass: "fa-solid fa-briefcase", label: "岗位管理" },
  { key: "space-mgmt", iconClass: "fa-solid fa-layer-group", label: "空间管理" }
];

/** 允许查看「库表看板」的手机号（与后端 DbInspectorApplicationService 一致）。 */
const DB_INSPECTOR_ALLOWED_PHONE = "19955347072";

const sidebarSystemNav = computed(() => {
  const items = [
    { key: "dashboard", iconClass: "fa-solid fa-gauge-high", label: "仪表盘" },
    { key: "recycle", iconClass: "fa-solid fa-trash", label: "回收站" },
    { key: "user", iconClass: "fa-solid fa-user", label: "用户管理" },
    { key: "config", iconClass: "fa-solid fa-sliders", label: "系统设置" }
  ];
  if (currentUser.value?.phone === DB_INSPECTOR_ALLOWED_PHONE) {
    items.push({ key: "db-inspector", iconClass: "fa-solid fa-database", label: "库表看板" });
  }
  return items;
});

const activeTab = ref("resume");
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
/** 详情编辑时锁定所属空间，避免聚合列表中 resumeId 歧义 */
const resumeEditingSpaceId = ref("");
/** 空间管理：各空间下的简历与岗位快照 */
const spaceMgmtLoading = ref(false);
const spaceMgmtRows = ref([]);
/** 空间管理：从「简历管理 / 岗位管理」聚合列表选择条目，在本空间建立绑定（弹窗） */
const showSpaceMgmtBindModal = ref(false);
const spaceMgmtBindKind = ref("");
const spaceMgmtBindTargetSpaceId = ref("");
const spaceMgmtBindPickLoading = ref(false);
/** { spaceId, name, items: [] } */
const spaceMgmtBindPickGroups = ref([]);
const spaceMgmtBindActionLoading = ref(false);
/** 空间管理：简历只读详情弹窗 */
const spaceMgmtResumeDetailOpen = ref(false);
const spaceMgmtResumeDetailLoading = ref(false);
/** 当前展示的简历文档（getResumeDocument 返回） */
const spaceMgmtResumeDetailDoc = ref(null);
const spaceMgmtResumeDetailSpaceLabel = ref("");

const currentSpace = computed(() => spaces.value.find((x) => x.spaceId === currentSpaceId.value) || null);

const spaceNameLookup = computed(() => {
  const m = {};
  for (const s of spaces.value || []) {
    if (s?.spaceId) m[s.spaceId] = (s.name || "").trim() || s.spaceId;
  }
  return m;
});

function spaceDisplayName(spaceId) {
  if (!spaceId) return "—";
  return spaceNameLookup.value[spaceId] || spaceId;
}

function activeJobsInMgmtRow(jobList) {
  return (Array.isArray(jobList) ? jobList : []).filter((j) => (j.status || "ACTIVE") === "ACTIVE");
}

function openRenameForSpaceRow(sp) {
  if (!sp?.spaceId) return;
  resetPanelModalDrag();
  renameTargetSpaceId.value = sp.spaceId;
  renameSpaceName.value = sp.name || "";
  showRenameSpaceModal.value = true;
}

const resumeBlocks = reactive([]);
/** 简历模块列表项的稳定 key，禁止用 title 作 key（编辑标题会导致整卡重挂载、失焦与内容异常） */
function newResumeBlockId() {
  return typeof crypto !== "undefined" && crypto.randomUUID
    ? crypto.randomUUID()
    : `rb-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`;
}
/** 当前选中的简历（服务端 resumeId）；仅在详情页编辑时使用 */
const selectedResumeId = ref("");
/** 简历管理：list = 卡片列表；detail = 某份简历的模块详情 */
const resumeUiPhase = ref("list");
/** 简历展示名（与模块一并持久化到 mm_resume_document） */
const resumeDisplayName = ref("");
/** 与上次保存一致的 JSON 快照，用于判断草稿是否变更 */
const resumeDraftBaseline = ref("");
const creatingResume = ref(false);
const resumeSaveLoading = ref(false);
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
/** 新增/编辑岗位所属空间（聚合列表下与「当前工作空间」解耦） */
const jobModalTargetSpaceId = ref("");
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

const resumes = ref([]);
const jobs = ref([]);

const sortedResumeList = computed(() =>
  [...(Array.isArray(resumes.value) ? resumes.value : [])].sort((a, b) => {
    const ta = Date.parse(a.updatedAt || a.createdAt || 0) || 0;
    const tb = Date.parse(b.updatedAt || b.createdAt || 0) || 0;
    return tb - ta;
  })
);

const selectedResumeRow = computed(() => {
  const id = selectedResumeId.value;
  if (!id) return null;
  const list = Array.isArray(resumes.value) ? resumes.value : [];
  return list.find((r) => String(r.resumeId) === String(id)) || null;
});

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
/** 开发环境预填百炼 Anthropic 网关与模型名，便于联调；API Key 勿写入代码，本地自行粘贴或使用 Mock。 */
const modelConfig = reactive({
  provider: "aliyun-bailian",
  baseUrl: import.meta.env.DEV ? "https://coding.dashscope.aliyuncs.com/apps/anthropic" : "",
  apiKey: "",
  modelName: import.meta.env.DEV ? "qwen3.6-plus" : "",
  testPrompt: "请输出一句“连接测试成功”"
});
const testingModelConfig = ref(false);
const showBailianApiKey = ref(true);
const modelConfigTestResult = ref("");
const dbInspectorLoading = ref(false);
const dbInspectorTables = ref([]);
const dbInspectorSelectedTable = ref("");
const dbInspectorColumns = ref([]);
const dbInspectorRows = ref([]);
const dbInspectorRowCount = ref(0);
const dbInspectorOffset = ref(0);
const dbInspectorLimit = ref(100);
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
    "space-mgmt": "空间管理",
    answer: "标准题库",
    mock: "模拟面试",
    interview: "正式面试",
    recycle: "回收站",
    config: "系统设置",
    user: "用户管理",
    "db-inspector": "库表看板"
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

function showToast(message, type = "info") {
  const id = ++toastSeq;
  toasts.value.push({ id, message, type });
  setTimeout(() => {
    toasts.value = toasts.value.filter((t) => t.id !== id);
  }, 3200);
}

function syncJobFormFromFirstJob() {
  const list = (jobs.value || []).filter((j) => (j.status || "ACTIVE") === "ACTIVE");
  const scoped = currentSpaceId.value
    ? list.filter((j) => rowSpaceIds(j).includes(String(currentSpaceId.value)))
    : list;
  const j = scoped[0] || list[0];
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
  if (spaceMgmtResumeDetailOpen.value) closeSpaceMgmtResumeDetail();
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
  jobModalTargetSpaceId.value = currentSpaceId.value || "";
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
  jobModalTargetSpaceId.value = pickLinkedSpaceIdForApi(row) || currentSpaceId.value || "";
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
  const targetSpace = (jobModalTargetSpaceId.value || currentSpaceId.value || "").trim();
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
      const payload = { title, company, location, baseRange };
      if (targetSpace) payload.spaceId = targetSpace;
      await createJobPosition(payload);
      showToast("岗位已创建", "success");
    }
    await loadAggregatedJobs();
    syncJobFormFromFirstJob();
    closeJobModal();
  } catch (e) {
    showToast(e?.message || "保存失败", "error");
  } finally {
    jobModalSaving.value = false;
  }
}

function formatJobDate(iso) {
  if (iso == null || iso === "") return "—";
  const s = typeof iso === "string" ? iso : String(iso);
  return s.split("T")[0] || "—";
}

function openImportJd() {
  const list = activeJobsList.value.filter(
    (j) => !currentSpaceId.value || rowSpaceIds(j).includes(String(currentSpaceId.value))
  );
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
    await loadAggregatedJobs();
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

/** 简历正文首行元数据，与模块「【标题】」区分，避免单独加表 */
const RESUME_META_PREFIX = "MMIEN_RESUME_META:";

function parseResumeContentPackage(raw) {
  const trimmed = (raw || "").trim();
  let displayName = "";
  let blocksPart = trimmed;
  if (trimmed.startsWith(RESUME_META_PREFIX)) {
    const nl = trimmed.indexOf("\n");
    const metaLine = nl >= 0 ? trimmed.slice(0, nl) : trimmed;
    try {
      const jsonStr = metaLine.slice(RESUME_META_PREFIX.length).trim();
      const o = JSON.parse(jsonStr);
      if (typeof o.name === "string") displayName = o.name.trim();
    } catch {
      /* 忽略损坏的元数据行 */
    }
    blocksPart = nl >= 0 ? trimmed.slice(nl + 1).replace(/^\n+/, "").trim() : "";
  }
  return { displayName, blocksPart };
}

function deriveDefaultResumeNameFromBlocksPart(blocksPart) {
  const t = (blocksPart || "").trim();
  if (!t) return "未命名简历";
  const firstChunk = t.split("\n\n").find((c) => c.trim()) || "";
  const line1 = (firstChunk.split("\n")[0] || "").trim();
  if (line1.startsWith("【") && line1.endsWith("】")) {
    const inner = line1.slice(1, -1).trim();
    if (inner) return inner.length > 40 ? `${inner.slice(0, 40)}…` : inner;
  }
  return "未命名简历";
}

function resumeRowPrimaryTitle(row) {
  if (!row) return "简历";
  if ((row.name || "").trim()) return row.name.trim();
  if (Array.isArray(row.modules) && row.modules.length > 0) {
    const t = (row.modules[0].title || "").trim();
    if (t) return t.length > 40 ? `${t.slice(0, 40)}…` : t;
  }
  const { displayName, blocksPart } = parseResumeContentPackage(row.content || "");
  return (displayName || deriveDefaultResumeNameFromBlocksPart(blocksPart)) || "未命名简历";
}

function resumeBindLabel(row) {
  if (!row) return "简历";
  return resumeRowPrimaryTitle(row);
}

/** 简历关联的空间 id 列表（兼容旧接口仅有 spaceId） */
function rowSpaceIds(row) {
  if (!row) return [];
  if (Array.isArray(row.spaceIds) && row.spaceIds.length) return row.spaceIds.map(String);
  if (row.spaceId) return [String(row.spaceId)];
  return [];
}

/** 调用需 spaceId 路径的接口时，优先当前工作空间，否则取 row 的首个关联空间（简历/岗位共用） */
function pickLinkedSpaceIdForApi(row) {
  if (!row) return "";
  const ids = rowSpaceIds(row);
  const cur = String(currentSpaceId.value || "");
  if (cur && ids.includes(cur)) return cur;
  return ids[0] || "";
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
  if (!currentUser.value) return;
  try {
    const listed = await listAllResumeDocuments();
    bindSourceResumes.value = Array.isArray(listed) ? listed : [];
    const list = await listAllJobPositions();
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
  addingSpace.value = true;
  try {
    const created = await createSpace({ name });
    const newId = created?.spaceId;
    if (!newId) {
      throw new Error("创建空间未返回 spaceId");
    }
    if (newSpaceBindResumeId.value) {
      const r = bindSourceResumes.value.find((x) => String(x.resumeId) === String(newSpaceBindResumeId.value));
      if (r) {
        await linkResumeToSpace(newId, r.resumeId);
      }
    }
    for (const jid of [...newSpaceBindJobIds.value]) {
      const j = bindSourceJobs.value.find((x) => x.positionId === jid);
      if (j) {
        await linkJobToSpace(newId, j.positionId);
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
  if (activeTab.value === "space-mgmt") {
    await loadSpaceManagementOverview();
  }
}

function buildResumeContentFromBlocks() {
  return resumeBlocks.map((b) => `【${b.title}】\n${b.text}`).join("\n\n");
}

/** 与后端 `UpsertResumeDocumentRequest` 对齐的快照，用于脏检测与保存 */
function serializeResumeDraft() {
  const name = (resumeDisplayName.value || "").trim() || "未命名简历";
  const modules = resumeBlocks.map((b) => ({
    id: String(b.id || newResumeBlockId()),
    title: b.title ?? "",
    text: b.text ?? ""
  }));
  if (modules.length === 0) {
    modules.push({ id: newResumeBlockId(), title: "模块1", text: "" });
  }
  return JSON.stringify({ name, modules });
}

/** 将编辑器恢复为上次保存快照（用于「放弃修改」） */
function revertResumeEditorToBaseline() {
  const raw = (resumeDraftBaseline.value || "").trim();
  if (!raw) {
    clearUndoState();
    return;
  }
  try {
    const data = JSON.parse(raw);
    resumeDisplayName.value = (data.name || "").trim() || "未命名简历";
    const mods = Array.isArray(data.modules) ? data.modules : [];
    resumeBlocks.splice(
      0,
      resumeBlocks.length,
      ...mods.map((m) => ({
        id: m.id || newResumeBlockId(),
        title: m.title ?? "未命名模块",
        text: m.text ?? ""
      }))
    );
    if (resumeBlocks.length === 0) {
      resumeBlocks.push({ id: newResumeBlockId(), title: "模块1", text: "" });
    }
  } catch {
    /* ignore */
  }
  clearUndoState();
}

function isResumeDetailDirty() {
  return resumeUiPhase.value === "detail" && serializeResumeDraft() !== resumeDraftBaseline.value;
}

/**
 * 离开简历详情前：若有未保存修改则提示保存或放弃。
 * @returns 是否允许继续离开（保存成功、无修改、或用户确认放弃）
 */
async function confirmResumeDetailLeave() {
  if (!isResumeDetailDirty()) return true;
  const saveFirst = confirm(
    "当前简历有未保存的修改。\n\n「确定」= 保存后再离开\n「取消」= 下一步（不保存或继续编辑）"
  );
  if (saveFirst) {
    try {
      await saveResume();
      return true;
    } catch (e) {
      showToast(e?.message || "保存失败", "error");
      return false;
    }
  }
  const discard = confirm(
    "确定放弃未保存的修改并离开？\n\n「确定」= 放弃修改并离开\n「取消」= 留在本页继续编辑"
  );
  if (!discard) return false;
  revertResumeEditorToBaseline();
  return true;
}

function onResumeBeforeUnload(e) {
  if (!isResumeDetailDirty()) return;
  e.preventDefault();
  e.returnValue = "";
}

function parseContentIntoResumeBlocks(rawContent) {
  resumeBlocks.splice(0, resumeBlocks.length);
  clearUndoState();
  const { displayName, blocksPart } = parseResumeContentPackage(rawContent);
  resumeDisplayName.value = displayName || deriveDefaultResumeNameFromBlocksPart(blocksPart);
  const trimmed = (blocksPart || "").trim();
  if (!trimmed) {
    resumeBlocks.push({ id: newResumeBlockId(), title: "模块1", text: "" });
    return;
  }
  const chunks = trimmed.split("\n\n").filter((c) => c.trim());
  chunks.forEach((chunk, idx) => {
    const lines = chunk.split("\n");
    const titleLine = (lines[0] || "").trim();
    const title = titleLine.startsWith("【") && titleLine.endsWith("】")
      ? titleLine.slice(1, -1)
      : `模块${idx + 1}`;
    const text = lines.slice(1).join("\n");
    resumeBlocks.push({ id: newResumeBlockId(), title, text });
  });
}

function hydrateResumeFromRow(row) {
  if (!row) {
    selectedResumeId.value = "";
    resumeEditingSpaceId.value = "";
    resumeDisplayName.value = "";
    resumeBlocks.splice(0, resumeBlocks.length);
    resumeDraftBaseline.value = "";
    clearUndoState();
    return;
  }
  selectedResumeId.value = String(row.resumeId);
  resumeEditingSpaceId.value = pickLinkedSpaceIdForApi(row);
  if (Array.isArray(row.modules) && row.modules.length > 0) {
    resumeDisplayName.value = (row.name || "").trim() || "未命名简历";
    resumeBlocks.splice(
      0,
      resumeBlocks.length,
      ...row.modules.map((m) => ({
        id: m.id || newResumeBlockId(),
        title: m.title ?? "未命名模块",
        text: m.text ?? ""
      }))
    );
  } else {
    parseContentIntoResumeBlocks(row.content || "");
    if ((row.name || "").trim()) {
      resumeDisplayName.value = row.name.trim();
    }
  }
  resumeDraftBaseline.value = serializeResumeDraft();
  clearUndoState();
}

function resumeUpdatedLabel(row) {
  const raw = row?.updatedAt || row?.createdAt;
  if (!raw) return "";
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

async function refreshCurrentResumeDetailFromServer() {
  if (!selectedResumeId.value) return;
  const doc = await getResumeDocumentById(selectedResumeId.value);
  hydrateResumeFromRow(doc);
}

async function openResumeDetail(row) {
  if (!row?.resumeId) return;
  if (resumeUiPhase.value === "detail") {
    if (String(selectedResumeId.value) === String(row.resumeId)) {
      return;
    }
    if (!(await confirmResumeDetailLeave())) return;
  }
  resumeSaveLoading.value = true;
  try {
    const doc = await getResumeDocumentById(row.resumeId);
    hydrateResumeFromRow(doc);
    resumeUiPhase.value = "detail";
  } catch (e) {
    showToast(e?.message || "加载简历失败", "error");
  } finally {
    resumeSaveLoading.value = false;
  }
}

async function backToResumeList() {
  if (!(await confirmResumeDetailLeave())) return;
  resumeUiPhase.value = "list";
  hydrateResumeFromRow(null);
  await loadSpaceData();
}

async function confirmDeleteResumeDoc(row, ev) {
  ev?.stopPropagation?.();
  if (!row?.resumeId) return;
  const title = resumeRowPrimaryTitle(row);
  if (!confirm(`确定删除简历「${title}」？将从所有已关联空间移除且不可恢复。`)) return;
  try {
    await deleteResumeDocumentEntire(row.resumeId);
    showToast("已删除", "success");
    if (String(selectedResumeId.value) === String(row.resumeId) && resumeUiPhase.value === "detail") {
      resumeUiPhase.value = "list";
      hydrateResumeFromRow(null);
    }
    await loadAggregatedResumes();
  } catch (e) {
    showToast(e?.message || "删除失败", "error");
  }
}

async function deleteCurrentResumeFromDetail() {
  if (!(await confirmResumeDetailLeave())) return;
  const row = selectedResumeRow.value;
  if (!row?.resumeId) return;
  await confirmDeleteResumeDoc(row);
}

function resumeModuleCount(row) {
  if (!row) return 0;
  if (Array.isArray(row.modules)) return row.modules.length;
  const { blocksPart } = parseResumeContentPackage(row.content || "");
  const t = (blocksPart || "").trim();
  if (!t) return 0;
  return t.split("\n\n").filter((c) => c.trim()).length;
}

function resumeCardPreview(row) {
  if (!row) return "（暂无模块正文）";
  if (Array.isArray(row.modules) && row.modules.length > 0) {
    const t = (row.modules.map((m) => (m.text || "").trim()).find(Boolean) || "").replace(/\s+/g, " ").trim();
    if (!t) return "（暂无模块正文）";
    return t.length > 96 ? `${t.slice(0, 96)}…` : t;
  }
  const { blocksPart } = parseResumeContentPackage(row.content || "");
  const t = (blocksPart || "").replace(/\s+/g, " ").trim();
  if (!t) return "（暂无模块正文）";
  return t.length > 96 ? `${t.slice(0, 96)}…` : t;
}

async function createNewResumeDoc() {
  if (creatingResume.value) return;
  if (!(await confirmResumeDetailLeave())) return;
  creatingResume.value = true;
  try {
    const list = Array.isArray(resumes.value) ? resumes.value : [];
    const initialName = `简历 ${list.length + 1}`;
    const modules = [{ id: newResumeBlockId(), title: "基本信息", text: "" }];
    const body = { name: initialName, modules };
    if (currentSpaceId.value) body.spaceId = currentSpaceId.value;
    const created = await createResumeDocumentMine(body);
    await loadAggregatedResumes();
    resumeUiPhase.value = "detail";
    hydrateResumeFromRow(created);
    showToast("已创建，请在详情页编辑模块", "success");
  } catch (e) {
    showToast(e?.message || "创建简历失败", "error");
  } finally {
    creatingResume.value = false;
  }
}

async function openAddSpaceModal() {
  resetPanelModalDrag();
  if (activeTab.value === "resume" && resumeUiPhase.value === "detail" && currentSpaceId.value && currentUser.value) {
    if (!(await confirmResumeDetailLeave())) return;
  }
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

async function openRenameSpaceModal() {
  if (activeTab.value === "resume" && resumeUiPhase.value === "detail") {
    if (!(await confirmResumeDetailLeave())) return;
  }
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

async function switchSpace(spaceId) {
  if (!spaceId) return;
  if (String(spaceId) === String(currentSpaceId.value)) {
    sidebarOpen.value = false;
    return;
  }
  if (!(await confirmResumeDetailLeave())) return;
  currentSpaceId.value = spaceId;
  sidebarOpen.value = false;
  resetTransientDrafts();
  resumes.value = [];
  selectedResumeId.value = "";
  resumeDisplayName.value = "";
  resumeDraftBaseline.value = "";
  resumeBlocks.splice(0, resumeBlocks.length);
  clearUndoState();
  resumeUiPhase.value = "list";
  loadSpaceData();
}

async function moveSpaceToRecycleBin(spaceId) {
  if (!spaceId) return;
  if (!(await confirmResumeDetailLeave())) return;
  const label = spaceDisplayName(spaceId);
  if (!confirm(`确认将空间「${label}」移入回收站？\n空间将进入回收站，30天后自动清除。`)) return;
  closeSpaceMgmtBindModal();
  closeSpaceMgmtResumeDetail();
  if (jobDetailModalOpen.value) closeJobDetailModal();
  try {
    await recycleSpace(spaceId);
    const wasCurrent = String(currentSpaceId.value) === String(spaceId);
    if (wasCurrent) {
      currentSpaceId.value = "";
    }
    await refreshSpaces();
    activeTab.value = "recycle";
    await loadSpaceData();
    showToast("空间已移入回收站", "success");
  } catch (e) {
    showToast(e?.message || "操作失败", "error");
  }
}

async function restoreFromRecycleBin(spaceId) {
  if (!confirm("确认还原该空间？")) return;
  await restoreSpace(spaceId);
  currentSpaceId.value = spaceId;
  await refreshSpaces();
  await loadSpaceData();
}

async function switchTab(key) {
  if (!currentUser.value && key !== "user") {
    resetPanelModalDrag();
    showAuthModal.value = true;
    switchAccountInline.value = false;
    return;
  }
  if (key === "db-inspector" && currentUser.value?.phone !== DB_INSPECTOR_ALLOWED_PHONE) {
    showToast("无权访问库表看板", "warning");
    return;
  }
  if (key === "resume" && activeTab.value === "resume" && resumeUiPhase.value === "detail") {
    if (!(await confirmResumeDetailLeave())) return;
    resumeUiPhase.value = "list";
    hydrateResumeFromRow(null);
    sidebarOpen.value = false;
    loadSpaceData();
    return;
  }
  if (activeTab.value === "resume" && resumeUiPhase.value === "detail" && key !== "resume") {
    if (!(await confirmResumeDetailLeave())) return;
    resumeUiPhase.value = "list";
    hydrateResumeFromRow(null);
  }
  if (key === "resume") {
    resumeUiPhase.value = "list";
  }
  activeTab.value = key;
  sidebarOpen.value = false;
  loadSpaceData();
}

async function openConfigPage() {
  await switchTab("config");
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
  if (!(await confirmResumeDetailLeave())) return;
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
  if (!(await confirmResumeDetailLeave())) return;
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

function pickModelConfigString(config, camel, snake) {
  const v = config?.[camel];
  if (v != null && String(v).trim() !== "") return String(v);
  const s = config?.[snake];
  if (s != null && String(s).trim() !== "") return String(s);
  return "";
}

async function loadBailianConfig() {
  if (!currentUser.value) return;
  const config = await getModelConfig();
  modelConfig.provider = (config.provider && String(config.provider).trim()) || "aliyun-bailian";
  modelConfig.baseUrl = pickModelConfigString(config, "baseUrl", "base_url");
  modelConfig.apiKey = pickModelConfigString(config, "apiKey", "api_key");
  modelConfig.modelName = pickModelConfigString(config, "modelName", "model_name");
}

async function saveBailianConfig() {
  if (!currentUser.value) {
    alert("请先登录");
    return;
  }
  await saveModelConfig({
    provider: modelConfig.provider || "aliyun-bailian",
    baseUrl: modelConfig.baseUrl.trim(),
    apiKey: modelConfig.apiKey.trim(),
    modelName: modelConfig.modelName.trim()
  });
  alert("百炼连接配置已保存到后端（当前账号下全部空间共享）");
}

/** 百炼 Anthropic 应用网关（Coding Plan / 按量等），官方路径为 POST .../apps/anthropic/v1/messages */
function isAnthropicAppsGatewayBase(baseUrl) {
  return /\/apps\/anthropic(\/|$)/i.test(baseUrl.trim());
}

function resolveAnthropicAppsMessagesUrl(baseUrl) {
  let t = baseUrl.trim().replace(/\/+$/, "");
  if (!t) return "";
  if (/\/v1\/messages$/i.test(t)) return t;
  if (t.endsWith("/chat/completions")) {
    t = t.slice(0, -"/chat/completions".length);
  }
  if (/\/v1$/i.test(t)) {
    t = t.slice(0, -"/v1".length);
  }
  return `${t}/v1/messages`;
}

/** OpenAI 兼容模式用 .../compatible-mode/v1 + /chat/completions；Anthropic 网关用 .../apps/anthropic + /v1/messages */
function resolveBailianInvoke(baseUrl) {
  const trimmed = baseUrl.trim().replace(/\/+$/, "");
  if (!trimmed) return { mode: null, url: "" };
  if (isAnthropicAppsGatewayBase(trimmed)) {
    return { mode: "anthropic", url: resolveAnthropicAppsMessagesUrl(trimmed) };
  }
  if (trimmed.endsWith("/chat/completions")) {
    return { mode: "openai-chat", url: trimmed };
  }
  if (trimmed.endsWith("/v1")) {
    return { mode: "openai-chat", url: `${trimmed}/chat/completions` };
  }
  return { mode: "openai-chat", url: `${trimmed}/chat/completions` };
}

function extractAnthropicAssistantText(data) {
  const blocks = data?.content;
  if (!Array.isArray(blocks)) return "";
  return blocks.map((b) => (b && typeof b.text === "string" ? b.text : "")).join("");
}

function ensureBailianConfigReady() {
  const { mode, url: invokeUrl } = resolveBailianInvoke(modelConfig.baseUrl);
  const apiKey = modelConfig.apiKey.trim();
  const modelName = modelConfig.modelName.trim();
  if (!invokeUrl || !apiKey || !modelName) {
    throw new Error("请先在系统设置中完整填写 Base URL / API 密钥 / 模型名称");
  }
  return { invokeUrl, mode, apiKey, modelName };
}

async function callBailianChat(userPrompt) {
  const { invokeUrl, mode, apiKey, modelName } = ensureBailianConfigReady();
  /** 百炼文档：x-api-key 与 Authorization: Bearer 二选一；Coding Plan 的 sk-* 密钥在浏览器侧与 Bearer 更一致 */
  const headers =
    mode === "anthropic"
      ? {
          "Content-Type": "application/json",
          ...(apiKey.startsWith("sk-")
            ? { Authorization: `Bearer ${apiKey}` }
            : { "x-api-key": apiKey })
        }
      : {
          "Content-Type": "application/json",
          Authorization: `Bearer ${apiKey}`
        };
  const body =
    mode === "anthropic"
      ? JSON.stringify({
          model: modelName,
          max_tokens: 1024,
          stream: false,
          messages: [{ role: "user", content: userPrompt }],
          thinking: { type: "disabled" }
        })
      : JSON.stringify({
          model: modelName,
          stream: false,
          messages: [{ role: "user", content: userPrompt }]
        });
  const res = await fetch(invokeUrl, {
    method: "POST",
    headers,
    credentials: "omit",
    body
  });
  const rawText = await res.text();
  let data = {};
  if (rawText) {
    try {
      data = JSON.parse(rawText);
    } catch {
      throw new Error(`响应非 JSON（HTTP ${res.status}）：${rawText.slice(0, 240)}`);
    }
  }
  if (!res.ok) {
    const msg =
      data?.error?.message ||
      data?.message ||
      (typeof data?.error === "string" ? data.error : null) ||
      `HTTP ${res.status}`;
    throw new Error(msg);
  }
  if (mode === "anthropic") {
    const text = extractAnthropicAssistantText(data);
    if (!text) {
      throw new Error("响应成功但未解析到 assistant 文本（请核对 Base URL 是否为 …/apps/anthropic 及模型名）");
    }
    return text;
  }
  const openaiText = data?.choices?.[0]?.message?.content || "";
  if (!openaiText) {
    throw new Error("响应成功但未解析到 choices[0].message.content（OpenAI 兼容网关请使用 …/compatible-mode/v1）");
  }
  return openaiText;
}

/**
 * 为 true 时，「测试调用」仅走 Mock，不调后端。
 * 默认关闭；需本地纯前端演练时设置 VITE_MOCK_BAILIAN_TEST=true。
 */
function isMockBailianTestEnabled() {
  const v = import.meta.env.VITE_MOCK_BAILIAN_TEST;
  return v === "true" || v === "1";
}

async function loadDbInspectorTables() {
  if (currentUser.value?.phone !== DB_INSPECTOR_ALLOWED_PHONE) return;
  dbInspectorLoading.value = true;
  try {
    const data = await listDbInspectorTables();
    dbInspectorTables.value = Array.isArray(data?.tableNames) ? data.tableNames : [];
    if (!dbInspectorSelectedTable.value && dbInspectorTables.value.length) {
      dbInspectorSelectedTable.value = dbInspectorTables.value[0];
      dbInspectorOffset.value = 0;
      await fetchDbInspectorRows();
    } else if (dbInspectorSelectedTable.value && !dbInspectorTables.value.includes(dbInspectorSelectedTable.value)) {
      dbInspectorSelectedTable.value = dbInspectorTables.value[0] || "";
      dbInspectorOffset.value = 0;
      if (dbInspectorSelectedTable.value) await fetchDbInspectorRows();
      else {
        dbInspectorColumns.value = [];
        dbInspectorRows.value = [];
        dbInspectorRowCount.value = 0;
      }
    } else if (dbInspectorSelectedTable.value) {
      await fetchDbInspectorRows();
    }
  } catch (e) {
    showToast(e?.message || "加载表列表失败", "error");
    dbInspectorTables.value = [];
  } finally {
    dbInspectorLoading.value = false;
  }
}

async function fetchDbInspectorRows() {
  const t = dbInspectorSelectedTable.value;
  if (!t || currentUser.value?.phone !== DB_INSPECTOR_ALLOWED_PHONE) return;
  dbInspectorLoading.value = true;
  try {
    const data = await listDbInspectorTableRows(t, dbInspectorOffset.value, dbInspectorLimit.value);
    dbInspectorColumns.value = Array.isArray(data?.columnNames) ? data.columnNames : [];
    dbInspectorRows.value = Array.isArray(data?.rows) ? data.rows : [];
    dbInspectorRowCount.value = typeof data?.rowCount === "number" ? data.rowCount : Number(data?.rowCount) || 0;
  } catch (e) {
    showToast(e?.message || "加载表数据失败", "error");
    dbInspectorColumns.value = [];
    dbInspectorRows.value = [];
    dbInspectorRowCount.value = 0;
  } finally {
    dbInspectorLoading.value = false;
  }
}

async function selectDbInspectorTable(tableName) {
  if (!tableName) return;
  dbInspectorSelectedTable.value = tableName;
  dbInspectorOffset.value = 0;
  await fetchDbInspectorRows();
}

function dbInspectorPrevPage() {
  const next = dbInspectorOffset.value - dbInspectorLimit.value;
  dbInspectorOffset.value = Math.max(0, next);
  fetchDbInspectorRows();
}

function dbInspectorNextPage() {
  const next = dbInspectorOffset.value + dbInspectorLimit.value;
  if (next >= dbInspectorRowCount.value) return;
  dbInspectorOffset.value = next;
  fetchDbInspectorRows();
}

function formatDbInspectorCell(val) {
  if (val == null || val === "") return "—";
  if (typeof val === "object") {
    try {
      return JSON.stringify(val);
    } catch {
      return String(val);
    }
  }
  return String(val);
}

async function testBailianConfigConnection() {
  if (testingModelConfig.value) return;
  if (!currentUser.value) return;
  testingModelConfig.value = true;
  modelConfigTestResult.value = "";
  try {
    if (isMockBailianTestEnabled()) {
      await new Promise((r) => setTimeout(r, 350));
      const prompt = modelConfig.testPrompt || "连接测试";
      const tail = prompt.length > 120 ? `${prompt.slice(0, 120)}…` : prompt;
      modelConfigTestResult.value =
        `调用成功（Mock，未请求后端）：连接测试成功\n（模拟模型：${modelConfig.modelName || "（未填）"}；提示词：${tail}）`;
      return;
    }
    const data = await testModelConfig({
      testPrompt: modelConfig.testPrompt || "连接测试",
      modelName: (modelConfig.modelName || "").trim()
    });
    const answer = data?.assistantText ?? "";
    modelConfigTestResult.value = answer ? `调用成功：${answer}` : "调用成功（无正文）";
  } catch (e) {
    modelConfigTestResult.value = `调用失败：${e?.message || "未知错误"}`;
  } finally {
    testingModelConfig.value = false;
  }
}

async function loadAggregatedResumes() {
  if (!currentUser.value) {
    resumes.value = [];
    return;
  }
  try {
    const raw = await listAllResumeDocuments();
    resumes.value = Array.isArray(raw) ? raw : [];
  } catch {
    resumes.value = [];
  }
  if (resumeUiPhase.value === "detail" && selectedResumeId.value) {
    const exists = resumes.value.some((r) => String(r.resumeId) === String(selectedResumeId.value));
    if (!exists) {
      showToast("当前简历已不存在或已被删除", "warning");
      resumeUiPhase.value = "list";
      hydrateResumeFromRow(null);
    } else {
      try {
        await refreshCurrentResumeDetailFromServer();
      } catch {
        resumeUiPhase.value = "list";
        hydrateResumeFromRow(null);
      }
    }
  }
}

async function loadAggregatedJobs() {
  if (!currentUser.value) {
    jobs.value = [];
    syncJobFormFromFirstJob();
    return;
  }
  try {
    const raw = await listAllJobPositions();
    jobs.value = Array.isArray(raw) ? raw : [];
  } catch {
    jobs.value = [];
  }
  syncJobFormFromFirstJob();
}

async function loadSpaceManagementOverview() {
  if (!currentUser.value) {
    spaceMgmtRows.value = [];
    return;
  }
  spaceMgmtLoading.value = true;
  try {
    const active = Array.isArray(spaces.value) ? [...spaces.value] : [];
    if (active.length === 0) {
      spaceMgmtRows.value = [];
      return;
    }
    const rows = await Promise.all(
      active.map(async (s) => {
        let docs = [];
        let jobList = [];
        try {
          const raw = await listResumeDocuments(s.spaceId);
          docs = Array.isArray(raw) ? raw : [];
        } catch {
          docs = [];
        }
        try {
          const raw = await listJobPositions(s.spaceId);
          jobList = Array.isArray(raw) ? raw : [];
        } catch {
          jobList = [];
        }
        return {
          spaceId: s.spaceId,
          name: s.name || s.spaceId,
          createdAt: s.createdAt,
          updatedAt: s.updatedAt,
          resumes: docs,
          jobs: jobList
        };
      })
    );
    spaceMgmtRows.value = rows;
  } finally {
    spaceMgmtLoading.value = false;
  }
}

function selectSpaceAsCurrent(spaceId) {
  if (!spaceId) return;
  currentSpaceId.value = spaceId;
  sidebarOpen.value = false;
  showToast("已切换为当前工作空间", "success");
}

async function openSpaceMgmtResumeDetail(spaceId, doc) {
  if (!spaceId || !doc?.resumeId) return;
  if (jobDetailModalOpen.value) closeJobDetailModal();
  if (showSpaceMgmtBindModal.value) closeSpaceMgmtBindModal();
  resetPanelModalDrag();
  spaceMgmtResumeDetailOpen.value = true;
  spaceMgmtResumeDetailLoading.value = true;
  spaceMgmtResumeDetailDoc.value = null;
  spaceMgmtResumeDetailSpaceLabel.value = spaceDisplayName(spaceId);
  try {
    spaceMgmtResumeDetailDoc.value = await getResumeDocument(spaceId, doc.resumeId);
  } catch (e) {
    showToast(e?.message || "加载简历失败", "error");
    spaceMgmtResumeDetailOpen.value = false;
  } finally {
    spaceMgmtResumeDetailLoading.value = false;
  }
}

function closeSpaceMgmtResumeDetail() {
  resetPanelModalDrag();
  spaceMgmtResumeDetailOpen.value = false;
  spaceMgmtResumeDetailDoc.value = null;
  spaceMgmtResumeDetailSpaceLabel.value = "";
}

function spaceMgmtJobsForDisplay(jobList) {
  const list = Array.isArray(jobList) ? [...jobList] : [];
  list.sort((a, b) => {
    const ac = (a.status || "ACTIVE") === "ACTIVE" ? 0 : 1;
    const bc = (b.status || "ACTIVE") === "ACTIVE" ? 0 : 1;
    if (ac !== bc) return ac - bc;
    return (Date.parse(b.updatedAt) || 0) - (Date.parse(a.updatedAt) || 0);
  });
  return list;
}

function spaceMgmtCanOpenResumeBind() {
  return (Array.isArray(resumes.value) ? resumes.value : []).length > 0;
}

function spaceMgmtCanOpenJobBind() {
  return (Array.isArray(jobs.value) ? jobs.value : []).length > 0;
}

/** 与「简历管理」列表同源：全部简历，按更新时间倒序（不按空间分组） */
function buildBindResumePickGroups(targetSpaceId) {
  void targetSpaceId;
  const list = [...(Array.isArray(resumes.value) ? resumes.value : [])];
  if (list.length === 0) return [];
  list.sort((a, b) => {
    const ta = Date.parse(a.updatedAt || a.createdAt || 0) || 0;
    const tb = Date.parse(b.updatedAt || b.createdAt || 0) || 0;
    return tb - ta;
  });
  return [{ spaceId: "_all", name: "", items: list }];
}

/** 与「岗位管理」列表同源：全部活跃岗位，按更新时间倒序 */
function buildBindJobPickGroups(targetSpaceId) {
  void targetSpaceId;
  const list = (Array.isArray(jobs.value) ? jobs.value : []).filter((j) => (j.status || "ACTIVE") === "ACTIVE");
  if (list.length === 0) return [];
  const sorted = [...list].sort((a, b) => {
    const ta = Date.parse(a.updatedAt || a.createdAt || 0) || 0;
    const tb = Date.parse(b.updatedAt || b.createdAt || 0) || 0;
    return tb - ta;
  });
  return [{ spaceId: "_all", name: "", items: sorted }];
}

function resumeDuplicateInTargetSpace(item, targetSpaceId) {
  if (!item || !targetSpaceId) return false;
  const t = resumeRowPrimaryTitle(item).trim();
  if (!t) return false;
  const inTarget = (resumes.value || []).filter((r) => rowSpaceIds(r).includes(String(targetSpaceId)));
  return inTarget.some((r) => String(r.resumeId) !== String(item.resumeId) && resumeRowPrimaryTitle(r).trim() === t);
}

function jobDuplicateInTargetSpace(item, targetSpaceId) {
  if (!item || !targetSpaceId) return false;
  const lab = jobBindLabel(item).trim();
  if (!lab) return false;
  const inTarget = (jobs.value || []).filter((j) => rowSpaceIds(j).includes(String(targetSpaceId)));
  return inTarget.some((j) => String(j.positionId) !== String(item.positionId) && jobBindLabel(j).trim() === lab);
}

function spaceMgmtResumeBindStatus(item, targetSpaceId) {
  if (!item || !targetSpaceId) return { code: "bindable", label: "未绑定" };
  if (rowSpaceIds(item).includes(String(targetSpaceId))) {
    return { code: "in_space", label: "已绑定本空间" };
  }
  if (resumeDuplicateInTargetSpace(item, targetSpaceId)) {
    return { code: "dup", label: "目标空间已有同名" };
  }
  return { code: "bindable", label: "未绑定" };
}

function spaceMgmtJobBindStatus(item, targetSpaceId) {
  if (!item || !targetSpaceId) return { code: "bindable", label: "未绑定" };
  if (rowSpaceIds(item).includes(String(targetSpaceId))) {
    return { code: "in_space", label: "已绑定本空间" };
  }
  if (jobDuplicateInTargetSpace(item, targetSpaceId)) {
    return { code: "dup", label: "目标空间已有同标签" };
  }
  return { code: "bindable", label: "未绑定" };
}

function spaceMgmtResumeBoundToTarget(item) {
  const tid = spaceMgmtBindTargetSpaceId.value;
  return !!(item && tid && rowSpaceIds(item).includes(String(tid)));
}

function spaceMgmtJobBoundToTarget(item) {
  const tid = spaceMgmtBindTargetSpaceId.value;
  return !!(item && tid && rowSpaceIds(item).includes(String(tid)));
}

function spaceMgmtResumeDupWarn(item) {
  const tid = spaceMgmtBindTargetSpaceId.value;
  if (!item || !tid || spaceMgmtResumeBoundToTarget(item)) return false;
  return spaceMgmtResumeBindStatus(item, tid).code === "dup";
}

function spaceMgmtJobDupWarn(item) {
  const tid = spaceMgmtBindTargetSpaceId.value;
  if (!item || !tid || spaceMgmtJobBoundToTarget(item)) return false;
  return spaceMgmtJobBindStatus(item, tid).code === "dup";
}

function closeSpaceMgmtBindModal() {
  resetPanelModalDrag();
  showSpaceMgmtBindModal.value = false;
  spaceMgmtBindKind.value = "";
  spaceMgmtBindTargetSpaceId.value = "";
  spaceMgmtBindPickGroups.value = [];
  spaceMgmtBindPickLoading.value = false;
  spaceMgmtBindActionLoading.value = false;
}

async function openSpaceMgmtBindResumeModal(targetSpaceId) {
  if (!targetSpaceId) return;
  closeSpaceMgmtResumeDetail();
  if (activeTab.value === "resume" && resumeUiPhase.value === "detail") {
    if (!(await confirmResumeDetailLeave())) return;
  }
  spaceMgmtBindKind.value = "resume";
  spaceMgmtBindTargetSpaceId.value = targetSpaceId;
  spaceMgmtBindPickLoading.value = true;
  spaceMgmtBindPickGroups.value = [];
  resetPanelModalDrag();
  showSpaceMgmtBindModal.value = true;
  try {
    await loadAggregatedResumes();
    spaceMgmtBindPickGroups.value = buildBindResumePickGroups(targetSpaceId);
    if (!spaceMgmtBindPickGroups.value.length) {
      showToast("暂无简历，请先在「简历管理」中创建。", "warning");
    }
  } finally {
    spaceMgmtBindPickLoading.value = false;
  }
}

async function openSpaceMgmtBindJobModal(targetSpaceId) {
  if (!targetSpaceId) return;
  closeSpaceMgmtResumeDetail();
  if (activeTab.value === "resume" && resumeUiPhase.value === "detail") {
    if (!(await confirmResumeDetailLeave())) return;
  }
  spaceMgmtBindKind.value = "job";
  spaceMgmtBindTargetSpaceId.value = targetSpaceId;
  spaceMgmtBindPickLoading.value = true;
  spaceMgmtBindPickGroups.value = [];
  resetPanelModalDrag();
  showSpaceMgmtBindModal.value = true;
  try {
    await loadAggregatedJobs();
    spaceMgmtBindPickGroups.value = buildBindJobPickGroups(targetSpaceId);
    if (!spaceMgmtBindPickGroups.value.length) {
      showToast("暂无岗位，请先在「岗位管理」中添加。", "warning");
    }
  } finally {
    spaceMgmtBindPickLoading.value = false;
  }
}

async function toggleSpaceMgmtBindResume(item) {
  const target = spaceMgmtBindTargetSpaceId.value;
  const resumeId = item?.resumeId;
  if (!resumeId || !target) return;
  if (spaceMgmtBindActionLoading.value) return;
  const bound = rowSpaceIds(item).includes(String(target));
  spaceMgmtBindActionLoading.value = true;
  try {
    if (bound) {
      const ids = rowSpaceIds(item);
      if (ids.length === 1 && ids[0] === String(target)) {
        if (
          !confirm(
            "该简历仅关联到本空间。从本空间移除后，系统将删除该简历数据（所有空间不可恢复）。确定要解绑吗？"
          )
        ) {
          return;
        }
      }
      await deleteResumeDocument(target, resumeId);
      showToast("已从本空间解绑", "success");
    } else {
      await linkResumeToSpace(target, resumeId);
      showToast("已绑定到本空间", "success");
    }
    await loadAggregatedResumes();
    spaceMgmtBindPickGroups.value = buildBindResumePickGroups(target);
    await loadSpaceManagementOverview();
  } catch (e) {
    showToast(e?.message || "操作失败", "error");
  } finally {
    spaceMgmtBindActionLoading.value = false;
  }
}

async function toggleSpaceMgmtBindJob(item) {
  const target = spaceMgmtBindTargetSpaceId.value;
  if (!target || !item?.positionId) return;
  if (spaceMgmtBindActionLoading.value) return;
  const bound = rowSpaceIds(item).includes(String(target));
  spaceMgmtBindActionLoading.value = true;
  try {
    if (bound) {
      await unlinkJobFromSpace(target, item.positionId);
      showToast("已从本空间解绑", "success");
    } else {
      await linkJobToSpace(target, item.positionId);
      showToast("已绑定到本空间", "success");
    }
    await loadAggregatedJobs();
    spaceMgmtBindPickGroups.value = buildBindJobPickGroups(target);
    await loadSpaceManagementOverview();
  } catch (e) {
    showToast(e?.message || "操作失败", "error");
  } finally {
    spaceMgmtBindActionLoading.value = false;
  }
}

async function loadSpaceData() {
  if (activeTab.value === "db-inspector") {
    await loadDbInspectorTables();
    return;
  }
  if (activeTab.value === "recycle") {
    recycleBinSpaces.value = await listRecycleBinSpaces();
    return;
  }
  if (activeTab.value === "resume") {
    await loadAggregatedResumes();
    return;
  }
  if (activeTab.value === "job") {
    await loadAggregatedJobs();
    return;
  }
  if (activeTab.value === "space-mgmt") {
    await Promise.all([loadSpaceManagementOverview(), loadAggregatedResumes(), loadAggregatedJobs()]);
    return;
  }
  if (activeTab.value === "config") {
    await loadBailianConfig();
    return;
  }

  if (!currentSpaceId.value) return;

  if (activeTab.value === "dashboard") {
    interviews.value = await listInterview(currentSpaceId.value);
    return;
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
    await loadAggregatedJobs();
    if (activeTab.value === "interview") {
      hydrateInterviewFromLatestReal();
      mergeJobFormIntoProfile(realJobProfile);
    }
    if (activeTab.value === "mock") {
      hydrateMockFromLatestMock();
      mergeJobFormIntoProfile(mockJobProfile);
    }
  }
}

async function saveResume() {
  const row = selectedResumeRow.value;
  if (!row?.resumeId) return;
  const spaceId = pickLinkedSpaceIdForApi(row);
  resumeSaveLoading.value = true;
  try {
    const body = JSON.parse(serializeResumeDraft());
    if (spaceId) {
      await updateResumeDocument(spaceId, row.resumeId, body);
    } else {
      await updateResumeDocumentById(row.resumeId, body);
    }
    await loadAggregatedResumes();
    const refreshed = (Array.isArray(resumes.value) ? resumes.value : []).find(
      (r) => String(r.resumeId) === String(selectedResumeId.value)
    );
    if (refreshed) {
      hydrateResumeFromRow(refreshed);
    } else {
      resumeDraftBaseline.value = serializeResumeDraft();
    }
    if (showAddSpaceModal.value) {
      await loadBindSourceResources();
    }
  } finally {
    resumeSaveLoading.value = false;
  }
}

async function saveCurrentResumeManual() {
  try {
    await saveResume();
    showToast("简历已保存", "success");
  } catch (e) {
    showToast(e?.message || "保存失败", "error");
  }
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
  if (!selectedResumeId.value) {
    showToast("请先新增或选择一份简历", "warning");
    return;
  }
  const title = newResumeBlockTitle.value.trim() || `自定义模块${resumeBlocks.length + 1}`;
  resumeBlocks.push({ id: newResumeBlockId(), title, text: "" });
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
  const title = (jobForm.title || "").trim();
  if (!title) return;
  const activeList = jobs.value.filter((j) => (j.status || "ACTIVE") === "ACTIVE");
  const cur = String(currentSpaceId.value || "");
  const scoped = cur ? activeList.filter((j) => rowSpaceIds(j).includes(cur)) : activeList;
  const first = scoped[0] || activeList[0];
  const body = {
    title,
    company: (jobForm.company || "").trim(),
    location: (jobForm.location || "").trim(),
    baseRange: jobForm.baseRange || ""
  };
  if (first?.positionId) {
    await updateJobPosition(first.positionId, body);
  } else {
    const payload = { ...body };
    if (cur) payload.spaceId = cur;
    await createJobPosition(payload);
  }
  await loadAggregatedJobs();
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
  if (spaceMgmtResumeDetailOpen.value) {
    closeSpaceMgmtResumeDetail();
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
  if (showSpaceMgmtBindModal.value) {
    closeSpaceMgmtBindModal();
    return;
  }
  if (showAuthModal.value) {
    dismissAuthModal();
  }
}

onMounted(async () => {
  document.addEventListener("keydown", onGlobalEscape);
  window.addEventListener("beforeunload", onResumeBeforeUnload);
  loadUserSession();
  if (currentUser.value) {
    await refreshSpaces();
    await loadSpaceData();
  }
});

onBeforeUnmount(() => {
  document.removeEventListener("keydown", onGlobalEscape);
  window.removeEventListener("beforeunload", onResumeBeforeUnload);
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
            <button type="button" class="text-gray-600 hover:text-primary text-sm flex items-center gap-1" @click="openConfigPage">
              <i class="fa-solid fa-wand-magic-sparkles"></i><span class="hidden sm:inline">AI</span>
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
        <section
          v-if="
            activeTab !== 'recycle' &&
            activeTab !== 'resume' &&
            activeTab !== 'job' &&
            activeTab !== 'space-mgmt' &&
            activeTab !== 'db-inspector' &&
            activeTab !== 'user' &&
            !currentSpaceId
          "
          class="bg-white rounded-lg shadow-card p-8 text-center fade-in"
        >
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

        <section v-if="activeTab === 'resume' && currentUser" class="fade-in space-y-6">
          <div v-if="resumeUiPhase === 'list'" class="bg-white rounded-lg shadow-card p-6">
            <div class="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-4">
              <div>
                <h2 class="text-lg font-semibold text-gray-800 mb-1 flex items-center gap-2">
                  <i class="fa-solid fa-file-lines text-primary"></i>简历管理
                </h2>
                <p class="text-sm text-gray-500">
                  每份简历<strong>仅存一条数据</strong>，可<strong>关联多个工作空间</strong>；列表按简历去重展示。无空间时也可新增；若左侧已选<strong>当前工作空间</strong>，新建将自动关联到该空间。删除将从<strong>所有已关联空间</strong>移除该简历。
                </p>
              </div>
              <button
                type="button"
                class="inline-flex items-center justify-center gap-2 shrink-0 px-4 py-2.5 bg-primary hover:bg-blue-700 text-white text-sm font-medium rounded-lg shadow-sm transition-colors disabled:opacity-50"
                :disabled="creatingResume"
                @click="createNewResumeDoc"
              >
                <i class="fa-solid fa-plus"></i>
                新增简历
              </button>
            </div>

            <p v-if="sortedResumeList.length === 0" class="text-sm text-gray-500 mb-4">
              暂无简历，请点击「新增简历」创建第一份。若已选择<strong>当前工作空间</strong>，新建会自动关联到该空间；未选空间时简历可先存在，稍后在空间管理中绑定。
            </p>
            <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              <div
                v-for="r in sortedResumeList"
                :key="r.resumeId"
                role="button"
                tabindex="0"
                class="relative text-left rounded-lg border p-4 transition-all hover:shadow-card bg-gray-50/50 border-gray-200 hover:border-primary/40 cursor-pointer outline-none focus:ring-2 focus:ring-primary/50"
                @click="openResumeDetail(r)"
                @keydown.enter.prevent="openResumeDetail(r)"
              >
                <button
                  type="button"
                  class="absolute top-2 right-2 z-10 p-2 rounded-md text-gray-400 hover:text-red-600 hover:bg-red-50 transition-colors"
                  title="从所有空间删除该简历"
                  @click.stop="confirmDeleteResumeDoc(r, $event)"
                >
                  <i class="fa-solid fa-trash text-sm"></i>
                </button>
                <div class="flex items-start justify-between gap-2 mb-1 pr-9">
                  <span class="font-medium text-gray-900 text-sm leading-snug">{{ resumeRowPrimaryTitle(r) }}</span>
                </div>
                <div v-if="rowSpaceIds(r).length" class="text-[11px] text-primary/90 font-medium mb-1">
                  <span class="text-gray-600">已关联空间</span>
                  <ul class="list-none m-0 mt-0.5 p-0 space-y-0.5">
                    <li v-for="sid in rowSpaceIds(r)" :key="sid" class="truncate">{{ spaceDisplayName(sid) }}</li>
                  </ul>
                </div>
                <p v-else class="text-[11px] text-gray-400 mb-1">未关联任何空间</p>
                <p v-if="resumeUpdatedLabel(r)" class="text-[11px] text-gray-400 mb-1">更新 {{ resumeUpdatedLabel(r) }}</p>
                <p class="text-xs text-gray-500 mb-1">{{ resumeModuleCount(r) }} 个模块</p>
                <p class="text-xs text-gray-600 line-clamp-2 leading-relaxed">{{ resumeCardPreview(r) }}</p>
                <p class="text-xs text-primary mt-2 font-medium">点击进入编辑 →</p>
              </div>
            </div>
          </div>

          <div v-else class="bg-white rounded-lg shadow-card p-6">
            <div class="flex flex-wrap items-center gap-3 mb-5 border-b border-gray-100 pb-4">
              <button
                type="button"
                class="inline-flex items-center gap-2 px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50"
                @click="backToResumeList"
              >
                <i class="fa-solid fa-arrow-left"></i>
                返回列表
              </button>
              <h2 class="text-lg font-semibold text-gray-800 flex-1 min-w-0 flex items-center gap-2">
                <i class="fa-solid fa-file-pen text-primary"></i>
                <span class="truncate">简历详情</span>
              </h2>
              <button
                type="button"
                class="inline-flex items-center gap-2 px-3 py-2 text-sm text-red-600 border border-red-200 rounded-lg hover:bg-red-50"
                @click="deleteCurrentResumeFromDetail"
              >
                <i class="fa-solid fa-trash"></i>
                删除此简历
              </button>
            </div>
            <div v-if="rowSpaceIds(selectedResumeRow).length" class="text-xs text-gray-500 mb-4 -mt-2">
              <span class="font-medium text-gray-600">已关联空间</span>
              <ul class="list-none m-0 mt-1 p-0 space-y-0.5">
                <li v-for="sid in rowSpaceIds(selectedResumeRow)" :key="sid" class="font-medium text-primary">
                  {{ spaceDisplayName(sid) }}
                </li>
              </ul>
            </div>
            <div v-if="selectedResumeId">
              <label class="block max-w-lg mb-4">
                <span class="text-xs font-medium text-gray-600">简历名称</span>
                <input
                  v-model="resumeDisplayName"
                  type="text"
                  class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder="例如：校招 Java 简历"
                />
                <span class="text-xs text-gray-500 mt-1 block leading-relaxed">
                  名称与模块在点击「保存此简历」时写入数据库；若名称为空，保存时将使用「未命名简历」。
                </span>
              </label>
              <p class="text-sm font-medium text-gray-800 mb-2">模块</p>
              <div class="flex flex-wrap gap-2 items-center mb-3">
                <button
                  type="button"
                  class="inline-flex items-center gap-2 px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-medium rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  :disabled="resumeSaveLoading"
                  @click="saveCurrentResumeManual"
                >
                  <i class="fa-solid fa-floppy-disk"></i>
                  保存此简历
                </button>
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
                  <i class="fa-solid fa-plus"></i>增加模块
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
                <h3 class="text-base font-medium text-gray-700 mb-1">该简历下暂无模块</h3>
                <p class="text-sm text-gray-500 mb-4">点击下方按钮添加第一个模块</p>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 px-4 py-2 bg-primary hover:bg-blue-700 text-white text-sm rounded-md transition-colors"
                  @click="addResumeBlock"
                >
                  <i class="fa-solid fa-plus"></i>添加模块
                </button>
              </div>
              <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div
                  v-for="(b, idx) in resumeBlocks"
                  :key="b.id"
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
            <p v-else class="text-sm text-gray-500">正在加载简历…</p>
          </div>
        </section>

        <section v-if="activeTab === 'job' && currentUser" class="fade-in space-y-6">
          <div class="bg-white rounded-lg shadow-md p-6">
            <div class="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-4 mb-6">
              <div>
                <h2 class="text-xl font-semibold text-gray-800 flex items-center gap-2">
                  <i class="fa-solid fa-briefcase text-primary"></i>岗位管理
                </h2>
                <p class="text-sm text-gray-500 mt-1">
                  每个岗位<strong>仅存一条数据</strong>，可<strong>关联多个工作空间</strong>；列表按岗位去重展示。无空间时也可添加；若已选<strong>当前工作空间</strong>，新建会自动关联。删除将<strong>彻底移除</strong>该岗位（所有空间不可再见）。
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
                    title="从所有空间删除该岗位"
                    @click="requestDeleteJob(row)"
                  >
                    <i class="fa-solid fa-trash"></i>
                  </button>
                </div>
                <div class="flex flex-wrap items-center gap-2 pr-16 mb-2">
                  <h3 class="text-lg font-semibold text-gray-900">{{ row.title || "未命名岗位" }}</h3>
                  <div v-if="rowSpaceIds(row).length" class="flex flex-wrap gap-1">
                    <span
                      v-for="sid in rowSpaceIds(row)"
                      :key="sid"
                      class="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-700 border border-gray-200 max-w-[10rem] truncate"
                      :title="spaceDisplayName(sid)"
                      >{{ spaceDisplayName(sid) }}</span>
                  </div>
                  <span
                    v-else
                    class="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-amber-50 text-amber-900 border border-amber-200"
                    >未关联空间</span>
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

        <section v-if="activeTab === 'space-mgmt' && currentUser" class="fade-in space-y-6">
          <div class="bg-white rounded-lg shadow-card p-6">
            <h2 class="text-lg font-semibold text-gray-800 mb-1 flex items-center gap-2">
              <i class="fa-solid fa-layer-group text-primary"></i>空间管理
            </h2>
            <p class="text-sm text-gray-500 mb-6">
              本页用于<strong>查看与维护空间与资源之间的绑定关系</strong>，以及<strong>删除空间</strong>（移入回收站）。列表为只读汇总；点击<strong>简历或岗位条目</strong>可弹出<strong>只读详情</strong>。「绑定简历 / 绑定岗位」弹窗列出与「简历管理」「岗位管理」一致的<strong>全量</strong>数据，可用<strong>开关</strong>绑定或解绑本空间（同一份数据，不复制）。<strong>编辑、删除简历或岗位</strong>请到「简历管理」「岗位管理」。
            </p>
            <div v-if="spaceMgmtLoading" class="text-center py-12 text-gray-500 text-sm">正在加载各空间资源…</div>
            <div
              v-else-if="!spaceMgmtRows.length"
              class="text-center py-10 text-gray-500 text-sm border border-dashed border-gray-200 rounded-lg"
            >
              暂无活跃空间，请先在侧栏「新建」创建工作空间。
            </div>
            <div v-else class="space-y-4">
              <article
                v-for="sp in spaceMgmtRows"
                :key="sp.spaceId"
                class="rounded-xl border border-gray-200 bg-gray-50/50 p-5 shadow-sm"
              >
                <div class="flex flex-wrap items-start justify-between gap-3 mb-4">
                  <div class="min-w-0">
                    <h3 class="text-base font-semibold text-gray-900">{{ sp.name }}</h3>
                    <p class="text-xs text-gray-500 font-mono mt-1 break-all">spaceId：{{ sp.spaceId }}</p>
                    <p v-if="sp.createdAt || sp.updatedAt" class="text-[11px] text-gray-400 mt-1">
                      创建于 {{ formatJobDate(sp.createdAt) }}
                      <span v-if="sp.updatedAt"> · 更新 {{ formatJobDate(sp.updatedAt) }}</span>
                    </p>
                  </div>
                  <div class="flex flex-wrap gap-2 shrink-0">
                    <button
                      v-if="sp.spaceId !== currentSpaceId"
                      type="button"
                      class="px-3 py-2 text-sm rounded-lg border border-primary text-primary hover:bg-blue-50 transition-colors"
                      @click="selectSpaceAsCurrent(sp.spaceId)"
                    >
                      设为当前工作空间
                    </button>
                    <span
                      v-else
                      class="inline-flex items-center px-3 py-2 text-sm rounded-lg bg-blue-50 text-primary font-medium border border-primary/30"
                      >当前工作空间</span>
                    <button
                      type="button"
                      class="px-3 py-2 text-sm rounded-lg border border-gray-300 text-gray-700 hover:bg-white transition-colors"
                      @click="openRenameForSpaceRow(sp)"
                    >
                      重命名
                    </button>
                    <button
                      type="button"
                      class="px-3 py-2 text-sm rounded-lg border border-red-200 text-red-600 hover:bg-red-50 transition-colors"
                      title="移入回收站，可在侧栏回收站还原"
                      @click.stop="moveSpaceToRecycleBin(sp.spaceId)"
                    >
                      <i class="fa-solid fa-trash mr-1" aria-hidden="true"></i>删除空间
                    </button>
                  </div>
                </div>
                <div class="grid md:grid-cols-2 gap-4">
                  <div class="rounded-lg bg-white border border-gray-100 p-4 min-h-[5rem]">
                    <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
                      <p class="text-xs font-semibold text-gray-600">简历（{{ sp.resumes?.length || 0 }}）</p>
                      <div class="flex flex-wrap gap-1.5 shrink-0">
                        <button
                          type="button"
                          class="text-xs px-2 py-1 rounded-md border border-primary text-primary hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed"
                          :disabled="!spaceMgmtCanOpenResumeBind()"
                          :title="
                            !spaceMgmtCanOpenResumeBind()
                              ? '请先在「简历管理」中创建至少一份简历'
                              : '从「简历管理」列表选择一份简历，在本空间建立绑定（新增副本）'
                          "
                          @click="openSpaceMgmtBindResumeModal(sp.spaceId)"
                        >
                          绑定简历
                        </button>
                      </div>
                    </div>
                    <ul v-if="sp.resumes?.length" class="text-sm text-gray-800 space-y-1.5 list-none m-0 p-0">
                      <li
                        v-for="doc in sp.resumes"
                        :key="doc.resumeId"
                        role="button"
                        tabindex="0"
                        class="rounded-md border border-gray-100 bg-gray-50/40 px-2 py-1.5 text-gray-800 cursor-pointer hover:border-primary/50 hover:bg-blue-50/40 transition-colors outline-none focus:ring-2 focus:ring-primary/40"
                        @click="openSpaceMgmtResumeDetail(sp.spaceId, doc)"
                        @keydown.enter.prevent="openSpaceMgmtResumeDetail(sp.spaceId, doc)"
                      >
                        <span class="font-medium text-gray-900">{{ resumeRowPrimaryTitle(doc) }}</span>
                        <span class="block text-[11px] text-gray-400 font-mono truncate mt-0.5" :title="doc.resumeId">{{ doc.resumeId }}</span>
                      </li>
                    </ul>
                    <p v-else class="text-sm text-gray-400">该空间下暂无简历</p>
                  </div>
                  <div class="rounded-lg bg-white border border-gray-100 p-4 min-h-[5rem]">
                    <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
                      <p class="text-xs font-semibold text-gray-600">
                        岗位（活跃 {{ activeJobsInMgmtRow(sp.jobs).length }} / 共 {{ sp.jobs?.length || 0 }}）
                      </p>
                      <button
                        type="button"
                        class="text-xs px-2 py-1 rounded-md border border-primary text-primary hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed shrink-0"
                        :disabled="!spaceMgmtCanOpenJobBind()"
                        :title="
                          !spaceMgmtCanOpenJobBind()
                            ? '请先在「岗位管理」中添加至少一个岗位'
                            : '从「岗位管理」列表选择一个岗位，在本空间建立绑定（新增副本）'
                        "
                        @click="openSpaceMgmtBindJobModal(sp.spaceId)"
                      >
                        绑定岗位
                      </button>
                    </div>
                    <ul v-if="spaceMgmtJobsForDisplay(sp.jobs).length" class="text-sm text-gray-800 space-y-1.5 list-none m-0 p-0">
                      <li
                        v-for="j in spaceMgmtJobsForDisplay(sp.jobs)"
                        :key="j.positionId"
                        role="button"
                        tabindex="0"
                        class="rounded-md border border-gray-100 bg-gray-50/40 px-2 py-1.5 cursor-pointer hover:border-primary/50 hover:bg-blue-50/40 transition-colors outline-none focus:ring-2 focus:ring-primary/40"
                        @click="openJobDetailModal(j)"
                        @keydown.enter.prevent="openJobDetailModal(j)"
                      >
                        <div class="flex flex-wrap items-center gap-2">
                          <span class="font-medium text-gray-900 min-w-0 flex-1">{{ jobBindLabel(j) }}</span>
                          <span
                            class="text-[10px] px-1.5 py-0.5 rounded shrink-0 font-medium"
                            :class="
                              (j.status || 'ACTIVE') === 'ACTIVE'
                                ? 'bg-emerald-50 text-emerald-800 border border-emerald-200'
                                : 'bg-gray-100 text-gray-600 border border-gray-200'
                            "
                          >
                            {{ (j.status || "ACTIVE") === "ACTIVE" ? "活跃" : "已关闭" }}
                          </span>
                        </div>
                        <span class="block text-[11px] text-gray-400 font-mono truncate mt-0.5" :title="j.positionId">{{ j.positionId }}</span>
                      </li>
                    </ul>
                    <p v-else class="text-sm text-gray-400">该空间下暂无岗位</p>
                  </div>
                </div>
              </article>
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
            v-if="!currentUser"
            class="border border-amber-200 bg-amber-50 text-amber-900 text-sm rounded-lg px-4 py-3 flex items-start gap-2"
          >
            <i class="fa-solid fa-triangle-exclamation mt-0.5 shrink-0"></i>
            <span>请先登录；百炼连接配置按账号保存，与当前所选空间无关，同一账号下全部空间共享。</span>
          </div>
          <div class="bg-white rounded-lg shadow-card p-6">
            <div class="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-4">
              <div>
                <h2 class="text-lg font-semibold text-gray-800 flex items-center gap-2">
                  <i class="fa-solid fa-gear text-primary"></i>阿里云百炼连接配置
                </h2>
                <p class="text-sm text-gray-500 mt-1">
                  配置 Base URL、API Key 与模型名称，供 JD 拆解等能力调用；作用域为当前登录账号，全空间共用同一份配置。
                </p>
              </div>
              <div class="flex flex-wrap gap-2 shrink-0">
                <button
                  type="button"
                  class="inline-flex items-center gap-2 px-4 py-2 bg-primary hover:bg-blue-700 text-white text-sm rounded-md transition-colors disabled:opacity-50"
                  :disabled="!currentUser"
                  @click="saveBailianConfig"
                >
                  <i class="fa-solid fa-floppy-disk"></i>保存配置
                </button>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 px-4 py-2 border border-gray-300 rounded-md text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                  :disabled="testingModelConfig || !currentUser"
                  @click="testBailianConfigConnection"
                >
                  <i class="fa-solid fa-plug"></i>{{ testingModelConfig ? "测试中…" : "测试调用" }}
                </button>
              </div>
            </div>
            <p
              v-if="isMockBailianTestEnabled()"
              class="mb-4 text-xs text-amber-900 bg-amber-50 border border-amber-200 rounded-md px-3 py-2 leading-relaxed"
            >
              当前为 <strong>Mock 测试</strong>：点击「测试调用」不会请求后端。默认已改为走服务端
              <code class="bg-white px-1 rounded text-gray-800">POST …/model-configs/test</code>
              ；关闭本提示请去掉环境变量
              <code class="bg-white px-1 rounded text-gray-800">VITE_MOCK_BAILIAN_TEST</code>
              或设为 false。
            </p>
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
                  placeholder="OpenAI 兼容：https://dashscope.aliyuncs.com/compatible-mode/v1；Anthropic 网关：https://coding.dashscope.aliyuncs.com/apps/anthropic"
                />
              </label>
              <label class="flex flex-col gap-1.5 text-gray-700 md:col-span-2">
                <span class="font-medium flex flex-wrap items-center gap-2">
                  API 密钥
                  <button
                    type="button"
                    class="text-xs font-normal text-primary hover:underline"
                    @click="showBailianApiKey = !showBailianApiKey"
                  >
                    {{ showBailianApiKey ? "隐藏" : "显示" }}
                  </button>
                </span>
                <input
                  v-model="modelConfig.apiKey"
                  :type="showBailianApiKey ? 'text' : 'password'"
                  autocomplete="off"
                  autocapitalize="off"
                  spellcheck="false"
                  class="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary font-mono text-sm"
                  placeholder="阿里云百炼 API Key（留空保存将保留库中已有密钥）"
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
              配置写入当前登录账号，切换工作空间不会切换百炼配置。文档：
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

        <section v-if="activeTab === 'db-inspector'" class="fade-in space-y-4">
          <div v-if="currentUser?.phone !== DB_INSPECTOR_ALLOWED_PHONE" class="bg-white rounded-lg shadow-card p-8 text-center">
            <i class="fa-solid fa-lock text-4xl text-amber-400 mb-3"></i>
            <h2 class="text-lg font-semibold text-gray-800 mb-2">无权访问</h2>
            <p class="text-gray-500 text-sm">库表看板仅限指定账号使用。</p>
          </div>
          <div v-else class="grid grid-cols-1 lg:grid-cols-4 gap-4 items-start">
            <div class="lg:col-span-1 bg-white rounded-lg shadow-card p-4">
              <h3 class="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
                <i class="fa-solid fa-table text-primary"></i>数据表
              </h3>
              <p v-if="dbInspectorLoading && !dbInspectorTables.length" class="text-xs text-gray-500">加载中…</p>
              <ul v-else class="max-h-[70vh] overflow-y-auto text-sm space-y-0.5 list-none m-0 p-0">
                <li v-for="t in dbInspectorTables" :key="t">
                  <button
                    type="button"
                    class="w-full text-left px-2 py-1.5 rounded truncate"
                    :class="
                      t === dbInspectorSelectedTable
                        ? 'bg-blue-50 text-primary font-medium'
                        : 'text-gray-700 hover:bg-gray-50'
                    "
                    @click="selectDbInspectorTable(t)"
                  >
                    {{ t }}
                  </button>
                </li>
              </ul>
            </div>
            <div class="lg:col-span-3 bg-white rounded-lg shadow-card p-4 min-w-0">
              <div class="flex flex-wrap items-center justify-between gap-2 mb-3">
                <h3 class="text-sm font-semibold text-gray-700 truncate">
                  <span v-if="dbInspectorSelectedTable">{{ dbInspectorSelectedTable }}</span>
                  <span v-else class="text-gray-400">请选择左侧表</span>
                </h3>
                <div v-if="dbInspectorSelectedTable" class="flex flex-wrap items-center gap-2 text-xs text-gray-600">
                  <span
                    >{{ dbInspectorRowCount ? dbInspectorOffset + 1 : 0 }}–{{
                      Math.min(dbInspectorOffset + dbInspectorRows.length, dbInspectorRowCount)
                    }}
                    / 共 {{ dbInspectorRowCount }} 行</span
                  >
                  <button
                    type="button"
                    class="px-2 py-1 border border-gray-200 rounded hover:bg-gray-50 disabled:opacity-40"
                    :disabled="dbInspectorOffset <= 0 || dbInspectorLoading"
                    @click="dbInspectorPrevPage"
                  >
                    上一页
                  </button>
                  <button
                    type="button"
                    class="px-2 py-1 border border-gray-200 rounded hover:bg-gray-50 disabled:opacity-40"
                    :disabled="
                      dbInspectorLoading ||
                      !dbInspectorRowCount ||
                      dbInspectorOffset + dbInspectorLimit >= dbInspectorRowCount
                    "
                    @click="dbInspectorNextPage"
                  >
                    下一页
                  </button>
                </div>
              </div>
              <p v-if="dbInspectorLoading" class="text-sm text-gray-500 py-6 text-center">加载中…</p>
              <div v-else-if="!dbInspectorSelectedTable" class="text-sm text-gray-500 py-6 text-center">暂无表或无权限</div>
              <div v-else class="overflow-x-auto border border-gray-100 rounded-md">
                <table class="min-w-full text-xs text-left border-collapse">
                  <thead class="bg-gray-50 text-gray-600">
                    <tr>
                      <th
                        v-for="c in dbInspectorColumns"
                        :key="c"
                        class="px-2 py-2 font-medium border-b border-gray-200 whitespace-nowrap"
                      >
                        {{ c }}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, ri) in dbInspectorRows" :key="ri" class="odd:bg-white even:bg-gray-50/80">
                      <td
                        v-for="c in dbInspectorColumns"
                        :key="c + '-' + ri"
                        class="px-2 py-1.5 border-b border-gray-100 align-top max-w-[20rem] break-words"
                      >
                        {{ formatDbInspectorCell(row[c]) }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p class="mt-3 text-[11px] text-gray-400 leading-relaxed">
                每页最多 500 行（当前 {{ dbInspectorLimit }}）。敏感字段请注意环境安全；生产环境请移除此入口或改为更细审计。
              </p>
            </div>
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
            <p
              v-if="bindSourceResumes.length === 0 && bindSourceJobs.length === 0"
              class="text-xs text-gray-500 mb-3"
            >
              账号下暂无简历或岗位，将创建空白空间；之后可在「简历管理」「岗位管理」中新增，并在本弹窗或「空间管理」中绑定到新空间。
            </p>
            <div class="space-y-4">
              <div>
                <p class="text-xs font-medium text-gray-600 mb-2">选择简历（单选）</p>
                <p class="text-xs text-gray-500 mb-2 leading-relaxed">
                  列表与「简历管理」一致，为当前账号下<strong>全部简历</strong>（与当前工作空间无关）。可不选；选中则在新建空间创建成功后<strong>关联</strong>该简历（同一份数据，不复制）。若在简历详情中有未保存修改，请先保存或重新打开本弹窗以刷新列表。
                </p>
                <div class="border border-gray-200 rounded-lg p-2 max-h-40 overflow-y-auto space-y-1 bg-gray-50/50">
                  <label
                    v-for="r in bindSourceResumes"
                    :key="r.resumeId"
                    class="flex items-start gap-2 rounded-md px-2 py-2 text-sm cursor-pointer hover:bg-white transition-colors"
                  >
                    <input
                      v-model="newSpaceBindResumeId"
                      type="radio"
                      name="newSpaceResume"
                      :value="String(r.resumeId)"
                      class="mt-1 text-primary"
                    />
                    <span class="text-gray-800">{{ resumeBindLabel(r) }}</span>
                  </label>
                </div>
                <button
                  v-if="newSpaceBindResumeId && bindSourceResumes.length"
                  type="button"
                  class="mt-2 text-xs text-primary hover:underline"
                  @click="newSpaceBindResumeId = ''"
                >
                  清除选择
                </button>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-600 mb-2">选择岗位（多选）</p>
                <p class="text-xs text-gray-500 mb-2 leading-relaxed">
                  列表与「岗位管理」一致，为当前账号下<strong>全部活跃岗位</strong>（与当前工作空间无关）；选中则在新空间创建成功后<strong>关联</strong>对应岗位（同一份数据，不复制）。
                </p>
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
      v-if="showSpaceMgmtBindModal"
      class="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 p-4"
      @click.self="closeSpaceMgmtBindModal"
    >
      <div
        class="bg-white rounded-xl shadow-2xl border border-gray-100 w-full max-w-lg max-h-[90vh] flex flex-col overflow-hidden slide-in-modal"
        :class="panelModalDragging ? 'cursor-grabbing' : ''"
        :style="{ transform: `translate(${panelModalOffset.x}px, ${panelModalOffset.y}px)` }"
        @click.stop
      >
        <div
          class="shrink-0 px-5 py-4 border-b border-gray-100 flex items-center justify-between gap-2 cursor-move"
          @pointerdown="onPanelModalHeaderPointerDown"
        >
          <div class="min-w-0">
            <h3 class="text-lg font-bold text-gray-900 truncate">
              {{ spaceMgmtBindKind === "job" ? "岗位关联" : "简历关联" }}「{{ spaceDisplayName(spaceMgmtBindTargetSpaceId) }}」
            </h3>
            <p v-if="spaceMgmtBindKind === 'job'" class="text-xs text-gray-500 mt-1 leading-relaxed">
              列表与<strong>「岗位管理」</strong>一致（当前账号下<strong>全部活跃岗位</strong>）。右侧开关<strong>开</strong>表示已绑定本空间，<strong>关</strong>表示未绑定；点击即可绑定或解绑（同一份数据，不复制）。
            </p>
            <p v-else class="text-xs text-gray-500 mt-1 leading-relaxed">
              列表与<strong>「简历管理」</strong>一致（当前账号下<strong>全部简历</strong>）。右侧开关<strong>开</strong>表示已绑定本空间，<strong>关</strong>表示未绑定；点击即可绑定或解绑。若某简历<strong>仅关联本空间</strong>，解绑时会提示并删除该简历数据。
            </p>
          </div>
          <button type="button" class="p-2 text-gray-400 hover:text-gray-700 rounded-lg shrink-0" aria-label="关闭" @click="closeSpaceMgmtBindModal">
            <i class="fa-solid fa-xmark text-lg"></i>
          </button>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          <div v-if="spaceMgmtBindPickLoading" class="text-center py-10 text-gray-500 text-sm">正在加载…</div>
          <div v-else-if="!spaceMgmtBindPickGroups.length" class="text-center py-10 text-sm text-gray-500">
            暂无{{ spaceMgmtBindKind === "job" ? "岗位" : "简历" }}，请先在「{{ spaceMgmtBindKind === "job" ? "岗位管理" : "简历管理" }}」中维护。
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="g in spaceMgmtBindPickGroups"
              :key="g.spaceId || '_ungrouped'"
              class="rounded-lg border border-gray-100 bg-gray-50/50 p-2"
            >
              <p v-if="g.name" class="text-xs font-semibold text-gray-700 mb-2 px-1">{{ g.name }}</p>
              <ul class="space-y-2 list-none m-0 p-0">
                <li
                  v-for="item in g.items"
                  :key="
                    spaceMgmtBindKind === 'job'
                      ? `${g.spaceId || ''}-${item.positionId}`
                      : `${g.spaceId || ''}-${item.resumeId}`
                  "
                  class="flex flex-wrap items-center justify-between gap-3 rounded-md bg-white border border-gray-100 px-3 py-2.5"
                >
                  <div class="min-w-0 flex-1 space-y-1">
                    <p class="text-sm font-medium text-gray-900">
                      {{ spaceMgmtBindKind === "job" ? jobBindLabel(item) : resumeRowPrimaryTitle(item) }}
                    </p>
                    <p class="text-[11px] text-gray-400 font-mono truncate">
                      {{ spaceMgmtBindKind === "job" ? item.positionId : item.resumeId }}
                    </p>
                    <p
                      v-if="spaceMgmtBindKind === 'job' ? spaceMgmtJobDupWarn(item) : spaceMgmtResumeDupWarn(item)"
                      class="text-[10px] text-amber-800 leading-snug"
                    >
                      {{ spaceMgmtBindKind === "job" ? "目标空间已有同标签岗位，仍可绑定。" : "目标空间已有同名简历，仍可绑定。" }}
                    </p>
                  </div>
                  <button
                    type="button"
                    role="switch"
                    :aria-checked="spaceMgmtBindKind === 'job' ? spaceMgmtJobBoundToTarget(item) : spaceMgmtResumeBoundToTarget(item)"
                    class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border transition-colors focus:outline-none focus:ring-2 focus:ring-primary/35 focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-40"
                    :class="
                      (spaceMgmtBindKind === 'job' ? spaceMgmtJobBoundToTarget(item) : spaceMgmtResumeBoundToTarget(item))
                        ? 'border-primary bg-primary'
                        : 'border-gray-300 bg-gray-200'
                    "
                    :disabled="spaceMgmtBindActionLoading"
                    :title="
                      (spaceMgmtBindKind === 'job' ? spaceMgmtJobBoundToTarget(item) : spaceMgmtResumeBoundToTarget(item))
                        ? '点击解绑本空间'
                        : '点击绑定到本空间'
                    "
                    @click="spaceMgmtBindKind === 'job' ? toggleSpaceMgmtBindJob(item) : toggleSpaceMgmtBindResume(item)"
                  >
                    <span
                      class="pointer-events-none absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform duration-200 ease-out"
                      :class="
                        (spaceMgmtBindKind === 'job' ? spaceMgmtJobBoundToTarget(item) : spaceMgmtResumeBoundToTarget(item))
                          ? 'translate-x-5'
                          : 'translate-x-0'
                      "
                    ></span>
                  </button>
                </li>
              </ul>
            </div>
          </div>
        </div>
        <div class="shrink-0 px-5 py-3 border-t border-gray-100 flex justify-end bg-gray-50/80">
          <button
            type="button"
            class="px-4 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-white"
            @click="closeSpaceMgmtBindModal"
          >
            关闭
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
        v-if="spaceMgmtResumeDetailOpen"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4"
        @click.self="closeSpaceMgmtResumeDetail"
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
            <h3 class="text-lg font-bold text-gray-900 pr-4">简历详情（只读）</h3>
            <button
              type="button"
              class="p-2 text-gray-400 hover:text-gray-700 rounded-lg shrink-0 transition-colors"
              aria-label="关闭"
              @click="closeSpaceMgmtResumeDetail"
            >
              <i class="fa-solid fa-xmark text-lg"></i>
            </button>
          </div>
          <div class="min-h-0 flex-1 overflow-y-auto px-6 py-4 space-y-4 text-sm">
            <div v-if="spaceMgmtResumeDetailLoading" class="text-center py-12 text-gray-500">加载中…</div>
            <template v-else-if="spaceMgmtResumeDetailDoc">
              <div class="rounded-lg bg-gray-50 border border-gray-100 px-3 py-2 text-xs text-gray-600 space-y-1">
                <p><span class="font-medium text-gray-700">所属空间：</span>{{ spaceMgmtResumeDetailSpaceLabel }}</p>
                <p class="font-mono text-gray-500 break-all">resumeId：{{ spaceMgmtResumeDetailDoc.resumeId }}</p>
                <p v-if="resumeUpdatedLabel(spaceMgmtResumeDetailDoc)">
                  <span class="font-medium text-gray-700">更新：</span>{{ resumeUpdatedLabel(spaceMgmtResumeDetailDoc) }}
                </p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 mb-1">简历名称</p>
                <p class="text-base font-semibold text-gray-900">{{ spaceMgmtResumeDetailDoc.name || "未命名简历" }}</p>
              </div>
              <div v-if="(spaceMgmtResumeDetailDoc.modules || []).length" class="space-y-3">
                <p class="text-sm font-semibold text-gray-800">模块</p>
                <div
                  v-for="(m, midx) in spaceMgmtResumeDetailDoc.modules"
                  :key="m.id || String(midx)"
                  class="rounded-lg border border-gray-200 bg-white px-4 py-3"
                >
                  <p class="text-sm font-medium text-gray-900 border-b border-gray-100 pb-2 mb-2">{{ m.title || "未命名模块" }}</p>
                  <pre class="text-sm text-gray-700 whitespace-pre-wrap font-sans leading-relaxed m-0">{{ m.text || "（无正文）" }}</pre>
                </div>
              </div>
              <p v-else class="text-sm text-gray-500 border border-dashed border-gray-200 rounded-lg px-4 py-6 text-center">
                暂无模块数据
              </p>
            </template>
          </div>
          <div class="shrink-0 px-6 py-4 border-t border-gray-100 flex justify-end bg-gray-50/80">
            <button
              type="button"
              class="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-white text-sm font-medium transition-colors min-h-[40px]"
              @click="closeSpaceMgmtResumeDetail"
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

