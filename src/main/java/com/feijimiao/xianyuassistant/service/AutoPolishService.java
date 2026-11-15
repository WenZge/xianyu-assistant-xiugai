package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.exception.BusinessException;
import com.feijimiao.xianyuassistant.utils.XianyuApiCallUtils;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class AutoPolishService {
    private final AutomationSettingsService settings;
    private final AccountService accounts;
    private final XianyuApiCallUtils api;
    private final JdbcTemplate db;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Set<Long> queued = ConcurrentHashMap.newKeySet();
    public AutoPolishService(AutomationSettingsService settings, AccountService accounts, XianyuApiCallUtils api, JdbcTemplate db) {
        this.settings=settings; this.accounts=accounts; this.api=api; this.db=db;
    }
    @PreDestroy public void close() { workers.shutdownNow(); }
    @Scheduled(fixedDelay=60000, initialDelay=30000)
    public void scan() {
        long now=System.currentTimeMillis();
        for (Long id : db.queryForList("SELECT c.xianyu_account_id FROM xianyu_automation_config c JOIN xianyu_account a ON a.id=c.xianyu_account_id WHERE c.polish_enabled=1 AND a.status=1 AND c.next_polish_at<=? AND c.running_until<=?", Long.class, now, now)) {
            try { enqueue(id, false); } catch (Exception e) { log.warn("账号{}擦亮排程失败: {}", id, e.getMessage()); }
        }
    }
    public void enqueue(Long id, boolean manual) {
        var config=settings.get(id);
        if (!config.isPolishEnabled()) throw new BusinessException("请先开启自动擦亮并保存");
        if (manual && config.getLastPolishAt() > System.currentTimeMillis()-60000) throw new BusinessException("距离上次擦亮不足一分钟，请稍后再试");
        if (!queued.add(id)) { if (manual) throw new BusinessException("该账号已有擦亮任务"); return; }
        try { workers.execute(() -> { try { run(id, manual); } catch (Exception e) { log.warn("账号{}擦亮失败: {}", id, e.getMessage()); } finally { queued.remove(id); } }); }
        catch (RuntimeException e) { queued.remove(id); throw e; }
    }
    void run(Long id, boolean manual) {
        long now=System.currentTimeMillis();
        int claimed=db.update("UPDATE xianyu_automation_config SET running_until=?,last_polish_at=?,polish_result='执行中' WHERE xianyu_account_id=? AND polish_enabled=1 AND running_until<=? AND (?=1 OR next_polish_at<=?)", now+300000, now, id, now, manual, now);
        if (claimed==0) return;
        int succeeded=0, failed=0; String note="";
        try {
            var items=db.queryForList("SELECT xy_good_id FROM xianyu_goods WHERE xianyu_account_id=? AND status=0 ORDER BY id", String.class, id);
            if (items.isEmpty()) note="没有在售商品，请先在商品管理同步";
            for (String item : items) {
                if (Thread.currentThread().isInterrupted()) { note="服务停止，任务已中断"; break; }
                if (!settings.get(id).isPolishEnabled()) { note="开关已关闭，停止剩余任务"; break; }
                if (db.queryForObject("SELECT count(*) FROM xianyu_account WHERE id=? AND status=1", Integer.class, id)==0) { note="账号不可用，任务停止"; break; }
                db.update("UPDATE xianyu_automation_config SET running_until=? WHERE xianyu_account_id=?", System.currentTimeMillis()+300000, id);
                String cookie=accounts.getCookieByAccountId(id);
                if (cookie==null || cookie.isBlank()) throw new BusinessException("账号Cookie不可用");
                var result=api.callApiWithRetry(id, "mtop.taobao.idle.item.polish", Map.of("itemId",item), cookie,
                    Map.of("Origin","https://www.goofish.com","Referer","https://www.goofish.com/"), Map.of("v","2.0"));
                boolean success=result.isSuccess();
                if (success && Boolean.FALSE.equals(BuyerInteractionApi.map(result.extractData()).get("success"))) success=false;
                String message=success ? "擦亮成功" : Objects.toString(result.getErrorMessage(), "平台拒绝擦亮");
                if (message.length() > 1000) message=message.substring(0,1000);
                db.update("INSERT INTO xianyu_polish_record(xianyu_account_id,goods_id,success,message,created_at) VALUES (?,?,?,?,?)", id,item,success,message,System.currentTimeMillis());
                if (success) succeeded++; else {
                    failed++;
                    // 遇到风控、令牌或接口错误，停止整批，等待下一周期或人工处理。
                    note="平台请求失败，已停止本轮："+message;
                    break;
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); note="任务中断"; break; }
            }
        } catch (Exception e) { note="执行失败："+e.getMessage(); failed++; }
        finally {
            var rows=db.queryForList("SELECT polish_interval_hours FROM xianyu_automation_config WHERE xianyu_account_id=?", id);
            if (!rows.isEmpty()) {
                int hours=((Number)rows.get(0).get("polish_interval_hours")).intValue();
                db.update("UPDATE xianyu_automation_config SET running_until=0,next_polish_at=?,polish_result=? WHERE xianyu_account_id=?",
                    System.currentTimeMillis()+hours*3600000L, "成功 "+succeeded+" 件，失败 "+failed+" 件。"+note,id);
            }
            db.update("DELETE FROM xianyu_polish_record WHERE xianyu_account_id=? AND id NOT IN (SELECT id FROM xianyu_polish_record WHERE xianyu_account_id=? ORDER BY id DESC LIMIT 2000)", id,id);
        }
    }
    public Map<String,Object> records(Long id, int page) {
        settings.get(id); int offset=(Math.max(1,Math.min(page,100))-1)*20;
        return Map.of("records",db.queryForList("SELECT * FROM xianyu_polish_record WHERE xianyu_account_id=? ORDER BY id DESC LIMIT 20 OFFSET ?",id,offset),
            "total",db.queryForObject("SELECT count(*) FROM xianyu_polish_record WHERE xianyu_account_id=?",Integer.class,id));
    }
}
