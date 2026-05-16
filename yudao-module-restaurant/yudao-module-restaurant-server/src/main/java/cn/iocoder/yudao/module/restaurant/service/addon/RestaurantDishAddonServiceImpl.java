package cn.iocoder.yudao.module.restaurant.service.addon;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.RestaurantDishAddonCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.RestaurantDishAddonPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo.RestaurantDishAddonUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.convert.addon.RestaurantDishAddonConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishAddonDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishSpuAddonRelDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.addon.RestaurantDishAddonMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.addon.RestaurantDishSpuAddonRelMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreDishMapper;
import cn.iocoder.yudao.module.restaurant.service.menu.MenuCacheEvictEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.DISH_ADDON_NOT_EXISTS;
import static cn.iocoder.yudao.module.restaurant.enums.LogRecordConstants.*;

@Service
@Validated
public class RestaurantDishAddonServiceImpl implements RestaurantDishAddonService {

    @Resource
    private RestaurantDishAddonMapper addonMapper;

    @Resource
    private RestaurantDishSpuAddonRelMapper spuAddonRelMapper;

    @Resource
    private RestaurantStoreDishMapper storeDishMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    @LogRecord(type = ADDON_TYPE, subType = ADDON_CREATE_SUB_TYPE, bizNo = "{{#createReqVO.brandId}}",
            success = ADDON_CREATE_SUCCESS,
            extra = "{\"extraPrice\":\"{{#createReqVO.extraPrice}}\"}")
    public Long createAddon(RestaurantDishAddonCreateReqVO createReqVO) {
        RestaurantDishAddonDO addon = RestaurantDishAddonConvert.INSTANCE.convert(createReqVO);
        addonMapper.insert(addon);
        return addon.getId();
    }

    @Override
    @LogRecord(type = ADDON_TYPE, subType = ADDON_UPDATE_SUB_TYPE, bizNo = "{{#updateReqVO.id}}",
            success = ADDON_UPDATE_SUCCESS,
            extra = "{\"extraPrice\":\"{{#updateReqVO.extraPrice}}\"}")
    public void updateAddon(RestaurantDishAddonUpdateReqVO updateReqVO) {
        validateAddonExists(updateReqVO.getId());
        RestaurantDishAddonDO updateObj = RestaurantDishAddonConvert.INSTANCE.convert(updateReqVO);
        addonMapper.updateById(updateObj);

        eventPublisher.publishEvent(new MenuCacheEvictEvent(findStoreIdsByAddonId(updateReqVO.getId())));
    }

    @Override
    @LogRecord(type = ADDON_TYPE, subType = ADDON_DELETE_SUB_TYPE, bizNo = "{{#id}}",
            success = ADDON_DELETE_SUCCESS)
    public void deleteAddon(Long id) {
        validateAddonExists(id);

        Set<Long> storeIds = findStoreIdsByAddonId(id);
        addonMapper.deleteById(id);

        eventPublisher.publishEvent(new MenuCacheEvictEvent(storeIds));
    }

    private void validateAddonExists(Long id) {
        if (addonMapper.selectById(id) == null) {
            throw exception(DISH_ADDON_NOT_EXISTS);
        }
    }

    @Override
    public RestaurantDishAddonDO getAddon(Long id) {
        return addonMapper.selectById(id);
    }

    @Override
    public List<RestaurantDishAddonDO> getAddonListByGroupName(Long brandId, String groupName) {
        return addonMapper.selectListByGroupName(brandId, groupName);
    }

    @Override
    public List<String> getDistinctGroupNames(Long brandId) {
        return addonMapper.selectDistinctGroupNames(brandId);
    }

    @Override
    public PageResult<RestaurantDishAddonDO> getAddonPage(RestaurantDishAddonPageReqVO pageReqVO) {
        return addonMapper.selectPage(pageReqVO);
    }

    @Override
    public List<RestaurantDishAddonDO> getAddonListByBrand(Long brandId) {
        return addonMapper.selectListByBrand(brandId);
    }

    private Set<Long> findStoreIdsByAddonId(Long addonId) {
        Set<Long> spuIds = spuAddonRelMapper.selectList().stream()
                .filter(rel -> rel.getAddonId().equals(addonId))
                .map(RestaurantDishSpuAddonRelDO::getSpuId)
                .collect(Collectors.toSet());
        if (spuIds.isEmpty()) {
            return Set.of();
        }
        return storeDishMapper.selectList().stream()
                .filter(sd -> spuIds.contains(sd.getSpuId()))
                .map(RestaurantStoreDishDO::getStoreId)
                .collect(Collectors.toSet());
    }

}
