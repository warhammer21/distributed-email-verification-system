import http from 'k6/http';
import { check } from 'k6';

export const options = {
};

export default function () {
  const url = 'http://localhost:8081/request-registration';

  const payload = JSON.stringify({
    email: 'test@example.com',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(url, payload, params);

  check(res, {
    'status is success': (r) => r.status >= 200 && r.status < 300,
  });
}
export const options = {
  // hosts line is completely removed
  stages: [
     { duration: '0s', target: 10 },  // Instantly jump to 10 users
     { duration: '30s', target: 10 },
  ],
  thresholds: { http_req_duration: ['avg<100', 'p(95)<200'] },
  noConnectionReuse: true,
  userAgent: 'MyK6UserAgentString/1.0',
};

export default function () {
  http.get('http://localhost:8081/request-registration');
}
