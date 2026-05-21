# 휴가 도메인 패턴 가이드

전자결재 도메인 리팩토링 시 이 패턴을 그대로 따른다.
실제 코드는 `leave/` 패키지 참조.

## 1. Enum 기반 상태 관리

### 파일
`leave/enums/LeaveStatus.java`

### 패턴
- 상태값을 Enum으로 정의
- DB 컬럼은 VARCHAR 유지, JPA가 `@Enumerated(EnumType.STRING)`으로 변환
- 한글 dbValue 등 별도 매핑 필드 없음 (Enum 이름이 곧 DB 값)
- 비즈니스 판단 메서드를 Enum에 포함 (예: `isHalfDay()`, `isDeductFromAnnual()`)
- 상태 전이 규칙도 Enum이 보유: `canTransitionTo(다음 상태)` 메서드

### 결재 도메인 적용
- `ApprovalStatus`: PROCESSING, APPROVED, REJECTED, WITHDRAWN, TEMP_SAVED
- `ApproverStatus`: PENDING, APPROVED, REJECTED
- 각 Enum에 `canTransitionTo()` 구현 (상태 전이 규칙은 task.md 참조)

## 2. Entity 구조

### 파일
`leave/entity/LeaveRequest.java`

### 핵심 패턴

#### 어노테이션
```java
@Entity
@Table(name = "leave_request")
@Getter        // Lombok
public class LeaveRequest {
    // @Setter 없음. 모든 변경은 메서드로만.
}
```

#### 기본 생성자
```java
protected LeaveRequest() { }
```
- 접근제한자 `protected` (JPA 요구사항 충족 + 외부 생성 방지)

#### 생성자 단위 @Builder
```java
@Builder
public LeaveRequest(int memberId, LeaveType leaveType, LeaveStatus status,
                    LocalDate startDate, LocalDate endDate, BigDecimal useDays,
                    String reason, LocalDateTime appliedAt) {
    this.memberId = memberId;
    // ...
}
```
- 클래스 단위 `@Builder` 아님. 생성 시 필요한 필드만 받는 생성자에 부착
- 자동 부여되는 필드(id, 처리 후 채워지는 필드 등)는 생성자에서 제외

#### 상태 필드
```java
@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false)
private LeaveStatus status;
```

### 결재 도메인 적용
- `Approval`, `Approver` 모두 동일 구조
- setter 절대 만들지 말 것

## 3. 상태 변경 메서드

### 패턴
모든 상태 변경은 Entity 자신의 메서드로만 수행.

```java
public void approve(int approverId) {
    if (!status.canTransitionTo(LeaveStatus.APPROVED)) {
        throw new BusinessException(ErrorCode.LEAVE_INVALID_STATUS_TRANSITION);
    }
    this.status = LeaveStatus.APPROVED;
    this.approverId = approverId;
    this.approvedAt = LocalDateTime.now();
}
```

### 구조 원칙
1. **전이 가능 여부 검증** — `status.canTransitionTo(다음 상태)` 호출
2. **검증 실패 시 BusinessException** — 도메인 에러 코드와 함께
3. **상태 변경 + 관련 필드 갱신** — 시간, 처리자 등 함께 업데이트
4. **반환 타입은 void** — 변경 결과는 dirty checking으로 자동 반영

### 결재 도메인 적용
- `Approval.withdraw(int memberId)` — 본인 확인 + 상태 전이 검증
- `Approval.submitFromTempSaved()` — 임시저장→기안 전환
- `Approval.markAsApproved()` / `markAsRejected(String rejectReason)` — 단계 6에서 사용
- `Approver.approve()` / `Approver.reject()` — 결재자 처리

## 4. dirty checking 활용

### 패턴
```java
@Transactional
public void approveLeave(Long leaveRequestId, int approverId) {
    LeaveRequest request = leaveRequestRepository.findById(leaveRequestId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LEAVE_NOT_FOUND));
    request.approve(approverId);
    // save() 호출 없음 — JPA가 자동 UPDATE
}
```

### 핵심
- Repository에서 조회 → Entity 메서드 호출 → 메서드 종료 시 자동 UPDATE
- 명시적 `save()` 호출 불필요
- Entity → DTO → Entity 이중 변환 금지

### 결재 도메인 적용
- `ApprovalCommandService`의 모든 변경 작업
- 단계 1에서는 ApprovalService 임시 수정 시에도 이 패턴 따를 것

## 5. 합성 우선 (상속 ❌)

### 안 좋은 패턴 (지양)
```java
public class LeaveService extends LeaveUtil { ... }
```

### 좋은 패턴 (지향)
```java
@Service
@RequiredArgsConstructor
public class LeaveService {
    private final LeaveDaysCalculator leaveDaysCalculator;  // @Component 주입
}
```

### 이유
- 상속은 결합도가 높고 테스트가 어려움
- 합성은 의존성을 명시적으로 표현, 모킹 가능

