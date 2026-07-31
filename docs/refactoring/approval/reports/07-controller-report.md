# 단계 7: Controller 슬림화 — 작업 보고서

> 작업일: 2026-07-30
> 실행: Claude Code (Opus 5)
> 명세: `docs/refactoring/approval/tasks/07-controller.md` (**v3** — D1~D15 확정본)
> 선행: 단계 1·1.5·2·3·4·5·6 완료·커밋·푸시 (`2446c73`(코드)·`43e3a61`(문서))
> **리팩토링 마지막 단계.** 종료 문서 `docs/refactoring/completed/approval-domain.md` 동봉(D12)

---

## 1. 변경 파일

| 파일 | 구분 | 라인 수 (+/-) | 커밋 |
|---|---|---|---|
| `approval/controller/ApprovalController.java` | 수정 | **253 → 178** (+18 / −93) | ① 코드 |
| `approval/service/ApprovalQueryService.java` | 수정 | 456 → **446** (+9 / −19) | ① 코드 |
| `src/test/.../approval/service/ApprovalServiceTest.java` | **삭제** | 476 → 0 | ② 잔해 |
| `src/test/.../approval/controller/ApprovalControllerTest.java` | **삭제** | 272 → 0 | ② 잔해 |
| **`approval/dto/ResponseDTO.java`** | **삭제** | **26 → 0** | ② 잔해 |
| `docs/refactoring/approval/test-suite-status.md` | 수정 | +34 / −4 | ③ 문서 |
| `docs/refactoring/approval/reports/07-controller-report.md` | 신규 | — | ③ 문서 |
| `docs/refactoring/completed/approval-domain.md` | 신규 | — | ③ 문서 |
| `AGENTS.md` | 수정 | +8 / −5 — §10-1(현재 단계·로드맵·완료 목록) **+ "작업 시작 시 규칙" 4번에 `compileTestJava` 추가** | ③ 문서 |

> ⚠ **명세 v3 §6 "수정/삭제" 목록 밖의 항목 3건은 전부 사용자 승인 예외다.**
>
> | 파일 | 근거 |
> |---|---|
> | `test-suite-status.md` | 삭제한 2파일을 기술한 문서라 그대로 두면 낡는다. 사용자가 정정본으로 직접 작성 |
> | `ApprovalQueryService`의 `int totalPage` 제거 | §5 "3지점"을 넘지만 **내 변경이 만든 orphan**이다 (§3 참조) |
> | `approval/dto/ResponseDTO.java` 삭제 | §6이 `dto/**`를 금지하지만 사용자 승인. 삭제 전 참조 0 확인 (§8) |
>
> 명세 파일(`07-controller.md`) 자체는 사용자 소관이라 내가 §6을 고치지 않았다 — 이 각주가 diff 리뷰용 근거다.

### git diff --stat

```
 AGENTS.md                                          |  13 +-
 docs/refactoring/approval/test-suite-status.md     |  38 +-
 .../approval/controller/ApprovalController.java    | 111 +----
 .../insider/login/approval/dto/ResponseDTO.java    |  26 --
 .../approval/service/ApprovalQueryService.java     |  28 +-
 .../controller/ApprovalControllerTest.java         | 272 ------------
 .../approval/service/ApprovalServiceTest.java      | 476 ---------------------
 7 files changed, 69 insertions(+), 895 deletions(-)
```

**위 3건 외에 §6 Scope 밖 파일은 diff에 없다.** `auth/**`·`ApprovalCommandService`·
`ApprovalFileService`·`ApprovalNoGenerator`·`enums/**`·`dto/**`의 나머지 8개·`entity/**`·
`repository/**`·`ErrorCode` **전부 무변경.**

---

## 2. Controller 라인 수 — 실측 (D4 · R10)

> **계측 기준**은 `completed/approval-domain.md` §1과 맞춘다 — **총 줄 수**(빈 줄 포함)가 주 지표,
> **비어있지 않은 줄 수**를 병기한다.

| 지표 | 총 줄 | 비어있지 않은 줄 |
|---|---|---|
| Before (단계 6 종료) | **253** | 178 |
| After (단계 7) | **178** | 130 |
| 감소 | **−75 (−29.6%)** | −48 (−27.0%) |
| 목표 (D4) | 140~155 | — |
| **판정** | **초과 23줄 (약 15%)** | (참고: 목표 구간 안) |

> **🔢 `178`이 두 뜻으로 등장한다 — 기준을 섞어 읽지 말 것.**
> **Before의 "비어있지 않은 줄"(178)과 After의 "총 줄"(178)이 우연히 같다.**
> 섞어 읽으면 "줄어든 것이 없다" 또는 "기준을 몰래 바꿨다"로 오독된다.
> 이 보고서가 Before/After로 비교하는 주 지표는 **총 줄 253 → 178**이다.
> (`completed/approval-domain.md` §1 ①에 같은 경고가 있다.)

**압축하지 않았다.** D4 v3가 "지표를 또 개정하지 않는다. 실측값을 그대로 보고하고 초과 시 사유만 적어라"로
지시했고, R10이 `@Tag` 삭제·줄바꿈 압축·컨트롤러 분할을 금지했다.

### 초과 사유 — 178줄의 구성

| 블록 | 줄 수 | 압축 가능성 |
|---|---|---|
| package·import·클래스 선언·필드·생성자 | 37 | 불가 (D4 추산과 동일) |
| `@Tag` 애노테이션 10개 | 10 | **삭제 금지** (R10) |
| 엔드포인트 12개 본문 + 시그니처 | ~125 | 본문은 이미 위임 1~3줄 |
| `getCurrentMemberId()` 헬퍼 + 주석 | 4 | 불가 |
| 클래스 닫는 괄호 | 1 | 불가 |

D4의 바닥값 추산(`37 + 105 + 4 ≈ 146`)과 실적의 차이 **약 30줄은 전부 빈 줄**이다.
원본 Controller는 **메서드 여는 중괄호 다음과 닫는 중괄호 앞에 빈 줄을 두는 스타일**이고,
엔드포인트 사이에 이중 빈 줄이 들어간 곳이 4군데 있다. 이 스타일을 **원문 그대로 보존**했다.
빈 줄을 걷어내면 130줄(비어있지 않은 줄 기준)로 목표 안에 들어오지만, 그것은
"숫자를 맞추기 위한 줄바꿈 압축"이라 R10이 금지한 행위다.

> **참고 — 계측 기준이 명세 안에서 엇갈렸다.**
> §3-1의 Controller `253줄`은 **총 줄 수**(빈 줄 포함)이고,
> §3-6의 `src/test` **`2개 파일 615줄`** 은 **비어있지 않은 줄 수**다
> (실측: 총 476+272=748줄 / 비어있지 않은 줄 390+225=**615줄** — §3-6 수치와 정확히 일치).
> 목표치 140~155가 빈 줄을 12줄로 가정해 산출된 것이 초과의 근본 원인이다.
> 기준은 `completed/approval-domain.md` §1에서 **총 줄 수 주 지표 + 비어있지 않은 줄 병기**로 고정했다.
> **이 보고서는 지표를 개정하지 않는다.**

---

## 3. 결함별 처리

### `[I]` `@RequestHeader("memberId")` 백도어 제거 (D1 = 분기 A) ★보안

L72(`selectApprovalList`)·L164(`insertApproval`)의 `@RequestHeader(value/name = "memberId",
required = false) String memberIdstr` 파라미터를 **제거**했고, 두 메서드의

```java
int memberId = 0;
if (memberIdstr == null) { ... SecurityContext ... } else { memberId = Integer.parseInt(memberIdstr); }
```

**fallback 분기를 분기째로** `int memberId = getCurrentMemberId();` 한 줄로 대체했다.
(`insertApproval`은 `approvalDTO.setMemberId(getCurrentMemberId());` 형태)

- **파라미터만 지우고 분기를 남기지 않았다** — 명세 §5 [I]의 명시 요구
- 프론트가 헤더를 보내더라도 Spring이 **조용히 무시**한다(400 아님) → 프론트 배포와 순서 의존성 없음
- 검색 확인 #3: approval 패키지 전체 `RequestHeader` **0건**

