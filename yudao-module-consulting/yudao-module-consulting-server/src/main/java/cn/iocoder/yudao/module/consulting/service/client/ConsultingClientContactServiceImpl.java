package cn.iocoder.yudao.module.consulting.service.client;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientContactCreateReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientContactPageReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientContactUpdateReqVO;
import cn.iocoder.yudao.module.consulting.convert.client.ConsultingClientContactConvert;
import cn.iocoder.yudao.module.consulting.dal.dataobject.client.ConsultingClientContactDO;
import cn.iocoder.yudao.module.consulting.dal.mysql.client.ConsultingClientContactMapper;
import cn.iocoder.yudao.module.consulting.dal.mysql.client.ConsultingClientMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.consulting.enums.ErrorCodeConstants.*;

import org.springframework.transaction.annotation.Transactional;

@Service
@Validated
@Transactional(rollbackFor = Exception.class)
public class ConsultingClientContactServiceImpl implements ConsultingClientContactService {

    @Resource
    private ConsultingClientContactMapper contactMapper;

    @Resource
    private ConsultingClientMapper clientMapper;

    @Override
    public Long createContact(ConsultingClientContactCreateReqVO createReqVO) {
        validateClientExists(createReqVO.getClientId());
        ConsultingClientContactDO contact = ConsultingClientContactConvert.INSTANCE.convert(createReqVO);
        contactMapper.insert(contact);
        return contact.getId();
    }

    @Override
    public void updateContact(ConsultingClientContactUpdateReqVO updateReqVO) {
        validateContactExists(updateReqVO.getId());
        validateClientExists(updateReqVO.getClientId());
        ConsultingClientContactDO updateObj = ConsultingClientContactConvert.INSTANCE.convert(updateReqVO);
        contactMapper.updateById(updateObj);
    }

    @Override
    public void deleteContact(Long id) {
        validateContactExists(id);
        contactMapper.deleteById(id);
    }

    @Override
    public ConsultingClientContactDO getContact(Long id) {
        return contactMapper.selectById(id);
    }

    @Override
    public PageResult<ConsultingClientContactDO> getContactPage(ConsultingClientContactPageReqVO pageReqVO) {
        return contactMapper.selectPage(pageReqVO);
    }

    private void validateContactExists(Long id) {
        if (contactMapper.selectById(id) == null) {
            throw exception(CONTACT_NOT_EXISTS);
        }
    }

    private void validateClientExists(Long clientId) {
        if (clientMapper.selectById(clientId) == null) {
            throw exception(CLIENT_NOT_EXISTS);
        }
    }

}
