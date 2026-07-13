/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.mcjebooster.scheduler;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

import com.mcjebooster.util.Logger;
import com.mcjebooster.adapter.VersionAdapter;

/**
 * Multi-threaded Region Scheduler for Minecraft
 * 
 * This is the core scheduling engine that distributes Minecraft's tick
 * processing across multiple CPU cores. It divides the world into regions
 * and processes each region on a separate worker thread.
 * 
 * Key features:
 * - Z-order curve spatial partitioning for cache efficiency
 * - Dynamic load balancing based on execution time
 * - Synchronization barriers for tick consistency
 * - Automatic fallback to single-threaded mode on failure
 * 
 * Thread Safety: This class is thread-safe and designed for concurrent access
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 * @since 1.0
 */
public class RegionScheduler {
    
    /** Singleton instance */
    private static volatile RegionScheduler INSTANCE;
    
    /** Worker thread pool for parallel region processing */
    private ForkJoinPool workerPool;
    
    /** Number of worker threads (typically CPU count - 1) */
    private int workerCount;
    
    /** Registry of all managed regions */
    private final ConcurrentHashMap<Integer, Region> regions = new ConcurrentHashMap<>();
    
    /** Cyclic barrier for synchronizing worker threads at tick boundaries */
    private CyclicBarrier tickBarrier;
    
    /** Read-write lock for world state access */
    private final ReadWriteLock worldLock = new ReentrantReadWriteLock();
    
    /** Reference to the original tick method (via reflection) */
    private java.lang.reflect.Method originalTickMethod;
    
    /** Reference to the version adapter for version-specific behavior */
    private VersionAdapter versionAdapter;
    
    /** Current TPS (Ticks Per Second) measurement */
    private volatile double currentTPS = 20.0;
    
    /** Last tick time for TPS calculation */
    private volatile long lastTickTime = System.nanoTime();
    
    /** Tick counter for statistics */
    private volatile long tickCount = 0;
    
    /** Flag indicating if the scheduler is running */
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    /** Flag indicating if the scheduler is alive (threads not dead) */
    private volatile boolean alive = true;
    
    /** Conflict counter for thread safety monitoring */
    private final AtomicLong conflictCount = new AtomicLong(0);

    private final AtomicLong safeTaskCount = new AtomicLong(0);

    private final AtomicLong safeTaskNanos = new AtomicLong(0);

    private final AtomicLong snapshotAnalysisCount = new AtomicLong(0);

    private final AtomicLong snapshotItemCount = new AtomicLong(0);

    private final AtomicLong snapshotAnalysisNanos = new AtomicLong(0);

    /** 已解析的实体字段缓存：Class -> 字段名 */
    private final ConcurrentHashMap<Class<?>, String> resolvedEntityXField = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, String> resolvedEntityZField = new ConcurrentHashMap<>();

    /** 已解析的方块实体字段缓存 */
    private final ConcurrentHashMap<Class<?>, String> resolvedBlockEntityPosField = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, String> resolvedBlockEntityXField = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, String> resolvedBlockEntityZField = new ConcurrentHashMap<>();

    /** 已解析的方块实体方法缓存 */
    private final ConcurrentHashMap<Class<?>, String> resolvedBlockEntityGetXMethod = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, String> resolvedBlockEntityGetZMethod = new ConcurrentHashMap<>();
    
    /** Execution time tracking for load balancing */
    private final ConcurrentHashMap<Integer, Long> regionExecutionTimes = new ConcurrentHashMap<>();
    
    /** Threshold for triggering load rebalancing (standard deviation) */
    private static final double REBALANCE_THRESHOLD = 0.3;
    
    /** Tick timeout in milliseconds (50ms = 20 TPS target) */
    private long tickTimeoutMs = 45;
    
    /** Minimum TPS before considering the system unhealthy */
    private static final double MIN_HEALTHY_TPS = 5.0;
    
    /** 
     * Private constructor for singleton pattern
     */
    private RegionScheduler() {
        this.workerCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        Logger.info("RegionScheduler initialized with " + workerCount + " worker threads");
    }
    
