package cn.iocoder.yudao.module.restaurant.service.dish;

import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishImportResultVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishImportVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.category.RestaurantCategoryDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.category.RestaurantCategoryMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import cn.iocoder.yudao.module.restaurant.service.menu.MenuCacheEvictEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RestaurantDishImportServiceImpl implements RestaurantDishImportService {

    @Resource
    private RestaurantDishSpuMapper dishSpuMapper;

    @Resource
    private RestaurantCategoryMapper categoryMapper;

    @Resource
    private RestaurantStoreMapper storeMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public RestaurantDishImportResultVO importDishes(List<RestaurantDishImportVO> importList) {
        RestaurantDishImportResultVO result = new RestaurantDishImportResultVO();

        List<RestaurantCategoryDO> allCategories = categoryMapper.selectList();
        Map<String, Long> categoryNameToId = allCategories.stream()
                .collect(Collectors.toMap(RestaurantCategoryDO::getName, RestaurantCategoryDO::getId, (a, b) -> a));

        Set<String> existingNames = dishSpuMapper.selectList().stream()
                .map(RestaurantDishSpuDO::getName)
                .collect(Collectors.toSet());

        int rowNum = 0;
        for (RestaurantDishImportVO row : importList) {
            rowNum++;
            if (row.getName() == null || row.getName().isBlank()) {
                result.addFail(rowNum, "(空)", "菜品名为空");
                continue;
            }
            if (existingNames.contains(row.getName())) {
                result.addSkip();
                continue;
            }
            Long categoryId = categoryNameToId.get(row.getCategoryName());
            if (categoryId == null) {
                result.addFail(rowNum, row.getName(), "分类不存在: " + row.getCategoryName());
                continue;
            }
            RestaurantDishSpuDO spu = RestaurantDishSpuDO.builder()
                    .categoryId(categoryId)
                    .name(row.getName())
                    .price(row.getPrice() != null ? row.getPrice() : java.math.BigDecimal.ZERO)
                    .description(row.getDescription())
                    .image(row.getImage())
                    .sort(row.getSort() != null ? row.getSort() : 0)
                    .status(0)
                    .build();
            dishSpuMapper.insert(spu);
            existingNames.add(row.getName());
            result.addSuccess();
        }

        Set<Long> allStoreIds = storeMapper.selectList().stream()
                .map(RestaurantStoreDO::getId).collect(Collectors.toSet());
        eventPublisher.publishEvent(new MenuCacheEvictEvent(allStoreIds));

        return result;
    }

}
