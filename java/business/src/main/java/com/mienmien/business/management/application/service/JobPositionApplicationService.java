package com.mienmien.business.management.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mienmien.business.management.application.dto.JobPositionJdParseResponse;
import com.mienmien.business.management.application.dto.JobPositionResponse;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.application.support.ApplicationSpaceGuard;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import com.mienmien.business.management.domain.model.AiModelConfig;
import com.mienmien.business.management.domain.model.JobPosition;
import com.mienmien.business.management.domain.repository.AiModelConfigRepository;
import com.mienmien.business.management.domain.repository.JobPositionRepository;
import com.mienmien.business.management.domain.repository.SpaceRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import com.mienmien.business.management.infrastructure.capability.BailianLlmClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 岗位持久化至 MySQL（{@code mm_job_position} + {@code mm_job_position_space}）；解析 JD 仍调大模型。
 */
@Service
public class JobPositionApplicationService {
    private static final long MAX_JD_IMAGE_BYTES = 8L * 1024 * 1024;

    private static final Pattern JSON_FENCE =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final SpaceRepository spaceRepository;
    private final ShortIdGenerator shortIdGenerator;
    private final AiModelConfigRepository aiModelConfigRepository;
    private final BailianLlmClient bailianLlmClient;
    private final ObjectMapper objectMapper;
    private final JobPositionRepository jobPositionRepository;

    public JobPositionApplicationService(
            SpaceRepository spaceRepository,
            ShortIdGenerator shortIdGenerator,
            AiModelConfigRepository aiModelConfigRepository,
            BailianLlmClient bailianLlmClient,
            ObjectMapper objectMapper,
            JobPositionRepository jobPositionRepository) {
        this.spaceRepository = spaceRepository;
        this.shortIdGenerator = shortIdGenerator;
        this.aiModelConfigRepository = aiModelConfigRepository;
        this.bailianLlmClient = bailianLlmClient;
        this.objectMapper = objectMapper;
        this.jobPositionRepository = jobPositionRepository;
    }

    @Transactional
    public JobPositionResponse create(String spaceId, String title, String company, String location, String baseRange) {
        if (spaceId != null && !spaceId.isBlank()) {
            ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        }
        String userId = BusinessRequestActor.requireUserId();
        String positionId = shortIdGenerator.newId("p");
        JobPosition jp = JobPosition.createNew(positionId, userId, title, company, location, baseRange);
        List<String> spaces = new ArrayList<>();
        if (spaceId != null && !spaceId.isBlank()) {
            spaces.add(spaceId);
        }
        jobPositionRepository.insert(jp, spaces);
        return toResponse(jp, jobPositionRepository.findSpaceIdsByPositionId(positionId));
    }

    public List<JobPositionResponse> listBySpace(String spaceId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        String actor = BusinessRequestActor.requireUserId();
        return jobPositionRepository.findByLinkedSpaceIdOrderByUpdatedAtDesc(spaceId).stream()
                .filter(j -> actor.equals(j.getUserId()))
                .map(j -> toResponse(j, jobPositionRepository.findSpaceIdsByPositionId(j.getPositionId())))
                .toList();
    }

    public List<JobPositionResponse> listMine() {
        String userId = BusinessRequestActor.requireUserId();
        return jobPositionRepository.findByOwnerUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(j -> toResponse(j, jobPositionRepository.findSpaceIdsByPositionId(j.getPositionId())))
                .toList();
    }

    public JobPositionResponse getForOwner(String positionId) {
        JobPosition jp =
                jobPositionRepository
                        .findById(positionId)
                        .orElseThrow(() -> new ResourceNotFoundException("岗位不存在"));
        assertActorOwns(jp);
        return toResponse(jp, jobPositionRepository.findSpaceIdsByPositionId(positionId));
    }

    @Transactional
    public JobPositionResponse update(String positionId, String title, String company, String location, String baseRange) {
        JobPosition jp =
                jobPositionRepository
                        .findById(positionId)
                        .orElseThrow(() -> new ResourceNotFoundException("岗位不存在"));
        assertActorOwns(jp);
        jp.updateProfile(title, company, location, baseRange);
        jobPositionRepository.update(jp);
        return toResponse(jp, jobPositionRepository.findSpaceIdsByPositionId(positionId));
    }

    @Transactional
    public JobPositionResponse close(String positionId) {
        JobPosition jp =
                jobPositionRepository
                        .findById(positionId)
                        .orElseThrow(() -> new ResourceNotFoundException("岗位不存在"));
        assertActorOwns(jp);
        jp.markClosed();
        jobPositionRepository.update(jp);
        return toResponse(jp, jobPositionRepository.findSpaceIdsByPositionId(positionId));
    }

    @Transactional
    public void deleteEntire(String positionId) {
        JobPosition jp =
                jobPositionRepository
                        .findById(positionId)
                        .orElseThrow(() -> new ResourceNotFoundException("岗位不存在"));
        assertActorOwns(jp);
        jobPositionRepository.deleteById(positionId);
    }

