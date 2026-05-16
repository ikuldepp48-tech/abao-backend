package cn.iocoder.yudao.module.restaurant.service.table;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.table.vo.*;
import cn.iocoder.yudao.module.restaurant.controller.app.table.vo.RestaurantTableScanRespVO;
import cn.iocoder.yudao.module.restaurant.convert.table.RestaurantTableConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.table.RestaurantTableDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.table.RestaurantTableMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.restaurant.enums.LogRecordConstants.*;

import org.springframework.transaction.annotation.Transactional;

@Service
@Validated
@Transactional(rollbackFor = Exception.class)
public class RestaurantTableServiceImpl implements RestaurantTableService {

    @Resource
    private RestaurantTableMapper restaurantTableMapper;

    @Resource
    private RestaurantStoreMapper restaurantStoreMapper;

    @Resource
    private TableTokenService tableTokenService;

    @Value("${restaurant.qr-code.base-url:http://localhost:5173}")
    private String qrCodeBaseUrl;

    @Override
    @LogRecord(type = TABLE_TYPE, subType = TABLE_CREATE_SUB_TYPE, bizNo = "0",
            success = TABLE_CREATE_SUCCESS)
    public Long createTable(RestaurantTableCreateReqVO createReqVO) {
        RestaurantTableDO table = RestaurantTableConvert.INSTANCE.convert(createReqVO);
        restaurantTableMapper.insert(table);
        return table.getId();
    }

    @Override
    @LogRecord(type = TABLE_TYPE, subType = TABLE_UPDATE_SUB_TYPE, bizNo = "0",
            success = TABLE_UPDATE_SUCCESS)
    public void updateTable(RestaurantTableUpdateReqVO updateReqVO) {
        validateTableExists(updateReqVO.getId());
        RestaurantTableDO updateObj = RestaurantTableConvert.INSTANCE.convert(updateReqVO);
        restaurantTableMapper.updateById(updateObj);
    }

    @Override
    @LogRecord(type = TABLE_TYPE, subType = TABLE_DELETE_SUB_TYPE, bizNo = "{{#id}}",
            success = TABLE_DELETE_SUCCESS)
    public void deleteTable(Long id) {
        validateTableExists(id);
        restaurantTableMapper.deleteById(id);
    }

