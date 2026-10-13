# ========================================================
# Multi-stage Dockerfile cho ứng dụng Spring Boot Gradle
# Stage 1: Build stage (compile & package JAR)
# ========================================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy các tệp cấu hình Gradle wrapper
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Cấp quyền thực thi cho gradle wrapper
RUN chmod +x gradlew

# Copy toàn bộ mã nguồn ứng dụng
COPY src src

# Đóng gói file JAR của Spring Boot (bỏ qua test vì test đã được chạy độc lập ở Job test)
RUN ./gradlew bootJar -x test --no-daemon

# ========================================================
# Stage 2: Runtime stage (Production JRE siêu nhẹ)
# ========================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Tạo user không có đặc quyền root để tăng tính bảo mật
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Sao chép tệp .jar từ builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Khai báo cổng 8080 cho ứng dụng Spring Boot
EXPOSE 8080

# Thiết lập điểm khởi chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]
