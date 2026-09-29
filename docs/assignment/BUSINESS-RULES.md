# legacy/assignment-thymeleaf 비즈니스 규칙 후보 — 과제 배포 폼 화면

- 작성일: 2026-09-29
- 범위: 비즈니스 규칙 후보.
  - **범위 제한:** "과제 배포 폼" 화면(`GET /distributions/new` → `POST /distributions`)과 그 화면이 부르는 코드만 다룬다. 배포 목록 · 재배포 화면, 과제 목록 화면의 규칙은 제외했다.
- 근거 표기: `파일:줄번호`. 모듈 안 파일은 `legacy/assignment-thymeleaf/` 를 뺀 경로로, 모듈 밖 파일은 저장소 루트 기준 경로로 쓴다. 규칙마다 파일 경로를 생략 없이 적었다.
- 선행 문서: `docs/assignment/ARCHITECTURE.md`, `docs/assignment/ERD.md`
- 작성 원칙
  - 조건문 · SQL 에 있는 것만 규칙으로 올렸다. 주석과 코드가 다르면 코드를 기준으로 쓰고 주석은 비고에 남겼다.
  - 같은 조건을 쓰는 다른 구현(화면 템플릿 · 컨트롤러 · 서비스 · SQL)은 하나씩 따라가 비교했다.
  - 형 변환 · 경계값은 결과가 어떻게 되는지 확인해 적었고, 실행해 보지 않은 것은 비고에 "실행 확인 안 함"으로 적었다.
  - 앱 비교와 DB 비교(콜레이션)가 다를 수 있는 곳은 결과를 단정하지 않았다.
  - 코드 인용은 원문 파일의 해당 줄과 글자 그대로 대조했다.

## 요약

| 영역 | 규칙 ID | 개수 |
|---|---|---|
| 배포 폼 선택지 표시 | BR-01 ~ BR-04 | 4 |
| 배포 등록 사전 검증(컨트롤러) | BR-05 ~ BR-06 | 2 |
| 배포 등록 검증(서비스) | BR-07 ~ BR-10 | 4 |
| 배포 저장 | BR-11 ~ BR-13 | 3 |
| 결과 · 오류 처리 | BR-14 ~ BR-15 | 2 |
| 시각 기준 | BR-16 | 1 |
| **합계** | | **16** |

## 1. 배포 폼 선택지 표시

### BR-01
- 규칙: 배포 폼의 과제 선택 목록에는 상태가 `O`, `X`, `C` 인 과제만 나온다. 그 밖의 상태(`D` 포함)는 목록에 없다.
- 근거: `src/main/java/com/example/assign/dao/AssignmentDao.java:34-38`, `src/main/java/com/example/assign/web/DistributionController.java:42`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/dao/AssignmentDao.java:36
                  + " WHERE a.status IN ('O', 'X', 'C') "
  ```
- 확신도: 확실
- 비고
  - 스키마 주석은 상태를 `O=진행 C=마감` 두 가지로만 설명한다(`db/mariadb/init/01-schema.sql:87`). `X` 는 코드에서만 나온다.
  - 목록 쿼리(`AssignmentDao.findAllForList`)는 `status <> 'D'` 를 쓴다(`src/main/java/com/example/assign/dao/AssignmentDao.java:29`). 같은 "삭제 제외" 의도의 다른 구현이지만 이 화면에서 쓰는 쪽은 허용 목록 방식이라 `R`(검수중) 같은 다른 값도 뺀다. 목록 화면은 범위 밖이라 결과 차이는 비교하지 않았다.
  - 컬럼 콜레이션이 `utf8mb4_unicode_ci` 라(`db/mariadb/init/01-schema.sql:90`) 소문자 값이 저장돼 있으면 `IN` 이 어떻게 비교할지는 실행 확인 안 함.

### BR-02
- 규칙: 과제 선택 목록은 마감 시각(`due_at`)이 빠른 순, 같으면 id 가 작은 순으로 정렬된다.
- 근거: `src/main/java/com/example/assign/dao/AssignmentDao.java:37`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/dao/AssignmentDao.java:37
                  + " ORDER BY a.due_at ASC, a.id ASC";
  ```
- 확신도: 확실
- 비고: 마감이 지난 과제가 목록 앞쪽에 몰린다(정렬이 오름차순이므로). 화면에서 지난 과제는 비활성이라(BR-03) 사용자가 처음 보게 되는 항목들이 선택 불가 항목일 수 있다. 실행 확인 안 함.

