# MindMate

MindMate 是一个校园心理健康智能体，支持动态路由 RAG、SSE 流式输出、后台风险识别、Excel/邮件预警和多 Agent 协作。

> **来源声明**
>
> 本项目原名为 MindBridge，现更名为 MindMate，本项目基于 MindBridge 开源代码二次开发并进行了安全加固与功能重构。原始交付包未包含上游仓库地址和 LICENSE 文件；本仓库对二次开发新增和修改的部分采用 MIT License，上游代码、文档、图片、数据集和模型衍生资产仍归原作者所有并受其原始许可约束。公开发布或再分发前，请自行确认上游授权并保留所有必需的版权和 NOTICE 信息。详见 [LICENSE](LICENSE) 和 [NOTICE](NOTICE)。

## 主要能力

- 动态路由 RAG：先识别 `CHAT / CONSULT / RISK`，闲聊不查知识库，咨询与风险消息才进入检索增强。
- SSE 流式输出：`/api/chat/stream` 返回 `text/event-stream`，适合前端做打字机效果。
- 高风险安全兜底：命中高危词库时，在进入 Agent/LLM 之前直接返回固定安全话术和心理援助热线。
- 后台心理状态识别：记录情绪标签、情绪分数、风险等级和置信度，但学生端不展示评估结果。
- 数据闭环：咨询/风险消息写入数据库，高风险报告可写入 Excel，并触发邮件或 HTTP/MCP 预警。
- Spring AI 模型接入：默认通过 Ollama 调用项目模型，也可切到 OpenAI；`mock` 用于无模型离线演示。
- 可替换知识库：默认本地轻量检索，可启用 Chroma。
- 多 Agent loop：由 MemoryAgent、SupervisorAgent、KnowledgeAgent、RiskGuardianAgent 和回复 Agent 协作完成，最多执行 8 步。

## 目录结构

```text
src/main/java/com/mindbridge/agent
├── config                 # 配置、安全、AI/MCP Bean
├── controller             # Chat / Knowledge / Report API
├── domain                 # JPA 实体与枚举
├── dto                    # 请求与响应对象
├── repository             # Spring Data JPA
├── security               # 当前用户与认证查询
└── service
    ├── ai                 # Spring AI 模型适配器、mock 客户端与 Prompt
    ├── agent              # 多 Agent loop：记忆、路由、知识检索、风险守护与回复规划
    ├── knowledge          # 切块、检索、Chroma 网关
    └── mcp                # Excel 与邮件/HTTP 预警工具
```

## Agent loop

每轮对话进入一个有限步 agent loop，最多执行 8 步：

```text
MemoryAgent
-> SupervisorAgent
-> KnowledgeAgent
-> RiskGuardianAgent
-> CompanionAgent / CounselorAgent
```

- `MemoryAgent`：读取 Redis 短期记忆；Redis 为空时从数据库恢复最近上下文。
- `SupervisorAgent`：判断 `CHAT / CONSULT / RISK`，决定后续链路。
- `KnowledgeAgent`：改写检索 query，执行 RAG 检索并在必要时二次检索。
- `RiskGuardianAgent`：执行后台心理状态评估，同时保留高风险词库硬兜底。
- `CompanionAgent`：生成普通聊天回复策略。
- `CounselorAgent`：生成心理支持回复策略，并结合记忆、RAG 和风险结果。
## 高风险安全兜底

`RiskLexicon` 维护了一组明确高危词，例如自杀、不想活了、活不下去、结束生命、伤害自己等。

`ChatService.prepare(...)` 会在调用 `AgentRuntimeService` 和任何 LLM 之前检查输入：

1. 命中高危词；
2. 保存用户消息和高风险报告；
3. 直接返回固定安全话术；
4. 话术包含全国心理援助热线 `12356`，以及紧急求助 `110/120`；
5. **不调用 Agent，也不调用 LLM**；
6. 按配置异步执行后台 Excel/邮件预警工具链。

相关单元测试：`src/test/java/com/mindbridge/agent/service/ChatServiceHighRiskFallbackTest.java`。

> 该兜底不能替代现实中的紧急救助。如果用户已经处于危险中，应优先联系身边的人、学校心理中心、急救或警方。

## 环境要求

