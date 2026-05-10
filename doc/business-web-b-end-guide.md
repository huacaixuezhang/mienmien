# MienMien B 端（Web）功能与设计说明

本文档描述仓库内 **B 端 Web 工作台**（`web/src/App.vue` 单页应用 + `web/src/api.js` 与 `java/business`）的**产品定位、设计理念、页面组成、交互结构、数据流与后端契约**，便于产品、设计与研发对齐。

---

## 1. 定位与设计理念

### 1.1 产品定位

B 端是面向「求职过程管理」的 **企业/个人工作台**：以 **空间（Space）** 为**核心**业务隔离单元（面试记录、标准题库、模型配置等按空间存放）；**简历与岗位**以独立实体存在并可关联 **0～N** 个工作空间。在同一空间内管理 **岗位 JD 与考点、模拟面试、正式面试** 等；通过 **用户账号 + 服务端会话** 控制访问边界。

### 1.2 核心理念（与仓库 README 一致）

- **空间即边界**：**面试记录、标准答案库、JD 目标、模型配置**等仍严格按 `spaceId` 隔离；切换空间即切换该套上下文。
- **简历与岗位的「实体 + 关联」**：每份简历、每个岗位在后端为**一条主体数据**，可与 **0～N 个**工作空间建立关联。**新建**时：若侧栏已选「当前工作空间」，前端在请求中带 `spaceId` 以默认绑定；若当前没有任何空间或未选择空间，也允许先创建「未关联任何空间」的简历/岗位，稍后在「空间管理」等入口执行 **link** 绑定。
- **先身份、后业务**：未登录时不能进入除「用户管理」外的业务 Tab；避免无会话状态下误操作或泄露他人空间数据。
- **配置与数据分离**：百炼 `Base URL / API Key / 模型名` 按空间持久化在后端；前端可发起直连百炼的补全能力（如 JD 拆解），与业务 CRUD 解耦。
- **面试结构化沉淀**：正式面试与模拟面试采用 **同一套双模块结构**（面试总结 + 题目卡片复盘），`summary` 字段使用统一前缀 **`MM_INTERVIEW_V2::`** 承载 JSON，便于扩展与迁移。
- **可恢复的生命周期**：空间支持回收站与还原，与后端定时清理策略一致（如回收超过一定天数硬删）。

### 1.3 体验原则

- **单页 + 侧栏导航**：减少路由跳转成本，状态集中在当前页。
- **切换空间即刷新**：切换 `currentSpaceId` 时清空跨空间临时草稿（如 JD 编辑区、面试表单草稿），再拉取当前空间数据，避免串空间污染。
- **错误可感知**：注册/登录等异步操作带 `try/catch` 与用户可见提示；会话失效时清理本地存储并引导重新登录。

---

## 2. 技术栈与系统关系

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端 | Vue 3（`<script setup>`）、Vite | 入口 `web/src/main.js`，主界面 `web/src/App.vue` |
| HTTP | `fetch` 封装于 `web/src/api.js` | 统一 `Content-Type`、除登录/注册外自动附加 `Authorization: Bearer <sessionToken>` |
| 后端 | Spring Boot 3（`java/business`） | 端口默认 **8080**，上下文路径下 REST：`/api/v1/business/...` |
| 数据 | MySQL 库 `MienMieApp` | 表前缀 `mm_`，与种子脚本 `scripts/seed-mienmien.sql` 对齐 |

前端默认将业务 API 基址写为 `http://localhost:8080/api/v1/business`（可按部署改为环境变量或构建注入，当前仓库以本地开发为准）。

---

## 3. 整体页面骨架（布局结构）

### 3.1 三栏式结构

