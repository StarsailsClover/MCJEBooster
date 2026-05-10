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

package com.mcjebooster.util;

import java.io.InputStream;
import java.util.Properties;

/**
 * Minecraft Version Detector
 * 
 * Detects the running Minecraft version using various heuristics:
 * 1. Package structure analysis
 * 2. Class name patterns
 * 3. Version-specific bytecode signatures
 * 4. System properties
 * 
 * This class is essential for version-specific transformations as different
 * Minecraft versions have different obfuscation mappings.
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 * @since 1.0
 */
public class VersionDetector {
    
    /** Cache for detected version */
    private static String cachedVersion = null;
    
    /** Known version signatures for detection */
    private static final VersionSignature[] VERSION_SIGNATURES = {
        new VersionSignature("1.8.9", "net/minecraft/server/MinecraftServer", "tick"),
        new VersionSignature("1.12.2", "net/minecraft/server/MinecraftServer", "tick"),
        new VersionSignature("1.16.5", "net/minecraft/class_3176", "method_3748"),
        new VersionSignature("1.17.1", "net/minecraft/server/MinecraftServer", "tickServer"),
        new VersionSignature("1.18.1", "net/minecraft/server/MinecraftServer", "tickServer"),
        new VersionSignature("1.19.1", "net/minecraft/server/MinecraftServer", "tickServer"),
        new VersionSignature("1.20.6", "net/minecraft/server/MinecraftServer", "tickServer"),
        new VersionSignature("1.26.1", "net/minecraft/server/MinecraftServer", "tickServer")
    };
    
    /**
     * Detects the Minecraft version by analyzing loaded classes
     * 
     * @return The detected version string, or "unknown" if detection fails
     */
    public static String detectMinecraftVersion() {
        if (cachedVersion != null) {
            return cachedVersion;
        }
        
        // Try multiple detection methods
        String version = detectFromSystemProperties();
        if (version != null) {
            cachedVersion = version;
            return version;
        }
        
        version = detectFromClassNames();
        if (version != null) {
            cachedVersion = version;
            return version;
        }
        
        version = detectFromBytecodeSignatures();
        if (version != null) {
            cachedVersion = version;
            return version;
        }
        
        // Fallback: try to detect from package structure
        version = detectFromPackageStructure();
        if (version != null) {
            cachedVersion = version;
            return version;
        }
        
        // Ultimate fallback
        cachedVersion = "unknown";
        return cachedVersion;
    }
    
    /**
     * Attempts to detect version from system properties
     * Some launchers set version properties
     * 
     * @return The version string, or null if not found
     */
    private static String detectFromSystemProperties() {
        // Check for common version properties
        String[] propertyKeys = {
            "minecraft.version",
            "mc.version",
            "game.version",
            "fabric.game.version",
            "forge.version"
        };
        
        for (String key : propertyKeys) {
            String value = System.getProperty(key);
            if (value != null && !value.isEmpty()) {
                Logger.info("Detected version from system property '" + key + "': " + value);
                return normalizeVersion(value);
            }
        }
        
        return null;
    }
    
