/** Source of the current time (injected so that tests control it). */
export interface Clock {
  /** Current instant. */
  now(): Date;
}

/** Real clock. */
export const systemClock: Clock = { now: () => new Date() };

/** Clock moved by hand (tests). */
export class ManualClock implements Clock {
  private current: Date;

  /** @param start initial instant. */
  constructor(start: Date) {
    this.current = new Date(start.getTime());
  }

  /** @inheritdoc */
  now(): Date {
    return new Date(this.current.getTime());
  }

  /** Moves the clock forward. */
  advance(milliseconds: number): void {
    this.current = new Date(this.current.getTime() + milliseconds);
  }
}

const PARIS_DATE_FORMAT = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Europe/Paris",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  weekday: "short",
});

const WEEKDAY_INDEX: Readonly<Record<string, number>> = { Mon: 0, Tue: 1, Wed: 2, Thu: 3, Fri: 4, Sat: 5, Sun: 6 };

/**
 * Monday (ISO date) of the week of an instant, in Paris time: the free analysis comes back every Monday at 00:00.
 *
 * @param instant any instant.
 */
export function parisWeekStart(instant: Date): string {
  const parts = Object.fromEntries(PARIS_DATE_FORMAT.formatToParts(instant).map((part) => [part.type, part.value]));
  const parisDay = Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day));
  const monday = new Date(parisDay - WEEKDAY_INDEX[parts.weekday] * 86_400_000);
  return monday.toISOString().slice(0, 10);
}

/** UTC date (`2026-10-25`) of an instant. */
export function utcDay(instant: Date): string {
  return instant.toISOString().slice(0, 10);
}

/** Whole days between two instants (b minus a), rounded down. */
export function daysBetween(start: Date, end: Date): number {
  return Math.floor((end.getTime() - start.getTime()) / 86_400_000);
}
