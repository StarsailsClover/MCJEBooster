# MCJEBooster Test Plan

**Version:** v26.4-20260510  
**Last Updated:** 2026-05-10

---

## Test Environment Setup

### Prerequisites
- Java 8, 11, 16, 17, 21 (multiple versions for different MC versions)
- Minecraft Java Edition (versions 1.8.9 - 26.1.1)
- Maven 3.8+
- Windows 10/11 or Linux

### Test Versions Matrix

| Minecraft | Java | Loader | Adapter File | Priority |
|-----------|------|--------|--------------|----------|
| 1.8.9 | 8 | Vanilla | 1.8.9-Vanilla.mcjeb | P1 |
| 1.8.9 | 8 | Forge | 1.8.9-Forge.mcjeb | P1 |
| 1.12.2 | 8 | Vanilla | 1.12.2-Vanilla.mcjeb | P1 |
| 1.12.2 | 8 | Forge | 1.12.2-Forge.mcjeb | P1 |
| 1.12.2 | 8 | LiteLoader | 1.12.2-LiteLoader.mcjeb | P2 |
| 1.12.2 | 8 | Forge+LiteLoader | 1.12.2-Forge-LiteLoader.mcjeb | P2 |
| 1.16.5 | 8/11 | Vanilla | 1.16.5-Vanilla.mcjeb | P1 |
| 1.16.5 | 8/11 | Fabric | 1.16.5-Fabric.mcjeb | P1 |
| 1.16.5 | 8/11 | Forge | 1.16.5-Forge.mcjeb | P2 |
| 1.17.1 | 16 | Vanilla | 1.17.1-Vanilla.mcjeb | P2 |
| 1.18.1 | 17 | Vanilla | 1.18.1-Vanilla.mcjeb | P1 |
| 1.18.1 | 17 | Fabric | 1.18.1-Fabric.mcjeb | P2 |
| 1.19.1 | 17 | Vanilla | 1.19.1-Vanilla.mcjeb | P2 |
| 1.20.6 | 21 | Vanilla | 1.20.6-Vanilla.mcjeb | P1 |
| 1.20.6 | 21 | NeoForge | 1.20.6-NeoForge.mcjeb | P2 |
| 26.1.1 | 21 | Vanilla | 26.1.1-Vanilla.mcjeb | P1 |

---

## Test Cases

### TC-001: Agent Initialization
**Objective:** Verify agent loads correctly

**Steps:**
1. Start Minecraft
2. Run injector: `java -jar MCJEBooster.jar`
3. Check console output

**Expected Results:**
- [ ] Version detection successful
- [ ] Adapter loaded correctly
- [ ] Transformer registered
- [ ] Scheduler initialized
- [ ] No exceptions

**Pass Criteria:** All checks pass

---

### TC-002: TPS Improvement
**Objective:** Verify TPS improvement in high-load scenarios

**Setup:**
- World with 5000+ entities
- Redstone contraptions
- View distance 16+

**Steps:**
1. Record baseline TPS (vanilla) for 5 minutes
2. Inject MCJEBooster
3. Record TPS for 5 minutes
4. Compare results

**Expected Results:**
- [ ] TPS improves by at least 50%
- [ ] No tick skips
- [ ] Stable performance

**Pass Criteria:** TPS improvement >= 50%

---

### TC-003: Auto-Rollback
**Objective:** Verify automatic rollback on failure

**Steps:**
1. Inject MCJEBooster
2. Simulate failure (e.g., kill worker thread)
3. Monitor behavior

**Expected Results:**
- [ ] Failure detected
- [ ] Rollback initiated
- [ ] Vanilla behavior restored
- [ ] No crash

**Pass Criteria:** Graceful rollback

---

### TC-004: Adapter Auto-Download
**Objective:** Verify adapter auto-download from GitHub

**Steps:**
1. Clear adapter cache
2. Start Minecraft
3. Inject MCJEBooster
4. Check download

