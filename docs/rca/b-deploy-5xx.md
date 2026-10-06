# 장애 원인 분석 — b-deploy-5xx

> 대상 로그: `incident-logs/b-deploy-5xx/` (3개 파일: `deploy-history.md` · `app.log` · `nginx-access.log`).
> 이 문서는 3절의 가설 표까지다. 반증 실행 · 코드 대조 · 판정 · 원인 · 수정안은 쓰지 않았다.
> 조사 방식: 로그는 통째로 읽지 않고 `wc` · `head` · `tail` · `grep -c` · `grep -n` · `awk` 집계로만 확인했다. `deploy-history.md` 는 39줄이라 전체를 읽었다. IP · 이메일 · 토큰은 마스킹했다.

## 1. 수집 범위

### 1-1. 파일별 기록 기간 · 줄 수 · 시각 형식 · 타임존

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 | 타임존 |
|---|---|---|---|---|
| `deploy-history.md` | 39 | 2026-09-01 ~ 2026-09-17 (배포 4건) | `2026-09-17 13:40:05` | KST. `deploy-history.md:3` 에 "시각은 모두 KST" |
| `app.log` | 2,938 (시각이 있는 줄 1,275 + 스택 트레이스 이어지는 줄 1,663) | 2026-09-17 09:00:04.878 ~ 14:39:57.905 | `2026-09-17T09:00:04.878+09:00` (ISO-8601, 밀리초) | KST. 오프셋 `+09:00` 이 시각 줄 1,275줄 모두에 붙어 있다 |
| `nginx-access.log` | 20,365 | 2026-09-17 09:00:00 ~ 14:39:58 | `[17/Sep/2026:09:00:00 +0900]` | KST. 오프셋 `+0900` 이 20,365줄 모두에 붙어 있다 |

- `app.log` 로그 레벨 분포: INFO 1,194 · WARN 35 · ERROR 46. WARN 35건은 모두 `state conflict` 409 이다.
- `nginx-access.log` 상태 코드 분포: 200 19,278 · 404 969 · 409 35 · 400 29 · 500 46 · 502 8.
- 배포 대상 호스트는 API-1 이고 대기 호스트 API-2 는 평소 정지다(`deploy-history.md:3`). 로그에는 호스트 단서가 없다.

### 1-2. KST 기준 변환 규칙

세 파일 모두 KST 라 변환하지 않는다. 근거는 두 가지다.

- 같은 요청이 두 로그에 같은 초로 있다. `nginx-access.log:3150` 의 `09:52:17` 과 `app.log:194` 의 `09:52:17.335` 다.
- 재기동 구간이 일치한다. `app.log:1240` 의 종료 시작 `13:41:48.214` 부터 `app.log:1264` 의 기동 완료 `13:42:00.304` 사이에 `nginx-access.log:16865` ~ `nginx-access.log:16872` 의 502 가 `13:41:51` ~ `13:41:59` 로 들어 있다.
- 각 시각은 사건이 **끝났거나 감지된** 시각이다. `nginx-access.log` 는 응답이 끝날 때, `app.log` 의 ERROR 는 예외를 처리할 때 찍힌다. 시작 시각은 더 앞이다.

### 1-3. 분 단위 이상 건수와 평소 수준

구간은 배포 시각(`deploy-history.md:7`)으로 나눴다. `redistribute` 는 `POST /api/distributions/{id}/redistribute` 요청이다. 건수는 `nginx-access.log` 줄 기준이다.

| 구간 (KST) | 분당 요청 수 | `redistribute` 200 · 409 · 500 | 5xx |
|---|---|---|---|
| 배포 전 09:00:00 ~ 13:40:04 | 59.7 (09:00 ~ 13:39, 280분) | 65 · 31 · 6 | 7건(500). 전부 distribution 41 대상이다(`redistribute` 6 + `GET /api/distributions/41` 1) |
| 재기동 13:40:05 ~ 13:42:31 | — | 2 · 0 · 0 | 8건(502) |
| 배포 후 13:42:32 ~ 14:39:58 | 60.1 (13:43 ~ 14:39, 57분) | 5 · 4 · 39 | 39건(500) |

