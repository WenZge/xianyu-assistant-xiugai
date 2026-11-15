package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuBuyerInteractionConfig;
import com.feijimiao.xianyuassistant.service.BuyerInteractionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/buyer-interaction")
public class BuyerInteractionController {
    private final BuyerInteractionService service;
    public BuyerInteractionController(BuyerInteractionService service) { this.service = service; }
    @GetMapping("/config")
    public ResultObject<XianyuBuyerInteractionConfig> config(@RequestParam Long xianyuAccountId) { return ResultObject.success(service.getConfig(xianyuAccountId)); }
    @PostMapping("/config")
    public ResultObject<XianyuBuyerInteractionConfig> save(@Valid @RequestBody XianyuBuyerInteractionConfig config) { return ResultObject.success(service.saveConfig(config)); }
    @GetMapping("/records")
    public ResultObject<Map<String, Object>> records(@RequestParam Long xianyuAccountId, @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) { return ResultObject.success(service.records(xianyuAccountId, page, size)); }
    @PostMapping("/retry")
    public ResultObject<Void> retry(@RequestParam Long xianyuAccountId, @RequestParam Long recordId) { service.retry(xianyuAccountId, recordId); return ResultObject.success(null); }
}
