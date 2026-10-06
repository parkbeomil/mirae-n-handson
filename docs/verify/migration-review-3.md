판정: 반려

# 검증 결과: 레거시 엔드포인트 1개 이관 (migration-review-3)

- 검증 대상: `git diff upstream/main...HEAD -- modern characterization` (브랜치 `day2`, HEAD `85b6117`, 11개 파일 · +2343줄, 전부 신규 파일)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 기준 문서: `templates/approval-checklist.md`(v1 · 2회차). 절차: `/verify` Skill(`.claude/skills/verify/SKILL.md`).
- 코드는 수정하지 않았다. 이 문서를 새로 썼고, 이 세션에서 낸 문서 실수를 `.claude/failure-log.md` · `.claude/mistake-patterns.md` 에 기록했다.
- 이관 대상은 변경 내용으로 확인했다: `legacy/item-bank-php/search.php` → `GET /api/items/search`(`ItemSearchController.java:24`). `legacy/` 는 diff 에 없다.
- 대상 경로를 `modern characterization` 으로 좁혔으므로, 이 범위 밖 변경(docs · `.claude/` · `.mcp.json` 등)은 판정에 넣지 않았다.

## 판정: 반려

반려 사유는 "컨벤션 준수" 미충족 한 가지다. 체크리스트 §4 반려 항목 "리포지토리 메서드 선언이 변경분에 없다"에 신규 `ItemSearchRepository` 가 걸린다(이슈 1). 테스트는 전부 통과했고 치명 이슈는 0건으로 판단했다(이슈 2의 판단 근거 참고).

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `./gradlew test` 86건 통과 · 실패 0 · 건너뜀 0. characterization 레거시 33건 통과, 새 API 32건 통과 · 1건 건너뜀(아래 설명). 새 public 메서드 4종(컨트롤러 `search` · 서비스 `search` · `count` · `findPage`)마다 호출 테스트가 있다. 테스트를 지우거나 `@Disabled` 로 바꾼 곳 없음 |
| 치명 이슈 0건 | 충족 | 치명 0건. 값은 모두 바인딩(`ItemSearchRepository.java:57-73`). 비밀값 · 삭제 · 인증 변경 없음. 단 SQL 조립 해석은 이슈 2 참고 |
| 요청 범위 이탈 없음 | 충족 | 요청 "엔드포인트 1개 이관" vs 변경 11개 파일: 검색 컨트롤러 · 서비스 · 리포지토리 · DTO 3종 · 테스트 3종 · characterization 테스트와 스냅샷. 전부 `/api/items/search` 하나로 설명된다. `build.gradle` · `application.yml` · 스키마 · 시드 변경 없음, 기존 파일 수정 0건(전부 `A`) |
| 컨벤션 준수 | 미충족 | 반려 항목 위반 1건(이슈 1). `convention-check` Skill 8개 항목은 위반 0건(web 항목 6 · 7 은 해당 없음, 8 은 승인 필요 파일 없음). 경고 항목 위반은 이슈 2~5 외에 없음 |

## 테스트 실행 결과

| 대상 | 명령 | 통과 | 실패 | 건너뜀 |
|---|---|---|---|---|
| modern/api | `cd modern/api && ./gradlew test` | 86 | 0 | 0 |
| characterization(레거시 8081) | `cd characterization && npm test` | 33 | 0 | 0 |
| characterization(새 API 8080) | `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` | 32 | 0 | 1 |

