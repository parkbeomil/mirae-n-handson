# 실전 프로젝트 설계 — 백앤드/박범일

- 작성일: 2026-10-20
- 상태: 설계만 있다. 코드는 만들거나 고치지 않았다.
- 재료 판정은 2026-10-20 에 `day1` 브랜치 작업 트리와 `origin/main` · 원격 PR을 읽어 한 결과다.

## 1. 목표

`/api/items/search` 한 개에 대해 Day 1-3의 입력 케이스 19개로 동작 보존을 확인하고, 그 변경을 담은 PR에서 자동 리뷰 코멘트 1회를 받는다.

## 2. 재료

| 재료 | 있음/없음 | 경로 |
|---|---|---|
| Skill | 있음 | `.claude/skills/verify/SKILL.md`, `.claude/skills/convention-check/SKILL.md`, `.claude/skills/document-module/SKILL.md`, `.claude/skills/rca/SKILL.md`, `.claude/skills/batch-check/SKILL.md` (`.claude/skills/convention-check.zip` 은 압축본이라 쓰지 않는다) |
| Sub-agent | 있음 | `.claude/agents/reviewer.md`, `.claude/agents/tester.md`, `.claude/agents/iac-reviewer.md` (이 트랙은 앞 둘만 쓴다) |
| Hook | 있음 (git 미추적) | `hooks/block-dangerous.mjs`, `hooks/secret-scan.mjs`. `.claude/settings.json` 의 `PreToolUse` 에 등록돼 있다. `git status` 에서 `?? hooks/` 로 보인다 |
| 권한 설정 | 있음 | `.claude/settings.json` (`git push *` 는 ask, `curl *` · `git push --force *` 는 deny) |
| 동작 보존 테스트 | 있음 | `characterization/tests/item-bank.test.js` (케이스 19개), `characterization/__snapshots__/item-bank.test.js.snap`, `characterization/lib/target.mjs` (`PATH_ALIASES` 에 `/search.php` → `/api/items/search`) |
| 이관 코드 | 있음 | `modern/api/src/main/java/com/example/item/ItemSearchController.java` 외 `ItemSearchService` · `ItemSearchRepository` · `ItemSearchCondition` · `ItemSearchResponse` · `ItemSearchRow` 와 테스트 3개(`ItemSearchControllerTest` · `ItemSearchServiceTest` · `ItemSearchRepositoryTest`). 커밋 `bd38576` 에서 들어왔다 |
| 레거시 근거 문서 | 있음 | `docs/item-bank/BUSINESS-RULES.md` (BR-01~BR-24, BR-27), `docs/item-bank/CROSS-CHECK.md` |
| 승인 체크리스트 | 있음 | `docs/approval-checklist.md` (자동 리뷰 프롬프트가 읽는 파일) |
| PR 자동 리뷰 워크플로 | 있음 (`origin/main` 에만) · `day1` 에는 없음 | `.github/workflows/pr-review.yml` (`origin/main` 기준). `day1` 작업 트리에는 `.github/` 가 없다 |
| 3회차에 받은 PR | 확인 필요 | 후보 `https://github.com/parkbeomil/mirae-n-handson/pull/1` — "4. 미해결 질문" 2번 |
| pipeline-samples | 있음 (이 트랙과 무관) | `pipeline-samples/` (배치 로그 · 적재 건수 CSV · terraform). 이 설계는 읽기만 했고 쓰지 않는다 |
| harness-baseline | 있음 (이 설계에서 쓰지 않음) | `harness-baseline/` (Day 4-1 · 4-2용 강사 제공 기준 한 벌) |
| 프로젝트 설계 문서 | 없음 | `docs/project/DESIGN.md` — 이 파일이 처음이다 |
| 실행 중인 서비스 | 일부 | `docker compose ps` 기준 `item-bank`(8081)와 `mariadb`(3306)가 떠 있다. 새 API(8080)는 떠 있지 않다 |

### 새로 만들 것

- 새로 만들 파일은 없다. 이 설계가 정한 범위에서는 위 재료로 충분하다.
- 단, 다음은 아직 없고 필요하면 사람이 정해야 한다(4번 참고): `day1` 에 `.github/workflows/pr-review.yml` 반영, 오늘 올릴 PR.

## 3. 범위

### 한다

1. `/api/items/search` 의 동작 보존 확인 — 레거시(8081)와 새 API(8080)에 같은 19개 케이스를 돌려 같은 스냅샷과 비교하고, `./gradlew test` 통과 · 실패 수를 기록한다.
2. PR 1개를 올려 Claude 자동 리뷰 코멘트가 1회 달리는 것을 확인하고, 지적마다 수용 · 기각을 한 줄로 적는다.

