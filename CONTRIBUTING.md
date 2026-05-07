# Contributing

感谢你对 `cat-rescue-api` 的关注与贡献。

## 1. Development Setup

1. Fork 本仓库并 clone 到本地
2. 准备环境：
   - Java 17+
   - Maven 3.8+
   - MySQL 8+（推荐）
3. 可选环境变量：

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_DATABASE=cat_rescue
export MYSQL_USER=root
export MYSQL_PASSWORD=your_password

export OPENAI_API_KEY=your_sk_key
export TRACKING_MOCK_FEATURE_EXTRACTION=true

# HTTP 端口默认 8090（与 HttpPortConfiguration 一致）；不要用 SERVER_PORT，改用 CAT_RESCUE_HTTP_PORT
# export CAT_RESCUE_HTTP_PORT=8090
```

4. 启动项目：

```bash
mvn spring-boot:run
```

浏览器默认：`http://localhost:8090/`（健康检查 `/actuator/health`）。

---

## 2. Branch & Commit

- 建议分支命名：
  - `feature/<topic>`
  - `fix/<topic>`
  - `docs/<topic>`
- 提交信息建议：
  - `feat: ...`
  - `fix: ...`
  - `docs: ...`
  - `refactor: ...`
  - `test: ...`

---

## 3. Code Style

- 保持与现有 Spring Boot 代码风格一致
- 新增逻辑请加简洁注释（解释“为什么”，不是“做了什么”）
- API 错误处理尽量返回可读 `ProblemDetail`
- 涉及模型输出的字段，优先做输入清洗（避免字符集/长度问题）

---

## 4. Testing

建议至少覆盖：

- 关键服务逻辑单元测试（routing、heatmap、dedup）
- 主要 API 手工验证（可使用 `postman/Cat-Tracking-API.postman_collection.json`）

执行测试：

```bash
mvn test
```

---

## 5. Pull Request Checklist

- [ ] 代码可编译、可启动
- [ ] 不包含密钥/凭据
- [ ] API 行为变更已更新文档（README / Postman）
- [ ] 关键路径已验证（至少本地）
- [ ] 变更说明清晰（背景、改动点、验证方式）

---

## 6. Security & Privacy

- 不要提交真实 API Key、数据库密码、个人隐私数据
- 上传图片仅用于推理或调试，不应泄露敏感内容

---

## 7. Questions

如有问题，可先创建 Issue，描述：

- 复现步骤
- 期望行为
- 实际行为
- 日志或错误信息

