package com.mienmien.business.management.application.support;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 与 Web {@code interviewerRolePresets.js} 内置角色代号对齐，供视频会话快照拼接；无自定义卡片时回落到此处。
 */
public final class BuiltinInterviewerRolePrompts {

    public record RolePreset(String interviewContent, String focusPoints, String evaluationHint) {}

    private static final Map<String, RolePreset> BY_CODE_LOWER = new LinkedHashMap<>();

    static {
        BY_CODE_LOWER.put(
                "hr",
                new RolePreset(
                        """
                                侧重公司制度与流程介绍、候选人动机与稳定性、薪酬福利沟通、入职安排与合规事项；可穿插轻度行为面试与情景题，了解协作风格与价值观是否与公司一致。""",
                        """
                                离职/转岗原因真实性、期望管理与岗位匹配、沟通与倾听、文化契合度、基础诚信与背调风险点；技术深度一般由业务侧补充。""",
                        "记录候选人表述是否前后一致、对加班/出差等政策的接受度及提问质量。"));
        BY_CODE_LOWER.put(
                "peer",
                new RolePreset(
                        """
                                以「一起做事的人」视角考察日常协作：具体项目经历、分工边界、冲突处理、代码/方案评审习惯或业务推进方式；可结合真实工作场景追问细节。（历史简写「P」与此为同一角色，不必重复维护。）""",
                        """
                                专业基本功、问题拆解与落地能力、学习与自驱、沟通效率、在压力下的协作表现；避免越权评价职级或薪酬承诺。""",
                        "关注事实细节是否经得起追问，团队角色是自驱型还是依赖型。"));
        BY_CODE_LOWER.put(
                "ld",
                new RolePreset(
                        """
                                从直接汇报关系出发：目标感、优先级管理、向上沟通、反馈接受度、成长潜力与培养成本；可讨论过往绩效、困难任务如何拆解与复盘。""",
                        """
                                ownership、结果导向、可塑性、与团队节奏是否匹配、是否具备未来带人或扩职责的可能；可适度压力测试但保持尊重。""",
                        "侧重管理成本：需要手把手还是可授权、冲突升级时的表现。"));
        BY_CODE_LOWER.put(
                "+1",
                new RolePreset(
                        """
                                上一级视角：跨团队影响、优先级与资源博弈、对业务方向的理解、在不确定下的决策质量；可结合部门目标考察候选人的格局与抽象能力。""",
                        """
                                系统性思维、沟通影响力、战略到执行的翻译能力、风险意识；与 ld 面形成互补，减少重复考察同一故事。""",
                        "关注候选人是否理解组织约束，而非仅罗列个人英雄事迹。"));
        BY_CODE_LOWER.put(
                "+2",
                new RolePreset(
                        """
                                更高层或隔级视角：行业判断、组织与人才观、长期职业规划与公司阶段是否契合；问题宜精炼，给候选人表达空间。""",
                        """
                                领导力潜质、价值观与组织文化、抗压与韧性、是否具备多线并行经验；避免陷入过细技术实现。""",
                        "记录高度概括与可信度；此轮结论往往对终局录用权重较高。"));
    }

    public static Optional<RolePreset> presetFor(String roleCodeRaw) {
        if (roleCodeRaw == null || roleCodeRaw.isBlank()) {
            return Optional.empty();
        }
        String k = roleCodeRaw.trim().toLowerCase(Locale.ROOT);
        RolePreset hit = BY_CODE_LOWER.get(k);
        if (hit == null && "p".equals(k)) {
            hit = BY_CODE_LOWER.get("peer");
        }
        return Optional.ofNullable(hit);
    }

    private BuiltinInterviewerRolePrompts() {}
}
