package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.*;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageData;
import com.feijimiao.xianyuassistant.mapper.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BuyerInteractionServiceTest {
    private final XianyuBuyerInteractionConfigMapper configs = mock(XianyuBuyerInteractionConfigMapper.class);
    private final XianyuBuyerInteractionRecordMapper records = mock(XianyuBuyerInteractionRecordMapper.class);
    private final XianyuAccountMapper accounts = mock(XianyuAccountMapper.class);
    private final XianyuGoodsOrderMapper orders = mock(XianyuGoodsOrderMapper.class);
    private final BuyerInteractionApi api = mock(BuyerInteractionApi.class);
    private final SentMessageSaveService messages = mock(SentMessageSaveService.class);
    private final WebSocketService websocket = mock(WebSocketService.class);
    private BuyerInteractionService service;
    @BeforeEach void setup() {
        service = new BuyerInteractionService(configs,records,accounts,orders,api,messages);
        ReflectionTestUtils.setField(service,"websocket",websocket);
        when(accounts.selectById(anyLong())).thenReturn(new XianyuAccount());
    }
    private XianyuBuyerInteractionConfig config(Long id, boolean rate, boolean flower, boolean thanks) {
        var c = new XianyuBuyerInteractionConfig(); c.setXianyuAccountId(id); c.setAutoRate(rate); c.setAutoFlower(flower); c.setAutoThanks(thanks); c.setEnabledSince(1L); return c;
    }
    private ChatMessageData signal(Long account, String order) {
        var m = new ChatMessageData(); m.setXianyuAccountId(account); m.setContentType(26); m.setMsgContent("[买家确认收货，交易成功]"); m.setOrderId(order); m.setSId("chat@goofish"); m.setSenderUserId("buyer"); return m;
    }
    private Map<?, ?> completed(String id, int sellerRate, boolean flower) {
        return Map.of("commonData",Map.of("orderId",id,"orderStatus","交易成功","sellerRateStatus",sellerRate),
                "buyerInfoVO",Map.of("buyerId","buyer"),"rightVO",Map.of("btnList",flower ? List.of(Map.of("tradeAction","REQUIRE_FLOWER")) : List.of()));
    }
    private XianyuBuyerInteractionRecord task(String action) {
        var t = new XianyuBuyerInteractionRecord(); t.setId(10L); t.setXianyuAccountId(1L); t.setOrderId("order"); t.setAction(action); t.setState(0); t.setAttempts(0); t.setContent("感谢支持"); t.setSid("chat@goofish"); t.setBuyerUserId("buyer");
        when(records.due(anyLong())).thenReturn(List.of(t)); when(records.claim(eq(10L),anyLong())).thenReturn(1);
        return t;
    }
    @Test void defaultsOffAndIndependentByAccount() {
        var c = service.getConfig(1L); assertFalse(c.enabled());
        when(configs.selectById(1L)).thenReturn(config(1L,true,false,false));
        service.onReceipt(signal(1L,"order")); service.onReceipt(signal(2L,"order"));
        var capture = ArgumentCaptor.forClass(XianyuBuyerInteractionRecord.class); verify(records).enqueue(capture.capture());
        assertEquals(1L,capture.getValue().getXianyuAccountId()); assertEquals("RATE",capture.getValue().getAction());
    }
    @Test void thanksIsIndependentFromRateAndFlower() {
        when(configs.selectById(1L)).thenReturn(config(1L,false,false,true)); service.onReceipt(signal(1L,"order"));
        var capture = ArgumentCaptor.forClass(XianyuBuyerInteractionRecord.class); verify(records).enqueue(capture.capture()); assertEquals("THANKS",capture.getValue().getAction());
        verifyNoInteractions(api,websocket);
    }
    @Test void ordinaryChatCannotSpoofReceipt() {
        var m = signal(1L,"order"); m.setContentType(1); service.onReceipt(m);
        verifyNoInteractions(configs,records,api,websocket);
    }
    @Test void oldMessagesDoNotTriggerAfterEnabling() {
        var c = config(1L,true,true,true); c.setEnabledSince(100L); when(configs.selectById(1L)).thenReturn(c);
        var m = signal(1L,"order"); m.setMessageTime(99L); service.onReceipt(m);
        verify(records,never()).enqueue(any());
    }
    @Test void doesNotExecuteBeforePlatformCompletesOrder() {
        var t = task("RATE"); when(configs.selectById(1L)).thenReturn(config(1L,true,true,true)); when(api.completedOrders(1L,"order",1)).thenReturn(List.of());
        service.runDueTasks(); assertEquals(0,t.getState()); assertEquals(1,t.getAttempts()); verify(api,never()).rate(any(),any(),any());
    }
    @Test void alreadyRatedOrderIsSkipped() {
        var t = task("RATE"); when(configs.selectById(1L)).thenReturn(config(1L,true,true,true)); when(api.completedOrders(1L,"order",1)).thenReturn(List.of(completed("order",4,true)));
        service.runDueTasks(); assertEquals(3,t.getState()); verify(api,never()).rate(any(),any(),any());
    }
    @Test void flowerRequiresPlatformAction() {
        var t = task("FLOWER"); when(configs.selectById(1L)).thenReturn(config(1L,true,true,true)); when(api.completedOrders(1L,"order",1)).thenReturn(List.of(completed("order",0,false)));
        service.runDueTasks(); assertEquals(0,t.getState()); verify(api,never()).flower(any(),any());
    }
    @Test void sendsFlowerOnlyForExactCompletedOrder() {
        var t = task("FLOWER"); when(configs.selectById(1L)).thenReturn(config(1L,true,true,true)); when(api.completedOrders(1L,"order",1)).thenReturn(List.of(completed("other",0,true),completed("order",0,true)));
        service.runDueTasks(); assertEquals(1,t.getState()); verify(api).flower(1L,"order");
    }
    @Test void disabledActionIsSkippedAtExecutionTime() {
        var t = task("RATE"); when(configs.selectById(1L)).thenReturn(config(1L,false,true,false)); service.runDueTasks();
        assertEquals(3,t.getState()); verifyNoInteractions(api);
    }
    @Test void thanksWaitsForWebsocketAndUsesVerifiedBuyer() {
        var t = task("THANKS"); t.setBuyerUserId("untrusted"); when(configs.selectById(1L)).thenReturn(config(1L,false,false,true)); when(api.completedOrders(1L,"order",1)).thenReturn(List.of(completed("order",0,false)));
        service.runDueTasks(); assertEquals(0,t.getState()); verify(websocket,never()).sendMessageWithResult(any(),any(),any(),any());
        when(websocket.isConnected(1L)).thenReturn(true); when(websocket.sendMessageWithResult(1L,"chat","buyer","感谢支持")).thenReturn(true);
        service.runDueTasks(); assertEquals(1,t.getState()); verify(messages).saveAiAssistantReply(1L,"chat","buyer","感谢支持",null);
    }
    @Test void uncertainWriteFailureIsVisibleAndNotAutomaticallyRepeated() {
        var t = task("RATE"); when(configs.selectById(1L)).thenReturn(config(1L,true,false,false)); when(api.completedOrders(1L,"order",1)).thenReturn(List.of(completed("order",0,false)));
        doThrow(new RuntimeException("timeout")).when(api).rate(any(),any(),any()); service.runDueTasks(); assertEquals(-1,t.getState()); assertTrue(t.getResultMessage().contains("timeout"));
    }
    @Test void refundAndWrongOrderNeverTriggerWrites() {
        var t = task("RATE"); when(configs.selectById(1L)).thenReturn(config(1L,true,false,false));
        when(api.completedOrders(1L,"order",1)).thenReturn(List.of(Map.of("commonData",Map.of("orderId","order","orderStatus","交易成功","inRefund",true)),completed("other",0,true)));
        service.runDueTasks(); assertEquals(0,t.getState()); verify(api,never()).rate(any(),any(),any());
    }
}
