package org.jeecg.modules.ainote.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.entity.AinoteNoteVersion;
import org.jeecg.modules.ainote.mapper.AinoteNoteMapper;
import org.jeecg.modules.ainote.mapper.AinoteNoteVersionMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 笔记版本历史裁剪任务：每天凌晨 3 点清理每条笔记超过 10 个的旧版本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AinoteNoteVersionPruneTask {

    private static final int RETAIN_VERSION_COUNT = 10;
    private static final int DELETE_BATCH_SIZE = 500;

    private final AinoteNoteVersionMapper ainoteNoteVersionMapper;
    private final AinoteNoteMapper ainoteNoteMapper;

    @Scheduled(cron = "0 0 3 * * ?")
    public void pruneOldVersions() {
        QueryWrapper<AinoteNoteVersion> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "note_id", "version_number", "created_at")
                .orderByAsc("note_id")
                .orderByDesc("version_number")
                .orderByDesc("created_at")
                .orderByDesc("id");

        List<AinoteNoteVersion> versions = ainoteNoteVersionMapper.selectList(queryWrapper);
        if (versions == null || versions.isEmpty()) {
            log.info("笔记版本历史裁剪完成，删除版本数: 0");
            return;
        }

        Map<String, Integer> currentVersionMap = loadCurrentVersionMap(versions);
        List<String> deleteIds = collectDeleteIds(versions, currentVersionMap);
        if (deleteIds.isEmpty()) {
            log.info("笔记版本历史裁剪完成，删除版本数: 0");
            return;
        }

        int deletedCount = 0;
        for (int i = 0; i < deleteIds.size(); i += DELETE_BATCH_SIZE) {
            List<String> batchIds = deleteIds.subList(i, Math.min(i + DELETE_BATCH_SIZE, deleteIds.size()));
            deletedCount += ainoteNoteVersionMapper.deleteBatchIds(batchIds);
        }

        log.info("笔记版本历史裁剪完成，删除版本数: {}", deletedCount);
    }

    private Map<String, Integer> loadCurrentVersionMap(List<AinoteNoteVersion> versions) {
        List<String> noteIds = new ArrayList<>();
        for (AinoteNoteVersion version : versions) {
            if (version == null
                    || oConvertUtils.isEmpty(version.getNoteId()) || version.getNoteId().isBlank()
                    || noteIds.contains(version.getNoteId())) {
                continue;
            }
            noteIds.add(version.getNoteId());
        }
        if (noteIds.isEmpty()) {
            return Map.of();
        }

        QueryWrapper<AinoteNote> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "current_version").in("id", noteIds);
        List<AinoteNote> notes = ainoteNoteMapper.selectList(queryWrapper);
        if (notes == null || notes.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> currentVersionMap = new HashMap<>();
        for (AinoteNote note : notes) {
            if (note == null
                    || oConvertUtils.isEmpty(note.getId()) || note.getId().isBlank()
                    || note.getCurrentVersion() == null) {
                continue;
            }
            currentVersionMap.put(note.getId(), note.getCurrentVersion());
        }
        return currentVersionMap;
    }

    private List<String> collectDeleteIds(List<AinoteNoteVersion> versions, Map<String, Integer> currentVersionMap) {
        List<String> deleteIds = new ArrayList<>();
        String currentNoteId = null;
        int currentCount = 0;

        for (AinoteNoteVersion version : versions) {
            if (version == null
                    || oConvertUtils.isEmpty(version.getId()) || version.getId().isBlank()
                    || oConvertUtils.isEmpty(version.getNoteId()) || version.getNoteId().isBlank()) {
                continue;
            }

            if (!version.getNoteId().equals(currentNoteId)) {
                currentNoteId = version.getNoteId();
                currentCount = 0;
            }

            currentCount++;
            Integer currentVersion = currentVersionMap.get(currentNoteId);
            if (currentCount > RETAIN_VERSION_COUNT
                    && !Objects.equals(version.getVersionNumber(), currentVersion)) {
                deleteIds.add(version.getId());
            }
        }

        return deleteIds;
    }
}
