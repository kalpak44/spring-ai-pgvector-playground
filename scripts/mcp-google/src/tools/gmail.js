import { google } from "googleapis";
import { z } from "zod";
import { computeDateRange } from "./utils.js";

function gmailClient(token) {
  const auth = new google.auth.OAuth2();
  auth.setCredentials({ access_token: token });
  return google.gmail({ version: "v1", auth });
}

function buildRawEmail(to, subject, body, cc) {
  const lines = [
    `To: ${to}`,
    cc ? `Cc: ${cc}` : null,
    `Subject: ${subject}`,
    "MIME-Version: 1.0",
    "Content-Type: text/plain; charset=utf-8",
    "",
    body,
  ].filter(Boolean);
  return Buffer.from(lines.join("\r\n")).toString("base64url");
}

function header(headers, name) {
  return headers?.find((h) => h.name.toLowerCase() === name.toLowerCase())?.value ?? null;
}

function decodeBase64(data) {
  return Buffer.from(data, "base64url").toString("utf-8");
}

function cap(text, maxLength) {
  return text.length > maxLength ? text.slice(0, maxLength) + "…[truncated]" : text;
}

function partWithData(parts, mimeType) {
  return parts.find((p) => p.mimeType === mimeType && p.body?.data) ?? null;
}

function bodyFromParts(parts, maxLength) {
  const plain = partWithData(parts, "text/plain");
  if (plain) return cap(decodeBase64(plain.body.data), maxLength);

  for (const part of parts) {
    if (part.mimeType?.startsWith("multipart/")) {
      const nested = extractBody(part, maxLength);
      if (nested) return nested;
    }
  }

  const html = partWithData(parts, "text/html");
  if (html) {
    const text = decodeBase64(html.body.data).replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
    return cap(text, maxLength);
  }

  return null;
}

// Recursively extracts plain text from a Gmail MIME payload.
// Prefers text/plain, falls back to tag-stripped text/html.
function extractBody(payload, maxLength = 3000) {
  if (!payload) return null;
  if (payload.body?.data) return cap(decodeBase64(payload.body.data), maxLength);
  if (payload.parts) return bodyFromParts(payload.parts, maxLength);
  return null;
}

function hasAttachment(payload) {
  if (!payload?.parts) return false;
  return payload.parts.some((p) => p.filename && p.filename.length > 0);
}

// Full message with decoded body — used by gmail_get_message.
function formatMessage(msg) {
  const h = msg.payload?.headers ?? [];
  return {
    id: msg.id,
    threadId: msg.threadId,
    from: header(h, "from"),
    to: header(h, "to"),
    cc: header(h, "cc"),
    subject: header(h, "subject"),
    date: header(h, "date"),
    snippet: msg.snippet,
    body: extractBody(msg.payload),
    hasAttachment: hasAttachment(msg.payload),
    labels: msg.labelIds ?? [],
  };
}

// Compact summary without body — used by list and search.
function formatSummary(msg) {
  const h = msg.payload?.headers ?? [];
  return {
    id: msg.id,
    from: header(h, "from"),
    subject: header(h, "subject"),
    date: header(h, "date"),
    snippet: msg.snippet,
    hasAttachment: hasAttachment(msg.payload),
    labels: msg.labelIds ?? [],
  };
}

