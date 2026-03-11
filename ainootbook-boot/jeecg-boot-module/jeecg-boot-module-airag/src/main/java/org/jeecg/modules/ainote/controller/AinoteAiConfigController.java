package org.jeecg.modules.ainote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.TokenUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.entity.AinoteAiConfig;
import org.jeecg.modules.ainote.service.IAinoteAiConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * AI笔记配置 Controller
 */
@Tag(name = "AI笔记配置管理")
@RestController
@RequestMapping("/ainote/aiConfig")
@Slf4j
public class AinoteAiConfigController extends JeecgController<AinoteAiConfig, IAinoteAiConfigService> {

    @Autowired
    private IAinoteAiConfigService ainoteAiConfigService;

    @Operation(summary = "查询当前租户配置")
    @GetMapping
    public Result<AinoteAiConfig> getCurrentConfig() {
        Integer tenantId = getCurrentTenantId();
        AinoteAiConfig config = ainoteAiConfigService.getConfig(tenantId);
        return Result.OK(config);
    }

    @Operation(summary = "更新当前租户配置")
    @PutMapping
    public Result<String> updateConfig(@RequestBody AinoteAiConfig config) {
        Integer tenantId = getCurrentTenantId();
        config.setTenantId(tenantId);
        ainoteAiConfigService.updateConfig(config);
        return Result.OK("更新成功！");
    }

    @Operation(summary = "手动刷新缓存")
    @PostMapping("/refresh")
    public Result<AinoteAiConfig> refreshCache() {
        Integer tenantId = getCurrentTenantId();
        ainoteAiConfigService.invalidateCache(tenantId);
        AinoteAiConfig config = ainoteAiConfigService.getConfig(tenantId);
        return Result.OK(config);
    }

    private Integer getCurrentTenantId() {
        try {
            HttpServletRequest request = SpringContextUtils.getHttpServletRequest();
            String tenantIdStr = TokenUtils.getTenantIdByRequest(request);
            if (oConvertUtils.isNotEmpty(tenantIdStr)) {
                return Integer.parseInt(tenantIdStr);
            }
        } catch (Exception e) {
            log.warn("获取租户ID失败，使用默认值", e);
        }
        return 0;
    }
}
