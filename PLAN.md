# Knowledge Base — Implementation Plan

## Goal

Build a shared Knowledge Base feature that lets any user connect 3rd-party data sources
(starting with GitHub), pull content into the pgvector store, and have the chat AI answer
questions by searching that content and citing the exact source.

---

## Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Ownership | Shared across all users | Simpler; one org-level KB |
| First data source | GitHub (REST API, PAT auth) | Already requested, no OAuth complexity |
| Sync trigger | Manual "Sync Now" + optional scheduled cron | Start simple, add schedule later |
| Chat scope | All KBs searched on every message | No per-chat selector in phase 1 |
| Chunk metadata | KB name + data source ID + file path stored per chunk | Enables citations |
| Connector architecture | REST Connector API, pull model | Each data source is an independent service in any language; main app is fully source-agnostic |
| DataSource DB design | Single table + `connector_url` + `config JSONB` | No sub-tables, no class hierarchy; adding a connector = deploy + register URL, zero DB migration |
| Credential storage | Encrypted inline in `config JSONB` | Connector declares secret fields via `/connector/info`; main app encrypts those values before storing |

---

## Connector Architecture

Each data source is an independently deployable HTTP service — any language, any runtime.
The main app has zero source-specific code; it speaks to every connector through the same
4-endpoint contract. Adding a new data source = deploy a new service + register its URL.

This covers file-based sources (GitHub, filesystem, S3), API-based sources (Notion, Confluence),
and web crawlers (lex.bg, any public or internal site). All produce the same document shape.

### Connector API contract

```
GET /connector/info
  ← {
      "name": "GitHub",
      "version": "1.0",
      "fields": [
        { "key": "repository",   "label": "Repository (org/repo)", "required": true },
        { "key": "branch",       "label": "Branch",                "default": "main" },
        { "key": "filePatterns", "label": "File patterns",         "default": "**/*.txt" },
        { "key": "accessToken",  "label": "Access Token",          "secret": true }
      ]
    }

POST /connector/test
  → { "config": { "repository": "org/repo", "accessToken": "ghp_..." } }
  ← { "ok": true, "documentCount": 42, "message": "Connected — 42 matching documents found" }

POST /connector/documents
  → { "config": { ... } }
  ← { "documents": [
        { "id": "sha:abc123", "path": "traffic/art-12.txt",                  "contentHash": "md5:..." },
        { "id": "lex:209813", "path": "https://lex.bg/laws/ldoc/2135891408", "contentHash": "md5:..." }
      ]}

POST /connector/fetch
  → { "config": { ... }, "documentId": "sha:abc123" }
  ← { "path": "traffic/art-12.txt", "rawContent": "Art. 12. Driving without a license..." }
```

`path` is a human-readable identifier — a file path or a full URL. For web crawlers it becomes
the clickable `source_url` in citations directly. `secret: true` fields are masked in the UI
and encrypted by the main app before storage; the connector always receives decrypted values.

### Connector types the contract covers

| Connector | Source | How documents are listed |
|---|---|---|
| Filesystem (reference) | Local `.txt` / `.md` files | `glob(path, pattern)` |
| GitHub | Repo via REST API | `GET /git/trees` filtered by pattern |
| Web crawler (lex.bg, etc.) | Public / internal site | Crawl sitemap or link pattern, extract via CSS selector |
| Notion / Confluence | Workspace API | Fetch pages from space/database |
| REST API proxy | Any internal API | Call endpoint, map response to document list |

### Reference connector — filesystem (Express.js)

`scripts/data-source-connector-template/` — a minimal Node.js/Express app implementing all
4 endpoints by reading files from a local directory. Serves as:

1. End-to-end proof the contract works with the main app
2. Copy-paste starting point for any new connector in any language

### Web crawler connector fields example (lex.bg)

```json
"fields": [
  { "key": "baseUrl",    "label": "Base URL",     "default": "https://lex.bg" },
  { "key": "urlPattern", "label": "URL pattern",  "default": "/laws/*" },
  { "key": "selector",   "label": "CSS selector", "default": "div.law-content" },
  { "key": "maxPages",   "label": "Max pages",    "default": "100" }
]
```

The crawler connector handles all HTML fetching, pagination, and content extraction internally.
The main app only ever receives clean plain text.

### ConnectorClient (main app)

A thin HTTP client in the main app that speaks the contract:

```
ConnectorClient
  .info(url)                        → ConnectorInfo (name, version, fields)
  .test(url, config)                → TestResult (ok, documentCount, message)
  .documents(url, config)           → List<ConnectorDocument> (id, path, contentHash)
  .fetch(url, config, documentId)   → ConnectorDocument (path, rawContent)
```

---

## Architecture

### Actual DB entities (as of July 2026)

