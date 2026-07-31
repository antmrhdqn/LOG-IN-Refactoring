# 전자결재(Approval) 도메인 리팩토링 — 완료 보고

> 기간: **2026-07-24 ~ 2026-07-30** (단계 1 ~ 7, 7일)
> 명세: `docs/refactoring/approval/spec.md` / `plan.md` / `tasks/01~07-*.md`
> 단계별 보고서: `docs/refactoring/approval/reports/01~07-*.md`
> 패턴 레퍼런스: `docs/reference/leave-pattern.md`
>
> ⚠ 이 문서는 `docs/refactoring/completed/`의 **첫 문서**다.
> `AGENTS.md`가 선례로 가리키는 `error-handling.md`·`leave-domain.md`는 **리포에 존재하지 않는다**
> (§4 문서 드리프트 참조). 따라서 형식은 `tasks/07-controller.md` §10-2가 지정한 5개 항목 구성을 따랐다.

---

## 1. spec 성공 지표 대비 실적

> **계측 기준**: 라인 수는 **총 줄 수**(빈 줄 포함)를 주 지표로 하고, 필요한 곳에 **비어있지 않은 줄 수**를
> 병기한다. 명세 안에서 기준이 엇갈렸기 때문에(§1 ① 참조) 이 문서에서 기준을 고정한다.

| 지표 | Before | 목표 (spec) | **실적** | 판정 |
|---|---|---|---|---|
| `ApprovalService` 라인 수 | 1,066줄 (단계 6 착수 시 812줄) | Command/Query 분리, **각 200~400줄** | Command **343** / Query **446** | ⚠ **Query 46줄 초과** |
| `ApprovalController` 라인 수 | 470줄 → 단계 6 종료 시 **253(총) / 178(비어있지 않은)** | **100~120줄** (→ D4로 140~155 개정) | **178(총) / 130(비어있지 않은)** | ⚠ **총 줄 기준 23줄 초과** |
| 상태값 표현 | 한글 문자열 | Enum 100% (Repository JPQL 포함) | `ApprovalStatus`·`ApproverStatus` 전면 적용 | ✅ |
| 응답 체계 | 자체 `ResponseDTO` | `ResponseMessage<T>` / `ErrorResponse` | 참조 0 + **클래스 파일 삭제** (단계 7) | ✅ |
| 상태 전이 검증 | 없음 | Entity 내부 메서드 (`canTransitionTo`) | `withdraw`/`submitFromTempSaved`/`markAsApproved`/`markAsRejected`/`modifyDraft` | ✅ |
| 에러 처리 | try-catch + null 반환 | `BusinessException` 위임 | `GlobalExceptionHandler` 위임, 컨트롤러 try-catch 0 | ✅ |
| 각 단계 독립 빌드 가능 | — | 유지 | 전 단계 `compileJava`+`bootRun` 통과 | ✅ |
| 기존 API 응답 JSON 구조 | — | 불변 | **구조·값 불변.** 실패 경로만 변경 — 미지원 `fg` 500→**400**, 빈 `page` 500→**200**(§4) | ✅ |
| `compileTestJava` | 실패 | (spec 무언급) | **통과** | ✅ |

> **⚠ `ApprovalCommandService` 343줄에 대한 정정**
> `06-command-query-report.md` §1은 이 값을 **337줄**로 적었으나 **오기다**. 실측은 **343줄**(총 줄) ·
> 270줄(비어있지 않은 줄)이며, 337은 어느 기준에도 해당하지 않는다.
> 이 파일은 **단계 7에서 무변경**이다 — `git diff --stat`에 등장하지 않는다(`07-controller-report.md` §1).
> 숫자 차이를 파일 변경으로 오독하지 말 것.

### 미달 항목과 사유

#### ① `ApprovalController` 178줄 — 목표(140~155) 23줄 초과

**spec 원지표 100~120은 Controller가 470줄이고 채번·번호 조립을 품고 있던 시절의 숫자**다.
그 로직은 Stage 5·6에서 이미 빠져나갔고, 남은 253줄의 성격이 달랐다
(`07-controller.md` §3-1: 상위 3개 엔드포인트가 본문의 절반, 나머지 9개는 이미 6~21줄).
→ Stage 7 착수 시 **D4로 140~155로 개정**했으나, 실적은 **그 개정 목표도 초과**했다.

**초과분 약 30줄은 전부 빈 줄이다.** 원본 Controller는 메서드 여는 중괄호 다음과 닫는 중괄호 앞에
빈 줄을 두는 스타일이며, 이 스타일을 원문 그대로 보존했다.
**비어있지 않은 줄 기준으로는 130줄**로 개정 목표 안에 든다.

