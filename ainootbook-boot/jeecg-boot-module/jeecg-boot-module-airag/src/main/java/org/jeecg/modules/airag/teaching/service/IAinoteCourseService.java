package org.jeecg.modules.airag.teaching.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.airag.teaching.entity.AinoteCourse;

/**
 * 课程表 Service 接口
 */
public interface IAinoteCourseService extends IService<AinoteCourse> {

    /**
     * 删除课程（带引用保护）
     * @param id 课程ID
     * @return 是否删除成功
     */
    boolean removeWithProtection(String id);

    /**
     * 批量删除课程（带引用保护）
     * @param ids 课程ID列表
     * @return 是否删除成功
     */
    boolean removeBatchWithProtection(java.util.List<String> ids);
}
