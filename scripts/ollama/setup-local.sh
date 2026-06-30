#!/usr/bin/env bash
set -euo pipefail

# Models used by this project
CHAT_MODEL="gemma3:4b-it-q4_K_M"
EMBED_MODEL="mxbai-embed-large"

# Port the app expects (spring.ai.ollama.base-url: http://localhost:11431)
OLLAMA_PORT=11431

# ── helpers ──────────────────────────────────────────────────────────────────

info()  { echo "[INFO]  $*"; }
warn()  { echo "[WARN]  $*"; }
error() { echo "[ERROR] $*" >&2; exit 1; }

# ── 1. check ollama is installed ─────────────────────────────────────────────

if ! command -v ollama &>/dev/null; then
    warn "ollama not found. Installing via official script..."
    curl -fsSL https://ollama.com/install.sh | sh
    if ! command -v ollama &>/dev/null; then
        error "Installation failed. Install manually: https://ollama.com/download"
    fi
    info "ollama installed: $(ollama --version)"
else
    info "ollama found: $(ollama --version)"
fi

# ── 2. start ollama on the expected port if not already running ───────────────

if curl -sf "http://localhost:${OLLAMA_PORT}" &>/dev/null; then
    info "ollama already running on port ${OLLAMA_PORT}"
else
    info "Starting ollama on port ${OLLAMA_PORT}..."
    OLLAMA_HOST="127.0.0.1:${OLLAMA_PORT}" ollama serve &
    OLLAMA_PID=$!

    # wait up to 15 s for the server to be ready
    for i in $(seq 1 15); do
        if curl -sf "http://localhost:${OLLAMA_PORT}" &>/dev/null; then
            info "ollama is up (pid ${OLLAMA_PID})"
            break
        fi
        if [ "$i" -eq 15 ]; then
            error "ollama did not start within 15 s"
        fi
        sleep 1
    done
fi

# ── 3. pull models ────────────────────────────────────────────────────────────

pull_model() {
    local model="$1"
    if OLLAMA_HOST="127.0.0.1:${OLLAMA_PORT}" ollama list 2>/dev/null | grep -q "^${model}"; then
        info "Model already present: ${model}"
    else
        info "Pulling model: ${model} ..."
        OLLAMA_HOST="127.0.0.1:${OLLAMA_PORT}" ollama pull "${model}"
        info "Pulled: ${model}"
    fi
}

pull_model "${CHAT_MODEL}"
pull_model "${EMBED_MODEL}"

# ── done ─────────────────────────────────────────────────────────────────────

echo ""
info "Done. Ollama is running on http://localhost:${OLLAMA_PORT}"
info "Models available:"
OLLAMA_HOST="127.0.0.1:${OLLAMA_PORT}" ollama list