**Expected Results:**
- [ ] Version detected
- [ ] Adapter downloaded
- [ ] Checksum verified
- [ ] Adapter loaded

**Pass Criteria:** Adapter auto-downloaded and loaded

---

### TC-005: Multi-Loader Compatibility
**Objective:** Verify compatibility with different mod loaders

**Test Loaders:**
- [ ] Vanilla
- [ ] Forge
- [ ] Fabric
- [ ] NeoForge
- [ ] LiteLoader
- [ ] OptiFine combinations

**Steps:**
1. Install loader
2. Start Minecraft
3. Inject MCJEBooster
4. Verify functionality

**Pass Criteria:** No crashes, TPS improved

---

### TC-006: Memory Safety
**Objective:** Verify no memory leaks

**Steps:**
1. Start Minecraft with MCJEBooster
2. Run for 1 hour
3. Monitor heap usage

**Expected Results:**
- [ ] No OutOfMemoryError
- [ ] Heap usage stable
- [ ] No memory leaks detected

**Pass Criteria:** Stable memory usage

---

### TC-007: Thread Safety
**Objective:** Verify no race conditions

**Steps:**
1. Run stress test (10000 entities)
2. Monitor for:
   - Deadlocks
   - Race conditions
   - ConcurrentModificationException

**Expected Results:**
- [ ] No deadlocks
- [ ] No race conditions
- [ ] No exceptions

**Pass Criteria:** Clean execution

---

### TC-008: Security
**Objective:** Verify security hardening

**Tests:**
- [ ] Null pointer handling
- [ ] Input validation
- [ ] Resource cleanup
- [ ] File size limits

**Pass Criteria:** No security vulnerabilities

---

## Test Execution Schedule

### Phase 1: Unit Tests (Day 1-2)
- [ ] Logger tests
- [ ] VersionDetector tests
- [ ] AdapterLoader tests
- [ ] SyncPointManager tests

### Phase 2: Integration Tests (Day 3-5)
- [ ] Agent initialization
- [ ] Transformer injection
- [ ] Scheduler operation
- [ ] UpdateManager download

### Phase 3: Compatibility Tests (Day 6-10)
- [ ] Vanilla versions
- [ ] Forge versions
- [ ] Fabric versions
- [ ] NeoForge versions
- [ ] Combined loaders

### Phase 4: Performance Tests (Day 11-14)
- [ ] TPS benchmarks
- [ ] Memory profiling
- [ ] Stress tests
- [ ] Long-running stability

---

## Test Tools

### Profiling Tools
- VisualVM: Memory and CPU profiling
- JProfiler: Advanced profiling
- Minecraft's built-in debug (F3)

### Benchmark Tools
- Spark: TPS monitoring
- LagGoggles: Tick time analysis
- Custom TPS logger

### Automation
- JUnit 5 for unit tests
- GitHub Actions for CI/CD
- Test scripts for manual testing

---

## Success Criteria

### Must Have (P1)
- [ ] Agent loads without errors
- [ ] TPS improves by 50%+
- [ ] No crashes on supported versions
- [ ] Auto-rollback works

### Should Have (P2)
- [ ] All loaders supported
- [ ] Auto-download works
- [ ] Memory usage stable
- [ ] Thread-safe operation

### Nice to Have (P3)
- [ ] GUI injector
- [ ] Real-time dashboard
- [ ] Advanced profiling

---

## Bug Reporting

### Template
```
**Version:** [MC Version] [Loader]
**Java:** [Java Version]
**MCJEBooster:** [Version]

**Description:**
[What happened]

**Steps to Reproduce:**
1. [Step 1]
2. [Step 2]

**Expected:**
[What should happen]

**Actual:**
[What actually happened]

**Logs:**
```
[Attach logs]
```

**Screenshots:**
[If applicable]
```

---

## Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Test Lead | | | |
| Developer | StarsailsClover | 2026-05-10 | |
| QA | | | |

---

*Test Plan Version: v26.4-20260510*
