package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("xianyu_buyer_interaction_record")
public class XianyuBuyerInteractionRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private Long xianyuAccountId;
    private String orderId;
    private String action;
    private String sid;
    private String buyerUserId;
    private String xyGoodsId;
    private String content;
    private Integer state;
    private Integer attempts;
    private String resultMessage;
    private Long nextRunAt;
    private Long updatedAt;
}