### 하지 않는다

- 엔드포인트를 추가로 이관하지 않는다 — 오늘 범위는 엔드포인트 1개로 정해졌다.
- `characterization/` 의 테스트 · 스냅샷을 고치지 않는다 — 통과시키려고 기준을 바꾸면 보존 확인이 되지 않고, 루트 `CLAUDE.md` 5번이 금지한다.
- `legacy/` 를 고치지 않는다 — 분석 · 이관 대상이며 허락 없이 수정할 수 없다.
- 레거시 동작이 버그로 보여도 고치지 않는다 — 이관 중에는 "의심 동작" 목록으로 따로 보고한다.
- 의존성 추가와 DB 스키마 · 시드 변경을 하지 않는다 — 사람에게 먼저 묻는 항목이다.
- 새 Skill · Sub-agent 를 만들지 않는다 — 40분 안에 끝낼 범위가 아니고, 있는 재료로 완성 기준을 채울 수 있다.
- `modern/web` 을 건드리지 않는다 — 백엔드 트랙이다.
- PR을 `--force` 로 올리거나 머지하지 않는다 — `settings.json` 이 막고 있고, 머지는 사람이 결정한다.
- `/convention-check` 를 따로 실행하지 않는다 — 완성 기준 6개 중 어느 것도 이 결과를 요구하지 않고, PR 자동 리뷰가 읽는 `docs/approval-checklist.md` 4번이 같은 컨벤션 대조를 하므로 코멘트에서 위반이 나오면 잡힌다.
- 변경 파일이 요청 범위 안인지 `git diff --name-only` 로 따로 점검하지 않는다 — 이 점검도 완성 기준에 없고, `docs/approval-checklist.md` 3번(요청 범위 이탈 없음)을 자동 리뷰가 본다. 이 설계는 파일을 고치지 않으므로 범위 이탈 여지도 작다.

## 4. 미해결 질문

저장소를 읽어서는 답을 정할 수 없어 비워 둔 것들이다. 1·2번 답에 따라 5번 단계가 달라진다.

1. **`/api/items/search` 는 이미 이관돼 있다.** 커밋 `bd38576`("search.php 를 GET /api/items/search 로 이관")에 구현과 테스트가 들어 있다. 같은 커밋 메시지에는 `./gradlew test` 72건, 레거시 대상 33건, 새 API 대상 32건 통과 + 1건 건너뜀이 적혀 있지만 이번에 다시 실행하지는 않았다.
   - Day 1-3의 입력 케이스(`characterization/tests/item-bank.test.js` 19개)가 모두 `search.php` 대상이라, 이 설계는 "그 엔드포인트를 다시 확인하고 PR로 올린다"로 잡았다.
   - 다른 해석이 맞다면 알려 달라: (a) `/api/items/search` 를 지우고 처음부터 다시 이관 (b) 다른 엔드포인트(`assignment` · `grade` 모듈은 입력 케이스가 없다) 이관 (c) 이미 있는 구현의 보강.
2. **"3회차에 받은 PR 주소"가 어느 것인지 확인하지 못했다.** `gh pr list` 로 찾은 후보는 둘이다.
   - `https://github.com/parkbeomil/mirae-n-handson/pull/1` — `practice/bad-pr`, OPEN. 2026-10-13T04:22:04Z 에 `claude` 계정의 판정 코멘트가 이미 1건 있다.
   - `https://github.com/tristan-kkim/mirae-n-handson/pull/8` — `day3/parkbeomil`, CLOSED. 코멘트는 내가 쓴 닫음 사유 1건뿐이다.
   - 완성 기준 ②가 "그 PR에 이미 달린 코멘트를 확인"인지, "오늘 새로 올리는 이관 PR에서 코멘트 1회 받기"인지 정해야 한다. 이 문서는 후자로 썼다(6번 체크박스).
3. **자동 리뷰 워크플로가 `day1` 에 없다.** `.github/workflows/pr-review.yml` 은 `origin/main` 에만 있다. `git rev-list --left-right --count day1...origin/main` 은 `11 4` 다(`day1` 만 11개, `origin/main` 만 4개). 새 PR의 기준 브랜치를 `origin/main` 으로 올릴지 정해야 한다.
4. **저장소 시크릿 `CLAUDE_CODE_OAUTH_TOKEN` 이 설정돼 있는지 확인하지 못했다.** 워크플로가 이 값을 쓴다. `gh secret list` 는 실행하지 않았다.
5. **`hooks/` 가 git 에 추적되지 않는다.** `settings.json` 은 이 경로를 가리킨다. 오늘 PR에 포함할지 정해야 한다.
6. **`modern/api` 를 8080 으로 띄울 환경(Java 21, DB 연결)이 지금 되는지 확인하지 못했다.** 이번에 `./gradlew` 를 실행하지 않았다.
7. **PR을 올리려면 `git push` 승인이 필요하다.** `settings.json` 의 ask 규칙이다. 오늘 어느 브랜치 이름으로 올릴지 정하지 않았다.

