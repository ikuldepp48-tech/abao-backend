package cn.iocoder.yudao.module.restaurant.service.store;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStorePageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.store.vo.RestaurantStoreUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.convert.store.RestaurantStoreConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.*;

import org.springframework.transaction.annotation.Transactional;

@Service
@Validated
@Transactional(rollbackFor = Exception.class)
public class RestaurantStoreServiceImpl implements RestaurantStoreService {

    @Resource
    private RestaurantStoreMapper restaurantStoreMapper;

    @Override
    public Long createStore(RestaurantStoreCreateReqVO createReqVO) {
        validateStoreNameUnique(null, createReqVO.getName());
        validateStoreCodeUnique(null, createReqVO.getCode());
        RestaurantStoreDO store = RestaurantStoreConvert.INSTANCE.convert(createReqVO);
        restaurantStoreMapper.insert(store);
        return store.getId();
    }

    @Override
    public void updateStore(RestaurantStoreUpdateReqVO updateReqVO) {
        validateStoreExists(updateReqVO.getId());
        validateStoreNameUnique(updateReqVO.getId(), updateReqVO.getName());
        validateStoreCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        RestaurantStoreDO updateObj = RestaurantStoreConvert.INSTANCE.convert(updateReqVO);
        restaurantStoreMapper.updateById(updateObj);
    }

    @Override
    public void deleteStore(Long id) {
        validateStoreExists(id);
        restaurantStoreMapper.deleteById(id);
    }

    private void validateStoreExists(Long id) {
        if (restaurantStoreMapper.selectById(id) == null) {
            throw exception(STORE_NOT_EXISTS);
        }
    }

    private void validateStoreNameUnique(Long id, String name) {
        if (StrUtil.isBlank(name)) {
            return;
        }
        RestaurantStoreDO store = restaurantStoreMapper.selectByName(name);
        if (store == null) {
            return;
        }
        if (id == null || !store.getId().equals(id)) {
            throw exception(STORE_NAME_EXISTS);
        }
    }

    private void validateStoreCodeUnique(Long id, String code) {
        if (StrUtil.isBlank(code)) {
            return;
        }
        RestaurantStoreDO store = restaurantStoreMapper.selectByCode(code);
        if (store == null) {
            return;
        }
        if (id == null || !store.getId().equals(id)) {
            throw exception(STORE_CODE_EXISTS);
        }
    }

    @Override
    public RestaurantStoreDO getStore(Long id) {
        return restaurantStoreMapper.selectById(id);
    }

    @Override
    public List<RestaurantStoreDO> getStoreList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return ListUtil.empty();
        }
        return restaurantStoreMapper.selectByIds(ids);
    }

    @Override
    public PageResult<RestaurantStoreDO> getStorePage(RestaurantStorePageReqVO pageReqVO) {
        return restaurantStoreMapper.selectPage(pageReqVO);
    }

    @Override
    public List<RestaurantStoreDO> getStoreList() {
        return restaurantStoreMapper.selectList();
    }

}
