package com.geihou.module.supplychain.api.bom.explode.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO representing a node in the BOM explosion tree.
 *
 * <p>Each node corresponds to one component product at a given depth.
 * The tree is built via recursive DFS, maximum depth 3 (root = depth 1).
 *
 * <p>Source: TASK-G2-02B.
 */
public class BomExplosionRespDTO {

    /** Component product ID. */
    private Long productId;
    /** Component product code. */
    private String productCode;
    /** Component product name. */
    private String productName;
    /** Component type: FINISHED / SEMI_FINISHED / RAW_MATERIAL. */
    private String componentType;
    /** Calculated quantity for this node (after proportional scaling + waste). */
    private BigDecimal quantity;
    /** Unit of measure. */
    private String unit;
    /** Waste rate applied at this node's parent level. */
    private BigDecimal wasteRate;
    /** Depth in the tree (root = 1). */
    private Integer depth;
    /** True if depth limit was reached and children were not expanded. */
    private boolean truncated;
    /** True if a cycle was detected at this node (defensive guard). */
    private boolean cycleDetected;
    /** Child nodes. */
    private List<BomExplosionRespDTO> children = new ArrayList<>();

    // --- Getters and Setters ---

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getWasteRate() { return wasteRate; }
    public void setWasteRate(BigDecimal wasteRate) { this.wasteRate = wasteRate; }

    public Integer getDepth() { return depth; }
    public void setDepth(Integer depth) { this.depth = depth; }

    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }

    public boolean isCycleDetected() { return cycleDetected; }
    public void setCycleDetected(boolean cycleDetected) { this.cycleDetected = cycleDetected; }

    public List<BomExplosionRespDTO> getChildren() { return children; }
    public void setChildren(List<BomExplosionRespDTO> children) {
        this.children = children != null ? children : new ArrayList<>();
    }
}
