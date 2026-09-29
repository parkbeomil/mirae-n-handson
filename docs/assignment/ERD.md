# legacy/assignment-thymeleaf ERD — 과제 배포 폼 화면

- 작성일: 2026-09-29
- 범위: 데이터 흐름. 진입점 · 의존 관계는 선행 문서에 있고 비즈니스 규칙 후보는 이 문서에 넣지 않았다.
  - **범위 제한:** "과제 배포 폼" 화면(`GET /distributions/new` → `POST /distributions`)이 부르는 코드가 접근하는 DB 객체만 다룬다. 배포 목록 · 재배포 화면의 쿼리는 제외했다.
- 근거 표기: `파일:줄번호`. 모듈 안 파일은 `legacy/assignment-thymeleaf/` 를 뺀 경로로, 모듈 밖 파일은 저장소 루트 기준 경로로 쓴다. 표 칸과 bullet 마다 첫 참조에는 파일 경로를 붙였다.
- 선행 문서: `docs/assignment/ARCHITECTURE.md`
- 작성 원칙
  - 테이블 정의는 DB 초기화 스크립트를 기준으로 하고, 코드가 기대하는 값과 다르면 차이를 적는다.
  - 계정 · 비밀번호 값은 옮기지 않는다.

## 1. 정의 위치와 근거 선택

- 이 모듈이 접속하는 DB는 MariaDB `itembank` 이다(`src/main/resources/application.properties:5`, `docker-compose.yml:61`). 그래서 정의는 `db/mariadb/init/` 을 근거로 썼다.
- 모듈 안 SQL 파일은 없다(`src/main/` 아래 `.sql` 없음, 모듈 파일 목록 기준). 뷰 · 프로시저 · 트리거를 부르는 코드도 없다. 범위 안 SQL 은 모두 테이블 직접 조회 · 삽입이다(`src/main/java/com/example/assign/dao/AssignmentDao.java:21-38`, `src/main/java/com/example/assign/dao/ClassDao.java:21-25`, `src/main/java/com/example/assign/dao/DistributionDao.java:47-63`).
- `db/mssql/init/01-schema.sql:21-22`, `db/mssql/init/01-schema.sql:54-55` 에도 `class` · `assignment` 가 있지만 컬럼 · 키 타입이 다르다(예: `VARCHAR(10)` id, `unit_code`). 이 모듈은 MS-SQL 에 접속하지 않으므로 근거로 쓰지 않았다(미확인 U-5 참조).
- `db/mariadb/init/` 의 적용 순서는 `01-schema.sql` → `02-seed.sql` → `03-users.sql` 이다(`db/mariadb/init/01-schema.sql:2`).

## 2. 접근하는 테이블

| 테이블 | 정의 | 이 화면에서의 용도 |
|---|---|---|
| `assignment` | `db/mariadb/init/01-schema.sql:82-90` | 과제 목록 · 단건 조회 (읽기) |
| `unit` | `db/mariadb/init/01-schema.sql:11-18` | 과제 조회에 LEFT JOIN 해서 단원 코드 · 이름 표시 (읽기) |
| `class` | `db/mariadb/init/01-schema.sql:75-80` | 학급 목록 · 단건 조회 (읽기) |
| `distribution` | `db/mariadb/init/01-schema.sql:92-103` | 중복 확인 · 번호 채번 · 등록 (읽기 · **쓰기**), 과제 조회의 `class_cnt` 서브쿼리 (읽기) |

- 접근하는 뷰 · 프로시저: 없음.
- `class` 는 MariaDB 에서 예약어와 겹치지 않는데도 코드에서 백틱으로 감싸 쓴다(`src/main/java/com/example/assign/dao/ClassDao.java:21`). MariaDB 방언이다.

### 2.1 테이블 정의 요약 (`db/mariadb/init/01-schema.sql` 기준)

| 테이블 | 컬럼(타입, NULL 여부) | 키 · 제약 |
|---|---|---|
| `unit` | `id` INT, `code` VARCHAR(16), `name` VARCHAR(100), `grade` TINYINT (모두 NOT NULL) | PK `id`, UNIQUE `code` (`db/mariadb/init/01-schema.sql:16-17`) |
| `class` | `id` INT, `name` VARCHAR(50), `teacher_id` VARCHAR(20) (모두 NOT NULL) | PK `id` (`db/mariadb/init/01-schema.sql:79`) |
| `assignment` | `id` INT, `title` VARCHAR(200), `unit_id` INT, `due_at` DATETIME, `status` CHAR(1) 기본 `'O'` (모두 NOT NULL) | PK `id`, FK `unit_id → unit.id` (`db/mariadb/init/01-schema.sql:88-89`) |
| `distribution` | `id` INT, `assignment_id` INT, `class_id` INT, `distributed_at` DATETIME, `redistributed` TINYINT 기본 0 (모두 NOT NULL) | PK `id`, 인덱스 2개, FK 2개 (`db/mariadb/init/01-schema.sql:98-102`) |

