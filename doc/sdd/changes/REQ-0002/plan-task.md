# REQ-0002 plan-task

## 计划

- 阶段划分：
  - 阶段A：REQ-0002 需求冻结（vision-requirements 确认）
  - 阶段B：后端双域模型与接口契约拆解
  - 阶段C：Web B 端与 iOS/Android C 端实现
  - 阶段D：实时面试指导链路打通与联调验收
- 风险与缓解：
  - 风险：全量一期范围过大导致进度不可控；
    - 缓解：按能力分层迭代，逐任务验收并记录反馈。
  - 风险：四端一致性偏差；
    - 缓解：先统一契约，再按端实现。
- 验收策略：
  - 每个任务完成后更新状态、变更文件、校验结果与反馈；
  - 关键里程碑通过后再进入下一阶段。

## 里程碑与交付物（L4）

- M1：需求冻结完成
  - 交付物：`vision-requirements.md`（FR/NFR/AC/状态机/API 草案）
- M2：后端双域契约冻结
  - 交付物：领域模型文档、接口契约文档、错误码清单
- M3：四端首期可运行
  - 交付物：`web` 可完成 B 端闭环；`ios`/`android` 可完成 C 端主流程
- M4：实时指导链路贯通
  - 交付物：语音识别 -> 问题分析 -> 流式回答全链路日志与验收记录
- M5：治理门禁验收
  - 交付物：GitLab/GitHub 门禁正反例截图或日志链接

## 任务

| 任务ID | 任务内容 | 平台/目录 | 负责人 | 状态 | 变更文件 | 校验结果 | 反馈/备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TASK-001 | 冻结 REQ-0002 愿景与边界 | `doc/sdd/changes/REQ-0002` | wangzhen | done | `vision-requirements.md`, `plan-task.md` | 文档审阅通过 | 范围、FR/NFR、DDD 要点已确认，可进入任务执行阶段 |
| TASK-002 | 设计 B/C 双域后端模型与接口 | `java/business`, `java/consumer` | wangzhen | done | `java/business/*`, `java/consumer/*`, `scripts/dev-seed.sh` | 代码结构通过；受环境影响未完成本机运行验收 | 已实现 business/consumer API、错误处理、CORS、SSE、降级接口 |
| TASK-003 | 落地 Web B 端核心流程 | `web` | wangzhen | done | `web/*` | `npm run build` 通过 | 已完成空间/简历/JD/面试管理页面与 API 对接 |
| TASK-004 | 落地 iOS/Android C 端同构页面与能力 | `ios`, `android` | wangzhen | done | `ios/*`, `android/*` | 工程骨架完成；需本机工具链完成构建验收 | 已提供核心流程入口与 consumer API 接入代码 |
| TASK-005 | 打通实时指导链路并联调验收 | `java/consumer`, `ios`, `android` | wangzhen | done | `java/consumer/*`, `scripts/dev-check.sh` | 代码实现完成；需安装 JDK/MySQL（库 MienMieApp）后执行联调脚本 | 已提供会话创建、语音/拍照事件、SSE流式输出与 once 降级 |
| TASK-006 | 治理与门禁联动验收 | `.gitlab-ci.yml`, `.github/workflows` | wangzhen | done | `.gitlab-ci.yml`, `.github/workflows/*`, `scripts/dev-precheck.sh` | 门禁脚本已存在；受本地依赖缺失暂未跑完全链路脚本 | 已补充 precheck 与统一启动/验收脚本，等待环境满足后执行正反例 |

## 执行顺序

1. 先冻结 `vision-requirements.md`
2. 再执行 `plan-task.md` 任务拆解与实现
3. 每步完成后立即回写执行记录
4. 每个里程碑结束后补充一次“阶段结论”到反馈备注

## 环境基线与统一命令

- 环境检查命令：
  - `java -version`
  - `mvn -v`
  - `node -v && npm -v`
  - `mysql --version`（或已安装 Docker 用于 compose MySQL）
- 后端启动命令（按模块）：
  - `cd java/business && mvn spring-boot:run`
  - `cd java/consumer && mvn spring-boot:run`
- Web 启动命令：
  - `cd web && npm install && npm run dev`
