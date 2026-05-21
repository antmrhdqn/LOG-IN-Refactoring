# 전자결재 도메인 - AS-IS (리팩토링 전 현황)

리팩토링 작업 시작 시점의 코드 상태 스냅샷. 작업 진행 중 참조용이며,
완료 후에는 `docs/refactoring/completed/approval-domain.md`의 Before 섹션으로 통합된다.

## 1. ApprovalService.java (1,066줄, God Class)

### 혼재된 책임
- 결재 CRUD (기안 작성, 수정, 삭제)
- 결재 상태 변경 (승인, 반려, 회수)
- 파일 업로드·삭제 (multipart 처리, 파일 저장 경로 계산)
- 부서·멤버·직급 조회 (결재선 구성용)
- DTO ↔ Entity 변환 (ModelMapper 사용)

### 코드 특성
- 수동 Builder 클래스 4개를 `approval/builder/` 패키지에서 사용
- try-catch로 예외를 잡고 null을 반환하는 패턴 다수
- 디버깅용 `System.out.println` 호출 잔존
- 한글 문자열로 상태 비교 (`if (status.equals("승인"))` 형태)

## 2. ApprovalController.java (470줄)

### 비즈니스 로직 잔존
- 결재번호 생성 로직 (`YYYY-폼번호-순번` 조립)
- 결재자번호·참조자번호·첨부파일번호 채번
- 임시저장 → 기안 전환 시 ID 재조립

### 응답 처리
- 자체 정의한 `ResponseDTO`로 응답 (공통 `ResponseMessage<T>`와 불일치)
- try-catch 후 에러 시 ResponseDTO에 메시지만 담아 반환

## 3. 상태값 관리

### 현재 표현
한글 문자열로 직접 저장·비교.

| 결재 상태 (APPROVAL_STATUS) | 결재자 상태 (APPROVER_STATUS) |
|---|---|
| "처리 중" | "승인" |
| "승인" | "반려" |
| "반려" | (null 또는 공백) |
| "회수" | |
| "임시저장" | |

### 문제점
- 오타 위험 ("처리 중" vs "처리중" 등 공백 차이 등)
- 상태 전이 규칙이 코드 어디에도 없음 (이미 승인된 결재를 다시 반려해도 막을 장치 없음)
- IDE의 자동완성·리팩토링 도구 활용 불가

## 4. DTO

- 수동 getter/setter (Lombok 미적용)
- `approvalDate`, `approverDate`: String 타입 (LocalDateTime 아님)
- `approvalStatus`, `approverStatus`: String 타입

## 5. 에러 처리

- 도메인별로 분산된 try-catch
- 실패 시 null 반환 또는 자체 ResponseDTO에 에러 메시지
- `BusinessException` / `GlobalExceptionHandler` 미사용

## 6. 파일 처리

- 업로드·삭제 로직이 `ApprovalService` 안에 인라인
- 파일 저장 경로 계산 로직도 서비스에 포함
- DB 트랜잭션과 파일 I/O 트랜잭션 경계가 섞임 (rollback 시 파일이 남거나, 반대 상황 발생 가능)

## 7. 의존성

- ModelMapper 기반 Entity ↔ DTO 변환
- 수동 Builder 패키지 (`approval/builder/`)
- 공통 컴포넌트 의존 없음 (자체 ResponseDTO, 자체 에러 처리)
