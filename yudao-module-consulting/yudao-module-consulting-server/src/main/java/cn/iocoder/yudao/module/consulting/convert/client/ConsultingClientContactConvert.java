package cn.iocoder.yudao.module.consulting.convert.client;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientContactCreateReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientContactRespVO;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientContactUpdateReqVO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.client.ConsultingClientContactDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ConsultingClientContactConvert {

    ConsultingClientContactConvert INSTANCE = Mappers.getMapper(ConsultingClientContactConvert.class);

    ConsultingClientContactDO convert(ConsultingClientContactCreateReqVO bean);

    ConsultingClientContactDO convert(ConsultingClientContactUpdateReqVO bean);

    ConsultingClientContactRespVO convert(ConsultingClientContactDO bean);

    List<ConsultingClientContactRespVO> convertList(List<ConsultingClientContactDO> list);

    PageResult<ConsultingClientContactRespVO> convertPage(PageResult<ConsultingClientContactDO> page);

}
