package com.feijimiao.xianyuassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.entity.*;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageData;
import com.feijimiao.xianyuassistant.exception.BusinessException;
import com.feijimiao.xianyuassistant.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.feijimiao.xianyuassistant.service.BuyerInteractionApi.*;

@Slf4j
@Service
public class BuyerInteractionService {
    private final XianyuBuyerInteractionConfigMapper configs;
    private final XianyuBuyerInteractionRecordMapper records;
    private final XianyuAccountMapper accounts;
    private final XianyuGoodsOrderMapper orders;
    private final BuyerInteractionApi api;
    private final SentMessageSaveService messages;
    @Lazy @Autowired private WebSocketService websocket;
    private final Map<Long, Long> lastScan = new ConcurrentHashMap<>();
    private final Object[] accountLocks = new Object[64];

    public BuyerInteractionService(XianyuBuyerInteractionConfigMapper configs, XianyuBuyerInteractionRecordMapper records,
                                   XianyuAccountMapper accounts, XianyuGoodsOrderMapper orders, BuyerInteractionApi api,
                                   SentMessageSaveService messages) {
        this.configs = configs; this.records = records; this.accounts = accounts; this.orders = orders; this.api = api; this.messages = messages;
        for (int i = 0; i < accountLocks.length; i++) accountLocks[i] = new Object();
    }
    private Object lock(Long id) { return accountLocks[Math.floorMod(id.hashCode(), accountLocks.length)]; }
    public XianyuBuyerInteractionConfig getConfig(Long accountId) {
        if (accounts.selectById(accountId) == null) throw new BusinessException("账号不存在");
        var config = configs.selectById(accountId);
        if (config == null) { config = new XianyuBuyerInteractionConfig(); config.setXianyuAccountId(accountId); }
        return config;
    }
    public XianyuBuyerInteractionConfig saveConfig(XianyuBuyerInteractionConfig config) {
        synchronized (lock(config.getXianyuAccountId())) {
            var previous = getConfig(config.getXianyuAccountId());
            config.setEnabledSince(previous.getEnabledSince());
            if (config.enabled() && !previous.enabled()) config.setEnabledSince(System.currentTimeMillis());
            if (configs.selectById(config.getXianyuAccountId()) == null) configs.insert(config); else configs.updateById(config);
            lastScan.remove(config.getXianyuAccountId());
            return config;
        }
    }
    public Map<String, Object> records(Long accountId, int page, int size) {
        getConfig(accountId);
        int safeSize = Math.max(1, Math.min(size, 100)); int safePage = Math.max(1, Math.min(page, 10000));
        var query = new LambdaQueryWrapper<XianyuBuyerInteractionRecord>().eq(XianyuBuyerInteractionRecord::getXianyuAccountId, accountId);
        long total = records.selectCount(query);
        query.orderByDesc(XianyuBuyerInteractionRecord::getId).last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize);
        return Map.of("records", records.selectList(query), "total", total);
    }
    public void retry(Long accountId, Long recordId) {
        var config = getConfig(accountId);
        var record = records.selectById(recordId);
        if (record == null || !accountId.equals(record.getXianyuAccountId())) throw new BusinessException("互动记录不存在");
        if (!enabled(config, record.getAction())) throw new BusinessException("请先开启对应互动开关");
        if (records.retry(recordId, accountId, System.currentTimeMillis()) == 0) throw new BusinessException("仅失败的互动记录可重试");
    }
    public static boolean receiptSignal(ChatMessageData message) {
        if (Integer.valueOf(1).equals(message.getContentType())) return false;
        return Set.of("[买家确认收货，交易成功]", "[交易成功]", "交易成功", "快给ta一个评价吧~", "快给ta一个评价吧～")
                .contains(message.getMsgContent() == null ? "" : message.getMsgContent().trim());
    }
    public void onReceipt(ChatMessageData message) {
        if (!receiptSignal(message) || message.getXianyuAccountId() == null) return;
        synchronized (lock(message.getXianyuAccountId())) {
            var config = getConfig(message.getXianyuAccountId());
            if (!config.enabled()) return;
            if (message.getMessageTime() != null && config.getEnabledSince() != null && message.getMessageTime() < config.getEnabledSince()) return;
            // 消息可能缺少订单号；通过卖家已完成订单核实归属后才创建任务。
            if (message.getOrderId() != null && !message.getOrderId().isBlank()) {
                enqueue(config, message.getOrderId(), message.getSId(), message.getSenderUserId(), message.getXyGoodsId());
            } else {
                discover(config, message);
            }
        }
    }
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void scanCompletedOrders() {
        for (var config : configs.selectList(null)) {
            if (!config.enabled()) continue;
            synchronized (lock(config.getXianyuAccountId())) {
                long now = System.currentTimeMillis();
                if (now - lastScan.getOrDefault(config.getXianyuAccountId(), 0L) < config.getPollIntervalSeconds() * 1000L) continue;
                try { discover(config, null); lastScan.put(config.getXianyuAccountId(), now); }
                catch (Exception e) { log.warn("【账号{}】买家互动轮询失败: {}", config.getXianyuAccountId(), e.getMessage()); }
            }
        }
    }
    private void discover(XianyuBuyerInteractionConfig config, ChatMessageData signal) {
        Long accountId = config.getXianyuAccountId();
        for (int page = 1; page <= 10; page++) {
            var batch = api.completedOrders(accountId, signal != null ? signal.getOrderId() : null, page);
            for (var item : batch) {
                var common = map(item.get("commonData")); var buyer = map(item.get("buyerInfoVO"));
                if (!completed(item)) continue;
                String orderId = text(common.get("orderId")); if (orderId.isBlank()) continue;
                String buyerId = text(buyer.get("buyerId")); if (buyerId.isBlank()) buyerId = text(buyer.get("userId"));
                var local = orders.selectByAccountIdAndOrderId(accountId, orderId);
                boolean signalMatch = signal != null && (orderId.equals(signal.getOrderId()) ||
                        (signal.getOrderId() == null && local != null && signal.getSId() != null && signal.getSId().equals(local.getSid())) ||
                        (signal.getOrderId() == null && !buyerId.isBlank() && buyerId.equals(signal.getSenderUserId())));
                Long finish = finishTime(common.get("finishTime"));
                if (!signalMatch && (finish == null || config.getEnabledSince() == null || finish < config.getEnabledSince())) continue;
                if (signal != null && !signalMatch) continue;
                if (signal != null && signal.getOrderId() == null && (finish == null || config.getEnabledSince() == null || finish < config.getEnabledSince())) continue;
                // 过滤启用开关前的历史推送，防止刚连接时补发旧订单互动。
                if (signalMatch && signal.getMessageTime() != null && config.getEnabledSince() != null && signal.getMessageTime() < config.getEnabledSince()) continue;
                String sid = signalMatch && signal.getSId() != null ? signal.getSId() : local != null ? local.getSid() : null;
                if (buyerId.isBlank() && local != null) buyerId = local.getBuyerUserId();
                enqueue(config, orderId, sid, buyerId, text(common.get("itemId")));
            }
            if (batch.size() < 50 || (signal != null && signal.getOrderId() != null)) break;
        }
    }
    private void enqueue(XianyuBuyerInteractionConfig config, String orderId, String sid, String buyerId, String goodsId) {
        for (String action : List.of("RATE", "FLOWER", "THANKS")) {
            if (!enabled(config, action)) continue;
            var record = new XianyuBuyerInteractionRecord();
            record.setXianyuAccountId(config.getXianyuAccountId()); record.setOrderId(orderId); record.setAction(action);
            record.setSid(sid); record.setBuyerUserId(buyerId); record.setXyGoodsId(goodsId);
            record.setContent("RATE".equals(action) ? config.getRateContent() : "THANKS".equals(action) ? config.getThanksContent() : "索要小红花");
            record.setNextRunAt(System.currentTimeMillis() + ("THANKS".equals(action) ? 0 : 20000));
            record.setUpdatedAt(System.currentTimeMillis()); records.enqueue(record);
        }
    }
    static Long finishTime(Object value) {
        String text = text(value); if (text.isBlank()) return null;
        try { long number = Long.parseLong(text); return number < 100000000000L ? number * 1000 : number; } catch (Exception ignored) {}
        try { return LocalDateTime.parse(text.replace('T', ' '), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(); }
        catch (Exception ignored) { return null; }
    }
    public static boolean completed(Map<?, ?> item) {
        var common = map(item.get("commonData"));
        String status = text(common.get("orderStatus"));
        return Set.of("交易成功", "交易完成", "已完成").contains(status)
                && !Boolean.parseBoolean(text(common.get("inRefund"))) && !"1".equals(text(common.get("inRefund")));
    }
    private boolean enabled(XianyuBuyerInteractionConfig config, String action) {
        return switch (action) { case "RATE" -> Boolean.TRUE.equals(config.getAutoRate()); case "FLOWER" -> Boolean.TRUE.equals(config.getAutoFlower()); case "THANKS" -> Boolean.TRUE.equals(config.getAutoThanks()); default -> false; };
    }
    @Scheduled(fixedDelay = 5000, initialDelay = 30000)
    public void runDueTasks() {
        long now = System.currentTimeMillis(); records.recoverInterrupted(now - 600000);
        for (var record : records.due(now)) {
            synchronized (lock(record.getXianyuAccountId())) {
                if (records.claim(record.getId(), System.currentTimeMillis()) != 1) continue;
                try { execute(record); }
                catch (Exception e) { finish(record, -1, "执行失败或结果未知: " + e.getMessage()); }
            }
        }
    }
    private void execute(XianyuBuyerInteractionRecord record) {
        var config = getConfig(record.getXianyuAccountId());
        if (!enabled(config, record.getAction())) { finish(record, 3, "对应互动开关已关闭"); return; }
        var target = api.completedOrders(record.getXianyuAccountId(), record.getOrderId(), 1).stream()
                .filter(item -> record.getOrderId().equals(text(map(item.get("commonData")).get("orderId"))) && completed(item)).findFirst();
        if (target.isEmpty()) { postpone(record, "等待平台确认交易成功"); return; }
        var item = target.get();
        String verifiedBuyer = text(map(item.get("buyerInfoVO")).get("buyerId"));
        if (verifiedBuyer.isBlank()) verifiedBuyer = text(map(item.get("buyerInfoVO")).get("userId"));
        if (!verifiedBuyer.isBlank()) record.setBuyerUserId(verifiedBuyer);
        switch (record.getAction()) {
            case "RATE" -> {
                if ("4".equals(text(map(item.get("commonData")).get("sellerRateStatus")))) { finish(record, 3, "平台显示已评价"); return; }
                api.rate(record.getXianyuAccountId(), record.getOrderId(), record.getContent());
            }
            case "FLOWER" -> {
                boolean available = list(map(item.get("rightVO")).get("btnList")).stream().anyMatch(btn -> "REQUIRE_FLOWER".equals(text(map(btn).get("tradeAction"))));
                if (!available) { postpone(record, "平台尚未提供求花操作，可能已求花"); return; }
                api.flower(record.getXianyuAccountId(), record.getOrderId());
            }
            case "THANKS" -> {
                if (record.getSid() == null || record.getSid().isBlank()) {
                    var local = orders.selectByAccountIdAndOrderId(record.getXianyuAccountId(), record.getOrderId());
                    if (local != null) { record.setSid(local.getSid()); record.setBuyerUserId(local.getBuyerUserId()); }
                }
                if (record.getSid() == null || record.getBuyerUserId() == null || record.getBuyerUserId().isBlank() || !websocket.isConnected(record.getXianyuAccountId())) {
                    postpone(record, "等待买家会话或WebSocket连接"); return;
                }
                if (!websocket.sendMessageWithResult(record.getXianyuAccountId(), record.getSid().replace("@goofish", ""), record.getBuyerUserId(), record.getContent()))
                    throw new BusinessException("致谢消息未获发送确认，请核实聊天记录");
                messages.saveAiAssistantReply(record.getXianyuAccountId(), record.getSid().replace("@goofish", ""), record.getBuyerUserId(), record.getContent(), record.getXyGoodsId());
            }
            default -> throw new BusinessException("未知互动类型");
        }
        finish(record, 1, "执行成功");
    }
    private void postpone(XianyuBuyerInteractionRecord record, String reason) {
        int attempts = record.getAttempts() == null ? 1 : record.getAttempts() + 1;
        record.setAttempts(attempts); record.setNextRunAt(System.currentTimeMillis() + 30000);
        finish(record, attempts >= 20 ? -1 : 0, reason);
    }
    private void finish(XianyuBuyerInteractionRecord record, int state, String reason) {
        record.setState(state); record.setResultMessage(reason); record.setUpdatedAt(System.currentTimeMillis()); records.updateById(record);
    }
}
