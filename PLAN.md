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
  ← { "path": "traffic/art-12.txt", "rawContent": "Art. 12. Driving without a license...",
      "metadata": { "sourceUrl": "https://...", "crawledAt": "2026-07-10T..." } }
```

`path` is a human-readable identifier — a file path or a full URL. For web crawlers it becomes
the clickable `source_url` in citations directly. `secret: true` fields are masked in the UI
and encrypted by the main app before storage; the connector always receives decrypted values.

The `metadata` field is optional; connectors can return arbitrary key→value pairs that are
merged into each chunk's metadata in the vector store.

### Connector types the contract covers

| Connector | Source | How documents are listed |
|---|---|---|
| Filesystem (reference) | Local `.txt` / `.md` files | `glob(path, pattern)` |
| GitHub | Repo via REST API | `GET /git/trees` filtered by pattern |
| lex.bg (live) | Bulgarian legislation site | Crawl tree pages, extract via CSS selector |
| Notion / Confluence | Workspace API | Fetch pages from space/database |
| REST API proxy | Any internal API | Call endpoint, map response to document list |

### Reference connector — filesystem (Express.js)

`scripts/data-source-connector-template/` — a minimal Node.js/Express app implementing all
4 endpoints by reading files from a local directory.

### lex.bg connector (live — `scripts/lex-bg/`)

A Node.js/Express crawler for the Bulgarian legislation database:
- Discovers all laws, codes, regulations, rules, and the Constitution from the category tree pages
- Builds an in-memory catalog (rebuilt every 24 h)
- Optional `PRELOAD=true` mode: pre-fetches all document content on startup and on a cron schedule, saving to a local file cache (`PRELOAD_PATH`)
- `GET /connector/status` — exposes preload stats (cached, total, running, lastRunAt)
- Fetch returns `metadata: { sourceUrl, crawledAt }` for proper citations

### ConnectorClient (main app)

A thin HTTP client in the main app that speaks the contract:

```
ConnectorClient
  .info(url)                        → ConnectorInfo (name, version, fields)
  .test(url, config)                → TestResult (ok, documentCount, message)
  .documents(url, config)           → List<ConnectorDocument> (id, path, contentHash)
  .fetch(url, config, documentId)   → ConnectorFetchResult (path, rawContent, metadata)
```

Connect timeout: 10 s. Read timeout: 60 s (large document fetches can be slow).

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
  id            BIGSERIAL PK
  name          VARCHAR NOT NULL
  description   VARCHAR
  strategy      VARCHAR NOT NULL  (FIXED_TOKENS | PARAGRAPH | CUSTOM_SEPARATOR | MARKDOWN_HEADERS | AI)
  chunk_size    INT
  chunk_overlap INT
  separator     VARCHAR
  created_at    TIMESTAMP

loaded_document
  id              BIGSERIAL PK
  data_source_id  BIGINT FK → data_source
  filename        VARCHAR   (= path from connector)
  content_hash    VARCHAR
  document_type   VARCHAR
  chunk_count     INT
  loaded_at       TIMESTAMP

sync_log_entry
  id              BIGSERIAL PK
  data_source_id  BIGINT FK → data_source ON DELETE CASCADE
  sync_run_id     VARCHAR(36)  (UUID per sync run)
  level           VARCHAR(8)   (INFO | WARN | ERROR)
  event_type      VARCHAR(32)  (SyncEventType enum)
  message         TEXT
  details         JSONB
  created_at      TIMESTAMP
```

**Skipped from original plan:** `KnowledgeBase` entity. The two-level hierarchy
(`KnowledgeBase → DataSource`) was simplified to a flat list of data sources.
`SyncLog` was replaced by `sync_log_entry` with a richer event model.

### Vector store chunk metadata (current)

```json
{
  "data_source_id":   "3",
  "data_source_name": "lex-bg",
  "source_file":      "https://lex.bg/laws/ldoc/521957377",
  "sourceUrl":        "https://lex.bg/laws/ldoc/521957377",
  "crawledAt":        "2026-07-10T08:23:11.000Z",
  "article":          "Чл. 5",
  "topic":            "penalties",
  "keywords":         ["санкция", "глоба", "нарушение"]
}
```

