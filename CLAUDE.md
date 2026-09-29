# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 저장소 개요

Claude Code 심화 과정(4회차) 실습 저장소. 코드 · 데이터 · 로그는 전부 **더미 에듀테크 도메인**(문항 은행 · 과제 배포 · 성적 집계)이며 실제 서비스가 아니다.

- 포크 기반 작업 흐름: `origin` = 내 포크, `upstream` = 원본. 회차 사이 보강 자료는 `git fetch upstream && git merge --no-edit upstream/main` 으로 받는다.
- Claude Code는 항상 저장소 루트(`~/work/mirae-n-handson`)에서 실행한다. `CLAUDE.md`, `.claude/`, `hooks/`, `docs/` 는 전부 루트 기준으로 생긴다.
- 도메인 용어: 문항 `item` · 단원 `unit` · 난이도 `level`(1~5) · 태그 `tag` / 학급 `class` · 과제 `assignment` · 배포 `distribution` · 제출 `submission` / 학생 식별자 `STU-<숫자>`.

## 모듈 구성

| 모듈 | 위치 | 스택 | 주소 |
|---|---|---|---|
| 문항 은행 (레거시) | `legacy/item-bank-php/` | PHP 7.4 + MariaDB 10.11 | http://localhost:8081 |
| 과제 배포 (레거시) | `legacy/assignment-thymeleaf/` | Spring MVC + Thymeleaf + JDBC | http://localhost:8082 |
| 성적 집계 (레거시) | `legacy/grade-mssql/` | MS-SQL 저장 프로시저 + 얇은 Java 호출부 | http://localhost:8083 |
| 현행 API | `modern/api/` | Spring Boot 3 · Java 21 · Gradle | http://localhost:8080 (로컬 실행) |
| 현행 화면 | `modern/web/` | React 18 · TypeScript · Vite | http://localhost:5173 (로컬 실행) |

세 레거시 모듈은 이관 대상, `modern/*`는 팀 컨벤션의 기준이다. `modern/api`에는 이미 문항 · 단원 조회 API와 과제 배포 · 학급 리포트 API가 있다(아래 패키지 구조 참고). 다만 `/api/items/search`(레거시 `search.php`에 대응하는 이관 엔드포인트)는 아직 없다 — 실습 중에 만드는 대상이라 없는 게 정상이다.

### `modern/api` 패키지 구조

```
com.example
├── ItemBankApplication.java   부트 클래스
├── common/    RootController(GET /, 헬스체크), GlobalExceptionHandler, ErrorResponse, NotFoundException, ClockConfig
├── config/    WebConfig
├── item/      Item · Unit · Tag 도메인
│   ├── ItemController    GET /api/items/{id}, GET /api/units/{code}/items(공개 문항만)
│   └── UnitController    GET /api/units
└── assignment/  Assignment · ClassRoom · Distribution · Submission 도메인
    ├── DistributionController  GET /api/distributions/{id}, POST /api/distributions/{id}/redistribute
    └── ReportController        GET /api/classes/{id}/report
```

- `DistributionController.redistribute`는 v1.4.2에서 응답 모양이 바뀌었다: 재배포된 건 하나만이 아니라 같은 학급의 배포 이력 전체(`history`)를 함께 돌려준다(코드 주석에 버전 이력이 남아 있다).
- `application.yml`의 HikariCP 풀은 운영 값과 맞춰 일부러 작다(`maximum-pool-size: 5`, `leak-detection-threshold: 10000`) — 트랜잭션을 오래 쥐는 코드가 있으면 금방 고갈된다. `incident-logs/a-connection-pool/`이 바로 이 유형의 장애 시나리오다.
- 설정 파일: `src/main/resources/application.yml` = 기본 프로필(로컬 MariaDB, 커넥션 풀 설정 포함), `src/test/resources/application-test.yml` = 테스트 프로필(H2, MariaDB 모드).
- 새 엔드포인트는 `/api/<도메인 복수형>` 아래에 둔다.
- 문항 공개 여부는 삭제 플래그가 아니라 상태 코드(`status = 'A'`)로 판단한다. 목록 조회(`GET /api/units/{code}/items`)는 공개 문항만 노출하고, 단건 조회(`GET /api/items/{id}`)는 상태와 무관하게 찾는다(관리 화면용).
- 새 조회 API 는 컨트롤러 → 서비스 → 리포지토리 세 파일과 테스트를 같은 도메인 패키지에 만든다.
- 레거시 규칙을 옮길 때는 근거를 `파일:줄번호` 로 답변에 적는다(예: `legacy/item-bank-php/search.php:214`).

