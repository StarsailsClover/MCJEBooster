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

import com.mcjebooster.util.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.*;

/**
 * Adapter loader for MCJEBooster.
 * 
 * Loads version-specific adapters from *.mcjeb files.
 * Each adapter file contains the transformation rules for a specific
 * Minecraft version and mod loader combination.
 * 
 * Adapter file format (.mcjeb):
 * - JSON-based metadata
 * - Class/method/field mappings
 * - Version compatibility info
 * - Transformation rules
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 */
public class AdapterLoader {
    
    /** Singleton instance */
    private static volatile AdapterLoader INSTANCE;
    
    /** Cache of loaded adapters */
    private final Map<String, VersionAdapter> adapterCache = new ConcurrentHashMap<>();
    
    /** Map of adapter IDs to file paths */
    private final Map<String, Path> adapterFiles = new ConcurrentHashMap<>();
    
    /** Directory containing adapter files */
    private Path adaptersDirectory;
    
    /** Default adapters directory name */
    private static final String DEFAULT_ADAPTERS_DIR = "adapters";
    
    /** Adapter file extension */
    private static final String ADAPTER_EXTENSION = ".mcjeb";
    
    /** Adapter manifest entry name */
    private static final String MANIFEST_ENTRY = "adapter.json";
    
    /**
     * Private constructor
     */
    private AdapterLoader() {
    }
    
