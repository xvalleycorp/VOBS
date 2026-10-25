#!/usr/bin/env bash
# Chạy thử luồng khách hàng duyệt consent theo FAPI 2.0 trên sandbox:
#   1. đối tác tạo consent qua API Gateway bằng mã truy cập client credentials;
#   2. đối tác gửi Pushed Authorization Request (PAR) tới Keycloak qua mTLS, kèm PKCE S256 và consent_id;
#   3. khách hàng đăng nhập ngân hàng, xem phạm vi dữ liệu, chọn tài khoản rồi duyệt (trang của vobs-keycloak);
#   4. đối tác đổi authorization code lấy mã truy cập gắn chứng thư, có claim consent_id;
#   5. đối tác đọc dữ liệu qua gateway bằng mã truy cập đó, không cần header x-consent-id.
# Script đóng cả vai trình duyệt của khách hàng (curl giữ cookie, không theo redirect). Dữ liệu tài khoản lấy từ API giả
# lập trên Microcks (chạy ./import-specs.sh trước). Mỗi bước in kết quả và dừng với mã lỗi nếu khác mong đợi.
#
#   KEYCLOAK_URL  Keycloak                (mặc định https://localhost:8443)
#   GATEWAY_URL   API Gateway             (mặc định https://localhost:9443/open-banking/v1)
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-https://localhost:8443}"
GATEWAY_URL="${GATEWAY_URL:-https://localhost:9443/open-banking/v1}"
OIDC="$KEYCLOAK_URL/realms/open-banking/protocol/openid-connect"
ISSUER="$KEYCLOAK_URL/realms/open-banking"
# Chứng thư tạo bằng ./pki.sh
PKI="$(cd "$(dirname "$0")/pki" && pwd)"
# Khách hàng mẫu trong keycloak/open-banking-realm.json, có tài khoản acc-1001 và acc-2002
CUSTOMER="cif-2001"
PASSWORD="sandbox"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
BODY="$WORK/body"
HEADERS="$WORK/headers"

step=0
step() {
  step=$((step + 1))
  printf '\n%d. %s\n' "$step" "$1"
}

expect() {
  local what="$1" actual="$2" expected="$3"
  if [ "$actual" = "$expected" ]; then
    printf '   ✓ %s: %s\n' "$what" "$actual"
  else
    printf '   ✗ %s: nhận %s, mong đợi %s\n   Nội dung: %s\n' "$what" "$actual" "$expected" "$(head -c 2000 "$BODY")" >&2
    exit 1
  fi
}

# field NAME -> giá trị chuỗi hoặc true/false/null của trường NAME trong $BODY
field() {
  sed -En "s/.*\"$1\":(\"[^\"]*\"|true|false|null).*/\1/p" "$BODY" | head -1 | tr -d '"'
}

# [AS=CLIENT] call METHOD URL [JSON] [header...] -> mã HTTP ra stdout, nội dung vào $BODY; AS chọn chứng thư client
call() {
  local method="$1" url="$2" data="${3:-}"
  shift 3 || shift $#
  local args=(-s -o "$BODY" -w '%{http_code}' -X "$method" -H 'Content-Type: application/json' --cacert "$PKI/ca.crt")
  if [ -n "${AS:-}" ]; then args+=(--cert "$PKI/$AS.crt" --key "$PKI/$AS.key"); fi
  for header in "$@"; do args+=(-H "$header"); done
  if [ -n "$data" ]; then args+=(--data "$data"); fi
  curl "${args[@]}" "$url"
}

# mtls CLIENT URL [form...] -> POST form tới Keycloak qua mTLS bằng chứng thư của CLIENT; mã HTTP ra stdout
mtls() {
  local client="$1" url="$2"
  shift 2
  local args=(-s -o "$BODY" -w '%{http_code}' --cacert "$PKI/ca.crt" --cert "$PKI/$client.crt" --key "$PKI/$client.key")
  for pair in "$@"; do args+=(--data-urlencode "$pair"); done
  curl "${args[@]}" "$url"
}

# client_token CLIENT -> mã truy cập client credentials
client_token() {
  mtls "$1" "$OIDC/token" grant_type=client_credentials "client_id=$1" >/dev/null
  field access_token
}

