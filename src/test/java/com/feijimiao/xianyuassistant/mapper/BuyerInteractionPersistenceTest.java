package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.feijimiao.xianyuassistant.config.*;
import com.feijimiao.xianyuassistant.entity.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BuyerInteractionPersistenceTest {
    private SingleConnectionDataSource ds;
    private XianyuBuyerInteractionRecordMapper records;
    private XianyuBuyerInteractionConfigMapper configs;
    @BeforeEach void setup() throws Exception {
        ds = new SingleConnectionDataSource("jdbc:sqlite::memory:",true);
        var jdbc = new JdbcTemplate(ds); var schema = new SqlSchemaParser().parseSchemaFile("sql/schema.sql");
        for (var table : schema.getTables().values()) jdbc.execute(table.getCreateSql());
        for (var index : schema.getIndexes().values()) jdbc.execute(index.getCreateSql());
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); config.addMapper(XianyuBuyerInteractionRecordMapper.class); config.addMapper(XianyuBuyerInteractionConfigMapper.class); config.addMapper(XianyuGoodsOrderMapper.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject()); records = session.getMapper(XianyuBuyerInteractionRecordMapper.class); configs = session.getMapper(XianyuBuyerInteractionConfigMapper.class);
    }
    @AfterEach void close() { ds.destroy(); }
    private XianyuBuyerInteractionRecord task(Long account, String action) {
        var t = new XianyuBuyerInteractionRecord(); t.setXianyuAccountId(account); t.setOrderId("order"); t.setAction(action); t.setNextRunAt(1L); t.setUpdatedAt(1L); return t;
    }
    @Test void duplicateEventsRemainDeduplicatedAcrossMapperRecreation() {
        assertEquals(1,records.enqueue(task(1L,"RATE"))); assertEquals(0,records.enqueue(task(1L,"RATE")));
        assertEquals(1,records.enqueue(task(2L,"RATE"))); assertEquals(1,records.enqueue(task(1L,"THANKS")));
        assertEquals(3,records.due(10L).size());
    }
    @Test void onlyOneWorkerCanClaimAndSuccessCannotRetry() {
        records.enqueue(task(1L,"RATE")); var t = records.due(10L).getFirst();
        assertEquals(1,records.claim(t.getId(),10L)); assertEquals(0,records.claim(t.getId(),10L));
        t.setState(1); records.updateById(t); assertTrue(records.due(100L).isEmpty()); assertEquals(0,records.retry(t.getId(),1L,100L));
    }
    @Test void interruptedJobsBecomeVisibleFailuresAndAccountScopedRetry() {
        records.enqueue(task(1L,"RATE")); var t = records.due(10L).getFirst(); records.claim(t.getId(),10L);
        assertEquals(1,records.recoverInterrupted(11L)); assertEquals(-1,records.selectById(t.getId()).getState());
        assertEquals(0,records.retry(t.getId(),2L,20L)); assertEquals(1,records.retry(t.getId(),1L,20L));
    }
    @Test void booleanSettingsRoundTripAndRemainAccountScoped() {
        var c = new XianyuBuyerInteractionConfig(); c.setXianyuAccountId(1L); c.setAutoRate(true); configs.insert(c);
        assertTrue(configs.selectById(1L).getAutoRate()); assertFalse(configs.selectById(1L).getAutoThanks()); assertNull(configs.selectById(2L));
    }
}
