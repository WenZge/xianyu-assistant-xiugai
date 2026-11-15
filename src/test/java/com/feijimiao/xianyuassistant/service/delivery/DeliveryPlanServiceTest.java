package com.feijimiao.xianyuassistant.service.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.*;
import com.feijimiao.xianyuassistant.exception.BusinessException;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeliveryPlanServiceTest {
    private AnnotationConfigApplicationContext context;
    private DeliveryPlanService plans;
    private DeliveryStrategyResolver resolver;
    private XianyuGoodsOrderMapper orders;
    private JdbcTemplate jdbc;
    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() { return new SingleConnectionDataSource("jdbc:sqlite::memory:", true); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean DeliveryPlanService plans(OrderDetailFetcher f, DeliveryConfigSelector s, DeliveryStrategyResolver r, XianyuGoodsOrderMapper o) { return new DeliveryPlanService(f,s,r,o); }
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("fetcher", mock(OrderDetailFetcher.class));
        context.getBeanFactory().registerSingleton("selector", mock(DeliveryConfigSelector.class));
        context.getBeanFactory().registerSingleton("resolver", mock(DeliveryStrategyResolver.class));
        context.getBeanFactory().registerSingleton("orders", mock(XianyuGoodsOrderMapper.class));
        context.register(Config.class); context.refresh();
        plans = context.getBean(DeliveryPlanService.class); resolver = context.getBean(DeliveryStrategyResolver.class); orders = context.getBean(XianyuGoodsOrderMapper.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class)); jdbc.execute("CREATE TABLE reserved (id INTEGER)");
        var detail = new OrderDetailFetcher.OrderDetailInfo(); detail.buyNum = 3; detail.skuId = "month";
        when(context.getBean(OrderDetailFetcher.class).fetch(1L,"goods","order")).thenReturn(detail);
        var config = new XianyuGoodsAutoDeliveryConfig(); config.setDeliveryMode(2);
        when(context.getBean(DeliveryConfigSelector.class).select(1L,"goods","month")).thenReturn(config);
        var existing = new XianyuGoodsOrder(); existing.setId(10L);
        when(orders.selectByAccountIdAndOrderId(1L,"order")).thenReturn(existing);
    }
    @AfterEach void teardown() { context.close(); }
    @Test void allocatesExactlyPurchasedQuantityAndPersistsBeforeSending() {
        when(resolver.resolve(eq(2),any())).thenAnswer(inv -> { jdbc.update("INSERT INTO reserved VALUES (1)"); return "CARD"; });
        var plan = plans.prepare(1L,"goods","order",null,"buyer",true);
        assertEquals(List.of("CARD","CARD","CARD"),plan.contents());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM reserved",Integer.class));
        verify(orders).saveDeliveryPayload(eq(10L),contains("CARD"),eq("CARD\nCARD\nCARD"));
    }
    @Test void insufficientStockRollsBackWholeOrder() {
        when(resolver.resolve(eq(2),any())).thenAnswer(inv -> { jdbc.update("INSERT INTO reserved VALUES (1)"); return "CARD"; }).thenReturn(null);
        assertThrows(BusinessException.class, () -> plans.prepare(1L,"goods","order",null,null,true));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM reserved",Integer.class));
        verify(orders,never()).saveDeliveryPayload(anyLong(),anyString(),anyString());
    }
    @Test void offlineLengthLimitRollsBackInsteadOfTruncatingCards() {
        when(resolver.resolve(eq(2),any())).thenAnswer(inv -> { jdbc.update("INSERT INTO reserved VALUES (1)"); return "A".repeat(100); });
        assertThrows(BusinessException.class, () -> plans.prepare(1L,"goods","order",null,null,true));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM reserved",Integer.class));
    }
    @Test void retryReusesOriginalReservedCardsWithoutAllocatingAgain() throws Exception {
        var existing = new XianyuGoodsOrder(); existing.setId(10L);
        existing.setDeliveryPayload(new ObjectMapper().writeValueAsString(new DeliveryPlanService.Plan(List.of("ORIGINAL"),List.of(),false)));
        when(orders.selectByAccountIdAndOrderId(1L,"order")).thenReturn(existing);
        assertEquals("ORIGINAL",plans.prepare(1L,"goods","order",null,null,true).text());
        verifyNoInteractions(resolver);
        verify(context.getBean(OrderDetailFetcher.class),never()).fetch(any(),any(),any());
    }
    @Test void invalidQuantityCannotConsumeStock() {
        var detail = new OrderDetailFetcher.OrderDetailInfo(); detail.buyNum = 0;
        when(context.getBean(OrderDetailFetcher.class).fetch(1L,"goods","order")).thenReturn(detail);
        assertThrows(BusinessException.class, () -> plans.prepare(1L,"goods","order",null,null,true));
        verifyNoInteractions(resolver);
    }
}