**압축하지 않은 이유**: `07-controller.md` R10이 `@Tag` 삭제·줄바꿈 압축·컨트롤러 분할을 금지했고,
D4가 "지표를 두 번 연속 개정하면 지표가 무의미해진다"며 **실측값 그대로 보고**를 지시했다.
숫자를 위한 압축은 리팩토링이 아니다.
**유리한 기준을 골라 "달성"으로 적지 않는다** — 주 지표(총 줄 수)로 **미달**이 정확한 판정이다.

> **🔢 숫자 혼동 주의 — `178`이 두 뜻으로 등장한다.**
>
> | | 총 줄 | 비어있지 않은 줄 |
> |---|---|---|
> | Before (단계 6 종료) | 253 | **178** |
> | After (단계 7) | **178** | 130 |
>
> **Before의 "비어있지 않은 줄"과 After의 "총 줄"이 우연히 같은 178이다.**
> 기준을 섞어 읽으면 "줄어든 것이 없다" 또는 "기준을 몰래 바꿨다"로 오독된다.
> 실제 감소는 **총 줄 253 → 178 (-75, -29.6%)** / **비어있지 않은 줄 178 → 130 (-48, -27.0%)** 이다.

> **계측 기준이 명세 안에서 엇갈렸던 기록**: `07-controller.md` §3-1의 Controller `253줄`은
> **총 줄 수**, §3-6의 `src/test 2파일 615줄`은 **비어있지 않은 줄 수**였다(실측: 총 748 / 비어있지 않은 615).
> 목표치 140~155가 빈 줄을 12줄로 가정해 산출된 것이 초과의 근본 원인이며, **명세의 결함**이다.

#### ② `ApprovalQueryService` 446줄 — 목표(200~400) 46줄 초과

읽기 경로가 **`getApproval` 하나에 집중**돼 있다. 상세 조회 한 건이 기안자·부서·직급·양식·결재선·
참조선·첨부·최종처리일·대기자를 전부 조립하며(93줄), 목록 조회는 그 메서드를 항목마다 호출한다.
분리하려면 조립 책임을 별도 어셈블러로 빼야 하는데, 그것은 **Stage 6 명세에 없던 구조 변경**이라
당시 범위 밖이었다.

> `06-command-query-report.md` §1은 이 값(당시 456줄)을 **"성공 기준 각 200~400줄 범위 안"**이라고
> 적었으나, **456 > 400이므로 그 기술은 틀렸다.** Stage 7의 로그 정리와 orphan 1줄 제거로 446줄이 됐고
> 여전히 초과다.
> **이 문서에서 정정한다.**

---

## 2. 단계별 커밋

| 단계 | 내용 | 코드 커밋 | 문서 커밋 |
|---|---|---|---|
| 1 | Entity Enum 전환 + 상태 전이 검증 | `a9d1436` | — |
| 1.5 | Repository 상태 리터럴 정합 (JPQL·네이티브) | `60721f6` | `994633e` |
| 2 | DTO Lombok 적용 · 날짜 필드 타입 정합 | `c102e63` | `c30ea54` (소급) |
| 3 | 공통 응답 체계 통합 (`ResponseMessage`) | `a6e2348` | `c30ea54` (소급) · `eee0e10` † |
| 4 | 파일 처리 분리 (`ApprovalFileService`) | `89d27b8` ※ | `67b63ee` · `875bf76` |
| 5 | 결재번호 생성기 분리 (`ApprovalNoGenerator`) | `80f60b7` | `0215951` |
| 6 | God Class 분리 (Command / Query) | `2446c73` | `43e3a61` |
| **7** | **Controller 슬림화** | **`e35e0b1`** (코드) · **`b314778`** (잔해 정리) | **(이 문서를 포함한 커밋)** |

> **`c30ea54` 귀속 확정 (2026-07-30 실물 검증)** — 커밋 제목이
> `docs: 밀린 결재 리팩토링 문서 소급 커밋 (Stage 2·3 보고서/명세, test-suite-status)`이므로
> **단계 2·3의 소급 커밋이 맞다.** `AGENTS.md`가 이를 단계 4의 문서 커밋으로 기록한 것이
> 부정확했다(§4 문서 드리프트).
>
> **† `eee0e10` 확정** — `docs: AGENTS 현재 단계 Stage 3으로 갱신`(2026-07-29).
> `AGENTS.md`(리포 루트)만 변경했고 `docs/refactoring/approval/` 아래는 건드리지 않아
> 경로 필터 로그에는 나타나지 않는다. 기재는 정당하다.
>
> **※ `89d27b8`(단계 4 코드)에는 문서 변경이 섞여 있다** — 문서 경로 로그에 함께 검색된다.
> 코드/문서 분리 커밋 규칙은 **단계 5부터 정착**했다.
>
> 단계 7의 문서 커밋 해시는 **이 문서 자신이 그 커밋에 포함**되므로 적을 수 없다(자기참조).
> 확인은 `git log --oneline -3`으로 한다.

