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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.mcjebooster.util.Logger;

/**
 * 实体密度分析器
 * 
 * 分析热点 region 内的实体分布密度，用于优化实体处理调度。
 * 这是一个只读分析任务，不会修改游戏状态。
 * 
 * @author StarsailsClover
 * @version 26.6-20260714
 */
public class EntityDensityAnalyzer {
    
    /** 每个 region 的实体密度统计 */
    private static final ConcurrentHashMap<Integer, DensityStats> DENSITY_STATS = 
        new ConcurrentHashMap<>();
    
    /** 分析次数统计 */
    private static final AtomicLong ANALYSIS_COUNT = new AtomicLong(0);
    
    /**
     * 密度统计数据结构
     */
    public static class DensityStats {
        public final int regionId;
        public final int entityCount;
        public final double density; // 每区块平均实体数
        public final long timestamp;
        
        public DensityStats(int regionId, int entityCount, double density) {
            this.regionId = regionId;
            this.entityCount = entityCount;
            this.density = density;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * 为指定 region 创建密度分析任务
     */
    public static Runnable createTask(RegionScheduler.Region region) {
        return () -> {
            long startTime = System.nanoTime();
            
            try {
                // 从 region 获取实体数量信息
                int entityCount = estimateEntityCount(region);
                
                // 计算密度（实体数 / 区块数）
                int chunkCount = region.getMaxX() - region.getMinX();
                chunkCount *= region.getMaxZ() - region.getMinZ();
                chunkCount = Math.max(1, chunkCount);
                
                double density = (double) entityCount / chunkCount;
                
                // 存储统计结果
                DensityStats stats = new DensityStats(region.getId(), entityCount, density);
                DENSITY_STATS.put(region.getId(), stats);
                
                ANALYSIS_COUNT.incrementAndGet();
                
                long elapsed = System.nanoTime() - startTime;
                Logger.debug("[EntityDensityAnalyzer] Region " + region.getId() + 
                    ": entities=" + entityCount + ", density=" + String.format("%.2f", density) + 
                    ", time=" + (elapsed / 1_000_000.0) + "ms");
                
            } catch (Exception e) {
                Logger.warn("[EntityDensityAnalyzer] Analysis failed for region " + 
                    region.getId() + ": " + e.getMessage());
            }
        };
    }
    
    /**
     * 估算 region 内的实体数量
     * 基于 region 的负载值进行估算
     */
    private static int estimateEntityCount(RegionScheduler.Region region) {
        // 使用 region 的平滑负载作为实体数量的近似值
        // 实际实现中应该从 Minecraft 服务器获取真实数据
        double load = region.getSmoothedLoad();
        return (int) Math.round(load);
    }
    
    /**
     * 获取指定 region 的密度统计
     */
    public static DensityStats getStats(int regionId) {
        return DENSITY_STATS.get(regionId);
    }
    
    /**
     * 获取所有 region 的密度统计
     */
    public static ConcurrentHashMap<Integer, DensityStats> getAllStats() {
        return DENSITY_STATS;
    }
    
    /**
     * 获取总分析次数
     */
    public static long getAnalysisCount() {
        return ANALYSIS_COUNT.get();
    }
    
    /**
     * 清空统计数据
     */
    public static void clear() {
        DENSITY_STATS.clear();
        ANALYSIS_COUNT.set(0);
    }
}
