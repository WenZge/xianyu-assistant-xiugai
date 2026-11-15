package com.feijimiao.xianyuassistant.service.delivery;

import com.feijimiao.xianyuassistant.entity.*;
import com.feijimiao.xianyuassistant.exception.BusinessException;
import com.feijimiao.xianyuassistant.mapper.XianyuKamiUsageRecordMapper;
import com.feijimiao.xianyuassistant.service.KamiConfigService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KamiDeliveryStrategyTest {
    private final KamiConfigService stock = mock(KamiConfigService.class);
    private final XianyuKamiUsageRecordMapper usage = mock(XianyuKamiUsageRecordMapper.class);
    private final KamiDeliveryStrategy strategy = new KamiDeliveryStrategy();
    private DeliveryContext context;
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(strategy,"kamiConfigService",stock); ReflectionTestUtils.setField(strategy,"kamiUsageRecordMapper",usage);
        var config = new XianyuGoodsAutoDeliveryConfig(); config.setKamiConfigIds("10");
        context = DeliveryContext.builder().accountId(1L).xyGoodsId("goods").orderId("order").deliveryConfig(config).build();
        var warehouse = new XianyuKamiConfig(); warehouse.setId(10L); warehouse.setXianyuAccountId(1L); when(stock.getConfig(10L)).thenReturn(warehouse);
    }
    @Test void offlineCardAllocationAcceptsAbsentConversation() {
        var card = new XianyuKamiItem(); card.setId(100L); card.setKamiContent("CARD"); when(stock.acquireKami(10L,"order")).thenReturn(card);
        assertEquals("CARD",strategy.resolve(context)); verify(usage).insert(any(XianyuKamiUsageRecord.class));
    }
    @Test void otherAccountWarehouseCannotConsumeCard() {
        var other = new XianyuKamiConfig(); other.setXianyuAccountId(2L); when(stock.getConfig(10L)).thenReturn(other);
        assertThrows(BusinessException.class, () -> strategy.resolve(context)); verify(stock,never()).acquireKami(any(),any());
    }
    @Test void templateWithoutCardPlaceholderCannotConsumeCard() {
        context.getDeliveryConfig().setKamiDeliveryTemplate("谢谢购买"); assertThrows(BusinessException.class, () -> strategy.resolve(context)); verify(stock,never()).acquireKami(any(),any());
    }
    @Test void sharedWarehouseAllowsOtherAccountWithoutRelaxingPrivateCheck() {
        var shared=new XianyuKamiConfig();shared.setXianyuAccountId(2L);shared.setScope("SHARED");when(stock.getConfig(10L)).thenReturn(shared);
        var card=new XianyuKamiItem();card.setId(99L);card.setKamiContent("SHARED-CARD");when(stock.acquireKami(10L,"order")).thenReturn(card);
        assertEquals("SHARED-CARD",strategy.resolve(context));verify(usage).insert(any(XianyuKamiUsageRecord.class));
    }
}
