# REQ-0002 vision-requirements

## 愿景

- 目标：启动 MienMien 全量一期（B 端 + C 端 + 实时指导 + 四端可运行）需求定义。
- 业务价值：形成可执行、可验收、可审计的统一产品与技术基线，支撑后续持续迭代。
- 成功指标：
  - REQ-0002 范围与边界冻结；
  - 四端交付目标与后端双域职责明确；
  - 后续任务可直接按 `plan-task.md` 执行并追踪。
  - 一期完成后具备真实求职场景闭环：建立资料 -> 模拟演练 -> 实战复盘 -> 迭代提升。

## 技术栈锁定（Web 优先）

- 后端：`Java 21` + `Spring Boot 3` + `Maven` + `MySQL`（本地库名 **`MienMieApp`**，utf8mb4）
- Web：`Vue 3` + `Vite` + `JavaScript` + `Pinia` + `Vue Router`
- 实时链路（首期）：`SSE`（后续可升级 `WebSocket`）
- 架构规范：Java 侧严格遵循 DDD 分层与《阿里巴巴 Java 开发手册》

说明：因项目宪法对 `web/` 目录语言约束为 JavaScript/HTML/CSS，首期 Web 不使用 TypeScript。

## 需求

- 功能需求（FR）：
  - FR-001：B 端支持多空间、简历管理、岗位管理、JD 管理、模拟面试与面试管理；
  - FR-002：C 端支持模拟面试、实时面试指导、拍照识别问答；
  - FR-003：后端区分 `java/business` 与 `java/consumer`，数据库共用；
  - FR-004：Web 对齐 B 端，iOS/Android 对齐 C 端并保持页面一致。
- 非功能需求（NFR）：
  - NFR-001：遵循简化 SDD 流程；
  - NFR-002：Java 遵循 DDD 与阿里 Java 开发手册；
  - NFR-003：门禁与治理规则持续可执行（CLA/SDD）。
- 范围内：
  - 一期全量能力的需求冻结、架构拆解、任务编排与执行追踪。
- 范围外：
  - 二期优化项与长期运营策略。

## FR 首期最小实现定义（MVP）

- FR-001（B 端管理域）
  - MVP 边界：空间 CRUD、主简历版本化、目标 JD 管理、模拟/真实面试记录管理
  - 非首期：复杂报表、批量导入导出、高级搜索
- FR-002（C 端交互域）
  - MVP 边界：模拟面试、实时指导（语音问题->流式回答）、拍照识别问答
  - 非首期：离线语音包、复杂多轮策略学习
- FR-003（后端双域）
  - MVP 边界：`java/business` 与 `java/consumer` 独立应用层，数据库共用但逻辑隔离
  - 非首期：多租户物理分库分表
- FR-004（端侧对齐）
  - MVP 边界：Web 完成 B 端闭环；iOS/Android 完成 C 端主流程与页面同构
  - 非首期：多主题 UI 系统、端侧插件机制

## 首期实现裁决（与当前代码对齐）

- `JobPosition`：已落地独立表 `mm_job_position` 与 REST API（与 `JdTarget` 并存；JD 仍表示「目标岗位描述」，JobPosition 表示「在招岗位编制」）。
- `Space`：**重命名**（`PUT /spaces/{id}`）、**归档**（`DELETE /spaces/{id}`，无关联数据时）；列表仅展示 `ACTIVE` 空间。
- `GuidanceSession.endedAt`：首期已支持用户主动结束（`POST /api/v1/consumer/sessions/{sessionId}/end`）；历史库需执行 [`scripts/migrate-mm-guidance-ended-at.sql`](../../../../scripts/migrate-mm-guidance-ended-at.sql) 补齐列。
- 状态字段：首期以 `init/listening/analyzing/completed/failed` 为主；`responding` 可由后续实时链路细化。

## FR 功能矩阵（首期对照）

