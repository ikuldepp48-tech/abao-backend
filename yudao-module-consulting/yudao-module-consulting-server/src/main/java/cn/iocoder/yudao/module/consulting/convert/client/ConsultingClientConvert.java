package cn.iocoder.yudao.module.consulting.convert.client;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientCreateReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientRespVO;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientUpdateReqVO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.client.ConsultingClientDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ConsultingClientConvert {

    ConsultingClientConvert INSTANCE = Mappers.getMapper(ConsultingClientConvert.class);

    ConsultingClientDO convert(ConsultingClientCreateReqVO bean);

    ConsultingClientDO convert(ConsultingClientUpdateReqVO bean);

    ConsultingClientRespVO convert(ConsultingClientDO bean);

    List<ConsultingClientRespVO> convertList(List<ConsultingClientDO> list);

    PageResult<ConsultingClientRespVO> convertPage(PageResult<ConsultingClientDO> page);

}