    @Transactional
    public JobPositionResponse linkToSpace(String spaceId, String positionId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        JobPosition jp =
                jobPositionRepository
                        .findById(positionId)
                        .orElseThrow(() -> new ResourceNotFoundException("岗位不存在"));
        assertActorOwns(jp);
        jobPositionRepository.addSpaceLink(positionId, spaceId);
        return toResponse(jp, jobPositionRepository.findSpaceIdsByPositionId(positionId));
    }

    @Transactional
    public JobPositionResponse unlinkFromSpace(String spaceId, String positionId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        JobPosition jp =
                jobPositionRepository
                        .findById(positionId)
                        .orElseThrow(() -> new ResourceNotFoundException("岗位不存在"));
        assertActorOwns(jp);
        jobPositionRepository.removeSpaceLink(positionId, spaceId);
        return toResponse(jp, jobPositionRepository.findSpaceIdsByPositionId(positionId));
    }

    /**
     * 使用当前用户在系统设置中保存的模型，将 JD 全文解析为岗位字段；模型须仅输出 JSON 对象，服务端解析后返回结构化 DTO。
     */
    public JobPositionJdParseResponse parseJdToStructuredFields(String rawJd) {
        String userId = BusinessRequestActor.requireUserId();
        String jd = rawJd == null ? "" : rawJd.trim();
        if (jd.isBlank()) {
            throw new DomainException("BUS-4001", "JD 原文不能为空");
        }
        AiModelConfig cfg =
                aiModelConfigRepository
                        .findByOwnerUserId(userId)
                        .orElseThrow(
                                () ->
                                        new DomainException(
                                                "BUS-4001", "请先在系统设置中保存模型连接配置"));
        if (cfg.getApiKey().isBlank() || cfg.getBaseUrl().isBlank()) {
            throw new DomainException("BUS-4001", "请先在系统设置中配置 Base URL 与 API Key");
        }
        if (cfg.getModelName().isBlank()) {
            throw new DomainException("BUS-4001", "请先在系统设置中配置模型名称");
        }
        String prompt = buildJdParseUserPrompt(jd);
        String answer =
                bailianLlmClient.completeUserPrompt(
                        cfg.getBaseUrl(), cfg.getApiKey(), cfg.getModelName(), prompt, 4096);
        JsonNode root = extractTopLevelJsonObject(answer);
        return new JobPositionJdParseResponse(
                jsonText(root, "title"),
                jsonText(root, "company"),
                jsonText(root, "location"),
                normalizeJobType(jsonText(root, "jobType")),
                jsonText(root, "salary"),
                jsonText(root, "focusPoints"),
                jsonText(root, "description"));
    }

