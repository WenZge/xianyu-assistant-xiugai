package com.feijimiao.xianyuassistant.service.delivery;

import org.springframework.stereotype.Component;
import java.util.Objects;

/** 固定大小的锁池，让消息推送、轮询和手动重试串行处理同一订单。 */
@Component
public class OrderDeliveryLock {
    private final Object[] locks = new Object[128];
    public OrderDeliveryLock() { for (int i = 0; i < locks.length; i++) locks[i] = new Object(); }
    public Object forOrder(Long accountId, String orderId) {
        return locks[Math.floorMod(Objects.hash(accountId, orderId), locks.length)];
    }
}
