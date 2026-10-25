import { type Database, queryOne, toIsoTimestamp } from "../database/database.ts";
import { notFound } from "../http/api_error.ts";
import { findAnalysis } from "./analysis_records.ts";

/** Base of the public links (always `https://jauge.io/a/{analysisId}`). */
export const PUBLIC_LINK_BASE = "https://jauge.io/a/";

/** Formats of a share card. */
export type ShareFormat = "story" | "square" | "link";

/** 128 random bits in base64url (22 characters): identifiers that cannot be guessed. */
export function randomShareIdentifier(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

interface ShareRow {
  id: string;
  analysis_id: string;
  format: ShareFormat;
  hide_company: boolean;
  show_calm_twin: boolean;
  created_at: unknown;
}

function presentShare(row: ShareRow): Record<string, unknown> {
  return {
    id: row.id,
    analysisId: row.analysis_id,
    url: `${PUBLIC_LINK_BASE}${row.analysis_id}`,
    // Card images are rendered by a later phase (docs/backend/ARCHITECTURE.md); the app draws the card meanwhile.
    imageUrl: null,
    format: row.format,
    hideCompany: row.hide_company,
    showCalmTwin: row.show_calm_twin,
    createdAt: toIsoTimestamp(row.created_at),
  };
}

const SHARE_COLUMNS = `id, analysis_id::text as analysis_id, format, hide_company, show_calm_twin, created_at`;

/**
 * Public shares. The public link of an analysis works while at least one share of it exists; deleting the shares
 * stops it. The analysis identifier is a random UUID (122 random bits) and the share identifier 128 random bits.
 */
export class ShareService {
  /** @param database database. */
  constructor(private readonly database: Database) {}

  /** Shares of the user, newest first. */
  async list(userId: string): Promise<Record<string, unknown>> {
    const rows = await this.database.query<ShareRow>(
      `select ${SHARE_COLUMNS} from public.shares where user_id = $1::uuid order by created_at desc limit 200`,
      [userId],
    );
    return { items: rows.map(presentShare) };
  }

  /** Shares a completed analysis of the user. */
  async create(
    userId: string,
    request: { analysisId: string; format: ShareFormat; hideCompany: boolean; showCalmTwin: boolean },
  ): Promise<Record<string, unknown>> {
    const analysis = await findAnalysis(this.database, request.analysisId, userId);
    if (!analysis || analysis.status !== "completed") throw notFound("Analysis");
    const rows = await this.database.query<ShareRow>(
      `insert into public.shares (id, user_id, analysis_id, format, hide_company, show_calm_twin)
       values ($1, $2::uuid, $3::uuid, $4, $5, $6) returning ${SHARE_COLUMNS}`,
      [randomShareIdentifier(), userId, request.analysisId, request.format, request.hideCompany, request.showCalmTwin],
    );
    return presentShare(rows[0]);
  }

  /** Deletes a share of the user. */
  async delete(userId: string, shareId: string): Promise<void> {
    if (!/^[A-Za-z0-9_-]{22}$/.test(shareId)) throw notFound("Share");
    const deleted = await this.database.query(
      `delete from public.shares where id = $1 and user_id = $2::uuid returning id`,
      [shareId, userId],
    );
    if (deleted.length === 0) throw notFound("Share");
  }

  /**
   * Public view of a shared analysis (page jauge.io/a/{id}): no user data, the company hidden when the latest share
   * asks for it, the calmest twin when allowed.
   */
  async publicView(analysisId: string): Promise<Record<string, unknown>> {
    const share = await queryOne<ShareRow>(
      this.database,
      `select ${SHARE_COLUMNS} from public.shares where analysis_id = $1::uuid order by created_at desc limit 1`,
      [analysisId],
    );
    if (!share) throw notFound("Shared analysis");
    const analysis = await findAnalysis(this.database, analysisId, null);
    if (!analysis || analysis.status !== "completed" || !analysis.offer || !analysis.result) {
      throw notFound("Shared analysis");
    }
    const calmTwin = share.show_calm_twin
      ? await queryOne<{ job_title: string; city: string | null; estimated_applicants: number; score: number }>(
        this.database,
        `select c.job_title, c.city, t.estimated_applicants, t.score
         from public.snapshot_twins t join public.collected_offers c on c.id = t.collected_offer_id
         where t.snapshot_id = $1::uuid order by t.score, t.estimated_applicants limit 1`,
        [analysis.snapshotId],
      )
      : null;
    const result = analysis.result;
    return {
      analysisId,
      jobTitle: analysis.offer.jobTitle,
      company: share.hide_company ? null : analysis.offer.company ?? null,
      place: analysis.offer.city,
      score: result.score,
      applicants: result.applicants,
      reliability: result.reliability,
      regionName: result.regionName,
      calmTwin: calmTwin
        ? {
          jobTitle: calmTwin.job_title,
          place: calmTwin.city ?? "",
          estimatedApplicants: calmTwin.estimated_applicants,
          score: calmTwin.score,
        }
        : null,
      createdAt: analysis.createdAt,
    };
  }
}
