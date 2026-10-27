# Arknights Gacha Recorder

明日方舟抽卡记录工具。一个独立 Git 仓库，四个模块：

| 模块 | 职责 |
| --- | --- |
| `modeling` | 公共数据模型、Agent 通信协议与兼容性测试 |
| `backend` | Ktor 接口、任务调度和 PostgreSQL 存储 |
| `agent` | 执行鹰角接口采集任务的 JVM 应用 |
| `webui` | Kotlin/JS、React、Ant Design 页面 |

## 构建

使用仓库自带的 `./kotlin`（Windows 使用 `kotlin.bat`）。Toolchain 固定为 **0.13.0**，Kotlin **2.4.21**、JDK **25**、Ktor **3.5.1** 在 `common.module-template.yaml` 统一配置。首次执行会下载所需工具；可设置 `JAVA_HOME` 使用本机 JDK 25。

```sh
./kotlin build -p jvm
./kotlin test -m modeling -p jvm

cd webui
npm ci
npm run build
```

后端保留 JVM、Linux x64 和 macOS ARM64 目标。Toolchain 要求分别声明应用，因此 `backend/jvm`、`backend/linux`、`backend/macos` 只提供启动入口，共用 `backend/src`。

```sh
./scripts/package-agent.sh                  # dist/agent/agent.jar
./scripts/package-native.sh linuxX64        # dist/linuxX64/arknights-gacha-recorder.kexe
./scripts/package-native.sh macosArm64 debug
```

## 本地运行

准备 PostgreSQL，在仓库根目录启动后端：

```sh
export DATABASE_URL=postgresql://127.0.0.1:5432/postgres
export DATABASE_USER=postgres
export DATABASE_PASSWORD=postgres
export AGENT_KEY=local-development-key
export OPEN_LOOP=false
./kotlin run -m jvm
```

默认监听 `8080`，可通过 `PORT` 修改。首次启动会初始化表结构，健康检查为 `/healthcheck`。`OPEN_LOOP=true` 开启后端自身的采集循环；使用独立 Agent 时保持 `false`。

另开终端启动 Agent，`AGENT_KEY` 与后端一致：

```sh
AGENT_KEY=local-development-key SERVER_HOST=localhost SERVER_PORT=8080 ./kotlin run -m agent
```

默认 WebSocket 路径为 `/agent/ws`，可通过 `SERVER_PATH` 修改。

前端在 `webui` 目录运行 `npm ci && npm run dev`，访问 `http://127.0.0.1:5173`。开发代理把 `/api/ark/*` 转发到 `http://localhost:8080` 并移除前缀。修改 Kotlin 代码后重新运行 `npm run compile:kotlin`，Vite 会加载更新后的产物。

## 部署

前端 `npm run build` 输出到 `webui/dist/`，可用 `npm run preview` 本地检查。生产服务器需将 `/api/ark/*` 转发到后端并移除前缀，为 `/agent/ws` 配置 WebSocket 转发。

Docker 构建统一使用仓库根目录作为上下文：

```sh
docker build --platform linux/amd64 -f backend/Dockerfile -t arknights-gacha-recorder:local .
docker build -f agent/Dockerfile -t arknights-gacha-agent:local .
DATABASE_PASSWORD=your-password AGENT_KEY=your-agent-key docker compose -f backend/docker-compose.yml up --build -d
```

## 工具链说明

Kotlin Toolchain 仍处于实验阶段。0.13.0 的 `build` 在 WebUI 模块只生成 KLIB，所以前端脚本使用 `task :webui:linkJs` 链接，再复制完整产物供 Vite 解析 npm 依赖。当前链接入口位于 `build/artifacts/CompiledWebArtifact/webuijsrelease/kotlin-output/webui.mjs`；升级工具链时需检查这一任务与路径。

三个旧仓库的完整历史已导入当前 Git 仓库，不再关联或同步旧远端。构建产物、npm 依赖、IDE 状态和本地 `.env` 不纳入版本控制。
