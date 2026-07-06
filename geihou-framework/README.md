# geihou-framework

Status: POM aggregator with MyBatis and tenant framework starters.

Package root: `com.geihou.framework`

This module is part of the standalone Geihou aggregator only and is not runtime-ready.

Submodules:

- `geihou-spring-boot-starter-mybatis`: `BaseMapperX`, `PageResult` conversion, MyBatis-Plus pagination wiring, and test-scope H2 DB smoke coverage.
- `geihou-spring-boot-starter-tenant`: tenant context, tenant ignore, and tenant-line MyBatis wiring primitives.

Build role: framework compile baseline with Geihou BOM import.

Runtime readiness: false.

Rules:

- Do not add direct dependencies without a reviewed task package.
- Do not add cache, gateway, security, or RPC behavior without a reviewed task package.
- Do not extend MyBatis or tenant runtime wiring beyond the reviewed G0-01/G0-02 starter slices without a reviewed task package.
- Do not treat this baseline as service-ready or runtime-ready.
