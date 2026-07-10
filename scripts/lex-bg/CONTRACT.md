# Connector Contract

A connector is any HTTP service that exposes exactly **4 endpoints** under the `/connector` path.
The main app is fully source-agnostic — it treats every connector identically through this contract.
Language, runtime, and deployment are entirely up to the implementer.

---

## Authentication between main app and connector

> **TBD** — The mechanism by which the main app authenticates itself to the connector is not yet defined.
> Options under consideration: shared secret header, mTLS, API key field in connector URL.
> Until this is decided, connectors deployed in a private network (not publicly reachable) are the recommended setup.

---

## Timeouts

The main app enforces these hard limits per request:

| Limit | Value |
|---|---|
| Connect timeout | 5 s |
| Read timeout | 15 s |

`/connector/fetch` is the most likely to be slow (fetching large documents). Stay under 15 s or
paginate / pre-cache content at indexing time.

---

## Endpoints

### `GET /connector/info`

Called once when the user registers or edits the connector. Returns connector metadata and the
list of config fields the main app will render as a form.

**Response**
```json
{
  "name": "GitHub",
  "version": "1.0",
  "fields": [
    { "key": "repository",   "label": "Repository (org/repo)", "required": true },
    { "key": "branch",       "label": "Branch",                "default": "main" },
    { "key": "filePatterns", "label": "File patterns",         "default": "**/*.txt" },
    { "key": "accessToken",  "label": "Access Token",          "secret": true }
  ]
}
```

**Field descriptor**

| Property  | Type    | Required | Description |
|-----------|---------|----------|-------------|
| `key`     | string  | yes | Key used in the `config` map for all subsequent requests |
| `label`   | string  | yes | Human-readable label shown in the form |
| `required`| boolean | no  | Form validation — field must be non-empty before saving |
| `default` | string  | no  | Pre-filled value in the form |
| `secret`  | boolean | no  | Value is masked in the UI; the main app encrypts it at rest and decrypts before every call |

---

### `POST /connector/test`

Called when the user clicks **Test Connection**. Validates that the config is reachable and returns a document count preview.

**Request**
```json
{ "config": { "repository": "org/repo", "accessToken": "ghp_..." } }
```

**Response — success**
```json
{ "ok": true, "documentCount": 42, "message": "Connected — 42 matching documents found" }
```

**Response — failure**
```json
{ "ok": false, "message": "Repository not found or token lacks read access" }
```

Rules:
- Always return **HTTP 200**. Communicate errors through `ok: false` and `message`.
- `documentCount` is optional on failure but should be included on success.

---

### `POST /connector/documents`

Called at the start of every sync. Returns the full list of available documents.
The main app uses `contentHash` to skip documents that have not changed since the last sync.

**Request**
```json
{ "config": { "repository": "org/repo", "branch": "main", "filePatterns": "**/*.txt" } }
```

**Response**
```json
{
  "documents": [
    { "id": "sha:abc123", "path": "traffic/art-12.txt",               "contentHash": "md5:a1b2c3" },
    { "id": "lex:209813", "path": "https://lex.bg/laws/ldoc/2135891408", "contentHash": "md5:d4e5f6" }
  ]
}
```

Field rules:
- `id` — stable, opaque identifier for this document. Used only in `/connector/fetch`. Can be any unique string (SHA, URL, database ID, etc.).
- `path` — human-readable identifier shown as the citation source in chat responses. Use a relative file path or a full URL. For web crawlers, `path` becomes a clickable link.
- `contentHash` — any stable hash of the document content (e.g. `md5:...`, `sha1:...`). The main app skips fetching a document when its hash matches the previously stored value.

---

### `POST /connector/fetch`

Called for each document that is new or has changed. Returns the raw plain-text content.

**Request**
```json
{ "config": { "repository": "org/repo", "accessToken": "ghp_..." }, "documentId": "sha:abc123" }
```

**Response**
```json
{
  "path": "traffic/art-12.txt",
  "rawContent": "Art. 12 — Driving without a valid licence\n\nAny person who..."
}
```

Rules:
- `rawContent` must be **plain text**. Strip all HTML tags, Markdown formatting, and binary encoding before returning.
- The main app stores `rawContent` verbatim and passes it to the configured chunking strategy. What you return is exactly what gets embedded.
- `path` should match the value returned for this document in `/connector/documents`.

---

## Error handling summary

| Situation | Required behaviour |
|---|---|
| Config invalid / source unreachable | `/test` → `{ ok: false, message: "..." }` with HTTP 200 |
| Document not found in `/fetch` | HTTP 404 |
| Unexpected server error | HTTP 500 with `{ "error": "..." }` |
| `/documents` or `/fetch` failure during sync | The main app logs the error, marks the data source as ERROR, and stops the sync |

Do not return non-200 from `/test` — the main app treats any non-200 on that endpoint as a hard failure and does not inspect the body.