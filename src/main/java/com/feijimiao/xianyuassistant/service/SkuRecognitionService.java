package com.feijimiao.xianyuassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsSku;
import com.feijimiao.xianyuassistant.exception.BusinessException;
import com.feijimiao.xianyuassistant.utils.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.feijimiao.xianyuassistant.service.BuyerInteractionApi.*;

@Service
public class SkuRecognitionService {
    private final BuyerInteractionApi seller;
    private final AccountService accounts;
    private final XianyuApiCallUtils api;
    private final GoodsSkuService skus;
    private final JdbcTemplate db;
    private final Map<String, Long> cooldown = new ConcurrentHashMap<>();
    private final Object[] locks = new Object[64];
    public SkuRecognitionService(BuyerInteractionApi seller, AccountService accounts, XianyuApiCallUtils api, GoodsSkuService skus, JdbcTemplate db) {
        this.seller = seller; this.accounts = accounts; this.api = api; this.skus = skus; this.db = db;
        Arrays.setAll(locks, i -> new Object());
    }
    public List<XianyuGoodsSku> recognize(Long accountId, String itemId, String source) {
        if (accountId == null || itemId == null || itemId.isBlank()) throw new BusinessException("请选择账号和商品");
        if (!Set.of("seller", "buyer").contains(source)) throw new BusinessException("未知识别来源");
        synchronized (locks[Math.floorMod(accountId.hashCode(), locks.length)]) {
            if (db.queryForObject("SELECT count(*) FROM xianyu_goods WHERE xianyu_account_id=? AND xy_good_id=?", Integer.class, accountId, itemId) == 0)
                throw new BusinessException("商品不属于当前账号，请先同步商品列表");
            String key = accountId + ":" + source;
            if (cooldown.getOrDefault(accountId + ":risk", 0L) > System.currentTimeMillis()) throw new BusinessException("平台请求异常，已暂停识别5分钟，请先核实账号状态");
            if (cooldown.getOrDefault(key, 0L) > System.currentTimeMillis()) throw new BusinessException("识别请求冷却中，请稍后再试（普通请求10秒，失败后5分钟）");
            cooldown.put(key, System.currentTimeMillis() + 10000);
            try {
                Object data;
                if ("seller".equals(source)) {
                    var response = seller.call(accountId, "mtop.alibaba.idle.seller.pc.common.item.search", Map.of(
                        "pageNo", 1, "pageSize", 50, "itemStatus", "0,-9", "bizType", "commonPro",
                        "searchRequest", new ObjectMapper().writeValueAsString(Map.of("itemId", itemId))));
                    Map<?, ?> body = response.get("data") instanceof Map ? map(response.get("data")) : response;
                    var items = list(body.containsKey("itemSearchResponseList") ? body.get("itemSearchResponseList") : body.get("items"));
                    data = items.stream().filter(Map.class::isInstance).map(BuyerInteractionApi::map)
                        .filter(row -> itemId.equals(text(row.containsKey("itemId") ? row.get("itemId") : row.get("id"))))
                        .findFirst().orElseThrow(() -> new BusinessException("卖家接口未找到该商品，可手动尝试买家接口"));
                } else {
                    String cookie = accounts.getCookieByAccountId(accountId);
                    if (cookie == null || cookie.isBlank()) throw new BusinessException("账号Cookie不可用");
                    // 不自动回退；备用接口仅在用户点击时请求。
                    var response = api.callOnce(accountId, "mtop.taobao.idle.pc.detail", Map.of("itemId", itemId), cookie);
                    if (!response.isSuccess()) throw new BusinessException(response.getErrorMessage());
                    data = response.extractData();
                    String returnedId = text(map(map(data).get("itemDO")).get("itemId"));
                    if (!returnedId.isEmpty() && !itemId.equals(returnedId)) throw new BusinessException("返回商品ID不匹配");
                }
                var found = ItemDetailUtils.extractSkuList(new ObjectMapper().writeValueAsString(data));
                if (found.isEmpty()) throw new BusinessException("接口未返回可用SKU，已保留原规格。请确认商品已发布多规格，或尝试另一识别入口");
                skus.saveSkus(itemId, accountId, found);
                db.update("UPDATE xianyu_goods SET sku_count=? WHERE xianyu_account_id=? AND xy_good_id=?", found.size(), accountId, itemId);
                return found;
            } catch (Exception e) {
                cooldown.put(key, System.currentTimeMillis() + 300000);
                String error = Objects.toString(e.getMessage(), "");
                if (error.contains("风控") || error.contains("RGV") || error.contains("验证") || error.contains("令牌") || error.contains("流量"))
                    cooldown.put(accountId + ":risk", System.currentTimeMillis() + 300000);
                if (e instanceof BusinessException b) throw b;
                throw new BusinessException("SKU识别失败，请稍后重试", e);
            }
        }
    }
}
