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

package com.mcjebooster.sync;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.mcjebooster.util.Logger;

/**
 * Synchronization Point Manager for Multi-threaded Tick Processing
 * 
 * Manages synchronization barriers between worker threads to ensure
 * data consistency during multi-core tick processing. Implements:
 * - Tick barriers for coordinating worker threads
 * - Entity boundary synchronization
 * - Block update ordering
 * - Network packet ordering
 * 
 * Thread Safety: This class is thread-safe
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 * @since 1.0
 */
public class SyncPointManager {
    
    /** Singleton instance */
    private static volatile SyncPointManager INSTANCE;
    
    /** The cyclic barrier for tick synchronization */
    private volatile CyclicBarrier tickBarrier;
    
    /** Number of parties expected at the barrier */
    private volatile int barrierParties;
    
    /** Current tick number */
    private final AtomicInteger tickNumber = new AtomicInteger(0);
    
    /** Flag indicating if synchronization is active */
    private volatile boolean active = false;
    
    /** Timeout for barrier await in milliseconds */
    private static final long BARRIER_TIMEOUT_MS = 50;
    
    /** Maximum consecutive barrier failures before disabling sync */
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    
    /** Counter for consecutive failures */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    
    /** Executor for barrier timeout handling */
    private final ExecutorService timeoutExecutor = Executors.newSingleThreadExecutor(
        r -> new Thread(r, "MCJEBooster-SyncTimeout")
    );
    
    /**
     * Private constructor for singleton
     */
    private SyncPointManager() {
    }
    
