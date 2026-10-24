#!/usr/bin/env bash
# Chạy thử luồng của bên thứ ba qua API Gateway trên sandbox: xác thực bằng API key, tạo consent, đọc tài khoản
# theo phạm vi consent, giới hạn tần suất. Dữ liệu tài khoản lấy từ API giả lập trên Microcks (chạy ./import-specs.sh
# trước). Mỗi bước in kết quả và dừng với mã lỗi nếu kết quả khác mong đợi.
#
#   GATEWAY_URL   API Gateway                         (mặc định http://localhost:9080/open-banking/v1)
#   INTERNAL_URL  cổng nội bộ của Quản lý sự đồng ý    (mặc định http://localhost:8091), đóng vai kênh ngân hàng
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:9080/open-banking/v1}"
INTERNAL_URL="${INTERNAL_URL:-http://localhost:8091}"
FINTECH_KEY="apikey: sandbox-fintech-a"
WALLET_KEY="apikey: sandbox-wallet-b"
CUSTOMER="cif-2001"
BODY="$(mktemp)"
trap 'rm -f "$BODY"' EXIT

step=0
step() {
  step=$((step + 1))
  printf '\n%d. %s\n' "$step" "$1"
}

# call METHOD URL [JSON] [header...] -> mã HTTP ra stdout, nội dung vào $BODY
call() {
  local method="$1" url="$2" data="${3:-}"
  shift 3 || shift $#
  local args=(-s -o "$BODY" -w '%{http_code}' -X "$method" -H 'Content-Type: application/json')
  for header in "$@"; do args+=(-H "$header"); done
  if [ -n "$data" ]; then args+=(--data "$data"); fi
  curl "${args[@]}" "$url"
}

# field NAME -> giá trị chuỗi hoặc true/false/null của trường NAME trong $BODY (sed -E chạy cả trên macOS lẫn Linux)
field() {
  sed -En "s/.*\"$1\":(\"[^\"]*\"|true|false|null).*/\1/p" "$BODY" | head -1 | tr -d '"'
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

printf 'Chờ API Gateway sẵn sàng tại %s ' "$GATEWAY_URL"
for _ in $(seq 1 60); do
  # Chưa có API key thì gateway trả 401: nghĩa là gateway đã chạy và đã nạp route
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY_URL/consents" || true)" = "401" ]; then
    break
  fi
  printf '.'
  sleep 2
done
echo

step "Gọi gateway không có API key: bị từ chối"
status=$(call GET "$GATEWAY_URL/accounts/acc-1001" "" 'x-consent-id: bat-ky')
expect "HTTP" "$status" 401

step "fintech-a tạo consent qua gateway, đồng thời tự gửi x-vobs-client-id: wallet-b để giả danh"
status=$(call POST "$GATEWAY_URL/consents" '{"scopes":["ACCOUNTS_READ"],"validityDays":90}' \
  "$FINTECH_KEY" 'x-vobs-client-id: wallet-b')
expect "HTTP" "$status" 201
CONSENT="$(field consentId)"
printf '   consentId: %s\n' "$CONSENT"

step "Consent thuộc fintech-a, không phải wallet-b: gateway ghi đè header giả danh"
status=$(call GET "$GATEWAY_URL/consents/$CONSENT" "" "$FINTECH_KEY")
expect "fintech-a xem consent, HTTP" "$status" 200
status=$(call GET "$GATEWAY_URL/consents/$CONSENT" "" "$WALLET_KEY")
expect "wallet-b xem consent, HTTP" "$status" 404

step "Đọc tài khoản trước khi khách hàng duyệt: bị từ chối"
status=$(call GET "$GATEWAY_URL/accounts/acc-1001" "" "$FINTECH_KEY" "x-consent-id: $CONSENT")
expect "HTTP" "$status" 403
expect "reason" "$(field reason)" consent_awaiting_authorisation

step "Khách hàng $CUSTOMER duyệt trên kênh của ngân hàng, chọn chia sẻ tài khoản acc-1001"
status=$(call POST "$INTERNAL_URL/internal/consents/$CONSENT/authorisation" \
  "{\"customerId\":\"$CUSTOMER\",\"accountIds\":[\"acc-1001\"]}")
expect "HTTP" "$status" 200

step "Đọc tài khoản acc-1001 qua gateway: được phép, dữ liệu từ API giả lập"
status=$(call GET "$GATEWAY_URL/accounts/acc-1001" "" "$FINTECH_KEY" "x-consent-id: $CONSENT")
expect "HTTP" "$status" 200
expect "accountId" "$(field accountId)" acc-1001

step "Đọc lịch sử giao dịch: consent không có phạm vi TRANSACTIONS_READ, bị từ chối"
status=$(call GET "$GATEWAY_URL/accounts/acc-1001/transactions" "" "$FINTECH_KEY" "x-consent-id: $CONSENT")
expect "HTTP" "$status" 403
expect "reason" "$(field reason)" scope_not_granted

step "Đọc tài khoản acc-2002 (không được chọn): bị từ chối"
status=$(call GET "$GATEWAY_URL/accounts/acc-2002" "" "$FINTECH_KEY" "x-consent-id: $CONSENT")
expect "HTTP" "$status" 403
expect "reason" "$(field reason)" account_not_granted

step "Thiếu header x-consent-id: bị từ chối"
status=$(call GET "$GATEWAY_URL/accounts/acc-1001" "" "$FINTECH_KEY")
expect "HTTP" "$status" 403
expect "reason" "$(field reason)" consent_required

step "fintech-a thu hồi consent qua gateway, rồi đọc lại: bị từ chối"
status=$(call DELETE "$GATEWAY_URL/consents/$CONSENT" "" "$FINTECH_KEY")
expect "Thu hồi, HTTP" "$status" 204
status=$(call GET "$GATEWAY_URL/accounts/acc-1001" "" "$FINTECH_KEY" "x-consent-id: $CONSENT")
expect "Đọc lại, HTTP" "$status" 403
expect "reason" "$(field reason)" consent_revoked

step "Gọi liên tục quá hạn mức (30 lần mỗi phút cho mỗi đối tác, Điều 11 Thông tư 64): bị giới hạn"
limited=""
for i in $(seq 1 40); do
  status=$(call GET "$GATEWAY_URL/accounts/acc-1001" "" "$WALLET_KEY" "x-consent-id: khong-ton-tai")
  if [ "$status" = "429" ]; then limited="$i"; break; fi
done
if [ -n "$limited" ]; then
  printf '   ✓ Lần gọi thứ %s nhận HTTP 429\n' "$limited"
else
  printf '   ✗ Gọi 40 lần mà không nhận HTTP 429\n' >&2
  exit 1
fi

printf '\nHoàn tất: %d bước đều cho kết quả đúng.\n' "$step"
