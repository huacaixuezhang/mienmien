# MienMien 项目宪法

## 1. 目的

本宪法用于定义 MienMien 仓库的强制模块边界、语言约束与治理基线。

所有贡献者与维护者均应遵守本文件。

## 2. 仓库模块地图

### `java/business/`（B 端后端）

- 核心功能：
  - B 端管理域服务
  - 后台 API/服务编排
  - B 端数据访问与外部集成
- 允许语言：
  - Java
- 允许内容：
  - Java 源码
  - Java 测试
  - 构建文件与运行配置
- 禁止内容：
  - C 端后端业务实现
  - iOS/Android/Web 应用源码

### `java/consumer/`（C 端后端）

- 核心功能：
  - C 端用户域服务
  - C 端 API/服务编排
  - C 端数据访问与外部集成
- 允许语言：
  - Java
- 允许内容：
  - Java 源码
  - Java 测试
  - 构建文件与运行配置
- 禁止内容：
  - B 端后端业务实现
  - iOS/Android/Web 应用源码

### `java/`（后端根目录）

- 核心功能：
  - 后端分层聚合目录，仅用于组织子模块
- 允许语言：
  - 不直接放业务源码
- 允许内容：
  - `business/` 与 `consumer/` 子目录
- 禁止内容：
  - 后端领域服务
  - API/服务编排
  - 数据访问与外部集成
  - 直接在 `java/` 根目录新增业务源码

### `ios/`（iOS 客户端）

- 核心功能：
  - iOS 客户端模块与功能流程
- 允许语言：
  - Swift / Objective-C
- 允许内容：
  - iOS 源码
  - iOS 测试
  - iOS 工程与构建配置
- 禁止内容：
  - Java 后端实现
  - Android 应用实现
  - Web 页面实现

### `android/`（Android 客户端）

- 核心功能：
  - Android 客户端模块与功能流程
- 允许语言：
  - Kotlin / Java（Android）
- 允许内容：
  - Android 源码
  - Android 测试
  - Android 构建配置
- 禁止内容：
  - 后端 Java 服务实现（非 Android 范畴）
  - iOS 实现
  - Web 页面实现

### `web/`（Web 客户端）

- 核心功能：
  - 浏览器端交互与页面呈现
- 允许语言：
  - JavaScript
  - HTML
  - CSS
- 允许内容：
  - 前端脚本
  - 页面模板
  - 样式与静态资源
- 禁止内容：
  - 移动端原生实现
  - 后端 Java 服务实现

### `doc/`（文档）

- 核心功能：
  - SDD 规格、ADR、贡献流程与治理文档
- 允许内容：
  - Markdown 文档
  - 架构与需求工件
- 禁止内容：
  - 生产运行代码

## 3. 强制工程规则

1. 模块代码与资源必须放在本模块目录内。
2. 禁止通过源码跨目录混放实现跨平台耦合。
3. 跨平台共享决策必须通过 `doc/sdd/*` 与 ADR 同步。
4. 新功能必须按简化 SDD 流程：在 `doc/sdd/changes/<ChangeID>/` 中维护两个文件：`vision-requirements.md` 与 `plan-task.md`。
5. 变更顺序强制为：先确认 `vision-requirements.md`，再更新 `plan-task.md`，未完成前者不得进入后者。
6. 同一 PR 仅允许关联一个 `doc/sdd/changes/<ChangeID>/` 变更目录。
7. `java/business/` 与 `java/consumer/` 的后端实现必须采用 DDD，且 Java 代码规范以《阿里巴巴 Java 开发手册》为基线；具体见 `doc/sdd/java-ddd-guideline.md`。
8. `ios/`、`android/`、`web/` 必须采用业界成熟且可维护的架构模式，分别遵循：
   - `doc/sdd/ios-architecture-guideline.md`
   - `doc/sdd/android-architecture-guideline.md`
   - `doc/sdd/web-architecture-guideline.md`

## 4. 合规规则

1. 许可基线：BSL 1.1（`LICENSE`）。
2. CLA 为合并前强制条件（`CLA.md` + CI 门禁）。
3. 法律/流程条款调整时必须同步更新：
   - `LICENSE` / `CLA.md` / `CONTRIBUTING.md`
   - 关联 SDD/ADR 文档

## 5. 变更控制

修改本宪法必须满足：

1. 提交 `doc/sdd/templates/change-request.md` 变更申请；
2. 经维护者评审通过；
3. 若影响架构边界，必须同步更新 ADR。

## 6. 执行优先级

当本宪法与临时实现习惯冲突时，以本宪法为准。
