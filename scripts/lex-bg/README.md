# lex-bg connector

Live proxy connector for [lex.bg](https://lex.bg) — serves Bulgarian legislation directly to the Knowledge Base without local storage.

On startup the server discovers all documents from the lex.bg category tree in the background. The catalog refreshes daily; content is fetched on demand per the [connector contract](CONTRACT.md).

## Run locally

```sh
npm install
npm start
```

## Docker

```sh
docker build -t lex-bg .
docker run -p 3200:3200 lex-bg
```

## Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `3200` | HTTP port |
| `DELAY_MS` | `1500` | Politeness delay between requests (ms) |
| `PRELOAD_PATH` | — | Directory of pre-fetched `.md` files (`{category}/{docId}.md`). Files found here are loaded at startup and skip a network fetch. Newly fetched docs are saved here too. A `.preload-meta.json` tracking fetch timestamps is maintained alongside. |
| `PRELOAD` | `false` | After catalog discovery, fetch and cache all documents not already fresh in `PRELOAD_PATH`. |
| `PRELOAD_CRON` | — | Cron expression for recurring preload (e.g. `0 2 * * *` for daily at 2 am). |
| `PRELOAD_MAX_AGE_DAYS` | `7` | How many days before a cached document is considered stale and re-fetched during preload. |