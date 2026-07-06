# geihou-module-system

Status: POM aggregator with system API contracts and a compile-only system-biz persistence slice.

Package root: `com.geihou.module.system`

Reserved port: `48092`

This module is part of the standalone Geihou aggregator only and is not runtime-ready.

Submodules:

- `geihou-module-system-api`: pure Java `TenantApi` interface and `TenantRespDTO`.
- `geihou-module-system-biz`: PRD 0-01 tenant DO/Mapper persistence slice with test-scope mapper DB smoke coverage. No service/controller runtime logic yet.

Build role: system module baseline with Geihou BOM import.

Runtime readiness: false.

Rules:

- Do not add direct dependencies without a reviewed task package.
- Do not add controllers, services, converters, or implementations without a reviewed task package.
- Do not add more mappers or data objects beyond the reviewed PRD 0-01 tenant persistence slices without a reviewed task package.
- Do not add runtime configuration.
- Do not treat this baseline as service-ready or runtime-ready.
