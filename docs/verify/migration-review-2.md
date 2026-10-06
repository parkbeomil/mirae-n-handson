# 검증 결과: 레거시 엔드포인트 1개 이관 (migration-review-2)

- 검증 대상: `git diff upstream/main...HEAD` (브랜치 `day2`, HEAD `9391f7f`, 31개 파일 · +4815줄)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 검증 방법: `/verify` Skill(`.claude/skills/verify/SKILL.md`) 절차. 기준 문서는 `templates/approval-checklist.md`(v0).
- 코드는 수정하지 않았다. 이 문서만 새로 썼다.
- 이관 대상은 `legacy/item-bank-php/search.php` → `GET /api/items/search` 로 확인했다(커밋 `bd38576`).
- 이 문서는 2차 검증이다. 1차(`docs/verify/migration-review-1.md`, HEAD `5418388`) 이후의 변경은 커밋 `9391f7f` 한 건이며, 1차 이슈 #1 을 고친 것이다.

## 판정: 반려

반려 사유는 1차와 같은 한 가지, "요청 범위 이탈 없음" 미충족이다. 이관 코드가 아닌 파일 16개가 diff 대상에 들어 있다("요청 범위" 절). 치명 이슈는 0건이다. 1차 이슈 #1(`level[]=5&level=1` 같은 파라미터 순서 차이)은 고쳐졌고 레거시와 같은 결과를 내는 것을 확인했다. 사람이 정해야 하는 경고가 4건 남아 있다.

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 (충분성은 확인 필요) | `cd modern/api && ./gradlew cleanTest test` 86건 통과 · 실패 0 · 건너뜀 0. `cd characterization && npm test` 33건 통과. 새 API 대상은 8090(HEAD 소스) 32건 통과 · 1건 건너뜀. 8080 은 옛 코드(아래 "테스트 실행 결과") |
| 치명 이슈 0건 | 충족 | 치명 0건. 조건 값은 모두 바인딩(`modern/api/src/main/java/com/example/item/ItemSearchRepository.java:57-73`), 비밀 · 토큰 리터럴 없음, 삭제 · 덮어쓰기 코드 없음 |
| 요청 범위 이탈 없음 | 미충족 | 요청 "엔드포인트 1개 이관" vs 변경 31개 파일 중 16개가 설명되지 않음. "요청 범위" 절 참고 |
| 컨벤션 준수 | 충족 | 위반 0건(`convention-check` Skill 은 호출하지 않고 직접 대조). 제안 2건은 이슈 #6 · #7 |

## 테스트 실행 결과

| 대상 | 명령 | 통과 | 실패 | 건너뜀 |
|---|---|---|---|---|
| modern/api | `cd modern/api && ./gradlew cleanTest test` | 86 | 0 | 0 |
| characterization(레거시, 8081) | `cd characterization && npm test` | 33 | 0 | 0 |
| characterization(새 API, 8090 · HEAD 소스) | `cd characterization && TARGET_BASE_URL=http://localhost:8090 npm test` | 32 | 0 | 1 |
| characterization(새 API, 8080 · 기존 서버) | `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` | 32 | 0 | 1 |

- `cleanTest` 를 붙여 캐시를 피하고 실제로 다시 실행했다. `ItemSearch*` 3개 클래스는 `ItemSearchServiceTest` 52건(1차 38건), `ItemSearchRepositoryTest` 7건, `ItemSearchControllerTest` 3건이며 전부 통과했다.
- `ItemSearchRepositoryTest` 는 H2 를 쓴다(`modern/api/src/test/java/com/example/item/ItemSearchRepositoryTest.java`). 실제 MariaDB 에 접속하는 테스트는 없다.
- 건너뛴 1건은 `characterization/tests/example-units.test.js:21` 의 `it.skipIf(legacyOnly)` 이고, 이 diff 에서 바뀐 파일이 아니다.
- **8080 서버는 이번 HEAD 소스가 아니다.** 프로세스 시작이 2026-10-06 12:34 이고 HEAD 커밋 `9391f7f` 는 13:26 이다. 같은 요청에 대한 응답이 다르다.

  | 요청(쿼리 문자열) | 레거시 | 8090(HEAD 소스) | 8080(기존 서버) |
  |---|---|---|---|
  | `level%5B%5D=5&level=1` | 5건 | 5건(난이도 1) | 6건(난이도 5) |
  | `level=1&level%5B%5D=5` | 6건 | 6건 | 6건 |
  | `level%5B%5D%5B%5D=5` | 0건 | 0건 | 20건(파라미터 무시) |