`article`, `topic`, and `keywords` are populated by `AiChunkingSplitter` when the AI chunking
strategy is used. They are used as retrieval signals (article metadata search, keyword metadata
search) and displayed in chat citations.

### Services

| Service | Status | Responsibility |
|---|---|---|
| `ConnectorClient` | ✅ done | HTTP client for the 4-endpoint connector contract |
| `ConnectorSyncService` | ✅ done | Source-agnostic sync with per-document error isolation and SSE events |
| `SyncProgressService` | ✅ done | SSE emitter management + sync log persistence |
| `ChunkingProfileService` | ✅ done | CRUD for ChunkingProfile |
| `DataSourceService` | ✅ done | CRUD for DataSource, findById |
| `HybridSearchService` | ✅ done | 4-channel search (dense + FTS + article + keyword) merged via weighted RRF |
| `RerankerService` | ✅ done | LLM-based relevance scorer; rates candidates 0–10 with Bulgarian legal context |
| `AiChunkingSplitter` | ✅ done | AI chunking for Bulgarian legal text; segments large docs; retry + fallback |
| `EncryptionService` | ❌ skipped | AES-256-GCM for secret config fields |

---

## UI — Pages

### Current UI (as of July 2026)

All knowledge base management lives under **Settings → Knowledge Base** (`/settings/knowledge-base`):

- **List page** (`/settings/knowledge-base`): clickable cards per data source showing status badge,
  URL, chunk count, last synced. "Sync Now" button per card. "Add Data Source" modal.
- **Detail page** (`/settings/knowledge-base/data-sources/{id}`):
  - Connector info card with live-updating status badge, Sync Now button
  - Live sync log panel — SSE stream during active sync, full history otherwise
  - Danger zone with name-confirmation delete
- **Chunking profiles** (`/settings/knowledge-base/chunking-profiles`): CRUD with inline delete modal

**Skipped from original plan:** Separate apps-level KB list/detail pages at `/apps/knowledge-base`.
No inline KB name/description edit (no KB entity).

---

## Sync Pipeline

`ConnectorSyncService.sync(Long dataSourceId)` is fully source-agnostic:

1. Generate UUID `runId`; emit `SYNC_STARTED` event with document count
2. `ConnectorClient.documents(ds.connectorUrl, config)` → list of `{ id, path, contentHash }`
3. For each document (each in its own try/catch — one failure doesn't abort the sync):
   - Check `LoadedDocument` for same `dataSourceId` + `path` + `contentHash` → emit `DOC_SKIPPED` + skip
   - `ConnectorClient.fetch(...)` → `rawContent` + optional `metadata`; emit `DOC_FETCHED`
   - Delete old chunks from vector store (filter by `data_source_id` + `path` metadata)
   - Chunk using `ChunkingStrategyFactory.get(ds.chunkingProfile)` transformer
   - Attach metadata (data_source_id, data_source_name, source_file, connector metadata) to each chunk
   - Store chunks in `VectorStore`; emit `DOC_CHUNKED` with keyword/article summary
   - Save `LoadedDocument`
4. Detect removed documents → delete their chunks; emit `DOC_REMOVED`
5. Set `ds.status = IDLE`, update `ds.lastSyncedAt`, `ds.chunkCount`; emit `SYNC_COMPLETE`
6. `finally`: `syncProgressService.completeEmitters(dataSourceId)` — always closes SSE

All events are persisted to `sync_log_entry` and broadcast to any active SSE subscribers.

**Still missing:** secret decryption before connector calls, `rawContent` persistence in
`LoadedDocument` (blocks re-indexing without re-fetch).

---

## AI Chunking Strategy (`AiChunkingSplitter`)

Activated when a chunking profile has `strategy = AI`.

**Large document handling:** Documents longer than 30 000 chars are split into segments at
Bulgarian legal structural boundaries (`\n\nЧл.`, `\n\n§`, `\n\nРаздел`, `\n\nГлава`, etc.)
so no article is ever cut mid-text. Each segment is independently processed.

**Per-segment pipeline:**
1. Send segment to LLM with a 3-step prompt: CLEAN (strip amendment history, Flash errors, noise)
   → SPLIT (one chunk per article/paragraph/section) → ENRICH (article id, English topic, Bulgarian keywords)
2. Parse JSON response `{ chunks: [{ text, article, topic, keywords }] }`
3. Retry up to 2 times on empty/invalid response
4. Fallback: paragraph split capped at 3 000 chars to stay within embedding model token limit

**LLM config:** `OllamaChatOptions` with `temperature=0.0` for deterministic output.
System message enforces JSON-only responses.

---

## Chat Integration — Current State

### Advisor chain

```
User message
    │
    ▼
① ExpansionQueryAdvisor   ✅ WIRED (order=5)
    Generates 3 alternative search queries as JSON {"queries": [...]}
    Expands abbreviations, resolves follow-up references using conversation history
    Stores list as ENRICHED_QUESTIONS + first query as ENRICHED_QUESTION (backward compat)
    │
    ▼
② RagAdvisor              ✅ WIRED (order=10)
    Reads ENRICHED_QUESTIONS list → passes all queries to HybridSearchService
    HybridSearchService: 4 channels × 3 queries, weighted RRF → top-20 candidates
    RerankerService: LLM relevance scores → top-5
    Builds numbered [N] source blocks with article/topic/crawledAt and prepends to user message
    │
    ▼
③ LLM call
    System prompt: legal citation format, answer ONLY from sources, prompt injection protection
```

### HybridSearchService — 4 retrieval channels

| Channel | Method | Weight | Purpose |
|---|---|---|---|
| Dense | `vectorStore.similaritySearch(threshold=0.2)` | 1.2 | Semantic similarity |
| Sparse FTS | `websearch_to_tsquery('simple')` + `ts_rank_cd` | 1.0 | Exact keyword matching |
| Article metadata | `metadata->>'article' ILIKE 'Чл. 5%'` | 1.5 | Precise article reference lookups |
| Keyword metadata | `jsonb_array_elements_text(metadata->'keywords') ILIKE 'задълж%'` | 1.1 | AI-curated term matching with morphological prefix |

All channels run per-query; scores accumulate across all 3 expanded queries via weighted RRF
(k=60). Top-20 candidates go to the LLM reranker; top-5 go to context.

---

## Implementation Phases — Actual Status

### Phase 0 — Reference connector (Express.js)

- ✅ `scripts/data-source-connector-template/` created with all 4 endpoints
- ✅ `README.md` with connector contract documentation
- ✅ Sample data files for end-to-end testing
- ✅ `scripts/lex-bg/` — live Bulgarian legislation connector with preload + file cache + cron

### Phase 1 — Data model + UI

- ✅ DB migration: `chunking_profile`, `data_source`, `sync_log_entry`
- ✅ JPA entities: `ChunkingProfile`, `DataSource`, `LoadedDocument`, `SyncLogEntry`
- ✅ Repositories: all CRUD + `SyncLogRepository` with top-200 ordered query
- ✅ `ChunkingProfileService`, `DataSourceService`
- ✅ `ChunkingProfileController` + `chunking-profiles.html` (CSRF fix on delete)
- ✅ `KnowledgeBaseController`: list page + detail page + sync + delete
- ✅ List page: clickable cards, compact Sync button, Add modal
- ✅ Detail page: status card, SSE sync log panel, danger zone with name confirmation
- ❌ `KnowledgeBase` entity (architecture simplified to flat)
- ❌ `EncryptionService` for secret config fields (secrets stored in plain text in JSONB)

### Phase 2 — ConnectorSyncService + sync pipeline

- ✅ `ConnectorClient`: HTTP client for all 4 connector endpoints
- ✅ `ConnectorSyncService`: source-agnostic sync with per-document isolation
- ✅ `SyncProgressService`: SSE emitter management + `sync_log_entry` persistence
- ✅ `SyncLogController`: `/api/.../sync-stream` (SSE) + `/api/.../sync-log` (history)
- ✅ "Sync Now" POST endpoint → async sync → redirect (list or detail based on `from` param)
- ✅ `fts tsvector` generated column + `GIN` index added to `vector_store`
- ✅ Sync log displayed live in detail page via SSE; history on page load
- ✅ Auto-SSE connection while status = SYNCING
- ❌ `rawContent` not stored in `LoadedDocument` (blocks re-indexing without re-fetch)

### Phase 3 — Chat integration

- ✅ Chunk metadata: `data_source_id`, `data_source_name`, `source_file`, `sourceUrl`, `crawledAt`
- ✅ AI chunker adds: `article`, `topic`, `keywords` to chunk metadata
- ✅ `ExpansionQueryAdvisor`: multi-query expansion (3 queries), wired at order=5
- ✅ `HybridSearchService`: 4-channel search with weighted RRF, multi-query support
- ✅ `RerankerService`: LLM-based relevance scorer with Bulgarian legal context
- ✅ `RagAdvisor`: reads ENRICHED_QUESTIONS list, passes to hybrid search, formats citations
- ✅ System prompt with legal citation format and prompt injection protection
- ⚠️ Reranker is LLM-based (not a true cross-encoder); cross-encoder would be higher quality
- ❌ `EncryptionService` — secrets in plain text

### Phase 4 — Polish & scheduled sync

- ❌ Not started

---

## RAG Quality Roadmap

### R1 — Wire query rewrite ✅ DONE (upgraded to multi-query)

`ExpansionQueryAdvisor` wired at order=5. Generates 3 alternative queries covering different
angles and phrasings. Expands abbreviations, resolves follow-up references.

### R2 — Hybrid search ✅ DONE (4 channels + weighted RRF)

`HybridSearchService` with dense, FTS (`websearch_to_tsquery`/`ts_rank_cd`), article metadata,
and keyword metadata channels. All channels run for all 3 expanded queries; scores accumulated
via weighted RRF. Similarity threshold (0.2) on dense to filter irrelevant embeddings.

### R3 — Store rawContent in LoadedDocument ❌ PENDING

Raw copies would enable re-indexing when chunking strategy or embedding model changes
without re-fetching from the connector.

- Add `raw_content TEXT` column to `loaded_document`
- Persist `fetched.rawContent()` in `ConnectorSyncService`
- Phase 4 "Re-index" reads from `rawContent` instead of calling the connector

### R4 — source_url in chunk metadata ✅ DONE

Connectors return `metadata: { sourceUrl, crawledAt }` from `/connector/fetch`. These are
merged into chunk metadata and displayed in `RagAdvisor` citation blocks. Web crawler paths
(lex.bg URLs) are clickable links in chat responses.

### R5 — Encrypt secrets in config JSONB ❌ PENDING

`EncryptionService` was planned but skipped. Connector config fields marked `secret: true`
are stored as plain text. Implement AES-256-GCM encrypt/decrypt keyed from `application.yaml`.

### R6 — Scheduled sync + webhook ❌ PENDING

- Add `sync_interval_hours INT` to `data_source`
- `@Scheduled` job: trigger sync when `last_synced_at < now() - interval`
- `POST /settings/knowledge-base/webhook/{dataSourceId}` with shared secret header

### R7 — Cross-encoder reranker ⚠️ PARTIAL

LLM-based `RerankerService` exists (scores candidates 0–10 with Bulgarian legal context,
500-char preview, article/topic headers). This is better than BM25 but not a true cross-encoder.
A proper cross-encoder (`bge-reranker-v2-m3` via Ollama) would give stronger signal at the
cost of ~200–500 ms latency.

### R8 — Large law chunking ✅ DONE

`AiChunkingSplitter` detects documents exceeding 30 000 chars and splits at Bulgarian
structural boundaries before processing each segment. Previously, documents > 30 000 chars
were truncated — the bulk of large laws (Civil Code, Penal Code, etc.) was invisible to RAG.

---

## Open Questions

1. **KnowledgeBase grouping**: add the entity later for multi-project scenarios, or keep flat?
2. **Reranker model**: upgrade to `bge-reranker-v2-m3` cross-encoder, or keep LLM-based (latency tradeoff)?
3. **Max document size**: ceiling on rawContent to avoid enormous chunks? (Suggest 500 KB.)
4. **Secret encryption**: when to implement `EncryptionService`? Blocks production use of connectors with API keys.
5. **Re-index without re-fetch**: blocked on R3 (rawContent storage); needed when switching chunking profiles.