- `distribution` 에는 `(assignment_id, class_id)` 유일 제약이 없다. 인덱스는 각각 단독 컬럼이다(`db/mariadb/init/01-schema.sql:99-100`). 코드도 "UNIQUE 제약은 없음, 여기서 막는다"고 적었다(`src/main/java/com/example/assign/dao/DistributionDao.java:46`).
- `distribution.id` 는 `AUTO_INCREMENT` 가 아니다(`db/mariadb/init/01-schema.sql:93`). 코드가 직접 채번한다(`src/main/java/com/example/assign/dao/DistributionDao.java:54-57`).

## 3. 테이블 관계

```mermaid
erDiagram
    unit ||--o{ assignment : "unit_id"
    assignment ||--o{ distribution : "assignment_id"
    class ||--o{ distribution : "class_id"

    unit {
        int id PK
        varchar code UK
        varchar name
        tinyint grade
    }
    class {
        int id PK
        varchar name
        varchar teacher_id
    }
    assignment {
        int id PK
        varchar title
        int unit_id FK
        datetime due_at
        char status
    }
    distribution {
        int id PK
        int assignment_id FK
        int class_id FK
        datetime distributed_at
        tinyint redistributed
    }
```

| 관계 | 근거 |
|---|---|
| `unit` 1 : N `assignment` | FK `fk_assignment_unit` (`db/mariadb/init/01-schema.sql:89`). 코드 조인: `LEFT JOIN unit u ON u.id = a.unit_id` (`src/main/java/com/example/assign/dao/AssignmentDao.java:24`) |
| `assignment` 1 : N `distribution` | FK `fk_distribution_assignment` (`db/mariadb/init/01-schema.sql:101`). 코드 서브쿼리: `d.assignment_id = a.id` (`src/main/java/com/example/assign/dao/AssignmentDao.java:23`) |
| `class` 1 : N `distribution` | FK `fk_distribution_class` (`db/mariadb/init/01-schema.sql:102`) |

- `assignment` 조회의 `class_cnt` 는 그 과제가 배포된 서로 다른 학급 수다(`src/main/java/com/example/assign/dao/AssignmentDao.java:23`). 폼 화면에서는 이 값을 쓰지 않는다(`src/main/resources/templates/distribution_form.html:14-18`).

## 4. 테이블별 읽기 · 쓰기 위치

| 테이블 | 읽기 | 쓰기 |
|---|---|---|
| `assignment` (+ `unit` 조인, `distribution` 서브쿼리) | `AssignmentDao.findAllForSelect` — 상태 `O`·`X`·`C` 만, 마감 빠른 순(`src/main/java/com/example/assign/dao/AssignmentDao.java:34-39`). 호출: `src/main/java/com/example/assign/web/DistributionController.java:42` <br> `AssignmentDao.findById` — 상태 무관 단건(`src/main/java/com/example/assign/dao/AssignmentDao.java:41-48`). 호출: `src/main/java/com/example/assign/web/DistributionController.java:53`, `src/main/java/com/example/assign/service/DistributionService.java:48` | 없음 |
| `class` | `ClassDao.findAll` — id 순 전체(`src/main/java/com/example/assign/dao/ClassDao.java:20-22`). 호출: `src/main/java/com/example/assign/web/DistributionController.java:43` <br> `ClassDao.findById`(`src/main/java/com/example/assign/dao/ClassDao.java:24-30`). 호출: `src/main/java/com/example/assign/service/DistributionService.java:52` | 없음 |
| `distribution` | `DistributionDao.countByAssignmentAndClass` — 같은 과제 · 학급 건수(`src/main/java/com/example/assign/dao/DistributionDao.java:47-52`). 호출: `src/main/java/com/example/assign/service/DistributionService.java:59` <br> `DistributionDao.nextId` — `MAX(id)` + 1(`src/main/java/com/example/assign/dao/DistributionDao.java:54-57`). 호출: `src/main/java/com/example/assign/service/DistributionService.java:62` | `DistributionDao.insert` — `redistributed` 는 0 으로 고정(`src/main/java/com/example/assign/dao/DistributionDao.java:59-63`). 호출: `src/main/java/com/example/assign/service/DistributionService.java:63` |
| `unit` | `assignment` 조회에 딸려서만 읽는다(`src/main/java/com/example/assign/dao/AssignmentDao.java:24`) | 없음 |

- 이 화면이 쓰는 테이블은 `distribution` 하나이고, 저장 컬럼은 `id` · `assignment_id` · `class_id` · `distributed_at` · `redistributed` 다섯 개다(`src/main/java/com/example/assign/dao/DistributionDao.java:61`).
- `distributed_at` 은 DB 시각이 아니라 `AppClock.now()` 값을 넣는다(`src/main/java/com/example/assign/service/DistributionService.java:63`). 고정 시각 설정이면 고정값이 저장된다(`src/main/java/com/example/assign/AppClock.java:9`).

