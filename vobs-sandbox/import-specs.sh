#!/usr/bin/env bash
# Nạp các file OpenAPI của vobs-spec vào Microcks. Mỗi `examples` trong đặc tả trở thành một phản hồi giả lập.
set -euo pipefail

MICROCKS_URL="${MICROCKS_URL:-http://localhost:8585}"
SPEC_DIR="$(cd "$(dirname "$0")/../vobs-spec/openapi" && pwd)"

for spec in "$SPEC_DIR"/*.yaml; do
  echo "Nạp $(basename "$spec")"
  curl --fail --silent --show-error \
    -F "file=@${spec}" \
    "${MICROCKS_URL}/api/artifact/upload?mainArtifact=true"
  echo
done
