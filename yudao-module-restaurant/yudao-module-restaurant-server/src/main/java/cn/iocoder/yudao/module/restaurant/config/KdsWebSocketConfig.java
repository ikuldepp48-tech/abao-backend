package cn.iocoder.yudao.module.restaurant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * KDS WebSocket 配置（STOMP 协议）
 * <p>
 * 与 yudao 自带 WebSocket（基于用户会话）不同，KDS 屏幕是匿名设备，
 * 使用 STOMP topic-based pub/sub，档口订阅对应 topic 接收订单推送。
 */
@Configuration
@EnableWebSocketMessageBroker
public class KdsWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 客户端订阅 /topic/kds/station/{stationId} 接收对应档口的订单推送
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // KDS 屏幕通过 ws://host:port/ws/kds 连接
        registry.addEndpoint("/ws/kds")
                .setAllowedOriginPatterns("*");
    }

}
