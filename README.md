# AgentChat

> 口袋里的 AI 超级助手 —— 开箱即用的安卓 AI 对话应用：多 Agent 工作台、工具调用、国产模型一键接入，数据完全保存在本机。

AgentChat 是一个纯 Kotlin + Jetpack Compose 原生实现的安卓应用。它不是又一个套壳 ChatGPT，而是一个可自定义 Agent、可调用工具、数据完全属于用户的个人 AI 工作台。轻量、快速、隐私，不臃肿。

## 功能特性

- **多 Agent 工作台**：9 个内置 Agent（通用/编程/写作/翻译/深度思考/数据分析/旅行/健身/导师），每条人设差异化；支持自定义编辑、图标选择、模型与温度独立配置、欢迎语与 Prompt 变量（`{{date}}` `{{time}}` `{{weekday}}`）
- **工具调用可视化**：时间、计算器（exp4j）、单位换算、随机数 4 个工具，调用过程以卡片实时展示，透明不黑盒
- **流式对话全交互**：打字机式输出、停止保留已生成内容、失败一键重试、消息编辑、断网横幅、触觉反馈、键盘适配、无障碍（48dp 触控 / contentDescription / 动态字体）
- **国产服务商预设**：DeepSeek / 阿里云百炼 / 通义千问 / Kimi / 智谱 GLM / MiniMax + 自定义，任何 OpenAI 兼容端点均可，测试连接一键验证
- **隐私优先**：API Key 经 Android Keystore 硬件加密存储；无 WebView、无明文流量（仅局域网段放行）、不收集任何数据
- **备份迁移**：SAF 导出/导入 JSON，导入前预览摘要，兼容旧版备份格式自动迁移
- **系统集成**：分享文本直达输入框、桌面「新对话」快捷方式、品牌色启动屏（无白屏）

## 快速开始

### 环境要求

- JDK 17
- Android SDK（compileSdk 35 / targetSdk 35 / minSdk 26）
- Gradle 8.11.1（Wrapper 自带）

### 构建

```bash
cd v2
./gradlew assembleRelease        # Release 混淆包（约 8.3 MB）
./gradlew assembleDebug          # Debug 包
./gradlew test                   # 单元测试（17 项）
```

国内网络环境构建零障碍：Gradle 仓库已配置阿里云 Maven 镜像，无需科学上网。

### 签名配置

签名信息从 `v2/local.properties` 读取（已 gitignore，不入库）：

```properties
sdk.dir=/path/to/android-sdk
storeFile=/path/to/your.keystore
storePassword=your_store_pass
keyAlias=your_alias
keyPassword=your_key_pass
```

也支持环境变量 `AC_STORE_PASS` / `AC_KEY_PASS` 兜底。**永远不要把真实口令提交到仓库。**

### 使用

1. 安装 APK，打开应用
2. 右上角设置 → 选择服务商预设（如 DeepSeek）→ 填入 API Key → 测试连接
3. 返回聊天，选择 Agent（如「编程专家」），开始对话

## 技术栈

| 模块 | 选型 | 说明 |
|------|------|------|
| UI | Jetpack Compose (Material3) | 原生渲染，无 WebView |
| 数据库 | Room (+ KSP) | 会话/消息/Agent 三表 + FTS4 全文搜索，schema 版本化迁移 |
| 设置 | DataStore Preferences + Android Keystore | API Key AES-256-GCM 加密存储 |
| 网络 | OkHttp + okhttp-sse | OpenAI 兼容协议，SSE 流式解析 |
| Markdown | Markwon (commonmark) | 原生 Spannable 渲染，无 WebView |
| 序列化 | kotlinx.serialization | 请求/响应/备份模型 |
| 计算器 | exp4j | 表达式计算（不手写解析器） |
| Agent 编排 | 自研轻量状态机（约 150 行） | Idle→Requesting→ToolCalling→Responding→Done，6 轮上限、400 自动降级 |

## 目录结构

```
v2/
├── app/src/main/java/com/agentchat/lite/
│   ├── data/          # Room 实体/DAO/数据库迁移、DataStore 设置、备份管理
│   ├── network/       # OpenAI 兼容客户端、SSE 流式、请求/响应模型
│   ├── agent/         # Agent 编排状态机、工具注册中心、Prompt 模板
│   ├── ui/            # Compose UI（聊天/设置/Agent 编辑器/组件）
│   └── di/            # 手动依赖注入容器
└── app/src/test/      # 单元测试（状态机/工具/备份）
```

## 测试

17 项单元测试覆盖：AgentOrchestrator 状态机（工具循环/上限/降级/取消）、工具执行（时间/计算器边界）、备份序列化（v1/v2 迁移 round-trip）。

```bash
./gradlew test
```

## 开源许可

MIT License（见 [LICENSE](LICENSE)）。核心依赖均采用宽松许可：Apache-2.0（OkHttp/Markwon/Room/DataStore/kotlinx.serialization/exp4j）。

## 隐私与免责

- 应用不收集任何数据；会话、消息、设置仅存本机
- API Key 仅在你的设备加密存储，只在对话请求时发送给你配置的模型服务商
- 本项目仅供学习与自用，请遵守所用模型服务商的服务条款
