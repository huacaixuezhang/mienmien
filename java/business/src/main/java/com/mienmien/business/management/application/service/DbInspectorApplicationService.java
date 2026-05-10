package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.DbInspectorRowsResponse;
import com.mienmien.business.management.application.dto.DbInspectorTablesResponse;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.model.UserAccount;
import com.mienmien.business.management.domain.repository.UserAccountRepository;
import com.mienmien.business.management.infrastructure.persistence.JdbcDatabaseBrowseSupport;
import org.springframework.stereotype.Service;

@Service
public class DbInspectorApplicationService {
    /** 仅此手机号可访问库表看板（与前端侧栏展示条件一致）。 */
    public static final String ALLOWED_PHONE = "19955347072";

    private final UserAccountRepository userAccountRepository;
    private final JdbcDatabaseBrowseSupport databaseBrowseSupport;

    public DbInspectorApplicationService(
            UserAccountRepository userAccountRepository, JdbcDatabaseBrowseSupport databaseBrowseSupport) {
        this.userAccountRepository = userAccountRepository;
        this.databaseBrowseSupport = databaseBrowseSupport;
    }

    public DbInspectorTablesResponse listTables() {
        assertDbInspectorAllowed();
        return new DbInspectorTablesResponse(databaseBrowseSupport.listTablesInCurrentSchema());
    }

    public DbInspectorRowsResponse listTableRows(String tableName, int offset, int limit) {
        assertDbInspectorAllowed();
        JdbcDatabaseBrowseSupport.TablePage page = databaseBrowseSupport.queryTablePage(tableName, offset, limit);
        return new DbInspectorRowsResponse(
                page.columnNames(), page.rows(), page.rowCount(), page.offset(), page.limit());
    }

    private void assertDbInspectorAllowed() {
        String userId = BusinessRequestActor.requireUserId();
        UserAccount account =
                userAccountRepository
                        .findByUserId(userId)
                        .orElseThrow(() -> new DomainException("BUS-4010", "未找到当前用户"));
        if (!ALLOWED_PHONE.equals(account.getPhone())) {
            throw new DomainException("BUS-4033", "无权访问库表看板");
        }
    }
}
