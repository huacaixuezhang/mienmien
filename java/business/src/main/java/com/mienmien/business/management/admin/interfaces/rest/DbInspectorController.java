package com.mienmien.business.management.admin.interfaces.rest;

import com.mienmien.business.management.application.dto.DbInspectorRowsResponse;
import com.mienmien.business.management.application.dto.DbInspectorTablesResponse;
import com.mienmien.business.management.application.service.DbInspectorApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business/admin/db-inspector")
public class DbInspectorController {
    private final DbInspectorApplicationService dbInspectorApplicationService;

    public DbInspectorController(DbInspectorApplicationService dbInspectorApplicationService) {
        this.dbInspectorApplicationService = dbInspectorApplicationService;
    }

    @GetMapping("/tables")
    public DbInspectorTablesResponse tables() {
        return dbInspectorApplicationService.listTables();
    }

    @GetMapping("/tables/{tableName}/rows")
    public DbInspectorRowsResponse rows(
            @PathVariable("tableName") String tableName,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return dbInspectorApplicationService.listTableRows(tableName, offset, limit);
    }
}