export function registerGmailTools(server, token) {
  server.registerTool(
    "gmail_list_messages",
    {
      description:
        "List recent Gmail messages with key metadata (from, subject, date, snippet). " +
        "Use gmail_get_message to retrieve the full body of a specific message.",
      inputSchema: z.object({
        maxResults: z.number().int().min(1).max(50).default(10),
        query: z.string().optional().describe("Gmail search query (e.g. 'is:unread', 'from:alice')"),
        labelIds: z.array(z.string()).optional().describe("Filter by label IDs"),
      }),
    },
    async ({ maxResults, query, labelIds }) => {
      const gmail = gmailClient(token);
      const list = await gmail.users.messages.list({ userId: "me", maxResults, q: query, labelIds });
      const ids = list.data.messages ?? [];
      const messages = await Promise.all(
        ids.map((m) =>
          gmail.users.messages.get({ userId: "me", id: m.id, format: "metadata" }).then((r) => formatSummary(r.data))
        )
      );
      return { content: [{ type: "text", text: JSON.stringify(messages) }] };
    }
  );

  server.registerTool(
    "gmail_get_message",
    {
      description:
        "Get the full content of a Gmail message by ID, including the decoded body. " +
        "Use this after gmail_list_messages or gmail_search_messages to read a specific email.",
      inputSchema: z.object({
        messageId: z.string().describe("The Gmail message ID"),
      }),
    },
    async ({ messageId }) => {
      const gmail = gmailClient(token);
      const msg = await gmail.users.messages.get({ userId: "me", id: messageId, format: "full" });
      return { content: [{ type: "text", text: JSON.stringify(formatMessage(msg.data)) }] };
    }
  );

  server.registerTool(
    "gmail_send_message",
    {
      description: "Send an email via Gmail",
      inputSchema: z.object({
        to: z.string().describe("Recipient email address"),
        subject: z.string().describe("Email subject"),
        body: z.string().describe("Plain-text email body"),
        cc: z.string().optional().describe("CC email address"),
      }),
    },
    async ({ to, subject, body, cc }) => {
      const gmail = gmailClient(token);
      const raw = buildRawEmail(to, subject, body, cc);
      const sent = await gmail.users.messages.send({ userId: "me", requestBody: { raw } });
      return { content: [{ type: "text", text: JSON.stringify({ id: sent.data.id, threadId: sent.data.threadId }) }] };
    }
  );

  server.registerTool(
    "gmail_search_messages",
    {
      description:
        "Search Gmail using Gmail search syntax and return a list of matching messages with key metadata. " +
        "Use gmail_get_message to read the full body of any result.",
      inputSchema: z.object({
        query: z.string().describe("Gmail search query (e.g. 'from:alice subject:invoice is:unread')"),
        maxResults: z.number().int().min(1).max(50).default(10),
      }),
    },
    async ({ query, maxResults }) => {
      const gmail = gmailClient(token);
      const list = await gmail.users.messages.list({ userId: "me", q: query, maxResults });
      const ids = list.data.messages ?? [];
      const messages = await Promise.all(
        ids.map((m) =>
          gmail.users.messages.get({ userId: "me", id: m.id, format: "metadata" }).then((r) => formatSummary(r.data))
        )
      );
      return { content: [{ type: "text", text: JSON.stringify(messages) }] };
    }
  );

  server.registerTool(
    "gmail_summary",
    {
      description:
        "Summarize Gmail activity for a named period (today, this week, this month, etc.) or a custom date range. " +
        "Returns total and unread counts, top senders with message counts, and a compact message list. " +
        "Ideal for answering 'what emails did I get today/this week?' without multiple tool calls. " +
        "Use gmail_get_message to read the body of any individual result.",
      inputSchema: z.object({
        period: z
          .enum(["today", "yesterday", "this_week", "last_week", "this_month", "last_month", "custom"])
          .describe("Time period to summarize"),
        referenceDate: z
          .string()
          .optional()
          .describe("ISO date (YYYY-MM-DD) used as 'today' anchor — pass the current date from context. Defaults to server clock."),
        dateFrom: z.string().optional().describe("ISO date (YYYY-MM-DD) — required when period is 'custom'"),
        dateTo: z.string().optional().describe("ISO date (YYYY-MM-DD) — required when period is 'custom'"),
        maxMessages: z.number().int().min(1).max(100).default(30).describe("Max messages to include in the list"),
        unreadOnly: z.boolean().default(false).describe("Restrict to unread messages only"),
      }),
    },
    async ({ period, referenceDate, dateFrom, dateTo, maxMessages, unreadOnly }) => {
      const { start, end, label } = computeDateRange(period, referenceDate, dateFrom, dateTo);

      const toGmailDate = (d) =>
        `${d.getUTCFullYear()}/${String(d.getUTCMonth() + 1).padStart(2, "0")}/${String(d.getUTCDate()).padStart(2, "0")}`;

      const baseQuery = `after:${toGmailDate(start)} before:${toGmailDate(end)}`;
      const query = unreadOnly ? `${baseQuery} is:unread` : baseQuery;

      const gmail = gmailClient(token);
      const list = await gmail.users.messages.list({ userId: "me", q: query, maxResults: maxMessages });
      const ids = list.data.messages ?? [];

      const messages = await Promise.all(
        ids.map((m) =>
          gmail.users.messages.get({ userId: "me", id: m.id, format: "metadata" }).then((r) => formatSummary(r.data))
        )
      );

      const unreadCount = messages.filter((m) => m.labels.includes("UNREAD")).length;
      const topSenders = buildTopSenders(messages);

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify({ period: label, total: messages.length, unread: unreadCount, topSenders, messages }),
          },
        ],
      };
    }
  );
}

function extractEmailAddress(fromHeader) {
  if (!fromHeader) return "unknown";
  const match = fromHeader.match(/<([^>]+)>/);
  return match ? match[1].toLowerCase() : fromHeader.trim().toLowerCase();
}

function buildTopSenders(messages, limit = 15) {
  const map = new Map();
  for (const msg of messages) {
    const email = extractEmailAddress(msg.from);
    if (!map.has(email)) {
      map.set(email, { email, from: msg.from, count: 0, latestSubject: null, latestDate: null });
    }
    const entry = map.get(email);
    entry.count++;
    if (!entry.latestDate || new Date(msg.date) > new Date(entry.latestDate)) {
      entry.latestDate = msg.date;
      entry.latestSubject = msg.subject;
    }
  }
  return [...map.values()].sort((a, b) => b.count - a.count).slice(0, limit);
}