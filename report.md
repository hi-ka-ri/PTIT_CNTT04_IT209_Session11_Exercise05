# Báo Cáo Kỹ Thuật Bài 5: Tách Luồng & Nối Job Tự Động Build & Push Docker Image Với Ràng Buộc Phụ Thuộc (Job Dependencies)


## 2. Bối Cảnh & Vấn Đề Kỹ Thuật

Trong quy trình phát triển phần mềm hiện đại và tích hợp liên tục (CI/CD), một nguyên tắc bất di bất dịch là **chất lượng mã nguồn phải luôn được đảm bảo trước khi phát hành**. 
- Nếu gộp toàn bộ quá trình kiểm thử (Unit Test) và đóng gói hình ảnh Docker (Docker Image) vào cùng một bước hoặc một Job đơn nhất:
  1. Thiếu tính phân tách nhiệm vụ (Separation of Concerns): Việc theo dõi thời gian và tài nguyên của từng giai đoạn trở nên khó khăn.
  2. Rủi ro phát hành lỗi: Nếu xử lý lỗi không chặt chẽ, Docker Image vẫn có thể vô tình được đẩy lên Container Registry (GHCR/Docker Hub), gây nguy cơ triển khai (deployment) phiên bản có bug ra môi trường Production/Staging.
  3. Lãng phí tài nguyên: Nếu test fail, runner không nên mất thời gian tải base image và chạy Buildx.

**Giải pháp**: Tách pipeline thành **2 Job độc lập**:
- **Job 1 (`test`)**: Chỉ tập trung kiểm thử chất lượng mã nguồn và đóng gói artifact JAR.
- **Job 2 (`docker-build`)**: Đóng gói Docker Image và phát hành lên GitHub Container Registry.
- **Cầu nối (`needs: test`)**: Thiết lập ràng buộc có điều kiện (DAG Dependency Graph), biến Job `test` thành cổng kiểm soát an toàn (Quality Gate).

---

## 3. Cấu Trúc Pipeline Và Cơ Chế Hoạt Động Của Từ Khóa `needs`

### 3.1. Sơ Đồ Đồ Thị Có Hướng Không Chu Trình (DAG - Directed Acyclic Graph)

GitHub Actions mô hình hóa các Job trong một workflow dưới dạng một đồ thị DAG. Khi thuộc tính `needs` được khai báo:

```mermaid
graph TD
    subgraph CI_CD_Pipeline ["GitHub Actions Workflow Pipeline"]
        direction LR
        A["<b>Job: test</b><br/>• actions/checkout<br/>• actions/setup-java (cache)<br/>• ./gradlew test<br/>• ./gradlew assemble"] 
        --> |"needs: test<br/>(Exit Code = 0)"| 
        B["<b>Job: docker-build</b><br/>• docker/login-action<br/>• docker/setup-buildx-action<br/>• docker/build-push-action<br/>• Push to ghcr.io"]
    end
    style A fill:#238636,stroke:#2ea44f,stroke-width:2px,color:#ffffff
    style B fill:#1f6feb,stroke:#388bfd,stroke-width:2px,color:#ffffff
```

### 3.2. Phân Tích Kỹ Thuật Từng Job

| Thành phần | Job `test` | Job `docker-build` |
| :--- | :--- | :--- |
| **Vai trò chính** | Quality Gatekeeper (Kiểm soát chất lượng) | Artifact Packaging & Delivery (Phát hành) |
| **Môi trường chạy** | `ubuntu-latest` | `ubuntu-latest` |
| **Công cụ cốt lõi** | JDK 17 Temurin, Gradle Wrapper | Docker Engine, Docker Buildx, GHCR CLI |
| **Điều kiện kích hoạt** | Tự động khi có `push` hoặc `pull_request` | **Phụ thuộc tuyệt đối vào `test` (`needs: test`)** |
| **Thời gian chạy ước tính** | ~20 - 30 giây (nhờ Gradle Dependency Cache) | ~35 - 50 giây (Multi-stage build & push) |
| **Quyền hạn (Permissions)** | `contents: read` | `contents: read`, `packages: write` |

---

## 4. Chi Tiết Kịch Bản Workflow (`ci.yml`)

