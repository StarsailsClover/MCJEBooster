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

            OBSERVED_TICKS.incrementAndGet();

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