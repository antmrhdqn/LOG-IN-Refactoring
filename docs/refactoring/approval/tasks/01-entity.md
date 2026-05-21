# 단계 1: Entity 리팩토링

## 목표
결재 도메인의 Entity를 휴가 도메인과 동일한 패턴으로 정비하고, 코드 분석에서 발견된
보안 결함과 도메인 규칙 결함 중 Entity 책임 범위의 것들을 해결한다.

구체적으로:
- Lombok 적용으로 보일러플레이트 제거
- 상태값을 Enum으로 명시 (`ApprovalStatus`, `ApproverStatus`)
- 상태 변경을 메서드로만 가능하도록 캡슐화 (setter 금지)
- 잘못된 상태 전이를 도메인 계층에서 차단
- 회수 시 기안자 본인 확인 (보안 결함 해결)
- 기존 DB의 한글 상태값을 영어 코드로 마이그레이션할 SQL 작성
- 회수 API의 인증 정보 누락 보안 결함 임시 수정

## 작업 범위 (이 단계에서 수정하는 파일)

### 수정
- `approval/entity/Approval.java`
- `approval/entity/Approver.java`
- `common/error/ErrorCode.java` (AP001~AP009 추가)

### 신규 생성
- `approval/enums/ApprovalStatus.java`
- `approval/enums/ApproverStatus.java`
- `src/main/resources/db/migration/V1__approval_status_to_enum.sql`
- `src/main/resources/db/migration/V2__approver_status_to_enum.sql`

### 삭제
- `approval/builder/` 패키지 전체

### 임시 수정 허용 (빌드 통과 + 보안 결함 우선 해결 목적)

다음 파일들은 단계 1 완료 시점에 빌드가 통과되어야 하므로 최소 수정 허용.
이 임시 수정은 단계 6·7에서 본격 정리할 때 자연스럽게 제거된다.

- `approval/service/ApprovalService.java`
  - setter 호출 → Builder 또는 새 Entity 메서드로 교체
  - 한글 상태 문자열 → Enum 비교로 교체
  - `updateApprovalStatus(approvalNo)` → `updateApprovalStatus(approvalNo, memberId)`로 시그니처 변경
    (내부에서 `Approval.withdraw(memberId)` 호출)

- `approval/controller/ApprovalController.java`
  - `updateApprovalstatus()` 메서드에 SecurityContext에서 memberId 추출 코드 추가
  - Service 호출 시 memberId 전달
  - **이 한 메서드만 수정**. 다른 컨트롤러 메서드는 단계 7에서 처리.

임시 수정한 위치는 작업 보고서에 반드시 기록할 것.

## 범위 외 (이 단계에서 절대 건드리지 말 것)
- DTO 파일들 (단계 2에서 처리)
- Repository 파일들 (변경 불필요)
- 다른 Entity 파일들 (Attachment, Referencer, Form, Member, Department, Position)
- 응답 처리 로직 (단계 3에서 처리)
- 파일 처리 코드 (단계 4에서 처리)
- 번호 채번 로직 (단계 5에서 처리)
- ApprovalService 본격 리팩토링 (단계 6)
- ApprovalController 본격 리팩토링 — `updateApprovalstatus` 외 다른 메서드 (단계 7)
- `memberId` 헤더 백도어 제거 (`@RequestHeader("memberId")`) — 단계 7

"이왕 하는 김에" 정신으로 위 항목을 건드리면 안 된다.

## 참조해야 할 파일 (변경 금지, 패턴 참조용)

1. **`leave/entity/LeaveRequest.java`**
   - Entity의 전체 구조 (어노테이션, `protected` 기본 생성자, 생성자 단위 `@Builder`)
   - 상태 변경 메서드 시그니처와 본문 구조 (`status.canTransitionTo(...)` 호출 패턴)
   - setter 없음. 모든 변경이 의도 명확한 메서드로만 가능

