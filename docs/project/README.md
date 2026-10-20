레거시 `search.php` 를 `/api/items/search` 로 옮긴 변경을 characterization 스냅샷 테스트와 `docs/approval-checklist.md` 기준으로 대조해 승인 · 반려를 판정하고, PR 에는 `Claude Auto Review` 워크플로가 같은 체크리스트로 코멘트를 남기게 한다.
`/verify <비교 범위>` (Skill. 요청 범위 한 문장을 함께 넘기면 reviewer · tester Sub-agent 가 체크리스트 대조와 테스트 실행을 맡는다)
통과 · 실패 수와 코멘트 수신이 기록된 실행 결과가 아직 없고(`docs/project/CHECK.md` 증거란이 비어 있다), 현재 설정은 로컬 포트 8080 · 8081, 미추적 `hooks/`, 확인하지 못한 저장소 시크릿 `CLAUDE_CODE_OAUTH_TOKEN` 에 기대므로 현업에 옮길 때는 이 환경 가정을 대상 저장소의 접속 정보 · 인증 · 체크리스트 기준으로 바꾸고 증거를 먼저 채워야 한다.