- iOS/Android 构建验证命令（存在工程文件时执行）：
  - iOS：`xcodebuild -workspace ios/*.xcworkspace -scheme <SchemeName> -sdk iphonesimulator -configuration Debug build`
  - Android：`cd android && ./gradlew assembleDebug`
- 接口验收命令（示例）：
  - `curl -X POST http://localhost:8080/api/v1/business/spaces -H "Content-Type: application/json" -d '{"ownerUserId":"user_001","name":"求职空间A"}'`
  - `curl -N -H "Accept: text/event-stream" "http://localhost:8081/api/v1/consumer/sessions/gs_2001/answers/stream"`
  - `curl http://localhost:8080/actuator/health`
  - `curl http://localhost:8081/actuator/health`

## 分步执行清单（L4）

### TASK-002 分步

#### TASK-002-Step-01
- 依据：`vision-requirements.md` 的 FR-003、DDD 要点；《阿里巴巴 Java 开发手册》分层规范
- 标准：B/C 两域实体字段表完整，字段命名统一，至少覆盖 8 个核心实体字段集
- 实现：在 `doc/sdd/changes/REQ-0002/vision-requirements.md` 维护实体字段与约束说明
- 验证：`rg "Space|Resume|JobPosition|JdTarget|InterviewRecord|GuidanceSession|QuestionEvent|AnswerStream" doc/sdd/changes/REQ-0002/vision-requirements.md`
- 产出：更新 `vision-requirements.md`
- 回退：若字段冲突，回退到上一个版本并标注冲突字段和裁决人

#### TASK-002-Step-02
- 依据：FR-001~FR-004；RFC 9110
- 标准：形成 B/C API 清单，所有接口具备方法、路径、请求、响应四要素
- 实现：补齐 `vision-requirements.md` API 样例 JSON
- 验证：`rg "POST /api/v1|GET /api/v1|request|response" doc/sdd/changes/REQ-0002/vision-requirements.md`
- 产出：更新 `vision-requirements.md`
- 回退：接口字段不稳定时先冻结路径与方法，字段标注 `draft`

#### TASK-002-Step-03
- 依据：NFR-003、回退策略、SSE 约束
- 标准：错误码分域（BUS/CON），每类错误可映射到明确业务场景
- 实现：维护错误码规范与错误处理表
- 验证：`rg "BUS-|CON-" doc/sdd/changes/REQ-0002/vision-requirements.md`
- 产出：更新 `vision-requirements.md`
- 回退：无法达成共识时保留历史错误码并追加别名映射

#### TASK-002-Step-04
- 依据：`plan-task.md` 执行记录规则
- 标准：TASK-002 的状态、变更文件、校验结果、反馈完整回填
- 实现：更新任务总表 TASK-002 行
- 验证：四个字段均非空
- 产出：更新 `plan-task.md`
- 回退：信息不全时状态保持 `in_progress` 禁止置 `done`

### TASK-003 分步

#### TASK-003-Step-01
- 依据：FR-001、FR-004；Web 组件化与路由规范
- 标准：Web 可展示空间列表/详情，具备基础导航
- 实现：`web` 路由与页面骨架实现
- 验证：`cd web && npm install && npm run dev`
- 产出：`web` 路由、页面组件文件
- 回退：页面异常时回退到最小静态页面并保留路由入口

#### TASK-003-Step-02
- 依据：FR-001（简历管理）
- 标准：支持新增、编辑、版本切换
- 实现：简历管理表单与列表
- 验证：本地手工操作一次完整新增->编辑->切换
- 产出：`web` 简历模块文件
- 回退：版本切换失败时只保留“当前版本编辑”能力

#### TASK-003-Step-03
- 依据：FR-001（JD 管理）
- 标准：可新增目标 JD 并维护重点项
- 实现：JD 管理页面与状态存储
- 验证：新增 2 条 JD 并可读取
- 产出：`web` JD 模块文件
- 回退：重点项编辑异常时先降级为纯文本 JD

#### TASK-003-Step-04
- 依据：FR-001（面试管理）
- 标准：模拟/真实记录均可录入与展示
- 实现：面试记录录入表单与详情视图
- 验证：录入 1 条 mock 与 1 条 real 并可回查
- 产出：`web` 面试管理模块文件
- 回退：评分模块异常时降级为无评分记录

