package cn.iocoder.yudao.module.consulting.service.engagement;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo.ConsultingEngagementCreateReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo.ConsultingEngagementPageReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo.ConsultingEngagementUpdateReqVO;
import cn.iocoder.yudao.module.consulting.convert.engagement.ConsultingEngagementConvert;
import cn.iocoder.yudao.module.consulting.dal.dataobject.engagement.ConsultingEngagementDO;
import cn.iocoder.yudao.module.consulting.dal.mysql.engagement.ConsultingEngagementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.consulting.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ConsultingEngagementServiceImpl implements ConsultingEngagementService {

    @Resource
    private ConsultingEngagementMapper engagementMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createEngagement(ConsultingEngagementCreateReqVO createReqVO) {
        validateCodeUnique(null, createReqVO.getCode());
        ConsultingEngagementDO engagement = ConsultingEngagementConvert.INSTANCE.convert(createReqVO);
        engagementMapper.insert(engagement);
        return engagement.getId();
    }

    @Override
    public void updateEngagement(ConsultingEngagementUpdateReqVO updateReqVO) {
        validateEngagementExists(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        ConsultingEngagementDO updateObj = ConsultingEngagementConvert.INSTANCE.convert(updateReqVO);
        engagementMapper.updateById(updateObj);
    }

    @Override
    public void deleteEngagement(Long id) {
        validateEngagementExists(id);
        engagementMapper.deleteById(id);
    }

    @Override
    public ConsultingEngagementDO getEngagement(Long id) {
        return engagementMapper.selectById(id);
    }

    @Override
    public PageResult<ConsultingEngagementDO> getEngagementPage(ConsultingEngagementPageReqVO pageReqVO) {
        return engagementMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void advancePhase(Long id, Integer phase) {
        ConsultingEngagementDO engagement = validateEngagementExists(id);

        // 1. 阶段必须在 1-6
        if (phase == null || phase < 1 || phase > 6) {
            throw exception(ENGAGEMENT_PHASE_INVALID);
        }

        // 2. 项目必须处于进行中
        if (!"in_progress".equals(engagement.getStatus())) {
            throw exception(ENGAGEMENT_PHASE_NOT_IN_PROGRESS);
        }

        // 3. 不能跳过阶段
        int currentPhase = engagement.getCurrentPhase() == null ? 0 : engagement.getCurrentPhase();
        if (phase != currentPhase + 1) {
            throw exception(ENGAGEMENT_PHASE_SKIP, currentPhase, phase);
        }

        // 4. 已完成阶段不能重复完成
        if (isPhaseDone(engagement, phase)) {
            throw exception(ENGAGEMENT_PHASE_ALREADY_DONE, phase);
        }

        // 5. 设置阶段完成时间
        setPhaseDoneTime(engagement, phase, LocalDateTime.now());

        // 6. 更新当前阶段
        engagement.setCurrentPhase(phase);

        // 7. 阶段6完成 → 项目自动结束
        if (phase == 6) {
            engagement.setStatus("completed");
            engagement.setActualEndDate(LocalDate.now());
        }

        engagementMapper.updateById(engagement);
    }

    private ConsultingEngagementDO validateEngagementExists(Long id) {
        ConsultingEngagementDO engagement = engagementMapper.selectById(id);
        if (engagement == null) {
            throw exception(ENGAGEMENT_NOT_EXISTS);
        }
        return engagement;
    }

    private void validateCodeUnique(Long id, String code) {
        if (StrUtil.isBlank(code)) {
            return;
        }
        ConsultingEngagementDO existing = engagementMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (id == null || !existing.getId().equals(id)) {
            throw exception(ENGAGEMENT_CODE_EXISTS);
        }
    }

    private boolean isPhaseDone(ConsultingEngagementDO engagement, int phase) {
        return switch (phase) {
            case 1 -> engagement.getPhase1DoneAt() != null;
            case 2 -> engagement.getPhase2DoneAt() != null;
            case 3 -> engagement.getPhase3DoneAt() != null;
            case 4 -> engagement.getPhase4DoneAt() != null;
            case 5 -> engagement.getPhase5DoneAt() != null;
            case 6 -> engagement.getPhase6DoneAt() != null;
            default -> false;
        };
    }

    private void setPhaseDoneTime(ConsultingEngagementDO engagement, int phase, LocalDateTime time) {
        switch (phase) {
            case 1 -> engagement.setPhase1DoneAt(time);
            case 2 -> engagement.setPhase2DoneAt(time);
            case 3 -> engagement.setPhase3DoneAt(time);
            case 4 -> engagement.setPhase4DoneAt(time);
            case 5 -> engagement.setPhase5DoneAt(time);
            case 6 -> engagement.setPhase6DoneAt(time);
        }
    }

}
