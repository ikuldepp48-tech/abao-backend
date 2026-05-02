package cn.iocoder.yudao.module.restaurant.service.combo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.combo.vo.*;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.combo.RestaurantComboDO;

import jakarta.validation.Valid;
import java.util.List;

public interface RestaurantComboService {

    Long createCombo(@Valid RestaurantComboCreateReqVO createReqVO);

    void updateCombo(@Valid RestaurantComboUpdateReqVO updateReqVO);

    void deleteCombo(Long id);

    RestaurantComboDO getCombo(Long id);

    List<RestaurantComboItemBaseVO> getComboItems(Long comboId);

    List<RestaurantComboDO> getComboListByBrandId(Long brandId);

    PageResult<RestaurantComboDO> getComboPage(RestaurantComboPageReqVO pageReqVO);

}
