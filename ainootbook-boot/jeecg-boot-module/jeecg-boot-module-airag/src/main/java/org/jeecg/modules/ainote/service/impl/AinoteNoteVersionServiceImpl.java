package org.jeecg.modules.ainote.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.ainote.entity.AinoteNoteVersion;
import org.jeecg.modules.ainote.mapper.AinoteNoteVersionMapper;
import org.jeecg.modules.ainote.service.IAinoteNoteVersionService;
import org.jeecg.modules.ainote.vo.AinoteNoteVersionVO;
import org.springframework.stereotype.Service;

/**
 * 笔记版本表 Service 实现
 */
@Service
public class AinoteNoteVersionServiceImpl extends ServiceImpl<AinoteNoteVersionMapper, AinoteNoteVersion>
        implements IAinoteNoteVersionService {

    @Override
    public IPage<AinoteNoteVersionVO> queryVersionPage(
            String noteId, Integer tenantId, Integer pageNo, Integer pageSize) {
        long safePageNo = (pageNo == null || pageNo < 1) ? 1L : pageNo.longValue();
        long safePageSize = (pageSize == null || pageSize < 1) ? 20L : pageSize.longValue();
        Page<AinoteNoteVersionVO> page = new Page<>(safePageNo, safePageSize);
        return baseMapper.selectVersionPage(page, noteId, tenantId);
    }
}
