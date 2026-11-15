package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.exception.BusinessException;
import com.feijimiao.xianyuassistant.utils.XianyuApiCallUtils;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BuyerInteractionApiTest {
    private final AccountService accounts = mock(AccountService.class);
    private final XianyuApiCallUtils http = mock(XianyuApiCallUtils.class);
    private final BuyerInteractionApi api = new BuyerInteractionApi(accounts,http);
    @Test void outerSuccessDoesNotHideBusinessFailure() {
        when(accounts.getCookieByAccountId(1L)).thenReturn("cookie");
        when(http.callApiWithRetry(eq(1L),anyString(),anyMap(),eq("cookie"),anyMap(),anyMap()))
            .thenReturn(new XianyuApiCallUtils.ApiCallResult(true,"{\"data\":{\"module\":{\"success\":false,\"failOrderInfos\":[{\"orderId\":\"order\"}]}}}",null,false));
        assertThrows(BusinessException.class, () -> api.rate(1L,"order","好评"));
    }
    @Test void emptyCookieCannotSendInteraction() {
        assertThrows(BusinessException.class, () -> api.flower(1L,"order")); verifyNoInteractions(http);
    }
}
