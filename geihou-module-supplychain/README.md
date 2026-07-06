# geihou-module-supplychain

Status: empty supplychain module JAR baseline.

Package root: `com.geihou.module.supplychain`

Reserved port: `48082`

This module belongs to the standalone Geihou aggregator only and is not runtime-ready.

It imports the Geihou BOM for dependency management, but has no direct dependencies.

Rules:

- Do not migrate kitchen, KDS, printer, store, table, procurement, supplier, inventory, warehouse, transfer, or BOM legacy code in this baseline slice.
- Do not add Java implementation or runtime configuration.
- Do not treat this baseline as a running supplychain service.
