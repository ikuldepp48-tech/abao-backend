package cn.iocoder.yudao.module.restaurant.service.combo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.combo.vo.*;
import cn.iocoder.yudao.module.restaurant.convert.combo.RestaurantComboConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.combo.RestaurantComboDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.combo.RestaurantComboItemDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.combo.RestaurantComboItemMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.combo.RestaurantComboMapper;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import cn.iocoder.yudao.module.restaurant.service.menu.MenuCacheEvictEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.COMBO_NOT_EXISTS;

@Service
@Validated
public class RestaurantComboServiceImpl implements RestaurantComboService {

    @Resource
    private RestaurantComboMapper comboMapper;

    @Resource
    private RestaurantComboItemMapper comboItemMapper;

    @Resource
    private RestaurantStoreMapper storeMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Long createCombo(RestaurantComboCreateReqVO createReqVO) {
        RestaurantComboDO combo = RestaurantComboConvert.INSTANCE.convert(createReqVO);
        comboMapper.insert(combo);
        if (createReqVO.getItems() != null && !createReqVO.getItems().isEmpty()) {
            List<RestaurantComboItemDO> items = RestaurantComboConvert.INSTANCE.convertItemList(createReqVO.getItems());
            items.forEach(item -> item.setComboId(combo.getId()));
            items.forEach(comboItemMapper::insert);
        }

        eventPublisher.publishEvent(new MenuCacheEvictEvent(allStoreIds()));
        return combo.getId();
    }

    @Override
    @Transactional
    public void updateCombo(RestaurantComboUpdateReqVO updateReqVO) {
        validateComboExists(updateReqVO.getId());
        RestaurantComboDO updateObj = RestaurantComboConvert.INSTANCE.convert(updateReqVO);
        comboMapper.updateById(updateObj);
        comboItemMapper.deleteByComboId(updateReqVO.getId());
        if (updateReqVO.getItems() != null && !updateReqVO.getItems().isEmpty()) {
            List<RestaurantComboItemDO> items = RestaurantComboConvert.INSTANCE.convertItemList(updateReqVO.getItems());
            items.forEach(item -> item.setComboId(updateReqVO.getId()));
            items.forEach(comboItemMapper::insert);
        }

        eventPublisher.publishEvent(new MenuCacheEvictEvent(allStoreIds()));
    }

    @Override
    @Transactional
    public void deleteCombo(Long id) {
        validateComboExists(id);
        comboItemMapper.deleteByComboId(id);
        comboMapper.deleteById(id);

        eventPublisher.publishEvent(new MenuCacheEvictEvent(allStoreIds()));
    }

    private void validateComboExists(Long id) {
        if (comboMapper.selectById(id) == null) {
            throw exception(COMBO_NOT_EXISTS);
        }
    }

    @Override
    public RestaurantComboDO getCombo(Long id) {
        return comboMapper.selectById(id);
    }

    @Override
    public List<RestaurantComboItemBaseVO> getComboItems(Long comboId) {
        List<RestaurantComboItemDO> items = comboItemMapper.selectListByComboId(comboId);
        return RestaurantComboConvert.INSTANCE.convertItemDOList(items);
    }

    @Override
    public List<RestaurantComboDO> getComboListByBrandId(Long brandId) {
        return comboMapper.selectListByBrandId(brandId);
    }

    @Override
    public PageResult<RestaurantComboDO> getComboPage(RestaurantComboPageReqVO pageReqVO) {
        return comboMapper.selectPage(pageReqVO);
    }

    private Set<Long> allStoreIds() {
        return storeMapper.selectList().stream()
                .map(RestaurantStoreDO::getId)
                .collect(Collectors.toSet());
    }

}
