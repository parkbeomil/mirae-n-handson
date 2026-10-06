# 장애 원인 분석 — a-connection-pool

## 요약

- **무슨 일**: 2026-09-16 14:37 ~ 14:52(KST) 학급 리포트 API 가 DB 커넥션 풀(5개)을 모두 쥐었다. 사용자 영향은 14:40:52 ~ 14:51:47 이고 5xx 186건이다(500 49 · 502 82 · 504 55). 리포트와 무관한 API 의 5xx 가 94건이다.
- **직접 원인**: `ReportService.buildClassReport` 가 `@Transactional` 안에서 서명 계산(SHA-256 200,000회)까지 하며 커넥션을 쥐었다. 이상 구간 리포트 134건이 모두 15.5 ~ 21.5초 점유했다.
- **배경 원인**: 풀 5개 · 대기 3초 설정에 리포트를 격리하는 장치가 없었다. 촉발 요인(리포트 호출 6건 → 171건, 느린 쿼리 1건)의 기여도는 가르지 못했다.
- **확신 수준: 중간**. 점유 구조는 로그와 코드가 줄 단위로 맞는다. 점유가 15.5 ~ 21.5초가 된 이유와 촉발 요인은 판단 불가다.
- **조치**: 서명을 트랜잭션 밖으로 옮긴다(6절). 14:37:52 의 누수 경고가 사용자 영향 3분 전 신호였다(8절).

> 대상 로그: `incident-logs/a-connection-pool/` (4개 파일).
> 1절은 로그에서 센 건수와 시각만 적었다. 원인 추정은 3절부터다. IP 는 마스킹했다(서버 `API-1` · `API-2`, 교사 단말 `교사단말A` ~ `교사단말D`).
> 조사 방식: 파일을 통째로 읽지 않고 `wc` · `head` · `tail` · `grep -c` · `grep -n` · `awk` 집계로만 확인했다.

## 1. 수집 범위

### 1-1. 파일별 기록 기간 · 줄 수 · 시각 형식 · 타임존

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 | 타임존 |
|---|---|---|---|---|
| `app.log` | 5,371 (시각이 있는 줄 1,927 + 스택 트레이스 이어지는 줄 3,444) | 2026-09-16 06:43:56.753 ~ 15:29:51.760 | `2026-09-16T06:43:56.753+09:00` (ISO-8601, 밀리초) | KST. 오프셋 `+09:00`이 줄마다 붙어 있다 |
| `nginx-access.log` | 21,086 | 2026-09-16 06:43:56 ~ 15:29:59 | `[16/Sep/2026:06:43:56 +0900]` | KST. 오프셋 `+0900`이 21,086줄 모두에 붙어 있다 |
| `nginx-error.log` | 168 | 2026-09-16 09:12:03 ~ 14:51:47 | `2026/09/16 14:51:47` | 표기 없음. 같은 요청이 `nginx-access.log` 와 같은 초에 찍혀 KST 로 판단한다(아래 근거) |
| `mariadb-slow.log` | 21 (슬로우 쿼리 3건) | 3건이 KST 09:14:09 · 11:02:42 · 14:37:52 | `# Time: 260916  5:37:52` (`YYMMDD H:MM:SS`) + `SET timestamp=<epoch>` | **UTC**. KST 로 바꾸려면 +9시간 한다 |

- `app.log` 로그 레벨 분포: INFO 1,626 · WARN 203 · ERROR 98.
- `nginx-access.log` 상태 코드 분포: 200 19,605 · 400 25 · 404 1,250 · 409 20 · 500 49 · 502 82 · 504 55.
- `nginx-error.log` 레벨 분포: error 137 · warn 28 · info 3.

### 1-2. KST 기준 변환 규칙

세 파일은 KST 그대로 쓰고, 슬로우 로그만 변환한다.

| 파일 | 변환 규칙 |
|---|---|
| `app.log` · `nginx-access.log` | 변환 없음 |
| `nginx-error.log` | 변환 없음(KST 로 간주) |
| `mariadb-slow.log` | `# Time:` 값에 +9시간. 예: `260916  5:37:52` → 14:37:52 KST |

근거는 두 가지다.

- `nginx-error.log:167` 의 `14:51:03` (`/api/classes/3/report`, 클라이언트 교사단말A)과 `nginx-access.log` 의 같은 클라이언트 · 같은 경로 504 가 `14:51:03 +0900` 으로 같은 초에 있다. `nginx-error.log:168` 의 `14:51:47` 도 같은 방식으로 일치한다. 그래서 error 로그에는 오프셋이 없어도 KST 다.
- `mariadb-slow.log:20` 의 `SET timestamp=1789537062` 는 UTC 2026-09-16 05:37:42 이고 KST 로는 14:37:42 다. 같은 항목의 `mariadb-slow.log:15` `# Time: 260916  5:37:52` 는 여기에 `Query_time` 9.81초를 더한 값과 같다(05:37:42 + 9.8초). 즉 `SET timestamp` 는 쿼리 시작, `# Time:` 은 쿼리 종료 시각이고 둘 다 UTC 다.
- 슬로우 로그의 나머지 두 건도 같은 규칙으로 바꾼다. `0:14:09` → 09:14:09 KST, `2:02:42` → 11:02:42 KST.

- 각 시각은 사건이 **끝났거나 감지된** 시각이지 시작 시각이 아니다. `nginx-access.log` 는 응답이 끝날 때, `nginx-error.log` 의 "upstream timed out" 은 타임아웃이 발동할 때 찍힌다(access 로그 필드에는 `request_time` 이 없다). `app.log` 의 "Connection is not available" ERROR 는 요청이 시작된 지 3000ms 뒤에 찍힌다(147줄 모두 `request timed out after 3000ms`). `ProxyLeakTask` WARN 은 연결을 잡은 시점보다 늦게 나온다. 그래서 아래 "첫 줄" 시각들은 실제 시작보다 늦다.

### 1-3. 분 단위 이상 건수와 평소 수준

**평소 수준.** 로그 시작(06:43)부터 이상 구간 직전(14:36)까지 약 473분의 값이다.

| 지표 | 평소 수준 | 비고 |
|---|---|---|
| `app.log` WARN + ERROR | 16건 / 473분 (분당 약 0.03건). ERROR 0건 | 16건 전부 `GlobalExceptionHandler` 의 "state conflict" 409 WARN 이다 |
| `nginx-access.log` 5xx | 0건 | 500 · 502 · 504 가 14:40 이전에는 한 건도 없다 |
| `nginx-access.log` 4xx | 분당 약 2.5건 | 대부분 404 로 상시 존재하는 잡음이다 |
| `nginx-error.log` `[error]` | 0건 | 14:41 이전 줄은 warn · info 5건뿐이다 |
| `mariadb-slow.log` | 2건 (09:14:09 KST 2.32초, 11:02:42 KST 2.04초) | 둘 다 2초대 |
| 분당 요청 수 | 약 45건 (14:00 ~ 14:36 구간 분당 32 ~ 56건) | 14:37 ~ 14:52 는 689건 / 16분 = 분당 약 43건으로 달라지지 않는다 |

**이상 구간 분 단위 건수** (14:36 ~ 14:53, KST). 평소와 달라지는 줄만 센 값이다.

| 분 | `app.log` WARN+ERROR | 그중 `ProxyLeakTask` WARN | 그중 "Connection is not available" ERROR | `nginx-access.log` 5xx | `nginx-error.log` `[error]` | `nginx-error.log` warn · info |
|---|---|---|---|---|---|---|
| 14:36 | 0 | 0 | 0 | 0 | 0 | 0 |
| **14:37** | **1** | 1 | 0 | 0 | 0 | 0 |
| 14:38 | 2 | 2 | 0 | 0 | 0 | 0 |
| 14:39 | 3 | 3 | 0 | 0 | 0 | 0 |
| **14:40** | **17** | 14 | 1 | **1** | 0 | 0 |
| 14:41 | 31 | 13 | 6 | 9 | 3 | 0 |
| 14:42 | 17 | 10 | 2 | 20 | 18 | 2 |
| 14:43 | 44 | 14 | 10 | 17 | 7 | 1 |
| 14:44 | 17 | 11 | 2 | 13 | 11 | 4 |
| 14:45 | 33 | 9 | 8 | 27 | 19 | 3 |
| 14:46 | 24 | 12 | 4 | 26 | 22 | 3 |
| 14:47 | 24 | 9 | 5 | 14 | 9 | 1 |
| 14:48 | 22 | 10 | 4 | 15 | 11 | 4 |
| 14:49 | 33 | 12 | 7 | 23 | 16 | 4 |
| 14:50 | 12 | 11 | 0 | 18 | 18 | 4 |
| **14:51** | **4** | 3 | 0 | **3** | 3 | 0 |
| 14:52 | 1 | 0 | 0 | 0 | 0 | 0 |
| 14:53 | 0 | 0 | 0 | 0 | 0 | 0 |

- `app.log` 14:52 의 1건은 `ProxyLeakTask` 가 아니라 평소에도 나오는 "state conflict" 409 WARN 이다(`app.log:5269`). 14:51:52 의 409 WARN 도 마찬가지다(`app.log:5268`).
- `app.log` 에서 14:53 이후 WARN · ERROR 는 15:29 까지 0건이다. `nginx-access.log` 5xx 도 14:51:47 이후 0건이다.
- 이상 구간(14:37 ~ 14:52) `app.log` 타임스탬프 줄은 589줄이고 그중 WARN + ERROR 가 285건이다. 평소 구간은 1,238줄 중 16건이다.

**파일별 합계와 시작 · 끝 시각** (KST)

