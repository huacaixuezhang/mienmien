package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.Resume;

import java.util.List;

public interface ResumeRepository {
    void save(Resume resume);

    List<Resume> findBySpaceIdOrderByVersionDesc(String spaceId);

    boolean existsBySpaceIdAndVersion(String spaceId, int version);

    long countBySpaceId(String spaceId);
}
