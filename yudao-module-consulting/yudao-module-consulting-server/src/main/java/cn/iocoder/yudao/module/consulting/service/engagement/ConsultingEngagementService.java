package cn.iocoder.yudao.module.consulting.service.engagement;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo.ConsultingEngagementCreateReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo.ConsultingEngagementPageReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo.ConsultingEngagementUpdateReqVO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.engagement.ConsultingEngagementDO;

/**
 * 咨询项目 Service 接口
 */
public interface ConsultingEngagementService {

    /**
     * 创建项目
     *
     * @param createReqVO 创建信息
     * @return 项目编号
     */
    Long createEngagement(ConsultingEngagementCreateReqVO createReqVO);

    /**
     * 更新项目
     *
     * @param updateReqVO 更新信息
     */
    void updateEngagement(ConsultingEngagementUpdateReqVO updateReqVO);

    /**
     * 删除项目
     *
     * @param id 项目编号
     */
    void deleteEngagement(Long id);

    /**
     * 获得项目
     *
     * @param id 项目编号
     * @return 项目
     */
    ConsultingEngagementDO getEngagement(Long id);

    /**
     * 获得项目分页
     *
     * @param pageReqVO 分页查询
     * @return 项目分页
     */
    PageResult<ConsultingEngagementDO> getEngagementPage(ConsultingEngagementPageReqVO pageReqVO);

    /**
     * 推进项目阶段（1-6）
     *
     * @param id    项目编号
     * @param phase 目标阶段
     */
    void advancePhase(Long id, Integer phase);

}
