/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.test;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mcjebooster.scheduler.RegionScheduler;
import com.mcjebooster.sync.SyncPointManager;
import com.mcjebooster.util.VersionDetector;

/**
 * Unit tests for MCJEBooster core components
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 */
public class MCJEBoosterTest {
    
    @BeforeEach
    void setUp() {
        // Reset singletons before each test
        // Note: In production, these would need proper singleton reset mechanisms
    }
    
    @Test
    @DisplayName("Version detection should handle standard version strings")
    void testVersionDetection() {
        assertEquals(1, VersionDetector.getMajorVersion("1.20.6"));
        assertEquals(20, VersionDetector.getMinorVersion("1.20.6"));
        assertEquals(6, VersionDetector.getPatchVersion("1.20.6"));
    }
    
    @Test
    @DisplayName("Version comparison should work correctly")
    void testVersionComparison() {
        assertTrue(VersionDetector.compareVersions("1.20.6", "1.19.1") > 0);
        assertTrue(VersionDetector.compareVersions("1.18.1", "1.20.6") < 0);
        assertEquals(0, VersionDetector.compareVersions("1.20.6", "1.20.6"));
    }
    
    @Test
    @DisplayName("RegionScheduler should be a singleton")
    void testRegionSchedulerSingleton() {
        RegionScheduler scheduler1 = RegionScheduler.getInstance();
        RegionScheduler scheduler2 = RegionScheduler.getInstance();
        assertSame(scheduler1, scheduler2);
    }
    
    @Test
    @DisplayName("SyncPointManager should be a singleton")
    void testSyncPointManagerSingleton() {
        SyncPointManager sync1 = SyncPointManager.getInstance();
        SyncPointManager sync2 = SyncPointManager.getInstance();
        assertSame(sync1, sync2);
    }
    
    @Test
    @DisplayName("Region should correctly identify contained points")
    void testRegionContains() {
        RegionScheduler.Region region = new RegionScheduler.Region(
            0, 0, 0, 16, 16
        );
        
        assertTrue(region.contains(8, 8));
        assertTrue(region.contains(0, 0));
        assertFalse(region.contains(16, 16));
        assertFalse(region.contains(-1, -1));
    }
    
    @Test
    @DisplayName("Version range check should work correctly")
    void testVersionRange() {
        assertTrue(VersionDetector.isInRange("1.20.6", "1.18.0", "1.21.0"));
        assertFalse(VersionDetector.isInRange("1.17.1", "1.18.0", "1.21.0"));
    }
}
