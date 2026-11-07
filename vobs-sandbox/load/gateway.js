// Tải lên API Gateway: đối tác đọc tài khoản với tốc độ cố định, qua mTLS, introspection, kiểm tra consent. Chạy bằng
// run.sh. Biến môi trường: RATE (lượt mỗi giây), DURATION, TOKENS (số mã truy cập đang dùng), SUMMARY (file kết quả).
import http from 'k6/http';

const CLIENT = 'fintech-a';
const CUSTOMER = 'cif-2001';
const ACCOUNT = 'acc-1001';
const GATEWAY = 'https://localhost:9443/open-banking/v1';
const TOKEN_URL = 'https://localhost:8443/realms/open-banking/protocol/openid-connect/token';
const INTERNAL = 'http://localhost:8091/internal';

const rate = Number(__ENV.RATE);

export const options = {
  tlsAuth: [{ domains: ['localhost'], cert: open('/pki/' + CLIENT + '.crt'), key: open('/pki/' + CLIENT + '.key') }],
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  setupTimeout: '300s',
  scenarios: {
    load: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration: __ENV.DURATION,
      preAllocatedVUs: Math.max(20, rate),
      maxVUs: Math.max(50, rate * 2),
    },
  },
};

function required(response, what) {
  if (response.status < 200 || response.status >= 300) {
    throw new Error(what + ': HTTP ' + response.status + ' ' + response.body);
  }
  return response;
}

// Mỗi token là một mục riêng trong cache introspection của gateway. Một consent dùng chung cho mọi token của đối tác.
export function setup() {
  const tokens = [];
  for (let i = 0; i < Number(__ENV.TOKENS); i++) {
    const issued = required(http.post(TOKEN_URL, { grant_type: 'client_credentials', client_id: CLIENT },
      { responseType: 'text' }), 'Lấy mã truy cập');
    tokens.push(issued.json('access_token'));
  }
  const created = required(http.post(GATEWAY + '/consents',
    JSON.stringify({ scopes: ['ACCOUNTS_READ'], validityDays: 90 }), {
      responseType: 'text',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + tokens[0] },
    }), 'Tạo consent');
  const consent = created.json('consentId');
  required(http.post(INTERNAL + '/consents/' + consent + '/authorisation',
    JSON.stringify({ customerId: CUSTOMER, accountIds: [ACCOUNT] }), {
      responseType: 'text',
      headers: { 'Content-Type': 'application/json' },
    }), 'Duyệt consent');
  return { tokens, consent };
}

export default function (data) {
  const token = data.tokens[(__VU * 31 + __ITER) % data.tokens.length];
  http.get(GATEWAY + '/accounts/' + ACCOUNT, {
    headers: { Authorization: 'Bearer ' + token, 'x-consent-id': data.consent },
  });
}

export function handleSummary(data) {
  return { [__ENV.SUMMARY]: JSON.stringify(data.metrics) };
}