```yaml
name: CI/CD Pipeline - Gradle Test & Docker GHCR Push

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

permissions:
  contents: read
  packages: write

jobs:
  test:
    name: Gradle Test & Package JAR
    runs-on: ubuntu-latest

    steps:
      - name: Checkout mã nguồn
        uses: actions/checkout@v4

      - name: Thiết lập môi trường Java JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Cấp quyền thực thi cho Gradle Wrapper
        run: chmod +x gradlew

      - name: Chạy kiểm thử tự động (Gradle Test)
        run: ./gradlew test --no-daemon

      - name: Đóng gói sản phẩm JAR (Build & Assemble)
        run: ./gradlew assemble --no-daemon

  docker-build:
    name: Build and Push Docker Image
    needs: test
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout mã nguồn
        uses: actions/checkout@v4

      - name: Đăng nhập vào GitHub Container Registry (GHCR)
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.REGISTRY_TOKEN || secrets.GITHUB_TOKEN }}

      - name: Thiết lập Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Chuẩn hóa metadata và tên Image cho GHCR
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository }}
          tags: |
            type=raw,value=latest
            type=sha,format=short

      - name: Đóng gói Dockerfile và Push lên GHCR
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./Dockerfile
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

---

## 5. Thiết Kế Dockerfile Tối Ưu (Multi-Stage Build)

Để Job `docker-build` có thể thực thi độc lập và bảo mật mà không phụ thuộc vào artifact trung gian giữa các runner, Dockerfile được thiết kế theo kiến trúc **Multi-stage build**:

```dockerfile
# Stage 1: Build stage (compile & package JAR)
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Runtime stage (Production JRE siêu nhẹ)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Ưu điểm vượt trội:
1. **Dung lượng tối ưu**: Loại bỏ toàn bộ JDK và Gradle cache, runtime image chỉ sử dụng JRE Alpine (~150MB so với ~500MB+ của JDK thông thường).
2. **An toàn bảo mật**: Chạy với `appuser` (Non-root user), ngăn ngừa rủi ro leo thang đặc quyền container breakout.
3. **Tính độc lập**: `docker/build-push-action@v5` tự xây dựng trọn vẹn từ nguồn mã đã checkout trong runner.

---

## 6. Đánh Giá Hai Kịch Bản Thực Thi (Test Matrix)

### Kịch bản 1: Mọi bài test đều đạt (Success - Happy Path)
- **Diễn biến**:
  - Job `test` chạy các bài kiểm thử `GreetingControllerTest` và `Session11Ex3ApplicationTests`.
  - Toàn bộ assertion đều pass $\rightarrow$ Exit Code: 0.
  - Job `test` chuyển màu xanh (**Success**).
  - Ràng buộc `needs: test` thỏa mãn $\rightarrow$ Kích hoạt Job `docker-build`.
  - Job `docker-build` hoàn tất việc login GHCR, build image và push tag `:latest` cùng `:${{ github.sha }}`.
- **Kết quả**: Cả 2 job đều xanh, mũi tên kết nối trên giao diện Actions hiển thị liền mạch.

### Kịch bản 2: Kiểm thử thất bại (Failure - Quality Gate Active)
- **Diễn biến**:
  - Giả định nhà phát triển sửa code làm hỏng logic hoặc kiểm thử JUnit thất bại (ví dụ: `assertEquals("Hello, World!", "Wrong Content")`).
  - Job `test` dừng lại ngay tại bước `./gradlew test --no-daemon` với lỗi kiểm thử. Trạng thái Job `test` đổi thành đỏ (**Failed**).
  - GitHub Actions đánh giá điều kiện `needs: test`: Không thỏa mãn yêu cầu thành công của Job trước.
  - **Job `docker-build` tự động chuyển sang trạng thái Skipped** (Bị bỏ qua - biểu tượng gạch chéo xám).
- **Ý nghĩa thực tế**:
  - Không có Docker Image lỗi nào được tạo ra hay đẩy lên registry.
  - Tiết kiệm tối đa thời gian tính phí (GitHub Actions Runner minutes) và tài nguyên băng thông.

---

## 7. Kết Luận

Kỹ thuật liên kết Job với từ khóa `needs` là nền tảng cốt lõi của kiến trúc CI/CD chuyên nghiệp. Nó giúp phân chia rõ ràng trách nhiệm giữa giai đoạn kiểm thử chất lượng (Verification) và giai đoạn phân phối sản phẩm (Packaging & Delivery), đồng thời thiết lập rào chắn tự động loại trừ mọi sản phẩm lỗi trước khi tiếp cận người dùng.