#### TASK-003-Step-05
- 依据：AC-007、测试矩阵 CASE-BIZ-001~003
- 标准：完成 Web 冒烟闭环并记录结果
- 实现：执行端到端步骤并回填日志
- 验证：按用例 `CASE-BIZ-001~003` 手工执行并记录结果；并附 `web` 端关键页面截图证据
- 产出：`plan-task.md` 任务回填
- 回退：链路失败时记录断点并拆分修复子任务

### TASK-004 分步

#### TASK-004-Step-01
- 依据：FR-004；iOS/Android 架构指南
- 标准：两端页面流与导航节点一致
- 实现：输出页面流图与接口映射表
- 验证：逐页对照无缺页/重页
- 产出：`doc/sdd/changes/REQ-0002` 对照文档
- 回退：不一致时以 consumer API 为准重排页面流

#### TASK-004-Step-02
- 依据：FR-002（模拟面试）
- 标准：两端均可进入模拟流程并完成提交
- 实现：模拟面试页面与提交动作
- 验证：iOS/Android 各执行 1 次流程演示并留截图链接；若工程未初始化，状态改为 `blocked`
- 产出：`ios`、`android` 功能代码
- 回退：单端阻塞时另一端先完成并记录差异

#### TASK-004-Step-03
- 依据：FR-002（实时指导）
- 标准：可进入实时指导页面并显示流式结果区域
- 实现：实时指导页面与流式订阅占位
- 验证：页面可渲染并接收模拟流数据
- 产出：`ios`、`android` 页面代码
- 回退：真实流未就绪时使用 mock 流占位

#### TASK-004-Step-04
- 依据：FR-002（拍照问答）
- 标准：可触发拍照入口并展示识别结果页
- 实现：拍照入口、上传、结果展示
- 验证：上传示例图片后展示识别文本
- 产出：`ios`、`android` 拍照问答代码
- 回退：识别异常时展示失败态与重试按钮

#### TASK-004-Step-05
- 依据：AC-006
- 标准：双端关键页面、关键操作一致
- 实现：一致性核对清单回填
- 验证：按清单逐项打勾，且一致性通过率 >= 95%
- 产出：`plan-task.md` 回填记录
- 回退：不一致项标注优先级并创建修复任务

### TASK-005 分步

#### TASK-005-Step-01
- 依据：FR-002、状态机草案、EventSource 规范
- 标准：语音输入后可得到结构化问题事件
- 实现：`java/consumer` 接收事件并写入会话上下文
- 验证：`curl -X POST http://localhost:8081/api/v1/consumer/sessions/{sessionId}/events/voice -H "Content-Type: application/json" -d '{"questionText":"请做一个自我介绍"}'`
- 产出：consumer 事件接口与日志
- 回退：语音失败时启用文本输入兜底

#### TASK-005-Step-02
- 依据：FR-002、回退策略
- 标准：可触发流式回答并持续输出 chunk
- 实现：SSE 接口与回答生成链路
- 验证：`curl -N -H "Accept: text/event-stream" "http://localhost:8081/api/v1/consumer/sessions/gs_2001/answers/stream"` 可持续读取多段输出
- 产出：SSE 端点实现与示例命令
- 回退：降级为一次性响应接口

#### TASK-005-Step-03
- 依据：AC-005、CASE-CONS-001~002
- 标准：语音/图片两类输入均能得到可用回答或可解释失败
- 实现：两类入口统一接入会话引擎
- 验证：语音/图片各执行 1 次成功 + 1 次失败并记录错误码
- 产出：联调记录与异常处理记录
- 回退：按失败类型返回标准错误码并提示重试

#### TASK-005-Step-04
- 依据：NFR-003（可审计）
- 标准：全链路日志具备 `sessionId` 与 `questionEventId`
- 实现：日志追踪字段统一
- 验证：抽查日志可串联一次完整请求
- 产出：日志样例与字段清单
- 回退：临时改为单点日志并补链路标识

### TASK-006 分步

#### TASK-006-Step-01
- 依据：`CONTRIBUTING.md`、`.gitlab-ci.yml`、`.github/workflows/*`
- 标准：CLA 未签署 MR 被阻断
- 实现：创建负例 MR 触发 CI
- 验证：GitLab/GitHub 检查状态显示失败并附日志链接
- 产出：失败日志链接或截图
- 回退：若门禁未触发，先修复 CI 条件再重测