```
data_source
  id                   BIGSERIAL PK
  name                 VARCHAR
  connector_url        VARCHAR
  connector_name       VARCHAR   (cached from /connector/info)
  config               JSONB     (flat key→value; secrets NOT yet encrypted)
  chunking_profile_id  BIGINT FK → chunking_profile
  status               VARCHAR   (IDLE | SYNCING | ERROR | NEVER_SYNCED)
  last_synced_at       TIMESTAMP
  chunk_count          INT
  error_message        VARCHAR
  created_at           TIMESTAMP

chunking_profile
  id          BIGSERIAL PK
  name        VARCHAR NOT NULL
  description VARCHAR
  strategy    VARCHAR NOT NULL   (FIXED_TOKENS | PARAGRAPH | CUSTOM_SEPARATOR | MARKDOWN_HEADERS)
  chunk_size  INT
  chunk_overlap INT
  separator   VARCHAR
  created_at  TIMESTAMP

loaded_document
  id              BIGSERIAL PK
  data_source_id  BIGINT
  filename        VARCHAR   (= path from connector)
  content_hash    VARCHAR
  document_type   VARCHAR
  chunk_count     INT
  loaded_at       TIMESTAMP
  -- raw_content TEXT not yet added
```

**Skipped from original plan:** `KnowledgeBase` entity, `SyncLog` entity. The two-level
hierarchy (`KnowledgeBase → DataSource`) was simplified to a flat list of data sources
under `/settings/knowledge-base`. This is a valid simplification for phase 1; KnowledgeBase
grouping can be added later if multi-tenant or multi-project scenarios require it.

### Vector store chunk metadata (current)

```json
{
  "data_source_id":   "3",
  "data_source_name": "laws-repo",
  "source_file":      "traffic/art-12.txt"
}
```

**Missing vs plan:** `knowledge_base_id`, `knowledge_base_name` (no KB entity), `source_url`
(GitHub URL is not reconstructed from path; web crawler paths are full URLs so those work).

### New services

| Service | Status | Responsibility |
|---|---|---|
| `ConnectorClient` | ✅ done | HTTP client for the 4-endpoint connector contract |
| `ConnectorSyncService` | ✅ done | Source-agnostic sync |
| `SyncJobService` | ✅ done | `@Async` orchestration, status tracking |
| `ChunkingProfileService` | ✅ done | CRUD for ChunkingProfile |
| `DataSourceService` | ✅ done | CRUD for DataSource |
| `EncryptionService` | ❌ skipped | AES-256-GCM for secret config fields |
| `HybridSearchService` | ❌ missing | Dense + FTS sparse merged via RRF |
| `QueryRewriteService` | ⚠️ partial | `ExpansionQueryAdvisor` exists but not wired; template is Spring-specific |
| `RerankService` (Ollama) | ⚠️ partial | `BM25RerankEngine` in-process; no cross-encoder |

---

## UI — Pages

### Current UI (as of July 2026)

All knowledge base management lives under **Settings → Knowledge Base** (`/settings/knowledge-base`):

- Flat list of data sources with status badges (Synced / Syncing / Error / Never synced)
- "Add Data Source" modal: Name → Connector URL → Discover → dynamic config form → profile dropdown → Save
- "Sync Now" button per data source
- "Remove" button with confirmation modal
- Chunking profiles page (`/settings/knowledge-base/chunking-profiles`)

**Skipped from original plan:** Separate apps-level KB list/detail pages at `/apps/knowledge-base`.
The settings page handles everything. No inline KB name/description edit (no KB entity).
No sync log display.

### Original planned UI (deferred)

The plan described a richer two-level hierarchy with a list page and detail page per KB.
These are deferred. If KnowledgeBase grouping is added later, the UI would need:

- `/apps/knowledge-base` — list of KBs with aggregate status
- `/apps/knowledge-base/{id}` — detail page: data sources, sync log, inline name edit
- Auto-polling while status = SYNCING

---

## Sync Pipeline

`ConnectorSyncService.sync(DataSource ds)` is fully source-agnostic:

1. Set `ds.status = SYNCING`, save
2. `ConnectorClient.documents(ds.connectorUrl, config)` → list of `{ id, path, contentHash }`
3. For each document:
   - Check `LoadedDocument` for same `dataSourceId` + `path` + `contentHash` → skip if unchanged
   - `ConnectorClient.fetch(...)` → `rawContent`
   - Delete old chunks from vector store (filter by `data_source_id` + `path` metadata)
   - Chunk using `ChunkingStrategyFactory.get(ds)` transformer
   - Attach metadata to each chunk
   - Store chunks in `VectorStore`
   - Upsert `LoadedDocument` (without rawContent — not yet persisted)
