package org.jeecg.modules.ainote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.ainote.entity.AinoteAiConfig;
import org.jeecg.modules.ainote.mapper.AinoteAiConfigMapper;
import org.jeecg.modules.ainote.service.IAinoteAiConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
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
        if (config == null) {
            throw new JeecgBootException("配置对象不能为空");
        }

        Integer tenantId = normalizeTenantId(config.getTenantId());

        LambdaQueryWrapper<AinoteAiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AinoteAiConfig::getTenantId, tenantId);
        AinoteAiConfig existed = baseMapper.selectOne(wrapper);

        if (existed == null) {
            // 新建记录
            config.setTenantId(tenantId);
            config.setId(null);
            config.setCreateTime(new Date());
            config.setUpdateTime(new Date());
            baseMapper.insert(config);
            log.info("创建新配置: tenantId={}", tenantId);
        } else {
            // 字段级 merge-update + 乐观锁
            Date oldUpdateTime = existed.getUpdateTime();
            UpdateWrapper<AinoteAiConfig> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", existed.getId());
            updateWrapper.eq("tenant_id", tenantId);

            // 乐观锁：仅当 update_time 未变时才更新
            if (oldUpdateTime != null) {
                updateWrapper.eq("update_time", oldUpdateTime);
            }

            // 字段级 merge：仅更新非 null 字段
            boolean hasUpdate = false;
            if (config.getSummaryModelId() != null) {
                updateWrapper.set("summary_model_id", config.getSummaryModelId());
                hasUpdate = true;
            }
            if (config.getOcrModelId() != null) {
                updateWrapper.set("ocr_model_id", config.getOcrModelId());
                hasUpdate = true;
            }
            if (config.getAsrModelId() != null) {
                updateWrapper.set("asr_model_id", config.getAsrModelId());
                hasUpdate = true;
            }
            if (config.getVideoModelId() != null) {
                updateWrapper.set("video_model_id", config.getVideoModelId());
                hasUpdate = true;
            }
            if (config.getKeywordsModelId() != null) {
                updateWrapper.set("keywords_model_id", config.getKeywordsModelId());
                hasUpdate = true;
            }
            if (config.getIntegrateModelId() != null) {
                updateWrapper.set("integrate_model_id", config.getIntegrateModelId());
                hasUpdate = true;
            }
            if (config.getKnowledgeId() != null) {
                updateWrapper.set("knowledge_id", config.getKnowledgeId());
                hasUpdate = true;
            }
            if (config.getSummaryPromptKey() != null) {
                updateWrapper.set("summary_prompt_key", config.getSummaryPromptKey());
                hasUpdate = true;
            }
            if (config.getKeywordsPromptKey() != null) {
                updateWrapper.set("keywords_prompt_key", config.getKeywordsPromptKey());
                hasUpdate = true;
            }
            if (config.getIntegratePromptKey() != null) {
                updateWrapper.set("integrate_prompt_key", config.getIntegratePromptKey());
                hasUpdate = true;
            }
            if (config.getMaxSummaryLength() != null) {
                updateWrapper.set("max_summary_length", config.getMaxSummaryLength());
                hasUpdate = true;
            }
            if (config.getMaxKeywordsCount() != null) {
                updateWrapper.set("max_keywords_count", config.getMaxKeywordsCount());
                hasUpdate = true;
            }
            if (config.getAsrFailureMode() != null) {
                updateWrapper.set("asr_failure_mode", config.getAsrFailureMode());
                hasUpdate = true;
            }
            if (config.getAsrRetryLimit() != null) {
                updateWrapper.set("asr_retry_limit", config.getAsrRetryLimit());
                hasUpdate = true;
            }
            if (config.getOcrFailureMode() != null) {
                updateWrapper.set("ocr_failure_mode", config.getOcrFailureMode());
                hasUpdate = true;
            }
            if (config.getOcrRetryLimit() != null) {
                updateWrapper.set("ocr_retry_limit", config.getOcrRetryLimit());
                hasUpdate = true;
            }
            if (config.getVideoFailureMode() != null) {
                updateWrapper.set("video_failure_mode", config.getVideoFailureMode());
                hasUpdate = true;
            }
            if (config.getVideoRetryLimit() != null) {
                updateWrapper.set("video_retry_limit", config.getVideoRetryLimit());
                hasUpdate = true;
            }
            if (config.getSummaryFailureMode() != null) {
                updateWrapper.set("summary_failure_mode", config.getSummaryFailureMode());
                hasUpdate = true;
            }
            if (config.getSummaryRetryLimit() != null) {
                updateWrapper.set("summary_retry_limit", config.getSummaryRetryLimit());
                hasUpdate = true;
            }
            if (config.getIntegrateFailureMode() != null) {
                updateWrapper.set("integrate_failure_mode", config.getIntegrateFailureMode());
                hasUpdate = true;
            }
            if (config.getIntegrateRetryLimit() != null) {
                updateWrapper.set("integrate_retry_limit", config.getIntegrateRetryLimit());
                hasUpdate = true;
            }

            if (!hasUpdate) {
                log.warn("无字段需要更新: tenantId={}", tenantId);
                return;
            }

            updateWrapper.set("update_time", new Date());
            if (config.getUpdateBy() != null) {
                updateWrapper.set("update_by", config.getUpdateBy());
            }

            int updated = baseMapper.update(null, updateWrapper);
            if (updated == 0) {
                throw new JeecgBootException("配置更新失败，可能存在并发修改，请重试");
            }
            log.info("更新配置成功: tenantId={}, updated={}", tenantId, updated);
        }

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
