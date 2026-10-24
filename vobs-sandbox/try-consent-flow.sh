#!/usr/bin/env bash
# Chạy thử trọn luồng consent trên sandbox: bên thứ ba tạo consent, khách hàng duyệt trên kênh ngân hàng, gateway
# kiểm tra quyền, khách hàng thu hồi. Mỗi bước in kết quả và dừng với mã lỗi nếu kết quả khác mong đợi.
#
#   PARTNER_URL   cổng đối tác    (mặc định http://localhost:8090)
#   INTERNAL_URL  cổng nội bộ     (mặc định http://localhost:8091)
set -euo pipefail

PARTNER_URL="${PARTNER_URL:-http://localhost:8090}"
INTERNAL_URL="${INTERNAL_URL:-http://localhost:8091}"
CUSTOMER="cif-1001"
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
  # sed -E để chạy được cả trên macOS (BSD sed) lẫn Linux (GNU sed)
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

check() {
  local consent="$1" scope="$2" account="$3"
  call POST "$INTERNAL_URL/internal/consent-checks" \
    "{\"consentId\":\"$consent\",\"clientId\":\"fintech-a\",\"scope\":\"$scope\",\"accountId\":\"$account\"}" >/dev/null
}

printf 'Chờ service Quản lý sự đồng ý sẵn sàng tại %s ' "$INTERNAL_URL"
for _ in $(seq 1 60); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "$INTERNAL_URL/internal/customers/$CUSTOMER/consents" || true)" = "200" ]; then
    break
  fi
  printf '.'
  sleep 2
done
echo

step "fintech-a tạo yêu cầu consent đọc tài khoản và lịch sử giao dịch trong 90 ngày"
status=$(call POST "$PARTNER_URL/consents" '{"scopes":["ACCOUNTS_READ","TRANSACTIONS_READ"],"validityDays":90}' \
  'x-vobs-client-id: fintech-a')
expect "HTTP" "$status" 201
expect "Trạng thái" "$(field status)" AWAITING_AUTHORISATION
CONSENT="$(field consentId)"
printf '   consentId: %s\n' "$CONSENT"

step "Gateway kiểm tra trước khi khách hàng duyệt: bị từ chối"
check "$CONSENT" ACCOUNTS_READ acc-1001
expect "allowed" "$(field allowed)" false
expect "reason" "$(field reason)" consent_awaiting_authorisation

step "Khách hàng $CUSTOMER duyệt trên kênh của ngân hàng, chọn chia sẻ tài khoản acc-1001"
status=$(call POST "$INTERNAL_URL/internal/consents/$CONSENT/authorisation" \
  "{\"customerId\":\"$CUSTOMER\",\"accountIds\":[\"acc-1001\"]}")
expect "HTTP" "$status" 200
expect "Trạng thái" "$(field status)" AUTHORISED

step "Gateway kiểm tra fintech-a đọc tài khoản acc-1001: được phép"
check "$CONSENT" ACCOUNTS_READ acc-1001
expect "allowed" "$(field allowed)" true

step "Gateway kiểm tra fintech-a đọc tài khoản acc-2002 (không được chọn): bị từ chối"
check "$CONSENT" ACCOUNTS_READ acc-2002
expect "allowed" "$(field allowed)" false
expect "reason" "$(field reason)" account_not_granted

step "fintech-a xin phạm vi khởi tạo thanh toán: bị từ chối, nhóm này chỉ dành cho ngân hàng và trung gian thanh toán (Điều 5 Thông tư 64)"
status=$(call POST "$PARTNER_URL/consents" '{"scopes":["PAYMENTS_INITIATE"],"validityDays":30}' 'x-vobs-client-id: fintech-a')
expect "HTTP" "$status" 403
expect "reason" "$(field reason)" scope_not_permitted

step "wallet-b (trung gian thanh toán) xin cùng phạm vi đó: được tạo"
status=$(call POST "$PARTNER_URL/consents" '{"scopes":["PAYMENTS_INITIATE"],"validityDays":30}' 'x-vobs-client-id: wallet-b')
expect "HTTP" "$status" 201

step "Khách hàng xem các consent đã cấp (Điều 11, 12 Thông tư 64)"
status=$(call GET "$INTERNAL_URL/internal/customers/$CUSTOMER/consents")
expect "HTTP" "$status" 200
if grep -q "$CONSENT" "$BODY"; then printf '   ✓ Có consent %s trong danh sách\n' "$CONSENT"; else
  printf '   ✗ Không thấy consent %s trong danh sách\n' "$CONSENT" >&2; exit 1; fi

step "Khách hàng thu hồi consent"
status=$(call POST "$INTERNAL_URL/internal/consents/$CONSENT/revocation" "{\"customerId\":\"$CUSTOMER\"}")
expect "HTTP" "$status" 200
expect "Trạng thái" "$(field status)" REVOKED

step "Gateway kiểm tra sau khi thu hồi: bị từ chối"
check "$CONSENT" ACCOUNTS_READ acc-1001
expect "allowed" "$(field allowed)" false
expect "reason" "$(field reason)" consent_revoked

printf '\nHoàn tất: %d bước đều cho kết quả đúng.\n' "$step"