### `[H]` + `[A-확장]` 인증 추출 4곳 → 1곳

```java
//인증 정보에서 현재 로그인한 사원의 사번을 꺼낸다
private int getCurrentMemberId() {
    return Integer.parseInt(SecurityContextHolder.getContext().getAuthentication().getName());
}
```

- 패턴 3종(L79 `Authentication` 변수 + 헤더 fallback / L119 한 줄 축약 / L143 변수 경유 / L176 변수 + fallback)이
  **한 형태로 수렴**했다
- `Authentication` 지역 변수와 `int memberId = 0;` 초기화 전부 제거 →
  `import org.springframework.security.core.Authentication;` **도 제거**(내 변경이 만든 orphan)
- L118 `// TODO: Stage 7에서 제거` 주석 제거
- **인증 부재 처리를 만들지 않았다**(D6) — 필터가 도달 전에 401을 내는 불가 경로. **`AP012` 미사용**
- 헬퍼는 **Controller private 메서드**(D7). 위치는 클래스 최하단
- `[A-확장]`(회수 API 인증)은 Stage 1의 `Approval.withdraw(memberId)` + 이번 헬퍼 정리로 **닫혔다**

### `[J]` 로그 정리 (D10 · D14-a) — v3 §5 확정 범위

**Controller — 제거 12건 / 축약 2건**

| 원본 라인 | 내용 | 처리 |
|---|---|---|
| 44 | `log.info("폼 목록 조회 controller 들어왔다")` | 제거 (진입 추적) |
| 73 | `log.info("****컨트롤러 들어왔어")` | 제거 (진입 추적) |
| 80·88·144·150·177·185 | `log.info("memberId: "…)` / `log.info("현재 사용자 : "…)` | 제거 (인증 블록과 함께) |
| 97·103·106·167·238 | `System.out.println` **5건** | 제거 |
| 98 | `log.info("현재 pageNo : " + pageNo)` | 제거 (진입 추적) |
| 117·130·166·202 | 🎉 이모지 `log.info` | 제거 |
| 132 | `log.info("새로운 approval Form : "…)` | 제거 (진입 추적) |
| **155** | `log.info("결재 임시저장 수정 결과 성공: " + result)` | **축약** → `"결재 임시저장 수정 성공: " + result.getApprovalNo()` |
| **191** | `log.info("결재 기안 결과 성공: " + result)` | **축약** → `"결재 기안 성공: " + result.getApprovalNo()` |

DTO 덤프 2건을 **완전 제거하지 않고 결재번호만 남긴** 이유는 명세 §5의 근거 그대로다 —
`ApprovalCommandService`는 무변경(§6)인데 그쪽 로그는 전환·재저장·처리·삭제만 `approvalNo`를 찍고
**신규 기안 경로는 찍지 않는다.** 통째로 지우면 신규 기안의 결재번호가 로그 어디에도 남지 않는다.

제거한 노이즈 주석: `//기안자사번`, `//현재 사용자의 인증 정보 가져오기`,
`//인증 정보에서 사용자의 식별 정보 가져오기`.

**유지한 주석 2건** (명세 §5 지시): L189 `//채번, 임시저장 -> 기안 전환 판정, 결재선·참조선 구성은
서비스가 책임진다`, L240 `//fileSavepath 는 요청 형태 유지를 위해 받기만 하고 사용하지 않는다…`.
`//전자결재 상세 조회`·`//HttpHeaders 설정`도 노이즈 목록에 없어 원문 유지했다.

🚫 **유지한 로직 라인** (v3 §5 명시): `approvalDTO.setApprovalNo(approvalNo)` ·
`approvalDTO.setMemberId(...)` 는 **로그가 아니라 로직**이다. 제거하지 않았고, 헬퍼 도입으로
인자 표현만 바뀌었다(`setMemberId(getCurrentMemberId())`).

**QueryService — 로그 문장만. 로직은 한 줄도 바꾸지 않았다.**

| 원본 라인 | 내용 | 처리 |
|---|---|---|
| 251·270·282·361·363·366·368 | `System.out.println` **7건** | 제거 |
| 250 | `log.info("service 들어왔다 : selectApprovalList")` | 제거 (251 println과 동일 문구) |
| 269 | `log.info("*****서비스 들어옴 : memberId…")` | 제거 (270 println과 동일 문구) |
| **394** | **`log.info("memberDTO" + memberDTO)`** | **제거 — 정보 유출 차단 (D14-a)** |
| 224 | `log.info("마지막 " + status + " 날짜 : "…)` | **원문 유지** |
| 333 | `log.info("\nSERVICE (received) 결재대기 DTO 갯수…")` | **원문 유지** |

L394는 로그 정돈이 아니라 **비밀번호 유출 차단**이다. `MemberDTO.toString()`은 Lombok이 아니라
수동 작성이며 `", password='" + password + '\''`를 포함한다(§3-5). `GET /approvals/members/{memberId}`를
호출할 때마다 **평문 비밀번호가 로그로 출력**되고 있었다.

> **📌 정정 (2026-07-31, 보안 작업 A)** — 초판은 "**로그 파일에 적재**"라고 적었으나 사실이 아니다.
> 이 앱에는 **파일 appender가 없다** (`logback-*.xml` 부재, `application.yml`에 `logging.file.*` 없음).
> 유출 경로는 **콘솔/stdout**이며 디스크에 영속되지 않는다. **피해 크기 판단이 달라진다.**
> 같은 오기가 `completed/approval-domain.md:148`에도 있었고 함께 정정했다.
**응답 JSON의 password 필드는 건드리지 않았다**(D14-b — 범위 밖. 종료 문서 보안 권고 최우선 항목).

`ApprovalCommandService`의 기존 로그는 **무변경**(§6 금지).

### R7 — 미지원 `fg` 값 NPE 가드 (D2 = 2-b)

```java
        }

        //지원하지 않는 fg 값(receivedAll 포함)은 switch를 그냥 빠져나와 approvalPage가 null이다
        if (approvalPage == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        int totalPage = approvalPage.getTotalPages();
```

- `switch`의 **`case` 내부는 한 글자도 손대지 않았다.** `case "receivedAll"`의 빈 본문도 원문 그대로
- **`receivedAll`을 구현하지 않았다** (기능 추가 = 범위 밖)
- 기존 상수 `INVALID_INPUT_VALUE`(**C001**, 400) 재사용. **새 ErrorCode 없음** — `BusinessException`·
  `ErrorCode` import는 QueryService에 **이미 있어** 추가 import도 없다
- `case "received"`는 조기 `return`이라 **가드에 도달하지 않는다** → 결재 대기함 영향 없음(R3)

### `limit = 10` 정책 상수 이관 (D8) ★R4

**Controller와 QueryService를 같은 변경으로 처리했다.** 한쪽만 하면 `(Integer) null` 역참조로
목록 5종 전부 500이 된다.

| 파일 | 변경 |
|---|---|
| Controller | `condition.put("limit", 10);` **제거** |
| QueryService | 클래스 상수 `private static final int DEFAULT_PAGE_SIZE = 10;` **신설** |
| QueryService | `int limit = (Integer) condition.get("limit");` **제거** |
| QueryService | `PageRequest.of(pageNo, limit)` → `PageRequest.of(pageNo, DEFAULT_PAGE_SIZE)` |

`condition` Map의 나머지(`flag`/`title`/`direction`)와 `getApprovalList` 시그니처는 **불변**이다.

### `int totalPage` 미사용 지역 변수 제거 — **사용자 승인 예외** (§6 3지점 밖)

```java
-        int totalPage = approvalPage.getTotalPages();
         long total = approvalPage.getTotalElements();
```

`getApprovalList` 말미의 이 지역 변수는 **유일한 사용처가 이번에 지운
`System.out.println("Service last totalPage : " + totalPage)`** 였다.
즉 **내 로그 제거가 직접 만든 orphan**이다.

- `getTotalPages()`는 `Page`의 **순수 계산**(`totalElements` / `pageSize` 올림)이라 부수효과가 없다.
  호출을 없애도 **동작이 중립**이다
