package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.UserInterviewerStyleResponse;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import com.mienmien.business.management.domain.model.UserInterviewerStyle;
import com.mienmien.business.management.domain.repository.UserInterviewerStyleRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserInterviewerStyleApplicationService {

    private final UserInterviewerStyleRepository repository;
    private final ShortIdGenerator shortIdGenerator;

    public UserInterviewerStyleApplicationService(
            UserInterviewerStyleRepository repository, ShortIdGenerator shortIdGenerator) {
        this.repository = repository;
        this.shortIdGenerator = shortIdGenerator;
    }

    public List<UserInterviewerStyleResponse> listMine() {
        String userId = BusinessRequestActor.requireUserId();
        return repository.findByOwnerUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(UserInterviewerStyleApplicationService::toResponse)
                .toList();
    }

    @Transactional
    public UserInterviewerStyleResponse create(String title, String promptBody) {
        String userId = BusinessRequestActor.requireUserId();
        String id = shortIdGenerator.newId("st");
        UserInterviewerStyle s = UserInterviewerStyle.createNew(id, userId, title, promptBody);
        repository.insert(s);
        return toResponse(s);
    }

    @Transactional
    public UserInterviewerStyleResponse update(String styleId, String title, String promptBody) {
        String userId = BusinessRequestActor.requireUserId();
        UserInterviewerStyle s =
                repository
                        .findByIdAndOwnerUserId(styleId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("面试官风格不存在"));
        s.updateProfile(title, promptBody);
        repository.update(s);
        return toResponse(s);
    }

    @Transactional
    public void delete(String styleId) {
        String userId = BusinessRequestActor.requireUserId();
        if (repository.findByIdAndOwnerUserId(styleId, userId).isEmpty()) {
            throw new ResourceNotFoundException("面试官风格不存在");
        }
        repository.deleteByIdAndOwnerUserId(styleId, userId);
    }

    private static UserInterviewerStyleResponse toResponse(UserInterviewerStyle s) {
        return new UserInterviewerStyleResponse(
                s.getStyleId(), s.getTitle(), s.getPromptBody(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
