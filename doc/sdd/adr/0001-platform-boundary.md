# ADR-0001 平台目录边界

## 状态

已采纳

## 背景

MienMien 按平台目录组织：

- `java/business/`：B 端后端 Java 代码
- `java/consumer/`：C 端后端 Java 代码
- `ios/`：iOS 代码
- `android/`：Android 代码
- `web/`：JavaScript/HTML/CSS 代码

跨目录混放源码会导致职责不清、评审路径混乱并提升交付风险。

## 决策

各平台必须在本目录内维护自身实现与契约。跨平台共享决策通过 `doc/sdd/*` 与 ADR 记录，不通过源码跨目录耦合。

## 影响

- 职责边界更清晰
- 评审与协作路径更明确
- 降低平台间意外耦合风险