- **`long total`은 유지**했다 — `new PageImpl<>(approvalDTOList, sortedPageable, total)`에 쓰인다
- 명세 §5는 "QueryService는 로그 문장만, 로직은 한 줄도 바꾸지 않는다"로 3지점을 제한했으나,
  **사용자 승인으로 예외 처리**했다. 근거는 두 가지다 —
  ① `06-command-query-report.md` §8 관찰 6의 **선례**(이관이 만든 죽은 대입 1줄 제거),
  ② 전역 `CLAUDE.md` §3의 **"내 변경이 만든 orphan은 정리한다"** 규칙
- 이로써 QueryService 변경은 **3지점 + 승인 예외 1건**이 됐다

### `page` 파라미터 파싱 (D9)

`@RequestParam(name = "page", defaultValue = "0") String page` + `int pageNo = Integer.parseInt(page);`
→ `@RequestParam(name = "page", defaultValue = "0") int page`.
`direction`·`title`·`fg`는 **불변**.

### `dounloadFile` → `downloadFile` 개명 (R7)

**메서드명만 바꿨다.** `@GetMapping("/approvals/files")` 매핑 경로와 파라미터 3종
(`fileSavepath`·`fileSavename`·`fileOriname`)은 **불변** → 프론트 영향 없음.
리포 전체에 호출자가 없어(정의부 1곳뿐) 다른 수정이 필요 없었다.
`fileSavepath`를 받기만 하고 쓰지 않는 현행도 유지(Stage 6 결정).

### 깨진 `src/test` 2파일 삭제 (D15) — **별개 커밋 ②**

| 파일 | 총 줄 | 비어있지 않은 줄 |
|---|---|---|
| `ApprovalServiceTest.java` | 476 | 390 |
| `ApprovalControllerTest.java` | 272 | 225 |
| **합계** | **748** | **615** (§3-6 수치와 일치) |

**파손 시점 — 근거 문서 실물 확인 후 인용** (v3 §5 요구)

- **1차 근거**: `docs/refactoring/approval/test-suite-status.md` (2026-07-28 작성, **리포에 실존**).
  Stage 3 명세 작성 중 `compileTestJava`로 실측한 1차 기록이며, 파손 원인을
  **Stage 1**(`updateApprovalStatus(String)` → `(String, int)`)과
  **Stage 2**(`ApprovalDTO.approvalDate` `String` → `LocalDateTime`)로 특정한다.
  → **이 테스트는 Stage 1 시점부터 죽어 있었다.**
- **보강 근거**: `06-command-query-report.md` §8 관찰 1 ("Stage 5에서 제거된 `selectApprovalNo` 호출").
- **두 문서는 충돌이 아니라 시점 해상도 차이다.** 06 보고서는 Stage 5·6이 만든 파손만 본 것이고,
  test-suite-status.md가 Stage 1까지 거슬러 올라간 1차 기록이다.

처리 원칙: 컴파일조차 되지 않으므로 **검증 가치 0**. 자산을 버리는 게 아니라 잔해를 치운다.
**새 테스트는 쓰지 않았다**(spec Out of Scope). **결재 외 도메인 테스트 17개는 손대지 않았다.**

### `approval/dto/ResponseDTO.java` 물리 삭제 — **사용자 승인 예외** (§6 `dto/**` 금지 밖), 커밋 ②

Stage 3에서 프로덕션 참조가 0이 됐고, 위 테스트 2파일 삭제로 **리포 전체 참조가 0**이 됐다.
`test-suite-status.md` "그때 결정할 것" §2가 미뤄둔 결정을 이번에 닫았다.

**삭제 전 확인 — grep 원문**

```powershell
cd final
Get-ChildItem -Path .\src -Filter *.java -Recurse | Select-String -Pattern "ResponseDTO" -Encoding UTF8
```

```
src\main\java\com\insider\login\approval\dto\ResponseDTO.java:11:public class ResponseDTO {
src\main\java\com\insider\login\approval\dto\ResponseDTO.java:17:    public  ResponseDTO(){
src\main\java\com\insider\login\approval\dto\ResponseDTO.java:21:    public ResponseDTO(HttpStatus status, String message, Object data){
src\main\java\com\insider\login\survey\controller\SurveyController.java:7:import com.insider.login.survey.dto.SurveyResponseDTO;
src\main\java\com\insider\login\survey\controller\SurveyController.java:124:    public ResponseEntity<String> insertResponse(@RequestBody SurveyResponseDTO responseDTO) {
src\main\java\com\insider\login\survey\controller\SurveyController.java:128:        return ResponseEntity.ok().headers(headers).body(surveyService.insertResponse(responseDTO));
src\main\java\com\insider\login\survey\dto\SurveyResponseDTO.java:5:public class SurveyResponseDTO {
src\main\java\com\insider\login\survey\dto\SurveyResponseDTO.java:13:    public SurveyResponseDTO() {
src\main\java\com\insider\login\survey\dto\SurveyResponseDTO.java:16:    public SurveyResponseDTO(int memberId, int surveyAnswer) {
src\main\java\com\insider\login\survey\dto\SurveyResponseDTO.java:47:        return "SurveyResponseDTO{" +
src\main\java\com\insider\login\survey\service\SurveyService.java:6:import com.insider.login.survey.dto.SurveyResponseDTO;
src\main\java\com\insider\login\survey\service\SurveyService.java:120:    public String insertResponse(SurveyResponseDTO responseDTO) {
src\main\java\com\insider\login\survey\service\SurveyService.java:122:            SurveyResponse surveyResponse = modelMapper.map(responseDTO, SurveyResponse.class);
src\main\java\com\insider\login\webSocket\Cahtting\dto\EntRoomResponseDTO.java:9:public class EntRoomResponseDTO {
src\main\java\com\insider\login\webSocket\Cahtting\dto\RoomResponseDTO.java:11:public class RoomResponseDTO {
src\test\java\com\insider\login\survey\service\SurveyServiceTests.java:5:import com.insider.login.survey.dto.SurveyResponseDTO;
src\test\java\com\insider\login\survey\service\SurveyServiceTests.java:156:        SurveyResponseDTO responseDTO = new SurveyResponseDTO(surveyAnswerNo, memberId);
src\test\java\com\insider\login\survey\service\SurveyServiceTests.java:158:        String result = surveyService.insertResponse(responseDTO);
```

**판정: approval 쪽 참조는 정의부 3줄(11·17·21)뿐 → 삭제 가능.**

- **survey 도메인의 `SurveyResponseDTO`는 별개 클래스**다(`com.insider.login.survey.dto`). 무관·무변경
- ⚠ **지시에 없던 매칭 2건**: `webSocket/Cahtting/dto/EntRoomResponseDTO.java`·`RoomResponseDTO.java`.
  둘 다 **클래스명 접미사가 우연히 겹친 별개 클래스**의 선언 줄이며,
  `com.insider.login.approval.dto.ResponseDTO`를 import하는 줄은 **리포 전체에 0건**이다.
  → 삭제 게이트("approval 쪽 참조가 하나라도 있으면 중단")는 **통과**. 무변경

**삭제 후 재컴파일 — 원문**

```
> cd final; .\gradlew.bat compileJava
> Task :compileJava
BUILD SUCCESSFUL in 4s
1 actionable task: 1 executed
```

→ spec 성공 지표 **"자체 `ResponseDTO` 제거"가 파일 수준까지 닫혔다.**

---

## 4. 의도된 동작 변경 (성공 응답 JSON **구조·값** 불변 — 실패 경로와 위조 경로만)

| 지점 | Before | After |
|---|---|---|
| `memberId` 헤더로 **타인 사번 위조** | 헤더 값이 이기고 **남의 결재함이 열림** (06 §6-1 S8 실측: `received` 토큰 3건 vs 헤더 5건) | 헤더 **무시**, 토큰 사번으로만 조회 |
| 미지원 `fg` (`receivedAll`·`zzz`·빈 문자열) | NPE → **500 / C999** | **400 / C001** |
| `page=`(빈 문자열) | `NumberFormatException` → **500** | 바인딩 실패 → **400** |
| `GET /approvals/members/{id}` 로그 | **평문 비밀번호가 로그에 적재** | 로그 없음 |

