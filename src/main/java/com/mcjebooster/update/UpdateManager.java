/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.update;

import com.mcjebooster.util.Logger;
import com.mcjebooster.adapter.VersionAdapter;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Update Manager for MCJEBooster.
 * 
 * Automatically detects Minecraft version and downloads the appropriate
 * adapter package from GitHub Releases.
 * 
 * Features:
 * - Automatic version detection
 * - GitHub Release API integration
 * - Adapter package download and caching
 * - Checksum verification
 * - Fallback to bundled adapters
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 */
public class UpdateManager {
    
    /** Singleton instance */
    private static volatile UpdateManager INSTANCE;
    
    /** GitHub API base URL */
    private static final String GITHUB_API_BASE = "https://api.github.com/repos/StarsailsClover/MCJEBooster";
    
    /** GitHub Releases URL */
    private static final String GITHUB_RELEASES_URL = GITHUB_API_BASE + "/releases";
    
    /** GitHub raw content URL */
    private static final String GITHUB_RAW_URL = "https://raw.githubusercontent.com/StarsailsClover/MCJEBooster/main";
    
    /** Local adapter cache directory */
    private Path adapterCacheDir;
    
    /** HTTP connection timeout */
    private static final int CONNECT_TIMEOUT_MS = 10000;
    
    /** HTTP read timeout */
    private static final int READ_TIMEOUT_MS = 30000;
    
