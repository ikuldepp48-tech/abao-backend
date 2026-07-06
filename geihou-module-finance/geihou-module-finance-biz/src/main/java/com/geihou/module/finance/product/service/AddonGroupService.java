package com.geihou.module.finance.product.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.controller.admin.vo.*;
import com.geihou.module.finance.product.dal.dataobject.*;
import com.geihou.module.finance.product.dal.mapper.*;
import com.geihou.module.finance.product.enums.AddonOptionStatusEnum;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import com.geihou.module.finance.product.framework.ProductErrorCodeConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Addon group service for 加料组 + 加料选项 CRUD and validation.
 *
 * <p>Tenant isolation is enforced via MyBatis-Plus TenantLineInnerInterceptor
 * and explicit tenant_id from TenantContextHolder.
 * Soft delete is used (no hard delete).
 * All extra_price fields use BigDecimal (never double/float).
 * Addon option status uses ENUM_ADDON_OPTION_STATUS (ACTIVE/SOLD_OUT/DISABLED).
 */
@Service
public class AddonGroupService {

    private final ProductAddonGroupMapper addonGroupMapper;
    private final ProductAddonOptionMapper addonOptionMapper;
    private final ProductSkuMapper skuMapper;

    public AddonGroupService(ProductAddonGroupMapper addonGroupMapper,
                             ProductAddonOptionMapper addonOptionMapper,
                             ProductSkuMapper skuMapper) {
        this.addonGroupMapper = addonGroupMapper;
        this.addonOptionMapper = addonOptionMapper;
        this.skuMapper = skuMapper;
    }

    // ===== Addon Group CRUD =====

    @Transactional(rollbackFor = Exception.class)
    public Long createAddonGroup(AddonGroupCreateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate group_code uniqueness within tenant
        ProductAddonGroupDO existing = addonGroupMapper.selectOne(
                ProductAddonGroupDO::getGroupCode, reqVO.getGroupCode());
        if (existing != null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.ADDON_GROUP_CODE_DUPLICATED);
        }

        // Validate select_min <= select_max (AC-7)
        validateSelectRange(reqVO.getSelectMin(), reqVO.getSelectMax());

        LocalDateTime now = LocalDateTime.now();
        ProductAddonGroupDO group = new ProductAddonGroupDO();
        group.setTenantId(tenantId);
        group.setGroupCode(reqVO.getGroupCode());
        group.setGroupName(reqVO.getGroupName());
        group.setSelectMin(reqVO.getSelectMin() != null ? reqVO.getSelectMin() : 0);
        group.setSelectMax(reqVO.getSelectMax());
        group.setIsRequired(reqVO.getIsRequired() != null ? reqVO.getIsRequired() : false);
        group.setSortOrder(reqVO.getSortOrder() != null ? reqVO.getSortOrder() : 0);
        group.setCreator("");
        group.setCreateTime(now);
        group.setUpdater("");
        group.setUpdateTime(now);
        group.setDeleted(false);

