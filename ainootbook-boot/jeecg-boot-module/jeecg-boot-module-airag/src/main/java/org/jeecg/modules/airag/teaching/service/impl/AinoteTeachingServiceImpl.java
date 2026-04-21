package org.jeecg.modules.airag.teaching.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import org.jeecg.modules.airag.teaching.dto.BatchResult;
import org.jeecg.modules.airag.teaching.dto.BatchUpsertDTO;
import org.jeecg.modules.airag.teaching.entity.AinoteTeaching;
import org.jeecg.modules.airag.teaching.mapper.AinoteTeachingMapper;
import org.jeecg.modules.airag.teaching.service.IAinoteTeachingService;
import org.jeecg.modules.airag.teaching.vo.AinoteTeachingVO;
import org.jeecg.modules.ainote.util.RoleUtils;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.vo.SysDepartModel;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 教学关系表 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AinoteTeachingServiceImpl extends ServiceImpl<AinoteTeachingMapper, AinoteTeaching>
        implements IAinoteTeachingService {

    private final ISysBaseAPI sysBaseAPI;

    /** 学期格式：YYYY-YYYY-NN */
    private static final Pattern SEMESTER_PATTERN = Pattern.compile("^\\d{4}-\\d{4}-\\d{2}$");

    /** 允许的机构类型：2=学院，3=专业，4=班级（按 orgType 字段判断，orgCategory 在本项目数据中不可靠） */
    private static final Set<String> ALLOWED_ORG_TYPES = Set.of("2", "3", "4");

    @Override
    public void applyDataPermission(QueryWrapper<AinoteTeaching> wrapper) {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            throw new JeecgBootException("用户未登录");
        }
        // 租户隔离
        Integer tenantId = getCurrentTenantId();
        if (tenantId != null) {
            wrapper.eq("tenant_id", tenantId);
        }
        // 教师角色强制过滤（teacher_id 字段存逗号分隔多个ID，用 FIND_IN_SET 匹配）
        if (isTeacherRole(user)) {
            wrapper.apply("FIND_IN_SET({0}, teacher_id)", user.getId());
        }
    }

    @Override
    public void validateDepartId(String departId) {
        if (departId == null || departId.isBlank()) {
            throw new JeecgBootException("组织ID不能为空");
        }
        SysDepartModel depart = sysBaseAPI.selectAllById(departId);
        if (depart == null) {
            throw new JeecgBootException("组织不存在：" + departId);
        }
        if (!ALLOWED_ORG_TYPES.contains(depart.getOrgType())) {
            throw new JeecgBootException("组织类别不合法，仅支持学院/专业/班级");
        }
    }

    @Override
    public void validateSemester(String semester) {
        if (semester == null || !SEMESTER_PATTERN.matcher(semester).matches()) {
            throw new JeecgBootException("学期格式错误，应为 YYYY-YYYY-NN（如 2024-2025-01）");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchResult batchUpsert(BatchUpsertDTO dto) {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            throw new JeecgBootException("用户未登录");
        }

        // 参数校验
        validateSemester(dto.getSemester());
        List<String> departIds = dto.getDepartIds().stream()
                .distinct()
                .collect(Collectors.toList());

        // 校验所有 departId
        for (String departId : departIds) {
            validateDepartId(departId);
        }

        // 查询已存在的记录
        List<AinoteTeaching> existing = baseMapper.selectExistingByKeys(
                dto.getCourseId(), departIds, dto.getSemester());
        Set<String> existingDepartIds = existing.stream()
                .map(AinoteTeaching::getDepartId)
                .collect(Collectors.toSet());

        // 计算需要新增的
        List<String> toInsert = departIds.stream()
                .filter(id -> !existingDepartIds.contains(id))
                .collect(Collectors.toList());

        int successCount = 0;
        List<BatchResult.FailedItem> failedList = new ArrayList<>();

        // 已存在的记录放入 failedList
        for (String departId : existingDepartIds) {
            if (departIds.contains(departId)) {
                failedList.add(new BatchResult.FailedItem(departId, "已存在相同配置"));
            }
        }

        // 批量插入新记录
        for (String departId : toInsert) {
            try {
                AinoteTeaching teaching = new AinoteTeaching();
                teaching.setTeacherId(user.getId());
                teaching.setCourseId(dto.getCourseId());
                teaching.setDepartId(departId);
                teaching.setSemester(dto.getSemester());
                teaching.setAcademicYear(dto.getAcademicYear());
                teaching.setStatus(1);
                teaching.setTenantId(getCurrentTenantId());
                baseMapper.insert(teaching);
                successCount++;
            } catch (DuplicateKeyException e) {
                // 并发下唯一约束兜底
                failedList.add(new BatchResult.FailedItem(departId, "已存在相同配置"));
            } catch (Exception e) {
                log.error("插入教学关系失败: departId={}", departId, e);
                // 不暴露内部异常详情，仅返回通用错误信息
                failedList.add(new BatchResult.FailedItem(departId, "插入失败，请稍后重试"));
            }
        }

        return new BatchResult(successCount, failedList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeleteWithPermission(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            throw new JeecgBootException("用户未登录");
        }

        // 查询待删除记录并校验权限
        QueryWrapper<AinoteTeaching> wrapper = new QueryWrapper<>();
        wrapper.in("id", ids);
        applyDataPermission(wrapper);

        List<AinoteTeaching> records = list(wrapper);
        if (records.size() != ids.size()) {
            throw new JeecgBootException("部分记录无权限删除或不存在");
        }

        return removeByIds(ids);
    }

    @Override
    public IPage<AinoteTeachingVO> queryTeachingVoPage(
            Page<AinoteTeachingVO> page,
            QueryWrapper<AinoteTeaching> wrapper) {
        return baseMapper.queryTeachingVoPage(page, wrapper);
    }

    /**
     * 查询指定部门及其所有子孙部门的 ID 列表
     * 通过 ISysBaseAPI.getAllSysDepart() 获取全量部门树，递归提取子孙节点
     */
    @Override
    public List<String> getDepartAndSubIds(String departId) {
        List<SysDepartModel> allDeparts = sysBaseAPI.getAllSysDepart();
        List<String> result = new ArrayList<>();
        result.add(departId);
        collectSubIds(departId, allDeparts, result);
        return result;
    }

    /**
     * 递归收集子孙部门 ID
     */
    private void collectSubIds(String parentId, List<SysDepartModel> allDeparts, List<String> result) {
        for (SysDepartModel depart : allDeparts) {
            if (parentId.equals(depart.getParentId())) {
                result.add(depart.getId());
                collectSubIds(depart.getId(), allDeparts, result);
            }
        }
    }

    /**
     * 判断是否为教师角色
     */
    private boolean isTeacherRole(LoginUser user) {
        return RoleUtils.hasRole(user.getRoleCode(), "teacher")
                && !RoleUtils.hasRole(user.getRoleCode(), "admin");
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
