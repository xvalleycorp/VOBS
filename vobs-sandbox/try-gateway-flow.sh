#!/usr/bin/env bash
# Chạy thử luồng của bên thứ ba qua API Gateway trên sandbox: kết nối mTLS bằng chứng thư client, lấy mã truy cập
# gắn chứng thư từ Keycloak (FAPI 2.0, RFC 8705), tạo consent, đọc tài khoản theo phạm vi consent, giới hạn tần suất.
# Dữ liệu tài khoản lấy từ API giả lập trên Microcks (chạy ./import-specs.sh trước). Mỗi bước in kết quả và dừng với
# mã lỗi nếu kết quả khác mong đợi.
#
#   KEYCLOAK_URL  Keycloak cấp mã truy cập             (mặc định https://localhost:8443)
#   GATEWAY_URL   API Gateway                         (mặc định https://localhost:9443/open-banking/v1)
#   INTERNAL_URL  cổng nội bộ của Quản lý sự đồng ý    (mặc định http://localhost:8091), đóng vai kênh ngân hàng
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-https://localhost:8443}"
GATEWAY_URL="${GATEWAY_URL:-https://localhost:9443/open-banking/v1}"
# Chứng thư tạo bằng ./pki.sh
PKI="$(cd "$(dirname "$0")/pki" && pwd)"
INTERNAL_URL="${INTERNAL_URL:-http://localhost:8091}"
CUSTOMER="cif-2001"
BODY="$(mktemp)"
trap 'rm -f "$BODY"' EXIT

step=0
step() {
  step=$((step + 1))
  printf '\n%d. %s\n' "$step" "$1"
}

# [AS=CLIENT] call METHOD URL [JSON] [header...] -> mã HTTP ra stdout, nội dung vào $BODY. AS chọn chứng thư client
# của đối tác cho kết nối mTLS; không đặt thì kết nối không có chứng thư client.
call() {
  local method="$1" url="$2" data="${3:-}"
  shift 3 || shift $#
  local args=(-s -o "$BODY" -w '%{http_code}' -X "$method" -H 'Content-Type: application/json' --cacert "$PKI/ca.crt")
  if [ -n "${AS:-}" ]; then args+=(--cert "$PKI/$AS.crt" --key "$PKI/$AS.key"); fi
  for header in "$@"; do args+=(-H "$header"); done
  if [ -n "$data" ]; then args+=(--data "$data"); fi
  curl "${args[@]}" "$url"
}

# field NAME -> giá trị chuỗi hoặc true/false/null của trường NAME trong $BODY (sed -E chạy cả trên macOS lẫn Linux)
field() {
  sed -En "s/.*\"$1\":(\"[^\"]*\"|true|false|null).*/\1/p" "$BODY" | head -1 | tr -d '"'
}

# token CLIENT_ID -> mã truy cập client credentials từ Keycloak. Đối tác xác thực bằng chứng thư client qua mTLS,
# không có client secret; token gắn với chứng thư đó (claim cnf).
token() {
  curl -s --cacert "$PKI/ca.crt" --cert "$PKI/$1.crt" --key "$PKI/$1.key" \
    -d grant_type=client_credentials -d "client_id=$1" \
    "$KEYCLOAK_URL/realms/open-banking/protocol/openid-connect/token" \
    | sed -En 's/.*"access_token":"([^"]+)".*/\1/p'
}

expect() {
  local what="$1" actual="$2" expected="$3"
  if [ "$actual" = "$expected" ]; then
    printf '   ✓ %s: %s\n' "$what" "$actual"
  else
    printf '   ✗ %s: nhận %s, mong đợi %s\n   Nội dung: %s\n' "$what" "$actual" "$expected" "$(cat "$BODY")" >&2
    exit 1
  fi
}

printf 'Chờ Keycloak sẵn sàng tại %s ' "$KEYCLOAK_URL"
for _ in $(seq 1 90); do
  if [ -n "$(token fintech-a || true)" ]; then
    break
  fi
  printf '.'
  sleep 2
