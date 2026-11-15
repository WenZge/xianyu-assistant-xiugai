package com.feijimiao.xianyuassistant.service.delivery;

import com.feijimiao.xianyuassistant.service.*;
import com.feijimiao.xianyuassistant.utils.XianyuApiCallUtils;
import com.feijimiao.xianyuassistant.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.feijimiao.xianyuassistant.service.BuyerInteractionApi.*;

/** 只在出库/发送前检查；命中后由原发货流程记录失败原因，不自动关单。 */
@Service
public class DeliveryProtectionService {
    private final AutomationSettingsService settings;
    private final JdbcTemplate db;
    private final BuyerInteractionApi seller;
    private final AccountService accounts;
    private final XianyuApiCallUtils api;
    public DeliveryProtectionService(AutomationSettingsService settings, JdbcTemplate db, BuyerInteractionApi seller, AccountService accounts, XianyuApiCallUtils api) {
        this.settings=settings; this.db=db; this.seller=seller; this.accounts=accounts; this.api=api;
    }
    public void check(Long accountId, String goodsId, String orderId, String buyerId) {
        var config = settings.get(accountId);
        boolean rules = config.isBlockLowCredit() || config.isBlockRepeatAccount() || config.isBlockRepeatGlobal() || config.isBlockUnconfirmed();
        boolean hasBlacklist = config.isBlacklistEnabled() && db.queryForObject("SELECT count(*) FROM xianyu_buyer_blacklist WHERE enabled=1 AND (xianyu_account_id IS NULL OR xianyu_account_id=?) AND (goods_id IS NULL OR goods_id='' OR goods_id=?)", Integer.class, accountId, goodsId) > 0;
        if (!hasBlacklist && !rules) return;
        if (buyerId == null || buyerId.isBlank()) throw new BusinessException("发货保护：未获取到买家ID，已暂停发货");
        if (config.isBlacklistEnabled()) {
            var blocked = db.queryForList("SELECT reason FROM xianyu_buyer_blacklist WHERE enabled=1 AND buyer_id=? AND (xianyu_account_id IS NULL OR xianyu_account_id=?) AND (goods_id IS NULL OR goods_id='' OR goods_id=?) LIMIT 1", buyerId, accountId, goodsId);
            if (!blocked.isEmpty()) throw new BusinessException("发货保护：买家命中黑名单。" + text(blocked.get(0).get("reason")));
        }
        if (!rules || Arrays.asList(Objects.toString(config.getExcludedGoods(), "").split("[,，\\s]+" )).contains(goodsId)) return;
        if (config.isBlockLowCredit()) {
            var result = api.callApiWithRetry(accountId, "mtop.idle.web.trade.rate.list", Map.of(
                "rateType",0,"ratedUid",buyerId,"raterType",0,"rowsPerPage",20,"pageNumber",1,"foldFlag",0,"fishAdCode","330110","extraTag",""), accounts.getCookieByAccountId(accountId));
            if (!result.isSuccess()) throw new BusinessException("发货保护：无法核实买家被评价数，请稍后重试");
            int count;
            try { count = Integer.parseInt(text(map(result.extractData()).get("totalCount"))); }
            catch (Exception e) { throw new BusinessException("发货保护：平台未返回有效的买家被评价数"); }
            if (count < 0) throw new BusinessException("发货保护：平台返回无效信用数据");
            if (count <= config.getRatingThreshold()) throw new BusinessException("发货保护：买家被评价数 " + count + " 未超过阈值 " + config.getRatingThreshold());
        }
        if (!(config.isBlockRepeatAccount() || config.isBlockRepeatGlobal() || config.isBlockUnconfirmed())) return;
        // 检查本系统已记录的买家历史订单，排除当前单；付款、退款和确认收货状态由平台重新核实。
        var previous = db.queryForList("SELECT DISTINCT xianyu_account_id,order_id,xy_goods_id FROM xianyu_goods_order WHERE buyer_user_id=? AND order_id<>? AND (?=1 OR xianyu_account_id=?) AND (?=0 OR xy_goods_id=?) LIMIT 1001",
            buyerId, orderId, config.isBlockRepeatGlobal(), accountId, config.isSameGoodsOnly(), goodsId);
        if (previous.size() > 1000) throw new BusinessException("发货保护：历史订单过多，请人工核实");
        int pending = 0;
        for (var previousOrder : previous) {
            Long previousAccount = ((Number)previousOrder.get("xianyu_account_id")).longValue();
            Map<?, ?> common;
            try {
                var detail = seller.call(previousAccount, "mtop.taobao.idle.trade.merchant.full.info", Map.of("tid", text(previousOrder.get("order_id"))));
                common = map(map(detail.get("module")).get("merchantCommonData"));
            } catch (Exception e) { throw new BusinessException("发货保护：历史订单状态核实失败，稍后重试"); }
            String state = text(common.get("orderStatus"));
            if (state.isBlank()) throw new BusinessException("发货保护：历史订单状态未知，已暂停发货");
            String refund = text(common.get("refundStatusDesc"));
            if (Boolean.parseBoolean(text(common.get("inRefund"))) || state.contains("退款") || state.contains("关闭") || state.contains("取消") || state.contains("待付款") || state.contains("等待买家付款")
                    || state.equals("TRADE_CLOSED") || state.equals("WAIT_BUYER_PAY") || refund.contains("退款中")) continue;
            boolean shipped = state.contains("待收货") || state.contains("等待买家确认收货") || state.contains("已发货") || state.equals("WAIT_BUYER_CONFIRM_GOODS");
            boolean valid = shipped || state.contains("待发货") || state.contains("等待卖家发货") || state.contains("交易成功") || state.contains("已完成") || Set.of("TRADE_SUCCESS","WAIT_SELLER_SEND_GOODS").contains(state);
            if (!valid) throw new BusinessException("发货保护：无法识别历史订单状态：" + state);
            if (config.isBlockRepeatGlobal() || (config.isBlockRepeatAccount() && accountId.equals(previousAccount)))
                throw new BusinessException("发货保护：买家存在其他有效历史订单（" + text(previousOrder.get("order_id")) + "）");
            if (config.isBlockUnconfirmed() && shipped && accountId.equals(previousAccount) && ++pending >= config.getUnconfirmedThreshold())
                throw new BusinessException("发货保护：买家待确认收货订单达到 " + pending + " 单");
        }
    }
}