```
┌─────────────────────────────────────────────────────────────┐
│  aside.sidebar          │  main                            │
│  ┌─────────────────┐   │  ┌─────────────────────────────┐ │
│  │ brand 品牌区     │   │  │ header.topbar 顶栏           │ │
│  ├─────────────────┤   │  │ 当前空间名 | 用户手机 | …    │ │
│  │ space-panel      │   │  ├─────────────────────────────┤ │
│  │ 空间选择/新建等  │   │  │ content-area 主内容区        │ │
│  ├─────────────────┤   │  │ （随 activeTab 切换卡片）   │ │
│  │ 当前空间 / 系统   │   │  └─────────────────────────────┘ │
│  │ 功能分组导航     │   │                                  │
│  │ （可滚动）       │   │                                  │
│  ├─────────────────┤   │                                  │
│  │ 页脚（版权）     │   │                                  │
│  └─────────────────┘   │                                  │
└─────────────────────────────────────────────────────────────┘
```

- **左侧 `sidebar`**：品牌区、**工作空间**；**可滚动区**内为 **「当前空间」**（标准题库 / 模拟面试 / 正式面试）、**「资源管理」**（简历管理 / 岗位管理；岗位为当前页时可显示「当前」角标）、**「系统功能」**（仪表盘 / 回收站 / 用户管理 / **系统设置**）。底部 **版权** 固定。
- **右侧 `main`**：**顶栏**（当前空间展示、子模块标题、**AI** 快捷入口、删除当前空间、已登录用户摘要等）、**主内容区**（按 `activeTab` 渲染对应 `section`）。**退出登录** 仅在 **「用户管理」** Tab 内提供（与 `logoutSession` 一致）。
- **全局遮罩**：新增/重命名空间弹窗、**登录/注册**弹窗（未登录强制出现，除非在用户页「使用其他账号」流程中临时抑制）。

### 3.2 顶栏（`topbar`）职责

| 元素 | 行为 |
|------|------|
| 当前空间名称 | 来自 `spaces` 中与 `currentSpaceId` 匹配的项 |
| 登录用户手机号 | 登录后在顶栏展示头像缩写与手机号 |
| 删除空间 | 将当前空间移入回收站并跳转回收站 Tab |
| **AI** | 调用 `openConfigPage()` → `switchTab('config')`，进入 **系统设置**（百炼等） |

---

## 4. 空间（Space）模型

### 4.1 概念

- **空间**是 B 端数据的根命名空间；每个空间有 `spaceId`、`name`、`status`（如 ACTIVE / RECYCLED）等。
- **创建空间**时，后端将空间 **归属到当前登录用户**（不再信任请求体中的 `ownerUserId`）。
- **列表空间 / 回收站**仅返回 **当前用户** 名下的空间。

### 4.2 前端交互

- **下拉框**：`select.space-select` 绑定 `currentSpaceId`，`change` 时调用 `switchSpace`。
- **`switchSpace`**：`currentSpaceId` 更新 → `resetTransientDrafts()` → `loadSpaceData()`。
- **新增空间**：弹窗「创建新空间」输入名称（必填）；可选从 **当前侧栏所选工作空间** 拉取列表，将已有简历/岗位 **关联** 到新空间（`linkResumeToSpace` / `linkJobToSpace`，**不复制**正文）。未选工作空间或无数据时仍可创建空白空间。成功后自动切换到新空间并 `loadSpaceData()`。
- **重命名**：弹窗绑定 `renameTargetSpaceId` 与名称 → `renameSpace`。
- **删除（进回收站）**：确认后 `recycleSpace`。
- **回收站 Tab**：列表展示 `recycleBinSpaces`，支持还原 `restoreSpace`。

### 4.3 与后端的对应

- `GET/POST /spaces`、`PUT /spaces/{id}`、`DELETE` 回收与硬删、`POST .../restore` 等，详见 `web/src/api.js`。

---

## 5. 鉴权、会话与用户管理

### 5.1 会话模型

- 登录/注册成功后，后端返回 **`sessionToken`**；前端写入 `localStorage`（键名 `USER_SESSION_STORAGE_KEY`，与 `api.js` 导出常量一致），结构包含：`userId`、`phone`、`sessionToken`。
- 后续除 `/auth/register`、`/auth/login` 外，所有业务请求自动带 **`Authorization: Bearer <sessionToken>`**。
- 退出登录调用 **`POST /auth/logout`**，并清除本地存储。

### 5.2 未登录时的 Tab 策略

