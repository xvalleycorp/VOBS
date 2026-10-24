#!/usr/bin/env bash
# Nạp các file OpenAPI của vobs-spec vào Microcks. Mỗi `examples` trong đặc tả trở thành một phản hồi giả lập.
set -euo pipefail

MICROCKS_URL="${MICROCKS_URL:-http://localhost:8585}"
SPEC_DIR="$(cd "$(dirname "$0")/../vobs-spec/openapi" && pwd)"

printf 'Chờ Microcks sẵn sàng tại %s ' "$MICROCKS_URL"
for _ in $(seq 1 90); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "$MICROCKS_URL/api/services" || true)" = "200" ]; then
    break
  fi
  printf '.'
  sleep 2
done
echo

for spec in "$SPEC_DIR"/*.yaml; do
  echo "Nạp $(basename "$spec")"
  curl --fail --silent --show-error \
    -F "file=@${spec}" \
    "${MICROCKS_URL}/api/artifact/upload?mainArtifact=true"
  echo
done
