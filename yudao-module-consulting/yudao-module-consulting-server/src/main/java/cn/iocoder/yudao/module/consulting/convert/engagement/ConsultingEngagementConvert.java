package cn.iocoder.yudao.module.consulting.convert.engagement;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo.ConsultingEngagementCreateReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo.ConsultingEngagementRespVO;
import cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo.ConsultingEngagementUpdateReqVO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.engagement.ConsultingEngagementDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ConsultingEngagementConvert {

    ConsultingEngagementConvert INSTANCE = Mappers.getMapper(ConsultingEngagementConvert.class);

    ConsultingEngagementDO convert(ConsultingEngagementCreateReqVO bean);

    ConsultingEngagementDO convert(ConsultingEngagementUpdateReqVO bean);

    ConsultingEngagementRespVO convert(ConsultingEngagementDO bean);

    List<ConsultingEngagementRespVO> convertList(List<ConsultingEngagementDO> list);

    PageResult<ConsultingEngagementRespVO> convertPage(PageResult<ConsultingEngagementDO> page);

}