- `switchTab`：若 `!currentUser` 且目标不是 **`user`**，则弹出登录窗并中止切换。
- **用户管理**页在未登录时仍可展示注册/登录表单（与弹窗并存时以产品实际为准；弹窗优先阻断）。

### 5.3 「用户管理」页结构（`activeTab === 'user'`）

**已登录：**

- 文案说明（账号摘要、密码摘要存储、空间隔离等）。
- **当前账号**信息区：`手机号`、`用户 ID`、`会话状态`、**令牌尾缀**（仅展示末尾若干字符，便于核对、不完整展示）。
- 操作：**退出登录**、**使用其他账号**（先注销服务端会话并清本地，留在本页展示注册/登录表单，不弹全局登录窗；若此时点击其他业务 Tab，会重新要求登录）。

**未登录：**

- 「未登录」提示（区分是否由「使用其他账号」进入）。
- **双栏表单**：手机号注册、手机号登录（与弹窗逻辑共用 `registerUser` / `loginUser`）。

### 5.4 登录/注册弹窗（`showAuthModal`）

- Tab：**登录** / **注册**。
- 注册需确认密码一致；成功后会 `refreshSpaces` 并关闭弹窗。

---

## 6. 业务模块逐页说明

多数依赖空间的模块在 **`currentSpaceId` 有值** 时拉取主数据。**简历管理**、**岗位管理**可在尚无空间或未选择「当前工作空间」时使用：通过 **`listAllResumeDocuments` / `listAllJobPositions`** 展示当前用户聚合列表；新建时若有 `currentSpaceId` 则在 body 中带 **`spaceId`** 以建立默认关联，否则创建为「未关联任何空间」的实体。模拟/正式面试、标准题库等仍以当前空间为前提（无空间时相关 Tab 多为空态或提前返回）。数据加载入口包括 **`loadSpaceData()`** 以及 **`loadAggregatedResumes` / `loadAggregatedJobs`** 等。

### 6.1 简历管理（`resume`）

- **结构**：多卡片「简历模块」`resumeBlocks`（`id` + 标题 + 正文），支持拖拽排序、增删、删除后短时 **撤销**；展示名 `resumeDisplayName` 与模块一并序列化。
- **与后端**：持久化为 **简历文档**（`name` + `modules[]`），不再使用旧版「单字段 content + version」的 `/resumes` 模型。
  - **列表**：`GET /resume-documents`（`listAllResumeDocuments`）拉取当前用户全部简历，每条含 `spaceIds`（可能为空数组）。
  - **新建**：`POST /resume-documents`（`createResumeDocumentMine`），body：`{ name, modules[, spaceId] }`；`spaceId` 可选。
  - **读取详情**：`GET /resume-documents/{resumeId}`（`getResumeDocumentById`）；若需校验「在某空间下可见」仍可用空间路径的 `GET /spaces/{spaceId}/resume-documents/{resumeId}`（`getResumeDocument`）。
  - **保存**：若该简历已关联空间且能解析出用于路径的 `spaceId`（如当前工作空间命中其 `spaceIds`），则 `PUT /spaces/{spaceId}/resume-documents/{resumeId}`（`updateResumeDocument`）；若尚未关联任何空间，则 `PUT /resume-documents/{resumeId}`（`updateResumeDocumentById`）。
  - **删除**：`DELETE /resume-documents/{resumeId}`（`deleteResumeDocumentEntire`）删除主体及全部空间关联；仅从某空间移除关联可用 `DELETE /spaces/{spaceId}/resume-documents/{resumeId}`（`deleteResumeDocument`）。
  - **绑定到空间**：`POST /spaces/{spaceId}/resume-documents/{resumeId}/link`（`linkResumeToSpace`）。
- **保存入口**：`saveResume` 根据 `pickLinkedSpaceIdForApi` 结果选择 **`updateResumeDocument`** 或 **`updateResumeDocumentById`**；在简历详情页通过 **「保存此简历」** 等按钮触发。

### 6.2 岗位管理（`job`）