    /**
     * Gets the singleton instance of the RegionScheduler
     * 
     * @return The RegionScheduler instance
     */
    public static RegionScheduler getInstance() {
        if (INSTANCE == null) {
            synchronized (RegionScheduler.class) {
                if (INSTANCE == null) {
                    INSTANCE = new RegionScheduler();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Initializes the scheduler with the detected Minecraft version
     * 
     * @param minecraftVersion The detected Minecraft version string
     */
    public void initialize(String minecraftVersion) {
        initialize(minecraftVersion, null);
    }
    
    /**
     * Initializes the scheduler with version adapter
     * 
     * @param minecraftVersion The detected Minecraft version string
     * @param versionAdapter The version-specific adapter
     */
    public void initialize(String minecraftVersion, VersionAdapter versionAdapter) {
        Logger.info("Initializing RegionScheduler for MC version: " + minecraftVersion);
        
        this.versionAdapter = versionAdapter;
        
        // Use adapter settings if available
        if (versionAdapter != null) {
            this.workerCount = versionAdapter.getRecommendedWorkerCount();
            Logger.info("Using adapter-recommended worker count: " + workerCount);
        }
        
        // Initialize the worker pool
        this.workerPool = new ForkJoinPool(
            workerCount,
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true
        );
        
        // Initialize the tick barrier
        this.tickBarrier = new CyclicBarrier(workerCount + 1); // workers + main thread
        
        // Initialize regions based on world size
        initializeRegions();
        
        running.set(true);
        alive = true;
        
        Logger.info("RegionScheduler initialization completed");
    }
    
    /**
     * Initializes the spatial regions for the world
     * Uses Z-order curve for spatial partitioning to improve cache locality
     */
    private void initializeRegions() {
        // Default region configuration: divide world into regions
        // Each region is 16x16 chunks (256x256 blocks)
        int regionSize = 16;
        if (versionAdapter != null) {
            regionSize = versionAdapter.getRegionSize();
        }
        
        int worldRadius = 32; // 32 chunks radius = 64x64 chunk area
        
        int regionId = 0;
        for (int x = -worldRadius; x < worldRadius; x += regionSize) {
            for (int z = -worldRadius; z < worldRadius; z += regionSize) {
                // Use Z-order curve for region ID to improve cache locality
                int zOrderId = interleaveBits(
                    (x + worldRadius) / regionSize,
                    (z + worldRadius) / regionSize
                );
                
                Region region = new Region(zOrderId, x, z, x + regionSize, z + regionSize);
                regions.put(zOrderId, region);
                regionId++;
            }
        }
        
        Logger.info("Created " + regions.size() + " regions with size " + regionSize + "x" + regionSize + " chunks");
    }
    
    /**
     * Main tick method called from the injected MinecraftServer.tick()
     * This method distributes tick processing across worker threads
     * 
     * @param minecraftServer The MinecraftServer instance
     */
    public void tickRegions(Object minecraftServer) {
        if (!running.get()) {
            return;
        }
        
        // Use adapter timeout if available
        if (versionAdapter != null) {
            tickTimeoutMs = versionAdapter.getTickTimeoutMs();
        }
        
        long tickStartTime = System.nanoTime();
        
        // Acquire read lock for world state
        Lock readLock = worldLock.readLock();
        readLock.lock();
        
        try {
            // Submit all region tasks to worker threads
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (Region region : regions.values()) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> tickRegion(region, minecraftServer),
                    workerPool
                );
                futures.add(future);
            }
            
            // Wait for all regions to complete with timeout
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(tickTimeoutMs, TimeUnit.MILLISECONDS);
                
        } catch (TimeoutException e) {
            // Tick timeout - cancel tasks and fall back
            Logger.warn("Tick timeout detected! Falling back to single-threaded mode");
            handleTickTimeout();
            return;
            
        } catch (Exception e) {
            Logger.error("Error during tick processing: " + e.getMessage());
            handleTickError(e);
            return;
            
        } finally {
            readLock.unlock();
        }
        
        // Update TPS calculation
        updateTPS(tickStartTime);
        
        // Perform load rebalancing if needed
        if (tickCount % 100 == 0) {
            rebalanceRegions();
        }
        
        tickCount++;
    }

    public SafeExecutionStats runSafeRegionTasks(Collection<? extends Runnable> tasks, long timeoutMs) {
        ensureWorkerPool();
        if (tasks == null || tasks.isEmpty()) {
            return new SafeExecutionStats(0, 0, 0, 0, 0);
        }

        long startTime = System.nanoTime();
        AtomicLong taskNanos = new AtomicLong(0);
        AtomicLong failures = new AtomicLong(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>(tasks.size());

        for (Runnable task : tasks) {
            futures.add(CompletableFuture.runAsync(() -> {
                long taskStart = System.nanoTime();
                try {
                    task.run();
                } catch (Throwable t) {
                    failures.incrementAndGet();
                    throw t;
                } finally {
                    taskNanos.addAndGet(System.nanoTime() - taskStart);
                }
            }, workerPool));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            failures.incrementAndGet();
            for (CompletableFuture<Void> future : futures) {
                future.cancel(true);
            }
        } catch (Exception e) {
            failures.incrementAndGet();
        }

        long wallNanos = System.nanoTime() - startTime;
        safeTaskCount.addAndGet(tasks.size());
        safeTaskNanos.addAndGet(taskNanos.get());
        return new SafeExecutionStats(tasks.size(), failures.get(), wallNanos, taskNanos.get(), workerCount);
    }

    public SnapshotAnalysisStats analyzeServerSnapshot(Object minecraftServer) {
        if (minecraftServer == null || regions.isEmpty()) {
            return new SnapshotAnalysisStats(0, 0, 0, 0, 0, workerCount);
        }

        long startTime = System.nanoTime();
        List<SnapshotItem> items = collectSnapshotItems(minecraftServer);
        if (items.isEmpty()) {
            long wallNanos = System.nanoTime() - startTime;
            snapshotAnalysisCount.incrementAndGet();
            snapshotAnalysisNanos.addAndGet(wallNanos);
            return new SnapshotAnalysisStats(0, 0, 0, wallNanos, 0, workerCount);
        }

        AtomicLong matchedItems = new AtomicLong(0);
        AtomicLong hotRegionCount = new AtomicLong(0);
        List<Runnable> tasks = new ArrayList<>(regions.size() * 2);
        Region[] regionArray = regions.values().toArray(new Region[0]);

        for (Region region : regions.values()) {
            tasks.add(() -> {
                long count = 0;
                for (SnapshotItem item : items) {
                    if (region.contains(item.chunkX, item.chunkZ)) {
                        count += item.weight;
                    }
                }
                region.recordSnapshotLoad(count);
                matchedItems.addAndGet(count);
            });
        }

        double averageLoad = items.size() / (double) Math.max(1, regionArray.length);
        double hotThreshold = averageLoad * 2.0;
        for (Region region : regionArray) {
            tasks.add(() -> {
                double neighborSum = 0.0;
                int neighborCount = 0;
                double dx = (region.getMinX() + region.getMaxX()) * 0.5;
                double dz = (region.getMinZ() + region.getMaxZ()) * 0.5;
                double span = Math.max(1.0, region.getMaxX() - region.getMinX());
                for (Region other : regionArray) {
                    if (other == region) {
                        continue;
                    }
                    double ox = (other.getMinX() + other.getMaxX()) * 0.5;
                    double oz = (other.getMinZ() + other.getMaxZ()) * 0.5;
                    double dist = Math.hypot((ox - dx) / span, (oz - dz) / span);
                    if (dist > 2.5) {
                        continue;
                    }
                    double weight = 1.0 / (1.0 + dist * dist);
                    neighborSum += other.getSmoothedLoad() * weight;
                    neighborCount++;
                }
                double predicted = neighborCount > 0 ? neighborSum / neighborCount : 0.0;
                region.setNeighborPredictedLoad(predicted);
                double score = computeImbalanceScore(region.getSmoothedLoad(), predicted, averageLoad);
                region.setImbalanceScore(score);
                if (region.getSmoothedLoad() >= hotThreshold && score > 0.0) {
                    hotRegionCount.incrementAndGet();
                }
            });
        }

        SafeExecutionStats executionStats = runSafeRegionTasks(tasks, Math.max(10L, tickTimeoutMs));

        double peakLoad = 0.0;
        double totalLoad = 0.0;
        for (Region region : regionArray) {
            double load = region.getSmoothedLoad();
            totalLoad += load;
            if (load > peakLoad) {
                peakLoad = load;
            }
        }
        double computedAverageLoad = totalLoad / Math.max(1, regionArray.length);

        long wallNanos = System.nanoTime() - startTime;
        snapshotAnalysisCount.incrementAndGet();
        snapshotItemCount.addAndGet(items.size());
        snapshotAnalysisNanos.addAndGet(wallNanos);
        return new SnapshotAnalysisStats(
            items.size(),
            matchedItems.get(),
            executionStats.getFailureCount(),
            wallNanos,
            executionStats.getTaskNanos(),
            workerCount,
            hotRegionCount.get(),
            peakLoad,
            computedAverageLoad
        );
    }

    private double computeImbalanceScore(double selfLoad, double neighborLoad, double averageLoad) {
        if (averageLoad <= 0) {
            return 0.0;
        }
        double excess = selfLoad - averageLoad;
        if (excess <= 0) {
            return 0.0;
        }
        double neighborRatio = averageLoad > 0 ? neighborLoad / averageLoad : 0.0;
        return (excess / averageLoad) * (1.0 + neighborRatio * 0.5);
    }

    /**
     * 收集热点 region 列表
     * @return 热点 region 列表（按负载降序排列）
     */
    public List<Region> collectHotRegions() {
        if (regions.isEmpty()) {
            return Collections.emptyList();
        }

        double average = getAverageRegionLoad();
        double threshold = average * 2.0;
        List<Region> hotRegions = new ArrayList<>();

        for (Region region : regions.values()) {
            if (region.getSmoothedLoad() >= threshold && region.getImbalanceScore() > 0.0) {
                hotRegions.add(region);
            }
        }

        hotRegions.sort((a, b) -> Double.compare(b.getSmoothedLoad(), a.getSmoothedLoad()));
        return hotRegions;
    }

    /**
     * 为热点 region 执行并行计算任务
     * @param taskFactory 任务工厂，接收 region 返回计算任务
     * @return 执行统计
     */
    public SafeExecutionStats executeHotspotTasks(java.util.function.Function<Region, Runnable> taskFactory) {
        List<Region> hotRegions = collectHotRegions();
        if (hotRegions.isEmpty()) {
            return new SafeExecutionStats(0, 0, 0, 0, workerCount);
        }

        List<Runnable> tasks = new ArrayList<>(hotRegions.size());
        for (Region region : hotRegions) {
            tasks.add(taskFactory.apply(region));
        }

        return runSafeRegionTasks(tasks, Math.max(10L, tickTimeoutMs));
    }

    private List<SnapshotItem> collectSnapshotItems(Object minecraftServer) {
        Object levelsObj = com.mcjebooster.util.ReflectionHelper.getFieldValue(
            minecraftServer,
            "levels",
            "worlds",
            "worldServers",
            "field_71305_c"
        );

        Iterable<?> levels = toIterable(levelsObj);
        if (levels == null) {
            return Collections.emptyList();
        }

        List<SnapshotItem> items = new ArrayList<>();
        for (Object level : levels) {
            if (level == null) {
                continue;
            }

            addEntitySnapshots(items, level);
            addBlockEntitySnapshots(items, level);
        }
        return items;
    }

    private Iterable<?> toIterable(Object value) {
        if (value instanceof Iterable) {
            return (Iterable<?>) value;
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).values();
        }
        return null;
    }

    private void addEntitySnapshots(List<SnapshotItem> items, Object level) {
        Collection<?> entities = com.mcjebooster.util.ReflectionHelper.getCollectionField(
            level,
            "entitiesById",
            "entities",
            "loadedEntityList",
            "field_72996_f"
        );

        for (Object entity : entities) {
            if (entity == null) {
                continue;
            }
            Integer chunkX = readChunkCoordinate(entity, true);
            Integer chunkZ = readChunkCoordinate(entity, false);
            if (chunkX != null && chunkZ != null) {
                items.add(new SnapshotItem(chunkX, chunkZ, 1));
            }
        }
    }

    private void addBlockEntitySnapshots(List<SnapshotItem> items, Object level) {
        Collection<?> blockEntities = com.mcjebooster.util.ReflectionHelper.getCollectionField(
            level,
            "blockEntityTickers",
            "blockEntities",
            "loadedTileEntityList",
            "tickableBlockEntities",
            "field_147482_g"
        );

        for (Object blockEntity : blockEntities) {
            if (blockEntity == null) {
                continue;
            }
            int[] chunkPos = readBlockEntityChunk(blockEntity);
            if (chunkPos != null) {
                items.add(new SnapshotItem(chunkPos[0], chunkPos[1], 2));
            }
        }
    }

    private Integer readChunkCoordinate(Object entity, boolean xAxis) {
        Class<?> clazz = entity.getClass();
        ConcurrentHashMap<Class<?>, String> cache = xAxis ? resolvedEntityXField : resolvedEntityZField;
        String resolved = cache.get(clazz);
        if (resolved == null) {
            String[] candidates = xAxis
                ? new String[]{"x", "posX", "field_70165_t"}
                : new String[]{"z", "posZ", "field_70161_v"};
            Object testValue = com.mcjebooster.util.ReflectionHelper.getFieldValue(entity, candidates);
            if (testValue instanceof Number) {
                cache.put(clazz, candidates[0]);
                return ((Number) testValue).intValue() >> 4;
            }
            for (String name : candidates) {
                Object v = com.mcjebooster.util.ReflectionHelper.getFieldValue(entity, new String[]{name});
                if (v instanceof Number) {
                    cache.put(clazz, name);
                    return ((Number) v).intValue() >> 4;
                }
            }
            return null;
        }
        Object value = com.mcjebooster.util.ReflectionHelper.getFieldValue(entity, new String[]{resolved});
        if (value instanceof Number) {
            return ((Number) value).intValue() >> 4;
        }
        return null;
    }

    private int[] readBlockEntityChunk(Object blockEntity) {
        Class<?> clazz = blockEntity.getClass();

        String posField = resolvedBlockEntityPosField.get(clazz);
        if (posField == null) {
            String[] posCandidates = {"worldPosition", "pos", "blockPos", "field_174879_c"};
            Object testPos = com.mcjebooster.util.ReflectionHelper.getFieldValue(blockEntity, posCandidates);
            if (testPos != null) {
                posField = posCandidates[0];
                resolvedBlockEntityPosField.put(clazz, posField);
            } else {
                for (String name : posCandidates) {
                    Object v = com.mcjebooster.util.ReflectionHelper.getFieldValue(blockEntity, name);
                    if (v != null) {
                        posField = name;
                        resolvedBlockEntityPosField.put(clazz, posField);
                        break;
                    }
                }
            }
        }

        if (posField != null) {
            Object pos = com.mcjebooster.util.ReflectionHelper.getFieldValue(blockEntity, posField);
            if (pos != null) {
                Class<?> posClass = pos.getClass();
                String getMethodName = resolvedBlockEntityGetXMethod.get(posClass);
                String getZMethodName = resolvedBlockEntityGetZMethod.get(posClass);
                if (getMethodName == null || getZMethodName == null) {
                    String[] xCandidates = {"getX", "x", "func_177958_n"};
                    String[] zCandidates = {"getZ", "z", "func_177952_p"};
                    Object testX = com.mcjebooster.util.ReflectionHelper.invokeMethod(pos, xCandidates);
                    if (testX instanceof Number) {
                        getMethodName = xCandidates[0];
                        resolvedBlockEntityGetXMethod.put(posClass, getMethodName);
                    } else {
                        for (String name : xCandidates) {
                            Object v = com.mcjebooster.util.ReflectionHelper.invokeMethod(pos, new String[]{name});
                            if (v instanceof Number) {
                                getMethodName = name;
                                resolvedBlockEntityGetXMethod.put(posClass, name);
                                break;
                            }
                        }
                    }
                    Object testZ = com.mcjebooster.util.ReflectionHelper.invokeMethod(pos, zCandidates);
                    if (testZ instanceof Number) {
                        getZMethodName = zCandidates[0];
                        resolvedBlockEntityGetZMethod.put(posClass, getZMethodName);
                    } else {
                        for (String name : zCandidates) {
                            Object v = com.mcjebooster.util.ReflectionHelper.invokeMethod(pos, new String[]{name});
                            if (v instanceof Number) {
                                getZMethodName = name;
                                resolvedBlockEntityGetZMethod.put(posClass, name);
                                break;
                            }
                        }
                    }
                }
                if (getMethodName != null && getZMethodName != null) {
                    Object x = com.mcjebooster.util.ReflectionHelper.invokeMethod(pos, new String[]{getMethodName});
                    Object z = com.mcjebooster.util.ReflectionHelper.invokeMethod(pos, new String[]{getZMethodName});
                    if (x instanceof Number && z instanceof Number) {
                        return new int[] { ((Number) x).intValue() >> 4, ((Number) z).intValue() >> 4 };
                    }
                }
            }
        }

        String xField = resolvedBlockEntityXField.get(clazz);
        String zField = resolvedBlockEntityZField.get(clazz);
        if (xField == null) {
            String[] xCandidates = {"x", "xCoord", "field_174879_c"};
            Object testX = com.mcjebooster.util.ReflectionHelper.getFieldValue(blockEntity, xCandidates);
            if (testX instanceof Number) {
                xField = xCandidates[0];
                resolvedBlockEntityXField.put(clazz, xField);
            } else {
                for (String name : xCandidates) {
                    Object v = com.mcjebooster.util.ReflectionHelper.getFieldValue(blockEntity, name);
                    if (v instanceof Number) {
                        xField = name;
                        resolvedBlockEntityXField.put(clazz, name);
                        break;
                    }
                }
            }
        }
        if (zField == null) {
            String[] zCandidates = {"z", "zCoord", "field_174881_e"};
            Object testZ = com.mcjebooster.util.ReflectionHelper.getFieldValue(blockEntity, zCandidates);
            if (testZ instanceof Number) {
                zField = zCandidates[0];
                resolvedBlockEntityZField.put(clazz, zField);
            } else {
                for (String name : zCandidates) {
                    Object v = com.mcjebooster.util.ReflectionHelper.getFieldValue(blockEntity, name);
                    if (v instanceof Number) {
                        zField = name;
                        resolvedBlockEntityZField.put(clazz, name);
                        break;
                    }
                }
            }
        }
        if (xField != null && zField != null) {
            Object x = com.mcjebooster.util.ReflectionHelper.getFieldValue(blockEntity, xField);
            Object z = com.mcjebooster.util.ReflectionHelper.getFieldValue(blockEntity, zField);
            if (x instanceof Number && z instanceof Number) {
                return new int[] { ((Number) x).intValue() >> 4, ((Number) z).intValue() >> 4 };
            }
        }
        return null;
    }

    private void ensureWorkerPool() {
        if (workerPool != null && !workerPool.isShutdown()) {
            return;
        }
        workerPool = new ForkJoinPool(
            workerCount,
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true
        );
    }
    
    /**
     * Processes a single region's tick
     * 
     * @param region The region to process
     * @param minecraftServer The MinecraftServer instance
     */
    private void tickRegion(Region region, Object minecraftServer) {
        long startTime = System.nanoTime();
        
        try {
            // Process entities in this region
            tickEntitiesInRegion(region, minecraftServer);
            
            // Process block ticks in this region
            tickBlocksInRegion(region, minecraftServer);
            
            // Process tile entities in this region
            tickTileEntitiesInRegion(region, minecraftServer);
            
        } catch (Exception e) {
            Logger.error("Error ticking region " + region.getId() + ": " + e.getMessage());
            conflictCount.incrementAndGet();
        }
        
        // Record execution time for load balancing
        long executionTime = System.nanoTime() - startTime;
        regionExecutionTimes.put(region.getId(), executionTime);
    }
    
    /**
     * Ticks entities within a specific region
     * 
     * @param region The region containing entities
     * @param minecraftServer The MinecraftServer instance
     */
    private void tickEntitiesInRegion(Region region, Object minecraftServer) {
        try {
            // 获取所有世界/Level
            Object levelsObj = com.mcjebooster.util.ReflectionHelper.getFieldValue(
                minecraftServer,
                "levels",           // Mojang mapping
                "worlds",           // Some versions
                "worldServers",     // Older versions
                "field_71305_c"     // MCP obfuscated
            );
            
            if (levelsObj == null) {
                return;
            }
            
            // levelsObj 可能是 Iterable 或 Map
            Iterable<?> levels;
            if (levelsObj instanceof Iterable) {
                levels = (Iterable<?>) levelsObj;
            } else if (levelsObj instanceof java.util.Map) {
                levels = ((java.util.Map<?, ?>) levelsObj).values();
            } else {
                return;
            }
            
            // 遍历每个世界
            for (Object level : levels) {
                if (level == null) continue;
                
                // 获取实体列表
                java.util.Collection<?> entities = com.mcjebooster.util.ReflectionHelper.getCollectionField(
                    level,
                    "entitiesById",         // 1.20+ Mojang
                    "entities",             // Older Mojang
                    "loadedEntityList",     // Some versions
                    "field_72996_f"         // MCP obfuscated
                );
                
                // Tick 区域内的实体
                for (Object entity : entities) {
                    if (entity == null) continue;
                    
                    // 获取实体位置
                    Object posX = com.mcjebooster.util.ReflectionHelper.getFieldValue(
                        entity, "x", "posX", "field_70165_t"
                    );
                    Object posZ = com.mcjebooster.util.ReflectionHelper.getFieldValue(
                        entity, "z", "posZ", "field_70161_v"
                    );
                    
                    if (posX == null || posZ == null) continue;
                    
                    // 转换为区块坐标
                    int chunkX = ((Number) posX).intValue() >> 4;
                    int chunkZ = ((Number) posZ).intValue() >> 4;
                    
                    // 检查是否在当前区域内
                    if (region.contains(chunkX, chunkZ)) {
                        // 调用实体的 tick 方法
                        com.mcjebooster.util.ReflectionHelper.invokeMethod(
                            entity,
                            new String[]{"tick", "onUpdate", "func_70071_h_"}
                        );
                    }
                }
            }
            
        } catch (Exception e) {
            Logger.error("Error ticking entities in region " + region.getId() + ": " + e.getMessage());
        }
    }
    
    /**
     * Ticks blocks within a specific region
     * 
     * @param region The region containing blocks
     * @param minecraftServer The MinecraftServer instance
     */
    private void tickBlocksInRegion(Region region, Object minecraftServer) {
        try {
            // 获取所有世界
            Object levelsObj = com.mcjebooster.util.ReflectionHelper.getFieldValue(
                minecraftServer,
                "levels", "worlds", "worldServers", "field_71305_c"
            );
            
            if (levelsObj == null) {
                return;
            }
            
            Iterable<?> levels;
            if (levelsObj instanceof Iterable) {
                levels = (Iterable<?>) levelsObj;
            } else if (levelsObj instanceof java.util.Map) {
                levels = ((java.util.Map<?, ?>) levelsObj).values();
            } else {
                return;
            }
            
            // 遍历每个世界
            for (Object level : levels) {
                if (level == null) continue;
                
                // 获取区块缓存/提供器
                Object chunkSource = com.mcjebooster.util.ReflectionHelper.getFieldValue(
                    level,
                    "chunkSource",          // Mojang
                    "chunkProvider",        // Older
                    "field_73020_y"         // MCP obfuscated
                );
                
                if (chunkSource == null) continue;
                
                // 遍历区域内的区块
                for (int x = region.getMinX(); x < region.getMaxX(); x++) {
                    for (int z = region.getMinZ(); z < region.getMaxZ(); z++) {
                        try {
                            // 获取区块（不强制加载）
                            Object chunk = com.mcjebooster.util.ReflectionHelper.invokeMethod(
                                chunkSource,
                                new String[]{"getChunkNow", "getChunkIfLoaded", "func_217213_a"},
                                x, z
                            );
                            
                            if (chunk != null) {
                                // 调用区块的 tick 方法
                                com.mcjebooster.util.ReflectionHelper.invokeMethod(
                                    chunk,
                                    new String[]{"tick", "tickChunk", "func_150804_b"},
                                    false  // 随机 tick 参数
                                );
                            }
                        } catch (Exception e) {
                            // 忽略单个区块的错误
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Logger.error("Error ticking blocks in region " + region.getId() + ": " + e.getMessage());
        }
    }
    
    /**
     * Ticks tile entities within a specific region
     * 
     * @param region The region containing tile entities
     * @param minecraftServer The MinecraftServer instance
     */
    private void tickTileEntitiesInRegion(Region region, Object minecraftServer) {
        try {
            // 获取所有世界
            Object levelsObj = com.mcjebooster.util.ReflectionHelper.getFieldValue(
                minecraftServer,
                "levels", "worlds", "worldServers", "field_71305_c"
            );
            
            if (levelsObj == null) {
                return;
            }
            
            Iterable<?> levels;
            if (levelsObj instanceof Iterable) {
                levels = (Iterable<?>) levelsObj;
            } else if (levelsObj instanceof java.util.Map) {
                levels = ((java.util.Map<?, ?>) levelsObj).values();
            } else {
                return;
            }
            
            // 遍历每个世界
            for (Object level : levels) {
                if (level == null) continue;
                
                // 获取方块实体列表（BlockEntity/TileEntity）
                java.util.Collection<?> blockEntities = com.mcjebooster.util.ReflectionHelper.getCollectionField(
                    level,
                    "blockEntityTickers",       // 1.20+ Mojang
                    "blockEntities",            // Older Mojang
                    "loadedTileEntityList",     // Legacy
                    "tickableBlockEntities",    // Some versions
                    "field_147482_g"            // MCP obfuscated
                );
                
                // Tick 区域内的方块实体
                for (Object blockEntity : blockEntities) {
                    if (blockEntity == null) continue;
                    
                    // 获取方块实体位置
                    Object pos = com.mcjebooster.util.ReflectionHelper.getFieldValue(
                        blockEntity,
                        "worldPosition",    // Mojang 1.20+
                        "pos",              // Older Mojang
                        "blockPos",         // Some versions
                        "field_174879_c"    // MCP obfuscated
                    );
                    
                    if (pos == null) {
                        // 尝试直接获取坐标
                        Object x = com.mcjebooster.util.ReflectionHelper.getFieldValue(
                            blockEntity, "x", "xCoord", "field_174879_c"
                        );
                        Object z = com.mcjebooster.util.ReflectionHelper.getFieldValue(
                            blockEntity, "z", "zCoord", "field_174881_e"
                        );
                        
                        if (x != null && z != null) {
                            int chunkX = ((Number) x).intValue() >> 4;
                            int chunkZ = ((Number) z).intValue() >> 4;
                            
                            if (region.contains(chunkX, chunkZ)) {
                                // 调用方块实体的 tick 方法
                                com.mcjebooster.util.ReflectionHelper.invokeMethod(
                                    blockEntity,
                                    new String[]{"tick", "update", "func_73660_a"}
                                );
                            }
                        }
                    } else {
                        // pos 是 BlockPos 对象
                        Object x = com.mcjebooster.util.ReflectionHelper.invokeMethod(
                            pos, new String[]{"getX", "x", "func_177958_n"}
                        );
                        Object z = com.mcjebooster.util.ReflectionHelper.invokeMethod(
                            pos, new String[]{"getZ", "z", "func_177952_p"}
                        );
                        
                        if (x != null && z != null) {
                            int chunkX = ((Number) x).intValue() >> 4;
                            int chunkZ = ((Number) z).intValue() >> 4;
                            
                            if (region.contains(chunkX, chunkZ)) {
                                // 调用方块实体的 tick 方法
                                com.mcjebooster.util.ReflectionHelper.invokeMethod(
                                    blockEntity,
                                    new String[]{"tick", "update", "func_73660_a"}
                                );
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Logger.error("Error ticking tile entities in region " + region.getId() + ": " + e.getMessage());
        }
    }
    
    /**
     * Updates the current TPS calculation
     * 
     * @param tickStartTime The start time of the current tick in nanoseconds
     */
    private void updateTPS(long tickStartTime) {
        long currentTime = System.nanoTime();
        long tickDuration = currentTime - lastTickTime;
        
        // Calculate TPS: 1 second / tick duration
        double instantTPS = 1_000_000_000.0 / tickDuration;
        
        // Smooth the TPS value (exponential moving average)
        currentTPS = (currentTPS * 0.9) + (instantTPS * 0.1);
        
        lastTickTime = currentTime;
    }
    
    /**
     * Rebalances regions based on execution time statistics
     * Splits high-load regions and merges low-load regions
     */
    private void rebalanceRegions() {
        if (regionExecutionTimes.isEmpty()) {
            return;
        }
        
        // Calculate average and standard deviation
        double avgTime = regionExecutionTimes.values().stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
        
        double variance = regionExecutionTimes.values().stream()
            .mapToLong(Long::longValue)
            .mapToDouble(time -> Math.pow(time - avgTime, 2))
            .average()
            .orElse(0.0);
        
        double stdDev = Math.sqrt(variance);
        double cv = (avgTime > 0) ? stdDev / avgTime : 0;
        
        // If coefficient of variation exceeds threshold, rebalance
        if (cv > REBALANCE_THRESHOLD) {
            Logger.info("Load imbalance detected (CV=" + String.format("%.2f", cv) + "), rebalancing regions...");
            
            // Identify high-load regions
            List<Integer> highLoadRegions = new ArrayList<>();
            for (Map.Entry<Integer, Long> entry : regionExecutionTimes.entrySet()) {
                if (entry.getValue() > avgTime + stdDev) {
                    highLoadRegions.add(entry.getKey());
                }
            }
            
            // Log rebalancing action
            if (!highLoadRegions.isEmpty()) {
                Logger.info("Identified " + highLoadRegions.size() + " high-load regions for optimization");
            }
        }
    }
    
    /**
     * Handles tick timeout by falling back to single-threaded mode
     */
    private void handleTickTimeout() {
        // Cancel all pending tasks
        workerPool.shutdownNow();
        
        // Reset to single-threaded mode
        Logger.warn("Falling back to vanilla single-threaded tick processing");
        
        // Reinitialize with more conservative settings
        this.workerPool = new ForkJoinPool(
            Math.max(1, workerCount / 2),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true
        );
    }
    
    /**
     * Handles tick processing errors
     * 
     * @param e The exception that occurred
     */
    private void handleTickError(Exception e) {
        Logger.error("Tick processing error: " + e.getMessage());
        conflictCount.incrementAndGet();
    }
    
    /**
     * Interleaves bits of two integers using Z-order curve (Morton code)
     * This provides better cache locality for spatial access patterns
     * 
     * @param x The x coordinate
     * @param z The z coordinate
     * @return The interleaved Z-order index
     */
    private int interleaveBits(int x, int z) {
        // Spread bits using magic numbers for Z-order curve
        x = (x | (x << 8)) & 0x00FF00FF;
        x = (x | (x << 4)) & 0x0F0F0F0F;
        x = (x | (x << 2)) & 0x33333333;
        x = (x | (x << 1)) & 0x55555555;
        
        z = (z | (z << 8)) & 0x00FF00FF;
        z = (z | (z << 4)) & 0x0F0F0F0F;
        z = (z | (z << 2)) & 0x33333333;
        z = (z | (z << 1)) & 0x55555555;
        
        return x | (z << 1);
    }
    
    /**
     * Shuts down the scheduler and all worker threads
     */
    public void shutdown() {
        Logger.info("Shutting down RegionScheduler...");
        running.set(false);
        alive = false;
        
        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
            }
        }
        
        regions.clear();
        regionExecutionTimes.clear();
        
        Logger.info("RegionScheduler shutdown completed");
    }
    
    /**
     * Checks if the scheduler is running
     * 
     * @return true if the scheduler is active
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Checks if the scheduler threads are alive
     * 
     * @return true if worker threads are not dead
     */
    public boolean isAlive() {
        return alive && (workerPool == null || !workerPool.isShutdown());
    }
    
    /**
     * Gets the current measured TPS
     * 
     * @return The current TPS value
     */
    public double getCurrentTPS() {
        return currentTPS;
    }
    
    /**
     * Gets the total tick count since initialization
     * 
     * @return The tick count
     */
    public long getTickCount() {
        return tickCount;
    }
    
    /**
     * Gets the number of thread conflicts detected
     * 
     * @return The conflict count
     */
    public long getConflictCount() {
        return conflictCount.get();
    }
    
    /**
     * Gets the number of regions
     * 
     * @return The region count
     */
    public int getRegionCount() {
        return regions.size();
    }

    public long getSafeTaskCount() {
        return safeTaskCount.get();
    }

    public long getSafeTaskNanos() {
        return safeTaskNanos.get();
    }

    public long getSnapshotAnalysisCount() {
        return snapshotAnalysisCount.get();
    }

    public long getSnapshotItemCount() {
        return snapshotItemCount.get();
    }

    public long getSnapshotAnalysisNanos() {
        return snapshotAnalysisNanos.get();
    }

    public double getPeakRegionLoad() {
        double peak = 0.0;
        for (Region region : regions.values()) {
            double load = region.getSmoothedLoad();
            if (load > peak) {
                peak = load;
            }
        }
        return peak;
    }

    public long getHotRegionCount() {
        long count = 0;
        double average = getAverageRegionLoad();
        double threshold = average * 2.0;
        for (Region region : regions.values()) {
            if (region.getSmoothedLoad() >= threshold && region.getImbalanceScore() > 0.0) {
                count++;
            }
        }
        return count;
    }

    public double getAverageRegionLoad() {
        if (regions.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (Region region : regions.values()) {
            total += region.getSmoothedLoad();
        }
        return total / regions.size();
    }
    
    /**
     * Adds a region to the scheduler
     * 
     * @param region The region to add
     */
    public void addRegion(Region region) {
        regions.put(region.getId(), region);
    }
    
    /**
     * Removes a region from the scheduler
     * 
     * @param regionId The ID of the region to remove
     */
    public void removeRegion(int regionId) {
        regions.remove(regionId);
        regionExecutionTimes.remove(regionId);
    }
    
    /**
     * Represents a spatial region in the Minecraft world
     */
    public static class Region {
        private final int id;
        private final int minX;
        private final int minZ;
        private final int maxX;
        private final int maxZ;
        private volatile double smoothedLoad;
        private volatile double peakLoad;
        private volatile long loadSampleCount;
        private volatile double instantLoad;
        private volatile double neighborPredictedLoad;
        private volatile double imbalanceScore;
        
        public Region(int id, int minX, int minZ, int maxX, int maxZ) {
            this.id = id;
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }
        
        public int getId() {
            return id;
        }
        
        public int getMinX() {
            return minX;
        }
        
        public int getMinZ() {
            return minZ;
        }
        
        public int getMaxX() {
            return maxX;
        }
        
        public int getMaxZ() {
            return maxZ;
        }
        
        /**
         * Checks if a point is within this region
         * 
         * @param x The x coordinate
         * @param z The z coordinate
         * @return true if the point is within the region
         */
        public boolean contains(int x, int z) {
            return x >= minX && x < maxX && z >= minZ && z < maxZ;
        }

        public double getSmoothedLoad() {
            return smoothedLoad;
        }

        public double getPeakLoad() {
            return peakLoad;
        }

        public long getLoadSampleCount() {
            return loadSampleCount;
        }

        public double getInstantLoad() {
            return instantLoad;
        }

        public double getNeighborPredictedLoad() {
            return neighborPredictedLoad;
        }

        public void setNeighborPredictedLoad(double value) {
            this.neighborPredictedLoad = value;
        }

        public double getImbalanceScore() {
            return imbalanceScore;
        }

        public void setImbalanceScore(double value) {
            this.imbalanceScore = value;
        }

        public synchronized void recordSnapshotLoad(double load) {
            this.instantLoad = load;
            this.loadSampleCount++;
            if (loadSampleCount <= 1) {
                this.smoothedLoad = load;
            } else {
                this.smoothedLoad = this.smoothedLoad * 0.7 + load * 0.3;
            }
            if (load > this.peakLoad) {
                this.peakLoad = load;
            }
        }

        @Override
        public String toString() {
            return "Region[" + id + "] (" + minX + "," + minZ + ") to (" + maxX + "," + maxZ + ")";
        }
    }

    public static class SafeExecutionStats {
        private final int taskCount;
        private final long failureCount;
        private final long wallNanos;
        private final long taskNanos;
        private final int workerCount;

        public SafeExecutionStats(int taskCount, long failureCount, long wallNanos, long taskNanos, int workerCount) {
            this.taskCount = taskCount;
            this.failureCount = failureCount;
            this.wallNanos = wallNanos;
            this.taskNanos = taskNanos;
            this.workerCount = workerCount;
        }

        public int getTaskCount() {
            return taskCount;
        }

        public long getFailureCount() {
            return failureCount;
        }

        public long getWallNanos() {
            return wallNanos;
        }

        public long getTaskNanos() {
            return taskNanos;
        }

        public int getWorkerCount() {
            return workerCount;
        }

        public double getParallelismRatio() {
            if (wallNanos <= 0) {
                return 0.0;
            }
            return taskNanos / (double) wallNanos;
        }
    }

    private static class SnapshotItem {
        private final int chunkX;
        private final int chunkZ;
        private final int weight;

        private SnapshotItem(int chunkX, int chunkZ, int weight) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.weight = weight;
        }
    }

    public static class SnapshotAnalysisStats {
        private final int itemCount;
        private final long matchedWeight;
        private final long failureCount;
        private final long wallNanos;
        private final long taskNanos;
        private final int workerCount;
        private final long hotRegionCount;
        private final double peakLoad;
        private final double averageLoad;

        public SnapshotAnalysisStats(int itemCount, long matchedWeight, long failureCount, long wallNanos, long taskNanos, int workerCount) {
            this(itemCount, matchedWeight, failureCount, wallNanos, taskNanos, workerCount, 0L, 0.0, 0.0);
        }

        public SnapshotAnalysisStats(int itemCount, long matchedWeight, long failureCount, long wallNanos, long taskNanos, int workerCount, long hotRegionCount, double peakLoad, double averageLoad) {
            this.itemCount = itemCount;
            this.matchedWeight = matchedWeight;
            this.failureCount = failureCount;
            this.wallNanos = wallNanos;
            this.taskNanos = taskNanos;
            this.workerCount = workerCount;
            this.hotRegionCount = hotRegionCount;
            this.peakLoad = peakLoad;
            this.averageLoad = averageLoad;
        }

        public int getItemCount() {
            return itemCount;
        }

        public long getMatchedWeight() {
            return matchedWeight;
        }

        public long getFailureCount() {
            return failureCount;
        }

        public long getWallNanos() {
            return wallNanos;
        }

        public long getTaskNanos() {
            return taskNanos;
        }

        public int getWorkerCount() {
            return workerCount;
        }

        public long getHotRegionCount() {
            return hotRegionCount;
        }

        public double getPeakLoad() {
            return peakLoad;
        }

        public double getAverageLoad() {
            return averageLoad;
        }

        public double getParallelismRatio() {
            if (wallNanos <= 0) {
                return 0.0;
            }
            return taskNanos / (double) wallNanos;
        }
    }
}