> **정상 사용 경로의 응답은 값까지 동일하다.** 프론트는 `memberId` 헤더를 보내지 않으므로(§3-7)
> 위 1행은 실사용 경로에 영향이 없다. 2·3행은 원래도 실패였고 상태 코드만 정확해졌다.
> **응답 JSON의 `password` 필드는 그대로 있다**(D14-b — 이번 범위 밖).

---

## 5. 빌드 · 부팅 결과

```
> cd final; .\gradlew.bat compileJava
> Task :compileJava
BUILD SUCCESSFUL in 9s
1 actionable task: 1 executed
```

```
> cd final; .\gradlew.bat bootRun
2026-07-30T11:21:39.137+09:00  INFO 4160 --- [restartedMain] o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port 8080 (http) with context path ''
2026-07-30T11:21:39.149+09:00  INFO 4160 --- [restartedMain] com.insider.login.Application            : Started Application in 11.158 seconds (process running for 11.821)
```

포트 8080은 기동 전 비어 있었고, 확인 후 프로세스를 종료해 **8080을 반납**했다.

**기동 로그 점검 (S6 자동 확인분)**
- 서러게이트 페어(이모지) **0건**
- `"들어왔"` 진입 추적 문구 **0건**
- 한글 로그·주석 **인코딩 깨짐 없음** (R6 — 편집한 파일을 육안 재확인)

### `compileTestJava` — P8 삭제 후

```
> cd final; .\gradlew.bat compileTestJava
> Task :compileTestJava
BUILD SUCCESSFUL in 4s
3 actionable tasks: 1 executed, 2 up-to-date
```

`ResponseDTO` 삭제 이후 다시 돌렸을 때 Gradle이 `UP-TO-DATE`로 넘겨(사용되지 않는 클래스 제거는
컴파일 회피 대상이라 ABI가 바뀌지 않는다) **실제 재컴파일이 아니었으므로, 전체 강제 재실행으로 확정**했다.

```
> cd final; .\gradlew.bat compileTestJava --rerun-tasks
> Task :compileJava
Note: ...\auth\config\WebSecurityConfig.java uses or overrides a deprecated API.
Note: Some input files use unchecked or unsafe operations.
> Task :compileTestJava
BUILD SUCCESSFUL in 8s
3 actionable tasks: 3 executed
```

> 위 `Note:` 2줄은 **`auth/**`의 기존 경고**이며 이번 변경과 무관하다(무변경 파일).

**성공 기준 "`compileTestJava` 실패 → 통과" 달성.**
잔존 테스트 **17개 파일**(announce·calendar·commute·leave·note·notice·proposal·survey·`ApplicationTests`)은
**별개 사유로 깨져 있지 않았다.** 손대지 않았다.

### 검색 확인 8종 (07-controller.md §11 원문)

```powershell
cd final
$approval   = Get-ChildItem -Path .\src\main\java\com\insider\login\approval -Filter *.java -Recurse
$controller = Get-ChildItem -Path .\src\main\java\com\insider\login\approval\controller -Filter *.java -Recurse
```

| # | 패턴 | 대상 | 기준선 | 결과 |
|---|---|---|---|---|
| 1 | `System\.out\.println` | approval | 12 | **0건** ✅ |
| 2 | `[\uD800-\uDBFF]` (서러게이트) | approval | 7줄 | **0건** ✅ |
| 3 | `RequestHeader` | approval | 2 | **0건** ✅ |
| 4 | `SecurityContextHolder` | approval | 5 (import 1 + 사용 4) | **2건 = import 1 + 사용 1** ✅ |
| 5 | `dounloadFile` | approval | 1 | **0건** ✅ |
| 6 | `put\("limit"` | controller | 1 | **0건** ✅ |
| 7 | `AP012` | `src/main/java` | 0 | **0건** ✅ |
| 8 | `ApprovalService` | `src` 전체 (P8 후) | 24 | **0건** ✅ |

**#4 주석**: §11 v3가 명시한 대로 `SecurityContextHolder`는 **import 줄도 매칭**되므로 2줄이 정상이다.
성공 기준의 "1곳"은 **사용 지점 1곳**을 뜻하며, 그 1곳이 `getCurrentMemberId()` 내부다.

**#1·#2 주석 (§3-3 v3)**: 이모지 7줄과 `println` 12건은 **별개 집합이 아니다.**
Controller 103·167·238 **3줄이 교집합**(이모지를 품은 `println`)이다. 두 수를 합산하면 안 된다.
제거된 실제 라인 수는 12 + 7 − 3 = **16줄**이다.

> ⚠ `compileJava`·`bootRun` 통과는 **아무것도 증명하지 않는다**(단계 1.5).
> **이번 단계는 특히 그렇다** — 인증 경로 변경은 컴파일·기동에 전혀 드러나지 않는다(R11).
> **아래 §6 수동 검증이 이 단계의 본체다.**

---

## 6. 📋 수동 검증 체크리스트 (사용자 담당 — 07-controller.md §11 원문)

- [x] **S0. `[I]` 사칭 차단** ★핵심 보안 검증
      사번 **X**의 토큰으로, `memberId` 헤더에 **타인 사번 Y**를 실어 `GET /approvals?fg=given` 호출
      → **X의 목록이 반환**되어야 한다. Y의 목록이 나오면 **제거 실패.**
      `POST /approvals`도 동일하게 확인 → 기안자가 **X**로 기록되어야 한다.

- [x] **S1. 전 API 토큰 전용 재검증** ★**이 단계의 본체**
      `memberId` 헤더를 **일절 보내지 않고** 12개 엔드포인트 전부 실행. Stage 6 S8과 **동일한 JSON 구조**.
      - 양식 목록 / 특정 양식 / 상세 조회 / 사원 조회 / 전 사원 조회 / 파일 다운로드
      - 목록 5종 (`given` `tempGiven` `received` `receivedRef` `receivedAll`)
      - 기안(무첨부·첨부 2건) / 재임시저장 / 회수 / 결재 처리(승인·반려) / 삭제
      - 최종승인일(`finalApproverDate`) · 대기자(`standByApprover`) 표시

- [x] **S2. R7 가드** — 양방향
      - `fg=receivedAll` → **400 / C001** (현행 500/C999)
      - `fg=zzz` → **400 / C001**
      - `fg` 누락 → 400 (기존 `@RequestParam` 필수)

- [x] **S3. 목록 정상 경로 비회귀 + `limit` 이관 확인** ★
      `given` / `tempGiven` / `received` / `receivedRef` **4종 전부 200**,
      **페이지 크기 10 · `totalPages`·`totalElements`가 Stage 6와 동일**.
      하나라도 500이면 R4(`limit` 이관 한쪽만 반영).

- [x] **S4. `page` 파라미터**
      - `page` 누락 → 0페이지
      - `page=2` → 정상
      - **`page=`(빈 문자열) → 400** (현행 500. 의도된 변경 — D9)

- [x] **S5. SecurityContext 전용 경로 비회귀**
      회수(`PUT /approvals/{no}/status`)·재임시저장(`PUT /approvals/{no}`)이 헤더 없이 정상 동작.
      회수 양방향도 재확인 — 아무도 처리 안 한 건 회수 **성공**, 결재자 1명 승인 후 회수 **400/AP009**.

- [x] **S6. `[J]` 로그** — 기동 후 로그 육안 확인
      - 콘솔에 **이모지·`System.out.println` 0건**
      - **`GET /approvals/members/{memberId}` 호출 후 로그에 password가 찍히지 않는지** ★(D14-a)
      - 한글 로그·주석이 **인코딩 깨짐 없이** 출력되는지 (R6)

- [x] **S7. 파일 다운로드** — 개명 후 `GET /approvals/files` 매핑 불변, 파일 내용 일치, 없는 파일 404/AP007

- [x] **S8. Stage 1.5 비회귀**
      결재 대기함·임시저장함 **검색어별 건수**가 Stage 6 S9와 동일 (조용한 0건 없음).

- [x] **S9. 사원 조회 응답 기록** (D14-b 근거 수집 — 고치지 않는다)
      `GET /approvals/members/{memberId}` 응답 JSON에 `password` 필드가 있는지, 값이 **평문인지 해시인지**
      기록. 종료 문서 보안 권고의 우선순위 근거.