| 파일 | 이상 건수 | 첫 줄 | 마지막 줄 |
|---|---|---|---|
| `app.log` — `ProxyLeakTask` WARN | 134건 | 14:37:52.199 (`app.log:1243`) | 14:51:49.148 (`app.log:5267`) |
| `app.log` — "Connection is not available" ERROR | 49건 | 14:40:52.319 (`app.log:1522`) | 14:49:51.552 |
| `app.log` — ERROR 전체 | 98건 | 14:40:52.319 | 14:49:51.553 |
| `nginx-access.log` — 5xx (500 49 · 502 82 · 504 55) | 186건 | 14:40:52 (`nginx-access.log:18918`) | 14:51:47 (`nginx-access.log:19381`) |
| `nginx-error.log` — `[error]` (no live upstreams 68 · upstream timed out 55 · connect() failed 14) | 137건 | 14:41:06 (`nginx-error.log:6`) | 14:51:47 (`nginx-error.log:168`) |
| `nginx-error.log` — warn "upstream server temporarily disabled" | 26건 | 14:42 | 14:50 |
| `mariadb-slow.log` | 14:37:52 KST 에 끝난 1건 (9.81초, `Rows_examined` 1,284,310, `submission` 테이블 조회) | 14:37:42 시작 / 14:37:52 종료 | — |

- 슬로우 로그는 3건뿐이라 범위를 정하는 기준으로는 쓰지 않고, 시각 대조용으로만 쓴다. 14:37:52 종료 시각이 `app.log:1243` 의 첫 `ProxyLeakTask` WARN(14:37:52.199)과 같은 초라는 점만 기록한다. 이것이 원인이라는 뜻은 아니다.
- 로그에 남은 학생 식별자 · 토큰은 이 문서에 옮기지 않았다(`nginx-error.log` 에 `Authorization: Bearer …` 가 포함된 줄이 있다).

### 1-4. 수집 범위 제안

**이상 구간(분 단위)**

| 구분 | 시각 (KST) | 근거 |
|---|---|---|
| 시작 | **14:37** (첫 징후 14:37:42 슬로우 쿼리 시작 · 14:37:52 `ProxyLeakTask` 첫 WARN) | `app.log` WARN+ERROR 가 평소 0.03건/분에서 14:37 에 1건, 14:38 에 2건, 14:39 에 3건으로 늘기 시작한다 |
| 사용자 영향 시작 | 14:40 (첫 5xx 14:40:52) | 14:40 에 17건으로 급증하고, `nginx-access.log` 5xx 가 처음 나온다 |
| 끝 | **14:51:49** (분 단위로는 14:51. 마지막 5xx · `[error]` 14:51:47, 마지막 `ProxyLeakTask` 14:51:49.148) | 14:52 부터는 평소 수준이다. 14:52 의 WARN 1건은 평소에 나오는 409 이다 |

**앞뒤 여유 제안: 앞 30분, 뒤 10분 → 수집 범위 14:07 ~ 15:02 (KST)**

이상 구간 시작 14:37 에서 30분을 빼면 14:07, 끝 14:51(분 단위로 14:52:00 직전)에 10분을 더하면 15:02 다.

- **앞 30분 (14:07 부터).** 앞쪽을 더 넉넉히 잡는 이유는 위 타이밍 설명 때문이다. 로그에 찍힌 시각은 모두 사건보다 늦다. 풀 대기 ERROR 는 요청이 시작되고 3초 뒤에 찍히고, 누수 경고는 연결을 잡은 뒤에 나오며, access 로그는 응답이 끝날 때 찍힌다. 그래서 첫 줄(14:37:52, 14:40:52)보다 실제 시작이 더 앞일 수 있다. 첫 징후(14:37)가 첫 5xx(14:40:52)보다 3분 앞서 있는 것도 같은 이유로 더 앞에 징후가 있을 수 있다는 뜻이다. 30분이면 평소 상태와 비교할 구간이 `app.log` 14:07 ~ 14:36 으로 확보된다. 이 구간에는 409 WARN 3건(14:11, 14:14, 14:33)이 들어오지만 평소 잡음이다(1-3 의 평소 수준 16건에 포함).
- **뒤 10분 (15:02 까지).** 정상 복귀를 확인하는 용도다. 14:52 이후 WARN · ERROR 0건과 5xx 0건이 유지되는지, 요청 수가 평소 수준인지만 보면 되므로 앞쪽보다 짧게 잡는다.
- 이 범위의 분량은 `app.log` 시각 줄 710줄(스택 트레이스 포함 `app.log:1135` ~ `app.log:5288`, 약 4,150줄)과 `nginx-access.log` 2,438줄이다. 스택 트레이스가 이상 구간에 몰려 있어 `app.log` 는 시각 줄 기준으로 먼저 보고, 스택 트레이스는 필요한 줄 번호만 골라 본다.

**수집 공백 (원인 판단 아님)**

- `nginx-error.log` 에는 upstream 이 두 대 나온다. `API-1:8080` 을 가리키는 줄이 83건(09:12:03 ~ 14:51:47)이고 `API-2:8080` 을 가리키는 줄이 14건(14:42:01 ~ 14:50:52)이다. 14건은 모두 `connect() failed (111: Connection refused)` 다. 나머지 68건은 upstream 그룹 이름(`item_bank_api`)만 있는 "no live upstreams" 줄이다.
- `app.log` 에는 서버 호스트를 알 수 있는 단서가 없다. 슬로우 로그의 접속 호스트 3건은 모두 `API-1` 이다. 그래서 `app.log` 가 `API-1` 과 `API-2` 중 어느 서버의 로그인지, 다른 서버의 로그가 따로 있는지를 확인해야 한다. 어느 쪽이든 나머지 한 서버의 앱 로그는 이번 수집 범위에 없을 수 있다.

**남은 확인 사항**

- 앞 30분 안에서도 징후가 없으면 앞쪽을 더 넓힐지는 2단계(타임라인 정리)에서 결정한다.
- 슬로우 로그 3건은 `# Time:` 이 UTC 라서 다른 로그와 섞어 볼 때 +9시간 변환을 빼먹지 않도록 주의한다.

## 3. 가설과 검증

> 3-1 ~ 3-5 의 앞 두 열은 가설을 세울 때 쓴 것이다. 세 번째 열(실행 결과)과 3-7 에 로그로 실행한 결과와 판정을 2026-10-06 에 추가했다. 판정은 로그 범위 안에서만 한 것이다.
> 이 문서에 2절(타임라인 정리)이 아직 없어서, 타임라인 근거는 1절의 분 단위 표(`a-connection-pool.md:61`)와 시각 정리(`a-connection-pool.md:88`)를 쓴다.
> 아래 명령은 모두 `incident-logs/a-connection-pool/` 에서 실행하고 원본 로그는 고치지 않는다.

### 3-0. 증상과 원인의 구분

- 많이 나온 메시지는 모두 증상이다. 시각이 붙은 줄(이벤트) 기준으로 세면 다음과 같다.
  - `app.log` ERROR 98건은 `Connection is not available, request timed out after 3000ms` 49건(`app.log:1522`, `a-connection-pool.md:93`)과 이어서 찍힌 `unhandled exception` 49건이다. 같은 49개 요청이 두 줄씩 남긴 것이다.
  - `app.log` 전체에서는 `ProxyLeakTask` WARN 134건(`a-connection-pool.md:92`)이 가장 많다.
  - `nginx-access.log` 5xx 는 502 82건 · 504 55건 · 500 49건이고(`a-connection-pool.md:27`), `nginx-error.log` 는 `no live upstreams` 68건 · `upstream timed out` 55건이다(`a-connection-pool.md:96`).
  - 이 문서 1-2 의 "147줄"(`a-connection-pool.md:46`)은 스택 트레이스 줄까지 센 줄 수라 이벤트 수(49건)와 다르다.
  - 이 메시지들은 **풀이 비어서 기다리다 포기했다 · 오래 쥔 연결이 있다 · 업스트림이 응답하지 않았다**는 결과를 말할 뿐이다. 무엇이 그 상태를 만들었는지는 말해 주지 않는다.
- 증상이 말해 주는 것은 `total=5, active=5, idle=0` 이라는 상태(`app.log:1522`)까지다. 풀이 왜 가득 찼는지는 말해 주지 않는다. 가능한 길은 두 가지로 나뉜다. 한 요청이 커넥션을 오래 쥔다(점유 시간 증가). 또는 커넥션을 쓰려는 요청이 늘었다(수요 증가). 아래 가설은 이 두 길 중 어느 쪽이 무엇 때문에 생겼는지를 계층별로 나눈 것이다.
- 1절에서 이미 확정된 사실은 시각뿐이다. 14:37:52 `ProxyLeakTask` 첫 WARN(`app.log:1243`)은 `leak-detection-threshold: 10000`(`modern/api/src/main/resources/application.yml:18`) 때문에 **커넥션을 잡은 지 10초 뒤**에 찍힌다. 그러므로 점유 시작은 14:37:42 이전이다.

### 3-1. 가설 H1 — 애플리케이션 코드: 리포트 API 가 트랜잭션(커넥션)을 쥔 채 CPU 작업을 한다