- `redistribute` 의 500 비율은 배포 전 6 / 102 (5.9%), 배포 후 39 / 48 (81%)다.
- 09:52:17 이전 5xx 는 0건이다. 분 단위 5xx 건수는 다음과 같다.
  - 배포 전: 09:52 3 · 10:31 1 · 11:17 1 · 11:18 2
  - 재기동: 13:41 8(502)
  - 배포 후: 13:46 2 · 13:47 1 · 13:50 4 · 13:52 1 · 13:53 1 · 13:55 3 · 13:58 2 · 14:02 2 · 14:03 4 · 14:06 1 · 14:11 3 · 14:15 1 · 14:16 2 · 14:22 3 · 14:27 2 · 14:28 1 · 14:31 2 · 14:32 1 · 14:34 3
- 평소 잡음은 404(배포 전 796 · 후 173)와 `state conflict` 409(배포 전 31 · 후 4)다.

### 1-4. 수집 범위 제안

| 구분 | 시각 (KST) | 근거 |
|---|---|---|
| 시작 | **09:52:17** (배포 전 첫 500) | `nginx-access.log:3150` |
| 배포 영향 시작 | 13:41:51 (첫 502) / 13:46:38 (배포 후 첫 500) | `nginx-access.log:16865` / `nginx-access.log:17155` |
| 끝 | 해소 기록 없음. 마지막 500 14:34:45, 로그 끝 14:39:58 | `nginx-access.log:20084` / `nginx-access.log:20365` |

- **앞 여유는 더 필요 없다.** 로그 시작(09:00:00)이 첫 500 보다 52분 앞이라 평소 비교 구간이 이미 들어 있다.
- **뒤 여유는 없다.** 로그가 14:39:58 에서 끝나 복구를 확인할 구간이 없다.
- **수집 범위는 전체(09:00:00 ~ 14:39:58)다.** `app.log` 는 시각 줄 기준으로 먼저 보고, 스택 트레이스(1,663줄)는 필요한 줄만 본다.

**수집 공백 (원인 판단 아님)**

- 배포 전 날짜(2026-09-14 v1.4.1 ~ 2026-09-16)의 로그가 없다. 같은 오류가 v1.4.2 이전 언제부터 있었는지 알 수 없다.
- 14:35:26 이후 `redistribute` 요청이 없다(마지막 `nginx-access.log:20119`, 409). 마지막 500 이후 해소됐는지 판단할 자료가 없다.
- 대기 호스트의 로그, DB 상태, 스테이징 기록, 클라이언트 로그가 없다.
- `deploy-history.md:20` 의 배포 호출 명령에 `Authorization: Bearer [TOKEN]` 가 들어 있다. 이 문서에는 값을 옮기지 않았다.
- `nginx-access.log` 의 요청 쿼리에 `student=` 값이 있다. 이 문서에 옮기지 않았다.

## 2. 타임라인

