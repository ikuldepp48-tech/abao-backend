package cn.iocoder.yudao.module.restaurant.service.kitchen;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * KDS WebSocket 推送服务
 */
@Slf4j
@Service
public class KdsPushService {

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 推送消息到指定档口
     */
    public void pushToStation(Long stationId, KdsPushMessage message) {
        String destination = "/topic/kds/station/" + stationId;
        messagingTemplate.convertAndSend(destination, message);
        log.info("[pushToStation][推送到档口({}) 订单号({})]", stationId,
                message.getData() != null ? message.getData().getOrderNo() : "null");
    }

}
