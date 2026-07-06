package com.geihou.module.finance.cart.enums;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Legacy cart method scan test (Cart root-cause 5, AC-17).
 *
 * <p>Verifies that the codebase contains no legacy addCart/addToCart/oldAddCart
 * method names in the cart module source code.
 */
class LegacyCartMethodScanTest {

    private static final String CART_SOURCE_DIR =
            "src/main/java/com/geihou/module/finance/cart";

    private static final String[] FORBIDDEN_METHOD_PATTERNS = {
            "addCart", "addToCart", "oldAddCart", "cartAdd_old"
    };

    @Test
    void noLegacyCartMethodsInCodebase() throws IOException {
        Path baseDir = Paths.get(CART_SOURCE_DIR);
        if (!Files.exists(baseDir)) {
            // If running from different working directory, try relative to module root
            baseDir = Paths.get("geihou-module-finance/geihou-module-finance-biz", CART_SOURCE_DIR);
        }
        assertThat(Files.exists(baseDir))
                .as("Cart source directory must exist: %s", baseDir)
                .isTrue();

        try (Stream<Path> paths = Files.walk(baseDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file);
                            for (String pattern : FORBIDDEN_METHOD_PATTERNS) {
                                assertThat(content)
                                        .as("File %s must not contain legacy method name '%s'", file, pattern)
                                        .doesNotContain(pattern);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    @Test
    void noDebugFieldsInMigrations() throws IOException {
        Path migrationDir = Paths.get(
                "geihou-module-finance/geihou-module-finance-biz/src/main/resources/db/migration");
        if (!Files.exists(migrationDir)) {
            migrationDir = Paths.get("src/main/resources/db/migration");
        }
        assertThat(Files.exists(migrationDir)).isTrue();

        String[] debugPatterns = {"is_test", "is_mock", "is_debug", "fake_"};

        try (Stream<Path> paths = Files.list(migrationDir)) {
            paths.filter(p -> p.getFileName().toString().startsWith("V02_06"))
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file);
                            for (String pattern : debugPatterns) {
                                assertThat(content)
                                        .as("Migration %s must not contain debug field '%s'", file, pattern)
                                        .doesNotContain(pattern);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
