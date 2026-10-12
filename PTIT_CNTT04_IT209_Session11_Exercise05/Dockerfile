# ==========================================
# Stage 1: Build JAR bằng Gradle
# ==========================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Sao chép các tệp cấu hình Gradle trước để tận dụng Docker layer caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

# Sao chép mã nguồn và đóng gói JAR (bỏ qua test vì đã test ở job CI)
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# ==========================================
# Stage 2: Runtime Image gọn nhẹ
# ==========================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Sao chép file JAR từ stage builder sang
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
