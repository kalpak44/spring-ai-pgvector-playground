import { google } from "googleapis";
import { z } from "zod";
import { computeDateRange } from "./utils.js";

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

// Strips Google API noise fields and returns a clean, flat event object.
function formatEvent(event) {
  const result = {
    id: event.id,
    calendarId: event._calendarId ?? "primary",
    title: event.summary ?? "(No title)",
    start: event.start?.dateTime ?? event.start?.date,
    end: event.end?.dateTime ?? event.end?.date,
    status: event.status,
  };
  if (event.description) result.description = event.description;
  if (event.location) result.location = event.location;
  if (event.htmlLink) result.link = event.htmlLink;
  if (event.organizer?.email) result.organizer = event.organizer.email;
  if (event.attendees?.length) {
    result.attendees = event.attendees.map((a) => ({
      email: a.email,
      ...(a.displayName ? { name: a.displayName } : {}),
      status: a.responseStatus,
    }));
  }
  return result;
}

export function registerCalendarTools(server, token) {
  server.registerTool(
    "calendar_list_events",
    {
      description:
        "List events from one or more Google Calendars. Results are merged and sorted by start time.",
      inputSchema: z.object({
        calendarIds: z
          .array(z.string())
          .default(["primary"])
          .describe('Calendar IDs to query. Use calendar_list_calendars to discover IDs. Default: ["primary"]'),
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
        .sort((a, b) => new Date(a.start?.dateTime ?? a.start?.date) - new Date(b.start?.dateTime ?? b.start?.date))
        .map(formatEvent);
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
      return { content: [{ type: "text", text: JSON.stringify(formatEvent({ ...event.data, _calendarId: calendarId })) }] };
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
      return { content: [{ type: "text", text: JSON.stringify(formatEvent({ ...event.data, _calendarId: calendarId })) }] };
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
      description:
        "Search for events by keyword across one or more calendars. Useful for finding meetings by title, description, location, or attendee.",
      inputSchema: z.object({
        query: z.string().describe("Free-text search query"),
        calendarIds: z
          .array(z.string())
          .default(["primary"])
          .describe("Calendar IDs to search. Pass multiple to search across all your calendars."),
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
        .sort((a, b) => new Date(a.start?.dateTime ?? a.start?.date) - new Date(b.start?.dateTime ?? b.start?.date))
        .map(formatEvent);
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
        title: c.summary,
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
        emails: z.array(z.string()).describe("Email addresses or calendar IDs to check"),
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

  server.registerTool(
    "calendar_summary",
    {
      description:
        "Summarize calendar events for a named period (today, this week, this month, etc.) or a custom date range. " +
        "Returns total event count and events grouped by day — ideal for answering 'what's on my calendar this week?' " +
        "without multiple tool calls.",
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
        calendarIds: z
          .array(z.string())
          .default(["primary"])
          .describe("Calendar IDs to include. Use calendar_list_calendars to discover IDs."),
        maxResultsPerCalendar: z
          .number()
          .int()
          .min(1)
          .max(500)
          .default(50)
          .describe("Max events per calendar"),
      }),
    },
    async ({ period, referenceDate, dateFrom, dateTo, calendarIds, maxResultsPerCalendar }) => {
      const { start, end, label } = computeDateRange(period, referenceDate, dateFrom, dateTo);
      const cal = calendarClient(token);

      const results = await Promise.all(
        calendarIds.map((id) =>
          fetchEvents(cal, id, {
            timeMin: start.toISOString(),
            timeMax: end.toISOString(),
            maxResults: maxResultsPerCalendar,
          })
        )
      );

      const allEvents = results
        .flat()
        .sort((a, b) => new Date(a.start?.dateTime ?? a.start?.date) - new Date(b.start?.dateTime ?? b.start?.date))
        .map(formatEvent);

      const byDay = groupByDay(allEvents);

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify({ period: label, total: allEvents.length, byDay }),
          },
        ],
      };
    }
  );
}

function groupByDay(events) {
  const map = new Map();
  for (const event of events) {
    const date = (event.start ?? "").slice(0, 10);
    if (!map.has(date)) map.set(date, []);
    map.get(date).push(event);
  }
  return [...map.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, events]) => ({ date, events }));
}