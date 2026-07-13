/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.agent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bridge invoked from transformed Minecraft bytecode.
 * 
 * This class is intentionally self-contained with NO dependencies on other
 * MCJEBooster classes so it can be loaded by the bootstrap classloader
 * without causing loader constraint violations.
 * 
 * All communication with other agent components (RegionScheduler, Logger)
 * is done via reflection through the context classloader.
 */
public final class InjectionBridge {
    private static final boolean EXPERIMENTAL_PARALLEL_TICK = 
        Boolean.getBoolean("mcjebooster.experimental.parallelTick");
    private static final AtomicBoolean MODE_LOGGED = new AtomicBoolean(false);
    private static final AtomicLong OBSERVED_TICKS = new AtomicLong(0);
    private static final AtomicLong LAST_STATS_TICK = new AtomicLong(0);
    private static final long STATS_INTERVAL_TICKS = Long.getLong("mcjebooster.statsIntervalTicks", 1200L);
    private static final long SNAPSHOT_INTERVAL_TICKS = Long.getLong("mcjebooster.snapshotIntervalTicks", 20L);
    private static final long HOTSPOT_INTERVAL_TICKS = Long.getLong("mcjebooster.hotspotIntervalTicks", 10L);

    private InjectionBridge() {
    }

    public static void tickRegions(Object minecraftServer) {
        try {
            if (MODE_LOGGED.compareAndSet(false, true)) {
                String msg = EXPERIMENTAL_PARALLEL_TICK
                    ? "[MCJEBooster] Experimental parallel tick is enabled. Vanilla world tick may be duplicated."
                    : "[MCJEBooster] Safe tick bridge active. Parallel region tick is disabled by default.";
                logInfo(msg);
            }

            long observedTicks = OBSERVED_TICKS.incrementAndGet();
            maybeAnalyzeSnapshot(minecraftServer, observedTicks);
            maybeExecuteHotspotTasks(observedTicks);
            maybeLogSchedulerStats(observedTicks);

            if (EXPERIMENTAL_PARALLEL_TICK) {
                callRegionScheduler(minecraftServer);
            }
        } catch (Throwable t) {
            logError("Injected tick bridge failed: " + t.getClass().getName() + ": " + t.getMessage());
        }
    }

    public static long getObservedTicks() {
        return OBSERVED_TICKS.get();
    }