2. **`leave/enums/LeaveStatus.java`**
   - Enum 정의 방식
   - `canTransitionTo()` 구현 방식
   - 휴가 상태와 동일한 구조로 결재 상태 만들 것

3. **`common/error/exception/BusinessException.java`** / **`common/error/ErrorCode.java`**
   - 예외 발생 패턴 (`throw new BusinessException(ErrorCode.XXX)`)
   - ErrorCode Enum의 명명 규칙

## 상태 전이 규칙 (확정)

### ApprovalStatus

| From → To | 허용 | 조건/주체 |
|---|---|---|
| (없음) → TEMP_SAVED | ✅ | 기안자가 임시저장으로 신규 작성 |
| (없음) → PROCESSING | ✅ | 기안자가 바로 상신 |
| TEMP_SAVED → PROCESSING | ✅ | 기안자 본인이 임시저장→기안 전환 |
| TEMP_SAVED → (삭제) | ✅ | 기안자 본인, Entity 삭제 |
| PROCESSING → APPROVED | ✅ | 모든 결재자 승인 시 (단계 6에서 Service가 호출) |
| PROCESSING → REJECTED | ✅ | 결재자 1명이라도 반려 시 (단계 6에서 Service가 호출) |
| PROCESSING → WITHDRAWN | ✅ | 기안자 본인. 결재자 처리 여부 검증은 단계 6에서 Service가 사전 수행 |
| APPROVED → 모든 상태 | ❌ | |
| REJECTED → 모든 상태 | ❌ | 재상신은 새 결재로 |
| WITHDRAWN → 모든 상태 | ❌ | 재상신은 새 결재로 |

### ApproverStatus

| From → To | 허용 | 조건 |
|---|---|---|
| (없음) → PENDING | ✅ | 결재선 등록 시 초기값 |
| PENDING → APPROVED | ✅ | 결재자가 승인 |
| PENDING → REJECTED | ✅ | 결재자가 반려 |
| APPROVED → 모든 상태 | ❌ | |
| REJECTED → 모든 상태 | ❌ | |

## 체크리스트

### A. Enum 신설

- [ ] `approval/enums/ApprovalStatus.java` 생성
  - 값: `PROCESSING`, `APPROVED`, `REJECTED`, `WITHDRAWN`, `TEMP_SAVED`
  - `canTransitionTo(ApprovalStatus next)` 메서드 구현 (위 표에 따라)
  - 휴가의 `LeaveStatus.java`와 동일 패턴

- [ ] `approval/enums/ApproverStatus.java` 생성
  - 값: `PENDING`, `APPROVED`, `REJECTED`
  - `canTransitionTo(ApproverStatus next)` 메서드 구현 (위 표에 따라)

### B. ErrorCode 추가 (총 9개)

`common/error/ErrorCode.java`에 다음 항목 추가. HTTP 상태코드와 메시지는
기존 ErrorCode들의 패턴을 따를 것.

- [ ] `APPROVAL_NOT_FOUND` (AP001) — 404
- [ ] `APPROVAL_INVALID_STATUS_TRANSITION` (AP002) — 400
- [ ] `APPROVAL_UNAUTHORIZED` (AP003) — 403, 본인이 아닌 결재 수정/조회 시도
- [ ] `APPROVER_NOT_FOUND` (AP004) — 404
- [ ] `APPROVER_INVALID_STATUS_TRANSITION` (AP005) — 400
- [ ] `APPROVAL_FILE_UPLOAD_FAILED` (AP006) — 500
- [ ] `APPROVAL_FILE_NOT_FOUND` (AP007) — 404
- [ ] `APPROVAL_WITHDRAW_NOT_OWNER` (AP008) — 403, 회수 시 기안자 본인 아님
- [ ] `APPROVAL_WITHDRAW_ALREADY_PROCESSED` (AP009) — 400, 결재자가 이미 처리한 결재 회수 시도
      (단계 1에서는 정의만, 실제 사용은 단계 6에서)

### C. Approval Entity 리팩토링

