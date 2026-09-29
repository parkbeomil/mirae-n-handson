# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

- 답변과 문서(주석 · 커밋 메시지 · `docs/` 포함)는 한국어로 쓴다.
- Claude Code는 저장소 루트(`~/work/mirae-n-handson`)에서 실행한다. 아래 명령의 `cd` 경로는 루트 기준이다.

## 1. 빌드 · 테스트 명령

```bash
# modern/api — Spring Boot 3 · Java 21 · Gradle wrapper
cd modern/api && ./gradlew test                                        # DB 없이 통과한다(테스트 프로필 = H2)
cd modern/api && ./gradlew test --tests 'com.example.item.ItemServiceTest'  # 단일 테스트 클래스
cd modern/api && ./gradlew build                                       # 테스트 포함 전체 빌드
cd modern/api && ./gradlew bootRun     # 8080. 먼저 루트에서 docker compose --profile modern up -d 로 DB를 띄운다

# modern/web — React 18 · TypeScript · Vite · Vitest
cd modern/web && npm ci                                                # 처음 한 번. package-lock.json 기준 설치
cd modern/web && npm run lint && npm run typecheck && npm test         # 커밋 전 3종 검사
cd modern/web && npx vitest run src/components/ItemTable.test.tsx      # 단일 테스트 파일
cd modern/web && npx vitest run -t '<테스트 이름 일부>'                  # 이름으로 필터
cd modern/web && npm run dev                                           # 5173. /api 요청은 8080으로 프록시된다
cd modern/web && npm run build                                         # 배포용 빌드(dist/)
```

- `modern/api` 기동 확인: `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/` 가 `200`.
- 작업을 끝내면 바꾼 모듈의 검사를 실행하고 통과 · 실패 수를 답변에 적는다. api는 `./gradlew test`, web은 `lint` · `typecheck` · `test` 3종이다. 실행하지 않았으면 "실행하지 않음"이라고 쓴다. 하나라도 실패하면 "완료"라고 쓰지 않는다.
- 테스트가 실제 MariaDB나 실제 `localhost:8080` 에 접속하게 만들지 않는다.

## 2. 코딩 컨벤션

### modern/api — 계층
- **[팀 규칙] 컨트롤러는 서비스만 호출한다.** 컨트롤러 클래스에 `*Repository` 필드 · 생성자 파라미터, `JdbcTemplate`, SQL 문자열이 없어야 한다.
- 호출 순서는 `Controller` → `Service` → `Repository` → 엔티티다. 패키지는 도메인 단위(`com.example.item`, `com.example.assignment`)로 둔다.
- 서비스는 자기 도메인 패키지의 Repository만 주입받는다. 다른 도메인 데이터는 그 도메인의 Service를 주입받아 쓴다.
- 요청 · 응답 DTO는 `record` 로 선언한다. 컨트롤러 메서드의 반환 타입에 `@Entity` 클래스가 나오지 않는다.
- 새 조회 API는 같은 도메인 패키지에 컨트롤러 · 서비스 · 리포지토리 세 파일과 테스트를 만든다. 경로는 `/api/<도메인 복수형>` 아래에 둔다.

### modern/api — 예외 · 로깅
- **[팀 규칙] 예외를 삼키지 않는다.** `catch` 블록은 둘 중 하나를 한다.
  - `log` 호출 뒤 다시 던진다.
  - `GlobalExceptionHandler` 가 매핑하는 예외로 감싸 던진다(원인 `e` 를 생성자에 넘긴다).
  - 본문이 비었거나 주석만 있는 `catch` 는 금지한다.
- 예외 → HTTP 상태 변환은 `common/GlobalExceptionHandler`(`@RestControllerAdvice`)에서만 한다. 컨트롤러 메서드 안에 `try` · `ResponseEntity.status(...)` 를 쓰지 않는다.
- 상태 매핑은 아래와 같다. 새 예외 유형은 이 핸들러에 `@ExceptionHandler` 로 추가한다.
  - `NotFoundException` → 404
  - `IllegalArgumentException` · 검증 실패 → 400
  - `IllegalStateException` → 409
  - 그 밖의 예외 → 500
- **[팀 규칙] 로그는 SLF4J(`org.slf4j.Logger`)로만 남긴다.** `System.out` · `System.err` · `printStackTrace()` 가 코드에 없어야 한다.
- 로그 인자에 학생 식별자(`STU-…`) · 이메일 · 토큰 값을 넣지 않는다. 대상은 내부 숫자 id로 남긴다.
- 로그 레벨은 `GlobalExceptionHandler` 를 기준으로 한다.
  - 404 · 400 응답은 `INFO`, 409 응답은 `WARN`, 500 응답은 `ERROR`(예외 객체를 마지막 인자로)다.
  - 그 밖의 정상 흐름은 `INFO` 이하다.