# jwt_claim TOKEN NAME -> giá trị chuỗi của claim NAME trong payload của JWT
jwt_claim() {
  local payload
  payload="$(echo "$1" | cut -d. -f2 | tr '_-' '/+')"
  while [ $(( ${#payload} % 4 )) -ne 0 ]; do payload="$payload="; done
  echo "$payload" | openssl base64 -d -A | sed -En "s/.*\"$2\":\"([^\"]*)\".*/\1/p"
}

# pkce -> đặt VERIFIER và CHALLENGE (S256)
pkce() {
  VERIFIER="$(openssl rand -hex 32)"
  CHALLENGE="$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')"
}

# par CLIENT CONSENT_ID [no-pkce] -> gửi PAR; mã HTTP vào PAR_STATUS, request_uri trong $BODY. Đặt STATE, VERIFIER,
# CHALLENGE nên phải gọi trực tiếp, không gọi trong $(...)
par() {
  local client="$1" consent="$2" args=()
  STATE="$(openssl rand -hex 16)"
  pkce
  args=(response_type=code "client_id=$client" "redirect_uri=https://$client.example/callback" scope=openid
        "state=$STATE" "consent_id=$consent")
  if [ "${3:-}" != "no-pkce" ]; then args+=("code_challenge=$CHALLENGE" code_challenge_method=S256); fi
  PAR_STATUS=$(mtls "$client" "$OIDC/ext/par/request" "${args[@]}")
}

# form_action FILE PATTERN -> địa chỉ action của form đầu tiên khớp PATTERN trong trang HTML
form_action() {
  grep -o 'action="[^"]*"' "$1" | sed -e 's/^action="//' -e 's/"$//' -e 's/&amp;/\&/g' | grep "$2" | head -1
}

# browse URL -> trình duyệt của khách hàng mở URL (giữ cookie), đi theo chuyển hướng trong Keycloak; dừng ở chuyển
# hướng ra ngoài (về đối tác). Trang vào $WORK/page, header vào $HEADERS, mã HTTP ra stdout
browse() {
  local url="$1" status location
  for _ in 1 2 3 4 5; do
    status=$(curl -s -o "$WORK/page" -D "$HEADERS" -w '%{http_code}' --cacert "$PKI/ca.crt" \
      -c "$WORK/cookies" -b "$WORK/cookies" "$url")
    location="$(sed -En 's/^[Ll]ocation: (.*)$/\1/p' "$HEADERS" | tr -d '\r')"
    case "$status:$location" in
      30?:"$KEYCLOAK_URL"*) url="$location" ;;
      30?:/*) url="$KEYCLOAK_URL$location" ;;
      *) break ;;
    esac
  done
  echo "$status"
}

# customer_opens CLIENT REQUEST_URI -> khách hàng mở trang ủy quyền và đăng nhập; trang tiếp theo vào $WORK/page
customer_opens() {
  local status action query
  rm -f "$WORK/cookies"
  # request_uri của Keycloak dạng urn:ietf:params:oauth:request_uri:<base64url>: chỉ cần mã hóa dấu hai chấm
  query="client_id=$1&request_uri=$(printf '%s' "$2" | sed 's/:/%3A/g')"
  status=$(browse "$OIDC/auth?$query")
  action="$(form_action "$WORK/page" login-actions/authenticate)"
  if [ "$status" != "200" ] || [ -z "$action" ]; then
    { echo "Chuyển hướng: $(sed -En 's/^[Ll]ocation: (.*)$/\1/p' "$HEADERS")"; cat "$WORK/page"; } > "$BODY"
    expect "Trang đăng nhập" "$status" "200 có form đăng nhập"
  fi
  curl -s -o "$WORK/page" -D "$HEADERS" -w '%{http_code}' --cacert "$PKI/ca.crt" -c "$WORK/cookies" -b "$WORK/cookies" \
    --data-urlencode "username=$CUSTOMER" --data-urlencode "password=$PASSWORD" "$action"
}

# customer_decides DECISION [ACCOUNT...] -> khách hàng bấm Đồng ý (approve) hoặc Từ chối (reject) trên trang duyệt;
# địa chỉ chuyển hướng về đối tác ra stdout
customer_decides() {
  local decision="$1" action args=()
  shift
  action="$(form_action "$WORK/page" login-actions/authenticate)"
  args=(--data-urlencode "$decision=")
  for account in "$@"; do args+=(--data-urlencode "accountId=$account"); done
  curl -s -o "$WORK/page" -D "$HEADERS" --cacert "$PKI/ca.crt" -c "$WORK/cookies" -b "$WORK/cookies" \
    "${args[@]}" "$action" >/dev/null
  sed -En 's/^[Ll]ocation: (.*)$/\1/p' "$HEADERS" | tr -d '\r'
}

# query_param URL NAME -> giá trị tham số NAME trong URL (đã giải mã %3A, %2F)
query_param() {
  echo "$1" | sed -En "s/.*[?&]$2=([^&]*).*/\1/p" | sed -e 's/%3A/:/g' -e 's/%2F/\//g'
}

printf 'Chờ Keycloak sẵn sàng tại %s ' "$KEYCLOAK_URL"
for _ in $(seq 1 90); do
  if [ -n "$(client_token fintech-a 2>/dev/null || true)" ]; then break; fi
  printf '.'
  sleep 2
done
echo
FINTECH_TOKEN="$(client_token fintech-a)"
WALLET_TOKEN="$(client_token wallet-b)"
if [ -z "$FINTECH_TOKEN" ] || [ -z "$WALLET_TOKEN" ]; then
  echo "Không lấy được mã truy cập client credentials từ Keycloak:" >&2
  cat "$BODY" >&2
  exit 1
fi

step "fintech-a tạo consent đọc tài khoản và giao dịch qua gateway"
status=$(AS=fintech-a call POST "$GATEWAY_URL/consents" '{"scopes":["ACCOUNTS_READ","TRANSACTIONS_READ"],"validityDays":90}' \
  "Authorization: Bearer $FINTECH_TOKEN")
expect "HTTP" "$status" 201
CONSENT="$(field consentId)"
printf '   consentId: %s\n' "$CONSENT"

step "PAR thiếu PKCE: bị từ chối (FAPI 2.0 bắt buộc PKCE S256)"
par fintech-a "$CONSENT" no-pkce
expect "HTTP" "$PAR_STATUS" 400
expect "error" "$(field error)" invalid_request

step "fintech-a gửi PAR qua mTLS, kèm PKCE và consent_id"
par fintech-a "$CONSENT"
expect "HTTP" "$PAR_STATUS" 201
REQUEST_URI="$(field request_uri)"
printf '   request_uri: %s\n' "$REQUEST_URI"

step "Khách hàng $CUSTOMER đăng nhập ngân hàng: thấy trang duyệt consent với phạm vi và tài khoản của mình"
status=$(customer_opens fintech-a "$REQUEST_URI")
cp "$WORK/page" "$BODY"
expect "HTTP" "$status" 200
expect "Trang duyệt consent" "$(grep -c 'id="vobs-consent-form"' "$WORK/page")" 1
expect "Phạm vi" "$(grep -o 'data-scope="[A-Z_]*"' "$WORK/page" | tr '\n' ' ')" \
  'data-scope="ACCOUNTS_READ" data-scope="TRANSACTIONS_READ" '
expect "Tài khoản chọn được" "$(grep -o 'name="accountId" value="[^"]*"' "$WORK/page" | sed 's/.*value="//; s/"//' | tr '\n' ' ')" \
  'acc-1001 acc-2002 '

step "Khách hàng chọn tài khoản acc-1001 và đồng ý: ngân hàng chuyển về đối tác kèm authorization code"
LOCATION="$(customer_decides approve acc-1001)"
printf '   Chuyển hướng: %s\n' "${LOCATION%%\?*}"
expect "state" "$(query_param "$LOCATION" state)" "$STATE"
expect "iss (RFC 9207)" "$(query_param "$LOCATION" iss)" "$ISSUER"
CODE="$(query_param "$LOCATION" code)"
if [ -z "$CODE" ]; then
  printf '   ✗ Không có code trong %s\n' "$LOCATION" >&2
  exit 1
fi

step "fintech-a đổi code lấy mã truy cập qua mTLS, kèm code_verifier: token gắn consent và chứng thư"
status=$(mtls fintech-a "$OIDC/token" grant_type=authorization_code "code=$CODE" client_id=fintech-a \
  "redirect_uri=https://fintech-a.example/callback" "code_verifier=$VERIFIER")
expect "HTTP" "$status" 200
CUSTOMER_TOKEN="$(field access_token)"
REFRESH_TOKEN="$(field refresh_token)"
expect "consent_id trong token" "$(jwt_claim "$CUSTOMER_TOKEN" consent_id)" "$CONSENT"
expect "Token gắn chứng thư (cnf.x5t#S256)" "$([ -n "$(jwt_claim "$CUSTOMER_TOKEN" 'x5t#S256')" ] && echo có)" "có"

step "Làm mới mã truy cập bằng refresh token: token mới vẫn gắn đúng consent"
status=$(mtls fintech-a "$OIDC/token" grant_type=refresh_token "refresh_token=$REFRESH_TOKEN" client_id=fintech-a)
expect "HTTP" "$status" 200
expect "consent_id trong token mới" "$(jwt_claim "$(field access_token)" consent_id)" "$CONSENT"
CUSTOMER_TOKEN="$(field access_token)"
REFRESH_TOKEN="$(field refresh_token)"

step "Dùng lại authorization code: bị từ chối, refresh token cấp từ code này bị thu hồi (RFC 6749 mục 4.1.2)"
status=$(mtls fintech-a "$OIDC/token" grant_type=authorization_code "code=$CODE" client_id=fintech-a \
  "redirect_uri=https://fintech-a.example/callback" "code_verifier=$VERIFIER")
expect "HTTP" "$status" 400
expect "error" "$(field error)" invalid_grant
status=$(mtls fintech-a "$OIDC/token" grant_type=refresh_token "refresh_token=$REFRESH_TOKEN" client_id=fintech-a)
expect "Refresh token sau khi code bị dùng lại: HTTP" "$status" 400

step "Consent đã được duyệt với đúng tài khoản khách hàng chọn"
status=$(AS=fintech-a call GET "$GATEWAY_URL/consents/$CONSENT" "" "Authorization: Bearer $FINTECH_TOKEN")
expect "HTTP" "$status" 200
expect "status" "$(field status)" AUTHORISED
expect "accountIds" "$(sed -En 's/.*"accountIds":\[([^]]*)\].*/\1/p' "$BODY")" '"acc-1001"'

step "Đọc tài khoản acc-1001 bằng mã truy cập của khách hàng, không cần header x-consent-id"
status=$(AS=fintech-a call GET "$GATEWAY_URL/accounts/acc-1001" "" "Authorization: Bearer $CUSTOMER_TOKEN")
expect "HTTP" "$status" 200
expect "accountId" "$(field accountId)" acc-1001

step "Đọc tài khoản acc-2002 (khách hàng không chọn): bị từ chối"
status=$(AS=fintech-a call GET "$GATEWAY_URL/accounts/acc-2002" "" "Authorization: Bearer $CUSTOMER_TOKEN")
expect "HTTP" "$status" 403
expect "reason" "$(field reason)" account_not_granted

step "Gửi kèm x-consent-id của consent khác: bị từ chối, consent lấy từ token"
status=$(AS=fintech-a call GET "$GATEWAY_URL/accounts/acc-1001" "" "Authorization: Bearer $CUSTOMER_TOKEN" \
  "x-consent-id: consent-khac")
expect "HTTP" "$status" 403
expect "reason" "$(field reason)" consent_mismatch

step "Khách hàng từ chối một consent khác: đối tác nhận access_denied, consent chuyển sang REJECTED"
AS=fintech-a call POST "$GATEWAY_URL/consents" '{"scopes":["ACCOUNTS_READ"],"validityDays":30}' \
  "Authorization: Bearer $FINTECH_TOKEN" >/dev/null
SECOND="$(field consentId)"
par fintech-a "$SECOND"
customer_opens fintech-a "$(field request_uri)" >/dev/null
LOCATION="$(customer_decides reject)"
expect "error" "$(query_param "$LOCATION" error)" access_denied
expect "state" "$(query_param "$LOCATION" state)" "$STATE"
AS=fintech-a call GET "$GATEWAY_URL/consents/$SECOND" "" "Authorization: Bearer $FINTECH_TOKEN" >/dev/null
expect "status" "$(field status)" REJECTED

step "wallet-b dùng consent của fintech-a trong PAR của mình: bị từ chối, consent không đổi"
AS=fintech-a call POST "$GATEWAY_URL/consents" '{"scopes":["ACCOUNTS_READ"],"validityDays":30}' \
  "Authorization: Bearer $FINTECH_TOKEN" >/dev/null
THIRD="$(field consentId)"
par wallet-b "$THIRD"
expect "PAR, HTTP" "$PAR_STATUS" 201
customer_opens wallet-b "$(field request_uri)" >/dev/null
LOCATION="$(sed -En 's/^[Ll]ocation: (.*)$/\1/p' "$HEADERS" | tr -d '\r')"
expect "Chuyển về wallet-b với error" "$(query_param "$LOCATION" error)" access_denied
AS=fintech-a call GET "$GATEWAY_URL/consents/$THIRD" "" "Authorization: Bearer $FINTECH_TOKEN" >/dev/null
expect "Consent của fintech-a" "$(field status)" AWAITING_AUTHORISATION

printf '\nHoàn tất: %d bước đều cho kết quả đúng.\n' "$step"