done
echo
FINTECH_TOKEN="$(token fintech-a)"
WALLET_TOKEN="$(token wallet-b)"
if [ -z "$FINTECH_TOKEN" ] || [ -z "$WALLET_TOKEN" ]; then
  echo "Không lấy được mã truy cập từ Keycloak. Phản hồi:" >&2
  curl -s --cacert "$PKI/ca.crt" --cert "$PKI/fintech-a.crt" --key "$PKI/fintech-a.key" \
    -d grant_type=client_credentials -d client_id=fintech-a \
    "$KEYCLOAK_URL/realms/open-banking/protocol/openid-connect/token" >&2 || true
  echo >&2
  exit 1
fi
FINTECH_KEY="Authorization: Bearer $FINTECH_TOKEN"
WALLET_KEY="Authorization: Bearer $WALLET_TOKEN"

printf 'Chờ API Gateway sẵn sàng tại %s ' "$GATEWAY_URL"
for _ in $(seq 1 60); do
  # Xem một consent không tồn tại, có token: service consent trả 404 kèm "reason". Nghĩa là gateway đã nạp route,
  # đọc được khóa của Keycloak để kiểm tra token và chuyển được request tới service consent.
  if [ "$(AS=fintech-a call GET "$GATEWAY_URL/consents/khong-ton-tai" "" "$FINTECH_KEY" || true)" = "404" ] \
    && grep -q '"reason"' "$BODY"; then
    break
  fi
  printf '.'
  sleep 2
done
echo

step "Kết nối gateway không có chứng thư client: bị từ chối"
status=$(call GET "$GATEWAY_URL/consents/bat-ky" "" "$FINTECH_KEY" || true)
# APISIX trả 400 khi kết nối không có chứng thư client hợp lệ; nếu bắt tay TLS thất bại, curl báo 000
case "$status" in
  400|000) expect "Bị từ chối, HTTP" "$status" "$status" ;;
  *) expect "HTTP" "$status" "400 hoặc 000" ;;
esac

step "Có chứng thư nhưng không có mã truy cập: bị từ chối"
status=$(AS=fintech-a call GET "$GATEWAY_URL/accounts/acc-1001" "" 'x-consent-id: bat-ky')
expect "HTTP" "$status" 401

step "Token của fintech-a dùng trên kết nối bằng chứng thư của wallet-b (token bị lộ): bị từ chối"
status=$(AS=wallet-b call GET "$GATEWAY_URL/consents/bat-ky" "" "$FINTECH_KEY")
expect "HTTP" "$status" 401
expect "reason" "$(field reason)" certificate_binding_mismatch

step "Token giả: ghép payload của wallet-b với chữ ký của fintech-a, bị từ chối"
FORGED="$(echo "$FINTECH_TOKEN" | cut -d. -f1).$(echo "$WALLET_TOKEN" | cut -d. -f2).$(echo "$FINTECH_TOKEN" | cut -d. -f3)"
status=$(AS=fintech-a call GET "$GATEWAY_URL/consents/bat-ky" "" "Authorization: Bearer $FORGED")
expect "HTTP" "$status" 401

step "fintech-a tạo consent qua gateway, đồng thời tự gửi x-vobs-client-id và X-Userinfo của wallet-b để giả danh"
SPOOFED_USERINFO="$(printf '{"azp":"wallet-b","client_id":"wallet-b"}' | base64 | tr -d '\n')"
status=$(AS=fintech-a call POST "$GATEWAY_URL/consents" '{"scopes":["ACCOUNTS_READ"],"validityDays":90}' \
  "$FINTECH_KEY" 'x-vobs-client-id: wallet-b' "X-Userinfo: $SPOOFED_USERINFO")
expect "HTTP" "$status" 201
CONSENT="$(field consentId)"
printf '   consentId: %s\n' "$CONSENT"

