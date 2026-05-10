# MienMien

MienMien 是一个社区驱动项目，独家商业化权利由 `王振` 保留。

## 快速开始（日常三条命令）

- 一键启动并验收：`bash scripts/dev-all-jdk21.sh`
- 查看当前运行状态：`bash scripts/dev-status.sh`
- 一键停止全部服务：`bash scripts/dev-down.sh`
- B 端访问地址：`http://localhost:5173`
- B 端功能与设计详解：`doc/business-web-b-end-guide.md`

## 产品理想

MienMien（面面）是面向广大求职者的面试助手，核心理想如下：

1. 为“风采”平权（Democratize Mien）  
   让每一位求职者，无论性格、学历背景与地域，都能在 AI 辅助下更充分地准备面试，在关键时刻展示最得体、最真实的自己。
2. 让“炉火”传递智慧（Passing the Torch of Wisdom）  
   将资深面试官的隐性经验与行业知识沉淀为可普惠的模拟训练能力，让高质量面试训练不再被地域与资源垄断。
3. 探索“公义”的开源商业模式（Model of Just Openness）  
   通过 BSL + CLA + 收益公开与回馈机制，平衡创造者生存、社区协作与长期可持续发展。
4. 终极理想：照见更好的自己  
   帮助使用者在反复练习中建立底气与自信，在真实面试中呈现更从容、更卓越的状态。

## 授权模型

- 源码许可：Business Source License 1.1（见 `LICENSE`）
- 贡献前置协议：`CLA.md`
- 商业化权利：依据 `LICENSE` 中的独家商业化例外条款，由 `王振` 统一管理

## 贡献要求

所有 PR 在合并前必须通过 CLA 校验。

1. 在 `doc/governance/cla-signatures/register.md` 完成签署登记并提交签署证据。
2. 创建 PR。
3. 确保 `cla-gate` CI 状态为 `success`。

未签署 CLA 的 PR 将被阻断合并。

## 收益公开与贡献者回馈单方声明

以下声明由 `王振` 单方发布，不构成对 `LICENSE` 或 `CLA.md` 约束条款的修改。

1. `王振` 将在 `doc/governance/revenue-disclosures/` 定期公开与 `MienMien` 相关的高层级商业收益信息。
2. `王振` 可基于独立规则设立贡献者回馈机制（如资助、赏金、赞助或服务额度）。
3. 参与条件、发放频率、金额标准与具体规则由 `王振` 单方决定，并可随时调整、暂停或终止。
4. 贡献行为本身不自动产生版税、分红、股权或持续性报酬权利。

相关公开与归档目录见：`doc/governance/`。

## 架构与目录原则

本仓库按平台分目录组织：

- `java/business/`：B 端后端代码（Java）
- `java/consumer/`：C 端后端代码（Java）
- `ios/`：iOS 代码（Swift/Objective-C）
- `android/`：Android 代码（Kotlin/Java）
- `web/`：Web 代码（JavaScript/HTML/CSS）

各平台在本目录内维护自身契约与实现，不跨目录混放源码。
其中 Java 后端采用 DDD（领域驱动设计）进行建模与实现。
iOS、Android、Web 采用业界成熟架构模式，具体见 `doc/sdd/` 对应规范文件。

## SDD 规范

本项目采用 SDD（Spec-Driven Development）规范，文档位于 `doc/sdd/`。
开发新功能前，请在 `doc/sdd/changes/<ChangeID>/` 中先完成 `vision-requirements.md`，确认后再更新 `plan-task.md`。
PR 合并前必须通过 `sdd-gate` 检查，否则无法合并。
当前以 GitLab 为主门禁（`.gitlab-ci.yml`），GitHub 工作流用于镜像仓库一致性校验。

## 本机一键启动（全量开发环境）

前置依赖：

- Java 21
- Maven 3.9+
- Node.js 20+
- **MySQL**：数据库名固定为 **`MienMieApp`**（utf8mb4）
  - **本机 MySQL**：安装后执行 `CREATE DATABASE IF NOT EXISTS MienMieApp ...`（或直接跑种子脚本，内含建库语句）
  - **Docker（可选）**：安装 Docker Desktop（含 **Compose 插件**），由 [`docker-compose.yml`](docker-compose.yml) 启动 `mysql:8.4`，默认端口 `3306`，root 密码默认 `root`（可用环境变量 `DB_PASSWORD` 覆盖）。若仅有 `docker` CLI 而无 `docker compose`，可安装独立的 `docker-compose` v1，或改用本机 MySQL。

