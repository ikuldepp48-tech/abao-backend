package com.geihou.module.finance.product.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.controller.admin.vo.SpuAddonGroupRespVO;
import com.geihou.module.finance.product.dal.dataobject.ProductAddonGroupDO;
import com.geihou.module.finance.product.dal.dataobject.ProductSpuAddonGroupDO;
import com.geihou.module.finance.product.dal.dataobject.ProductSpuDO;
import com.geihou.module.finance.product.dal.mapper.ProductAddonGroupMapper;
import com.geihou.module.finance.product.dal.mapper.ProductSpuAddonGroupMapper;
import com.geihou.module.finance.product.dal.mapper.ProductSpuMapper;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import com.geihou.module.finance.product.framework.ProductErrorCodeConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for SPU-addon group mapping CRUD.
 *
 * <p>Manages the many-to-many relationship between SPU and addon groups
 * via the product_spu_addon_group mapping table.
 *
 * <p>Tenant isolation via tenant_id; soft delete via @TableLogic.
 * Validates SPU and addon group existence before creating mappings.
 */
@Service
public class SpuAddonGroupService {

    private final ProductSpuAddonGroupMapper spuAddonGroupMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductAddonGroupMapper addonGroupMapper;

    public SpuAddonGroupService(ProductSpuAddonGroupMapper spuAddonGroupMapper,
                                ProductSpuMapper spuMapper,
                                ProductAddonGroupMapper addonGroupMapper) {
        this.spuAddonGroupMapper = spuAddonGroupMapper;
        this.spuMapper = spuMapper;
        this.addonGroupMapper = addonGroupMapper;
    }

    /**
     * Assign an addon group to an SPU.
     *
     * @param spuId        SPU ID (must exist)
     * @param addonGroupId addon group ID (must exist)
     * @param sortOrder    sort order (nullable, defaults to 0)
     * @return mapping record ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long assignAddonGroup(Long spuId, Long addonGroupId, Integer sortOrder) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate SPU exists
        ProductSpuDO spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SPU_NOT_FOUND);
        }

        // Validate addon group exists
        ProductAddonGroupDO group = addonGroupMapper.selectById(addonGroupId);
        if (group == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.ADDON_GROUP_NOT_FOUND);
        }

        // Check for duplicate mapping
        ProductSpuAddonGroupDO existing = spuAddonGroupMapper.selectOne(
                ProductSpuAddonGroupDO::getSpuId, spuId,
                ProductSpuAddonGroupDO::getAddonGroupId, addonGroupId);
        if (existing != null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SPU_ADDON_GROUP_DUPLICATED);
        }

        LocalDateTime now = LocalDateTime.now();
        ProductSpuAddonGroupDO mapping = new ProductSpuAddonGroupDO();
        mapping.setTenantId(tenantId);
        mapping.setSpuId(spuId);
        mapping.setAddonGroupId(addonGroupId);
        mapping.setSortOrder(sortOrder != null ? sortOrder : 0);
        mapping.setCreator("");
        mapping.setCreateTime(now);
        mapping.setUpdater("");
        mapping.setUpdateTime(now);
        mapping.setDeleted(false);

        spuAddonGroupMapper.insert(mapping);
        return mapping.getId();
    }

    /**
     * Remove an addon group mapping from an SPU (soft delete).
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeAddonGroup(Long spuId, Long addonGroupId) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        ProductSpuAddonGroupDO mapping = spuAddonGroupMapper.selectOne(
                ProductSpuAddonGroupDO::getSpuId, spuId,
                ProductSpuAddonGroupDO::getAddonGroupId, addonGroupId);
        if (mapping == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SPU_ADDON_GROUP_NOT_FOUND);
        }

        spuAddonGroupMapper.deleteById(mapping.getId());
    }

    /**
     * List addon group mappings for an SPU.
     */
    public List<SpuAddonGroupRespVO> listBySpu(Long spuId) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        List<ProductSpuAddonGroupDO> mappings = spuAddonGroupMapper.selectList(
                ProductSpuAddonGroupDO::getSpuId, spuId);

        return mappings.stream()
                .map(this::toRespVO)
                .collect(Collectors.toList());
    }

    /**
     * Get addon group IDs for an SPU (internal use by ProductApiImpl).
     *
     * @param spuId SPU ID
     * @return list of addon group IDs ordered by sort_order
     */
    public List<Long> getAddonGroupIdsBySpu(Long spuId) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        List<ProductSpuAddonGroupDO> mappings = spuAddonGroupMapper.selectList(
                ProductSpuAddonGroupDO::getSpuId, spuId);

        return mappings.stream()
                .map(ProductSpuAddonGroupDO::getAddonGroupId)
                .collect(Collectors.toList());
    }

    private SpuAddonGroupRespVO toRespVO(ProductSpuAddonGroupDO mapping) {
        SpuAddonGroupRespVO vo = new SpuAddonGroupRespVO();
        vo.setId(mapping.getId());
        vo.setSpuId(mapping.getSpuId());
        vo.setAddonGroupId(mapping.getAddonGroupId());
        vo.setSortOrder(mapping.getSortOrder());

        // Populate addon group name/code if available
        ProductAddonGroupDO group = addonGroupMapper.selectById(mapping.getAddonGroupId());
        if (group != null) {
            vo.setAddonGroupCode(group.getGroupCode());
            vo.setAddonGroupName(group.getGroupName());
        }

        return vo;
    }
}
