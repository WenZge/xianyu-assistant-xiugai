package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuAutomationConfig;
import com.feijimiao.xianyuassistant.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {
    private final AutomationSettingsService settings;
    private final AutoPolishService polish;
    public AutomationController(AutomationSettingsService settings,AutoPolishService polish) { this.settings=settings; this.polish=polish; }
    @GetMapping("/config") public ResultObject<XianyuAutomationConfig> get(@RequestParam Long xianyuAccountId) { return ResultObject.success(settings.get(xianyuAccountId)); }
    @PostMapping("/config") public ResultObject<XianyuAutomationConfig> save(@Valid @RequestBody XianyuAutomationConfig value) { return ResultObject.success(settings.save(value)); }
    @GetMapping("/blacklist") public ResultObject<List<Map<String,Object>>> list(@RequestParam Long xianyuAccountId) { return ResultObject.success(settings.blacklist(xianyuAccountId)); }
    @PostMapping("/blacklist") public ResultObject<Void> saveBlacklist(@Valid @RequestBody AutomationSettingsService.BlacklistEntry value) { settings.saveBlacklist(value); return ResultObject.success(null); }
    @DeleteMapping("/blacklist/{id}") public ResultObject<Void> deleteBlacklist(@PathVariable Long id) { settings.deleteBlacklist(id); return ResultObject.success(null); }
    @PostMapping("/polish") public ResultObject<Void> run(@RequestParam Long xianyuAccountId) { polish.enqueue(xianyuAccountId,true); return ResultObject.success(null); }
    @GetMapping("/polish-records") public ResultObject<Map<String,Object>> records(@RequestParam Long xianyuAccountId,@RequestParam(defaultValue="1") int page) { return ResultObject.success(polish.records(xianyuAccountId,page)); }
}
