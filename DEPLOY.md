# 배포 가이드 (Render + Neon)

구성: **백엔드**(Render Web Service, Docker) + **프론트엔드**(Render Static Site) + **DB**(Neon 영구 무료 Postgres).
DB만 Render 바깥(Neon)에 두고, 연결 문자열은 환경변수 `DATABASE_URL`로 주입한다.

```
[브라우저] → mini-exchange-web (정적, Render) → mini-exchange-api (Docker, Render) → Neon Postgres
                                  REST /metrics, WS /ws
```

---

## 1. Neon 준비 (DB)

1. https://neon.tech 에서 프로젝트 생성 → 데이터베이스 생성(기본 `neondb`).
2. **Connection Details**에서 연결 문자열 확보. 두 형식 중 하나를 쓴다.
   - 표준형: `postgresql://OWNER:PASSWORD@ep-xxx.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`
   - 이 프로젝트가 기대하는 **JDBC 형식**으로 변환(자격증명을 URL에 포함):
     ```
     jdbc:postgresql://ep-xxx.ap-southeast-1.aws.neon.tech/neondb?user=OWNER&password=PASSWORD&sslmode=require
     ```
   - 변환 규칙: 앞에 `jdbc:` 를 붙이고, `OWNER:PASSWORD@` 부분을 빼서 `?user=OWNER&password=PASSWORD&` 쿼리 파라미터로 옮긴다. `sslmode=require`는 **반드시 유지**(Neon은 SSL 필수).
3. (권장) pooled 엔드포인트 사용: 호스트가 `ep-xxx-pooler...` 형태. 무료 티어 동시연결에 유리.

---

## 2. 배포 방법 A — Blueprint(`render.yaml`) 한 번에

1. 이 레포를 GitHub에 푸시.
2. Render 대시보드 → **New → Blueprint** → 레포 선택. `render.yaml`을 읽어 두 서비스를 생성한다.
3. 생성 과정에서 `sync:false` 환경변수 입력을 요구한다:
   - `mini-exchange-api` → **DATABASE_URL** = 위 JDBC 문자열
   - `mini-exchange-web` → **VITE_API_BASE_URL** = (백엔드 배포 후 받은 주소, 아래 4번 참고)
4. 백엔드가 먼저 떠서 URL(`https://mini-exchange-api.onrender.com`)이 정해지면, 그 값을 프론트 `VITE_API_BASE_URL`에 넣고 프론트를 **Manual Deploy**(재빌드). Vite는 빌드 시점에 값을 박으므로 재배포가 필요하다.

## 2. 배포 방법 B — 서비스 수동 생성

Blueprint 없이 만들 때:

**백엔드** (New → Web Service)
- Runtime: Docker, Dockerfile Path: `backend/Dockerfile`, Docker Context: `backend`
- Health Check Path: `/metrics`
- 환경변수: `SPRING_PROFILES_ACTIVE=prod`, `DATABASE_URL=<Neon JDBC>`, `JAVA_OPTS=-XX:MaxRAMPercentage=75`

**프론트엔드** (New → Static Site)
- Root Directory: `frontend`
- Build Command: `npm install && npm run build`
- Publish Directory: `dist`
- Rewrite Rule: `/* → /index.html` (Rewrite)
- 환경변수: `VITE_API_BASE_URL=<백엔드 주소>`

---

## 3. 필요한 환경변수 정리

### 백엔드 (`mini-exchange-api`)

| 변수 | 필수 | 예시 / 값 | 설명 |
|------|------|-----------|------|
| `SPRING_PROFILES_ACTIVE` | ✅ | `prod` | PostgreSQL 프로파일 활성화 |
| `DATABASE_URL` | ✅ | `jdbc:postgresql://ep-xxx.neon.tech/neondb?user=OWNER&password=PW&sslmode=require` | **Neon 연결 문자열**(자격증명·sslmode 포함) |
| `JAVA_OPTS` | ⬜ | `-XX:MaxRAMPercentage=75` | 컨테이너 메모리에 맞춘 힙 옵션 |
| `PORT` | (자동) | `10000` | Render가 자동 주입. 앱이 이 포트로 바인딩(`server.port`) |
| `SPRING_DATASOURCE_USERNAME` | ⬜ | `OWNER` | URL에 자격증명을 안 넣을 때만 사용(대안) |
| `SPRING_DATASOURCE_PASSWORD` | ⬜ | `PW` | 위와 함께 사용하는 대안 |

> `DATABASE_URL` 한 줄에 `user`/`password`를 넣었다면 `SPRING_DATASOURCE_USERNAME/PASSWORD`는 설정하지 않는다.

### 프론트엔드 (`mini-exchange-web`)

| 변수 | 필수 | 예시 / 값 | 설명 |
|------|------|-----------|------|
| `VITE_API_BASE_URL` | ✅ | `https://mini-exchange-api.onrender.com` | 백엔드 절대 주소. **빌드 시점**에 주입되므로 값 변경 후 재배포 필요. 끝 슬래시 없음 |

---

## 4. 배포 순서 체크리스트

1. [ ] Neon 프로젝트/DB 생성 → JDBC 연결 문자열 확보
2. [ ] GitHub에 푸시
3. [ ] 백엔드 배포 (`DATABASE_URL`, `SPRING_PROFILES_ACTIVE=prod` 입력) → URL 확인
4. [ ] `/metrics` 200 확인, 로그에 Hibernate `update`로 테이블 생성 확인
5. [ ] 프론트 `VITE_API_BASE_URL`에 백엔드 URL 입력 → 프론트 재배포
6. [ ] 프론트 접속 → 호가창/체결테이프/차트/메트릭이 실시간으로 도는지 확인

---

## 주의사항

- **Render free 플랜**은 15분 무활동 시 슬립 → 첫 요청에 수십 초 콜드스타트. 가상 주문 생성기가 계속 돌아도 외부 트래픽이 없으면 슬립될 수 있다(포트폴리오 데모 한정 트레이드오프).
- 시뮬레이터는 prod에서도 켜져 있다(`simulator.enabled=true`). DB에 주문/체결/이벤트가 계속 쌓이므로 Neon 무료 용량(0.5GB) 소진에 유의. 필요 시 `simulator.interval-ms`를 늘리거나 주기적으로 테이블 정리.
- CORS: 백엔드는 데모 목적상 모든 오리진 허용(`@CrossOrigin(origins="*")`, WS `setAllowedOriginPatterns("*")`). 공개 데모라 허용 범위를 넓게 둔 것이며, 실서비스라면 프론트 도메인으로 좁혀야 한다.
