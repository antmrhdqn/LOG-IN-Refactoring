# Stage 3: 공통 응답 체계 통합 (Task)

> 대상 단계: 3 (의존 순서 1 → 1.5 → 2 → **3** → {4,5} → 6 → 7)
> 선행 완료: Stage 1(Entity), 1.5(Repository JPQL), 2(DTO) — 커밋·푸시됨
> 실행: Claude Code (plan mode 승인 후) / 검증·커밋: 사용자

---

## 0. 이 단계에서 확정된 전제 (실제 파일로 확인 완료)

작성 시점에 `ResponseDTO.java`, `GlobalExceptionHandler.java` 원본을 확인해 아래를 못박는다.
추측이 아니라 실측이다.

1. **성공 응답 envelope는 기존과 동일하다.**
   - `ResponseDTO(HttpStatus, String, Object)` 생성자가 `this.status = status.value()`로 **int(200)** 를 담는다. 필드는 `{ status:int, message:String, data:Object }`.
   - `ResponseMessage<T>`도 `{ status:int(200), message:String, data:T }`.
   - → `ResponseMessage.success(msg, data)`로 바꿔도 성공 응답 JSON은 **바이트 단위로 동일**. 프론트 무영향.

2. **`GlobalExceptionHandler`에 catch-all이 있다.**
   - `@ExceptionHandler(BusinessException.class)` → 도메인 코드별 `ErrorResponse`
   - `@ExceptionHandler(Exception.class)` → **500 / `C999` `ErrorResponse` 폴백**
   - → Controller의 try-catch를 제거해 예외가 전파돼도 raw 에러 페이지가 아니라 `ErrorResponse`로 떨어진다.

3. **에러 경로 응답 shape은 의도적으로 바뀐다 (insert/updateTemp 2곳 한정).**
   - Before: `HTTP 400` + `{status:200, message:<예외문자열>, data:null}` (기존부터 자기모순)
   - After: `HTTP 500` + `{status:500, code:"C999", message:"서버 내부 오류가 발생했습니다."}`
   - 이는 회귀가 아니라 통합의 결과다. Service를 `BusinessException`으로 정리하는 것은 **Stage 6([G])** 범위이며, Stage 3에서는 catch-all 500이 정상 결과다.

4. **테스트 소스셋(`src/test/**`)은 Stage 1 시점부터 이미 컴파일 실패 상태다 (실측 확인).**
   - `.\gradlew.bat compileTestJava` 실행 시 3개 에러: `ApprovalServiceTest`의 `setApprovalDate(String)` 2건(Stage 2의 `LocalDateTime` 전환과 충돌), `updateApprovalStatus(approvalNo)` 1건(Stage 1의 `(String,int)` 시그니처 전환과 충돌).
   - 즉 테스트는 이번 변경이 깨뜨리는 게 아니라 **이미 죽어 있다.** 프로젝트 검증 파이프라인(`compileJava`+`bootRun`+수동 API)이 테스트 소스셋을 컴파일하지 않아 그동안 드러나지 않았다.
   - → **Stage 3 검증은 `compileJava` / `bootRun` / 수동 API만 사용한다. `compileTestJava`는 판정 대상이 아니다.** 테스트 정비는 결재 리팩토링(Stage 7) 완료 후 별도로 다룬다(백로그 기록됨).

---

## 1. 목표

spec.md **문제 4(자체 ResponseDTO 사용)** 와 **[G-부분](컨트롤러 try-catch + null/ResponseDTO 반환)** 을 해소한다.
결재 도메인의 응답을 프로젝트 공통 체계(`ResponseMessage<T>` / `ErrorResponse`)로 통합하고,
Controller의 예외 처리를 `GlobalExceptionHandler` 위임으로 전환한다.

leave-pattern.md §7 목표: *자체 `ResponseDTO` 완전 제거(단계 3), 컨트롤러 try-catch 제거(단계 3),
에러는 Service `BusinessException` → `GlobalExceptionHandler` → `ErrorResponse`.*

> "완전 제거"는 이 단계에서 **프로덕션 코드의 `ResponseDTO` 참조를 0으로 만드는 것**으로 달성한다.
> 클래스 파일 물리 삭제는 유일 잔존 참조인 (이미 깨진) 테스트 정비와 묶어 별도로 처리한다 — 근거는 §5.

---

## 2. 작업 범위 (수정 대상)

- `approval/controller/ApprovalController.java` — 반환형·응답 본문·try-catch·import **(이 단계에서 수정하는 유일한 파일)**