### BR-03
- 규칙: 폼의 과제 옵션은 마감 시각이 현재 시각(`now`)보다 이전이고 상태가 `X` 가 아니면 비활성(`disabled`)이다. 마감 시각이 비어 있으면 비활성이 아니다.
- 근거: `src/main/resources/templates/distribution_form.html:14-16`, `src/main/java/com/example/assign/web/DistributionController.java:44`
- 근거 코드:
  ```html
  <!-- src/main/resources/templates/distribution_form.html:16 -->
                      th:disabled="${a.dueAt != null and a.dueAt.isBefore(now) and a.status != 'X'}"
  ```
- 확신도: 확실
- 비고
  - 같은 규칙의 서버 쪽 구현이 BR-06 이다. 화면은 표시용이고, 막는 것은 서버다. 비활성 옵션은 브라우저 조작으로 우회해서 보낼 수 있으므로 BR-06 이 실제 방어선이다.
  - `now` 는 폼을 그린 시점의 `AppClock.now()` 다(`src/main/java/com/example/assign/web/DistributionController.java:44`). 보낼 때의 `now`(`src/main/java/com/example/assign/web/DistributionController.java:58`)와 다른 시각이 될 수 있다. 고정 시각 설정에서는 같다(BR-16).
  - 상태 `C`(마감)이지만 마감 시각이 아직 안 지난 과제는 비활성이 아니다. 상태 `C` 로 막는 조건이 없다. 시드에서 `C` 인 과제 1, 2 는 마감도 지났다(`db/mariadb/init/02-seed.sql:120-121`)라 이 경우는 시드로는 드러나지 않는다. 실행 확인 안 함.

### BR-04
- 규칙: 마감이 지난 과제의 옵션 문구 뒤에는 상태가 `X` 면 ` [연장]`, 아니면 ` [마감]` 이 붙는다. 마감 전 과제에는 아무 표시가 붙지 않는다.
- 근거: `src/main/resources/templates/distribution_form.html:17-18`
- 근거 코드:
  ```html
  <!-- src/main/resources/templates/distribution_form.html:18 -->
                               + (${a.dueAt != null and a.dueAt.isBefore(now)} ? (${a.status == 'X'} ? ' [연장]' : ' [마감]') : '')"></option>
  ```
- 확신도: 확실
- 비고: 표시 문구일 뿐 검증은 아니다. 마감 판정 조건(`dueAt.isBefore(now)`)은 BR-03 과 같은 식을 한 번 더 쓴 것이다. 문구의 옵션 텍스트는 `id. 제목 (단원코드, 마감 MM-dd HH:mm)` 형식이며 마감 시각이 NULL 이면 `#temporals.format` 결과는 실행 확인 안 함(스키마상 `due_at` 은 NOT NULL 이라 발생하지 않는 것으로 추정: `db/mariadb/init/01-schema.sql:86`).

## 2. 배포 등록 사전 검증 (컨트롤러)

