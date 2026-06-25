import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.K6_BASE_URL || 'http://127.0.0.1:18080';
const user = __ENV.K6_USER || 'smoke_user_a';
const pass = __ENV.K6_PASS || 'smokepass123';
const lab = __ENV.K6_LAB === '1' || __ENV.K6_LAB === 'true';

export const options = {
  vus: Number(__ENV.K6_VUS || (lab ? 2 : 5)),
  duration: __ENV.K6_DURATION || '30s',
  thresholds: lab
    ? {}
    : {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<800'],
      },
};

export default function () {
  const loginRes = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ username: user, password: pass }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  const ok = check(loginRes, {
    'login 200': (r) => r.status === 200,
  });
  if (!ok) {
    sleep(1);
    return;
  }
  let token = '';
  try {
    token = loginRes.json('access_token');
  } catch (e) {
    sleep(1);
    return;
  }
  const headers = { Authorization: `Bearer ${token}` };
  const chats = http.get(`${baseUrl}/api/v1/chats?limit=20`, { headers });
  check(chats, {
    'chats 200': (r) => r.status === 200,
  });
  sleep(1);
}
