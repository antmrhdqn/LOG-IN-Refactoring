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

직전 작업의 명세·보고서도 함께 읽는다. 결정(D 항목)과 검증 방식이 그대로 선례가 된다.
- `docs/security/tasks/04-auth-failure-status.md` + `docs/security/reports/04-auth-failure-status-report.md`
- `docs/security/tasks/03-read-authz.md` + `docs/security/reports/03-read-authz-report.md`

### 참조 문서
- 현황 (Before): `docs/refactoring/approval/as-is.md`
- 목표 구조: `docs/refactoring/approval/target-structure.md`
- 휴가 도메인 패턴: `docs/reference/leave-pattern.md` (결재가 따를 레퍼런스)

### 현재 진행 작업
**작업 F 완료.** 다음 작업 미정 — `docs/security/spec.md` §4-3의 후속 후보에서 선정한다.

직전 완료: **작업 F 인증 실패 응답 정상화 (200 → 401)** — `docs/security/tasks/04-auth-failure-status.md`
(코드 2파일 / `git diff` +9−0 / 캡처 17항목 전 항목 PASS / 신규 ErrorCode 0건 · **ErrorCode 사용 0건**)

### 작업 로드맵
```
전자결재 리팩토링 1~7 ✅ 전부 완료 (docs/refactoring/completed/approval-domain.md)

보안 결함 정리
A. 쓰기 경로 권한 경계 정리 ✅  91de70c(코드) · 8731d37(문서)
B. 비밀 정보 노출 차단 · 비밀번호 경로 인가 ✅  3e2db66(코드) · 4c2b50b(문서) · b00e2c6(spec 정정)
   ※ 구 B + 구 C 통합, commute 도메인 포함
E. 읽기 경로 인가 (결재 상세 · 첨부 다운로드) ✅  7f66056(코드) · 0b1cc92(문서) · 18e9298(배치 평평화)
F. 인증 실패 응답 정상화 (200 → 401) ✅  c79006f(코드) · <문서 커밋>(문서)  ← 방금 완료
D. 등재만 — XSS · CORS · 상태값 불일치 · insite 무성 0건 ·
   스택트레이스 노출 · 계정 열거 · 만료/위조 미구분 · 죽은 ErrorCode 3건

후속 후보 (미착수)
- member/** · commute/** 읽기 경로 인가 (GET /members/{id}, /api/rooms/members, /commutes)
- GET /approvals/members* 인가 (회원 조회, 결재 문서 아님)
- TestController 엔드포인트 제거 · CORS 전역 개방
- 필터 예외 시 스택트레이스 노출 차단 (server.error.include-stacktrace)
```

### 선례 · 검증 방법론 — 정본은 `docs/security/precedents.md`

번호(`S1`~`S9` · `P1`~`P3` · `R11`)의 정본은 위 파일이다. 후속 명세는 이 번호로 참조한다
(예: `선례 S2`). **아래는 사본이며, 문구가 어긋나면 `precedents.md`가 정답이다.**

**작업 E에서 확정된 것**

- **S1. 읽기 차단은 404, 쓰기 차단은 403.** 읽기는 GET이라 열거 비용이 0이므로 존재를 은폐한다
- **S2. 인가 판정은 관계로만.** role(ADMIN/MEMBER) 분기를 넣지 않는다 — ADMIN도 관계 없으면 차단
- **S3. 인가는 새 public 진입점에 넣고 기존 내부용 메서드는 package-private으로 낮춘다.**
  목록이 상세 메서드를 항목마다 호출하므로, 기존 메서드에 인가를 넣으면 목록이 통째로 깨진다
- **S4. 응답 구조는 바꾸지 않는다.** 전부 아니면 전무 — 부분 권한 개념을 만들지 않는다
- **S5.** 차단·비차단을 `log.warn`으로 구분 기록

**작업 F에서 확정된 것**

- **S6. 응답 본문을 건드리지 않는 처방은 판정이 두 숫자로 끝난다** — 본문 해시 동일 + 상태 코드.
  본문까지 바꾸면 회귀 판정과 처방 판정이 뒤섞인다
- **S7. 필터·핸들러는 `GlobalExceptionHandler`가 못 잡는다.** DispatcherServlet 밖이라
  `ErrorCode`/`ErrorResponse`로 위임할 경로가 없고, **상태 코드 직접 세팅이 유일한 수단이다.**
  같은 기준선 안에서 대조 실증됨 — 캡처 `07`(`ErrorResponse` C999, 90B) vs `16`·`17`(Spring 기본 에러, 8~9KB)
- **S8. `catch`가 커밋된 응답에 도달할 수 있으면 `isCommitted()` 가드를 둔다.**
  없으면 `setStatus`가 조용히 무시되고, 무시됐다는 사실도 남지 않는다
- **S9. 인증 실패는 401 단일.** 예외 종류별로 403을 섞으면 프론트가 `=== 401`만 보므로
  그 경우만 조용히 실패한다
  > ⚠ 인계 문서 원문은 "인증/인가 실패 상태 코드는 단일화한다"였으나 **인가 실패는 S1이
  > 404/403으로 가른다.** 05 명세 리뷰(2026-08-14)에서 "인증"으로 좁혔다 — `precedents.md` 각주 참조

**검증 방법론 (P1~P3 · R11)**

- **P1. 기준선은 코드 수정 전에 찍는다.** 작업 E는 명세에 없던 결함 2건, 작업 F는 실측 등재 3건을 여기서 얻었다
- **P2. 정상 경로 비회귀를 차단·전환 검증보다 먼저 본다**
- **P3. 지점 전수 × 숫자 판정.** "몇 곳인가"를 전수 검색으로 먼저 확정한다
- **R11. 해시 판정은 재기동을 건너야 성립한다.** 작업 F에서 **실제로 발동했다** — `02`(목록,
  Jackson 파생 속성)가 재기동 후 키 순서가 바뀌어 `hashSame=False`, `canonSame=True`. 정규화
  재판정이 없었으면 FAIL 오판이었다. ⚠ 예상은 인증 실패 본문(`json-simple` HashMap)이었으나
  **정상 응답 쪽이 흔들렸다**

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
