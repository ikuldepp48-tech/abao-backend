package cn.iocoder.yudao.module.restaurant.service.dish;

import cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo.RestaurantDishImportResultVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface RestaurantDishImportService {

    RestaurantDishImportResultVO importDishes(MultipartFile file) throws IOException;

    void generateTemplate(HttpServletResponse response) throws IOException;

}
