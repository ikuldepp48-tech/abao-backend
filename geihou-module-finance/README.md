# geihou-module-finance

Status: finance module with product domain implementation (SPU, SKU, addon group, combo, category, price).

Package root: `com.geihou.module.finance`

Reserved port: `48081`

Build role: finance module with Geihou BOM import; product sub-domain is runtime-ready, other finance sub-domains remain reserved.

Rules:

- Do not add direct dependencies without a reviewed task package.
- Do not migrate order, payment, or restaurant legacy code without a reviewed task package.
- Do not treat unimplemented finance sub-domains as service-ready.
