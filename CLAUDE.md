@AGENTS.md

## Claude Code 전용 지시

- 세션 시작 전 `git status`로 워킹 트리 상태 확인
- 코드 수정 전 plan mode로 계획 제시 후 승인받을 것
- 빌드: `cd final; .\gradlew.bat compileJava`
- 부팅 검증: `cd final; .\gradlew.bat bootRun`
  (`80% EXECUTING`에서 멈춘 것처럼 보이는 게 정상. 종료는 `Ctrl + C`)
- 작업 완료 시 보고서를 `docs/{작업 스트림}/reports/{작업}-report.md`에
  UTF-8로 저장할 것
  (예: docs/security/reports/03-read-authz-report.md)
  명세는 `docs/{작업 스트림}/tasks/{작업}.md`
  (예: docs/security/tasks/03-read-authz.md)

  > **도메인 하위 폴더를 만들지 않는다** (2026-08-12 평평화).
  > 작업이 여러 도메인에 걸치는 일이 잦아 `{도메인}` 판정이 매번 애매했다.
  > 다만 **`docs/refactoring/**`는 기존 배치를 유지한다** — 완료된 기록이라 옮기지 않는다
  > (예: docs/refactoring/approval/reports/07-controller-report.md).
- 모든 명령어는 Windows / PowerShell 5.1 기준 (`.\gradlew.bat`, `Get-ChildItem`,
  백슬래시 경로). PowerShell 5.1의 `Select-String`에는 `-Recurse`/`-Include`가 없다.
- 모든 `.md`는 UTF-8로 저장·유지할 것