    /**
     * Gets the singleton instance
     * 
     * @return The AdapterLoader instance
     */
    public static AdapterLoader getInstance() {
        if (INSTANCE == null) {
            synchronized (AdapterLoader.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AdapterLoader();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Initializes the adapter loader.
     * Scans for adapter files in the specified or default directory.
     * 
     * @param adaptersDir Path to adapters directory, or null for default
     */
    public void initialize(Path adaptersDir) {
        if (adaptersDir != null) {
            this.adaptersDirectory = adaptersDir;
        } else {
            // Try to find adapters directory relative to JAR location
            this.adaptersDirectory = findDefaultAdaptersDirectory();
        }
        
        if (this.adaptersDirectory != null && Files.exists(this.adaptersDirectory)) {
            scanAdapterFiles();
            Logger.info("AdapterLoader initialized with " + adapterFiles.size() + " adapter(s)");
        } else {
            Logger.warn("Adapters directory not found: " + this.adaptersDirectory);
        }
    }
    
    /**
     * Finds the default adapters directory.
     * 
     * @return Path to adapters directory, or null if not found
     */
    private Path findDefaultAdaptersDirectory() {
        try {
            // Try to locate relative to the running JAR
            java.security.ProtectionDomain pd = AdapterLoader.class.getProtectionDomain();
            if (pd != null && pd.getCodeSource() != null && pd.getCodeSource().getLocation() != null) {
                String jarPath = pd.getCodeSource().getLocation().toURI().getPath();
                Path jarDir = new File(jarPath).getParentFile().toPath();
                Path adaptersDir = jarDir.resolve(DEFAULT_ADAPTERS_DIR);
                if (Files.exists(adaptersDir)) {
                    return adaptersDir;
                }
            }
            
            // Try current working directory
            Path adaptersDir = Paths.get(DEFAULT_ADAPTERS_DIR);
            if (Files.exists(adaptersDir)) {
                return adaptersDir;
            }
            
            // Try user home directory
            adaptersDir = Paths.get(System.getProperty("user.home"), ".mcjebooster", DEFAULT_ADAPTERS_DIR);
            if (Files.exists(adaptersDir)) {
                return adaptersDir;
            }
            
        } catch (Exception e) {
            Logger.warn("Failed to find default adapters directory: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Scans for adapter files in the adapters directory.
     */
    private void scanAdapterFiles() {
        try {
            Files.walk(adaptersDirectory)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(ADAPTER_EXTENSION))
                .forEach(this::registerAdapterFile);
        } catch (IOException e) {
            Logger.error("Failed to scan adapter files: " + e.getMessage());
        }
    }
    
    /**
     * Registers an adapter file.
     * 
     * @param path Path to the adapter file
     */
    private void registerAdapterFile(Path path) {
        try {
            // Extract adapter ID from manifest
            String adapterId = extractAdapterId(path);
            if (adapterId != null) {
                adapterFiles.put(adapterId, path);
                Logger.debug("Registered adapter: " + adapterId + " -> " + path);
            }
        } catch (Exception e) {
            Logger.warn("Failed to register adapter file: " + path + " - " + e.getMessage());
        }
    }
    
    /**
     * Extracts the adapter ID from an adapter file.
     * 
     * @param path Path to the adapter file
     * @return The adapter ID, or null if extraction failed
     * @throws IOException if reading fails
     */
    private String extractAdapterId(Path path) throws IOException {
        String content = readAdapterJson(path);
        String adapterId = extractJsonField(content, "adapterId");
        if (adapterId != null && !adapterId.isEmpty()) {
            return adapterId;
        }
        // Fallback: derive from filename
        String fileName = path.getFileName().toString();
        return fileName.substring(0, fileName.length() - ADAPTER_EXTENSION.length());
    }
    
    private String readAdapterJson(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            JarEntry entry = jar.getJarEntry(MANIFEST_ENTRY);
            if (entry == null) {
                throw new FileNotFoundException("Missing " + MANIFEST_ENTRY + " in adapter archive: " + path);
            }
            try (InputStream in = jar.getInputStream(entry)) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (java.util.zip.ZipException e) {
            return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    
    /**
     * Simple JSON field extraction.
     * 
     * @param json The JSON string
     * @param fieldName The field name to extract
     * @return The field value, or null if not found
     */
    private String extractJsonField(String json, String fieldName) {
        String search = "\"" + fieldName + "\"";
        int index = json.indexOf(search);
        if (index == -1) {
            return null;
        }
        
        int valueStart = json.indexOf(":", index) + 1;
        while (valueStart < json.length() && 
               (json.charAt(valueStart) == ' ' || json.charAt(valueStart) == '"')) {
            valueStart++;
        }
        
        int valueEnd = json.indexOf("\"", valueStart);
        if (valueEnd == -1) {
            valueEnd = json.indexOf(",", valueStart);
        }
        if (valueEnd == -1) {
            valueEnd = json.indexOf("}", valueStart);
        }
        
        if (valueEnd > valueStart) {
            return json.substring(valueStart, valueEnd).trim();
        }
        
        return null;
    }
    
    /**
     * Loads an adapter for the specified version.
     * 
     * @param minecraftVersion The Minecraft version
     * @param loaderType The mod loader type
     * @param loaderVersion The mod loader version (can be null)
     * @return The loaded adapter, or null if not found
     */
    public VersionAdapter loadAdapter(String minecraftVersion, 
                                     VersionAdapter.LoaderType loaderType,
                                     String loaderVersion) {
        String adapterId = buildAdapterId(minecraftVersion, loaderType, loaderVersion);
        
        // Check cache first
        if (adapterCache.containsKey(adapterId)) {
            return adapterCache.get(adapterId);
        }
        
        // Find adapter file
        Path adapterPath = adapterFiles.get(adapterId);
        if (adapterPath == null) {
            // Try to find a compatible adapter
            adapterPath = findCompatibleAdapter(minecraftVersion, loaderType);
        }
        
        if (adapterPath == null) {
            Logger.warn("No adapter found for: " + adapterId);
            return null;
        }
        
        // Load the adapter
        VersionAdapter adapter = loadAdapterFromFile(adapterPath);
        if (adapter != null) {
            adapterCache.put(adapterId, adapter);
            Logger.info("Loaded adapter: " + adapterId);
        }
        
        return adapter;
    }
    
    /**
     * Builds an adapter ID from version info.
     * 
     * @param minecraftVersion The Minecraft version
     * @param loaderType The loader type
     * @param loaderVersion The loader version
     * @return The adapter ID string
     */
    private String buildAdapterId(String minecraftVersion, 
                                 VersionAdapter.LoaderType loaderType,
                                 String loaderVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append(minecraftVersion);
        sb.append("-").append(loaderType.name());
        if (loaderVersion != null && !loaderVersion.isEmpty()) {
            sb.append("_").append(loaderVersion);
        }
        return sb.toString();
    }
    
    /**
     * Finds a compatible adapter for the specified version.
     * Falls back to vanilla adapter if specific loader adapter not found.
     * 
     * @param minecraftVersion The Minecraft version
     * @param loaderType The loader type
     * @return Path to compatible adapter, or null if none found
     */
    private Path findCompatibleAdapter(String minecraftVersion, 
                                      VersionAdapter.LoaderType loaderType) {
        // Try exact match first
        String exactId = buildAdapterId(minecraftVersion, loaderType, null);
        if (adapterFiles.containsKey(exactId)) {
            return adapterFiles.get(exactId);
        }
        
        // Try vanilla fallback for this version
        if (loaderType != VersionAdapter.LoaderType.VANILLA) {
            String vanillaId = buildAdapterId(minecraftVersion, 
                VersionAdapter.LoaderType.VANILLA, null);
            if (adapterFiles.containsKey(vanillaId)) {
                Logger.info("Using vanilla adapter as fallback for: " + minecraftVersion);
                return adapterFiles.get(vanillaId);
            }
        }
        
        // Try major version match (e.g., 1.20.x for 1.20.6)
        String majorVersion = getMajorVersion(minecraftVersion);
        if (majorVersion != null) {
            for (Map.Entry<String, Path> entry : adapterFiles.entrySet()) {
                if (entry.getKey().startsWith(majorVersion)) {
                    Logger.info("Using major version adapter: " + entry.getKey());
                    return entry.getValue();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Gets the major version from a version string.
     * 
     * @param version The version string (e.g., "1.20.6")
     * @return The major version (e.g., "1.20"), or null
     */
    private String getMajorVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return null;
    }
    
    /**
     * Loads an adapter from a file.
     * 
     * @param path Path to the adapter file
     * @return The loaded adapter, or null if loading failed
     */
    private VersionAdapter loadAdapterFromFile(Path path) {
        try {
            // Read the adapter file (which is essentially a JAR with JSON manifest)
            return new JsonVersionAdapter(path);
        } catch (Exception e) {
            Logger.error("Failed to load adapter from: " + path + " - " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets all available adapter IDs.
     * 
     * @return Set of adapter IDs
     */
    public Set<String> getAvailableAdapters() {
        return new HashSet<>(adapterFiles.keySet());
    }
    
    /**
     * Clears the adapter cache.
     */
    public void clearCache() {
        adapterCache.clear();
        Logger.info("Adapter cache cleared");
    }
    
    /**
     * Reloads all adapters from disk.
     */
    public void reload() {
        adapterCache.clear();
        adapterFiles.clear();
        scanAdapterFiles();
        Logger.info("AdapterLoader reloaded with " + adapterFiles.size() + " adapter(s)");
    }
}
