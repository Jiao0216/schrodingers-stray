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

推荐一键脚本（先起 MySQL，再执行；会等待数据库端口、可选后台启动 **cat-agent:8000**，再启动 Spring）：

```bash
./scripts/start-all.sh
```

日常可只用等价入口：`./scripts/dev-up.sh`（参数与 `start-all.sh` 相同，如 `--skip-agent`）。

仅 Java API（与原先一致）：

```bash
mvn spring-boot:run
```

或沿用加载 `.env.local` 的脚本：

```bash
./scripts/start-dev.sh
```

停止本脚本拉起的 cat-agent：`./scripts/stop-all.sh`（Spring 请在运行 `mvn` 的终端里 Ctrl+C）。

访问地址（默认 HTTP 端口 **8090**，可用环境变量 `CAT_RESCUE_HTTP_PORT` 修改）：

- 前端页面：`http://localhost:8090/`
- 健康检查：`http://localhost:8090/actuator/health`

### 2.4 站长 Agent（cat-agent）与图片格式

- **虚拟环境**：`cat-agent/.venv` 已加入 `.gitignore`，勿提交。使用 **`./scripts/dev-up.sh`** / `start-all.sh` 时会**自动**创建 `.venv` 并执行 `pip install -r cat-agent/requirements.txt`（解决 Homebrew Python 的 PEP 668，勿对系统 `python3` 全局 `pip install`）。若只手动起 agent：  
  `cd cat-agent && python3 -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt && uvicorn api:app --host 127.0.0.1 --port 8000`
- **HEIC / iPhone 照片**：上传分析、同猫检索、按图找猫前，前端会尝试用 **heic2any**（CDN 加载，需联网）转为 **JPEG**，以满足 OpenAI Vision 仅支持 `png/jpeg/gif/webp` 的限制。
- **附近机构推荐逻辑**（评估列表 + Agent 调用的 nearest 接口）：见 [`docs/recommendation-engine.zh-CN.md`](docs/recommendation-engine.zh-CN.md)。
- **3 分钟产品 Demo 剧本**（单对话框、工具 / 联网 / RAG 分工）：见 [`docs/agent_demo.md`](docs/agent_demo.md)。

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
