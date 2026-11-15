package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class KamiConfigReqDTO {

    private Long id;

    @NotNull(message = "闲鱼账号ID不能为空")
    private Long xianyuAccountId;

    /** PRIVATE:账号私有；SHARED:所有账号通用。旧仓库默认为私有。 */
    private String scope;

    private String aliasName;

    private Integer alertEnabled;

    private Integer alertThresholdType;

    private Integer alertThresholdValue;

    private String alertEmail;
}
