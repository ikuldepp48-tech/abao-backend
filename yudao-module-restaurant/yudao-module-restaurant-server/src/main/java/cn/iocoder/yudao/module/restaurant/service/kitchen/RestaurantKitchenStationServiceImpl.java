package cn.iocoder.yudao.module.restaurant.service.kitchen;

import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.restaurant.controller.admin.kitchen.vo.RestaurantKitchenStationSaveReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.kitchen.RestaurantKitchenStationDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.kitchen.RestaurantKitchenStationMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.restaurant.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.restaurant.enums.LogRecordConstants.*;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class RestaurantKitchenStationServiceImpl implements RestaurantKitchenStationService {

    @Resource
    private RestaurantKitchenStationMapper stationMapper;

    @Override
    @LogRecord(type = KITCHEN_STATION_TYPE, subType = KITCHEN_STATION_CREATE_SUB_TYPE, bizNo = "0",
            success = KITCHEN_STATION_CREATE_SUCCESS)
    public Long createStation(RestaurantKitchenStationSaveReqVO reqVO) {
        RestaurantKitchenStationDO station = RestaurantKitchenStationDO.builder()
                .name(reqVO.getName())
                .dishCategories(JSONUtil.toJsonStr(reqVO.getDishCategories()))
                .sort(reqVO.getSort() != null ? reqVO.getSort() : 0)
                .status(reqVO.getStatus() != null ? reqVO.getStatus() : 1)
                .build();
        stationMapper.insert(station);
        return station.getId();
    }

    @Override
    @LogRecord(type = KITCHEN_STATION_TYPE, subType = KITCHEN_STATION_UPDATE_SUB_TYPE, bizNo = "{{#reqVO.id}}",
            success = KITCHEN_STATION_UPDATE_SUCCESS)
    public void updateStation(RestaurantKitchenStationSaveReqVO reqVO) {
        RestaurantKitchenStationDO station = stationMapper.selectById(reqVO.getId());
        if (station == null) {
            throw exception(KITCHEN_STATION_NOT_EXISTS);
        }
        station.setName(reqVO.getName());
        station.setDishCategories(JSONUtil.toJsonStr(reqVO.getDishCategories()));
        station.setSort(reqVO.getSort());
        station.setStatus(reqVO.getStatus());
        stationMapper.updateById(station);
    }

    @Override
    @LogRecord(type = KITCHEN_STATION_TYPE, subType = KITCHEN_STATION_DELETE_SUB_TYPE, bizNo = "{{#id}}",
            success = KITCHEN_STATION_DELETE_SUCCESS)
    public void deleteStation(Long id) {
        stationMapper.deleteById(id);
    }

    @Override
    public RestaurantKitchenStationDO getStation(Long id) {
        return stationMapper.selectById(id);
    }

    @Override
    public PageResult<RestaurantKitchenStationDO> getStationPage(Integer pageNo, Integer pageSize) {
        return stationMapper.selectPage(new PageParam() {{
            setPageNo(pageNo);
            setPageSize(pageSize);
        }}, new LambdaQueryWrapperX<RestaurantKitchenStationDO>()
                .orderByAsc(RestaurantKitchenStationDO::getSort));
    }

    @Override
    public List<RestaurantKitchenStationDO> listEnabledStations() {
        return stationMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RestaurantKitchenStationDO>()
                        .eq(RestaurantKitchenStationDO::getStatus, 1)
                        .orderByAsc(RestaurantKitchenStationDO::getSort));
    }
}