- **岗位列表与弹窗**：卡片展示活跃岗位（类型标签：全职 / 校招 / 实习）；**添加/编辑** 使用 `max-w-2xl` 模态框（可拖拽标题区、未保存关闭确认、Toast 反馈）。扩展字段（类型、描述、JD 富文本、薪资备注）序列化进后端 `base_range` 的 JSON（`v:1`），历史纯文本 `base_range` 仍视为薪资/备注。**数据库**需执行 `scripts/migrate-mm-job-position-base-range-expand.sql` 将 `base_range` 扩至 `VARCHAR(8000)`，否则长 JD 可能保存失败。
- **与空间**：`POST /job-positions`（`createJobPosition`）的 body 中 **`spaceId` 可选**；有值则校验空间归属并写入关联，无值则创建不绑定任何空间的岗位（进程内存储实现下仍支持多空间 **link**）。
- **与 `jobForm` 同步**：`jobForm` 与 **首条活跃岗位** 同步；在保存 **模拟/正式面试** 等流程前会将档案中的公司/地点写回 `jobForm`，再调用 `upsertPrimaryJobFromForm` 对首条岗位 **PUT 更新** 或 **POST 创建**（依赖当前空间时才会写入带 `spaceId` 的创建）。
- **岗位 JD 与考点**（同页下方）：`jdEditor`；一键拆解、新增 JD、`listJd` 列表不变。

### 6.3 标准题库（`answer`）

- **结构**：`answerCards` 数组，支持自定义卡片、拖拽排序；持久化字段含 `cardsJson` 及 legacy 五段字段兼容。
- **保存**：`saveAnswerBank`（`getAnswerBank` 拉取后解析 `cardsJson` 或 legacy）。

### 6.4 模拟面试（`mock`）

与 **正式面试** 对齐的 **双模块** 结构：

1. **元信息**：`mockMeta`（时间、公司、地点/线上）；`mockForm.round`（轮次）、`interviewType`（技术/业务/HR）、`score`（0~100）。
2. **模块一：面试总结**：`mockForm.summary`（整体评价）、`finalResult`、`result`（pending/passed/failed）。
3. **模块二：面试复盘（题目卡片）**：`mockReviewCards`，左侧列表可选中、拖拽排序、删除；右侧编辑单题字段（标题、原题、作答、优缺点、优化方案、标准答案）。
4. **保存**：`createInterview("mock", { ... })`，其中 `summary` 为 **`serializeMockSummary()`** → `MM_INTERVIEW_V2::{...}`。
5. **加载**：`hydrateMockFromLatestMock()` 读取该空间 **最新一条 `type === mock`** 的记录；旧数据无 `MM_` 前缀时，整段进入「整体评价」并给一道空卡片便于迁移。

### 6.5 正式面试（`interview`）

- 与模拟面试 **同构**；状态使用 `interviewMeta`、`interviewForm`、`interviewReviewCards`。
- **保存**：`createInterview("real", ...)` + `serializeInterviewSummary()`。
- **加载**：`hydrateInterviewFromLatestReal()`（最新 `type === real`）。
- **说明**：`interviewForm` 中仍保留部分历史字段（如 `qa` 等）用于兼容或扩展，主路径以双模块 + V2 序列化为准。

### 6.6 系统设置 / 百炼（`config`）

- **阿里云百炼**：`modelConfig`（provider、baseUrl、apiKey、modelName、测试提示语）。
- **操作**：保存到后端按**登录用户**维度 `saveModelConfig` / `getModelConfig`（与空间无关，同一账号下全部空间共享）；「测试调用」走 **`POST /model-configs/test`**：服务端从数据库读取当前用户的 `baseUrl` 与 `apiKey`，使用前端提交的测试提示词与模型名调用大模型，返回 `assistantText`。
- **进入方式**：侧栏 **系统功能 → 系统设置**，或顶栏 **AI** 入口（均切到 `activeTab === 'config'`）。

### 6.7 回收站（`recycle`）

- 展示 `recycleBinSpaces`；操作 **还原** 指定空间。
- 与侧栏「系统功能」中的 **回收站** 入口一致，数据同源。

---

## 7. 关键前端状态与数据流（摘要）

