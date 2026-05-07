# Cat Rescue API（中文）

English: [README.md](README.md) | 中文

流浪猫救助与追踪平台（Spring Boot + MySQL + 多模态模型）。

项目支持：

- 上传猫咪照片进行伤病/喂养/绝育风险评估
- 同猫检索与去重（空间 + 时间窗口 + 多模态外观特征）
- 猫咪热力图与最后目击轨迹
- 志愿者通知（附近未绝育新目击、7天未再目击提醒）
- 科技风中英文前端页面

---

## 1. 技术栈

- Java 17
- Spring Boot 3.2.5
- Spring Web / Validation / Data JPA / WebFlux
- MySQL
- OpenAI 视觉模型（默认 `gpt-4o-mini`；未配置 Key 时为本地 stub）
- 前端：静态 `index.html` + Leaflet + leaflet.heat

---

## 2. 快速启动

### 2.1 环境要求

- JDK 17+
- Maven 3.8+
- MySQL（建议 8.0+）

### 2.2 环境变量

模型与调试开关（可选）：

```bash
export OPENAI_API_KEY=你的_sk_密钥
export TRACKING_MOCK_FEATURE_EXTRACTION=true
```

数据库连接：

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_DATABASE=cat_rescue
export MYSQL_USER=root
export MYSQL_PASSWORD=your_password
```

HTTP 端口（可选；默认 **8090**）：

```bash
export CAT_RESCUE_HTTP_PORT=8090
```

### 2.3 启动

```bash
mvn spring-boot:run
```

访问地址（默认 HTTP 端口 **8090**，可用环境变量 `CAT_RESCUE_HTTP_PORT` 修改）：

- 前端页面：`http://localhost:8090/`
- 健康检查：`http://localhost:8090/actuator/health`

---

## 3. 核心接口

### 3.1 评估接口

- `POST /api/v1/assessments`（multipart: `image`，可选 `latitude` / `longitude`）
- `GET /api/v1/assessments/{id}`

### 3.2 追踪与去重

- `POST /api/v1/sightings`（JSON）
- `POST /api/v1/sightings/upload`（multipart，前端推荐）
- `GET /api/v1/sightings/review`
- `POST /api/v1/sightings/{sightingId}/confirm-duplicate`
- `POST /api/v1/sightings/{sightingId}/reject-duplicate`

### 3.3 热力图

- `GET /cats/{id}/heatmap`
- `GET /cats/{id}/heatmap/sightings`
- `GET /cats/{id}/last-seen`

### 3.4 志愿者通知

- `GET /api/v1/volunteers/notifications?userId=...`

---

## 4. 前端能力

前端文件：`src/main/resources/static/index.html`

支持：

- 中英文切换
- 照片分析 + 定位 / 地址解析
- 自动“按当前照片检索同猫并定位热力图”
- 热力层 + 点位层 + 弹窗（图片/特征/同猫判定）

---

## 5. 本地无模型调试

如果不想每次都调用大模型：

```bash
export TRACKING_MOCK_FEATURE_EXTRACTION=true
```

开启后 tracking 特征提取会走 mock，不调用 OpenAI。

---

## 6. 数据库说明

项目当前采用 `spring.jpa.hibernate.ddl-auto=update`（开发环境）。

建议确认：

```sql
ALTER TABLE sightings MODIFY COLUMN image_url MEDIUMTEXT NOT NULL;
```

若 `POST /api/v1/assessments` 报错 **`image_bytes` 数据过长**（大图或旧库列为 `BLOB`），请扩大列：

```sql
ALTER TABLE assessments MODIFY COLUMN image_bytes LONGBLOB NULL;
```

若报错 **Packet for query is too large**（默认 `max_allowed_packet` 常为 4MB，存大图会超），在 MySQL 上提高限制（必要时重启）：

```sql
SET GLOBAL max_allowed_packet = 67108864;
```

`application.yml` 里 JDBC 已带较大的 `maxAllowedPacket`；**服务端** `max_allowed_packet` 仍须 ≥ 单次插入体积。

若出现字符集报错（`Incorrect string value`），建议迁移 `utf8mb4`：

```sql
ALTER DATABASE cat_rescue CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE assessments CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE cats CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE sightings CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 7. Postman

已提供集合：

- `postman/Cat-Tracking-API.postman_collection.json`

---

## 8. 作者

项目作者：**zhangjiao**
