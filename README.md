# back_web — 이커머스 백엔드 API (Spring Boot)

상품 조회부터 주문 생성까지를 담당하는 REST API 서버입니다.
컨테이너로 빌드되어 AWS ECS Fargate 위에서 동작하며, Aurora MySQL을 사용합니다.

| 항목 | 값 |
|---|---|
| 언어 / 런타임 | Java 21 |
| 프레임워크 | Spring Boot 3.3.5 |
| 데이터 | Spring Data JPA (Hibernate), Flyway |
| 인증 | Spring Security + JWT (jjwt 0.12.6) |
| DB | MySQL 8.0 / Aurora MySQL Serverless v2 |
| 빌드 | Gradle |
| 테스트 | JUnit 5, MockMvc, Testcontainers |

관련 리포지토리
- 인프라 (Terraform): [`wonjune0/terraform`](https://github.com/wonjune0/terraform)
- 프론트엔드 (정적 웹): [`wonjune0/front_web`](https://github.com/wonjune0/front_web)

---

## 실행 위치

```
브라우저 → CloudFront (app.wonjune.cloud)
             └─ /api/*  → ALB → ECS Fargate (이 애플리케이션, :80)
                                    └─ Aurora MySQL (프라이빗 서브넷)
```

컨테이너는 프라이빗 서브넷에서 동작하며 퍼블릭 IP가 없습니다. 외부에서 오는 요청은
반드시 CloudFront → ALB를 거칩니다. 프론트엔드와 **같은 도메인**을 사용하므로
운영 환경에서는 CORS가 개입하지 않습니다.

---

## 패키지 구조

도메인별로 패키지를 나누고, 각 도메인 안에 엔티티 · 리포지토리 · 서비스 · 컨트롤러 ·
DTO를 함께 둡니다.

```
com.wonjune.backweb
├── auth/          회원가입, 로그인, 내 정보
├── user/          User 엔티티
├── category/      카테고리 트리 조회
├── product/       상품 목록/검색/상세
├── cart/          장바구니 (사용자당 1개)
├── order/         주문 생성/조회
└── common/
    ├── config/    SecurityConfig, LocalCorsConfig
    ├── security/  JwtTokenProvider, JwtAuthenticationFilter, AuthenticatedUser
    ├── exception/ ApiException, GlobalExceptionHandler, ErrorResponse
    ├── dto/       PageResponse
    └── web/       HealthController
```

---

## API

### 인증 불필요

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/` | 헬스 체크. ALB 대상 그룹이 200을 기대 |
| `GET` | `/actuator/health` | 상태 확인 |
| `POST` | `/api/auth/signup` | 회원가입 → `201` |
| `POST` | `/api/auth/login` | 로그인 → `accessToken`, `expiresIn`, `user` |
| `GET` | `/api/categories` | 카테고리 트리 (2단계) |
| `GET` | `/api/products` | 목록. `search`, `parentCategory`, `category`, `sort`, `page`, `size` |
| `GET` | `/api/products/{id}` | 상세 |

### JWT 필요 (`Authorization: Bearer <token>`)

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/api/auth/me` | 내 정보 |
| `GET` | `/api/cart` | 장바구니 조회 |
| `POST` | `/api/cart/items` | 담기 (같은 상품이면 수량 누적) |
| `PATCH` | `/api/cart/items/{productId}` | 수량을 절대값으로 설정 |
| `DELETE` | `/api/cart/items/{productId}` | 항목 삭제 |
| `DELETE` | `/api/cart` | 비우기 → `204` |
| `POST` | `/api/orders` | 주문 생성 → `201` |
| `GET` | `/api/orders` | 주문 목록 (페이징) |
| `GET` | `/api/orders/{orderNumber}` | 주문 상세 |

### 정렬 키

`/api/products`의 `sort` 파라미터는 `recommended`(기본, id 오름차순), `price-asc`,
`price-desc`, `reviews`를 받습니다. 프론트엔드의 정렬 탭이 이 값을 그대로 전달합니다.

---

## 설계 판단

### 인증 — 상태를 서버에 두지 않는다

세션 대신 JWT를 사용하고 `SessionCreationPolicy.STATELESS`로 설정했습니다.
ECS 태스크가 오토스케일링으로 늘어나거나 교체되어도 **어느 태스크가 요청을 받든
동일하게 동작**합니다. 세션 클러스터링이나 스티키 세션이 필요 없습니다.

`JwtAuthenticationFilter`는 토큰의 클레임만으로 `Authentication`을 구성하므로
**요청마다 사용자 테이블을 조회하지 않습니다.**

```java
AuthenticatedUser principal = jwtTokenProvider.parseToken(token);
var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
```

서명 키(`JWT_SECRET`)는 코드에 없습니다. Terraform이 `random_password`로 생성해
Secrets Manager에 저장하고, ECS 태스크 정의가 ARN을 참조해 환경 변수로 주입합니다.

### 스키마는 Flyway가 소유한다

`spring.jpa.hibernate.ddl-auto: none`입니다. Hibernate가 테이블을 만들지 않고
`db/migration`의 SQL이 유일한 스키마 원본입니다.

```
V1__create_users_table.sql       V5__seed_products.sql
V2__create_categories_table.sql  V6__create_cart_tables.sql
V3__seed_categories.sql          V7__create_order_tables.sql
V4__create_products_table.sql
```

인프라를 재구축해 DB가 새로 만들어져도 애플리케이션이 기동하면서 스키마와 시드
데이터(카테고리 5그룹, 상품 16건)를 자동으로 복원합니다. 별도 초기화 작업이 없습니다.

### N+1과 지연 로딩 — 조회 시점에 필요한 것을 다 가져온다

`spring.jpa.open-in-view: false`입니다. 요청이 컨트롤러를 벗어나면 영속성 컨텍스트가
닫히므로, DTO로 변환하는 시점에 초기화되지 않은 프록시를 건드리면 예외가 납니다.
그래서 조회 쿼리에서 필요한 연관관계를 **명시적으로 함께 가져옵니다.**

```java
SELECT p FROM Product p
JOIN FETCH p.category c
LEFT JOIN FETCH c.parent pc
WHERE ...
```

- 상품 → 카테고리 → 상위 카테고리를 한 번의 쿼리로 로딩
- 상위 카테고리는 `LEFT JOIN`. 내부 조인으로 처리하면 상위가 없는 최상위 카테고리의
  상품이 조건과 무관하게 결과에서 누락됩니다
- 페이징과 함께 쓰므로 `countQuery`를 명시

`ProductService`에는 `@Transactional(readOnly = true)`를 두어 DTO 변환까지 하나의
세션 안에서 끝나도록 했습니다.

### 장바구니와 주문 — 스냅샷 가격

주문 항목은 상품 테이블을 참조하지 않고 **주문 시점의 이름과 가격을 복사해 저장**합니다.
나중에 상품 가격이 바뀌거나 상품이 삭제되어도 과거 주문 내역은 그대로 유지됩니다.

```java
new OrderItem(order.getId(), product.getId(), product.getName(),
              product.getPrice(), item.getQuantity());
```

주문 생성은 `productIds`를 선택적으로 받습니다. 비어 있으면 장바구니 전체를,
지정하면 해당 항목만 주문하고 **주문된 항목만** 장바구니에서 제거합니다.
장바구니에 없는 id가 섞여 오면 무시하지 않고 `400`으로 거절합니다. 오래된
체크아웃 화면이 표시한 것보다 적은 주문이 조용히 들어가는 것을 막기 위해서입니다.

`CartItem`은 `product_id`를 FK 컬럼으로만 들고 JPA 연관관계를 맺지 않습니다.
장바구니를 그릴 때 필요한 상품 정보는 `ProductRepository.findAllById`로 한 번에
조회합니다.

### 예외 처리 — 서버 오류가 401로 위장되지 않도록

`GlobalExceptionHandler`가 `ApiException`, `MethodArgumentNotValidException`,
그리고 **최종 fallback으로 `Exception`**을 처리합니다.

마지막 핸들러가 없으면 예상치 못한 예외가 디스패처를 벗어나고, 서블릿 컨테이너가
`/error`로 재디스패치하며, 시큐리티 필터 체인이 그 익명 요청을 거절해서
**서버 버그가 클라이언트에게 401 인증 오류로 보입니다.** 실제로 이 프로젝트에서
`LazyInitializationException`이 그렇게 위장된 적이 있어, fallback 핸들러와
`/error` permitAll을 함께 넣었습니다.

---

## 로컬 실행

MySQL 8.0이 필요합니다. `docker-compose.yml`이 포함되어 있습니다.

```bash
docker compose up -d
./gradlew bootRun
```

기본 프로필은 `local`이며 `application-local.yml`의 설정을 사용합니다
(포트 8080, `localhost:3306`, 개발용 JWT 키). Flyway가 기동 시 스키마를 생성합니다.

프론트엔드를 별도 정적 서버로 띄워 붙이려면 `LocalCorsConfig`가 `localhost` 출처를
허용합니다. **이 설정은 `@Profile("local")`이라 운영(`prod`)에서는 등록되지 않습니다.**

### 테스트

```bash
./gradlew test
```

컨트롤러 통합 테스트는 Testcontainers로 실제 MySQL 8.0 컨테이너를 띄우고 Flyway
마이그레이션을 그대로 실행합니다. 인메모리 DB로 대체하지 않으므로 **운영과 같은
방언·제약조건에서 검증**되고, 시드 마이그레이션이 프론트엔드 계약과 맞는지도
함께 확인됩니다. (Docker 필요)

---

## 배포

`main` 브랜치 푸시 시 GitHub Actions가 다음을 수행합니다.

```
test  ──► deploy
 │         ├─ Docker 이미지 빌드 (멀티 스테이지: JDK 빌드 → JRE 런타임)
 │         ├─ ECR 푸시 (태그 = 커밋 SHA)
 │         ├─ 기존 태스크 정의를 받아 image 필드만 교체 → 새 리비전 등록
 │         └─ ECS 서비스 업데이트 후 안정화 대기
 └─ Pull Request에서는 test만 실행 (deploy는 push 이벤트에서만)
```

- **이미지 태그는 커밋 SHA**입니다. ECR이 `IMMUTABLE` 모드라 같은 태그를 덮어쓸 수
  없고, 어떤 커밋이 배포되었는지 태그만 보고 알 수 있습니다. 같은 워크플로 실행을
  재시도하면 태그가 이미 존재해 실패하므로, 재배포가 필요하면 새 커밋을 만듭니다.
- **태스크 정의 전체가 아니라 `image` 필드만** 교체합니다. 환경 변수와 시크릿 ARN,
  로그 설정은 Terraform이 정의한 값을 그대로 이어받습니다.

### 환경 변수 (운영)

ECS 태스크 정의가 주입합니다. 애플리케이션은 값을 저장하지 않습니다.

| 변수 | 출처 |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | 태스크 정의 상수 |
| `DB_HOST` | Aurora 클러스터 엔드포인트 (Terraform 출력) |
| `DB_NAME`, `DB_USERNAME` | Terraform 변수 |
| `DB_PASSWORD` | Secrets Manager — RDS 관리형 마스터 시크릿의 `password` 필드 |
| `JWT_SECRET` | Secrets Manager — Terraform이 생성한 값 |

기본 프로필이 `local`이므로 **`SPRING_PROFILES_ACTIVE=prod`가 빠지면 컨테이너가
`localhost:3306`에 접속을 시도하다 실패합니다.**

---

## 알려진 한계

- **결제 연동 없음** — 주문은 생성되지만 실제 PG 결제는 이루어지지 않습니다.
  `paymentMethod`는 `card` / `transfer` 문자열만 검증합니다.
- **상품평·문의 API 없음** — 상세 페이지의 해당 탭은 프론트엔드에서 생성한
  데모 데이터입니다.
- **재고 관리 없음** — 주문 시 수량을 차감하지 않습니다.
- **리프레시 토큰 없음** — 액세스 토큰 만료 시 재로그인이 필요합니다.
- **DB 읽기 분산 미적용** — Aurora 리더 엔드포인트가 있으나 조회 트래픽을 분리하지
  않습니다. `@Transactional(readOnly = true)` 경계는 이미 잡혀 있어 라우팅
  데이터소스를 붙이면 확장할 수 있습니다.
