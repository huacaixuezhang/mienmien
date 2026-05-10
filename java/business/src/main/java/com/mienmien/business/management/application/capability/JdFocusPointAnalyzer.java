package com.mienmien.business.management.application.capability;

/**
 * 从岗位描述（JD）原文中提炼要点，可由百炼等外部模型实现。
 */
public interface JdFocusPointAnalyzer {
    String analyzeFocusPoints(String rawText);
}