| 状态/函数 | 作用 |
|-----------|------|
| `activeTab` | 当前业务模块 key |
| `currentSpaceId` | 当前工作空间；驱动面试、题库及多数「按空间」列表；百炼/模型配置为**用户级**（与空间无关）；简历/岗位聚合列表亦可在无空间时仅按用户拉取 |
| `spaces` / `recycleBinSpaces` | 活跃空间列表、回收站列表 |
| `resetTransientDrafts` | 切换空间时清空各模块草稿态 |
| `loadSpaceData` | 按 `activeTab` 拉取简历、岗位（含 JD 列表）、面试、题库、配置等 |
| `refreshSpaces` | 刷新空间下拉与回收站列表 |
| `USER_SESSION_STORAGE_KEY` | 本地会话持久化 |

---

## 8. B 端页面 HTTP 接口梳理

实现入口：**`web/src/api.js`**（常量 `BIZ`，默认 `http://localhost:8080/api/v1/business`）。除特别声明外，请求与响应均为 **JSON**。

### 8.1 鉴权规则（与页面行为一致）

| 规则 | 说明 |
|------|------|
| 公开接口 | 仅 **`POST .../auth/register`**、**`POST .../auth/login`** 不附加 `Authorization`。 |
| 业务接口 | 其余路径在本地存在 `sessionToken` 时自动带 **`Authorization: Bearer <sessionToken>`**。 |
| 注销 | **`logoutSession()`** 使用原生 `fetch` 调 **`POST .../auth/logout`**，可返回 **204 无正文**；仍带 Bearer。 |
| 401 `BUS-4010` | `fetchJson` 会清除 `localStorage` 中会话键（与注册/登录失败等业务 401 区分以实际响应体为准）。 |

后端对业务路径做 **会话校验**；涉及具体 `spaceId` 路径或 body 时再做 **空间归属** 校验。聚合简历/岗位接口以 **用户归属** 为主。实现见 `java/business` 中会话过滤器、`ApplicationSpaceGuard` 与 `SpaceAccessPolicy`。

### 8.2 `java/business` 接口一览（相对路径均相对于 `BIZ`）

以下路径列在表中的「路径」列省略前缀，完整为 **`{BIZ}{路径}`**。

