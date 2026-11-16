#!/usr/bin/env bash
# Backup and restore rehearsal for the pilot operations runbook.
#
# Dumps SOURCE_DATABASE_URL, restores it into the disposable RESTORE_DATABASE_URL, compares row
# counts for the critical tables, checks the Flyway history, and writes an evidence file with
# timestamps, the dump checksum, and the results. It never writes to the source database.
#
#   SOURCE_DATABASE_URL=postgres://... RESTORE_DATABASE_URL=postgres://... scripts/backup-restore-drill.sh
#
# Optional: DRILL_OUTPUT_DIR (default ./restore-drill-<timestamp>), DRILL_OPERATOR.
set -euo pipefail

: "${SOURCE_DATABASE_URL:?Set SOURCE_DATABASE_URL to the database being backed up}"
: "${RESTORE_DATABASE_URL:?Set RESTORE_DATABASE_URL to an empty, disposable database}"
if [[ "$SOURCE_DATABASE_URL" == "$RESTORE_DATABASE_URL" ]]; then
  echo "RESTORE_DATABASE_URL must be a different, disposable database" >&2
  exit 2
fi

started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
stamp=$(date -u +%Y%m%dT%H%M%SZ)
out_dir=${DRILL_OUTPUT_DIR:-"./restore-drill-$stamp"}
mkdir -p "$out_dir"
dump="$out_dir/race-tracker-$stamp.dump"
evidence="$out_dir/evidence.txt"

existing=$(psql "$RESTORE_DATABASE_URL" -Atqc "select count(*) from pg_tables where schemaname = 'public'")
if [[ "$existing" != "0" ]]; then
  echo "RESTORE_DATABASE_URL is not empty ($existing tables); refusing to overwrite it" >&2
  exit 3
fi

TABLES=(races participants checkpoints checkpoint_records rfid_read_events race_results race_result_versions
        checkpoint_record_corrections payments payment_refunds audit_events users gps_locations)

count_rows() {
  local url=$1 table=$2
  if [[ $(psql "$url" -Atqc "select to_regclass('public.$table') is not null") == "t" ]]; then
    psql "$url" -Atqc "select count(*) from public.$table"
  else
    echo "absent"
  fi
}

pg_dump --format=custom --no-owner --file="$dump" "$SOURCE_DATABASE_URL"
if command -v sha256sum >/dev/null 2>&1; then
  checksum=$(sha256sum "$dump" | awk '{print $1}')
else
  checksum=$(shasum -a 256 "$dump" | awk '{print $1}')
fi
pg_restore --list "$dump" > "$out_dir/restore-list.txt"
pg_restore --no-owner --exit-on-error --dbname="$RESTORE_DATABASE_URL" "$dump"

status=PASSED
{
  echo "Race Tracker backup and restore drill"
  echo "operator: ${DRILL_OPERATOR:-unrecorded}"
  echo "started_at: $started_at"
  echo "dump: $(basename "$dump")"
  echo "dump_sha256: $checksum"
  echo "source_flyway_head: $(psql "$SOURCE_DATABASE_URL" -Atqc "select coalesce(max(version), 'none') from flyway_schema_history where success" 2>/dev/null || echo unavailable)"
  echo "restored_flyway_head: $(psql "$RESTORE_DATABASE_URL" -Atqc "select coalesce(max(version), 'none') from flyway_schema_history where success" 2>/dev/null || echo unavailable)"
  echo "table,source_rows,restored_rows,result"
} > "$evidence"

for table in "${TABLES[@]}"; do
  source_rows=$(count_rows "$SOURCE_DATABASE_URL" "$table")
  restored_rows=$(count_rows "$RESTORE_DATABASE_URL" "$table")
  result=match
  if [[ "$source_rows" != "$restored_rows" ]]; then
    result=MISMATCH
    status=FAILED
  fi
  echo "$table,$source_rows,$restored_rows,$result" >> "$evidence"
done

failed_migrations=$(psql "$RESTORE_DATABASE_URL" -Atqc "select count(*) from flyway_schema_history where not success" 2>/dev/null || echo 0)
if [[ "$failed_migrations" != "0" ]]; then
  status=FAILED
fi
{
  echo "failed_migrations_in_restored_history: $failed_migrations"
  echo "finished_at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "result: $status"
  echo "next: start the backend against the restored database with SPRING_PROFILES_ACTIVE=prod to run Flyway and Hibernate validation, then run representative race, result, and RFID queries."
} >> "$evidence"

cat "$evidence"
[[ "$status" == "PASSED" ]]
