#!/usr/bin/env bash
# Tạo PKI cho sandbox vào pki/ (không commit): một CA, chứng thư máy chủ cho Keycloak và API Gateway, chứng thư client
# cho từng đối tác mẫu. Đối tác dùng chứng thư client để xác thực mTLS với Keycloak (lấy mã truy cập gắn chứng thư,
# RFC 8705) và với API Gateway. Thêm một khóa riêng để gateway xác thực với Keycloak khi hỏi về token. Rồi ghép chứng
# thư và khóa vào cấu hình gateway (pki/apisix.yaml), khóa công khai vào realm (pki/open-banking-realm.json).
#
# Chạy một lần trước docker compose up; chạy lại thì giữ nguyên PKI đã có. FORCE=1 tạo lại từ đầu.
# Khóa chỉ dùng cho sandbox: quyền đọc mở cho mọi user để container (Keycloak, APISIX) đọc được.
set -euo pipefail

cd "$(dirname "$0")"
OUT=pki
PARTNERS=(fintech-a wallet-b bank-c)

if [ "${FORCE:-0}" = "1" ]; then
  rm -rf "$OUT"
fi
mkdir -p "$OUT"

cat > "$OUT/openssl.cnf" <<'EOF'
[req]
distinguished_name = dn
prompt = no
[dn]
[ca]
basicConstraints = critical, CA:TRUE
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
[server]
basicConstraints = critical, CA:FALSE
keyUsage = critical, digitalSignature
extendedKeyUsage = serverAuth
subjectAltName = DNS:localhost, DNS:keycloak, DNS:gateway, IP:127.0.0.1
[client]
basicConstraints = critical, CA:FALSE
keyUsage = critical, digitalSignature
extendedKeyUsage = clientAuth
EOF

# key NAME -> khóa EC P-256 dạng PKCS#8 (Keycloak, APISIX và curl đều đọc được)
key() {
  openssl ecparam -name prime256v1 -genkey -noout -out "$OUT/$1.ec.key"
  openssl pkcs8 -topk8 -nocrypt -in "$OUT/$1.ec.key" -out "$OUT/$1.key"
  rm -f "$OUT/$1.ec.key"
}

# issue NAME SUBJECT EXTENSIONS -> chứng thư do CA của sandbox ký, hiệu lực 825 ngày
issue() {
  key "$1"
  openssl req -new -key "$OUT/$1.key" -subj "$2" -config "$OUT/openssl.cnf" -out "$OUT/$1.csr"
  openssl x509 -req -in "$OUT/$1.csr" -CA "$OUT/ca.crt" -CAkey "$OUT/ca.key" -CAcreateserial -days 825 \
    -extfile "$OUT/openssl.cnf" -extensions "$3" -out "$OUT/$1.crt" 2>/dev/null
  rm -f "$OUT/$1.csr"
}

if [ ! -f "$OUT/ca.crt" ]; then
  echo "Tạo CA của sandbox"
  key ca
  openssl req -x509 -new -key "$OUT/ca.key" -subj "/O=VOBS Sandbox/CN=VOBS Sandbox CA" -days 3650 \
    -config "$OUT/openssl.cnf" -extensions ca -out "$OUT/ca.crt"
fi
for server in keycloak gateway; do
  if [ ! -f "$OUT/$server.crt" ]; then
    echo "Tạo chứng thư máy chủ $server"
    issue "$server" "/O=VOBS Sandbox/CN=$server" server
  fi
done
for partner in "${PARTNERS[@]}"; do
  if [ ! -f "$OUT/$partner.crt" ]; then
    echo "Tạo chứng thư client $partner"
    issue "$partner" "/O=VOBS Sandbox/CN=$partner" client
  fi
done
if [ ! -f "$OUT/gateway-client.key" ]; then
  echo "Tạo khóa của gateway để xác thực với Keycloak"
  key gateway-client
fi
chmod 644 "$OUT"/*.key "$OUT"/*.crt

# Realm = keycloak/open-banking-realm.json + khóa công khai của gateway. Khóa P-256 dạng DER kết thúc bằng 65 byte
# 04 || x || y; JWK cần x và y mã hóa base64url.
base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}
openssl pkey -in "$OUT/gateway-client.key" -pubout -outform DER 2>/dev/null | tail -c 64 > "$OUT/gateway-client.xy"
JWK_X="$(head -c 32 "$OUT/gateway-client.xy" | base64url)"
JWK_Y="$(tail -c 32 "$OUT/gateway-client.xy" | base64url)"
rm -f "$OUT/gateway-client.xy"
sed -e "s|__GATEWAY_JWK_X__|$JWK_X|" -e "s|__GATEWAY_JWK_Y__|$JWK_Y|" keycloak/open-banking-realm.json \
  > "$OUT/open-banking-realm.json"

# Cấu hình gateway = route trong gateway/apisix.yaml + chứng thư máy chủ và CA để xác thực client (mTLS). APISIX
# standalone chỉ nhận PEM viết thẳng trong file, nên ghép tại đây.
indent() {
  sed 's/^/      /' "$1"
}
# Khóa của gateway nằm trong chuỗi YAML một dòng, xuống dòng viết là \n (sed cần \\n)
GATEWAY_CLIENT_KEY="$(awk 'BEGIN { ORS = "\\\\n" } { print }' "$OUT/gateway-client.key")"
{
  sed -e '/^#END$/d' -e "s|__GATEWAY_CLIENT_KEY__|$GATEWAY_CLIENT_KEY|" gateway/apisix.yaml
  echo
  echo "ssls:"
  echo "  - id: gateway"
  echo "    snis: [localhost]"
  echo "    cert: |"
  indent "$OUT/gateway.crt"
  echo "    key: |"
  indent "$OUT/gateway.key"
  echo "    client:"
  echo "      ca: |"
  sed 's/^/        /' "$OUT/ca.crt"
  echo "      depth: 1"
  echo "#END"
} > "$OUT/apisix.yaml"

echo "PKI của sandbox ở $(pwd)/$OUT; CA: $OUT/ca.crt"