- [ ] 클래스 어노테이션: `@Entity`, `@Table`, `@Getter`
- [ ] **`@Setter` 사용 금지** (어떤 형태로든)
- [ ] `protected Approval()` 기본 생성자
- [ ] 생성자에 `@Builder` 부착 (클래스 단위 `@Builder` 아님)
- [ ] 수동 getter/setter/toString 제거
- [ ] `approvalStatus` 필드:
  - 타입: `ApprovalStatus` (Enum)
  - 어노테이션: `@Enumerated(EnumType.STRING)`, `@Column(nullable = false)`
- [ ] 상태 변경 메서드 추가:

  **withdraw(int memberId)** — PROCESSING → WITHDRAWN
  ```java
  public void withdraw(int memberId) {
      if (this.memberId != memberId) {
          throw new BusinessException(ErrorCode.APPROVAL_WITHDRAW_NOT_OWNER);
      }
      if (!status.canTransitionTo(ApprovalStatus.WITHDRAWN)) {
          throw new BusinessException(ErrorCode.APPROVAL_INVALID_STATUS_TRANSITION);
      }
      this.status = ApprovalStatus.WITHDRAWN;
  }
  ```
  - "결재자 처리 여부" 검증은 여기서 하지 않는다. Service 책임.

  **submitFromTempSaved()** — TEMP_SAVED → PROCESSING
  ```java
  public void submitFromTempSaved() {
      if (!status.canTransitionTo(ApprovalStatus.PROCESSING)) {
          throw new BusinessException(ErrorCode.APPROVAL_INVALID_STATUS_TRANSITION);
      }
      this.status = ApprovalStatus.PROCESSING;
  }
  ```
  - 본인 확인은 단계 6에서 Service가 사전 수행 (Entity는 자신이 누가 호출했는지 모름)

  **markAsApproved() / markAsRejected(String rejectReason)** — 단계 6에서 사용할 메서드
  ```java
  public void markAsApproved() {
      if (!status.canTransitionTo(ApprovalStatus.APPROVED)) {
          throw new BusinessException(ErrorCode.APPROVAL_INVALID_STATUS_TRANSITION);
      }
      this.status = ApprovalStatus.APPROVED;
  }

  public void markAsRejected(String rejectReason) {
      if (!status.canTransitionTo(ApprovalStatus.REJECTED)) {
          throw new BusinessException(ErrorCode.APPROVAL_INVALID_STATUS_TRANSITION);
      }
      this.status = ApprovalStatus.REJECTED;
      this.rejectReason = rejectReason;
  }
  ```
  - "모든 결재자가 승인했는지" 같은 검증은 Service에서 사전 수행 후 호출.

### D. Approver Entity 리팩토링

- [ ] 클래스 어노테이션 동일 (`@Entity`, `@Table`, `@Getter`, 생성자 `@Builder`)
- [ ] **`@Setter` 사용 금지**
- [ ] `protected Approver()` 기본 생성자
- [ ] `approverStatus` 필드:
  - 타입: `ApproverStatus` (Enum)
  - 어노테이션: `@Enumerated(EnumType.STRING)`, `@Column(nullable = false)`
- [ ] 상태 변경 메서드 추가:

  **approve()** — PENDING → APPROVED
  ```java
  public void approve() {
      if (!approverStatus.canTransitionTo(ApproverStatus.APPROVED)) {
          throw new BusinessException(ErrorCode.APPROVER_INVALID_STATUS_TRANSITION);
      }
      this.approverStatus = ApproverStatus.APPROVED;
      this.approverDate = LocalDateTime.now();
  }
  ```

  **reject()** — PENDING → REJECTED
  ```java
  public void reject() {
      if (!approverStatus.canTransitionTo(ApproverStatus.REJECTED)) {
          throw new BusinessException(ErrorCode.APPROVER_INVALID_STATUS_TRANSITION);
      }
      this.approverStatus = ApproverStatus.REJECTED;
      this.approverDate = LocalDateTime.now();
  }
  ```

