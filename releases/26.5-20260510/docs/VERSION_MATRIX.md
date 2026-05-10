# MCJEBooster Version Compatibility Matrix

**Document Version:** v26.1-20260510  
**Last Updated:** 2026-05-10

---

## Supported Versions

| Minecraft Version | Java Version | Loader | Status | Adapter ID |
|-------------------|--------------|--------|--------|------------|
| 1.12.2 | 8 | Vanilla | ✅ Supported | 1.12.2-Vanilla |
| 1.12.2 | 8 | Forge 14.23.5.x | ✅ Supported | 1.12.2-Forge |
| 1.12.2 | 8 | LiteLoader | 🔄 Planned | 1.12.2-LiteLoader |
| 1.12.2 | 8 | Forge+LiteLoader | 🔄 Planned | 1.12.2-Forge_LITELOADER |
| 1.12.2 | 8 | OptiFine | 🔄 Planned | 1.12.2-OptiFine |
| 1.12.2 | 8 | Forge+OptiFine | 🔄 Planned | 1.12.2-Forge_OptiFine |
| 1.16.5 | 8/11 | Vanilla | ✅ Supported | 1.16.5-Vanilla |
| 1.16.5 | 8/11 | Fabric 0.19.x | ✅ Supported | 1.16.5-Fabric |
| 1.16.5 | 8/11 | Fabric+OptiFine | 🔄 Planned | 1.16.5-Fabric_OptiFine |
| 1.17.1 | 16 | Vanilla | 🔄 Planned | 1.17.1-Vanilla |
| 1.17.1 | 16 | Fabric | 🔄 Planned | 1.17.1-Fabric |
| 1.18.1 | 17 | Vanilla | ✅ Supported | 1.18.1-Vanilla |
| 1.18.1 | 17 | Forge | 🔄 Planned | 1.18.1-Forge |
| 1.18.1 | 17 | Fabric | 🔄 Planned | 1.18.1-Fabric |
| 1.19.1 | 17 | Vanilla | 🔄 Planned | 1.19.1-Vanilla |
| 1.19.1 | 17 | Forge | 🔄 Planned | 1.19.1-Forge |
| 1.20.6 | 21 | Vanilla | ✅ Supported | 1.20.6-Vanilla |
| 1.20.6 | 21 | Forge | 🔄 Planned | 1.20.6-Forge |
| 1.20.6 | 21 | NeoForge | 🔄 Planned | 1.20.6-NeoForge |
| 1.20.6 | 21 | Fabric | 🔄 Planned | 1.20.6-Fabric |
| 26.1.1 | 21 | Vanilla | ✅ Supported | 26.1.1-Vanilla |
| 26.1.1 | 21 | Forge | 🔄 Planned | 26.1.1-Forge |
| 26.1.1 | 21 | NeoForge | 🔄 Planned | 26.1.1-NeoForge |
| 26.1.1 | 21 | Fabric | 🔄 Planned | 26.1.1-Fabric |

---

## Legend

- ✅ Supported: Fully tested and working
- 🔄 Planned: Adapter exists but needs testing
- ⚠️ Partial: Some features may not work
- ❌ Not Supported: Known incompatibility

---

## Java Version Requirements

| Java Version | Minecraft Versions | Notes |
|--------------|-------------------|-------|
| Java 8 | 1.8.9 - 1.16.5 | Legacy support |
| Java 11 | 1.16.5+ | Optional for 1.16.5 |
| Java 16 | 1.17.x | Required for 1.17+ |
| Java 17 | 1.18.x - 1.20.4 | LTS version |
| Java 21 | 1.20.5+ | Required for 1.20.5+ |

---

## Adapter Package Structure

Each `.mcjeb` adapter package contains:

```
adapter.json          # Main configuration
manifest.json         # Package metadata
mappings/             # Class/method/field mappings
  classes.json        # Class name mappings
  methods.json        # Method name mappings
  fields.json         # Field name mappings
  descriptors.json    # Method descriptors
hooks/                # Injection hook points
  tick_hooks.json     # Tick method hooks
  entity_hooks.json   # Entity tick hooks
  block_hooks.json    # Block tick hooks
config/               # Version-specific config
  features.json       # Supported features
  jvm_args.json       # Required JVM arguments
  tuning.json         # Performance tuning params
```

---

## Download URLs

Adapters are automatically downloaded from:

```
https://github.com/StarsailsClover/MCJEBooster/releases/latest
```

Fallback to bundled adapters if download fails.

---

## Testing Status

| Version | Unit Tests | Integration Tests | Performance Tests |
|---------|------------|-------------------|-------------------|
| 1.12.2-Vanilla | ✅ | ✅ | 🔄 |
| 1.12.2-Forge | ✅ | 🔄 | 🔄 |
| 1.16.5-Fabric | ✅ | 🔄 | 🔄 |
| 1.18.1-Vanilla | ✅ | 🔄 | 🔄 |
| 1.20.6-Vanilla | ✅ | 🔄 | 🔄 |
| 26.1.1-Vanilla | ✅ | 🔄 | 🔄 |

---

## Known Issues

### 1.12.2
- Some Forge mods may conflict with multi-threading
- LiteLoader compatibility needs verification

### 1.16.5
- Fabric API events must be posted on main thread
- OptiFabric compatibility needs testing

### 1.20.6+
- Component system changes may affect some optimizations
- Java 21 ZGC tuning parameters need more testing

---

## Reporting Issues

Please report version-specific issues at:
https://github.com/StarsailsClover/MCJEBooster/issues

Include:
- Minecraft version
- Mod loader and version
- Java version
- Error logs
- Steps to reproduce
