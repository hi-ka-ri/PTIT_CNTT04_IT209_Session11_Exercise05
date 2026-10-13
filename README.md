# Báo Cáo & Hướng Dẫn Bài 5: Tự Động Hóa CI/CD - Nối Job Build & Push Docker Image Sau Khi Test Gradle Xanh


---

## 2. Mục Tiêu & Yêu Cầu Kỹ Thuật

### 2.1. Mục Tiêu
1. **Tách luồng CI thành 2 Job độc lập**:
   - `test`: Đảm nhận việc thiết lập Java 17, khôi phục Gradle cache, chạy bộ kiểm thử tự động (Unit/Integration Test) và đóng gói file JAR.
   - `docker-build`: Đảm nhận việc xác thực đăng nhập Container Registry (GHCR), đóng gói Docker Image từ Dockerfile và đẩy (push) lên GitHub Packages/GHCR.
2. **Ràng buộc thứ tự thực thi (Job Dependency)**:
   - Sử dụng từ khóa `needs: test` để tạo quan hệ phụ thuộc có điều kiện.
   - Chỉ khi Job `test` hoàn thành xuất sắc (Exit code 0, trạng thái Success màu xanh) thì Job `docker-build` mới được kích hoạt.
   - Nếu Job `test` thất bại (Failure màu đỏ do Unit Test lỗi), Job `docker-build` sẽ ngay lập tức chuyển sang trạng thái **Skipped** (Bị bỏ qua), ngăn chặn hoàn toàn việc đóng gói và phát hành mã nguồn lỗi ra môi trường Registry.

### 2.2. Yêu Cầu Cụ Thể
- **Tệp kịch bản `.github/workflows/ci.yml`**:
  - Khai báo Job 1 (`test`) thực hiện Gradle Test & Assemble.
  - Khai báo Job 2 (`docker-build`) với thuộc tính `needs: test`.
  - Trong Job `docker-build`, thực thi 2 hành động chủ chốt: Đăng nhập Registry (`docker/login-action@v3`) và đóng gói/đẩy image (`docker/build-push-action@v5`).
- **Tối ưu Dockerfile**:
  - Áp dụng kỹ thuật Multi-stage build (Builder stage & Runtime stage) với base image JRE Alpine siêu nhẹ và Non-root user đảm bảo chuẩn bảo mật.
- **Sản phẩm nộp bài**:
  - Tệp `.github/workflows/ci.yml` hoàn chỉnh.
  - Tệp `report.md` và `README.md` ghi nhận kết quả và cơ chế hoạt động.
  - Sơ đồ trực quan trên tab Actions thể hiện mối liên kết `test` $\rightarrow$ `docker-build`.

---

## 3. Cấu Trúc Thư Mục Dự Án

```plaintext
IT209-homework-session_11-ex5-/
├── .github/
│   └── workflows/
│       └── ci.yml                 # Kịch bản GitHub Actions gồm 2 Job liên kết qua needs
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── src/
│   ├── main/
│   │   ├── java/com/example/caching/
│   │   │   ├── Session11Ex3Application.java
│   │   │   └── controller/
│   │   │       └── GreetingController.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/example/caching/
│           ├── Session11Ex3ApplicationTests.java
│           └── GreetingControllerTest.java
├── .gitignore                     # Loại trừ build/, .gradle/, IDE cache
├── build.gradle                   # Cấu hình Spring Boot 3.3.4, Java 17, JUnit 5
├── Dockerfile                     # Multi-stage build tối ưu cho Spring Boot
├── gradlew                        # Script thực thi Gradle trên Linux/macOS
├── gradlew.bat                    # Script thực thi Gradle trên Windows
├── settings.gradle                # Tên root project: session11-ex5-docker-pipeline
├── README.md                      # Hướng dẫn tổng quan bài tập
└── report.md                      # Báo cáo kỹ thuật chi tiết
```

---

## 4. Kịch Bản CI/CD (`.github/workflows/ci.yml`)

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
  # ==========================================================
  # Job 1: Chạy kiểm thử tự động và đóng gói sản phẩm JAR
  # ==========================================================
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

  # ==========================================================
  # Job 2: Đóng gói Docker Image và đẩy lên GHCR
  # Ràng buộc: Chỉ chạy khi Job test hoàn thành thành công (needs: test)
  # ==========================================================
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

## 5. Cơ Chế Điều Khiển Luồng Của `needs`

### 5.1. Khi Kiểm Thử Thành Công (Happy Path)
```mermaid
graph LR
    A["Job test: Gradle Test & Assemble"] -->|Exit code: 0 Success| B["Job docker-build: Build & Push GHCR"]
    style A fill:#2ea44f,stroke:#22863a,stroke-width:2px,color:#ffffff
    style B fill:#2ea44f,stroke:#22863a,stroke-width:2px,color:#ffffff
```
1. Job `test` chạy toàn bộ JUnit test và biên dịch JAR.
2. Kiểm thử hoàn thành với trạng thái **Success (Màu xanh)**.
3. Bộ điều phối GitHub Actions phân giải quan hệ `needs: test` $\rightarrow$ Thỏa mãn điều kiện $\rightarrow$ Kích hoạt Job `docker-build`.
4. Job `docker-build` xác thực với GHCR, đóng gói Docker Image và push thành công.

### 5.2. Khi Kiểm Thử Thất Bại (Failure Gatekeeper)
```mermaid
graph LR
    A["Job test: Gradle Test & Assemble"] -->|JUnit Assertion Failed| B["Job docker-build: Skipped"]
    style A fill:#cb2431,stroke:#b31d28,stroke-width:2px,color:#ffffff
    style B fill:#6a737d,stroke:#586069,stroke-width:2px,stroke-dasharray: 5 5,color:#ffffff
```
1. Một ca kiểm thử JUnit bị đứt hoặc lỗi biên dịch.
2. Job `test` dừng lại và trả về trạng thái **Failure (Màu đỏ)**.
3. Ràng buộc `needs: test` không thỏa mãn.
4. Job `docker-build` chuyển trạng thái sang **Skipped (Bị bỏ qua - Màu xám)** mà không tốn tài nguyên chạy runner, bảo vệ môi trường registry khỏi việc phát tán bản build lỗi.

---

## 6. Hướng Dẫn Kiểm Thử Tình Huống JUnit Test Bị Lỗi

Để kiểm chứng tính năng tự động chuyển trạng thái `Skipped` của Job `docker-build`:
1. Mở file `src/test/java/com/example/caching/GreetingControllerTest.java`.
2. Sửa một khẳng định kiểm thử sai, ví dụ:
   ```java
   assertEquals("Hello, World!", "Wrong Content");
   ```
3. Commit và đẩy lên nhánh `main`:
   ```bash
   git commit -am "test: verify skipped status when test fails"
   git push origin main
   ```
4. Vào tab **Actions** trên GitHub repository:
   - Sẽ thấy Job `test` có dấu gạch chéo đỏ (**Failed**).
   - Job `docker-build` có biểu tượng gạch chéo xám (**Skipped**).
   - Biểu đồ sơ đồ Workflow thể hiện rõ Job `docker-build` không được kích hoạt.
5. Sửa lại mã kiểm thử đúng và push lại để khôi phục trạng thái xanh toàn bộ pipeline.
