package com.feijimiao.xianyuassistant.utils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class SkuCompatibilityTest {
    @Test void nestedBuyerResponseUsesActualValuesAndDeduplicatesExactIds() {
        var skus=ItemDetailUtils.extractSkuList("""
            {"data":{"result":{"itemDO":{"skuList":[],"idleItemSkuList":[
              {"skuId":"00001234567890123456789","propertyList":[{"propertyText":"类型","actualValueText":"月卡","valueText":"旧值"},{"value":"自动续期"}]},
              {"skuId":"00001234567890123456789","name":"重复"},
              {"inventoryId":"another","properties":["周卡"]},
              {"skuId":"0","name":"无效"},{"skuId":"blank"}
            ]}}}}
            """);
        assertEquals(2,skus.size());assertEquals("00001234567890123456789",skus.getFirst().getSkuId());assertEquals("月卡 / 自动续期",skus.getFirst().getValueText());assertEquals("周卡",skus.get(1).getValueText());
    }
    @Test void polishVersionAppliesToUrlPathAsWellAsQuery() {
        String url=ReflectionTestUtils.invokeMethod(XianyuApiUtils.class,"buildUrl","mtop.taobao.idle.item.polish",Map.of("v","2.0"));
        assertTrue(url.contains("/mtop.taobao.idle.item.polish/2.0/"));assertTrue(url.contains("v=2.0"));
    }
    @Test void recommendationsCannotBecomeCurrentProductSkus() {
        var skus=ItemDetailUtils.extractSkuList("""
            {"data":{"itemDO":{"skuList":[]},"recommendations":[{"skuId":"foreign","name":"不属于本商品"}]}}
            """);
        assertTrue(skus.isEmpty());
    }
}