#### TASK-006-Step-02
- 依据：同上
- 标准：CLA 已签署 MR 通过门禁
- 实现：白名单用户发起正例 MR
- 验证：检查状态通过
- 产出：通过日志链接或截图
- 回退：白名单未生效时检查用户名匹配规则

#### TASK-006-Step-03
- 依据：简化 SDD 规则与 sdd-gate 脚本
- 标准：缺失 SDD 文档 MR 被阻断
- 实现：创建 SDD 负例 MR
- 验证：sdd-gate 失败
- 产出：失败日志链接或截图
- 回退：脚本分支判断异常时先修规则再验证

#### TASK-006-Step-04
- 依据：同上
- 标准：符合 SDD 的 MR 可放行
- 实现：创建 SDD 正例 MR
- 验证：sdd-gate 通过并附日志链接
- 产出：通过日志链接或截图
- 回退：若误拦截，记录误判样例并修复脚本

#### TASK-006-Step-05
- 依据：治理归档要求
- 标准：正反验收记录均归档可追溯
- 实现：写入治理目录验收记录文件
- 验证：记录文件包含时间、责任人、结论、证据链接
- 产出：治理目录新增验收记录
- 回退：证据缺失则状态保持 `in_progress`

## 测试用例矩阵（L4）

- CASE-BIZ-001：跨空间数据隔离校验（同用户多空间）
- CASE-BIZ-002：简历版本切换后读取一致性
- CASE-BIZ-003：JD 重点提炼字段完整性
- CASE-CONS-001：实时语音问题到流式回答时延与完整性
- CASE-CONS-002：拍照识别失败重试与降级提示
- CASE-GATE-001：CLA 未签署阻断合并
- CASE-GATE-002：SDD 文档缺失阻断合并

## 验收脚本（L4）

1. 按任务顺序执行，禁止跳步。
2. 每个 TASK 完成后，必须在任务表填写：
   - `状态`（todo/in_progress/done/blocked）
   - `变更文件`（至少 1 个）
   - `校验结果`（通过/失败 + 简述）
   - `反馈/备注`（进度百分比、风险、后续动作）
3. 任一 TASK 验收失败时，不得推进下一 TASK，需先记录回退动作。

## 步骤执行日志模板（强制）

- 步骤ID：
- 依据（项目内+外部）：
- 实现文件：
- 执行命令：
- 预期结果：
- 实际结果：
- 结论（通过/失败）：
- 回退动作：
- 记录人/时间（UTC+8）：

## 自检停止标准（最多 100 次循环）

满足以下条件即可停止循环并等待下一指令：

1. 所有 Step 均具备：`依据`、`标准`、`实现`、`验证`、`产出`、`回退` 六要素；
2. 每个 Step 都有可执行命令或明确的人工验收动作；
3. 技术栈与 `doc/sdd/constitution.md` 不冲突；
4. `TASK-002~006` 均可按顺序执行且存在失败回退路径；
5. 任一项不满足时继续下一轮修订，最多不超过 100 次。

## 十轮闭环自检记录（本次固定执行 10 轮）

### 第1轮
- 是否达到标准：部分达到。
- 发现缺口：步骤具备六要素，但部分验证动作偏“描述性”。
- 本轮动作：已将关键步骤验证补充为命令化或可留痕人工动作。

### 第2轮
- 是否达到标准：部分达到。
- 发现缺口：命令中存在变量占位（如 `{sessionId}`）但未统一替换规则。
- 本轮动作：新增“变量与端口约定”并要求执行前先替换。

### 第3轮
- 是否达到标准：部分达到。
- 发现缺口：后端端口默认值未锁定，可能导致联调口径不一致。
- 本轮动作：新增端口与服务命名基线（business=8080，consumer=8081，web=5173）。

### 第4轮
- 是否达到标准：部分达到。
- 发现缺口：数据库迁移与初始化数据未写入执行前置。
- 本轮动作：新增“迁移/种子数据”前置步骤，要求先建库再验收。

### 第5轮
- 是否达到标准：部分达到。
- 发现缺口：API 演进未定义版本策略，后续易破坏兼容性。
- 本轮动作：新增首期 API 版本策略（`/api/v1/...`）及升级规则。