- 그래서 HEAD 소스를 8090 에 임시로 띄워 새 API 대상 테스트를 돌렸다. 코드 · 설정은 건드리지 않고 `./gradlew bootRun --args='--server.port=8090'` 로만 기동했고, 끝난 뒤 종료했다(8090 응답 없음 · 8080 은 그대로 200). **8080 을 최신 소스로 다시 기동해 달라고 요청한다**(확인 필요 절).
- 새 파서의 경계 입력 21건을 레거시(8081)와 8090 에 같은 쿼리 문자열로 보내 `{status, count, message, 앞 3개 id}` 를 비교했다. 19건이 같고 2건이 다르다. 다른 2건은 `%20level=3`(이슈 #6)과 인코딩하지 않은 `level[]=…`(Tomcat 400, 1차 이슈 #3 에 이미 있는 차이)다. 같은 것으로 나온 입력은 `q=%FF` · `q=%E2%82` · `q=a%00b` · `q=%41%2` · `q=%` · `q`(값 없음) · `level=2;level=3` · `&&level=2&` · `level[-5]` · `level[9223372036854775807]` · `level.x=2&level=3` · `LEVEL=2` · `level=3&level[3]=2` 등이다.
- `characterization/` 은 baseline 이후 바뀌지 않았다(`git diff --stat 5418388..HEAD -- characterization` 이 비어 있다).

## 항목별 대조

항목 문구는 `templates/approval-checklist.md` 를 따른다. 여기서는 번호(기준 번호-순서)와 결과만 적는다.

| 항목 | 결과 | 비고 |
|---|---|---|
| 1-1 · 1-2 · 1-4 | 충족 | 위 실행 결과. `@Disabled` · `skipIf` 를 새로 넣은 곳이 없다 |
| 1-3 (테스트 충분) | 확인 필요 | 1차 이슈 #1 은 `ItemSearchServiceTest.java` 의 `followsQueryOrderLikePhp`(8건) · `decodesLikePhpUrldecode`(5건) · `treatsMissingQueryAsNoParams` 가 막는다. 남은 차이(이슈 #1 · #2 · #3 · #6)는 테스트가 없다 |
| 2-1 · 2-2 · 2-3 | 충족 | 비밀 관련 단어 검색 결과 없음. `LIMIT`/`OFFSET` 이어붙임은 정수 `int` 뿐이다(`ItemSearchRepository.java:44`). 삭제 · 덮어쓰기 코드 없음 |
| 2-4 | 충족 | 치명 0건 |
| 3-1 | 충족 | 요청 문장은 호출 인자로 받았다 |
| 3-2 · 3-3 | 미충족 | "요청 범위" 절 |
| 3-4 | 충족 | `build.gradle` · `package.json` · `application.yml` · 마이그레이션 · 시드 파일은 diff 에 없다 |
| 4-1 | 충족(수동) | `modern/api` 변경 Java 파일에 120자 초과 줄 · 와일드카드 import · `System.out` · `printStackTrace` · 탭이 없다(문자 수로 확인). 새 상수 `HEX_RADIX` · `HEX_DIGITS` · `BYTE_MASK` 는 `private static final` 이다. 테스트 메서드는 camelCase + 한국어 `@DisplayName` |
| 4-2 | 충족 | `ItemSearchController.java:25-27` 은 `ItemSearchService` 만 호출한다. `HttpServletRequest` 에서 쿼리 문자열만 꺼낸다 |
| 4-3 | 충족 | `catch` 블록이 없다 |
| 4-4 | 충족 | 임시 출력이 없다 |
| 4-5 (팀 스타일) | 확인 필요 | 객관 기준이 없는 항목이라 위반을 찾지 못했다는 뜻으로만 읽는다 |

## 요청 범위

변경 파일 31개 중 "엔드포인트 1개 이관"으로 설명되는 파일은 다음 15개다.

- 새 API 소스 6개와 테스트 3개: `modern/api/src/main/java/com/example/item/ItemSearch*.java` · `modern/api/src/test/java/com/example/item/ItemSearch*Test.java`
- 동작 보존 테스트 2개: `characterization/tests/item-bank.test.js` · `characterization/__snapshots__/item-bank.test.js.snap`
- 이관 근거 문서 4개: `docs/item-bank/ARCHITECTURE.md` · `BUSINESS-RULES.md`(이관 회고 절 포함) · `CROSS-CHECK.md` · `ERD.md`. 이관 전에 분석용으로 만든 문서라 "이관 근거"로 넣는 것은 판단이다. 빼면 19개가 설명되지 않아도 판정은 같다.

나머지 16개 파일은 요청 문장으로 설명되지 않는다.

| 파일 | 성격 |
|---|---|
| `CLAUDE.md` | 팀 규칙 문서 |
| `docs/assignment/ARCHITECTURE.md` · `docs/assignment/BUSINESS-RULES.md` · `docs/assignment/ERD.md`, `docs/handoff/skill-candidates.md` | 다른 모듈 분석 문서와 핸드오프 |
| `.claude/skills/convention-check/SKILL.md`, `.claude/skills/document-module/SKILL.md`, `.claude/skills/verify/SKILL.md` | Skill 3개 |
| `.claude/failure-log.md`, `.claude/mistake-patterns.md` | 실수 기록 |
| `docs/verify/migration-review-1.md` | 1차 검증 결과(`9391f7f` 에서 추가됨) |
| `.claude/.DS_Store`, `.claude/skills/.DS_Store`, `.claude/skills/convention-check.zip`, `.claude/tmp/.reviewer-ran`, `.claude/skills/convention-check/.claude/tmp/.reviewer-ran` | 편집기 · 임시 · 압축본 파일 |

- 이 판정은 diff 대상이 `day1` 의 사전 작업(문서화 · Skill · `CLAUDE.md`)까지 모두 포함해서 나온 것이다. 1차와 같은 사유이고 사람이 대상 범위를 정해야 한다.
- 이관 커밋만 보면 `c3902e0`(baseline 테스트), `bd38576`(이관), `3223c1e`(회고), `9391f7f`(1차 반영)이다. 이 4개 커밋에도 이관과 무관한 파일이 섞여 있다: `.claude/failure-log.md`(`c3902e0` · `bd38576`), `.claude/mistake-patterns.md` · `.claude/skills/document-module/SKILL.md`(`c3902e0`), `docs/verify/migration-review-1.md`(`9391f7f`). 이 범위로 다시 돌린다면 이 4개를 이관 요청 안으로 볼지도 사람이 정한다.

## 1차 이슈 처리 현황

| 1차 # | 1차 내용 | 2차 확인 |
|---|---|---|
| 1 | 파라미터 순서(`level[]=5&level=1`) · 키 있는 배열 · 중첩 배열 · 잘못된 퍼센트 인코딩이 레거시와 다름 | **해결.** `ItemSearchService.java:110-` 의 `parseQuery` 가 쿼리 문자열을 앞에서부터 읽는다. 8090 이 레거시와 같은 결과를 낸다(위 표). 남은 차이는 이슈 #6 |
| 2 | 입력 경고 문구가 응답에 없음 | 미해결 → 이슈 #2 |
| 3 | POST · `Accept` · 인코딩하지 않은 대괄호 · DB 오류 응답 차이 | 미해결 → 이슈 #3 |
| 4 | 풀 5개 · 3초 대기, 실측 없음 | 미해결 → 이슈 #4 |
| 5 | `characterization` 이 차이를 잡지 못함 | 미해결 → 이슈 #5 |
| 6 | `LIMIT`/`OFFSET` 이어붙임 | 변경 없음, 그대로 제안 수준. 정수 `int` 라 주입 경로가 아니다 |

## 이슈 목록

치명은 없다.

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `docs/item-bank/BUSINESS-RULES.md:641-643`, `docs/item-bank/BUSINESS-RULES.md:645` | `9391f7f` 가 코드를 고쳤는데 이관 회고 표는 그대로다. 표는 `q[0]` · `level[]=5&level=1` · `level[][]=5` · `q=%ZZ` 를 "AI가 만든 동작: 무시 · 항상 5 · 버림"으로 적지만 8090 은 레거시와 같은 결과를 낸다(위 비교). 다음 사람이 이미 해결된 차이를 열린 문제로 읽는다 | 해당 4행을 "해결(`9391f7f`)"로 갱신하거나 행을 정리한다. 이 회고 표는 이관 커밋 `3223c1e` 가 만든 이관 근거 문서이므로 요청 범위 안이다. 표의 "다음에 막을 방법" 칸은 전 행이 `(미정)` 이다 |
| 2 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchResponse.java:12` | 레거시는 입력 보정 때 경고 목록을 응답에 보여 준다(`legacy/item-bank-php/search.php:88`, `legacy/item-bank-php/search.php:92`, `legacy/item-bank-php/search.php:95`, `legacy/item-bank-php/search.php:113` 등 12곳). record 에는 경고 필드가 없다. "동작을 바꾸지 않고" 이관한다는 요청과 어긋난다. 1차 #2 와 같다 | 응답에 `warnings` 를 추가할지, 이관 범위에서 뺀다고 기록할지 사람이 정한다. 추가하면 `characterization` 정규화가 경고를 읽도록 바뀌어 스냅샷을 건드리므로 사람에게 먼저 알려야 한다 |
| 3 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchController.java:24` | `@GetMapping` 만 있어 요청 방식과 헤더가 다르면 응답이 달라진다. POST 는 레거시 200, 새 API 500. `Accept: text/html` 만 보내면 406. 인코딩하지 않은 `[]` 는 Tomcat 이 400(이번에도 `level[]=&level[]=2` 로 레거시 200 · 새 API 400 확인). DB 오류는 레거시 200 + 오류 HTML, 새 API 500 + JSON(`docs/item-bank/BUSINESS-RULES.md:644-649`). 1차 #3 과 같다 | 읽기 전용 JSON API 라는 점과 DB 오류 500 은 의도한 변경으로 기각하고 표에 "수용"으로 적을지 사람이 정한다. POST 500 은 405 가 `modern/api/src/main/java/com/example/common/GlobalExceptionHandler.java` 매핑에 맞는지 확인이 필요하다 |
| 4 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:24` | `@Transactional(readOnly = true)` 로 건수 · 목록 두 쿼리가 연결 하나를 잡는다. 풀 최대 5개 · 대기 3초 뒤 500 이고 레거시는 요청마다 새 연결이다. 회고 표에 "실측 안 함"이다(`docs/item-bank/BUSINESS-RULES.md:653`). 1차 #4 와 같다 | 동시 요청 수를 정해 실측한 뒤 수용 여부를 정한다 |
| 5 | 제안 | `characterization/tests/item-bank.test.js:101-104` | 배열 입력 케이스는 `level[]=5&level[]=1` 하나뿐이라 이번에 고친 순서 규칙(`level[]=5&level=1`), 키 있는 배열, 중첩 배열, 퍼센트 인코딩 오류를 `characterization` 이 지키지 못한다. 지금은 `ItemSearchServiceTest.java` 단위 테스트만 막는다 | 해당 입력을 케이스로 추가한다. 스냅샷을 새로 찍는 일이라 `CLAUDE.md` 규칙에 따라 사람과 먼저 정한다 |
| 6 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:120` | 이름 앞의 공백을 PHP 는 지우는데 새 파서는 그대로 둔다. `?%20level=3` 은 레거시 5건(난이도 3), 새 API 20건(`level` 무시). `parseQuery` 의 Javadoc(`ItemSearchService.java:105-109`)이 "공백 · 점 변환은 옮기지 않았다"고 밝혔지만, 앞 공백 제거는 회고 표에 없다. 브라우저 폼에서는 나오지 않는 입력이다 | 앞 공백을 지우도록 맞추거나, 회고 표에 허용 차이로 적는다 |
| 7 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:146-148` | `Map<String, Object>` 에 배열 요소를 `Object` 로 섞고 `@SuppressWarnings("unchecked")` 캐스트(`castArray`)와 `(String) value` 캐스트(`ItemSearchService.java:93-103`)로 되돌린다. 동작에는 문제가 없지만 다음 변경에서 타입 오류를 컴파일러가 못 잡는다. 같은 파일의 주석 `ItemSearchService.java:92` 는 "마지막 대입이 이긴다"의 근거로 `legacy/item-bank-php/search.php:52-81` 을 적는데, 이 줄들은 `reset()` 으로 첫 요소를 꺼내는 부분이고 마지막 대입 규칙은 PHP `$_GET` 의 동작이다 | 값을 문자열 · 배열로 구분하는 작은 타입으로 바꾸는 방법이 있다. 바꾸지 않아도 된다. 주석 근거 문구는 두 규칙을 나눠 적는 쪽이 정확하다 |

## 확인 필요

근거 줄번호를 댈 수 없거나 실행하지 못한 항목이다.

- **8080 서버를 최신 소스로 다시 기동**: 지금 8080 은 HEAD(`9391f7f`)보다 먼저 떠 있는 옛 코드다. 이 검증은 8090 임시 서버로 대신했지만, 8080 을 쓰는 화면 · 다른 검증이 옛 응답을 받는다. `modern/api` 를 다시 기동한 뒤 `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` 를 한 번 더 돌려 주기 바란다.
- **동시 요청 동작**: 이슈 #4 와 같다. 실측 자료가 없다.
- **저장소 위생**: `.claude/.DS_Store` · `.claude/skills/.DS_Store` 2개, `.claude/skills/convention-check.zip`, `.claude/tmp/.reviewer-ran` · `.claude/skills/convention-check/.claude/tmp/.reviewer-ran` 2개가 diff 에 들어 있다. 바이너리 · 빈 파일이라 줄번호가 없고, 추적 대상인지 사람이 정한다. 작업 트리에서는 `.claude/skills/.DS_Store` 가 지금도 수정된 상태(`M`)다.
- **`example-units.test.js` 의 건너뜀**: `units.php` → `/api/units` 도 이관된 경로인데 새 API 대상에서는 항상 건너뛴다(`characterization/tests/example-units.test.js:21`). 이관 요청 범위 밖이고 이 diff 에서 바뀌지 않아 이슈로 올리지 않았다.
- **파서 대조의 범위**: 경계 입력 21건만 레거시와 직접 비교했다. 유효하지 않은 UTF-8(`q=%FF` 등)은 이번 입력에서는 같았지만(값이 DB 조회까지 가는지는 확인하지 않았다), PHP 의 `max_input_vars` · 이름 길이 제한 같은 한계값은 확인하지 않았다.
- **`search.php` 전수 대조**: 731줄을 한 줄씩 `ItemSearchService.java` 와 대조하지는 않았다. 이번에 직접 열어 본 곳은 파라미터 꺼내기(`legacy/item-bank-php/search.php:52-81`)와 키워드 경고(`legacy/item-bank-php/search.php:85-98`)이고, 나머지 경고 문구 줄(`legacy/item-bank-php/search.php:113-323`)은 `grep` 으로 위치만 확인했다. 난이도 · 페이지 · 단원 · 태그 · `ORDER BY` 는 `characterization` 18건과 `ItemSearchServiceTest` 에만 의존한다.
- **체크리스트 문구**: 항목 1-3(테스트가 충분하다)과 4-5(팀 스타일)는 판정 기준이 문장에 없어서 "확인 필요"로 남겼다.