| FR | 能力 | 主要实现位置 | 首期未做/简化 |
| --- | --- | --- | --- |
| FR-001 | 多空间、简历、JD、面试、岗位 | [`java/business/management/`](../../../../java/business/src/main/java/com/mienmien/business/management/)、[`web/src/`](../../../../web/src/) | 空间物理删除、复杂权限；`BUS-4091`/`BUS-4092` 已映射 HTTP 409 |
| FR-002 | 模拟/实时指导、拍照问答、文本兜底 | [`java/consumer/guidance/`](../../../../java/consumer/src/main/java/com/mienmien/consumer/guidance/) | 真实 ASR/视觉需替换 `ClientQuestionEnrichmentPolicy` 实现；`CON-5031` 另支持 `GET /health/stream` 503 预检 |
| FR-003 | B/C 双应用、库表共用 | `java/business`、`java/consumer`、[`scripts/seed-mienmien.sql`](../../../../scripts/seed-mienmien.sql) | 物理多租户分库分表 |
| FR-004 | Web=B，移动=C | [`web/`](../../../../web/)、[`android/`](../../../../android/)、[`ios/`](../../../../ios/) | 同构 UI 细部差异可后续收敛 |

## 核心实体与字段草案（L4）

### B 端核心实体

- `Space`
  - `spaceId`、`ownerUserId`、`name`、`status`、`createdAt`、`updatedAt`
- `Resume`
  - `resumeId`、`spaceId`、`version`、`content`、`isActive`、`updatedAt`
- `JobPosition`
  - `positionId`、`spaceId`、`title`、`company`、`location`、`baseRange`、`status`
- `JdTarget`
  - `jdId`、`spaceId`、`sourceType`、`rawText`、`focusPoints[]`
- `InterviewRecord`（含模拟与真实）
  - `recordId`、`spaceId`、`type`（mock/real）、`round`、`interviewType`、`score`、`result`、`summary`

### C 端核心实体

- `GuidanceSession`
  - `sessionId`、`userId`、`mode`（mock/live/photo）、`status`、`startedAt`、`endedAt`
- `QuestionEvent`
  - `eventId`、`sessionId`、`source`（voice/photo/text）、`questionText`、`timestamp`
- `AnswerStream`
  - `streamId`、`sessionId`、`questionEventId`、`chunks[]`、`finalAnswer`

## API 契约草案（L4）

### B 端（Web 对齐）

- `POST /api/v1/business/spaces`：创建空间
- `GET /api/v1/business/spaces`：列出 **ACTIVE** 空间
- `GET /api/v1/business/spaces/{spaceId}`：查询空间详情
- `PUT /api/v1/business/spaces/{spaceId}`：重命名（body: `name`）
- `DELETE /api/v1/business/spaces/{spaceId}`：归档（无简历/JD/面试/在招岗位时）
- `POST /api/v1/business/resumes`：新增简历版本（`space_id+version` 唯一；冲突 `BUS-4091` / HTTP 409）
- `POST /api/v1/business/jd-targets`：新增目标 JD
- `POST /api/v1/business/job-positions`：新增在招岗位
- `GET /api/v1/business/job-positions/{spaceId}`：按空间列出岗位
- `PUT /api/v1/business/job-positions/item/{positionId}`：更新岗位
- `DELETE /api/v1/business/job-positions/item/{positionId}`：关闭岗位
- `POST /api/v1/business/interviews/mock`：创建模拟面试记录
- `POST /api/v1/business/interviews/real`：创建真实面试记录

请求/响应样例（`POST /api/v1/business/spaces`）：

```json
{
  "request": {
    "ownerUserId": "user_001",
    "name": "后端求职空间"
  },
  "response": {
    "spaceId": "sp_1001",
    "ownerUserId": "user_001",
    "name": "后端求职空间",
    "status": "ACTIVE",
    "createdAt": "2026-04-09T10:00:00+08:00"
  }
}
```

### C 端（iOS/Android 对齐）

- `POST /api/v1/consumer/sessions`：创建指导会话
- `POST /api/v1/consumer/sessions/{sessionId}/end`：结束会话（写入 `endedAt`，后续禁止继续提问）
- `POST /api/v1/consumer/sessions/{sessionId}/events/voice`：提交语音识别问题
- `POST /api/v1/consumer/sessions/{sessionId}/events/photo`：提交图片识别问题
- `POST /api/v1/consumer/sessions/{sessionId}/events/text`：文本兜底问题（语音识别失败时使用）
- `GET /api/v1/consumer/sessions/{sessionId}/answers/stream`：订阅流式回答
- `GET /api/v1/consumer/health/stream`：流式服务预检（配置降级时返回 HTTP 503 + `CON-5031`）