> 단계 7은 **3분할 커밋**이다 — ① 코드 ② `src/test` 잔해 + `ResponseDTO` 삭제 ③ 문서.
> 지금까지 모든 task가 `src/test/**`를 금지해 왔으므로 diff 리뷰에서 리팩토링과 잔해 정리가
> 섞이면 안 된다(`07-controller.md` R8).

---

## 3. 해결한 결함

spec "코드 분석으로 발견된 추가 문제"에 열거된 항목 기준.

| 결함 | 내용 | 해결 단계 |
|---|---|---|
| **[A]** | 회수 검증 누락 (본인 확인·상태 전이 없음) | 1 — `Approval.withdraw(memberId)` |
| **[A-잔여]** | 회수 시 타인 처리 여부 미검증 | 6 — `AP009` (기안자 `_apr000` 제외) |
| **[A-확장]** | 회수 API 인증 정보 부재 | 1 + **7** — SecurityContext에서 추출 |
| **[B]** | 결재 순서 무시 (`i == size-1` 인덱스 판정) | 6 — 전원 `APPROVED`일 때만 완료 |
| **[C]** | 수정이 삭제 후 재생성 | 6 — `modifyDraft` + dirty checking |
| **[C-첨부]** | 재임시저장 시 옛 첨부 디스크 orphan | 6 — 삭제 순서 교정 + `clearPersistenceContext` |
| **[D]** | 임시저장 status 클라이언트 신뢰 | 6 — 서버 화이트리스트 (`AP010`) |
| **[F]** | `save()` 남용으로 dirty checking 미활용 | 6 — Entity 메서드 + persist 전환 |
| **[G]** | 예외 삼키기 (`catch { log }` 후 null 반환) | 3 (컨트롤러) · 6 (서비스) |
| **[H]** | 인증 정보 추출 중복 (4곳 / 패턴 3종) | **7** — `getCurrentMemberId()` 1곳 |
| **[I]** | `memberId` 헤더 백도어 (수평적 권한 상승) | **7** — 파라미터·fallback 분기 제거 |
| **[J]** | 이모지 로그 · `System.out.println` 폭격 | **7** — 16줄 제거 + password 로그 차단 |
| **[K-persist]** | 결재번호 동시성 — merge 덮어쓰기 | 5 (채번 일원화) · 6 (persist 전환) |
| **[L]** | 임시저장→기안 전환이 삭제 후 재생성 | 6 — 같은 번호 유지 |
| **[M]** | Repository 한글 리터럴 무성 실패 | 1.5 — 전체 경로 Enum 상수 표기 |

### 단계 7이 닫은 보안 결함 — 실측 근거

`[I]`의 실제 피해는 "인증 무력화"가 아니라 **"유효한 토큰 + 남의 사번 헤더 → 남의 결재함 열람"**
이라는 **수평적 권한 상승**이었다(`roleLessList` 오기 때문에 토큰 없이는 컨트롤러에 도달조차 못 한다).
`06-command-query-report.md` §6-1 S8이 실측했다 — `received` **3건(토큰) vs 5건(헤더 241811)**,
`receivedRef` **0건(토큰) vs 7건(헤더 240401001)**. 다른 사람의 목록이 그대로 반환됐다.

`[J]`의 password 로그(`ApprovalQueryService:394`)는 `MemberDTO.toString()`이 평문 비밀번호를
포함하기 때문에, `GET /approvals/members/{memberId}` 호출마다 **평문 비밀번호가 로그로 출력**되고
있었다. 이번에 차단했다.

> **📌 정정 (2026-07-31, 보안 작업 A)** — 초판은 "**로그 파일에 적재**"라고 적었으나 사실이 아니다.
> 이 앱에는 **파일 appender가 없다** (`logback-*.xml` 부재, `application.yml`에 `logging.file.*` 없음).
> 유출 경로는 **콘솔/stdout**이다. 디스크에 영속되지 않으므로 **피해 크기 판단이 달라진다.**
> 같은 오기가 `reports/07-controller-report.md:183`에도 있었고 함께 정정했다.

