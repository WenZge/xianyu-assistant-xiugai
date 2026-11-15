package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.exception.BusinessException;
import com.feijimiao.xianyuassistant.utils.XianyuApiCallUtils;
import org.springframework.stereotype.Component;
import java.util.*;

/** 复用项目现有签名、Cookie刷新和卖家请求头。 */
@Component
public class BuyerInteractionApi {
    private final AccountService accounts;
    private final XianyuApiCallUtils api;
    public BuyerInteractionApi(AccountService accounts, XianyuApiCallUtils api) { this.accounts = accounts; this.api = api; }
    public static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> m ? m : Map.of(); }
    public static List<?> list(Object value) { return value instanceof List<?> l ? l : List.of(); }
    public static String text(Object value) { return value == null ? "" : value.toString(); }

    public Map<String, Object> call(Long accountId, String name, Map<String, Object> payload) {
        String cookie = accounts.getCookieByAccountId(accountId);
        if (cookie == null || cookie.isBlank()) throw new BusinessException("账号Cookie不可用");
        var result = api.callApiWithRetry(accountId, name, payload, cookie,
                Map.of("idle_site_biz_code", "COMMONPRO", "Origin", "https://seller.goofish.com", "Referer", "https://seller.goofish.com/?site=COMMONPRO"),
                Map.of("type", "json"));
        if (!result.isSuccess()) throw new BusinessException(result.getErrorMessage());
        var data = result.extractData();
        if (data == null) throw new BusinessException("平台返回空数据");
        return data;
    }

    public List<Map<?, ?>> completedOrders(Long accountId, String orderId, int page) {
        var data = call(accountId, "mtop.taobao.idle.trade.merchant.sold.get", Map.of(
                "pageNumber", page, "rowsPerPage", 50, "orderIds", orderId == null ? "" : orderId,
                "queryCode", "TRADE_SUCCESS", "orderSearchParam", "{}"));
        return list(map(data.get("module")).get("items")).stream().filter(Map.class::isInstance).<Map<?, ?>>map(v -> (Map<?, ?>) v).toList();
    }

    public void rate(Long accountId, String orderId, String content) {
        var data = call(accountId, "mtop.taobao.idle.merchant.rate.create", Map.of(
                "tradeIdList", List.of(orderId), "rate", 1, "feedback", content, "imageUrls", List.of(), "anonymous", false));
        var module = map(data.get("module"));
        if (!Boolean.parseBoolean(text(module.get("success"))) || !list(module.get("failOrderInfos")).isEmpty())
            throw new BusinessException("平台未确认评价成功: " + text(module.get("failOrderInfos")));
    }

    public void flower(Long accountId, String orderId) {
        var data = call(accountId, "mtop.taobao.idlemessage.red.flower", Map.of("orderId", orderId));
        if (Boolean.FALSE.equals(data.get("success")) || Boolean.FALSE.equals(map(data.get("module")).get("success")))
            throw new BusinessException("平台拒绝求小红花");
    }
}
