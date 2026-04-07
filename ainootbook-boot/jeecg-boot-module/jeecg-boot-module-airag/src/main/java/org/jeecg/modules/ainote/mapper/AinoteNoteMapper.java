package org.jeecg.modules.ainote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.vo.AinoteNoteShareDetailVO;
import org.jeecg.modules.ainote.vo.AinoteTeacherNoteVO;
import org.jeecg.modules.ainote.vo.SemanticSearchResultDTO;

import java.util.Date;
import java.util.List;

/**
 * 笔记表 Mapper 接口
 */
public interface AinoteNoteMapper extends BaseMapper<AinoteNote> {

    /**
     * 新增笔记分享记录
     */
    @Insert("INSERT INTO ainote_share (id, note_id, share_code, share_type, expire_time, " +
            "view_count, status, create_by, update_by, sys_org_code, tenant_id) " +
            "VALUES (#{id}, #{noteId}, #{shareCode}, #{shareType}, #{expireTime}, " +
            "0, 1, #{createBy}, #{createBy}, #{sysOrgCode}, #{tenantId})")
    int insertShare(@Param("id") String id,
                    @Param("noteId") String noteId,
                    @Param("shareCode") String shareCode,
                    @Param("shareType") Integer shareType,
                    @Param("expireTime") Date expireTime,
                    @Param("createBy") String createBy,
                    @Param("sysOrgCode") String sysOrgCode,
                    @Param("tenantId") Integer tenantId);

    /**
     * 通过分享码查询分享详情（包含笔记信息），仅返回有效且未过期的分享
     * 增加租户隔离：必须传入 tenantId 参数进行过滤
     */
    @Select({
            "SELECT",
            "  s.id AS shareId,",
            "  s.note_id AS noteId,",
            "  s.share_code AS shareCode,",
            "  s.share_type AS shareType,",
            "  s.expire_time AS expireTime,",
            "  s.view_count AS shareViewCount,",
            "  s.status AS shareStatus,",
            "  n.student_id AS studentId,",
            "  n.course_id AS courseId,",
            "  n.chapter_id AS chapterId,",
            "  n.teaching_id AS teachingId,",
            "  n.note_title AS noteTitle,",
            "  n.note_content AS noteContent,",
            "  n.ai_summary AS aiSummary,",
            "  n.keywords AS keywords,",
            "  n.note_status AS noteStatus,",
            "  n.is_public AS isPublic,",
            "  n.view_count AS noteViewCount,",
            "  n.like_count AS likeCount,",
            "  n.create_time AS createTime,",
            "  n.update_time AS updateTime",
            "FROM ainote_share s",
            "JOIN ainote_note n ON n.id = s.note_id",
            "WHERE s.share_code = #{shareCode}",
            "  AND s.status = 1",
            "  AND (s.expire_time IS NULL OR s.expire_time > CURRENT_TIMESTAMP)",
            "  AND n.note_status <> 3",
            "  AND (#{tenantId} IS NULL OR s.tenant_id = #{tenantId})",
            "  AND (#{tenantId} IS NULL OR n.tenant_id = #{tenantId})"
    })
    AinoteNoteShareDetailVO selectShareDetailByCode(@Param("shareCode") String shareCode,
                                                    @Param("tenantId") Integer tenantId);

    /**
     * 分享查看次数 +1
     */
    @Update("UPDATE ainote_share SET view_count = view_count + 1 WHERE id = #{shareId}")
    int incShareViewCount(@Param("shareId") String shareId);

    /**
     * 笔记浏览次数 +1
     */
    @Update("UPDATE ainote_note SET view_count = view_count + 1 WHERE id = #{noteId}")
    int incNoteViewCount(@Param("noteId") String noteId);

    /**
     * 通过笔记ID批量查询语义检索结果基础信息
     */
    @Select({
            "<script>",
            "SELECT",
            "  n.id AS noteId,",
            "  n.note_title AS noteTitle,",
            "  u.realname AS authorName,",
            "  c.course_name AS courseTitle,",
            "  n.is_public AS isPublic",
            "FROM ainote_note n",
            "LEFT JOIN sys_user u ON u.id = n.create_by",
            "LEFT JOIN ainote_course c ON c.id = n.course_id",
            "WHERE n.id IN",
            "<foreach collection='noteIds' item='noteId' open='(' separator=',' close=')'>",
            "  #{noteId}",
            "</foreach>",
            "<if test='tenantId != null'>",
            "  AND n.tenant_id = #{tenantId}",
            "</if>",
            "</script>"
    })
    List<SemanticSearchResultDTO> selectSemanticResultBaseByNoteIds(@Param("noteIds") List<String> noteIds,
                                                                     @Param("tenantId") Integer tenantId);

    /**
     * 查询教师关联课程ID
     */
    @Select({
            "<script>",
            "SELECT DISTINCT course_id",
            "FROM ainote_teaching",
            "WHERE teacher_id = #{teacherId}",
            "<if test='tenantId != null'>",
            "  AND tenant_id = #{tenantId}",
            "</if>",
            "</script>"
    })
    List<String> selectTeacherCourseIds(@Param("teacherId") String teacherId,
                                        @Param("tenantId") Integer tenantId);

    /**
     * 分页查询教师名下学生笔记
     */
    IPage<AinoteTeacherNoteVO> queryTeacherNotePage(Page<AinoteTeacherNoteVO> page,
                                                    @Param("teacherId") String teacherId,
                                                    @Param("tenantId") Integer tenantId,
                                                    @Param("courseId") String courseId,
                                                    @Param("studentName") String studentName);
}
