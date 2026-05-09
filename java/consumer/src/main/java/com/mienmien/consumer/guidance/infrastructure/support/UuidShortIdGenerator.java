package com.mienmien.consumer.guidance.infrastructure.support;

import com.mienmien.consumer.guidance.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidShortIdGenerator implements ShortIdGenerator {
    @Override
    public String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
