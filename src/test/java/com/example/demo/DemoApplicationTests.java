package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DemoApplicationTests {

    @Test
    void contextLoads() {
        // Test kiểm thử ngữ cảnh Spring Boot khởi động thành công
        assertTrue(true);
    }

    /*
     * GỢI Ý THỰC HIỆN BƯỚC KIỂM TRA ĐỀ BÀI:
     * "Thử sửa code làm đứt JUnit Test, xác nhận Job docker-build tự động chuyển trạng thái Skipped"
     *
     * Để làm bài test bị fail, bạn có thể uncomment test dưới đây hoặc sửa assertTrue(false):
     *
     * @Test
     * void testFailToVerifySkip() {
     *     org.junit.jupiter.api.Assertions.fail("Cố tình làm fail test để kiểm tra tính năng needs: test");
     * }
     */
}
