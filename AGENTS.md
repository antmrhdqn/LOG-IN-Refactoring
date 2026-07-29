# LOG-IN 프로젝트 컨텍스트

## 프로젝트 기본
- Spring Boot 3.2.4 / Java 17, 사내 그룹웨어
- 패키지 루트: `com.insider.login`
- DB: MySQL, JPA (직접 DDL 관리), Gradle 빌드
- 상세: `docs/project-overview.md`

## 현재 작업: 전자결재(Approval) 도메인 리팩토링

### 필수 읽기 순서 (작업 시작 전)
1. `docs/refactoring/approval/spec.md` — 무엇을, 왜
2. `docs/refactoring/approval/plan.md` — 어떻게 (기술 결정)
3. `docs/refactoring/approval/tasks/{현재 단계}.md` — 지금 할 작업

### 참조 문서
- 현황 (Before): `docs/refactoring/approval/as-is.md`
- 목표 구조: `docs/refactoring/approval/target-structure.md`
- 휴가 도메인 패턴: `docs/reference/leave-pattern.md` (결재가 따를 레퍼런스)

### 현재 진행 단계
**단계 6: God Class 분리 (Command / Query)** — `docs/refactoring/approval/tasks/06-command-query.md`
(단계 1·1.5·2·3·4 완료 / 단계 5 결재번호 생성기 분리 완료·커밋·푸시 — `80f60b7`(코드)·`0215951`(문서), origin/main 동기화)

### 단계 로드맵
```
1. Entity 리팩토링 ✅
1.5. Repository 상태 리터럴 정합 ✅
2. DTO 정리 ✅
3. 공통 응답 체계 통합 ✅
4. 파일 처리 분리 ✅
5. 결재번호 생성기 분리 ✅
6. God Class 분리 (Command / Query) (현재)
7. Controller 슬림화
```

## 완료된 리팩토링
- 전역 에러 처리: `docs/refactoring/completed/error-handling.md`
- 휴가 도메인: `docs/refactoring/completed/leave-domain.md`

## 작업 시작 시 규칙

1. 작업 중인 단계의 `tasks/NN-*.md`를 먼저 정독할 것
2. 그 단계의 "작업 범위"에 명시된 파일만 수정. "범위 외" 항목은 절대 건드리지 말 것
3. 그 단계의 "Success Criteria"를 모두 만족시킬 것
4. 매 단계 완료 시 `./gradlew compileJava` 빌드 통과 + `./gradlew bootRun` 정상 기동 + 수동 API 확인 필수
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
