package com.feijimiao.xianyuassistant.event.chatMessageEvent.lister;

import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageReceivedEvent;
import com.feijimiao.xianyuassistant.service.BuyerInteractionService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ChatMessageEventBuyerInteractionListener {
    private final BuyerInteractionService service;
    public ChatMessageEventBuyerInteractionListener(BuyerInteractionService service) { this.service = service; }
    @Async @EventListener
    public void onMessage(ChatMessageReceivedEvent event) {
        try { service.onReceipt(event.getMessageData()); }
        catch (Exception e) { log.warn("【账号{}】收货互动触发失败，等待轮询补偿: {}", event.getMessageData().getXianyuAccountId(), e.getMessage()); }
    }
}
