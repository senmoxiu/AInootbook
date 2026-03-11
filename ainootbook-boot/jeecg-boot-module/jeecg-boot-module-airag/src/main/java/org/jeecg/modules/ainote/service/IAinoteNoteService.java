package org.jeecg.modules.ainote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.ainote.dto.AinoteNoteCreateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteShareCreateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteUpdateDTO;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.vo.AinoteNoteShareDetailVO;
import org.jeecg.modules.ainote.vo.AinoteNoteShareVO;

import java.util.List;

/**
 * 笔记表 Service 接口
 */
public interface IAinoteNoteService extends IService<AinoteNote> {

    /**
     * 应用数据权限过滤到查询条件（租户隔离 + owner/public）
     */
    void applyDataPermission(QueryWrapper<AinoteNote> wrapper);

    /**
     * 创建笔记（强制填充 studentId/tenantId）
     */
    String createNote(AinoteNoteCreateDTO dto);

    /**
     * 编辑笔记（仅 owner/admin）
     */
    void updateNote(AinoteNoteUpdateDTO dto);

    /**
     * 逻辑删除笔记（note_status=3，仅 owner/admin）
     */
    boolean deleteLogicalById(String id);

    /**
     * 批量逻辑删除笔记（note_status=3，仅 owner/admin）
     */
    boolean deleteLogicalByIds(List<String> ids);

    /**
     * 通过ID查询笔记（带数据权限）
     */
    AinoteNote getByIdWithPermission(String id);

    /**
     * 创建分享（16位 SecureRandom 分享码）
     */
    AinoteNoteShareVO createShare(AinoteNoteShareCreateDTO dto);

    /**
     * 通过分享码查询分享详情（有效且未过期）
     */
    AinoteNoteShareDetailVO queryByShareCode(String shareCode);

    /**
     * 查询公开笔记广场（仅展示公开笔记，带租户隔离）
     */
    IPage<AinoteNote> queryPublicNotes(Integer pageNo, Integer pageSize, String keyword);

    /**
     * 安全导入Excel（强制覆盖敏感字段，仅管理员可用）
     */
    Result<?> importExcelWithSecurity(HttpServletRequest request, HttpServletResponse response, LoginUser user);
}
