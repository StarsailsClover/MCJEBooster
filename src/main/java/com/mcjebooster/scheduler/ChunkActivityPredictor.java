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
 * 区块活跃度预测器
 * 
 * 预测热点 region 内区块的活跃度，用于优化区块加载和卸载策略。
 * 这是一个只读分析任务，不会修改游戏状态。
 * 
 * @author StarsailsClover
 * @version 26.6-20260714
 */
public class ChunkActivityPredictor {
    
    /** 每个 region 的活跃度预测统计 */
    private static final ConcurrentHashMap<Integer, ActivityStats> ACTIVITY_STATS = 
        new ConcurrentHashMap<>();
    
    /** 预测次数统计 */
    private static final AtomicLong PREDICTION_COUNT = new AtomicLong(0);
    
    /**
     * 活跃度统计数据结构
     */
    public static class ActivityStats {
        public final int regionId;
        public final double activityScore; // 0.0 - 1.0
        public final boolean isHot; // 是否高活跃度
        public final long timestamp;
        
        public ActivityStats(int regionId, double activityScore, boolean isHot) {
            this.regionId = regionId;
            this.activityScore = activityScore;
            this.isHot = isHot;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * 为指定 region 创建活跃度预测任务
     */
    public static Runnable createTask(RegionScheduler.Region region) {
        return () -> {
            long startTime = System.nanoTime();
            
            try {
                // 基于多个指标计算活跃度分数
                double smoothedLoad = region.getSmoothedLoad();
                double peakLoad = region.getPeakLoad();
                double instantLoad = region.getInstantLoad();
                double neighborPredicted = region.getNeighborPredictedLoad();
                
                // 综合活跃度分数计算
                // 权重：平滑负载 40%，峰值负载 20%，即时负载 25%，邻域预测 15%
                double activityScore = 
                    (smoothedLoad * 0.4) +
                    (peakLoad * 0.2) +
                    (instantLoad * 0.25) +
                    (neighborPredicted * 0.15);
                
                // 归一化到 0.0 - 1.0 范围
                activityScore = Math.min(1.0, activityScore / 1000.0);
                
                // 判断是否为高活跃度
                boolean isHot = activityScore > 0.7;
                
                // 存储统计结果
                ActivityStats stats = new ActivityStats(region.getId(), activityScore, isHot);
                ACTIVITY_STATS.put(region.getId(), stats);
                
                PREDICTION_COUNT.incrementAndGet();
                
                long elapsed = System.nanoTime() - startTime;
                Logger.debug("[ChunkActivityPredictor] Region " + region.getId() + 
                    ": score=" + String.format("%.3f", activityScore) + 
                    ", hot=" + isHot + 
                    ", time=" + (elapsed / 1_000_000.0) + "ms");
                
            } catch (Exception e) {
                Logger.warn("[ChunkActivityPredictor] Prediction failed for region " + 
                    region.getId() + ": " + e.getMessage());
            }
        };
    }
    
    /**
     * 获取指定 region 的活跃度统计
     */
    public static ActivityStats getStats(int regionId) {
        return ACTIVITY_STATS.get(regionId);
    }
    
    /**
     * 获取所有 region 的活跃度统计
     */
    public static ConcurrentHashMap<Integer, ActivityStats> getAllStats() {
        return ACTIVITY_STATS;
    }
    
    /**
     * 获取总预测次数
     */
    public static long getPredictionCount() {
        return PREDICTION_COUNT.get();
    }
    
    /**
     * 清空统计数据
     */
    public static void clear() {
        ACTIVITY_STATS.clear();
        PREDICTION_COUNT.set(0);
    }
}
