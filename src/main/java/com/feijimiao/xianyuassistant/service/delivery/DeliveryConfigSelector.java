package com.feijimiao.xianyuassistant.service.delivery;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.exception.BusinessException;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import org.springframework.stereotype.Component;
import java.util.List;

/** 在线与离线共用的规格配置选择；存在规格规则时必须精确命中。 */
@Component
public class DeliveryConfigSelector {
    private final XianyuGoodsAutoDeliveryConfigMapper mapper;
    public DeliveryConfigSelector(XianyuGoodsAutoDeliveryConfigMapper mapper) { this.mapper = mapper; }

    public XianyuGoodsAutoDeliveryConfig select(Long accountId, String goodsId, String skuId) {
        List<XianyuGoodsAutoDeliveryConfig> configs = mapper.findByAccountIdAndGoodsId(accountId, goodsId);
        boolean hasSkuRules = configs.stream().anyMatch(c -> c.getSkuId() != null && !c.getSkuId().isBlank());
        if (skuId != null && !skuId.isBlank()) {
            var exact = configs.stream().filter(c -> skuId.equals(c.getSkuId())).findFirst();
            if (exact.isPresent()) return exact.get();
        }
        if (hasSkuRules) throw new BusinessException("订单规格未识别或未配置发货，请同步商品规格并配置对应规格后重试");
        return configs.stream().filter(c -> c.getSkuId() == null || c.getSkuId().isBlank()).findFirst()
                .orElseThrow(() -> new BusinessException("商品无发货配置，请先配置自动发货"));
    }
}
