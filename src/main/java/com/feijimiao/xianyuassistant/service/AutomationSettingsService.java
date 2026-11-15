package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuAutomationConfig;
import com.feijimiao.xianyuassistant.mapper.*;
import com.feijimiao.xianyuassistant.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.constraints.*;
import java.util.*;

@Service
public class AutomationSettingsService {
    private final XianyuAutomationConfigMapper configs;
    private final XianyuAccountMapper accounts;
    private final JdbcTemplate db;
    public AutomationSettingsService(XianyuAutomationConfigMapper configs, XianyuAccountMapper accounts, JdbcTemplate db) {
        this.configs = configs; this.accounts = accounts; this.db = db;
    }
    public XianyuAutomationConfig get(Long id) {
        if (id == null || accounts.selectById(id) == null) throw new BusinessException("账号不存在");
        var value = configs.selectById(id);
        if (value == null) { value = new XianyuAutomationConfig(); value.setXianyuAccountId(id); }
        return value;
    }
    @Transactional
    public XianyuAutomationConfig save(XianyuAutomationConfig value) {
        var old = get(value.getXianyuAccountId());
        if (value.getPolishIntervalHours() < 1 || value.getPolishIntervalHours() > 720 || value.getUnconfirmedThreshold() < 1 || value.getRatingThreshold() < 0)
            throw new BusinessException("设置参数超出允许范围");
        if (configs.selectById(value.getXianyuAccountId()) == null) configs.insert(old);
        // 仅更新用户可编辑字段，避免覆盖后台运行进度和租约。
        value.setExcludedGoods(value.getExcludedGoods() == null ? "" : value.getExcludedGoods().trim());
        configs.updateSettings(value);
        if (value.isPolishEnabled() && (!old.isPolishEnabled() || old.getPolishIntervalHours() != value.getPolishIntervalHours())) {
            long next = old.getLastPolishAt() > 0 ? Math.max(System.currentTimeMillis(), old.getLastPolishAt() + value.getPolishIntervalHours() * 3600000L) : System.currentTimeMillis();
            configs.reschedule(value.getXianyuAccountId(), next);
        }
        return get(value.getXianyuAccountId());
    }
    public record BlacklistEntry(Long id, Long xianyuAccountId, @NotBlank @Pattern(regexp="[0-9]{1,30}") String buyerId,
                                 @Size(max=100) String buyerName, @Size(max=200) String reason,
                                 @Pattern(regexp="[0-9]{0,30}") String goodsId, boolean enabled) {}
    public List<Map<String,Object>> blacklist(Long accountId) {
        get(accountId);
        return db.queryForList("SELECT * FROM xianyu_buyer_blacklist WHERE xianyu_account_id IS NULL OR xianyu_account_id=? ORDER BY id DESC LIMIT 1000", accountId);
    }
    public void saveBlacklist(BlacklistEntry entry) {
        if (entry.xianyuAccountId() != null) get(entry.xianyuAccountId());
        String goods = entry.goodsId() == null ? "" : entry.goodsId().trim();
        if (entry.id() == null) db.update("INSERT INTO xianyu_buyer_blacklist(xianyu_account_id,buyer_id,buyer_name,reason,goods_id,enabled) VALUES (?,?,?,?,?,?)",
                new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.BIGINT, entry.xianyuAccountId()), entry.buyerId(), Objects.toString(entry.buyerName(), ""), Objects.toString(entry.reason(), ""), goods, entry.enabled());
        else if (db.update("UPDATE xianyu_buyer_blacklist SET xianyu_account_id=?,buyer_id=?,buyer_name=?,reason=?,goods_id=?,enabled=? WHERE id=?",
                new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.BIGINT, entry.xianyuAccountId()), entry.buyerId(), Objects.toString(entry.buyerName(), ""), Objects.toString(entry.reason(), ""), goods, entry.enabled(), entry.id()) == 0) throw new BusinessException("黑名单记录不存在");
    }
    public void deleteBlacklist(Long id) { db.update("DELETE FROM xianyu_buyer_blacklist WHERE id=?", id); }
}
