# Google Cloud Setup

Sets up a GCP project with Gmail + Calendar APIs enabled, then guides you through creating an OAuth client. No Terraform — pure `gcloud` CLI.

## Prerequisites

### 1. Authenticate with Google Cloud

```bash
gcloud auth login
gcloud auth application-default login
```

The script will run both automatically if not already done.

### 2. Billing account

You need a Google Cloud billing account. The script lists your accounts automatically.
Create one at [console.cloud.google.com/billing](https://console.cloud.google.com/billing) if needed.

---

## Run

```bash
cd scripts/gcloud
./setup.sh
```

The script will:
1. Install `gcloud` if missing (macOS/Linux)
2. Check authentication — runs `gcloud auth login` if needed
3. List billing accounts, prompt to select one
4. Prompt for a project ID (random suffix suggested to avoid collisions)
5. Create the GCP project and link billing
6. Enable Gmail + Calendar APIs
7. Open Google Cloud Console to create the OAuth consent screen + client
8. Prompt to paste the Client ID and Secret, then print the export commands

---

## After Running

Add the credentials to your environment or `.env` file (never commit this):

```bash
export GOOGLE_CLIENT_ID='...'
export GOOGLE_CLIENT_SECRET='...'
```

---

## Re-running

The script is safe to re-run — it skips project creation if the project already exists.
API enablement is idempotent. The OAuth client step opens the Console again — skip it if you already have credentials.

---

## OAuth scopes reference

Scopes are added in two places:
1. **Google Cloud Console** → APIs & Services → OAuth consent screen → Scopes (what users are asked to consent to)
2. **`application.yaml`** → `spring.security.oauth2.client.registration.google.scope` (what your app requests)

Both lists must match.

### Recommended scopes

| Scope | Access | Sensitivity |
|---|---|---|
| `https://www.googleapis.com/auth/gmail.modify` | Read + send + archive/label emails | Sensitive |
| `https://www.googleapis.com/auth/gmail.send` | Send only (no read) | Sensitive |
| `https://www.googleapis.com/auth/gmail.readonly` | Read only | Sensitive |
| `https://www.googleapis.com/auth/calendar` | Full calendar read + write | Sensitive |
| `https://www.googleapis.com/auth/calendar.readonly` | Read calendar only | Sensitive |
| `https://www.googleapis.com/auth/calendar.events` | Read + write events only | Sensitive |

**For read + write Gmail and Calendar use:**
```
https://www.googleapis.com/auth/gmail.modify
https://www.googleapis.com/auth/calendar
```

> Avoid `https://www.googleapis.com/auth/gmail` (full Gmail access) — it is a **restricted** scope and requires a Google security assessment on top of standard verification.

### Manual Console setup (cannot be automated for personal GCP projects)

Google does not expose a public API, `gcloud` CLI command, or Terraform resource for configuring the OAuth consent screen on personal projects — it is Console-only.

**Step 1 — OAuth consent screen**
Go to **APIs & Services → OAuth consent screen**

| Field | Value |
|---|---|
| User type | External |
| App name | AI Assistant (or your app name) |
| User support email | your email |
| Developer contact email | your email |
| App domain / Homepage | your domain (optional) |
| Privacy policy URL | required for verification |

**Step 2 — Add scopes**
Click **"Add or remove scopes"** and add:
```
https://www.googleapis.com/auth/gmail.modify
https://www.googleapis.com/auth/calendar
```

**Step 3 — Create OAuth client**
Go to **APIs & Services → Credentials → Create Credentials → OAuth client ID**

| Field | Value |
|---|---|
| Application type | Web application |
| Name | AI Assistant |
| Authorised redirect URIs | `http://localhost:8080/settings/connectors/google/callback` |

Add your production domain redirect URI when deploying:
```
https://yourdomain.com/settings/connectors/google/callback
```

Copy the **Client ID** and **Client Secret** — these are your `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.

---

### `application.yaml` example

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
              - https://www.googleapis.com/auth/gmail.modify
              - https://www.googleapis.com/auth/calendar
```

---

## Publishing the app for real users

By default the OAuth consent screen is in **Testing** mode — max 100 test users, everyone sees an "unverified app" warning.

### Publish without verification (trusted/internal users)

1. Go to **Google Cloud Console → APIs & Services → OAuth consent screen**
2. Click **Publish App**

Users will still see a warning screen but can click **Advanced → Go to [app name] (unsafe)** to proceed. Fine for private or internal use.

### Full verification (public users, no warning)

Gmail and Calendar are **sensitive scopes** — Google requires verification before the warning is removed. You'll need:

- A publicly accessible **Privacy Policy URL**
- An **App Homepage URL**
- A description of how your app uses Gmail/Calendar data
- Screenshots of the OAuth flow in your app

Submit via the OAuth consent screen page → **"Prepare for verification"**. Google's review typically takes **4–6 weeks** for sensitive scopes.

> If you use more permissive scopes (e.g. full Gmail access instead of `gmail.readonly`), a third-party security assessment is also required.