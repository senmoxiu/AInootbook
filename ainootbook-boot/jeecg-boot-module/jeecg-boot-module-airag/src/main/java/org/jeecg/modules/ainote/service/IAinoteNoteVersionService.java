package org.jeecg.modules.ainote.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.ainote.entity.AinoteNoteVersion;
import org.jeecg.modules.ainote.vo.AinoteNoteVersionVO;

/**
 * 笔记版本表 Service 接口
 */
public interface IAinoteNoteVersionService extends IService<AinoteNoteVersion> {

    /**
     * 分页查询笔记版本历史
     */
    IPage<AinoteNoteVersionVO> queryVersionPage(
            String noteId, Integer tenantId, Integer pageNo, Integer pageSize);
}
