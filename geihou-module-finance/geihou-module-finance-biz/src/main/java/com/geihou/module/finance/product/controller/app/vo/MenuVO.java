package com.geihou.module.finance.product.controller.app.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level customer menu response VO (G1-02H).
 *
 * <p>Returned by GET /app-api/customer/menu?storeId=.
 * Contains the category tree with SPUs, and echoes the requested storeId.
 * storeId is accepted but store-level inventory filtering is not yet implemented
 * (see task package D-3).
 */
public class MenuVO {

    /** Store ID (echoes request parameter) */
    private Long storeId;

    /** Category tree (ACTIVE categories only, with SPUs) */
    private List<MenuCategoryVO> categories = new ArrayList<>();

    /** Menu snapshot time */
    private LocalDateTime menuSnapshotTime;

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public List<MenuCategoryVO> getCategories() { return categories; }
    public void setCategories(List<MenuCategoryVO> categories) { this.categories = categories; }

    public LocalDateTime getMenuSnapshotTime() { return menuSnapshotTime; }
    public void setMenuSnapshotTime(LocalDateTime menuSnapshotTime) { this.menuSnapshotTime = menuSnapshotTime; }
}
