package cn.iocoder.yudao.module.restaurant.service.menu;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * 菜单缓存失效事件 — 菜品/分类/加料/套餐改动后触发
 */
@Getter
@RequiredArgsConstructor
public class MenuCacheEvictEvent {

    /** 需要清缓存的门店ID集合（为空时表示不处理） */
    private final Set<Long> storeIds;

}