        addonGroupMapper.insert(group);
        return group.getId();
    }

    public AddonGroupRespVO getAddonGroup(Long id) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        ProductAddonGroupDO group = addonGroupMapper.selectById(id);
        if (group == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.ADDON_GROUP_NOT_FOUND);
        }
        return toGroupRespVO(group, listOptions(id));
    }

    public List<AddonGroupRespVO> listAddonGroups() {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        List<ProductAddonGroupDO> groups = addonGroupMapper.selectList();
        return groups.stream()
                .map(g -> toGroupRespVO(g, listOptions(g.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateAddonGroup(Long id, AddonGroupUpdateReqVO reqVO) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        ProductAddonGroupDO group = addonGroupMapper.selectById(id);
        if (group == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.ADDON_GROUP_NOT_FOUND);
        }

        if (reqVO.getSelectMin() != null || reqVO.getSelectMax() != null) {
            int selectMin = reqVO.getSelectMin() != null ? reqVO.getSelectMin() : group.getSelectMin();
            int selectMax = reqVO.getSelectMax() != null ? reqVO.getSelectMax() : group.getSelectMax();
            validateSelectRange(selectMin, selectMax);
        }

        if (reqVO.getGroupName() != null) {
            group.setGroupName(reqVO.getGroupName());
        }
        if (reqVO.getSelectMin() != null) {
            group.setSelectMin(reqVO.getSelectMin());
        }
        if (reqVO.getSelectMax() != null) {
            group.setSelectMax(reqVO.getSelectMax());
        }
        if (reqVO.getIsRequired() != null) {
            group.setIsRequired(reqVO.getIsRequired());
        }
        if (reqVO.getSortOrder() != null) {
            group.setSortOrder(reqVO.getSortOrder());
        }

        group.setUpdater("");
        group.setUpdateTime(LocalDateTime.now());
        addonGroupMapper.updateById(group);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAddonGroup(Long id) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        ProductAddonGroupDO group = addonGroupMapper.selectById(id);
        if (group == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.ADDON_GROUP_NOT_FOUND);
        }

        // Soft delete all options first
        List<ProductAddonOptionDO> options = listOptions(id);
        for (ProductAddonOptionDO option : options) {
            addonOptionMapper.deleteById(option.getId());
        }

        // Soft delete group
        addonGroupMapper.deleteById(id);
    }

    // ===== Addon Option CRUD =====

    @Transactional(rollbackFor = Exception.class)
    public Long createAddonOption(Long groupId, AddonOptionCreateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate group exists
        ProductAddonGroupDO group = addonGroupMapper.selectById(groupId);
        if (group == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.ADDON_GROUP_NOT_FOUND);
        }

        // Validate option_sku_id references an existing SKU in the same tenant (AC-9)
        ProductSkuDO sku = skuMapper.selectById(reqVO.getOptionSkuId());
        if (sku == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.SKU_NOT_FOUND);
        }

        // Validate extra_price is non-negative BigDecimal
        if (reqVO.getExtraPrice() != null && reqVO.getExtraPrice().signum() < 0) {
            throw new IllegalArgumentException("extraPrice must be >= 0");
        }

        LocalDateTime now = LocalDateTime.now();
        ProductAddonOptionDO option = new ProductAddonOptionDO();
        option.setTenantId(tenantId);
        option.setAddonGroupId(groupId);
        option.setOptionSkuId(reqVO.getOptionSkuId());
        option.setOptionName(reqVO.getOptionName());
        option.setExtraPrice(reqVO.getExtraPrice() != null ? reqVO.getExtraPrice() : BigDecimal.ZERO);
        option.setSortOrder(reqVO.getSortOrder() != null ? reqVO.getSortOrder() : 0);
        option.setStatus(AddonOptionStatusEnum.ACTIVE.getCode());
        option.setCreator("");
        option.setCreateTime(now);
        option.setUpdater("");
        option.setUpdateTime(now);
        option.setDeleted(false);

        addonOptionMapper.insert(option);
        return option.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateAddonOption(Long groupId, Long optionId, AddonOptionUpdateReqVO reqVO) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        ProductAddonOptionDO option = addonOptionMapper.selectOne(
                ProductAddonOptionDO::getId, optionId,
                ProductAddonOptionDO::getAddonGroupId, groupId);
        if (option == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.ADDON_OPTION_NOT_FOUND);
        }

        if (reqVO.getExtraPrice() != null && reqVO.getExtraPrice().signum() < 0) {
            throw new IllegalArgumentException("extraPrice must be >= 0");
        }

        if (reqVO.getOptionName() != null) {
            option.setOptionName(reqVO.getOptionName());
        }
        if (reqVO.getExtraPrice() != null) {
            option.setExtraPrice(reqVO.getExtraPrice());
        }
        if (reqVO.getSortOrder() != null) {
            option.setSortOrder(reqVO.getSortOrder());
        }

        option.setUpdater("");
        option.setUpdateTime(LocalDateTime.now());
        addonOptionMapper.updateById(option);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeAddonOptionStatus(Long groupId, Long optionId, AddonOptionStatusChangeReqVO reqVO) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        if (reqVO.getReason() == null || reqVO.getReason().length() < 5) {
            throw new ProductBusinessException(ProductErrorCodeConstants.STATUS_CHANGE_REASON_REQUIRED);
        }

        ProductAddonOptionDO option = addonOptionMapper.selectOne(
                ProductAddonOptionDO::getId, optionId,
                ProductAddonOptionDO::getAddonGroupId, groupId);
        if (option == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.ADDON_OPTION_NOT_FOUND);
        }

        AddonOptionStatusEnum oldStatus = AddonOptionStatusEnum.fromCode(option.getStatus());
        AddonOptionStatusEnum newStatus = AddonOptionStatusEnum.fromCode(reqVO.getNewStatus());

        option.setStatus(newStatus.getCode());
        option.setUpdater("");
        option.setUpdateTime(LocalDateTime.now());
        addonOptionMapper.updateById(option);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAddonOption(Long groupId, Long optionId) {
        Objects.requireNonNull(TenantContextHolder.getTenantId(), "Tenant context is required");

        ProductAddonOptionDO option = addonOptionMapper.selectOne(
                ProductAddonOptionDO::getId, optionId,
                ProductAddonOptionDO::getAddonGroupId, groupId);
        if (option == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.ADDON_OPTION_NOT_FOUND);
        }

        addonOptionMapper.deleteById(optionId);
    }

    // ===== Internal helpers =====

    private List<ProductAddonOptionDO> listOptions(Long groupId) {
        return addonOptionMapper.selectList(
                ProductAddonOptionDO::getAddonGroupId, groupId);
    }

    private static void validateSelectRange(Integer selectMin, Integer selectMax) {
        int min = selectMin != null ? selectMin : 0;
        if (selectMax == null) {
            throw new ProductBusinessException(ProductErrorCodeConstants.ADDON_GROUP_INVALID,
                    "select_max is required");
        }
        if (min < 0) {
            throw new ProductBusinessException(ProductErrorCodeConstants.ADDON_GROUP_INVALID,
                    "select_min must be >= 0");
        }
        if (min > selectMax) {
            throw new ProductBusinessException(ProductErrorCodeConstants.ADDON_GROUP_INVALID,
                    "select_min (" + min + ") must be <= select_max (" + selectMax + ")");
        }
    }

    private AddonGroupRespVO toGroupRespVO(ProductAddonGroupDO group, List<ProductAddonOptionDO> options) {
        AddonGroupRespVO vo = new AddonGroupRespVO();
        vo.setId(group.getId());
        vo.setGroupCode(group.getGroupCode());
        vo.setGroupName(group.getGroupName());
        vo.setSelectMin(group.getSelectMin());
        vo.setSelectMax(group.getSelectMax());
        vo.setIsRequired(group.getIsRequired());
        vo.setSortOrder(group.getSortOrder());
        vo.setCreateTime(group.getCreateTime());
        vo.setUpdateTime(group.getUpdateTime());
        vo.setOptions(options.stream()
                .map(this::toOptionRespVO)
                .collect(Collectors.toList()));
        return vo;
    }

    private AddonOptionRespVO toOptionRespVO(ProductAddonOptionDO option) {
        AddonOptionRespVO vo = new AddonOptionRespVO();
        vo.setId(option.getId());
        vo.setAddonGroupId(option.getAddonGroupId());
        vo.setOptionSkuId(option.getOptionSkuId());
        vo.setOptionName(option.getOptionName());
        vo.setExtraPrice(option.getExtraPrice());
        vo.setSortOrder(option.getSortOrder());
        vo.setStatus(option.getStatus());
        vo.setCreateTime(option.getCreateTime());
        vo.setUpdateTime(option.getUpdateTime());
        return vo;
    }
}
