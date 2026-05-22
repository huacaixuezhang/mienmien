package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.UserAuthResponse;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.model.UserAccount;
import com.mienmien.business.management.domain.repository.BusinessSessionRepository;
import com.mienmien.business.management.domain.repository.UserAccountRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import com.mienmien.business.management.infrastructure.crypto.RsaAsymmetricCryptoService;
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
    private final RsaAsymmetricCryptoService rsaAsymmetricCryptoService;

    public UserAuthApplicationService(
            UserAccountRepository userAccountRepository,
            BusinessSessionRepository businessSessionRepository,
            PasswordEncoder passwordEncoder,
            ShortIdGenerator shortIdGenerator,
            RsaAsymmetricCryptoService rsaAsymmetricCryptoService) {
        this.userAccountRepository = userAccountRepository;
        this.businessSessionRepository = businessSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.shortIdGenerator = shortIdGenerator;
        this.rsaAsymmetricCryptoService = rsaAsymmetricCryptoService;
    }

    public UserAuthResponse register(String phone, String passwordCipher) {
        if (userAccountRepository.findByPhone(phone).isPresent()) {
            throw new DomainException("BUS-4091", "该手机号已注册");
        }
        String plainPassword = rsaAsymmetricCryptoService.resolveInboundSecret(passwordCipher);
        String userId = shortIdGenerator.newId("u");
        String digest = passwordEncoder.encode(plainPassword);
        UserAccount account = UserAccount.createWithPasswordDigest(userId, phone, digest);
        userAccountRepository.save(account);
        String token = newSessionToken();
        businessSessionRepository.save(token, userId, Instant.now().plusSeconds(SESSION_TTL_SECONDS));
        return new UserAuthResponse(userId, phone, token);
    }

    public UserAuthResponse login(String phone, String passwordCipher) {
        String plainPassword = rsaAsymmetricCryptoService.resolveInboundSecret(passwordCipher);
        UserAccount account = userAccountRepository.findByPhone(phone)
                .orElseThrow(() -> new DomainException("BUS-4012", "手机号或密码错误"));
        if (!passwordMatches(plainPassword, account.getPasswordDigest())) {
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
