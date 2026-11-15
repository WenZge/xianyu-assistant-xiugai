package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@TableName("xianyu_buyer_interaction_config")
public class XianyuBuyerInteractionConfig {
    @TableId(type = IdType.INPUT) @NotNull
    private Long xianyuAccountId;
    @NotNull private Boolean autoRate = false;
    @NotNull private Boolean autoFlower = false;
    @NotNull private Boolean autoThanks = false;
    @NotBlank @Size(max = 200) private String rateContent = "感谢惠顾，欢迎下次光临！";
    @NotBlank @Size(max = 200) private String thanksContent = "亲，感谢支持！有任何问题随时找我~";
    @NotNull @Min(300) @Max(86400) private Integer pollIntervalSeconds = 7200;
    private Long enabledSince;
    public boolean enabled() { return Boolean.TRUE.equals(autoRate) || Boolean.TRUE.equals(autoFlower) || Boolean.TRUE.equals(autoThanks); }
}
