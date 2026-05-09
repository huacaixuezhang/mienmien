package com.mienmien.business.management.infrastructure.support;

import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidShortIdGenerator implements ShortIdGenerator {
    @Override
    public String newId(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return prefix + suffix;
    }
}
