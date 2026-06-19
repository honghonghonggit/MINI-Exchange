# 미니 거래소 매칭엔진

> 교육·시뮬레이션 목적의 프로젝트입니다. 실거래·실자금을 다루지 않으며, KRX/코스콤 시스템을 그대로 재현한 것이 아닙니다.

한국 자본시장의 가격-시간 우선 매매 체결 원리를 직접 구현한 거래소 시뮬레이터.

---

## 아키텍처 다이어그램

```
[React Frontend]
      │ WebSocket (STOMP)        │ REST
      ▼                          ▼
[Spring Boot API Server]
      │
      ├─ REST Thread ──► orderRepository.save() ──► PostgreSQL
      │        │
      │        └──► commandQueue.offer(order)
      │                          │
      │              [Matching Thread (단 1개)]
      │                          │
      │                    OrderBook.submit()
      │                          │
      │              ┌───────────┴──────────┐
      │         WS 브로드캐스트          persistQueue
      │         (즉시)                       │
      │                          [Persist Thread]
      │                                      │
      │                               Execution 저장
      └──────────────────────────────► PostgreSQL
```

---

## 설계 결정

### 1. 오더북 자료구조: `TreeMap<Long, ArrayDeque<Order>>`

**선택 이유**

| 요구사항 | 자료구조 | 복잡도 |
|---|---|---|
| 가격 우선: best bid/ask 즉시 접근 | TreeMap (정렬된 키) | O(log n) |
| 시간 우선: 동일 가격 내 FIFO | ArrayDeque | O(1) enqueue/dequeue |
| 취소: 특정 주문 O(1) 조회 | HashMap orderIndex | O(1) lookup |

- **매수(bid)**: `TreeMap(역순)` → `firstKey()` = 가장 높은 가격 = best bid
- **매도(ask)**: `TreeMap(정순)` → `firstKey()` = 가장 낮은 가격 = best ask
- **가격 표현**: `long` (정수)로 저장하여 부동소수점 오차를 원천 차단

**트레이드오프**

취소 시 `ArrayDeque.remove(order)`는 레벨 내 O(n). 실서비스라면 이중 연결 리스트로 O(1)로 낮출 수 있지만, 단일 종목 시뮬레이터에서 레벨당 주문 수는 소수이므로 실질적 차이 없음.

---

### 2. 동시성 전략: 단일 매칭 스레드 + `LinkedBlockingQueue`

```
외부 스레드 (여럿) ──► LinkedBlockingQueue<OrderCommand>
                                   │
                      [matchingThread (단 1개)] ──► OrderBook (lock-free)
```

**선택 이유**

- `OrderBook`을 단 하나의 스레드만 읽고 쓰므로, **lock 없이 race condition을 구조적으로 제거**
- LMAX Disruptor, 실거래소 매칭엔진이 채택하는 패턴과 같은 원리
- 대안인 `ReentrantLock`이나 `synchronized`보다 단순하고 추론하기 쉬움
- 단일 종목 시뮬레이터 규모에서 단일 스레드 처리량은 충분

**race condition 방지 근거**

외부에서 OrderBook에 직접 접근하는 경로가 없음. REST 스레드는 반드시 커맨드 큐를 통해서만 간접 접근. Java의 `LinkedBlockingQueue`는 생산자-소비자 happens-before를 보장하므로 별도 volatile/synchronized 불필요.

---

### 3. DB 저장 비동기 분리

**흐름**

1. REST 스레드: `orders` 테이블에 저장 (sync) → DB id 확보 → 매칭 큐에 제출
2. 매칭 스레드: 매칭 처리 → WebSocket 즉시 브로드캐스트 → `persistQueue`에 결과 추가
3. Persist 스레드: `executions` 저장 + `orders` 상태 업데이트

**목적**: 매칭 레이턴시에서 DB I/O 제거 → 매칭 처리량과 레이턴시가 DB 성능에 독립

**알려진 트레이드오프**: 서버 비정상 종료 시 체결은 완료됐지만 DB 미저장된 실행 내역이 유실될 수 있음. 시뮬레이터 목적이므로 허용.

---

### 4. 오더북 스냅샷 안전 발행: `volatile` + 불변 record

REST 스레드가 `GET /orderbook`을 호출할 때, 매칭 스레드가 OrderBook을 수정 중일 수 있음.

**해결책**

```java
// MatchingEngine
private volatile OrderBookSnapshot lastSnapshot;

// 매칭 스레드: 매 명령 처리 후 새 불변 스냅샷으로 교체
lastSnapshot = orderBook.snapshot();

// REST 스레드: volatile 읽기 → 항상 완전히 구성된 스냅샷 반환
public OrderBookSnapshot snapshot() { return lastSnapshot; }
```

