package cn.iocoder.yudao.module.restaurant.service.brand;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo.RestaurantBrandUpdateReqVO;
import cn.iocoder.yudao.module.restaurant.convert.brand.RestaurantBrandConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.brand.RestaurantBrandDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.brand.RestaurantBrandMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.BRAND_NOT_EXISTS;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.BRAND_NAME_EXISTS;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.BRAND_CODE_EXISTS;

import org.springframework.transaction.annotation.Transactional;

@Service
@Validated
@Transactional(rollbackFor = Exception.class)
public class RestaurantBrandServiceImpl implements RestaurantBrandService {

    @Resource
    private RestaurantBrandMapper restaurantBrandMapper;

    @Override
    public Long createBrand(RestaurantBrandCreateReqVO createReqVO) {
        validateBrandNameUnique(null, createReqVO.getName());
        validateBrandCodeUnique(null, createReqVO.getCode());
        RestaurantBrandDO brand = RestaurantBrandConvert.INSTANCE.convert(createReqVO);
        restaurantBrandMapper.insert(brand);
        return brand.getId();
    }

    @Override
    public void updateBrand(RestaurantBrandUpdateReqVO updateReqVO) {
        validateBrandExists(updateReqVO.getId());
        validateBrandNameUnique(updateReqVO.getId(), updateReqVO.getName());
        validateBrandCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        RestaurantBrandDO updateObj = RestaurantBrandConvert.INSTANCE.convert(updateReqVO);
        restaurantBrandMapper.updateById(updateObj);
    }

    @Override
    public void deleteBrand(Long id) {
        validateBrandExists(id);
        restaurantBrandMapper.deleteById(id);
    }

    private void validateBrandExists(Long id) {
        if (restaurantBrandMapper.selectById(id) == null) {
            throw exception(BRAND_NOT_EXISTS);
        }
    }

    private void validateBrandNameUnique(Long id, String name) {
        if (StrUtil.isBlank(name)) {
            return;
        }
        RestaurantBrandDO brand = restaurantBrandMapper.selectByName(name);
        if (brand == null) {
            return;
        }
        if (id == null || !brand.getId().equals(id)) {
            throw exception(BRAND_NAME_EXISTS);
        }
    }

    private void validateBrandCodeUnique(Long id, String code) {
        if (StrUtil.isBlank(code)) {
            return;
        }
        RestaurantBrandDO brand = restaurantBrandMapper.selectByCode(code);
        if (brand == null) {
            return;
        }
        if (id == null || !brand.getId().equals(id)) {
            throw exception(BRAND_CODE_EXISTS);
        }
    }

    @Override
    public RestaurantBrandDO getBrand(Long id) {
        return restaurantBrandMapper.selectById(id);
    }

    @Override
    public List<RestaurantBrandDO> getBrandList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return ListUtil.empty();
        }
        return restaurantBrandMapper.selectByIds(ids);
    }

    @Override
    public PageResult<RestaurantBrandDO> getBrandPage(RestaurantBrandPageReqVO pageReqVO) {
        return restaurantBrandMapper.selectPage(pageReqVO);
    }

    @Override
    public List<RestaurantBrandDO> getBrandList() {
        return restaurantBrandMapper.selectList();
    }

}