### 결재 도메인 적용
- `ApprovalFileService`, `ApprovalNoGenerator`는 `@Component`로 만들어 주입
- 어떤 Service도 다른 클래스를 상속하지 않음

## 6. Builder 패턴

### 안 좋은 패턴 (지양)
```java
// 별도 빌더 클래스 (수동)
public class LeaveRequestBuilder {
    private LeaveRequest target;
    public LeaveRequestBuilder(LeaveRequest source) { ... }
    public LeaveRequestBuilder status(String status) { ... }
    public LeaveRequest builder() { ... }
}

// 사용
leaveRequest = new LeaveRequestBuilder(leaveRequest).status("APPROVED").builder();
```

### 좋은 패턴 (지향)
```java
// Lombok @Builder를 생성자에
@Builder
public LeaveRequest(int memberId, ...) { ... }

// 사용
LeaveRequest request = LeaveRequest.builder()
        .memberId(memberId)
        .leaveType(LeaveType.ANNUAL)
        // ...
        .build();
```

### 핵심
- 신규 생성은 `@Builder`로
- **변경은 새 Builder로 객체를 다시 만드는 것이 아니라 Entity 메서드 호출로**
- `approval/builder/` 패키지는 통째로 삭제

## 7. 공통 응답 / 에러

### 정상 응답
```java
@GetMapping("/leaves/{id}")
public ResponseEntity<ResponseMessage<LeaveRequestDTO>> getLeave(@PathVariable Long id) {
    LeaveRequestDTO dto = leaveQueryService.getLeave(id);
    return ResponseEntity.ok(new ResponseMessage<>(...));
}
```

### 에러
- 서비스에서 `throw new BusinessException(ErrorCode.XXX)`
- `GlobalExceptionHandler`가 자동으로 `ErrorResponse`로 변환
- 컨트롤러에서 try-catch 금지

### 결재 도메인 적용
- 자체 `ResponseDTO` 완전 제거 (단계 3)
- 컨트롤러의 모든 try-catch 제거 (단계 3)

## 8. Service 책임 분리 (Command / Query)

### 패턴
```java
@Service
@Transactional
@RequiredArgsConstructor
public class LeaveCommandService {
    // 쓰기 작업만: 신청, 승인, 반려, 취소
}

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaveQueryService {
    // 읽기 작업만: 단건 조회, 목록 조회
}
```

### 결재 도메인 적용
- `ApprovalCommandService`: 기안, 수정, 삭제, 상태 변경
- `ApprovalQueryService`: 목록·상세 조회, 부서·멤버 조회

## 9. 도메인 에러 코드 명명

### 패턴
```java
public enum ErrorCode {
    // 휴가
    LEAVE_NOT_FOUND("L001", HttpStatus.NOT_FOUND, "휴가 신청을 찾을 수 없습니다"),
    LEAVE_INVALID_STATUS_TRANSITION("L002", HttpStatus.BAD_REQUEST, "허용되지 않는 상태 전이입니다"),
    // ...
}
```

### 규칙
- 도메인별 접두사: C(공통), M(회원), L(휴가), AP(전자결재) 등
- 명명 패턴: `{도메인}_{상황}` (UPPER_SNAKE_CASE)
- 같은 도메인의 에러는 번호 순서대로

### 결재 도메인 적용
- AP001~AP009 (총 9개, 단계 1에서 추가)
- 향후 단계에서 발견되는 추가 케이스는 단계 진행 중 추가

## 종합 — 결재 도메인이 휴가에서 그대로 가져갈 것

| 영역 | 동일 패턴 적용 |
|---|---|
| Entity 구조 | @Getter + 생성자 @Builder + protected 기본 생성자 |
| 상태 관리 | Enum + @Enumerated(STRING) + canTransitionTo |
| 상태 변경 | Entity 메서드 (setter 금지) |
| DB 변환 | `@Enumerated(EnumType.STRING)`, 한글 dbValue 없음 |
| 트랜잭션 | @Transactional + dirty checking |
| 객체 생성 | Lombok @Builder (수동 Builder 금지) |
| 에러 처리 | BusinessException + ErrorCode |
| 응답 | ResponseMessage<T> / ErrorResponse |
| Service 구조 | Command / Query 분리 |

## 결재 도메인이 휴가와 다를 수 있는 부분

| 영역 | 차이 가능성 |
|---|---|
| 결재선 처리 | 휴가에 없는 복수 결재자 개념 (Approver Entity) |
| 파일 첨부 | 휴가는 단순, 결재는 첨부파일 필요 → `ApprovalFileService` 분리 |
| 번호 채번 | 휴가는 ID 자동 생성, 결재는 사용자 친화적 번호 (YYYY-폼번호-순번) → `ApprovalNoGenerator` |
| 임시저장 | 휴가에 없는 개념 → `TEMP_SAVED` 상태와 전환 메서드 추가 |

이런 부분은 휴가 패턴 흉내가 아니라 결재 도메인 고유 설계가 필요. task.md에 명시되어 있음.
