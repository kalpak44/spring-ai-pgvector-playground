import { google } from "googleapis";
import { z } from "zod";

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

export function registerGmailTools(server, token) {
  server.registerTool(
    "gmail_list_messages",
    {
      description: "List Gmail messages for the authenticated user",
      inputSchema: z.object({
        maxResults: z.number().int().min(1).max(500).default(10),
        query: z.string().optional().describe("Gmail search query"),
        labelIds: z.array(z.string()).optional().describe("Filter by label IDs"),
      }),
    },
    async ({ maxResults, query, labelIds }) => {
      const gmail = gmailClient(token);
      const list = await gmail.users.messages.list({
        userId: "me",
        maxResults,
        q: query,
        labelIds,
      });
      const messages = list.data.messages ?? [];
      return {
        content: [{ type: "text", text: JSON.stringify({ messages, resultSizeEstimate: list.data.resultSizeEstimate }) }],
      };
    }
  );

  server.registerTool(
    "gmail_get_message",
    {
      description: "Get a specific Gmail message by ID",
      inputSchema: z.object({
        messageId: z.string().describe("The Gmail message ID"),
        format: z.enum(["full", "metadata", "minimal", "raw"]).default("full"),
      }),
    },
    async ({ messageId, format }) => {
      const gmail = gmailClient(token);
      const msg = await gmail.users.messages.get({ userId: "me", id: messageId, format });
      return { content: [{ type: "text", text: JSON.stringify(msg.data) }] };
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
      description: "Search Gmail messages using Gmail search syntax",
      inputSchema: z.object({
        query: z.string().describe("Gmail search query (e.g. 'from:alice subject:invoice')"),
        maxResults: z.number().int().min(1).max(500).default(10),
      }),
    },
    async ({ query, maxResults }) => {
      const gmail = gmailClient(token);
      const list = await gmail.users.messages.list({ userId: "me", q: query, maxResults });
      const ids = list.data.messages ?? [];
      const messages = await Promise.all(
        ids.slice(0, maxResults).map((m) =>
          gmail.users.messages.get({ userId: "me", id: m.id, format: "metadata" }).then((r) => r.data)
        )
      );
      return { content: [{ type: "text", text: JSON.stringify(messages) }] };
    }
  );
}