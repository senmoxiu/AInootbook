package org.jeecg.modules.airag.teaching.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.TokenUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.airag.teaching.dto.AinoteCourseSelectionAddDTO;
import org.jeecg.modules.airag.teaching.entity.AinoteCourseSelection;
import org.jeecg.modules.airag.teaching.entity.AinoteTeaching;
import org.jeecg.modules.airag.teaching.mapper.AinoteCourseSelectionMapper;
import org.jeecg.modules.airag.teaching.service.IAinoteCourseSelectionService;
import org.jeecg.modules.airag.teaching.service.IAinoteTeachingService;
import org.jeecg.modules.airag.teaching.vo.AinoteCourseSelectionVO;
import org.jeecg.modules.ainote.util.RoleUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 选课表 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AinoteCourseSelectionServiceImpl
        extends ServiceImpl<AinoteCourseSelectionMapper, AinoteCourseSelection>
        implements IAinoteCourseSelectionService {

    private final IAinoteTeachingService ainoteTeachingService;

    @Override
    public void applyDataPermission(QueryWrapper<AinoteCourseSelection> wrapper) {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            throw new JeecgBootException("用户未登录");
        }
        // 租户隔离
        Integer tenantId = getCurrentTenantId();
        if (tenantId != null) {
            wrapper.eq("tenant_id", tenantId);
        }
        Set<String> roles = RoleUtils.parseRoles(user.getRoleCode());
        if (roles.contains("admin")) {
            // admin 仅租户隔离，无需额外过滤
            return;
        }
        if (roles.contains("teacher")) {
            // 教师只能查看自己教学任务下的选课记录（参数化防注入）
            wrapper.apply("teaching_id IN (SELECT id FROM ainote_teaching WHERE teacher_id = {0})", user.getId());
            return;
        }
        // 默认按学生维度过滤（student 角色或其他角色）
        wrapper.eq("student_id", user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSelection(AinoteCourseSelectionAddDTO dto) {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            throw new JeecgBootException("用户未登录");
        }

        // 确定学生ID：admin 可指定，否则取当前登录用户
        String studentId = dto.getStudentId();
        Set<String> roles = RoleUtils.parseRoles(user.getRoleCode());
        if (!roles.contains("admin") || studentId == null || studentId.isBlank()) {
            studentId = user.getId();
        }

        // 从 teaching 记录获取关联信息（带租户隔离）
        Integer tenantId = getCurrentTenantId();
        QueryWrapper<AinoteTeaching> teachingWrapper = new QueryWrapper<>();
        teachingWrapper.eq("id", dto.getTeachingId());
        if (tenantId != null) {
            teachingWrapper.eq("tenant_id", tenantId);
        }
        AinoteTeaching teaching = ainoteTeachingService.getOne(teachingWrapper);
        if (teaching == null) {
            throw new JeecgBootException("教学任务不存在或无权限访问");
        }

        final String finalStudentId = studentId;

        // 检查是否存在已退课记录（status=0），若存在则恢复（带租户隔离）
        QueryWrapper<AinoteCourseSelection> existWrapper = new QueryWrapper<>();
        existWrapper.eq("student_id", finalStudentId)
                .eq("teaching_id", dto.getTeachingId());
        if (tenantId != null) {
            existWrapper.eq("tenant_id", tenantId);
        }
        AinoteCourseSelection existing = getOne(existWrapper, false);

        if (existing != null) {
            if (existing.getStatus() != null && existing.getStatus() == 1) {
                throw new JeecgBootException("已选该课程，请勿重复选课");
            }
            // 恢复已退课记录
            existing.setStatus(1);
            updateById(existing);
            return;
        }

        // 新建选课记录，双轨写入 class_id 和 depart_id
        AinoteCourseSelection selection = new AinoteCourseSelection();
        selection.setStudentId(finalStudentId);
        selection.setTeachingId(dto.getTeachingId());
        selection.setCourseId(teaching.getCourseId());
        selection.setSemester(teaching.getSemester());
        selection.setAcademicYear(teaching.getAcademicYear());
        // 双轨并存：depart_id 优先从 teaching 获取，classId 同步写入
        selection.setDepartId(teaching.getDepartId() != null ? teaching.getDepartId() : dto.getDepartId());
        selection.setClassId(teaching.getDepartId() != null ? teaching.getDepartId() : dto.getClassId());
        selection.setStatus(1);
        selection.setSelectedAt(new Date());
        selection.setTenantId(getCurrentTenantId());

        try {
            save(selection);
        } catch (DuplicateKeyException e) {
            // 并发场景下唯一约束兜底
            throw new JeecgBootException("已选该课程，请勿重复选课");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteById(String id) {
        softDeleteByIds(Collections.singletonList(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 校验数据权限后再软删除
        QueryWrapper<AinoteCourseSelection> checkWrapper = new QueryWrapper<>();
        checkWrapper.in("id", ids);
        applyDataPermission(checkWrapper);
        long count = count(checkWrapper);
        if (count != ids.size()) {
            throw new JeecgBootException("部分记录无权限操作或不存在");
        }

        UpdateWrapper<AinoteCourseSelection> updateWrapper = new UpdateWrapper<>();
        updateWrapper.in("id", ids).set("status", 0);
        update(updateWrapper);
    }

    @Override
    public IPage<AinoteCourseSelectionVO> querySelectionVoPage(
            Page<AinoteCourseSelectionVO> page,
            QueryWrapper<AinoteCourseSelection> wrapper) {
        return baseMapper.querySelectionVoPage(page, wrapper);
    }

    /**
     * 获取当前租户ID
     */
    private Integer getCurrentTenantId() {
        try {
            HttpServletRequest request = SpringContextUtils.getHttpServletRequest();
            String tenantIdStr = TokenUtils.getTenantIdByRequest(request);
            if (oConvertUtils.isNotEmpty(tenantIdStr)) {
                return Integer.parseInt(tenantIdStr);
            }
        } catch (Exception e) {
            log.warn("获取租户ID失败", e);
        }
        return null;
    }
}