| 시각 (KST) | 사건 | 근거 |
|---|---|---|
| 09:00:00 · 09:00:04.878 | 두 로그 시작 | `nginx-access.log:1`, `app.log:1` |
| 09:52:17 | 첫 500: `POST /api/distributions/41/redistribute`. `app.log` 에 `unhandled exception` ERROR 와 `JpaObjectRetrievalFailureException: Entity … Assignment … identifier value 9 does not exist` | `nginx-access.log:3150`, `app.log:194`, `app.log:195` |
| 09:52:30 ~ 11:18:02 | 같은 distribution 41 의 500 6건 추가(09:52:30 · 09:52:41 · 10:31:05(`GET`) · 11:17:40 · 11:18:01 · 11:18:02). 스택은 `findWithDetailsById` → `DistributionService.loadOrThrow(DistributionService.java:65)` | `nginx-access.log:3162`, `nginx-access.log:3174`, `nginx-access.log:5563`, `nginx-access.log:8442`, `nginx-access.log:8466`, `nginx-access.log:8468`, `app.log:204`, `app.log:205` |
| 13:38 | 승인(`***@example.com`) | `deploy-history.md:21` |
| 13:40:05 | v1.4.2 배포 시작. 변경: `redistribute` 응답에 같은 학급의 배포 이력(`history`) 포함, `DistributionController.redistribute` 에서 `DistributionService.listByClass(classId)` 호출 추가 | `deploy-history.md:7`, `deploy-history.md:16` |
| 13:41:48.214 | 앱 graceful shutdown 시작(비무중단 단일 호스트 재기동) | `app.log:1240`, `deploy-history.md:18` |
| 13:41:51 ~ 13:41:59 | 502 8건(`/api/units/…` 등 조회 요청) | `nginx-access.log:16865` ~ `nginx-access.log:16872` |
| 13:41:55.902 · 13:42:00.304 | v1.4.2 기동 시작 · `Started ItemBankApplication in 4.921 seconds` | `app.log:1245`, `app.log:1264` |
| 13:42:31 | 배포 완료 | `deploy-history.md:7` |
| 13:46:38 | 배포 후 첫 500: distribution 42. 직전 줄이 `redistributed distribution 42 (assignment 4, class 1)` INFO 이고, 스택에 `DistributionService.listByClass(DistributionService.java:38)` 와 `DistributionController.redistribute(DistributionController.java:40)` 가 있다. 원인 예외는 `Assignment … identifier value 9 does not exist` | `nginx-access.log:17155`, `app.log:1274`, `app.log:1275`, `app.log:1286`, `app.log:1294`, `app.log:1304` |
| 13:50:12 | 첫 distribution 43 의 500 | `nginx-access.log:17365`, `app.log:1396` |
| 13:42:00 ~ 14:34:45 | 배포 후 500 39건(distribution 42 19건 · 43 20건). 39건 모두 직전 줄이 `redistributed distribution …` INFO 이고 스택에 `listByClass` 가 있다. 같은 구간 그 외 distribution 의 `redistribute` 10건(200 6 · 409 4)은 500 이 없다 | `app.log:1275` ~ `app.log:2887`, `nginx-access.log:16884`, `nginx-access.log:17353`, `nginx-access.log:17734`, `nginx-access.log:17867`, `nginx-access.log:19238`, `nginx-access.log:19786` |
| 14:34:45 | 마지막 500(distribution 43) | `nginx-access.log:20084`, `app.log:2887` |
| 14:35:26 | 마지막 `redistribute` 요청(409, distribution 2) | `nginx-access.log:20119` |
| 14:39:57 ~ 14:39:58 | 로그 끝 | `app.log:2938`, `nginx-access.log:20365` |

## 3. 가설과 검증

> 이 절은 가설만 세운다. 어느 가설이 맞는지는 판정하지 않는다.
> 아래 명령은 `incident-logs/b-deploy-5xx/` 에서 실행한다. 이 문서에서는 실행하지 않았다.

### 3-0. 증상과 원인의 구분

- 가장 많이 나온 오류는 이벤트 기준으로 `unhandled exception on /api/distributions/N/redistribute` ERROR 45건이다. `app.log` ERROR 는 46건이고 나머지 1건은 `GET /api/distributions/41` 이다. `nginx-access.log` 의 500 도 46건이다. 모두 **증상**이다. 이 줄들의 예외는 `Assignment … does not exist` 라는 같은 한 가지다.
- 첫 500(09:52:17, `nginx-access.log:3150`)은 배포(13:40:05) 약 3시간 48분 전이다. "배포 후 5xx 증가"는 관찰이고, 배포가 원인이라는 뜻이 아니다.
- 따라서 풀어야 할 질문은 둘이다. 왜 `Assignment` 를 못 찾는가. 왜 배포 뒤에 500 이 늘었는가.

### 3-1. 가설 H1 — 애플리케이션 코드: 배포한 `listByClass` 호출이 끊어진 참조를 읽는다

