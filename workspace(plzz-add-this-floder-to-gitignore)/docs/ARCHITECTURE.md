# MCJEBooster Architecture Documentation

**Version:** v26.1-05102026  
**Last Updated:** 2026-05-10

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Core Components](#core-components)
3. [Injection Flow](#injection-flow)
4. [Scheduling Architecture](#scheduling-architecture)
5. [Synchronization Mechanisms](#synchronization-mechanisms)
6. [Version Compatibility](#version-compatibility)
7. [Performance Considerations](#performance-considerations)
8. [Security & Safety](#security--safety)

---

## System Overview

MCJEBooster is a JVM-level optimization tool that transforms Minecraft's single-threaded tick processing into a multi-threaded, region-based system. It operates as a Java Agent that attaches to running Minecraft processes and performs bytecode transformation using ASM.

### Design Goals

1. **Transparency**: No modification to Minecraft installation
2. **Compatibility**: Support multiple Minecraft versions and mod loaders
3. **Safety**: Automatic rollback on failure
4. **Performance**: Significant TPS improvement in high-load scenarios
5. **Maintainability**: Clean, documented, testable code

---

## Core Components

### 1. MCJEBoosterAgent

The Java Agent entry point that handles:
- Agent initialization (premain/agentmain)
- Class transformer registration
- Version detection
- Health monitoring setup

### 2. InjectorMain

Standalone application for external injection:
- Process discovery using Java Attach API
- User interaction for multi-process scenarios
- Agent loading into target JVM

### 3. RegionScheduler

Core multi-threading engine:
- ForkJoinPool for worker thread management
- Z-order spatial partitioning
- Dynamic load balancing
- TPS calculation and monitoring

### 4. MinecraftServerTransformer

ASM-based bytecode transformer:
- Method signature detection
- Bytecode pattern matching
- Multi-version compatibility
- Safe transformation with rollback capability

### 5. SyncPointManager

Synchronization coordinator:
- CyclicBarrier for tick synchronization
- Entity transfer synchronization
- Block update ordering
- Timeout handling

### 6. VersionDetector

Version identification system:
- Multi-method detection (properties, classes, bytecode)
- Version comparison utilities
- Mapping-aware detection

---

## Injection Flow

```
1. User runs InjectorMain
        │
        ▼
2. Scan for Minecraft processes
        │
        ▼
3. Select target process
        │
        ▼
4. Attach to JVM via Attach API
        │
        ▼
5. Load MCJEBoosterAgent
        │
        ▼
6. Agent initialization:
   a. Detect MC version
   b. Register transformer
   c. Transform loaded classes
   d. Initialize scheduler
   e. Start health monitor
        │
        ▼
7. Transformer modifies:
   - MinecraftServer.tick()
   - ChunkProvider.tick()
   - Level.tickEntities()
        │
        ▼
8. Multi-threaded tick begins
```

---

## Scheduling Architecture

### Region Partitioning

The world is divided into regions using Z-order (Morton) curve for cache efficiency:

```
Without Z-order:          With Z-order:
┌───┬───┬───┬───┐        ┌───┬───┬───┬───┐
│ 0 │ 1 │ 2 │ 3 │        │ 0 │ 1 │ 4 │ 5 │
├───┼───┼───┼───┤        ├───┼───┼───┼───┤
│ 4 │ 5 │ 6 │ 7 │        │ 2 │ 3 │ 6 │ 7 │
├───┼───┼───┼───┤        ├───┼───┼───┼───┤
│ 8 │ 9 │10 │11 │        │ 8 │ 9 │12 │13 │
├───┼───┼───┼───┤        ├───┼───┼───┼───┤
│12 │13 │14 │15 │        │10 │11 │14 │15 │
└───┴───┴───┴───┘        └───┴───┴───┴───┘
```

### Worker Thread Pool

- Default: CPU count - 1 workers
- Uses ForkJoinPool for work stealing
- Each worker processes assigned regions
- Main thread coordinates via barriers

### Tick Flow

```
Main Thread:
1. Acquire read lock
2. Submit region tasks to workers
3. Wait for completion (with timeout)
4. Release read lock
5. Update TPS calculation

Worker Thread (per region):
1. Tick entities in region
2. Tick blocks in region
3. Tick tile entities in region
4. Record execution time
5. Signal completion
```

---

## Synchronization Mechanisms

### CyclicBarrier

- Synchronizes all workers at tick boundary
- Timeout: 45ms (leaving 5ms for sync operations)
- Auto-reset on trip
- Failure tracking for health monitoring

### ReadWriteLock

- World state: Read lock during tick
- Prevents concurrent modifications
- Allows multiple readers (worker threads)

### Entity Transfer Sync

When entities move between regions:
1. Source region marks entity for transfer
2. SyncPointManager coordinates handoff
3. Destination region accepts entity
4. Atomic update to prevent duplication/loss

---

## Version Compatibility

### Detection Strategy

1. **System Properties**: Check for launcher-set version
2. **Class Names**: Match known obfuscation patterns
3. **Bytecode Signatures**: Analyze method patterns
4. **Package Structure**: Check for version-specific packages

### Mapping Support

| Mapping | Support Status |
|---------|---------------|
| Official (Obf) | ✅ Full |
| MCP | ✅ Full |
| Yarn | ✅ Full |
| Quilt | ✅ Full |
| Intermediary | ✅ Full |

### Version-Specific Adaptations

- **1.8.9 - 1.12.2**: Legacy tick loop structure
- **1.13 - 1.16**: Flattening changes
- **1.17+**: New chunk system
- **1.20.5+**: Component system changes

---

## Performance Considerations

### Optimal Scenarios

- High entity counts (>1000)
- Large view distances (>16)
- Redstone contraptions
- Multiple players

### Suboptimal Scenarios

- Empty worlds (minimal gain)
- Single-player with low settings
- CPU-bound by other factors

### Tuning Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| workerCount | CPU-1 | Number of worker threads |
| regionSize | 16 chunks | Size of each region |
| tickTimeout | 45ms | Maximum tick duration |
| rebalanceInterval | 100 ticks | Load rebalancing frequency |

---

## Security & Safety

### Injection Safety

- No permanent modification to Minecraft
- Agent can be detached
- Original classes preserved for rollback

### Health Monitoring

Continuous monitoring of:
- TPS (must stay above 5.0)
- Thread liveness
- Deadlock detection
- Tick timeout

### Auto-Rollback Triggers

| Condition | Threshold | Action |
|-----------|-----------|--------|
| Low TPS | < 5.0 for 5 ticks | Rollback |
| Deadlock | Any detected | Rollback |
| Tick timeout | 3 consecutive | Reduce workers |
| Scheduler death | Immediate | Rollback |

### Rollback Process

1. Stop RegionScheduler
2. Restore original class definitions
3. Disable transformers
4. Log rollback event
5. Continue with vanilla behavior

---

## Development Guidelines

### Code Style

- Java 17 features
- English comments and documentation
- Comprehensive Javadoc
- Unit tests for core logic

### Testing Strategy

1. Unit tests for utilities
2. Integration tests with mock Minecraft
3. Version compatibility matrix
4. Performance regression tests

### Documentation Requirements

- Architecture decisions
- API documentation
- Troubleshooting guides
- Performance benchmarks

---

## Future Enhancements

### Planned Features

1. Linux support
2. GUI injector
3. Real-time performance dashboard
4. Profile-based optimization
5. Mod-specific compatibility patches

### Research Areas

1. GPU acceleration for entity AI
2. Predictive tick scheduling
3. Machine learning for load prediction
4. Distributed server support

---

## References

- [ASM Documentation](https://asm.ow2.io/)
- [Java Attach API](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.attach/com/sun/tools/attach/package-summary.html)
- [ForkJoinPool Guide](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html)
- Minecraft Protocol Documentation

---

*This document is part of the MCJEBooster project and is licensed under LGPL-2.1*