### ⚠ spec의 결함 목록이 완전하지 않았다

위 표의 결함은 **전부 닫혔다.** 그러나 단계 7 검증 중, **spec에 열거되지 않은 같은 계열의 결함**이
쓰기 경로에 남아 있음이 확인됐다 — `PUT /approvers/{approverNo}`의 신원 미검증(§4 🔴 1).
spec `[A-확장]`은 회수 API의 인증 부재를 지적했지만 **결재 처리 API는 아무도 짚지 않았다.**

→ **"spec 결함 목록에 없으니 없는 문제"로 읽지 말 것.** 이 리팩토링은 열거된 결함을 닫았을 뿐이며,
열거 자체가 완전했다는 보장은 없다. 인증·권한 경계는 **엔드포인트 단위로 다시 훑어야 한다.**

> **📌 정정 (2026-07-31, 보안 작업 A) — 실제 규모는 위 서술보다 훨씬 컸다.**
> 위 문단은 `PUT /approvers` **1건**을 예로 들었으나, 예고한 대로 훑어 본 결과:
> - **쓰기 엔드포인트 5종 중 4종**에 호출자 신원 검증이 없었다
>   (`PUT /approvers` · `DELETE /approvals/{no}` · `PUT /approvals/{no}` 재임시저장 ·
>   `POST /approvals` 기존번호 분기). 회수 1종만 검증이 있었다 → **작업 A에서 4종 전부 닫았다**
> - **읽기 경로에도 인가가 없다** — `GET /approvals/{approvalNo}` 상세 조회와
>   `GET /approvals/files` 파일 다운로드가 기안자·결재자·참조자 여부를 묻지 않는다
>   → **작업 E** (정책 결정 선행)
>
> `docs/security/approval/spec.md` §3·§4-4 참조. **이 문단의 경고 자체는 옳았다.**

### 단계 7이 함께 닫은 잔해

- **`src/test`의 결재 테스트 2파일 삭제** — `ApprovalServiceTest`(476줄) ·
  `ApprovalControllerTest`(272줄). **단계 1부터 컴파일 불가 상태**였다
  (`test-suite-status.md` 1차 기록 / `06-command-query-report.md` §8 관찰 1 보강).
  → `compileTestJava` **실패 → 통과**.
- **`approval/dto/ResponseDTO.java` 물리 삭제** — 단계 3에서 프로덕션 참조가 0이 되고,
  위 테스트 2파일 삭제로 **리포 전체 참조가 0**이 됐다. `test-suite-status.md` "그때 결정할 것" §2가
  미뤄둔 결정을 이번에 닫았다. (survey 도메인의 `SurveyResponseDTO`·`ResponseDTO`는 다른 클래스다.)
- **검증 파이프라인에 `compileTestJava` 추가** — 같은 문서 §3의 결정. §6 참조.

---

## 4. 미해결 · 이월 목록 (우선순위 순)

### 🔴 최우선 — 보안

#### 1. `PUT /approvers/{approverNo}` 결재 처리의 **신원 미검증** (2026-07-30 실측)

유효한 토큰만 있으면 **기안자도 결재자도 참조자도 아닌 제3자**가 남의 결재를 승인·반려할 수 있다.

| 실측 (2026-07-30, 보조 계정 토큰) | 결과 |
|---|---|
| 제3자 승인 | **200** — `_apr001` APPROVED, **결재 전체 `PROCESSING` → `APPROVED`** 완결 |
| 제3자 반려 | **200** — **`REJECTED`로 즉시 종료**, `rejectReason` 그대로 저장. 되돌리는 API 없음 |
| 기록된 처리자 | **호출자가 아니라 지정 결재자 사번(`241811`)** |
| 실제로 걸리는 가드 | 재처리 400/AP005(상태 전이) · 없는 번호 404/AP004(존재) — **둘 다 신원과 무관** |

**감사 기록이 위조된다.** `Approver` 행의 `memberId`는 이미 지정 결재자 것이고 `approve()`는 상태·일시만
바꾸므로, 누가 호출해도 **결백한 사람이 승인한 것으로 남는다.**

**원인**: `ApprovalController:126`이 인증을 추출하지 않고
`ApprovalCommandService.processApprover(approverNo, approverDTO)` 시그니처에도 인증 정보가 없다 —
**검증할 지점 자체가 없다.** 회수는 단계 1에서 `withdraw(approvalNo, memberId)`로 바뀌어 AP008로
본인을 확인하는데, spec `[A-확장]`이 **회수 API에만 적용되고 결재 처리에는 적용되지 않았다.**