| 항목 | 내용 |
|---|---|
| 가설 | v1.4.2 가 `redistribute` 에 추가한 `DistributionService.listByClass` 호출이, 같은 학급의 배포를 읽다가 `Assignment` 9 를 찾지 못하는 배포를 만나 예외를 낸다. 재배포는 이미 끝난 뒤라 응답 단계에서만 500 이 된다. |
| 지지 근거 | ① `deploy-history.md:16` 이 변경 지점을 "`DistributionController.redistribute` 에서 재배포 후 `DistributionService.listByClass(classId)` 호출 추가"라고 적었다. ② 배포 후 500 39건은 모두 직전 줄이 `redistributed distribution …` INFO 이고(예: `app.log:1274` → `app.log:1275`) 스택에 `listByClass`(`app.log:1286`)와 `DistributionController.redistribute`(`app.log:1294`)가 있다. 배포 전 500 6건은 이 INFO 가 없고 `loadOrThrow`(`app.log:205`)에서 났다. ③ 13:42:00 이후 distribution 42 · 43(INFO 상 학급 1)은 39건 모두 500 이다(`nginx-access.log:17155`, `nginx-access.log:17365`). 같은 두 대상은 13:42:00 이전에 18건 모두 200 이었다. 그 외 distribution 10건은 500 이 없다(`nginx-access.log:16884` 등). |
| 반증 조건 | (a) 배포 후 500 중 스택에 `listByClass` 가 없는 건이 있다. (b) DB 에서 distribution 41 이 학급 1 이 아니거나 `assignment` 9 를 가리키지 않는다. (c) 학급 1 이 아닌 학급의 `redistribute` 에서 같은 예외가 `listByClass` 로 난다. |
| 확인 방법 | (a) `sed -n '1265,$p' app.log \| grep -cE '^20[0-9]{2}-.* ERROR '` 와 `sed -n '1265,$p' app.log \| grep -c 'DistributionService.listByClass(DistributionService.java'` 를 비교한다. (b) DB 에서 `distribution` 41 · 42 · 43 의 학급과 과제 참조, `assignment` 9 의 존재를 본다. 열어 볼 파일은 `modern/api/src/main/java/com/example/assignment/DistributionService.java` 의 38줄 근처와 `modern/api/src/main/java/com/example/assignment/DistributionController.java` 의 40줄 근처다. (c) `grep -B1 'unhandled exception on /api/distributions/[0-9]*/redistribute' app.log \| grep -o 'class [0-9]*' \| sort \| uniq -c`. |

### 3-2. 가설 H2 — 데이터 · DB: 41 이 가리키는 `Assignment` 9 행이 없다

| 항목 | 내용 |
|---|---|
| 가설 | distribution 41 이 참조하는 `Assignment` 9 행이 DB 에 없다(삭제나 누락으로 참조가 끊겼다). v1.4.2 이전부터 있던 데이터 결함이다. |
| 지지 근거 | ① 500 46건의 원인 예외가 모두 `Entity … Assignment … identifier value 9 does not exist` 다(`app.log:195`, `app.log:224`, `app.log:1304`). ② 배포 전 500 7건은 모두 distribution 41 대상이고(`nginx-access.log:3150` ~ `nginx-access.log:8468`) 첫 건이 배포 약 3시간 48분 전이다. ③ 41 은 `findWithDetailsById`(`app.log:204`) → `loadOrThrow`(`app.log:205`)에서 조회하다 실패한다. |
| 반증 조건 | (a) 예외가 난 시점에 `assignment` 9 가 DB 에 있었다. (b) distribution 41 이 가리키는 과제가 9 가 아니다. (c) 500 의 원인 예외에 `identifier value` 가 9 아닌 값도 있다. |
| 확인 방법 | (a) · (b) DB 에서 `assignment` 9 와 `distribution` 41 을 조회하고 `SHOW CREATE TABLE distribution` 으로 참조 제약 여부를 본다. 삭제 시각은 DB 감사 로그나 배치 로그에 있을 수 있다(이번 수집 범위에 없다). (c) `grep -A1 ' ERROR .*unhandled exception' app.log \| grep -o 'identifier value [^ ]* does not exist' \| sort \| uniq -c`. |

### 3-3. 가설 H3 — 배포 · 검증 환경: 스테이징 시험이 이 데이터를 만나지 못했다