step "Consent thuộc fintech-a, không phải wallet-b: gateway bỏ qua các header giả danh"
status=$(AS=fintech-a call GET "$GATEWAY_URL/consents/$CONSENT" "" "$FINTECH_KEY")
expect "fintech-a xem consent, HTTP" "$status" 200
status=$(AS=wallet-b call GET "$GATEWAY_URL/consents/$CONSENT" "" "$WALLET_KEY")
expect "wallet-b xem consent, HTTP" "$status" 404

step "Đọc tài khoản trước khi khách hàng duyệt: bị từ chối"
status=$(AS=fintech-a call GET "$GATEWAY_URL/accounts/acc-1001" "" "$FINTECH_KEY" "x-consent-id: $CONSENT")
expect "HTTP" "$status" 403
expect "reason" "$(field reason)" consent_awaiting_authorisation

step "Khách hàng $CUSTOMER duyệt trên kênh của ngân hàng, chọn chia sẻ tài khoản acc-1001"
status=$(call POST "$INTERNAL_URL/internal/consents/$CONSENT/authorisation" \
  "{\"customerId\":\"$CUSTOMER\",\"accountIds\":[\"acc-1001\"]}")
expect "HTTP" "$status" 200

step "Đọc tài khoản acc-1001 qua gateway: được phép, dữ liệu từ API giả lập"
status=$(AS=fintech-a call GET "$GATEWAY_URL/accounts/acc-1001" "" "$FINTECH_KEY" "x-consent-id: $CONSENT")
expect "HTTP" "$status" 200
expect "accountId" "$(field accountId)" acc-1001

step "Đọc lịch sử giao dịch: consent không có phạm vi TRANSACTIONS_READ, bị từ chối"
status=$(AS=fintech-a call GET "$GATEWAY_URL/accounts/acc-1001/transactions" "" "$FINTECH_KEY" "x-consent-id: $CONSENT")
expect "HTTP" "$status" 403
expect "reason" "$(field reason)" scope_not_granted

step "Đọc tài khoản acc-2002 (không được chọn): bị từ chối"
status=$(AS=fintech-a call GET "$GATEWAY_URL/accounts/acc-2002" "" "$FINTECH_KEY" "x-consent-id: $CONSENT")
expect "HTTP" "$status" 403
expect "reason" "$(field reason)" account_not_granted

step "Thiếu header x-consent-id: bị từ chối"
status=$(AS=fintech-a call GET "$GATEWAY_URL/accounts/acc-1001" "" "$FINTECH_KEY")
expect "HTTP" "$status" 403
expect "reason" "$(field reason)" consent_required

step "fintech-a thu hồi consent qua gateway, rồi đọc lại: bị từ chối"
status=$(AS=fintech-a call DELETE "$GATEWAY_URL/consents/$CONSENT" "" "$FINTECH_KEY")
expect "Thu hồi, HTTP" "$status" 204
status=$(AS=fintech-a call GET "$GATEWAY_URL/accounts/acc-1001" "" "$FINTECH_KEY" "x-consent-id: $CONSENT")
expect "Đọc lại, HTTP" "$status" 403
expect "reason" "$(field reason)" consent_revoked

step "Gọi liên tục quá hạn mức (30 lần mỗi phút cho mỗi đối tác, Điều 11 Thông tư 64): bị giới hạn"
limited=""
for i in $(seq 1 40); do
  status=$(AS=wallet-b call GET "$GATEWAY_URL/accounts/acc-1001" "" "$WALLET_KEY" "x-consent-id: khong-ton-tai")
  if [ "$status" = "429" ]; then limited="$i"; break; fi
done
if [ -n "$limited" ]; then
  printf '   ✓ Lần gọi thứ %s nhận HTTP 429\n' "$limited"
else
  printf '   ✗ Gọi 40 lần mà không nhận HTTP 429\n' >&2
  exit 1
fi

printf '\nHoàn tất: %d bước đều cho kết quả đúng.\n' "$step"
