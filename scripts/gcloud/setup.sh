#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── colours ────────────────────────────────────────────────────────────────────
BOLD='\033[1m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
RESET='\033[0m'

print_header() { echo -e "\n${BOLD}${CYAN}$1${RESET}"; }
print_ok()     { echo -e "${GREEN}✓${RESET} $1"; }
print_warn()   { echo -e "${YELLOW}⚠${RESET}  $1"; }
print_err()    { echo -e "${RED}✗${RESET}  $1"; }

ask() {
  local prompt="$1" default="${2:-}" var
  if [[ -n "$default" ]]; then
    read -rp "$(echo -e "${BOLD}${prompt}${RESET} [${default}]: ")" var
    echo "${var:-$default}"
  else
    while true; do
      read -rp "$(echo -e "${BOLD}${prompt}${RESET}: ")" var
      [[ -n "$var" ]] && break
      print_err "This field is required."
    done
    echo "$var"
  fi
}

# ── platform ───────────────────────────────────────────────────────────────────
OS="$(uname -s)"
ARCH="$(uname -m)"

install_brew() {
  print_warn "Homebrew not found — installing..."
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  if [[ "$ARCH" == "arm64" ]]; then
    eval "$(/opt/homebrew/bin/brew shellenv)"
  else
    eval "$(/usr/local/bin/brew shellenv)"
  fi
}

install_gcloud() {
  print_warn "gcloud not found — installing..."
  case "$OS" in
    Darwin)
      command -v brew &>/dev/null || install_brew
      brew install --cask google-cloud-sdk
      local sdk_path
      sdk_path="$(brew --prefix)/share/google-cloud-sdk"
      [[ -f "$sdk_path/path.bash.inc" ]] && source "$sdk_path/path.bash.inc"
      ;;
    Linux)
      if command -v apt-get &>/dev/null; then
        curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg \
          | sudo gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg
        echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] \
https://packages.cloud.google.com/apt cloud-sdk main" \
          | sudo tee /etc/apt/sources.list.d/google-cloud-sdk.list
        sudo apt-get update && sudo apt-get install -y google-cloud-cli
      elif command -v yum &>/dev/null || command -v dnf &>/dev/null; then
        sudo tee /etc/yum.repos.d/google-cloud-sdk.repo <<'REPO'
[google-cloud-cli]
name=Google Cloud CLI
baseurl=https://packages.cloud.google.com/yum/repos/cloud-sdk-el9-x86_64
enabled=1
gpgcheck=1
repo_gpgcheck=0
gpgkey=https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
REPO
        sudo yum install -y google-cloud-cli
      else
        print_err "No supported package manager. Install gcloud manually: https://cloud.google.com/sdk/docs/install"
        exit 1
      fi
      ;;
    *)
      print_err "Unsupported OS: $OS. Install gcloud manually: https://cloud.google.com/sdk/docs/install"
      exit 1
      ;;
  esac
}

# ── preflight ──────────────────────────────────────────────────────────────────
print_header "Preflight checks"

if command -v gcloud &>/dev/null; then
  print_ok "gcloud found ($(command -v gcloud))"
else
  install_gcloud
  command -v gcloud &>/dev/null || { print_err "gcloud installation failed."; exit 1; }
  print_ok "gcloud installed"
fi

# ── auth ───────────────────────────────────────────────────────────────────────
GCLOUD_ACCOUNT=$(gcloud auth list --filter=status:ACTIVE --format="value(account)" 2>/dev/null | head -1)
if [[ -z "$GCLOUD_ACCOUNT" ]]; then
  print_warn "No active gcloud account — running: gcloud auth login"
  gcloud auth login
  GCLOUD_ACCOUNT=$(gcloud auth list --filter=status:ACTIVE --format="value(account)" 2>/dev/null | head -1)
fi
print_ok "Authenticated as ${GCLOUD_ACCOUNT}"

if ! gcloud auth application-default print-access-token &>/dev/null; then
  print_warn "Application Default Credentials not found — running: gcloud auth application-default login"
  gcloud auth application-default login
fi
print_ok "Application Default Credentials present"

# ── billing account ────────────────────────────────────────────────────────────
print_header "Billing account"
echo -e "  Create/manage : ${CYAN}https://console.cloud.google.com/billing${RESET}"
echo
gcloud billing accounts list \
  --format="table(name.basename():label=ACCOUNT_ID, displayName:label=NAME, open:label=OPEN)" 2>/dev/null \
  || print_warn "Could not list billing accounts — check your permissions."
echo
DEFAULT_BILLING=$(gcloud billing accounts list --filter="open=true" \
  --format="value(name.basename())" 2>/dev/null | head -1 || echo "")
BILLING_ACCOUNT_ID=$(ask "Billing account ID" "$DEFAULT_BILLING")

