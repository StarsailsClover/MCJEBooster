/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.scheduler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.mcjebooster.util.Logger;

/**
 * 热点任务注册中心
 * 
 * 管理所有可并行化的 Minecraft 工作负载任务，用于在热点 region 上执行。
 * 
 * @author StarsailsClover
 * @version 26.6-20260714
 */
public class HotspotTaskRegistry {
    
    private static final Map<String, Function<RegionScheduler.Region, Runnable>> TASK_FACTORIES = 
        new ConcurrentHashMap<>();
    
    private static final Set<String> ENABLED_TASKS = ConcurrentHashMap.newKeySet();
    
    static {
        // 注册默认任务
        registerTask("entity-density", EntityDensityAnalyzer::createTask);
        registerTask("chunk-activity", ChunkActivityPredictor::createTask);
        
        // 默认启用所有任务
        enableTask("entity-density");
        enableTask("chunk-activity");
    }
    
    /**
     * 注册一个新的热点任务
     * 
     * @param taskId 任务唯一标识
     * @param factory 任务工厂函数
     */
    public static void registerTask(String taskId, Function<RegionScheduler.Region, Runnable> factory) {
        TASK_FACTORIES.put(taskId, factory);
        Logger.info("[HotspotTaskRegistry] Registered task: " + taskId);
    }
    
    /**
     * 启用指定的热点任务
     */
    public static void enableTask(String taskId) {
        if (TASK_FACTORIES.containsKey(taskId)) {
            ENABLED_TASKS.add(taskId);
            Logger.debug("[HotspotTaskRegistry] Enabled task: " + taskId);
        }
    }
    
    /**
     * 禁用指定的热点任务
     */
    public static void disableTask(String taskId) {
        ENABLED_TASKS.remove(taskId);
        Logger.debug("[HotspotTaskRegistry] Disabled task: " + taskId);
    }
    
    /**
     * 获取所有已启用任务的工厂列表
     */
    public static List<Function<RegionScheduler.Region, Runnable>> getEnabledTaskFactories() {
        List<Function<RegionScheduler.Region, Runnable>> factories = new ArrayList<>();
        for (String taskId : ENABLED_TASKS) {
            Function<RegionScheduler.Region, Runnable> factory = TASK_FACTORIES.get(taskId);
            if (factory != null) {
                factories.add(factory);
            }
        }
        return factories;
    }
    
    /**
     * 为指定 region 创建所有已启用任务
     */
    public static List<Runnable> createTasksForRegion(RegionScheduler.Region region) {
        List<Runnable> tasks = new ArrayList<>();
        for (Function<RegionScheduler.Region, Runnable> factory : getEnabledTaskFactories()) {
            try {
                Runnable task = factory.apply(region);
                if (task != null) {
                    tasks.add(task);
                }
            } catch (Exception e) {
                Logger.warn("[HotspotTaskRegistry] Failed to create task: " + e.getMessage());
            }
        }
        return tasks;
    }
}