## 5. 코드와 스키마가 다른 곳

| # | 코드가 기대하는 것 | 스키마 · 시드 |
|---|---|---|
| D-1 | 과제 `status` 코드 `O` · `X` · `C` · `D`(`src/main/java/com/example/assign/dao/AssignmentDao.java:36`, `src/main/java/com/example/assign/service/DistributionService.java:56`, `src/main/java/com/example/assign/web/DistributionController.java:61`) | 스키마 주석은 `O=진행 C=마감` 두 가지만 설명한다(`db/mariadb/init/01-schema.sql:87`). 시드 과제 6건은 `O` · `C` 만 쓴다(`db/mariadb/init/02-seed.sql:119-125`). `CHECK` 제약은 없다. |
| D-2 | `assignment.due_at` 이 NULL 일 수 있다고 보고 검사한다(`src/main/java/com/example/assign/web/DistributionController.java:60`, `src/main/java/com/example/assign/dao/AssignmentDao.java:60`) | `due_at` 은 `NOT NULL` 이다(`db/mariadb/init/01-schema.sql:86`). NULL 분기는 실제로 타지 않는다. |
| D-3 | `AssignmentRow.unitId` 는 `int` 로 읽는다(`src/main/java/com/example/assign/dao/AssignmentDao.java:56`) | `assignment.unit_id` 는 `NOT NULL` 이라 문제없다(`db/mariadb/init/01-schema.sql:85`). 단, 조인이 `LEFT JOIN` 이라 단원이 없으면 `unit_code` 는 NULL 이 되는데 FK 때문에 발생하지 않는다. |

## 6. 이 화면이 쓰지 않는 테이블

| 테이블 | 정의 | 비고 |
|---|---|---|
| `submission` | `db/mariadb/init/01-schema.sql:105-115` | 범위 안 코드에서 접근하지 않는다. 배포 목록 · 재배포 쪽 쿼리에서만 쓴다(`src/main/java/com/example/assign/dao/DistributionDao.java:26`, 범위 밖). 단, `distribution` 을 부모로 FK 가 걸려 있다(`db/mariadb/init/01-schema.sql:114`). |
| `item`, `tag`, `item_tag`, 뷰 `v_item_public` | `db/mariadb/init/01-schema.sql:20-70` | 문항 은행 모듈 소속이다. 이 모듈 코드에 참조가 없다. |
| MS-SQL 의 모든 객체 | `db/mssql/init/` | 이 모듈이 접속하지 않는다. |

- DB 계정: 앱 계정은 `itembank` 전체 권한, 조회 전용 계정은 SELECT 만 갖는다(`db/mariadb/init/03-users.sql:9`, `db/mariadb/init/03-users.sql:14`). 이 모듈은 앱 계정으로 접속하는 것으로 보이며(`docker-compose.yml:62`), 값은 옮기지 않았다.

## 7. 미확인 목록

| # | 항목 | 이유 |
|---|---|---|
| U-1 | 실제 실행 중인 DB 의 상태값 분포 | 초기화 스크립트만 읽었다. 운영에서 `X` · `D` · `R` 값이 쓰이는지는 확인할 수 없다(D-1). |
| U-2 | `distribution` 에 이미 (과제, 학급) 중복 행이 있는지 | 유일 제약이 없다. 시드에는 과제 2 · 학급 1 이 두 번 들어 있다(`db/mariadb/init/02-seed.sql:133-134`). 재배포 이력 행이라고 시드 주석이 설명한다(`db/mariadb/init/02-seed.sql:128`). 이 경우 배포 폼에서는 새 배포가 막힌다는 것만 코드로 확인했고 실행해 보지는 않았다. |
| U-3 | `MAX(id)+1` 채번의 동시성 | 같은 요청이 동시에 오면 어떻게 되는지 실행해 보지 않았다. PK 충돌이 나면 `DataAccessException` 계열로 컨트롤러가 잡을 것으로 추정한다(`src/main/java/com/example/assign/web/DistributionController.java:72-75`). |
| U-4 | 콜레이션 · 문자셋에 따른 영향 | 이 화면의 쿼리는 문자열 비교가 없어(`status` 는 상수 비교, 나머지는 숫자) 영향이 없다고 보지만, 상태 비교의 대소문자 구분은 콜레이션(`utf8mb4_unicode_ci`, `db/mariadb/init/01-schema.sql:90`)에 기대므로 실행 확인은 하지 않았다. |
| U-5 | MS-SQL 쪽 `class` · `assignment` 와의 관계 | 이 모듈과 무관하다고 판단했으나 두 DB 사이의 이관 계획 문서는 읽지 않았다. |
