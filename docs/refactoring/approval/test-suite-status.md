# 백로그: 결재 도메인 테스트 소스셋 컴파일 실패 상태

> 기록일: 2026-07-28 (Stage 3 명세 작성 중 발견)
> 상태: 인지함 / 의도적으로 미룸 (결재 리팩토링 Stage 7 완료 후 처리)

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

## 원인

프로덕션 리팩토링이 테스트에 반영되지 않아 시그니처가 어긋난 것이다.

- `setApprovalDate(String)` 2건 → **Stage 2**의 `ApprovalDTO.approvalDate` `String → LocalDateTime` 전환과 충돌
- `updateApprovalStatus(approvalNo)` 1건 → **Stage 1**의 `updateApprovalStatus(String, int)` 시그니처 전환(회수 인증 결함 [A] 해결)과 충돌

즉 이 테스트는 **Stage 1 시점부터 죽어 있었다.** 검증 파이프라인이 `compileJava` + `bootRun` +
수동 API만 사용하고 `compileTestJava` / `test`를 돌리지 않아 그동안 드러나지 않았다.

## 왜 지금 고치지 않는가

- 결재 도메인은 Stage 6·7에서 Service 분리(Command/Query)와 Controller 슬림화로 **시그니처가 더 바뀐다.**
  지금 테스트를 살려도 곧 다시 깨진다.
- 테스트 정비는 Stage 3~7 어느 단계의 목표(범위)에도 속하지 않는다. 지금 손대면 Surgical Changes 원칙 위반.
- 따라서 **결재 리팩토링(Stage 7) 완료 후**, 시그니처가 안정된 시점에 일괄로 다룬다.

## 그때 결정할 것

1. 기존 테스트를 **되살릴지**(현 프로덕션 시그니처에 맞춰 수정) vs **폐기 후 재작성**할지
   — 참고: spec.md는 "향후 테스트 코드 추가는 별도 작업"으로 명시해 두었다.
2. `approval/dto/ResponseDTO.java` **물리 삭제**를 이 테스트 정비와 함께 처리
   (Stage 3에서 프로덕션 참조는 이미 0이 된 상태 — 남은 건 이 테스트뿅).
3. 검증 파이프라인에 `compileTestJava`(또는 `test`)를 포함할지 — 테스트를 유지하기로 한다면.

## 관련 문서

- Stage 3 명세: `docs/refactoring/approval/tasks/03-response.md` (§0-4, §5에 이 발견 반영됨)
- spec.md "향후 개선(범위 외)" 절: "자동화된 테스트 코드 부재"
