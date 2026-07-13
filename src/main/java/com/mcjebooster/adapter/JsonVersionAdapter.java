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
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/**
 * JSON-based implementation of VersionAdapter.
 * 
 * Loads adapter configuration from a JSON file embedded in a .mcjeb archive.
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 */
public class JsonVersionAdapter implements VersionAdapter {
    
    /** The adapter configuration */
    private final JSONObject config;
    
    /** The adapter file path */
    private final Path adapterPath;
    
    /** Class mappings */
    private final Map<String, String> classMappings = new HashMap<>();
    
    /** Method mappings */
    private final Map<String, String> methodMappings = new HashMap<>();
    
    /** Field mappings */
    private final Map<String, String> fieldMappings = new HashMap<>();
    
    /** Method descriptors */
    private final Map<String, String> methodDescriptors = new HashMap<>();
    
    /** Supported features */
    private final Set<Feature> supportedFeatures = new HashSet<>();
    
    /**
     * Constructs a JsonVersionAdapter from a .mcjeb file.
     * 
     * @param path Path to the .mcjeb adapter file
     * @throws IOException if reading fails
     */
    public JsonVersionAdapter(Path path) throws IOException {
        this.adapterPath = path;
        this.config = loadConfig(path);
        parseMappings();
        parseFeatures();
    }
    
    /**
     * Loads the configuration from the adapter file.
     * 
     * @param path Path to the adapter file
     * @return The JSON configuration
     * @throws IOException if reading fails
     */
    private JSONObject loadConfig(Path path) throws IOException {
        String content = readAdapterJson(path);
        return new JSONObject(content);
    }

    private String readAdapterJson(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            JarEntry entry = jar.getJarEntry("adapter.json");
            if (entry == null) {
                throw new FileNotFoundException("Missing adapter.json in adapter archive: " + path);
            }
            try (InputStream in = jar.getInputStream(entry)) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (java.util.zip.ZipException e) {
            return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    
    /**
     * Parses class/method/field mappings from the configuration.
     */
    private void parseMappings() {
        // Parse class mappings
        if (config.has("classMappings")) {
            JSONObject mappings = config.getJSONObject("classMappings");
            for (String key : mappings.keySet()) {
                classMappings.put(key, mappings.getString(key));
            }
        }
        
        // Parse method mappings
        if (config.has("methodMappings")) {
            JSONObject mappings = config.getJSONObject("methodMappings");
            for (String key : mappings.keySet()) {
                methodMappings.put(key, mappings.getString(key));
            }
        }
        
        // Parse field mappings
        if (config.has("fieldMappings")) {
            JSONObject mappings = config.getJSONObject("fieldMappings");
            for (String key : mappings.keySet()) {
                fieldMappings.put(key, mappings.getString(key));
            }
        }
        
        // Parse method descriptors
        if (config.has("methodDescriptors")) {
            JSONObject descriptors = config.getJSONObject("methodDescriptors");
            for (String key : descriptors.keySet()) {
                methodDescriptors.put(key, descriptors.getString(key));
            }
        }
    }
    
    /**
     * Parses supported features from the configuration.
     */
    private void parseFeatures() {
        if (config.has("supportedFeatures")) {
            JSONArray features = config.getJSONArray("supportedFeatures");
            for (int i = 0; i < features.length(); i++) {
                try {
                    Feature feature = Feature.valueOf(features.getString(i));
                    supportedFeatures.add(feature);
                } catch (IllegalArgumentException e) {
                    Logger.warn("Unknown feature in adapter: " + features.getString(i));
                }
            }
        }
    }
    
    @Override
    public String getAdapterId() {
        return config.optString("adapterId", "unknown");
    }
    
    @Override
    public String getMinecraftVersion() {
        return config.optString("minecraftVersion", "unknown");
    }
    
    @Override
    public LoaderType getLoaderType() {
        String type = config.optString("loaderType", "VANILLA");
        try {
            return LoaderType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return LoaderType.UNKNOWN;
        }
    }
    
    @Override
    public String getLoaderVersion() {
        return config.optString("loaderVersion", null);
    }
    
    @Override
    public int getRequiredJavaVersion() {
        return config.optInt("requiredJavaVersion", 8);
    }
    
    @Override
    public Map<String, String> getClassMappings() {
        return new HashMap<>(classMappings);
    }
    
    @Override
    public Map<String, String> getMethodMappings() {
        return new HashMap<>(methodMappings);
    }
    
    @Override
    public Map<String, String> getFieldMappings() {
        return new HashMap<>(fieldMappings);
    }
    
    @Override
    public Map<String, String> getMethodDescriptors() {
        return new HashMap<>(methodDescriptors);
    }
    
    @Override
    public String getTickMethodTarget() {
        return config.optString("tickMethodTarget", "MinecraftServer.tick");
    }
    
    @Override
    public String getEntityTickMethodTarget() {
        return config.optString("entityTickMethodTarget", "World.tickEntities");
    }
    
    @Override
    public String getBlockTickMethodTarget() {
        return config.optString("blockTickMethodTarget", "World.tickBlocks");
    }
    
    @Override
    public String getChunkProviderClass() {
        return config.optString("chunkProviderClass", "ChunkProvider");
    }
    
    @Override
    public String getWorldClass() {
        return config.optString("worldClass", "World");
    }
    
    @Override
    public String getEntityClass() {
        return config.optString("entityClass", "Entity");
    }
    
    @Override
    public String getEntityListField() {
        return config.optString("entityListField", "loadedEntityList");
    }
    
    @Override
    public int getRegionSize() {
        return config.optInt("regionSize", 16);
    }
    
    @Override
    public int getRecommendedWorkerCount() {
        return config.optInt("recommendedWorkerCount", 
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }
    
    @Override
    public long getTickTimeoutMs() {
        return config.optLong("tickTimeoutMs", 45);
    }
    
    @Override
    public boolean supportsFeature(Feature feature) {
        return supportedFeatures.contains(feature);
    }
    
    @Override
    public Set<Feature> getSupportedFeatures() {
        return new HashSet<>(supportedFeatures);
    }
    
    @Override
    public String[] getRequiredJvmArgs() {
        if (config.has("requiredJvmArgs")) {
            JSONArray args = config.getJSONArray("requiredJvmArgs");
            String[] result = new String[args.length()];
            for (int i = 0; i < args.length(); i++) {
                result[i] = args.getString(i);
            }
            return result;
        }
        return new String[0];
    }
    
    @Override
    public int getPriority() {
        return config.optInt("priority", 100);
    }
    
    @Override
    public boolean validate() {
        // Check required fields
        if (!config.has("minecraftVersion")) {
            Logger.error("Adapter missing minecraftVersion: " + adapterPath);
            return false;
        }
        
        // Check Java version compatibility
        int requiredJava = getRequiredJavaVersion();
        int currentJava = Runtime.version().feature();
        if (currentJava < requiredJava) {
            Logger.error("Java version mismatch: adapter requires " + requiredJava + 
                        ", current is " + currentJava);
            return false;
        }
        
        return true;
    }
    
    @Override
    public String getAdapterVersion() {
        return config.optString("adapterVersion", "1.0");
    }
    
    /**
     * Gets the raw JSON configuration.
     * 
     * @return The JSON configuration object
     */
    public JSONObject getRawConfig() {
        return config;
    }
    
    /**
     * Gets the adapter file path.
     * 
     * @return Path to the adapter file
     */
    public Path getAdapterPath() {
        return adapterPath;
    }
}