`modern/web`은 이 중 조회 API만 호출한다(`src/api/items.ts`): `fetchUnits` → `GET /api/units`, `fetchUnitItems` → `GET /api/units/{code}/items`, `fetchItem` → `GET /api/items/{id}`. 개발 서버(Vite, 5173)는 `/api`를 8080으로 프록시하므로 `VITE_API_BASE`를 비워 두면(`vite.config.ts`) CORS 설정 없이도 붙는다.

## `modern/api` 코딩 컨벤션

### 계층 규칙
- 패키지는 도메인 단위: `com.example.item`, `com.example.assignment`. 한 도메인 안에 `Controller` → `Service` → `Repository` → 엔티티 순으로 호출한다.
- 컨트롤러는 서비스만 호출한다. **컨트롤러에서 Repository 를 주입받거나 SQL 문자열을 직접 실행하는 것은 금지**한다.
- 서비스가 다른 도메인의 데이터를 쓸 때는 그 도메인의 서비스를 통한다. 다른 도메인의 Repository 를 직접 주입하지 않는다.
- 요청 · 응답은 record 기반 DTO 를 쓴다. 엔티티를 컨트롤러 밖으로 그대로 반환하지 않는다.

### 예외 처리
- **예외를 잡고 아무 처리 없이 넘기는 빈 `catch` 블록은 금지**한다. 잡았으면 로그를 남기고 다시 던지거나, 의미 있는 도메인 예외로 바꿔 던진다.
- 예외 → HTTP 응답 변환은 `@ControllerAdvice` 한 곳에서만 한다. 컨트롤러 메서드 안의 try-catch 로 상태 코드를 만들지 않는다.
- 없는 리소스는 404, 검증 실패는 400, 그 밖의 예상 못 한 예외는 500 으로 매핑한다.

### 로깅
- 로그는 SLF4J(`org.slf4j.Logger`)로만 남긴다. **`System.out.println` · `System.err.println` · `e.printStackTrace()` 는 금지**한다.
- 로그에 학생 식별자(`STU-…`) · 이메일 · 토큰 값을 그대로 찍지 않는다.
- 로그 레벨: 정상 흐름은 `INFO` 이하, 복구 가능한 실패는 `WARN`, 요청을 실패시키는 예외는 `ERROR`.

### 테스트
- **동작을 바꾸는 변경에는 대응하는 테스트가 있어야 한다.** 새 public 서비스 메서드 · 새 엔드포인트마다 테스트 1개 이상.
- 컨트롤러는 `@WebMvcTest` 슬라이스 테스트, 서비스는 Mockito 단위 테스트, 리포지토리 쿼리는 `@DataJpaTest`(H2) 로 검증한다.
- 테스트 메서드 이름은 확인하는 동작이 드러나는 camelCase(예: `rejectsOtherOrigins`)로 짓고, `@DisplayName` 에 한국어 설명을 붙인다. 기존 테스트의 형식을 따른다.
- 스냅샷 · 동작 보존 테스트(`characterization/`)를 깨뜨리는 변경은 먼저 사람에게 알린다.

### 이름 · 형식
- 클래스 `PascalCase`, 메서드 · 필드 `camelCase`, 상수 `UPPER_SNAKE_CASE`. 약어도 `ItemDto` 처럼 첫 글자만 대문자.
- 매직 넘버는 이름 붙인 상수로 뺀다(예: 난이도 상한 `MAX_LEVEL = 5`).
- 들여쓰기 4칸, 한 줄 120자 이내. 와일드카드 import 금지.

## `modern/api` 금지 사항

- `legacy/` 는 분석 · 이관 대상이다. 허락 없이 수정하지 않는다.
- **DB 스키마 변경 금지**: 테이블 · 컬럼 추가 · 삭제, 인덱스 변경, 마이그레이션 파일 추가는 먼저 사람에게 묻는다. 시드 데이터도 마찬가지다.
- 의존성 추가(`build.gradle` 의 `dependencies` 변경)는 먼저 묻는다. 이유와 대안을 함께 적는다.
- `application.yml` 의 DB 접속 정보 · 커넥션 풀 설정을 바꾸지 않는다. 실제 비밀값(운영 계정 · 토큰 · 키)을 코드나 설정 파일에 리터럴로 넣지 않는다. 실습용 더미 값(`application.yml` 의 `app-pass`)은 예외다.
- 운영 DB 호스트(`prod-db` 등)에 접속하는 명령 · 설정을 만들지 않는다.
- `@Transactional` 안에서 외부 HTTP 호출이나 긴 루프를 돌리지 않는다.
- 요청받지 않은 파일을 "정리" 명목으로 고치지 않는다. 포맷팅 · import 정리도 요청 범위 안의 파일에서만 한다.

## `modern/api` 완료 기준

작업을 "끝났다"고 보고하려면 아래를 모두 만족해야 한다.

