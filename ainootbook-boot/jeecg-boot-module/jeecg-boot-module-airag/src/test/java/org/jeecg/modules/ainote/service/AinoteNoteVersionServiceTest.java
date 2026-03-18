package org.jeecg.modules.ainote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.TokenUtils;
import org.jeecg.modules.ainote.dto.AinoteNoteRegenerateDTO;
import org.jeecg.modules.ainote.entity.AinoteAiConfig;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.entity.AinoteNoteVersion;
import org.jeecg.modules.ainote.facade.AinoteGenerationFacade;
import org.jeecg.modules.ainote.mapper.AinoteNoteMapper;
import org.jeecg.modules.ainote.mapper.AinoteNoteVersionMapper;
import org.jeecg.modules.ainote.service.impl.AinoteEmbeddingService;
import org.jeecg.modules.ainote.service.impl.AinoteNoteServiceImpl;
import org.jeecg.modules.ainote.service.impl.AinoteNoteVersionServiceImpl;
import org.jeecg.modules.ainote.task.AinoteNoteVersionPruneTask;
import org.jeecg.modules.ainote.vo.AinoteNoteRegenerateVO;
import org.jeecg.modules.ainote.vo.AinoteNoteVersionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Transactional
@DisplayName("AinoteNoteVersion 版本管理单元测试")
class AinoteNoteVersionServiceTest {

    private static final String NOTE_ID = "note-1";
    private static final String USER_ID = "user-1";
    private static final int TENANT_ID = 7;

    @Mock
    private AinoteNoteMapper noteMapper;
    @Mock
    private AinoteEmbeddingService ainoteEmbeddingService;
    @Mock
    private IAinoteAiConfigService ainoteAiConfigService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private IAinoteNoteVersionService ainoteNoteVersionService;
    @Mock
    private AinoteGenerationFacade generationFacade;
    @Mock
    private IAinoteAiTaskService ainoteAiTaskService;
    @Mock
    private MarkdownPrecompileService markdownPrecompileService;

    @Spy
    @InjectMocks
    private AinoteNoteServiceImpl noteService = new AinoteNoteServiceImpl();

    @Mock
    private AinoteNoteVersionMapper versionMapper;

    @InjectMocks
    private AinoteNoteVersionServiceImpl versionService;

