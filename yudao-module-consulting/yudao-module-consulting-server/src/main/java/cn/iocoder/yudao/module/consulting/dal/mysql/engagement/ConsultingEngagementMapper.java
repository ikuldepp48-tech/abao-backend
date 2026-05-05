package cn.iocoder.yudao.module.consulting.dal.mysql.engagement;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo.ConsultingEngagementPageReqVO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.engagement.ConsultingEngagementDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConsultingEngagementMapper extends BaseMapperX<ConsultingEngagementDO> {

    default PageResult<ConsultingEngagementDO> selectPage(ConsultingEngagementPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ConsultingEngagementDO>()
                .eqIfPresent(ConsultingEngagementDO::getClientId, reqVO.getClientId())
                .eqIfPresent(ConsultingEngagementDO::getType, reqVO.getType())
                .eqIfPresent(ConsultingEngagementDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ConsultingEngagementDO::getCurrentPhase, reqVO.getCurrentPhase())
                .likeIfPresent(ConsultingEngagementDO::getTitle, reqVO.getTitle())
                .orderByDesc(ConsultingEngagementDO::getId));
    }

    default ConsultingEngagementDO selectByCode(String code) {
        return selectOne(ConsultingEngagementDO::getCode, code);
    }

}
