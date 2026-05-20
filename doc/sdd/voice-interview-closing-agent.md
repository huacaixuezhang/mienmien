# 语音模拟面试 · 终局综合评价 Agent

## 1. 与「单轮 Turn Agent」的区别

| 维度 | 单轮 Turn Agent | 终局综合评价 Agent |
|------|-----------------|-------------------|
| 类 | `VideoInterviewTurnAgentService` | `VideoInterviewRuntimeService.tryBuildClosingAnalysisJson` |
| 触发时机 | 每题提交作答后 | 整场会话进入 `session_closing` / 结束时 |
| 主要输出 | `evaluation`（含 `dimensions[].score`）、`standard_answer`、是否结束、下一题提示 | 本轮结构化 `interviewConclusion` + 总评文本 |
| 题目权重 | **无** `questionWeights`；单题分来自 `dimensions` 均分/聚合 | **有** `questionWeights`（按 `videoTurnId`） |
| 落库 | `mm_video_interview_turn.evaluation_json` | `summary.rounds[i].interviewConclusion`（V3） |

单题卡片上的 **分数** 来自 Turn Agent 的 `evaluation_json`（合并进 `questions[].score`）；**本轮面试综合分数** 由终局 Agent 的 `overallScore` + `questionWeights` 与逐题分按公式合成。

---

## 2. 终局 Agent 是否返回题目权重？

**协议上：必须返回。**

- Prompt 要求输出字段 **`questionWeights`**。
- 解析：`normalizeInterviewConclusionFromLlm` → `normalizeQuestionWeightsArrayFromLlm`。
- 持久化：写入 V3 `rounds[roundIndex].interviewConclusion.questionWeights`；非空时 Web `serializeV3` 也会写出。
- 回显：同步综合分后，各语音题可带 **`questions[].scoreWeight`**（解析后的权重，0–1）。

**实际上可能为空：**

- 未配置 API Key、摘要为空、JSON 解析失败 → 走 `buildOverallEvaluation` 纯文本，结构化结论无权重数组。
- 模型未输出或输出非法 → 空数组；综合分计算时对「参与集」题目 **均分权重** 兜底。

---

## 3. Prompt 结构

**入口：** `completeSessionClosing` → `tryBuildClosingAnalysisJson(row, digest)`  
**模型：** `properties.orchestratorModel()`，user 约 1200 max tokens。

### 3.1 System（拼接 `CLOSING_SYSTEM_ALIGNMENT`）

```
你是面试评估助手。
总评须严格以用户消息中「各轮问答摘要」的事实为依据，不得编造摘要未出现的内容。
语气、考察维度与评价侧重须与「风格与角色综合快照」及「本轮面试记录语境」（若有）一致……

请仅输出一个 JSON 对象，不要使用 Markdown 代码围栏，不要输出任何 JSON 以外的文字。
字段要求：
evaluation：字符串，280 字以内的中文总体评价；
resultAssessment：字符串，必须是「通过」「未通过」「待评估」之一；
overallScore：0 到 100 的整数，表示你在通盘阅读各轮问答后的综合打分（后续会与逐题得分加权合成最终 overallScore）；
questionWeights：数组，每项为对象，必须含 videoTurnId（字符串，须与摘要中各轮「turnId=」后的标识完全一致）与 weight（0 到 1 的小数）；
仅覆盖本场会话摘要中出现的问答轮次；所有项 weight 之和必须等于 1（允许误差 ±0.02，否则服务端会按比例归一）；
comment：字符串，面试评语；
candidatePortrait：字符串，对候选人能力与特质的简要画像；
nextRoundAdvice：字符串，当 nextRoundStatus 为 yes 或 pending 时可填写下轮/后续建议，为 no 时写空字符串；
nextRoundStatus：字符串，必须是 no、yes、pending 之一；
若同时输出 hasNextRound 布尔值，以 nextRoundStatus 为准。
```

完整字符串见 `java/consumer/src/main/java/com/mienmien/consumer/videointerview/application/VideoInterviewRuntimeService.java` 中 `tryBuildClosingAnalysisJson` 的 `system` 变量（约 878–892 行）。

### 3.2 User（动态拼接）

1. **【所选面试官风格 key】**
2. **【风格与角色综合快照】**（尾部截断，`CLOSING_SNAPSHOT_TAIL_CHARS`）
3. **【本轮面试记录语境】**（若有 V3 绑定：`roundTitle`、`category`、`interviewers` 等）
4. **【各轮问答摘要】**（`buildConversationDigest`，尾部最多约 8000 字）

摘要每轮格式：

```text
[第N轮] turnId=<mm_video_interview_turn.turn_id>
问：<question_text>
答：<answer_text>
```

`questionWeights[].videoTurnId` **必须与上述 `turnId=` 一致**（与复盘里 `questions[].videoTurnId` 对应；题目 `id` 常为 `vi_` + turnId，权重键用 **裸 turnId**）。

---

## 4. 模型输出 JSON（顶层对象）

