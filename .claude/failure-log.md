# failure-log

> 카테고리 표준값이 프로젝트 CLAUDE.md에 아직 없어 임시로 `doc-reference` · `doc-accuracy` 를 쓴다.

---
date: 2026-09-29
stage: review
category: doc-accuracy
task: docs/item-bank/ERD.md 의 mermaid erDiagram 관계 작성
detected_by: advisor 검토(작성 완료 직전)
attempts:
  - 1차: `v_item_public ||--o{ item_tag` 로 작성 → 검수중('R') 문항의 item_tag 행은 뷰에 짝이 없어 "정확히 1"이 틀림. `|o--o{` 로 고침
root_cause: 뷰의 필터 조건(status='A')을 관계 카디널리티에 반영하지 않음
---

---
date: 2026-09-29
stage: review
category: doc-reference
task: docs/item-bank/BUSINESS-RULES.md 비고 작성(BR-13, BR-34)
detected_by: 문서 점검 스크립트(생략형 `:N` 의 상속 파일 검사) + advisor
attempts:
  - 1차: 인용 코드 91줄 대조는 통과했지만, 비고의 생략형 `:485-492`(BR-13) · `:84`(BR-34)는 검사 대상 밖이라 놓침. 바로 앞 bullet 이 `db/mariadb/init/01-schema.sql` 을 가리켜 스키마 파일 줄로 읽힘
root_cause: 앞 bullet 이 다른 파일을 가리킨 뒤 파일명 없이 `:N` 만 써서 참조 문맥이 바뀜
---

---
date: 2026-09-29
stage: review
category: doc-reference
task: docs/item-bank/ARCHITECTURE.md §5 (buildSearchQuery 근거)
detected_by: 문서 점검 스크립트(범위 초과 `inc/db.php:541-563`) + advisor
attempts:
  - 1차: `:29-36` · `:541-563` 을 파일명 없이 적어 직전 참조(`inc/db.php`)로 읽힘. 의도는 `search.php`
root_cause: 앞 bullet 이 다른 파일을 가리킨 뒤 파일명 없이 `:N` 만 써서 참조 문맥이 바뀜
---
