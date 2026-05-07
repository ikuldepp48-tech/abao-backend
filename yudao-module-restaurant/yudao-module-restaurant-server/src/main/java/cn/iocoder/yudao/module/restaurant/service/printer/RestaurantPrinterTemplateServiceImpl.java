package cn.iocoder.yudao.module.restaurant.service.printer;

import cn.iocoder.yudao.module.restaurant.controller.admin.printer.template.vo.RestaurantPrinterTemplateRespVO;
import cn.iocoder.yudao.module.restaurant.controller.admin.printer.template.vo.RestaurantPrinterTemplateSaveReqVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.printer.RestaurantPrinterTemplateDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.printer.RestaurantPrinterTemplateMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

@Service
public class RestaurantPrinterTemplateServiceImpl implements RestaurantPrinterTemplateService {

    @Resource
    private RestaurantPrinterTemplateMapper templateMapper;

    @Override
    public RestaurantPrinterTemplateRespVO getByPrinterId(Long printerId) {
        RestaurantPrinterTemplateDO entity = templateMapper.selectByPrinterId(printerId);
        if (entity == null) {
            return null;
        }
        return convert(entity);
    }

    @Override
    public void saveTemplate(RestaurantPrinterTemplateSaveReqVO reqVO) {
        RestaurantPrinterTemplateDO existing = templateMapper.selectByPrinterId(reqVO.getPrinterId());
        if (existing != null) {
            RestaurantPrinterTemplateDO updateObj = new RestaurantPrinterTemplateDO();
            updateObj.setId(existing.getId());
            updateObj.setPaperWidth(reqVO.getPaperWidth());
            updateObj.setHeaderText(reqVO.getHeaderText());
            updateObj.setFooterText(reqVO.getFooterText());
            updateObj.setShowLogo(reqVO.getShowLogo());
            updateObj.setShowQr(reqVO.getShowQr());
            updateObj.setAutoCut(reqVO.getAutoCut());
            updateObj.setPrintCopies(reqVO.getPrintCopies());
            templateMapper.updateById(updateObj);
        } else {
            RestaurantPrinterTemplateDO newObj = new RestaurantPrinterTemplateDO();
            newObj.setPrinterId(reqVO.getPrinterId());
            newObj.setPaperWidth(reqVO.getPaperWidth() != null ? reqVO.getPaperWidth() : 58);
            newObj.setHeaderText(reqVO.getHeaderText());
            newObj.setFooterText(reqVO.getFooterText());
            newObj.setShowLogo(reqVO.getShowLogo() != null ? reqVO.getShowLogo() : 1);
            newObj.setShowQr(reqVO.getShowQr() != null ? reqVO.getShowQr() : 0);
            newObj.setAutoCut(reqVO.getAutoCut() != null ? reqVO.getAutoCut() : 1);
            newObj.setPrintCopies(reqVO.getPrintCopies() != null ? reqVO.getPrintCopies() : 1);
            templateMapper.insert(newObj);
        }
    }

    private RestaurantPrinterTemplateRespVO convert(RestaurantPrinterTemplateDO entity) {
        RestaurantPrinterTemplateRespVO vo = new RestaurantPrinterTemplateRespVO();
        vo.setId(entity.getId());
        vo.setPrinterId(entity.getPrinterId());
        vo.setPaperWidth(entity.getPaperWidth());
        vo.setHeaderText(entity.getHeaderText());
        vo.setFooterText(entity.getFooterText());
        vo.setShowLogo(entity.getShowLogo());
        vo.setShowQr(entity.getShowQr());
        vo.setAutoCut(entity.getAutoCut());
        vo.setPrintCopies(entity.getPrintCopies());
        return vo;
    }

}