- JDK 17
- Maven 3.9+
- Ollama（使用本地模型时需要）
- Docker / Docker Compose（使用容器部署时需要）

## 快速启动

### 1. 配置首次启动账号

本项目不再内置默认账号或默认密码。首次启动前必须通过环境变量配置：

```bash
export MIND_BRIDGE_ADMIN_USERNAME=admin
export MIND_BRIDGE_ADMIN_PASSWORD='请替换为强密码'
export MIND_BRIDGE_STUDENT_USERNAME=student
export MIND_BRIDGE_STUDENT_PASSWORD='请替换为强密码'
```

说明：

- `MIND_BRIDGE_ADMIN_PASSWORD` 为空时不会创建管理员账号。
- `MIND_BRIDGE_STUDENT_PASSWORD` 为空时不会创建学生演示账号。
- 管理员账号不能发起学生聊天，只能查看后台记录、报告、预警和知识库。
- 生产环境不要把这些变量写入源码、README、镜像或 Git 仓库。

### 2. 本地运行

```bash
cd MindMate
./scripts/run-dev.sh
```

脚本会尝试启动 Ollama 并运行 Maven。启动后打开：

```text
http://localhost:8080
```

也可以手动启动：

```bash
cd MindMate
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

打包后运行：

```bash
cd MindMate
mvn -Dmaven.repo.local=.m2/repository package
java -jar target/mindbridge-agent-0.1.0.jar --server.address=127.0.0.1 --server.port=8080
```
### 3. Docker 启动

```bash
cd MindMate
cp .env.example .env
# 编辑 .env，至少填写：
# MIND_BRIDGE_ADMIN_PASSWORD
# DB_PASSWORD
# MYSQL_ROOT_PASSWORD
docker compose up -d --build
```

查看日志：

```bash
docker compose logs -f app
```

访问：

```text
http://localhost:8080
```

默认 Compose 将应用和基础设施端口绑定在 `127.0.0.1`。如果要从公网访问，请在前面部署 HTTPS 反向代理，不要直接暴露 MySQL、Redis、Chroma、Mailpit 或 H2 Console。

停止服务：

```bash
docker compose down
```

`docker compose down -v` 会删除数据库和 Redis 卷，执行前请确认不需要保留数据。

如果只想用 Docker 启动依赖、在宿主机上运行应用：

```bash
docker compose up -d mysql redis chroma mailpit
./scripts/run-dev.sh
```

## 环境变量

完整示例见 [.env.example](.env.example)。

| 变量 | 说明 | 默认值 |
|---|---|---|
| `SERVER_PORT` | HTTP 端口 | `8080` |
| `MIND_BRIDGE_ADMIN_USERNAME` | 首次启动管理员用户名 | `admin` |
| `MIND_BRIDGE_ADMIN_PASSWORD` | 首次启动管理员密码；为空则不创建 | 空 |
| `MIND_BRIDGE_STUDENT_USERNAME` | 首次启动学生用户名 | `student` |
| `MIND_BRIDGE_STUDENT_PASSWORD` | 首次启动学生密码；为空则不创建 | 空 |
| `DB_URL` | JDBC 地址 | H2 文件数据库 |
| `DB_USERNAME` | 数据库用户名 | H2 为 `sa`，MySQL 为 `mindbridge` |
| `DB_PASSWORD` | 数据库密码 | 空，必须按环境配置 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址 | `localhost:6379` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `AI_PROVIDER` | `ollama`、`openai` 或 `mock` | `ollama` |
| `OLLAMA_BASE_URL` | Ollama 地址 | `http://localhost:11434` |
| `OLLAMA_MODEL` | Ollama 模型名 | `mindbridge-qwen2.5-7b-ft:latest` |
| `OPENAI_BASE_URL` | OpenAI 兼容接口地址 | `https://api.openai.com` |
| `OPENAI_API_KEY` | OpenAI API Key | 空 |
| `OPENAI_MODEL` | OpenAI 模型 | `gpt-4o-mini` |
| `USE_CHROMA` | 是否使用 Chroma | `true` |
| `CHROMA_BASE_URL` | Chroma 地址 | `http://localhost:8000` |
| `MCP_SERVER_ENABLED` | 是否启用 MCP Server | `true` |
| `MCP_CLIENT_ENABLED` | 是否启用 MCP Client | `false` |
| `MCP_EXCEL_MODE` | `local`、`http` 或 `mcp` | `local` |
| `MCP_EMAIL_MODE` | `log`、`smtp`、`http` 或 `mcp` | `log` |
| `MAIL_HOST` / `MAIL_PORT` | SMTP 地址 | `localhost:1025` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 凭据 | 空 |
| `ALERT_MAIL_FROM` | 预警发件人 | 示例地址 |
| `ALERT_MAIL_RECIPIENTS` | 预警收件人，逗号分隔 | 示例地址 |
## 调用示例

