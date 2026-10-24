#!/usr/bin/env bash
# Kiểm tra dữ liệu mẫu của API giả lập (data/vobs-open-api.examples.yaml) trên sandbox đang chạy: tài khoản nhiều
# loại và trạng thái, lịch sử giao dịch có phân trang, lãi suất, tỷ giá, trạng thái thanh toán, tình huống lỗi; rồi
# đọc giao dịch qua API Gateway với consent thật. Chạy sau ./import-specs.sh. Mỗi bước in kết quả và dừng với mã lỗi
# nếu kết quả khác mong đợi.
#
#   MOCK_URL      API giả lập trên Microcks  (mặc định http://localhost:8585/rest/VOBS+Open+API/0.2.0-draft)
#   KEYCLOAK_URL  Keycloak cấp mã truy cập   (mặc định http://localhost:8180)
#   GATEWAY_URL   API Gateway                (mặc định http://localhost:9080/open-banking/v1)
#   INTERNAL_URL  cổng nội bộ của Quản lý sự đồng ý (mặc định http://localhost:8091), đóng vai kênh ngân hàng
set -euo pipefail

MOCK_URL="${MOCK_URL:-http://localhost:8585/rest/VOBS+Open+API/0.2.0-draft}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:9080/open-banking/v1}"
INTERNAL_URL="${INTERNAL_URL:-http://localhost:8091}"
CONSENT_ID="6f1c2a7e-3b0d-4f5e-9a8c-1d2e3f4a5b6c"
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

# field NAME -> giá trị chuỗi hoặc true/false/null của trường đầu tiên có tên NAME trong $BODY
field() {
  { grep -oE "\"$1\":(\"[^\"]*\"|true|false|null)" "$BODY" || true; } | head -1 | sed -E 's/^"[^"]*"://' | tr -d '"'
}

# count NAME -> số lần trường NAME xuất hiện trong $BODY
count() {
  { grep -o "\"$1\":" "$BODY" || true; } | wc -l | tr -d ' '
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

# get PATH -> gọi API giả lập với consent mẫu; mã HTTP ra stdout
get() {
  call GET "$MOCK_URL$1" "" "x-consent-id: $CONSENT_ID"
}

step "Lãi suất tiền gửi có kỳ hạn"
expect "HTTP" "$(get '/interest-rates?productType=TERM_DEPOSIT')" 200
expect "Số kỳ hạn" "$(count annualRatePercent)" 6

step "Tỷ giá EUR"
expect "HTTP" "$(get '/exchange-rates?currency=EUR')" 200
expect "currency" "$(field currency)" EUR

step "Tài khoản ngoại tệ, tài khoản ngủ, tài khoản bị phong tỏa"
expect "acc-1003, HTTP" "$(get /accounts/acc-1003)" 200
expect "acc-1003, currency" "$(field currency)" USD
expect "acc-2002, HTTP" "$(get /accounts/acc-2002)" 200
expect "acc-2002, status" "$(field status)" DORMANT
expect "acc-2009, HTTP" "$(get /accounts/acc-2009)" 200
expect "acc-2009, status" "$(field status)" BLOCKED

step "Tài khoản không tồn tại"
expect "HTTP" "$(get /accounts/acc-0000)" 404
expect "reason" "$(field reason)" not_found

step "Lịch sử giao dịch acc-2001, trang 1"
expect "HTTP" "$(get /accounts/acc-2001/transactions)" 200
expect "Giao dịch đầu" "$(field transactionId)" txn-20011
expect "Trang sau" "$(field next)" "/accounts/acc-2001/transactions?cursor=acc-2001-p2"

step "Trang 2 theo cursor; tham số lọc khác được nhận nhưng bỏ qua"
expect "HTTP" "$(get '/accounts/acc-2001/transactions?cursor=acc-2001-p2&limit=50')" 200
expect "Giao dịch đầu" "$(field transactionId)" txn-20007
expect "Trang sau" "$(field next)" null
expect "Có fromDate, toDate: HTTP" "$(get '/accounts/acc-2001/transactions?fromDate=2026-10-01&toDate=2026-10-24')" 200
expect "Có fromDate, toDate: giao dịch đầu" "$(field transactionId)" txn-20011

step "Tài khoản ngủ không có giao dịch"
expect "HTTP" "$(get /accounts/acc-2002/transactions)" 200
expect "Số giao dịch" "$(count transactionId)" 0

step "Trạng thái thanh toán: bị từ chối, chờ xác thực, chưa rõ kết quả"
expect "pay-20261024-0002, HTTP" "$(get /payments/pay-20261024-0002)" 200
expect "pay-20261024-0002, status" "$(field status)" REJECTED
expect "pay-20261024-0002, reasonCode" "$(field reasonCode)" AM04
expect "pay-20261024-0003, xác thực" "$(get /payments/pay-20261024-0003/authentication)" 200
expect "pay-20261024-0003, status" "$(field status)" PENDING
expect "pay-20261024-0004, HTTP" "$(get /payments/pay-20261024-0004)" 200
expect "pay-20261024-0004, status" "$(field status)" UNKNOWN

step "Nạp ví bị từ chối vì vượt hạn mức"
expect "HTTP" "$(get /ewallet-topups/top-20261024-0002)" 200
expect "reasonCode" "$(field reasonCode)" AM02

# Đối tác riêng cho script này, để không dùng chung hạn mức tần suất với ./try-gateway-flow.sh
BANK_TOKEN="$(curl -s -u bank-c:sandbox-bank-c-secret -d grant_type=client_credentials \
  "$KEYCLOAK_URL/realms/open-banking/protocol/openid-connect/token" \
  | sed -En 's/.*"access_token":"([^"]+)".*/\1/p')"
if [ -z "$BANK_TOKEN" ]; then
  echo "Không lấy được mã truy cập của bank-c từ Keycloak" >&2
  exit 1
fi
BANK_KEY="Authorization: Bearer $BANK_TOKEN"

step "Qua API Gateway: bank-c tạo consent đọc tài khoản và giao dịch, khách hàng chọn acc-2001"
status=$(call POST "$GATEWAY_URL/consents" '{"scopes":["ACCOUNTS_READ","TRANSACTIONS_READ"],"validityDays":30}' \
  "$BANK_KEY")
expect "Tạo consent, HTTP" "$status" 201
CONSENT="$(field consentId)"
status=$(call POST "$INTERNAL_URL/internal/consents/$CONSENT/authorisation" \
  '{"customerId":"cif-2001","accountIds":["acc-2001"]}')
expect "Duyệt, HTTP" "$status" 200

step "Qua API Gateway: đọc trang 2 lịch sử giao dịch acc-2001 (cursor đi qua gateway)"
status=$(call GET "$GATEWAY_URL/accounts/acc-2001/transactions?cursor=acc-2001-p2" "" "$BANK_KEY" \
  "x-consent-id: $CONSENT")
expect "HTTP" "$status" 200
expect "Giao dịch đầu" "$(field transactionId)" txn-20007

step "Qua API Gateway: tài khoản acc-1003 không nằm trong consent"
status=$(call GET "$GATEWAY_URL/accounts/acc-1003" "" "$BANK_KEY" "x-consent-id: $CONSENT")
expect "HTTP" "$status" 403
expect "reason" "$(field reason)" account_not_granted

printf '\nHoàn tất: %d bước đều cho kết quả đúng.\n' "$step"
