# 핸드오프 — Skill로 뺄 후보

- 작성일: 2026-09-29
- 배경: 루트 `CLAUDE.md` 재작성(커밋 `3d5493f`) 때 "단계가 여러 개인 절차는 파일에 넣지 않는다"는 기준으로 뺀 절차들이다. 각 후보를 `.claude/skills/<이름>/SKILL.md` 로 만들 때 이 문서를 출발점으로 쓴다.
- 상태: 후보 목록만 있다. 이 문서의 후보는 아직 Skill로 만들지 않았다. 별도로 만든 `convention-check` Skill(코드 규칙 점검)을 1번이 호출한다.
- 공통 원칙: 규칙 자체(무엇을 지키는가)는 `CLAUDE.md` 에 남아 있다. Skill에는 그 규칙을 지키는 **순서**만 담고, 규칙 문장을 다시 복사하지 않는다.

## 1. 작업 완료 검증 (`modern/*`)

- 언제: `modern/api` · `modern/web` 변경 작업을 "완료"로 보고하기 직전.
- 절차:
  1. 바꾼 모듈의 검사를 실행한다. api는 `./gradlew test`, web은 `npm run lint && npm run typecheck && npm test`.
  2. 통과 · 실패 수를 기록한다. 실패가 1건이라도 있으면 완료로 보고하지 않는다.
  3. `/convention-check` 를 실행한다. 금지 패턴과 승인 필요 파일 변경은 이 Skill이 점검한다(`.claude/skills/convention-check/SKILL.md`).
  4. `git diff --name-only` 로 변경 파일이 요청 범위 안인지 확인한다. 범위 밖 파일은 이유를 적는다.
  5. 바꾼 동작마다 대응 테스트가 추가 · 수정되었는지 대조한다.
- 재료: `templates/CLAUDE.spring.md` §5, `templates/CLAUDE.react.md` §5, `templates/approval-checklist.md`, `templates/verification-loop.md`

## 2. 새 조회 API 추가 (`modern/api`)

- 언제: `/api/<도메인 복수형>` 아래에 조회 엔드포인트를 새로 만들 때(예: `/api/items/search`).
- 절차:
  1. 대상 도메인 패키지를 정한다(`com.example.item` 등).
  2. 응답 `record` DTO를 만든다.
  3. Repository 쿼리 메서드를 만들고 `@DataJpaTest`(H2) 테스트를 쓴다.
  4. Service 메서드를 만들고 Mockito 단위 테스트를 쓴다. 없는 리소스는 `NotFoundException`, 잘못된 입력은 `IllegalArgumentException` 을 던진다.
  5. Controller를 만들고 `@WebMvcTest` 테스트를 쓴다. 200 · 404 · 400 경로를 모두 다룬다.
  6. 레거시 이관이면 규칙마다 근거 `파일:줄번호` 를 답변에 적는다.
  7. 1번 Skill(작업 완료 검증)을 실행한다.
- 재료: `templates/CLAUDE.spring.md` §2 · §4, 기존 `item/` 패키지와 테스트(`ItemControllerTest`, `ItemServiceTest`, `ItemRepositoryTest`)

## 3. 레거시 규칙 이관 · 수정 전 영향도 분석

- 언제: `legacy/` 의 동작을 분석해 `modern/` 으로 옮기거나, 허락을 받고 `legacy/` 를 고칠 때.
- 절차:
  1. 대상 함수와 그 호출부(`grep -rn "<함수명>" legacy/`), 그 함수가 부르는 SQL · include 파일을 모두 연다.
  2. 영향도 표를 답변으로 먼저 낸다. 열: 바뀌는 `파일:줄번호` / 영향받는 화면 · 요청 / 확인 방법.
  3. 사람이 승인하기 전에는 파일을 고치지 않는다.
  4. 한 번에 규칙 하나만 옮기거나 고친다. 여러 동작 변경을 한 커밋에 섞지 않는다.
  5. 발견한 규칙은 모두 `파일:줄번호` 근거를 붙인다. 근거를 댈 수 없으면 "확인 필요"로 표시한다.
  6. 4번 Skill(동작 보존 비교)로 확인한다.
- 재료: `templates/CLAUDE.legacy-php.md` §2 · §3 · §4

## 4. 동작 보존 비교 (`characterization/`)

- 언제: 레거시 동작을 고정하거나, 이관한 `modern/api` 가 레거시와 같은 결과를 내는지 확인할 때.
- 절차:
  1. 레거시를 띄운다(`docker compose --profile <php|thymeleaf|mssql> up -d`).
  2. 대응 테스트가 없으면 `tests/<모듈명>.test.js` 를 만든다. 케이스는 정상 · 경계값 · 빈 결과를 포함해 최소 5개다.
  3. `npm run baseline -- <item-bank|assignment|grade>` 로 스냅샷을 찍는다.
  4. 스냅샷에 실행마다 바뀌는 값이 섞였는지 확인한다. 섞였으면 `lib/normalize.mjs` 의 `VOLATILE_KEYS` 로 처리한다.
  5. 경로가 바뀐 화면은 `lib/target.mjs` 의 `PATH_ALIASES` 에 등록한다(예: `/search.php` → `/api/items/search`).
  6. `TARGET_BASE_URL=http://localhost:8080 npm test` 로 새 API와 비교한다.
  7. 실패하면 이관 코드를 고친다. 테스트 · 스냅샷은 고치지 않는다.
  8. 의도한 동작 변경이면 바뀐 케이스와 이유를 적고 사람 확인을 받은 뒤 baseline을 다시 찍는다.
- 재료: `characterization/README.md`, `characterization/lib/target.mjs` · `normalize.mjs`, 루트 `CLAUDE.md` 이전 버전의 "동작 보존 테스트 하네스" 섹션(커밋 `758309b`)

## 5. web 화면 · 컴포넌트 추가 (`modern/web`)

- 언제: 새 조회 화면이나 컴포넌트를 추가할 때.
- 절차:
  1. `src/api/types.ts` 에 응답 타입을 추가한다. 필드명은 백엔드 JSON 그대로 쓴다.
  2. `src/api/items.ts`(또는 도메인 파일)에 `getJson` 을 쓰는 엔드포인트 함수를 추가한다.
  3. `src/hooks/use<이름>.ts` 에 `useApiQuery` 기반 훅을 만들고 `.test.ts` 를 쓴다.
  4. `src/components/<이름>.tsx` 를 만들고 로딩 · 오류는 `AsyncSection` 에 맡긴다. 빈 결과 문구도 넣는다.
  5. `<이름>.test.tsx` 를 쓴다. `mockFetch` 로 렌더 확인 1개와 상호작용 1개 이상을 포함한다.
  6. 1번 Skill(작업 완료 검증)을 실행한다.
- 재료: `templates/CLAUDE.react.md` §2 · §4, 기존 `ItemBrowser` · `useUnits` · `ItemTable.test.tsx`

## 6. 로컬 전체 기동

- 언제: 화면에서 API까지 실제로 붙여 확인할 때.
- 절차:
  1. `docker compose --profile modern up -d` 로 DB를 띄우고 `docker compose ps` 로 확인한다.
  2. `cd modern/api && ./gradlew bootRun` 을 실행한다.
  3. `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/` 가 `200` 인지 확인한다.
  4. `cd modern/web && npm ci && npm run dev` 를 실행한다(5173, `/api` 는 8080으로 프록시).
  5. 끝나면 `docker compose --profile modern down` 으로 정리한다.
- 미확인: 2026-09-29 기준으로 `bootRun` 은 DB 컨테이너가 없어 실행 확인하지 못했다. Skill로 만들 때 먼저 확인한다.
