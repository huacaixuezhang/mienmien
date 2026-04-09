# MienMien 的 SDD 规范

本目录定义本仓库的 SDD（Spec-Driven Development，规格驱动开发）基线。

## 目标

- 在编码前把需求沉淀为可评审的规格文档。
- 明确 `java/business/`、`java/consumer/`、`ios/`、`android/`、`web/` 的平台边界。
- 在设计阶段完成法律与合规项检查。

## 工作流程（简化版）

1. 每个新需求在 `doc/sdd/changes/` 下只创建一个变更文件夹（如 `REQ-0001`）。
2. 每个变更文件夹固定只使用两个文件：
   - `vision-requirements.md`（愿景与需求）
   - `plan-task.md`（计划与任务）
3. 第一阶段必须先更新 `vision-requirements.md`，经确认后再进入第二阶段。
4. 第二阶段再更新 `plan-task.md`，随后进入实现。
5. 同一个 PR 只能关联一个变更文件夹，避免并行需求混杂。

## 开发就绪定义（DoR）

功能只有在满足以下条件后才可开始编码：

- `vision-requirements.md` 已完成并确认；
- `plan-task.md` 已补充实施计划与任务拆解；
- Java 后端需求已按 DDD 原则补充关键上下文/聚合说明（如适用）。

## 文档索引

- `constitution.md`：模块职责、语言约束与基础规则
- `java-ddd-guideline.md`：Java 后端 DDD 开发规范
- `ios-architecture-guideline.md`：iOS 架构规范（成熟模式）
- `android-architecture-guideline.md`：Android 架构规范（成熟模式）
- `web-architecture-guideline.md`：Web 架构规范（成熟模式）
- `changes/`：按功能ID存放的独立 SDD 文档集
- `templates/change-request.md`：范围变更申请模板
- `templates/vision-requirements-template.md`：愿景与需求模板
- `templates/plan-task-template.md`：计划与任务模板
- `adr/`：架构决策记录
