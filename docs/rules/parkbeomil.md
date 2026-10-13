# 실 전체 개발자 규칙 후보 — 박범일 (주 스택: spring)

`.claude/settings.json`(permissions · hooks)과 `hooks/`, `scripts/hook-node.sh` 의 현행 규칙을 실 전체에 걸 후보로 정리한 문서다. 기존 파일은 수정하지 않았다.

- 출처: `.claude/settings.json` 85줄, `hooks/block-dangerous.mjs`, `hooks/secret-scan.mjs`, `scripts/hook-node.sh`
- 제외: `defaultMode: "default"`(규칙이 아니라 모드 설정), `.claude/settings.local.json`(개인 파일 — "주의" 참고)
- 적용 범위: 어느 스택에서든 뜻이 같으면 `공통`, 특정 빌드 · 패키지 도구에 매인 규칙은 스택 코드(`spring` · `react` · `php` · `data-infra`)
- 행 수: permissions 55(deny 22 · ask 11 · allow 22) + Hook 7 = 62행

## 규칙 표

| 규칙 | 종류(deny / ask / allow / Hook) | 적용 범위(공통 또는 스택 코드) | 막는 사고 또는 허용 이유 |
|---|---|---|---|
| `Bash(rm -rf *)` | deny | 공통 | 디렉터리 통째 삭제. 단 접두 패턴이라 `rm -fr` · `rm -r -f` 는 못 잡아 Hook 이 보완한다 |
| `Bash(sudo *)` | deny | 공통 | 관리자 권한으로 시스템 파일 · 설정을 바꾸는 사고 |
| `Bash(git push --force *)` | deny | 공통 | 원격 이력 덮어쓰기로 동료 커밋 유실 |
| `Bash(git push -f *)` | deny | 공통 | 위와 같음(단축 옵션) |
| `Bash(git reset --hard *)` | deny | 공통 | 커밋하지 않은 작업 내용 유실 |
| `Bash(*prod-db*)` | deny | 공통 | 운영 DB 호스트 접속. 같은 문자열을 Hook 도 막는다(이중 방어) |
| `Bash(*prod.db*)` | deny | 공통 | 위와 같음(`prod.db` 표기) |
| `Bash(curl *)` | deny | 공통 | 내부 데이터 · 토큰의 외부 전송, 출처 불명 스크립트 내려받기 |
| `Bash(wget *)` | deny | 공통 | 위와 같음 |
| `Read(./.env)` | deny | 공통 | 환경 변수 파일의 비밀값이 모델 컨텍스트로 유입 |
| `Read(./.env.*)` | deny | 공통 | `.env.local` · `.env.production` 등 변형 파일 |
| `Read(./secrets/**)` | deny | 공통 | 비밀 디렉터리 읽기 |
| `Read(**/*.pem)` | deny | 공통 | 개인키 · 인증서 읽기 |
| `Read(~/.ssh/**)` | deny | 공통 | SSH 개인키 유출 |
| `Read(~/.aws/**)` | deny | 공통 | AWS 자격증명 유출 |
| `Edit(./.env)` | deny | 공통 | 비밀 파일을 AI 가 만들거나 덮어쓰는 사고 |
| `Edit(./.env.*)` | deny | 공통 | 위와 같음(변형 파일) |
| `Edit(./secrets/**)` | deny | 공통 | 위와 같음(비밀 디렉터리) |
| `mcp__github__delete_file` | deny | 공통 | 원격 저장소 파일 삭제 |
| `mcp__github__merge_pull_request` | deny | 공통 | 리뷰 없는 머지. 머지는 사람이 승인 체크리스트를 거친 뒤 한다 |
| `Bash(terraform apply *)` | deny | data-infra | 인프라 실변경을 AI 가 직접 적용 |
| `Bash(terraform destroy *)` | deny | data-infra | 인프라 리소스 일괄 삭제 |
| `Bash(git push *)` | ask | 공통 | 원격 반영은 매번 사람이 확인(force 는 위에서 deny) |
| `Bash(npm install *)` | ask | react | 의존성 추가는 사람에게 먼저 묻는다는 팀 규칙(CLAUDE.md §3) |
| `Bash(npm i *)` | ask | react | 위와 같음(단축형) |
| `Bash(npm ci *)` | ask | react | `package-lock.json` 기준 설치. 의존성 변동 확인용 |
| `Bash(pip install *)` | ask | data-infra | 파이썬 패키지 설치는 공급망 위험이 있어 확인 |
| `Bash(docker compose down *)` | ask | 공통 | 실행 중인 DB · 서비스 컨테이너 중단 |
| `Bash(docker compose --profile *)` | ask | 공통 | 프로필별 컨테이너 기동(DB · 레거시)은 포트와 자원을 점유 |
| `mcp__github__create_pull_request` | ask | 공통 | 외부 공개 PR 생성은 사람이 확인 |
| `mcp__github__push_files` | ask | 공통 | 원격에 파일을 직접 올리는 동작 |
| `mcp__dbhub` | ask | 공통 | DB 직접 질의. 대상 · 쿼리를 사람이 확인 |
| `mcp__dbhub-mssql` | ask | 공통 | MS-SQL(레거시) DB 직접 질의 |
| `Bash(npm test *)` | allow | react | 읽기만 하는 검증. 매번 묻지 않게 허용 |
| `Bash(npm run test *)` | allow | react | 위와 같음 |
| `Bash(npm run lint *)` | allow | react | 위와 같음(정적 검사) |
| `Bash(npm run typecheck *)` | allow | react | 위와 같음(타입 검사) |
| `Bash(npm run build *)` | allow | react | 산출물 `dist/` 만 만드는 빌드 |
| `Bash(./gradlew test *)` | allow | spring | 테스트 프로필은 H2 라 DB 없이 안전. 작업 종료 검사에 필수 |
| `Bash(./gradlew check *)` | allow | spring | 위와 같음(검증 묶음) |
| `Bash(./gradlew build *)` | allow | spring | 테스트 포함 전체 빌드 |
| `Bash(git status *)` | allow | 공통 | 읽기 전용 |
| `Bash(git diff *)` | allow | 공통 | 읽기 전용 |
| `Bash(git log *)` | allow | 공통 | 읽기 전용 |
| `Bash(git add *)` | allow | 공통 | 로컬 스테이징만. 커밋 전 secret-scan 이 걸러 준다 |
| `Bash(git commit *)` | allow | 공통 | 로컬 커밋은 되돌릴 수 있다. 커밋 직전 검사는 secret-scan Hook 하나뿐이다 |
| `WebFetch(domain:docs.spring.io)` | allow | spring | 공식 문서 조회 |
| `WebFetch(domain:docs.gradle.org)` | allow | spring | 공식 문서 조회 |
| `WebFetch(domain:developer.mozilla.org)` | allow | react | 웹 표준 공식 문서 조회 |
| `mcp__github__get_file_contents` | allow | 공통 | 읽기 전용 |
| `mcp__github__list_pull_requests` | allow | 공통 | 읽기 전용 |
| `mcp__github__pull_request_read` | allow | 공통 | 읽기 전용 |
| `mcp__github__list_issues` | allow | 공통 | 읽기 전용 |
| `mcp__item-bank__search_items` | allow | 공통 | 문항 검색 조회 전용 도구 |
| `Bash(pytest *)` | allow | data-infra | 테스트 실행. 읽기 위주 |
| `block-dangerous.mjs` — `rm` 의 재귀(`-r` `-R` `--recursive`)와 강제(`-f` `--force`) 옵션 조합 | Hook | 공통 | `rm -fr` · `rm -r -f` · `rm -Rf` 등 deny 접두 패턴이 놓치는 변형 삭제 |
| `block-dangerous.mjs` — `DROP TABLE` · `DROP DATABASE`(대소문자 무시) | Hook | 공통 | 스키마 · 데이터 파괴. 어떤 DB 클라이언트 명령에도 적용 |
| `block-dangerous.mjs` — `prod-db` · `prod.db` | Hook | 공통 | 운영 DB 접근. deny 패턴과 이중 방어, 소문자만 매칭 |
| `secret-scan.mjs` — AWS Access Key ID(`AKIA…`) | Hook | 공통 | `git commit` 직전 스테이징 추가 줄에서 클라우드 키 커밋 방지 |
| `secret-scan.mjs` — `password = '…'` 형태 하드코딩 | Hook | 공통 | 코드에 비밀번호 리터럴 커밋 방지(값은 출력하지 않고 건수만 보고) |
| `secret-scan.mjs` — PEM 개인키 헤더 | Hook | 공통 | 개인키 파일 커밋 방지 |
| `scripts/hook-node.sh` — node 를 못 찾으면 명령 차단(fail-closed) | Hook | 공통 | node 가 PATH 에 없는 셸에서 Hook 이 조용히 꺼져 보호가 사라지는 사고 |

