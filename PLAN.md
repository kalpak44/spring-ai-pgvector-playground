# Google Gmail + Calendar Integration Plan

## Architecture

```
Google Cloud (Gmail API + Calendar API)
         ↑  OAuth2 access tokens
  ┌──────────────────────┐
  │     MCP Server       │  Node.js, Zod, @modelcontextprotocol/sdk
  │  scripts/mcp-google/ │  exposes gmail + calendar tools, stateless
  └──────────────────────┘
         ↑  MCP protocol (HTTP/SSE)
  ┌──────────────────────┐
  │   Spring Boot App    │  Spring AI MCP client
  │                      │  OAuth2 flow + token storage per user
  └──────────────────────┘
         ↑
      App Users
```

---

## Phase 1 — Terraform (`scripts/terraform/`)

Sets up Google Cloud infrastructure via `hashicorp/google` provider.

### File Structure
```
scripts/terraform/
├── providers.tf      google provider config
├── variables.tf      project_id, app_domain, support_email
├── main.tf           resources
└── outputs.tf        client_id, client_secret (sensitive)
```

### Resources
| Resource | Purpose |
|---|---|
| `google_project_service` "gmail" | Enable Gmail API |
| `google_project_service` "calendar" | Enable Google Calendar API |
| `google_iap_brand` | OAuth consent screen (app name, support email) |
| `google_iap_client` | OAuth2 client → outputs `client_id` + `client_secret` |

### Known Limitation
`google_iap_client` creates a working OAuth credential but is IAP-branded.
For an External app (real users outside your org), promote the consent screen
to External once manually in Google Cloud Console — one-time, ~2 min.

### Outputs
Terraform outputs `client_id` and `client_secret` (marked sensitive).
These feed into app config via env vars (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`).

---

## Phase 2 — MCP Server (`scripts/mcp-google/`)

Standalone Node.js service. Stateless — caller provides the Google OAuth
access token per request. Spring Boot manages the token lifecycle (storage + refresh).

### File Structure
```
scripts/mcp-google/
├── package.json
├── tsconfig.json
└── src/
    ├── index.js          MCP server entry, HTTP transport on port 3100
    ├── auth.js           extracts + validates Bearer token from each request
    ├── schemas.js        all Zod schemas for tool inputs/outputs
    └── tools/
        ├── gmail.js      gmail tools
        └── calendar.js   calendar tools
```

### Auth Design
Each request carries `Authorization: Bearer <google_access_token>`.
The MCP server extracts the token and passes it to Google API calls.
No token is stored server-side — fully stateless.

### Tools Exposed

**Gmail**
| Tool | Input Schema |
|---|---|
| `gmail_list_messages` | `{ maxResults, query?, labelIds? }` |
| `gmail_get_message` | `{ messageId, format? }` |
| `gmail_send_message` | `{ to, subject, body, cc? }` |
| `gmail_search_messages` | `{ query, maxResults? }` |

**Calendar**
| Tool | Input Schema |
|---|---|
| `calendar_list_events` | `{ calendarId?, timeMin?, timeMax?, maxResults? }` |
| `calendar_create_event` | `{ summary, start, end, description?, attendees? }` |
| `calendar_get_event` | `{ calendarId?, eventId }` |
| `calendar_delete_event` | `{ calendarId?, eventId }` |

### Key Dependencies
```json
{
  "@modelcontextprotocol/sdk": "latest",
  "googleapis": "latest",
  "zod": "latest",
  "typescript": "latest"
}
```

---

## Phase 3 — Spring Boot App

### 3a. DB Migration — `scripts/postgres/05-google-tokens.sql`
```sql
CREATE TABLE user_google_tokens (
    user_id       BIGINT PRIMARY KEY REFERENCES users(id),
    access_token  TEXT NOT NULL,
    refresh_token TEXT,
    token_expiry  TIMESTAMP,
    google_email  VARCHAR(255),
    connected_at  TIMESTAMP DEFAULT NOW()
);
```

### 3b. New `pom.xml` Dependencies
```xml
<!-- OAuth2 client — handles the Google connect flow -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>

<!-- Spring AI MCP client — connects to the MCP server -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-client-spring-boot-starter</artifactId>
</dependency>
```

### 3c. New Java Files
| File | Responsibility |
|---|---|
| `model/UserGoogleToken.java` | JPA entity for `user_google_tokens` |
| `repo/UserGoogleTokenRepository.java` | Spring Data JPA repo |
| `services/GoogleOAuthService.java` | exchange auth code → tokens, refresh expired tokens |
| `controller/GoogleConnectorController.java` | `GET /settings/connectors/google/connect` → redirect to Google<br>`GET /settings/connectors/google/callback` → receive code, store tokens<br>`POST /settings/connectors/google/disconnect` → delete tokens |
| `config/McpClientConfig.java` | configures Spring AI MCP client (URL, auth header injection) |
| `advisors/GoogleAwareAdvisor.java` | Spring AI advisor that injects user's Google token into MCP calls |

### 3d. `application.yaml` Additions
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - openid
              - email
              - https://www.googleapis.com/auth/gmail.readonly
              - https://www.googleapis.com/auth/calendar
  ai:
    mcp:
      client:
        sse:
          connections:
            google:
              url: ${MCP_GOOGLE_URL:http://localhost:3100}
```

### 3e. `SecurityConfig.java` Changes
- Add `/settings/connectors/google/callback` to permitted URLs during OAuth redirect
- Existing form login remains unchanged — Google connect is additive, not a login replacement

### 3f. `SettingsController.java` Changes
- Replace hardcoded `googleConnected = false` with a real DB lookup via `UserGoogleTokenRepository`

---

## Delivery Order

| Step | What | Where |
|---|---|---|
| 1 | Terraform config | `scripts/terraform/` |
| 2 | DB migration | `scripts/postgres/05-google-tokens.sql` |
| 3 | MCP server | `scripts/mcp-google/` |
| 4 | JPA entity + repo | Spring Boot |
| 5 | OAuth flow (connect/callback/disconnect) | Spring Boot |
| 6 | MCP client config + advisor | Spring Boot |
| 7 | Activate existing UI stub | `settings-connectors.html` |

---

## Environment Variables Required
| Variable | Source |
|---|---|
| `GOOGLE_CLIENT_ID` | Terraform output |
| `GOOGLE_CLIENT_SECRET` | Terraform output |
| `MCP_GOOGLE_URL` | where MCP server runs (default: `http://localhost:3100`) |