4. Detect removed documents → delete their chunks
5. Set `ds.status = IDLE`, update `ds.lastSyncedAt`, `ds.chunkCount`

**Not yet implemented:** secret decryption before connector calls, rawContent persistence,
SyncLog write, retry logic.

---

## Chat Integration — Current State

### Advisor chain

```
User message
    │
    ▼
① ExpansionQueryAdvisor   ← EXISTS but NOT WIRED in ChatService
    Spring-specific query expander; template needs to be domain-agnostic
    │
    ▼
② RagAdvisor              ← WIRED (order=10)
    Vector similarity search → top-K * 2 candidates
    BM25RerankEngine reranks in-process → top-K
    Builds numbered [N] source blocks and prepends to user message
    │
    ▼
③ LLM call
    System prompt: "answer ONLY from those sources" + prompt injection protection
```

### What's working

- RAG activates automatically when any data source has `chunk_count > 0`
- Numbered citation blocks `[N] source — file` prepended to user message
- System prompt enforces ground rule and prompt injection protection
- BM25 lexical reranking of the retrieved candidate set

### What's missing vs the plan

- Query rewrite not in the chain (advisor exists but unused)
- No hybrid search: only vector similarity, no FTS/tsvector
- No true cross-encoder reranker (Ollama bge-reranker)
- No `source_url` in citations for GitHub files

---

## Implementation Phases — Actual Status

### Phase 0 — Reference connector (Express.js)

- ✅ `scripts/data-source-connector-template/` created with all 4 endpoints
- ✅ `README.md` with connector contract documentation
- ✅ Sample data files for end-to-end testing

### Phase 1 — Data model + UI

- ✅ DB migration: `chunking_profile`, `data_source`
- ✅ JPA entities: `ChunkingProfile`, `DataSource`; `LoadedDocument` updated with `data_source_id`
- ✅ Repositories: `ChunkingProfileRepository`, `DataSourceRepository`, `DocumentRepository`
- ✅ `ChunkingProfileService`: findAll, create, update, delete (blocked if in use)
- ✅ `DataSourceService`: findAll, create, delete, markSyncing
- ✅ `ChunkingProfileController` + `chunking-profiles.html`
- ✅ `KnowledgeBaseController` at `/settings/knowledge-base` + `settings-knowledge-base.html`
- ✅ Add data source modal: connector URL → Discover → dynamic field form → profile dropdown → Save
- ❌ `KnowledgeBase` entity + `SyncLog` entity (architecture simplified to flat)
- ❌ `EncryptionService` for secret config fields (secrets stored in plain text in JSONB)
- ❌ Separate apps-level list/detail pages

### Phase 2 — ConnectorSyncService + sync pipeline

- ✅ `ConnectorClient`: HTTP client for all 4 connector endpoints
- ✅ `ConnectorSyncService`: source-agnostic sync
- ✅ `SyncJobService`: `@Async` orchestration, status tracking
- ✅ "Sync Now" POST endpoint → async sync → redirect
- ✅ Status badges on UI
- ❌ `rawContent` not stored in `LoadedDocument` (blocks future re-indexing without re-fetch)
- ❌ `fts tsvector` generated column + `GIN` index not added to `vector_store`
- ❌ Sync log not displayed in UI
- ❌ Auto-polling while SYNCING

### Phase 3 — Chat integration

- ✅ Chunk metadata written during sync (`data_source_id`, `data_source_name`, `source_file`)
- ✅ `RagAdvisor`: vector search + BM25 rerank → numbered context blocks
- ✅ Wired into `ChatService` when `chunk_count > 0`
- ✅ System prompt ground rule + prompt injection protection
- ⚠️ `ExpansionQueryAdvisor` exists but NOT wired; template is Spring-specific, needs rewrite
- ❌ `HybridSearchService`: no FTS, no RRF merge
- ❌ `RerankService` via Ollama cross-encoder
- ❌ `source_url` missing from chunk metadata (GitHub file URLs not reconstructed)

### Phase 4 — Polish & scheduled sync

- ❌ Not started

---

## RAG Quality Roadmap

Gaps identified against the [RAG cheatsheet](two-loop architecture: ingestion + retrieval).
Ordered by impact on answer quality.

### R1 — Wire query rewrite (high impact, low effort)

`ExpansionQueryAdvisor` already exists. Two changes needed:

1. Rewrite the prompt template to be domain-agnostic (remove Spring specialization):
   ```
   Rewrite the user question into a precise, search-optimised query.
   Resolve pronouns and follow-up context from the conversation.
   Expand abbreviations. Keep all key terms. Return a plain string.
   Question: {question}
   Rewritten query:
   ```