## 추가 제안

제 스택(spring)에 필요한데 지금 없는 규칙이다. 1~5번은 spring 코드, 6~8번은 현행 Hook 의 빈틈이라 공통이다.

| # | 규칙 | 종류 | 적용 범위 | 막는 사고 또는 허용 이유 |
|---|---|---|---|---|
| 1 | `Edit(modern/api/build.gradle)` | ask | spring | 의존성 추가는 먼저 사람에게 묻는다는 팀 규칙(CLAUDE.md §3)을 도구로 강제. react 쪽 `npm install` ask 와 짝 |
| 2 | `Edit(**/application.yml)` | ask | spring | DB 접속 정보 · `hikari`(`maximum-pool-size: 5`) 변경 금지를 강제. 다른 설정 수정은 필요할 수 있어 deny 가 아니라 ask |
| 3 | 스키마 · 마이그레이션 · 시드 파일 Edit | ask | spring | 스키마 변경과 시드 데이터 변경은 사람에게 먼저 묻는다는 규칙. 경로가 확정되면 패턴을 정한다 |
| 4 | `Bash(./gradlew bootRun *)` | ask | spring | 8080 포트를 점유하고 DB 컨테이너가 떠 있어야 한다. 테스트가 실제 MariaDB 에 붙는 사고 예방 |
| 5 | `curl` 의 localhost 헬스체크 허용(`curl *` deny → ask 로 낮추기) | ask | spring | 현재 `curl *` deny 가 CLAUDE.md 의 기동 확인(`curl … http://localhost:8080/`)까지 막는다. deny 는 allow 보다 우선이라 allow 로는 예외를 못 만든다. `curl *` 을 ask 로 낮추거나, 외부 도메인을 막는 Hook 으로 바꾸는 방안이 필요하다 |
| 6 | `password` 탐지에 YAML · properties 형식 추가(`password: …`, 따옴표 없는 `password=…`) | Hook | 공통 | 현행 정규식은 따옴표가 있는 `password = '…'` 만 잡아 `application.yml` 의 비밀번호를 놓친다. 단 실습용 더미 `app-pass` 는 CLAUDE.md 가 허용하므로 예외 처리가 함께 필요하다 |
| 7 | secret-scan 의 커밋 감지를 `startsWith("git commit")` 에서 복합 명령까지 확장 | Hook | 공통 | `git add . && git commit …`, `cd modern/api && git commit …` 은 검사를 건너뛴다 |
| 8 | block-dangerous 의 `prod[-.]db` · `DROP` 매칭을 대소문자 무시로 | Hook | 공통 | `PROD-DB` 처럼 대문자로 쓰면 Hook 을 통과한다(DROP 은 이미 `/i`). 두 Hook 모두 stdin 파싱 실패 시 통과(fail-open)하는 점도 팀에서 의도한 동작인지 확인이 필요하다 |