- `ItemSearch*` 3개 클래스: `ItemSearchServiceTest` 52건, `ItemSearchRepositoryTest` 7건, `ItemSearchControllerTest` 3건. 합계는 `build/test-results/test/*.xml` 에서 센 값이다.
- 8080 서버는 13:42 에 떴고 검색 소스의 마지막 수정(13:24)보다 늦다. `modern` · `characterization` 작업 트리는 깨끗하다. 현재 코드가 응답한 것으로 본다.
- 건너뛴 1건은 `tests/example-units.test.js:21` 의 `it.skipIf(legacyOnly)`(units.php 는 이번 이관 대상이 아니다). 이 파일은 이번 diff 에 없다. `item-bank.test.js` 에는 건너뛰기가 없다.
- `ItemSearchRepositoryTest` 는 H2(`@ActiveProfiles("test")`)를 쓴다. 실제 MariaDB 에는 접속하지 않았다. 새 API 쪽 characterization 은 8080 의 실제 서버를 대상으로 한다.

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고(§4 반려 항목 → ④ 미충족) | `modern/api/src/main/java/com/example/item/ItemSearchRepository.java:12-13`, `:32`, `:40` | 체크리스트 §4 "리포지토리 메서드 선언이 변경분에 없다(기존 리포지토리를 호출만 하는 것은 허용)". 신규 `@Repository` 클래스에 `count` · `findPage` 선언이 새로 생겼다. `JpaRepository` · `@Entity` · `@Query` 는 아니고 `NamedParameterJdbcTemplate` 을 쓴다. 문구상 "리포지토리 메서드 선언"에는 해당한다 | 사람이 정한다. (a) 이 이관의 예외로 승인한다(동적 조건 검색은 기존 리포지토리 호출만으로 만들 수 없다). (b) 체크리스트 문구를 JPA 대상으로 좁힌다(아래 확인 필요 1). 승인 전에는 반려 유지 |
| 2 | 경고 | `ItemSearchRepository.java:43-44` | `" LIMIT " + PAGE_SIZE + " OFFSET " + offset` 로 SQL 문자열에 숫자를 이어 붙인다. `PAGE_SIZE` 는 상수, `offset` 은 `page`(1~999 로 제한된 int, `ItemSearchService.java:241-253`)에서 계산한 값이라 외부 문자열이 SQL 에 들어가지는 않는다 | `LIMIT :limit OFFSET :offset` 으로 바인딩하면 "SQL 조립" 해석 논란이 사라진다. 같은 패턴: `ItemSearchRepository.java:34`(고정 문자열 연결), `ItemSearchRepository.java:65`(상수 `DEFAULT_LEVEL_EXCLUSIVE_UPPER`), `ItemSearchRepository.java:81-86`(고정 ORDER BY 문자열 선택) |
| 3 | 제안 | `modern/api/src/test/java/com/example/item/ItemSearchServiceTest.java:26` | `@ExtendWith(MockitoExtension.class)` 단위 테스트라 `@ActiveProfiles("test")` 가 아무 효과도 없다 | 제거. 동작에는 영향 없음 |
| 4 | 제안 | `ItemSearchRepositoryTest.java:27-40` | 테스트 안에서 운영 `v_item_public` 뷰를 같은 정의로 다시 만든다(주석에 `db/mariadb/init/01-schema.sql` 이라고 적혀 있음). 운영 정의가 바뀌어도 이 테스트는 계속 통과한다 | 정의가 바뀔 때 함께 고치도록 팀 규칙에 넣거나, 뷰 SQL 을 공유 파일로 둔다 |
| 5 | 제안 | `ItemSearchRepositoryTest.java:124-133` | 정렬 테스트가 TITLE · UNIT · LEVEL 만 본다. `ID` · `CREATED` 정렬(`ItemSearchRepository.java:81`, `ItemSearchRepository.java:85`)과 `ASC`/`DESC` 의 2차 정렬(`id ASC`)은 어느 테스트도 확인하지 않는다(characterization 도 title · level 정렬만 가진다). 코드는 `legacy/item-bank-php/search.php:279-313` 의 ORDER BY 와 대조해 같음을 확인했다 | `ID` · `CREATED` 케이스 추가 |

### 이슈 2 에 대한 판단 근거 (치명으로 올리지 않은 이유)

체크리스트 §2 는 "SQL 을 문자열로 이어 붙여 만드는 곳이 없다(파라미터 바인딩 사용)"이다. `ItemSearchRepository` 는 고정 조각을 이어 붙이지만, 사용자 입력 값(`keyword` · `unitCode` · `level` · `tag`)은 전부 `:name` 바인딩이고 `ORDER BY` 는 `switch` 로 고정 문자열만 고른다. 이어 붙는 것은 상수와 제한된 int 뿐이므로 주입 경로가 없다고 판단해 경고로 두었다. "조립 자체가 치명"이라는 팀 해석이면 이 항목은 치명이 되고 판정은 어차피 반려다. 해석은 사람이 확정해 달라.

## 확인 필요

1. 체크리스트 §4 반려 항목(`templates/approval-checklist.md:54`, 심각도와 무관하게 반려라는 규칙은 `templates/approval-checklist.md:19`)과 `CLAUDE.md` §2 가 충돌한다. `CLAUDE.md` 는 "새 조회 API는 같은 도메인 패키지에 컨트롤러 · 서비스 · 리포지토리 세 파일"을 요구하는데, 체크리스트는 변경분의 신규 리포지토리 메서드 선언을 반려로 본다. `templates/approval-checklist.md:4` 의 버전 노트가 "JPA 신규 추가 금지"를 팀 결정으로 적었으므로 의도는 JPA 쪽으로 보이지만, 이 문서의 문구는 그렇게 한정하지 않는다. 한정 여부를 정해 주면 이슈 1 이 정리된다.
2. 레거시는 입력 경고 문구를 만든다(`legacy/item-bank-php/search.php` 의 `$warnings[] =` 줄 14곳을 센 값이며 서로 다른 문구 수는 세지 않았다. 예: `search.php:225` "난이도는 1~5 사이여야 합니다."). 새 API 응답(`ItemSearchResponse.java:12`)에는 경고 필드가 없고, 하네스 `characterization/lib/normalize.mjs` 는 경고를 비교하지 않는다(`warn|summary|경고` grep 0건). 레거시 화면에서 이 경고가 사용자에게 보이는지는 확인하지 못했다. 보인다면 "동작을 바꾸지 않는다"에서 벗어나지만 스냅샷 테스트로는 잡히지 않는다.
3. 스냅샷(`characterization/__snapshots__/item-bank.test.js.snap`)은 이번 diff 에서 새로 추가된 파일이라 기존 스냅샷을 깨뜨리지 않았다. 다만 레거시에서 찍은 시점의 시드 상태가 현재 DB 와 같은지는 이 세션에서 확인하지 않았다. 두 실행 모두 통과했으므로 현재는 일치한다.

## 의심 동작 (레거시 버그로 보이나 이관 중 보존, 수정하지 않음)

- 난이도 빈 값이면 `level < 5` 라 난이도 5가 빠진다. 같은 곳의 주석은 "전체 난이도 검색 (1~5 모두 포함)"이다(`legacy/item-bank-php/search.php:213-215`). 새 코드는 그대로 옮기고 이유를 주석으로 남겼다(`ItemSearchRepository.java:18`).
- 키워드의 `%` · `_` 를 이스케이프하지 않아 와일드카드로 동작한다(`ItemSearchRepository.java:56`). 레거시도 같다.
