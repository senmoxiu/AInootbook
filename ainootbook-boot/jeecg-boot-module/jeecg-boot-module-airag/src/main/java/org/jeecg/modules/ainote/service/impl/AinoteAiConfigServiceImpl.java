package org.jeecg.modules.ainote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.ainote.entity.AinoteAiConfig;
import org.jeecg.modules.ainote.mapper.AinoteAiConfigMapper;
import org.jeecg.modules.ainote.service.IAinoteAiConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * AI笔记配置 Service 实现（Caffeine 本地缓存，5 分钟 TTL）
 */
@Slf4j
@Service
public class AinoteAiConfigServiceImpl extends ServiceImpl<AinoteAiConfigMapper, AinoteAiConfig>
        implements IAinoteAiConfigService {

    private static final int DEFAULT_TENANT_ID = 0;

    private final Cache<Integer, AinoteAiConfig> configCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    @Override
    public AinoteAiConfig getConfig(Integer tenantId) {
        Integer cacheKey = normalizeTenantId(tenantId);
        return configCache.get(cacheKey, this::loadFromDb);
    }

    private AinoteAiConfig loadFromDb(Integer tenantId) {
        LambdaQueryWrapper<AinoteAiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AinoteAiConfig::getTenantId, tenantId);
        AinoteAiConfig config = baseMapper.selectOne(wrapper);
        if (config != null) {
            return config;
        }
        return AinoteAiConfig.defaults().setTenantId(tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(AinoteAiConfig config) {
        AinoteAiConfig target = config == null ? AinoteAiConfig.defaults() : config;
        Integer tenantId = normalizeTenantId(target.getTenantId());

        LambdaQueryWrapper<AinoteAiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AinoteAiConfig::getTenantId, tenantId);
        AinoteAiConfig existed = baseMapper.selectOne(wrapper);

        target.setTenantId(tenantId);
        if (existed != null) {
            target.setId(existed.getId());
        } else {
            target.setId(null);
        }

        saveOrUpdate(target);
        configCache.invalidate(tenantId);
    }

    @Override
    public void invalidateCache(Integer tenantId) {
        configCache.invalidate(normalizeTenantId(tenantId));
    }

    private Integer normalizeTenantId(Integer tenantId) {
        return tenantId == null ? DEFAULT_TENANT_ID : tenantId;
    }
}
