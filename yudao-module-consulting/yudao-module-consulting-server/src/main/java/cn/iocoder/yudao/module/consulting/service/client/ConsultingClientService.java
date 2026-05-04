package cn.iocoder.yudao.module.consulting.service.client;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientCreateReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientPageReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientUpdateReqVO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.client.ConsultingClientDO;

import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;

/**
 * 咨询客户档案 Service 接口
 */
public interface ConsultingClientService {

    /**
     * 创建客户档案
     *
     * @param createReqVO 创建信息
     * @return 客户编号
     */
    Long createClient(@Valid ConsultingClientCreateReqVO createReqVO);

    /**
     * 更新客户档案
     *
     * @param updateReqVO 更新信息
     */
    void updateClient(@Valid ConsultingClientUpdateReqVO updateReqVO);

    /**
     * 删除客户档案
     *
     * @param id 客户编号
     */
    void deleteClient(Long id);

    /**
     * 获得客户档案
     *
     * @param id 客户编号
     * @return 客户档案
     */
    ConsultingClientDO getClient(Long id);

    /**
     * 获得客户档案列表
     *
     * @param ids 客户编号列表
     * @return 客户档案列表
     */
    List<ConsultingClientDO> getClientList(Collection<Long> ids);

    /**
     * 获得客户档案分页
     *
     * @param pageReqVO 分页查询
     * @return 客户档案分页
     */
    PageResult<ConsultingClientDO> getClientPage(ConsultingClientPageReqVO pageReqVO);

    /**
     * 获得所有客户档案列表（下拉用）
     *
     * @return 客户档案列表
     */
    List<ConsultingClientDO> getClientList();

}
