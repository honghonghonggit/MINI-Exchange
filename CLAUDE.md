# 프로젝트: 미니 거래소 매칭엔진

가격-시간 우선 매매 체결의 핵심 원리를 이해하기 위해 직접 구현하는 거래소 시뮬레이터.
전체 기획/스코프/스택은 @docs/PROJECT_BRIEF.md 참고.

## 기술 스택
- Backend: Java 17, Spring Boot, Spring WebSocket, JPA, PostgreSQL (Redis는 필수 아님, Phase3에서 다중 인스턴스 확장 시에만 고려)
- Frontend: React, TypeScript, Vite, Recharts
- Infra: Docker, GitHub Actions, Render 또는 Railway

## 빌드 / 테스트
- Backend: `./gradlew test`, `./gradlew bootRun` (스캐폴딩 후 실제 명령어로 검증할 것)
- Frontend: `npm run dev`, `npm run build`

## 개발 순서
1. 아키텍처/DB 스키마를 먼저 plan mode로 합의
2. 오더북 + 매칭엔진 핵심 로직부터 단위 테스트와 함께 구현 (TDD 권장) — Phase1
3. 기능 하나 끝날 때마다 "왜 이렇게 설계했는지"를 README 설계 결정 섹션에 바로 기록
4. REST API → 웹소켓 → 프론트엔드 → 배포 순으로 진행
5. Phase1이 안정적으로 끝나기 전에는 Phase2/3 기능에 손대지 않는다

## 핵심 원칙
- 범위는 Phase1(MVP) → Phase2(차별화 기능) → Phase3(스트레치) 순서로 단계적으로 확장한다.
- README는 "기술 스택" 나열보다 "설계 결정"(자료구조 선택, 동시성 전략, race condition 방지, 테이블 분리 이유)을 더 비중 있게 다룬다. 다만 맨 앞에는 한 줄 소개 + 아키텍처 다이어그램 + 데모 GIF를 먼저 배치한다.
- 모든 주문/체결 이벤트는 event_logs 테이블에 타임스탬프와 함께 기록한다.
- 시스템 메트릭(매칭 레이턴시, TPS, 미체결 주문 수)을 화면에 노출하고, 1만/10만/50만 건 주문 처리 성능 테스트 결과를 README에 포함한다.
- 코스콤/KRX가 "만든 시스템을 그대로 재현했다"는 단정적 표현은 쓰지 않는다.
- 큰 설계 변경 전에는 plan mode로 먼저 합의받는다.
- 커밋 메시지에 "Co-Authored-By: Claude" 등 AI attribution을 넣지 않는다. (settings.json `includeCoAuthoredBy: false`)

## DB 스키마 (초안)
- orders: id, client_order_id, side, price, quantity, remaining_quantity, type, status, created_at, updated_at
- executions: id, buy_order_id(FK), sell_order_id(FK), price, quantity, executed_at
- event_logs: id, event_type, payload(JSON), timestamp

## API 명세 (초안)
- POST /orders
- DELETE /orders/{id}
- GET /orderbook
- GET /trades
- GET /metrics
- GET /events (Phase2)

## 폴더 구조
(스캐폴딩 이후 채울 것)
