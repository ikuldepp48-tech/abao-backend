package cn.iocoder.yudao.module.restaurant.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

/**
 * 二维码生成工具
 */
public class QrCodeUtils {

    private static final int DEFAULT_SIZE = 512;

    /**
     * 生成二维码 PNG 字节数组
     *
     * @param content 二维码内容（URL 或文本）
     * @param size    图片尺寸（像素），默认 512
     * @return PNG 图片字节数组
     */
    public static byte[] generatePng(String content, int size) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, size, size);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成二维码失败: " + e.getMessage(), e);
        }
    }

    public static byte[] generatePng(String content) {
        return generatePng(content, DEFAULT_SIZE);
    }

}
