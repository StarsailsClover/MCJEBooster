# MCJEBooster Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [v26.5-20260510] - 2026-05-10

### Added
- Comprehensive test plan (TEST_PLAN.md)
- Build release script (scripts/build-release.ps1)
- Test runner script (scripts/run-tests.ps1)
- Development environment setup script (scripts/setup-dev-env.ps1)
- Quick test script for Linux/Mac (scripts/quick-test.sh)
- Changelog documentation

### Security
- Code audit completed with security fixes

---

## [v26.4-20260510] - 2026-05-10

### Security
- Fixed null pointer vulnerabilities in multiple components
- Added resource cleanup in shutdown methods
- Changed volatile boolean to AtomicBoolean for thread safety
- Added input validation for version strings and JSON parsing
- Added file size limit (10MB) for adapter downloads
- Added bounds checking for JSON parsing
- Added package filtering to prevent recursive transformation
- Added exception handling in class transformer
- Added temporary file + atomic move for downloads
- Added checksum verification for downloaded adapters

### Changed
- Improved error handling in all shutdown methods
- Enhanced rollback mechanism in MCJEBoosterAgent
- Better resource management in UpdateManager

### Fixed
- Potential memory leaks in executor services
- Race conditions in RegionScheduler state management
- Incomplete cleanup in SyncPointManager shutdown

---

## [v26.3-20260510] - 2026-05-10

### Added
- Complete set of 30 adapter packages for all supported versions
- Adapters for 1.8.9 series (Vanilla, Forge, LiteLoader, OptiFine, combinations)
- Adapters for 1.12.2 series (all loader combinations)
- Adapters for 1.16.5 series (Vanilla, Forge, Fabric, OptiFine)
- Adapters for 1.17.1, 1.18.1, 1.19.1 series
- Adapters for 1.20.6 series (Vanilla, Forge, NeoForge, Fabric)
- Adapters for 26.1 series (Vanilla, Forge, NeoForge, Fabric)
- Version compatibility matrix (docs/VERSION_MATRIX.md)

---

## [v26.2-20260510] - 2026-05-10

### Added
- Version compatibility matrix documentation
- Additional vanilla adapters for 1.16.5, 1.17.1, 1.19.1
- UpdateManager for automatic adapter downloads from GitHub Releases

### Changed
- Enhanced adapter loading with fallback mechanisms
- Improved version detection with multiple strategies

---

## [v26.1-20260510] - 2026-05-10

### Added
- Initial adapter system with VersionAdapter interface
- AdapterLoader for loading .mcjeb adapter packages
- JsonVersionAdapter implementation
- UpdateManager for GitHub integration
- TaskScheduler for multi-level task scheduling

### Changed
- Refactored project structure for better organization
- Enhanced MinecraftServerTransformer with adapter support
- Improved RegionScheduler with adapter configuration

---

## [v26.0-20260510] - 2026-05-10

### Added
- Initial release of MCJEBooster
- Java Agent architecture for runtime injection
- External injector using Java Attach API
- Region-based multi-threaded scheduler
- ASM bytecode transformer
- SyncPointManager for thread synchronization
- VersionDetector for automatic version detection
- Logger utility for leveled logging
- Health monitoring with auto-rollback
- Support for Minecraft 1.8.9 - 26.1.1

### Features
- Multi-core tick processing
- Z-order spatial partitioning
- Dynamic load balancing
- Automatic fallback to single-threaded mode
- TPS monitoring and calculation
- Deadlock detection
- Health check system

---

## Planned Features

### v27.x (Future)
- [ ] GUI injector with process selection
- [ ] Real-time performance dashboard
- [ ] Advanced profiling integration
- [ ] Hot-swap adapter support
- [ ] Linux support improvements

### v28.x (Future)
- [ ] GPU acceleration for entity AI
- [ ] Predictive tick scheduling
- [ ] Machine learning for load prediction
- [ ] Distributed server support

---

## Version Numbering

MCJEBooster uses a custom versioning scheme:

```
v[YY].[PATCH]-[YYYYMMDD]

Examples:
- v26.5-20260510 = Year 26, Patch 5, Date 2026-05-10
- v26.4-20260510 = Year 26, Patch 4, Date 2026-05-10
```

This scheme allows for:
- Clear release date identification
- Sequential patch numbering within year
- Easy chronological sorting

---

## Support

For questions or issues, please refer to:
- GitHub Issues: https://github.com/StarsailsClover/MCJEBooster/issues
- Documentation: See docs/ directory
- Test Plan: TEST_PLAN.md
