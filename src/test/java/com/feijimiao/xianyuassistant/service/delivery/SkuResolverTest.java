package com.feijimiao.xianyuassistant.service.delivery;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsSku;
import com.feijimiao.xianyuassistant.service.GoodsSkuService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkuResolverTest {
    private final GoodsSkuService goods = mock(GoodsSkuService.class);
    private final SkuResolver resolver = new SkuResolver();
    @BeforeEach void setup() { ReflectionTestUtils.setField(resolver, "goodsSkuService", goods); }
    private XianyuGoodsSku sku(Long account, String id, String text) {
        var s = new XianyuGoodsSku(); s.setXianyuAccountId(account); s.setSkuId(id); s.setValueText(text); return s;
    }
    @Test void parsesFullWidthSeparatorsAndPreservesColonsInValues() {
        assertEquals("红色 XL", resolver.parseSkuInfoToValueText("颜色：红色；尺码：XL"));
        assertEquals("12:30", resolver.parseSkuInfoToValueText("时间:12:30"));
        assertNull(resolver.parseSkuInfoToValueText(null));
    }
    @Test void reorderedDimensionsStillMatch() {
        when(goods.listByXyGoodsId("goods")).thenReturn(List.of(sku(1L,"red-xl","红色 XL")));
        assertEquals("red-xl", resolver.resolveSkuIdByText(1L,"goods","尺码:XL;颜色:红色"));
    }
    @Test void otherAccountSkuCannotMatch() {
        when(goods.listByXyGoodsId("goods")).thenReturn(List.of(sku(2L,"other","红色 XL")));
        assertNull(resolver.resolveSkuIdByText(1L,"goods","红色 XL"));
    }
    @Test void ambiguousTextStopsInsteadOfGuessing() {
        when(goods.listByXyGoodsId("goods")).thenReturn(List.of(sku(1L,"first","红色 XL"), sku(1L,"second","XL 红色")));
        assertNull(resolver.resolveSkuIdByText(1L,"goods","红色 XL"));
    }
}
