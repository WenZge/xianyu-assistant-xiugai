package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryConfigReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryConfigRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryConfigQueryReqDTO;
import com.feijimiao.xianyuassistant.service.AutoDeliveryConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AutoDeliveryConfigServiceImpl implements AutoDeliveryConfigService {
    
    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;
    
    @Autowired
    private com.feijimiao.xianyuassistant.service.GoodsSkuService goodsSkuService;
    @Autowired
    private com.feijimiao.xianyuassistant.service.KamiConfigService kamiConfigService;

    @Override
    public ResultObject<AutoDeliveryConfigRespDTO> saveOrUpdateConfig(AutoDeliveryConfigReqDTO reqDTO) {
        try {
            String skuId = reqDTO.getSkuId() == null || reqDTO.getSkuId().isBlank() ? null : reqDTO.getSkuId().trim();
            reqDTO.setSkuId(skuId);
            if (reqDTO.getDeliveryMode() == null || (reqDTO.getDeliveryMode() != 1 && reqDTO.getDeliveryMode() != 2)) return ResultObject.failed("请选择文本或卡密发货");
            if (reqDTO.getDeliveryMode() == 1 && (reqDTO.getAutoDeliveryContent() == null || reqDTO.getAutoDeliveryContent().isBlank())) return ResultObject.failed("发货文本不能为空");
            if (reqDTO.getDeliveryMode() == 2 && (reqDTO.getKamiConfigIds() == null || reqDTO.getKamiConfigIds().isBlank())) return ResultObject.failed("请绑定卡密仓库");
            if (reqDTO.getAutoConfirmShipment() != null && reqDTO.getAutoConfirmShipment() != 0 && reqDTO.getAutoConfirmShipment() != 1)
                return ResultObject.failed("自动确认发货开关只能为0或1");
            if (skuId != null && goodsSkuService.listByXyGoodsId(reqDTO.getXyGoodsId()).stream()
                    .noneMatch(s -> skuId.equals(s.getSkuId()) && reqDTO.getXianyuAccountId().equals(s.getXianyuAccountId())))
                return ResultObject.failed("该规格不属于当前账号商品，请先同步商品规格");
            if (reqDTO.getDeliveryMode() == 2) {
                for (String id : reqDTO.getKamiConfigIds().split(",")) {
                    var warehouse = kamiConfigService.getConfig(Long.parseLong(id.trim()));
                    if (warehouse == null || !reqDTO.getXianyuAccountId().equals(warehouse.getXianyuAccountId()))
                        return ResultObject.failed("卡密仓库不属于当前闲鱼账号");
                }
                if (reqDTO.getKamiDeliveryTemplate() != null && !reqDTO.getKamiDeliveryTemplate().isBlank() && !reqDTO.getKamiDeliveryTemplate().contains("{kmKey}"))
                    return ResultObject.failed("卡密文案必须包含{kmKey}占位符");
            }
            XianyuGoodsAutoDeliveryConfig existingConfig = null;
            if (skuId != null && !skuId.isEmpty()) {
                existingConfig = autoDeliveryConfigMapper
                        .findByAccountIdAndGoodsIdAndSkuId(reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), skuId);
            }
            if (existingConfig == null) {
                existingConfig = autoDeliveryConfigMapper
                        .findByAccountIdAndGoodsIdNoSku(reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());
                if (existingConfig != null && skuId != null && !skuId.isEmpty()) {
                    existingConfig = null;
                }
            }
            
            XianyuGoodsAutoDeliveryConfig config;
            if (existingConfig != null) {
                config = existingConfig;
                config.setDeliveryMode(reqDTO.getDeliveryMode());
                config.setSkuId(reqDTO.getSkuId());
                config.setSkuName(reqDTO.getSkuName());
                config.setAutoDeliveryContent(reqDTO.getAutoDeliveryContent());
                config.setKamiConfigIds(reqDTO.getKamiConfigIds());
                config.setKamiDeliveryTemplate(reqDTO.getKamiDeliveryTemplate());
                config.setAutoDeliveryImageUrl(reqDTO.getAutoDeliveryImageUrl());
                config.setXianyuGoodsId(reqDTO.getXianyuGoodsId());
                if (reqDTO.getAutoConfirmShipment() != null) {
                    config.setAutoConfirmShipment(reqDTO.getAutoConfirmShipment());
                }
                
                autoDeliveryConfigMapper.updateById(config);
                log.info("更新自动发货配置成功，ID: {}", config.getId());
            } else {
                config = new XianyuGoodsAutoDeliveryConfig();
                BeanUtils.copyProperties(reqDTO, config);
                if (config.getSkuId() == null) {
                    config.setSkuId(null);
                }
                
                autoDeliveryConfigMapper.insert(config);
                log.info("创建自动发货配置成功，ID: {}", config.getId());
            }
            
            AutoDeliveryConfigRespDTO respDTO = new AutoDeliveryConfigRespDTO();
            BeanUtils.copyProperties(config, respDTO);
            
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("保存自动发货配置失败", e);
            return ResultObject.failed("保存自动发货配置失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<AutoDeliveryConfigRespDTO> getConfig(AutoDeliveryConfigQueryReqDTO reqDTO) {
        try {
            log.info("开始查询自动发货配置: xianyuAccountId={}, xyGoodsId={}, skuId={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getSkuId());
            
            XianyuGoodsAutoDeliveryConfig config = null;
            
            if (reqDTO.getXyGoodsId() != null && !reqDTO.getXyGoodsId().trim().isEmpty()) {
                String skuId = reqDTO.getSkuId();
                if (skuId != null && !skuId.isEmpty()) {
                    config = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdAndSkuId(
                            reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), skuId);
                }
                if (config == null && (skuId == null || skuId.isBlank())) {
                    config = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(
                            reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());
                }
            } else {
                List<XianyuGoodsAutoDeliveryConfig> configs = autoDeliveryConfigMapper
                        .findByAccountId(reqDTO.getXianyuAccountId());
                config = configs.isEmpty() ? null : configs.get(0);
            }
            
            if (config == null) {
                return ResultObject.success(null);
            }
            
            AutoDeliveryConfigRespDTO respDTO = new AutoDeliveryConfigRespDTO();
            BeanUtils.copyProperties(config, respDTO);
            
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("查询自动发货配置失败", e);
            return ResultObject.failed("查询自动发货配置失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<List<AutoDeliveryConfigRespDTO>> getConfigsByGoodsId(Long xianyuAccountId, String xyGoodsId) {
        try {
            List<XianyuGoodsAutoDeliveryConfig> configs = autoDeliveryConfigMapper
                    .findByAccountIdAndGoodsId(xianyuAccountId, xyGoodsId);
            
            List<AutoDeliveryConfigRespDTO> respDTOs = configs.stream()
                    .map(config -> {
                        AutoDeliveryConfigRespDTO respDTO = new AutoDeliveryConfigRespDTO();
                        BeanUtils.copyProperties(config, respDTO);
                        return respDTO;
                    })
                    .collect(Collectors.toList());
            
            return ResultObject.success(respDTOs);
        } catch (Exception e) {
            log.error("查询商品自动发货配置列表失败", e);
            return ResultObject.failed("查询商品自动发货配置列表失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<List<AutoDeliveryConfigRespDTO>> getConfigsByAccountId(Long xianyuAccountId) {
        try {
            List<XianyuGoodsAutoDeliveryConfig> configs = autoDeliveryConfigMapper
                    .findByAccountId(xianyuAccountId);
            
            List<AutoDeliveryConfigRespDTO> respDTOs = configs.stream()
                    .map(config -> {
                        AutoDeliveryConfigRespDTO respDTO = new AutoDeliveryConfigRespDTO();
                        BeanUtils.copyProperties(config, respDTO);
                        return respDTO;
                    })
                    .collect(Collectors.toList());
            
            return ResultObject.success(respDTOs);
        } catch (Exception e) {
            log.error("查询账号自动发货配置列表失败", e);
            return ResultObject.failed("查询账号自动发货配置列表失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<Void> deleteConfig(Long xianyuAccountId, String xyGoodsId) {
        try {
            LambdaQueryWrapper<XianyuGoodsAutoDeliveryConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(XianyuGoodsAutoDeliveryConfig::getXianyuAccountId, xianyuAccountId)
                   .eq(XianyuGoodsAutoDeliveryConfig::getXyGoodsId, xyGoodsId);
            
            int deletedCount = autoDeliveryConfigMapper.delete(wrapper);
            
            if (deletedCount > 0) {
                log.info("删除自动发货配置成功，账号ID: {}, 商品ID: {}", xianyuAccountId, xyGoodsId);
                return ResultObject.success(null);
            } else {
                return ResultObject.failed("未找到对应的自动发货配置");
            }
        } catch (Exception e) {
            log.error("删除自动发货配置失败", e);
            return ResultObject.failed("删除自动发货配置失败: " + e.getMessage());
        }
    }
}
