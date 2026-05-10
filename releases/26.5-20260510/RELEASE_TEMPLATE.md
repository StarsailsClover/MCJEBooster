# MCJEBooster Release Notes

**Release Version:** {{VERSION}}  
**Release Date:** {{DATE}}  
**Injector Version:** {{INJECTOR_VERSION}}

---

## Overview

MCJEBooster is a Minecraft Java Edition Multi-Core Optimization Engine that uses JVM-level bytecode injection to enable parallel tick processing across multiple CPU cores.

---

## Injector Version Compatibility

| Injector Version | Status |
|-----------------|--------|
| {{INJECTOR_VERSION}} | ✅ Current Release |
| 26.4-20260510 | ⚠️ Deprecated |
| 26.3-20260510 | ⚠️ Deprecated |
| 26.2-20260510 | ⚠️ Deprecated |
| 26.1-20260510 | ⚠️ Deprecated |
| 26.0-20260510 | ❌ Unsupported |

**Note:** This release requires injector version {{INJECTOR_VERSION}} or higher. Older injectors may not support all adapter features.

---

## What's New

### New Features
- Automatic adapter download from GitHub Releases
- Version-specific adapter system (30+ adapters)
- Multi-loader support (Vanilla, Forge, Fabric, NeoForge, LiteLoader, OptiFine)
- Health monitoring with automatic rollback
- TPS calculation and performance metrics
- Dynamic region load balancing

### Improvements
- Enhanced security with null checks and input validation
- Improved resource cleanup and shutdown procedures
- Atomic operations for thread-safe state management
- Better error handling and recovery mechanisms

---

## Fixed Issues

### Security Fixes
- Fixed potential null pointer vulnerabilities
- Added resource cleanup in all shutdown methods
- Fixed race conditions in RegionScheduler state management
- Added file size limits for adapter downloads (10MB max)
- Enhanced input validation for version strings and JSON parsing

### Bug Fixes
- Fixed incomplete cleanup in SyncPointManager shutdown
- Fixed memory leaks in executor services
- Fixed recursive transformation of MCJEBooster classes
- Fixed adapter validation failures

---

## Other Updates

### Documentation
- Added comprehensive test plan (TEST_PLAN.md)
- Added version compatibility matrix
- Added API documentation (English & Chinese)
- Added architecture documentation
- Added changelog

### Build System
- Added automated build scripts
- Added test runner scripts
- Added development environment setup scripts
- Added pre-commit hooks for security checks

### Adapter System
- 30 version-specific adapters created
- Automatic adapter matching based on game version and loader
- Checksum verification for downloaded adapters
- Fallback to bundled adapters on download failure

---

## Supported Game Versions

### ✅ Fully Supported

| Minecraft Version | Java | Loaders | Adapter Files |
|-------------------|------|---------|---------------|
| 1.8.9 | 8 | Vanilla, Forge, LiteLoader, OptiFine | 5 adapters |
| 1.12.2 | 8 | Vanilla, Forge, LiteLoader, OptiFine, Combinations | 8 adapters |
| 1.16.5 | 8/11 | Vanilla, Forge, Fabric, OptiFine | 5 adapters |
| 1.17.1 | 16 | Vanilla, Fabric | 2 adapters |
| 1.18.1 | 17 | Vanilla, Forge, Fabric | 3 adapters |
| 1.19.1 | 17 | Vanilla, Forge, Fabric | 3 adapters |
| 1.20.6 | 21 | Vanilla, Forge, NeoForge, Fabric | 4 adapters |
| 26.1.1 | 21 | Vanilla, Forge, NeoForge, Fabric | 4 adapters |

### 🔄 Planned Support

| Minecraft Version | Java | Loaders | Status |
|-------------------|------|---------|--------|
| 1.13.2 | 8 | Vanilla, Forge | Planned |
| 1.14.4 | 8 | Vanilla, Forge, Fabric | Planned |
| 1.15.2 | 8 | Vanilla, Forge, Fabric | Planned |