- [ ] `cd modern/api && ./gradlew test` 가 통과했고, 통과 · 실패 수를 답변에 적었다
- [ ] 바꾼 동작마다 대응하는 테스트가 추가 · 수정되었다
- [ ] 컨트롤러에 Repository 주입 · SQL 문자열이 없고, 빈 `catch` 와 `System.out.println` 이 없다
- [ ] 변경 파일 목록이 요청 범위 안에 있다(요청에 없는 파일을 고쳤다면 이유를 적었다)
- [ ] 스키마 · 의존성 · 설정 변경이 없거나, 있었다면 사전에 승인받았다

## 명령

```bash
# 기동 · 확인 · 정리 — down · build · logs 도 전부 프로필을 붙인다
docker compose --profile php up -d          # 문항 은행 + MariaDB
docker compose --profile thymeleaf up -d    # 과제 배포 + MariaDB
docker compose --profile mssql up -d        # 성적 집계 (Apple Silicon: 느리거나 안 뜰 수 있음)
docker compose --profile modern up -d       # 현행 샘플용 MariaDB만
docker compose ps
docker compose --profile php down

# 현행 샘플
cd modern/api && ./gradlew test             # DB 없이 통과해야 함
cd modern/api && ./gradlew test --tests 'com.example.item.ItemServiceTest'   # 단일 테스트 클래스
cd modern/api && ./gradlew bootRun          # 먼저 docker compose --profile modern up -d 로 DB를 띄운다
cd modern/api && ./gradlew build            # 테스트 포함 전체 빌드
cd modern/web && npm ci && npm run dev
cd modern/web && npm run lint && npm run typecheck && npm test
cd modern/web && npx vitest run src/components/ItemTable.test.tsx           # 단일 테스트 파일
cd modern/web && npx vitest run -t '<테스트 이름 일부>'                       # 이름으로 필터

# 동작 보존 테스트
cd characterization && npm ci
cd characterization && npm run baseline -- <모듈명>          # item-bank | assignment | grade
cd characterization && npm test                               # 대상: 레거시(기본 포트)
cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test   # 대상: 새 API
cd characterization && npx vitest run tests/normalize.unit.test.js      # 정규화 도우미 단위 테스트, 서비스 없이 돈다

bash scripts/check-env.sh   # 환경 셀프 점검
```

`modern/api` 빌드 · 테스트:
- 서버 포트는 8080. 기동 확인은 `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/`.
- 테스트 설정은 `src/test/resources/application-test.yml`. 테스트가 실제 MariaDB에 붙게 만들지 않는다.
- 작업이 끝나면 반드시 `./gradlew test` 를 실행하고, 실행 결과(통과 · 실패 수)를 답변에 적는다. 실행하지 않았으면 "실행하지 않음"이라고 쓴다.

`php` · `thymeleaf` · `modern` 프로필은 MariaDB 서비스 하나(`itembank` DB)를 공유하고 데이터는 tmpfs에 있다. `down` 후 `up`이면 항상 같은 시드 상태로 돌아온다. DB 계정은 두 종류다: README에 문서화된 `readonly`/`readonly-pass`는 참가자가 직접 조회할 때 쓰는 SELECT 전용 계정이고, `modern/api`(`application.yml`)와 레거시 컨테이너들이 실제로 붙는 계정은 `app`/`app-pass`(쓰기 가능, `docker-compose.yml`·`application.yml` 안에서만 쓴다).

`.claude/settings.json`에 hook을 연결할 때는 `scripts/hook-node.sh`를 거친다(`bash "$CLAUDE_PROJECT_DIR"/scripts/hook-node.sh hooks/<파일>.mjs`) — PATH에 `node`가 없는 셸에서도 hook이 조용히 꺼지지 않도록 node를 못 찾으면 명령 자체를 막는다(fail-closed).

## 동작 보존 테스트 하네스 (`characterization/`)

레거시 이관 작업의 핵심 도구. 레거시의 **지금 동작**을 스냅샷으로 고정해 두고, 이관 후 같은 입력으로 다시 실행해 결과가 같은지 비교한다. 기대값을 손으로 적지 않고 스냅샷 하나로 판정한다.

