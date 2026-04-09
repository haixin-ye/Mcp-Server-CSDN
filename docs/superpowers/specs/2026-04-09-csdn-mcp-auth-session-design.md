# CSDN MCP Auth Session Design

## Goal
Refactor the current CSDN MCP server from a fragile request-forwarder into a single-account publishing service with built-in login lifecycle management. The MCP surface should expose only one business tool: `publishArticle`.

## Scope
This design targets a single CSDN account shared by the running MCP server instance. The server runs as an SSE MCP service inside Docker and must survive container restarts without forcing re-login unless the stored session has actually expired.

## Non-Goals
- Multi-user account isolation
- General account management tools exposed to the agent
- Full browser-based article publishing fallback in phase 1

## Tool Contract
Expose one MCP tool:

- `publishArticle(title, markdowncontent, tags, description)`

The tool always returns a structured result instead of `null`.

## Result Model
`publishArticle` returns:

- `status`: `SUCCESS`, `AUTH_REQUIRED`, or `FAILED`
- `message`: operator-friendly explanation
- `articleUrl`: present on success
- `articleId`: present on success
- `loginUrl`: present when login is required
- `retryable`: whether the agent should retry later

## Architecture
### 1. PublishTool
The only MCP-facing entrypoint. It validates input, delegates session checks, and calls the publisher.

### 2. SessionManager
Loads stored session state, decides whether the current login is usable, and updates lifecycle state after publish attempts.

### 3. LoginCoordinator
Creates a one-time login session and returns a local HTTP URL when re-authentication is required. After browser login completes, it persists the new authenticated browser state.

### 4. SessionStore
Persists state under a Docker-mounted data directory so restarts do not erase login state.

Suggested files:
- `data/session/storage-state.json`
- `data/session/session-meta.json`

### 5. CsdnPublisher
Executes article publishing against CSDN. Phase 1 uses authenticated API publishing. A later phase may add browser publishing as a fallback adapter.

## Session State Machine
Use exactly four states:

- `UNBOUND`: no stored login state exists
- `LOGIN_PENDING`: a login URL has been issued and the service is waiting for browser completion
- `ACTIVE`: stored login state exists and is considered usable
- `EXPIRED`: stored login state failed authentication and must be refreshed

## Publish Flow
1. `PublishTool` receives article input.
2. `SessionManager` loads `session-meta.json` and any stored browser state.
3. If state is `UNBOUND` or `EXPIRED`, `LoginCoordinator` creates a login session and returns `AUTH_REQUIRED` with `loginUrl`.
4. If state is `LOGIN_PENDING`, return `AUTH_REQUIRED` with the existing `loginUrl`.
5. If state is `ACTIVE`, `CsdnPublisher` attempts API publishing.
6. On success, return `SUCCESS` with article metadata.
7. On authentication failure, mark state `EXPIRED` and return `AUTH_REQUIRED`.
8. On non-auth failures, return `FAILED` with `retryable` based on error type.

## Login Flow
The login helper is an internal HTTP endpoint, not an MCP tool.

1. `LoginCoordinator` generates a `loginSessionId`.
2. It returns a URL such as `/auth/csdn/login?session=<id>`.
3. The user opens the URL and completes browser login.
4. Playwright captures authenticated browser state and writes `storage-state.json`.
5. `session-meta.json` is updated to `ACTIVE`.

## Error Handling
- Never return `null`.
- Authentication failures map to `AUTH_REQUIRED`.
- Validation or request-shape problems map to `FAILED`.
- Temporary upstream failures map to `FAILED` with `retryable=true`.
- Persist the latest error summary in `session-meta.json` for diagnosis.

## Configuration
Add configuration for:
- data directory path
- base URL used to generate login links
- login session timeout

Do not require the agent to provide cookies, nonce values, or signatures.

## Testing Strategy
Phase 1 tests should cover:
- state transitions: `UNBOUND -> LOGIN_PENDING -> ACTIVE -> EXPIRED`
- `publishArticle` result mapping for success, auth failure, and generic failure
- session metadata persistence and recovery after restart
- login URL reuse while a login session is still pending

## Phase Plan
### Phase 1
Implement single-tool contract, session persistence, internal login flow, and API publishing with auth failure detection.

### Phase 2
Add browser-driven publishing fallback if API publishing remains brittle after login-state recovery.
