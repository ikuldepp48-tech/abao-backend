package cn.iocoder.yudao.module.restaurant.util;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPCell;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * PDF 生成工具
 */
public class PdfUtils {

    /**
     * 生成桌台二维码 PDF（2x2 网格，每页 4 个二维码）
     *
     * @param entries [桌号, 二维码PNG的Base64字符串]
     * @return PDF 字节数组
     */
    public static byte[] generateQrCodePdf(String[][] entries) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Paragraph title = new Paragraph("桌台二维码", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            float imgWidth = 180;

            for (int i = 0; i < entries.length; i++) {
                String tableNo = entries[i][0];
                byte[] qrPng = Base64.getDecoder().decode(entries[i][1]);

                Image pdfImage = Image.getInstance(qrPng);
                pdfImage.scaleToFit(imgWidth, imgWidth);

                PdfPTable cellTable = new PdfPTable(1);
                cellTable.setWidthPercentage(45);

                PdfPCell imgCell = new PdfPCell(pdfImage);
                imgCell.setBorder(Rectangle.NO_BORDER);
                imgCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellTable.addCell(imgCell);

                Font textFont = new Font(Font.HELVETICA, 12, Font.BOLD);
                PdfPCell textCell = new PdfPCell(new Phrase(tableNo, textFont));
                textCell.setBorder(Rectangle.NO_BORDER);
                textCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                textCell.setPaddingTop(5);
                cellTable.addCell(textCell);

                document.add(cellTable);
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成PDF失败: " + e.getMessage(), e);
        }
    }

}