### E. Builder 패키지 삭제

- [ ] `approval/builder/` 디렉토리 전체 삭제
- [ ] 이 패키지를 import하던 곳 확인 후 Lombok `@Builder` 사용으로 교체
  - ApprovalService 등에서 사용 중 → "임시 수정 허용" 범위에서 처리

### F. DB 마이그레이션 스크립트 작성

- [ ] `src/main/resources/db/migration/V1__approval_status_to_enum.sql`
  ```sql
  UPDATE APPROVAL SET APPROVAL_STATUS = 'PROCESSING' WHERE APPROVAL_STATUS = '처리 중';
  UPDATE APPROVAL SET APPROVAL_STATUS = 'APPROVED'   WHERE APPROVAL_STATUS = '승인';
  UPDATE APPROVAL SET APPROVAL_STATUS = 'REJECTED'   WHERE APPROVAL_STATUS = '반려';
  UPDATE APPROVAL SET APPROVAL_STATUS = 'WITHDRAWN'  WHERE APPROVAL_STATUS = '회수';
  UPDATE APPROVAL SET APPROVAL_STATUS = 'TEMP_SAVED' WHERE APPROVAL_STATUS = '임시저장';
  ```

- [ ] `src/main/resources/db/migration/V2__approver_status_to_enum.sql`
  ```sql
  UPDATE APPROVER SET APPROVER_STATUS = 'APPROVED' WHERE APPROVER_STATUS = '승인';
  UPDATE APPROVER SET APPROVER_STATUS = 'REJECTED' WHERE APPROVER_STATUS = '반려';
  UPDATE APPROVER SET APPROVER_STATUS = 'PENDING'  WHERE APPROVER_STATUS = '대기'
                                                      OR APPROVER_STATUS IS NULL
                                                      OR APPROVER_STATUS = '';
  ```

  **실제 테이블·컬럼명이 위와 다를 경우 작업 중지하고 사용자에게 확인 요청.**

### G. ApprovalService 임시 수정

다음 메서드 시그니처 변경:
- [ ] `updateApprovalStatus(String approvalNo)` → `updateApprovalStatus(String approvalNo, int memberId)`
  - 내부에서 `Approval.withdraw(memberId)` 호출
  - 기존 builder 기반 상태 변경 코드 제거
  - 기존 한글 비교 코드를 Enum 비교로 교체

다른 메서드들도 컴파일 깨질 가능성 있음:
- [ ] setter 호출하던 부분 → Lombok `@Builder` 또는 Entity 메서드로 교체
- [ ] 한글 상태 문자열 비교 → Enum 비교로 교체
- [ ] **단, 위 변경은 "컴파일 통과" 목적의 최소 수정만 허용.** 본격 리팩토링은 단계 6.

### H. ApprovalController 임시 수정

**`updateApprovalstatus` 메서드만 수정**:
- [ ] SecurityContext에서 memberId 추출 (기존 다른 메서드의 패턴 참조)
  ```java
  Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
  int memberId = Integer.parseInt(authentication.getName());
  ```
- [ ] `approvalService.updateApprovalStatus(approvalNo, memberId)` 호출로 변경

**다른 컨트롤러 메서드는 절대 건드리지 말 것.** 단계 7에서 일괄 정리.

### I. 빌드 통과 확인

- [ ] `./gradlew compileJava` 통과
- [ ] 임시 수정한 모든 위치를 작업 보고서에 기록

## Success Criteria

다음 조건을 **모두** 만족해야 단계 1 완료로 간주한다.

1. `./gradlew compileJava` 빌드 통과
2. `Approval`, `Approver` Entity에 setter가 단 하나도 없음
3. `approval/builder/` 패키지가 존재하지 않음
4. `ApprovalStatus`, `ApproverStatus` Enum에 `canTransitionTo()` 메서드가 존재하고,
   본 task의 "상태 전이 규칙" 표대로 구현되어 있음
