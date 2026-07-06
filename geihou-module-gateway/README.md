# geihou-module-gateway

Status: empty gateway module JAR baseline.

Package root: `com.geihou.gateway`

Reserved port: `48080`

This module is part of the standalone Geihou aggregator only and is not runtime-ready.

Build role: gateway module baseline with Geihou BOM import.

Rules:

- Do not add direct dependencies without a reviewed task package.
- Do not add routes or runtime configuration in the skeleton slice.
- Do not modify legacy gateway files from this skeleton task.
- Do not treat this baseline as service-ready.
