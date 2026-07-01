# mcp-google

Node.js MCP server for Gmail and Google Calendar.

## Purpose

This project is an MCP server that allows AI assistants to read and write Gmail and Google Calendar data.
It runs over HTTP and is stateless — the caller supplies a Google OAuth access token with every request.
Token acquisition and refresh are handled by the Spring Boot application.

- List, get, search, and send Gmail messages
- List, create, get, and delete Google Calendar events
- Works with all calendars visible to the user, including shared calendars

## Requirements

- Node.js 20+
- A Google Cloud project with Gmail and Calendar APIs enabled
- A valid Google OAuth2 access token (`https://www.googleapis.com/auth/gmail.modify` + `https://www.googleapis.com/auth/calendar`)

See [`../gcloud/README.md`](../gcloud/README.md) for how to set up the Google Cloud project and OAuth credentials.

## Install

```bash
cd scripts/mcp-google
npm install
```

## Start

```bash
npm start
```

The server listens on `http://localhost:3100/mcp` by default. Set the `PORT` environment variable to override.

## Add To Claude Code

```bash
claude mcp add mcp-google \
  https://google-assistant.pavel-usanli.online/mcp \
  --transport http \
  --header "Authorization: Bearer YOUR_GOOGLE_ACCESS_TOKEN"
```

Remove it from Claude Code:

```bash
claude mcp remove mcp-google
```

## Add To Codex

```bash
export GOOGLE_ACCESS_TOKEN="YOUR_GOOGLE_ACCESS_TOKEN"
codex mcp add mcp-google \
  --url https://google-assistant.pavel-usanli.online/mcp \
  --bearer-token-env-var GOOGLE_ACCESS_TOKEN
```

Remove it from Codex:

```bash
codex mcp remove mcp-google
```

## Available Tools

| Tool                      | Description                                                  |
|---------------------------|--------------------------------------------------------------|
| `gmail_list_messages`     | List Gmail messages, optionally filtered by query or labels  |
| `gmail_get_message`       | Get a specific Gmail message by ID                           |
| `gmail_send_message`      | Send an email (to, subject, body, optional cc)               |
| `gmail_search_messages`   | Search Gmail using Gmail search syntax and return full metadata |
| `calendar_list_calendars`     | List all calendars the user has access to, including shared and holiday imports |
| `calendar_list_events`        | List events from one or more calendars merged and sorted by time                |
| `calendar_search_events`      | Search events by keyword across one or more calendars                           |
| `calendar_create_event`       | Create a new calendar event with optional attendees                             |
| `calendar_get_event`          | Get a specific calendar event by ID                                             |
| `calendar_delete_event`       | Delete a calendar event by ID                                                   |
| `calendar_check_availability` | Check free/busy status for one or more people over a time range                 |

`calendar_list_events` and `calendar_search_events` both accept a `calendarIds` array — pass multiple IDs to query across your primary calendar, holiday imports, and shared calendars in a single call. Use `calendar_list_calendars` first to discover available IDs.

## Example Prompts

```text
Do I have any unread emails from Alice?
```

```text
Search my inbox for invoices from last month.
```

```text
Send an email to bob@example.com with subject "Hello" and body "Hi Bob!".
```

```text
What's on my calendar this week?
```

```text
Schedule a meeting with alice@example.com tomorrow at 10am for one hour.
```

```text
Delete the event with ID abc123 from my primary calendar.
```

```text
List all calendars I have access to.
```

```text
Is alice@example.com free tomorrow between 10am and 12pm?
```

```text
Check availability of alice@example.com and bob@example.com next Monday from 9am to 5pm.
```

```text
What public holidays do I have this month?
```

```text
Show me everything on my calendar this week — primary, holidays, and the shared team calendar.
```

```text
Find all events mentioning "standup" across all my calendars.
```

## Auth

Every request must include an `Authorization: Bearer <google_access_token>` header.
The server returns `401` if the header is missing and `403` (from Google) if the token is invalid or lacks the required scopes.

In production this header is injected automatically by the Spring Boot app using the stored per-user OAuth token.