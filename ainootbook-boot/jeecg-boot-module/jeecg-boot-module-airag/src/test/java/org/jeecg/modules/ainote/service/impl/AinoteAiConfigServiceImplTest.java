package org.jeecg.modules.ainote.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.ainote.entity.AinoteAiConfig;
import org.jeecg.modules.ainote.mapper.AinoteAiConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AinoteAiConfigServiceImpl 单元测试")
class AinoteAiConfigServiceImplTest {

    @Mock
    private AinoteAiConfigMapper baseMapper;

    @InjectMocks
    private AinoteAiConfigServiceImpl service;

    private Cache<Integer, AinoteAiConfig> configCache;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        configCache = getConfigCache();
    }

    @Test
    @DisplayName("updateConfig: 新建配置时应写入记录并清理缓存")
    void updateConfig_shouldCreateNewRecordAndInvalidateCache_whenTenantConfigDoesNotExist() {
        int tenantId = 7;
        configCache.put(tenantId, existingConfig(tenantId));

        when(baseMapper.selectOne(any())).thenReturn(null);
        when(baseMapper.insert(any(AinoteAiConfig.class))).thenReturn(1);

        AinoteAiConfig request = new AinoteAiConfig()
                .setTenantId(tenantId)
                .setSummaryModelId("summary-model")
                .setUpdateBy("tester");

        service.updateConfig(request);

        ArgumentCaptor<AinoteAiConfig> insertCaptor = ArgumentCaptor.forClass(AinoteAiConfig.class);
        verify(baseMapper).insert(insertCaptor.capture());
        AinoteAiConfig inserted = insertCaptor.getValue();
        assertThat(inserted.getTenantId()).isEqualTo(tenantId);
        assertThat(inserted.getId()).isNull();
        assertThat(inserted.getCreateTime()).isNotNull();
        assertThat(inserted.getUpdateTime()).isNotNull();
        assertThat(configCache.getIfPresent(tenantId)).isNull();
        verify(baseMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("updateConfig: 重复调用同一租户时不应重复插入")
    void updateConfig_shouldAvoidDuplicateInsert_whenCalledRepeatedlyForSameTenant() {
        int tenantId = 9;
        AinoteAiConfig existed = existingConfig(tenantId);

        when(baseMapper.selectOne(any())).thenReturn(null, existed);
        when(baseMapper.insert(any(AinoteAiConfig.class))).thenReturn(1);
        when(baseMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        service.updateConfig(new AinoteAiConfig().setTenantId(tenantId).setSummaryModelId("summary-model"));
        service.updateConfig(new AinoteAiConfig().setTenantId(tenantId).setSummaryModelId("summary-model"));

        verify(baseMapper, times(1)).insert(any(AinoteAiConfig.class));
        verify(baseMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("updateConfig: 仅应 merge 更新非空字段并清理缓存")
    void updateConfig_shouldMergeOnlyNonNullFieldsAndInvalidateCache_whenRecordExists() {
        int tenantId = 11;
        AinoteAiConfig existed = existingConfig(tenantId);
        configCache.put(tenantId, existed);

        when(baseMapper.selectOne(any())).thenReturn(existed);
        when(baseMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        AinoteAiConfig request = new AinoteAiConfig()
                .setTenantId(tenantId)
                .setSummaryModelId("summary-new")
                .setMaxKeywordsCount(8)
                .setUpdateBy("tester");

        service.updateConfig(request);

        ArgumentCaptor<UpdateWrapper<AinoteAiConfig>> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(baseMapper).update(isNull(), updateCaptor.capture());
        String sqlSet = updateCaptor.getValue().getSqlSet();
        assertThat(sqlSet).contains("summary_model_id");
        assertThat(sqlSet).contains("max_keywords_count");
        assertThat(sqlSet).contains("update_time");
        assertThat(sqlSet).contains("update_by");
        assertThat(sqlSet).doesNotContain("knowledge_id");
        assertThat(configCache.getIfPresent(tenantId)).isNull();
        verify(baseMapper, never()).insert(any(AinoteAiConfig.class));
    }

    @Test
    @DisplayName("updateConfig: 乐观锁冲突时应抛出并发修改异常")
    void updateConfig_shouldThrowOnOptimisticLockConflict_whenConcurrentUpdateDetected() {
        int tenantId = 13;
        AinoteAiConfig existed = existingConfig(tenantId);

        when(baseMapper.selectOne(any())).thenReturn(existed);
        when(baseMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        AinoteAiConfig request = new AinoteAiConfig()
                .setTenantId(tenantId)
                .setSummaryModelId("summary-conflict");

        assertThatThrownBy(() -> service.updateConfig(request))
                .isInstanceOf(JeecgBootException.class)
                .hasMessageContaining("并发修改");
    }

    private AinoteAiConfig existingConfig(int tenantId) {
        return new AinoteAiConfig()
                .setId("cfg-" + tenantId)
                .setTenantId(tenantId)
                .setSummaryModelId("summary-old")
                .setKnowledgeId("knowledge-keep")
                .setUpdateTime(new Date());
    }

    @SuppressWarnings("unchecked")
    private Cache<Integer, AinoteAiConfig> getConfigCache() {
        return (Cache<Integer, AinoteAiConfig>) ReflectionTestUtils.getField(service, "configCache");
    }
}
