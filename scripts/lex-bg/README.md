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
| `DELAY_MS` | `300` | Politeness delay between requests |
| `PRELOAD_PATH` | — | Directory of pre-fetched `.md` files (`{category}/{docId}.md`). Files found here skip a network fetch and provide a real content hash. Newly fetched docs are saved here too. |
| `PRELOAD` | `false` | After catalog discovery, fetch and cache all documents not already in `PRELOAD_PATH`. |