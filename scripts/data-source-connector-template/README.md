# Data Source Connector Template

A minimal reference connector for the Knowledge Base connector API.
It reads `.txt` files from a local directory and exposes them through the 4-endpoint contract
that the main app expects from every data source.

Use this as a copy-paste starting point for any new connector in any language.

---

## Running

```bash
npm install
npm start          # production
npm run dev        # auto-restart on file change (Node 18+)
```

Default port: **3200**. Override with `PORT=<n> npm start`.

Test with the bundled sample data:

```bash
curl http://localhost:3200/connector/info

curl -X POST http://localhost:3200/connector/test \
  -H 'Content-Type: application/json' \
  -d '{"config": {"directory": "data"}}'

curl -X POST http://localhost:3200/connector/documents \
  -H 'Content-Type: application/json' \
  -d '{"config": {"directory": "data"}}'

curl -X POST http://localhost:3200/connector/fetch \
  -H 'Content-Type: application/json' \
  -d '{"config": {"directory": "data"}, "documentId": "file:traffic/art-12.txt"}'
```

---

## Connector API contract

Every connector — regardless of language or runtime — must implement exactly these 4 endpoints.

### `GET /connector/info`

Returns connector metadata and the list of config fields the main app should render as a form.

**Response:**
```json
{
  "name": "Filesystem",
  "version": "1.0",
  "fields": [
    { "key": "directory",    "label": "Documents directory",  "required": true },
    { "key": "filePattern",  "label": "File pattern (glob)",  "default": "**/*.txt" }
  ]
}
```

**Field descriptor properties:**

| Property   | Type    | Required | Description |
|------------|---------|----------|-------------|
| `key`      | string  | yes      | Config map key; used in all subsequent requests |
| `label`    | string  | yes      | Human-readable label shown in the form |
| `required` | boolean | no       | Field must be filled before saving |
| `default`  | string  | no       | Pre-filled value in the form |
| `secret`   | boolean | no       | Value is masked in the UI and encrypted at rest by the main app |

---

### `POST /connector/test`

Validates connectivity with the given config. Called when the user clicks "Test Connection".

**Request:**
```json
{ "config": { "directory": "data", "filePattern": "**/*.txt" } }
```

**Response (success):**
```json
{ "ok": true, "documentCount": 5, "message": "Connected — 5 matching documents found" }
```

**Response (failure):**
```json
{ "ok": false, "message": "Directory not found: /no/such/path" }
```

Always return HTTP 200; communicate errors via `ok: false` + `message`.

---

### `POST /connector/documents`

Returns the full list of available documents with their IDs, paths, and content hashes.
The main app uses `contentHash` to skip unchanged documents during incremental sync.

**Request:**
```json
{ "config": { "directory": "data", "filePattern": "**/*.txt" } }
```

**Response:**
```json
{
  "documents": [
    { "id": "file:privacy/art-5.txt",  "path": "privacy/art-5.txt",  "contentHash": "md5:a1b2..." },
    { "id": "file:traffic/art-12.txt", "path": "traffic/art-12.txt", "contentHash": "md5:c3d4..." }
  ]
}
```

- `id` — stable, opaque identifier used in `/connector/fetch`. Can be any unique string.
- `path` — human-readable identifier shown in citations. Use a relative file path or a full URL.
  For web crawlers, `path` becomes the clickable source URL in the chat response.
- `contentHash` — any stable hash of the document content. The main app skips fetch if unchanged.

---

### `POST /connector/fetch`

Returns the raw text content for a single document identified by its `id`.

**Request:**
```json
{ "config": { "directory": "data" }, "documentId": "file:traffic/art-12.txt" }
```

**Response:**
```json
{
  "path": "traffic/art-12.txt",
  "rawContent": "Art. 12 — Driving without a valid licence\n\nAny person who..."
}
```

`rawContent` must be plain text. Strip all HTML, Markdown, or binary encoding before returning.
The main app stores `rawContent` verbatim and passes it to the configured chunking strategy.

---

## Pre-deployed example instance

A live instance with Bulgarian sample data (traffic laws, data protection law, and a local character profile)
is deployed at:

```
https://data-source-example.internal.pavel-usanli.online
```

When registering it in the main app, use these values:

| Field | Value |
|---|---|
| Connector URL | `https://data-source-example.internal.pavel-usanli.online` |
| Subdirectory within data root | *(leave empty to index all documents, or enter `zdp`, `zzld`, or `hora` to limit to one topic)* |
| File pattern (glob) | `**/*.txt` |

Topic directories:
- `zdp` — Bulgarian Road Traffic Act (ЗДП) articles
- `zzld` — Personal Data Protection Act (ЗЗЛД) articles
- `hora` — Local character profiles

---

## Registering a connector in the main app

1. Deploy the connector service so it is reachable from the main app (e.g. `http://my-connector:8081`).
2. Open **Knowledge Base → [your KB] → Add Data Source**.
3. Enter the connector URL and click **Discover** — the main app calls `GET /connector/info`
   and renders the config form from the returned `fields`.
4. Fill in the config, click **Test Connection**, then **Save**.
5. Click **Sync Now** to run the first ingestion.

---

## Writing a new connector

Copy this directory, replace the business logic in `index.js`, and update `fields` in
`CONNECTOR_INFO` to match your source's required config. The contract is the same for every
language — a Go, Python, or JVM connector only needs to implement the same 4 JSON endpoints.