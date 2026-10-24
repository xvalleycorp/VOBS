#!/usr/bin/env bash
# Nạp các file OpenAPI của vobs-spec vào Microcks. Mỗi `examples` trong đặc tả trở thành một phản hồi giả lập.
# Sau đó nạp dữ liệu mẫu trong data/ dưới dạng artifact phụ: cấu hình (*.metadata.yaml) trước, rồi phản hồi mẫu
# (*.examples.yaml). Nạp lại đặc tả sẽ đặt lại cấu hình, nên luôn chạy cả script.
set -euo pipefail

MICROCKS_URL="${MICROCKS_URL:-http://localhost:8585}"
SPEC_DIR="$(cd "$(dirname "$0")/../vobs-spec/openapi" && pwd)"
DATA_DIR="$(cd "$(dirname "$0")/data" && pwd)"

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

for data in "$DATA_DIR"/*.metadata.yaml "$DATA_DIR"/*.examples.yaml; do
  echo "Nạp dữ liệu mẫu $(basename "$data")"
  curl --fail --silent --show-error \
    -F "file=@${data}" \
    "${MICROCKS_URL}/api/artifact/upload?mainArtifact=false"
  echo
done