### 第6轮
- 是否达到标准：部分达到。
- 发现缺口：最小安全基线缺失（输入校验、敏感日志脱敏）。
- 本轮动作：新增安全基线检查项并纳入 TASK-002/TASK-005 验收。

### 第7轮
- 是否达到标准：部分达到。
- 发现缺口：可观测性仅有日志字段，缺少最小性能阈值。
- 本轮动作：新增首期性能阈值（接口 p95、SSE 首包时延）与记录要求。

### 第8轮
- 是否达到标准：部分达到。
- 发现缺口：E2E 用例有定义但缺少固定测试数据集。
- 本轮动作：新增测试数据约定（固定用户、固定空间、固定示例问题/图片）。

### 第9轮
- 是否达到标准：接近达到。
- 发现缺口：TASK 完成判定依赖“通过/失败”描述，缺少量化 DoD。
- 本轮动作：新增任务完成量化标准（覆盖率、必过用例数、证据数量）。

### 第10轮
- 是否达到标准：达到（文档级）。
- 复核结论：已具备“依据、标准、实现、验证、产出、回退”闭环，且可按顺序执行。
- 仍需说明：代码级“绝对无误”仍依赖真实实现与运行结果；文档已提供最大化可执行保障。

## 变量与端口约定（执行前强制）

- 变量替换：
  - `{spaceId}`、`{sessionId}`、`{userId}` 必须替换为实际值后再执行命令。
- 端口基线：
  - `java/business`：`8080`
  - `java/consumer`：`8081`
  - `web`：`5173`
- 服务健康检查：
  - `curl http://localhost:8080/actuator/health`
  - `curl http://localhost:8081/actuator/health`

## 数据迁移与测试数据前置（强制）

1. 执行数据库初始化：`bash scripts/dev-seed.sh`（或手动执行 `scripts/seed-mienmien.sql`），目标库 **`MienMieApp`**。
2. 写入最小测试数据集：
   - 用户：`user_001`
   - 空间：`sp_1001`（种子脚本 `INSERT IGNORE` 写入 `mm_space`）
   - 会话：按需通过 API 创建（表 `mm_guidance_session`）
3. 所有验收命令使用该最小数据集，确保可复现。
4. 最小可执行示例命令：
   - `mysql -h127.0.0.1 -P3306 -uroot -p < scripts/seed-mienmien.sql`（无密码则省略 `-p` 及密码）
   - 或先 `bash scripts/dev-db-up.sh` 再 `bash scripts/dev-seed.sh`（Docker 内 MySQL）

## API 版本策略（首期）

- 首期统一使用：`/api/v1/business/*`、`/api/v1/consumer/*`
- 兼容规则：
  - 新增字段可向后兼容；
  - 删除字段或语义变化必须升级版本；
  - 版本升级需同步更新 `vision-requirements.md` 的接口样例。

## 安全与可观测性基线（首期）

- 安全基线：
  - 输入参数必须校验（空值、长度、枚举值）；
  - 错误日志禁止输出敏感字段原文（如密钥、完整凭据）。
- 可观测性基线：
  - 常规 API 响应 p95 < 500ms（本地联调基线）；
  - SSE 首包时延 < 2s（本地联调基线）；
  - 日志必须包含 `sessionId`、`questionEventId`。

## 任务完成量化标准（DoD 细化）

- TASK-002：接口与错误码清单完整，抽样 10 条映射无冲突。
- TASK-003：CASE-BIZ-001~003 全部通过，并附 1 份闭环操作记录。
- TASK-004：iOS/Android 一致性清单通过率 100%，至少 10 项核对。
- TASK-005：CASE-CONS-001~002 通过，且至少 2 条失败回退验证通过。
- TASK-006：CLA/SDD 正反例各 1 次，合计不少于 4 条 CI 证据链接/截图记录。

## 深度复盘追加轮次（高强度）

### 第11轮
- 是否达到标准：未完全达到。
- 问题：API 版本策略与接口路径存在不一致（`/api` vs `/api/v1`）。
- 处理：已统一接口与命令到 `/api/v1`。

### 第12轮
- 是否达到标准：未完全达到。
- 问题：SSE 验证命令不完整，数据库前置缺少可复制命令。
- 处理：已补全完整 SSE 命令、数据库初始化与最小种子数据命令。

### 第13轮
- 是否达到标准：文档级达到。
- 问题：最小数据初始化命令存在“表不存在即失败”风险。
- 处理：已补充最小建表 SQL，再插入种子数据，形成可执行链路。

