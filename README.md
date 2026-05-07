# Cat Rescue API

[中文文档](README.zh-CN.md) | English

Stray cat rescue and tracking platform (Spring Boot + MySQL + multimodal models).

This project supports:

- Cat photo assessment for injury/feeding/TNR-related routing
- Same-cat deduplication (distance + time window + multimodal appearance traits)
- Cat heatmap and last-seen timeline
- Volunteer notifications (nearby unneutered sightings, 7-day absence reminder)
- Sci-fi themed bilingual frontend (`zh` / `en`)

---

## 1. Tech Stack

- Java 17
- Spring Boot 3.2.5
- Spring Web / Validation / Data JPA / WebFlux
- MySQL
- OpenAI vision (`gpt-4o-mini` by default; stub without `OPENAI_API_KEY`)
- Frontend: static `index.html` + Leaflet + leaflet.heat

---

## 2. Quick Start

### 2.1 Prerequisites

- JDK 17+
- Maven 3.8+
- MySQL 8+ recommended

### 2.2 Environment Variables

Optional model/config variables:

```bash
export OPENAI_API_KEY=your_sk_key
export TRACKING_MOCK_FEATURE_EXTRACTION=true
```

Database variables:

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_DATABASE=cat_rescue
export MYSQL_USER=root
export MYSQL_PASSWORD=your_password
```

HTTP port (optional; default **8090**):

```bash
export CAT_RESCUE_HTTP_PORT=8090
```

### 2.3 Run

```bash
mvn spring-boot:run
```

Then visit (default HTTP port **8090**; override with env `CAT_RESCUE_HTTP_PORT`):

- Frontend: `http://localhost:8090/`
- Health check: `http://localhost:8090/actuator/health`

---

## 3. Core APIs

### 3.1 Assessment

- `POST /api/v1/assessments` (multipart: `image`, optional `latitude`, `longitude`)
- `GET /api/v1/assessments/{id}`

### 3.2 Tracking / Dedup

- `POST /api/v1/sightings` (JSON)
- `POST /api/v1/sightings/upload` (multipart, recommended for web UI)
- `GET /api/v1/sightings/review`
- `POST /api/v1/sightings/{sightingId}/confirm-duplicate`
- `POST /api/v1/sightings/{sightingId}/reject-duplicate`

### 3.3 Cat Heatmap

- `GET /cats/{id}/heatmap`
- `GET /cats/{id}/heatmap/sightings`
- `GET /cats/{id}/last-seen`

### 3.4 Volunteer Notifications

- `GET /api/v1/volunteers/notifications?userId=...`

---

## 4. Frontend

File: `src/main/resources/static/index.html`

Features:

- Bilingual switch (`zh` / `en`)
- Photo assessment + geolocation/geocoding
- Auto “find same cat from current photo and center heatmap”
- Heat layer + point markers + popup with image/features/match status

---

## 5. Local Debug Without LLM Cost

Enable tracking mock extraction:

```bash
export TRACKING_MOCK_FEATURE_EXTRACTION=true
```

This bypasses OpenAI calls for tracking feature extraction.

---

## 6. Database Notes

Current config uses `spring.jpa.hibernate.ddl-auto=update` for development.

For legacy schema compatibility, ensure:

```sql
ALTER TABLE sightings MODIFY COLUMN image_url MEDIUMTEXT NOT NULL;
```

If `POST /api/v1/assessments` fails with **Data too long for column `image_bytes`** (large uploads or an older `BLOB` column), widen the column:

```sql
ALTER TABLE assessments MODIFY COLUMN image_bytes LONGBLOB NULL;
```

If MySQL returns **Packet for query is too large** (default `max_allowed_packet` is often 4MB), raise the server limit (and restart if needed):

```sql
SET GLOBAL max_allowed_packet = 67108864;
```

The JDBC URL in `application.yml` already requests a 64MB client buffer; the **server** must allow at least as much as your largest insert.

If you hit charset issues (`Incorrect string value`), migrate to `utf8mb4`:

```sql
ALTER DATABASE cat_rescue CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE assessments CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE cats CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE sightings CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 7. Postman

Collection file:

- `postman/Cat-Tracking-API.postman_collection.json`

---

## 8. Author

Project author: **zhangjiao**