### modern/api — 테스트
- 새 public 서비스 메서드 · 새 엔드포인트 · 동작을 바꾼 메서드마다 테스트를 1개 이상 추가하거나 수정한다.
- 계층별 테스트 방식은 다음과 같다.
  - 컨트롤러: `@WebMvcTest`
  - 서비스: Mockito 단위 테스트(`@ExtendWith(MockitoExtension.class)`)
  - 리포지토리 쿼리: `@DataJpaTest`(H2)
- 테스트 메서드 이름은 검증하는 동작을 camelCase로 쓴다(예: `rejectsOtherOrigins`). `@DisplayName` 에는 한국어 설명을 붙인다.

### modern/api — 이름 · 형식
- 클래스는 `PascalCase`, 메서드 · 필드는 `camelCase`, 상수는 `UPPER_SNAKE_CASE` 로 쓴다. 약어는 `ItemDto` 처럼 첫 글자만 대문자로 쓴다.
- 새로 쓰는 비교 · 계산의 숫자 리터럴(0 · 1 제외)은 `private static final` 상수로 뺀다(예: `MAX_LEVEL = 5`).
- 들여쓰기는 스페이스 4칸, 한 줄은 120자 이내로 쓴다. `import ….*;`(와일드카드) 금지.

### modern/web
- 컴포넌트는 `function` 선언의 named export 로 만든다. `class` 컴포넌트, `React.FC`, `defaultProps`, default export를 쓰지 않는다.
- props 타입은 같은 파일에 `interface <컴포넌트명>Props` 로 선언한다.
- `any`, `as unknown as`, `@ts-ignore` 를 쓰지 않는다. `npm run lint` 가 `no-explicit-any` 를 error로 잡는다.
- `import type` 으로 타입을 가져온다(`consistent-type-imports`). `eslint-disable` 주석에는 같은 줄에 사유를 적는다.
- HTTP 요청은 `src/api/client.ts` 의 `getJson` 을 거친다. `fetch(` 는 `client.ts` 에만 있어야 한다.
- 엔드포인트별 함수는 `src/api/items.ts` 에 두고, 응답 타입은 `src/api/types.ts` 에 백엔드 JSON 필드명 그대로 둔다.
- 컴포넌트는 서버 데이터를 `src/hooks/use*.ts` 훅(`useApiQuery` 기반)으로만 읽는다.
- 로딩 · 오류 분기는 `AsyncSection` 으로 한다. 목록 컴포넌트는 배열 길이 0일 때 표시할 문구를 렌더한다.
- `src/components/` 안에서 `ApiError.status` 를 비교하지 않는다. 오류 문구 변환은 `src/hooks/useApiQuery.ts` 의 `toErrorMessage` 한 곳에서만 한다.
- props나 다른 상태로 계산되는 값은 `useState` 에 저장하지 않는다. `useEffect` 안에서 다른 상태를 `set` 하는 동기화 코드를 쓰지 않는다.
- 추가하거나 동작을 바꾼 컴포넌트 · 훅에는 같은 폴더의 `<파일명>.test.tsx`(훅은 `.test.ts`)에 테스트가 있어야 한다.
  - 테스트는 Testing Library로 쓴다. 조회는 `getByRole` · `getByLabelText` 를 먼저 쓴다.
  - 네트워크는 `src/test/mockFetch.ts` 로 가로챈다.
  - 스냅샷(`toMatchSnapshot`)을 쓰지 않는다.
- 이벤트 prop은 `on<동작>`, 컴포넌트 안 핸들러 함수는 `handle<동작>` 으로 이름 짓는다. 훅 이름은 `use` 로 시작한다.

## 3. 금지 사항

- `legacy/` 는 분석 · 이관 대상이다. 허락 없이 수정하지 않는다.
- **[팀 규칙] 의존성 추가와 DB 스키마 변경은 먼저 사람에게 묻는다.** 이유와 대안을 함께 적는다.
  - 의존성: `modern/api/build.gradle` 의 `dependencies`, `modern/web/package.json` 의 `dependencies` · `devDependencies`
  - 스키마 · 데이터: 테이블 · 컬럼 · 인덱스 변경, 마이그레이션 파일 추가, 시드 데이터 변경
- `package-lock.json` 을 손으로 고치거나 지우지 않는다. 설치는 `npm ci` 로 한다.
- `modern/api/src/main/resources/application.yml` 의 DB 접속 정보와 `hikari` 설정을 바꾸지 않는다.
- 운영 계정 · 토큰 · 키 값을 코드나 설정 파일에 리터럴로 넣지 않고, `.env*` 파일을 만들지 않는다. 실습용 더미 `app-pass` 는 예외다.
- 운영 DB 호스트(`prod-db` 등)에 접속하는 명령 · 설정을 만들지 않는다.
- `@Transactional` 메서드 안에서 다음을 하지 않는다. DB 작업이 끝난 뒤의 계산은 트랜잭션 밖 메서드로 옮긴다.
  - 외부 HTTP 호출(`RestTemplate` · `RestClient` · `WebClient`)
  - `Thread.sleep`
  - 반복 해시 같은 CPU 반복 연산
