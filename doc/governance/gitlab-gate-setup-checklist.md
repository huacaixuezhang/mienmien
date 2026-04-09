# GitLab 门禁配置与验收清单

本清单用于在 GitLab 主仓完成 CLA/SDD 门禁生效配置与验收。

## 一、仓库文件准备

- [ ] `.gitlab-ci.yml` 已提交
- [ ] `.github/cla-signed-users.txt` 已提交
- [ ] `CLA.md`、`CONTRIBUTING.md`、`LICENSE` 已提交
- [ ] `doc/sdd/changes/` 与双文件模板已提交

## 二、GitLab 项目设置

- [ ] `Settings -> Merge requests -> Pipelines must succeed` 已开启
- [ ] `Settings -> Repository -> Protected branches` 已保护主分支（禁止直推）
- [ ] 仅允许通过 MR 合并主分支

## 三、门禁验收（必须执行）

### 用例A：验证拦截（应失败）

1. 提交一个未修改 `doc/sdd/changes/` 的 MR
2. 预期：`sdd-gate` 失败

### 用例B：验证放行（应通过）

1. 在单一 `doc/sdd/changes/<ChangeID>/` 下更新 `vision-requirements.md` 或 `plan-task.md`
2. 确保作者已在 `.github/cla-signed-users.txt`
3. 预期：`sdd-gate`、`cla-gate` 均通过

## 四、日常维护要求

- CLA 签署先登记 `doc/governance/cla-signatures/register.md`
- 审核通过后再同步 `.github/cla-signed-users.txt`
- 需求变更必须遵循简化 SDD 双文件流程
