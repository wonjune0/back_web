# 부하 테스트

`oversell.js`는 재고가 얼마 남지 않은 상품 하나에 다수의 구매자를 동시에 보내고,
**재고보다 많이 팔렸는지**를 센다.

## 실행

```bash
k6 run -e BASE_URL=https://<도메인> -e PRODUCT_ID=16 -e VUS=100 loadtest/oversell.js
```

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:8080` | 대상 서버 |
| `PRODUCT_ID` | `16` | 경합시킬 상품. 재고가 VUS보다 적어야 의미가 있다 |
| `VUS` | `100` | 동시 구매자 수 |

## 전략을 바꿔 가며 비교하기

재고 차감 방식은 이미지가 아니라 설정에 있으므로, **같은 빌드로** 세 번 측정할 수 있다.

- 로컬: `application.yml`의 `stock.strategy`
- AWS: ECS 태스크 정의의 `STOCK_STRATEGY` 환경변수
  (`terraform apply -var stock_strategy=PESSIMISTIC` 후 재배포)

| 값 | 방식 |
| --- | --- |
| `ATOMIC` | 조건부 UPDATE 한 방. 운영 기본값 |
| `PESSIMISTIC` | `SELECT ... FOR UPDATE` |
| `NONE` | 읽고-고치고-쓰기. **초과 판매가 나는 쪽** |

## 읽는 법

k6 요약의 `orders_placed`를 **시작 재고**와 비교한다.

- `orders_placed == 시작 재고` → 정확히 있는 만큼만 팔렸다
- `orders_placed > 시작 재고` → **초과 판매**. `NONE`에서 나타난다
- `orders_rejected_sold_out` → 재고 부족으로 정상 거절된 수(409)

같이 볼 것: `http_req_duration`의 p95(전략별 지연 차이), 그리고 CloudWatch의
ECS CPU / RDS DatabaseConnections.

## 주의

- 매 실행마다 VUS만큼 계정을 새로 만든다. 운영 데이터가 있는 환경에서는 돌리지 말 것.
- 재고는 실행할 때마다 줄어든다. 다시 측정하려면 재고를 되돌려야 한다
  (주문 취소 API를 쓰거나 `product_stocks`를 직접 갱신).
- `NONE`은 `product_stocks`의 `CHECK (quantity >= 0)` 때문에 음수까지는 가지 않는다.
  초과 판매는 **재고가 음수가 되는 것이 아니라 성공 주문 수가 재고를 넘는 것**으로 나타난다.
