# 배치 점검 보고서

**점검 기간 2026-09-08 ~ 2026-09-17 (10일 · 5개 job), 이상 7건 (4개 일자: 09-09 · 09-12 · 09-15 · 09-17)**

- 대상: `pipeline-samples/batch-logs/*.log`, `pipeline-samples/datamart-counts.csv`
- 기준: ① 실행 여부(누락 · 종료 기록 없음) ② 중복 실행(같은 날 같은 job의 SUCCESS 2회 이상) ③ 적재 건수(직전 7일 평균 대비 ±30% 초과, 0건은 무조건 이상) ④ 지연(job별 평소 소요 시간의 2배 초과)
- 계산 방식: 건수 평균은 CSV의 직전 최대 7개 행 평균, "평소 소요 시간"은 job별 전체 실행의 중앙값이다. 스크립트로 계산했다.
- 근거 표기: 로그는 `파일:줄번호`(`batch-logs/` 기준), 건수는 `datamart-counts.csv` 의 행 번호(헤더=1행)다.
- 원인은 로그에 없으면 추측하지 않고 "확인 필요"로 남겼다. "영향 추정"은 로그 · CSV에서 읽히는 결과만 적었다.

## 이상 목록

| 날짜 | job | 점검 항목 | 근거 | 영향 추정 | 다음 확인 사항 |
|---|---|---|---|---|---|
| 2026-09-09 | build_item_stats | 1. 실행 여부 (종료 기록 없음) | `2026-09-09.log:2` START → `:4` RUNNING 25% 가 마지막 줄. SUCCESS · lock released 없음. 같은 날 다른 4개 job은 정상 종료. CSV에 이 날 `build_item_stats` 행 없음(09-09는 7~10행 4개뿐). | `dm.item_stats_daily` 의 09-09분이 적재되지 않았을 가능성. 원인은 확인 필요. | 프로세스(`pid=40655`) 종료 기록(OOM · kill 등), 로그가 끊긴 시점의 스케줄러 · 호스트 로그, `/var/lock/batch/build_item_stats.lock` 잔존 여부, `dm.item_stats_daily` 09-09분 적재 여부 |
| 2026-09-12 | export_grades | 4. 지연 | `2026-09-12.log:10` START(01:00:01) → `:16` SUCCESS `elapsed=963s`. 중앙값 319s의 3.01배(나머지 9회는 307~328s). | 완료가 평소보다 약 10분 늦음. 건수(8257, `csv:22`)는 정상 범위(+1.2%)라 데이터 영향은 로그에서 보이지 않음. | 사유 기록이 없음(진행률이 4분 간격으로 일정하게 느림, `:12`~`:14`). 해당 시각 grade-db(`grades.dbo.grade_summary`) 부하 · 락, S3 쓰기 지연 확인 |
| 2026-09-15 | extract_submissions | 4. 지연 | `2026-09-15.log:18` START → `:20` SUCCESS `elapsed=1668s`. 중앙값 681s의 2.45배. `:19` WARN `source read slow: grade-db 10.20.2.41 replica lag 412s`. | 후속 load_datamart 시작(02:00)이 SUCCESS(01:57:50) 직후라 여유가 거의 없었음. 건수(41573, `csv:38`)는 정상. | replica lag가 1668s 전체를 설명하는지는 로그로 알 수 없음. 해당 시간대 grade-db 복제 지연 지표와 해소 시각 확인 |
| 2026-09-15 | load_datamart | 4. 지연 | `2026-09-15.log:21` START(02:00:03) → `:40` SUCCESS `elapsed=4357s`. 중앙값 923s의 4.72배. `:27` WARN `elapsed 40m exceeds p95 17m`, `:28` sla-monitor `NOT_FINISHED sla=02:45`. | SLA(02:45) 미충족, 완료 03:12:41. build_report_cache(03:30) 시작 전에는 끝남. | 지연 사유 기록 없음. 재실행(`:30`)은 첫 실행이 58분 진행된 뒤라 지연의 원인이 아님. 02:00~03:12 사이 `dm.daily_submission_fact` 쓰기 대기 · 락 · 디스크 IO 지표 확인 |
| 2026-09-15 | load_datamart | 2. 중복 실행 | SUCCESS 2회: `2026-09-15.log:40`(`ldm-20260916-0200-edf5`), `:42`(`ldm-20260916-0258-8963`). 두 번째는 `:30` batchctl `rerun requested ... force=true`로 시작, `:32` START `trigger=manual lock=bypassed`. `:31` 첫 실행이 락을 쥐고 있었으나 `--force`로 무시됨. `:43` `lock=release_skipped`. | 두 실행이 같은 target에 `mode=append`로 동시에 쓴 기록(`:22`, `:32`). 이중 적재 가능성(아래 건수 이상과 연결). | `dm.daily_submission_fact` 09-15분의 중복 키 여부와 run_id별 건수. 재실행 시 락 우회(`--force`)가 허용되는 경로 확인 |
| 2026-09-15 | load_datamart (`dm.daily_submission_fact`) | 3. 적재 건수 (+99.9%) | `datamart-counts.csv:39` `2026-09-15,load_datamart,369064`. 직전 7일(09-08~09-14) 평균 184,617.4. `2026-09-15.log:47` dq-check `rows=369064 avg_7d=184617 deviation=+99%`와 일치. 두 SUCCESS 로그의 `rows`는 각각 184532(`:40`, `:42`)이며 369064는 그 정확히 2배. | build_report_cache가 `input_rows=369064`를 읽음(`:45` WARN). 이 job 자체의 건수(1271, `csv:40`)는 정상 범위이나 입력이 2배라 집계 값 영향 여부는 확인 필요. | 09-15분 중복 적재 여부 확인(위 중복 실행 건과 함께). 09-15분 report cache 결과 검증 |
| 2026-09-17 | build_item_stats | 3. 적재 건수 (−41.5%) | `datamart-counts.csv:46` `2026-09-17,build_item_stats,7284`. 직전 7일(09-10~09-16) 평균 12,453.1. `2026-09-17.log:8` 도 `rows=7284`, `elapsed=202s`(소요 시간은 정상). | `dm.item_stats_daily` 09-17분이 평소의 약 58%만 적재됨. WARN · ERROR 없이 정상 종료. | 원천 `itembank.item_response` 의 09-17분 건수, 상류 수집 지연 · 누락 여부 확인 |

## 해석상 주의

- 건수 평균은 CSV에 있는 직전 행 기준이다. 09-09~09-15 초반 값은 직전 데이터가 7일 미만이라 비교 창이 짧다(이 구간에서 30%를 넘은 행은 없었다). 09-08은 직전 데이터가 없어 건수 비교를 하지 못했다.
- load_datamart의 09-16(−12.5%)과 09-17(−12.7%)은 이상이 아니다. 09-15의 369064가 평균에 섞여 기준이 부풀었을 뿐이고 값(184,895 · 184,551)은 정상이다.
- 범위 밖 관찰: load_datamart가 10일 내내 `student_id=STU-1013 reason="class_id is NULL"` 1건을 skip한다(예: `2026-09-08.log:22`). 4개 기준에는 해당하지 않아 이상 목록에 넣지 않았다.

## 이상 없음으로 판정한 날짜

4개 기준 모두에서 이상이 없었던 일자(5개 job 전부 START · SUCCESS 1회씩, 건수 ±30% 이내, 소요 시간 2배 이내):

- 2026-09-08 (건수는 직전 데이터가 없어 비교 불가, 나머지 기준은 통과)
- 2026-09-10
- 2026-09-11
- 2026-09-13
- 2026-09-14
- 2026-09-16
