package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KamiConfigRespDTO {

    private Long id;

    private Long xianyuAccountId;

    /** PRIVATE:账号私有；SHARED:所有账号通用。旧仓库默认为私有。 */
    private String scope;

    private String aliasName;

    private Integer alertEnabled;

    private Integer alertThresholdType;

    private Integer alertThresholdValue;

    private String alertEmail;

    private Integer totalCount;

    private Integer usedCount;

    private Integer availableCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
