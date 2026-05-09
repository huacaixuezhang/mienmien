# Android C端核心工程说明

当前提供 Android 核心工程骨架，包含：

- 模拟面试入口
- 实时指导入口
- 拍照问答入口

可通过 `./gradlew assembleDebug` 进行构建，并按后端 `java/consumer` API 对接。

## 最小主流程（模拟器）

- `MainActivity`：创建会话 → 发送文本问题 → 订阅 SSE → 一次性降级 → 拍照问答建议。
- 默认基址为 `http://10.0.2.2:8081/api/v1/consumer`（对应宿主机 `localhost:8081`）。

## 实时语音 WebSocket（PCM 单通道）

- 连接地址：`ws://10.0.2.2:8081/ws/consumer/diarization`
- 文本配置消息：
  - `{"type":"config","mode":"unsupervised|enrollment|hybrid","sessionId":"gs_xxx"}`
  - `{"type":"ping"}`
- 二进制消息：`PCM 16kHz / 16bit / mono` 音频帧（建议 50ms，一帧 800 字节）
- 服务端回推：
  - `transcription`：分说话人转写
  - `turn_event`：提问/回答开始结束时机事件
  - `config_ack`、`error`

## turn_event 结构化解析

- `RealtimeEventParser.parse(raw)`：解析 WebSocket 文本事件
- `RealtimeEventParser.turnTypeToLabel(type)`：映射中文标签
  - `INTERVIEWER_QUESTION_START` -> 面试官开始提问
  - `INTERVIEWER_QUESTION_END` -> 面试官结束提问
  - `CANDIDATE_ANSWER_START` -> 候选人开始回答
  - `CANDIDATE_ANSWER_END` -> 候选人结束回答

## 时间线卡片 UI（MainActivity）

- `MainActivity` 已将回合事件展示升级为 `RecyclerView` 时间线卡片，不再只依赖纯日志文本。
- 每条回合节点包含：
  - 事件标签（四类 turn）
  - 事件时间（`HH:mm:ss`）
  - 附带文本（为空时显示“无附带文本”）
  - 交互：点击卡片可展开/收起详情，长按卡片可复制单条事件
- 卡片按事件类型着色，便于快速识别提问/回答的起止节点。
- 内置操作按钮：
  - 筛选：单行 Tab 风格切换 `全部 / 面试官 / 候选人`
  - 操作栏：`清空时间线`、`导出`
    - 导出会同时执行：复制到剪贴板 + 落地 `txt` 文件
    - 导出内容仅包含**当前筛选结果**（可见列表）
    - 导出文件头会附带元信息：`sessionId`、`exportedAt`、`filter`
- 统计栏：显示 `总计 / 面试官 / 候选人 / 当前可见`，便于快速判断筛选效果。
- 稳定性保护：
  - 防止重复点击“开始实时语音”造成重复采集
  - 启停按钮互斥（运行中仅可停止）
  - 时间线事件上限保护（默认 300 条）
  - 日志区行数上限保护（超过阈值自动截断旧日志）
  - 筛选状态持久化（重进页面后保持上次筛选）
  - 列表筛选重建采用 `DiffUtil` 局部刷新，减少全量重绘

导出的 `txt` 文件命名格式：

- `timeline_<sessionId>_<yyyyMMdd_HHmmss>.txt`
- 存储目录：`getExternalFilesDir(null)`（不可用时回退 `filesDir`）