---

## Adapter Downloads

Individual adapter files are available in the Assets section below. Download the adapter that matches your Minecraft version and mod loader:

**Naming Convention:** `{minecraft_version}-{loader}.mcjeb`

Examples:
- `1.20.6-Vanilla.mcjeb` - For Minecraft 1.20.6 without mods
- `1.20.6-Forge.mcjeb` - For Minecraft 1.20.6 with Forge
- `1.20.6-Fabric.mcjeb` - For Minecraft 1.20.6 with Fabric
- `1.12.2-Forge-LiteLoader.mcjeb` - For 1.12.2 with Forge + LiteLoader

The injector will automatically detect your Minecraft version and download the appropriate adapter.

---

## Installation

### Method 1: Automatic Injection (Recommended)

1. Start Minecraft
2. Download and run the injector:
   ```bash
   java -jar MCJEBooster-{{INJECTOR_VERSION}}.jar
   ```
3. Select your Minecraft process
4. The injector will automatically download and apply the correct adapter

### Method 2: Java Agent Mode

Add to JVM arguments:
```bash
-javaagent:/path/to/MCJEBooster-{{INJECTOR_VERSION}}.jar
```

---

## Requirements

### Minimum Requirements
- Java 8 (for Minecraft 1.8.9 - 1.16.5)
- Java 16 (for Minecraft 1.17.x)
- Java 17 (for Minecraft 1.18.x - 1.20.4)
- Java 21 (for Minecraft 1.20.5+)

### Recommended JVM Arguments
```bash
-XX:+UseG1GC
-XX:MaxGCPauseMillis=50
--add-opens=java.base/java.util.jar=ALL-UNNAMED
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED
```

For Minecraft 26.x+:
```bash
-XX:+UseZGC
-XX:+UseStringDeduplication
```

---

## Known Issues

### 1.12.2
- Some Forge mods may conflict with multi-threading
- OptiFine's lazy chunk loading may conflict with async chunk loading

### 1.16.5
- Fabric API events must be posted on main thread
- OptiFabric compatibility needs verification

### 1.20.6+
- Component system changes may affect some optimizations
- ZGC tuning parameters need more testing

---

## Performance Expectations

| Scenario | Vanilla TPS | With MCJEBooster | Improvement |
|----------|-------------|------------------|-------------|
| Empty World | 20.0 | 20.0 | 0% |
| 5000 Entities | 12.3 | 24.1 | +96% |
| Redstone Circuit | 8.7 | 15.5 | +78% |
| View Distance 32 | 14.2 | 30.1 | +112% |
| 10 Players | 11.5 | 21.3 | +85% |

*Results may vary based on hardware and world complexity*

---

## Troubleshooting

### "No adapter found for version X"
- Check that your Minecraft version is supported
- Download the appropriate adapter manually from Assets
- Verify the adapter file is not corrupted

### "Injection failed"
- Ensure you're using JDK (not just JRE)
- Check that Minecraft is running
- Try running injector with administrator privileges
- Check logs for specific error messages

### "TPS not improving"
- Check if your world is CPU-bound or I/O-bound
- Verify adapter is loaded correctly
- Try adjusting worker thread count

---

## Checksums

Verify file integrity using SHA256:

```
{{CHECKSUMS}}
```

---

## Credits

**Author:** StarsailsClover  
**License:** LGPL-2.1  
**Repository:** https://github.com/StarsailsClover/MCJEBooster

### Third-Party Libraries
- ASM 9.6 - Bytecode manipulation
- JSON-java - JSON processing

---

## Support

- **Issues:** https://github.com/StarsailsClover/MCJEBooster/issues
- **Documentation:** See docs/ directory
- **Test Plan:** TEST_PLAN.md

---

## Disclaimer

MCJEBooster is an independent third-party tool. It is not affiliated with Mojang Studios or Microsoft. Use at your own risk. Always backup your worlds before using optimization tools.

---

*Release {{VERSION}} - {{DATE}}*
