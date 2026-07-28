# Changelog

All notable changes to Modular Maze Engine are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and releases use [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.2] - 2026-07-28

### Security

- Updated PMD from 7.15.0 to 7.22.0, resolving reported vulnerabilities in
  the transitive PMD core and Apache Commons Lang tooling dependencies.

## [1.0.1] - 2026-07-27

### Changed

- Updated JavaCC from 7.0.12 to 7.0.13.
- Updated the Jython standalone runtime from 2.7.3 to 2.7.4.

## [1.0.0] - 2026-07-27

### Added

- Stable extension contracts for plugin lifecycle, movement, item, menu, and
  interactive command events.
- Complete serializable session snapshots covering the board, visibility,
  inventory, position, time, and plugin-relevant flags.
- Project-specific PMD quality gates and hosted Java CI.
- Verified default map launch plus custom-map support through Gradle.

### Changed

- Reorganized the application into API, engine, UI, scripting, persistence,
  parser, and independent plugin modules.
- Renamed packages and runtime classes under the
  `io.github.himath2002.maze` namespace.
- Decoupled the core and Swing interface from concrete plugin implementations.
- Reworked the interface around a focused coastal exploration palette and
  contract-driven extension actions.
- Optimized runtime artwork for a substantially smaller repository footprint.

### Removed

- Generated build output, IDE metadata, packaged archives, and legacy
  publication artifacts.
- The former position-only undo control, which could not safely restore
  inventory, obstacle, script, or plugin state.

[1.0.2]: https://github.com/Himath2002/modular-maze-engine/releases/tag/v1.0.2
[1.0.1]: https://github.com/Himath2002/modular-maze-engine/releases/tag/v1.0.1
[1.0.0]: https://github.com/Himath2002/modular-maze-engine/releases/tag/v1.0.0