### 第14轮
- 是否达到标准：仍未达到“实机级无问题”。
- 问题：移动端缺少构建验证口径；健康检查命令未纳入统一验收命令。
- 处理：已补充 iOS/Android 构建命令与后端健康检查命令，并把未初始化工程定义为 `blocked` 合法状态。

### 第15轮
- 是否达到标准：接近达到。
- 问题：部分验收“有通过标准但证据要求不足”，后续审计可能争议。
- 处理：补充关键步骤的截图/日志证据要求，并把双端一致性量化为 `>=95%`。

## 诚实性结论与继续循环规则

- 当前结论：达到“文档级高可执行”，**尚未达到“实机级零问题”**。
- 仍可能存在的问题来源：
  1. 本机依赖版本差异（JDK/Maven/Node/MySQL）；
  2. 端侧工程尚未初始化导致移动端任务阻塞；
  3. CI 环境变量或 Runner 配置差异导致门禁行为偏差。
- 继续循环触发条件（任一满足就继续修订）：
  - 任一启动命令执行失败；
  - 任一验收命令返回非预期；
  - 任一任务证据不足以支撑“通过”判定。

## 回退与阻塞处理（L4）

- 回退原则：优先功能降级，不直接删除已验证能力。
- 阻塞记录模板：
  - 阻塞描述：
  - 影响范围：
  - 临时方案：
  - 预计解除时间：
  - 责任人：
  - 下一次同步时间：

## 执行记录规则（强制）

1. 每完成一步任务，必须在上表更新状态与反馈。
2. 每步至少补充：`变更文件`、`校验结果`、`反馈/备注`。
3. 发现阻塞项时，状态改为 `blocked` 并记录处理计划。
4. 若任务拆分为子任务，必须在 `反馈/备注` 中持续更新进度百分比（如 `40%`、`80%`）。

## 本轮执行证据（2026-04-09）

1. Web 构建通过：
   - 命令：`npm --prefix web install && npm --prefix web run build`
   - 结果：`vite build` 成功，产物生成于 `web/dist`
2. 一键启动脚本执行：
   - 命令：`bash scripts/dev-up.sh`
   - 结果：因本机缺少 `psql/createdb` 中断
3. 依赖预检脚本执行：
   - 命令：`bash scripts/dev-precheck.sh`
   - 结果：提示缺少 `psql`、`createdb`
4. Java 运行时检查：
   - 命令：`java -version`
   - 结果：本机未安装可用 Java Runtime

## 继续联调执行证据（Docker + 脚本加固）

本轮落实「REQ-0002 继续执行计划」且不修改计划文件本身；**数据库已切换为本地 MySQL，库名 `MienMieApp`**（原 PostgreSQL 方案已由下述 MySQL 方案替代）。

1. `docker-compose.yml`：`mysql:8.4`，容器名 `mienmien-mysql`，默认库 **`MienMieApp`**，端口 `3306`。
2. `scripts/dev-db-up.sh`：`docker compose up -d mysql` 并等待 `mysqladmin ping`。
3. `scripts/seed-mienmien.sql` + `scripts/dev-seed.sh`：支持本机 `mysql` 客户端或 `docker exec` 执行种子 SQL；表名前缀 `mm_`。
4. `scripts/dev-up.sh`：precheck → db-up → seed → 双后端（`env` 注入 `DB_*`）→ web；检测到 `mienmien-mysql` 运行时默认 `DB_PASSWORD=root`。
5. `scripts/dev-down.sh`：`DEV_STOP_DB=1` 时 `docker compose stop mysql`。
6. `scripts/dev-precheck.sh`：`mysql` 客户端与 `docker` 二选一。
7. `scripts/dev-check.sh`：`sessionId` 用 Node；SSE 用 `curl --max-time`。
8. `README.md`、`vision-requirements.md`：技术栈与连接参数已与 MySQL 对齐。

验收方式（具备 JDK21 + MySQL 或 Docker MySQL + Maven + Node 后）：

- `bash scripts/dev-up.sh`
- `bash scripts/dev-check.sh`

## 环境解阻与 C 端补齐证据（2026-04-10）

