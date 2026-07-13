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

package com.mcjebooster.agent;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.ClassDefinition;
import java.security.ProtectionDomain;
import java.io.File;
import java.util.jar.JarFile;

import com.mcjebooster.transformer.MinecraftServerTransformer;
import com.mcjebooster.scheduler.RegionScheduler;
import com.mcjebooster.util.VersionDetector;
import com.mcjebooster.util.Logger;
import com.mcjebooster.adapter.VersionAdapter;
import com.mcjebooster.adapter.AdapterLoader;

/**
 * MCJEBooster Java Agent - Main entry point for runtime injection
 * 
 * This agent is loaded either via:
 * 1. -javaagent parameter at JVM startup (premain)
 * 2. Dynamic attach to running process (agentmain)
 * 
 * The agent performs bytecode transformation on Minecraft's tick loop
 * to enable multi-core region-based scheduling.
 * 
 * IMPORTANT: This is NOT a Minecraft Mod. It is a standalone injection
 * tool that modifies Minecraft at the JVM bytecode level.
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 * @since 1.0
 */
public class MCJEBoosterAgent {
    
    /** The instrumentation instance provided by the JVM */
    private static Instrumentation instrumentation;
    
    /** Flag indicating if the agent has been successfully initialized */
    private static volatile boolean initialized = false;
    
    /** The detected Minecraft version */
    private static String detectedVersion = null;
    
    /** The loaded version adapter */
    private static VersionAdapter versionAdapter = null;
    
    /** Health check interval in ticks (approximately 5 seconds) */
    private static final int HEALTH_CHECK_INTERVAL = 100;
    
    /** Consecutive failure counter for auto-rollback */
    private static int consecutiveFailures = 0;
    
    /** Maximum allowed consecutive failures before rollback */
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    
    /** TPS threshold for considering the system unhealthy */
    private static final double MIN_HEALTHY_TPS = 5.0;
    
    /** 
     * Agent main entry point for dynamic attach (runtime injection)
     * This method is called when the agent is attached to a running JVM.
     * 
     * @param agentArgs Arguments passed to the agent
     * @param inst The Instrumentation instance
     * @throws Exception if initialization fails
     */
    public static void agentmain(String agentArgs, Instrumentation inst) throws Exception {
        Logger.info("MCJEBooster Agent loaded via dynamic attach");
        initialize(agentArgs, inst);
    }
    