`OrderBookSnapshot`은 Java record(불변). volatile 쓰기 → volatile 읽기 사이에 happens-before 관계가 성립하여, REST 스레드는 항상 일관된 상태를 봄. 스냅샷은 수 밀리초 지연될 수 있으나 시뮬레이터에서 허용.

---

### 5. DB 스키마 테이블 분리 이유

| 테이블 | 역할 | 분리 이유 |
|---|---|---|
| `orders` | 주문 원장 (현재 상태) | 주문 수명주기(상태 전이)를 추적 |
| `executions` | 체결 내역 (불변 이벤트) | 주문 상태와 독립적으로 체결 감사 가능 |
| `event_logs` | 모든 이벤트 로그 (Phase 2) | 사후 재현·리플레이를 위한 이벤트 소싱 기반 |

orders와 executions를 합치면 단순해지지만, "이 주문이 몇 번에 나눠 체결됐나"를 추적하려면 별도 테이블이 필수. Phase 2의 이벤트 리플레이도 executions가 분리되어야 가능.

---

## 기술 스택

- **Backend**: Java 17, Spring Boot 3.5, Spring WebSocket (STOMP), JPA, PostgreSQL
- **Frontend**: React, TypeScript, Vite, Recharts *(Phase 1: 호가창만)*
- **Infra**: Docker, GitHub Actions, Render / Railway

---

## 실행 방법

### 1. Docker Compose (권장)

PostgreSQL + 백엔드를 한 번에 실행합니다.

```bash
# 백엔드 빌드 후 컨테이너 실행
cd backend
./gradlew bootJar
cd ..
docker-compose up --build
```

프론트엔드는 별도로 실행합니다.

```bash
cd frontend
npm install
npm run dev
# http://localhost:5173 에서 확인
```

### 2. 로컬 개발 (H2 인메모리)

PostgreSQL 없이 H2로 바로 실행합니다.

```bash
# 백엔드 (포트 8080, H2 자동 스키마 생성)
cd backend
./gradlew bootRun

# 프론트엔드 (포트 5173, 백엔드로 프록시)
cd frontend
npm install
npm run dev
```

### 3. 테스트

```bash
cd backend
./gradlew test
```

> **Note:** 시뮬레이터가 자동으로 랜덤 주문을 500ms 간격으로 생성합니다.  
> 시뮬레이터를 끄려면 `application.yml`에서 `simulator.enabled: false`로 설정하세요.

---

## 성능 테스트 결과

> 환경: Windows 11, JDK 17, `LinkedBlockingQueue` 기반 단일 매칭 스레드  
> 측정 대상: `offer()` 레이턴시(주문 큐 삽입) + 벽시계 TPS (큐 소진 대기 3초 포함)  
> 순수 매칭 처리량은 offer() 제출 완료 후 큐가 즉시 소진되므로 실제 처리량은 표보다 높음

| 주문 수 | TPS (벽시계) | offer() 평균 | offer() p99 | offer() p99.9 | 체결 건수 |
|---|---|---|---|---|---|
| **10,000** | 3,289 건/초 | 0.52 µs | 2.10 µs | 37.70 µs | 4,400 |
| **100,000** | 32,025 건/초 | 0.16 µs | 0.50 µs | 3.70 µs | 34,168 |
| **500,000** | 144,871 건/초 | 0.09 µs | 0.40 µs | 0.70 µs | 39,916 |

**해석:**
- offer() p99 < 3µs — 단일 매칭 스레드 구조에서 주문 제출이 매우 낮은 레이턴시로 논블로킹
- TPS는 "벽시계 / 주문 수"이며 3초 큐 소진 대기가 포함됨. 500K 기준 실제 offer() 구간(≈0.45초)만 보면 약 110만 건/초
- JIT 워밍업 후 배치 크기가 클수록 p99.9가 낮아지는 현상은 JVM 캐시 효율 향상 때문

---

## API 명세

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/orders` | 주문 제출 |
| `DELETE` | `/orders/{id}` | 주문 취소 |
| `GET` | `/orderbook` | 오더북 스냅샷 |
| `GET` | `/trades` | 최근 체결 내역 (최대 50건) |
| `GET` | `/metrics` | 매칭 레이턴시·TPS·미체결 주문 수 |
| `WS` | `/topic/orderbook` | 오더북 실시간 업데이트 |
| `WS` | `/topic/trades` | 체결 이벤트 실시간 스트림 |

---

## 회고

*(완성 후 작성 예정)*
