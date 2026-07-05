/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.core;

import com.mcjebooster.util.Logger;

import java.util.concurrent.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core Task Scheduler for MCJEBooster.
 * 
 * Implements a multi-level task scheduling system:
 * - Critical Tasks: Must execute on main thread (world state modifications)
 * - Parallel Tasks: Can execute on worker threads (entity ticking, block updates)
 * - Background Tasks: Low priority, execute when resources available
 * 
 * This is the central component that breaks Minecraft's single-threaded
 * bottleneck by distributing tasks across multiple CPU cores.
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 */
public class TaskScheduler {
    
    /** Singleton instance */
    private static volatile TaskScheduler INSTANCE;
    
    /** Thread pool for parallel task execution */
    private ForkJoinPool workerPool;
    
    /** Number of worker threads */
    private int workerCount;
    
    /** Executor for critical tasks (main thread) */
    private Executor mainThreadExecutor;
    
    /** Task queues by priority */
    private final BlockingQueue<Task> criticalTaskQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Task> parallelTaskQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Task> backgroundTaskQueue = new LinkedBlockingQueue<>();
    
    /** Active task tracking */
    private final Map<String, Task> activeTasks = new ConcurrentHashMap<>();
    
    /** Task statistics */
    private final AtomicLong taskCounter = new AtomicLong(0);
    private final AtomicLong completedTasks = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);
    
    /** Scheduler state */
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    
    /** Performance monitoring */
    private final PerformanceMonitor performanceMonitor = new PerformanceMonitor();
    
    /** Task dependency graph */
    private final TaskDependencyGraph dependencyGraph = new TaskDependencyGraph();
    
    /**
     * Task types with different execution characteristics
     */
    public enum TaskType {
        CRITICAL,    // Must run on main thread (world state changes)
        PARALLEL,    // Can run on worker threads (entity ticking)
        BACKGROUND   // Low priority (chunk loading, compression)
    }
    
    /**
     * Task priority levels
     */
    public enum TaskPriority {
        HIGHEST(0),
        HIGH(1),
        NORMAL(2),
        LOW(3),
        LOWEST(4);
        
        private final int level;
        
        TaskPriority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    /**
     * Represents a scheduled task
     */
    public static class Task {
        private final String id;
        private final String name;
        private final TaskType type;
        private final TaskPriority priority;
        private final Runnable action;
        private final Set<String> dependencies;
        private final long submitTime;
        private volatile long startTime;
        private volatile long endTime;
        private volatile TaskState state;
        private volatile Throwable error;
        
        public Task(String name, TaskType type, TaskPriority priority, 
                   Runnable action, Set<String> dependencies) {
            this.id = UUID.randomUUID().toString();
            this.name = name;
            this.type = type;
            this.priority = priority;
            this.action = action;
            this.dependencies = dependencies != null ? dependencies : new HashSet<>();
            this.submitTime = System.nanoTime();
            this.state = TaskState.PENDING;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public TaskType getType() { return type; }
        public TaskPriority getPriority() { return priority; }
        public TaskState getState() { return state; }
        public Set<String> getDependencies() { return dependencies; }
        
        public long getExecutionTimeNanos() {
            if (endTime > 0 && startTime > 0) {
                return endTime - startTime;
            }
            return 0;
        }
        
        public long getWaitTimeNanos() {
            if (startTime > 0) {
                return startTime - submitTime;
            }
            return System.nanoTime() - submitTime;
        }
        
        void setState(TaskState state) { this.state = state; }
        void setStartTime(long time) { this.startTime = time; }
        void setEndTime(long time) { this.endTime = time; }
        void setError(Throwable error) { this.error = error; }
        
        void execute() {
            setState(TaskState.RUNNING);
            setStartTime(System.nanoTime());
            try {
                action.run();
                setState(TaskState.COMPLETED);
            } catch (Exception e) {
                setState(TaskState.FAILED);
                setError(e);
                throw e;
            } finally {
                setEndTime(System.nanoTime());
            }
        }
    }
    
    /**
     * Task execution states
     */
    public enum TaskState {
        PENDING,    // Waiting in queue
        RUNNING,    // Currently executing
        COMPLETED,  // Successfully finished
        FAILED,     // Execution failed
        CANCELLED   // Cancelled before execution
    }
    
    /**
     * Private constructor
     */
    private TaskScheduler() {
    }
    
    /**
     * Gets the singleton instance
     */
    public static TaskScheduler getInstance() {
        if (INSTANCE == null) {
            synchronized (TaskScheduler.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TaskScheduler();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Initializes the task scheduler
     * 
     * @param workerCount Number of worker threads
     * @param mainThreadExecutor Executor for critical tasks
     */
    public void initialize(int workerCount, Executor mainThreadExecutor) {
        if (running.get()) {
            Logger.warn("TaskScheduler already initialized");
            return;
        }
        
        this.workerCount = workerCount;
        this.mainThreadExecutor = mainThreadExecutor;
        
        // Create worker pool with custom thread factory
        this.workerPool = new ForkJoinPool(
            workerCount,
            new WorkerThreadFactory(),
            new WorkerExceptionHandler(),
            true
        );
        
        running.set(true);
        shutdown.set(false);
        
        // Start task dispatch threads
        startDispatchers();
        
        Logger.info("TaskScheduler initialized with " + workerCount + " workers");
    }
    
    /**
     * Submits a task for execution
     * 
     * @param name Task name
     * @param type Task type
     * @param priority Task priority
     * @param action Task action
     * @return The submitted task
     */
    public Task submit(String name, TaskType type, TaskPriority priority, Runnable action) {
        return submit(name, type, priority, action, null);
    }
    
    /**
     * Submits a task with dependencies
     * 
     * @param name Task name
     * @param type Task type
     * @param priority Task priority
     * @param action Task action
     * @param dependencies Task dependencies (task IDs that must complete first)
     * @return The submitted task
     */
    public Task submit(String name, TaskType type, TaskPriority priority, 
                      Runnable action, Set<String> dependencies) {
        if (shutdown.get()) {
            throw new IllegalStateException("TaskScheduler is shutting down");
        }
        
        Task task = new Task(name, type, priority, action, dependencies);
        
        // Register in dependency graph
        if (dependencies != null && !dependencies.isEmpty()) {
            dependencyGraph.addTask(task);
        }
        
        // Add to appropriate queue
        switch (type) {
            case CRITICAL:
                criticalTaskQueue.offer(task);
                break;
            case PARALLEL:
                parallelTaskQueue.offer(task);
                break;
            case BACKGROUND:
                backgroundTaskQueue.offer(task);
                break;
        }
        
        activeTasks.put(task.getId(), task);
        taskCounter.incrementAndGet();
        
        if (Logger.isDebugEnabled()) {
            Logger.debug("Submitted task: " + name + " [" + type + ", " + priority + "]");
        }
        
        return task;
    }
    
    /**
     * Executes all tasks for a game tick
     * Called from the injected tick method
     * 
     * @param timeoutMs Maximum time to wait for tasks
     * @return true if all tasks completed within timeout
     */
    public boolean executeTickTasks(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        
        // 1. Execute critical tasks on main thread
        executeCriticalTasks();
        
        // 2. Submit parallel tasks to worker pool
        List<Future<?>> parallelFutures = submitParallelTasks();
        
        // 3. Wait for parallel tasks to complete
        boolean completed = waitForTasks(parallelFutures, deadline - System.currentTimeMillis());
        
        // 4. Update statistics
        performanceMonitor.recordTick(System.currentTimeMillis() - (deadline - timeoutMs));
        
        return completed;
    }
    
    /**
     * Executes critical tasks on main thread
     */
    private void executeCriticalTasks() {
        List<Task> tasks = new ArrayList<>();
        criticalTaskQueue.drainTo(tasks);
        
        // Sort by priority
        tasks.sort(Comparator.comparingInt(t -> t.getPriority().getLevel()));
        
        for (Task task : tasks) {
            if (canExecute(task)) {
                try {
                    task.execute();
                    completedTasks.incrementAndGet();
                } catch (Exception e) {
                    Logger.error("Critical task failed: " + task.getName(), e);
                    failedTasks.incrementAndGet();
                }
            }
        }
    }
    
    /**
     * Submits parallel tasks to worker pool
     */
    private List<Future<?>> submitParallelTasks() {
        List<Task> tasks = new ArrayList<>();
        parallelTaskQueue.drainTo(tasks);
        
        // Sort by priority
        tasks.sort(Comparator.comparingInt(t -> t.getPriority().getLevel()));
        
        List<Future<?>> futures = new ArrayList<>();
        
        for (Task task : tasks) {
            if (canExecute(task)) {
                Future<?> future = workerPool.submit(() -> {
                    try {
                        task.execute();
                        completedTasks.incrementAndGet();
                        dependencyGraph.completeTask(task.getId());
                    } catch (Exception e) {
                        Logger.error("Parallel task failed: " + task.getName(), e);
                        failedTasks.incrementAndGet();
                    }
                });
                futures.add(future);
            }
        }
        
        return futures;
    }
    
    /**
     * Checks if a task can execute (dependencies satisfied)
     */
    private boolean canExecute(Task task) {
        if (task.getDependencies().isEmpty()) {
            return true;
        }
        
        for (String depId : task.getDependencies()) {
            Task dep = activeTasks.get(depId);
            if (dep == null || dep.getState() != TaskState.COMPLETED) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Waits for all futures to complete
     */
    private boolean waitForTasks(List<Future<?>> futures, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        
        for (Future<?> future : futures) {
            try {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return false;
                }
                future.get(remaining, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Logger.warn("Task execution error: " + e.getMessage());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Starts task dispatcher threads
     */
    private void startDispatchers() {
        // Background task dispatcher
        Thread bgDispatcher = new Thread(this::backgroundTaskDispatcher, "MCJEBooster-BGDispatcher");
        bgDispatcher.setDaemon(true);
        bgDispatcher.start();
    }
    
    /**
     * Background task dispatcher loop
     */
    private void backgroundTaskDispatcher() {
        while (running.get() && !shutdown.get()) {
            try {
                Task task = backgroundTaskQueue.poll(100, TimeUnit.MILLISECONDS);
                if (task != null && canExecute(task)) {
                    workerPool.submit(() -> {
                        try {
                            task.execute();
                            completedTasks.incrementAndGet();
                        } catch (Exception e) {
                            Logger.error("Background task failed: " + task.getName(), e);
                            failedTasks.incrementAndGet();
                        }
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Shuts down the task scheduler
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return; // Already shutting down
        }
        
        running.set(false);
        
        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                    // Wait a bit more for forced shutdown
                    workerPool.awaitTermination(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Clear all queues
        criticalTaskQueue.clear();
        parallelTaskQueue.clear();
        backgroundTaskQueue.clear();
        activeTasks.clear();
        
        Logger.info("TaskScheduler shutdown");
    }
    
    /**
     * Gets task statistics
     */
    public TaskStatistics getStatistics() {
        return new TaskStatistics(
            taskCounter.get(),
            completedTasks.get(),
            failedTasks.get(),
            activeTasks.size(),
            criticalTaskQueue.size(),
            parallelTaskQueue.size(),
            backgroundTaskQueue.size(),
            performanceMonitor.getAverageExecutionTime(),
            performanceMonitor.getAverageWaitTime()
        );
    }
    
    /**
     * Task statistics container
     */
    public static class TaskStatistics {
        public final long totalSubmitted;
        public final long completed;
        public final long failed;
        public final long active;
        public final long criticalQueued;
        public final long parallelQueued;
        public final long backgroundQueued;
        public final double avgExecutionTimeMs;
        public final double avgWaitTimeMs;
        
        public TaskStatistics(long totalSubmitted, long completed, long failed,
                             long active, long criticalQueued, long parallelQueued,
                             long backgroundQueued, double avgExecutionTimeMs,
                             double avgWaitTimeMs) {
            this.totalSubmitted = totalSubmitted;
            this.completed = completed;
            this.failed = failed;
            this.active = active;
            this.criticalQueued = criticalQueued;
            this.parallelQueued = parallelQueued;
            this.backgroundQueued = backgroundQueued;
            this.avgExecutionTimeMs = avgExecutionTimeMs;
            this.avgWaitTimeMs = avgWaitTimeMs;
        }
    }
    
    /**
     * Custom thread factory for worker threads
     */
    private static class WorkerThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        private final AtomicLong counter = new AtomicLong(0);
        
        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            ForkJoinWorkerThread thread = 
                ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName("MCJEBooster-Worker-" + counter.incrementAndGet());
            return thread;
        }
    }
    
    /**
     * Exception handler for worker threads
     */
    private static class WorkerExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            Logger.error("Uncaught exception in worker thread " + t.getName(), e);
        }
    }
    
    /**
     * Task dependency graph
     */
    private static class TaskDependencyGraph {
        private final Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> dependents = new ConcurrentHashMap<>();
        
        void addTask(Task task) {
            dependencies.put(task.getId(), new HashSet<>(task.getDependencies()));
            
            for (String dep : task.getDependencies()) {
                dependents.computeIfAbsent(dep, k -> new HashSet<>()).add(task.getId());
            }
        }
        
        void completeTask(String taskId) {
            Set<String> deps = dependents.get(taskId);
            if (deps != null) {
                for (String dependent : deps) {
                    Set<String> remaining = dependencies.get(dependent);
                    if (remaining != null) {
                        remaining.remove(taskId);
                    }
                }
            }
        }
    }
    
    /**
     * Performance monitoring
     */
    private static class PerformanceMonitor {
        private final List<Long> executionTimes = new ArrayList<>();
        private final List<Long> waitTimes = new ArrayList<>();
        private final int maxSamples = 1000;
        
        synchronized void recordTick(long durationMs) {
            executionTimes.add(durationMs);
            if (executionTimes.size() > maxSamples) {
                executionTimes.remove(0);
            }
        }
        
        synchronized double getAverageExecutionTime() {
            if (executionTimes.isEmpty()) return 0;
            return executionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        }
        
        synchronized double getAverageWaitTime() {
            if (waitTimes.isEmpty()) return 0;
            return waitTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        }
    }
}
