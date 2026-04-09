# SDD 变更文档目录（简化版）

本目录用于存放每个新需求对应的单一变更文件夹。

## 目录规范

- 按变更ID建目录：`doc/sdd/changes/<ChangeID>/`
- 每个变更目录仅包含两个核心文件：
  - `vision-requirements.md`（愿景与需求）
  - `plan-task.md`（计划与任务）

## 运行顺序

1. 先维护 `vision-requirements.md` 并完成确认；
2. 再维护 `plan-task.md`；
3. 进入开发实现。

## 示例

- `doc/sdd/changes/REQ-0001/vision-requirements.md`
- `doc/sdd/changes/REQ-0001/plan-task.md`
