# 검증 결과: 레거시 엔드포인트 1개 이관 (migration-review-1)

- 검증 대상: `git diff upstream/main...HEAD` (브랜치 `day2`, HEAD `5418388`, 30개 파일 · +4551줄)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 검증 방법: `/verify` Skill(`.claude/skills/verify/SKILL.md`) 절차. 기준 문서는 `templates/approval-checklist.md`(v0).
- 코드는 수정하지 않았다. 이 문서만 새로 썼다(같은 경로의 이전 판을 이번 실행 결과로 교체했다).
- 이관 대상은 `legacy/item-bank-php/search.php` → `GET /api/items/search` 로 확인했다(커밋 `bd38576`).

## 판정: 반려

반려 사유는 "요청 범위 이탈 없음" 미충족 한 가지다. 이 미충족은 diff 대상 `upstream/main...HEAD` 에 이관 외 변경이 같이 들어 있어서 나온 것이다("요청 범위" 절). 치명 이슈는 0건이고, 사람이 수용 · 기각을 정해야 하는 경고가 4건 있다.

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 (충분성은 확인 필요) | `cd modern/api && ./gradlew cleanTest test` 72건 통과 · 실패 0 · 건너뜀 0. `cd characterization && npm test` 33건 통과. `TARGET_BASE_URL=http://localhost:8080 npm test` 32건 통과 · 1건 건너뜀 |
| 치명 이슈 0건 | 충족 | 치명 0건. 조건 값은 모두 바인딩(`modern/api/src/main/java/com/example/item/ItemSearchRepository.java:57-73`), 비밀 · 토큰 리터럴 없음, 삭제 · 덮어쓰기 코드 없음 |
| 요청 범위 이탈 없음 | 미충족 | 요청 "엔드포인트 1개 이관" vs 변경 30개 파일 중 15개가 설명되지 않음. "요청 범위" 절 참고 |
| 컨벤션 준수 | 충족 | 위반 0건(`convention-check` Skill 은 호출하지 않고 직접 대조) |

## 테스트 실행 결과

| 대상 | 명령 | 통과 | 실패 | 건너뜀 |
|---|---|---|---|---|
| modern/api | `cd modern/api && ./gradlew cleanTest test` | 72 | 0 | 0 |
| characterization(레거시, 8081) | `cd characterization && npm test` | 33 | 0 | 0 |
| characterization(새 API, 8080) | `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` | 32 | 0 | 1 |

- `cleanTest` 를 붙여 캐시(`UP-TO-DATE`)를 피하고 실제로 다시 실행했다. 결과는 `build/test-results/test/*.xml` 합계다.
- `ItemSearch*` 3개 클래스는 `ItemSearchServiceTest` 38건, `ItemSearchRepositoryTest` 7건, `ItemSearchControllerTest` 3건이다. 전부 통과했다.
- `ItemSearchRepositoryTest` 는 `@ActiveProfiles("test")` 로 H2 를 쓴다(`modern/api/src/test/java/com/example/item/ItemSearchRepositoryTest.java:25`, 프로필 정의는 `modern/api/src/test/resources/application-test.yml`). 실제 MariaDB 에 접속하지 않는다.
- 건너뛴 1건은 `characterization/tests/example-units.test.js:21` 의 `it.skipIf(legacyOnly)` 다. 이 diff 에서 바뀐 파일이 아니다.
- `localhost:8080` 은 `GET /` 가 200 이고 프로세스 시작이 2026-10-06 12:34 이다. `modern` 의 마지막 변경 커밋 `bd38576`(2026-09-29)보다 나중이고, `git diff --stat bd38576..HEAD -- modern` 이 비어 있다. 따라서 8080 은 현재 `modern` 소스로 기동된 것으로 본다(`bootRun` 이 시작 때 컴파일한다는 전제의 추정).
- `characterization/__snapshots__/item-bank.test.js.snap` 과 `characterization/tests/item-bank.test.js` 는 baseline 커밋 `c3902e0` 이후 수정되지 않았다. 이 diff 에서는 새로 추가된 파일(A)이다.

## 항목별 대조

항목 문구는 `templates/approval-checklist.md` 를 따른다. 여기서는 번호(기준 번호-순서)와 결과만 적는다.

