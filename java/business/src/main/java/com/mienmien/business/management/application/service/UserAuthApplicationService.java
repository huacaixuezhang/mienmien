package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.UserAuthResponse;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.model.UserAccount;
import com.mienmien.business.management.domain.repository.BusinessSessionRepository;
import com.mienmien.business.management.domain.repository.UserAccountRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserAuthApplicationService {
    private static final long SESSION_TTL_SECONDS = 7L * 24 * 3600;

    private final UserAccountRepository userAccountRepository;
    private final BusinessSessionRepository businessSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final ShortIdGenerator shortIdGenerator;

    public UserAuthApplicationService(
            UserAccountRepository userAccountRepository,
            BusinessSessionRepository businessSessionRepository,
            PasswordEncoder passwordEncoder,
            ShortIdGenerator shortIdGenerator) {
        this.userAccountRepository = userAccountRepository;
        this.businessSessionRepository = businessSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.shortIdGenerator = shortIdGenerator;
    }

    public UserAuthResponse register(String phone, String password) {
        if (userAccountRepository.findByPhone(phone).isPresent()) {
            throw new DomainException("BUS-4091", "该手机号已注册");
        }
        String userId = shortIdGenerator.newId("u");
        String digest = passwordEncoder.encode(password);
        UserAccount account = UserAccount.createWithPasswordDigest(userId, phone, digest);
        userAccountRepository.save(account);
        String token = newSessionToken();
        businessSessionRepository.save(token, userId, Instant.now().plusSeconds(SESSION_TTL_SECONDS));
        return new UserAuthResponse(userId, phone, token);
    }

    public UserAuthResponse login(String phone, String password) {
        UserAccount account = userAccountRepository.findByPhone(phone)
                .orElseThrow(() -> new DomainException("BUS-4012", "手机号或密码错误"));
        if (!passwordMatches(password, account.getPasswordDigest())) {
            throw new DomainException("BUS-4012", "手机号或密码错误");
        }
        String token = newSessionToken();
        businessSessionRepository.save(token, account.getUserId(), Instant.now().plusSeconds(SESSION_TTL_SECONDS));
        return new UserAuthResponse(account.getUserId(), account.getPhone(), token);
    }

    public void logout(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return;
        }
        businessSessionRepository.deleteByToken(sessionToken);
    }

    private boolean passwordMatches(String rawPassword, String storedDigest) {
        if (storedDigest != null && storedDigest.startsWith("$2")) {
            return passwordEncoder.matches(rawPassword, storedDigest);
        }
        return rawPassword != null && rawPassword.equals(storedDigest);
    }

    private static String newSessionToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