**심각도를 올리는 요소**: `approverNo`는 `{결재번호}_apr{순번3자리}`, 결재번호는 `{연도}-{양식3자}-{5자리}`이고
양식 코드 7종은 `GET /approvals/forms`로 누구나 조회된다. **토큰 하나로 전사의 `approverNo`를 열거**할 수 있고,
404(없음)와 400(이미 처리됨)이 갈려 **열거 결과를 구분**까지 할 수 있다.

**단계 7이 만든 결함이 아니다.** `ApprovalCommandService`는 무변경이었고, 이 메서드에는 원래 인증 추출이
없어 `07-controller.md` §3-2의 "인증 추출 4곳"에도 포함되지 않았다.
단계 7이 닫은 `[I]`는 **조회·기안 경로**였고, **쓰기 경로에 같은 계열(수평적 권한 상승)이 남아 있다.**

**처방 (스키마 변경 불필요)**: `withdraw` 선례대로 호출자 사번을 받아 지정 결재자 본인인지 확인한다.
`processApprover(approverNo, approverDTO, memberId)` + `approver.getMemberId() != memberId`면
**기존 `AP003 APPROVAL_UNAUTHORIZED`(403)** 를 던지면 된다 — 새 ErrorCode도 필요 없다.

> **왜 password 노출보다 위인가**: password는 bcrypt 해시라 즉시 피해가 제한적이다. 이쪽은
> **인증된 사용자 누구나 전사 결재를 임의로 승인·반려**할 수 있고, **기록까지 남의 이름으로 남는다.**
> 기밀성 문제가 아니라 **무결성·부인방지 문제**다.

#### 2. `MemberDTO` 응답 JSON의 password 노출 (D14-b)
`GET /approvals/members/{memberId}`·`GET /approvals/members` 응답에 `password` 필드가 실린다.
`MemberDTO`에 `getPassword()`가 있고 Jackson이 직렬화하기 때문이다.
**이번에 고치지 않은 이유**: spec 성공 기준 "기존 API 응답 불변" 때문이다. 리팩토링 마지막 단계에서
스스로 그 원칙을 깨지 않았다. → **리팩토링 직후 착수 권고.**
**S9 실측(2026-07-30)**: 값은 평문이 아니라 **bcrypt 해시**(60자, `$2a$` 접두사)다.
다만 개발 DB 계정이 4자리 숫자 비밀번호를 쓰고 있다 — **bcrypt라도 약한 비밀번호는 오프라인 대입으로
사실상 즉시 복원된다.** 인증과 무관한 조회 API가 **전 사원의 해시**를 반환하는 구조 자체가 문제이므로
**🔴 우선순위를 유지한다.**

### 🟠 다음

- **깨진 레거시 데이터 1건이 목록 페이지를 통째로 죽인다** (단계 7 S4에서 발견)
  `2024-con00002`가 `getApproval`에서 **404/AP004**를 낸다 — `APPROVED`인데 결재자 목록이 비었거나
  `REJECTED`인데 `REJECTED` 상태 결재자가 없는 경우다. 목록 조회는 항목마다 `getApproval`을 호출하므로
  **이 결재가 포함된 페이지 전체가 404**가 된다(`DESC page=2` / `ASC page=0`). 나머지 30건은 정상.
  - **단계 7 회귀가 아니다** — `getApproval`은 무변경이고, `06-command-query-report.md` §4 **R6-b가
    이미 예고한 현상**이다("데이터가 깨진 결재가 하나라도 있으면 목록이 통째로 죽는다").
    Stage 6 S8이 **page 0만** 확인해 닿지 않았고 이번에 처음 드러났다.
  - 처방 둘: **(1) DB 데이터 복구** — 권장. 결손 행을 보강한다.
    **(2) 코드에 내성 부여** — 비권장. 손상 데이터를 조용히 감추게 되고 "조용한 실패"를 다시 만든다.
  - 진단: `SELECT * FROM approver WHERE approval_no = '2024-con00002';`
- **`receivedAll` 호출 화면 존재 여부 미확인.** 프론트가 리포 외부에 있어 DevTools로만 확인 가능한데,
  실측한 3개 요청(`given`·`received`·`POST`)에 `receivedAll`이 없었다.
  단계 7 이후 **호출 시 500이 아니라 400/C001**이 된다. 화면이 실재한다면 기능 구현이 필요하다.