`approval/dto/ResponseDTO.java`는 **삭제하지 않는다**(§5). 그 외 파일도 **수정하지 않는다.**

---

## 3. 범위 외 (경계 명시 — 절대 건드리지 말 것)

- **공통 패키지 무변경**: `ResponseMessage`, `ErrorResponse`, `ErrorCode`, `GlobalExceptionHandler`,
  `BusinessException`은 이미 정비돼 있다. 사용만 한다.
- **`ApprovalService.java` 무변경**: Service의 예외 삼키기(파일 실패 시 null 반환 등, 문제 [G] 본체)는
  **Stage 6**. 이번에 Service에 `throw`를 새로 넣지 않는다.
- **`dounloadFile` 무변경 [권장 — veto 가능]**: `ResponseEntity<Resource>`(파일 스트림) 반환이라 envelope
  통합 대상이 아니고, 내부 try-catch는 파일 I/O 처리다 → **Stage 4(파일 처리 분리)** 로 이관.
  지금 걷으면 `BusinessException(APPROVAL_FILE_NOT_FOUND=AP007)` 등 파일 에러 의미를 조기에 도입하게 된다.
  "컨트롤러 try-catch 전면 제거"는 Stage 3(envelope 엔드포인트) + Stage 4(파일 엔드포인트)로 완성된다.
- **이모지/한글 debug 로그 무변경**: `🎉🎉🎉`, `System.out.println(...)` 등은 **Stage 7 [J]**.
- **memberId 헤더 백도어 무변경**: `@RequestHeader("memberId")` 분기는 **Stage 7 [I]**.
- **채번·임시저장→기안 분기·파일 DTO 조립 무변경**: `selectApprovalNo` 호출, `substring(5,8).equals("ims")`,
  attachment DTO 루프 등은 **Stage 5/6**.
- **Stage 1/2 임시 수정(`// TODO: Stage 6에서 제거`) 라인 무변경.**
- **`src/test/**` 무변경**: 테스트 소스셋은 Stage 1·2 이전부터 컴파일 실패 상태다(§0-4, 실측 확인). `ApprovalControllerTest`가 `ResponseDTO`를 참조하지만, 이번 단계에서 테스트를 고치거나 지우지 않는다. 결재 리팩토링 완료 후 별도 정비.

---

## 4. 세부 작업

### 4-1. 반환형 + 응답 본문 교체 (성공 응답 전부)

모든 `new ResponseDTO(HttpStatus.OK, "<메시지>", <data>)` **인스턴스 생성 호출**을
`ResponseMessage.success("<메시지>", <data>)` 로 교체하고, 메서드 반환형을
`ResponseEntity<ResponseMessage<T>>` 로 바꾼다. **메시지 문자열은 그대로 보존**한다.

> ⚠️ **지역 변수 할당 주의 (`selectApprovalList`):** 응답 객체를 지역 변수에 담는 코드가 있다.
> L117 부근 `ResponseDTO response = new ResponseDTO(HttpStatus.OK, "상신 목록 조회 성공", approvalDTOPage);`.
> 우항만 바꾸면 `ResponseDTO response = ResponseMessage.success(...)` 가 되어 **타입 미스매치 컴파일 에러**가 난다.
> → **변수 선언 타입도 `ResponseMessage<Page<ApprovalDTO>>` 로 함께 바꾸거나, 변수를 없애고 인라인으로 반환**한다.
> 나머지 메서드는 `return new ResponseDTO(...)` 를 바로 반환하므로 우항 교체 + 반환형 변경으로 충분하다.

`ResponseEntity.ok().body(x)` 는 `ResponseEntity.ok(x)` 로 정리한다(동작 동일, leave 패턴과 일치).

메서드별 제네릭 타입 `T` (Service 반환형 기준, 확인 완료):

| Controller 메서드 | 호출 Service 메서드 | `T` |
|---|---|---|
| selectFormList | selectFormList() | `List<FormDTO>` |
| selectForm | selectForm(String) | `FormDTO` |
| selectApprovalByNo | selectApproval(String) | `ApprovalDTO` |
| selectApprovalList | selectApprovalList(...) | `Page<ApprovalDTO>` |
| updateApprovalstatus | updateApprovalStatus(...) | `ApprovalDTO` |
| updateApprovalTemp | updateApproval(...) | `ApprovalDTO` |
| insertApproval | insertApproval(...) | `ApprovalDTO` |
| updateApprover | updateApprover(...) | `ApproverDTO` |
| deleteApproval | approvalDelete(String) | `Boolean` |
| selectMember | selectMember(int) | `MemberDTO` |
| selectAllMembers | selectAllMemberList() | `List<MemberDTO>` |

