import { google } from "googleapis";
import { z } from "zod";

function calendarClient(token) {
  const auth = new google.auth.OAuth2();
  auth.setCredentials({ access_token: token });
  return google.calendar({ version: "v3", auth });
}

async function fetchEvents(cal, calendarId, { timeMin, timeMax, maxResults, query }) {
  const res = await cal.events.list({
    calendarId,
    timeMin,
    timeMax,
    maxResults,
    q: query,
    singleEvents: true,
    orderBy: "startTime",
  });
  return (res.data.items ?? []).map((e) => ({ ...e, _calendarId: calendarId }));
}

export function registerCalendarTools(server, token) {
  server.registerTool(
    "calendar_list_events",
    {
      description: "List events from one or more Google Calendars (primary, shared, holiday imports, etc.). Results are merged and sorted by start time.",
      inputSchema: z.object({
        calendarIds: z
          .array(z.string())
          .default(["primary"])
          .describe("Calendar IDs to query. Use calendar_list_calendars to discover IDs. Default: [\"primary\"]"),
        timeMin: z.string().optional().describe("Start of time range (ISO 8601)"),
        timeMax: z.string().optional().describe("End of time range (ISO 8601)"),
        maxResults: z.number().int().min(1).max(2500).default(10).describe("Max results per calendar"),
      }),
    },
    async ({ calendarIds, timeMin, timeMax, maxResults }) => {
      const cal = calendarClient(token);
      const results = await Promise.all(
        calendarIds.map((id) => fetchEvents(cal, id, { timeMin, timeMax, maxResults }))
      );
      const merged = results
        .flat()
        .sort((a, b) => new Date(a.start?.dateTime ?? a.start?.date) - new Date(b.start?.dateTime ?? b.start?.date));
      return { content: [{ type: "text", text: JSON.stringify(merged) }] };
    }
  );

  server.registerTool(
    "calendar_create_event",
    {
      description: "Create a new event in a Google Calendar",
      inputSchema: z.object({
        summary: z.string().describe("Event title"),
        start: z.string().describe("Start datetime (ISO 8601)"),
        end: z.string().describe("End datetime (ISO 8601)"),
        description: z.string().optional().describe("Event description"),
        attendees: z.array(z.string()).optional().describe("Attendee email addresses"),
        calendarId: z.string().default("primary"),
      }),
    },
    async ({ summary, start, end, description, attendees, calendarId }) => {
      const cal = calendarClient(token);
      const event = await cal.events.insert({
        calendarId,
        requestBody: {
          summary,
          description,
          start: { dateTime: start },
          end: { dateTime: end },
          attendees: attendees?.map((email) => ({ email })),
        },
      });
      return { content: [{ type: "text", text: JSON.stringify(event.data) }] };
    }
  );

  server.registerTool(
    "calendar_get_event",
    {
      description: "Get a specific calendar event by ID",
      inputSchema: z.object({
        eventId: z.string().describe("The calendar event ID"),
        calendarId: z.string().default("primary"),
      }),
    },
    async ({ eventId, calendarId }) => {
      const cal = calendarClient(token);
      const event = await cal.events.get({ calendarId, eventId });
      return { content: [{ type: "text", text: JSON.stringify(event.data) }] };
    }
  );

  server.registerTool(
    "calendar_delete_event",
    {
      description: "Delete a calendar event",
      inputSchema: z.object({
        eventId: z.string().describe("The calendar event ID to delete"),
        calendarId: z.string().default("primary"),
      }),
      annotations: { destructiveHint: true, idempotentHint: true },
    },
    async ({ eventId, calendarId }) => {
      const cal = calendarClient(token);
      await cal.events.delete({ calendarId, eventId });
      return { content: [{ type: "text", text: `Event ${eventId} deleted.` }] };
    }
  );

  server.registerTool(
    "calendar_search_events",
    {
      description: "Search for events by keyword across one or more calendars. Useful for finding meetings, holidays, or events by title or description.",
      inputSchema: z.object({
        query: z.string().describe("Free-text search query (searches event title, description, location, attendees)"),
        calendarIds: z
          .array(z.string())
          .default(["primary"])
          .describe("Calendar IDs to search. Pass multiple to search across all your calendars including holidays and shared ones."),
        timeMin: z.string().optional().describe("Limit results to events after this datetime (ISO 8601)"),
        timeMax: z.string().optional().describe("Limit results to events before this datetime (ISO 8601)"),
        maxResults: z.number().int().min(1).max(250).default(10).describe("Max results per calendar"),
      }),
    },
    async ({ query, calendarIds, timeMin, timeMax, maxResults }) => {
      const cal = calendarClient(token);
      const results = await Promise.all(
        calendarIds.map((id) => fetchEvents(cal, id, { timeMin, timeMax, maxResults, query }))
      );
      const merged = results
        .flat()
        .sort((a, b) => new Date(a.start?.dateTime ?? a.start?.date) - new Date(b.start?.dateTime ?? b.start?.date));
      return { content: [{ type: "text", text: JSON.stringify(merged) }] };
    }
  );

  server.registerTool(
    "calendar_list_calendars",
    {
      description: "List all calendars the user has access to, including calendars shared by others",
      inputSchema: z.object({}),
    },
    async () => {
      const cal = calendarClient(token);
      const res = await cal.calendarList.list();
      const calendars = (res.data.items ?? []).map((c) => ({
        id: c.id,
        summary: c.summary,
        description: c.description,
        accessRole: c.accessRole,
        primary: c.primary ?? false,
      }));
      return { content: [{ type: "text", text: JSON.stringify(calendars) }] };
    }
  );

  server.registerTool(
    "calendar_check_availability",
    {
      description: "Check free/busy availability for one or more people or calendars over a time range",
      inputSchema: z.object({
        emails: z.array(z.string()).describe("Email addresses or calendar IDs to check (e.g. colleague's email)"),
        timeMin: z.string().describe("Start of the time range to check (ISO 8601)"),
        timeMax: z.string().describe("End of the time range to check (ISO 8601)"),
      }),
    },
    async ({ emails, timeMin, timeMax }) => {
      const cal = calendarClient(token);
      const res = await cal.freebusy.query({
        requestBody: {
          timeMin,
          timeMax,
          items: emails.map((id) => ({ id })),
        },
      });
      return { content: [{ type: "text", text: JSON.stringify(res.data.calendars) }] };
    }
  );
}