- `dangerouslySetInnerHTML` 을 쓰지 않는다. `console.log` 를 커밋에 남기지 않는다.
- 요청에 없는 파일은 수정하지 않는다. 포맷팅 · import 정렬 · 이름 변경도 여기에 포함된다. 어쩔 수 없이 고쳤다면 파일과 이유를 답변에 적는다.
- `characterization/` 의 테스트 · 스냅샷을 깨뜨리는 변경은 먼저 사람에게 알린다.

## 4. 아키텍처 안내

저장소 지도:

- `modern/api` · `modern/web` — 현행 코드. 이 파일의 컨벤션이 적용되는 대상이다.
- `legacy/` — 이관 대상 레거시 세 모듈(PHP · Thymeleaf · MS-SQL). docker compose 프로필로 8081~8083에서 띄운다.
- `characterization/` — 레거시 동작 보존(스냅샷) 테스트 하네스.
- `templates/` · `vendor-prs/` · `incident-logs/` · `specs/` 등은 실습 자료다(읽기 전용).

`modern/api` (`com.example`):

```
common/      RootController(GET /), GlobalExceptionHandler, ErrorResponse, NotFoundException, ClockConfig
config/      WebConfig (CORS: 5173 출처의 GET만 허용)
item/        ItemController  GET /api/items/{id}, GET /api/units/{code}/items
             UnitController  GET /api/units
assignment/  DistributionController  GET /api/distributions/{id}, POST /api/distributions/{id}/redistribute
             ReportController        GET /api/classes/{id}/report
```

- 설정은 두 파일로 나뉜다.
  - `src/main/resources/application.yml`: 로컬 MariaDB, `maximum-pool-size: 5`(트랜잭션을 오래 쥐면 금방 고갈된다)
  - `src/test/resources/application-test.yml`: H2, MariaDB 모드
- 문항 공개 여부는 삭제 플래그가 아니라 `status = 'A'` 로 판단한다.
  - 목록 조회(`/api/units/{code}/items`)는 공개 문항만 돌려준다.
  - 단건 조회(`/api/items/{id}`)는 상태와 무관하게 찾는다.
- `redistribute` 는 v1.4.2부터 같은 학급의 배포 이력 전체(`history`)를 함께 돌려준다.
- 레거시 규칙을 옮길 때는 근거를 `파일:줄번호` 로 답변에 적는다(예: `legacy/item-bank-php/search.php:214`).

`modern/web` (`src/`):

```
api/         client.ts(getJson · ApiError · API_BASE) · items.ts(엔드포인트 함수) · types.ts(응답 타입)
hooks/       useApiQuery(공통 조회 · AbortSignal 취소) · useUnits · useUnitItems · useItem
components/  ItemBrowser(화면 조립) · AsyncSection · UnitList · ItemTable · ItemDetail(Panel) · 배지
test/        mockFetch.ts(fetch 가짜) · fixtures.ts
```

- 데이터 흐름: `components` → `hooks/use*` → `api/items.ts` → `getJson` → `modern/api`.
- `VITE_API_BASE` 를 빈 값으로 두면 상대 경로로 요청해 Vite 프록시를 탄다. 설정하지 않으면 `http://localhost:8080` 으로 직접 요청한다.
- 도메인 용어는 백엔드와 같다: 문항 `item`, 단원 `unit`, 난이도 `level`(1~5), 태그 `tag`, 학급 `class`, 과제 `assignment`, 배포 `distribution`, 제출 `submission`.
- 아래 항목은 실습 중에 만드는 대상이라 지금 없는 게 정상이다.
  - `/api/items/search` 엔드포인트
  - `.claude/skills` · `.claude/agents` · `.claude/settings.json`
  - `hooks/*.mjs`
  - `characterization/tests/*.test.js`

## 5. 완료 기준

- 이관 · 리팩토링 작업은 `cd characterization && npm test` 가 전부 통과하기 전에는 완료라고 보고하지 않는다.
- 테스트가 실패하면 실패한 케이스와 기대값 · 실제값 차이를 그대로 보고한다. 요약해서 "거의 됐다"고 말하지 않는다.
- 테스트를 통과시키려고 `characterization/` 의 테스트 코드나 스냅샷 파일을 고치지 않는다. 스냅샷을 바꿔야 한다고 판단되면 멈추고 사람에게 묻는다.
- 레거시 동작이 버그로 보여도 이관 중에는 고치지 않는다. "의심 동작" 목록으로 따로 보고한다.
