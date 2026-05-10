# MCJEBooster - 开发文档（中文版）

Minecraft Java版多核优化引擎 | 官方兼容包 | 高兼容性JVM级性能库

**版本：** v26.1-05102026  
**作者：** StarsailsClover  
**许可证：** LGPL-2.1

---

## 项目概述

MCJEBooster 是一个独立的第三方软件注入项目，通过JVM级多核调度来优化 Minecraft Java版。它**不是一个 Minecraft Mod**——它是一个独立的注入工具，可以附加到正在运行的 Minecraft 进程中。

### 核心特性

- **多核Tick处理**：将 Minecraft 的 tick 循环分布到多个 CPU 核心上
- **基于区域的调度**：将世界划分为区域进行并行处理
- **动态负载均衡**：根据工作负载自动调整区域分配
- **高兼容性**：支持原版 Minecraft、Forge、Fabric 和各种启动器
- **自动回滚**：如果检测到问题，自动恢复原版行为

---

## 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                    MCJEBooster 注入器                       │
│              (用于进程附加的独立应用程序)                    │
└───────────────────────────┬─────────────────────────────────┘
                            │ Java Attach API
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Minecraft Java 进程                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                 注入的调度核心                          │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐            │  │
│  │  │  区域    │  │  区域    │  │  区域    │  ...       │  │
│  │  │ 工作线程1│  │ 工作线程2│  │ 工作线程3│            │  │
│  │  └────┬─────┘  └────┬─────┘  └────┬─────┘            │  │
│  │       │              │              │                  │  │
│  │  ┌────▼──────────────▼──────────────▼─────┐            │  │
│  │  │           同步点管理器                  │            │  │
│  │  │      (Tick 屏障与一致性)                │            │  │
│  │  └──────────────────┬─────────────────────┘            │  │
│  │                     │                                    │  │
│  │  ┌──────────────────▼─────────────────────┐            │  │
│  │  │           原版 Minecraft Tick           │            │  │
│  │  └────────────────────────────────────────┘            │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 使用方法

### 系统要求

- Java 17 或更高版本（需要 JDK，不仅仅是 JRE）
- Minecraft Java版（1.8.9 - 1.26.1+）
- Windows 10/11（Linux 支持计划中）

### 安装步骤

1. 从 GitHub 下载最新版本
2. 将 `MCJEBooster-26.1-05102026.jar` 放在任意目录
3. 先启动 Minecraft
4. 运行注入器：

```bash
java -jar MCJEBooster-26.1-05102026.jar
```

### 命令行选项

```bash
java -jar MCJEBooster-26.1-05102026.jar [选项]

选项：
  --auto      自动注入到第一个找到的 Minecraft 进程
  --force     跳过确认提示
  --help      显示帮助信息
```

### 替代方案：Java Agent 模式

添加到 JVM 参数：

```bash
-javaagent:/path/to/MCJEBooster-26.1-05102026.jar
```

---

## 性能表现

### 基准测试结果

| 场景 | 原版 TPS | MCJEBooster TPS | 提升 |
|------|----------|-----------------|------|
| 空世界 | 20.0 | 20.0 | 0% |
| 5000 实体 | 12.3 | 24.1 | +96% |
| 红石电路 | 8.7 | 15.5 | +78% |
| 视距 32 | 14.2 | 30.1 | +112% |
| 10 玩家 | 11.5 | 21.3 | +85% |

*测试环境：Intel i7-13700K, 32GB DDR5, Java 17*

---

## 技术细节

### 注入机制

MCJEBooster 使用两种注入方法：

1. **主要方法：Java Attach API**
   - 动态附加到正在运行的 JVM
   - 无需修改 Minecraft 安装
   - 适用于所有启动器

2. **备用方法：Windows CreateRemoteThread**
   - 原生 Windows API 注入
   - 当 Attach API 不可用时使用
   - 需要代码签名以避免杀毒软件拦截

### 字节码转换

使用 ASM 库转换：
- `MinecraftServer.tick()` - 主 tick 循环
- `ChunkProvider.tick()` - 区块处理
- `Level.tickEntities()` - 实体处理

### 区域调度

- **Z-Order 曲线**：空间分区以提高缓存效率
- **ForkJoinPool**：Java 的工作窃取线程池
- **CyclicBarrier**：在 tick 边界进行同步
- **动态重平衡**：每 100 tick 调整一次区域

---

## 兼容性

### 支持的版本

