package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;

/** 简历内单个模块（标题 + 正文），id 由前端生成 UUID 或由服务端补齐。 */
public record ResumeModule(String id, String title, String text) {
    public ResumeModule {
        if (id == null || id.isBlank()) {
            throw new DomainException("BUS-4001", "模块 id 不能为空");
        }
        String t = title == null ? "" : title.trim();
        if (t.isBlank()) {
            t = "未命名模块";
        }
        title = t;
        text = text == null ? "" : text;
    }
}
