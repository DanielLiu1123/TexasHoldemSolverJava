# 用统一的本地 HTTP API 取代 Swing GUI 与 JPype Python 接口

---
status: accepted
---

项目原有三个入口（Swing GUI、CLI、JPype Python 反射调用），各自直接耦合 `PokerSolver` 的方法签名——其中 GUI 依赖 IntelliJ forms 的 ant javac2 编译 hack（破坏 Gradle configuration cache），JPype 对 16 参数位置硬编码、极其脆弱。决定：求解器以**嵌入式 HTTP/JSON API（含 SSE 训练进度流）作为唯一对外契约**，Web 前端（浏览器 UI）、Python 客户端、CLI 都是这个 API 的消费者；Swing GUI 在 Web UI 达到功能对等后删除，JPype 接口直接退役。

## Considered Options

- **保留 Swing、仅去 forms 化**：消除 build hack，但 Swing 生态停滞，范围矩阵/策略树这类重交互 UI 开发成本高，且无法解决 JPype 脆弱性。
- **JavaFX / Compose Multiplatform 桌面端**：现代但仍是桌面分发负担（jlink/打包），且 Python 调用问题仍需单独解决。
- **gRPC**：类型契约更强，但本地单机工具引入 protobuf 工具链过重，浏览器消费还需 grpc-web 网关。
- **嵌入式 HTTP + Web UI（选定）**：一个契约服务所有消费者；JVM 25 虚拟线程使嵌入式服务器近乎零成本；策略可视化（范围网格、树浏览）在 Web 技术栈中是成熟问题。

## Consequences

- `PokerSolver` 的 Java 签名退化为内部 API，可自由重构；对外稳定面收敛为 HTTP 契约 + 策略 JSON schema。
- 发布产物从"双击 jar"变为"启动本地服务 + 自动打开浏览器"，对用户操作习惯有小幅改变。
- `java_interface.py` / `TreeBuilder.py` 退役；Python 用户改用任意 HTTP 客户端。
