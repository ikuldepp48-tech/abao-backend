package cn.iocoder.yudao.module.consulting.dal.mysql.client;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientContactPageReqVO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.client.ConsultingClientContactDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConsultingClientContactMapper extends BaseMapperX<ConsultingClientContactDO> {

    default PageResult<ConsultingClientContactDO> selectPage(ConsultingClientContactPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ConsultingClientContactDO>()
                .eqIfPresent(ConsultingClientContactDO::getClientId, reqVO.getClientId())
                .likeIfPresent(ConsultingClientContactDO::getName, reqVO.getName())
                .eqIfPresent(ConsultingClientContactDO::getRole, reqVO.getRole())
                .orderByDesc(ConsultingClientContactDO::getIsPrimary)
                .orderByAsc(ConsultingClientContactDO::getId));
    }

}
