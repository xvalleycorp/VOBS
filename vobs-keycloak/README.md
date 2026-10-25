# Xác thực và cấp quyền (`vobs-keycloak`)

Phần mở rộng cho [Keycloak](https://www.keycloak.org) 26.8 để khách hàng duyệt consent của Open API trên kênh của ngân
hàng, theo FAPI 2.0 Security Profile. Đối tác chuyển khách hàng sang ngân hàng; khách hàng đăng nhập, xem đối tác xin
dữ liệu gì và trong bao lâu, chọn tài khoản được chia sẻ, rồi đồng ý hoặc từ chối. Đối tác nhận mã truy cập chỉ dùng
được với đúng consent đó.

```
Đối tác                     Keycloak + vobs-keycloak               Quản lý sự đồng ý      API Gateway
  | POST /consents (client credentials) ---------------------------------------------------> | -> consentId
  | PAR: PKCE S256, consent_id, mTLS ----> | -> request_uri
  | chuyển khách hàng sang /auth --------> | khách hàng đăng nhập
  |                                        | GET /internal/consents/{id} -> |
  |                                        | trang duyệt: phạm vi, thời hạn, chọn tài khoản
  |                                        | POST .../authorisation ------> |
  | <-- redirect_uri?code&state&iss ------ |
  | POST /token: code, code_verifier, mTLS |
  | <-- access_token (consent_id, cnf) --- |
  | GET /accounts/{id} (Bearer, mTLS) -----------------------------------------------------> | kiểm tra consent theo claim
```

| Lớp | Vai trò |
| --- | --- |
| `ConsentAuthorisationAuthenticator` | Bước `vobs-consent-authorisation` trong luồng đăng nhập: kiểm tra consent, hiện trang duyệt, ghi quyết định |
| `ConsentIdProtocolMapper` | Mapper `vobs-consent-id-mapper`: gắn claim `consent_id` vào mã truy cập, ID token, kết quả introspection |
| `ConsentServiceClient` | Gọi API nội bộ của service consent: xem, duyệt, từ chối |
| `CustomerAccounts` | Mã khách hàng và tài khoản của người dùng Keycloak |
| Giao diện `vobs` | Giao diện đăng nhập kế thừa `keycloak.v2`, thêm trang `vobs-consent.ftl`; tiếng Việt và tiếng Anh |

Quy tắc chính:

- **Consent phải khớp đối tác.** Consent phải thuộc đúng đối tác đã gửi PAR, đang chờ duyệt, và chưa gắn với khách hàng
  khác. Sai một điều kiện thì đối tác nhận `access_denied`, không kèm chi tiết, để đối tác không dò được consent của bên
  khác.
- **Chỉ chọn tài khoản của mình.** Khách hàng chỉ chọn được tài khoản của chính mình; form bị sửa để gửi tài khoản khác
  thì bị từ chối. Consent đọc dữ liệu phải có ít nhất một tài khoản.
- **Từ chối được ghi lại.** Khách hàng từ chối thì consent chuyển sang `REJECTED`, đối tác nhận `access_denied`.
- **Token gắn đúng consent.** Claim `consent_id` lấy từ phiên của đối tác, nên mã truy cập cấp lại bằng refresh token
  vẫn mang đúng consent. Gateway lấy consent từ claim này, không tin header `x-consent-id` của đối tác.
- **Mỗi lần duyệt đăng nhập lại.** Luồng đăng nhập mẫu không dùng lại phiên cũ (SSO), nên mỗi consent có phiên riêng.

## Cài đặt

Build JAR rồi chép vào thư mục `providers` của Keycloak. JAR không kèm thư viện bên thứ ba: mọi API dùng đến đều có
sẵn trong Keycloak.

```bash
mvn -pl vobs-keycloak -am package
cp vobs-keycloak/target/vobs-keycloak-*.jar /opt/keycloak/providers/
/opt/keycloak/bin/kc.sh build
```

Hoặc dùng [`Dockerfile`](Dockerfile): image Keycloak chính thức thêm JAR này, như sandbox đang dùng.

Địa chỉ API nội bộ của service consent đặt bằng biến môi trường `VOBS_CONSENT_INTERNAL_URL` của Keycloak, ví dụ
`http://consent:8081`. API này chỉ mở trong mạng nội bộ; ở môi trường thật, đặt mTLS giữa Keycloak và service consent
bằng service mesh.

## Cấu hình realm

Realm mẫu: [`vobs-sandbox/keycloak/open-banking-realm.json`](../vobs-sandbox/keycloak/open-banking-realm.json).

| Mục | Cấu hình |
| --- | --- |
| Luồng đăng nhập | Luồng `vobs-browser` gồm bước xác thực khách hàng (mẫu dùng username và mật khẩu; ngân hàng thay bằng phương thức của mình, ví dụ OTP hoặc sinh trắc học) và bước `vobs-consent-authorisation`, đặt làm Browser flow |
| Giao diện | Login theme `vobs`; bật đa ngôn ngữ, mặc định `vi` |
| Client của đối tác | Bật Standard flow; `redirectUris` là địa chỉ HTTPS cố định; bắt buộc PAR (`require.pushed.authorization.requests`), PKCE S256, token gắn chứng thư; xác thực client bằng chứng thư (`client-x509`) |
| Mapper | `vobs-consent-id-mapper` trên mỗi client của đối tác |
| Client policy | Profile `vobs-fapi-2` áp cho mọi client (mục dưới) |
| Người dùng | Thuộc tính `customer_id` (không có thì dùng username) và `vobs_account`, mỗi giá trị dạng `<mã tài khoản>\|<tên hiển thị>` |

Ở môi trường thật, người dùng và tài khoản đến từ hệ thống của ngân hàng: User Federation (LDAP hoặc provider nối core)
nạp hai thuộc tính trên, hoặc thay `CustomerAccounts` bằng lớp gọi thẳng core banking.

### Profile `vobs-fapi-2`

Lấy profile `fapi-2-security-profile` có sẵn của Keycloak, bỏ hai executor:

- `consent-required`: hiện màn hình đồng ý mặc định của Keycloak, trùng với trang duyệt consent của VOBS.
- `secure-signature-algorithm`: bắt client ký bằng PS256. Gateway chưa xác minh được PS256 (mục dưới).

Các executor còn lại:

- client bảo mật (confidential);
- xác thực client bằng chứng thư hoặc JWT;
- URI chỉ dùng HTTPS;
- token gắn chứng thư (holder-of-key);
- PKCE bắt buộc;
- nội dung yêu cầu ủy quyền chỉ đến từ PAR;
- tắt full scope và implicit grant.

## Giới hạn hiện tại

- **Thuật toán ký token.** FAPI 2.0 yêu cầu PS256, ES256 hoặc EdDSA. Realm mẫu vẫn ký RS256, vì plugin
  `openid-connect` của APISIX 3.19 (lua-resty-openidc 1.9) chỉ xác minh được RS* và HS* khi lấy khóa từ JWKS. Hướng
  xử lý: gateway xác minh token bằng introspection, hoặc xác minh ES256 bằng plugin riêng.
- **Phân quyền đối tác.** Trang duyệt chưa đọc loại tổ chức của đối tác. Việc chặn đối tác không được dùng phạm vi
  thanh toán do service consent làm khi tạo consent (Điều 5).
- **Bộ kiểm thử chứng nhận.** Chưa chạy bộ kiểm thử FAPI 2.0 của OpenID Foundation (conformance suite).
