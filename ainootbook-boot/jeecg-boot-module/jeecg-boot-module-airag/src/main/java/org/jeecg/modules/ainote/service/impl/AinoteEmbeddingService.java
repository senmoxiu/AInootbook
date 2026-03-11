package org.jeecg.modules.ainote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.airag.llm.consts.LLMConsts;
import org.jeecg.modules.airag.llm.entity.AiragKnowledge;
import org.jeecg.modules.airag.llm.entity.AiragKnowledgeDoc;
import org.jeecg.modules.airag.llm.handler.EmbeddingHandler;
import org.jeecg.modules.airag.llm.service.IAiragKnowledgeService;
import org.jeecg.modules.airag.teaching.mapper.AinoteTeachingMapper;
import org.jeecg.modules.ainote.enums.NoteSearchScope;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.mapper.AinoteNoteMapper;
import org.jeecg.modules.ainote.vo.SemanticSearchResultDTO;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Ainote 知识库向量化服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AinoteEmbeddingService {

    private final EmbeddingHandler embeddingHandler;
    private final IAiragKnowledgeService airagKnowledgeService;
    private final AinoteTeachingMapper ainoteTeachingMapper;
    private final AinoteNoteMapper ainoteNoteMapper;

    /**
     * 将笔记内容向量化到指定知识库（C12: 仅 title+content）
     *
     * doc.title 设为 noteId：EmbeddingHandler 将其存为 docName metadata，
     * 检索时通过 docName 还原 noteId。
     * doc.content 传 noteTitle+noteContent，EmbeddingHandler 会自动前置 title(=noteId)，
     * 实际向量文本 = noteId + "\n" + noteTitle + "\n" + noteContent，
     * noteId 前缀无语义影响，向量语义由正文决定。
     */
    public void embedNote(AinoteNote note, String knowledgeId) {
        if (note == null || oConvertUtils.isEmpty(note.getId())) {
            throw new JeecgBootException("笔记不能为空");
        }
        if (oConvertUtils.isEmpty(knowledgeId)) {
            throw new JeecgBootException("知识库ID不能为空");
        }
        AiragKnowledge knowledge = airagKnowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new JeecgBootException("知识库不存在");
        }

        String noteTitle = oConvertUtils.getString(note.getNoteTitle(), "");
        String noteContent = oConvertUtils.getString(note.getNoteContent(), "");

        AiragKnowledgeDoc doc = new AiragKnowledgeDoc();
        doc.setId(note.getId());
        doc.setKnowledgeId(knowledgeId);
        doc.setTitle(note.getId());
        doc.setType(LLMConsts.KNOWLEDGE_DOC_TYPE_TEXT);
        doc.setContent(noteTitle + "\n" + noteContent);
        doc.setCreateBy(note.getCreateBy());
        doc.setCreateTime(new Date());
        if (note.getTenantId() != null) {
            doc.setTenantId(String.valueOf(note.getTenantId()));
        }

        Map<String, Object> extraMetadata = new HashMap<>(4);
        if (oConvertUtils.isNotEmpty(note.getCreateBy())) {
            extraMetadata.put("userId", note.getCreateBy());
        }
        if (note.getIsPublic() != null) {
            extraMetadata.put("isPublic", String.valueOf(note.getIsPublic()));
        }
        if (oConvertUtils.isNotEmpty(note.getCourseId())) {
            extraMetadata.put("courseId", note.getCourseId());
        }

        embeddingHandler.embeddingDocument(knowledgeId, doc, extraMetadata);
    }

    /**
     * 删除笔记的向量化数据
     */
    public void deleteNoteEmbedding(String noteId, String knowledgeId) {
        if (oConvertUtils.isEmpty(noteId)) {
            throw new JeecgBootException("笔记ID不能为空");
        }
        if (oConvertUtils.isEmpty(knowledgeId)) {
            throw new JeecgBootException("知识库ID不能为空");
        }
        AiragKnowledge knowledge = airagKnowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            log.warn("删除笔记向量跳过：知识库不存在，knowledgeId={}, noteId={}", knowledgeId, noteId);
            return;
        }
        String embedId = knowledge.getEmbedId();
        if (oConvertUtils.isEmpty(embedId)) {
            log.warn("删除笔记向量跳过：知识库未配置向量模型，knowledgeId={}, noteId={}", knowledgeId, noteId);
            return;
        }
        embeddingHandler.deleteEmbedDocsByDocIds(Collections.singletonList(noteId), embedId);
    }

    /**
     * 语义检索，返回按相似度降序排列的 noteId 列表（保序去重）
     *
     * EmbeddingHandler.searchEmbedding 返回 map 含 "docName" = noteId（写入时设置）。
     * topN 限制在 1~50 之间防止滥用。
     */
    public List<String> searchNotes(String knowledgeId, String queryText, Integer topN) {
        if (oConvertUtils.isEmpty(knowledgeId)) {
            throw new JeecgBootException("知识库ID不能为空");
        }
        if (oConvertUtils.isEmpty(queryText)) {
            throw new JeecgBootException("查询内容不能为空");
        }
        int safeTopN = (topN == null || topN <= 0) ? 10 : Math.min(topN, 50);

        AiragKnowledge knowledge = airagKnowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new JeecgBootException("知识库不存在");
        }

        // searchEmbedding 已按 score 降序，从 docName 提取 noteId
        List<Map<String, Object>> docs = embeddingHandler.searchEmbedding(knowledgeId, queryText, safeTopN, null);
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> noteIds = new LinkedHashSet<>();
        for (Map<String, Object> doc : docs) {
            Object docName = doc.get(EmbeddingHandler.EMBED_STORE_METADATA_DOCNAME);
            if (oConvertUtils.isNotEmpty(docName)) {
                noteIds.add(String.valueOf(docName));
            }
        }
        return new ArrayList<>(noteIds);
    }

    /**
     * 按范围语义检索笔记（向量元数据过滤）
     */
    public List<SemanticSearchResultDTO> searchNotesByScope(String knowledgeId,
                                                            String queryText,
                                                            NoteSearchScope scope,
                                                            String currentUserId,
                                                            Integer topN) {
        if (oConvertUtils.isEmpty(knowledgeId)) {
            throw new JeecgBootException("知识库ID不能为空");
        }
        if (oConvertUtils.isEmpty(queryText)) {
            throw new JeecgBootException("查询内容不能为空");
        }
        NoteSearchScope safeScope = scope == null ? NoteSearchScope.PERSONAL : scope;
        int safeTopN = (topN == null || topN <= 0) ? 20 : Math.min(topN, 50);

        Filter roleFilter = buildScopeFilter(safeScope, currentUserId);

        List<Map<String, Object>> docs = embeddingHandler.searchEmbedding(
                knowledgeId, queryText, safeTopN, null, roleFilter);
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> noteIds = new LinkedHashSet<>();
        for (Map<String, Object> doc : docs) {
            Object docName = doc.get(EmbeddingHandler.EMBED_STORE_METADATA_DOCNAME);
            if (oConvertUtils.isNotEmpty(docName)) {
                noteIds.add(String.valueOf(docName));
            }
        }
        if (noteIds.isEmpty()) {
            return Collections.emptyList();
        }

        QueryWrapper<AinoteNote> wrapper = new QueryWrapper<>();
        wrapper.in("id", noteIds);
        wrapper.ne("note_status", 3);
        applyScopeDbFilter(wrapper, safeScope, currentUserId);
        wrapper.select("id", "note_title", "is_public", "create_by", "course_id");
        List<AinoteNote> scopedNotes = ainoteNoteMapper.selectList(wrapper);
        if (scopedNotes == null || scopedNotes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, AinoteNote> noteMap = scopedNotes.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(AinoteNote::getId, note -> note, (a, b) -> a));

        Map<String, Map<String, Object>> docMap = new LinkedHashMap<>();
        for (Map<String, Object> doc : docs) {
            String noteId = oConvertUtils.getString(doc.get(EmbeddingHandler.EMBED_STORE_METADATA_DOCNAME));
            if (oConvertUtils.isNotEmpty(noteId) && !docMap.containsKey(noteId)) {
                docMap.put(noteId, doc);
            }
        }

        List<SemanticSearchResultDTO> result = new ArrayList<>();
        for (String noteId : noteIds) {
            AinoteNote note = noteMap.get(noteId);
            if (note == null) {
                continue;
            }
            SemanticSearchResultDTO dto = new SemanticSearchResultDTO();
            dto.setNoteId(note.getId());
            dto.setNoteTitle(note.getNoteTitle());
            dto.setIsPublic(note.getIsPublic());

            Map<String, Object> doc = docMap.get(noteId);
            if (doc != null) {
                dto.setSnippet(buildSnippet(oConvertUtils.getString(doc.get("content"), "")));
                dto.setScore(toDouble(doc.get("score")));
            }
            result.add(dto);
        }
        return result;
    }

    private Filter buildScopeFilter(NoteSearchScope scope, String currentUserId) {
        switch (scope) {
            case PERSONAL:
                if (oConvertUtils.isEmpty(currentUserId)) {
                    return metadataKey("userId").isEqualTo("__EMPTY__");
                }
                return metadataKey("userId").isEqualTo(currentUserId);
            case PUBLIC:
                return metadataKey("isPublic").isEqualTo("1");
            case COURSE:
                if (oConvertUtils.isEmpty(currentUserId)) {
                    return metadataKey("courseId").isEqualTo("__EMPTY__");
                }
                List<String> courseIds = getTeacherCourseIds(currentUserId);
                if (courseIds.isEmpty()) {
                    return metadataKey("courseId").isEqualTo("__EMPTY__");
                }
                return metadataKey("courseId").isIn(courseIds);
            case ALL:
            default:
                return null;
        }
    }

    private void applyScopeDbFilter(QueryWrapper<AinoteNote> wrapper, NoteSearchScope scope, String currentUserId) {
        switch (scope) {
            case PERSONAL:
                if (oConvertUtils.isNotEmpty(currentUserId)) {
                    wrapper.eq("create_by", currentUserId);
                }
                break;
            case PUBLIC:
                wrapper.eq("is_public", 1);
                break;
            case COURSE:
                List<String> dbCourseIds = getTeacherCourseIds(currentUserId);
                if (!dbCourseIds.isEmpty()) {
                    wrapper.in("course_id", dbCourseIds);
                } else {
                    wrapper.eq("course_id", "__EMPTY__");
                }
                break;
            case ALL:
            default:
                break;
        }
    }

    private List<String> getTeacherCourseIds(String teacherId) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<org.jeecg.modules.airag.teaching.entity.AinoteTeaching> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(org.jeecg.modules.airag.teaching.entity.AinoteTeaching::getTeacherId, teacherId);
            wrapper.select(org.jeecg.modules.airag.teaching.entity.AinoteTeaching::getCourseId);
            List<org.jeecg.modules.airag.teaching.entity.AinoteTeaching> teachings = ainoteTeachingMapper.selectList(wrapper);
            if (teachings == null || teachings.isEmpty()) {
                return Collections.emptyList();
            }
            return teachings.stream()
                    .map(org.jeecg.modules.airag.teaching.entity.AinoteTeaching::getCourseId)
                    .filter(id -> oConvertUtils.isNotEmpty(id))
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询教师课程ID失败: teacherId={}, err={}", teacherId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildSnippet(String content) {
        if (oConvertUtils.isEmpty(content)) {
            return "";
        }
        String normalized = content.replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim()
                .replaceAll("\\s{2,}", " ");
        int maxLen = 200;
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "...";
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

}
