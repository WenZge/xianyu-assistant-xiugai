package com.feijimiao.xianyuassistant.service.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.service.AccountService;
import com.feijimiao.xianyuassistant.utils.XianyuApiCallUtils;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderDetailFetcherTest {
    private final AccountService accounts = mock(AccountService.class);
    private final XianyuApiCallUtils api = mock(XianyuApiCallUtils.class);
    private final SkuResolver sku = mock(SkuResolver.class);
    private final OrderDetailFetcher fetcher = new OrderDetailFetcher();
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(fetcher,"accountService",accounts); ReflectionTestUtils.setField(fetcher,"xianyuApiCallUtils",api); ReflectionTestUtils.setField(fetcher,"skuResolver",sku);
        when(accounts.getCookieByAccountId(1L)).thenReturn("cookie");
    }
    private void response(Map<String,Object> module) throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of("data",Map.of("module",module)));
        when(api.callApiWithRetry(eq(1L),anyString(),anyMap(),eq("cookie"))).thenReturn(new XianyuApiCallUtils.ApiCallResult(true,body,null,false));
    }
    @Test void directSkuIdWinsAndQuantityAcceptsNumericString() throws Exception {
        response(Map.of("orderInfoVO",Map.of("itemInfo",Map.of("itemId","goods","skuId",12345,"skuInfo","周期:月卡")),"merchantPriceVO",Map.of("buyNum","3"),"merchantBuyerVO",Map.of("buyerId","buyer")));
        when(sku.parseSkuInfoToValueText("周期:月卡")).thenReturn("月卡"); when(sku.resolveSkuIdByText(1L,"goods","月卡")).thenReturn("different");
        var detail = fetcher.fetch(1L,"goods","order"); assertEquals("12345",detail.skuId); assertEquals(3,detail.buyNum); assertEquals("goods",detail.xyGoodsId); assertEquals("buyer",detail.buyerUserId);
    }
    @Test void invalidQuantityIsNeverSilentlyChangedToOne() throws Exception {
        response(Map.of("merchantPriceVO",Map.of("buyNum","bad")));
        assertNull(fetcher.fetch(1L,"goods","order").buyNum);
    }
}
