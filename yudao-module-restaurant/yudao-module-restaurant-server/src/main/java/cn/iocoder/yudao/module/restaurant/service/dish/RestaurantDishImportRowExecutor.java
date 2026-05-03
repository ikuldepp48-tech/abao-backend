package cn.iocoder.yudao.module.restaurant.service.dish;

import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.*;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishAddonDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishSpuAddonRelDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.category.RestaurantCategoryDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSkuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDishDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.addon.RestaurantDishAddonMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.addon.RestaurantDishSpuAddonRelMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.category.RestaurantCategoryMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSkuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreDishMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class RestaurantDishImportRowExecutor {

    @Resource
    private RestaurantCategoryMapper categoryMapper;

    @Resource
    private RestaurantDishSpuService dishSpuService;

    @Resource
    private RestaurantDishSpuMapper dishSpuMapper;

    @Resource
    private RestaurantDishSkuMapper skuMapper;

    @Resource
    private RestaurantDishAddonMapper addonMapper;

    @Resource
    private RestaurantDishSpuAddonRelMapper spuAddonRelMapper;

    @Resource
    private RestaurantStoreMapper storeMapper;

    @Resource
    private RestaurantStoreDishMapper storeDishMapper;

    // ========== Sheet 1: 分类 ==========

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void executeCategoryRow(RestaurantDishImportCategoryVO row, RestaurantDishImportContext ctx,
                                    RestaurantDishImportResultVO result, int rowNum) {
        String l1 = trimToNull(row.getLevel1Category());
        if (l1 == null) {
            result.addFail("分类", rowNum, "(空)", "一级分类不能为空");
            return;
        }

        String l2 = trimToNull(row.getLevel2Category());
        String l3 = trimToNull(row.getLevel3Category());
        int sort = row.getSort() != null ? row.getSort() : 0;

        // 逐层查找或创建
        Long l1Id = findOrCreateCategory(l1, 0L, sort, ctx);
        if (l2 == null) {
            return; // 只有一级分类
        }

        Long l2Id = findOrCreateCategory(l2, l1Id, sort, ctx);
        String l2Path = l1 + "/" + l2;
        ctx.putCategoryPath(l2Path, l2Id);

        if (l3 == null) {
            return; // 只有一二级分类
        }

        Long l3Id = findOrCreateCategory(l3, l2Id, sort, ctx);
        String l3Path = l1 + "/" + l2 + "/" + l3;
        ctx.putCategoryPath(l3Path, l3Id);
    }

    private Long findOrCreateCategory(String name, Long parentId, int sort, RestaurantDishImportContext ctx) {
        // 在 ctx 中按 parentId:name 查找
        String cacheKey = parentId + ":" + name;
        Long cached = ctx.getCategoryPathToId().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 查 DB：在已加载的全部分类中查找
        List<RestaurantCategoryDO> all = categoryMapper.selectList();
        for (RestaurantCategoryDO cat : all) {
            Long catParentId = cat.getParentId() != null ? cat.getParentId() : 0L;
            if (catParentId.equals(parentId) && cat.getName().equals(name)) {
                ctx.putCategoryPath(cacheKey, cat.getId());
                return cat.getId();
            }
        }

        // 创建
        RestaurantCategoryDO cat = new RestaurantCategoryDO();
        cat.setName(name);
        cat.setParentId(parentId == 0L ? null : parentId);
        cat.setSort(sort);
        cat.setStatus(0);
        categoryMapper.insert(cat);

        ctx.putCategoryPath(cacheKey, cat.getId());
        return cat.getId();
    }

    // ========== Sheet 2: 简单菜品 ==========

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void executeSimpleDishRow(RestaurantDishImportSimpleDishVO row, RestaurantDishImportContext ctx,
                                      RestaurantDishImportResultVO result, int rowNum) {
        String name = trimToNull(row.getName());
        String l1 = trimToNull(row.getLevel1Category());

        if (name == null) {
            result.addFail("简单菜品", rowNum, "(空)", "菜品名不能为空");
            return;
        }
        if (l1 == null) {
            result.addFail("简单菜品", rowNum, name, "一级分类不能为空");
            return;
        }
        if (row.getPrice() == null || row.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            result.addFail("简单菜品", rowNum, name, "售价必须大于0");
            return;
        }

        if (ctx.getExistingSpuNames().contains(name) || ctx.getSpuNameToId().containsKey(name)) {
            result.addSkip();
            return;
        }

        String l2 = trimToNull(row.getLevel2Category());
        Long categoryId = ctx.resolveCategoryId(l1, l2);
        if (categoryId == null) {
            result.addFail("简单菜品", rowNum, name, "分类路径不存在: " + l1 + (l2 != null ? "/" + l2 : ""));
            return;
        }

        String unit = row.getUnit() != null && !row.getUnit().isBlank() ? row.getUnit().trim() : "份";

        // Build CreateReqVO
        RestaurantDishSpuCreateReqVO createReqVO = new RestaurantDishSpuCreateReqVO();
        createReqVO.setCategoryId(categoryId);
        createReqVO.setName(name);
        createReqVO.setPrice(row.getPrice());
        createReqVO.setDescription(row.getDescription());
        createReqVO.setImage(row.getImageFileName());
        createReqVO.setIsSignature(parseBool(row.getIsSignature()));
        createReqVO.setIsNew(parseBool(row.getIsNew()));
        createReqVO.setSort(0);
        createReqVO.setStatus(0);

        // 默认SKU
        RestaurantDishSkuBaseVO sku = new RestaurantDishSkuBaseVO();
        sku.setName(unit);
        sku.setPrice(row.getPrice());
        sku.setSort(0);
        sku.setStatus(0);
        createReqVO.setSkus(List.of(sku));
        createReqVO.setAddonGroupNames(List.of());

        Long spuId = dishSpuService.createDishSpu(createReqVO);
        ctx.getSpuNameToId().put(name, spuId);
        result.addSuccess();
    }

    // ========== Sheet 3: 多SKU菜品 ==========

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void executeMultiSkuRow(RestaurantDishImportMultiSkuVO row, RestaurantDishImportContext ctx,
                                    RestaurantDishImportResultVO result, int rowNum) {
        String name = trimToNull(row.getName());
        String skuName = trimToNull(row.getSkuName());

        if (name == null) {
            result.addFail("多SKU菜品", rowNum, "(空)", "菜品名不能为空");
            return;
        }
        if (skuName == null) {
            result.addFail("多SKU菜品", rowNum, name, "SKU名不能为空");
            return;
        }
        if (row.getPrice() == null || row.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            result.addFail("多SKU菜品", rowNum, name, "售价必须大于0");
            return;
        }

        // 查找已有 SPU（可能是 DB 已有，也可能是本次导入创建）
        Long spuId = ctx.getSpuNameToId().get(name);
        if (spuId == null) {
            if (ctx.getExistingSpuNames().contains(name)) {
                result.addFail("多SKU菜品", rowNum, name, "该菜品在DB中存在但未加载到上下文，暂不支持");
            } else {
                result.addFail("多SKU菜品", rowNum, name, "菜品未在'简单菜品'Sheet中创建，请先在Sheet2中添加");
            }
            return;
        }

        RestaurantDishSkuDO sku = new RestaurantDishSkuDO();
        sku.setSpuId(spuId);
        sku.setName(skuName);
        sku.setProperties(row.getProperties());
        sku.setPrice(row.getPrice());
        sku.setSort(0);
        sku.setStatus(0);
        skuMapper.insert(sku);

        // 重算价格区间
        recalcPriceRange(spuId);
        result.addSuccess();
    }

    // ========== Sheet 4: 加料关联 ==========

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void executeAddonRelRow(RestaurantDishImportAddonRelVO row, RestaurantDishImportContext ctx,
                                    RestaurantDishImportResultVO result, int rowNum) {
        String name = trimToNull(row.getName());
        String groupName = trimToNull(row.getAddonGroupName());

        if (name == null) {
            result.addFail("加料关联", rowNum, "(空)", "菜品名不能为空");
            return;
        }
        if (groupName == null) {
            result.addFail("加料关联", rowNum, name, "加料组名不能为空");
            return;
        }

        Long spuId = ctx.getSpuNameToId().get(name);
        if (spuId == null && ctx.getExistingSpuNames().contains(name)) {
            result.addFail("加料关联", rowNum, name, "该菜品在DB中存在，需通过编辑页面关联加料");
            return;
        }
        if (spuId == null) {
            result.addFail("加料关联", rowNum, name, "菜品不存在，请先在Sheet2中创建");
            return;
        }

        List<RestaurantDishAddonDO> addons = addonMapper.selectListByGroupName(null, groupName);
        if (addons.isEmpty()) {
            result.addFail("加料关联", rowNum, name, "加料组不存在: " + groupName);
            return;
        }

        for (RestaurantDishAddonDO addon : addons) {
            RestaurantDishSpuAddonRelDO rel = RestaurantDishSpuAddonRelDO.builder()
                    .spuId(spuId)
                    .addonId(addon.getId())
                    .relType(1)
                    .build();
            spuAddonRelMapper.insert(rel);
        }
        result.addSuccess();
    }

    // ========== 工具方法 ==========

    private void recalcPriceRange(Long spuId) {
        List<RestaurantDishSkuDO> skus = skuMapper.selectListBySpuId(spuId);
        if (skus.isEmpty()) return;
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        for (RestaurantDishSkuDO sku : skus) {
            if (sku.getPrice() == null) continue;
            if (minPrice == null || sku.getPrice().compareTo(minPrice) < 0) minPrice = sku.getPrice();
            if (maxPrice == null || sku.getPrice().compareTo(maxPrice) > 0) maxPrice = sku.getPrice();
        }
        if (minPrice != null) {
            var spu = new cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO();
            spu.setId(spuId);
            spu.setMinPrice(minPrice);
            spu.setMaxPrice(maxPrice);
            dishSpuMapper.updateById(spu);
        }
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Boolean parseBool(String s) {
        if (s == null) return false;
        return "是".equals(s.trim()) || "true".equalsIgnoreCase(s.trim()) || "1".equals(s.trim());
    }

}
