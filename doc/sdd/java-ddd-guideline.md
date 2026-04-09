# Java 后端 DDD 开发规范

本规范适用于：

- `java/business/`
- `java/consumer/`

目标：统一 Java 后端采用 DDD（领域驱动设计）实现，降低业务复杂度与耦合度。

## 0. 代码规范基线（强制）

Java 开发规范以《阿里巴巴 Java 开发手册》为主要参考基线执行（命名、异常、集合、并发、日志、单元测试、数据库访问等）。

若本文件与《阿里巴巴 Java 开发手册》在工程细节层面出现差异，默认以“更严格、更可维护”的规则为准；领域建模边界仍以本 DDD 规范为优先。

## 1. 分层模型（强制）

每个限界上下文（Bounded Context）建议采用如下分层：

- `domain`：领域模型层（实体、值对象、聚合根、领域服务、领域事件、仓储接口）
- `application`：应用服务层（用例编排、事务边界、命令/查询处理）
- `infrastructure`：基础设施层（仓储实现、外部依赖适配、消息/数据库）
- `interfaces`：接口层（HTTP/RPC 控制器、DTO、参数校验）

规则：

1. `domain` 不依赖 `infrastructure` 与 `interfaces`。
2. `application` 只依赖 `domain` 抽象，不直接依赖具体基础设施实现。
3. `infrastructure` 实现 `domain` 中定义的仓储接口与网关接口。

## 2. 领域建模规则（强制）

1. 先识别限界上下文，再定义聚合边界。
2. 聚合内保持强一致，聚合间通过领域事件或应用服务协作。
3. 值对象必须不可变，并通过构造约束保证合法性。
4. 禁止在 Controller 或 DAO 层直接承载业务规则。

## 3. 工程结构建议

示例（以 `order` 上下文）：

- `java/business/order/domain/...`
- `java/business/order/application/...`
- `java/business/order/infrastructure/...`
- `java/business/order/interfaces/...`

`java/consumer/` 同理。

## 4. SDD 必填项（与 DDD 联动）

在每个功能 SDD 中必须补充：

1. 限界上下文清单（Bounded Contexts）
2. 聚合根与实体关系
3. 关键领域不变量（Invariants）
4. 领域事件与一致性策略

## 5. 代码评审基线

若出现以下情况，评审应拒绝：

1. 业务规则散落在接口层或基础设施层；
2. 领域对象退化为仅数据结构（贫血模型）且无合理说明；
3. 跨上下文直接操作他方内部模型，未通过应用服务或事件边界；
4. 关键领域约束仅靠数据库字段限制，缺少领域层表达。