예시 (selectFormList):

```java
// Before
@GetMapping("/approvals/forms")
public ResponseEntity<ResponseDTO> selectFormList() {
    log.info("폼 목록 조회 controller 들어왔다");
    return ResponseEntity.ok().body(new ResponseDTO(HttpStatus.OK, "폼 목록 조회 성공", approvalService.selectFormList()));
}

// After
@GetMapping("/approvals/forms")
public ResponseEntity<ResponseMessage<List<FormDTO>>> selectFormList() {
    log.info("폼 목록 조회 controller 들어왔다");
    return ResponseEntity.ok(ResponseMessage.success("폼 목록 조회 성공", approvalService.selectFormList()));
}
```

`selectApprovalList`의 지역 변수 케이스(위 ⚠️ 참조):

```java
// Before (L117~119 부근)
ResponseDTO response = new ResponseDTO(HttpStatus.OK, "상신 목록 조회 성공", approvalDTOPage);
System.out.println("조회성공");
return ResponseEntity.ok().body(response);

// After (인라인 반환 — 변수 제거)
System.out.println("조회성공");
return ResponseEntity.ok(ResponseMessage.success("상신 목록 조회 성공", approvalDTOPage));
```
(변수를 남기고 싶으면 선언 타입을 `ResponseMessage<Page<ApprovalDTO>>` 로 바꿔도 된다. `System.out.println`은 Stage 7 소관이라 이번엔 건드리지 않는다.)

`updateApprover`의 동적 메시지는 표현식 그대로 유지한다:
`ResponseMessage.success("전자결재" + approverDTO.getApproverStatus() + "처리 완료", approvalService.updateApprover(...))`.

### 4-2. try-catch 제거 ([G-부분]) — `insertApproval`, `updateApprovalTemp` 2곳

`ApprovalDTO result = null;` 선언 + try/catch 블록을 **직접 대입 + 성공 return** 으로 축약한다.
catch 안에서만 쓰이던 실패 로그·badRequest 반환을 제거한다(예외는 전파되어 `GlobalExceptionHandler`가 처리).

```java
// insertApproval - Before (L367~378 부근)
ApprovalDTO result = null;
try {
    result = approvalService.insertApproval(approvalDTO, multipartFile);
    log.info("결재 기안 결과 성공: " + result);
    return ResponseEntity.ok().body(new ResponseDTO(HttpStatus.OK, "전자결재 기안 성공", result));
} catch (Exception e) {
    log.info("결재 기안 결과 실패 : " + result);
    return ResponseEntity.badRequest().body(new ResponseDTO(HttpStatus.OK, e.getMessage(), result));
}

// insertApproval - After
ApprovalDTO result = approvalService.insertApproval(approvalDTO, multipartFile);
log.info("결재 기안 결과 성공: " + result);
return ResponseEntity.ok(ResponseMessage.success("전자결재 기안 성공", result));
```

`updateApprovalTemp`(L214~225 부근)도 동일 패턴으로, 메시지 `"결재 임시저장 수정 결과 성공"` 유지.

> 주의: 파일 저장 실패 시 Service가 **null을 반환**(예외 아님)하는 경로가 있다.
> 이 경우 여전히 `200 + data:null`이 나간다 — 기존과 **동일**하다. 이 무성 null은 Service 예외 삼키기
> = 문제 [G] 본체 = **Stage 6** 범위이므로, 이번 단계에서 Service를 고쳐 예외를 던지게 만들지 않는다.

### 4-3. import 정리

- **추가**: `import com.insider.login.common.response.ResponseMessage;`
- **제거(조건부)**: `import org.springframework.http.HttpStatus;` — `HttpStatus`가 파일 전체에서
  미사용이 되면 제거. (제거 전 `HttpStatus` 잔존 참조 0건 확인. `dounloadFile`은 `HttpStatus` 미사용.)
- `import com.insider.login.approval.dto.*;` 는 와일드카드라 유지(다른 DTO들이 여기서 옴). 손대지 않는다.

---

## 5. `ResponseDTO.java` 물리 삭제 — **보류 (이번 단계에서 삭제하지 않는다)**

전역 grep(실측 완료)으로 참조를 확인했다:

- **프로덕션 유일 참조는 `ApprovalController.java`** 다. 이 단계의 이행으로 프로덕션에서 `approval.dto.ResponseDTO` 참조는 0건이 된다. spec.md **문제 4(자체 `ResponseDTO` "사용")는 이 시점에 해소**된다.
- 잔존 참조는 `src/test/.../ApprovalControllerTest.java`(`new ResponseDTO()`) 하나뿐이며, 이 테스트 소스셋은 **이미 컴파일 실패 상태**다(§0-4).
- grep에 함께 잡히는 `survey.dto.SurveyResponseDTO`, `webSocket...RoomResponseDTO`/`EntRoomResponseDTO`는 **이름만 유사한 별개 클래스**로 무관하다.

**따라서 클래스 파일 물리 삭제는 하지 않는다.** 근거:
1. 목표(문제 4 해소)는 Controller 이행만으로 달성된다. 삭제는 목표에 불필요하다.
2. 유일 잔존 참조가 테스트인데, 이 프로젝트 검증은 `compileTestJava`를 돌리지 않는다. 지금 삭제하면 "검증 파이프라인이 못 잡는 파손"이 생긴다 — Stage 1.5에서 데인 무성 실패와 같은 성격이라 피한다.
3. 클래스 제거는 죽은 테스트 정비와 한 몸이다. 별도 클린업으로 분리하는 것이 surgical하고 커밋 서사도 깔끔하다.

→ **이 단계 산출물: `ResponseDTO.java` 파일은 그대로 두고, `ApprovalController`의 참조만 0으로 만든다.**

> 실행 지시: 에이전트는 참조 확인용 검색 스크립트나 파일 삭제 명령을 **실행할 필요가 없다**(grep은 이미 사람이 수행). `approval/dto/ResponseDTO.java`는 **어떤 경우에도 삭제·수정하지 않는다.**

---

## 6. Success Criteria

| # | 항목 | 판정 |
|---|---|---|
| 1 | `ApprovalController`에 `ResponseDTO` 참조 0건 (반환형·본문 모두) | grep |
| 2 | `try` 블록은 `dounloadFile` 1곳만 남음 (insert/updateTemp의 try-catch 제거) | grep/육안 |
| 3 | 모든 성공 응답이 `ResponseMessage.success(...)`, status 200 | 육안 |
| 4 | `import ResponseMessage` 추가, 미사용 `HttpStatus` import 제거 | 육안 |
| 5 | `ResponseDTO.java` 파일 **삭제되지 않고 그대로 존재** (§5 — 삭제 보류) | git diff |
| 6 | `.\gradlew.bat compileJava` BUILD SUCCESSFUL | 빌드 |
| 7 | `.\gradlew.bat bootRun` 정상 기동 (`Started Application` 로그) | 부팅 |
| 8 | 성공 응답 JSON이 `{status:200, message, data}`로 **기존과 동일** | 수동 API |
| 9 | `src/test/**` **무변경** (이미 컴파일 실패 상태, 이번 단계에서 손대지 않음) | git diff |
| 10 | Stage 1/2 TODO 라인, 이모지/한글 로그, memberId 백도어, 채번/분기/파일조립 **무변경** | git diff |

---

## 7. 검증 (compileJava + bootRun + 수동 API — 3중)

> ⚠️ Stage 1.5 교훈: `compileJava` 통과만으로는 런타임 차이를 못 잡는다. 수동 API 필수.

### 자동
```powershell
cd C:\env\GitHub\INSIDER\LOG-IN-Refactoring\final
.\gradlew.bat compileJava
.\gradlew.bat bootRun   # Started Application 확인 후 Ctrl+C
```

### 수동 API (Postman) — 성공 응답 envelope 동일성이 핵심
아래 각 응답이 `{ "status":200, "message":"...", "data":... }` 형태로 **기존과 동일**한지 확인.

- [ ] 폼 목록 조회 `GET /approvals/forms`
- [ ] 특정 폼 조회 `GET /approvals/forms/{formNo}`
- [ ] 상세 조회 `GET /approvals/{approvalNo}`
- [ ] **목록 조회 — 임시저장함(`fg`)**, **결재대기함(`fg`)** (Stage 1.5에서 살린 조회, 회귀 없는지 재확인)
- [ ] 기안 작성(정상) `POST /approvals` → 200 + envelope 동일
- [ ] 재임시저장(정상) `PUT /approvals/{approvalNo}` → 200 + envelope 동일
- [ ] 결재 처리(승인/반려) `PUT /approvers/{approverNo}`
- [ ] 회수 `PUT /approvals/{approvalNo}/status`
- [ ] 삭제 `DELETE /approvals/{approvalNo}`
- [ ] 사원 조회 `GET /approvals/members/{memberId}`, `GET /approvals/members`