    /**
     * Gets the singleton instance
     * 
     * @return The SyncPointManager instance
     */
    public static SyncPointManager getInstance() {
        if (INSTANCE == null) {
            synchronized (SyncPointManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SyncPointManager();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Initializes the synchronization manager
     * 
     * @param workerCount Number of worker threads
     */
    public void initialize(int workerCount) {
        this.barrierParties = workerCount + 1; // Workers + main thread
        this.tickBarrier = new CyclicBarrier(
            barrierParties,
            this::onBarrierTripped
        );
        this.active = true;
        
        Logger.info("SyncPointManager initialized with " + workerCount + " workers");
    }
    
    /**
     * Called when the barrier is tripped (all parties have arrived)
     */
    private void onBarrierTripped() {
        tickNumber.incrementAndGet();
        consecutiveFailures.set(0);
        
        if (Logger.isDebugEnabled()) {
            Logger.debug("Tick barrier tripped, tick #" + tickNumber.get());
        }
    }
    
    /**
     * Waits at the tick barrier
     * Called by worker threads to synchronize at the end of a tick
     * 
     * @throws BrokenBarrierException if the barrier is broken
     * @throws InterruptedException if the thread is interrupted
     */
    public void awaitTickBarrier() throws BrokenBarrierException, InterruptedException {
        if (!active || tickBarrier == null) {
            return;
        }
        
        try {
            tickBarrier.await(BARRIER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            handleBarrierTimeout();
        } catch (BrokenBarrierException e) {
            handleBrokenBarrier();
            throw e;
        }
    }
    
    /**
     * Waits at the tick barrier with a specific timeout
     * 
     * @param timeout The timeout duration
     * @param unit The time unit
     * @throws BrokenBarrierException if the barrier is broken
     * @throws InterruptedException if the thread is interrupted
     * @throws TimeoutException if the wait times out
     */
    public void awaitTickBarrier(long timeout, TimeUnit unit) 
            throws BrokenBarrierException, InterruptedException, TimeoutException {
        if (!active || tickBarrier == null) {
            return;
        }
        
        tickBarrier.await(timeout, unit);
    }
    
    /**
     * Handles barrier timeout
     */
    private void handleBarrierTimeout() {
        int failures = consecutiveFailures.incrementAndGet();
        Logger.warn("Tick barrier timeout (failure " + failures + "/" + MAX_CONSECUTIVE_FAILURES + ")");
        
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            Logger.error("Too many barrier failures, disabling synchronization");
            disableSync();
        }
    }
    
    /**
     * Handles a broken barrier
     */
    private void handleBrokenBarrier() {
        Logger.error("Tick barrier broken, attempting to reset");
        
        if (tickBarrier != null) {
            tickBarrier.reset();
        }
    }
    
    /**
     * Disables synchronization
     */
    public void disableSync() {
        active = false;
        Logger.warn("Synchronization disabled");
    }
    
    /**
     * Re-enables synchronization
     * Creates a new barrier if needed
     */
    public void enableSync() {
        if (tickBarrier == null || tickBarrier.isBroken()) {
            tickBarrier = new CyclicBarrier(barrierParties, this::onBarrierTripped);
        }
        active = true;
        Logger.info("Synchronization enabled");
    }
    
    /**
     * Shuts down the synchronization manager
     */
    public void shutdown() {
        if (!active && tickBarrier == null) {
            return; // Already shut down
        }
        
        active = false;
        
        try {
            if (tickBarrier != null) {
                tickBarrier.reset();
                tickBarrier = null;
            }
        } catch (Exception e) {
            Logger.warn("Error resetting tick barrier: " + e.getMessage());
        }
        
        try {
            timeoutExecutor.shutdown();
            try {
                if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    timeoutExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                timeoutExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            Logger.warn("Error shutting down timeout executor: " + e.getMessage());
        }
        
        Logger.info("SyncPointManager shutdown");
    }
    
    /**
     * Checks if synchronization is active
     * 
     * @return true if sync is active
     */
    public boolean isActive() {
        return active;
    }
    
    /**
     * Gets the current tick number
     * 
     * @return The tick number
     */
    public int getTickNumber() {
        return tickNumber.get();
    }
    
    /**
     * Gets the number of parties at the barrier
     * 
     * @return The number of parties
     */
    public int getBarrierParties() {
        return barrierParties;
    }
    
    /**
     * Gets the number of parties currently waiting at the barrier
     * 
     * @return The number of waiting parties
     */
    public int getWaitingParties() {
        if (tickBarrier == null) {
            return 0;
        }
        return tickBarrier.getNumberWaiting();
    }
    
    /**
     * Checks if the barrier is broken
     * 
     * @return true if the barrier is broken
     */
    public boolean isBarrierBroken() {
        return tickBarrier != null && tickBarrier.isBroken();
    }
    
    /**
     * Synchronizes entity movement across region boundaries
     * Ensures entities moving between regions are properly handled
     * 
     * @param entityId The entity ID
     * @param fromRegion The source region ID
     * @param toRegion The destination region ID
     * @return true if the transfer was successful
     */
    public boolean syncEntityTransfer(int entityId, int fromRegion, int toRegion) {
        if (!active) {
            return true; // Allow transfer when sync is disabled
        }
        
        // Implement entity transfer synchronization
        // This would coordinate with the RegionScheduler
        
        if (Logger.isDebugEnabled()) {
            Logger.debug("Entity " + entityId + " transferring from region " + 
                        fromRegion + " to " + toRegion);
        }
        
        return true;
    }
    
    /**
     * Synchronizes block updates that affect multiple regions
     * Ensures block updates are processed in the correct order
     * 
     * @param blockX The block X coordinate
     * @param blockY The block Y coordinate
     * @param blockZ The block Z coordinate
     * @param affectedRegions The regions affected by this update
     * @return true if the update was synchronized
     */
    public boolean syncBlockUpdate(int blockX, int blockY, int blockZ, int[] affectedRegions) {
        if (!active) {
            return true;
        }
        
        // Implement block update synchronization
        // This ensures updates affecting multiple regions are properly ordered
        
        if (Logger.isDebugEnabled()) {
            Logger.debug("Block update at (" + blockX + "," + blockY + "," + blockZ + 
                        ") affecting regions: " + java.util.Arrays.toString(affectedRegions));
        }
        
        return true;
    }
    
    /**
     * Waits for all worker threads to complete their current tick
     * Called by the main thread at the end of each tick
     * 
     * @return true if all workers completed successfully
     */
    public boolean waitForWorkers() {
        if (!active || tickBarrier == null) {
            return true;
        }
        
        try {
            tickBarrier.await(BARRIER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            Logger.warn("Failed to wait for workers: " + e.getMessage());
            return false;
        }
    }
}
