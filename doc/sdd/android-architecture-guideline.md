# Android 架构规范（成熟模式）

适用目录：`android/`

## 推荐模式

1. 采用 Clean Architecture + MVVM（或 MVI），按 `data/domain/presentation` 分层。
2. `domain` 层独立于 Android Framework，承载核心业务规则与 UseCase。
3. 仓储模式统一数据来源（网络、本地、缓存）。

## 强制规则

1. Activity/Fragment 不直接包含复杂业务逻辑。
2. 关键流程使用 UseCase 编排，避免跨层直接调用。
3. 状态流建议使用 `StateFlow`/`LiveData`，并保证可测试。
