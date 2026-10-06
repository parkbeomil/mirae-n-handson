# mistake-patterns

- pattern_id: DOC-REFERENCE-001
  category: doc-reference
  summary: 분석 문서에서 파일명 없는 `:N` 참조가 앞 bullet 의 다른 파일로 읽힘
  root_cause: 앞 bullet 이 다른 파일을 가리킨 뒤 파일명 없이 `:N` 만 써서 참조 문맥이 바뀜
  count: 2
  stages: { review: 2 }
  target_skill: document-module
  rule: bullet · 표 칸마다 첫 참조는 파일명을 붙여 쓰고, 파일이 바뀌면 생략형 `:N` 을 쓰지 않는다
  last_seen: 2026-09-29

- pattern_id: DOC-ACCURACY-001
  category: doc-accuracy
  summary: 분석 문서에 직접 세지 않은 건수 · 확인하지 않은 줄번호 · 확인되지 않은 설정값을 먼저 적음
  root_cause: 집계하지 않은 수치와 잘린 출력, 기억에 의존한 줄번호를 근거로 문서에 먼저 적음
  count: 3
  stages: { review: 3 }
  target_skill:
  rule: 문서에 적는 건수 · 줄번호는 같은 세션에서 grep · awk 로 전수 확인한 값만 쓰고, 센 기준(이벤트/줄)을 함께 적는다. 로그에서 확인하지 못한 값은 단정하지 않는다
  last_seen: 2026-10-06