    @Override
    @LogRecord(type = TABLE_TYPE, subType = TABLE_BATCH_CREATE_SUB_TYPE, bizNo = "0",
            success = TABLE_BATCH_CREATE_SUCCESS)
    public Map<String, Integer> batchCreateTable(RestaurantTableBatchCreateReqVO reqVO) {
        int successCount = 0;
        int skipCount = 0;
        int digitCount = String.valueOf(reqVO.getEndNo()).length();

        for (int i = reqVO.getStartNo(); i <= reqVO.getEndNo(); i++) {
            String tableNo = reqVO.getPrefix() + String.format("%0" + digitCount + "d", i);
            // 检查是否已存在
            RestaurantTableDO existing = restaurantTableMapper.selectByStoreIdAndTableNo(reqVO.getStoreId(), tableNo);
            if (existing != null) {
                skipCount++;
                continue;
            }
            RestaurantTableDO table = new RestaurantTableDO();
            table.setStoreId(reqVO.getStoreId());
            table.setArea(reqVO.getArea());
            table.setTableNo(tableNo);
            table.setSeatCapacity(reqVO.getSeatCapacity());
            table.setStatus(0); // 空闲
            restaurantTableMapper.insert(table);
            successCount++;
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("success", successCount);
        result.put("skip", skipCount);
        return result;
    }

    private void validateTableExists(Long id) {
        if (restaurantTableMapper.selectById(id) == null) {
            throw exception(TABLE_NOT_EXISTS);
        }
    }

    @Override
    public RestaurantTableDO getTable(Long id) {
        return restaurantTableMapper.selectById(id);
    }

    @Override
    public List<RestaurantTableDO> getTableList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return ListUtil.empty();
        }
        return restaurantTableMapper.selectByIds(ids);
    }

    @Override
    public PageResult<RestaurantTableDO> getTablePage(RestaurantTablePageReqVO pageReqVO) {
        return restaurantTableMapper.selectPage(pageReqVO);
    }

    @Override
    public List<RestaurantTableDO> getTableList() {
        return restaurantTableMapper.selectList();
    }

    @Override
    public byte[] generateQrCode(Long id) {
        RestaurantTableDO table = restaurantTableMapper.selectById(id);
        if (table == null) {
            throw exception(TABLE_NOT_EXISTS);
        }
        // 生成加密 token（防篡改）
        String token = tableTokenService.generateToken(table.getStoreId(), id);
        // 二维码内容：扫码URL带加密token
        String qrContent = qrCodeBaseUrl + "/scan?token=" + token;
        byte[] qrPng = cn.iocoder.yudao.module.restaurant.util.QrCodeUtils.generatePng(qrContent);

        // 存储图片路径和 token
        String qrDataUrl = "/restaurant/table/qr-image?id=" + id;
        table.setQrCode(qrDataUrl);
        table.setQrToken(token);
        restaurantTableMapper.updateById(table);

        return qrPng;
    }

    @Override
    public byte[] exportQrCodePdf(Long storeId) {
        // 查该门店所有桌台
        List<RestaurantTableDO> tables = restaurantTableMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RestaurantTableDO>()
                        .eq(RestaurantTableDO::getStoreId, storeId)
                        .orderByAsc(RestaurantTableDO::getTableNo));

        String[][] entries = new String[tables.size()][2];
        for (int i = 0; i < tables.size(); i++) {
            RestaurantTableDO table = tables.get(i);
            // 优先用已有的 token，没有则新生成
            String token = table.getQrToken();
            if (token == null || token.isBlank()) {
                token = tableTokenService.generateToken(storeId, table.getId());
                table.setQrToken(token);
                restaurantTableMapper.updateById(table);
            }
            String qrContent = qrCodeBaseUrl + "/scan?token=" + token;
            byte[] qrPng = cn.iocoder.yudao.module.restaurant.util.QrCodeUtils.generatePng(qrContent);
            entries[i][0] = table.getTableNo();
            entries[i][1] = new String(java.util.Base64.getEncoder().encode(qrPng));
        }

        return cn.iocoder.yudao.module.restaurant.util.PdfUtils.generateQrCodePdf(entries);
    }

    @Override
    public RestaurantTableScanRespVO scanTable(Long storeId, Long tableId) {
        return doScan(storeId, tableId);
    }

    @Override
    public RestaurantTableScanRespVO scanByToken(String token) {
        TableTokenService.TokenPayload payload;
        try {
            payload = tableTokenService.parseToken(token);
        } catch (Exception e) {
            throw exception(TABLE_TOKEN_INVALID);
        }
        return doScan(payload.storeId(), payload.tableId());
    }

    @Override
    public void occupyTable(Long tableId, Long orderId) {
        RestaurantTableDO table = restaurantTableMapper.selectById(tableId);
        if (table == null) {
            throw exception(TABLE_NOT_EXISTS);
        }
        table.setStatus(1); // 用餐中
        table.setCurrentOrderId(orderId);
        restaurantTableMapper.updateById(table);
    }

    @Override
    public void releaseTable(Long tableId) {
        RestaurantTableDO table = restaurantTableMapper.selectById(tableId);
        if (table == null) {
            throw exception(TABLE_NOT_EXISTS);
        }
        table.setStatus(0); // 空闲
        table.setCurrentOrderId(null);
        restaurantTableMapper.updateById(table);
    }

    /** 核心扫码校验逻辑 */
    private RestaurantTableScanRespVO doScan(Long storeId, Long tableId) {
        // 查门店
        RestaurantStoreDO store = restaurantStoreMapper.selectById(storeId);
        if (store == null) {
            throw exception(STORE_NOT_EXISTS);
        }
        // 校验门店营业状态
        if (store.getStatus() != null && store.getStatus() != 0) {
            throw exception(STORE_NOT_OPEN);
        }
        // 查桌台
        RestaurantTableDO table = restaurantTableMapper.selectById(tableId);
        if (table == null || !table.getStoreId().equals(storeId)) {
            throw exception(TABLE_NOT_EXISTS);
        }
        // 校验桌台状态
        if (table.getStatus() != null && table.getStatus() == 3) {
            throw exception(TABLE_LOCKED);
        }
        // 组装返回
        RestaurantTableScanRespVO vo = new RestaurantTableScanRespVO();
        vo.setTenantId(store.getTenantId());
        vo.setStoreId(store.getId());
        vo.setStoreName(store.getName());
        vo.setTableId(table.getId());
        vo.setTableNo(table.getTableNo());
        vo.setArea(table.getArea());
        vo.setSeatCapacity(table.getSeatCapacity());
        return vo;
    }

}