- **`[K]` 결재번호 채번 재시도** (D3) — persist 전환으로 **데이터 유실 경로는 이미 없다.**
  남은 것은 동시 기안 시 드문 500이라는 UX 비용. 재시도는 트랜잭션 **바깥** 진입점이 필요해
  독립 단계로 뗐다.
- **R7 `receivedAll` 미구현** — 빈 `case`를 원문 그대로 뒀다. 미구현을 빈 `Page` 200으로 위장하면
  단계 1.5에서 가장 크게 데인 "조용한 0건"이 된다. 그래서 **400으로 드러나게** 했다.
- **`finalApproverDate` 의미 어긋남** (D11) — `[B]`로 결재 순서가 자유로워지면서
  "가장 큰 `approverOrder`의 처리일시"가 **실제 마지막 승인자와 달라진다**
  (Stage 6 S1 실측: order3=21:57:13, order1·2=21:57:14 → 표시는 21:57:13).
  응답 **값**이 바뀌고 표시 정책에 걸려 이월했다.

### 🟡 구조 · 품질

- **`[E]` 기안자가 `Approver` 테이블에 `_apr000`으로 자동 등록되는 관례** — 기안자는 결재 요청자이지
  결재자가 아니다. DB 스키마 변경 수반.
- **트랜잭션 ↔ 디스크 원자성** — `ApprovalFileService.store()` 반환 후 바깥 tx가 롤백되면
  디스크 파일이 orphan으로 남는다. `[APPROVAL_FILE_ORPHAN]` 로그 관찰만 유지 중.
- **자식 엔티티(approver·referencer·attachment)의 persist 전환** — 기안 1건당 merge 전용 SELECT가
  남아 있다. `Approval`만 persist로 바꿨다.
- **`ApprovalQueryService` 446줄** — §1 ②. `getApproval` 조립 책임 분리가 필요하다.
- **`getForm`의 `RuntimeException`** (D13) — `FORM_NOT_FOUND` 신설이 필요하고, 이 예외는
  삼켜지지 않고 정상 전파되므로 `[G]` 대상이 아니었다.
- **자동화 테스트 부재** — 단계 7에서 컴파일 불가 2파일(총 748줄 / 비어있지 않은 615줄)을 **삭제**했다.
  새 테스트는 쓰지 않았다(spec Out of Scope). 결재 도메인의 테스트 커버리지는 **0**이다.
  `processApprover`의 `ApproverStatus.from()`이 잘못된 값에 500/C999를 내는 것(Stage 6 §8 관찰 5) 등
  검증되지 않은 경로가 남아 있다. → 도입 시 우선순위는 §6 참조.

### ⚪ `auth/**` 관찰 (단계 7에서 읽기만 함 — **고치지 않았다**)

- **`JwtAuthorizationFilter:57`의 `roleLessList` 오기** — 8번째 원소가
  `"/announces/{ancNo}, /approvals"`로, **쉼표가 따옴표 안에** 들어가 있다.
  `contains()` 완전 일치이므로 **어떤 URI에도 매칭되지 않는다.**
  결과적으로 `/approvals`가 인증 필요 상태로 유지되고 있으며 **현재 동작이 옳다.**
  🚫 **"고치면" `/approvals`가 무인증으로 열려 훨씬 위험해진다.** 같은 리스트의
  `"/members/{memberId}"` 등 템플릿 원소도 같은 이유로 매칭 불가다.
  → 손대려면 **어떤 경로를 인증 면제할지부터 재설계**해야 한다.
- **`JwtTokenInterceptor` 죽은 코드** — `WebConfig`에 빈 등록만 있고 `addInterceptors` 오버라이드가
  리포 전체에 0건이다.
- **`@EnableWebMvc`** (`WebConfig:14`) — Spring Boot 자동설정을 끈다.
- **🔓 로그인 경로가 bcrypt 해시를 로그로 출력한다 (보안 관련)** — `MemberService:50` ·
  `CustomAuthSuccessHandler:27` · `TokenUtils:77`. 로그인 5회에 **20건**이 찍혔다(단계 7 S6 실측).
  `auth/**`·`member/**`라 단계 7 §6 금지 범위여서 **기록만 했다.** 위 🔴 항목과 같은 부류이며,
  결재 도메인의 password 로그를 차단한 것과 **같은 조치가 로그인 경로에도 필요하다.**

  > **📌 정정 (2026-07-31, 보안 작업 A)** — 위 "해시 3곳"은 **과소집계다.**
  > 보안 조사에서 아래가 목록에 없었음이 확인됐다 (`docs/security/approval/spec.md` §4-2).
  > - **평문 비밀번호 4곳** — `CustomAuthenticationProvider:35`,
  >   `MemberController:317`(신규 비밀번호 2개 + 현재), `MemberController:477`, `MemberService:272`
  > - **전 사원 해시 일괄 출력** — `MemberService:191·193` (`findAll()` 전체, 한 요청에 2회)
  > - **토큰 전문 3곳(활성)** — `CustomAuthSuccessHandler:38`, `TestController:27·45`
  >   (+ `CustomAuthSuccessHandler:38` 주석에 실제 형태 JWT 하드코딩)
  > - **SQL 로그** — `show-sql=true` + `hibernate.sql=debug`로 `password` 컬럼이 SELECT 목록에 노출
  >
  > → **작업 C** 소관이다.

