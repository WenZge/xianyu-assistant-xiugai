package com.feijimiao.xianyuassistant.service.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.exception.BusinessException;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 一次预留整单库存并持久化，发送失败重试使用原卡密。 */
@Service
public class DeliveryPlanService {
    public record Plan(List<String> contents, List<String> images, boolean autoConfirm) {
        public String text() { return String.join("\n", contents); }
    }
    private final OrderDetailFetcher fetcher;
    private final DeliveryConfigSelector selector;
    private final DeliveryStrategyResolver resolver;
    private final XianyuGoodsOrderMapper orders;
    private final DeliveryProtectionService protection;
    private final ObjectMapper json = new ObjectMapper();
    public DeliveryPlanService(OrderDetailFetcher fetcher, DeliveryConfigSelector selector,
                               DeliveryStrategyResolver resolver, XianyuGoodsOrderMapper orders, DeliveryProtectionService protection) {
        this.fetcher = fetcher; this.selector = selector; this.resolver = resolver; this.orders = orders; this.protection = protection;
    }

    @Transactional(rollbackFor = Exception.class)
    public Plan prepare(Long accountId, String goodsId, String orderId, String sid, String buyerName, boolean offline) {
        if (orderId == null || orderId.isBlank()) throw new BusinessException("缺少订单ID，无法核实规格和数量");
        var existing = orders.selectByAccountIdAndOrderId(accountId, orderId);
        if (existing != null && existing.getDeliveryPayload() != null && !existing.getDeliveryPayload().isBlank()) {
            try {
                if (!goodsId.equals(existing.getXyGoodsId())) throw new BusinessException("订单商品与当前商品不一致");
                String buyerId = existing.getBuyerUserId();
                if (buyerId == null || buyerId.isBlank()) {
                    var detail = fetcher.fetch(accountId, goodsId, orderId);
                    buyerId = detail == null ? null : detail.buyerUserId;
                }
                protection.check(accountId, goodsId, orderId, buyerId);
                Plan plan = json.readValue(existing.getDeliveryPayload(), Plan.class);
                checkLength(plan, offline);
                return plan;
            } catch (BusinessException e) { throw e; }
            catch (Exception e) { throw new BusinessException("已预留发货内容读取失败，请检查发货记录", e); }
        }
        var detail = fetcher.fetch(accountId, goodsId, orderId);
        if (detail == null) throw new BusinessException("获取订单详情失败，请检查Cookie后重试");
        if (detail.xyGoodsId != null && !goodsId.equals(detail.xyGoodsId)) throw new BusinessException("订单商品与当前商品不一致，已停止发货");
        if (detail.buyNum == null || detail.buyNum < 1 || detail.buyNum > 100)
            throw new BusinessException("订单购买数量缺失或超出1至100件，请人工核实");
        protection.check(accountId, goodsId, orderId, detail.buyerUserId);
        var config = selector.select(accountId, goodsId, detail.skuId);
        var context = DeliveryContext.builder().accountId(accountId).xyGoodsId(goodsId).orderId(orderId)
                .sId(sid).buyerUserName(detail.buyerUserName != null ? detail.buyerUserName : buyerName)
                .deliveryConfig(config).build();
        List<String> contents = new ArrayList<>();
        int mode = config.getDeliveryMode() == null ? 1 : config.getDeliveryMode();
        for (int i = 0; i < detail.buyNum; i++) {
            String content = resolver.resolve(mode, context);
            if (content == null || content.isBlank()) throw new BusinessException("发货内容为空或卡密库存不足，整单预留已回滚");
            contents.add(content);
        }
        List<String> images = config.getAutoDeliveryImageUrl() == null ? List.of() :
                Arrays.stream(config.getAutoDeliveryImageUrl().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        Plan plan = new Plan(contents, images, Integer.valueOf(1).equals(config.getAutoConfirmShipment()));
        checkLength(plan, offline);
        if (existing == null) {
            existing = new XianyuGoodsOrder();
            existing.setXianyuAccountId(accountId); existing.setXyGoodsId(goodsId); existing.setOrderId(orderId);
            existing.setPnmId("api_" + orderId); existing.setSid(sid); existing.setState(0); existing.setConfirmState(0);
            orders.insert(existing);
        }
        if (detail.buyerUserId != null) orders.updateBuyerSession(existing.getId(), sid != null ? sid : existing.getSid(), detail.buyerUserId, detail.buyerUserName);
        orders.updateOrderDetail(existing.getId(), detail.buyerUserName, detail.orderCreateTime, detail.paySuccessTime,
                detail.consignTime, detail.skuName, detail.goodsTitle, detail.totalPrice, detail.buyNum);
        try { orders.saveDeliveryPayload(existing.getId(), json.writeValueAsString(plan), plan.text()); }
        catch (Exception e) { throw new BusinessException("保存发货预留内容失败", e); }
        return plan;
    }

    private void checkLength(Plan plan, boolean offline) {
        if (offline && plan.text().length() > 200)
            throw new BusinessException("多件发货内容超过凭证200字上限，请连接WebSocket后重试，避免卡密被截断");
    }
}
