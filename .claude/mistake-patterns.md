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
