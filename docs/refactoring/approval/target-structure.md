# 전자결재 도메인 - 목표 패키지 구조

리팩토링 완료 후 도달해야 할 패키지 구조.

## 디렉토리 구조

```
com.insider.login.approval/
├── controller/
│   └── ApprovalController.java            (얇은 컨트롤러, 100~120줄)
│
├── service/
│   ├── ApprovalCommandService.java        (기안·수정·삭제·상태변경 — 쓰기)
│   ├── ApprovalQueryService.java          (목록·상세 조회, 부서·멤버 조회 — 읽기)
│   ├── ApprovalFileService.java           (파일 업로드·삭제)
│   └── ApprovalNoGenerator.java           (번호 채번 컴포넌트)
│
├── repository/
│   ├── ApprovalRepository.java
│   ├── ApproverRepository.java
│   ├── AttachmentRepository.java
│   ├── ReferencerRepository.java
│   ├── FormRepository.java
│   ├── ApprovalMemberRepository.java
│   ├── ApprovalDepartmentRepository.java
│   └── ApprovalPositionRepository.java
│
├── entity/
│   ├── Approval.java                      (상태 변경 메서드 보유)
│   ├── Approver.java                      (상태 변경 메서드 보유)
│   ├── Attachment.java
│   ├── Referencer.java
│   ├── Form.java
│   ├── Member.java
│   ├── Department.java
│   └── Position.java
│
├── dto/
│   ├── ApprovalDTO.java
│   ├── ApproverDTO.java
│   ├── AttachmentDTO.java
│   ├── ReferencerDTO.java
│   ├── FormDTO.java
│   ├── DepartmentDTO.java
│   ├── MemberDTO.java
│   └── PositionDTO.java
│
└── enums/
    ├── ApprovalStatus.java                (신규)
    └── ApproverStatus.java                (신규)
```

## 삭제 대상 패키지

```
com.insider.login.approval.builder/        ← 전체 삭제 (Lombok @Builder로 대체)
```

## 각 패키지의 책임

### controller/
- HTTP 요청 파싱
- 인증 정보 추출 (헬퍼 메서드로 캡슐화)
- 서비스 메서드 호출
- `ResponseMessage<T>` 응답 구성
- **비즈니스 로직 금지**

### service/
- `ApprovalCommandService`: 쓰기 책임만. 모든 메서드 `@Transactional`
- `ApprovalQueryService`: 읽기 책임만. 메서드 `@Transactional(readOnly = true)`
- `ApprovalFileService`: 파일 I/O 전담. 트랜잭션 경계 분리
- `ApprovalNoGenerator`: 번호 생성 규칙 캡슐화

### entity/
- 상태 변경은 메서드로만 (setter 없음)
- `@Getter`, `@Builder` (Lombok)
- 비즈니스 규칙(상태 전이, 회수 가능 여부 등)을 메서드로 표현

### enums/
- `ApprovalStatus`: PROCESSING, APPROVED, REJECTED, WITHDRAWN, TEMP_SAVED
- `ApproverStatus`: APPROVED, REJECTED, PENDING
- 각각 `canTransitionTo(다음 상태)` 메서드 보유

### dto/
- Lombok 적용
- 시간 필드는 `LocalDateTime` 타입
- 상태 필드는 Enum 타입

### repository/
- 변경 없음 (Spring Data JPA 인터페이스 그대로)

## 책임 분리 원칙 요약

| 계층 | 알아야 할 것 | 몰라도 되는 것 |
|---|---|---|
| Controller | HTTP, 인증, DTO 변환 호출 | 비즈니스 규칙, DB |
| CommandService | 비즈니스 규칙, 트랜잭션 | HTTP, 파일 I/O 세부사항 |
| QueryService | 조회 조건, 페이징 | 쓰기 트랜잭션 |
| FileService | 파일 시스템, 경로 | 결재 비즈니스 규칙 |
| NoGenerator | 번호 규칙 | 결재 처리 로직 |
| Entity | 자신의 상태 전이 | 외부 서비스, DB 직접 접근 |
| Enum | 상태 간 전이 규칙 | Entity 필드 |
