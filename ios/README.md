# iOS C端核心工程说明

当前提供 iOS 核心能力代码骨架，包含：

- 模拟面试流程入口
- 实时指导流程入口
- 拍照问答流程入口

后续在 Xcode 中创建 App Target 后，可直接引入 `MienMieniOSCore` 包并连接 `java/consumer` API。

## Consumer API 封装

- `ConsumerHTTPClient`：与 Android `ConsumerApi` 对齐，提供 `createSession`、`postTextEvent`、`photoQa`、`onceAnswer`，以及在 **iOS 15+** 下的 `streamAnswerLines`（SSE）。
- 真机调试请将 base URL 指向开发机 IP（而非 `localhost`）。

最小异步示例（伪代码）：

1. `createSession` 解析 JSON 得到 `sessionId`
2. `postTextEvent(sessionId, "你好")`
3. `await streamAnswerLines(sessionId)`（Swift Concurrency）

## 实时语音 WebSocket（PCM 单通道）

- 连接地址：`ws://<dev-host>:8081/ws/consumer/diarization`
- 文本配置消息：
  - `{"type":"config","mode":"unsupervised|enrollment|hybrid","sessionId":"gs_xxx"}`
  - `{"type":"ping"}`
- 二进制消息：`PCM 16kHz / 16bit / mono` 音频帧（建议 50ms，一帧 800 字节）
- 事件回推：
  - `transcription`：增量转写（含 speaker/confidence）
  - `turn_event`：`INTERVIEWER_QUESTION_START/END`、`CANDIDATE_ANSWER_START/END`

可直接配合 `RealtimeAudioStreamer` 使用（iOS 端采集麦克风 PCM）：

1. `client.connectRealtimeDiarization(sessionId:onTextMessage:)`
2. `streamer.start { frame in client.sendPcmFrame(frame) }`
3. 结束时调用 `streamer.stop()` 与 `client.disconnectRealtimeDiarization()`

结构化事件解析（推荐）：

1. 使用 `connectRealtimeDiarization(sessionId:mode:onEvent:)`
2. 按 `RealtimeInboundEvent` 分支处理：
   - `.transcription(...)`
   - `.turn(...)`（`TurnEventType.displayLabel` 直接给 UI 标签）
   - `.other(...)`

## 最小 SwiftUI 时间线示例

已提供可直接复用的最小视图与 ViewModel：

- `RealtimeTimelineViewModel`
- `RealtimeTimelineView`

接入方式（示意）：

1. 创建 `let timelineVM = RealtimeTimelineViewModel()`
2. 页面中渲染 `RealtimeTimelineView(viewModel: timelineVM)`
3. WebSocket 事件回调里处理：
   - 当 `case .turn(let turn)` 时，调用 `timelineVM.appendTurn(event: turn)`

这样可将四类回合节点直接展示为时间线卡片，而非纯文本日志。
