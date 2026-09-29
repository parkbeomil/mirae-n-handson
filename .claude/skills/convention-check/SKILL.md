---
name: convention-check
description: modern/api · modern/web 변경분이 루트 CLAUDE.md 의 코딩 컨벤션 · 금지 사항을 지켰는지 예/아니오로 점검하고 위반을 파일:줄번호로 보고한다. "컨벤션 점검", "커밋 전 점검", "리뷰 전에 확인", "규칙 지켰는지 봐줘" 같은 요청이나 커밋 · PR 직전에 사용한다. 코드는 고치지 않는다.
argument-hint: "[점검할 경로 ...] (생략하면 git 변경 파일)"
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash(git diff *)
  - Bash(git status *)
---

# 컨벤션 점검

- **코드를 직접 고치지 않는다.** 위반은 보고만 하고 수정 방향을 적는다.
- **근거 라인을 댈 수 없는 지적은 하지 않고 "확인 필요"로 남긴다.**

## 1. 점검 대상 정하기

- 인자로 경로를 받았으면 그 경로 안의 파일 전체를 점검한다(`Glob` 으로 펼친다).
- 인자가 없으면 변경 파일만 점검한다.
  1. `git status --porcelain` 으로 수정(`M`) · 추가(`A`) · 추적 안 된 새 파일(`??`)을 모은다. 삭제(`D`)는 뺀다.
  2. `git diff HEAD -U0 -- <파일>` 의 `@@` 헤더로 바뀐 줄 번호를 구한다. 새 파일(`??`)은 전체 줄이 대상이다.
  3. 위반은 바뀐 줄에서 찾은 것만 보고한다. 바뀌지 않은 기존 코드의 위반은 보고하지 않는다.
- 대상이 0개면 "점검 대상 없음"만 출력하고 끝낸다.

## 2. 점검 항목

파일은 `Read` 로 열어 줄번호를 확인한다. `Grep` 결과만으로 판정하지 않는다.

| # | 항목 | 대상 파일 | 위반 조건 |
|---|---|---|---|
| 1 | 컨트롤러는 서비스만 호출 [팀 규칙] | `*Controller.java` | 필드 · 생성자 파라미터에 `*Repository` · `JdbcTemplate`, 또는 `SELECT`/`INSERT`/`UPDATE`/`DELETE` SQL 문자열 |
| 2 | 예외를 삼키지 않음 [팀 규칙] | `*.java` | `catch (…) {` 본문이 비었거나 주석만 있음 |
| 3 | 로그는 SLF4J로만 [팀 규칙] | `*.java` | `System.out` · `System.err` · `printStackTrace(` |
| 4 | 예외 → HTTP 변환은 핸들러에서만 | `*Controller.java` | 메서드 안에 `try` 또는 `ResponseEntity.status(` |
| 5 | 와일드카드 import 금지 | `*.java` | `import 패키지명.*;` |
| 6 | 요청은 `getJson` 을 거침 | `modern/web/src/**` | `src/api/client.ts` 밖의 `fetch(` |
| 7 | web 금지 구문 | `*.ts` · `*.tsx` | `any` 타입, `as unknown as`, `@ts-ignore`, `console.log`, `dangerouslySetInnerHTML` |
| 8 | 승인 필요 파일 [팀 규칙] | 변경 파일 목록 | `legacy/**`, `build.gradle`, `package.json`, `package-lock.json`, `application.yml`, 마이그레이션 · 시드 파일이 목록에 있음 |

- 8번은 위반이 아니라 "사전 승인 확인 필요"로 판정한다. 줄번호는 변경 hunk의 첫 줄을 쓴다.
- 7번의 `any` 는 타입 위치(`: any`, `<any>`, `any[]`)만 센다. 문자열 · 주석 안의 단어는 제외한다.
- 테스트 파일(`*Test.java`, `*.test.ts(x)`)은 3 · 7번의 `console.log` · `System.out` 만 점검하고 나머지는 건너뛴다.
- 대상 파일 종류가 없는 항목은 "해당 없음"으로 판정한다.

## 3. 출력 형식 (순서 고정)

```
### 판정 요약
- 점검 대상: N개 파일 (인자 경로 | git 변경분)
- 결과: 위반 N건 / 확인 필요 N건
| # | 항목 | 판정 |      ← 8행, 판정은 예(지킴) · 아니오(위반) · 확인 필요 · 해당 없음

### 위반 목록
| 파일:줄번호 | 어긴 규칙 | 수정 방향 |
|---|---|---|
| modern/api/src/main/java/com/example/item/FooController.java:12 | #1 컨트롤러는 서비스만 호출 | FooService 에 조회 메서드를 두고 컨트롤러는 그 메서드를 호출 |
```

- 위반이 0건이면 표 대신 "위반 없음" 한 줄을 쓴다.
- "확인 필요" 항목도 위반 목록 표에 넣는다. 어긴 규칙 칸 앞에 `[확인 필요]` 를 붙인다.
- 표 뒤에 다른 제안이나 리팩터링 의견을 덧붙이지 않는다.
