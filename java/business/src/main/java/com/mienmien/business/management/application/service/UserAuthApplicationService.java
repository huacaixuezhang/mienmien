package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.UserAuthResponse;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.model.UserAccount;
import com.mienmien.business.management.domain.repository.BusinessSessionRepository;
import com.mienmien.business.management.domain.repository.UserAccountRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class UserAuthApplicationService {
    private static final int SESSION_DAYS = 30;

    private final UserAccountRepository userAccountRepository;
    private final BusinessSessionRepository businessSessionRepository;
    private final ShortIdGenerator shortIdGenerator;
    private final PasswordEncoder passwordEncoder;

    public UserAuthApplicationService(
            UserAccountRepository userAccountRepository,
            BusinessSessionRepository businessSessionRepository,
            ShortIdGenerator shortIdGenerator,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.businessSessionRepository = businessSessionRepository;
        this.shortIdGenerator = shortIdGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAuthResponse register(String phone, String password) {
        String phoneTrim = normalizePhoneInput(phone);
        if (userAccountRepository.findByPhone(phoneTrim).isPresent()) {
            throw new DomainException("BUS-4093", "该手机号已注册");
        }
        String plain = normalizePlainPassword(password);
        String hash = passwordEncoder.encode(plain);
        UserAccount account = UserAccount.createWithPasswordDigest(shortIdGenerator.newId("usr_"), phoneTrim, hash);
        userAccountRepository.save(account);
        String token = newSessionToken(account.getUserId());
        return new UserAuthResponse(account.getUserId(), account.getPhone(), "注册成功", token);
    }

    @Transactional
    public UserAuthResponse login(String phone, String password) {
        String phoneTrim = normalizePhoneInput(phone);
        UserAccount account = userAccountRepository.findByPhone(phoneTrim)
                .orElseThrow(() -> new DomainException("BUS-4012", "手机号或密码错误"));
        String plain = normalizePlainPassword(password);
        String stored = account.getPasswordDigest();
        if (looksLikeBcrypt(stored)) {
            if (!passwordEncoder.matches(plain, stored)) {
                throw new DomainException("BUS-4012", "手机号或密码错误");
            }
        } else {
            if (!plain.equals(stored)) {
                throw new DomainException("BUS-4012", "手机号或密码错误");
            }
            userAccountRepository.updatePasswordDigest(account.getUserId(), passwordEncoder.encode(plain));
        }
        String token = newSessionToken(account.getUserId());
        return new UserAuthResponse(account.getUserId(), account.getPhone(), "登录成功", token);
    }

    @Transactional
    public void logout(String sessionToken) {
        if (sessionToken != null && !sessionToken.isBlank()) {
            businessSessionRepository.deleteByToken(sessionToken.trim());
        }
    }

    private String newSessionToken(String userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expires = Instant.now().plus(SESSION_DAYS, ChronoUnit.DAYS);
        businessSessionRepository.save(token, userId, expires);
        return token;
    }

    private static String normalizePlainPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new DomainException("BUS-4011", "密码不能为空");
        }
        return password.trim();
    }

    private static String normalizePhoneInput(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new DomainException("BUS-4011", "手机号不能为空");
        }
        return phone.trim();
    }

    private static boolean looksLikeBcrypt(String stored) {
        return stored != null && stored.startsWith("$2");
    }
}
