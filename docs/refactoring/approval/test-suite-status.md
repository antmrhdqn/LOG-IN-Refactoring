# 백로그: 결재 도메인 테스트 소스셋 컴파일 실패 상태

> 기록일: 2026-07-28 (Stage 3 명세 작성 중 발견)
> **상태: 해소 (2026-07-30, Stage 7 완료) — §"그때 결정할 것" 3항목 전부 결론.**

## 발견

인수인계 문서와 spec.md는 결재 도메인에 **"자동화된 테스트가 없다"** 고 전제하고 있으나,
실제로는 기존 테스트 파일이 존재하며 **이미 컴파일 실패 상태**임을 확인했다.

`ResponseDTO` 전역 grep 중 `src/test/` 참조가 나와, `.\gradlew.bat compileTestJava`로 실측:

```
ApprovalServiceTest.java:148  error: String cannot be converted to LocalDateTime
    approvalDTO.setApprovalDate("2024-05-07 15:13:13");
ApprovalServiceTest.java:262  error: String cannot be converted to LocalDateTime
    approvalDTO.setApprovalDate("2024-05-27 04:22:13");
ApprovalServiceTest.java:368  error: method updateApprovalStatus cannot be applied to given types;
    required: String,int  found: String
    ApprovalDTO approvalDTO = approvalService.updateApprovalStatus(approvalNo);
BUILD FAILED (3 errors)
```

추가로 `ApprovalControllerTest.java:152`가 `new ResponseDTO()`를 참조한다(같은 소스셋, 함께 실패).

> Stage 7에서 삭제(커밋 분리). 잔존 결재 외 17개는 무변경.

## 원인

프로덕션 리팩토링이 테스트에 반영되지 않아 시그니처가 어긋난 것이다.

- `setApprovalDate(String)` 2건 → **Stage 2**의 `ApprovalDTO.approvalDate` `String → LocalDateTime` 전환과 충돌
- `updateApprovalStatus(approvalNo)` 1건 → **Stage 1**의 `updateApprovalStatus(String, int)` 시그니처 전환(회수 인증 결함 [A] 해결)과 충돌

즉 이 테스트는 **Stage 1 시점부터 죽어 있었다.** 검증 파이프라인이 `compileJava` + `bootRun` +
수동 API만 사용하고 `compileTestJava` / `test`를 돌리지 않아 그동안 드러나지 않았다.

## 왜 (그때) 지금 고치지 않았는가

> 아래는 2026-07-28 시점의 판단 기록이다. Stage 7 완료로 전제가 해소됐다.

- 결재 도메인은 Stage 6·7에서 Service 분리(Command/Query)와 Controller 슬림화로 **시그니처가 더 바뀐다.**
  지금 테스트를 살려도 곧 다시 깨진다.
- 테스트 정비는 Stage 3~7 어느 단계의 목표(범위)에도 속하지 않는다. 지금 손대면 Surgical Changes 원칙 위반.
- 따라서 **결재 리팩토링(Stage 7) 완료 후**, 시그니처가 안정된 시점에 일괄로 다룬다.

## 그때 결정할 것 — **해소 (2026-07-30, Stage 7)**

1. 기존 테스트를 **되살릴지**(현 프로덕션 시그니처에 맞춰 수정) vs **폐기 후 재작성**할지
   — 참고: spec.md는 "향후 테스트 코드 추가는 별도 작업"으로 명시해 두었다.
   → **✅ 폐기 확정.** Stage 7에서 2파일을 **삭제**했다(`ApprovalServiceTest` 476줄 ·
     `ApprovalControllerTest` 272줄). 컴파일조차 되지 않아 검증 가치가 0이었고, 되살리는 것은
     spec Out of Scope("자동화된 테스트 작성")에 해당한다. **재작성은 하지 않았다** —
     결재 도메인 테스트 커버리지는 현재 **0**이다.
     삭제는 리팩토링 코드와 섞이지 않도록 **별개 커밋**으로 분리했다(`07-controller.md` R8).
     → `compileTestJava` **실패 → 통과.**

2. `approval/dto/ResponseDTO.java` **물리 삭제**를 이 테스트 정비와 함께 처리
   (Stage 3에서 프로덕션 참조는 이미 0이 된 상태 — 남은 건 이 테스트뿐).
   → **✅ 삭제 완료 (Stage 7).** 위 2파일 삭제로 **리포 전체 참조가 0**이 된 것을 확인한 뒤 지웠다.
     (survey 도메인의 `SurveyResponseDTO`·`ResponseDTO`는 **다른 클래스**이며 무관하다.)
     이로써 spec 성공 지표 "자체 `ResponseDTO` 제거"가 **파일 수준까지** 닫혔다.

3. 검증 파이프라인에 `compileTestJava`(또는 `test`)를 포함할지 — 테스트를 유지하기로 한다면.
   → **✅ `compileTestJava` 포함.** 테스트를 유지하지 않기로 했지만(1번), Stage 7에서 **처음으로
     통과 상태가 만들어졌으므로** 그 상태가 다시 조용히 깨지는 것을 막는 장치가 필요하다.
     결재 외 17개 테스트 파일도 정상 컴파일되므로 타 도메인 병목도 없다.
     `test`(실행)는 포함하지 않는다 — 돌릴 테스트가 없다.
     → `AGENTS.md` "작업 시작 시 규칙"과 `completed/approval-domain.md` §6에 반영.

## 남은 것

- **결재 도메인 테스트 커버리지 0.** 도입 시 우선순위는 `completed/approval-domain.md` §6 권고 참조
  — **목록 조회 5종의 건수**와 **상태 전이 규칙**부터. 이번 리팩토링에서 무성 실패가 실제로 발생한 두 지점이다.
- `src/test/.../approval/` 아래 **빈 디렉터리 2개**(`controller`·`service`)가 로컬에 남는다.
  Git은 빈 디렉터리를 추적하지 않으므로 커밋·클론에는 영향이 없다.

## 관련 문서

- Stage 3 명세: `docs/refactoring/approval/tasks/03-response.md` (§0-4, §5에 이 발견 반영됨)
- Stage 7 명세: `docs/refactoring/approval/tasks/07-controller.md` (D15 — 삭제 결정과 커밋 분리)
- Stage 7 보고서: `docs/refactoring/approval/reports/07-controller-report.md`
- 완료 보고: `docs/refactoring/completed/approval-domain.md` (§3 "단계 7이 함께 닫은 잔해" · §6)
- spec.md "향후 개선(범위 외)" 절: "자동화된 테스트 코드 부재"