## 5. 단계

### 1단계 — 동작 보존 확인 (약 25분)

- **할 일**: 레거시(8081)로 기준 통과를 확인하고, 새 API를 8080 으로 띄워 같은 테스트를 돌린 뒤 `modern/api` 단위 테스트를 돌려 통과 · 실패 수를 기록한다. 수정은 하지 않는다. 실패하면 케이스 · 기대값 · 실제값 차이를 그대로 적고, 고칠 곳은 이관 코드로 한정한다.
- **만들거나 고칠 파일**: 없음. 실패했을 때만 `modern/api/src/main/java/com/example/item/ItemSearch*.java` 를 고친다(`characterization/` 는 고치지 않는다).
- **확인 방법**:
  ```bash
  docker compose ps                                     # item-bank 8081, mariadb 가 Up
  (cd modern/api && ./gradlew bootRun)                  # 다른 터미널에서 실행. 이하는 저장소 루트에서
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/   # 200
  (cd characterization && npm test)                     # 레거시 대상
  (cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test)   # 새 API 대상
  git status --short characterization                   # 출력 없음
  (cd modern/api && ./gradlew test)                     # BUILD SUCCESSFUL, 실패 0건
  ```
  - 눈으로 볼 결과: 첫 `npm test` 는 `Tests 33 passed`, 둘째는 `32 passed | 1 skipped`(`example-units` 1건은 레거시 전용). 숫자가 다르면 그 차이를 그대로 보고한다.

### 2단계 — PR 올리고 자동 리뷰 코멘트 확인 (약 15분)

- **할 일**: 4번 질문 2 · 3 · 5 · 7 의 답에 따라 PR 1개를 올리고, `Claude Auto Review` 워크플로가 코멘트를 남기는지 본다. `git push` 와 PR 생성은 사람이 승인한 뒤에만 한다.
- **만들거나 고칠 파일**: 없음(PR 생성만). 4번 3번 답이 "`day1` 에 워크플로를 반영"이면 `.github/workflows/pr-review.yml` 한 파일이 추가된다.
- **확인 방법**:
  ```bash
  gh pr list --repo parkbeomil/mirae-n-handson --state open       # 올린 PR 번호가 보인다
  gh run list --repo parkbeomil/mirae-n-handson --workflow "Claude Auto Review" --limit 3   # 최신 실행의 conclusion 이 success
  gh pr view <PR번호> --repo parkbeomil/mirae-n-handson --json comments --jq '.comments[] | [.author.login, .createdAt] | @tsv'
  ```
  - 눈으로 볼 결과: `claude` 가 작성자이고 `createdAt` 이 2026-10-20 이후인 코멘트가 1건 있다. 코멘트의 지적마다 수용 · 기각을 한 줄로 적는다.

## 6. 완성 기준 (Day 4-1에서 점검)

- [ ] `cd characterization && npm test` 의 마지막 줄이 `Tests 33 passed (33)` 이고 실패가 0건이다. (레거시 8081 대상)
- [ ] `TARGET_BASE_URL=http://localhost:8080 npm test` 의 마지막 줄에 `failed` 가 없고 `32 passed | 1 skipped` 가 찍힌다. (새 API 8080 대상, 케이스 이름이 `item-bank.test.js` 의 19개와 같다)
- [ ] `git status --short characterization` 와 `git diff --stat characterization` 의 출력이 둘 다 비어 있다. (테스트 · 스냅샷을 고치지 않았다)
- [ ] `cd modern/api && ./gradlew test` 가 `BUILD SUCCESSFUL` 이고 실패 테스트가 0건이다. 통과 수를 숫자로 적는다.
- [ ] PR 1개의 주소를 적었고, `gh pr view <번호> --json comments` 결과에 작성자 `claude`, `createdAt` 이 2026-10-20 이후인 코멘트가 정확히 1건 있다.
- [ ] 그 코멘트의 각 지적에 `docs/approval-checklist.md` 의 항목 번호가 붙어 있고, 지적마다 수용 · 기각을 한 줄로 적었다.
