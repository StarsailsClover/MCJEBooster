/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.adapter;

import java.util.Map;
import java.util.Set;

/**
 * Version-specific adapter interface for MCJEBooster.
 * 
 * Each Minecraft version and mod loader combination requires a specific adapter
 * that defines:
 * - Class name mappings (obfuscated names vary by version)
 * - Method signatures (parameter types and return types)
 * - Field locations
 * - Java version compatibility
 * - Loader-specific modifications
 * 
 * Adapters are loaded from *.mcjeb files and provide version-specific
 * transformation rules.
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 */
public interface VersionAdapter {
    
    /**
     * Gets the adapter identifier.
     * Format: "mcVersion-loaderVersion"
     * Example: "1.12.2-Forge_14.23.5.2847"
     * 
     * @return The unique adapter identifier
     */
    String getAdapterId();
    
    /**
     * Gets the Minecraft version this adapter supports.
     * 
     * @return The Minecraft version string (e.g., "1.12.2")
     */
    String getMinecraftVersion();
    
    /**
     * Gets the mod loader type.
     * 
     * @return The loader type (VANILLA, FORGE, FABRIC, NEOFORGE, LITELOADER, etc.)
     */
    LoaderType getLoaderType();
    
    /**
     * Gets the mod loader version.
     * 
     * @return The loader version string, or null for vanilla
     */
    String getLoaderVersion();
    
    /**
     * Gets the required Java version.
     * 
     * @return The required Java major version (e.g., 8, 11, 17, 21)
     */
    int getRequiredJavaVersion();
    
    /**
     * Gets the class name mappings for this version.
     * Maps logical class names to actual obfuscated/intermediary names.
     * 
     * @return Map of logical name -> actual class name
     */
    Map<String, String> getClassMappings();
    
    /**
     * Gets the method name mappings for this version.
     * Maps logical method names to actual obfuscated/intermediary names.
     * 
     * @return Map of "className.methodName" -> actual method name
     */
    Map<String, String> getMethodMappings();
    
    /**
     * Gets the field name mappings for this version.
     * Maps logical field names to actual obfuscated/intermediary names.
     * 
     * @return Map of "className.fieldName" -> actual field name
     */
    Map<String, String> getFieldMappings();
    
    /**
     * Gets the method descriptors for this version.
     * Required for ASM transformation to match method signatures.
     * 
     * @return Map of "className.methodName" -> method descriptor
     */
    Map<String, String> getMethodDescriptors();
    
    /**
     * Gets the tick method target for injection.
     * This is the main method that will be transformed for multi-core scheduling.
     * 
     * @return The target method identifier (e.g., "MinecraftServer.tick")
     */
    String getTickMethodTarget();
    
    /**
     * Gets the entity tick method target.
     * 
     * @return The entity tick method identifier
     */
    String getEntityTickMethodTarget();
    
    /**
     * Gets the block tick method target.
     * 
     * @return The block tick method identifier
     */
    String getBlockTickMethodTarget();
    
    /**
     * Gets the chunk provider class name.
     * 
     * @return The chunk provider class identifier
     */
    String getChunkProviderClass();
    
    /**
     * Gets the world/level class name.
     * 
     * @return The world/level class identifier
     */
    String getWorldClass();
    
    /**
     * Gets the entity class name.
     * 
     * @return The entity class identifier
     */
    String getEntityClass();
    
    /**
     * Gets the server-side entity list field.
     * Used to access entities for region-based processing.
     * 
     * @return The entity list field identifier
     */
    String getEntityListField();
    
    /**
     * Gets the region size in chunks for this version.
     * Different versions may have different optimal region sizes.
     * 
     * @return Region size in chunks
     */
    int getRegionSize();
    
    /**
     * Gets the recommended worker thread count.
     * 
     * @return Recommended number of worker threads
     */
    int getRecommendedWorkerCount();
    
    /**
     * Gets the tick timeout in milliseconds.
     * 
     * @return Tick timeout
     */
    long getTickTimeoutMs();
    
    /**
     * Checks if this adapter supports a specific feature.
     * 
     * @param feature The feature to check
     * @return true if the feature is supported
     */
    boolean supportsFeature(Feature feature);
    