| 항목 | 내용 | 실행 결과 (2026-10-06, 로그 기준) |
|---|---|---|
| 가설 | `ReportService.buildClassReport` 가 `@Transactional` 안에서 DB 조회를 끝낸 뒤에도 서명 계산(SHA-256 200,000회 반복)을 하는 동안 커넥션을 반납하지 않아, 리포트 요청이 몰리면 풀 5개가 금방 찬다. | — |
| 지지 근거 | ① `a-connection-pool.md:92` 의 `ProxyLeakTask` WARN 134건은 연결을 10초 넘게 쥔 경우다. 이 134건의 스택에 `ReportService.buildClassReport` 와 `ReportController.classReport` 가 모두 나온다(`app.log:1243` 이하, 3-6 의 명령 1). ② `a-connection-pool.md:42` 에서 14:51:03 `/api/classes/3/report` 가 504 가 됐다. ③ 코드의 `ReportService.java:41`(`@Transactional`)과 `ReportService.java:69` ~ `ReportService.java:71`(TODO: DB 작업 뒤 서명 루프가 커넥션을 쥔 채 돈다)가 같은 구조를 가리킨다. | — |
| 반증 조건 | (a) 이상 구간 `ProxyLeakTask` 스택의 앱 프레임이 `buildClassReport` 가 아닌 다른 서비스에 흩어져 있다. (b) 장애 시점에 실제 배포된 `ReportService` 에 `@Transactional` 이 없거나 서명 호출이 트랜잭션 밖에 있다(저장소 코드가 운영 코드와 다르다). (c) 서명 계산 단독 시간이 로그에서 보이는 점유 시간(10초 이상)에 비해 훨씬 짧고, 점유 시간의 대부분이 DB 조회로 채워진다(이 경우 점유의 원인은 서명 루프가 아니다). | (a) 해당 안 됨 — 134건 모두 `ReportService.buildClassReport` · `ReportController.classReport` 다(실행 [1]). (b) 해당 안 됨 — 134건 모두 트랜잭션 프레임이 있고(`TransactionInterceptor.invoke` 134 · `JpaTransactionManager.doBegin` 134, 실행 [2]), 연결 반환은 `built class report` 줄보다 항상 2ms 뒤다(134건, 실행 [4]). 서명 호출이 끝난 뒤에 반환된다. (c) 성립 안 함 — 서명 단독은 85 · 108 · 145ms(로컬 3회, 실행 [13])로 점유 15.5 ~ 21.5초(실행 [3])보다 훨씬 짧다. 그러나 점유가 DB 조회로 채워졌다는 근거는 없다(이상 구간 슬로우 쿼리 1건, 실행 [6]). |
| 확인 방법 | (a) 3-6 의 명령 1. (b) 장애 당일 배포 산출물의 소스나 디컴파일 결과를 받아 `ReportService` 를 연다. 이 저장소에서 `ReportService.java` 의 git 기록은 2026-09-22 커밋 하나뿐이라(`git log -- modern/api/src/main/java/com/example/assignment/ReportService.java`) 장애일(2026-09-16)의 코드를 보증하지 못한다. (c) 3-6 의 명령 2(서명 단독 시간 측정)의 값을 점유 시간(= `built class report` 줄 시각 − (`ProxyLeakTask` WARN 시각 − 10초))과 비교한다. | 실행 [1] · [2] · [3] · [4] · [13] 을 실행했다. 배포본 소스는 받지 못해 (b)는 로그의 스택과 반환 시각으로 대신했다. |
| 판정 | **유지** | 리포트 134건 모두 서명 뒤(메서드 끝)에서 연결을 반환했고, 이상 구간 report 134건은 전부 점유 15.5 ~ 21.5초였다. 다만 서명 단독(약 0.1초)으로는 이 시간이 설명되지 않는다. 14:37 이전 report 50건은 leak 경고가 0건이다(점유 10초 미만, 실행 [5]). 14:37 부터 점유가 늘어난 이유는 이 가설만으로는 설명되지 않고 다른 요인(CPU 경합 · DB 시간 등)이 더 필요하다. |

### 3-2. 가설 H2 — DB · 쿼리: 제출 조회가 오래 걸려 커넥션 점유 시간이 늘었다

| 항목 | 내용 | 실행 결과 (2026-10-06, 로그 기준) |
|---|---|---|
| 가설 | `submission` 테이블을 `distribution_id` 로 조회하는 쿼리가 많은 행을 훑으며 수 초가 걸려, 리포트 한 건이 커넥션을 쥐는 시간이 길어졌다. | — |
| 지지 근거 | ① `a-connection-pool.md:98` 의 슬로우 쿼리는 9.81초, `Rows_examined` 1,284,310 이고 반환은 4행뿐이다. 평소 슬로우 쿼리는 2초대(`a-connection-pool.md:58`)였다. ② `a-connection-pool.md:100` 는 이 쿼리의 종료 시각과 첫 `ProxyLeakTask` WARN 이 같은 초라고 적었다. 여기에 3-0 의 10초 규칙을 더하면, 점유 시작 추정(14:37:42 이전)이 쿼리 시작(14:37:42, `a-connection-pool.md:43`)과 겹친다. ③ `mariadb-slow.log:18` 의 `Lock_time` 은 0.000071초라 잠금 대기는 아니고 스캔 시간으로 보인다. | — |
| 반증 조건 | (a) 운영 DB 의 `submission` 에 `distribution_id` 인덱스가 있고 `EXPLAIN` 이 그 인덱스로 소량의 행만 읽는다고 나온다. (b) 같은 시간 다른 리포트 요청들의 DB 시간이 짧았다(점유 시간을 DB 로 설명할 수 없다). | (a) 판단 불가 — 운영 DB 에 접속하지 못해 `SHOW INDEX` · `EXPLAIN` 은 실행하지 않았다. 참고: 해당 쿼리는 `Rows_examined` 1,284,310 · `Rows_sent` 4 다(`mariadb-slow.log:18`, 실행 [6]). (b) 판단 불가 — 이상 구간(UTC 5:37 이후) 슬로우 쿼리는 1건뿐이고 `submission` 조회도 그 1건이다(실행 [6]). 점유 134건 중 시각이 겹치는 것은 14:37:42 에 시작한 첫 건뿐이다(같은 요청인지는 로그로 연결되지 않는다). 나머지 133건의 DB 시간은 로그에 없다. 기록된 최소 `Query_time` 이 2.04초라 `long_query_time` 은 2.04초 이하로 보이나 설정값은 확인하지 못했다. |
| 확인 방법 | (a) 운영(또는 운영 사본) DB 에서 `SHOW INDEX FROM submission;` 와 `EXPLAIN select … from submission where distribution_id=5 order by student_id;`. 저장소의 연습용 스키마에는 `idx_submission_distribution` 가 있다(`db/mariadb/init/01-schema.sql:112`). 운영과 같은지는 따로 확인해야 한다. (b) 3-6 의 명령 3 과 `built class report` 줄 시각 − `ProxyLeakTask` WARN 시각 차이. 해석 주의: 슬로우 로그가 3건뿐이라는 사실이 "다른 쿼리는 빨랐다"는 뜻인지는 `long_query_time` 값에 달려 있고, 이 값은 로그에서 확인되지 않았다. 운영 DB 에서 `SHOW VARIABLES LIKE 'long_query_time';` · `SHOW VARIABLES LIKE 'slow_query_log%';` 로 먼저 확인한다. | 실행 [6]. `SHOW INDEX` · `EXPLAIN` · `SHOW VARIABLES` 는 운영 DB 접속이 필요해 실행하지 않았다. |
| 판정 | **판단 불가(로그 부족)** | 슬로우 로그로 점유와 연결되는 것이 1건뿐이라 지지도 반증도 못 한다. 필요한 로그: 이상 구간의 쿼리별 실행 시간(`long_query_time=0` 슬로우 로그 또는 `performance_schema.events_statements_history_long`), 앱 쪽 SQL 시간 로그(Hibernate 통계), 운영 DB 의 `SHOW INDEX FROM submission` · `EXPLAIN` 결과. |

### 3-3. 가설 H3 — 트래픽: 요청 총량은 같은데 리포트 요청 비중이 커졌다

| 항목 | 내용 | 실행 결과 (2026-10-06, 로그 기준) |
|---|---|---|
| 가설 | 14:37 무렵부터 `/api/classes/{id}/report` 호출이 평소보다 크게 늘어(특정 클라이언트의 반복 호출 포함), 점유 시간이 긴 요청이 동시에 풀 5개를 채웠다. | — |
| 지지 근거 | ① `a-connection-pool.md:59` 의 분당 요청 수는 평소 약 45건에서 이상 구간 약 43건으로 **총량은 그대로**다. 그러므로 총량 증가가 아니라 요청 **구성** 변화를 보는 가설이다. ② 구성 변화의 단서로 `nginx-access.log` 의 report 요청이 14:00 ~ 14:36 에는 6건이고 14:37 ~ 14:52 에는 171건이다(3-6 의 명령 4. 이 절에서 새로 센 값). ③ `a-connection-pool.md:42` 의 504 를 받은 단말(교사단말A)는 이상 구간 report 호출 상위 4개 클라이언트에 든다(명령 5). | — |
| 반증 조건 | (a) report 요청이 이상 구간 앞뒤로 분당 같은 수준에 머문다(분당 수가 늘지 않았다). (b) 이전 날짜에도 같은 시각에 report 호출이 몰렸지만 풀 고갈이 없었다(구성 변화만으로는 풀을 채우지 못한다). | (a) 해당 안 됨 — report 는 14:00 ~ 14:36 에 6건, 14:37 ~ 14:52 에 171건이다(실행 [7]). 14:37 이전 전체(06:43 ~ 14:36)는 52건 · 분당 최대 2건이고 이상 구간은 분당 3 ~ 19건이다(실행 [8]). 전체 요청은 689건 / 16분으로 평소와 같다(실행 [7]). (b) 판단 불가 — 이전 날짜 로그가 없다. 같은 날 14:37 이전에는 분당 최대 2건이라 "몰렸는데 고갈이 없었다"를 볼 구간이 없다. 참고: 이상 구간 report 호출은 고유 클라이언트 4곳이 보냈다(실행 [9]). Referer 는 이전 52건이 `/teacher/classes/N`, 이상 구간 171건이 `/teacher/classes/N/report` 다. |
| 확인 방법 | (a) 3-6 의 명령 4 · 5 · 6. (b) 이전 날짜의 `nginx-access.log` 가 있으면 같은 명령 6 을 돌린다. 이전 날짜 로그는 이번 수집 범위에 없다. 호출 주체는 `Referer` · `User-Agent`(access 로그 줄 끝 두 필드)로 배치성 호출인지 사용자 화면인지 가린다. | 실행 [7] · [8] · [9] · [10]. (b)는 이전 날짜 로그가 없어 실행하지 못했다. |
| 판정 | **유지** | report 요청이 평소 분당 최대 2건에서 3 ~ 19건으로 늘었다. 첫 5xx(14:40:52, 실행 [10]) 이전인 14:38 · 14:39 에도 분당 3건이라 증가가 재시도 때문만이라고 보기는 어렵다. 다만 평소 최대 2건과 차이가 작아 단정하지 않는다. 참고(근사): 분당 16건 × 평균 점유 18.9초 ÷ 60 ≈ 5.0 으로 풀 크기 5와 같다. (b)에 필요한 로그: 이전 날짜 `nginx-access.log`. |

