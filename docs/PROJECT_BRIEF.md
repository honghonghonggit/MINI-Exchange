# 프로젝트 기획서: 미니 거래소 매칭엔진

## 목적
한국 자본시장 매매 인프라(코스콤/KRX 등)에서 사용되는 주문 매칭의 핵심 원리를 직접 구현해 이해하고, 백엔드 시스템 설계 역량을 보여주는 포트폴리오로 활용한다.
실거래/실자금은 다루지 않는 교육·시뮬레이션 목적의 프로젝트임을 README에 명확히 표기한다.

## 전체 흐름
React(프론트엔드) ↔ REST/WebSocket ↔ Spring Boot(API 서버) → 매칭엔진(오더북, 인메모리) → PostgreSQL(주문/체결/이벤트 영속화)

## 기술 스택
- Backend: Java 21, Spring Boot, Spring WebSocket, JPA, PostgreSQL
- Frontend: React, TypeScript, Vite, Recharts
- Infra: Docker, GitHub Actions, Render 또는 Railway
- Redis는 필수 아님 — 다중 인스턴스로 확장할 때만 고려할 Phase3급 옵션

## DB 스키마
- orders (주문 원장): id, client_order_id, side, price, quantity, remaining_quantity, type, status, created_at, updated_at
- executions (체결 내역): id, buy_order_id, sell_order_id, price, quantity, executed_at
- event_logs (이벤트 로그): id, event_type, payload, timestamp

## API 명세
- POST /orders — 주문 제출
- DELETE /orders/{id} — 주문 취소
- GET /orderbook — 현재 오더북 스냅샷
- GET /trades — 최근 체결 내역
- GET /metrics — 시스템 메트릭(매칭 레이턴시, TPS, 미체결 주문 수)
- GET /events — 이벤트 로그 조회 (Phase2)

## 단계별 범위

### Phase 1 — MVP (가장 먼저 끝낼 것)
- 주문 데이터 모델 + 오더북 자료구조 (가격대별 정렬 + 동일 가격 내 FIFO)
- 매칭 로직: 가격-시간 우선원칙, limit/market 주문, 부분체결 처리
- REST API: 주문 제출/취소, 오더북 조회
- 웹소켓으로 오더북 변경 실시간 전송
- 프론트엔드: 호가창(매수/매도 depth)만
- 가상 주문 생성기(랜덤워크 기반)로 데모가 항상 살아있게 유지

### Phase 2 — 차별화 기능 (Phase1 완료 후 진행)
- 체결 테이프(실시간 거래 내역), 간단한 가격 차트(Recharts)
- 이벤트 로그: 모든 주문/체결 이벤트를 event_logs 테이블에 기록 (사후 복원 가능하게)
- 시스템 메트릭 패널: 매칭 레이턴시, TPS, 미체결 주문 수를 화면에 표시
- 성능 테스트: 1만 / 10만 / 50만 건 주문 처리 시 평균·최대 매칭 시간, TPS를 측정하고 결과를 README에 정리

### Phase 3 — 스트레치 (시간 남으면)
- 변동성완화장치(VI) 시뮬레이션: 단기간 급격한 가격 변동 시 거래 일시 정지
- 다양한 가상 투자자 유형 (노이즈 트레이더, 모멘텀 트레이더, 평균회귀 트레이더, 대형 투자자)
- 이벤트 리플레이 기능 (기록된 이벤트를 처음부터 재생해 체결 과정 복원)

## 동시성 처리 (반드시 설계 결정으로 문서화)
- 여러 주문이 동시에 들어올 때의 동기화 전략 선택 (예: lock 기반 vs concurrent 자료구조)과 그 이유
- race condition 방지 방법
- 이 부분은 README의 "설계 결정" 섹션에서 가장 비중 있게 다룰 것

## 개발 순서 권장
1. 아키텍처와 DB 스키마를 Claude Code와 plan mode로 먼저 합의
2. 오더북 + 매칭엔진 핵심 로직을 단위 테스트와 함께 구현 (TDD 권장) — Phase1
3. 기능이 끝날 때마다 "왜 이렇게 설계했는지"를 README에 바로 기록
4. REST API → 웹소켓 → 프론트엔드 → 배포 순서로 확장
5. Phase1이 안정적으로 동작한 뒤에만 Phase2로 진행

## README 구조
1. 프로젝트 소개 (한두 줄)
2. 아키텍처 다이어그램
3. 데모 GIF
4. 설계 결정 (자료구조 선택 이유, 동시성 전략, race condition 방지, 테이블 분리 이유) — 가장 비중 있게 다룰 부분
5. 기술 스택
6. 실행 방법
7. API 명세
8. 회고

## 배포
- 백엔드(매칭엔진+API+웹소켓): Render / Railway / Fly.io 등 무료~저가 클라우드에 상시 구동
- 프론트엔드: 백엔드와 같이 서빙하거나 별도 정적 호스팅
- 실거래/실자금 없음 → 법적 이슈 없음. 단 "시뮬레이터"임을 README와 화면에 명시

## 포트폴리오 활용
- README 구조(위 참고)를 따라 "기능 나열"보다 "설계 결정"이 먼저 보이게 작성
- 성능 테스트 결과(벤치마크 그래프)를 README에 포함해 "거래 시스템은 성능이 중요하다"는 인식을 보여줄 것
- 면접 대비: "왜 이 자료구조를 썼는지", "동시 주문 처리는 어떻게 했는지", "테이블을 왜 3개로 분리했는지", "이벤트 로그를 왜 분리했는지" 질문에 답할 수 있도록 설계 과정을 기록해둘 것
