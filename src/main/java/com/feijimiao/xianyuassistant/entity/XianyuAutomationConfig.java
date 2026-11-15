package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@TableName("xianyu_automation_config")
public class XianyuAutomationConfig {
    @TableId(type = IdType.INPUT) @NotNull private Long xianyuAccountId;
    private boolean polishEnabled = false;
    @Min(1) @Max(720) private int polishIntervalHours = 24;
    private Long nextPolishAt = 0L;
    private Long lastPolishAt = 0L;
    private Long runningUntil = 0L;
    private String polishResult = "尚未执行";
    private boolean blacklistEnabled = true;
    private boolean blockLowCredit = false;
    @Min(0) @Max(1000000) private int ratingThreshold = 0;
    private boolean blockRepeatAccount = false;
    private boolean blockRepeatGlobal = false;
    private boolean sameGoodsOnly = true;
    private boolean blockUnconfirmed = false;
    @Min(1) @Max(1000) private int unconfirmedThreshold = 1;
    @Size(max = 2000) private String excludedGoods = "";
}