## 주의

다른 스택에 그대로 걸면 일을 막을 수 있는 규칙이다.

- **`rm -rf` 계열(deny + Hook)** — react 에서 `rm -rf node_modules dist` 같은 일상 정리가 막힌다. 삭제 대상을 좁혀 지정하거나 `npm ci` 가 대신하게 안내해야 한다.
- **두 Hook 은 명령 전체 문자열을 검사한다** — `grep "DROP TABLE" legacy/`, `grep prod-db incident-logs/` 처럼 검색만 하는 명령도 차단한다. 레거시 분석(php)과 장애 로그 분석(data-infra) 작업에 걸린다.
- **`curl` · `wget` deny** — data-infra 의 데이터 내려받기 · API 점검이 막힌다. spring 기동 확인도 막힌다(추가 제안 5).
- **`docker compose --profile` ask** — php · 레거시 모듈(8081~8083)은 이 명령으로만 기동한다. 매번 확인창이 뜬다.
- **`docker compose down` ask** — 정리 작업이 잦은 data-infra 에서 확인창이 반복된다.
- **`npm ci` ask** — CLAUDE.md 가 설치는 `npm ci` 로 하라고 정했는데 매번 승인을 요구한다. react 는 `npm install` 과 구분해 `npm ci` 는 allow 로 두는 방안을 검토할 만하다.
- **`pip install` ask** — data-infra 는 가상환경 구성 때 확인창이 반복된다.
- **`terraform apply` · `destroy` deny** — data-infra 는 이 규칙으로 적용 자체가 불가능해진다. 적용 담당자 전용 예외 경로(별도 승인 절차)가 필요하다.
- **`sudo` deny** — 패키지 설치나 서비스 제어가 필요한 인프라 작업에 걸린다.
- **`mcp__dbhub` · `mcp__dbhub-mssql` ask** — data-infra 의 적재 건수 점검처럼 조회가 잦은 작업은 확인창이 반복된다. 읽기 전용 쿼리만 allow 로 두는 구분이 필요하다.
- **`.claude/settings.local.json` 의 `Bash(curl *)` allow** — 개인 파일이라 표에서 제외했다. `settings.json` 의 deny 가 우선하므로 효과가 없다. 팀에 퍼뜨릴 규칙으로 가져가지 않는다.
- **`Read(./.env.*)` deny** — 루트의 `.env.perm-test`(미추적 파일)처럼 `.env.` 로 시작하는 모든 파일이 대상이다. 의도한 동작이지만, `.env.example` 같은 템플릿도 읽을 수 없다.