| 方法 | 路径 | `api.js` 函数 | 典型调用场景 / Tab |
|------|------|---------------|-------------------|
| POST | `/auth/register` | `registerByPhone` | 登录弹窗、用户管理页注册 |
| POST | `/auth/login` | `loginByPhone` | 登录弹窗、用户管理页登录 |
| POST | `/auth/logout` | `logoutSession` | **用户管理** Tab 内「退出登录」 |
| POST | `/spaces` | `createSpace` | 侧栏新增空间；body：`{ name }` |
| GET | `/spaces` | `listSpaces` | 进入工作台、登录后刷新、切换空间前数据源 |
| GET | `/spaces/recycle-bin` | `listRecycleBinSpaces` | 回收站 Tab、侧栏计数刷新 |
| PUT | `/spaces/{spaceId}` | `renameSpace` | 重命名弹窗；body：`{ name }` |
| DELETE | `/spaces/{spaceId}` | `recycleSpace` / `archiveSpace` | **移入回收站**（两函数指向同一 DELETE，历史命名并存） |
| DELETE | `/spaces/{spaceId}/hard` | `deleteSpace` | 回收站内硬删（若页面有入口） |
| POST | `/spaces/{spaceId}/restore` | `restoreSpace` | 回收站还原空间 |
| GET | `/spaces/{spaceId}/resume-documents` | `listResumeDocuments` | 某空间下简历列表（空间管理按空间快照等） |
| POST | `/spaces/{spaceId}/resume-documents` | `createResumeDocument` | 在指定空间下创建并关联；body：`{ name, modules }` |
| GET | `/spaces/{spaceId}/resume-documents/{resumeId}` | `getResumeDocument` | 读取详情（校验空间路径） |
| PUT | `/spaces/{spaceId}/resume-documents/{resumeId}` | `updateResumeDocument` | 更新正文（已关联该空间时） |
| DELETE | `/spaces/{spaceId}/resume-documents/{resumeId}` | `deleteResumeDocument` | 从该空间解除关联；无剩余关联时可删主体（以后端为准） |
| POST | `/spaces/{spaceId}/resume-documents/{resumeId}/link` | `linkResumeToSpace` | 将已有简历关联到空间（幂等） |
| GET | `/resume-documents` | `listAllResumeDocuments` | 简历管理聚合列表（含 `spaceIds`） |
| POST | `/resume-documents` | `createResumeDocumentMine` | 创建简历；body：`{ name, modules[, spaceId] }`，`spaceId` **可选** |
| GET | `/resume-documents/{resumeId}` | `getResumeDocumentById` | 按 id 读详情（仅校验简历归属用户） |
| PUT | `/resume-documents/{resumeId}` | `updateResumeDocumentById` | 按 id 更新（适用于尚未关联任何空间） |
| DELETE | `/resume-documents/{resumeId}` | `deleteResumeDocumentEntire` | 删除简历主体及全部空间关联 |
| POST | `/jd-targets` | `createJd` | 岗位页「新增 JD」；body：`{ spaceId, rawText, focusPoints }` |
| GET | `/jd-targets/{spaceId}` | `listJd` | 岗位 Tab `loadSpaceData` |
| POST | `/jd-targets/analyze` | `analyzeJdFocusPoints` | 岗位页一键拆解；body：`{ rawText }` |
| POST | `/interviews/{type}` | `createInterview` | `type` 为 **`mock`** 或 **`real`**；body：`{ spaceId, round, interviewType, score, result, summary }` |
| GET | `/interviews/{spaceId}` | `listInterview` | 模拟/正式面试 Tab 拉列表后水合 |
| POST | `/job-positions` | `createJobPosition` | 新增岗位；body：`{ title, company, location, baseRange[, spaceId] }`，`spaceId` **可选**（有则校验并关联） |
| PUT | `/job-positions/item/{positionId}` | `updateJobPosition` | 编辑岗位 |
| GET | `/job-positions` | `listAllJobPositions` | 岗位管理聚合列表（每岗一条，含 `spaceIds`） |
| GET | `/job-positions/by-space/{spaceId}` | `listJobPositions` | 某空间下的岗位 |
| GET | `/job-positions/item/{positionId}` | `getJobPositionById` | 按 id 取详情 |
| POST | `/spaces/{spaceId}/job-positions/{positionId}/link` | `linkJobToSpace` | 将已有岗位关联到空间（幂等） |
| DELETE | `/spaces/{spaceId}/job-positions/{positionId}/link` | `unlinkJobFromSpace` | 从该空间解除岗位关联（**不**删除岗位主体）；空间管理绑定弹窗开关解绑 |
| DELETE | `/job-positions/item/{positionId}` | `closeJobPosition` | 岗位卡片删除（彻底删除该岗位及全部空间关联） |
| GET | `/answer-banks/{spaceId}` | `getAnswerBank` | 标准题库 Tab |
| PUT | `/answer-banks` | `saveAnswerBank` | 保存题库；body 含 `spaceId`、五段 legacy、`cardsJson` 等 |
| GET | `/model-configs/me` | `getModelConfig` | 系统设置（`config`）Tab；返回当前登录用户的配置（`ownerUserId` 等） |
| PUT | `/model-configs` | `saveModelConfig` | 保存百炼连接配置；body：`provider`、`baseUrl`、`apiKey`、`modelName`（**无** `spaceId`） |
| POST | `/model-configs/test` | `testModelConfig` | 测试调用：body 含 `testPrompt`、`modelName`（可空则使用库中已存模型名）；服务端按当前用户读库中密钥与 Base URL 后外呼大模型 |

**说明**：`createJd`、`createInterview` 等仍依赖 `spaceId`；简历正文更新以 **PUT** 为主（`updateResumeDocument` / `updateResumeDocumentById`）。岗位新建为 **POST**；与「严格 REST 幂等编辑」不一致处若后续收敛，需同步改前端。

### 8.3 页面与接口的对应关系（按 Tab）