    /** Download executor */
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2);
    
    /** Update check interval (24 hours) */
    private static final long UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000;
    
    /** Last update check time */
    private volatile long lastUpdateCheck = 0;
    
    /** Cached release info */
    private volatile ReleaseInfo cachedRelease;
    
    /**
     * Release information container
     */
    public static class ReleaseInfo {
        public final String version;
        public final String tagName;
        public final String downloadUrl;
        public final String checksum;
        public final long publishedAt;
        public final Map<String, String> adapterAssets;
        
        public ReleaseInfo(String version, String tagName, String downloadUrl, 
                          String checksum, long publishedAt, Map<String, String> adapterAssets) {
            this.version = version;
            this.tagName = tagName;
            this.downloadUrl = downloadUrl;
            this.checksum = checksum;
            this.publishedAt = publishedAt;
            this.adapterAssets = adapterAssets;
        }
    }
    
    /**
     * Private constructor
     */
    private UpdateManager() {
        initializeCacheDirectory();
    }
    
    /**
     * Gets the singleton instance
     */
    public static UpdateManager getInstance() {
        if (INSTANCE == null) {
            synchronized (UpdateManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new UpdateManager();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Initializes the adapter cache directory
     */
    private void initializeCacheDirectory() {
        String userHome = System.getProperty("user.home");
        this.adapterCacheDir = Paths.get(userHome, ".mcjebooster", "adapters");
        
        try {
            Files.createDirectories(adapterCacheDir);
        } catch (IOException e) {
            Logger.error("Failed to create adapter cache directory: " + e.getMessage());
        }
    }
    
    /**
     * Automatically detects version and downloads appropriate adapter
     * 
     * @param minecraftVersion Detected Minecraft version
     * @param loaderType Detected loader type
     * @param loaderVersion Detected loader version (can be null)
     * @return Path to downloaded adapter, or null if download failed
     */
    public Path autoDownloadAdapter(String minecraftVersion, 
                                   VersionAdapter.LoaderType loaderType,
                                   String loaderVersion) {
        String adapterId = buildAdapterId(minecraftVersion, loaderType, loaderVersion);
        
        Logger.info("Auto-downloading adapter: " + adapterId);
        
        // Check local cache first
        Path cachedAdapter = findCachedAdapter(adapterId);
        if (cachedAdapter != null) {
            Logger.info("Found cached adapter: " + cachedAdapter);
            return cachedAdapter;
        }
        
        // Check for updates from GitHub
        ReleaseInfo release = checkForUpdates();
        if (release == null) {
            Logger.warn("Could not check for updates, trying bundled adapters");
            return findBundledAdapter(adapterId);
        }
        
        // Download adapter from release
        String assetName = adapterId + ".mcjeb";
        String downloadUrl = release.adapterAssets.get(assetName);
        
        if (downloadUrl == null) {
            // Try to find compatible adapter
            Logger.warn("Adapter not found for " + adapterId + ", searching for compatible version");
            downloadUrl = findCompatibleAdapterUrl(release, minecraftVersion, loaderType);
        }
        
        if (downloadUrl == null) {
            Logger.error("No compatible adapter found for: " + adapterId);
            return findBundledAdapter(adapterId);
        }
        
        // Download the adapter
        Path downloadedPath = downloadAdapter(downloadUrl, adapterId);
        if (downloadedPath != null) {
            Logger.info("Successfully downloaded adapter: " + downloadedPath);
        }
        
        return downloadedPath;
    }
    
    /**
     * Checks for updates from GitHub Releases
     * 
     * @return ReleaseInfo or null if check failed
     */
    public ReleaseInfo checkForUpdates() {
        // Check if we need to refresh
        long now = System.currentTimeMillis();
        if (cachedRelease != null && (now - lastUpdateCheck) < UPDATE_CHECK_INTERVAL_MS) {
            return cachedRelease;
        }
        
        try {
            String apiUrl = GITHUB_RELEASES_URL + "/latest";
            String response = httpGet(apiUrl);
            
            if (response == null) {
                return null;
            }
            
            // Parse JSON response
            ReleaseInfo release = parseReleaseJson(response);
            
            if (release != null) {
                cachedRelease = release;
                lastUpdateCheck = now;
                
                Logger.info("Found latest release: " + release.tagName);
            }
            
            return release;
            
        } catch (Exception e) {
            Logger.error("Failed to check for updates: " + e.getMessage());
            return cachedRelease; // Return cached if available
        }
    }
    
    /**
     * Parses GitHub release JSON
     */
    private ReleaseInfo parseReleaseJson(String json) {
        try {
            // Simple JSON parsing (in production, use a proper JSON library)
            String tagName = extractJsonValue(json, "tag_name");
            String publishedAt = extractJsonValue(json, "published_at");
            
            // Parse assets
            Map<String, String> adapterAssets = new HashMap<>();
            int assetsStart = json.indexOf("\"assets\":");
            if (assetsStart != -1) {
                int assetsEnd = json.indexOf("]", assetsStart);
                String assetsSection = json.substring(assetsStart, assetsEnd);
                
                // Extract browser_download_url for each asset
                int urlIndex = 0;
                while ((urlIndex = assetsSection.indexOf("\"browser_download_url\":", urlIndex)) != -1) {
                    int urlStart = assetsSection.indexOf("\"", urlIndex + 23) + 1;
                    int urlEnd = assetsSection.indexOf("\"", urlStart);
                    String url = assetsSection.substring(urlStart, urlEnd);
                    
                    // Extract name
                    int nameIndex = assetsSection.lastIndexOf("\"name\":", urlIndex);
                    if (nameIndex != -1) {
                        int nameStart = assetsSection.indexOf("\"", nameIndex + 7) + 1;
                        int nameEnd = assetsSection.indexOf("\"", nameStart);
                        String name = assetsSection.substring(nameStart, nameEnd);
                        
                        if (name.endsWith(".mcjeb")) {
                            adapterAssets.put(name, url);
                        }
                    }
                    
                    urlIndex = urlEnd;
                }
            }
            
            return new ReleaseInfo(
                tagName,
                tagName,
                null,
                null,
                System.currentTimeMillis(),
                adapterAssets
            );
            
        } catch (Exception e) {
            Logger.error("Failed to parse release JSON: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Downloads adapter from URL
     */
    private Path downloadAdapter(String downloadUrl, String adapterId) {
        Path targetPath = adapterCacheDir.resolve(adapterId + ".mcjeb");
        Path tempPath = adapterCacheDir.resolve(adapterId + ".mcjeb.tmp");
        
        try {
            Logger.info("Downloading adapter from: " + downloadUrl);
            
            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/octet-stream");
            
            // Add GitHub API headers
            conn.setRequestProperty("User-Agent", "MCJEBooster-UpdateManager");
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                Logger.error("Download failed with response code: " + responseCode);
                return null;
            }
            
            // Download to temporary file first
            long totalSize = conn.getContentLengthLong();
            long maxSize = 10 * 1024 * 1024; // 10MB max
            
            if (totalSize > maxSize) {
                Logger.error("File too large: " + totalSize + " bytes");
                return null;
            }
            
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(tempPath)) {
                
                byte[] buffer = new byte[8192];
                long downloaded = 0;
                int bytesRead;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    downloaded += bytesRead;
                    
                    // Check size limit during download
                    if (downloaded > maxSize) {
                        Logger.error("Download exceeded maximum size");
                        throw new IOException("File too large");
                    }
                    
                    out.write(buffer, 0, bytesRead);
                    
                    // Log progress every 100KB
                    if (downloaded % (100 * 1024) < 8192) {
                        int percent = totalSize > 0 ? (int) ((downloaded * 100) / totalSize) : 0;
                        Logger.debug("Download progress: " + percent + "%");
                    }
                }
            }
            
            // Verify checksum if available
            if (cachedRelease != null && cachedRelease.checksum != null) {
                String fileChecksum = calculateChecksum(tempPath);
                if (fileChecksum == null || !fileChecksum.equalsIgnoreCase(cachedRelease.checksum)) {
                    Logger.error("Checksum mismatch for downloaded adapter");
                    Files.deleteIfExists(tempPath);
                    return null;
                }
            }
            
            // Atomic move to final location
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            
            return targetPath;
            
        } catch (Exception e) {
            Logger.error("Failed to download adapter: " + e.getMessage());
            try {
                Files.deleteIfExists(tempPath);
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {}
            return null;
        }
    }
    
    /**
     * Finds compatible adapter URL for version/loader
     */
    private String findCompatibleAdapterUrl(ReleaseInfo release, 
                                           String minecraftVersion,
                                           VersionAdapter.LoaderType loaderType) {
        // Try exact match first
        String exactId = buildAdapterId(minecraftVersion, loaderType, null);
        String assetName = exactId + ".mcjeb";
        String url = release.adapterAssets.get(assetName);
        if (url != null) return url;
        
        // Try vanilla fallback
        if (loaderType != VersionAdapter.LoaderType.VANILLA) {
            String vanillaId = buildAdapterId(minecraftVersion, 
                VersionAdapter.LoaderType.VANILLA, null);
            url = release.adapterAssets.get(vanillaId + ".mcjeb");
            if (url != null) {
                Logger.info("Using vanilla adapter as fallback for " + minecraftVersion);
                return url;
            }
        }
        
        // Try major version match
        String majorVersion = getMajorVersion(minecraftVersion);
        if (majorVersion != null) {
            for (Map.Entry<String, String> entry : release.adapterAssets.entrySet()) {
                if (entry.getKey().startsWith(majorVersion)) {
                    Logger.info("Using major version adapter: " + entry.getKey());
                    return entry.getValue();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Finds cached adapter
     */
    private Path findCachedAdapter(String adapterId) {
        Path cachedPath = adapterCacheDir.resolve(adapterId + ".mcjeb");
        if (Files.exists(cachedPath)) {
            // Check if file is not empty
            try {
                if (Files.size(cachedPath) > 0) {
                    return cachedPath;
                }
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Finds bundled adapter
     */
    private Path findBundledAdapter(String adapterId) {
        // Look in classpath resources
        String resourcePath = "/adapters/" + adapterId + ".mcjeb";
        
        try {
            URL resourceUrl = getClass().getResource(resourcePath);
            if (resourceUrl != null) {
                return Paths.get(resourceUrl.toURI());
            }
        } catch (Exception e) {
            Logger.debug("Bundled adapter not found: " + resourcePath);
        }
        
        // Look in local adapters directory
        Path localPath = Paths.get("adapters", adapterId + ".mcjeb");
        if (Files.exists(localPath)) {
            return localPath;
        }
        
        return null;
    }
    
    /**
     * Performs HTTP GET request
     */
    private String httpGet(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("User-Agent", "MCJEBooster-UpdateManager");
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                Logger.error("HTTP GET failed: " + responseCode + " for " + urlString);
                return null;
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
            
        } catch (Exception e) {
            Logger.error("HTTP GET failed: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Calculates SHA-256 checksum of file
     */
    private String calculateChecksum(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
            
        } catch (Exception e) {
            Logger.error("Failed to calculate checksum: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts JSON string value with bounds checking
     */
    private String extractJsonValue(String json, String key) {
        if (json == null || key == null || json.isEmpty() || key.isEmpty()) {
            return null;
        }
        
        String search = "\"" + key + "\"";
        int index = json.indexOf(search);
        if (index == -1) return null;
        
        int valueStart = json.indexOf(":", index);
        if (valueStart == -1) return null;
        valueStart++;
        
        // Skip whitespace and quotes
        while (valueStart < json.length() && 
               (json.charAt(valueStart) == ' ' || json.charAt(valueStart) == '"')) {
            valueStart++;
        }
        
        if (valueStart >= json.length()) return null;
        
        int valueEnd = json.indexOf("\"", valueStart);
        if (valueEnd == -1) {
            valueEnd = json.indexOf(",", valueStart);
        }
        if (valueEnd == -1) {
            valueEnd = json.indexOf("}", valueStart);
        }
        
        if (valueEnd > valueStart && valueEnd < json.length()) {
            return json.substring(valueStart, valueEnd).trim();
        }
        
        return null;
    }
    
    /**
     * Builds adapter ID from version info
     */
    private String buildAdapterId(String minecraftVersion, 
                                 VersionAdapter.LoaderType loaderType,
                                 String loaderVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append(minecraftVersion);
        
        if (loaderType != VersionAdapter.LoaderType.VANILLA) {
            sb.append("-").append(loaderType.name());
            if (loaderVersion != null && !loaderVersion.isEmpty()) {
                sb.append("_").append(loaderVersion);
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Gets major version
     */
    private String getMajorVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return null;
    }
    
    /**
     * Clears the adapter cache
     */
    public void clearCache() {
        try {
            Files.walk(adapterCacheDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        Logger.warn("Failed to delete cached adapter: " + path);
                    }
                });
            Logger.info("Adapter cache cleared");
        } catch (IOException e) {
            Logger.error("Failed to clear adapter cache: " + e.getMessage());
        }
    }
    
    /**
     * Gets cache directory path
     */
    public Path getCacheDirectory() {
        return adapterCacheDir;
    }
    
    /**
     * Shuts down the update manager
     */
    public void shutdown() {
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            downloadExecutor.shutdownNow();
        }
    }
}
