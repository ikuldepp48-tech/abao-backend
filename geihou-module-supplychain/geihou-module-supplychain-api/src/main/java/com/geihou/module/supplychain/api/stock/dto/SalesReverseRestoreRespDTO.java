package com.geihou.module.supplychain.api.stock.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for sales reverse restore.
 *
 * <p>Returns the list of inbound restore events created, each paired with
 * the original {@code CONSUME_OUT} event it reverses.
 *
 * <p>Key rules (TASK-G2-02E):
 * <ul>
 *   <li>No parent stock_event is created — only IN restore events for raw materials.</li>
 *   <li>parent_event_id stays null in every created event.</li>
 *   <li>Recipe ID and version are copied from original events.</li>
 * </ul>
 */
public class SalesReverseRestoreRespDTO {

    /** Number of restored items. */
    private int restoredItemCount;

    /** Per-component restore rows. */
    private List<SalesReverseRestoreItemRespDTO> items = new ArrayList<>();

    // --- Getters and Setters ---

    public int getRestoredItemCount() { return restoredItemCount; }
    public void setRestoredItemCount(int restoredItemCount) { this.restoredItemCount = restoredItemCount; }

    public List<SalesReverseRestoreItemRespDTO> getItems() { return items; }
    public void setItems(List<SalesReverseRestoreItemRespDTO> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
}