- `lib/target.mjs`: 대상 주소는 이 파일의 `DEFAULT_BASE_URLS` **한 곳에만** 있다. `TARGET_BASE_URL` 환경변수가 있으면 그쪽을 쓴다. 이관하며 경로가 바뀐 화면은 `PATH_ALIASES`에 등록한다(예: `/search.php` → `/api/items/search`) — 테스트 코드 자체는 레거시 경로로 그대로 두고, 대상이 404를 돌려주면 대응표의 새 경로로 재요청한다.
- `lib/normalize.mjs`: HTML(`<table id="...">`)이든 JSON이든 `{status, rows[], count, message}` 한 모양으로 정규화한다. 실행마다 바뀌는 값(`timestamp`, `requestId`, `sessionId`, `token` 등, `VOLATILE_KEYS`)은 제거한다.
- 테스트 파일은 `tests/<모듈명>.test.js` (모듈명은 폴더명이 아니라 `item-bank` · `assignment` · `grade`). 스냅샷은 `__snapshots__/`에 커밋해서 기준으로 쓴다.
- 이관 후 비교는 테스트 · 스냅샷을 그대로 두고 `TARGET_BASE_URL`만 바꾼다. 실패하면 고치는 곳은 이관 코드이지 테스트나 스냅샷이 아니다.
- 주소를 테스트 코드에 직접 적지 않는다: `grep -rn "localhost:808" lib tests`가 `lib/target.mjs`의 세 줄만 찍어야 한다.

## `mcp-skeleton/` — TypeScript MCP 서버 골격

stdio로 동작하며 외부 서비스 없이 메모리 안 더미 문항으로 응답한다.

- **SDK v2 기준** (`@modelcontextprotocol/server@2.0.0`, `zod@4.6.5`). 인터넷 예제 대부분은 v1(`@modelcontextprotocol/sdk`)이라 import 경로와 `inputSchema` 모양이 달라 그대로 붙이면 빌드가 깨진다.
- 도구는 `src/index.ts`의 "여기에 도구를 등록합니다" 주석 아래 `server.registerTool(...)`로 추가하고 `npx tsc`로 빌드한다.
- 로그는 `console.error`만 쓴다. `console.log`는 stdout(프로토콜 채널)을 깨뜨린다.
- 더미 문항의 `id`·단원 코드는 `itembank` DB와 같지만, 난이도는 DB의 정수 `level`(1~5)이 아니라 문자열 `하`·`중`·`상`이다.

## `templates/` — 팀별 CLAUDE.md 및 검증 절차 템플릿

이 루트 `CLAUDE.md`는 공통 뼈대다. 스택별 세부 규칙은 `templates/CLAUDE.{spring,react,legacy-php,data-pipeline,iac}.md`에 따로 있고, 팀 사정에 맞게 고쳐 이 파일로 옮겨 합치는 용도다(각 파일이 어느 실습 자료를 재료로 쓰는지는 파일 안에 적혀 있다). `templates/verification-loop.md` · `approval-checklist.md` · `secure-coding-checklist.md`는 AI가 만든 변경분·외주 PR을 머지하기 전 검증 절차 초안(v0, 골격)이며, 실습 중 팀 기준을 반영해 v1로 올리는 대상이다.

## 그 밖의 실습 자료 (읽기 전용, 원본 유지)

- `vendor-prs/`: 외주 PR patch 3건(테스트 누락 · 하드코딩된 시크릿 · 범위 초과) — 리뷰 실습용이며 적용하지 않는다.
- `incident-logs/`: 장애 로그 시나리오 a~d(커넥션 풀 고갈 · 배포 후 5xx · 배치 중복 적재 · MS-SQL 데드락). 더미지만 IP·이메일·토큰처럼 보이는 값이 섞여 있다. 원본 로그는 고치지 않고, 분석 결과는 `docs/rca/<폴더 이름>.md`에 쓴다.
- `pipeline-samples/`: 배치 로그 · 적재 건수 CSV · Terraform 예시.
- `ci-ports/`: GitLab CI(`.gitlab-ci.yml`) → Jenkins 이식용 예시(`Jenkinsfile`).
- `specs/`: 신규 개발 요구사항 스펙(기획팀 초안, 개발 검토 전) + Java/Python 시작 골격(`specs/starters/`).

## 이 저장소에 의도적으로 없는(또는 최소만 있는) 것

아래는 참가자가 실습 중에 직접 만드는 것이라 저장소에 미리 채워져 있지 않다. 존재하지 않는다고 해서 누락된 게 아니다: `.claude/skills/*/SKILL.md`, `.claude/agents/*.md`, `.claude/settings.json`, `hooks/block-dangerous.mjs` · `hooks/secret-scan.mjs`, `.mcp.json` · `dbhub.toml`, `.github/workflows/pr-review.yml`, `modern/api`의 `/api/items/search` 엔드포인트, `characterization/tests/<모듈>.test.js`와 그 스냅샷, 루트 `.env`(권한 실습 더미는 `.env.perm-test`). `docs/`는 완전히 비어 있지는 않다 — `docs/approval-checklist.md`가 `templates/approval-checklist.md`와 동일한 내용으로 이미 들어 있다(v0 골격을 옮겨 둔 상태로 보인다).