2. Add it to the advisor chain in `ChatService` before `RagAdvisor`:
   ```java
   spec.advisors(ExpansionQueryAdvisor.builder(chatModel).order(5).build());
   spec.advisors(RagAdvisor.build(vectorStore).order(10).build());
   ```

This directly improves recall: follow-up questions like "what about that law?" get rewritten
into search-friendly form before hitting the vector store.

### R2 — Hybrid search: FTS + vector + RRF (high impact, medium effort)

Per cheatsheet: "Hybrid index: vector + BM25". Currently only vector search is used.

**Step 1** — Add FTS column to vector_store (one-time migration):
```sql
ALTER TABLE vector_store ADD COLUMN fts tsvector
  GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;
CREATE INDEX ON vector_store USING GIN(fts);
```

**Step 2** — `HybridSearchService` runs both queries and merges:
```
dense  = vectorStore.similaritySearch(query, topK=50)
sparse = jdbc("SELECT id, content, metadata FROM vector_store
               WHERE fts @@ plainto_tsquery('english', ?)
               ORDER BY ts_rank(fts, plainto_tsquery('english', ?)) DESC
               LIMIT 50", query, query)
merged = reciprocalRankFusion(dense, sparse)  // score = 1/(60+rank_d) + 1/(60+rank_s)
```

**Step 3** — Replace `vectorStore.similaritySearch` call in `RagAdvisor` with `HybridSearchService`.

This is the single biggest retrieval improvement — keyword queries (error codes, names,
exact terms) are weak in vector search but strong in FTS.

### R3 — Store rawContent in LoadedDocument (medium impact, low effort)

Per cheatsheet "object storage" step: raw copies enable re-indexing when chunking strategy
or embedding model changes, without re-fetching from the connector.

Changes:
- Add `raw_content TEXT` column to `loaded_document` table
- Add `rawContent` field to `LoadedDocument` entity
- Persist `fetched.rawContent()` in `ConnectorSyncService` when saving `LoadedDocument`
- Phase 4 "Re-index" option reads from `rawContent` instead of calling the connector

### R4 — Add source_url to chunk metadata (medium impact, low effort)

For proper citations. Currently chunk metadata has `source_file` (path) but no clickable URL.

In `ConnectorSyncService`, when building the `Document` metadata map:
- If `source_file` starts with `http`, use it directly as `source_url`
- Otherwise, connector could return a `sourceUrl` in the fetch response (extend contract)
- Or reconstruct from connector config for known types (GitHub: `https://github.com/{repo}/blob/{branch}/{path}`)

Update citation format in `RagAdvisor` context block to include the URL when present.

### R5 — Encrypt secrets in config JSONB (security, medium effort)

`EncryptionService` was planned but skipped. Connector config fields marked `secret: true`
are currently stored as plain text in the JSONB column.

Implement AES-256-GCM encrypt/decrypt:
- `EncryptionService.encrypt(value)` / `decrypt(value)` — key from `application.yaml`
- `DataSourceService.create()` — encrypt secret fields before saving
- `ConnectorClient` calls — decrypt before passing config to connector
- UI — mask secret fields, show `••••` for existing values

### R6 — Scheduled sync + webhook (medium impact, medium effort)

Per cheatsheet: "Ingestion is triggered automatically (webhook / schedule)".

- Add `sync_interval_hours INT` to `data_source` (null = manual only)
- `@Scheduled` job every hour: find sources where `last_synced_at < now() - interval` → trigger
- `POST /settings/knowledge-base/webhook/{dataSourceId}` → verify shared secret header → trigger sync
- Configurable interval dropdown in the data source form (None / 1h / 6h / 24h)

### R7 — Cross-encoder reranker via Ollama (high quality, high effort)

Replace `BM25RerankEngine` with a proper cross-encoder that scores each (query, chunk) pair
by actual relevance. Current BM25 re-scores only the already-retrieved top-K; a cross-encoder
reads the full pair and gives a richer signal.

- Pull `bge-reranker-v2-m3` model in Ollama
- Implement `RerankService` calling Ollama `/api/embed` or a dedicated rerank endpoint
- Hybrid search retrieves 20–50 candidates; cross-encoder selects top 5–10
- Adds ~200–500ms latency per request — acceptable if retrieval quality is the priority

---

## Open Questions

1. **KnowledgeBase grouping**: add the entity later for multi-project scenarios, or keep flat?
2. **Reranker model**: `bge-reranker-v2-m3` vs keeping in-process BM25 (latency tradeoff)?
3. **Max document size**: ceiling on rawContent to avoid enormous chunks? (Suggest 500 KB.)
4. **Query rewrite model**: same Ollama model as chat, or a faster/smaller one for the rewrite step?
5. **FTS language**: `english` tsvector config. Needs `simple` or `pg_catalog.bulgarian` for multi-language corpora.