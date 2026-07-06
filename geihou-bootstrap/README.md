# geihou-bootstrap

Local/dev bootstrap boundary for PRD 0-01 Flyway migration smoke.

This module belongs to the Geihou platform, not to the Abao tenant sample. It exists to prove that the group-0 migration candidates can be promoted into a classpath Flyway runtime path and executed against a MySQL-compatible database boundary.

## Scope

- Owns the first minimal `@SpringBootApplication` boundary.
- Owns `classpath:db/migration` runtime copies for PRD 0-01 SQL.
- Uses Testcontainers MySQL for the smoke test.
- Does not expose HTTP APIs.
- Does not implement controller, infra-api, G0-02 tenant runtime, PRD 0-03 OperateLog, scheduler, MQ, security, Swagger/OpenAPI, staging, or production configuration.

## Runtime SQL Policy

The root files under `/Users/mac/Desktop/abao-projects/abao-backend/db/migrations` remain candidate SQL and keep their `NOT RUNTIME-APPLIED` marker.

The files under this module's `src/main/resources/db/migration` are runtime copies. Their comments must be final before the first successful Flyway migration, because changing an applied migration file changes the Flyway checksum.

## Local Smoke Test

The canonical smoke command is:

```bash
mvn -f /Users/mac/Desktop/abao-projects/abao-backend/pom-geihou.xml -pl geihou-bootstrap test -Dtest=FlywayMigrationSmokeTest
```

On this local machine the Docker context is Colima, so Testcontainers needs the Colima socket and Ryuk disabled:

```bash
export DOCKER_HOST=unix:///Users/$(whoami)/.colima/default/docker.sock
export TESTCONTAINERS_RYUK_DISABLED=true
mvn -f /Users/mac/Desktop/abao-projects/abao-backend/pom-geihou.xml -pl geihou-bootstrap test -Dtest=FlywayMigrationSmokeTest
```

Do not put the local `DOCKER_HOST` value into CI. GitHub-hosted Linux runners should use their normal Docker environment unless a workflow run proves otherwise.
