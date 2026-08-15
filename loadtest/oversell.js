/**
 * Sends every virtual user at the last units of one product at the same instant, and
 * reports how many orders got through against how many units existed.
 *
 * The number that matters is `ordered`. If it ever exceeds the starting stock, the
 * service sold something it did not have.
 *
 *   k6 run -e BASE_URL=https://<host> -e PRODUCT_ID=16 -e VUS=100 loadtest/oversell.js
 *
 * Run it once per value of STOCK_STRATEGY on the ECS task definition (ATOMIC,
 * PESSIMISTIC, NONE) to get the comparison. NONE is the unguarded read-modify-write and
 * is the one expected to oversell -- that is what it is there to show.
 *
 * Carts are filled during setup, before the rush, on purpose. The cart's own stock check
 * is per cart ("is one unit more than the shelf holds?"), so all users can hold one unit
 * each while stock is untouched. The contention then happens where it is being measured:
 * the decrement inside the order.
 */
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 16);
const VUS = Number(__ENV.VUS || 100);

const ordered = new Counter('orders_placed');
const soldOut = new Counter('orders_rejected_sold_out');
const declined = new Counter('orders_declined');
const failed = new Counter('orders_failed_other');

export const options = {
  scenarios: {
    rush: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: VUS,
      maxDuration: '3m',
    },
  },
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function stockOf(productId) {
  const res = http.get(`${BASE_URL}/api/products/${productId}`);
  return res.status === 200 ? res.json('stockQuantity') : null;
}

export function setup() {
  const startingStock = stockOf(PRODUCT_ID);
  const suffix = `${Date.now()}`;
  const tokens = [];

  for (let i = 0; i < VUS; i++) {
    const email = `rush-${suffix}-${i}@example.com`;
    http.post(
      `${BASE_URL}/api/auth/signup`,
      JSON.stringify({
        email,
        password: 'password123',
        name: '부하테스트',
        phone: '010-1234-5678',
        age14: true,
        termsOfService: true,
        financialTerms: true,
        thirdPartyConsent: true,
      }),
      { headers: JSON_HEADERS }
    );

    const login = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email, password: 'password123' }),
      { headers: JSON_HEADERS }
    );
    if (login.status !== 200) {
      throw new Error(`login failed for ${email}: ${login.status} ${login.body}`);
    }
    const token = login.json('accessToken');

    const add = http.post(
      `${BASE_URL}/api/cart/items`,
      JSON.stringify({ productId: PRODUCT_ID, quantity: 1 }),
      { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } }
    );
    if (add.status !== 200) {
      throw new Error(`cart setup failed: ${add.status} ${add.body}`);
    }
    tokens.push(token);
  }

  console.log(`starting stock for product ${PRODUCT_ID}: ${startingStock}, buyers: ${VUS}`);
  if (startingStock !== null && startingStock >= VUS) {
    console.warn('stock is not scarcer than demand -- nothing will contend');
  }
  return { tokens, startingStock };
}

export default function (data) {
  const token = data.tokens[(exec.vu.idInTest - 1) % data.tokens.length];

  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({
      recipientName: '부하테스트',
      recipientPhone: '010-1234-5678',
      zipcode: '12345',
      address1: '서울시 강남구 테헤란로 1',
      address2: '101동 202호',
      deliveryRequest: '문 앞에 놓아주세요',
      paymentMethod: 'card',
    }),
    {
      headers: {
        ...JSON_HEADERS,
        Authorization: `Bearer ${token}`,
        // 한 사용자당 한 번의 결제 시도. 재시도가 새 주문으로 세어지지 않게 한다.
        'Idempotency-Key': `rush-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
      },
      tags: { name: 'POST /api/orders' },
    }
  );

  if (res.status === 201) ordered.add(1);
  else if (res.status === 409) soldOut.add(1);
  else if (res.status === 402) declined.add(1);
  else failed.add(1);
}

export function teardown(data) {
  const remaining = stockOf(PRODUCT_ID);
  console.log('--------------------------------------------------');
  console.log(`starting stock : ${data.startingStock}`);
  console.log(`remaining stock: ${remaining}`);
  console.log('Compare orders_placed against starting stock in the summary above.');
  console.log('orders_placed > starting stock means the service oversold.');
  console.log('--------------------------------------------------');
}
