package com.feijimiao.xianyuassistant.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuAutomationConfig;
import org.apache.ibatis.annotations.*;
@Mapper
public interface XianyuAutomationConfigMapper extends BaseMapper<XianyuAutomationConfig> {
    @Update("UPDATE xianyu_automation_config SET polish_enabled=#{polishEnabled},polish_interval_hours=#{polishIntervalHours},blacklist_enabled=#{blacklistEnabled},block_low_credit=#{blockLowCredit},rating_threshold=#{ratingThreshold},block_repeat_account=#{blockRepeatAccount},block_repeat_global=#{blockRepeatGlobal},same_goods_only=#{sameGoodsOnly},block_unconfirmed=#{blockUnconfirmed},unconfirmed_threshold=#{unconfirmedThreshold},excluded_goods=#{excludedGoods} WHERE xianyu_account_id=#{xianyuAccountId}")
    int updateSettings(XianyuAutomationConfig value);
    @Update("UPDATE xianyu_automation_config SET next_polish_at=#{next} WHERE xianyu_account_id=#{id}")
    int reschedule(@Param("id") Long id,@Param("next") long next);
}