### 3-4. 가설 H4 — 인프라: 업스트림 한 대가 이탈해 남은 서버 하나가 수요를 모두 받았다

| 항목 | 내용 | 실행 결과 (2026-10-06, 로그 기준) |
|---|---|---|
| 가설 | 14:37 이전에 `API-2` 가 요청을 처리하지 못하게 되어(다운 · health 실패 등) `API-1` 하나가 같은 요청을 받았고, 그래서 풀 5개로 감당하는 수요가 늘었다. | — |
| 지지 근거 | ① `a-connection-pool.md:123` 에서 `API-2` 를 가리키는 `connect() failed (111)` 14건이 14:42:01 ~ 14:50:52 에 있다. ② `a-connection-pool.md:96` 에서 `no live upstreams` 68건 · `temporarily disabled` 26건이 나온다. ③ `a-connection-pool.md:124` 에서 `app.log` 가 어느 서버의 로그인지 알 수 없다고 적었다(한 대분의 앱 로그만 있을 수 있다). | — |
| 반증 조건 | (a) 14:37 이전에 `API-2` 가 정상 응답을 하고 있었고 이탈이 풀 고갈 징후 뒤에 시작됐다(이탈이 결과다). (b) 이탈 전후로 `API-1` 이 받은 요청 수가 달라지지 않았다. | (a) 해당 — `API-2` 줄은 14건이고 첫 줄이 14:42:01 이다(`nginx-error.log:11`). 같은 14:42:01 에 `API-1` 이 `temporarily disabled` 됐고(`nginx-error.log:10`), 그 전 14:41:06 에는 `API-1` 의 `upstream timed out` 이 있다(`nginx-error.log:6`). `temporarily disabled` 26건은 전부 `API-1` 을 가리킨다. 14:41:06 이전 `nginx-error.log` 는 warn · info 5줄뿐이라 `API-2` 오류는 14:42:01 이전에 없다(실행 [11]). 풀 고갈 징후(`app.log:1243` 14:37:52, `app.log:1522` 14:40:52)가 더 앞선다. (b) 판단 불가 — access 로그에 upstream 필드가 없다(`grep -c upstream nginx-access.log` = 0, 실행 [11]). |
| 확인 방법 | (a) `grep -n 'connect() failed' nginx-error.log \| head -2` 의 14:42:01 은 **에러가 기록된 첫 시각**일 뿐, 이탈 시작 시각이 아니다. 이탈 시작은 nginx 의 `max_fails` · `fail_timeout` 설정, `API-2` 의 앱 · 시스템 로그, 모니터링의 health 기록이 있어야 알 수 있다. 모두 이번 수집 범위에 없다. (b) `nginx-access.log` 는 combined 형식이라 upstream 주소 필드가 없다(`head -1 nginx-access.log` 로 확인). 서버별 요청 수는 nginx 설정의 `log_format` 에 `$upstream_addr` 가 있는지 확인한 뒤 해당 로그로 센다. 없으면 이 반증 조건은 지금 로그로 검증할 수 없다. | 실행 [10] · [11]. `max_fails` · `fail_timeout` · `backup` 이 담긴 nginx 설정 원문과 `API-2` 쪽 로그는 없어 확인하지 못했다. |
| 판정 | **기각** | `API-2` 의 첫 오류(14:42:01)는 `API-1` 이 비활성화된 같은 초에 나오고 그 전에는 오류가 없어, 이탈은 풀 고갈 뒤의 결과로 보인다. `API-2` 가 `backup` 서버였다면 부하 이동 전제 자체가 성립하지 않으므로 어느 경우든 기각이다. 남은 불확실성: nginx upstream 설정 원문이 없다. |

### 3-5. 가설 H5 — 배포 · 설정 변경: 14:37 직전에 풀 설정이나 리포트 코드가 바뀌었다

| 항목 | 내용 | 실행 결과 (2026-10-06, 로그 기준) |
|---|---|---|
| 가설 | 14:37 직전의 배포나 설정 변경(풀 크기 · 타임아웃 · 리포트 로직 변경)이 있어서, 평소에는 문제없던 동작이 이상 구간부터 풀을 고갈시켰다. | — |
| 지지 근거 | 타임라인에 배포 · 설정 변경 줄이 없다(이 폴더에 `deploy-history.md` 도 없고, 변경 이력은 수집 범위 밖이다). 간접 단서는 두 가지다. ① `a-connection-pool.md:54` ~ `a-connection-pool.md:57` 에서 평소 473분은 ERROR 0건 · 5xx 0건이었다가 `a-connection-pool.md:109` 처럼 14:37 에 달라진다. ② 풀은 `maximum-pool-size: 5`, `connection-timeout: 3000`(`modern/api/src/main/resources/application.yml:14` · `modern/api/src/main/resources/application.yml:15`)이다. 이 설정 주석에 "운영 값과 같게 맞춘다"고 적혀 있다. | — |
| 반증 조건 | (a) 14:37 이전 최소 며칠간 풀 설정과 `ReportService` 가 같은 값 · 같은 버전이었고, 장애 당일 14:00 ~ 14:37 에 배포 · 설정 반영 기록이 없다. (b) 이전 날짜에도 report 호출이 몰린 시각에 같은 증상이 있었다(변경과 무관한 상시 취약점이다). | (a) 일부만 확인 — 06:43 ~ 15:29 `app.log` 에 기동 줄(`Started ItemBankApplication` 등)이 0건이고 `nginx-error.log` 에 reload · signal 줄이 0건이다(실행 [12]). 오류 줄의 풀 값은 `total=5` · `3000ms` 다(`app.log:1522`). `modern/api/src/main/resources/application.yml:14` · `modern/api/src/main/resources/application.yml:15` 와 같다. 며칠간 같은 값 · 같은 버전이었는지는 확인하지 못했다. (b) 판단 불가 — 이전 날짜 로그가 없다. |
| 확인 방법 | (a) 배포 이력(CI/CD 실행 기록 · 릴리스 노트 · 설정 저장소 변경 이력)을 장애일 앞뒤로 연다. `app.log` 에는 기동 줄이 없어서(`grep -ciE 'Started ItemBankApplication' app.log` 가 0) 재시작 여부도 로그로 확인되지 않는다. (b) 이전 날짜의 `app.log` · `nginx-access.log` 에서 report 요청이 몰린 시각의 `Connection is not available` 유무를 센다. 이전 날짜 로그는 이번 수집 범위에 없다. | 실행 [12]. 배포 이력 · 설정 저장소 이력은 이 폴더에 없어 열지 못했다(폴더에 파일 4개뿐, 실행 [12]). |
| 판정 | **판단 불가(로그 부족)** | 이 로그 범위에서 앱 재시작 · nginx reload 흔적이 없어 14:37 직전 배포 가능성을 낮추지만, `API-2` 앱 로그 · 배포 이력 · 이전 날짜 로그가 없어 기각하지 못한다. 필요한 로그: 장애일 앞뒤 CI/CD 배포 이력, 설정 저장소 변경 이력, 이전 날짜 `app.log` · `nginx-access.log`, `API-2` 의 `app.log`. |

### 3-6. 확인 명령 모음

