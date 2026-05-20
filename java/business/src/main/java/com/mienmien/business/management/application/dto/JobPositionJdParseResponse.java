package com.mienmien.business.management.application.dto;

/**
 * 岗位 JD 解析结果：与前端岗位弹窗及 {@code base_range} JSON 扩展字段对齐。
 *
 * @param jobType 取值 {@code fulltime}、{@code campus}、{@code intern}
 */
public record JobPositionJdParseResponse(
        String title,
        String company,
        String location,
        String jobType,
        String salary,
        String focusPoints,
        String description) {
}