5. `Approval.withdraw(memberId)` 호출 시 본인이 아니면 `APPROVAL_WITHDRAW_NOT_OWNER` 발생
6. `Approval.withdraw(memberId)` 호출 시 PROCESSING 외 상태에서는 `APPROVAL_INVALID_STATUS_TRANSITION` 발생
7. `ApprovalController.updateApprovalstatus`가 SecurityContext에서 memberId 추출 후 Service에 전달
8. ErrorCode에 AP001~AP009 (9개) 추가
9. 마이그레이션 SQL 파일 2개가 작성됨
10. 임시 수정한 위치 목록이 작업 보고서에 포함됨

## 수동 검증 (인간이 수행)

마이그레이션 SQL을 적용한 DB에서 다음을 수행:

### DB 상태 확인
```sql
SELECT DISTINCT APPROVAL_STATUS FROM APPROVAL;
SELECT DISTINCT APPROVER_STATUS FROM APPROVER;
```
모두 영어 코드여야 한다.

### API 동작 확인

- [ ] **결재 목록 조회 API**: 기존과 동일한 응답 확인
- [ ] **결재 상세 조회 API**: 정상 응답 확인
- [ ] **회수 API (보안 결함 검증)**:
  - 로그인한 사용자 본인의 결재 회수 → 성공
  - 다른 사용자가 본인이 아닌 결재 회수 시도 → 403 + `APPROVAL_WITHDRAW_NOT_OWNER`
  - 이미 APPROVED된 결재 회수 시도 → 400 + `APPROVAL_INVALID_STATUS_TRANSITION`

## 작업 후 보고 양식

작업 완료 후 다음 형식으로 보고할 것.

```
## 단계 1 작업 보고

### 신규 생성 파일
- approval/enums/ApprovalStatus.java
- approval/enums/ApproverStatus.java
- src/main/resources/db/migration/V1__approval_status_to_enum.sql
- src/main/resources/db/migration/V2__approver_status_to_enum.sql

### 수정 파일
- approval/entity/Approval.java (+XX -YY)
- approval/entity/Approver.java (+XX -YY)
- common/error/ErrorCode.java (+9 -0)

### 임시 수정 파일 (단계 6·7에서 제거 예정)
- approval/service/ApprovalService.java
  - 라인 XXX: setter 호출을 새 Entity 메서드로 교체
  - 라인 YYY: 한글 상태 비교 → Enum 비교 교체
  - 라인 ZZZ: updateApprovalStatus 시그니처 변경
- approval/controller/ApprovalController.java
  - 라인 NNN: updateApprovalstatus에 SecurityContext 추출 추가

### 삭제 파일
- approval/builder/ApprovalBuilder.java
- approval/builder/ApproverBuilder.java
- (등 모든 Builder)

### 빌드 결과
./gradlew compileJava: BUILD SUCCESSFUL

### 임시 수정 라인 합계
XX 라인 (30 라인 이하 권장 — 초과 시 단계 분할 재검토 필요)

### 확인 필요 사항
(있다면 기재. 없다면 "없음")
```

## 작업 중 멈춰야 할 상황

다음 상황이 발생하면 작업을 중단하고 사용자에게 보고할 것.

1. 테이블명·컬럼명이 마이그레이션 SQL과 다름
2. Approval/Approver Entity에 상속 관계가 있어 단순 변경이 어려움
3. 한글/영어 상태값 외에 예상하지 못한 다른 값이 DB에 존재
4. 임시 수정 합계가 30라인을 초과할 것으로 예상됨 (단계 분할 재검토 필요)
5. `leave/entity/LeaveRequest.java` 또는 `leave/enums/LeaveStatus.java` 파일이 존재하지 않음
   (참조 패턴을 확인할 수 없음)
6. ApprovalController의 다른 메서드가 `updateApprovalStatus` 호출 시그니처에 의존하고 있음
   (즉, 시그니처 변경이 더 광범위한 영향을 미칠 경우)