    /**
     * Calls RegionScheduler.getInstance().tickRegions() via reflection
     * using the context classloader to avoid direct class dependencies.
     */
    private static void callRegionScheduler(Object minecraftServer) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        Class<?> schedulerClass = cl.loadClass("com.mcjebooster.scheduler.RegionScheduler");
        Object scheduler = schedulerClass.getMethod("getInstance").invoke(null);
        schedulerClass.getMethod("tickRegions", Object.class).invoke(scheduler, minecraftServer);
    }

    private static void maybeLogSchedulerStats(long observedTicks) {
        if (STATS_INTERVAL_TICKS <= 0 || observedTicks < STATS_INTERVAL_TICKS) {
            return;
        }

        long last = LAST_STATS_TICK.get();
        if (observedTicks - last < STATS_INTERVAL_TICKS) {
            return;
        }
        if (!LAST_STATS_TICK.compareAndSet(last, observedTicks)) {
            return;
        }

        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }
            Class<?> schedulerClass = cl.loadClass("com.mcjebooster.scheduler.RegionScheduler");
            Object scheduler = schedulerClass.getMethod("getInstance").invoke(null);
            long safeTaskCount = ((Number) schedulerClass.getMethod("getSafeTaskCount").invoke(scheduler)).longValue();
            long safeTaskNanos = ((Number) schedulerClass.getMethod("getSafeTaskNanos").invoke(scheduler)).longValue();
            long snapshotCount = ((Number) schedulerClass.getMethod("getSnapshotAnalysisCount").invoke(scheduler)).longValue();
            long snapshotItems = ((Number) schedulerClass.getMethod("getSnapshotItemCount").invoke(scheduler)).longValue();
            long snapshotNanos = ((Number) schedulerClass.getMethod("getSnapshotAnalysisNanos").invoke(scheduler)).longValue();
            long hotRegions = ((Number) schedulerClass.getMethod("getHotRegionCount").invoke(scheduler)).longValue();
            double peakLoad = ((Number) schedulerClass.getMethod("getPeakRegionLoad").invoke(scheduler)).doubleValue();
            double avgLoad = ((Number) schedulerClass.getMethod("getAverageRegionLoad").invoke(scheduler)).doubleValue();
            double safeTaskMillis = safeTaskNanos / 1_000_000.0;
            double snapshotMillis = snapshotNanos / 1_000_000.0;

            logInfo(
                "[MCJEBooster] Bridge stats: observedTicks=" + observedTicks +
                ", safeTasks=" + safeTaskCount +
                ", safeTaskMs=" + formatDouble(safeTaskMillis) +
                ", snapshots=" + snapshotCount +
                ", snapshotItems=" + snapshotItems +
                ", snapshotMs=" + formatDouble(snapshotMillis) +
                ", hotRegions=" + hotRegions +
                ", peakLoad=" + formatDouble(peakLoad) +
                ", avgLoad=" + formatDouble(avgLoad)
            );
        } catch (Throwable ignored) {
        }
    }

    private static void maybeAnalyzeSnapshot(Object minecraftServer, long observedTicks) {
        if (minecraftServer == null || SNAPSHOT_INTERVAL_TICKS <= 0 || observedTicks % SNAPSHOT_INTERVAL_TICKS != 0) {
            return;
        }

        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }
            Class<?> schedulerClass = cl.loadClass("com.mcjebooster.scheduler.RegionScheduler");
            Object scheduler = schedulerClass.getMethod("getInstance").invoke(null);
            schedulerClass.getMethod("analyzeServerSnapshot", Object.class).invoke(scheduler, minecraftServer);
        } catch (Throwable ignored) {
        }
    }

    private static void maybeExecuteHotspotTasks(long observedTicks) {
        if (HOTSPOT_INTERVAL_TICKS <= 0 || observedTicks % HOTSPOT_INTERVAL_TICKS != 0) {
            return;
        }

        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }
            Class<?> schedulerClass = cl.loadClass("com.mcjebooster.scheduler.RegionScheduler");
            Object scheduler = schedulerClass.getMethod("getInstance").invoke(null);

            // 创建一个任务工厂，为每个热点 region 生成一个轻量级计算任务
            Object taskFactory = java.lang.reflect.Proxy.newProxyInstance(
                cl,
                new Class<?>[] { cl.loadClass("java.util.function.Function") },
                (proxy, method, args) -> {
                    if (method.getName().equals("apply")) {
                        Object region = args[0];
                        // 返回一个 Runnable 任务
                        return (Runnable) () -> {
                            try {
                                // 获取 region 的负载信息
                                double load = (double) region.getClass().getMethod("getSmoothedLoad").invoke(region);
                                int id = (int) region.getClass().getMethod("getId").invoke(region);
                                // 执行轻量级计算：模拟热点 region 的负载分析
                                double sum = 0;
                                for (int i = 0; i < 1000; i++) {
                                    sum += Math.sin(i * load) * Math.cos(i);
                                }
                                // 避免优化掉计算
                                if (sum == Double.MAX_VALUE) {
                                    System.out.println("Hotspot task for region " + id);
                                }
                            } catch (Throwable ignored) {
                            }
                        };
                    }
                    return null;
                }
            );

            schedulerClass.getMethod("executeHotspotTasks", cl.loadClass("java.util.function.Function"))
                .invoke(scheduler, taskFactory);
        } catch (Throwable ignored) {
        }
    }

    private static String formatDouble(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    /**
     * Logs via Logger if available, otherwise falls back to System.err.
     */
    private static void logInfo(String msg) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }
            Class<?> loggerClass = cl.loadClass("com.mcjebooster.util.Logger");
            loggerClass.getMethod("info", String.class).invoke(null, msg);
        } catch (Exception e) {
            System.err.println(msg);
        }
    }

    private static void logError(String msg) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }
            Class<?> loggerClass = cl.loadClass("com.mcjebooster.util.Logger");
            loggerClass.getMethod("error", String.class).invoke(null, msg);
        } catch (Exception e) {
            System.err.println(msg);
        }
    }
}
