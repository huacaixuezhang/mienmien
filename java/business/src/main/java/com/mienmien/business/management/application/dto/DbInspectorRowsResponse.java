package com.mienmien.business.management.application.dto;

import java.util.List;
import java.util.Map;

public record DbInspectorRowsResponse(
        List<String> columnNames, List<Map<String, Object>> rows, long rowCount, int offset, int limit) {
}
