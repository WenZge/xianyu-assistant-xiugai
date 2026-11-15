package com.feijimiao.xianyuassistant.service.delivery;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.exception.BusinessException;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryConfigSelectorTest {
    private final XianyuGoodsAutoDeliveryConfigMapper mapper = mock(XianyuGoodsAutoDeliveryConfigMapper.class);
    private final DeliveryConfigSelector selector = new DeliveryConfigSelector(mapper);
    private XianyuGoodsAutoDeliveryConfig config(String sku) { var c = new XianyuGoodsAutoDeliveryConfig(); c.setSkuId(sku); return c; }
    @Test void selectsExactSkuOverDefault() {
        var base = config(null); var weekly = config("week"); var monthly = config("month");
        when(mapper.findByAccountIdAndGoodsId(1L, "goods")).thenReturn(List.of(base, weekly, monthly));
        assertSame(monthly, selector.select(1L, "goods", "month"));
    }
    @Test void unknownSkuNeverFallsBackWhenSkuRulesExist() {
        when(mapper.findByAccountIdAndGoodsId(1L, "goods")).thenReturn(List.of(config(null), config("week")));
        assertThrows(BusinessException.class, () -> selector.select(1L, "goods", "unknown"));
        assertThrows(BusinessException.class, () -> selector.select(1L, "goods", null));
    }
    @Test void singleSpecificationKeepsLegacyDefault() {
        var base = config(null); when(mapper.findByAccountIdAndGoodsId(2L, "goods")).thenReturn(List.of(base));
        assertSame(base, selector.select(2L, "goods", null));
        assertSame(base, selector.select(2L, "goods", "new-sku"));
    }
    @Test void missingConfigurationStopsDelivery() {
        when(mapper.findByAccountIdAndGoodsId(1L, "goods")).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> selector.select(1L, "goods", null));
    }
}
