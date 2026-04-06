package org.finos.gitproxy.db.jdbc.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.finos.gitproxy.db.model.AccessRule;
import org.springframework.jdbc.core.RowMapper;

/** Maps an {@code access_rules} result-set row to an {@link AccessRule}. */
public final class AccessRuleRowMapper implements RowMapper<AccessRule> {

    public static final AccessRuleRowMapper INSTANCE = new AccessRuleRowMapper();

    private AccessRuleRowMapper() {}

    @Override
    public AccessRule mapRow(ResultSet rs, int rowNum) throws SQLException {
        return AccessRule.builder()
                .id(rs.getString("id"))
                .provider(rs.getString("provider"))
                .slug(rs.getString("slug"))
                .owner(rs.getString("owner"))
                .name(rs.getString("name"))
                .access(AccessRule.Access.valueOf(rs.getString("access")))
                .operations(AccessRule.Operations.valueOf(rs.getString("operations")))
                .description(rs.getString("description"))
                .enabled(rs.getBoolean("enabled"))
                .ruleOrder(rs.getInt("rule_order"))
                .source(AccessRule.Source.valueOf(rs.getString("source")))
                .build();
    }
}
