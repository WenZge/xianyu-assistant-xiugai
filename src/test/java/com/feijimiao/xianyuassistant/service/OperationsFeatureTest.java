package com.feijimiao.xianyuassistant.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.feijimiao.xianyuassistant.config.SqlSchemaParser;
import com.feijimiao.xianyuassistant.entity.*;
import com.feijimiao.xianyuassistant.mapper.*;
import com.feijimiao.xianyuassistant.service.delivery.DeliveryProtectionService;
import com.feijimiao.xianyuassistant.service.impl.*;
import com.feijimiao.xianyuassistant.controller.dto.KamiConfigReqDTO;
import com.feijimiao.xianyuassistant.exception.BusinessException;
import com.feijimiao.xianyuassistant.utils.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OperationsFeatureTest {
    private SingleConnectionDataSource ds;
    private JdbcTemplate db;
    private SqlSessionTemplate session;
    private AutomationSettingsService settings;
    private DeliveryProtectionService guard;
    private final BuyerInteractionApi seller=mock(BuyerInteractionApi.class);
    private final AccountService accounts=mock(AccountService.class);
    private final XianyuApiCallUtils api=mock(XianyuApiCallUtils.class);
    private AutoPolishService polish;
    @BeforeEach void setup() throws Exception {
        ds=new SingleConnectionDataSource("jdbc:sqlite::memory:",true); db=new JdbcTemplate(ds);
        var schema=new SqlSchemaParser().parseSchemaFile("sql/schema.sql");
        for(var table:schema.getTables().values())db.execute(table.getCreateSql());
        for(var index:schema.getIndexes().values())db.execute(index.getCreateSql());
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.getTypeHandlerRegistry().register(java.time.LocalDateTime.class, com.feijimiao.xianyuassistant.config.CustomLocalDateTimeTypeHandler.class);
        config.addMapper(XianyuAutomationConfigMapper.class);config.addMapper(XianyuAccountMapper.class);
        config.addMapper(XianyuKamiConfigMapper.class);config.addMapper(XianyuKamiItemMapper.class);config.addMapper(XianyuGoodsSkuMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
        session=new SqlSessionTemplate(factory.getObject());
        db.update("INSERT INTO xianyu_account(id,unb,status) VALUES(1,'seller-one',1),(2,'seller-two',1)");
        settings=new AutomationSettingsService(session.getMapper(XianyuAutomationConfigMapper.class),session.getMapper(XianyuAccountMapper.class),db);
        guard=new DeliveryProtectionService(settings,db,seller,accounts,api);
        polish=new AutoPolishService(settings,accounts,api,db);
        when(accounts.getCookieByAccountId(anyLong())).thenReturn("dummy-cookie");
    }
    @AfterEach void close(){polish.close();ds.destroy();}
    private void order(long account,String id,String goods){db.update("INSERT INTO xianyu_goods_order(xianyu_account_id,order_id,xy_goods_id,buyer_user_id,pnm_id,state) VALUES(?,?,?,?,?,1)",account,id,goods,"123",id);}
    private void status(long account,String order,String status){when(seller.call(account,"mtop.taobao.idle.trade.merchant.full.info",Map.of("tid",order))).thenReturn(Map.of("module",Map.of("merchantCommonData",Map.of("orderStatus",status))));}
    private void goods(){db.update("INSERT INTO xianyu_goods(id,xianyu_account_id,xy_good_id,status,title) VALUES(1,1,'goods',0,'Sample')");}
    @Test void defaultsAndRuntimeFieldsAreNotOverwrittenBySettingsSave(){
        var c=settings.get(1L);assertFalse(c.isPolishEnabled());assertEquals(24,c.getPolishIntervalHours());assertFalse(c.isBlockLowCredit());
        settings.save(c);db.update("UPDATE xianyu_automation_config SET running_until=123,last_polish_at=456,polish_result='running' WHERE xianyu_account_id=1");
        c.setRunningUntil(0L);c.setPolishIntervalHours(6);c.setBlockRepeatAccount(true);settings.save(c);
        var saved=settings.get(1L);assertEquals(123L,saved.getRunningUntil());assertEquals("running",saved.getPolishResult());assertTrue(saved.isBlockRepeatAccount());assertFalse(settings.get(2L).isBlockRepeatAccount());
    }
    @Test void privateAndGlobalBlacklistRespectAccountGoodsAndDisabledFlag(){
        settings.saveBlacklist(new AutomationSettingsService.BlacklistEntry(null,1L,"123","buyer","blocked","goods",true));
        assertThrows(BusinessException.class,()->guard.check(1L,"goods","new","123"));
        assertDoesNotThrow(()->guard.check(2L,"goods","new","123"));assertDoesNotThrow(()->guard.check(1L,"other","new","123"));
        settings.saveBlacklist(new AutomationSettingsService.BlacklistEntry(null,null,"456","","global","",true));
        assertThrows(BusinessException.class,()->guard.check(2L,"other","new","456"));
        db.update("UPDATE xianyu_buyer_blacklist SET enabled=0");assertDoesNotThrow(()->guard.check(1L,"goods","new","123"));verifyNoInteractions(seller,api);
    }
    @Test void exemptionDoesNotBypassBlacklist(){
        var c=settings.get(1L);c.setBlockLowCredit(true);c.setExcludedGoods("other, goods\nthird");settings.save(c);
        assertDoesNotThrow(()->guard.check(1L,"goods","new","123"));verifyNoInteractions(api);
        settings.saveBlacklist(new AutomationSettingsService.BlacklistEntry(null,null,"123","","","",true));
        assertThrows(BusinessException.class,()->guard.check(1L,"goods","new","123"));
    }
    @Test void ratingThresholdAndUnknownCreditStopDelivery(){
        var c=settings.get(1L);c.setBlockLowCredit(true);settings.save(c);
        when(api.callApiWithRetry(eq(1L),eq("mtop.idle.web.trade.rate.list"),anyMap(),anyString())).thenReturn(new XianyuApiCallUtils.ApiCallResult(true,"""
                {"data":{"totalCount":0}}
                """,null,false));
        assertThrows(BusinessException.class,()->guard.check(1L,"goods","new","123"));
        when(api.callApiWithRetry(eq(1L),eq("mtop.idle.web.trade.rate.list"),anyMap(),anyString())).thenReturn(new XianyuApiCallUtils.ApiCallResult(true,"""
                {"data":{}}
                """,null,false));
        assertThrows(BusinessException.class,()->guard.check(1L,"goods","new","123"));
    }
    @Test void repeatProtectionExcludesCurrentCancelledAndOtherAccountOrders(){
        var c=settings.get(1L);c.setBlockRepeatAccount(true);settings.save(c);
        order(1,"new","goods");order(1,"old","goods");order(2,"other","goods");status(1,"old","交易关闭");status(2,"other","交易成功");
        assertDoesNotThrow(()->guard.check(1L,"goods","new","123"));
        c.setBlockRepeatGlobal(true);settings.save(c);assertThrows(BusinessException.class,()->guard.check(1L,"goods","new","123"));
    }
    @Test void unconfirmedOrdersAreVerifiedOnPlatformInsteadOfLocalSendState(){
        var c=settings.get(1L);c.setBlockUnconfirmed(true);c.setUnconfirmedThreshold(2);settings.save(c);
        order(1,"a","goods");order(1,"b","goods");status(1,"a","待收货");status(1,"b","交易成功");
        assertDoesNotThrow(()->guard.check(1L,"goods","new","123"));status(1,"b","待收货");
        assertThrows(BusinessException.class,()->guard.check(1L,"goods","new","123"));status(1,"b","new-unknown-platform-status");
        assertThrows(BusinessException.class,()->guard.check(1L,"goods","new","123"));
    }
    @Test void sharedWarehouseVisibleToAllButCannotNarrowWithOtherAccountBindings(){
        var mapper=session.getMapper(XianyuKamiConfigMapper.class);
        var warehouse=new XianyuKamiConfig();warehouse.setXianyuAccountId(1L);warehouse.setAliasName("shared");warehouse.setScope("SHARED");mapper.insert(warehouse);
        var privateWarehouse=new XianyuKamiConfig();privateWarehouse.setXianyuAccountId(1L);privateWarehouse.setAliasName("private");privateWarehouse.setScope("PRIVATE");mapper.insert(privateWarehouse);
        assertEquals(2,mapper.findByAccountId(1L).size());assertEquals(1,mapper.findByAccountId(2L).size());
        db.update("INSERT INTO xianyu_goods_auto_delivery_config(xianyu_account_id,xy_goods_id,kami_config_ids) VALUES (2,'goods',?)",warehouse.getId().toString());
        var service=new KamiConfigServiceImpl();ReflectionTestUtils.setField(service,"kamiConfigMapper",mapper);ReflectionTestUtils.setField(service,"kamiItemMapper",session.getMapper(XianyuKamiItemMapper.class));ReflectionTestUtils.setField(service,"db",db);
        var request=new KamiConfigReqDTO();request.setId(warehouse.getId());request.setXianyuAccountId(1L);request.setScope("PRIVATE");
        assertNotEquals(200,service.createOrUpdateConfig(request).getCode());assertEquals("SHARED",mapper.selectById(warehouse.getId()).getScope());
        db.update("DELETE FROM xianyu_goods_auto_delivery_config");assertEquals(200,service.createOrUpdateConfig(request).getCode());assertEquals(0,mapper.findByAccountId(2L).size());
    }
    @Test void polishUsesVersionTwoStopsOnFailureAndPersistsNextRun(){
        goods();db.update("INSERT INTO xianyu_goods(xianyu_account_id,xy_good_id,status,title) VALUES(1,'second',0,'Second')");
        var c=settings.get(1L);c.setPolishEnabled(true);c.setPolishIntervalHours(3);settings.save(c);
        when(api.callApiWithRetry(eq(1L),eq("mtop.taobao.idle.item.polish"),anyMap(),anyString(),anyMap(),eq(Map.of("v","2.0")))).thenReturn(new XianyuApiCallUtils.ApiCallResult(false,null,"risk",false));
        polish.run(1L,false);
        verify(api,times(1)).callApiWithRetry(eq(1L),anyString(),anyMap(),anyString(),anyMap(),eq(Map.of("v","2.0")));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM xianyu_polish_record",Integer.class));
        var saved=settings.get(1L);assertEquals(0L,saved.getRunningUntil());assertTrue(saved.getNextPolishAt()>System.currentTimeMillis()+2*3600000L);
        polish.run(1L,false);verifyNoMoreInteractions(api);
    }
    @Test void skuRecognitionNeverAcceptsAnotherGoodsAndEmptyResponsePreservesExisting(){
        goods();var store=new GoodsSkuServiceImpl();ReflectionTestUtils.setField(store,"goodsSkuMapper",session.getMapper(XianyuGoodsSkuMapper.class));
        var existing=new XianyuGoodsSku();existing.setSkuId("sku-original");existing.setValueText("Original");store.saveSkus("goods",1L,List.of(existing));
        var recognition=new SkuRecognitionService(seller,accounts,api,store,db);
        when(seller.call(eq(1L),anyString(),anyMap())).thenReturn(Map.of("itemSearchResponseList",List.of(Map.of("itemId","other","skuList",List.of(Map.of("skuId","wrong","name","Wrong"))))));
        assertThrows(BusinessException.class,()->recognition.recognize(1L,"goods","seller"));
        when(api.callOnce(eq(1L),anyString(),anyMap(),anyString())).thenReturn(new XianyuApiCallUtils.ApiCallResult(true,"""
                {"data":{"itemDO":{"itemId":"goods"}}}
                """,null,false));
        assertThrows(BusinessException.class,()->recognition.recognize(1L,"goods","buyer"));
        assertEquals("sku-original",store.listByXyGoodsId("goods").getFirst().getSkuId());
    }
    @Test void sellerSkuRecognitionSavesActualValueTextAndExactId(){
        goods();var store=new GoodsSkuServiceImpl();ReflectionTestUtils.setField(store,"goodsSkuMapper",session.getMapper(XianyuGoodsSkuMapper.class));
        var recognition=new SkuRecognitionService(seller,accounts,api,store,db);
        when(seller.call(eq(1L),eq("mtop.alibaba.idle.seller.pc.common.item.search"),anyMap())).thenReturn(Map.of("data",Map.of("itemSearchResponseList",List.of(Map.of("itemId","goods","idleItemSkuList",List.of(Map.of("inventoryId","00012345678901234567890","propertyList",List.of(Map.of("actualValueText","月卡","valueText","旧值")))))))));
        var found=recognition.recognize(1L,"goods","seller");assertEquals(1,found.size());assertEquals("月卡",found.getFirst().getValueText());assertEquals("00012345678901234567890",found.getFirst().getSkuId());
        assertEquals(1,db.queryForObject("SELECT sku_count FROM xianyu_goods WHERE id=1",Integer.class));
    }
    @Test void transactionalSaveReturnsFreshSettingsInsteadOfMybatisCachedValues() {
        settings.save(settings.get(1L));
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        tx.execute(status -> {
            var value=settings.get(1L);assertEquals(24,value.getPolishIntervalHours());value.setPolishIntervalHours(8);
            assertEquals(8,settings.save(value).getPolishIntervalHours());return null;
        });
    }
}