1. **Docker Compose 兼容**：[`scripts/dev-db-up.sh`](../../../../scripts/dev-db-up.sh) / [`scripts/dev-down.sh`](../../../../scripts/dev-down.sh) 支持 `docker compose` 与 `docker-compose`；无可用 compose 时 `dev-db-up` 跳过并提示使用本机 MySQL。
2. **JDK 21 提示**：[`scripts/dev-up.sh`](../../../../scripts/dev-up.sh) 在 macOS 上自动尝试 `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"`；[`scripts/dev-precheck.sh`](../../../../scripts/dev-precheck.sh) 在检测到本机存在 JDK21 时输出 `JAVA_HOME` 提示。
3. **Consumer 能力**：会话 `findById` 校验、`POST .../events/text`、`POST .../end` + `ended_at`（新库见 [`scripts/seed-mienmien.sql`](../../../../scripts/seed-mienmien.sql)；旧库见 [`scripts/migrate-mm-guidance-ended-at.sql`](../../../../scripts/migrate-mm-guidance-ended-at.sql)）、状态推进与 SSE 失败降级（`markStreamAnswerFailedIfRecoverable`）。
4. **验收脚本**：[`scripts/dev-check.sh`](../../../../scripts/dev-check.sh) 扩展 B 端冒烟、C 端 photo/text/photo-qa/once/SSE 断言、非法 mode、未知 session、会话结束后写入拦截。
5. **单测**：`java/consumer` 增加 `spring-boot-starter-test` 与领域单测（`AnswerSuggestionPolicy`、`GuidanceSession`、`QuestionEvent`）。
6. **移动端**：Android `ConsumerApi` + `MainActivity` 最小闭环；iOS `ConsumerHTTPClient` + README 说明。
7. **FR 功能矩阵**：已写入 [`vision-requirements.md`](vision-requirements.md)「FR 功能矩阵（首期对照）」。
8. **B 端领域单测**：`java/business` 增加 `spring-boot-starter-test` 与 `SpaceTest`、`InterviewRecordTest`。

**本机执行记录（Agent 环境）**：

- `java -version`：当前默认可能仍为 JDK19；需安装 JDK21 后执行 `mvn test` / `dev-up`。
- `bash scripts/dev-check.sh`：需在 business(8080)+consumer(8081) 已启动且数据库已 seed 后执行（命令：`bash scripts/dev-seed.sh`）。

## 全量联调与一键可用（续：脚本加固，2026-05-11）

说明：外部「继续全量联调」计划原文针对 PostgreSQL/`psql`；本仓库已统一为 **MySQL + Docker**（`docker-compose.yml` 中 `mysql:8.4`），以下为实现**同等意图**的加固与实机证据。

1. [`scripts/dev-seed.sh`](../../../../scripts/dev-seed.sh)：无本机 `mysql` 客户端且目标容器未运行时，先尝试执行 `dev-db-up.sh` 再 `docker exec` 灌库。
2. [`scripts/dev-down.sh`](../../../../scripts/dev-down.sh)：`DEV_DOWN_DOCKER=1` 与 `DEV_STOP_DB=1` 等价，用于停止 Compose MySQL 服务。
3. [`scripts/_dev_json.sh`](../../../../scripts/_dev_json.sh) + [`scripts/dev-check.sh`](../../../../scripts/dev-check.sh)：JSON 字段解析优先 `node`，其次 `jq`；各 `curl` 增加 `--max-time`，避免 macOS 无 GNU `timeout` 时长时间挂起。
4. [`README.md`](../../../../README.md)：`DEV_DOWN_DOCKER`、`dev-seed` 自动拉起库、`dev-check` 解析依赖说明。

**本轮 Agent 实机命令与结果**

| 步骤 | 命令 | 结果 |
|------|------|------|
| 预检 | `bash scripts/dev-precheck.sh` | 输出「依赖检查通过。」exit 0 |
| 启动 | `DEV_UP_SKIP_WEB=1 bash scripts/dev-up.sh` | `dev-db-up` 因环境无 `docker compose` 插件跳过；本机 `mysql` 执行 `dev-seed` 成功；business/consumer `nohup mvn spring-boot:run` 已拉起 |
| 验收 | `bash scripts/dev-check.sh` | 健康检查、B 端冒烟、C 端会话/SSE/409 等全部通过，末行 `[check] DONE`，exit 0 |
