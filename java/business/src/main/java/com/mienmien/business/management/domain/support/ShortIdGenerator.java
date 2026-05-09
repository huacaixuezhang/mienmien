package com.mienmien.business.management.domain.support;

/**
 * 领域侧 ID 生成抽象，由基础设施实现，避免应用层散落 UUID 规则。
 */
public interface ShortIdGenerator {
    String newId(String prefix);
}
