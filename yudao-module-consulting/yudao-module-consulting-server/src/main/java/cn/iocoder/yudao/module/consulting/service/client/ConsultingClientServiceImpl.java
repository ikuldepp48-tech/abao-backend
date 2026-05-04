package cn.iocoder.yudao.module.consulting.service.client;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientCreateReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientPageReqVO;
import cn.iocoder.yudao.module.consulting.controller.admin.client.vo.ConsultingClientUpdateReqVO;
import cn.iocoder.yudao.module.consulting.convert.client.ConsultingClientConvert;
import cn.iocoder.yudao.module.consulting.dal.dataobject.client.ConsultingClientDO;
import cn.iocoder.yudao.module.consulting.dal.dataobject.tenant.SystemTenantDO;
import cn.iocoder.yudao.module.consulting.dal.mysql.client.ConsultingClientMapper;
import cn.iocoder.yudao.module.consulting.dal.mysql.tenant.SystemTenantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.consulting.enums.ErrorCodeConstants.CLIENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.consulting.enums.ErrorCodeConstants.CLIENT_NAME_EXISTS;

@Service
@Validated
public class ConsultingClientServiceImpl implements ConsultingClientService {

    /**
     * 咨询客户套餐 ID
     */
    private static final Long CONSULTING_PACKAGE_ID = 115L;

    /**
     * 默认过期时间（无服务结束日期时使用）
     */
    private static final LocalDateTime DEFAULT_EXPIRE_TIME = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    @Resource
    private ConsultingClientMapper consultingClientMapper;

    @Resource
    private SystemTenantMapper systemTenantMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createClient(ConsultingClientCreateReqVO createReqVO) {
        validateClientNameUnique(null, createReqVO.getName());

        // 1. 创建 yudao 租户
        LocalDateTime expireTime = createReqVO.getServiceEndDate() != null
                ? createReqVO.getServiceEndDate().atTime(23, 59, 59)
                : DEFAULT_EXPIRE_TIME;

        SystemTenantDO tenant = SystemTenantDO.builder()
                .name(createReqVO.getName())
                .contactName("咨询客户")
                .status(0)
                .packageId(CONSULTING_PACKAGE_ID)
                .expireTime(expireTime)
                .accountCount(10)
                .creator("1")
                .createTime(LocalDateTime.now())
                .build();
        systemTenantMapper.insert(tenant);
        Long tenantId = tenant.getId();

        // 2. 创建客户档案，回写租户 ID
        ConsultingClientDO client = ConsultingClientConvert.INSTANCE.convert(createReqVO);
        client.setTenantId(tenantId);
        consultingClientMapper.insert(client);
        return client.getId();
    }

    @Override
    public void updateClient(ConsultingClientUpdateReqVO updateReqVO) {
        validateClientExists(updateReqVO.getId());
        validateClientNameUnique(updateReqVO.getId(), updateReqVO.getName());
        ConsultingClientDO updateObj = ConsultingClientConvert.INSTANCE.convert(updateReqVO);
        consultingClientMapper.updateById(updateObj);
    }

    @Override
    public void deleteClient(Long id) {
        validateClientExists(id);
        consultingClientMapper.deleteById(id);
    }

    private void validateClientExists(Long id) {
        if (consultingClientMapper.selectById(id) == null) {
            throw exception(CLIENT_NOT_EXISTS);
        }
    }

    private void validateClientNameUnique(Long id, String name) {
        if (StrUtil.isBlank(name)) {
            return;
        }
        ConsultingClientDO client = consultingClientMapper.selectByName(name);
        if (client == null) {
            return;
        }
        if (id == null || !client.getId().equals(id)) {
            throw exception(CLIENT_NAME_EXISTS);
        }
    }

    @Override
    public ConsultingClientDO getClient(Long id) {
        return consultingClientMapper.selectById(id);
    }

    @Override
    public List<ConsultingClientDO> getClientList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return ListUtil.empty();
        }
        return consultingClientMapper.selectByIds(ids);
    }

    @Override
    public PageResult<ConsultingClientDO> getClientPage(ConsultingClientPageReqVO pageReqVO) {
        return consultingClientMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ConsultingClientDO> getClientList() {
        return consultingClientMapper.selectList();
    }

}