    /**
     * 从岗位 JD 截图解析结构化字段；需使用 OpenAI 兼容 chat/completions 与<strong>支持图片输入的多模态模型</strong>（名称以百炼控制台为准，如
     * qwen3.5-plus、qwen-vl-plus 等）。
     */
    public JobPositionJdParseResponse parseJdImageToStructuredFields(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new DomainException("BUS-4001", "请上传图片文件");
        }
        long size = imageFile.getSize();
        if (size <= 0 || size > MAX_JD_IMAGE_BYTES) {
            throw new DomainException("BUS-4001", "图片须小于 8MB 且非空");
        }
        String mime = normalizeImageMimeType(imageFile.getContentType());
        if (mime == null) {
            throw new DomainException("BUS-4001", "仅支持 JPEG、PNG、WebP、GIF 图片");
        }
        byte[] bytes;
        try {
            bytes = imageFile.getBytes();
        } catch (Exception e) {
            throw new DomainException("BUS-4001", "读取上传图片失败: " + e.getMessage());
        }
        String userId = BusinessRequestActor.requireUserId();
        AiModelConfig cfg =
                aiModelConfigRepository
                        .findByOwnerUserId(userId)
                        .orElseThrow(
                                () ->
                                        new DomainException(
                                                "BUS-4001", "请先在系统设置中保存模型连接配置"));
        if (cfg.getApiKey().isBlank() || cfg.getBaseUrl().isBlank()) {
            throw new DomainException("BUS-4001", "请先在系统设置中配置 Base URL 与 API Key");
        }
        if (cfg.getModelName().isBlank()) {
            throw new DomainException("BUS-4001", "请先在系统设置中配置模型名称");
        }
        String prompt = buildJdParseUserPromptForImage();
        String b64 = Base64.getEncoder().encodeToString(bytes);
        String answer =
                bailianLlmClient.completeUserPromptWithImageDataUrl(
                        cfg.getBaseUrl(), cfg.getApiKey(), cfg.getModelName(), prompt, mime, b64, 4096);
        JsonNode root = extractTopLevelJsonObject(answer);
        return new JobPositionJdParseResponse(
                jsonText(root, "title"),
                jsonText(root, "company"),
                jsonText(root, "location"),
                normalizeJobType(jsonText(root, "jobType")),
                jsonText(root, "salary"),
                jsonText(root, "focusPoints"),
                jsonText(root, "description"));
    }

    private static String normalizeImageMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String t = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(t)) {
            return "image/jpeg";
        }
        if ("image/jpeg".equals(t)
                || "image/png".equals(t)
                || "image/webp".equals(t)
                || "image/gif".equals(t)) {
            return t;
        }
        return null;
    }

    private static String buildJdParseUserPromptForImage() {
        return String.join(
                "\n",
                "你是招聘数据抽取助手。用户上传了一张「岗位招聘信息」截图（可能含文字、表格、Logo）。请识别图中与岗位相关的全部可读文字，",
                "仅输出一个 JSON 对象（不要 Markdown 代码围栏、不要解释）。",
                "JSON 的键必须严格为以下 7 个，且均为字符串类型：",
                "title, company, location, jobType, salary, focusPoints, description",
                "含义：岗位名称、所属公司、工作地点、岗位类型、期望薪资（含范围/备注均可）、考点关键词（逗号分隔的一条字符串）、岗位描述（纯文本摘要，2～8 句）。",
                "jobType 取值必须是以下英文小写之一：fulltime（全职）、campus（校招）、intern（实习）；若图中未写清则填 fulltime。",
                "若某字段在图中找不到，用空字符串 \"\" 作为值。");
    }

    private static String buildJdParseUserPrompt(String jd) {
        return String.join(
                "\n",
                "你是招聘数据抽取助手。请阅读下面的「岗位招聘信息」全文，仅输出一个 JSON 对象（不要 Markdown 代码围栏、不要解释）。",
                "JSON 的键必须严格为以下 7 个，且均为字符串类型：",
                "title, company, location, jobType, salary, focusPoints, description",
                "含义：岗位名称、所属公司、工作地点、岗位类型、期望薪资（含范围/备注均可）、考点关键词（逗号分隔的一条字符串）、岗位描述（纯文本摘要，2～8 句）。",
                "jobType 取值必须是以下英文小写之一：fulltime（全职）、campus（校招）、intern（实习）；若 JD 未写清则填 fulltime。",
                "若某字段在 JD 中找不到，用空字符串 \"\" 作为值。",
                "以下为 JD 全文：",
                "---",
                jd,
                "---");
    }

    private JsonNode extractTopLevelJsonObject(String assistantText) {
        String raw = assistantText == null ? "" : assistantText.trim();
        if (raw.isEmpty()) {
            throw new DomainException("BUS-5020", "模型未返回内容");
        }
        try {
            JsonNode n = objectMapper.readTree(raw);
            if (n.isObject()) {
                return n;
            }
        } catch (Exception ignored) {
            // fall through
        }
        var m = JSON_FENCE.matcher(raw);
        if (m.find()) {
            try {
                JsonNode n = objectMapper.readTree(m.group(1).trim());
                if (n.isObject()) {
                    return n;
                }
            } catch (Exception e) {
                throw new DomainException("BUS-5020", "模型返回的 JSON 围栏内容无法解析: " + e.getMessage());
            }
        }
        int i = raw.indexOf('{');
        int j = raw.lastIndexOf('}');
        if (i >= 0 && j > i) {
            try {
                JsonNode n = objectMapper.readTree(raw.substring(i, j + 1));
                if (n.isObject()) {
                    return n;
                }
            } catch (Exception e) {
                throw new DomainException("BUS-5020", "模型返回正文中的 JSON 无法解析: " + e.getMessage());
            }
        }
        throw new DomainException("BUS-5020", "未能从模型输出中提取 JSON 对象");
    }

    private static String jsonText(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return "";
        }
        JsonNode n = root.get(field);
        if (n.isTextual()) {
            return n.asText("").trim();
        }
        if (n.isNumber()) {
            return n.asText().trim();
        }
        if (n.isBoolean()) {
            return n.asBoolean() ? "true" : "false";
        }
        return n.toString().trim();
    }

    private static String normalizeJobType(String v) {
        if (v == null) {
            return "fulltime";
        }
        String t = v.trim();
        String s = t.toLowerCase();
        if ("campus".equals(s) || "校招".equals(t)) {
            return "campus";
        }
        if ("intern".equals(s) || "实习".equals(t)) {
            return "intern";
        }
        if ("fulltime".equals(s) || "全职".equals(t)) {
            return "fulltime";
        }
        return "fulltime";
    }

    private void assertActorOwns(JobPosition jp) {
        String actor = BusinessRequestActor.requireUserId();
        if (!jp.getUserId().equals(actor)) {
            throw new DomainException("BUS-4033", "无权操作该岗位");
        }
    }

    private static JobPositionResponse toResponse(JobPosition j, List<String> spaceIds) {
        String first = spaceIds != null && !spaceIds.isEmpty() ? spaceIds.get(0) : "";
        return new JobPositionResponse(
                j.getPositionId(),
                first,
                spaceIds == null ? List.of() : List.copyOf(spaceIds),
                j.getTitle(),
                j.getCompany(),
                j.getLocation(),
                j.getBaseRange(),
                j.getStatus(),
                j.getCreatedAt(),
                j.getUpdatedAt());
    }
}