| Tab / 区域 | 主要调用的接口 |
|------------|----------------|
| 侧栏空间 | `listSpaces`、`createSpace`、`listRecycleBinSpaces`、`renameSpace`、`recycleSpace`、`restoreSpace` |
| 用户管理 | `registerByPhone`、`loginByPhone`、`logoutSession`（退出登录）、账号展示等 |
| 简历 | `listAllResumeDocuments`、`listResumeDocuments`、`getResumeDocumentById`、`createResumeDocumentMine`、`updateResumeDocument`、`updateResumeDocumentById`、`deleteResumeDocumentEntire`、`deleteResumeDocument`、`linkResumeToSpace` |
| 岗位 | `listAllJobPositions`、`listJobPositions`、`createJobPosition`、`updateJobPosition`、`closeJobPosition`、`linkJobToSpace`、`unlinkJobFromSpace`、`listJd`、`createJd`、`analyzeJdFocusPoints` |
| 空间管理 | 聚合与绑定弹窗：`listAllResumeDocuments`、`listAllJobPositions`、`linkResumeToSpace`、`deleteResumeDocument`（解绑本空间）、`linkJobToSpace`、`unlinkJobFromSpace`；另依赖空间列表/各空间下资源等既有接口 |
| 标准题库 | `getAnswerBank`、`saveAnswerBank`（在题库 Tab 内保存） |
| 模拟面试 | `listInterview`、`createInterview('mock', …)`（保存流程内含 `upsertPrimaryJobFromForm` 等，依赖当前空间） |
| 正式面试 | `listInterview`、`createInterview('real', …)`（同上） |
| 系统设置 | `getModelConfig`、`saveModelConfig`、`testModelConfig` |
| 回收站 | `listRecycleBinSpaces`、`restoreSpace`（及可能的硬删 `deleteSpace`） |
| 登录/注册（弹窗） | `registerByPhone`、`loginByPhone` |

### 8.4 非 `business` 的浏览器直连（百炼 OpenAPI）

以下 **不经过** `8080` business，由 **`App.vue` 内 `fetch`** 直接请求当前空间配置中的 **`baseUrl`**（如兼容模式 `.../chat/completions` 或 Anthropic 网关 `.../v1/messages`）：

| 场景 | 说明 |
|------|------|
| `callBailianChat` | 使用 `modelConfig` 中的 **API Key** 与 `baseUrl` 组装请求（兼容 OpenAI Chat 与 Anthropic Messages 两种网关）。 |
| 调用方 | 例如 **JD 一键拆解** 等仍在前端直连百炼的能力。 |

**说明**：系统设置中的 **「测试调用」** 已改为经 **`POST /api/v1/business/model-configs/test`** 由服务端读库密钥后外呼，不再走本节所述的浏览器直连。

密钥与模型名来源：用户在前端填写的配置（及/或自 `getModelConfig` 回填）；涉及安全与合规时建议更多能力改为 **后端代理** 再调百炼。

---

## 9. 面试总结序列化（`MM_INTERVIEW_V2`）

JSON 载荷字段（概念）：

- `summaryText`：模块一整体评价文本
- `finalResult`：最终结果描述（如 offer / 挂科 / 自评等）
- `reviewCards[]`：复盘题目数组，每项含 `id`、`title`、`questionRecord`、`answerRecord`、`pros`、`cons`、`improvementPlan`、`standardAnswer`

持久化字符串形态：`MM_INTERVIEW_V2::` + `JSON.stringify(payload)`。

---

## 10. 边界、取舍与扩展建议

| 项目 | 现状 |
|------|------|
| `interviewMeta` / `mockMeta` | 仅前端展示，**未写入**面试记录表；若需审计或跨端同步，可扩展后端字段 |
| 百炼 API Key | 存于后端按空间配置；前端直连百炼时密钥仍可能出现在浏览器网络层，强安全场景建议改为 **后端代理调用** |
| 单文件 `App.vue` | 便于原型迭代；规模继续增长时可拆为 **views + composables + components** |
| `SpacePage.vue` | 多空间演示页；主工作台以 `App.vue` 为准 |

---

## 11. 文档维护

- 本文档路径：`doc/business-web-b-end-guide.md`
- 若 B 端交互或 **第 8 节所列接口** 有重大变更，请同步更新 `web/src/api.js`、本节与 `README.md` 中的入口说明。
