package org.jeecg.modules.ainote.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.ainote.entity.AinoteAiConfig;

/**
 * AI配置 Service 接口
 */
public interface IAinoteAiConfigService extends IService<AinoteAiConfig> {

    /**
     * 查询租户配置
     */
    AinoteAiConfig getConfig(Integer tenantId);

    /**
     * 更新租户配置
     */
    void updateConfig(AinoteAiConfig config);

    /**
     * 失效租户缓存
     */
    void invalidateCache(Integer tenantId);
}