### 수동 API — 의도된 변화 확인 (회귀 아님)
- [ ] 기안/재임시저장 **실패**(예: 잘못된 바디로 예외 유발) → 응답이 `HTTP 500 + {status:500, code:"C999", ...}`
      (`ErrorResponse` shape)로 나가는지 확인. 기존 `HTTP 400 + {status:200,...}`에서 바뀌는 게 **정상**.
- [ ] (선택) 프론트 연동 확인 — 프론트가 붙어 있다면, 위 실패를 UI에서 유도했을 때 화면 백지화 없이
      에러 처리(모달/경고)가 뜨는지 한 번 눌러본다. 프론트가 과거 `status:200`에 의존해 성공/실패를
      분기했을 경우에만 영향이 있으며, 프론트 수정은 이번 범위 밖이다. 이상 발견 시 별도 백로그로 기록.

---

## 8. 작업 중 멈춰야 할 상황 (추측 말고 즉시 보고)

- 진행을 위해 `src/test/**`를 수정해야 한다고 판단되면 → 중단, 보고. (테스트는 이미 깨져 있고 이번 범위 밖이다. `ResponseDTO`도 삭제하지 않으므로 테스트를 건드릴 이유가 없어야 정상이다.)
- 수동 API에서 성공 응답 JSON이 기존과 **다르게** 나옴 → 중단, 보고 (예상: 동일).
- common 패키지(`ResponseMessage`/`ErrorResponse`/`GlobalExceptionHandler` 등) 수정이 필요해 보임
  → 범위 밖. 중단, 보고.
- try-catch 제거 시 checked exception 미처리로 컴파일 에러 → 보고
  (현재 catch는 `Exception`이고 대상 Service 메서드는 checked 미선언이라 문제없을 것).
- 범위 외(§3) 파일을 건드려야 진행된다고 판단되면 → 중단, 보고.

---

## 9. 작업 보고서

완료 후 `docs/refactoring/approval/reports/03-response-report.md` (UTF-8)에 저장:
- 수정 파일 + 변경 라인 수(+추가/-삭제) — `ApprovalController.java` 단일 파일이어야 정상
- `ResponseDTO.java`는 **삭제하지 않음**을 명시 (§5, 삭제 보류 근거 포함)
- `src/test/**` 무변경 확인
- 미사용 `HttpStatus` import 제거 여부
- compileJava / bootRun 결과
- Success Criteria 표(1~10) 대조 결과
- 수동 API는 사용자 담당이므로 "사용자 확인 대기"로 표기

---

## 10. 교차검증 반영 내역 (GPT/Gemini, 2026-07-28)

실행 전 교차검증 결과를 아래와 같이 처리했다. (판정: "보강 후 실행")

**반영**
- **[A/C] §4-1 지역 변수 할당 누락** — `selectApprovalList`의 `ResponseDTO response = ...` 우항만 바꾸면
  타입 미스매치 컴파일 에러. → §4-1에 ⚠️ 경고 + 인라인 반환 예시 추가. *(교차검증이 잡은 유일한 컴파일 직결 리스크)*
- **[C] §5 삭제/검색 실행 통제** — 에이전트가 불필요한 grep·삭제를 하지 않도록 "스크립트 실행 불필요,
  파일은 어떤 경우에도 삭제·수정 안 함" 실행 지시를 §5에 명문화.
- **[D] §7 프론트 연동 확인** — 에러 shape 변화(400→500)에 대한 프론트 UI 확인을 **선택 항목**으로 추가.

**톤 조정 후 반영 (지적을 축소 수용)**
- [D]의 "반드시/운영 안전 담보" 프레이밍은 채택하지 않음. 근거: 이 에러 경로는 서버 예외라는 예외적
  상황이고 기존 응답이 이미 자기모순(`400 + status:200`)이라 프론트가 정상 의존했을 가능성이 낮다.
  프론트 변경은 범위 밖이며 로컬 포트폴리오 개발 성격상 과한 게이트는 부적합. → "선택" 수준으로만 반영.

**기각/해당 없음**
- envelope 동일성, try-catch 안전성, T 타입 표, 범위 경계(dounloadFile→Stage 4)는 "문제 없음" 판정 —
  변경 없음.
- §5에 "grep 명령어가 있어 위험"이라는 지적은 현재 명세엔 해당 없음(명령어는 이미 제거된 상태). 다만
  방어적 명문화는 위 [C]로 수용.
