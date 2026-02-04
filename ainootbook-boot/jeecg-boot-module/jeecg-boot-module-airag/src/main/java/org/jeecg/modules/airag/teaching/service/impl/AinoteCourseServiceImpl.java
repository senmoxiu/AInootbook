package org.jeecg.modules.airag.teaching.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.airag.teaching.entity.AinoteCourse;
import org.jeecg.modules.airag.teaching.entity.AinoteTeaching;
import org.jeecg.modules.airag.teaching.mapper.AinoteCourseMapper;
import org.jeecg.modules.airag.teaching.mapper.AinoteTeachingMapper;
import org.jeecg.modules.airag.teaching.service.IAinoteCourseService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 课程表 Service 实现
 */
@Service
@RequiredArgsConstructor
public class AinoteCourseServiceImpl extends ServiceImpl<AinoteCourseMapper, AinoteCourse>
        implements IAinoteCourseService {

    private final AinoteTeachingMapper teachingMapper;

    @Override
    public boolean removeWithProtection(String id) {
        checkCourseReference(id);
        return removeById(id);
    }

    @Override
    public boolean removeBatchWithProtection(List<String> ids) {
        for (String id : ids) {
            checkCourseReference(id);
        }
        return removeByIds(ids);
    }

    /**
     * 检查课程是否被教学任务引用
     */
    private void checkCourseReference(String courseId) {
        long count = teachingMapper.selectCount(
                new QueryWrapper<AinoteTeaching>().eq("course_id", courseId)
        );
        if (count > 0) {
            throw new JeecgBootException("该课程已被教学任务引用，无法删除");
        }
    }
}
