# 단계 2: DTO 정리

## 목적
현재 DTO는 Lombok 미적용, 상태/날짜 필드가 String 타입으로 되어 있다.
이를 Lombok 적용 + 타입 정확성 확보로 정리한다.
단계 3(공통 응답 체계 통합)의 전제 조건이다.

## 전제 조건
- 단계 1 완료 (ApprovalStatus, ApproverStatus Enum 존재)
- compileJava BUILD SUCCESSFUL 상태

---

## 작업 범위 (Scope)

### 수정 대상
- `approval/dto/ApprovalDTO.java`
- `approval/dto/ApproverDTO.java`

### 범위 외 (절대 건드리지 말 것)
- `approval/dto/AttachmentDTO.java`
- `approval/dto/ReferencerDTO.java`
- `approval/dto/FormDTO.java`
- `approval/dto/DepartmentDTO.java`
- `approval/dto/MemberDTO.java`
- `approval/dto/PositionDTO.java`
- `approval/entity/` 하위 모든 파일
- `approval/service/ApprovalService.java`
- `approval/controller/ApprovalController.java`
- `common/` 하위 모든 파일
- Repository 하위 모든 파일
- 그 외 approval/ 하위 모든 파일

---

## AS-IS (현재 상태)

```
ApprovalDTO
- 수동 getter/setter (Lombok 미적용)
- approvalDate: String 타입
- approvalStatus: String 타입

ApproverDTO
- 수동 getter/setter (Lombok 미적용)
- approverDate: String 타입
- approverStatus: String 타입
```

---

## TO-BE (목표 상태)

```
ApprovalDTO
- @Getter @Setter (Lombok 적용)
- approvalDate: LocalDateTime 타입
- approvalStatus: String 타입 유지 (단계 3에서 Enum으로 전환)

ApproverDTO
- @Getter @Setter (Lombok 적용)
- approverDate: LocalDateTime 타입
- approverStatus: String 타입 유지 (단계 3에서 Enum으로 전환)
```

> **approvalStatus / approverStatus를 String으로 유지하는 이유**
> Controller와 Service가 현재 String 기반으로 DTO를 사용하고 있다.
> 단계 3에서 응답 체계를 통합할 때 함께 Enum으로 전환하면 한 번에 정리된다.
> 단계 2에서 Enum으로 바꾸면 Service/Controller 임시 수정이 30라인을 초과할 가능성이 높다.

---

## 세부 작업 지시

### A. ApprovalDTO.java 수정

1. 클래스 레벨에 `@Getter @Setter` 추가 (Lombok)
2. 수동 getter/setter 메서드 전체 삭제
3. `approvalDate` 필드 타입: `String` → `LocalDateTime`
4. `toString()` 존재하면 삭제 (Lombok이 불필요하게 만들어준다면 유지 무방)
5. `approvalStatus` 필드는 `String` 타입 유지
6. import 추가: `java.time.LocalDateTime`, `lombok.Getter`, `lombok.Setter`

### B. ApproverDTO.java 수정

1. 클래스 레벨에 `@Getter @Setter` 추가 (Lombok)
2. 수동 getter/setter 메서드 전체 삭제
3. `approverDate` 필드 타입: `String` → `LocalDateTime`
4. `toString()` 존재하면 삭제
5. `approverStatus` 필드는 `String` 타입 유지
6. import 추가: `java.time.LocalDateTime`, `lombok.Getter`, `lombok.Setter`

### C. ApprovalService 컴파일 에러 임시 처리

DTO 타입 변경으로 ApprovalService에서 컴파일 에러가 발생할 수 있다.

- `approvalDate` / `approverDate`를 String으로 변환하던 코드 → LocalDateTime 직접 할당으로 교체
- 예: `.approvalDate(approval.getApprovalDate().toString())` → `.approvalDate(approval.getApprovalDate())`
- 컴파일 통과 목적의 최소 수정만. 본격 리팩토링은 단계 6.
- 임시 수정 위치에 주석 추가: `// TODO: Stage 6에서 제거`
- 임시 수정이 20라인을 초과할 것 같으면 즉시 중단하고 보고한다.

### D. ApprovalController 컴파일 에러 임시 처리

DTO 타입 변경으로 ApprovalController에서 컴파일 에러가 발생할 수 있다.

- 동일하게 최소 수정만 적용
- 임시 수정 위치에 주석 추가: `// TODO: Stage 3에서 제거`
- 임시 수정이 10라인을 초과할 것 같으면 즉시 중단하고 보고한다.

---

## 작업 중 멈춰야 할 상황

1. ApprovalDTO / ApproverDTO 외 DTO 파일을 수정해야 할 것 같은 경우
2. ApprovalService 임시 수정이 20라인을 초과할 것 같은 경우
3. ApprovalController 임시 수정이 10라인을 초과할 것 같은 경우
4. LocalDateTime 변환 중 포맷 불일치로 데이터 손실 가능성이 발견된 경우
5. approvalStatus / approverStatus를 Enum으로 바꾸지 않으면 컴파일이 안 되는 상황

위 상황 중 하나라도 해당되면 즉시 중단하고 사용자에게 보고한다.

---

## Success Criteria

작업 완료 후 다음 10가지를 모두 확인한다.

1. `compileJava` BUILD SUCCESSFUL
2. `ApprovalDTO`에 수동 getter/setter 없음
3. `ApproverDTO`에 수동 getter/setter 없음
4. `ApprovalDTO.approvalDate` 타입이 `LocalDateTime`
5. `ApproverDTO.approverDate` 타입이 `LocalDateTime`
6. `ApprovalDTO.approvalStatus` 타입이 `String` (변경 없음)
7. `ApproverDTO.approverStatus` 타입이 `String` (변경 없음)
8. Lombok `@Getter @Setter` 어노테이션이 두 DTO 클래스에 존재
9. 임시 수정 파일 및 위치 기록 완료
10. 범위 외 파일 변경 없음

---

## 작업 후 보고 양식

```
### 수정 파일 (+추가 -삭제 라인 수)

### 임시 수정 파일 (단계 6·7에서 제거 예정)
- 파일명
  - 라인 번호: 수정 내용 요약

### 임시 수정 라인 합계
(ApprovalService: N라인 / ApprovalController: N라인)

### 빌드 결과
cd final && ./gradlew compileJava: BUILD SUCCESSFUL / FAILED

### Success Criteria 확인
1. compileJava 통과:
2. ApprovalDTO 수동 getter/setter 없음:
3. ApproverDTO 수동 getter/setter 없음:
4. ApprovalDTO.approvalDate 타입 LocalDateTime:
5. ApproverDTO.approverDate 타입 LocalDateTime:
6. ApprovalDTO.approvalStatus String 유지:
7. ApproverDTO.approverStatus String 유지:
8. Lombok @Getter @Setter 존재:
9. 임시 수정 위치 기록:
10. 범위 외 파일 변경 없음:

### 확인 필요 사항
```