# ── project ────────────────────────────────────────────────────────────────────
print_header "Project"
echo -e "  Project IDs are ${BOLD}globally unique across all GCP users${RESET}."
echo -e "  Rules: 6-30 chars, lowercase letters, digits, hyphens only."
echo -e "  Browse existing : ${CYAN}https://console.cloud.google.com/cloud-resource-manager${RESET}"
echo
RANDOM_SUFFIX=$(set +o pipefail; LC_ALL=C tr -dc 'a-z0-9' < /dev/urandom | head -c 8)
PROJECT_ID=$(ask "Project ID" "ai-assistant-${RANDOM_SUFFIX}")

# ── other settings ─────────────────────────────────────────────────────────────
REGION=$(ask "Region" "europe-west1")
APP_NAME=$(ask "App name (shown on OAuth consent screen)" "AI Assistant")
SUPPORT_EMAIL=$(ask "Support email" "$GCLOUD_ACCOUNT")

# ── confirm ────────────────────────────────────────────────────────────────────
print_header "Summary"
echo -e "  Billing account : ${BOLD}${BILLING_ACCOUNT_ID}${RESET}"
echo -e "  Project ID      : ${BOLD}${PROJECT_ID}${RESET}"
echo -e "  Region          : ${BOLD}${REGION}${RESET}"
echo -e "  App name        : ${BOLD}${APP_NAME}${RESET}"
echo -e "  Support email   : ${BOLD}${SUPPORT_EMAIL}${RESET}"
echo
read -rp "$(echo -e "${BOLD}Proceed? [y/N]: ${RESET}")" CONFIRM
[[ "$CONFIRM" == "y" || "$CONFIRM" == "Y" ]] || { echo "Aborted."; exit 0; }

# ── create project ─────────────────────────────────────────────────────────────
print_header "Creating project"
if gcloud projects describe "$PROJECT_ID" &>/dev/null; then
  print_warn "Project '${PROJECT_ID}' already exists — using it."
else
  gcloud projects create "$PROJECT_ID" --name="$APP_NAME"
  print_ok "Project '${PROJECT_ID}' created."
fi

gcloud config set project "$PROJECT_ID" --quiet
print_ok "Active project set to ${PROJECT_ID}"

# ── link billing ───────────────────────────────────────────────────────────────
print_header "Linking billing"
gcloud billing projects link "$PROJECT_ID" --billing-account="$BILLING_ACCOUNT_ID" 2>/dev/null \
  && print_ok "Billing account linked." \
  || print_warn "Could not link billing account — it may already be linked or you lack permission."

# ── enable APIs ────────────────────────────────────────────────────────────────
print_header "Enabling APIs"
gcloud services enable \
  gmail.googleapis.com \
  calendar-json.googleapis.com \
  --project="$PROJECT_ID"
print_ok "Gmail and Calendar APIs enabled."

# ── ADC quota project ──────────────────────────────────────────────────────────
gcloud auth application-default set-quota-project "$PROJECT_ID" --quiet 2>/dev/null \
  && print_ok "ADC quota project set to ${PROJECT_ID}" \
  || print_warn "Could not set ADC quota project — you may see quota warnings."

# ── OAuth client — manual step ─────────────────────────────────────────────────
print_header "Create OAuth client"
echo "Gmail and Calendar APIs are enabled. Now create the OAuth consent screen and client."
echo "The browser will open — follow these steps:"
echo
echo "  1. Configure consent screen  →  External  →  fill app name + support email"
echo "  2. Credentials → Create Credentials → OAuth client ID"
echo "  3. Application type: Web application"
echo "  4. Add redirect URI: http://localhost:8080/settings/connectors/google/callback"
echo "  5. Copy the Client ID and Client Secret"
echo
read -rp "$(echo -e "${BOLD}Press Enter to open the Console (consent screen first)...${RESET}")"
case "$OS" in
  Darwin)
    open "https://console.cloud.google.com/apis/credentials/consent?project=${PROJECT_ID}"
    sleep 3
    open "https://console.cloud.google.com/apis/credentials?project=${PROJECT_ID}"
    ;;
  Linux)
    xdg-open "https://console.cloud.google.com/apis/credentials/consent?project=${PROJECT_ID}" 2>/dev/null || true
    sleep 3
    xdg-open "https://console.cloud.google.com/apis/credentials?project=${PROJECT_ID}" 2>/dev/null || true
    ;;
esac

echo
CLIENT_ID=$(ask "Paste your Client ID")
CLIENT_SECRET=$(ask "Paste your Client Secret")

# ── done ───────────────────────────────────────────────────────────────────────
print_header "Setup complete — environment variables"
echo
echo -e "  export GOOGLE_CLIENT_ID='${CLIENT_ID}'"
echo -e "  export GOOGLE_CLIENT_SECRET='${CLIENT_SECRET}'"
echo -e "  export GOOGLE_PROJECT_ID='${PROJECT_ID}'"
echo -e "  export GOOGLE_REGION='${REGION}'"
echo
print_warn "Add the above to your .env file (never commit it)."
echo
print_ok "Done."