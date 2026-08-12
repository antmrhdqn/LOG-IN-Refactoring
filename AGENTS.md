# LOG-IN 프로젝트 컨텍스트

## 프로젝트 기본
- Spring Boot 3.2.4 / Java 17, 사내 그룹웨어
- 패키지 루트: `com.insider.login`
- DB: MySQL, JPA (직접 DDL 관리), Gradle 빌드
- 상세: `docs/project-overview.md`

## 현재 작업: 전자결재(Approval) 도메인 보안 결함 정리

### 필수 읽기 순서 (작업 시작 전)
1. `docs/refactoring/completed/approval-domain.md` — 리팩토링 최종 기록 (§4 이월 목록이 출발점)
2. `docs/security/spec.md` — 무엇을, 왜 (plan은 spec에 흡수)
3. `docs/security/tasks/{현재 작업}.md` — 지금 할 작업

### 참조 문서
- 현황 (Before): `docs/refactoring/approval/as-is.md`
- 목표 구조: `docs/refactoring/approval/target-structure.md`
- 휴가 도메인 패턴: `docs/reference/leave-pattern.md` (결재가 따를 레퍼런스)

### 현재 진행 작업
**작업 B: 비밀 정보 노출 차단 · 비밀번호 경로 인가** — `docs/security/tasks/02-secret-exposure.md`
(작업 A 쓰기 경로 권한 경계 정리 완료·커밋·푸시 — `91de70c`(코드)·`8731d37`(문서), origin/main 동기화)

### 작업 로드맵
```
전자결재 리팩토링 1~7 ✅ 전부 완료 (docs/refactoring/completed/approval-domain.md)

보안 결함 정리
A. 쓰기 경로 권한 경계 정리 ✅
B. 비밀 정보 노출 차단 · 비밀번호 경로 인가 (현재)  ※ 구 B + 구 C 통합, commute 도메인 포함
E. 읽기 경로 인가 (정책 결정 선행)
D. 등재만 — XSS · 인증 실패 200 · CORS · 상태값 불일치 · insite 무성 0건
```

## 완료된 리팩토링
- 전역 에러 처리: `docs/refactoring/completed/error-handling.md`
- 휴가 도메인: `docs/refactoring/completed/leave-domain.md`
- 전자결재 도메인: `docs/refactoring/completed/approval-domain.md`

## 작업 시작 시 규칙

1. 작업 중인 단계의 `tasks/NN-*.md`를 먼저 정독할 것
2. 그 단계의 "작업 범위"에 명시된 파일만 수정. "범위 외" 항목은 절대 건드리지 말 것
3. 그 단계의 "Success Criteria"를 모두 만족시킬 것
4. 매 단계 완료 시 `./gradlew compileJava` + `./gradlew compileTestJava` 통과 +
   `./gradlew bootRun` 정상 기동 + 수동 API 확인 필수
   (`compileTestJava`는 단계 7에서 처음 통과 상태가 됐다 — test-suite-status.md §3)
   (`compileJava`만으로는 JPQL 리터럴 무성 실패 등을 잡지 못한다 — 단계 1.5 참조)
5. 임시 수정한 위치는 작업 보고서에 빠짐없이 기록
6. 의문점·예상 못 한 상황은 추측 말고 사용자에게 즉시 보고
7. task.md의 "작업 중 멈춰야 할 상황"에 해당하면 즉시 중단

## 작업 분담

- **사용자**: spec/plan/task 작성, diff 리뷰, 수동 API 검증, DB 마이그레이션 실행, Git 커밋·푸시
- **Claude Code**: task.md 범위 내 코드 작성, 빌드·부팅 통과 확인, 작업 보고서 저장, 변경 사항 보고
- **공통 원칙**: task.md가 곧 진실(source of truth). 의도 변경 필요 시 사용자에게 보고.
  동일 도메인에 두 에이전트를 동시에 투입하지 않는다.

## 핵심 원칙 (모든 작업에 적용)

휴가 도메인과 동일한 패턴을 따른다. 자세한 패턴은 `docs/reference/leave-pattern.md` 참조.

요약:
- Enum 기반 상태 관리 (`@Enumerated(EnumType.STRING)`)
- Entity 상태 변경은 메서드로만 (setter 금지)
- dirty checking 활용 (불필요한 save() 호출 금지)
- Command / Query 책임 분리
- BusinessException + GlobalExceptionHandler 위임
- 공통 응답 `ResponseMessage<T>` / `ErrorResponse`
