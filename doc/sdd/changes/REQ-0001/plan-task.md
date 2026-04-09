# REQ-0001 plan-task

## 计划

- 阶段划分：
  - 阶段A：治理规则与法律文本初始化
  - 阶段B：简化 SDD 流程与门禁规则落地
  - 阶段C：平台架构规范与 Cursor 规则/技能落地
  - 阶段D：治理目录、CLA 签署登记与邮件模板完善
- 风险与缓解：
  - 风险：规则冲突导致执行口径不一致；
    - 缓解：统一以 `doc/sdd/constitution.md` 与 `.cursor/rules/*.mdc` 为基线。
  - 风险：托管平台差异导致门禁失效；
    - 缓解：同时维护 `.gitlab-ci.yml` 与 `.github/workflows/*`。
- 验收策略：
  - 文档验收：关键文件存在且互相引用一致；
  - 规则验收：门禁脚本可读且逻辑闭环；
  - 流程验收：REQ 文档无示例占位并可作为后续模板。

## 任务

| 任务ID | 任务内容 | 平台/目录 | 负责人 | 状态 | 变更文件 | 校验结果 | 反馈/备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TASK-001 | BSL/CLA/贡献治理文本初始化 | `doc/` | wangzhen | done | `LICENSE`, `CLA.md`, `CONTRIBUTING.md`, `README.md` | 文档检查通过 | 法律与治理基线已落地 |
| TASK-002 | 简化 SDD 规则与模板落地 | `doc/sdd` | wangzhen | done | `doc/sdd/**`, `.cursor/rules/sdd-lite-and-ddd.mdc` | 规则检索通过 | 单变更目录+双文件流程已生效 |
| TASK-003 | Java DDD 与多端架构规范落地 | `doc/sdd` | wangzhen | done | `doc/sdd/java-ddd-guideline.md`, `doc/sdd/*architecture-guideline.md` | 文档检查通过 | 多端架构规则已明确 |
| TASK-004 | CLA 门禁与 SDD 门禁落地（GitHub + GitLab） | `.github`, `.gitlab-ci.yml` | wangzhen | done | `.github/workflows/cla-gate.yml`, `.github/workflows/sdd-gate.yml`, `.gitlab-ci.yml` | 配置检查通过 | 双平台门禁规则已就绪 |
| TASK-005 | 治理归档目录与邮件模板落地 | `doc/governance` | wangzhen | done | `doc/governance/**` | 文档检查通过 | 收益/分配/费用/签名归档结构已就绪 |

## 执行顺序

1. 后端（`java/business`、`java/consumer`）
2. 客户端（`ios`、`android`、`web`）
3. 联调与回归

## 执行记录规则（强制）

1. 每完成一步任务，必须在上表更新状态与反馈。
2. 每步至少补充：`变更文件`、`校验结果`、`反馈/备注`。
3. 发现阻塞项时，状态改为 `blocked` 并记录处理计划。
