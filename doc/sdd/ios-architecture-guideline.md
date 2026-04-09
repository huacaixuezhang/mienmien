# iOS 架构规范（成熟模式）

适用目录：`ios/`

## 推荐模式

1. 采用 MVVM（或 VIPER/Clean Architecture），禁止 ViewController 直接承载复杂业务逻辑。
2. UI 层仅负责展示与交互，业务流程通过 UseCase/Service 组织。
3. 网络、缓存、设备能力通过协议抽象，便于测试替身注入。

## 强制规则

1. 业务规则不得直接写在 ViewController。
2. 状态管理必须可追踪（单向数据流优先）。
3. 新功能必须补齐单元测试（ViewModel/UseCase）与关键 UI 流程测试。
