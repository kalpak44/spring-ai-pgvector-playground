/**
 * Computes a UTC date range from a named period or a custom interval.
 *
 * @param {"today"|"yesterday"|"this_week"|"last_week"|"this_month"|"last_month"|"custom"} period
 * @param {string|undefined} referenceDate  ISO date (YYYY-MM-DD) used as "today" anchor; defaults to server clock
 * @param {string|undefined} dateFrom       ISO date, required when period === "custom"
 * @param {string|undefined} dateTo         ISO date, required when period === "custom"
 * @returns {{ start: Date, end: Date, label: string }}
 */
export function computeDateRange(period, referenceDate, dateFrom, dateTo) {
  const base = referenceDate ? new Date(referenceDate + "T00:00:00Z") : new Date();
  const today = new Date(Date.UTC(base.getUTCFullYear(), base.getUTCMonth(), base.getUTCDate()));

  if (period === "custom") {
    return {
      start: new Date(dateFrom + "T00:00:00Z"),
      end: new Date(dateTo + "T23:59:59Z"),
      label: `${dateFrom} to ${dateTo}`,
    };
  }

  const shift = (n) => {
    const d = new Date(today);
    d.setUTCDate(today.getUTCDate() + n);
    return d;
  };

  const dow = today.getUTCDay(); // 0 = Sunday
  const toMonday = dow === 0 ? -6 : 1 - dow;

  const ranges = {
    today:      [shift(0),             shift(1)],
    yesterday:  [shift(-1),            shift(0)],
    this_week:  [shift(toMonday),      shift(toMonday + 7)],
    last_week:  [shift(toMonday - 7),  shift(toMonday)],
    this_month: [
      new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), 1)),
      new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth() + 1, 1)),
    ],
    last_month: [
      new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth() - 1, 1)),
      new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), 1)),
    ],
  };

  const [start, end] = ranges[period] ?? ranges.today;
  const fmt = (d) => d.toISOString().slice(0, 10);

  // Inclusive end label: subtract one millisecond so "end" midnight shows as the previous day
  const endLabel = fmt(new Date(end.getTime() - 1));
  const label = fmt(start) === endLabel ? fmt(start) : `${fmt(start)} to ${endLabel}`;

  return { start, end, label };
}