```bash
cd incident-logs/a-connection-pool

# 명령 1. leak 경고 스택의 앱 프레임 분포 (H1 반증 (a))
grep -A12 'Apparent connection leak' app.log | grep -o 'com.example[^(]*' | sort | uniq -c | sort -rn

# 명령 2. 서명 단독 시간 측정 — ReportService.sign 과 같은 반복 (H1 반증 (c))
printf 'var d=java.security.MessageDigest.getInstance("SHA-256");\nbyte[] b="3|x".getBytes();\nlong t=System.nanoTime();\nfor(int i=0;i<200_000;i++){d.reset(); b=d.digest(b);}\nSystem.out.println((System.nanoTime()-t)/1_000_000+" ms");\n/exit\n' | jshell -s

# 명령 3. 슬로우 로그 쿼리 시간 · 조회 행 수 (H2 반증 (b))
grep -E '^# (Time|Query_time)' mariadb-slow.log

# 명령 4. 구간별 전체 · report 요청 수 (H3 지지 ② · 반증 (a))
awk 'match($0,/16\/Sep\/2026:14:[0-9][0-9]/){m=substr($0,RSTART+15,2)+0; r=($0 ~ /\/api\/classes\/[0-9]+\/report/); if(m<37){a++; ra+=r} else if(m<=52){b++; rb+=r}} END{print "14:00-14:36 전체",a,"report",ra+0," | 14:37-14:52 전체",b,"report",rb+0}' nginx-access.log

# 명령 5. 이상 구간 report 호출 클라이언트 상위 (H3 지지 ③)
awk 'match($0,/16\/Sep\/2026:14:[0-9][0-9]/){m=substr($0,RSTART+15,2)+0; if(m>=37&&m<=52&&$0 ~ /\/report/) print $1}' nginx-access.log | sort | uniq -c | sort -rn | head

# 명령 6. 분당 report 요청 수 (H3 반증 (a))
grep '/report' nginx-access.log | awk 'match($0,/16\/Sep\/2026:[0-9:]{5}/){print substr($0,RSTART+12,5)}' | sort | uniq -c
```

- 명령 4 · 5 는 14시대 줄만 센다. 로그는 06:43 부터 있으므로 이른 시간의 report 비중은 명령 6 의 전체 분포로 본다.
- 명령 4 · 5 · 6 의 결과는 아직 해석하지 않았다. 4절(검증)에서 가설별 반증 조건과 하나씩 대조한다.

### 3-7. 실행 기록과 판정 요약

| 가설 | 판정 | 한 줄 이유 |
|---|---|---|
| H1 코드 | 유지 | 리포트 134건 모두 서명 뒤에 연결을 반환했다. 다만 점유 15.5 ~ 21.5초는 서명 단독(약 0.1초)으로 설명되지 않는다 |
| H2 DB · 쿼리 | 판단 불가(로그 부족) | 점유와 연결되는 슬로우 쿼리가 134건 중 1건뿐이고 운영 DB 확인이 없다 |
| H3 트래픽 | 유지 | report 요청이 분당 최대 2건에서 3 ~ 19건으로 늘었다. 이전 날짜 로그가 없어 (b)는 못 봤다 |
| H4 인프라 | 기각 | `API-2` 첫 오류(14:42:01)가 `API-1` 비활성화와 같은 초이고 그 전 오류가 없다 |
| H5 배포 · 설정 | 판단 불가(로그 부족) | 재시작 · reload 흔적은 없으나 배포 이력과 `API-2` 로그가 없다 |

- 유지는 "반증 조건에 걸리지 않았다"는 뜻이다. 서로 배타적이지 않다. H1 · H2 · H3 가 함께 작용했을 수 있고 이 절은 그 기여도를 가르지 않았다.
- 원본 로그는 읽기만 했다. 마스킹 때문에 명령 안의 IP 는 별칭으로 바뀌어 있어 그대로는 실행되지 않는다. 실제 주소로 되돌려 실행한다. 아래 번호 [1] ~ [12] 는 `incident-logs/a-connection-pool/` 에서 아래 스크립트로 실행한 것이다. [13] 은 별도로 3회 실행했다.
- 같은 현상을 다른 로그가 다르게 센 곳이 있다. report 호출은 `nginx-access.log` 기준 14:37 이전 52건이고 `app.log` 의 `built class report` 는 14:37 이전 50건이다([5] · [8]). 요청 단위와 기록 시점이 달라서이며 이유는 확인하지 않았다.

**실행한 명령**

```bash
cd incident-logs/a-connection-pool

# [1] leak 스택의 앱 프레임 분포
grep -A12 'Apparent connection leak' app.log | grep -o 'com.example[^(]*' | sort | uniq -c | sort -rn

# [2] leak 스택의 트랜잭션 프레임 (TransactionInterceptor.invoke / JpaTransactionManager.doBegin)
grep -A14 'Apparent connection leak' app.log | grep -c 'TransactionInterceptor.invoke'
grep -A14 'Apparent connection leak' app.log | grep -c 'JpaTransactionManager.doBegin'

# [3] 점유 시간(초) = 반환 줄 시각 - (leak WARN 시각 - 10초), 같은 스레드끼리 짝지음
awk '
function ts(s){return substr(s,12,2)*3600+substr(s,15,2)*60+substr(s,18,6)}
/Connection leak detection triggered/{match($0,/on thread [^,]*/);t=substr($0,RSTART+10,RLENGTH-10);w[t]=ts($1);next}
/Previously reported leaked connection/{match($0,/on thread [^ ]*/);t=substr($0,RSTART+10,RLENGTH-10);if(t in w){print ts($1)-w[t]+10;delete w[t]}}' app.log | sort -n | awk '{a[NR]=$1} END{printf "n %d  min %.1f  median %.1f  p90 %.1f  max %.1f\n",NR,a[1],a[int((NR+1)/2)],a[int(NR*0.9)],a[NR]}'

# [4] 직전 built class report 줄과 반환 줄의 시각 차(ms), 스레드 번호 정확 일치
awk '
function ts(s){return substr(s,12,2)*3600+substr(s,15,2)*60+substr(s,18,6)}
/ReportService .*built class report/{match($0,/exec-[0-9]+/);b[substr($0,RSTART+5,RLENGTH-5)]=ts($1);next}
/Previously reported leaked connection/{match($0,/exec-[0-9]+/);k=substr($0,RSTART+5,RLENGTH-5);if(k in b){d=(ts($1)-b[k])*1000;if(d>=0)print d}}' app.log | sort -n | awk '{a[NR]=$1} END{printf "n %d  min %.0f  median %.0f  max %.0f\n",NR,a[1],a[int((NR+1)/2)],a[NR]}'

# [5] built class report 줄 수: 14:37 이전 / 14:37~14:52 / 14:53 이후, 14:37 이전 leak WARN 수
grep 'built class report' app.log | awk '{t=substr($1,12,5); if(t<"14:37")a++; else if(t<="14:52")b++; else c++} END{print a+0,b+0,c+0}'
grep 'Connection leak detection triggered' app.log | awk '{t=substr($1,12,5); if(t<"14:37")n++} END{print n+0}'

# [6] 슬로우 로그
grep -E '^# (Time|Query_time)' mariadb-slow.log
grep -c '^# Time: 260916  5:' mariadb-slow.log
grep -o 'Query_time: [0-9.]*' mariadb-slow.log | sort -t' ' -k2 -n | head -1

# [7] report 요청 수: 14:00~14:36 / 14:37~14:52 (전체, report)
awk 'match($0,/16\/Sep\/2026:14:[0-9][0-9]/){m=substr($0,RSTART+15,2)+0; r=($0 ~ /\/api\/classes\/[0-9]+\/report/); if(m<37){a++; ra+=r} else if(m<=52){b++; rb+=r}} END{print "14:00-14:36 전체",a,"report",ra+0," | 14:37-14:52 전체",b,"report",rb+0}' nginx-access.log

# [8] 분당 report (14:37 이후) · 14:37 이전 report 합계와 분당 최대
grep '/report' nginx-access.log | awk 'match($0,/16\/Sep\/2026:[0-9:]{5}/){print substr($0,RSTART+12,5)}' | sort | uniq -c | awk '{k=$2; if(k<"14:37"){pre+=$1; if($1>m)m=$1} else if(k<="14:52") printf "%s:%s ",k,$1} END{print "\n14:37 이전 report 합계",pre,"분당 최대",m}'

# [9] 이상 구간 report 호출 클라이언트 (건수 · 고유 수) 와 Referer
awk 'match($0,/16\/Sep\/2026:14:[0-9][0-9]/){m=substr($0,RSTART+15,2)+0; if(m>=37&&m<=52&&$0 ~ /\/report/) print $1}' nginx-access.log | sort | uniq -c | sort -rn | head -4
awk 'match($0,/16\/Sep\/2026:14:[0-9][0-9]/){m=substr($0,RSTART+15,2)+0; if(m>=37&&m<=52&&$0 ~ /\/report/) print $1}' nginx-access.log | sort -u | wc -l
awk 'match($0,/16\/Sep\/2026:14:[0-9][0-9]/){m=substr($0,RSTART+15,2)+0; if(m>=37&&m<=52&&$0 ~ /\/report/) print}' nginx-access.log | awk -F'"' '{print $4}' | sed -E 's#classes/[0-9]+#classes/N#' | sort | uniq -c
awk 'match($0,/16\/Sep\/2026:[0-9:]{5}/){k=substr($0,RSTART+12,5); if(k<"14:37" && $0 ~ /\/report/) print}' nginx-access.log | awk -F'"' '{print $4}' | sed -E 's#classes/[0-9]+#classes/N#' | sort | uniq -c

# [10] 첫 5xx 시각
awk '$9 ~ /^5/{print $4; exit}' nginx-access.log

# [11] nginx-error: upstream 별 줄 수와 시각, disabled 대상, 14:41:06 이전 줄 수
grep -c 'API-2' nginx-error.log
grep 'API-2' nginx-error.log | awk '{print $2}' | sed -n '1p;$p'
grep -n 'temporarily disabled' nginx-error.log | grep -o 'API-[12]:8080' | sort | uniq -c
awk '$2<"14:41:06"' nginx-error.log | wc -l
sed -n '6p;10,11p' nginx-error.log | awk '{print NR": "$2" "$3" "$5" "$6" "$7" "$8}'
sed -n '6p;10p;11p' nginx-error.log | grep -o 'upstream: "http://[0-9.]*'
grep -c 'upstream' nginx-access.log

# [12] 재시작 · 설정 반영 흔적 (app.log 기동 줄, nginx-error reload 줄), 풀 설정 단서
grep -ciE 'Started ItemBankApplication|Starting ItemBankApplication|HikariPool-[0-9]+ - (Starting|Start completed)|itembank-pool - (Starting|Start completed)|Shutdown|Initializing' app.log
grep -ciE 'signal|reload|worker process|exited|start worker|using the' nginx-error.log
sed -n 1522p app.log | grep -o 'itembank-pool - Connection is not available, request timed out after [0-9]*ms (total=[0-9]*'
ls

# [13] 서명 단독 시간 (jshell, 3회: 108ms · 145ms · 85ms)
printf 'var d=java.security.MessageDigest.getInstance("SHA-256");\nbyte[] b="3|x".getBytes();\nlong t=System.nanoTime();\nfor(int i=0;i<200_000;i++){d.reset(); b=d.digest(b);}\nSystem.out.println((System.nanoTime()-t)/1_000_000+" ms");\n/exit\n' | jshell -s
```

