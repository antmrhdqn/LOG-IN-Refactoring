# 단계 2: DTO 정리

## 목적
현재 DTO는 Lombok 미적용, 상태/날짜 필드가 String 타입으로 되어 있다.
이를 Lombok 적용 + 타입 정확성 확보로 정리한다.
단계 3(공통 응답 체계 통합)의 전제 조건이다.

## 전제 조건
- 단계 1 완료 (ApprovalStatus, ApproverStatus Enum 존재)
- 단계 1.5 완료 (Repository JPQL 리터럴 정합 완료·커밋·푸시)
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
- `approval/service/ApprovalService.java` — 단, 세부 작업 C의 임시 수정만 허용
- `approval/controller/ApprovalController.java` — 단, 세부 작업 D의 임시 수정만 허용
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
- approvalDate: LocalDateTime 타입 + @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
- approvalStatus: String 타입 유지 (단계 3에서 Enum으로 전환)

ApproverDTO
- @Getter @Setter (Lombok 적용)
- approverDate: LocalDateTime 타입 + @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
- approverStatus: String 타입 유지 (단계 3에서 Enum으로 전환)
```

> **@JsonFormat을 붙이는 이유**
> spec.md 제약: "기존 API 명세(요청/응답 JSON 구조)는 변경되지 않는다."
> LocalDateTime을 Jackson이 직렬화하면 기본 포맷이 ISO 8601(`2026-07-28T10:30:00`)로
> 바뀌어 기존 포맷(`2026-07-28 10:30:00`)과 달라진다.
> 같은 응답 안에서 `finalApproverDate`(String, 기존 포맷)과 `approvalDate`(LocalDateTime,
> ISO 포맷)가 섞이는 문제도 발생한다. @JsonFormat으로 기존 포맷을 유지하면
> 내부 타입 안전성과 API 호환성을 동시에 확보한다.

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
4. `approvalDate` 필드에 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` 추가
5. `toString()` 삭제
6. `approvalStatus` 필드는 `String` 타입 유지
7. 수동 생성자는 유지하되 `approvalDate` 파라미터 타입만 `String` → `LocalDateTime`으로 변경
   (⚠️ `@AllArgsConstructor` 사용 금지 — 파라미터명 `approverDate` → 필드명 `finalApproverDate` 매핑이 깨짐)
8. import 추가: `java.time.LocalDateTime`, `lombok.Getter`, `lombok.Setter`,
   `com.fasterxml.jackson.annotation.JsonFormat`

### B. ApproverDTO.java 수정

1. 클래스 레벨에 `@Getter @Setter` 추가 (Lombok)
2. 수동 getter/setter 메서드 전체 삭제
3. `approverDate` 필드 타입: `String` → `LocalDateTime`
4. `approverDate` 필드에 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` 추가
5. `toString()` 삭제
6. `approverStatus` 필드는 `String` 타입 유지
7. 수동 생성자는 유지하되 `approverDate` 파라미터 타입만 `String` → `LocalDateTime`으로 변경
8. import 추가: `java.time.LocalDateTime`, `lombok.Getter`, `lombok.Setter`,
   `com.fasterxml.jackson.annotation.JsonFormat`

### C. ApprovalService 컴파일 에러 임시 처리

DTO 타입 변경으로 ApprovalService에서 컴파일 에러가 발생한다.
아래 패턴의 최소 수정만 적용한다. 본격 리팩토링은 단계 6.

**수정 패턴**: `.format(formatter)`로 String 변환 후 DTO에 넣던 코드
→ Entity의 `LocalDateTime` 값을 DTO에 직접 전달

**예상 영향 지점** (라인 번호는 현재 파일 기준, 실제 위치는 컴파일 에러로 확인):
- `selectApproval()` 내 `approvalFormattedDateTime` 변수 → 불필요해짐, 직접 전달
- `selectApproval()` 내 `approverFormattedDateTime` 변수 → 동일
- `selectApproval()` 내 ApprovalDTO·ApproverDTO 생성자 호출 → 파라미터 타입 변경에 맞춤
- `updateApprover()` 내 `LocalDateTime.parse(approvalDTO.getApprovalDate(), formatter)`
  → `approvalDTO.getApprovalDate()` 직접 사용 (역파싱 제거, 오히려 단순화)
- `ListToDTO()` 내 `setApprovalDate(... .format(formatter))` → `.setApprovalDate(... getApprovalDate())`

**예상 임시 수정 합계: 6~7라인** (20라인 한도 내)

- 임시 수정 위치에 주석 추가: `// TODO: Stage 6에서 제거`
- 기존 Stage 1 임시 수정(라인 463, 630, 688, 694)은 Enum 관련이므로 건드리지 말 것
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

작업 완료 후 다음 12가지를 모두 확인한다.

1. `compileJava` BUILD SUCCESSFUL
2. `ApprovalDTO`에 수동 getter/setter 없음
3. `ApproverDTO`에 수동 getter/setter 없음
4. `ApprovalDTO.approvalDate` 타입이 `LocalDateTime`
5. `ApproverDTO.approverDate` 타입이 `LocalDateTime`
6. `ApprovalDTO.approvalDate`에 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` 존재
7. `ApproverDTO.approverDate`에 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` 존재
8. `ApprovalDTO.approvalStatus` 타입이 `String` (변경 없음)
9. `ApproverDTO.approverStatus` 타입이 `String` (변경 없음)
10. Lombok `@Getter @Setter` 어노테이션이 두 DTO 클래스에 존재
11. 임시 수정 파일 및 위치 기록 완료
12. 범위 외 파일 변경 없음

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
6. ApprovalDTO.approvalDate @JsonFormat 존재:
7. ApproverDTO.approverDate @JsonFormat 존재:
8. ApprovalDTO.approvalStatus String 유지:
9. ApproverDTO.approverStatus String 유지:
10. Lombok @Getter @Setter 존재:
11. 임시 수정 위치 기록:
12. 범위 외 파일 변경 없음:

### 확인 필요 사항
```
