#!/usr/bin/env bash
# Claude Code Stop hook: runs spotlessCheck + test for services with changes.
# Exit 2 => blocks stop and feeds stderr back to Claude to fix.
set -uo pipefail

ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
cd "$ROOT" || exit 0
SERVICES="catalog-service config-service dispatcher-service edge-service inventory-service order-service search-service"

changed_files="$(git -C "$ROOT" status --porcelain | awk '{print $2}')"
[ -z "$changed_files" ] && exit 0

changed=""
for svc in $SERVICES; do
  echo "$changed_files" | grep -q "^$svc/" && changed="$changed $svc"
done
# Shared gradle scripts affect every service.
echo "$changed_files" | grep -qE "^gradle/" && changed="$SERVICES"

[ -z "$changed" ] && exit 0

failed=""
for svc in $changed; do
  if ! (cd "$ROOT/$svc" && ./gradlew --quiet spotlessCheck test >/tmp/verify-$svc.log 2>&1); then
    failed="$failed $svc"
    echo "### $svc FAILED" >&2
    tail -n 25 "/tmp/verify-$svc.log" >&2
  fi
done

if [ -n "$failed" ]; then
  echo "" >&2
  echo "Verification failed for:$failed. Fix the errors (run ./gradlew spotlessApply for format issues), then finish." >&2
  exit 2
fi
exit 0