**출력(그대로)**

```text
 134 com.example.assignment.ReportService$$SpringCGLIB$$0.buildClassReport
 134 com.example.assignment.ReportController.classReport
134
134
n 134  min 15.5  median 18.9  p90 21.0  max 21.5
n 134  min 2  median 2  max 2
50 134 6
0
# Time: 260916  0:14:09
# Query_time: 2.318446  Lock_time: 0.000071  Rows_sent: 7  Rows_examined: 41822
# Time: 260916  2:02:42
# Query_time: 2.041903  Lock_time: 0.912004  Rows_sent: 5  Rows_examined: 5
# Time: 260916  5:37:52
# Query_time: 9.812417  Lock_time: 0.000071  Rows_sent: 4  Rows_examined: 1284310
1
Query_time: 2.041903
14:00-14:36 전체 1633 report 6  | 14:37-14:52 전체 689 report 171
14:38:3 14:39:3 14:40:10 14:41:16 14:42:15 14:43:19 14:44:14 14:45:15 14:46:19 14:47:12 14:48:14 14:49:16 14:50:10 14:51:5 
14:37 이전 report 합계 52 분당 최대 2
  67 교사단말B
  40 교사단말A
  35 교사단말C
  29 교사단말D
       4
 171 https://lms.example.com/teacher/classes/N/report
  52 https://lms.example.com/teacher/classes/N
[16/Sep/2026:14:40:52
14
14:42:01
14:50:52
  26 API-1:8080
       5
1: 14:41:06 [error] *224941 upstream timed out
2: 14:42:01 [warn] *225025 upstream server temporarily
3: 14:42:01 [error] *225045 connect() failed (111:
upstream: "http://API-1
upstream: "http://API-1
upstream: "http://API-2
0
0
0
itembank-pool - Connection is not available, request timed out after 3000ms (total=5
app.log
mariadb-slow.log
nginx-access.log
nginx-error.log
```

### 3-8. 판정

> 3-7 에서 유지된 가설(H1 · H3)을 `modern/api` 의 코드 · 설정과 대조했다. 읽기만 했고 벤치마크 · 테스트는 실행하지 않았다. 설정값은 `modern/api/src/main/resources/application.yml` 에서 읽은 값만 쓴다. 이 저장소에서 `application.yml` 과 `ReportService.java` 의 git 기록은 2026-09-22 커밋 하나뿐이다(`git log`).

**로그 단서와 코드 · 설정의 대응**

| 로그 단서 | 읽은 코드 · 설정 | 일치 여부 |
|---|---|---|
| `app.log:1254` 스택의 `ReportController.classReport(ReportController.java:22)` | `ReportController.java:22` 가 `return reportService.buildClassReport(id);` | 줄번호까지 일치 |
| `app.log:1252` 의 `TransactionInterceptor.invoke` 와 `app.log:1253` 의 `ReportService$$SpringCGLIB$$0.buildClassReport` | `ReportService.java:41` 의 `@Transactional`(`readOnly` 없음) | 일치 |
| `app.log:103` 의 `built class report for class 2: 3 distributions, 10 submissions` | `ReportService.java:73` ~ `ReportService.java:74` 의 `log.info("built class report for class {}: {} distributions, {} submissions", …)` | 문구 일치 |
| `app.log:1522` 의 `itembank-pool - Connection is not available, request timed out after 3000ms (total=5` | `application.yml:10` `pool-name: itembank-pool`, `application.yml:14` `maximum-pool-size: 5`, `application.yml:15` `connection-timeout: 3000` | 값 일치 |
| leak WARN 이 점유 10초 뒤에 찍힌다(3-0) | `application.yml:18` `leak-detection-threshold: 10000` | 일치 |
| `mariadb-slow.log:21` 의 `… from submission s1_0 where s1_0.distribution_id=5 order by s1_0.student_id` | `SubmissionRepository.java:8` `findByDistributionIdOrderByStudentIdAsc`. 이 메서드의 호출은 `ReportService.java:54` 한 곳뿐이다(`grep -rn` 결과) | 일치. 슬로우 쿼리는 리포트 경로의 쿼리다 |

**유지된 가설의 코드 · 설정 판정**

| 가설 | 판정 | 근거 |
|---|---|---|
| H1 코드 | **코드 · 설정이 가설을 지지한다** | ① 트랜잭션은 `ReportService.java:41` 에서 시작해 메서드 끝까지다. `application.yml:22` 가 `open-in-view: false` 라 연결 보유 범위는 이 서비스 메서드다. ② DB 호출은 `ReportService.java:43` · `ReportService.java:46` · 루프 안 `ReportService.java:54`(`ReportService.java:52` 루프)이다. 쿼리 수는 1 + 1 + 배포 수이고, 로그는 모든 리포트가 `3 distributions` 라 5개다. ③ DB 작업이 끝난 뒤에도 같은 트랜잭션 안에서 `ReportService.java:71` 이 `sign()` 을 호출한다. `ReportService.java:23` 의 `SIGNATURE_ROUNDS = 200_000` 번 SHA-256 을 `ReportService.java:88` 루프로 돈다. ④ 코드 주석 `ReportService.java:69` ~ `ReportService.java:70` 이 "DB 작업은 위에서 끝났는데 커넥션(트랜잭션)을 쥔 채로 돈다"고 적었다. 팀 규칙 `CLAUDE.md:96` ~ `CLAUDE.md:99` 는 `@Transactional` 안의 반복 해시를 금지한다. 이 코드는 규칙에 어긋난다. ⑤ 설정 주석 `application.yml:11` ~ `application.yml:12` 가 "풀이 작고 대기 시간이 짧아, 트랜잭션을 오래 쥐는 코드가 있으면 금방 고갈된다"고 적었다. 풀은 `application.yml:14` 가 5개, 대기는 `application.yml:15` 가 3000ms 다. |
| H3 트래픽 | **관련 코드를 찾지 못했다** | 요청이 늘어난 이유는 클라이언트 쪽 일이라 `modern/api` 코드에 없다. 요청을 제한하거나 줄이는 장치도 찾지 못했다. `modern/api/src/main` 과 `modern/api/build.gradle` 에서 `Cacheable · EnableCaching · RateLimit · Bucket4j · Semaphore · @Async · Executor · tomcat · @Scheduled · resilience4j` 를 `grep -rnE` 로 찾았고 0건이다. `application.yml:28` ~ `application.yml:29` 의 `server` 항목은 `port` 뿐이라 스레드 수 설정도 파일에 없다. 이 값은 추측하지 않는다. |

- H1 의 코드 대응은 한계가 있다. 코드는 점유 구간이 `ReportService.java:41` ~ `ReportService.java:82` 임을 보여 줄 뿐, 점유가 15.5 ~ 21.5초가 되는 이유는 보여 주지 않는다. 서명 단독 시간은 3-7 의 [13] 에서 85 · 108 · 145ms 였다. 이번 대조에서는 새로 실행하지 않았다.
- 로그의 줄번호(`ReportController.java:22`)와 문구가 저장소 코드와 일치하므로 같은 버전일 가능성은 높다. 그러나 장애일(2026-09-16)의 배포본이라는 보증은 아니다.

**채택 가설: H1 — 리포트 API 가 트랜잭션(커넥션)을 쥔 채 서명 계산을 한다**

- **확신 수준: 중간.**
- 확신을 받치는 것:
  - 134건 모두 리포트 스택이고(3-7 [1]), 연결이 메서드 끝에서 반환된다(3-7 [4]).
  - 풀 · 대기 · 누수 설정값이 로그와 줄 단위로 맞는다.
  - 코드 주석과 팀 규칙이 같은 결함을 스스로 지적한다.
  - "원인 구조"로는 높음에 가깝다.
- 확신을 낮추는 것:
  - 이 코드는 14:37 이전에도 같은 모습으로 돌았다. 14:37 이전 report 50건은 leak 경고가 0건이었다(3-7 [5]). 결함은 상시 있었는데 14:37 부터 터졌다. 터뜨린 요인(요청 증가 · 느린 쿼리)과 점유 시간이 15.5 ~ 21.5초가 된 이유는 코드로 설명되지 않는다.
  - 저장소 코드가 장애일 배포본과 같다는 보증이 없다.
  - H2 · H5 가 "판단 불가"로 남아 있다.

**채택하지 않은 가설과 사유**