学生聊天：

```bash
curl -N -u "$MIND_BRIDGE_STUDENT_USERNAME:$MIND_BRIDGE_STUDENT_PASSWORD" \
  -H 'Content-Type: application/json' \
  -d '{"message":"我最近很焦虑，晚上总是睡不着"}' \
  http://localhost:8080/api/chat/stream
```

高风险输入会直接命中安全兜底，不调用模型：

```bash
curl -N -u "$MIND_BRIDGE_STUDENT_USERNAME:$MIND_BRIDGE_STUDENT_PASSWORD" \
  -H 'Content-Type: application/json' \
  -d '{"message":"我不想活了，感觉撑不下去了"}' \
  http://localhost:8080/api/chat/stream
```

管理员查看后台报告：

```bash
curl -u "$MIND_BRIDGE_ADMIN_USERNAME:$MIND_BRIDGE_ADMIN_PASSWORD" \
  http://localhost:8080/api/admin/reports
```

管理员追加知识库：

```bash
curl -u "$MIND_BRIDGE_ADMIN_USERNAME:$MIND_BRIDGE_ADMIN_PASSWORD" \
  -H 'Content-Type: application/json' \
  -d '{"source":"sleep-guide","content":"失眠时可先固定起床时间，减少睡前屏幕刺激，必要时联系校心理中心。"}' \
  http://localhost:8080/api/admin/knowledge
```

管理员上传知识库文件：

```bash
curl -u "$MIND_BRIDGE_ADMIN_USERNAME:$MIND_BRIDGE_ADMIN_PASSWORD" \
  -F "file=@./docs/knowledge.md" \
  http://localhost:8080/api/admin/knowledge/file
```

文件上传只接受 PDF、Markdown 和 txt，最大 10MB，服务端会流式写入临时文件并在处理后删除。

## 接入 Ollama / LoRA 模型

默认模型名为：

```text
mindbridge-qwen2.5-7b-ft:latest
```

本地模型由 GGUF 权重创建：

```text
models/mindbridge-qwen2.5-7b-ft/mindbridge-qwen2.5-7b-ft-q4_k_m.gguf
```

创建模型：

```bash
ollama create mindbridge-qwen2.5-7b-ft:latest \
  -f models/mindbridge-qwen2.5-7b-ft/Modelfile
```

之后启动项目即可。LoRA 微调、合并、GGUF 转换和 Ollama 接入的完整流程见：

[docs/qwen25-7b-lora-finetune-guide.md](docs/qwen25-7b-lora-finetune-guide.md)

如果只想离线演示业务流程，不调用真实模型：

```bash
AI_PROVIDER=mock \
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

手动指定本地模型：

```bash
AI_PROVIDER=ollama \
OLLAMA_BASE_URL=http://localhost:11434 \
OLLAMA_MODEL=mindbridge-qwen2.5-7b-ft:latest \
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

如果不是 macOS，或 Ollama/JDK/Maven 不在默认路径，请安装对应工具，并按实际路径设置 `OLLAMA_BIN`、`JAVA_HOME`、`MAVEN_BIN`。

## 接入 OpenAI

```bash
AI_PROVIDER=openai \
OPENAI_API_KEY='请通过环境变量注入' \
OPENAI_MODEL=gpt-4o-mini \
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

不要把真实 API Key 写入源码、`application.yml`、Dockerfile 或 Git。

## 使用 MySQL、Chroma、SMTP

启动依赖：

```bash
docker compose up -d mysql redis chroma mailpit
```

使用 MySQL profile：

```bash
AI_PROVIDER=ollama \
USE_CHROMA=true \
MCP_EMAIL_MODE=smtp \
ALERT_MAIL_RECIPIENTS=counselor@example.com \
DB_USERNAME=mindbridge \
DB_PASSWORD='请通过环境变量注入' \
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Mailpit 管理页面：`http://localhost:8025`
## MCP 工具模式