> **S0·S1이 이 단계의 본체다.** 인증 경로를 바꿨으므로 자동 검증은 회귀를 전혀 잡지 못한다(R11).

---

## 6-1. 자동 수행 결과 (Claude Code 실행 — 2026-07-30)

실 개발 DB·실 서버(`bootRun`, 포트 8080)에서 수행했다. HTTP 도구는 `curl.exe`,
한글 본문은 **UTF-8(BOM 없이) 파일**을 `-F "approvalDTO=@dto.json;type=application/json"`으로 전달했다.
인증은 `Authorization: BEARER <token>` — 토큰은 `POST /login` 응답 **본문 `token` 필드**(동명의
`Authorization` 응답 헤더로도 온다)에서 얻었다.

| 항목 | 판정 | 한 줄 근거 |
|---|---|---|
| **S0** 사칭 차단 ★ | **통과** | 보조 토큰 + 주 계정 헤더 → **0건**(주 계정 27건 아님). 기안도 토큰 사번으로 기록 |
| **S1** 전 API 토큰 전용 ★ | **통과** | 12개 엔드포인트 전부 정상, 응답 키 17개 Stage 6과 동일 |
| **S2** R7 가드 | **통과** | `receivedAll`·`zzz`·빈 문자열 → **400/C001**, `fg` 누락 → 400/C003 |
| **S3** 목록 비회귀 + `limit` | **통과** | 4종 전부 200, **`size=10`**, `totalPages` 일관 (500 **0건** → R4 없음) |
| **S4** `page` 파라미터 | ⚠ **2건 예상과 다름** | `page=2` → **404/AP004**(기존 데이터), `page=` → **200**(명세 예측 400) — 아래 상세 |
| **S5** SecurityContext 전용 | **통과** | 회수 200 / **400·AP009** / **403·AP008** 3방향, 재임시저장 200 |
| **S6** `[J]` 로그 | **통과** | 이모지 0 · `들어왔` 0 · 옛 println 문구 0 · **결재 경로 password 로그 0** |
| **S7** 파일 다운로드 | **통과** | `/approvals/files` 매핑 불변, 내용 일치(한글), 없는 파일 **404/AP007** |
| **S8** Stage 1.5 비회귀 | **통과** | 검색어별로 건수가 정확히 갈림, 조용한 0건 없음 |
| **S9** password 기록 | **완료** | 필드 **존재**, **bcrypt 해시**(평문 아님) — 아래 상세 |

### S0 — 사칭 차단 ★핵심 보안 검증

`GET /approvals?fg=given&page=0&title=&direction=DESC` 4가지 조합:

| # | 인증 | `memberId` 헤더 | totalElements | 응답의 기안자 사번 |
|---|---|---|---|---|
| A | 주 계정 토큰 | 없음 | **27** | 240501629 |
| B | 보조 계정 토큰 | 없음 | **0** | — |
| C | 주 계정 토큰 | `123`(보조) | **27** | **240501629** ✅ 헤더 무시 |
| D | 보조 계정 토큰 | `240501629`(주) | **0** | — ✅ 헤더 무시 |

**D가 결정적 증거다.** 헤더가 이겼다면 27건이 나왔어야 한다.
Stage 6 §6-1 S8에서는 헤더가 이겨 **남의 목록이 그대로 반환**됐다(`received` 3건 vs 5건) — 그 경로가 닫혔다.

**기안(`POST /approvals`)** — 주 계정 토큰 + `memberId: 123` 헤더 + **본문 DTO의 `memberId`도 123**:

```
200  전자결재 기안 성공   결재번호 2026-con00011
기록된 기안자 memberId : 240501629  ← 토큰 사번
기안자 이름            : 이진아
```

**헤더와 본문 양쪽의 사번 위조가 모두 무시됐다.**

### S1 — 전 API 토큰 전용 재검증 (`memberId` 헤더 일절 없음)

| # | 엔드포인트 | 결과 |
|---|---|---|
| 1 | `GET /approvals/forms` | 200 · 양식 7건 · 키 `formNo,formName,formShape` |
| 2 | `GET /approvals/forms/con` | 200 · `경조금 지급 신청서` |
| 3 | `GET /approvals/{no}` 상세 | 200 · **최상위 키 17개** · 결재선 order **0-1-2-3 오름차순** · 첨부 2건 |
| 3b | 상세 (PROCESSING) | 200 · `standByApprover='김지환'` · `finalApproverDate=''` |
| 3c | 상세 (REJECTED) | 200 · `finalApproverDate` 채워짐 · `rejectReason` 반영 (R6-b 미발동) |
| 3d | 상세 (없는 번호) | **404 / AP001** |
| 4 | `GET /approvals` 목록 5종 | `given`·`tempGiven`·`received`·`receivedRef` 200 / `receivedAll` **400/C001**(D2 의도) |
| 5 | `PUT /approvals/{no}/status` 회수 | 200 · `WITHDRAWN` |
| 6 | `PUT /approvals/{no}` 재임시저장 | 200 · **번호 불변** · `TEMP_SAVED` 유지 · 첨부 교체 · 기안자 = 토큰 사번 |
| 7 | `POST /approvals` 무첨부 | 200 · `2026-con00012` · `attachment: []` |
| 7b | `POST /approvals` 첨부 2건 | 200 · `2026-con00013` · `_f001`·`_f002` |
| 8 | `PUT /approvers/{no}` 승인 | 200 · 응답 키 10개 · `approverStatus=APPROVED` · `approverDate` **변경 후 값** |
| 8b | `PUT /approvers/{no}` 반려 | 200 · 결재 `REJECTED` · `rejectReason` 반영 |
| 9 | `DELETE /approvals/{no}` | 200 · `data=true` → 이후 상세 **404/AP001** |
| 10 | `GET /approvals/members/{id}` | 200 · `name=이진아` `depart=인사팀` |
| 11 | `GET /approvals/members` | 200 · 사원 90명 |
| 12 | `GET /approvals/files` | 200 · 내용 일치 (S7) |

