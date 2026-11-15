package com.feijimiao.xianyuassistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class FeatureSchemaMigrationTest {
    @Test void upgradingExistingOrderPreservesDataAndAddsFeatureTables() throws Exception {
        var ds = new SingleConnectionDataSource("jdbc:sqlite::memory:",true);
        try {
            var jdbc = new JdbcTemplate(ds);
            var schema = new SqlSchemaParser().parseSchemaFile("sql/schema.sql");
            String previousTable = schema.getTables().get("xianyu_goods_order").getCreateSql()
                    .replaceAll("(?m)^.*delivered_count.*\n", "").replaceAll("(?m)^.*delivery_payload.*\n", "");
            jdbc.execute(previousTable);
            jdbc.update("INSERT INTO xianyu_goods_order (id,xianyu_account_id,xy_goods_id,pnm_id,content) VALUES (1,1,'goods','original','original-card')");
            var listener = new DatabaseInitListener();
            try (var conn = ds.getConnection(); var statement = conn.createStatement()) {
                ReflectionTestUtils.invokeMethod(listener,"checkAndCreateTables",statement,schema);
                ReflectionTestUtils.invokeMethod(listener,"checkAndAddColumns",statement,schema);
                ReflectionTestUtils.invokeMethod(listener,"checkAndCreateIndexes",statement,schema);
            }
            assertEquals("original-card",jdbc.queryForObject("SELECT content FROM xianyu_goods_order WHERE id=1",String.class));
            assertEquals(0,jdbc.queryForObject("SELECT delivered_count FROM xianyu_goods_order WHERE id=1",Integer.class));
            assertNull(jdbc.queryForObject("SELECT delivery_payload FROM xianyu_goods_order WHERE id=1",String.class));
            jdbc.update("INSERT INTO xianyu_buyer_interaction_config (xianyu_account_id) VALUES (1)");
            assertEquals(0,jdbc.queryForObject("SELECT auto_rate + auto_flower + auto_thanks FROM xianyu_buyer_interaction_config",Integer.class));
        } finally { ds.destroy(); }
    }
}
