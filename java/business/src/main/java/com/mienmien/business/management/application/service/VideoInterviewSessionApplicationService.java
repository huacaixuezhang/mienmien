package com.mienmien.business.management.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mienmien.business.management.application.dto.VideoInterviewSessionCreatedResponse;
import com.mienmien.business.management.application.dto.VideoInterviewerSlotRequest;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.application.support.ApplicationSpaceGuard;
import com.mienmien.business.management.application.support.BuiltinInterviewerRolePrompts;
import com.mienmien.business.management.application.support.BuiltinInterviewerStylePrompts;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import com.mienmien.business.management.domain.model.InterviewRecordWithMeta;
import com.mienmien.business.management.domain.model.JobPosition;
import com.mienmien.business.management.domain.model.ResumeDocument;
import com.mienmien.business.management.domain.model.ResumeModule;
import com.mienmien.business.management.domain.model.UserInterviewerRole;
import com.mienmien.business.management.domain.model.UserInterviewerStyle;
import com.mienmien.business.management.domain.model.VideoInterviewSession;
import com.mienmien.business.management.domain.repository.InterviewRecordRepository;
import com.mienmien.business.management.domain.repository.JobPositionRepository;
import com.mienmien.business.management.domain.repository.ResumeDocumentRepository;
import com.mienmien.business.management.domain.repository.SpaceRepository;
import com.mienmien.business.management.domain.repository.UserInterviewerRoleRepository;
import com.mienmien.business.management.domain.repository.UserInterviewerStyleRepository;
import com.mienmien.business.management.domain.repository.VideoInterviewSessionRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class VideoInterviewSessionApplicationService {

    private final SpaceRepository spaceRepository;
    private final InterviewRecordRepository interviewRecordRepository;
    private final JobPositionRepository jobPositionRepository;
    private final ResumeDocumentRepository resumeDocumentRepository;
    private final UserInterviewerStyleRepository userInterviewerStyleRepository;
    private final UserInterviewerRoleRepository userInterviewerRoleRepository;
    private final VideoInterviewSessionRepository videoInterviewSessionRepository;
    private final ShortIdGenerator shortIdGenerator;
    private final ObjectMapper objectMapper;
    private final String consumerPublicBaseUrl;
    private final String orchestratorModel;
    private final String asrModel;

    public VideoInterviewSessionApplicationService(
            SpaceRepository spaceRepository,
            InterviewRecordRepository interviewRecordRepository,
            JobPositionRepository jobPositionRepository,
            ResumeDocumentRepository resumeDocumentRepository,
            UserInterviewerStyleRepository userInterviewerStyleRepository,
            UserInterviewerRoleRepository userInterviewerRoleRepository,
            VideoInterviewSessionRepository videoInterviewSessionRepository,
            ShortIdGenerator shortIdGenerator,
            ObjectMapper objectMapper,
            @Value("${mienmien.consumer.public-base-url:http://localhost:8081}") String consumerPublicBaseUrl,
            @Value("${mienmien.video-interview.orchestrator-model:qwen-turbo}") String orchestratorModel,
            @Value("${mienmien.video-interview.asr-model:client-web-speech}") String asrModel) {
        this.spaceRepository = spaceRepository;
        this.interviewRecordRepository = interviewRecordRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.resumeDocumentRepository = resumeDocumentRepository;
        this.userInterviewerStyleRepository = userInterviewerStyleRepository;
        this.userInterviewerRoleRepository = userInterviewerRoleRepository;
        this.videoInterviewSessionRepository = videoInterviewSessionRepository;
        this.shortIdGenerator = shortIdGenerator;
        this.objectMapper = objectMapper;
        this.consumerPublicBaseUrl = trimTrailingSlash(consumerPublicBaseUrl);
        this.orchestratorModel = orchestratorModel;
        this.asrModel = asrModel;
    }

    @Transactional
    public VideoInterviewSessionCreatedResponse create(
            String recordId,
            String spaceId,
            int roundIndex,
            String interviewerStyleKey,
            List<VideoInterviewerSlotRequest> interviewers) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        String userId = BusinessRequestActor.requireUserId();
        InterviewRecordWithMeta meta =
                interviewRecordRepository
                        .findByRecordIdAndSpaceId(recordId, spaceId)
                        .orElseThrow(() -> new ResourceNotFoundException("面试记录不存在"));
        String positionId = meta.positionId();
        ObjectNode jobJson = objectMapper.createObjectNode();
        if (positionId != null && !positionId.isBlank()) {
            Optional<JobPosition> jp = jobPositionRepository.findById(positionId);
            if (jp.isPresent()) {
                JobPosition j = jp.get();
                jobJson.put("positionId", j.getPositionId());
                jobJson.put("title", j.getTitle());
                jobJson.put("company", j.getCompany());
                jobJson.put("location", j.getLocation());
                jobJson.put("baseRange", j.getBaseRange());
            }
        }
        ArrayNode resumesJson = objectMapper.createArrayNode();
        List<ResumeDocument> resumes = resumeDocumentRepository.findBySpaceIdOrderByUpdatedAtDesc(spaceId);
        for (ResumeDocument d : resumes) {
            ObjectNode one = objectMapper.createObjectNode();
            one.put("resumeId", d.getResumeId());
            one.put("name", d.getName());
            ArrayNode mods = objectMapper.createArrayNode();
            for (ResumeModule m : d.getModulesView()) {
                ObjectNode mo = objectMapper.createObjectNode();
                mo.put("id", m.id());
                mo.put("title", m.title());
                mo.put("text", m.text());
                mods.add(mo);
            }
            one.set("modules", mods);
            resumesJson.add(one);
        }
        String styleKey = interviewerStyleKey == null ? "" : interviewerStyleKey.trim();
        String stylePrompt = resolveStylePrompt(styleKey, userId);
        List<VideoInterviewerSlotRequest> slots = interviewers == null ? List.of() : interviewers;
        String snapshot = mergeStyleAndRoleSnapshots(stylePrompt, buildInterviewerRoleAppendix(slots, userId));
        String sessionId = shortIdGenerator.newId("vs");
        VideoInterviewSession session =
                VideoInterviewSession.createNew(
                        sessionId,
                        userId,
                        spaceId,
                        recordId,
                        positionId,
                        roundIndex,
                        styleKey,
                        resumesJson.toString(),
                        jobJson.toString(),
                        snapshot,
                        orchestratorModel,
                        asrModel);
        videoInterviewSessionRepository.insert(session);
        String wsPath = "/ws/consumer/video-interview/" + sessionId;
        return new VideoInterviewSessionCreatedResponse(
                sessionId,
                recordId,
                spaceId,
                session.getStyleKey(),
                consumerPublicBaseUrl,
                wsPath,
                session.getStatus(),
                orchestratorModel,
                asrModel,
                session.getStartedAt());
    }

    private String resolveStylePrompt(String styleKey, String userId) {
        if (BuiltinInterviewerStylePrompts.isBuiltinKey(styleKey)) {
            return BuiltinInterviewerStylePrompts.builtinPrompt(styleKey);
        }
        if (!styleKey.isBlank()) {
            Optional<UserInterviewerStyle> custom =
                    userInterviewerStyleRepository.findByIdAndOwnerUserId(styleKey, userId);
            if (custom.isPresent()) {
                return custom.get().getPromptBody();
            }
        }
        return BuiltinInterviewerStylePrompts.defaultPrompt();
    }

    private String buildInterviewerRoleAppendix(List<VideoInterviewerSlotRequest> slots, String userId) {
        if (slots == null || slots.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (VideoInterviewerSlotRequest slot : slots) {
            String roleCode = slot.role() == null ? "" : slot.role().trim();
            if (roleCode.isEmpty()) {
                continue;
            }
            used++;
            String displayName = slot.name() == null ? "" : slot.name().trim();
            String whoLabel = displayName.isEmpty() ? "（姓名未填）" : displayName;
            sb.append("### 面试官 ")
                    .append(used)
                    .append("：")
                    .append(whoLabel)
                    .append(" · 角色代号 ")
                    .append(roleCode)
                    .append("\n\n");
            Optional<UserInterviewerRole> custom =
                    userInterviewerRoleRepository.findByOwnerUserIdAndRoleCodeIgnoreCase(userId, roleCode);
            if (custom.isPresent()) {
                UserInterviewerRole r = custom.get();
                sb.append("**面试内容**\n").append(r.getInterviewContent()).append("\n\n");
                sb.append("**侧重点**\n").append(r.getFocusPoints()).append("\n\n");
                String hint = r.getEvaluationHint();
                if (hint != null && !hint.isBlank()) {
                    sb.append("**评估与记录建议**\n").append(hint.trim()).append("\n\n");
                }
            } else {
                Optional<BuiltinInterviewerRolePrompts.RolePreset> preset =
                        BuiltinInterviewerRolePrompts.presetFor(roleCode);
                if (preset.isPresent()) {
                    BuiltinInterviewerRolePrompts.RolePreset p = preset.get();
                    sb.append("**面试内容**\n").append(p.interviewContent()).append("\n\n");
                    sb.append("**侧重点**\n").append(p.focusPoints()).append("\n\n");
                    sb.append("**评估与记录建议**\n").append(p.evaluationHint()).append("\n\n");
                } else {
                    sb.append("**说明**\n")
                            .append("未匹配到内置或自定义角色卡片；请按代号「")
                            .append(roleCode)
                            .append("」的常见职场分工组织提问，并结合简历与岗位追问。\n\n");
                }
            }
        }
        if (used == 0) {
            return "";
        }
        return "\n\n【本轮面试官角色与考察说明】\n" + sb;
    }

    private static String mergeStyleAndRoleSnapshots(String stylePrompt, String roleAppendix) {
        String base = stylePrompt == null ? "" : stylePrompt;
        String extra = roleAppendix == null ? "" : roleAppendix;
        String merged = base + extra;
        int max = 24000;
        if (merged.length() <= max) {
            return merged;
        }
        return merged.substring(0, max)
                + "\n\n（…综合快照过长已截断；若提问偏离预期，请缩短自定义风格或角色说明。）";
    }

    private static String trimTrailingSlash(String u) {
        if (u == null || u.isBlank()) {
            return "http://localhost:8081";
        }
        String t = u.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}
