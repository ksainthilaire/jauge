#!/usr/bin/env bash
# Deploys the backend of Jauge on the linked Supabase project: database migrations, then the Edge Functions.
# Run from the root of the repository after `supabase login` and `supabase link` (docs/backend/DEPLOIEMENT.md).
# It never prints a secret. Stops at the first error.
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v supabase >/dev/null 2>&1; then
  echo "Supabase CLI missing: brew install supabase/tap/supabase" >&2
  exit 1
fi
if ! command -v deno >/dev/null 2>&1; then
  echo "Deno missing: brew install deno" >&2
  exit 1
fi

echo "1/4 Tests of the API (format, lint, types, tests)"
(cd supabase/functions/api && deno task verify)

echo "2/4 Database migrations"
supabase db push

echo "3/4 Edge Function api"
supabase functions deploy api --no-verify-jwt

echo "4/4 Edge Function send-notification"
supabase functions deploy send-notification --no-verify-jwt

echo "Done. Check: curl -s https://<project-ref>.supabase.co/functions/v1/api/health"
