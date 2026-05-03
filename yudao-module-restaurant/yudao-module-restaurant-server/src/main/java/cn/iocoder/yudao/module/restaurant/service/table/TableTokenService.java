package cn.iocoder.yudao.module.restaurant.service.table;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 桌台二维码 Token 服务（AES 加密/解密）
 */
@Component
public class TableTokenService {

    @Value("${restaurant.qr-code.aes-key:abao-restaurant-qr-token}")
    private String aesKey;

    private AES aes;

    @PostConstruct
    public void init() {
        aes = SecureUtil.aes(aesKey.getBytes());
    }

    /**
     * 生成加密 token，内容为 "storeId:tableId"
     * 输出为 URL-safe 的 hex 字符串
     */
    public String generateToken(Long storeId, Long tableId) {
        return aes.encryptHex(storeId + ":" + tableId);
    }

    /**
     * 解析 token，返回 [storeId, tableId]
     * @throws IllegalArgumentException token 无效时
     */
    public TokenPayload parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token为空");
        }
        try {
            String plain = aes.decryptStr(token);
            String[] parts = plain.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("token格式错误");
            }
            return new TokenPayload(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("token无效: " + e.getMessage());
        }
    }

    /**
     * Token 负载
     */
    public record TokenPayload(Long storeId, Long tableId) {}
}