| 가설 | 상태 | 사유 |
|---|---|---|
| H3 트래픽 | 단독 원인으로는 채택하지 않음(반박되지는 않음) | 14:37 이전 report 50건이 점유 10초 미만이었으므로, 건당 점유가 10초 미만으로 유지됐다면 분당 최대 19건(3-7 [8])은 동시 점유 19 × 10 ÷ 60 = 3.2 로 풀 크기 5(`application.yml:14`)에 못 미친다(근사). 요청이 늘어도 점유 시간이 길지 않으면 풀이 차지 않는다. 요청 증가는 H1 의 결함을 터뜨린 촉발 조건으로 본다. 코드에는 요청을 막는 장치가 없다(위 표). |
| H2 DB · 쿼리 | 채택 보류(판단 불가 유지) | 슬로우 쿼리가 리포트가 실행하는 쿼리라는 것은 코드로 확인했다(`SubmissionRepository.java:8`, `ReportService.java:54`). 별개 경로가 아니라 H1 의 같은 트랜잭션 안의 한 구간이다. 인덱스는 코드로 판정할 수 없다. `Submission.java:17` 은 `@Table(name = "submission")` 만 있고 인덱스 정의가 없으며, `application.yml:21` 이 `ddl-auto: none` 이라 스키마는 DB 쪽이다. 연습용 스키마에는 `db/mariadb/init/01-schema.sql:112` 의 `idx_submission_distribution` 이 있는데 슬로우 로그는 128만 행을 훑었다(`mariadb-slow.log:18`). 운영 DB 의 `EXPLAIN` 이 있어야 판정한다. |
| H4 인프라 | 기각 | 3-7 의 로그 판정을 따른다. `API-2` 의 첫 오류가 `API-1` 비활성화와 같은 초이고 그 전 오류가 없다. 저장소에는 nginx 설정이 없다(`docker-compose.yml` 에도 nginx · upstream 줄이 없다). 코드 · 설정으로는 대조할 곳이 없어 로그 판정이 그대로 근거다. |
| H5 배포 · 설정 | 채택 보류(판단 불가 유지) | `application.yml:14` · `application.yml:15` · `application.yml:18` 값이 로그와 맞는다. 그러나 `application.yml` 의 git 기록이 2026-09-22 커밋 하나뿐이라 장애 전후에 값이 바뀌었는지 비교할 수 없다. 배포 이력이 필요하다. |

**채택을 확정하거나 뒤집을 확인**

- 점유 시간 15.5 ~ 21.5초의 내역: 이상 구간의 컨테이너 CPU 사용률 · GC 로그와 쿼리별 실행 시간. 서명이 CPU 경합으로 늦어졌다면 H1 에 가깝다. 쿼리가 늦었다면 H2 쪽이다.
- 장애일 배포본의 `ReportService`: 배포 산출물이나 해당 시점의 소스.
- 운영 DB 의 `SHOW INDEX FROM submission` 과 `EXPLAIN`.

## 4. 원인

3-8 에서 채택한 H1 을 인과 순서로 쓴다. 각 문장 번호는 5절의 근거 번호와 같다. 확신 수준은 중간이다.

**배경 원인** (장애 전부터 있었다)

- **B1.** `ReportService.buildClassReport` 는 `@Transactional` 안에서 DB 조회를 끝낸 뒤에도 서명 계산을 하고, 커넥션은 메서드가 끝날 때 반환된다.
- **B2.** 풀은 5개, 대기는 3초, 누수 감지는 10초다. 설정 주석이 "오래 쥐는 코드가 있으면 금방 고갈된다"고 이미 경고한다.
- **B3.** 리포트 요청을 제한하거나 분리하는 장치가 없어, 리포트가 다른 API 와 같은 풀 5개를 쓴다.

**촉발 요인** (기여도 미확정 — 판단 불가)

- **T1.** 14:37 부터 리포트 호출이 평소(분당 최대 2건)보다 늘었다(14:00 ~ 14:36 6건 → 14:37 ~ 14:52 171건).
- **T2.** 같은 시각 리포트 경로의 `submission` 조회 1건이 9.81초 걸렸다.

**직접 원인과 경과**

- **D1.** 14:37:42 무렵부터 리포트 요청이 커넥션을 건당 15.5 ~ 21.5초 쥐었다. 이상 구간 리포트 134건이 모두 그랬다.
- **D2.** 14:40:52 에 풀 5개가 모두 사용 중이었고, 3초 안에 연결을 얻지 못한 요청이 실패하기 시작했다.
- **R1.** 연결을 얻지 못한 요청에는 리포트와 무관한 API 도 있었다. 5xx 186건 중 94건이다.
- **R2.** 응답 지연으로 nginx 가 `API-1` 을 일시 비활성화했다. `API-2` 는 연결을 거부했고, `no live upstreams` 와 502 · 504 가 늘었다.
- **R3.** 14:52 에 리포트 호출이 분당 0건이 되자 5xx 가 멈췄다. 코드를 고친 것이 아니다. 복구 원인은 9절에 남겼다.

## 5. 근거 로그 줄

클라이언트 IP 는 `<client>` 로 가렸다. 발췌는 길이를 줄였다.

| 번호 | 파일:줄 | 발췌 |
|---|---|---|
| B1 | `ReportService.java:41` · `ReportService.java:71` · `ReportService.java:23` | `@Transactional` / `String signature = sign(payload.toString());` / `static final int SIGNATURE_ROUNDS = 200_000;` |
| B1 | `ReportService.java:69` | `// TODO: 트랜잭션 밖으로 — 외부 집계 시스템 호출을 흉내내는 긴 루프.` |
| B1 | `app.log:1258` → `app.log:1259` | `14:38:01.900 INFO … ReportService : built class report for class 3: 3 distributions, 10 submissions` → `14:38:01.902 INFO … ProxyLeakTask : Previously reported leaked connection … was returned to the pool (unleaked)` (메서드 끝 2ms 뒤 반환) |
| B2 | `application.yml:14` · `application.yml:15` · `application.yml:18` | `maximum-pool-size: 5` / `connection-timeout: 3000` / `leak-detection-threshold: 10000` |
| B2 | `application.yml:11` · `application.yml:12` | `# 커넥션 풀 — 운영 값과 같게 맞춘다.` / `# 풀이 작고 대기 시간이 짧아, 트랜잭션을 오래 쥐는 코드가 있으면 금방 고갈된다.` |
| B3 | `modern/api/src/main` 전체 | `grep -rnE "Cacheable\|EnableCaching\|RateLimit\|Bucket4j\|Semaphore\|@Async\|Executor\|tomcat\|@Scheduled\|resilience4j" modern/api/src/main modern/api/build.gradle` 결과 0건. `application.yml:5` 의 `datasource` 는 하나뿐이다. |
| T1 | `nginx-access.log:18801` | `<client> - - [16/Sep/2026:14:38:01 +0900] "GET /api/classes/3/report HTTP/1.1" 200 266 …` (이상 구간 첫 리포트 응답. 구간별 건수는 3-7 의 [7]) |
| T2 | `mariadb-slow.log:18` · `mariadb-slow.log:21` | `# Query_time: 9.812417  Lock_time: 0.000071  Rows_sent: 4  Rows_examined: 1284310` / `select … from submission s1_0 where s1_0.distribution_id=5 order by s1_0.student_id;` |
| D1 | `app.log:1243` | `2026-09-16T14:37:52.199+09:00 WARN … ProxyLeakTask : Connection leak detection triggered for org.mariadb.jdbc.Connection@6e8f14d3 …` (임계 10초이므로 점유 시작은 14:37:42 이전. 반환 `app.log:1259` 까지 약 19.7초) |
| D1 | `app.log:1252` · `app.log:1253` · `app.log:1254` | `TransactionInterceptor.invoke(…)` / `ReportService$$SpringCGLIB$$0.buildClassReport(<generated>)` / `ReportController.classReport(ReportController.java:22)` |
| D2 | `app.log:1522` | `14:40:52.319 ERROR … SqlExceptionHelper : itembank-pool - Connection is not available, request timed out after 3000ms (total=5, active=5, idle=0, waiting=4)` |
| R1 | `nginx-access.log:18918` · `app.log:1523` | `[16/Sep/2026:14:40:52 +0900] "GET /api/units/M6-2/items HTTP/1.1" 500 162 …` / `14:40:52.320 ERROR … GlobalExceptionHandler : unhandled exception on /api/units/M6-2/items` (첫 5xx 는 리포트가 아닌 API) |
| R2 | `nginx-error.log:6` → `nginx-error.log:10` → `nginx-error.log:11` → `nginx-error.log:12` | `14:41:06 [error] upstream timed out (110: Connection timed out) …` → `14:42:01 [warn] upstream server temporarily disabled …` → `14:42:01 [error] connect() failed (111: Connection refused) …` → `14:42:04 [error] no live upstreams while connecting to upstream …` |
| R3 | `nginx-access.log:19381` | `<client> - - [16/Sep/2026:14:51:47 +0900] "GET /api/classes/3/report HTTP/1.1" 504 167 …` (마지막 5xx). `grep '/report' nginx-access.log \| grep -cE '16/Sep/2026:14:5[2-5]'` 결과 0건. 14:53 이후 리포트 6건은 모두 200 이고 leak 경고가 없다. |

## 6. 수정안

지금 당장 적용할 수정이다. 코드는 고치지 않았고 제안만 쓴다.

**6-1. `modern/api/src/main/java/com/example/assignment/ReportService.java` — 서명을 트랜잭션 밖으로**