    /**
     * Gets the set of supported features.
     * 
     * @return Set of supported features
     */
    Set<Feature> getSupportedFeatures();
    
    /**
     * Gets loader-specific JVM arguments that need to be added.
     * 
     * @return Array of JVM arguments, or empty array if none needed
     */
    String[] getRequiredJvmArgs();
    
    /**
     * Gets the priority of this adapter.
     * Higher priority adapters are checked first when matching versions.
     * 
     * @return The adapter priority (default: 100)
     */
    default int getPriority() {
        return 100;
    }
    
    /**
     * Validates that this adapter can be used with the current environment.
     * 
     * @return true if the adapter is compatible
     */
    boolean validate();
    
    /**
     * Gets the adapter version.
     * This is the version of the adapter itself, not the Minecraft version.
     * 
     * @return The adapter version string
     */
    String getAdapterVersion();
    
    /**
     * Mod loader types supported by MCJEBooster.
     */
    enum LoaderType {
        VANILLA("Vanilla"),
        FORGE("Forge"),
        FABRIC("Fabric"),
        NEOFORGE("NeoForge"),
        LITELOADER("LiteLoader"),
        OPTIFINE("OptiFine"),
        FORGE_OPTIFINE("Forge+OptiFine"),
        FABRIC_OPTIFINE("Fabric+OptiFine"),
        FORGE_LITELOADER("Forge+LiteLoader"),
        FORGE_LITELOADER_OPTIFINE("Forge+LiteLoader+OptiFine"),
        UNKNOWN("Unknown");
        
        private final String displayName;
        
        LoaderType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        /**
         * Detects loader type from version string.
         * 
         * @param versionString The version string to parse
         * @return The detected loader type
         */
        public static LoaderType fromVersionString(String versionString) {
            if (versionString == null) {
                return VANILLA;
            }
            
            String lower = versionString.toLowerCase();
            
            // Check for combined loaders first (most specific)
            if (lower.contains("forge") && lower.contains("liteloader") && lower.contains("optifine")) {
                return FORGE_LITELOADER_OPTIFINE;
            }
            if (lower.contains("forge") && lower.contains("liteloader")) {
                return FORGE_LITELOADER;
            }
            if (lower.contains("forge") && lower.contains("optifine")) {
                return FORGE_OPTIFINE;
            }
            if (lower.contains("fabric") && lower.contains("optifine")) {
                return FABRIC_OPTIFINE;
            }
            
            // Check for single loaders
            if (lower.contains("neoforge")) {
                return NEOFORGE;
            }
            if (lower.contains("forge")) {
                return FORGE;
            }
            if (lower.contains("fabric")) {
                return FABRIC;
            }
            if (lower.contains("liteloader")) {
                return LITELOADER;
            }
            if (lower.contains("optifine")) {
                return OPTIFINE;
            }
            
            return VANILLA;
        }
    }
    
    /**
     * Features that may vary by version/loader.
     */
    enum Feature {
        MULTITHREADED_ENTITIES,      // Multi-threaded entity ticking
        MULTITHREADED_BLOCKS,        // Multi-threaded block ticking
        MULTITHREADED_TILE_ENTITIES, // Multi-threaded tile entity ticking
        ASYNC_CHUNK_LOADING,         // Asynchronous chunk loading
        ASYNC_PATHFINDING,           // Asynchronous mob pathfinding
        ASYNC_LIGHTING,              // Asynchronous lighting updates
        REDSTONE_OPTIMIZATION,       // Redstone circuit optimization
        FLUID_OPTIMIZATION,          // Fluid simulation optimization
        ENTITY_COLLISION,            // Entity collision optimization
        DYNAMIC_REGION_BALANCING,    // Dynamic region load balancing
        SYNC_POINT_BARRIERS,         // Synchronization barriers
        AUTO_ROLLBACK,               // Automatic rollback on failure
        DEBUG_PROFILING,             // Debug profiling support
        HOT_SWAP,                    // Hot-swap adapter support
        FORGE_EVENT_BUS,           // Forge event bus compatibility
        FABRIC_API_COMPAT            // Fabric API compatibility
    }
}
