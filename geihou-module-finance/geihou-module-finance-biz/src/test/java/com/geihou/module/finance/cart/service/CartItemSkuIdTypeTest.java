package com.geihou.module.finance.cart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cart item skuId type validation test (Cart root-cause 2).
 *
 * <p>Tests that skuId=0 is rejected by BeanValidation (@Min(1)),
 * skuId="abc" is rejected by Jackson (type mismatch),
 * and skuId=null is rejected by BeanValidation (@NotNull).
 */
class CartItemSkuIdTypeTest {

    private static ValidatorFactory factory;
    private static Validator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void addItemWithZeroSkuIdRejected() {
        CartAddItemReqVO vo = new CartAddItemReqVO();
        vo.setSkuId(0L); // @Min(1) violation
        vo.setQuantity(1);

        Set<ConstraintViolation<CartAddItemReqVO>> violations = validator.validate(vo);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("skuId")))
                .isTrue();
    }

    @Test
    void addItemWithStringSkuIdRejected() throws Exception {
        String json = "{\"skuId\":\"abc\",\"quantity\":1}";

        assertThatThrownBy(() -> objectMapper.readValue(json, CartAddItemReqVO.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void addItemWithNullSkuIdRejected() {
        CartAddItemReqVO vo = new CartAddItemReqVO();
        vo.setSkuId(null); // @NotNull violation
        vo.setQuantity(1);

        Set<ConstraintViolation<CartAddItemReqVO>> violations = validator.validate(vo);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("skuId")))
                .isTrue();
    }

    @Test
    void addItemWithNegativeSkuIdRejected() {
        CartAddItemReqVO vo = new CartAddItemReqVO();
        vo.setSkuId(-1L); // @Min(1) violation
        vo.setQuantity(1);

        Set<ConstraintViolation<CartAddItemReqVO>> violations = validator.validate(vo);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("skuId")))
                .isTrue();
    }

    @Test
    void addItemWithValidSkuIdPasses() {
        CartAddItemReqVO vo = new CartAddItemReqVO();
        vo.setSkuId(1001L);
        vo.setQuantity(1);

        Set<ConstraintViolation<CartAddItemReqVO>> violations = validator.validate(vo);
        assertThat(violations).isEmpty();
    }
}
