#!/usr/bin/env bash
# Đo tải API Gateway trên sandbox bằng k6: tăng dần tốc độ cho tới khi vượt mục tiêu, ở hai cách giữ kết quả
# introspection (cached, nocache), kèm CPU của từng service. Kết quả trong load/results: bảng results.md, số liệu thô.
#
# Chạy sau ./pki.sh, không cần chạy sandbox trước; script tự dựng và tự dừng. Chỉ đo được trên máy đang chạy: xem
# docs/load-testing.md để đọc kết quả cho đúng. Biến môi trường:
#   RATES      các mức tải, lượt mỗi giây    (mặc định "50 100 200 400 800 1600")
#   DURATION   thời gian đo mỗi mức          (mặc định 40s)
#   TOKENS     số mã truy cập đang dùng      (mặc định 50)
#   PROFILES   cách giữ kết quả cần đo       (mặc định "cached nocache")
#   TARGET_P99 mục tiêu p99, mili giây       (mặc định 200, theo docs/architecture.md)
set -euo pipefail

cd "$(dirname "$0")/.."
RATES="${RATES:-50 100 200 400 800 1600}"
DURATION="${DURATION:-40s}"
TOKENS="${TOKENS:-50}"
PROFILES="${PROFILES:-cached nocache}"
TARGET_P99="${TARGET_P99:-200}"
RESULTS="$(pwd)/load/results"
PKI="$(pwd)/pki"
K6_IMAGE=grafana/k6:2.3.0
COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.load.yml)

if [ ! -f "$PKI/ca.crt" ]; then
  echo "Chưa có PKI: chạy ./pki.sh trước" >&2
  exit 1
fi
rm -rf "$RESULTS"
mkdir -p "$RESULTS"
SAMPLER=""
cleanup() {
  if [ -n "$SAMPLER" ]; then kill "$SAMPLER" 2>/dev/null || true; fi
  "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

python3 -c 'print("{\"padding\":\"" + "x" * 4080 + "\"}")' > "$PKI/load-body.json"
chmod 644 "$PKI/load-body.json"

# k6 PROFILE RATE DURATION SUMMARY_FILE -> chạy k6 trong mạng của máy chủ, tin CA của sandbox
k6() {
  docker run --rm --network host --user "$(id -u)" \
    -e SSL_CERT_FILE=/pki/ca.crt -e RATE="$1" -e DURATION="$2" -e TOKENS="$TOKENS" -e SUMMARY="/results/$3" \
    -v "$PKI:/pki:ro" -v "$RESULTS:/results" -v "$(pwd)/load/gateway.js:/gateway.js:ro" \
    "$K6_IMAGE" run --quiet --no-usage-report /gateway.js
}

# sample_cpu FILE -> ghi CPU của các container vào FILE cho tới khi bị dừng
sample_cpu() {
  while true; do
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}}' >> "$1"
  done
}

wait_for_keycloak() {
  printf 'Chờ Keycloak '
  for _ in $(seq 1 90); do
    if curl -s -o /dev/null --cacert "$PKI/ca.crt" --cert "$PKI/fintech-a.crt" --key "$PKI/fintech-a.key" \
      -d grant_type=client_credentials -d client_id=fintech-a --fail \
      https://localhost:8443/realms/open-banking/protocol/openid-connect/token; then
      echo ' sẵn sàng'
      return
    fi
    printf '.'
    sleep 2
  done
  echo ' không sẵn sàng' >&2
  exit 1
}

# wait_for_gateway -> gateway đang nhận kết nối mTLS (trả 401 vì không có token)
wait_for_gateway() {
  for _ in $(seq 1 30); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' --cacert "$PKI/ca.crt" --cert "$PKI/fintech-a.crt" \
      --key "$PKI/fintech-a.key" https://localhost:9443/open-banking/v1/accounts/acc-1001 || true)" = "401" ]; then
      return
    fi
    sleep 1
  done
  echo 'Gateway không sẵn sàng' >&2
  exit 1
}

for profile in $PROFILES; do
  echo "== Profile $profile"
  python3 load/gateway_config.py "$PKI/apisix.yaml" "$PKI/apisix.load.yaml" "$profile"
  chmod 644 "$PKI/apisix.load.yaml"
  # Dựng lại từ đầu để JVM và cache của profile trước không ảnh hưởng profile sau. Không cần Microcks.
  "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
  "${COMPOSE[@]}" up -d --build --no-deps consent keycloak load-upstream gateway
  wait_for_keycloak
  wait_for_gateway

  first_rate="${RATES%% *}"
  echo "Làm nóng ở $first_rate lượt/s, không tính kết quả"
  k6 "$first_rate" 60s "warmup-$profile.tmp" >/dev/null
  rm -f "$RESULTS/warmup-$profile.tmp"

  for rate in $RATES; do
    echo "Đo $rate lượt/s trong $DURATION"
    sample_cpu "$RESULTS/$profile-$rate.cpu.csv" &
    SAMPLER=$!
    k6 "$rate" "$DURATION" "$profile-$rate.json"
    kill "$SAMPLER" 2>/dev/null || true
    wait "$SAMPLER" 2>/dev/null || true
    SAMPLER=""
    # Đã vượt mục tiêu thì mức cao hơn không thêm thông tin
    if ! python3 load/report.py "$RESULTS" "$TARGET_P99" | grep -E "^\| $profile \| $rate \|" | grep -q '| đạt |$'; then
      echo "Dừng profile $profile ở $rate lượt/s"
      break
    fi
  done
done

{
  echo "Máy đo: $(nproc 2>/dev/null || sysctl -n hw.ncpu) vCPU, tất cả service và k6 chạy chung. $TOKENS mã truy cập, mỗi mức $DURATION."
  echo
  python3 load/report.py "$RESULTS" "$TARGET_P99"
} > "$RESULTS/results.md"
cat "$RESULTS/results.md"
