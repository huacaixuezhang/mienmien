package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.UserAccount;

import java.util.Optional;

public interface UserAccountRepository {
    void save(UserAccount userAccount);

    Optional<UserAccount> findByPhone(String phone);

    Optional<UserAccount> findByUserId(String userId);

    void updatePasswordDigest(String userId, String passwordDigest);
}