    /**
     * Attempts to detect version from loaded class names
     * Different versions have different obfuscation patterns
     * 
     * @return The version string, or null if not detected
     */
    private static String detectFromClassNames() {
        // Get all loaded classes
        Class<?>[] loadedClasses = getAllLoadedClasses();
        
        for (Class<?> clazz : loadedClasses) {
            String className = clazz.getName();
            
            // Check against version signatures
            for (VersionSignature sig : VERSION_SIGNATURES) {
                if (className.contains(sig.classPattern)) {
                    // Verify by checking for version-specific method
                    if (hasMethod(clazz, sig.methodPattern)) {
                        Logger.info("Detected version from class signature: " + sig.version);
                        return sig.version;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Attempts to detect version from bytecode signatures
     * 
     * @return The version string, or null if not detected
     */
    private static String detectFromBytecodeSignatures() {
        // This would analyze bytecode patterns to detect version
        // For now, this is a placeholder
        return null;
    }
    
    /**
     * Attempts to detect version from package structure
     * 
     * @return The version string, or null if not detected
     */
    private static String detectFromPackageStructure() {
        // Check for version-specific package structures
        Package[] packages = Package.getPackages();
        
        for (Package pkg : packages) {
            String name = pkg.getName();
            
            // Check for modern versions (1.17+)
            if (name.startsWith("net.minecraft.server.level")) {
                return "1.17+";
            }
            
            // Check for older versions
            if (name.startsWith("net.minecraft.server.v1_8")) {
                return "1.8.x";
            }
            
            if (name.startsWith("net.minecraft.server.v1_12")) {
                return "1.12.x";
            }
            
            if (name.startsWith("net.minecraft.server.v1_16")) {
                return "1.16.x";
            }
        }
        
        return null;
    }
    
    /**
     * Gets all loaded classes in the JVM
     * Uses Instrumentation if available, otherwise uses ClassLoader
     * 
     * @return Array of loaded classes
     */
    private static Class<?>[] getAllLoadedClasses() {
        // Try to use instrumentation if available
        java.lang.instrument.Instrumentation inst = 
            com.mcjebooster.agent.MCJEBoosterAgent.getInstrumentation();
        
        if (inst != null) {
            return inst.getAllLoadedClasses();
        }
        
        // Fallback: get classes from the system class loader
        // This is less comprehensive but works without instrumentation
        return new Class<?>[0];
    }
    
    /**
     * Checks if a class has a method with the given name pattern
     * 
     * @param clazz The class to check
     * @param methodPattern The method name pattern
     * @return true if the method exists
     */
    private static boolean hasMethod(Class<?> clazz, String methodPattern) {
        try {
            java.lang.reflect.Method[] methods = clazz.getDeclaredMethods();
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().equals(methodPattern)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }
        return false;
    }
    
    /**
     * Normalizes a version string to a standard format
     * 
     * @param version The raw version string
     * @return The normalized version string
     */
    private static String normalizeVersion(String version) {
        if (version == null || version.isEmpty()) {
            return "unknown";
        }
        
        // Remove common prefixes
        version = version.replaceAll("^(mc|minecraft|forge|fabric|neoforge)-?", "");
        
        // Remove snapshot/build info
        version = version.replaceAll("-?(snapshot|rc|pre|beta|alpha).*", "");
        
        // Trim whitespace
        version = version.trim();
        
        // Validate version format (should be like x.x.x or x.x)
        if (!version.matches("^\\d+(\\.\\d+)*$")) {
            return "unknown";
        }
        
        return version;
    }
    
    /**
     * Gets the major version number from a version string
     * 
     * @param version The version string (e.g., "1.20.6")
     * @return The major version number (e.g., 1)
     */
    public static int getMajorVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            if (parts.length > 0) {
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return 1; // Default to 1
    }
    
    /**
     * Gets the minor version number from a version string
     * 
     * @param version The version string (e.g., "1.20.6")
     * @return The minor version number (e.g., 20)
     */
    public static int getMinorVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            if (parts.length > 1) {
                return Integer.parseInt(parts[1]);
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return 0;
    }
    
    /**
     * Gets the patch version number from a version string
     * 
     * @param version The version string (e.g., "1.20.6")
     * @return The patch version number (e.g., 6)
     */
    public static int getPatchVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            if (parts.length > 2) {
                return Integer.parseInt(parts[2]);
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return 0;
    }
    
    /**
     * Compares two version strings
     * 
     * @param v1 First version
     * @param v2 Second version
     * @return negative if v1 < v2, 0 if equal, positive if v1 > v2
     */
    public static int compareVersions(String v1, String v2) {
        int major1 = getMajorVersion(v1);
        int major2 = getMajorVersion(v2);
        
        if (major1 != major2) {
            return major1 - major2;
        }
        
        int minor1 = getMinorVersion(v1);
        int minor2 = getMinorVersion(v2);
        
        if (minor1 != minor2) {
            return minor1 - minor2;
        }
        
        return getPatchVersion(v1) - getPatchVersion(v2);
    }
    
    /**
     * Checks if a version is at least the specified minimum
     * 
     * @param version The version to check
     * @param minimum The minimum required version
     * @return true if version >= minimum
     */
    public static boolean isAtLeast(String version, String minimum) {
        return compareVersions(version, minimum) >= 0;
    }
    
    /**
     * Checks if a version is within a range
     * 
     * @param version The version to check
     * @param minVersion The minimum version (inclusive)
     * @param maxVersion The maximum version (inclusive)
     * @return true if minVersion <= version <= maxVersion
     */
    public static boolean isInRange(String version, String minVersion, String maxVersion) {
        return isAtLeast(version, minVersion) && compareVersions(version, maxVersion) <= 0;
    }
    
    /**
     * Represents a version signature for detection
     */
    private static class VersionSignature {
        final String version;
        final String classPattern;
        final String methodPattern;
        
        VersionSignature(String version, String classPattern, String methodPattern) {
            this.version = version;
            this.classPattern = classPattern;
            this.methodPattern = methodPattern;
        }
    }
}