- 무엇을: `buildClassReport`(`ReportService.java:41`)에서 `@Transactional` 을 떼고, DB 조회 · 집계(`ReportService.java:43` ~ `ReportService.java:67`)만 읽기 전용 트랜잭션으로 묶는다. `sign()`(`ReportService.java:71`)은 그 밖에서 호출한다.
- 어떻게:
  - 같은 클래스 안에서 `@Transactional` 메서드를 부르면 프록시를 거치지 않아 효과가 없다. 조회 · 집계는 같은 도메인 패키지의 별도 빈으로 뽑는다(예: `ReportAggregator.aggregate(classId)` 에 `@Transactional(readOnly = true)`).
  - 서명에 필요한 값(payload · 배포 수 · 제출 수 · 평균)은 `record` 로 돌려준다(`CLAUDE.md` 의 DTO 규칙).
  - `ReportService.buildClassReport` 는 `aggregate()` 결과로 `sign(payload)` 를 호출한 뒤 `ClassReport` 를 만든다.
  - `SIGNATURE_ROUNDS`(`ReportService.java:23`)는 외부 집계 시스템 요구값이라 바꾸지 않는다.
- 기대 효과: 커넥션 보유 시간이 DB 구간으로 줄어든다. 서명 구간(`ReportService.java:88` 루프)이 커넥션을 쥐지 않는다. 느린 `submission` 조회(T2)가 있으면 그 구간은 그대로 남는다.

**6-2. `modern/api/src/test/java/com/example/assignment/ReportServiceTest.java` — 테스트**

- 동작이 바뀌므로 테스트를 추가 · 수정한다(`CLAUDE.md` 의 테스트 규칙). 기존 `buildsAggregatedReport`(`ReportServiceTest.java:42`)는 서명 값이 같다는 것을 계속 확인하게 둔다.
- 새 테스트 예: `signsOutsideTransaction`, `@DisplayName("서명 계산은 트랜잭션 밖에서 한다")`. 서명 시점에 `TransactionSynchronizationManager.isActualTransactionActive()` 가 false 임을 확인한다.
- 실행: `cd modern/api && ./gradlew test`.

**6-3. 이번에 바꾸지 않는 것**

- `application.yml` 의 `maximum-pool-size` · `connection-timeout` · `leak-detection-threshold`: `CLAUDE.md` 3절이 `hikari` 설정 변경을 금지한다. 풀을 늘리면 점유 시간은 그대로이고 고갈 시점만 미뤄진다.
- `submission` 인덱스: 운영 DB 의 `EXPLAIN` 이 없다(9절). 스키마 변경은 사람에게 먼저 묻는다.

## 7. 재발 방지

같은 유형(트랜잭션 · 커넥션을 오래 쥐는 코드가 작은 풀을 고갈시킨다)을 구조적으로 막는 장치다. 위 6절의 수정이나 8절의 경보와 별개다.

| 장치 | 내용 | 승인 필요 |
|---|---|---|
| 규칙을 검사로 | `CLAUDE.md` 의 "`@Transactional` 안에서 반복 해시 · `Thread.sleep` · 외부 호출 금지"를 PR 마다 `convention-check` 로 점검한다. `@Transactional` 메서드에 `TODO: 트랜잭션 밖으로` 가 남은 채 머지하지 않는다. 이번 코드는 TODO 로 알려진 채 운영에 있었다. | 없음 |
| 무거운 경로 격리 | 리포트 같은 무거운 엔드포인트의 동시 실행 상한을 풀 크기보다 작게 둔다. 상한을 넘으면 기다리지 않고 즉시 503 으로 돌려, 다른 API 가 쓸 연결을 남긴다. | 없음(JDK `Semaphore`). 풀을 둘로 나누려면 `application.yml` 변경이라 사람 승인 |
| 반복 호출 흡수 | 이상 구간의 리포트 호출은 클라이언트 4곳에서 왔다. 같은 학급 결과를 일정 시간 재사용하거나 클라이언트 재시도 간격을 늘려 같은 계산이 겹치지 않게 한다. | 캐시 라이브러리는 의존성 추가라 사람 승인 |
| 배포 전 점유 시험 | 새로 만들거나 바꾼 엔드포인트는 스테이징에서 풀 크기(5) 이상으로 동시 호출해 풀 대기 ERROR 가 없는지 본 결과를 PR 에 붙인다. 테스트 코드는 실제 DB 에 접속하지 않는다(`CLAUDE.md`). | 없음 |
| 긴급 차단 수단 | 문제 경로만 nginx 에서 일시 차단하거나 제한하는 절차를 런북에 미리 적어 둔다. 이번에는 리포트 호출이 0건이 된 14:52 에 복구됐다(R3). | 운영팀 |

## 8. 모니터링 항목

다음에 더 빨리 알아채기 위한 지표다. 평소 수준은 1-3 의 값이다(`app.log` WARN+ERROR 분당 약 0.03건, 5xx 0건). 경보 시각은 이번 로그에 임계값을 적용한 결과다. 로그 이벤트는 찍힌 시각, 1분 집계는 그 분이 끝나는 시각이다. 사용자 영향 시작은 14:40:52 다.

| 지표 (출처) | 임계값 | 이번 타임라인 경보 시각 |
|---|---|---|
| Hikari 누수 경고 `ProxyLeakTask` WARN (`app.log`) | 1건 이상 (평소 0건) | **14:37:52** (`app.log:1243`). 영향보다 3분 빠르다 |
| DB 슬로우 쿼리 (`mariadb-slow.log`) | `Query_time` 5초 이상 (평소 최대 2.32초) | **14:37:52** (쿼리 종료 시각, `mariadb-slow.log:15`) |
| 풀 사용률 (active / max) | 4 / 5 이상이 2분 지속 | 판정 불가. 로그에 풀 지표가 없다. 가장 이른 증거는 14:40:52 의 `active=5`(`app.log:1522`) |
| 풀 대기 타임아웃 ERROR (`app.log`) | 1건 이상 (평소 ERROR 0건) | 14:40:52 (`app.log:1522`). 영향과 같은 시각이다 |
| 5xx 비율 (`nginx-access.log`, 1분) | 2% 이상 (평소 0%) | 14:41:00. 14:40 분은 42건 중 1건(2.4%)이다. 영향보다 8초 늦다 |
| 리포트 호출 수 (`nginx-access.log`, 경로별 1분) | 6건 이상 (평소 최대 2건) | 14:41:00. 14:40 분이 10건이다. 14:38 · 14:39 는 각 3건이라 미달이다 |
| nginx upstream 오류 (`nginx-error.log`) | `upstream timed out` 1건 이상 | 14:41:06 (`nginx-error.log:6`). `temporarily disabled` 는 14:42:01 |
| 요청 지연 p95 (access 로그 `$request_time`) | 3초 초과(풀 대기 `application.yml:15` 와 같게) | 판정 불가. access 로그에 `request_time` 이 없다 |

- 예고 경보(페이지 전 단계)는 누수 경고와 슬로우 쿼리다. 둘 다 영향 3분 전에 울린다. 영향 경보는 풀 대기 ERROR 와 5xx 비율이다.

## 9. 확인하지 못한 것

| 못 한 판단 | 이유 | 남겨야 할 로그 · 자료 |
|---|---|---|
| 점유 15.5 ~ 21.5초의 내역 | 서명 단독은 85 ~ 145ms 였고 슬로우 쿼리는 1건뿐이다. 나머지 시간(CPU 경합 · GC · 쿼리)을 로그로 못 갈랐다 | 이상 구간 CPU · GC 지표, 리포트 단계별(DB · 서명) 소요 시간 로그 |
| 느린 쿼리의 기여(H2) | 운영 인덱스와 `EXPLAIN` 이 없고 `long_query_time` 값을 모른다 | 운영 `SHOW INDEX` · `EXPLAIN`, 장애 구간의 쿼리별 실행 시간 |
| 리포트 호출이 늘어난 이유(H3) | 이전 날짜 로그가 없다. 호출 상위는 클라이언트 4곳이다 | 이전 날짜 `nginx-access.log`, 프런트 쪽 요청 · 재시도 로그 |
| 14:37 직전 변경(H5) | 배포 이력이 없다. 이 로그 범위에 재시작 · reload 흔적은 없다 | 장애일 앞뒤 배포 · 설정 변경 이력, 이전 날짜 `app.log` |
| `API-2` 가 연결을 거부한 이유 | 그 서버의 앱 로그와 nginx upstream 설정(`backup` · `max_fails`)이 없다 | `API-2` 의 `app.log`, nginx 설정 원문 |
| 복구 원인(R3) | 14:52 에 리포트 호출이 0건이 된 이유를 모른다(사용자 이탈 · 클라이언트 포기 · 운영 조치). 대응 기록이 없다 | 대응 타임라인(누가 몇 시에 무엇을 했는지), 클라이언트 쪽 로그 |
| 실제 감지 시각 | 이번에 누가 몇 시에 알았는지, 경보가 있었는지 모른다 | 경보 · 대시보드 이력 |
| 서버별 부하 | access 로그에 upstream 필드가 없어 서버별 요청 수를 못 센다 | nginx `log_format` 에 `$upstream_addr` · `$request_time` · `$upstream_response_time` |
| 풀 상태 시계열 | 풀 상태(`active` · `idle` · `waiting`)가 ERROR 줄에만 있다 | Hikari 풀 상태를 주기적으로 남기는 로그나 메트릭 |

- 로그를 공유할 때 `nginx-error.log` 의 `Authorization: Bearer …` 는 가린다(1절).

마스킹 적용: 유형별 건수 — IP 43건(서버 별칭 38 · 교사 단말 별칭 5 · 그 밖 0) · 이메일 0건 · 학생 식별자 0건 · 토큰 · 세션 ID · API 키 · Authorization 값 0건 · 비밀번호 · 접속 문자열 0건 (`<client>` 로 이전에 가려 둔 3줄은 제외)
