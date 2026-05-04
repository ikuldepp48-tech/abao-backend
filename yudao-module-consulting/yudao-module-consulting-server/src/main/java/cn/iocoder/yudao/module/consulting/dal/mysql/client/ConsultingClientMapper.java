package cn.iocoder.yudao.module.consulting.dal.mysql.client;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientPageReqVO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.client.ConsultingClientDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConsultingClientMapper extends BaseMapperX<ConsultingClientDO> {

    default PageResult<ConsultingClientDO> selectPage(ConsultingClientPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ConsultingClientDO>()
                .likeIfPresent(ConsultingClientDO::getName, reqVO.getName())
                .likeIfPresent(ConsultingClientDO::getShortName, reqVO.getShortName())
                .eqIfPresent(ConsultingClientDO::getIndustry, reqVO.getIndustry())
                .eqIfPresent(ConsultingClientDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ConsultingClientDO::getConsultantUserId, reqVO.getConsultantUserId())
                .orderByDesc(ConsultingClientDO::getId));
    }

    default ConsultingClientDO selectByName(String name) {
        return selectOne(ConsultingClientDO::getName, name);
    }

}
