# 전자결재 리팩토링 - Plan

## 기본 방침
휴가(Leave) 도메인 리팩토링에서 검증된 패턴을 결재 도메인에 동일하게 적용한다.
세부 패턴 가이드는 `docs/reference/leave-pattern.md`를 참조한다.

## 핵심 원칙

### 1. 상태값은 Enum으로 표현
- 한글 문자열 → `ApprovalStatus`, `ApproverStatus` Enum
- DB 저장 방식: `@Enumerated(EnumType.STRING)` (휴가 도메인과 동일)
- 컬럼은 VARCHAR 유지, 값만 영어 코드로 마이그레이션

### 2. 상태 전이 규칙은 Enum이 안다
- `ApprovalStatus.canTransitionTo(ApprovalStatus next)` 메서드로 전이 가능 여부 판단
- Entity의 상태 변경 메서드는 이 메서드를 호출해 검증
- 전이 불가 시 `BusinessException(ErrorCode.APPROVAL_INVALID_STATUS_TRANSITION)`

### 3. 상태 변경은 Entity 메서드로만
- Entity에 setter 없음 (`@Setter` 사용 금지)
- 의미가 명확한 메서드: `withdraw()`, `approve(...)`, `reject(...)`
- `@Transactional` 환경에서 dirty checking 활용, 명시적 `save()` 호출 불필요

### 4. 책임 분리: Command / Query
- `ApprovalCommandService`: 기안, 수정, 삭제, 상태 변경 (쓰기)
- `ApprovalQueryService`: 목록·상세 조회, 부서·멤버 조회 (읽기)
- 기존 `ApprovalService` (1,066줄) 폐기

### 5. 컨트롤러는 얇게
- 역할: 요청 파싱 → 인증 정보 추출 → 서비스 위임 → 응답 구성
- 비즈니스 로직(번호 생성, 임시저장→기안 전환 등) 완전 제거

### 6. 파일 I/O 분리
- `ApprovalFileService`로 파일 업로드·삭제 로직 격리
- 비즈니스 트랜잭션과 파일 I/O 트랜잭션 경계를 분리

### 7. 번호 채번 캡슐화
- `ApprovalNoGenerator` (@Component)로 결재번호·결재자번호·참조자번호·첨부파일번호 생성 일원화
- 결재번호 규칙(연도-폼번호-순번)은 이 컴포넌트만 안다

### 8. 공통 응답 체계 사용
- 정상 응답: `ResponseMessage<T>`
- 에러 응답: `BusinessException` → `GlobalExceptionHandler` → `ErrorResponse`
- 자체 `ResponseDTO` 제거

## 패키지 구조
목표 구조는 `docs/refactoring/approval/target-structure.md` 참조.

## 의존 컴포넌트 (변경 없음)
다음은 이미 정비되어 있으므로 결재 도메인은 사용만 한다.

- `common/error/ErrorCode.java` — 결재용 코드(AP001~AP007) 추가만 진행
- `common/error/exception/BusinessException.java`
- `common/error/GlobalExceptionHandler.java`
- `common/response/ResponseMessage.java`
- `common/response/ErrorResponse.java`

## 참조 구현 (변경 없음)
다음 파일들이 결재 도메인의 패턴 레퍼런스다.

- `leave/entity/LeaveRequest.java` — Entity 상태 변경 메서드 패턴
- `leave/enums/LeaveStatus.java` — Enum + canTransitionTo 패턴
- `leave/enums/LeaveType.java` — Enum의 비즈니스 판단 메서드 패턴
- `leave/service/LeaveService.java` — Service 구성 패턴
- `leave/controller/LeaveController.java` — 얇은 컨트롤러 패턴

## DB 마이그레이션 전략

### 처리 방침
기존 한글 값으로 저장된 데이터를 영어 코드로 일괄 UPDATE한다.

### 대상 컬럼
- `APPROVAL.APPROVAL_STATUS`: "처리 중" / "승인" / "반려" / "회수" / "임시저장"
  → `PROCESSING` / `APPROVED` / `REJECTED` / `WITHDRAWN` / `TEMP_SAVED`
- `APPROVER.APPROVER_STATUS`: "승인" / "반려" / null/공백
  → `APPROVED` / `REJECTED` / `PENDING`

### 스크립트 위치
`src/main/resources/db/migration/` 디렉토리에 작성.

```
V{n}__approval_status_to_enum.sql
V{n+1}__approver_status_to_enum.sql
```

(현재 프로젝트는 Flyway 미사용. 스크립트는 수동 실행용으로 저장만 함.
파일명 규칙은 향후 Flyway 도입 가능성을 고려.)

### 실행 순서
1. 단계 1 작업 전: 현재 DB 상태 백업 (수동)
2. 단계 1 작업 중: Enum 정의 + 마이그레이션 스크립트 작성
3. 단계 1 작업 후: 마이그레이션 스크립트 수동 실행 후 빌드·기동 확인

## 7단계 진행 전략

### 각 단계의 공통 원칙
- 단계 시작 전: 해당 단계의 `tasks/NN-*.md` 정독
- 단계 진행 중: 작업 범위(Scope)에 명시된 파일만 수정
- 단계 완료 시: `./gradlew compileJava` 통과 + Success Criteria 모두 충족
- 단계 간 의존성 파악:

```
1 (Entity)
  └─> 2 (DTO)
        └─> 3 (Response 통합)
              ├─> 4 (File Service)       [3 이후, 6 이전 어디든]
              ├─> 5 (No Generator)       [3 이후, 6 이전 어디든]
              └─> 6 (God Class 분리)
                    └─> 7 (Controller 슬림화)
```

### 단계 간 빌드 가능성 유지 전략
단계 1에서 Entity가 변경되면 기존 ApprovalService가 컴파일 깨질 수 있다.
이 경우 단계 1 작업 안에서 ApprovalService에 **최소한의 임시 수정**을 가해
빌드만 통과시킨다. 이 임시 수정은 단계 6에서 ApprovalService를 분리할 때
자연스럽게 제거된다.

원칙: "각 단계는 빌드 통과 상태로 끝낸다. 다음 단계가 이를 무너뜨리지 않는다."

## 검증 방법 (단계별 공통)

### 자동 검증
- `./gradlew compileJava` 통과
- `./gradlew build` 통과 (테스트가 있다면)

### 수동 검증 (각 단계의 task.md에 구체 명시)
다음 API의 동작이 단계 전후로 동일해야 한다.

- 결재 기안 작성 (`POST /approval`)
- 결재 처리 (승인/반려)
- 결재 회수
- 결재 목록 조회
- 결재 상세 조회
- 임시저장 → 기안 전환

Postman 컬렉션 또는 수동 cURL로 확인하고, task.md의 체크리스트에 기록한다.

## 작업 분담 (인간 ↔ Codex)

### 인간 (저자) 담당
- spec.md, plan.md, tasks/*.md 작성과 리뷰
- 각 단계 완료 후 diff 리뷰
- 수동 검증 (API 동작 확인)
- DB 마이그레이션 스크립트 실행
- Git 커밋

### Codex CLI 담당
- task.md에 명시된 작업 범위 내 코드 작성·수정
- 빌드 통과 확인
- 변경 사항 보고 (변경 파일, 라인 수, 빌드 결과)

### 경계 원칙
- Codex는 task.md의 "작업 범위" 외 파일은 수정하지 않는다.
- 의도 변경, 범위 확장이 필요하다 판단되면 Codex는 작업을 중단하고 인간에게 보고한다.
- DB 마이그레이션 스크립트는 Codex가 작성할 수 있으나 **실행은 인간이 수동으로 한다.**