### 📄 문서 드리프트

- **`AGENTS.md`의 "완료된 리팩토링" 링크 2건이 죽어 있다** —
  `docs/refactoring/completed/error-handling.md`·`leave-domain.md` 둘 다 리포에 없다
  (`completed/` 디렉터리가 이 문서로 처음 생겼다). **타 도메인 소관이라 고치지 않았다.**
- **`leave-pattern.md` §9의 `ErrorCode` 예시가 실물과 인자 순서가 다르다** —
  문서는 `LEAVE_NOT_FOUND("L001", HttpStatus.NOT_FOUND, "…")`, 실물 생성자는
  **`ErrorCode(int status, String code, String message)`**. **문서를 믿으면 컴파일이 깨진다.**
- **`06-command-query-report.md` 수치 오기 2건** — `ApprovalCommandService` 337줄(실제 343),
  `ApprovalQueryService` 456줄을 "200~400 범위 안"이라고 기술. **이 문서 §1에서 정정했다.**
- **`AGENTS.md`가 `c30ea54`를 단계 4의 문서 커밋으로 기록했다** — 실물 커밋 제목은
  "Stage 2·3 보고서/명세 소급 커밋"이다(§2 검증). 단계 6 명세 §10의 "before" 블록에도 같은 오기가 있다.
  **이 문서 §2가 정본이다.**
- **`spec.md`는 고치지 않았다** — Controller 라인 지표(100~120)를 사후 개정하면 이력이 흐려진다.
  개정 근거는 `tasks/07-controller.md` D4와 이 문서 §1 ①에 남긴다.

---

## 5. 프론트엔드 제약 (실측 확인 — 리포 외부)

프론트는 이 리포에 없다(`WebConfig:53` `allowedOrigins("http://localhost:3000")`).
아래는 DevTools 실측으로 확정된 사실이며, **결재 도메인을 다음에 손댈 때 반드시 읽어야 한다.**

1. **프론트는 한글 상태값을 보낸다** — `"임시저장"` / `"처리 중"` (Stage 6 S7 실측).
   → 🚫 **`enums/ApprovalStatus.java`의 `description` 필드와 `from()`의 한글 매칭 분기를
   제거하면 기안이 전부 실패한다.**
2. **DTO의 Enum 타입 전환은 봉인** — Jackson 기본 역직렬화는 `name()`만 매칭하므로
   `"임시저장"`이 오는 순간 깨진다. 전환하려면 **`@JsonCreator` 추가** 또는 **프론트의 영문 전환**이
   선행돼야 한다.
3. **프론트는 `memberId` 헤더를 보내지 않는다** — 목록·기안 3요청 실측(`07-controller.md` §3-7).
   `cookie` 헤더도 없다(토큰만, `STATELESS`와 일치). 그래서 `[I]` 제거에 프론트 배포 의존성이 없었다.
4. **결재번호에 `ims`가 잔존한다** — 임시저장은 `ims`로 채번되고, 기안 전환 시 **번호를 유지**하므로
   `2026-ims00008`이 `PROCESSING` 상태로 남는다. 이력 추적 보존을 위해 수용한 결과다(Stage 6 D2=L1).
   **프론트는 번호가 아니라 상태로 목록을 거르므로 화면 위험은 없다**(상신함에만 노출 — 사용자 확인 완료).