| 항목 | 내용 |
|---|---|
| 가설 | 스테이징 DB(시드 데이터)에는 끊어진 참조가 없어서, 운영 데이터에서만 실패하는 `listByClass` 경로를 배포 전에 잡지 못했다. |
| 지지 근거 | ① `deploy-history.md:17` 은 시험을 "`./gradlew test` 통과 (24 tests). 스테이징 DB(시드 데이터)에서 재배포 3건 수동 확인"이라고 적었다. ② 운영에는 배포 전부터 41 의 오류가 있었다(`nginx-access.log:3150`). ③ 변경 기록 `deploy-history.md:14` ~ `deploy-history.md:22` 에 데이터 정합성 점검 항목이 없다. |
| 반증 조건 | (a) 스테이징 DB 에도 끊어진 참조가 있었는데 같은 시험이 통과했다(환경 차이가 아니다). (b) 수동 확인 3건이 끊어진 참조를 포함한 학급의 재배포를 포함했고 `listByClass` 가 정상 응답했다. |
| 확인 방법 | 스테이징 DB 의 `distribution` · `assignment` 정합성을 점검한다. 수동 확인 3건의 대상 id 는 배포 티켓이나 파이프라인 기록에서 찾는다(이번 수집 범위에 없다). 시험이 끊어진 참조 데이터를 쓰는지는 `modern/api/src/test/java/com/example/assignment/DistributionServiceTest.java` 를 열어 본다. |

### 3-4. 가설 H4 — 트래픽 · 사용 패턴: 500 을 받은 단말이 같은 재배포를 반복한다

| 항목 | 내용 |
|---|---|
| 가설 | 교사 단말이 500 을 받고도 같은 학급의 재배포를 반복 호출해 500 건수가 늘었다. 재배포는 매번 이미 성공해 있어서 반복마다 같은 대상이 다시 재배포된다. |
| 지지 근거 | ① 500 직전마다 재배포가 성공했다(`app.log:1274` → `app.log:1275`). ② 같은 대상에 반복된다: distribution 42 에 19건, 43 에 20건이다. ③ distribution 42 의 500 이 13:46:38 · 13:46:58 · 13:47:08 로 10 ~ 20초 간격이다(`app.log:1275`, `app.log:1313`, `app.log:1351`). ④ 요청 총량은 달라지지 않았다(분당 59.7 → 60.1). 그래서 총량 증가가 아니라 같은 대상의 반복 호출을 보는 가설이다. |
| 반증 조건 | (a) 500 요청의 클라이언트가 모두 달라 같은 단말의 재호출이 아니다. (b) 같은 단말이 같은 distribution 을 500 직후 다시 호출한 쌍이 없다. |
| 확인 방법 | `awk '$9==500 && $7 ~ /redistribute/{print $1, substr($4,14,8), $7}' nginx-access.log` 로 (클라이언트, 시각, 대상)을 뽑아 같은 클라이언트의 반복을 센다. 클라이언트 값은 리포트에 옮길 때 가린다. 프런트의 재시도 동작은 클라이언트 로그가 있어야 알 수 있다(이번 수집 범위에 없다). |

### 3-5. 가설 H5 — 배포 방식 · 인프라: 비무중단 재기동이 502 8건을 만들었다

| 항목 | 내용 |
|---|---|
| 가설 | 단일 호스트를 무중단 없이 재기동(약 12초)했고 대기 호스트 API-2 가 정지 상태라, 재기동 구간의 요청이 502 로 응답됐다. 이 가설은 502 8건만 설명한다. |
| 지지 근거 | ① `deploy-history.md:18` 이 "단일 호스트 재기동(무중단 아님). 예상 중단 10초 안팎"이라고 적었다. ② 대기 호스트는 평소 정지다(`deploy-history.md:3`). ③ 종료 시작(`app.log:1240`, 13:41:48.214)부터 기동 완료(`app.log:1264`, 13:42:00.304)까지 12초이고, 502 8건이 그 안(13:41:51 ~ 13:41:59, `nginx-access.log:16865` ~ `nginx-access.log:16872`)에 있다. |
| 반증 조건 | (a) 502 가 재기동 구간 밖에도 있다. (b) 재기동 구간에 502 없이 대기 호스트가 응답했다. |
| 확인 방법 | (a) `awk '$9==502{print substr($4,14,8)}' nginx-access.log` 의 시각을 `sed -n '1240p;1264p' app.log` 의 구간과 비교한다. (b) nginx upstream 설정(`backup` · `max_fails`)과 `nginx-error.log` 를 본다(이번 수집 범위에 없다). |

마스킹 적용: 유형별 건수 — IP 3건 · 이메일 1건 · 학생 식별자 0건 · 토큰 · 세션 ID · API 키 · Authorization 값 1건 · 비밀번호 · 접속 문자열 0건