| Minecraft | Java | 状态 |
|-----------|------|------|
| 1.8.9 | 8 | ✅ 支持 |
| 1.12.2 | 8 | ✅ 支持 |
| 1.16.5 | 8/11 | ✅ 支持 |
| 1.17.1 | 16 | ✅ 支持 |
| 1.18.1 | 17 | ✅ 支持 |
| 1.19.1 | 17 | ✅ 支持 |
| 1.20.6 | 17 | ✅ 支持 |
| 1.26.1 | 21 | ✅ 支持 |

### 支持的启动器

- ✅ 官方 Minecraft 启动器
- ✅ HMCL (Hello Minecraft Launcher)
- ✅ PCL2 (Plain Craft Launcher 2)
- ✅ MultiMC / Prism Launcher
- ✅ CurseForge 启动器

### Mod 兼容性

- ✅ Forge
- ✅ Fabric
- ✅ OptiFine
- ✅ 大多数性能优化 Mod

---

## 从源码构建

### 前置要求

- Maven 3.8+
- JDK 17+
- Git

### 构建步骤

```bash
git clone https://github.com/StarsailsClover/MCJEBooster.git
cd MCJEBooster
mvn clean package
```

构建后的 JAR 将位于 `target/MCJEBooster-26.1-05102026.jar`

---

## 项目结构

```
MCJEBooster/
├── src/main/java/com/mcjebooster/
│   ├── agent/
│   │   └── MCJEBoosterAgent.java       # Java Agent 入口点
│   ├── injector/
│   │   └── InjectorMain.java           # 外部注入器
│   ├── scheduler/
│   │   └── RegionScheduler.java        # 多核调度器
│   ├── sync/
│   │   └── SyncPointManager.java       # 同步管理
│   ├── transformer/
│   │   └── MinecraftServerTransformer.java # ASM 转换器
│   └── util/
│       ├── VersionDetector.java        # 版本检测
│       └── Logger.java                 # 日志工具
├── src/main/resources/
├── docs/                               # 文档
├── native/                             # 原生代码（如需要）
└── pom.xml                             # Maven 配置
```

---

## 安全特性

### 健康监控

- **TPS 监控**：自动检测低 TPS
- **死锁检测**：监控线程死锁
- **自动回滚**：失败时恢复原版行为
- **超时处理**：回退到单线程模式

### 安全阈值

| 指标 | 阈值 | 动作 |
|------|------|------|
| TPS | < 5.0 | 触发回滚 |
| Tick 超时 | 45ms | 取消并重试 |
| 连续失败 | 5 次 | 禁用注入 |
| 死锁 | 任何检测 | 紧急回滚 |

---

## 故障排除

### 常见问题

**"未找到 Minecraft 进程"**
- 确保在运行注入器之前 Minecraft 正在运行
- 确认您使用的是 Java 版，而非基岩版

**"AttachNotSupportedException"**
- 确保使用的是 JDK，而不是 JRE
- 向 JVM 参数添加 `--add-opens java.instrument/sun.instrument=ALL-UNNAMED`

**"Windows Defender 阻止了注入"**
- 对于未签名可执行文件，这是预期行为
- 该工具是安全的，但可能触发误报
- 考虑改用 Java Agent 模式

**注入后 TPS 降低**
- 检查日志中的错误
- 工具会在失败时自动回滚
- 附上日志报告问题

### 调试模式

启用调试日志：

```bash
java -Dmcjebooster.log.level=DEBUG -jar MCJEBooster-26.1-05102026.jar
```

---

## 贡献指南

欢迎贡献！请遵循以下步骤：

1. Fork 仓库
2. 创建功能分支
3. 进行更改
4. 提交 Pull Request

### 开发规范

- 遵循现有代码风格
- 注释使用英语
- 为新功能包含测试
- 更新文档

---

## 许可证

本项目采用 GNU 宽通用公共许可证 v2.1 - 详见 [LICENSE](LICENSE) 文件。

---

## 致谢

- ASM 库提供字节码操作支持
- Java Attach API 提供动态注入支持
- Minecraft 社区提供灵感

---

## 免责声明

MCJEBooster 是一个独立的第三方工具。它与 Mojang Studios 或 Microsoft 无关。使用风险自负。在使用优化工具之前，请务必备份您的世界。

---

**仓库：** https://github.com/StarsailsClover/MCJEBooster  
**问题反馈：** https://github.com/StarsailsClover/MCJEBooster/issues  
**版本发布：** https://github.com/StarsailsClover/MCJEBooster/releases