请求/响应样例（`POST /api/v1/consumer/sessions`）：

```json
{
  "request": {
    "userId": "user_001",
    "mode": "live"
  },
  "response": {
    "sessionId": "gs_2001",
    "userId": "user_001",
    "mode": "live",
    "status": "init",
    "startedAt": "2026-04-09T10:10:00+08:00"
  }
}
```

## 错误码规范（首期）

- `BUS-4001`：空间名称非法
- `BUS-4041`：空间不存在
- `BUS-4091`：同一空间下简历 **版本号** 已存在（HTTP 409）
- `BUS-4092`：空间仍存在关联数据，无法归档（HTTP 409）
- `CON-4001`：会话模式非法
- `CON-4041`：会话不存在
- `CON-4091`：会话已结束或不可继续写入（HTTP 409）
- `CON-5031`：流式回答服务暂不可用（触发降级）

## 状态机草案（L4）

- `GuidanceSession.status`
  - `init -> listening -> analyzing -> responding -> completed`
  - 异常路径：任意状态可转 `failed`

## 验收标准（L4）

- AC-001：同一用户可创建多个 `Space`，不同空间数据完全隔离。
- AC-002：每个空间可维护一份主简历并可版本化管理。
- AC-003：每个空间支持多个目标 JD，并可提炼重点考查内容。
- AC-004：模拟/真实面试记录均可保存评分、总结、题目与改进建议。
- AC-005：C 端实时指导可接收语音问题并返回流式回答。
- AC-006：iOS 与 Android 页面结构及交互流程保持一致。
- AC-007：Web 可完成 B 端核心管理流程闭环。

## 测试矩阵（L4）

- 单元测试：实体规则、状态机转换、评分逻辑。
- 集成测试：B/C API 契约、共享数据库隔离约束、流式回答接口。
- E2E：
  - Web：空间 -> 简历 -> JD -> 模拟面试记录
  - Mobile：语音输入 -> 问题识别 -> 流式回答 -> 结果确认

## 回退策略（L4）

- 实时流式回答异常时，降级为“非流式一次性回答”。
- 语音识别失败时，允许文本手动输入问题。
- 图片识别失败时，保留上传并给出重试建议。

## DDD 要点（Java 后端适用）

- 限界上下文：
  - `java/business`：B 端管理域（空间、简历、JD、面试管理）
  - `java/consumer`：C 端交互域（模拟面试、实时指导、拍照问答）
- 聚合根：
  - 预设为 `Space`、`ResumeProfile`、`InterviewSession`（后续可在任务阶段细化）。
- 关键不变量：
  - 空间数据必须隔离；
  - 面试记录与评分归属必须可追溯；
  - 实时指导链路需保证请求-响应关联一致性。
  - `Space` 删除前必须先处理关联简历/JD/面试记录（软删除或迁移策略）。

## 依据清单（项目内 + 外部）

- 项目内依据：
  - `doc/sdd/constitution.md`（模块边界、语言约束、治理要求）
  - `README.md`（产品愿景与平台职责）
  - `CONTRIBUTING.md`（门禁、流程、合规约束）
- 外部依据：
  - 《阿里巴巴 Java 开发手册》（Java 命名、异常、并发、分层规范）
  - [RFC 9110 HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110)
  - [HTML Living Standard - EventSource](https://html.spec.whatwg.org/multipage/server-sent-events.html)

## 可运行前置条件（首期）

- 本机安装：
  - `java -version` 显示 `21`
  - `mvn -v` 可用
  - `node -v` 与 `npm -v` 可用
  - `mysql --version` 可用（或使用 Docker 启动 `scripts/dev-db-up.sh` 所依赖的 MySQL 容器）
- 必要环境变量（示例）：
  - `DB_HOST=localhost`
  - `DB_PORT=3306`
  - `DB_NAME=MienMieApp`
  - `DB_USER=root`
  - `DB_PASSWORD=`（本机无密码则留空；Docker 默认与 compose 一致时常为 `root`）
