package org.jeecg.modules.ainote.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.airag.llm.consts.LLMConsts;
import org.jeecg.modules.airag.llm.entity.AiragKnowledge;
import org.jeecg.modules.airag.llm.entity.AiragKnowledgeDoc;
import org.jeecg.modules.airag.llm.handler.EmbeddingHandler;
import org.jeecg.modules.airag.llm.service.IAiragKnowledgeService;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Ainote 知识库向量化服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AinoteEmbeddingService {

    private final EmbeddingHandler embeddingHandler;
    private final IAiragKnowledgeService airagKnowledgeService;

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
        // title 设为 noteId，使 docName metadata = noteId，供检索时还原
        doc.setTitle(note.getId());
        doc.setType(LLMConsts.KNOWLEDGE_DOC_TYPE_TEXT);
        doc.setContent(noteTitle + "\n" + noteContent);
        doc.setCreateBy(note.getCreateBy());
        doc.setCreateTime(new Date());
        if (note.getTenantId() != null) {
            doc.setTenantId(String.valueOf(note.getTenantId()));
        }
        embeddingHandler.embeddingDocument(knowledgeId, doc);
    }

    /**
     * 删除笔记的向量化数据
     */
    public void deleteNoteEmbedding(String noteId, String modelId) {
        if (oConvertUtils.isEmpty(noteId)) {
            throw new JeecgBootException("笔记ID不能为空");
        }
        if (oConvertUtils.isEmpty(modelId)) {
            throw new JeecgBootException("向量模型ID不能为空");
        }
        embeddingHandler.deleteEmbedDocsByDocIds(Collections.singletonList(noteId), modelId);
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
}
