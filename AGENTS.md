# Repository Guidelines

## Project Structure & Module Organization

- `src/main/java/org/jim/mcpmysqlserver/`: Spring Boot application code (controllers, services, config, MCP integration).
- `src/main/resources/`: runtime configuration and bundled assets:
  - `application.yml` / `application-stdio.yml`: HTTP vs stdio profile settings.
  - `datasource.yml` (+ `*-example.yml`): database connection definitions.
  - `extension.yml` + `groovy/**`: Groovy-based extensions and optional JAR dependencies.
- `src/test/java/`: JUnit/Spring Boot tests (keep unit tests fast; avoid external DB unless necessary).
- `docs/`, `examples/`: user-facing docs and sample MCP configs.
- `target/`: Maven build output (generated).

## Build, Test, and Development Commands

- `./mvnw spring-boot:run`: run locally (HTTP MCP endpoint defaults to `http://localhost:6789/mcp`).
- `./mvnw test`: run the test suite.
- `./mvnw clean package`: build a runnable JAR in `target/`.
- `java -jar target/mcp-mysql-server-*.jar --spring.profiles.active=stdio`: run in stdio mode (see `application-stdio.yml`).
- `java -jar target/mcp-mysql-server-*.jar --datasource.config=/path/to/datasource.yml`: override datasource config at startup.
- `java -jar target/mcp-mysql-server-*.jar --extension.config=/path/to/extension.yml`: override extension config at startup.
- `curl -X POST localhost:6789/api/datasource/reload`: apply datasource config edits to the running server without restarting.

## Runtime Datasource Reload

Editing `datasource.yml` no longer requires a restart. Edit the file, then trigger a reload via `POST /api/datasource/reload` or the `reloadDataSources` MCP tool.

- The config file is the single source of truth. There is deliberately no add/remove API — adding a datasource means editing the file, then reloading.
- Datasources whose config is unchanged keep their existing connection pool untouched. Removed datasources keep serving in-flight queries for `datasource.reload-grace-seconds` (default 30) before their pool closes.
- A malformed config file aborts the whole reload and changes nothing. A single unreachable database fails alone and is reported under `failed`, while the rest of the file still takes effect.
- Reload cannot conjure a JDBC driver that is not in the jar. Only MySQL, PostgreSQL, SQLite, and Apache IoTDB drivers are bundled; adding any other database still requires a `pom.xml` change and a rebuild.
- MCP tool schemas do not enumerate datasource names, so reloading never requires MCP clients to reconnect.

## Coding Style & Naming Conventions

- Java 21, Spring Boot 3.x. Prefer small methods and clear service boundaries.
- Indentation: 4 spaces, no tabs. Keep imports organized; avoid wildcard imports.
- Packages: `org.jim.mcpmysqlserver.<area>` (e.g., `service`, `controller`, `config`, `util`).
- Tests: `*Test.java` under `src/test/java/` mirroring the main package structure.

## Documentation Map (GEB L1/L2/L3)

- **L1 (repo root)**: Keep this `AGENTS.md` aligned with the real layout and contributor workflows.
- **L2 (module)**: If you add/remove/rename files or change public APIs inside a directory, add/update that directory’s `AGENTS.md` to list members (1 line per file) and a parent link.
- **L3 (file header)**: For business-facing files, keep a top-of-file contract comment describing INPUT/OUTPUT/POS plus: `[PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md`.
- Do not merge PRs with “code/document drift” (map must match territory).

## Testing Guidelines

- Framework: Spring Boot Test (JUnit 5 via `spring-boot-starter-test`).
- Keep new tests deterministic and independent; prefer unit tests over integration tests.
- Run locally with `./mvnw test` before opening a PR.

## Commit & Pull Request Guidelines

- Commits in this repo follow a simple imperative subject style (e.g., “Fix …”, “Add …”, “Refactor …”).
- PRs should include: purpose, key behavior changes, and any config updates (e.g., `datasource.yml`, `extension.yml`, `application*.yml`).
- If you touch Groovy extensions, include a minimal example in `examples/` and mention how to run it (HTTP vs stdio).
