# MienMien

MienMien 是一个社区驱动项目，独家商业化权利由 `王振` 保留。

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