    private AinoteNoteVersionPruneTask pruneTask;
    private MockedStatic<SecurityUtils> securityUtilsMock;
    private MockedStatic<SpringContextUtils> springContextUtilsMock;
    private MockedStatic<TokenUtils> tokenUtilsMock;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(noteService, "baseMapper", noteMapper);
        ReflectionTestUtils.setField(versionService, "baseMapper", versionMapper);
        pruneTask = new AinoteNoteVersionPruneTask(versionMapper, noteMapper);
        mockUserAndTenant();
    }

    @AfterEach
    void tearDown() {
        if (securityUtilsMock != null) {
            securityUtilsMock.close();
        }
        if (springContextUtilsMock != null) {
            springContextUtilsMock.close();
        }
        if (tokenUtilsMock != null) {
            tokenUtilsMock.close();
        }
    }

    @Test
    void should_create_new_version_when_regenerate_given_valid_request() {
        AinoteNote existing = baseNote(3, "旧内容");
        AinoteNoteRegenerateDTO dto = regenerateRequest(3, "补充信息");
        AinoteNoteVersion latest = new AinoteNoteVersion().setVersionNumber(3);
        AinoteNote refreshed = baseNote(4, "新内容");

        doReturn(existing).when(noteService).getOne(any(QueryWrapper.class));
        doReturn(true).when(noteService).update(isNull(), any(UpdateWrapper.class));
        doReturn(refreshed).when(noteService).getById(NOTE_ID);
        when(generationFacade.regenerateNoteContent(existing, "补充信息")).thenReturn("新内容");
        when(ainoteNoteVersionService.getOne(any(QueryWrapper.class), eq(false)))
                .thenReturn(latest)
                .thenReturn((AinoteNoteVersion) null);
        when(ainoteNoteVersionService.save(any(AinoteNoteVersion.class))).thenReturn(true);
        when(ainoteAiTaskService.createTask(NOTE_ID, null, "summary")).thenReturn(new AinoteAiTask());
        when(ainoteAiConfigService.getConfig(TENANT_ID)).thenReturn(AinoteAiConfig.defaults().setKnowledgeId("kb-1"));

        AinoteNoteRegenerateVO result = noteService.regenerateNote(dto);

        assertThat(result.getVersion()).isEqualTo(4);
        assertThat(result.getNoteContent()).isEqualTo("新内容");
        ArgumentCaptor<AinoteNoteVersion> versionCaptor = ArgumentCaptor.forClass(AinoteNoteVersion.class);
        verify(ainoteNoteVersionService, org.mockito.Mockito.times(2)).save(versionCaptor.capture());
        assertThat(versionCaptor.getAllValues())
                .extracting(AinoteNoteVersion::getVersionNumber)
                .containsExactly(3, 4);
        verify(ainoteAiTaskService).createTask(NOTE_ID, null, "summary");
        verify(ainoteEmbeddingService).deleteNoteEmbedding(NOTE_ID, "kb-1");
        verify(ainoteEmbeddingService).embedNote(refreshed, "kb-1");
    }

    @Test
    void should_throw_exception_when_regenerate_given_optimistic_lock_conflict() {
        AinoteNote existing = baseNote(4, "旧内容");
        AinoteNoteRegenerateDTO dto = regenerateRequest(3, "补充信息");

        doReturn(existing).when(noteService).getOne(any(QueryWrapper.class));

        assertThatThrownBy(() -> noteService.regenerateNote(dto))
                .isInstanceOf(JeecgBootException.class)
                .hasMessageContaining("版本已变更");
    }

    @Test
    void should_keep_version_numbers_strictly_increasing_when_regenerate_given_sampled_states() {
        assertThat(nextVersionFor(1, 0)).isEqualTo(2);
        assertThat(nextVersionFor(3, 5)).isEqualTo(6);
        assertThat(nextVersionFor(8, 8)).isEqualTo(9);
    }

    @Test
    void should_return_versions_in_desc_order_when_listVersions_given_existing_history() {
        Page<AinoteNoteVersionVO> mapperPage = new Page<>(1, 20);
        mapperPage.setRecords(List.of(versionVo(5), versionVo(3), versionVo(1)));
        when(versionMapper.selectVersionPage(any(Page.class), eq(NOTE_ID), eq(TENANT_ID))).thenReturn(mapperPage);

        IPage<AinoteNoteVersionVO> page = versionService.queryVersionPage(NOTE_ID, TENANT_ID, 1, 20);

        assertThat(page.getRecords())
                .extracting(AinoteNoteVersionVO::getVersion)
                .containsExactly(5, 3, 1);
    }

    @Test
    void should_return_empty_list_when_listVersions_given_missing_note() {
        when(versionMapper.selectVersionPage(any(Page.class), eq("missing-note"), eq(TENANT_ID)))
                .thenReturn(new Page<>(1, 20));

        IPage<AinoteNoteVersionVO> page = versionService.queryVersionPage("missing-note", TENANT_ID, 1, 20);

        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    void should_update_currentVersion_when_rollback_given_existing_target_version() {
        AinoteNote existing = baseNote(5, "当前内容");
        AinoteNoteVersion targetVersion = new AinoteNoteVersion()
                .setId("ver-2")
                .setNoteId(NOTE_ID)
                .setVersionNumber(2)
                .setNoteContent("历史内容")
                .setAiSummary("历史摘要")
                .setKeywords("历史关键词");
        AinoteNote rolledBack = baseNote(2, "历史内容")
                .setAiSummary("历史摘要")
                .setKeywords("历史关键词");

        doReturn(existing).when(noteService).getOne(any(QueryWrapper.class));
        doReturn(true).when(noteService).updateById(any(AinoteNote.class));
        doReturn(rolledBack).when(noteService).getById(NOTE_ID);
        when(ainoteNoteVersionService.getOne(any(QueryWrapper.class))).thenReturn(targetVersion);
        when(markdownPrecompileService.precompile("历史内容")).thenReturn("<p>历史内容</p>");

        AinoteNote result = noteService.rollbackToVersion(NOTE_ID, 2);

        assertThat(result.getCurrentVersion()).isEqualTo(2);
        assertThat(result.getNoteContent()).isEqualTo("历史内容");
        ArgumentCaptor<AinoteNote> updateCaptor = ArgumentCaptor.forClass(AinoteNote.class);
        verify(noteService).updateById(updateCaptor.capture());
        AinoteNote update = updateCaptor.getValue();
        assertThat(update.getCurrentVersion()).isEqualTo(2);
        assertThat(update.getAiSummary()).isEqualTo("历史摘要");
        assertThat(update.getKeywords()).isEqualTo("历史关键词");
    }

    @Test
    void should_keep_history_immutable_when_rollback_given_existing_target_version() {
        AinoteNote existing = baseNote(5, "当前内容");
        AinoteNoteVersion targetVersion = new AinoteNoteVersion()
                .setId("ver-2")
                .setNoteId(NOTE_ID)
                .setVersionNumber(2)
                .setNoteContent("历史内容")
                .setAiSummary("历史摘要")
                .setKeywords("历史关键词");

        doReturn(existing).when(noteService).getOne(any(QueryWrapper.class));
        doReturn(true).when(noteService).updateById(any(AinoteNote.class));
        doReturn(baseNote(2, "历史内容")).when(noteService).getById(NOTE_ID);
        when(ainoteNoteVersionService.getOne(any(QueryWrapper.class))).thenReturn(targetVersion);
        when(markdownPrecompileService.precompile("历史内容")).thenReturn("<p>历史内容</p>");

        noteService.rollbackToVersion(NOTE_ID, 2);

        assertThat(targetVersion.getVersionNumber()).isEqualTo(2);
        assertThat(targetVersion.getNoteContent()).isEqualTo("历史内容");
        verify(ainoteNoteVersionService, never()).save(any(AinoteNoteVersion.class));
    }

    @Test
    void should_throw_exception_when_rollback_given_missing_target_version() {
        doReturn(baseNote(5, "当前内容")).when(noteService).getOne(any(QueryWrapper.class));
        when(ainoteNoteVersionService.getOne(any(QueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> noteService.rollbackToVersion(NOTE_ID, 9))
                .isInstanceOf(JeecgBootException.class)
                .hasMessageContaining("目标版本不存在");
    }

    @Test
    void should_delete_versions_older_than_latest_ten_when_pruneOldVersions_given_history_exceeds_limit() {
        when(versionMapper.selectList(any(QueryWrapper.class))).thenReturn(versionHistory("note-1", 12, 1));
        when(noteMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(new AinoteNote().setId("note-1").setCurrentVersion(12)));
        when(versionMapper.deleteBatchIds(any())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

        pruneTask.pruneOldVersions();

        ArgumentCaptor<List<String>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(versionMapper).deleteBatchIds(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue()).containsExactly("note-1-v2", "note-1-v1");
    }

    @Test
    void should_preserve_current_version_when_pruneOldVersions_given_rolled_back_current_version() {
        when(versionMapper.selectList(any(QueryWrapper.class))).thenReturn(versionHistory("note-1", 12, 1));
        when(noteMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(new AinoteNote().setId("note-1").setCurrentVersion(2)));
        when(versionMapper.deleteBatchIds(any())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

        pruneTask.pruneOldVersions();

        ArgumentCaptor<List<String>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(versionMapper).deleteBatchIds(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue()).containsExactly("note-1-v1");
    }

    private int nextVersionFor(int currentVersion, int latestPersistedVersion) {
        reset(generationFacade, ainoteNoteVersionService, ainoteAiTaskService, ainoteAiConfigService);

        AinoteNote existing = baseNote(currentVersion, "旧内容-" + currentVersion);
        AinoteNoteRegenerateDTO dto = regenerateRequest(currentVersion, "补充-" + latestPersistedVersion);
        AinoteNoteVersion latest = latestPersistedVersion <= 0 ? null
                : new AinoteNoteVersion().setVersionNumber(latestPersistedVersion);
        AinoteNote refreshed = baseNote(Math.max(currentVersion, latestPersistedVersion) + 1, "新内容-" + latestPersistedVersion);

        doReturn(existing).when(noteService).getOne(any(QueryWrapper.class));
        doReturn(true).when(noteService).update(isNull(), any(UpdateWrapper.class));
        doReturn(refreshed).when(noteService).getById(NOTE_ID);
        when(generationFacade.regenerateNoteContent(existing, dto.getAdditionalContent())).thenReturn(refreshed.getNoteContent());
        when(ainoteNoteVersionService.getOne(any(QueryWrapper.class), eq(false)))
                .thenReturn(latest)
                .thenReturn((AinoteNoteVersion) null);
        when(ainoteNoteVersionService.save(any(AinoteNoteVersion.class))).thenReturn(true);
        when(ainoteAiTaskService.createTask(NOTE_ID, null, "summary")).thenReturn(new AinoteAiTask());
        when(ainoteAiConfigService.getConfig(TENANT_ID)).thenReturn(AinoteAiConfig.defaults());

        return noteService.regenerateNote(dto).getVersion();
    }

    private void mockUserAndTenant() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(USER_ID);
        loginUser.setRoleCode("student");

        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(loginUser);

        MockHttpServletRequest request = new MockHttpServletRequest();

        securityUtilsMock = org.mockito.Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getSubject).thenReturn(subject);
        springContextUtilsMock = org.mockito.Mockito.mockStatic(SpringContextUtils.class);
        springContextUtilsMock.when(SpringContextUtils::getHttpServletRequest).thenReturn(request);
        tokenUtilsMock = org.mockito.Mockito.mockStatic(TokenUtils.class);
        tokenUtilsMock.when(() -> TokenUtils.getTenantIdByRequest(request)).thenReturn(String.valueOf(TENANT_ID));
    }

    private AinoteNote baseNote(int currentVersion, String noteContent) {
        return new AinoteNote()
                .setId(NOTE_ID)
                .setStudentId(USER_ID)
                .setCreateBy(USER_ID)
                .setTenantId(TENANT_ID)
                .setCurrentVersion(currentVersion)
                .setNoteStatus(1)
                .setNoteContent(noteContent)
                .setAiSummary("摘要-" + currentVersion)
                .setKeywords("关键词-" + currentVersion);
    }

    private AinoteNoteRegenerateDTO regenerateRequest(int baseVersion, String additionalContent) {
        AinoteNoteRegenerateDTO dto = new AinoteNoteRegenerateDTO();
        dto.setNoteId(NOTE_ID);
        dto.setBaseVersion(baseVersion);
        dto.setAdditionalContent(additionalContent);
        return dto;
    }

    private AinoteNoteVersionVO versionVo(int version) {
        AinoteNoteVersionVO vo = new AinoteNoteVersionVO();
        vo.setVersion(version);
        vo.setSummary("summary-" + version);
        vo.setKeywords("kw-" + version);
        vo.setCreatedBy(USER_ID);
        vo.setCreatedAt(new Date(version * 1000L));
        return vo;
    }

    private List<AinoteNoteVersion> versionHistory(String noteId, int startVersion, int endVersion) {
        java.util.ArrayList<AinoteNoteVersion> versions = new java.util.ArrayList<>();
        for (int version = startVersion; version >= endVersion; version--) {
            versions.add(new AinoteNoteVersion()
                    .setId(noteId + "-v" + version)
                    .setNoteId(noteId)
                    .setVersionNumber(version)
                    .setCreatedAt(new Date(version * 1000L)));
        }
        return versions;
    }
}
