# Stage 2 작업 보고서: DTO 정리

작성일: 2026-07-28

---

### 수정 파일 (+추가 -삭제 라인 수)

| 파일 | 변경 |
|---|---|
| `approval/dto/ApprovalDTO.java` | +10 -162 (수동 getter/setter 13쌍 + toString 삭제, Lombok 적용) |
| `approval/dto/ApproverDTO.java` | +12 -99 (수동 getter/setter 10쌍 + toString 삭제, Lombok 적용) |
| `approval/service/ApprovalService.java` | +8 -21 (임시 수정 3곳) |

---

### 세부 변경 내용

**ApprovalDTO.java**
- `@Getter @Setter` 추가, 수동 getter/setter 13쌍 전체 삭제
- `approvalDate`: `String` → `LocalDateTime` + `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`
- `approvalStatus`, `finalApproverDate`(생성자 파라미터명 `approverDate`): String 유지, 변경 없음
- `toString()` 삭제
- 17-파라미터 생성자 유지, `approvalDate` 파라미터 타입만 `LocalDateTime`으로 변경
  (`@AllArgsConstructor` 미사용 — `approverDate` 파라미터가 `finalApproverDate` 필드에 매핑되는 구조 보존)
- import 추가: `java.time.LocalDateTime`, `lombok.Getter`, `lombok.Setter`, `com.fasterxml.jackson.annotation.JsonFormat`

**ApproverDTO.java**
- `@Getter @Setter` 추가, 수동 getter/setter 10쌍 전체 삭제
- `approverDate`: `String` → `LocalDateTime` + `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`
- `approverStatus`: String 유지, 변경 없음
- `toString()` 삭제
- 6-파라미터 / 10-파라미터 생성자 둘 다 유지, `approverDate` 파라미터 타입만 변경
- import 추가: 위와 동일 4개

---

### 임시 수정 파일 (단계 6에서 제거 예정)

**ApprovalService.java**

- `selectApproval()` L345-349 부근: `String approvalFormattedDateTime = approval.getApprovalDate().format(formatter);` 삭제 → 호출부(L~415, `ApprovalDTO` 생성자)에서 `approval.getApprovalDate()` 직접 전달
- `selectApproval()` L360-368 부근: `approverFormattedDateTime` null 체크·포맷 블록 삭제 (LocalDateTime은 null 허용이라 가드 불필요) → `ApproverDTO` 생성자 호출에서 `approverList.get(i).getApproverDate()` 직접 전달
- `updateApprover()` L662-664 부근: `DateTimeFormatter formatter` 선언 + `LocalDateTime.parse(approvalDTO.getApprovalDate(), formatter)` 삭제 → `Approval` 생성자 호출에서 `approvalDTO.getApprovalDate()` 직접 전달 (역파싱 제거)
- `ListToDTO()` L844-852 부근: `DateTimeFormatter formatter` 선언 삭제 → `approvalDTO.setApprovalDate(approval.getApprovalDate().format(formatter))` → `approvalDTO.setApprovalDate(approval.getApprovalDate())`

각 위치에 `// TODO: Stage 6에서 제거` 주석 추가함.

`selectApproval()`의 346행 `DateTimeFormatter formatter` 선언 자체는 삭제하지 않음 — `finalApproverDate`(String, Stage 2 범위 밖 필드) 계산에 여전히 사용 중 (L~394, ~398, ~401행).

기존 Stage 1 임시 수정(L~450, ~618, ~676, ~682 부근, TODO 표시된 곳)은 건드리지 않음.

### 임시 수정 라인 합계

ApprovalService: **약 8라인 변경/추가** (삭제 21라인 포함, 20라인 한도 내)

---

### 빌드 결과

```
cd final; .\gradlew.bat compileJava: BUILD SUCCESSFUL (17s)
```

### 부팅 결과

```
cd final; .\gradlew.bat bootRun: 정상 기동 확인
"Started Application in 9.273 seconds" 로그 확인, 에러/예외 없음. 이후 프로세스 종료.
```

---

### Success Criteria 확인

| # | 항목 | 결과 |
|---|---|---|
| 1 | compileJava 통과 | ✅ BUILD SUCCESSFUL |
| 2 | ApprovalDTO 수동 getter/setter 없음 | ✅ |
| 3 | ApproverDTO 수동 getter/setter 없음 | ✅ |
| 4 | ApprovalDTO.approvalDate 타입 LocalDateTime | ✅ |
| 5 | ApproverDTO.approverDate 타입 LocalDateTime | ✅ |
| 6 | ApprovalDTO.approvalDate @JsonFormat 존재 | ✅ `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` |
| 7 | ApproverDTO.approverDate @JsonFormat 존재 | ✅ `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` |
| 8 | ApprovalDTO.approvalStatus String 유지 | ✅ |
| 9 | ApproverDTO.approverStatus String 유지 | ✅ |
| 10 | Lombok @Getter @Setter 존재 | ✅ 두 클래스 모두 |
| 11 | 임시 수정 위치 기록 | ✅ 위 "임시 수정 파일" 항목 참조 |
| 12 | 범위 외 파일 변경 없음 | ✅ `git diff --stat` 확인 (DTO 2개 + ApprovalService만 변경) |

---

### 확인 필요 사항

- **사전 확인**: 작업 시작 시점에 워킹 트리에 미커밋 변경(AGENTS.md, docs/refactoring/approval/tasks/02-dto.md
  문서 수정 + REFACTORING_SUMMARY.md, .idea/compiler.xml 미추적 파일)이 있었음. 사용자에게 보고 후
  "그대로 진행"으로 확인받고 작업 진행함. 이 파일들은 이번 Stage 2 코드 작업과 무관.
- `ApprovalController.java`는 `approvalDate`/`approverDate`를 참조하지 않아 수정 불필요함 (grep 확인 완료, 세부 작업 D 해당 없음).
- 날짜 관련 API 응답 포맷(`yyyy-MM-dd HH:mm:ss`)이 실제로 기존과 동일하게 나가는지는 수동 API 확인 필요 (사용자 담당 영역).
- `updateApproval()`(L~447 부근, Stage 6 TODO 구간)의 `DateTimeFormatter formatter` 선언(L446 부근)은
  현재도 해당 메서드 내에서 미사용 상태로 남아있음 — Stage 1 시점부터 존재하던 것으로 이번 작업 범위 밖이라 그대로 둠.