> ⚠ **S1의 검증 범위 각주**: 승인·반려(#8·#8b)는 **주 계정 토큰으로만** 호출했다.
> **지정 결재자 본인 여부의 검증은 §11 S0~S9 범위 밖이었다.**
> 이 각주가 없으면 "결재 처리 API의 권한까지 검증됐다"로 읽힐 여지가 있다.
> → 범위 밖 추가 확인에서 별도로 재현했다(아래 **§6-2**).

상세 응답 최상위 키(Stage 6과 동일):
`approvalNo, memberId, approvalTitle, approvalContent, approvalDate, approvalStatus, rejectReason,
formNo, formName, departName, name, positionName, attachment, approver, referencer,
finalApproverDate, standByApprover`

**`finalApproverDate`·`standByApprover` 모두 표시된다.** 승인 완료 시 `standByApprover`가 비워지고
`finalApproverDate`가 채워지는 동작도 확인했다(`2026-con00012`: 승인 직후 `2026-07-30 14:13:49`).

> **한글 상태값 수용 확인**: `"처리 중"`·`"임시저장"`을 UTF-8 본문으로 보내 전부 200이 나왔고,
> `"임시저장"`은 `2026-ims00009`로 **`ims` 채번**됐다.
> → `ApprovalStatus.description`·`from()` 한글 매칭이 살아 있다(§6 절대 금지 항목 준수 확인).

### S2 — R7 가드

| 요청 | 상태 | 코드 |
|---|---|---|
| `fg=receivedAll` | **400** | **C001** (현행 500/C999에서 변경) |
| `fg=zzz` | **400** | **C001** |
| `fg=`(빈 문자열) | **400** | **C001** |
| `fg` 누락 | **400** | C003 (`@RequestParam` 필수 — 기존 동작) |

`receivedAll`은 **구현하지 않았고**, 실패를 200으로 위장하지 않았다.

### S3 — 목록 정상 경로 + `limit` 이관 ★R4

| `fg` | 상태 | totalElements | **size** | totalPages | number |
|---|---|---|---|---|---|
| `given` | 200 | 28 | **10** | 3 | 0 |
| `tempGiven` | 200 | 8 | **10** | 1 | 0 |
| `received` | 200 | 3 | **10** | 1 | 0 |
| `receivedRef` | 200 | 0 | **10** | 0 | 0 |

**500이 하나도 없다 → R4(`limit` 이관 한쪽 누락) 없음.** 페이지 크기 **10**이 QueryService 상수에서
정상 공급된다. 쓰기 시나리오 수행 후 재확인에서도 `given` 33건 / `totalPages` 4로 **일관**했다.

> Stage 6 S8의 절대 건수(23건 등)와 비교하지 않았다 — 그 뒤 검증 데이터가 늘었다.
> 본 항목은 **200 여부·페이지 크기·`totalPages` 정합**을 본다.

### S4 — `page` 파라미터 ⚠ **예상과 다른 결과 2건 (고치지 않음)**

| 요청 | 명세 §11 예상 | **실제** | 판정 |
|---|---|---|---|
| `page` 누락 | 0페이지 | **200 · `number=0`** | ✅ |
| `page=2` | 정상 | **404 / AP004** | ⚠ 아래 ① |
| `page=`(빈 문자열) | **400** | **200 · `number=0`** | ⚠ 아래 ② |

**① `page=2` → 404/AP004 — Stage 7과 무관한 기존 데이터 문제**

원인을 단건까지 특정했다. 전체 결재를 개별 상세 조회로 훑은 결과
**`2024-con00002` 하나만 404/AP004**를 낸다(나머지 30건 전부 200).
이 결재가 `given` 정렬의 마지막 페이지에 들어 있어 **페이지 전체가 404**가 된다.

```
DESC page=0 → 200 (10건)
DESC page=1 → 200 (10건)
DESC page=2 → 404 / AP004     ← 2024-con00002 포함
ASC  page=0 → 404 / AP004     ← 같은 구간(가장 오래된 10건)
```

`getApproval`에서 AP004는 두 곳에서만 던져진다 — **APPROVED인데 결재자 목록이 비었거나**,
**REJECTED인데 `REJECTED` 상태 결재자가 없는** 경우다. 즉 `2024-con00002`는 **깨진 레거시 데이터**다.

**Stage 7 회귀가 아닌 근거 3가지**
1. Stage 7은 `getApproval`을 **한 줄도 바꾸지 않았다**(§1 diff — 변경은 로그·null 가드·`DEFAULT_PAGE_SIZE`뿐)
2. 페이지 크기는 Stage 6에서도 10이었다(Controller가 `condition.put("limit", 10)`) → **같은 8건이 같은 페이지에 있었다**
3. `06-command-query-report.md` §4가 **R6-b**로 이미 예고했다 — *"상신함에 데이터가 깨진 반려 결재가
   하나라도 있으면 목록이 통째로 죽는다. 다만 현행도 500으로 동일하게 죽으므로 회귀는 아니다."*
   Stage 6 S8은 **page 0만** 확인해 이 데이터에 닿지 않았고, 이번에 `page=2`를 열어 **처음 드러났다.**

→ **범위 밖(R9). 고치지 않았다.** 종료 문서 이월 목록의 R6-b 항목에 해당한다.

**② `page=`(빈 문자열) → 400이 아니라 200**

D9는 "`Integer.parseInt("")` → 500이 **바인딩 실패 → 400**이 된다"고 예측했으나,
Spring은 **빈 문자열을 `defaultValue = "0"`으로 대체**해 **200 · 0페이지**를 반환한다.

- **500은 확실히 사라졌다**(D9의 목적 달성). 다만 도착지가 400이 아니라 200이다
- 실패 경로가 아니라 **정상 경로가 됐으므로 사용자 영향은 없다**
- 프론트는 항상 `page=0`을 실어 보내므로(§3-7 실측) 실사용 경로와 무관하다
- **명세의 예측이 빗나간 것이며 코드 결함이 아니다.** 고치지 않았고, §11 S4 문구는 사용자 판단 사항이다

### S5 — SecurityContext 전용 경로 (헤더 없이)

| 시나리오 | 결과 |
|---|---|
| 회수 — 아무도 처리 안 한 건 (`2026-con00014`) | **200** · `WITHDRAWN` |
| 회수 — 결재자 1명 승인 후 (`2026-con00015`) | **400 / AP009** `이미 처리된 결재는 회수할 수 없습니다.` |
| 회수 — 본인 아닌 사람 (보조 토큰, `2026-con00011`) | **403 / AP008** `기안자 본인만 결재를 회수할 수 있습니다.` |
| 재임시저장 (`PUT /approvals/2026-ims00009`) | **200** · 번호 불변 · `TEMP_SAVED` 유지 · 기안자 = 토큰 사번 |

기안자 `_apr000` 제외 로직도 그대로 동작한다(첫 줄이 200이므로 — Stage 6 R9 회피 유지).

### S6 — `[J]` 로그 (기동 로그 29,045줄 전수 검사)

| 패턴 | 건수 | 판정 |
|---|---|---|
| 서러게이트 페어(이모지) | **0** | ✅ |
| `들어왔` | **0** | ✅ |
| `Service last totalPage` / `new PageImpl` / `totalPage :` | **0** | ✅ |
| `현재 pageNo` / `조회성공` / `service 들어왔다` / `서비스 들어옴` | **0** | ✅ |
| **`memberDTOMemberDTO`** (제거된 `log.info("memberDTO" + memberDTO)`의 출력 형태) | **0** | ✅ **D14-a** |

**남기기로 한 로그는 정상 출력된다**: `결재 기안 성공` 7건 · `결재 임시저장 수정 성공` 1건 ·
QueryService L224 `마지막 … 날짜` 241건 · L333 `결재대기 DTO 갯수` 13건.
한글이 **깨짐 없이** 출력된다(R6) — 예: `마지막 PROCESSING 날짜 :`.

**결재 경로 password 격리 검증** — `GET /approvals/members/{id}` + `GET /approvals/members` 호출 전후로
로그 라인을 잘라 비교했다(101줄 증가).

```
신규 101줄 중  bcrypt 해시 : 0건   ← ★ D14-a 검증
```

> ⚠ **범위 밖 관찰 (고치지 않았다)**: 로그 전체에는 bcrypt 해시가 **20건** 남아 있다.
> 전부 **라인 250~428 구간**, 즉 **로그인 5회 시점**에만 몰려 있고 이후 29,000줄에는 0건이다.
> 출처는 `MemberService:50`(`찾은 member는`) · `CustomAuthSuccessHandler:27`(`member에 대한 정보`) ·
> `TokenUtils:77`(`in Token Utils`) — **전부 `auth/**`·`member/**`로 §6 금지·범위 밖**이다.
> 결재 도메인이 찍던 것은 사라졌다. **로그인 경로의 동일 결함은 별도 작업으로 다뤄야 한다.**

### S7 — 파일 다운로드

```
GET /approvals/files?fileSavepath=…&fileSavename=…&fileOriname=att1.txt   → 200
Content-Disposition: attachment; filename="att1.txt"
Content-Type: text/plain;charset=UTF-8
```

- **매핑 경로 `/approvals/files` 불변** · 파라미터 3종 불변 (메서드명만 `downloadFile`로 개명 — R7)
- 파일 내용 **완전 일치**(한글 포함: `S1 첨부파일 A - 한글 내용 확인용`)
- 없는 파일 → **404 / AP007**

### S8 — Stage 1.5 비회귀 (검색 판별력)

절대 건수가 아니라 **검색어에 따라 건수가 갈리는지**를 봤다.

| 조회 | (없음) | 검색어별 |
|---|---|---|
| 결재 대기함 `received` | 3 | `연장근무` **2** · `금일` **2** · `Stage4` **1** · `ZZZZ` 0 |
| 임시저장함 `tempGiven` | 8 | `Stage4` **4** · `S4` **2** · `첨부교체` **1** · `연장근무` **1** · `ZZZZ` 0 |
| 상신함 `given` | 33 | `S1` **6** · `사칭` **1** · `ZZZZ` 0 |

**한글·영문 검색어 모두 정확히 판별된다. 조용한 0건 없음**(무매칭은 `ZZZZ`뿐).

### S9 — 사원 조회 응답의 password (D14-b 근거 수집 — 고치지 않았다)

| 확인 | 결과 |
|---|---|
| `password` 필드 존재 | **있다** (`GET /approvals/members/{id}`) |
| `GET /approvals/members`(전 사원)에도 실리는지 | **실린다** |
| 값 길이 | 60 |
| bcrypt 접두사(`$2a$`/`$2b$`/`$2y$`) | **일치** |
| 로그인 평문과 동일한가 | **아니다** |
| **판정** | **bcrypt 해시 (평문 아님)** |

> 응답 원문은 이 보고서에 싣지 않는다. 판정만 기록한다.

**종료 문서 §4 🔴 "(S9 결과 기입)" 자리에 넣을 한 줄 제안** — *그 파일은 수정하지 않았다*:

> S9 실측(2026-07-30): 값은 **평문이 아니라 bcrypt 해시**(60자, `$2a$` 접두사)다.
> 즉시적인 계정 탈취 위험은 낮으나, 해시는 오프라인 대입 공격의 대상이고 인증과 무관한 조회 API가
> 전 사원의 해시를 반환한다는 점에서 **여전히 제거 대상**이다(우선순위는 🔴 유지).

---

## 6-2. 범위 밖 추가 확인 — `PUT /approvers/{approverNo}` 신원 미검증 🔴

> **§11 S0~S9 밖이다.** 사용자 승인을 받아 **재현·기록만** 했다. **코드는 고치지 않았다.**
> 계기: S1의 승인·반려를 주 계정 토큰으로만 호출했다는 사실을 사용자가 지적했다.
> 기존 로그(주 계정 토큰으로 241811 지정 라인 승인 → 200)는 *"주 계정이 그 결재의 기안자"* 라는
> 반박 여지가 있어, **기안자도 결재자도 참조자도 아닌 제3자**로 다시 세웠다.

### 준비 — 제3자 지위 확정

주 계정(240501629)이 기안, 지정 결재자는 **241811 한 명**. 결재선 2줄이 생성된다.

```
2026-con00017_apr000  order=0  memberId=240501629  APPROVED   ← 기안자 자동 등록
2026-con00017_apr001  order=1  memberId=241811     PENDING    ← 지정 결재자
2026-con00018 도 동일
```

호출자는 **보조 계정(123)** 이다. 무관함을 세 방향으로 확인했다.

| 확인 | 결과 |
|---|---|
| 보조 계정 `given`(내가 기안) | **0건** → 기안자 아님 |
| 보조 계정 `receivedRef`(내가 참조자) | **0건** → 참조자 아님 |
| X1·X2의 `approver[].memberId` | `[240501629, 241811]` → **123 없음** → 결재자 아님 |
| 보조 계정 `received` 1건의 정체 | `2026-abs00001`(무관한 기존 결재) — X1·X2 아님 |

### ① 제3자 승인 → **성공. 문서가 완결됐다**

```
PUT /approvers/2026-con00017_apr001
Authorization: Bearer ‹생략›            ← 보조 계정(123) 토큰
{"approverStatus":"APPROVED"}

HTTP 200   "전자결재APPROVED처리 완료"
응답 approverStatus=APPROVED  approverDate=2026-07-30 15:29:32  memberId=241811
```

**DB 반영 확인** (주 계정으로 상세 조회):

| 확인 항목 | 결과 |
|---|---|
| `_apr001` 상태 | **APPROVED** (PENDING → 변경됨) |
| **결재 전체 상태** | **`PROCESSING` → `APPROVED`** ★ |
| `finalApproverDate` | `2026-07-30 15:29:33` 채워짐 |
| `standByApprover` | `''` 로 비워짐 |
| **기록된 처리자** | **`memberId=241811`** — 실제 호출자 123이 아니라 **지정 결재자 이름으로 남는다** |

→ **제3자가 남의 문서를 완결시킬 수 있고, 감사 로그에는 결백한 결재자가 승인한 것으로 남는다.**

### ② 제3자 반려 → **성공. 진행 중인 결재를 즉사시켰다**

```
PUT /approvers/2026-con00018_apr001
Authorization: Bearer ‹생략›            ← 보조 계정(123) 토큰
{"approverStatus":"REJECTED","rejectReason":"제3자가 임의로 반려함"}

HTTP 200   "전자결재REJECTED처리 완료"
```

| 확인 항목 | 결과 |
|---|---|
| 결재 전체 상태 | **`PROCESSING` → `REJECTED`** |
| `rejectReason` | **`'제3자가 임의로 반려함'` 그대로 저장** |
| `_apr001` | REJECTED (`memberId=241811`로 기록) |

반려는 순서와 무관하게 즉시 `markAsRejected`를 호출하므로 **결재선 어느 위치든 한 번의 요청으로
문서를 끝낼 수 있다.** 되돌리는 API는 없다.

### ③ 실제로 걸리는 가드는 두 개뿐 — 둘 다 신원과 무관

| 요청 | 결과 | 걸린 가드 |
|---|---|---|
| 이미 처리된 `_apr001`을 다시 승인 | **400 / AP005** | **상태 전이** 검증 |
| `2026-con00018_apr009` (없는 순번) | **404 / AP004** | **존재** 검증 |
| `2026-con99999_apr001` (없는 결재) | **404 / AP004** | **존재** 검증 |

**"당신이 이 결재의 지정 결재자인가"를 묻는 가드는 없다.**

### ④ 왜 없는가 — 검증할 지점 자체가 없다

```java
// ApprovalController.java:126  — 인증을 추출하지 않는다. getCurrentMemberId() 호출 없음
approvalCommandService.processApprover(approverNo, approverDTO)

// ApprovalCommandService.java:185  — 시그니처에 인증 정보가 없다
public ApproverDTO processApprover(String approverNo, ApproverDTO approverDTO) {
    Approver approver = approverRepository.findByApproverNo(approverNo)   // 존재 검증
            .orElseThrow(() -> new BusinessException(ErrorCode.APPROVER_NOT_FOUND));
    ...
```

회수(`withdraw(approvalNo, memberId)`)는 Stage 1에서 `memberId`를 받도록 바뀌어 AP008로 본인을 확인하지만,
**결재 처리는 그 대상이 아니었다.** spec `[A-확장]`이 **회수 API에만** 적용되고 결재 처리에는
적용되지 않았기 때문이다.

### ⑤ 심각도를 올리는 요소 — 식별자가 완전히 추측 가능하다

- `approverNo` = **`{결재번호}_apr{순번:3자리}`** — 규칙이 고정이다 (`2026-con00017_apr001`)
- 결재번호 = **`{연도}-{양식3자}-{5자리 순번}`** — 양식 코드는 `GET /approvals/forms`로 **누구나 조회 가능**하고(7종), 순번은 1부터 연속이다
- 즉 **유효한 토큰 하나만 있으면 전사의 `approverNo`를 열거**할 수 있다. 목록 조회 권한조차 필요 없다
- 존재하지 않는 번호는 404/AP004, 이미 처리된 건은 400/AP005로 **응답이 갈리므로 열거 결과를 구분**할 수 있다

### ⑥ Stage 7과의 관계

**이번 단계가 만든 결함이 아니다.** Stage 7은 `ApprovalCommandService`를 무변경으로 두었고(§6 금지),
Controller의 이 메서드에는 원래도 인증 추출이 없었다(§3-2의 인증 추출 4곳에 포함되지 않는다).
**Stage 7이 닫은 것은 조회·기안 경로의 `[I]`이며, 쓰기 경로의 같은 계열 결함이 남아 있다.**

→ **종료 문서 §4 🔴에 신규 항목으로 올려야 한다.**

**`completed/approval-domain.md` §4 🔴에 넣을 문구 제안** — *그 파일은 수정하지 않았다*:

> **`PUT /approvers/{approverNo}` 결재 처리의 신원 미검증** — 유효한 토큰만 있으면 **기안자도 결재자도
> 참조자도 아닌 제3자**가 남의 결재를 승인·반려할 수 있다(2026-07-30 실측: 보조 계정으로 승인 시 결재가
> `APPROVED`로 완결되고, 반려 시 `REJECTED`로 즉시 종료. 처리자는 **지정 결재자 사번으로 기록**된다).
> Controller가 인증을 추출하지 않고 `processApprover(approverNo, approverDTO)` 시그니처에도 인증 정보가
> 없어 **검증할 지점 자체가 없다.** `approverNo`가 `{결재번호}_apr{순번}`으로 완전히 추측 가능해 열거도 쉽다.
> spec `[A-확장]`이 회수 API에만 적용되고 결재 처리에는 적용되지 않은 결과다.
> → `withdraw(approvalNo, memberId)` 선례대로 **호출자 사번을 받아 지정 결재자 본인인지 확인**해야 한다.

---

### 이번 검증으로 개발 DB에 생성된 결재 (정리 필요 시 참고)

| 결재번호 | 용도 | 최종 상태 |
|---|---|---|
| `2026-con00011` | S0 사칭 차단 (기안) | PROCESSING |
| `2026-con00012` | S1 무첨부 기안 → 승인 | **APPROVED** |
| `2026-con00013` | S1 첨부 2건 기안 → S7 다운로드 | PROCESSING (**첨부 2건 디스크 잔존**) |
| `2026-ims00009` | S1 임시저장 → 재임시저장 → 삭제 | **삭제됨** |
| `2026-con00014` | S5 회수 (a) | **WITHDRAWN** |
| `2026-con00015` | S5 회수 (b) | PROCESSING (결재자1 APPROVED) |
| `2026-con00016` | S1 반려 처리 | **REJECTED** |
| `2026-con00017` | **§6-2 ① 제3자 승인** | **APPROVED** (제3자가 완결시킴) |
| `2026-con00018` | **§6-2 ② 제3자 반려** | **REJECTED** (제3자가 반려함) |

디스크(`C:/login/upload`)에는 **`2026-con00013`의 첨부 2건**이 남아 있다.
`2026-ims00009`의 첨부 1건은 결재 삭제와 함께 정리됐다.

### 임시 파일 정리

검증용으로 만든 파일은 **전부 스크래치패드(리포 밖)** 에 두었고 종료 시 삭제했다 —
로그인 본문 JSON, 토큰 파일 2개, 요청 DTO JSON, 첨부용 `att1.txt`·`att2.txt`, 응답 임시 파일.
`bootrun.log`(리포 루트, **`.gitignore` 미등록**)도 삭제했다. → §5 참조
**§6-2 추가 확인 때 서버를 재기동하며 같은 파일들이 다시 생겼고, 종료 후 동일하게 전부 삭제했다.**
두 차례 모두 `git status`에 임시 파일이 나타나지 않는 것을 확인했다.

> ⚠ `bootrun.log`는 리포 루트에 생기는데 **`.gitignore`에 없다.** 앞으로 같은 방식으로 로그를 남길 때는
> 커밋 전에 삭제하거나 `.gitignore`에 추가해야 한다(이번 단계에서는 `.gitignore`를 고치지 않았다 — 범위 밖).

---

## 7. 범위 외 — 손대지 않은 항목 (원문 유지 확인)

- **`auth/**` 전체 미개봉** — `JwtAuthorizationFilter`(특히 `roleLessList`)·`WebConfig`·
  `JwtTokenInterceptor`·`WebSecurityConfig`·`TokenUtils`. **R2에 따라 수정 제안도 하지 않는다**
- **`enums/ApprovalStatus.java`의 `description`·`from()` 한글 매칭** — 무변경 (제거하면 기안이 전부 실패)
- **`ApprovalCommandService`** — 무변경. `ApprovalFileService`·`ApprovalNoGenerator` — 무변경
- **`dto/**`의 나머지 8개**(`MemberDTO` 포함)·**`entity/**`·`repository/**`·`ErrorCode`** — 무변경
  (`ResponseDTO.java`만 사용자 승인으로 삭제 — §3)
- **`[K]` 재시도**(D3) — 리팩토링 종료 후 독립 단계
- **`receivedAll` 실제 구현**(기능 추가) / **`finalApproverDate` 계산 교정**(D11) /
  **`getForm`의 `RuntimeException`**(D13) / **`MemberDTO` 응답 password**(D14-b) — 전부 이월
- **`src/test/**`의 나머지 17개 파일** — 무변경

---

## 8. 관찰 (R9 — 기록만, 고치지 않았다)

> **처리 완료로 이동한 2건**
> - `int totalPage` 미사용 지역 변수 → **제거함** (사용자 승인 예외). §3 참조
> - `approval/dto/ResponseDTO.java` → **삭제함** (사용자 승인 예외). §3 참조
>
> 초안에서는 둘 다 이 절의 "기록만" 항목이었다. **사용자 승인으로 처리 항목이 됐다.**

1. **`case "received"`는 R7 가드를 우회한다.** 조기 `return`이라 `approvalPage`가 null인 채로도
   가드에 닿지 않는다. 명세 D2·R3가 의도한 동작이다(결재 대기함 영향 없음). 다만
   **`fg=received`만은 미지원 값 방어의 바깥에 있다**는 사실은 기록해 둘 가치가 있다.

2. **`direction == "ASC"` 참조 비교** (QueryService `case "received"` 내부).
   `|| direction.equals("ASC")`가 뒤에 있어 결과는 정상. **§6 3지점 밖이라 손대지 않았다.**

3. **`@Tag`가 `selectMember`·`selectAllMembers` 2개에 없다.** §3-6이 "추가하지 않는다"로 못박아
   추가하지 않았다. 라인 수를 늘릴 뿐 spec 근거가 없다.

4. **`@RequestMapping`이 L22에 값 없이 붙어 있다.** 경로는 메서드마다 전체 표기.
   묶으면 `/approvers/{approverNo}`가 어긋나므로 원문 유지.

5. **`src/test/.../approval/` 아래 빈 디렉터리 2개**(`controller`·`service`)가 남았다.
   Git은 빈 디렉터리를 추적하지 않으므로 커밋·클론에는 영향이 없다. 로컬 파일시스템에만 남는다.
   (`test-suite-status.md` "남은 것"에도 기록됐다.)

6. **명세의 지표 계측 기준이 §3-1(총 줄 수)과 §3-6(비어있지 않은 줄 수)에서 엇갈린다.**
   §2에 상세. 기준은 `completed/approval-domain.md` §1에서 고정했다.

7. **문서 드리프트 2건 (고치지 않음)**
   - `leave-pattern.md` §9의 `ErrorCode` 예시가 실물과 인자 순서가 다르다
     (문서 `("L001", HttpStatus, "…")` vs 실물 `(int status, String code, String message)`).
     06 보고서 §8 관찰 2가 이미 기록했고, 이번에도 실물 시그니처를 따랐다.
   - **`AGENTS.md`의 "완료된 리팩토링" 링크 2건이 죽어 있다** —
     `docs/refactoring/completed/error-handling.md`·`leave-domain.md` **둘 다 리포에 없다**
     (`completed/` 디렉터리 자체가 이번에 처음 생겼다). v3 §10-2 지시대로 **고치지 않고 기록만** 한다
     (타 도메인 소관).

---

## 9. 다음 단계 (사용자)

1. **수동 검증 S0~S9** — 특히 **S0(사칭 차단)·S1(전 API 토큰 전용)**. 생략 불가(R11)
2. **커밋 3분할** — 파일을 섞지 않는다
   - ① **코드**: `ApprovalController.java`, `ApprovalQueryService.java`
   - ② **잔해 삭제**: `ApprovalServiceTest.java`, `ApprovalControllerTest.java`,
     **`approval/dto/ResponseDTO.java`**
   - ③ **문서**: `tasks/07-controller.md`, `reports/07-controller-report.md`,
     `completed/approval-domain.md`, `AGENTS.md`, `test-suite-status.md`
3. **`completed/approval-domain.md`의 "커밋 전 채울 것" 주석 블록** 처리
   (기간 시작일 · 단계 7 커밋 해시 3건 · `c30ea54` 충돌 검증 · S9 password 평문 여부)
4. 푸시 → **전자결재 도메인 리팩토링 종료**