### BR-05
- 규칙: 요청한 `assignmentId` 의 과제가 없으면(상태와 무관하게 조회) "존재하지 않는 과제입니다." 오류를 담아 폼으로 되돌린다.
- 근거: `src/main/java/com/example/assign/web/DistributionController.java:53-57`, `src/main/java/com/example/assign/dao/AssignmentDao.java:41-48`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/web/DistributionController.java:54-56
          if (a == null) {
              ra.addFlashAttribute("error", "존재하지 않는 과제입니다.");
              return "redirect:/distributions/new";
  ```
- 확신도: 확실
- 비고: 서비스에도 같은 검사가 있다(BR-07). 컨트롤러 검사가 먼저라 정상 흐름에서는 서비스 쪽이 실행되지 않는다. 폼 파라미터 `assignmentId`, `classId` 는 `long` 으로 받으므로 숫자가 아닌 값은 컨트롤러 메서드에 들어오기 전에 스프링이 거부한다(응답 코드는 실행 확인 안 함).

### BR-06
- 규칙: 과제의 마감 시각이 있고 현재 시각보다 이전이며(같은 시각은 통과), 상태가 정확히 `X` 가 아니면 새 배포를 거부하고 마감 시각과 과제 제목이 든 오류를 담아 폼으로 되돌린다.
- 근거: `src/main/java/com/example/assign/web/DistributionController.java:58-65`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/web/DistributionController.java:60-65
          if (a.getDueAt() != null && a.getDueAt().isBefore(now)) {
              if (!"X".equals(a.getStatus())) {
                  ra.addFlashAttribute("error", "마감(" + AppClock.fmt(a.getDueAt()) + ")이 지난 과제는 배포할 수 없습니다. [" + a.getTitle() + "]");
                  return "redirect:/distributions/new";
              }
          }
  ```
- 확신도: 확실
- 비고
  - 주석("마감 지난 과제는 새 배포 불가. 연장(X) 상태만 예외", `src/main/java/com/example/assign/web/DistributionController.java:59`)과 코드가 일치한다.
  - 이 검사는 컨트롤러에만 있고 서비스 `distribute` 에는 없다. `DistributionService.distribute` 를 다른 곳에서 부르면 마감 검사 없이 배포된다. 범위 안에서는 컨트롤러만 부른다(`src/main/java/com/example/assign/web/DistributionController.java:67`).
  - 상태 비교는 대소문자 · 공백을 그대로 비교한다(`"X".equals`). 재배포 쪽처럼 `trim().toUpperCase()` 를 하는 구현도 있지만(`src/main/java/com/example/assign/service/DistributionService.java:176`) 이 화면 경로에는 정규화가 없다. 재배포는 범위 밖이라 참고로만 적는다.
  - 마감 검사가 서비스의 삭제 검사(BR-09)보다 먼저 실행된다. 삭제(`D`)이면서 마감이 지난 과제를 직접 POST 하면 삭제 메시지가 아니라 마감 메시지가 나온다.
  - 상태 `C` 이지만 마감 전인 과제, 상태 `R`(검수중) 과제는 이 검사에도 서비스 검사에도 걸리지 않고 배포된다(BR-09 는 `D` 만 막는다). 의도인지 누락인지는 코드로 알 수 없고 실행 확인 안 함.

## 3. 배포 등록 검증 (서비스)

### BR-07
- 규칙: 서비스는 과제를 트랜잭션 안에서 다시 조회하고, 없으면 `IllegalArgumentException("존재하지 않는 과제입니다. (id=…)")` 를 던진다.
- 근거: `src/main/java/com/example/assign/service/DistributionService.java:47-51`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/service/DistributionService.java:49-51
          if (a == null) {
              throw new IllegalArgumentException("존재하지 않는 과제입니다. (id=" + assignmentId + ")");
          }
  ```
- 확신도: 확실
- 비고: BR-05 와 같은 규칙의 서비스 쪽 구현이며 메시지가 다르다(컨트롤러 쪽은 id 가 없다). 컨트롤러 조회와 서비스 조회 사이에 과제가 삭제되는 경우에만 도달한다. 실행 확인 안 함.

### BR-08
- 규칙: `classId` 의 학급이 없으면 `IllegalArgumentException("존재하지 않는 학급입니다. (id=…)")` 를 던진다.
- 근거: `src/main/java/com/example/assign/service/DistributionService.java:52-55`, `src/main/java/com/example/assign/dao/ClassDao.java:24-30`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/service/DistributionService.java:52-55
          ClassRow c = classDao.findById(classId);
          if (c == null) {
              throw new IllegalArgumentException("존재하지 않는 학급입니다. (id=" + classId + ")");
          }
  ```
- 확신도: 확실
- 비고: 학급 존재 검사는 컨트롤러에는 없고 서비스에만 있다. 폼 select 에서는 `required` 가 걸려 있지만(`src/main/resources/templates/distribution_form.html:23`) 이는 브라우저 검증이라 규칙에 올리지 않았다.

### BR-09
- 규칙: 과제 상태가 정확히 `D`(삭제)면 `IllegalStateException("삭제된 과제는 배포할 수 없습니다.")` 를 던진다.
- 근거: `src/main/java/com/example/assign/service/DistributionService.java:56-58`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/service/DistributionService.java:56-58
          if ("D".equals(a.getStatus())) {
              throw new IllegalStateException("삭제된 과제는 배포할 수 없습니다.");
          }
  ```
- 확신도: 확실
- 비고
  - 폼 목록에서도 `D` 는 빠진다(BR-01). 즉 같은 "삭제 과제는 배포 불가"가 SQL 필터(BR-01)와 서비스 조건(BR-09) 두 곳에 있다. 폼을 거치지 않은 POST 를 막는 쪽은 BR-09 다.
  - 마감이 지난 `D` 과제는 BR-06 에서 먼저 걸린다(BR-06 비고 참조).
  - 스키마 주석에는 `D` 상태가 없다(`db/mariadb/init/01-schema.sql:87`). 시드에도 없다(`db/mariadb/init/02-seed.sql:119-125`). 이 상태가 실제 데이터에 쓰이는지는 확인 불가.

### BR-10
- 규칙: 같은 과제를 같은 학급에 이미 배포한 행이 하나라도 있으면(재배포 이력 행 포함) `IllegalStateException` 으로 새 배포를 거부한다.
- 근거: `src/main/java/com/example/assign/service/DistributionService.java:59-61`, `src/main/java/com/example/assign/dao/DistributionDao.java:47-52`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/service/DistributionService.java:59-61
          if (distributionDao.countByAssignmentAndClass(assignmentId, classId) > 0) {
              throw new IllegalStateException("이미 이 학급에 배포된 과제입니다. 다시 내려면 배포 목록에서 재배포를 사용하세요.");
          }
  ```
  ```java
  // src/main/java/com/example/assign/dao/DistributionDao.java:47-50
      public int countByAssignmentAndClass(long assignmentId, long classId) {
          Integer n = jdbc.queryForObject(
                  "SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND class_id = ?",
                  Integer.class, assignmentId, classId);
  ```
- 확신도: 확실
- 비고
  - 주석은 "같은 학급 + 같은 과제 는 한 건만 존재해야 한다 (UNIQUE 제약은 없음, 여기서 막는다)" 라고 한다(`src/main/java/com/example/assign/dao/DistributionDao.java:46`). 코드는 배포 시점에 건수만 검사하고, DB 에는 유일 제약이 없다(`db/mariadb/init/01-schema.sql:92-103`).
  - 시드에는 과제 2 · 학급 1 이 두 행 있다(`db/mariadb/init/02-seed.sql:133-134`). 주석은 "재배포 이력"이라고 설명한다(`db/mariadb/init/02-seed.sql:128`). 코드가 지키는 "한 건" 규칙과 시드 데이터가 어긋난다. 이 경우 폼에서는 새 배포가 항상 거부된다.
  - 검사와 저장(BR-11, BR-12) 사이에 잠금이 없다. 동시 요청에서 두 건이 저장되는지는 실행 확인 안 함.
  - 이 검사는 마감 · 상태와 무관하다. `X`(연장) 과제도 이미 배포돼 있으면 거부된다.

## 4. 배포 저장

### BR-11
- 규칙: 새 배포의 id 는 `distribution` 의 현재 최대 id 에 1 을 더한 값이다. 행이 없으면 1 이다.
- 근거: `src/main/java/com/example/assign/dao/DistributionDao.java:54-57`, `src/main/java/com/example/assign/service/DistributionService.java:62`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/dao/DistributionDao.java:54-57
      public long nextId() {
          Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM distribution", Long.class);
          return (max == null ? 0L : max.longValue()) + 1L;
      }
  ```
- 확신도: 확실
- 비고
  - `id` 컬럼은 `AUTO_INCREMENT` 가 아니다(`db/mariadb/init/01-schema.sql:93`). 그래서 코드가 채번한다.
  - 마지막 행이 삭제되면 그 번호가 재사용된다. 동시 요청이 같은 번호를 받으면 기본 키 충돌로 실패할 것으로 추정한다. 실행 확인 안 함.

### BR-12
- 규칙: 배포 행은 `redistributed` 를 0 으로, `distributed_at` 을 `AppClock.now()` 값으로 저장한다.
- 근거: `src/main/java/com/example/assign/dao/DistributionDao.java:59-63`, `src/main/java/com/example/assign/service/DistributionService.java:63`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/dao/DistributionDao.java:61-62
                  "INSERT INTO distribution (id, assignment_id, class_id, distributed_at, redistributed) VALUES (?, ?, ?, ?, 0)",
                  id, assignmentId, classId, Timestamp.valueOf(distributedAt));
  ```
  ```java
  // src/main/java/com/example/assign/service/DistributionService.java:63
          int n = distributionDao.insert(id, assignmentId, classId, AppClock.now());
  ```
- 확신도: 확실
- 비고: 저장 시각이 DB 시각이 아니라 앱 시각이다. 기본 설정에서는 고정 시각이 저장된다(BR-16).

### BR-13
- 규칙: 삽입된 행 수가 1 이 아니면 `IllegalStateException("배포 저장에 실패했습니다.")` 를 던진다.
- 근거: `src/main/java/com/example/assign/service/DistributionService.java:63-66`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/service/DistributionService.java:64-66
          if (n != 1) {
              throw new IllegalStateException("배포 저장에 실패했습니다.");
          }
  ```
- 확신도: 확실
- 비고: `JdbcTemplate.update` 는 실패하면 보통 예외를 던지므로 `n != 1` 분기에 도달하는 경우는 실행 확인 안 함.

## 5. 결과 · 오류 처리

### BR-14
- 규칙: 배포 등록 결과에 따라 이동 위치와 문구가 정해진다.
  - 성공: `배포가 등록되었습니다. (배포 #id)` 를 담아 `/distributions` 로 이동한다.
  - `IllegalArgumentException` 또는 `IllegalStateException`: 예외 메시지를 그대로 오류로 담아 폼으로 되돌린다.
  - `DataAccessException`: "DB 오류로 배포에 실패했습니다." 를 담아 폼으로 되돌린다.
- 근거: `src/main/java/com/example/assign/web/DistributionController.java:66-76`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/web/DistributionController.java:69-75
          } catch (IllegalArgumentException | IllegalStateException e) {
              ra.addFlashAttribute("error", e.getMessage());
              return "redirect:/distributions/new";
          } catch (DataAccessException e) {
              ra.addFlashAttribute("error", "DB 오류로 배포에 실패했습니다.");
              return "redirect:/distributions/new";
          }
  ```
- 확신도: 확실
- 비고
  - `DataAccessException` 의 원인은 로그로도 남기지 않고 삼킨다(catch 본문에 로그 호출이 없다: `src/main/java/com/example/assign/web/DistributionController.java:72-75`).
  - 서비스 메서드가 `@Transactional` 이므로(`src/main/java/com/example/assign/service/DistributionService.java:46`) 서비스가 던진 예외에서 롤백되는 것으로 추정한다. 트랜잭션 설정은 확인하지 않았고 실행 확인 안 함.
  - 실패 후 폼으로 리다이렉트하므로 사용자가 고른 과제 · 학급 값은 유지되지 않는다(폼 템플릿에 선택값 복원 코드가 없다: `src/main/resources/templates/distribution_form.html:8-31`).

### BR-15
- 규칙: 폼을 여는 `GET /distributions/new` 에서 DB 오류(`DataAccessException`)가 나면 "데이터베이스 오류" 화면을 보여 주고, DB 의 가장 구체적인 원인 메시지를 화면에 그대로 출력한다.
- 근거: `src/main/java/com/example/assign/web/DbErrorAdvice.java:11-17`, `src/main/resources/templates/db_error.html:8`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/web/DbErrorAdvice.java:15
          model.addAttribute("detail", e.getMostSpecificCause().getMessage());
  ```
  ```html
  <!-- src/main/resources/templates/db_error.html:8 -->
  <p th:text="${detail}"></p>
  ```
- 확신도: 확실
- 비고
  - DB 내부 메시지(테이블 · 컬럼 · 접속 정보가 든 수 있음)가 사용자 화면에 노출된다. 이관 시 보안 검토 대상이다. 실제 메시지 내용은 실행 확인 안 함.
  - 같은 어드바이스는 로그를 `System.out.println` 으로 남긴다(`src/main/java/com/example/assign/web/DbErrorAdvice.java:13`). 이관 컨벤션(SLF4J)과 다르다.

## 6. 시각 기준

### BR-16
- 규칙: 현재 시각은 시스템 속성 `app.clock` 이 문자열 `system` 이면 시스템 시각이고, 그 밖이면(속성이 없을 때 포함) 고정값 `2026-09-15 00:00:00` 이다.
- 근거: `src/main/java/com/example/assign/AppClock.java:9`, `src/main/java/com/example/assign/AppClock.java:14-20`
- 근거 코드:
  ```java
  // src/main/java/com/example/assign/AppClock.java:8-9
      // 2024-03 QA 기간 중 임시로 고정. 운영 반영 전 LocalDateTime.now() 로 되돌릴 것 (아직 안 되돌림)
      private static final String FIXED = "2026-09-15 00:00:00";
  ```
  ```java
  // src/main/java/com/example/assign/AppClock.java:15-19
          String sys = System.getProperty("app.clock");
          if (sys != null && sys.equals("system")) {
              return LocalDateTime.now().withNano(0);
          }
          return LocalDateTime.parse(FIXED, FMT);
  ```
- 확신도: 확실
- 비고
  - 주석은 "임시 고정, 되돌릴 것(아직 안 되돌림)"이라고 한다. 코드는 기본값이 고정 시각이다. 주석은 임시라고 하지만 실제로는 기본 동작이다.
  - 컨테이너 실행 명령에 `-Dapp.clock` 이 없다(`legacy/assignment-thymeleaf/Dockerfile:11`). 그래서 compose 로 띄우면 BR-03, BR-04, BR-06 의 "현재 시각"은 항상 2026-09-15 00:00:00 이다. 다른 실행 경로에서의 설정은 확인 불가.
  - 고정 시각(2026-09-15) 기준으로 시드 과제 1 · 2 · 3 은 모두 마감이 지난 것이 되어 BR-06 에 걸린다(`db/mariadb/init/02-seed.sql:120-122`). 과제 3 은 상태가 `O` 라 상태와 무관하게 마감 시각만으로 막힌다. 실행 확인 안 함.
  - 연도가 코드의 고정 문자열에 하드코딩돼 있어 시각이 흐르지 않는다.

## 7. 매직 넘버 · 상수

| 값 | 위치 | 의미(추정 포함) | 비고 |
|---|---|---|---|
| `'O'` `'X'` `'C'` `'D'` | `src/main/java/com/example/assign/dao/AssignmentDao.java:36`, `src/main/java/com/example/assign/web/DistributionController.java:61`, `src/main/java/com/example/assign/service/DistributionService.java:56` | 진행 · 연장 · 마감/종료 · 삭제 | 상수 · enum 없이 문자열 리터럴이다. 스키마 주석은 `O`, `C` 만 정의한다(`db/mariadb/init/01-schema.sql:87`). `X` · `D` 의 의미는 코드 문맥과 주석(`src/main/java/com/example/assign/web/DistributionController.java:59`)에서만 알 수 있다. |
| `"2026-09-15 00:00:00"` | `src/main/java/com/example/assign/AppClock.java:9` | 고정 현재 시각 | BR-16 참조 |
| `0` (redistributed 초기값) | `src/main/java/com/example/assign/dao/DistributionDao.java:61` | 재배포 아님 | SQL 문자열 안에 하드코딩 |
| `1L` (다음 id 증가분) | `src/main/java/com/example/assign/dao/DistributionDao.java:56` | 채번 증가분 | BR-11 |
| `1` (`n != 1`) | `src/main/java/com/example/assign/service/DistributionService.java:64` | 삽입 기대 행 수 | BR-13 |
| `"MM-dd HH:mm"` | `src/main/resources/templates/distribution_form.html:17` | 옵션 문구의 마감 시각 형식 | BR-04. 오류 문구의 형식은 별도로 `yyyy-MM-dd HH:mm` 이다(`src/main/java/com/example/assign/AppClock.java:12`). |

## 8. 규칙으로 올리지 않은 항목

| 항목 | 위치 | 올리지 않은 이유 |
|---|---|---|
| 과제 · 학급 select 의 `required` | `src/main/resources/templates/distribution_form.html:11`, `src/main/resources/templates/distribution_form.html:23` | 브라우저 입력 검증이다. 서버 규칙이 아니다(서버는 BR-05, BR-08 로 검사). |
| 학급 목록에 필터가 없고 id 순 전체가 나온다 | `src/main/java/com/example/assign/dao/ClassDao.java:21` | 조건 없이 전체를 돌려주는 조회라 "규칙"이라 할 조건이 없다. 옵션 문구 형식은 `이름 (담당교사ID)` 이다(`src/main/resources/templates/distribution_form.html:25`). |
| 학급과 과제의 관계(어느 교사가 어느 학급에 배포할 수 있는지) | 코드에 없음 | 담당 교사 · 권한 확인이 코드에 없다. 코드에 없는 것은 규칙으로 올리지 않았다. 이관 때 확인이 필요한 질문으로만 남긴다. |
| 주석 "목록: 삭제('D') 제외, 마감 빠른 순" | `src/main/java/com/example/assign/dao/AssignmentDao.java:26` | 이 주석은 `findAllForList`(과제 목록 화면, 범위 밖)에 붙은 것이다. 폼 쿼리(BR-01, BR-02)의 주석이 아니다. |
| `class_cnt` (배포된 학급 수) | `src/main/java/com/example/assign/dao/AssignmentDao.java:23` | 폼 화면에서 쓰지 않는 값이다. 조회 결과에 딸려 오지만 화면 규칙과 무관하다. |
| 표시용 CSS · 문구 · `menu` 값 | `src/main/resources/templates/layout.html:6-20`, `src/main/java/com/example/assign/web/DistributionController.java:45` | 표시 요소이고 비즈니스 조건이 아니다. |
