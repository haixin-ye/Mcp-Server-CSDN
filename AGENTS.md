# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Maven project for a Spring AI MCP server that publishes CSDN articles. Production code lives under `src/main/java/cn/bugstack/mcp/server/csdn` and is split by responsibility:

- `domain/`: tool-facing models, ports, and services
- `infrastructure/`: Retrofit adapters and gateway DTOs
- `types/`: shared properties and utility classes
- `src/main/resources/application.yml`: runtime configuration
- `src/test/java/.../test`: test classes such as `ApiTest`

Build output is generated in `target/`; do not edit committed artifacts there.

## Build, Test, and Development Commands
Use Java 17 and Maven.

- `mvn clean package`: compile, run tests, and build the executable jar
- `mvn test`: run the test suite only
- `mvn spring-boot:run`: start the MCP server locally
- `java -jar target/mcp-server-csdn-1.0.0.jar`: run the packaged application
- `docker build -t mcp-server-csdn .`: 构建本地 Docker 镜像
- `docker run -p 18080:18080 mcp-server-csdn`: 运行本地 MCP 服务容器

Set required credentials before local runs, for example: `CSDN_COOKIE`, `X_CA_NONCE`, and `X_CA_SIGNATURE`.

## Coding Style & Naming Conventions
Follow standard Java conventions with 4-space indentation and UTF-8 source files. Keep packages under `cn.bugstack.mcp.server.csdn`. Use:

- `UpperCamelCase` for classes
- `lowerCamelCase` for methods and fields
- clear suffixes such as `Service`, `Port`, `DTO`, and `Properties`

Prefer constructor or Spring-managed dependency injection patterns already used in the codebase, and keep API mapping logic inside `infrastructure`.

## Testing Guidelines
Tests use JUnit with Spring Boot test support. Name test classes `*Test` and test methods with a `test_` prefix, matching the existing style. Run `mvn test` before opening a PR.

`ApiTest` is integration-oriented and may require real CSDN credentials. Do not hardcode cookies or tokens in test sources; load them from environment variables or local-only config.

## Commit & Pull Request Guidelines
Git history is not available in this workspace snapshot, so no repository-specific commit convention could be verified. Use short, imperative commit messages such as `add article publish validation`.

PRs should include a concise summary, affected packages, configuration changes, and test evidence. If behavior changes affect request/response payloads or logging, include sample input/output.

## Security & Configuration Tips
Keep secrets out of `application.yml` and source files. Prefer environment variables and verify logs do not expose cookies, signatures, or other sensitive request data.
