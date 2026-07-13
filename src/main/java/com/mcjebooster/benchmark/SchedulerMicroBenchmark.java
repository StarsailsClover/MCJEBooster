package com.mcjebooster.benchmark;

import java.util.ArrayList;
import java.util.List;

import com.mcjebooster.agent.InjectionBridge;
import com.mcjebooster.scheduler.RegionScheduler;

public class SchedulerMicroBenchmark {
    private static volatile long sink;

    public static void main(String[] args) {
        int taskCount = getInt(args, 0, 64);
        int iterations = getInt(args, 1, 2_000_000);
        int rounds = getInt(args, 2, 5);
        int bridgeTicks = getInt(args, 3, 0);
        int simulatedEntities = getInt(args, 4, 0);
        boolean hotspotMode = getBool(args, 5, false);

        RegionScheduler scheduler = RegionScheduler.getInstance();
        scheduler.initialize("benchmark", null);

        List<Runnable> tasks = createTasks(taskCount, iterations);
        runSerial(tasks);
        scheduler.runSafeRegionTasks(tasks, 120_000);

        long bestSerial = Long.MAX_VALUE;
        long bestParallel = Long.MAX_VALUE;
        RegionScheduler.SafeExecutionStats bestStats = null;

        for (int i = 0; i < rounds; i++) {
            long serial = runSerial(tasks);
            RegionScheduler.SafeExecutionStats stats = scheduler.runSafeRegionTasks(tasks, 120_000);
            bestSerial = Math.min(bestSerial, serial);
            if (stats.getFailureCount() == 0 && stats.getWallNanos() < bestParallel) {
                bestParallel = stats.getWallNanos();
                bestStats = stats;
            }
        }

        Object bridgeServer = simulatedEntities > 0 ? (hotspotMode ? createHotspotServer(simulatedEntities) : createServer(simulatedEntities)) : null;
        for (int i = 0; i < bridgeTicks; i++) {
            InjectionBridge.tickRegions(bridgeServer);
        }

        double peakLoad = scheduler.getPeakRegionLoad();
        double avgLoad = scheduler.getAverageRegionLoad();
        long hotRegions = scheduler.getHotRegionCount();

        scheduler.shutdown();

        double speedup = bestSerial / (double) bestParallel;
        double serialMs = bestSerial / 1_000_000.0;
        double parallelMs = bestParallel / 1_000_000.0;
        double ratio = bestStats == null ? 0.0 : bestStats.getParallelismRatio();

        System.out.println(
            "MCJEBoosterBenchmark " +
            "tasks=" + taskCount + " " +
            "iterations=" + iterations + " " +
            "rounds=" + rounds + " " +
            "bridgeTicks=" + bridgeTicks + " " +
            "simulatedEntities=" + simulatedEntities + " " +
            "hotspotMode=" + hotspotMode + " " +
            "workers=" + (bestStats == null ? 0 : bestStats.getWorkerCount()) + " " +
            "serialMs=" + format(serialMs) + " " +
            "parallelMs=" + format(parallelMs) + " " +
            "speedup=" + format(speedup) + " " +
            "parallelismRatio=" + format(ratio) + " " +
            "peakLoad=" + format(peakLoad) + " " +
            "avgLoad=" + format(avgLoad) + " " +
            "hotRegions=" + hotRegions + " " +
            "sink=" + sink
        );
    }

    private static ServerFixture createServer(int entityCount) {
        ServerFixture server = new ServerFixture();
        LevelFixture level = new LevelFixture();
        server.levels.add(level);

        for (int i = 0; i < entityCount; i++) {
            double x = ((i % 128) - 64) * 4.0;
            double z = (((i / 128) % 128) - 64) * 4.0;
            level.entities.add(new EntityFixture(x, z));
        }

        int blockEntities = Math.max(1, entityCount / 8);
        for (int i = 0; i < blockEntities; i++) {
            int x = ((i % 64) - 32) * 16;
            int z = (((i / 64) % 64) - 32) * 16;
            level.blockEntities.add(new BlockEntityFixture(new BlockPosFixture(x, z)));
        }

        return server;
    }

    private static ServerFixture createHotspotServer(int entityCount) {
        ServerFixture server = new ServerFixture();
        LevelFixture level = new LevelFixture();
        server.levels.add(level);

        java.util.Random random = new java.util.Random(42L);
        int hotRadiusBlocks = 48;
        for (int i = 0; i < entityCount; i++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double radius = Math.sqrt(random.nextDouble()) * hotRadiusBlocks;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            level.entities.add(new EntityFixture(x, z));
        }

        int blockEntities = Math.max(1, entityCount / 4);
        for (int i = 0; i < blockEntities; i++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double radius = Math.sqrt(random.nextDouble()) * (hotRadiusBlocks * 0.7);
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);
            level.blockEntities.add(new BlockEntityFixture(new BlockPosFixture(x, z)));
        }

        return server;
    }

    private static List<Runnable> createTasks(int taskCount, int iterations) {
        List<Runnable> tasks = new ArrayList<>(taskCount);
        for (int i = 0; i < taskCount; i++) {
            final int seed = i + 1;
            tasks.add(() -> sink ^= compute(seed, iterations));
        }
        return tasks;
    }

    private static long runSerial(List<Runnable> tasks) {
        long start = System.nanoTime();
        for (Runnable task : tasks) {
            task.run();
        }
        return System.nanoTime() - start;
    }

    private static long compute(int seed, int iterations) {
        long value = seed * 0x9E3779B97F4A7C15L;
        for (int i = 0; i < iterations; i++) {
            value ^= value << 13;
            value ^= value >>> 7;
            value ^= value << 17;
            value += i + seed;
        }
        return value;
    }

    private static int getInt(String[] args, int index, int defaultValue) {
        if (args == null || args.length <= index) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean getBool(String[] args, int index, boolean defaultValue) {
        if (args == null || args.length <= index) {
            return defaultValue;
        }
        String value = args[index].trim();
        return value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("yes");
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    public static class ServerFixture {
        public final List<LevelFixture> levels = new ArrayList<>();
    }

    public static class LevelFixture {
        public final List<EntityFixture> entities = new ArrayList<>();
        public final List<BlockEntityFixture> blockEntities = new ArrayList<>();
    }

    public static class EntityFixture {
        public final double x;
        public final double z;

        public EntityFixture(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    public static class BlockEntityFixture {
        public final BlockPosFixture worldPosition;

        public BlockEntityFixture(BlockPosFixture worldPosition) {
            this.worldPosition = worldPosition;
        }
    }

    public static class BlockPosFixture {
        private final int x;
        private final int z;

        public BlockPosFixture(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }
    }
}
