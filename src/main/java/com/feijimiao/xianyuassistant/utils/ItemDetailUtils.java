package com.feijimiao.xianyuassistant.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsSku;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsSkuProperty;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class ItemDetailUtils {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static String extractDescFromDetailJson(String detailJson) {
        if (detailJson == null || detailJson.isEmpty()) {
            return detailJson;
        }
        
        try {
            JsonNode rootNode = objectMapper.readTree(detailJson);
            
            JsonNode itemDONode = rootNode.get("itemDO");
            if (itemDONode == null || itemDONode.isNull()) {
                JsonNode dataNode = rootNode.get("data");
                if (dataNode != null && !dataNode.isNull()) {
                    itemDONode = dataNode.get("itemDO");
                }
            }
            
            if (itemDONode != null && !itemDONode.isNull()) {
                JsonNode descNode = itemDONode.get("desc");
                if (descNode != null && !descNode.isNull()) {
                    String desc = descNode.asText();
                    log.info("成功提取desc字段，长度: {}", desc.length());
                    return desc;
                } else {
                    log.warn("itemDO中未找到desc字段");
                }
            } else {
                log.warn("未找到itemDO字段");
            }
            
            log.warn("无法提取desc字段，返回原始JSON，长度: {}", detailJson.length());
            return detailJson;
        } catch (Exception e) {
            log.error("解析商品详情JSON失败，返回原始JSON: {}", e.getMessage());
            return detailJson;
        }
    }

    /** 兼容卖家和买家接口；ID 保留原字符串，不转换为浮点数或 long。 */
    public static List<XianyuGoodsSku> extractSkuList(String detailJson) {
        java.util.LinkedHashMap<String, XianyuGoodsSku> result = new java.util.LinkedHashMap<>();
        if (detailJson == null || detailJson.isBlank()) return new ArrayList<>();
        try { collectSkus(objectMapper.readTree(detailJson), 0, result); }
        catch (Exception e) { log.warn("解析SKU失败: {}", e.getMessage()); }
        return new ArrayList<>(result.values());
    }

    private static String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isValueNode() && !value.isNull() && !value.asText().isBlank()) return value.asText().trim();
        }
        return "";
    }

    private static void collectSkus(JsonNode node, int depth, java.util.Map<String, XianyuGoodsSku> result) {
        if (node == null || depth > 8 || result.size() >= 1000) return;
        if (node.isArray()) {
            for (JsonNode row : node) {
                String id = firstText(row, "skuId", "inventoryId");
                if (!id.isEmpty() && !"0".equals(id) && !"-1".equals(id)) {
                    JsonNode props = row.path("propertyList");
                    if (!props.isArray() || props.isEmpty()) props = row.path("properties");
                    List<String> names = new ArrayList<>(), labels = new ArrayList<>();
                    if (props.isArray()) for (JsonNode prop : props) {
                        String value = prop.isTextual() ? prop.asText().trim() : firstText(prop, "actualValueText", "valueText", "value");
                        String label = firstText(prop, "propertyText", "name");
                        if (!value.isEmpty()) { names.add(value); labels.add(label.isEmpty() ? value : label + ":" + value); }
                    }
                    String name = names.isEmpty() ? firstText(row, "name", "skuDesc", "valueText") : String.join(" / ", names);
                    if (!name.isEmpty()) {
                        XianyuGoodsSku sku = new XianyuGoodsSku();
                        sku.setSkuId(id); sku.setValueText(name);
                        sku.setPropertyText(labels.isEmpty() ? name : String.join(" / ", labels));
                        if (props.isArray() && !props.isEmpty()) {
                            JsonNode first = props.get(0);
                            if (first.path("propertyId").canConvertToInt()) sku.setPropertyId(first.path("propertyId").asInt());
                            if (first.path("valueId").canConvertToInt()) sku.setValueId(first.path("valueId").asInt());
                            if (first.path("propertySortOrder").canConvertToInt()) sku.setPropertySortOrder(first.path("propertySortOrder").asInt());
                            if (first.path("valueSortOrder").canConvertToInt()) sku.setValueSortOrder(first.path("valueSortOrder").asInt());
                        }
                        if (row.path("price").canConvertToInt()) sku.setPrice(row.path("price").asInt());
                        if (row.path("quantity").canConvertToInt()) sku.setQuantity(row.path("quantity").asInt());
                        result.putIfAbsent(id, sku);
                    }
                }
                collectSkus(row, depth + 1, result);
            }
        } else if (node.isObject()) {
            // 商品详情同时可能包含推荐商品，仅解析当前商品的 itemDO / 明确的 SKU 数组。
            if (node.path("itemDO").isObject()) { collectSkus(node.get("itemDO"), depth + 1, result); return; }
            if (node.path("skuList").isArray() || node.path("idleItemSkuList").isArray()) {
                collectSkus(node.get("skuList"), depth + 1, result);
                collectSkus(node.get("idleItemSkuList"), depth + 1, result);
                return;
            }
            node.elements().forEachRemaining(child -> collectSkus(child, depth + 1, result));
        }
    }

    public static List<XianyuGoodsSkuProperty> extractSkuPropertyList(String detailJson) {
        List<XianyuGoodsSkuProperty> result = new ArrayList<>();
        if (detailJson == null || detailJson.isEmpty()) {
            return result;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(detailJson);

            JsonNode itemDONode = rootNode.get("itemDO");
            if (itemDONode == null || itemDONode.isNull()) {
                JsonNode dataNode = rootNode.get("data");
                if (dataNode != null && !dataNode.isNull()) {
                    itemDONode = dataNode.get("itemDO");
                }
            }

            if (itemDONode == null || itemDONode.isNull()) {
                return result;
            }

            JsonNode skuListNode = itemDONode.get("skuList");
            if (skuListNode == null || !skuListNode.isArray()) {
                skuListNode = itemDONode.get("idleItemSkuList");
            }

            if (skuListNode == null || !skuListNode.isArray() || skuListNode.size() == 0) {
                return result;
            }

            Set<String> seen = new LinkedHashSet<>();
            for (JsonNode skuNode : skuListNode) {
                JsonNode propertyListNode = skuNode.get("propertyList");
                if (propertyListNode == null || !propertyListNode.isArray()) continue;
                for (JsonNode propNode : propertyListNode) {
                    if (!propNode.has("propertyId") || !propNode.has("valueId")) continue;
                    int propertyId = propNode.get("propertyId").asInt();
                    int valueId = propNode.get("valueId").asInt();
                    String key = propertyId + ":" + valueId;
                    if (seen.contains(key)) continue;
                    seen.add(key);

                    XianyuGoodsSkuProperty prop = new XianyuGoodsSkuProperty();
                    prop.setPropertyId(propertyId);
                    prop.setPropertyText(propNode.has("propertyText") ? propNode.get("propertyText").asText() : "");
                    prop.setPropertySortOrder(propNode.has("propertySortOrder") ? propNode.get("propertySortOrder").asInt() : 0);
                    prop.setValueId(valueId);
                    prop.setValueText(firstText(propNode, "actualValueText", "valueText", "value"));
                    prop.setValueSortOrder(propNode.has("valueSortOrder") ? propNode.get("valueSortOrder").asInt() : 0);
                    result.add(prop);
                }
            }

            log.info("解析SKU属性维度成功，数量: {}", result.size());
            return result;
        } catch (Exception e) {
            log.error("解析SKU属性维度失败: {}", e.getMessage());
            return result;
        }
    }
}