Excel 工具：

- `MCP_EXCEL_MODE=local`：默认写入 `./data/mindbridge-reports.xlsx`
- `MCP_EXCEL_MODE=http`：调用 `MCP_EXCEL_URL/write`
- `MCP_EXCEL_MODE=mcp`：通过标准 MCP Client 调用 `mindbridge_write_excel_report`

邮件工具：

- `MCP_EMAIL_MODE=log`：默认只记录日志
- `MCP_EMAIL_MODE=smtp`：使用 Spring Mail 发送
- `MCP_EMAIL_MODE=http`：调用 `MCP_EMAIL_URL/send`
- `MCP_EMAIL_MODE=mcp`：通过标准 MCP Client 调用 `mindbridge_send_risk_alert`

标准 MCP：

- `MCP_SERVER_ENABLED=true`：启用 MCP WebFlux Server，端点为 `/sse` 和 `/mcp/messages`
- `MCP_CLIENT_ENABLED=true`：启用 MCP WebFlux Client，默认连接 `MCP_SERVER_URL`
- `MCP_EMAIL_SERVER_DELIVERY_MODE=log|smtp`：MCP Server 收到邮件工具调用后的投递方式

安全说明：`/sse`、`/mcp/messages` 和 `/h2-console/**` 均要求 ADMIN 角色。即使如此，也不建议把这些端点和数据库、Redis、Chroma、Mailpit 直接暴露到公网。

## RAG 评测

项目内置 RAG 检索评测模块：

- `Recall@K`
- `Precision@K`
- `MRR`
- `nDCG@K`
- `Hit Rate`

运行评测：

```bash
AI_PROVIDER=mock \
USE_CHROMA=false \
RAG_EVAL_ENABLED=true \
RAG_EVAL_EXIT_AFTER_RUN=true \
mvn spring-boot:run
```

默认评测集：`src/main/resources/rag-eval/mindbridge-rag-eval.json`

默认输出报告：`target/rag-eval-report.json`

## 打包发布

生成不含模型权重和运行数据的应用发布包：

```bash
./scripts/package-release.sh
```

发布包会排除：

- 模型权重、GGUF、zip
- 运行数据库、Excel 报告、JSONL 数据集
- 日志、`target/`、`.m2/`、`.tools/`
- IDE 配置和本机临时文件

模型权重需要单独传输，不纳入 Git 仓库和普通发布包。

## 数据与隐私

以下内容不应提交到公开仓库：

- `data/mindbridge.mv.db`
- `data/mindbridge-reports.xlsx`
- `data/lora/psychqa_synthetic.jsonl`
- 任何真实用户对话、心理评估、预警记录或上传的知识库文件
- `.env`、密钥、证书、SMTP 授权码和 API Key

`.gitignore` 和 `.dockerignore` 已默认排除这些内容。

## 安全建议

1. 首次启动必须通过环境变量设置管理员密码，不要使用示例或弱密码。
2. 公网部署必须使用 HTTPS 反向代理，不要直接暴露应用端口。
3. 不需要 H2 Console 或 MCP Server 时，建议关闭对应配置。
4. 不要把 MySQL、Redis、Chroma、Mailpit 端口暴露到公网。
5. 定期轮换数据库、SMTP 和模型 API 凭据。
6. 备份和删除包含用户对话的数据前，先确认隐私和合规要求。
7. 高风险安全兜底不能替代现实中的紧急救助服务。

## 测试

```bash
mvn test
```

新增的高危兜底测试会验证：输入“我不想活了”时返回固定话术，并且 Agent 和 LLM 均未被调用。

## 许可证与归属

本仓库新增和修改部分采用 MIT License，详见 [LICENSE](LICENSE)。

上游 MindBridge 项目的版权、许可证和归属信息未随原始交付包提供。上游代码、文档、图片、数据集和模型衍生资产仍归原作者所有；使用或再分发前请自行确认上游授权。详见 [NOTICE](NOTICE)。

本项目原名为 MindBridge，现更名为 MindMate，本项目基于 MindBridge 开源代码二次开发并进行了安全加固与功能重构。