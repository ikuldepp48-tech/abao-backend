package cn.iocoder.yudao.module.restaurant.service.table;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.table.vo.*;
import cn.iocoder.yudao.module.restaurant.convert.table.RestaurantTableConvert;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.table.RestaurantTableDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.table.RestaurantTableMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.TABLE_NOT_EXISTS;

@Service
@Validated
public class RestaurantTableServiceImpl implements RestaurantTableService {

    @Resource
    private RestaurantTableMapper restaurantTableMapper;

    @Value("${restaurant.qr-code.base-url:http://localhost:5173}")
    private String qrCodeBaseUrl;

    @Override
    public Long createTable(RestaurantTableCreateReqVO createReqVO) {
        RestaurantTableDO table = RestaurantTableConvert.INSTANCE.convert(createReqVO);
        restaurantTableMapper.insert(table);
        return table.getId();
    }

    @Override
    public void updateTable(RestaurantTableUpdateReqVO updateReqVO) {
        validateTableExists(updateReqVO.getId());
        RestaurantTableDO updateObj = RestaurantTableConvert.INSTANCE.convert(updateReqVO);
        restaurantTableMapper.updateById(updateObj);
    }

    @Override
    public void deleteTable(Long id) {
        validateTableExists(id);
        restaurantTableMapper.deleteById(id);
    }

    @Override
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
        // 二维码内容：包含门店ID和桌台ID的扫码URL
        // 开发期用 localhost 占位，上线后替换为真实域名
        String qrContent = qrCodeBaseUrl + "/scan?storeId=" + table.getStoreId() + "&tableId=" + id;
        byte[] qrPng = cn.iocoder.yudao.module.restaurant.util.QrCodeUtils.generatePng(qrContent);

        // 把二维码内容以 Base64 形式存回数据库
        String qrDataUrl = "/restaurant/table/qr-image?id=" + id;
        table.setQrCode(qrDataUrl);
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
            String qrContent = qrCodeBaseUrl + "/scan?storeId=" + storeId + "&tableId=" + table.getId();
            byte[] qrPng = cn.iocoder.yudao.module.restaurant.util.QrCodeUtils.generatePng(qrContent);
            entries[i][0] = table.getTableNo();
            entries[i][1] = new String(java.util.Base64.getEncoder().encode(qrPng));
        }

        return cn.iocoder.yudao.module.restaurant.util.PdfUtils.generateQrCodePdf(entries);
    }

}
