package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.ResumeDocument;

import java.util.List;
import java.util.Optional;

public interface ResumeDocumentRepository {
    void save(ResumeDocument document);

    Optional<ResumeDocument> findByResumeId(String resumeId);

    List<ResumeDocument> findBySpaceIdOrderByUpdatedAtDesc(String spaceId);

    List<ResumeDocument> findByUserIdOrderByUpdatedAtDesc(String userId);

    List<String> findSpaceIdsByResumeId(String resumeId);

    void addSpaceLink(String resumeId, String spaceId);

    void deleteSpaceLink(String resumeId, String spaceId);

    int countSpaceLinks(String resumeId);

    void deleteByResumeId(String resumeId);
}