5. **목록 상태 컬럼에 영문 Enum(`PROCESSING`)이 그대로 노출된다** — Stage 1 Enum 전환의 파급이다.
   프론트 표시 매핑 문제이고 프론트 변경은 리팩토링 범위 밖이라 기록만 한다.

   > **📌 정정 (2026-07-31, 보안 작업 A) — 표시 문제가 아니라 기능 정지다.**
   > 같은 원인이 `canApproveOrReject`를 **항상 false**로 만든다.
   > ```
   > 서버 응답   ApprovalQueryService:185·242   .name()      → "PENDING" / "PROCESSING"
   > 프론트      ApprovalDetail.js:129          === '대기'   → 항상 false
   >             ApprovalDetail.js:222·265      {canApproveOrReject && ...}  → 미렌더
   > ```
   > **2026-07-31 실측**: 관리자 계정의 결재 수신함에서 사원이 상신한 결재 상세를 열어도
   > **승인·반려 버튼이 없다.** 즉 **결재 처리 기능이 현재 프론트에서 정지 상태다.**
   > (`docs/security/approval/spec.md` §5. 프론트 근거는 리포 외부 —
   > `LOG-IN-F-Refactoring`, `main`, `8c13156`)
6. **임시저장 → 기안 전환이 프론트의 실제 주 경로다** — 작성 화면에서 임시저장 후 기안을 누르면
   임시저장 응답의 결재번호를 그대로 실어 보낸다. Stage 6이 "요청 status가 전환 여부를 가른다"로
   구현한 덕에 **임시저장을 두 번 눌러도 상신되지 않는다.**
7. **목록 조회는 4개 파라미터를 항상 함께 보낸다** — `fg`·`page`·`title`·`direction`.
   `title`은 검색어가 없을 때 **빈 문자열**로 전송된다(실측: `?fg=received&page=0&title=&direction=DESC`).

---

## 6. 검증 방식에 대한 기록

이 도메인에는 자동화 테스트가 없었고, 리팩토링 중에도 **새로 쓰지 않았다**(spec Out of Scope).
검증은 전 단계에서 `compileJava` + `bootRun` + **수동 API**였다.

**단계 1.5의 교훈**이 이 방식을 결정했다. JPQL 한글 리터럴은 컴파일에서도 Hibernate 파싱에서도
거부되지 않고, **런타임에 예외·로그·500 없이 조용히 0건을 반환**했다. `compileJava` 통과가
아무것도 증명하지 않는다는 사실이 그때 확인됐다.

**단계 7은 그보다 더했다.** 인증 경로 변경은 컴파일·기동에 **전혀** 드러나지 않는다.
그래서 착수 게이트(프론트가 `memberId` 헤더를 보내는지 DevTools 실측)와
전 API 토큰 전용 재검증(S1)이 단계의 본체였다.

### 파이프라인 변경 (단계 7)

**`compileTestJava`를 검증 절차에 추가했다** (`test-suite-status.md` §3의 결정).
단계 7에서 컴파일 불가 2파일을 삭제해 **처음으로 통과 상태가 됐고**, 이 상태가 다시 조용히 깨지는 것을
막는 가장 값싼 장치이기 때문이다. 결재 외 17개 테스트 파일도 정상 컴파일되므로 타 도메인 병목도 없다.

```powershell
cd final
.\gradlew.bat compileJava
.\gradlew.bat compileTestJava
.\gradlew.bat bootRun
```

> ⚠ 그래도 **이 세 개가 통과했다는 것은 아무것도 증명하지 않는다.** 수동 API 검증이 본체다.

### 다음 작업을 위한 권고

결재 도메인에 테스트를 도입한다면 **목록 조회 5종의 건수**와 **상태 전이 규칙**부터 덮는 것이
비용 대비 효과가 크다. 두 곳이 이번 리팩토링에서 **무성 실패가 실제로 발생했던 지점**이다
(단계 1.5의 조용한 0건, 단계 6의 인덱스 기반 완료 판정).

**권한 경계는 조회뿐 아니라 쓰기 경로까지 훑어야 한다.** 단계 7의 검증 시나리오(S0~S9)는
사칭 차단을 **조회·기안 경로에서만** 확인했고, 결재 처리(`PUT /approvers`)에 대한 신원 검증 항목이
없었다. 그래서 §4 🔴 1이 체크리스트 밖의 추가 확인에서야 드러났다.
**앞으로 인증을 건드리는 작업은 "쓰기 엔드포인트 전수 × 제3자 토큰"을 기본 항목으로 둘 것.**

**그리고 목록 검증은 `page=0`만 보면 안 된다.** 단계 6 S8은 첫 페이지만 확인했고, 그래서
`2024-con00002`의 데이터 결손(§4 🟠)이 단계 7 S4에서야 드러났다. **끝 페이지와 정렬 반전(ASC/DESC)을
함께 훑어야** 특정 행에만 있는 결손이 잡힌다.
