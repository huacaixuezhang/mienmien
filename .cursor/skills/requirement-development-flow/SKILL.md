---
name: requirement-development-flow
description: 按简化版SDD执行需求全流程与节点控制。用于新需求分析、文档落地、开发前后检查、以及变更顺序管控。
---

# 需求开发全流程（简化 SDD）

适用场景：用户提出新需求、需求变更、或需要从需求到实现的全过程推进。

## 强制前提

1. 每个需求只允许一个变更目录：`doc/sdd/changes/<ChangeID>/`。
2. 变更目录只允许两个核心文件：
   - `vision-requirements.md`
   - `plan-task.md`
3. 顺序强制：先 `vision-requirements.md`，后 `plan-task.md`。

## 执行流程

### 阶段A：需求澄清与范围冻结

1. 确认 `ChangeID`（如 `REQ-0002`）。
2. 创建/更新 `vision-requirements.md`。
3. 校验必填项：目标、价值、FR/NFR、范围内/范围外、DDD要点（Java适用）。
4. 未确认前，不得进入 `plan-task.md`。

### 阶段B：计划与任务拆解

1. 在 A 阶段确认后，创建/更新 `plan-task.md`。
2. 拆解任务到平台目录（`java/business`、`java/consumer`、`ios`、`android`、`web`）。
3. 标注依赖、风险、验收策略。

### 阶段C：实现与校验

1. 必须逐条对照 `plan-task.md` 的任务清单执行，不得跳步实现。
2. 每完成一条任务，立即输出执行反馈（完成内容、结果、阻塞项、下一步）。
3. 每完成一条任务，必须同步回写 `plan-task.md`：
   - 更新任务状态（todo/in_progress/done）
   - 记录实际变更文件
   - 记录校验结果与问题处理说明
4. 代码实现必须遵循平台架构规范：
   - Java：`doc/sdd/java-ddd-guideline.md` + 《阿里巴巴 Java 开发手册》
   - iOS：`doc/sdd/ios-architecture-guideline.md`
   - Android：`doc/sdd/android-architecture-guideline.md`
   - Web：`doc/sdd/web-architecture-guideline.md`
5. 非代码内容使用中文；代码遵循对应语言规范。
6. 平台边界不可混放源码。

### 阶段D：交付前检查

1. 检查本次变更仅关联一个 `<ChangeID>` 目录。
2. 检查 `vision-requirements.md` 与 `plan-task.md` 已按顺序更新。
3. 检查 CLA/SDD 门禁可通过。

## 失败回退规则

- 如果发现本次变更混入多个需求，立即拆分为多个 PR。
- 如果直接修改了 `plan-task.md` 但未更新 `vision-requirements.md`，先补齐前者后再继续。
