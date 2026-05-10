# MCJEBooster API Documentation

**Version:** v26.1-05102026  
**API Level:** Public

---

## Table of Contents

1. [RegionScheduler API](#regionscheduler-api)
2. [SyncPointManager API](#syncpointmanager-api)
3. [VersionDetector API](#versiondetector-api)
4. [Logger API](#logger-api)
5. [Configuration](#configuration)

---

## RegionScheduler API

### Overview

The `RegionScheduler` is the core multi-threading engine that distributes Minecraft's tick processing across multiple CPU cores.

### Class: `com.mcjebooster.scheduler.RegionScheduler`

#### Singleton Access

```java
RegionScheduler scheduler = RegionScheduler.getInstance();
```

#### Initialization

```java
/**
 * Initializes the scheduler with the detected Minecraft version
 * 
 * @param minecraftVersion The detected Minecraft version string
 */
public void initialize(String minecraftVersion)
```

#### Core Methods

```java
/**
 * Main tick method - distributes processing across worker threads
 * Called automatically by the injected MinecraftServer.tick()
 * 
 * @param minecraftServer The MinecraftServer instance
 */
public void tickRegions(Object minecraftServer)
```

```java
/**
 * Adds a region to the scheduler
 * 
 * @param region The region to add
 */
public void addRegion(Region region)
```

```java
/**
 * Removes a region from the scheduler
 * 
 * @param regionId The ID of the region to remove
 */
public void removeRegion(int regionId)
```

```java
/**
 * Shuts down the scheduler and all worker threads
 */
public void shutdown()
```

#### Status Methods

```java
/**
 * Checks if the scheduler is running
 * @return true if active
 */
public boolean isRunning()
```

```java
/**
 * Checks if scheduler threads are alive
 * @return true if workers are not dead
 */
public boolean isAlive()
```

```java
/**
 * Gets the current measured TPS
 * @return Current TPS value
 */
public double getCurrentTPS()
```

```java
/**
 * Gets the total tick count since initialization
 * @return Tick count
 */
public long getTickCount()
```

```java
/**
 * Gets the number of thread conflicts detected
 * @return Conflict count
 */
public long getConflictCount()
```

```java
/**
 * Gets the number of regions
 * @return Region count
 */
public int getRegionCount()
```

#### Region Class

```java
public static class Region {
    public Region(int id, int minX, int minZ, int maxX, int maxZ)
    
    public int getId()
    public int getMinX()
    public int getMinZ()
    public int getMaxX()
    public int getMaxZ()
    
    /**
     * Checks if a point is within this region
     * @param x X coordinate
     * @param z Z coordinate
     * @return true if point is within region
     */
    public boolean contains(int x, int z)
}
```

---

## SyncPointManager API

### Overview

The `SyncPointManager` coordinates synchronization between worker threads to ensure data consistency.

### Class: `com.mcjebooster.sync.SyncPointManager`

#### Singleton Access

```java
SyncPointManager sync = SyncPointManager.getInstance();
```

#### Initialization

```java
/**
 * Initializes the synchronization manager
 * 
 * @param workerCount Number of worker threads
 */
public void initialize(int workerCount)
```

#### Barrier Methods

```java
/**
 * Waits at the tick barrier
 * Called by worker threads to synchronize
 * 
 * @throws BrokenBarrierException if barrier is broken
 * @throws InterruptedException if thread is interrupted
 */
public void awaitTickBarrier() throws BrokenBarrierException, InterruptedException
```

```java
/**
 * Waits at the tick barrier with specific timeout
 * 
 * @param timeout Timeout duration
 * @param unit Time unit
 * @throws BrokenBarrierException if barrier is broken
 * @throws InterruptedException if thread is interrupted
 * @throws TimeoutException if wait times out
 */
public void awaitTickBarrier(long timeout, TimeUnit unit) 
    throws BrokenBarrierException, InterruptedException, TimeoutException
```

#### Entity Transfer

```java
/**
 * Synchronizes entity movement across region boundaries
 * 
 * @param entityId The entity ID
 * @param fromRegion Source region ID
 * @param toRegion Destination region ID
 * @return true if transfer was successful
 */
public boolean syncEntityTransfer(int entityId, int fromRegion, int toRegion)
```

#### Block Updates

```java
/**
 * Synchronizes block updates affecting multiple regions
 * 
 * @param blockX Block X coordinate
 * @param blockY Block Y coordinate
 * @param blockZ Block Z coordinate
 * @param affectedRegions Regions affected by this update
 * @return true if update was synchronized
 */
public boolean syncBlockUpdate(int blockX, int blockY, int blockZ, int[] affectedRegions)
```

#### Control Methods

```java
/**
 * Disables synchronization
 */
public void disableSync()
```

```java
/**
 * Re-enables synchronization
 */
public void enableSync()
```

```java
/**
 * Shuts down the synchronization manager
 */
public void shutdown()
```

#### Status Methods

```java
public boolean isActive()
public int getTickNumber()
public int getBarrierParties()
public int getWaitingParties()
public boolean isBarrierBroken()
```

---

## VersionDetector API

### Overview

The `VersionDetector` identifies the running Minecraft version using various heuristics.

### Class: `com.mcjebooster.util.VersionDetector`

#### Version Detection

```java
/**
 * Detects the Minecraft version
 * 
 * @return Detected version string, or "unknown" if detection fails
 */
public static String detectMinecraftVersion()
```

#### Version Parsing

```java
/**
 * Gets the major version number
 * @param version Version string (e.g., "1.20.6")
 * @return Major version number (e.g., 1)
 */
public static int getMajorVersion(String version)
```

```java
/**
 * Gets the minor version number
 * @param version Version string (e.g., "1.20.6")
 * @return Minor version number (e.g., 20)
 */
public static int getMinorVersion(String version)
```

```java
/**
 * Gets the patch version number
 * @param version Version string (e.g., "1.20.6")
 * @return Patch version number (e.g., 6)
 */
public static int getPatchVersion(String version)
```

#### Version Comparison

```java
/**
 * Compares two version strings
 * 
 * @param v1 First version
 * @param v2 Second version
 * @return negative if v1 < v2, 0 if equal, positive if v1 > v2
 */
public static int compareVersions(String v1, String v2)
```

```java
/**
 * Checks if version is at least the specified minimum
 * 
 * @param version Version to check
 * @param minimum Minimum required version
 * @return true if version >= minimum
 */
public static boolean isAtLeast(String version, String minimum)
```

```java
/**
 * Checks if version is within a range
 * 
 * @param version Version to check
 * @param minVersion Minimum version (inclusive)
 * @param maxVersion Maximum version (inclusive)
 * @return true if minVersion <= version <= maxVersion
 */
public static boolean isInRange(String version, String minVersion, String maxVersion)
```

---

## Logger API

### Overview

Simple logging utility with leveled logging and timestamps.

### Class: `com.mcjebooster.util.Logger`

#### Log Levels

```java
public enum LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}
```

#### Configuration

```java
/**
 * Sets the current log level
 * @param level Minimum level to log
 */
public static void setLevel(LogLevel level)
```

```java
/**
 * Sets the log level from a string
 * @param levelName Level name (DEBUG, INFO, WARN, ERROR)
 */
public static void setLevel(String levelName)
```

```java
/**
 * Sets whether to include timestamps
 * @param include true to include timestamps
 */
public static void setIncludeTimestamps(boolean include)
```

```java
/**
 * Sets whether to include module name
 * @param include true to include module name
 */
public static void setIncludeModule(boolean include)
```

#### Logging Methods

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

#### Status Methods

```java
public static LogLevel getCurrentLevel()
public static boolean isDebugEnabled()
public static boolean isEnabled(LogLevel level)
```

---

## Configuration

### System Properties

| Property | Description | Default |
|----------|-------------|---------|
| `mcjebooster.log.level` | Log level (DEBUG, INFO, WARN, ERROR) | INFO |
| `mcjebooster.worker.count` | Number of worker threads | CPU-1 |
| `mcjebooster.region.size` | Region size in chunks | 16 |
| `mcjebooster.tick.timeout` | Tick timeout in ms | 45 |
| `mcjebooster.health.check` | Enable health monitoring | true |

### Example Configuration

```bash
java -Dmcjebooster.log.level=DEBUG \
     -Dmcjebooster.worker.count=4 \
     -jar MCJEBooster-26.1-05102026.jar
```

---

## Thread Safety

All public APIs are thread-safe:

- `RegionScheduler`: Thread-safe, singleton
- `SyncPointManager`: Thread-safe, singleton
- `VersionDetector`: Thread-safe, static methods
- `Logger`: Thread-safe, static methods

---

## Error Handling

### Checked Exceptions

- `BrokenBarrierException`: Synchronization barrier broken
- `InterruptedException`: Thread interrupted
- `TimeoutException`: Operation timed out

### Error Recovery

1. **Tick Timeout**: Falls back to single-threaded mode
2. **Barrier Break**: Attempts automatic reset
3. **Deadlock Detection**: Triggers rollback
4. **Low TPS**: Auto-rollback after threshold

---

*This API documentation is part of the MCJEBooster project and is licensed under LGPL-2.1*