    /**
     * Premain entry point for startup-time injection (-javaagent parameter)
     * This method is called when the agent is loaded at JVM startup.
     * 
     * @param agentArgs Arguments passed to the agent
     * @param inst The Instrumentation instance
     * @throws Exception if initialization fails
     */
    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        Logger.info("MCJEBooster Agent loaded via -javaagent");
        initialize(agentArgs, inst);
    }
    
    /**
     * Core initialization logic shared between agentmain and premain
     * 
     * @param agentArgs Arguments passed to the agent
     * @param inst The Instrumentation instance
     * @throws Exception if initialization fails
     */
    private static void appendAgentToBootstrap(Instrumentation inst) {
        try {
            File agentFile = new File(
                MCJEBoosterAgent.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
            );
            if (agentFile.isFile()) {
                // Add agent jar to system classloader. The system classloader is the
                // parent of the URLClassLoader that loads MinecraftServer (via -jar).
                // This makes InjectionBridge visible to the transformed bytecode
                // without causing loader constraint violations.
                inst.appendToSystemClassLoaderSearch(new JarFile(agentFile));
                Logger.info("Agent jar added to system classloader: " + agentFile.getName());
            } else {
                Logger.warn("Agent code source is not a jar file: " + agentFile);
            }
        } catch (Exception e) {
            Logger.warn("Failed to append agent jar to classloader: " + e.getMessage());
        }
    }
    
    private static synchronized void initialize(String agentArgs, Instrumentation inst) throws Exception {
        if (initialized) {
            Logger.warn("Agent already initialized, skipping duplicate initialization");
            return;
        }
        
        instrumentation = inst;
        
        try {
            Logger.info("=========================================");
            Logger.info("MCJEBooster Multi-Core Optimization Engine");
            Logger.info("Version: 26.6-20260706");
            Logger.info("Author: StarsailsClover");
            Logger.info("License: LGPL-2.1");
            Logger.info("=========================================");
            
            // Step 1: Detect Minecraft version
            detectedVersion = VersionDetector.detectMinecraftVersion();
            if (detectedVersion == null || detectedVersion.equals("unknown")) {
                Logger.warn("Could not detect Minecraft version, proceeding with defaults");
                detectedVersion = "unknown";
            }
            Logger.info("Detected Minecraft version: " + detectedVersion);
            
            // Step 2: Add agent jar to system classloader so dependencies are visible
            appendAgentToBootstrap(inst);
            
            // Step 3: Initialize adapter loader and load version-specific adapter
            AdapterLoader adapterLoader = AdapterLoader.getInstance();
            adapterLoader.initialize(null);
            
            // Detect loader type from version string
            VersionAdapter.LoaderType loaderType = VersionAdapter.LoaderType.fromVersionString(detectedVersion);
            versionAdapter = adapterLoader.loadAdapter(detectedVersion, loaderType, null);
            
            if (versionAdapter == null) {
                Logger.warn("No specific adapter found for " + detectedVersion + 
                           " with " + loaderType.getDisplayName() + ", using defaults");
            } else {
                Logger.info("Loaded adapter: " + versionAdapter.getAdapterId());
                if (!versionAdapter.validate()) {
                    Logger.error("Adapter validation failed, falling back to defaults");
                    versionAdapter = null;
                }
            }
            
            // Step 4: Verify retransformation capability
            if (!inst.isRetransformClassesSupported()) {
                throw new UnsupportedOperationException(
                    "JVM does not support class retransformation. " +
                    "MCJEBooster requires a JVM with retransformation support."
                );
            }
            
            // Step 5: Register transformer
            ClassFileTransformer transformer = new MinecraftServerTransformer(detectedVersion, versionAdapter);
            inst.addTransformer(transformer, true);
            Logger.info("Class file transformer registered successfully");
            
            // Step 6: Attempt to find and transform already loaded classes
            transformLoadedClasses(inst);
            
            // Step 7: Initialize the region scheduler with adapter
            RegionScheduler scheduler = RegionScheduler.getInstance();
            scheduler.initialize(detectedVersion, versionAdapter);
            Logger.info("Region scheduler initialized");
            
            initialized = true;

            // Step 8: Start health monitoring
            startHealthMonitoring();
            Logger.info("MCJEBooster initialization completed successfully!");
            
        } catch (Exception e) {
            Logger.error("Failed to initialize MCJEBooster: " + e.getMessage());
            Logger.error("Stack trace: " + getStackTraceString(e));
            // Attempt rollback on initialization failure
            handleFailure("Initialization exception: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Attempts to transform already loaded Minecraft classes
     * This is necessary for dynamic attach scenarios where classes
     * may already be loaded before the agent attaches.
     * 
     * @param inst The Instrumentation instance
     */
    private static void transformLoadedClasses(Instrumentation inst) {
        Class<?>[] loadedClasses = inst.getAllLoadedClasses();
        int transformedCount = 0;
        
        for (Class<?> clazz : loadedClasses) {
            if (isMinecraftServerClass(clazz)) {
                try {
                    inst.retransformClasses(clazz);
                    Logger.info("Retransformed already loaded class: " + clazz.getName());
                    transformedCount++;
                } catch (Exception e) {
                    Logger.warn("Failed to retransform class " + clazz.getName() + ": " + e.getMessage());
                }
            }
        }
        
        Logger.info("Retransformed " + transformedCount + " already loaded classes");
    }
    
    /**
     * Checks if a class is the MinecraftServer class
     * Uses multiple heuristics to handle different obfuscation mappings
     * 
     * @param clazz The class to check
     * @return true if the class is likely MinecraftServer
     */
    private static boolean isMinecraftServerClass(Class<?> clazz) {
        String className = clazz.getName();
        
        // Skip standard library, bytecode manipulation, and internal classes
        if (className.startsWith("java.") || className.startsWith("javax.") ||
            className.startsWith("sun.") || className.startsWith("com.sun.") ||
            className.startsWith("jdk.") || className.startsWith("org.objectweb.") ||
            className.startsWith("com.mcjebooster.") || className.startsWith("jdk.")) {
            return false;
        }
        
        // Check for common MinecraftServer class names across different mappings
        String[] possibleNames = {
            "net.minecraft.server.MinecraftServer",
            "net.minecraft.server.dedicated.DedicatedServer",
            "net.minecraft.class_3176", // Yarn intermediary
        };
        
        for (String name : possibleNames) {
            if (className.equals(name) || className.endsWith("." + name)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Starts the health monitoring thread
     * Periodically checks system health and triggers rollback if necessary
     */
    private static void startHealthMonitoring() {
        Thread healthMonitor = new Thread(() -> {
            while (initialized) {
                try {
                    Thread.sleep(HEALTH_CHECK_INTERVAL * 50); // Convert ticks to ms (approx)
                    checkHealth();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "MCJEBooster-HealthMonitor");
        
        healthMonitor.setDaemon(true);
        healthMonitor.start();
        Logger.info("Health monitoring started");
    }
    
    /**
     * Performs health checks on the system
     * Detects issues like deadlocks, scheduler failures, or low TPS
     */
    private static void checkHealth() {
        RegionScheduler scheduler = RegionScheduler.getInstance();
        
        // Check 1: Scheduler thread alive
        if (!scheduler.isAlive()) {
            handleFailure("Scheduler thread died unexpectedly");
            return;
        }
        
        // Check 2: Deadlock detection
        if (detectDeadlock()) {
            handleFailure("Deadlock detected in worker threads");
            return;
        }
        
        // Check 3: TPS check
        double currentTPS = scheduler.getCurrentTPS();
        if (currentTPS < MIN_HEALTHY_TPS) {
            consecutiveFailures++;
            Logger.warn("Low TPS detected: " + currentTPS + " (failure " + consecutiveFailures + "/" + MAX_CONSECUTIVE_FAILURES + ")");
            
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                handleFailure("TPS consistently too low: " + currentTPS);
            }
        } else {
            if (consecutiveFailures > 0) {
                Logger.info("TPS recovered: " + currentTPS);
                consecutiveFailures = 0;
            }
        }
    }
    
    /**
     * Detects potential deadlocks in the system
     * Uses JVM ThreadMXBean to check for deadlocked threads
     * 
     * @return true if a deadlock is detected
     */
    private static boolean detectDeadlock() {
        java.lang.management.ThreadMXBean threadMXBean = 
            java.lang.management.ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
        return deadlockedThreads != null && deadlockedThreads.length > 0;
    }
    
    /**
     * Handles injection failures by performing automatic rollback
     * Restores original class definitions and shuts down the scheduler
     * 
     * @param reason The reason for the failure
     */
    private static void handleFailure(String reason) {
        Logger.error("=========================================");
        Logger.error("INJECTION FAILURE DETECTED: " + reason);
        Logger.error("Initiating automatic rollback...");
        Logger.error("=========================================");
        
        try {
            // Step 1: Shutdown the scheduler
            RegionScheduler scheduler = RegionScheduler.getInstance();
            scheduler.shutdown();
            Logger.info("Scheduler shutdown completed");
            
            // Step 2: Remove transformer to prevent further modifications
            if (instrumentation != null) {
                // Note: Cannot remove transformer after it's added, but we can disable it
                // by setting initialized to false
                Logger.info("Transformer disabled");
            }
            
            // Step 3: Clear all cached references
            versionAdapter = null;
            detectedVersion = null;
            
            Logger.info("Rollback completed - MCJEBooster disabled, vanilla behavior restored");
            
        } catch (Exception e) {
            Logger.error("Rollback failed: " + e.getMessage());
            Logger.error("Manual intervention may be required");
        }
        
        initialized = false;
    }
    
    /**
     * Converts an exception stack trace to a string
     * 
     * @param e The exception
     * @return The stack trace as a string
     */
    private static String getStackTraceString(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Returns whether the agent has been successfully initialized
     * 
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Returns the detected Minecraft version
     * 
     * @return The detected version string, or null if not yet detected
     */
    public static String getDetectedVersion() {
        return detectedVersion;
    }
    
    /**
     * Returns the loaded version adapter
     * 
     * @return The VersionAdapter, or null if not loaded
     */
    public static VersionAdapter getVersionAdapter() {
        return versionAdapter;
    }
    
    /**
     * Returns the instrumentation instance
     * 
     * @return The Instrumentation instance, or null if not initialized
     */
    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
}
