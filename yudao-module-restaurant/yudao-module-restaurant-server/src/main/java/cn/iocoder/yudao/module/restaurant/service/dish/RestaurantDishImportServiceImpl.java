package cn.iocoder.yudao.module.restaurant.service.dish;

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.write.metadata.WriteSheet;
import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.*;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishAddonDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.category.RestaurantCategoryDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.dish.RestaurantDishSpuDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.addon.RestaurantDishAddonMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.category.RestaurantCategoryMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.dish.RestaurantDishSpuMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RestaurantDishImportServiceImpl implements RestaurantDishImportService {

    @Resource
    private RestaurantCategoryMapper categoryMapper;

    @Resource
    private RestaurantDishSpuMapper dishSpuMapper;

    @Resource
    private RestaurantDishAddonMapper addonMapper;

    @Resource
    private RestaurantStoreMapper storeMapper;

    @Resource
    private RestaurantDishImportRowExecutor rowExecutor;

    @Override
    public RestaurantDishImportResultVO importDishes(MultipartFile file) throws IOException {
        RestaurantDishImportContext ctx = buildContext();
        RestaurantDishImportResultVO result = new RestaurantDishImportResultVO();
        byte[] bytes = file.getBytes();

        // Sheet 0: 分类
        List<RestaurantDishImportCategoryVO> categories = readSheet(bytes, 0, RestaurantDishImportCategoryVO.class);
        if (categories != null) {
            int rowNum = 0;
            for (RestaurantDishImportCategoryVO row : categories) {
                rowNum++;
                try {
                    rowExecutor.executeCategoryRow(row, ctx, result, rowNum);
                } catch (Exception e) {
                    result.addFail("分类", rowNum, row.getLevel1Category(), e.getMessage());
                }
            }
        }

        // Sheet 1: 简单菜品
        List<RestaurantDishImportSimpleDishVO> simpleDishes = readSheet(bytes, 1, RestaurantDishImportSimpleDishVO.class);
        if (simpleDishes != null) {
            int rowNum = 0;
            for (RestaurantDishImportSimpleDishVO row : simpleDishes) {
                rowNum++;
                try {
                    rowExecutor.executeSimpleDishRow(row, ctx, result, rowNum);
                } catch (Exception e) {
                    result.addFail("简单菜品", rowNum, row.getName(), e.getMessage());
                }
            }
        }

        // Sheet 2: 多SKU菜品
        List<RestaurantDishImportMultiSkuVO> multiSkus = readSheet(bytes, 2, RestaurantDishImportMultiSkuVO.class);
        if (multiSkus != null) {
            int rowNum = 0;
            for (RestaurantDishImportMultiSkuVO row : multiSkus) {
                rowNum++;
                try {
                    rowExecutor.executeMultiSkuRow(row, ctx, result, rowNum);
                } catch (Exception e) {
                    result.addFail("多SKU菜品", rowNum, row.getName(), e.getMessage());
                }
            }
        }

        // Sheet 3: 加料关联
        List<RestaurantDishImportAddonRelVO> addonRels = readSheet(bytes, 3, RestaurantDishImportAddonRelVO.class);
        if (addonRels != null) {
            int rowNum = 0;
            for (RestaurantDishImportAddonRelVO row : addonRels) {
                rowNum++;
                try {
                    rowExecutor.executeAddonRelRow(row, ctx, result, rowNum);
                } catch (Exception e) {
                    result.addFail("加料关联", rowNum, row.getName(), e.getMessage());
                }
            }
        }

        return result;
    }

    @Override
    public void generateTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + URLEncoder.encode("菜品导入模板.xlsx", StandardCharsets.UTF_8));

        // Sheet 0: 分类（含示例数据）
        List<RestaurantDishImportCategoryVO> categorySamples = List.of(
                buildCategory("主食", "面类", "汤面", 1),
                buildCategory("饮品", "茶饮", null, 2));

        // Sheet 1: 简单菜品（含示例数据）
        List<RestaurantDishImportSimpleDishVO> simpleDishSamples = List.of(
                buildSimpleDish("主食", "面类", "红烧牛肉面", new java.math.BigDecimal("28.00"), "份", "是", "否", "是", "招牌红烧牛肉面"),
                buildSimpleDish("饮品", "茶饮", "柠檬红茶", new java.math.BigDecimal("12.00"), "杯", "否", "是", "否", "清新柠檬红茶"));

        // Sheet 2: 多SKU菜品（含示例数据）
        List<RestaurantDishImportMultiSkuVO> multiSkuSamples = List.of(
                buildMultiSku("红烧牛肉面", "大份", "大小:大", new java.math.BigDecimal("32.00")),
                buildMultiSku("红烧牛肉面", "小份", "大小:小", new java.math.BigDecimal("24.00")));

        // Sheet 3: 加料关联（含示例数据）
        List<RestaurantDishImportAddonRelVO> addonRelSamples = List.of(
                buildAddonRel("红烧牛肉面", "辣度"),
                buildAddonRel("柠檬红茶", "甜度"));

        ExcelWriter writer = FastExcelFactory.write(response.getOutputStream()).build();
        writer.write(categorySamples, FastExcelFactory.writerSheet(0, "分类").head(RestaurantDishImportCategoryVO.class).build());
        writer.write(simpleDishSamples, FastExcelFactory.writerSheet(1, "简单菜品").head(RestaurantDishImportSimpleDishVO.class).build());
        writer.write(multiSkuSamples, FastExcelFactory.writerSheet(2, "多SKU菜品").head(RestaurantDishImportMultiSkuVO.class).build());
        writer.write(addonRelSamples, FastExcelFactory.writerSheet(3, "加料关联").head(RestaurantDishImportAddonRelVO.class).build());
        writer.finish();
    }

    // ========== 内部方法 ==========

    private RestaurantDishImportContext buildContext() {
        RestaurantDishImportContext ctx = new RestaurantDishImportContext();

        // 预加载已存在的 SPU 名称
        Set<String> existingNames = dishSpuMapper.selectList().stream()
                .map(RestaurantDishSpuDO::getName)
                .collect(Collectors.toSet());
        ctx.setExistingSpuNames(existingNames);

        // 预加载所有分类（供 RowExecutor 查重用）
        // RowExecutor 内部会调用 categoryMapper.selectList()，这里先填充 path 映射
        List<RestaurantCategoryDO> allCategories = categoryMapper.selectList();
        Map<Long, RestaurantCategoryDO> catMap = allCategories.stream()
                .collect(Collectors.toMap(RestaurantCategoryDO::getId, c -> c));
        for (RestaurantCategoryDO cat : allCategories) {
            String path = buildCategoryPath(cat, catMap);
            if (path != null) {
                ctx.putCategoryPath(path, cat.getId());
            }
        }

        // 预加载所有门店
        ctx.setAllStores(storeMapper.selectList());

        // 预加载加料分组
        List<RestaurantDishAddonDO> allAddons = addonMapper.selectListByBrand(null);
        Map<String, List<RestaurantDishAddonDO>> addonGroups = new LinkedHashMap<>();
        for (RestaurantDishAddonDO addon : allAddons) {
            addonGroups.computeIfAbsent(addon.getGroupName(), k -> new ArrayList<>()).add(addon);
        }
        ctx.setAddonGroups(addonGroups);

        return ctx;
    }

    private String buildCategoryPath(RestaurantCategoryDO cat, Map<Long, RestaurantCategoryDO> catMap) {
        List<String> parts = new ArrayList<>();
        RestaurantCategoryDO current = cat;
        while (current != null) {
            parts.add(0, current.getName());
            if (current.getParentId() != null) {
                current = catMap.get(current.getParentId());
            } else {
                current = null;
            }
        }
        return parts.isEmpty() ? null : String.join("/", parts);
    }

    private <T> List<T> readSheet(byte[] bytes, int sheetIndex, Class<T> clazz) {
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        return FastExcelFactory.read(in, clazz, null)
                .sheet(sheetIndex)
                .headRowNumber(1)
                .doReadSync();
    }

    // ========== 模板示例数据构建 ==========

    private RestaurantDishImportCategoryVO buildCategory(String l1, String l2, String l3, int sort) {
        RestaurantDishImportCategoryVO vo = new RestaurantDishImportCategoryVO();
        vo.setLevel1Category(l1);
        vo.setLevel2Category(l2);
        vo.setLevel3Category(l3);
        vo.setSort(sort);
        return vo;
    }

    private RestaurantDishImportSimpleDishVO buildSimpleDish(String l1, String l2, String name,
                                                              java.math.BigDecimal price, String unit,
                                                              String isSignature, String isNew, String recommended,
                                                              String desc) {
        RestaurantDishImportSimpleDishVO vo = new RestaurantDishImportSimpleDishVO();
        vo.setLevel1Category(l1);
        vo.setLevel2Category(l2);
        vo.setName(name);
        vo.setPrice(price);
        vo.setUnit(unit);
        vo.setIsSignature(isSignature);
        vo.setIsNew(isNew);
        vo.setRecommended(recommended);
        vo.setDescription(desc);
        return vo;
    }

    private RestaurantDishImportMultiSkuVO buildMultiSku(String name, String skuName, String props, java.math.BigDecimal price) {
        RestaurantDishImportMultiSkuVO vo = new RestaurantDishImportMultiSkuVO();
        vo.setName(name);
        vo.setSkuName(skuName);
        vo.setProperties(props);
        vo.setPrice(price);
        return vo;
    }

    private RestaurantDishImportAddonRelVO buildAddonRel(String name, String groupName) {
        RestaurantDishImportAddonRelVO vo = new RestaurantDishImportAddonRelVO();
        vo.setName(name);
        vo.setAddonGroupName(groupName);
        return vo;
    }

}
