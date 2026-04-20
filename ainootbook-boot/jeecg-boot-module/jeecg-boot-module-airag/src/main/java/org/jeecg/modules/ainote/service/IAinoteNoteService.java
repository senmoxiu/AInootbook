package org.jeecg.modules.ainote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.ainote.dto.AinoteNoteCreateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteRegenerateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteShareCreateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteUpdateDTO;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.entity.AinoteNoteVersion;
import org.jeecg.modules.ainote.vo.AinoteNoteRegenerateVO;
import org.jeecg.modules.ainote.vo.AinoteNoteVersionVO;
import org.jeecg.modules.ainote.vo.AinoteNoteShareDetailVO;
import org.jeecg.modules.ainote.vo.AinoteNoteShareVO;
import org.jeecg.modules.ainote.vo.AinoteTeacherNoteVO;

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
     * 回滚笔记到指定版本（仅 owner/admin）
     */
    AinoteNote rollbackToVersion(String noteId, Integer targetVersion);

    /**
     * 基于 baseVersion 乐观锁重新生成笔记
     */
    AinoteNoteRegenerateVO regenerateNote(AinoteNoteRegenerateDTO dto);

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
     * 点赞笔记（幂等，已点赞则忽略）
     */
    void likeNote(String noteId);

    /**
     * 取消点赞（幂等，未点赞则忽略）
     */
    void unlikeNote(String noteId);

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
     * 分页查询教师名下学生笔记
     */
    IPage<AinoteTeacherNoteVO> queryTeacherNotePage(Page<AinoteTeacherNoteVO> page, String teacherId,
                                                    Integer tenantId, String courseId, String studentName);

    /**
     * 分页查询笔记版本历史
     */
    IPage<AinoteNoteVersionVO> queryVersionPage(String noteId, Integer pageNo, Integer pageSize);

    /**
     * 查询版本详情（含完整内容，仅 owner/admin）
     */
    AinoteNoteVersion queryVersionDetail(String versionId);

    /**
     * 安全导入Excel（强制覆盖敏感字段，仅管理员可用）
     */
    Result<?> importExcelWithSecurity(HttpServletRequest request, HttpServletResponse response, LoginUser user);
}