```json
{
  "evaluation": "280字以内总体评价正文",
  "resultAssessment": "通过",
  "overallScore": 75,
  "questionWeights": [
    { "videoTurnId": "vt_abc123", "weight": 0.4 },
    { "videoTurnId": "vt_def456", "weight": 0.6 }
  ],
  "comment": "面试评语",
  "candidatePortrait": "候选人画像",
  "nextRoundAdvice": "",
  "nextRoundStatus": "no",
  "hasNextRound": false
}
```

**兼容：** `questionWeights` 项可用 `turnId` 代替 `videoTurnId`（服务端会兜底读取）。

---

## 5. 服务端归一化与落库

### 5.1 `normalizeInterviewConclusionFromLlm`

写入 `interviewConclusion` 的字段：

| 字段 | 处理 |
|------|------|
| `resultAssessment` | 枚举规范化 |
| `overallScore` | 钳制 0–100 |
| `questionWeights` | `normalizeQuestionWeightsArrayFromLlm`：丢弃无效项；若 `sum(weight)` 与 1 相差 >0.02，则 **按比例缩放** |
| `comment` / `candidatePortrait` / `nextRoundAdvice` / `nextRoundStatus` / `hasNextRound` | 见现有逻辑 |

**注意：** 顶层 **`evaluation` 字符串不写入 `interviewConclusion`**，而是：

- 作为 `meta.videoInterviewMeta.evaluation` / 事件 `evaluation` / WS `ended` 载荷；
- 若 `comment` 为空，会用 `evaluation` 回填 `comment`。

### 5.2 终局处理顺序（同一会话结束）

1. `mergeInterviewRecordSummary`（写入 `interviewConclusion` 等）
2. `mergeVoiceTurnQuestionsIntoBusinessV3Summary`（逐题写入 `questions[]`）
3. `syncRoundOverallScoreFromVoiceQuestions`（按权重重算并覆盖 `overallScore`，写 `scoreWeight`）

Web 侧在载入/保存/合并语音轮次时调用同逻辑的 `syncInterviewConclusionOverallScoreFromQuestions`（`web/src/utils/interviewV3.js`）。

---

## 6. 综合分计算公式

**参与题目：**

- 仅 `source === "video_turn"` 且 `score` ∈ [0, 100]；
- **同轮多场次**时，只取 **最新一场**（`videoSessionOrdinal` 最大，或按 `videoSessionId` 在题目列表中最后首次出现的会话）。

**权重：**

- 优先 `interviewConclusion.questionWeights`（按 `videoTurnId` 匹配）；
- 缺项题目平分剩余权重；仍无法匹配则对参与集 **均分**，再保证参与集权重和为 1。

**公式：**

```text
weightedSum = Σ (题目得分 × 题目权重)
overallScore_final = round( 0.65 × weightedSum + 0.35 × overallScore_agent )
```

其中 **`overallScore_agent`** 为终局 Agent 返回、写入 `interviewConclusion.overallScore` 后、在 **第一次 sync 前** 读取的值。当前实现未单独持久化 `agentOverallScore`，若多次执行 sync，35% 项可能基于已混合分，需注意。

解析后的权重会写到 **`questions[].scoreWeight`**（便于排查，前端复盘 UI 可不展示）。

---

## 7. V3 持久化字段一览

**`rounds[i].interviewConclusion`（节选）：**

```json
{
  "resultAssessment": "未通过",
  "overallScore": 20,
  "comment": "...",
  "candidatePortrait": "...",
  "nextRoundAdvice": "",
  "nextRoundStatus": "no",
  "hasNextRound": false,
  "questionWeights": [
    { "videoTurnId": "vt_xxx", "weight": 0.5 },
    { "videoTurnId": "vt_yyy", "weight": 0.5 }
  ]
}
```

**`rounds[i].questions[j]`（语音题节选）：**

```json
{
  "source": "video_turn",
  "videoTurnId": "vt_xxx",
  "videoSessionId": "...",
  "videoSessionOrdinal": 4,
  "score": 30,
  "scoreWeight": 0.5,
  "label": "第4场｜语音第1题"
}
```

---

## 8. 代码索引

| 说明 | 路径 |
|------|------|
| 终局 Prompt + 调用 | `java/consumer/src/main/java/com/mienmien/consumer/videointerview/application/VideoInterviewRuntimeService.java`：`tryBuildClosingAnalysisJson`、`CLOSING_SYSTEM_ALIGNMENT` |
| 摘要含 turnId | 同文件 `buildConversationDigest` |
| 结论归一化 | `normalizeInterviewConclusionFromLlm`、`normalizeQuestionWeightsArrayFromLlm` |
| 综合分同步（Consumer） | 同文件 `syncRoundOverallScoreFromVoiceQuestions` |
| 综合分同步（Web） | `web/src/utils/interviewV3.js`：`syncInterviewConclusionOverallScoreFromQuestions` |
| 单轮 Agent（无 questionWeights） | `java/consumer/src/main/java/com/mienmien/consumer/videointerview/application/VideoInterviewTurnAgentService.java` |

---

## 9. 建议后续改进

- 持久化 **`agentOverallScore`**，避免多次 `sync` 时 35% 基数被稀释。
- 终局 Prompt 中显式说明 **仅对「最新一场」语音练习的 turn 赋权**（若产品规则不变）。
- 复盘 UI 可选展示 `scoreWeight`，便于核对 Agent 输出。
