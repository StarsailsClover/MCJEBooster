# MCJEBooster API 文档（中文版）

**版本：** v26.1-05102026  
**API 级别：** 公开

---

## 目录

1. [RegionScheduler API](#regionscheduler-api)
2. [SyncPointManager API](#syncpointmanager-api)
3. [VersionDetector API](#versiondetector-api)
4. [Logger API](#logger-api)
5. [配置](#配置)

---

## RegionScheduler API

### 概述

`RegionScheduler` 是核心多线程引擎，将 Minecraft 的 tick 处理分布到多个 CPU 核心上。

### 类：`com.mcjebooster.scheduler.RegionScheduler`

#### 单例访问

```java
RegionScheduler scheduler = RegionScheduler.getInstance();
```

#### 初始化

```java
/**
 * 使用检测到的 Minecraft 版本初始化调度器
 * 
 * @param minecraftVersion 检测到的 Minecraft 版本字符串
 */
public void initialize(String minecraftVersion)
```

#### 核心方法

```java
/**
 * 主 tick 方法 - 将处理分布到工作线程
 * 由注入的 MinecraftServer.tick() 自动调用
 * 
 * @param minecraftServer MinecraftServer 实例
 */
public void tickRegions(Object minecraftServer)
```

```java
/**
 * 向调度器添加区域
 * 
 * @param region 要添加的区域
 */
public void addRegion(Region region)
```

```java
/**
 * 从调度器中移除区域
 * 
 * @param regionId 要移除的区域 ID
 */
public void removeRegion(int regionId)
```

```java
/**
 * 关闭调度器和所有工作线程
 */
public void shutdown()
```

#### 状态方法

```java
/**
 * 检查调度器是否正在运行
 * @return 如果激活返回 true
 */
public boolean isRunning()
```

```java
/**
 * 检查调度器线程是否存活
 * @return 如果工作者未死亡返回 true
 */
public boolean isAlive()
```

```java
/**
 * 获取当前测量的 TPS
 * @return 当前 TPS 值
 */
public double getCurrentTPS()
```

```java
/**
 * 获取自初始化以来的总 tick 计数
 * @return Tick 计数
 */
public long getTickCount()
```

```java
/**
 * 获取检测到的线程冲突数量
 * @return 冲突计数
 */
public long getConflictCount()
```

```java
/**
 * 获取区域数量
 * @return 区域计数
 */
public int getRegionCount()
```

#### Region 类

```java
public static class Region {
    public Region(int id, int minX, int minZ, int maxX, int maxZ)
    
    public int getId()
    public int getMinX()
    public int getMinZ()
    public int getMaxX()
    public int getMaxZ()
    
    /**
     * 检查点是否在此区域内
     * @param x X 坐标
     * @param z Z 坐标
     * @return 如果点在区域内返回 true
     */
    public boolean contains(int x, int z)
}
```

---

## SyncPointManager API

### 概述

`SyncPointManager` 协调工作线程之间的同步以确保数据一致性。

### 类：`com.mcjebooster.sync.SyncPointManager`

#### 单例访问

```java
SyncPointManager sync = SyncPointManager.getInstance();
```

#### 初始化

```java
/**
 * 初始化同步管理器
 * 
 * @param workerCount 工作线程数量
 */
public void initialize(int workerCount)
```

#### 屏障方法

```java
/**
 * 在 tick 屏障处等待
 * 由工作线程调用以进行同步
 * 
 * @throws BrokenBarrierException 如果屏障被破坏
 * @throws InterruptedException 如果线程被中断
 */
public void awaitTickBarrier() throws BrokenBarrierException, InterruptedException
```

```java
/**
 * 在 tick 屏障处等待，带特定超时
 * 
 * @param timeout 超时持续时间
 * @param unit 时间单位
 * @throws BrokenBarrierException 如果屏障被破坏
 * @throws InterruptedException 如果线程被中断
 * @throws TimeoutException 如果等待超时
 */
public void awaitTickBarrier(long timeout, TimeUnit unit) 
    throws BrokenBarrierException, InterruptedException, TimeoutException
```

#### 实体转移

```java
/**
 * 同步跨区域边界的实体移动
 * 
 * @param entityId 实体 ID
 * @param fromRegion 源区域 ID
 * @param toRegion 目标区域 ID
 * @return 如果转移成功返回 true
 */
public boolean syncEntityTransfer(int entityId, int fromRegion, int toRegion)
```

#### 方块更新

```java
/**
 * 同步影响多个区域的方块更新
 * 
 * @param blockX 方块 X 坐标
 * @param blockY 方块 Y 坐标
 * @param blockZ 方块 Z 坐标
 * @param affectedRegions 受此更新影响的区域
 * @return 如果更新已同步返回 true
 */
public boolean syncBlockUpdate(int blockX, int blockY, int blockZ, int[] affectedRegions)
```

#### 控制方法

```java
/**
 * 禁用同步
 */
public void disableSync()
```

```java
/**
 * 重新启用同步
 */
public void enableSync()
```

```java
/**
 * 关闭同步管理器
 */
public void shutdown()
```

#### 状态方法

```java
public boolean isActive()
public int getTickNumber()
public int getBarrierParties()
public int getWaitingParties()
public boolean isBarrierBroken()
```

---

## VersionDetector API

### 概述

`VersionDetector` 使用各种启发式方法识别正在运行的 Minecraft 版本。

### 类：`com.mcjebooster.util.VersionDetector`

#### 版本检测

```java
/**
 * 检测 Minecraft 版本
 * 
 * @return 检测到的版本字符串，如果检测失败返回 "unknown"
 */
public static String detectMinecraftVersion()
```

#### 版本解析

```java
/**
 * 获取主版本号
 * @param version 版本字符串（如 "1.20.6"）
 * @return 主版本号（如 1）
 */
public static int getMajorVersion(String version)
```

```java
/**
 * 获取次版本号
 * @param version 版本字符串（如 "1.20.6"）
 * @return 次版本号（如 20）
 */
public static int getMinorVersion(String version)
```

```java
/**
 * 获取补丁版本号
 * @param version 版本字符串（如 "1.20.6"）
 * @return 补丁版本号（如 6）
 */
public static int getPatchVersion(String version)
```

#### 版本比较

```java
/**
 * 比较两个版本字符串
 * 
 * @param v1 第一个版本
 * @param v2 第二个版本
 * @return 如果 v1 < v2 为负数，相等为 0，如果 v1 > v2 为正数
 */
public static int compareVersions(String v1, String v2)
```

```java
/**
 * 检查版本是否至少为指定的最小值
 * 
 * @param version 要检查的版本
 * @param minimum 要求的最低版本
 * @return 如果 version >= minimum 返回 true
 */
public static boolean isAtLeast(String version, String minimum)
```

```java
/**
 * 检查版本是否在范围内
 * 
 * @param version 要检查的版本
 * @param minVersion 最小版本（包含）
 * @param maxVersion 最大版本（包含）
 * @return 如果 minVersion <= version <= maxVersion 返回 true
 */
public static boolean isInRange(String version, String minVersion, String maxVersion)
```

---

## Logger API

### 概述

带级别日志和时间戳的简单日志工具。

### 类：`com.mcjebooster.util.Logger`

#### 日志级别

```java
public enum LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}
```

#### 配置

```java
/**
 * 设置当前日志级别
 * @param level 要记录的最低级别
 */
public static void setLevel(LogLevel level)
```

```java
/**
 * 从字符串设置日志级别
 * @param levelName 级别名称（DEBUG, INFO, WARN, ERROR）
 */
public static void setLevel(String levelName)
```

```java
/**
 * 设置是否包含时间戳
 * @param include 如果包含时间戳返回 true
 */
public static void setIncludeTimestamps(boolean include)
```

```java
/**
 * 设置是否包含模块名称
 * @param include 如果包含模块名称返回 true
 */
public static void setIncludeModule(boolean include)
```

#### 日志方法

```java
public static void debug(String message)
public static void debug(String format, Object... args)

public static void info(String message)
public static void info(String format, Object... args)

public static void warn(String message)
public static void warn(String format, Object... args)

public static void error(String message)
public static void error(String format, Object... args)
public static void error(String message, Throwable e)
```

#### 状态方法

```java
public static LogLevel getCurrentLevel()
public static boolean isDebugEnabled()
public static boolean isEnabled(LogLevel level)
```

---

## 配置

### 系统属性

| 属性 | 描述 | 默认值 |
|----------|-------------|---------|
| `mcjebooster.log.level` | 日志级别（DEBUG, INFO, WARN, ERROR） | INFO |
| `mcjebooster.worker.count` | 工作线程数量 | CPU-1 |
| `mcjebooster.region.size` | 区域大小（区块） | 16 |
| `mcjebooster.tick.timeout` | Tick 超时（毫秒） | 45 |
| `mcjebooster.health.check` | 启用健康监控 | true |

### 配置示例

```bash
java -Dmcjebooster.log.level=DEBUG \
     -Dmcjebooster.worker.count=4 \
     -jar MCJEBooster-26.1-05102026.jar
```

---

## 线程安全

所有公共 API 都是线程安全的：

- `RegionScheduler`：线程安全，单例
- `SyncPointManager`：线程安全，单例
- `VersionDetector`：线程安全，静态方法
- `Logger`：线程安全，静态方法

---

## 错误处理

### 受检异常

- `BrokenBarrierException`：同步屏障被破坏
- `InterruptedException`：线程被中断
- `TimeoutException`：操作超时

### 错误恢复

1. **Tick 超时**：回退到单线程模式
2. **屏障破坏**：尝试自动重置
3. **死锁检测**：触发回滚
4. **低 TPS**：阈值后自动回滚

---

*此 API 文档是 MCJEBooster 项目的一部分，采用 LGPL-2.1 许可证*