| 항목 | 결과 | 비고 |
|---|---|---|
| 1-1 · 1-2 · 1-4 | 충족 | 위 실행 결과. 기존 테스트의 삭제 · `@Disabled` · `skip` 변경 없음(`@Disabled` 검색 결과 없음) |
| 1-3 (테스트 충분) | 확인 필요 | 새 테스트 3종이 계층별 방식(`@WebMvcTest` · Mockito · `@DataJpaTest`)을 따르고 `@Test` 수와 `@DisplayName` 수가 같다. 다만 이관 회고 표의 차이 13건을 테스트가 못 잡았다(이슈 #5) |
| 2-1 · 2-2 · 2-3 | 충족 | 리터럴 비밀 없음. `LIMIT`/`OFFSET` 이어붙임은 정수뿐이라 주입 경로가 아니다(이슈 #6). 삭제 · 덮어쓰기 코드 없음 |
| 2-4 | 충족 | 치명 0건 |
| 3-1 | 충족 | 요청 문장은 호출 인자로 받았다 |
| 3-2 · 3-3 | 미충족 | "요청 범위" 절 |
| 3-4 | 충족 | `build.gradle` · `package.json` · `application.yml` · 마이그레이션 · 시드 파일은 diff 에 없다 |
| 4-1 | 충족(수동) | `CLAUDE.md` 항목을 직접 대조했다. `ItemSearch*.java` 6+3개에 와일드카드 import · `System.out` · `printStackTrace` · `catch` 블록이 없다. 120자 초과 줄도 없다(바이트가 아니라 문자 수로 확인). 로그는 SLF4J `debug` 한 줄이고 학생 식별자를 넣지 않는다(`ItemSearchService.java:52`). `legacy/` 수정 없음. 테스트 메서드는 camelCase + 한국어 `@DisplayName` |
| 4-2 | 충족 | `ItemSearchController.java:17-27` 은 `ItemSearchService` 만 호출한다 |
| 4-3 | 충족 | `catch` 블록 자체가 없다 |
| 4-4 | 충족 | `modern/api` 와 `characterization/tests/item-bank.test.js` 에 임시 출력이 없다 |
| 4-5 (팀 스타일) | 확인 필요 | 객관 기준이 없는 항목이라 위반을 찾지 못했다는 뜻으로만 읽는다 |

## 요청 범위

변경 파일 30개 중 "엔드포인트 1개 이관"으로 설명되는 파일은 다음 15개다.

- 새 API 소스 6개와 테스트 3개: `modern/api/src/main/java/com/example/item/ItemSearch*.java` · `modern/api/src/test/java/com/example/item/ItemSearch*Test.java`
- 동작 보존 테스트 2개: `characterization/tests/item-bank.test.js` · `characterization/__snapshots__/item-bank.test.js.snap`
- 이관 근거 문서 4개: `docs/item-bank/ARCHITECTURE.md` · `BUSINESS-RULES.md`(이관 회고 절 포함) · `CROSS-CHECK.md` · `ERD.md`. 이 4개는 이관 이전(`7132129`, `d202067`)에 분석용으로 만든 문서라 "이관 근거"로 넣는 것은 판단이다. 빼면 설명되지 않는 파일이 19개로 늘어도 판정은 같다.

나머지 15개 파일은 요청 문장으로 설명되지 않는다.

| 파일 | 성격 |
|---|---|
| `CLAUDE.md` | 팀 규칙 문서 |
| `docs/assignment/ARCHITECTURE.md` · `BUSINESS-RULES.md` · `ERD.md`, `docs/handoff/skill-candidates.md` | 다른 모듈 분석 문서와 핸드오프 |
| `.claude/skills/convention-check/SKILL.md`, `.claude/skills/document-module/SKILL.md`, `.claude/skills/verify/SKILL.md` | Skill 3개 |
| `.claude/failure-log.md`, `.claude/mistake-patterns.md` | 실수 기록 |
| `.claude/.DS_Store`, `.claude/skills/.DS_Store`, `.claude/skills/convention-check.zip`, `.claude/tmp/.reviewer-ran`, `.claude/skills/convention-check/.claude/tmp/.reviewer-ran` | 편집기 · 임시 · 압축본 파일 |

- 이 판정은 diff 대상이 `day1` 의 사전 작업(문서화, Skill, `CLAUDE.md`)까지 모두 포함해서 나온 것이다. 이관 커밋만 보면 결과가 달라질 수 있다.
- 이관 커밋은 `c3902e0`(baseline 테스트), `bd38576`(이관), `3223c1e`(회고)이다. 이 범위로 다시 돌릴지는 사람이 정한다. 다시 돌리려면 대상에 `c3902e0^..3223c1e` 를 주면 된다.
- `bd38576` 에는 `.claude/failure-log.md` 가 같이 들어 있어 그 범위로 돌려도 1개 파일은 남는다.

## 이슈 목록

치명은 없다. 경고 4건은 모두 `docs/item-bank/BUSINESS-RULES.md` 의 "이관 회고" 표(`docs/item-bank/BUSINESS-RULES.md:634-654`)에 이미 적힌 차이이고, 표의 "다음에 막을 방법" 칸이 전부 `(미정)` 이다. 표의 인용 줄번호는 이번에 파일을 열어 대조했다.

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:75-82` | `name[]` 값을 일반 `name` 보다 먼저 쓴다. 레거시는 쿼리 문자열 순서대로 마지막 대입이 이긴다. `level[]=5&level=1` 은 레거시 1(5건), 새 API 5(6건)(`docs/item-bank/BUSINESS-RULES.md:642`). `q[0]` · `q[x]` · `level[][]` 는 새 API 가 무시한다(`docs/item-bank/BUSINESS-RULES.md:641`, `docs/item-bank/BUSINESS-RULES.md:643`) | 요청 파라미터를 순서 그대로 읽어 PHP `$_GET` 규칙(마지막 대입 우선, 키 있는 배열은 첫 값)을 따르게 하거나, 차이를 허용 동작으로 사람이 기각한다 |
| 2 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchResponse.java:12` | 레거시는 입력 보정 때 경고 목록을 보여 준다(`legacy/item-bank-php/search.php:88`, `legacy/item-bank-php/search.php:92`, `legacy/item-bank-php/search.php:95`). 응답 record 에는 경고 필드가 없다(`docs/item-bank/BUSINESS-RULES.md:650`) | 응답에 `warnings` 를 추가할지, 이관 범위에서 뺀다고 기록할지 사람이 정한다. 추가하면 `characterization` 정규화가 경고를 읽도록 바뀌어 스냅샷을 건드리게 되므로 사람에게 먼저 알려야 한다 |
| 3 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchController.java:24` | `@GetMapping` 만 있어 요청 방식과 헤더가 다르면 응답이 달라진다. POST 는 레거시 200, 새 API 500. `Accept: text/html` 만 보내면 406. 인코딩하지 않은 `[]` · 900자 키워드 URL 은 400. DB 오류는 레거시 200 + 오류 HTML, 새 API 500 + JSON(`docs/item-bank/BUSINESS-RULES.md:644-649`) | 읽기 전용 JSON API 라는 점과 DB 오류 500 은 의도한 변경으로 기각하고 표에 "수용"으로 적을지 사람이 정한다. POST 500 은 405 로 바뀌는 쪽이 `GlobalExceptionHandler` 매핑에 맞는지 확인이 필요하다 |
| 4 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:19` | 새 API 는 풀 최대 5개 · 대기 3초 뒤 500 이고 레거시는 요청마다 새 연결이다. 회고 표에 "실측 안 함"으로 적혀 있다(`docs/item-bank/BUSINESS-RULES.md:653`). 실측 자료가 없어 위험 크기를 판단하지 못한다 | 동시 요청 수를 정해 8080 에서 실측한 뒤 수용 여부를 정한다 |
| 5 | 제안 | `characterization/tests/item-bank.test.js:101` | 검색 케이스 18건이 모두 통과했지만 회고 표의 차이 13건은 테스트가 잡지 못했다(`docs/item-bank/BUSINESS-RULES.md:641-653`). 정규화가 `{status, rows, count, message}` 만 비교하기 때문이다 | 이슈 #1 을 수정하거나 수용한 뒤 해당 입력을 케이스로 추가한다. 스냅샷 수정은 사람과 먼저 정한다 |
| 6 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchRepository.java:44` | `" LIMIT " + PAGE_SIZE + " OFFSET " + offset` 이 문자열 이어붙임이다. `offset` 은 페이지 1~999 로 고정된 `int` 라 주입은 불가능하고, 레거시도 같은 방식이다(`legacy/item-bank-php/search.php:523`) | 바꾸지 않아도 된다. 바꾸려면 `:limit` · `:offset` 으로 바인딩한다 |

## 확인 필요

근거 줄번호를 댈 수 없거나 실행하지 못한 항목이다.

- **동시 요청 동작**: 이슈 #4 와 같다. 실측 자료가 없다.
- **저장소 위생**: `.claude/.DS_Store` · `.claude/skills/.DS_Store` 2개, `.claude/skills/convention-check.zip`, `.claude/tmp/.reviewer-ran` · `.claude/skills/convention-check/.claude/tmp/.reviewer-ran` 2개가 diff 에 들어 있다. 바이너리 · 빈 파일이라 줄번호가 없고, 추적 대상인지 사람이 정한다. 작업 트리에서는 `.claude/skills/.DS_Store` 가 지금도 수정된 상태(`M`)다.
- **`example-units.test.js` 의 건너뜀**: `units.php` → `/api/units` 도 이관된 경로인데 새 API 대상에서는 항상 건너뛴다(`characterization/tests/example-units.test.js:21`). 이관 요청 범위 밖이고 이 diff 에서 바뀌지 않아 이슈로 올리지 않았다.
- **8080 서버의 빌드 시점**: 위 "테스트 실행 결과"의 근거는 프로세스 시작 시각과 커밋 시각 비교뿐이다. 실행 중인 jar · 클래스를 직접 확인하지는 않았다.
- **`search.php` 전수 대조**: 731줄을 한 줄씩 `ItemSearchService.java` 와 대조하지는 않았다. 이번에 직접 열어 본 것은 키워드 처리(`legacy/item-bank-php/search.php:85-98`)와 `LIMIT` 이어붙임(`legacy/item-bank-php/search.php:520-525`)이다. 난이도 · 페이지 · 단원 · 태그 · `ORDER BY` 는 `characterization` 18건과 `ItemSearchServiceTest` 38건에만 의존한다.
- **체크리스트 문구**: 항목 1-3(테스트가 충분하다)과 4-5(팀 스타일)는 판정 기준이 문장에 없어서 "확인 필요"로 남겼다.