连接参数（与 Spring Boot 一致，均可通过环境变量覆盖）：

- `DB_NAME`：默认 `MienMieApp`
- `DB_HOST`：默认 `localhost`
- `DB_PORT`：默认 `3306`
- `DB_USER`：默认 `root`
- `DB_PASSWORD`：默认空字符串（本机 root 无密码时留空；Docker 场景默认与 compose 中 `MYSQL_ROOT_PASSWORD` 一致，建议设为 `root` 或自定义）

启动步骤：

1. 一键全流程（推荐）：
   - `bash scripts/dev-all-jdk21.sh`（自动切换 JDK21 + 启动 + 验收）
   - 若仓库根目录存在 `.env`，会自动加载其中环境变量（如 `DB_*`）
   - 启动前自动清理 `8080/8081/5173` 端口占用，并等待后端健康后再验收
   - 输出各阶段耗时与总耗时，便于定位慢步骤
   - 支持慢步骤告警阈值：`DEV_ALL_WARN_STEP_SECONDS=<秒>`（默认 120）
   - 失败自动诊断（端口/健康/日志片段）；可加 `DEV_ALL_AUTO_DOWN_ON_FAIL=1` 失败后自动执行 `dev-down`
   - 失败会自动归档现场到 `.dev-archives/<时间戳>/`
   - 可用 `DEV_ALL_HEALTH_RETRY=<次数>` 调整健康等待重试次数（默认 25）
   - 可选分段执行：
     - `DEV_ALL_SKIP_WEB=1 bash scripts/dev-all-jdk21.sh`（仅后端）
     - `DEV_ALL_SKIP_BACKEND=1 DEV_ALL_SKIP_CHECK=1 bash scripts/dev-all-jdk21.sh`（仅前端）
     - `DEV_ALL_SKIP_CHECK=1 bash scripts/dev-all-jdk21.sh`（只启动不验收）
2. 分步执行：
   - `bash scripts/dev-up-jdk21.sh`（自动切到 JDK 21 后再启动）
   - 若你已手动设置好 `JAVA_HOME`，也可执行 `bash scripts/dev-up.sh`
   - 两者都会先跑 `dev-precheck`，再 `dev-db-up` 可选拉起 MySQL 容器，再 `dev-seed` 建表
3. 待后端就绪后执行 `bash scripts/dev-check-jdk21.sh`（推荐）
   - 若你已手动设置好 `JAVA_HOME`，也可执行 `bash scripts/dev-check.sh`
4. 打开 `http://localhost:5173`

停止步骤：

- 执行 `bash scripts/dev-down.sh`（停止由 dev-up 拉起的 Java/Web 进程）
- 若需同时停止 MySQL 容器：`DEV_STOP_DB=1 bash scripts/dev-down.sh`
- 查看当前运行状态：`bash scripts/dev-status.sh`
- 查看最近一次一键执行报告：`bash scripts/dev-report.sh`

说明：

- 种子 SQL 在 `scripts/seed-mienmien.sql`（含 `CREATE DATABASE` / `USE MienMieApp`），表名前缀 `mm_` 表示 B/C 域数据同库隔离。
- 若你曾在更早版本初始化过数据库且缺少 `mm_guidance_session.ended_at` 列，可一次性执行 `scripts/migrate-mm-guidance-ended-at.sql`。
- 独立简历表 `mm_resume_document`：已包含在 `scripts/seed-mienmien.sql`；存量库可执行 `bash scripts/apply-migrate-mm-resume-document.sh`（读取根目录 `.env` 中 `DB_*`，SQL 内 `USE MienMieApp`）。
- 简历版本唯一约束：`scripts/migrate-mm-resume-unique.sql`；岗位表：`scripts/migrate-mm-job-position-table.sql`。
- C 端：`mienmien.consumer.stream-degraded=true` 时 `GET /api/v1/consumer/health/stream` 返回 503（`CON-5031`）；语音/拍照问题文本可通过实现 `ClientQuestionEnrichmentPolicy` 接入真实 ASR/视觉（默认 `NoOp`）。
- 若本机 `3306` 已被占用，可修改 `docker-compose.yml` 端口映射或设置 `DB_PORT` 后再启动，并保证 Java 的 `spring.datasource.url` 使用相同端口。
- 当前 iOS/Android 提供核心可用工程骨架与 API 接入点，详见 `ios/README.md`、`android/README.md`。
- 一键脚本会将最近一次执行摘要写入 `.dev-last-report.md`（包含状态、耗时、重试参数）。
