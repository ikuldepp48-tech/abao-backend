package cn.iocoder.yudao.module.consulting.service.client;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientContactCreateReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientContactPageReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientContactUpdateReqVO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.client.ConsultingClientContactDO;

/**
 * 咨询客户联系人 Service 接口
 */
public interface ConsultingClientContactService {

    /**
     * 创建联系人
     *
     * @param createReqVO 创建信息
     * @return 联系人编号
     */
    Long createContact(ConsultingClientContactCreateReqVO createReqVO);

    /**
     * 更新联系人
     *
     * @param updateReqVO 更新信息
     */
    void updateContact(ConsultingClientContactUpdateReqVO updateReqVO);

    /**
     * 删除联系人
     *
     * @param id 联系人编号
     */
    void deleteContact(Long id);

    /**
     * 获得联系人
     *
     * @param id 联系人编号
     * @return 联系人
     */
    ConsultingClientContactDO getContact(Long id);

    /**
     * 获得联系人分页
     *
     * @param pageReqVO 分页查询
     * @return 联系人分页
     */
    PageResult<ConsultingClientContactDO> getContactPage(ConsultingClientContactPageReqVO pageReqVO);

}
