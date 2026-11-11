# AI Agent 对话项目

项目包含 Vue 3 前端和 Spring Boot 后端。后端需要 Java 21。

## 配置与启动后端

在终端配置自己的 DeepSeek API 密钥，然后启动：

```sh
export DEEPSEEK_API_KEY='填入自己的密钥'
cd server
./gradlew bootRun
```

后端默认监听 8080，流式接口为 `GET /api/agent/chat?message=...`。搜索工具目前返回模拟结果。

## 启动前端

```sh
cd frontend
npm ci
cp .env.example .env.local
```

编辑 `frontend/.env.local`，把 `VITE_USE_MOCK` 改为 `false`，然后执行：

```sh
npm run dev
```

按终端显示的地址访问。开发环境将 `/api` 请求转发到本机 8080 端口。
