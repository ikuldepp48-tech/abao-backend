package cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo;

import cn.iocoder.yudao.module.restaurant.dal.dataobject.addon.RestaurantDishAddonDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import lombok.Data;

import java.util.*;

@Data
public class RestaurantDishImportContext {

    /** 分类路径 → 分类ID，如"主食/面类" → 3 */
    private Map<String, Long> categoryPathToId = new LinkedHashMap<>();

    /** 已存在的 SPU 名称集合（DB 预加载，用于去重） */
    private Set<String> existingSpuNames = new HashSet<>();

    /** 本次导入创建的 SPU 名 → ID */
    private Map<String, Long> spuNameToId = new LinkedHashMap<>();

    /** 所有门店 */
    private List<RestaurantStoreDO> allStores = new ArrayList<>();

    /** 加料组名 → 加料项列表，如"口味" → [{辣/0}, {不辣/0}] */
    private Map<String, List<RestaurantDishAddonDO>> addonGroups = new LinkedHashMap<>();

    /**
     * 按路径解析最深层的分类 ID。
     * 如路径"主食/面类/汤面" → 返回汤面的 categoryId
     */
    public Long resolveCategoryId(String level1, String level2) {
        if (level2 != null && !level2.isBlank()) {
            return categoryPathToId.get(level1 + "/" + level2);
        }
        return categoryPathToId.get(level1);
    }

    /**
     * 存储分类路径映射（内部用"/"连接层级）
     */
    public void putCategoryPath(String path, Long id) {
        categoryPathToId.put(path, id);
    }

}
