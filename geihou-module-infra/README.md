# geihou-module-infra

Status: infra module aggregator with a compile-only biz persistence slice.

Package root: `com.geihou.module.infra`

Reserved port: `48093`

This module is part of the standalone Geihou aggregator only and is not runtime-ready.

Build role: parent POM aggregator with `geihou-module-infra-biz` registered as a compile-only persistence slice.

Rules:

- Do not add direct dependencies without a reviewed task package.
- Do not add runtime configuration, codegen, file, or datasource implementation in the compile-only slice.
- Do not create `geihou-module-infra-api` until a PRD 0-03 task package authorizes it.
- Do not add further infra DO/Mapper classes, service logic, or health-check wiring without a reviewed task package.
- Do not modify legacy infra files from this skeleton task.
- Do not treat this baseline as service-ready.
