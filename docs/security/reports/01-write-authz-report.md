# 작업 A: 쓰기 경로 권한 경계 정리 — 작업 보고서

> 작업일: 2026-07-31
> 실행: Claude Code (Opus 5)
> 명세: `docs/security/tasks/01-write-authz.md` (**v3** — D1~D11 확정본)
> 선행: 전자결재 리팩토링 7단계 완료·커밋·푸시 (`e35e0b1`(코드)·`b314778`(잔해)·`94d7a18`(문서))
> **보안 결함 정리 스트림의 첫 작업.** 보고서 경로가 `docs/security/` 아래로 바뀌었다 (§9-2 → CLAUDE.md 갱신)
> ⚠ **경로 이동 (2026-08-12, 작업 E 완료 후).** 이 작업의 명세·보고서가
> `docs/security/approval/{tasks,reports}/` → `docs/security/{tasks,reports}/`로 옮겨졌다.
> **아래 §1 표에 적힌 경로는 당시 커밋의 기록이며 고치지 않는다.**
> **상태: 구현·빌드·수동 검증(S0~S8) 전부 완료.** 커밋·푸시만 남았다 (§9)

---

## 1. 변경 파일

| 파일 | 구분 | 변경량 | 커밋 |
|---|---|---|---|
| `approval/service/ApprovalCommandService.java` | 수정 | +47 / −6 | ① 코드 |
| `approval/controller/ApprovalController.java` | 수정 | +16 / −8 | ① 코드 |
| `docs/security/approval/reports/01-write-authz-report.md` | 신규 | — | ② 문서 |
| `AGENTS.md` | 수정 | §9-1 ①~④ | ② 문서 |
| `CLAUDE.md` | 수정 | §9-2 보고서 경로 | ② 문서 |
| `docs/refactoring/completed/approval-domain.md` | 수정 | §9-3 정정 1·2·3·4 | ② 문서 |
| `docs/refactoring/approval/reports/07-controller-report.md` | 수정 | §9-3 정정 1 | ② 문서 |

### git diff --stat

```
 AGENTS.md                                          | 32 +++++++--------
 CLAUDE.md                                          |  4 +-
 .../approval/reports/07-controller-report.md       |  7 +++-
 docs/refactoring/completed/approval-domain.md      | 41 ++++++++++++++++++-
 .../approval/controller/ApprovalController.java    | 16 +++++---
 .../approval/service/ApprovalCommandService.java   | 47 +++++++++++++++++++---
 6 files changed, 116 insertions(+), 31 deletions(-)
```

**코드는 §5가 허용한 2파일뿐이다.** 세 번째 코드 파일이 없다.
`ErrorCode.java`·`entity/**`·`enums/**`·`dto/**`·`repository/**`·`ApprovalQueryService`·
`service/file/**`·`service/generator/**`·`auth/**`·`member/**`·`insite/**`·`src/test/**` **전부 무변경.**

> `docs/security/`가 `??`(untracked)로 보이는 것은 이 디렉터리 자체가 이번에 처음 커밋되기 때문이다.
> `spec.md`·`tasks/01-write-authz.md`는 **사용자 작성분**이며 내가 만들지 않았다.

---

## 2. 결함별 처리

네 곳 모두 처방이 같다 — **엔티티에서 읽은 소유자 사번**과 **파라미터로 받은 호출자 사번**을
`int` 값으로 비교하고, 다르면 기존 `ErrorCode.APPROVAL_UNAUTHORIZED`(**AP003 / 403**)를 던진다.
**신규 ErrorCode 0건**(D6). 권한 판단에 `approvalDTO.getMemberId()`를 쓰지 않는다(D2).

검증 순서는 전부 **존재(404) → 신원(403) → 상태(400)**(D3)이며,
**디스크 파일 삭제·자식 벌크 삭제보다 앞**이다(§4 공통 원칙 4).

### [A1] `PUT /approvers/{approverNo}` — 지정 결재자 본인 확인

| 위치 | Before | After |
|---|---|---|
| `ApprovalController:131` | 인증 추출 **없음** | `getCurrentMemberId()` 전달 |
| `ApprovalCommandService:204` | `processApprover(String, ApproverDTO)` | `processApprover(String, ApproverDTO, int memberId)` |

`findByApproverNo` orElseThrow(AP004) **직후**에 삽입했다(`CommandService:211~215`).

```java
//지정 결재자 본인이 아니면 상태를 해석하기 전에 막는다.
//처리자는 Approver 행의 memberId 로 기록되므로, 막지 않으면 남의 이름으로 감사 기록이 남는다.
if (approver.getMemberId() != memberId) {
    throw new BusinessException(ErrorCode.APPROVAL_UNAUTHORIZED);
}
```

- 기존 AP004(존재)·AP005(상태 전이) 가드는 **그대로 뒀다.** 순서만 존재 → 신원 → 상태
- **부수 개선 (의도한 것은 아니다)**: 신원 검증이 `ApproverStatus.from()`(`CommandService:215` → 현 `:224`)보다
  앞서게 되어(`:213` vs `:222`), **제3자가 잘못된 상태 문자열을 보냈을 때의 500/C999(Stage 6 §8 관찰 5)가
  403으로 바뀐다.**
  본인이 보낸 잘못된 값은 여전히 500/C999다 — 그쪽은 이번 범위 밖이다

### [A2] `DELETE /approvals/{approvalNo}` — 기안자 본인 확인

| 위치 | Before | After |
|---|---|---|
| `ApprovalController:139` | 인증 추출 **없음** | `getCurrentMemberId()` 전달 |
| `ApprovalCommandService:251` | `delete(String)` — **가드 0줄** | `delete(String, int memberId)` |

메서드 첫머리, `approvalFileService.deleteByApprovalNo()`(`:263`) **앞**에 삽입했다(`CommandService:255~261`).

```java
//디스크 파일 삭제보다 "앞"이다. 롤백은 DB만 되돌리고 삭제된 파일은 되돌리지 못한다.
Approval approval = approvalRepository.findById(approvalNo)
        .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));   // AP001 / 404  ← 신설

if (approval.getMemberId() != memberId) {
    throw new BusinessException(ErrorCode.APPROVAL_UNAUTHORIZED);                  // AP003 / 403  ← 신설
}
```

- **상태 가드(임시저장만 삭제)는 넣지 않았다** — D5. `PROCESSING`·`APPROVED` 문서도 **소유자라면** 여전히 삭제된다
- 영속성: `findById`로 managed 상태가 되지만 이후 벌크 JPQL DELETE만 실행되고 엔티티를 변경하지 않아
  flush 시 부활 SQL이 없다
- **동작 변경 1건 발생** → §3

### [A3] `PUT /approvals/{approvalNo}` 재임시저장 — 기안자 본인 확인

| 위치 | Before | After |
|---|---|---|
| `ApprovalController:98~103` | 옛 L99가 `setMemberId(getCurrentMemberId())` — **권한 판단에 미사용** | `memberId`를 **파라미터로도** 전달 |
| `ApprovalCommandService:142` | `resaveTempSaved(String, ApprovalDTO, List<MultipartFile>)` | `+ int memberId` |

첫 `findById` orElseThrow(AP001) **직후**, `TEMP_SAVED` 가드 **앞**, `clearChildren` **앞**
(`CommandService:147~150`).

```
1. findById → orElseThrow(APPROVAL_NOT_FOUND)             기존 (AP001 / 404)
2. approval.getMemberId() != memberId → AP003 / 403       ← 신설
3. approvalStatus != TEMP_SAVED → AP011 / 400             기존
4. clearChildren(...) 이하 기존 순서 그대로
```

**2를 3보다 앞에 둔 이유**: 남의 문서인지가 상태보다 먼저 판정돼야 한다. 3이 먼저면 제3자에게
"그 문서는 임시저장이 아니다"라는 정보를 준다. 그리고 둘 다 **디스크 삭제(4) 앞**이어야 한다.
검증은 `clearChildren`의 `entityManager.clear()`(`ApprovalRepositoryImpl:22~24`) 앞이라 안전하다.

⚠ **호출부 2곳을 모두 고쳤다** (R2):
- `ApprovalController:103` — 외부 진입. 호출자 사번 전달
- `ApprovalCommandService:103` — `draft` 내부. `draft`가 받은 `memberId`를 그대로 넘긴다
  (**이중 검증**이 되지만 D4로 허용. 비용은 이미 로드된 엔티티의 int 비교 1회)

### [A4] `POST /approvals` 기존번호 분기 — 기안자 본인 확인 ★★

| 위치 | Before | After |
|---|---|---|
| `ApprovalController:114~119` | 옛 L112가 `setMemberId(getCurrentMemberId())` | **유지** + `memberId`를 파라미터로도 전달 |
| `ApprovalCommandService:77` | `existing`을 로드하지만 소유자 확인 없음 | `draft(ApprovalDTO, List<MultipartFile>, int memberId)` |

```java
if (requestedApprovalNo != null && !requestedApprovalNo.isBlank()) {
    Approval existing = approvalRepository.findById(requestedApprovalNo).orElse(null);

    if (existing != null && existing.getApprovalStatus() == ApprovalStatus.TEMP_SAVED) {

        //남의 임시저장을 이어받는 것을 막는다. 상신이든 재저장이든 갈래를 가리기 "전"에 판정해야
        //두 경로가 함께 닫힌다.
        if (existing.getMemberId() != memberId) {              // ★ CommandService:93
            throw new BusinessException(ErrorCode.APPROVAL_UNAUTHORIZED);
        }

        if (initialStatus == ApprovalStatus.PROCESSING) {
            //resubmit 은 private 이고 이 지점에서 이미 기안자 본인이 확인됐으므로 추가 검증을 두지 않는다
            return resubmit(requestedApprovalNo, approvalDTO, files);
        }
        return resaveTempSaved(requestedApprovalNo, approvalDTO, files, memberId);
    }
}

//신규 기안 : 기안자가 곧 호출자라 대조할 상대가 없다. 여기에 소유자 검증을 넣으면 안 된다.
//임시저장이면 양식번호를 ims 로 대체해 채번한다
```

- **R7 준수** — 신규 채번 분기(`CommandService:108~132`)에는 검증을 **넣지 않았다.** 그 경로의
  `memberId`는 언제나 호출자 자신이라 비교 대상이 없다. 넣었다면 **모든 신규 기안이 죽는다** → **S0-a가 판정**
- **R8 준수** — 검증이 `if (initialStatus == PROCESSING)` **바깥·앞**이다. 안쪽에 넣었다면
  `POST + "임시저장"` 갈래(`resaveTempSaved`)가 열린 채 S0~S8을 전부 통과했을 것이다 → **S4-b가 유일한 판정**
- `resubmit`(private)에는 추가 검증을 넣지 않았고, **주석으로 그 이유를 명시**했다(§4 [A4] 지시)

### Controller — `getCurrentMemberId()` 관용구 통일

`updateApprovalTemp`·`insertApproval`은 §5가 "**기존 추출값** 인자 전달"로 규정했으므로,
`getCurrentMemberId()`를 두 번 호출하지 않고 지역 변수로 한 번만 뽑아 `setMemberId`와 인자에 함께 쓴다.
**Controller에 이미 있던 관용구**(`selectApprovalList:70`의 `int memberId = getCurrentMemberId();`)와 같은 형태다.

```java
int memberId = getCurrentMemberId();

approvalDTO.setApprovalNo(approvalNo);
approvalDTO.setMemberId(memberId);          // D2·D8 — 유지. createChildren 이 _apr000 생성에 쓴다

ApprovalDTO result = approvalCommandService.resaveTempSaved(approvalNo, approvalDTO, multipartFile, memberId);
```

`updateApprover`·`deleteApproval`은 인증 추출이 **처음 생기는** 자리라 인자 위치에서 직접 호출했다.
`selectApprovalList`(L70)·`updateApprovalstatus`(L88)·`getCurrentMemberId()` 헬퍼는 **무변경**이다.

---

## 3. 의도된 동작 변경

### 3-1. 실패 경로 신설 — 제3자 쓰기 4종 (본 작업의 목적)

| 엔드포인트 | Before (제3자 토큰) | After |
|---|---|---|
| `PUT /approvers/{no}` | **200** — 문서 완결/즉시 반려, 처리자는 지정 결재자 이름으로 기록 | **403 / AP003** |
| `DELETE /approvals/{no}` | **200** — `PROCESSING` 문서도 실제 삭제 | **403 / AP003** |
| `PUT /approvals/{no}` | **200** — 제목·본문·양식·결재선·참조선 전량 덮어쓰기 | **403 / AP003** |
| `POST /approvals` 기존번호 | **200** — 남의 임시저장을 그 사람 이름으로 상신 | **403 / AP003** |

**정상 경로(본인·지정 결재자)의 성공 응답 JSON은 구조·값 모두 불변**이다. 서비스 시그니처 변경은
내부이며 응답 DTO는 그대로다. `delete`의 반환 타입 `boolean`도 유지해 `ResponseMessage<Boolean>`이 바뀌지 않는다(D7).

### 3-2. ⚠ 성공 → 실패 전환 1건 — 없는 번호 삭제 (D11 / R3)

**D7의 유일한 예외다.**

`ApprovalRepository:46~49`가 `deleteById`를 **벌크 JPQL DELETE로 재정의**하고 있어, 매칭 행이 없어도
0행 삭제로 조용히 끝났다. [A2]가 `findById`를 앞에 세우면서 이 경로가 404가 된다.

```java
@Modifying @Transactional
@Query("DELETE FROM Approval a WHERE a.approvalNo = :approvalNo")
void deleteById(@Param("approvalNo") String approvalNo);
```

**before (2026-07-31 실측, 변경 전 — 사용자 Postman)**

```
DELETE /approvals/2026-zzz99999
  → {"status":200,"message":"전자결재 삭제 성공","data":true}
```

**after (기대 — S7에서 확인)**

```
DELETE /approvals/2026-zzz99999
  → 404 / AP001  "해당 결재를 찾을 수 없습니다."
```

**수용 근거**(D11): 채택하지 않은 대안 (b)`orElse(null)`은 **"없는 것을 지웠는데 성공을 반환"**하는 형태다.
단계 1.5에서 가장 크게 데인 "조용한 성공/0건"과 같은 계열이고, 단계 7이 `receivedAll`을 400으로
**드러나게** 한 판단과도 어긋난다. 프론트 영향도 낮다 — `deleteApprovalAPI`는 에러를 `console.log`만 하고
화면 반응이 없어 404든 200이든 사용자에게 보이는 차이가 없다.

### 3-3. 부수 효과 1건 — 잘못된 상태 문자열의 500 → 403

[A1]의 신원 검증이 `ApproverStatus.from()`보다 앞서게 되어, **제3자**가 잘못된 `approverStatus`를
보냈을 때 500/C999(Stage 6 §8 관찰 5)가 아니라 403/AP003이 난다. **의도한 효과는 아니지만 방향이 옳다.**
지정 결재자 본인이 잘못된 값을 보내면 여전히 500/C999다 — 그 경로는 이번 범위 밖이며 이월된다.

---

## 4. 전제 — 이 검증이 무엇 위에 서 있는가 (R1) ★

> **이 작업의 신뢰 기반은 토큰의 무결성이며, 현재 그것이 보장되지 않는다.**

```
application.yml:44   jwt.key: ‹평문 커밋됨›
application.yml:45   jwt.time: 86400000   (24h, 블랙리스트 없음)
```

**백엔드·프론트 두 리포 모두 Public이다.** 이 키를 아는 사람은 임의 사번·임의 `role` 토큰을 위조할 수 있고,
그러면 이번에 넣은 `approver.getMemberId() != memberId` 검증은 **위조된 사번과 비교하게 된다.**
**인가는 인증의 무결성 위에서만 성립한다.**

**그럼에도 작업 A가 무의미한 것은 아니다.**
- 키를 모르는 **정상 사용자의 수평적 권한 상승을 막는다** — 유효한 토큰 하나로 전사 결재를 승인·반려·
  수정·삭제할 수 있던 경로가 닫힌다
- **감사 기록을 지킨다** — 처리자가 실제 호출자와 일치하게 된다

→ **작업 B(`jwt.key` + `PUT /resetPassword/{memberId}`)를 A 직후에 착수해야 한다.**
다만 **배포된 인스턴스가 없고 배포 예정도 없어**(2026-07-31 확인) 실질 위험은 0이며,
성격은 "배포 전에 갚아야 할 부채"다(`spec.md` §4-1).

---

## 5. 빌드 · 검증 결과

```
> cd final; .\gradlew.bat compileJava
> Task :compileJava
BUILD SUCCESSFUL in 3s
1 actionable task: 1 executed
```

```
> cd final; .\gradlew.bat compileTestJava --rerun-tasks
> Task :compileJava
Note: ...\auth\config\WebSecurityConfig.java uses or overrides a deprecated API.
Note: Some input files use unchecked or unsafe operations.
> Task :compileTestJava
BUILD SUCCESSFUL in 11s
3 actionable tasks: 3 executed
```

> 위 `Note:` 2줄은 **`auth/**`의 기존 경고**이며 이번 변경과 무관하다(무변경 파일).
> 단계 7 보고서 §5에도 같은 2줄이 기록돼 있다.

### P3 → P4 순서가 실제로 한 일 (R2 안전장치)

서비스 시그니처를 먼저 바꾸고 컴파일한 결과, **호출부 4곳이 전부 컴파일 에러로 드러났다.**

```
ApprovalController.java:101: error: method resaveTempSaved in class ApprovalCommandService cannot be applied to given types;
ApprovalController.java:115: error: method draft in class ApprovalCommandService cannot be applied to given types;
ApprovalController.java:127: error: method processApprover in class ApprovalCommandService cannot be applied to given types;
ApprovalController.java:135: error: method delete in class ApprovalCommandService cannot be applied to given types;
```

**명세 §0의 호출부 표(101·115·127·135)와 정확히 일치**하며, `ApprovalController` 외의 파일은 없다.
`resaveTempSaved`의 두 번째 호출부(`CommandService:104`, `draft` 내부)는 같은 파일 안이라 P3에서 함께 고쳤다.
**반대 순서였다면 컴파일러가 아무것도 알려주지 않았을 자리다.**

### bootRun

포트 8080을 **IntelliJ에서 사용자가 직접 띄운 같은 앱 인스턴스**(PID 5204, 2026-07-31 10:50 기동,
`final/build/classes/java/main` 사용)가 점유 중이었다. P2 기준선 캡처·D11 before 실측에 쓰였을 수 있어
**임의로 종료하지 않았고**, 사용자 판단으로 **사용자가 직접 재기동**하는 것으로 정리했다.

→ **사용자가 재기동해 정상 기동을 확인했다** (2026-07-31). 이후 §6-1의 수동 검증이 이 인스턴스에서 수행됐다.
`compileJava` 통과와 기동 성공은 **그 자체로 아무것도 증명하지 않는다**(R11) — 근거는 §6-1이다.

### 검색 확인 5종 (§10 원문 — PowerShell 5.1)

```powershell
cd final
$approval   = Get-ChildItem -Path .\src\main\java\com\insider\login\approval -Filter *.java -Recurse
$controller = Get-ChildItem -Path .\src\main\java\com\insider\login\approval\controller -Filter *.java -Recurse
$command    = Get-ChildItem -Path .\src\main\java\com\insider\login\approval\service -Filter ApprovalCommandService.java -Recurse
```

**#1. `APPROVAL_UNAUTHORIZED` — 기대 4건 / 결과 4건 ✅**

```
ApprovalCommandService.java:93:  throw new BusinessException(ErrorCode.APPROVAL_UNAUTHORIZED);   ← [A4] draft
ApprovalCommandService.java:149: throw new BusinessException(ErrorCode.APPROVAL_UNAUTHORIZED);   ← [A3] resaveTempSaved
ApprovalCommandService.java:214: throw new BusinessException(ErrorCode.APPROVAL_UNAUTHORIZED);   ← [A1] processApprover
ApprovalCommandService.java:260: throw new BusinessException(ErrorCode.APPROVAL_UNAUTHORIZED);   ← [A2] delete
```

**AP003은 선언만 있고 사용처가 0건이었다 — 이번에 활성화됐다**(R9. 실측 확인은 S1~S4).

**#2. `getCurrentMemberId` (controller) — 쓰기 5종 전부 ✅**

```
ApprovalController.java:70:  int memberId = getCurrentMemberId();                                             ← 목록 조회 (기존)
ApprovalController.java:88:  approvalCommandService.withdraw(approvalNo, getCurrentMemberId())));             ← 회수 (기존)
ApprovalController.java:98:  int memberId = getCurrentMemberId();                                             ← 재임시저장 [A3]
ApprovalController.java:114: int memberId = getCurrentMemberId();                                             ← 기안 [A4]
ApprovalController.java:131: approvalCommandService.processApprover(approverNo, approverDTO, getCurrentMemberId())));  ← 결재처리 [A1] 신규
ApprovalController.java:139: approvalCommandService.delete(approvalNo, getCurrentMemberId())));               ← 삭제 [A2] 신규
ApprovalController.java:179: private int getCurrentMemberId() {                                               ← 정의
```

**쓰기 5종(회수·재임시저장·기안·결재처리·삭제) 전부에 인증 추출이 있다.** 추출 지점은 여전히 헬퍼 1곳이다.

**#3. `approvalDTO.getMemberId()` (CommandService) — 2건, 전부 권한 판단이 아님 ✅**

```
ApprovalCommandService.java:114: .memberId(approvalDTO.getMemberId())    ← 신규 채번 시 Approval 의 기안자 세팅
ApprovalCommandService.java:323: .memberId(approvalDTO.getMemberId())    ← createChildren 의 _apr000 생성 (D8 — 손대지 않음)
```

**둘 다 값을 저장하는 자리이며 권한을 판단하는 자리가 아니다**(D2 준수).
권한 판단 4곳은 전부 `existing.getMemberId()` · `approval.getMemberId()` · `approver.getMemberId()`,
즉 **엔티티에서 읽은 값**과 파라미터 `memberId`를 비교한다.

**#4. `ErrorCode.java`의 `AP012` — 기대 0건 / 결과 0건 ✅** (`ErrorCode.java` 무변경, D6)

**#5. 범위 이탈 확인 — 코드 2파일 ✅** (§1의 `git diff --stat` 참조. 나머지 4개는 §9 문서)

> ⚠ **`compileJava`·`compileTestJava` 통과와 기동 성공은 아무것도 증명하지 않는다** (R11).
> **인증 경로 변경은 컴파일·기동에 전혀 드러나지 않는다.** 단계 1.5·단계 7이 같은 교훈을 남겼다.
> **아래 §6 수동 검증이 이 작업의 본체다.**

---

## 6. 📋 수동 검증 체크리스트 (사용자 담당 — `01-write-authz.md` §10 원문)

> **화면으로 검증할 수 없다** (R5). Postman 등으로 수행한다.
> 계정 3개: **A**=기안자 / **Z**=지정 결재자 / **B**=무관한 제3자.
> 시작 전 **B의 제3자 지위를 확정**한다 — `given` 0건, `receivedRef` 0건, 대상 문서의 `approver[].memberId`에 B 없음.
> **판정은 응답 코드로 한다** — 프론트는 403을 삼킨다(D10).

---

- [ ] **S0. 정상 경로 비회귀** ★★ **가장 중요. 먼저 한다**

      하나라도 실패하면 기능이 죽은 것이다. 특히 **S0-a는 R7의 판정**이다.

      | # | 시나리오 | 기대 |
      |---|---|---|
      | **S0-a** | **A가 신규 기안** (`approvalNo` 없이, `"처리 중"`) | **200** ← R7. 여기서 403이면 신규 채번 분기에 검증을 잘못 넣은 것 |
      | S0-b | A가 임시저장 → 같은 화면에서 재임시저장 (`PUT`) | 200, 내용 갱신 |
      | S0-c | A가 임시저장 → 기안 전환 (`POST` + 자기 `approvalNo` + `"처리 중"`) | 200, **결재번호 유지**, `PROCESSING` |
      | S0-d | A가 임시저장을 두 번 저장 (`POST` + 자기 `approvalNo` + `"임시저장"`) | 200, **상신되지 않음** (`TEMP_SAVED` 유지) |
      | S0-e | **Z가 자기 결재선 승인** (`PUT /approvers/{Z의 approverNo}`) | **200**, 상태 전이 정상 |
      | S0-f | Z가 반려 | 200, `REJECTED` |
      | S0-g | A가 자기 결재 회수 | 200 (AP008 비회귀) |
      | S0-h | A가 자기 결재 삭제 | 200 |

      > ✅ **S0-h 사전 조건 충족 (2026-07-31 화면 실측)**: 삭제 버튼은 **`임시저장함(tempGiven)`에만** 있다.
      > `내 결재함(given)`·`결재 대기함(received)`·`수신 참조함(receivedRef)`에는 **없다.**
      > → 소유자 검증이 정상 UI 흐름을 막지 않는다.
      > ⚠ 단, 이는 **회귀 부재의 근거일 뿐 정책의 근거가 아니다**(v3 ⚠). 검증 중 삭제 버튼이 예상 밖 화면에서
      > 발견되면 **중단·보고**.

---

- [ ] **S1. [A1] 제3자 결재 처리 차단** ★
      B 토큰으로 `PUT /approvers/{Z의 approverNo}` → **403 + `"AP003"`**

      > ⚠ **사전 조건**: Z의 `approverNo`는 반드시 **`PENDING`** 이어야 한다.
      > 이미 처리된 건이면 검증을 상태 검증 **뒤에** 잘못 배치해도 400/AP005가 나와
      > "차단됨"으로 오판하게 된다. **응답 코드가 AP005면 검증 배치가 틀린 것이다.**

- [ ] **S2. [A2] 제3자 삭제 차단** ★
      B 토큰으로 `DELETE /approvals/{A의 결재}` → **403 + `"AP003"`**, 문서가 **남아 있는지** 재조회로 확인

- [ ] **S3. [A3] 제3자 재임시저장 차단** ★
      B 토큰으로 `PUT /approvals/{A의 임시저장}` → **403 + `"AP003"`**
      → 재조회해서 **제목·본문·결재선·첨부가 그대로**인지 확인
      (막혔는데 자식이 지워졌으면 검증 위치가 디스크 삭제 뒤에 있는 것이다)

- [ ] **S4. [A4] 제3자 상신·덮어쓰기 차단** ★★ **두 방향 모두 필수**

      | # | 요청 | 기대 | 이 항목이 잡는 것 |
      |---|---|---|---|
      | **S4-a** | B 토큰 `POST /approvals` + A의 임시저장 `approvalNo` + **`"처리 중"`** | **403 + AP003** | `resubmit` 갈래 |
      | **S4-b** ★ | B 토큰 `POST /approvals` + A의 임시저장 `approvalNo` + **`"임시저장"`** | **403 + AP003** | **`resaveTempSaved` 갈래.** 검증을 `if (initialStatus == PROCESSING)` 안쪽에 넣었으면 **여기서만 200이 난다** (R8) |

      → 둘 다 재조회해서 **`TEMP_SAVED` 유지**, 제목·본문 불변 확인

- [ ] **S5. 회수 비회귀**
      B 토큰으로 `PUT /approvals/{A의 결재}/status` → **403 + `"AP008"`** (기존 동작, 바뀌면 안 된다)

---

- [ ] **S6. [D8] `_apr000` 무결성** ★ **PUT·POST 양쪽**

      | # | 시나리오 | 확인 |
      |---|---|---|
      | **S6-a** | A가 자기 임시저장을 재저장 (`PUT`) | 재조회 시 **`approver[0].memberId` == `approval.memberId` == A** |
      | **S6-b** | A가 자기 임시저장을 기안 전환 (`POST` + `"처리 중"`) | 동일 (`resubmit` 경로 — §3-1이 미실측으로 남긴 지점) |

      (현행은 재저장 주체가 `_apr000`에 덮인다. 소유자 검증으로 해소돼야 한다 — §3-1)

- [ ] **S7. 기존 에러코드 비회귀**

      | 요청 | 기대 |
      |---|---|
      | 없는 `approverNo` | 404 / AP004 |
      | 이미 처리된 결재자 재승인 (본인이) | 400 / AP005 |
      | 없는 `approvalNo` 재임시저장 | 404 / AP001 |
      | 본인의 `TEMP_SAVED` 아닌 결재 수정 | 400 / AP011 |
      | **없는 `approvalNo` 삭제** | **404 / AP001** ← D11. **before는 `200/true`였다.** 보고서에 명시 |

- [ ] **S8. 응답 형식 동등성**
      **P2에서 캡처한 기준선**과 대조한다. 성공 경로의 JSON **구조·값이 동일**해야 한다.
      - 상세 조회 / 목록 5종 / 삭제(`ResponseMessage<Boolean>`) / 결재 처리 응답의 `ApproverDTO`
      - 무첨부·첨부 2건 기안
      - **예외는 D11 1건뿐**이다. 그 외 차이가 나오면 회귀다

---

## 6-1. ✅ 수동 검증 결과 (2026-07-31 · Postman · 사용자 수행)

계정: **A**=123 김동환(기안자) / **Z**=240501629 이진아(지정 결재자) / **B**=240501544 김사원(제3자)
프론트가 403을 삼키므로(R4/D10) **판정은 전부 응답 코드로** 했다.
검증 전용 문서를 새로 만들어 수행했고, R10 오염 문서는 대상에서 제외했다.

| # | 시나리오 | 기대 | 결과 |
|---|---|---|---|
| **S0-a** ★★ | A 신규 기안 (`approvalNo: null`) | 200 | ✅ **200 / `PROCESSING`** — R7 판정 통과 |
| S0-b | A 재임시저장 (`PUT`) | 200 | ✅ |
| S0-c | A 기안 전환 (`POST` + 자기 번호 + `"처리 중"`) | 200, 번호 유지, `PROCESSING` | ✅ |
| S0-d | A 임시저장 재저장 (`POST` + 자기 번호 + `"임시저장"`) | 200, `TEMP_SAVED` 유지 | ✅ 상신되지 않음 |
| S0-e | **Z**가 자기 결재선 승인 | 200 | ✅ |
| S0-f | Z가 반려 | 200, `REJECTED` | ✅ |
| S0-g | A가 자기 결재 회수 | 200 | ✅ |
| S0-h | A가 자기 임시저장 삭제 | 200 | ✅ |
| **S1** | B가 결재 처리 (대상 `PENDING` 확인 후) | 403 / AP003 | ✅ |
| **S2** | B가 삭제 | 403 / AP003, 문서 잔존 | ✅ |
| **S3** | B가 재임시저장 | 403 / AP003, 제목·본문 불변 | ✅ |
| **S4-a** | B가 `POST` + A의 임시저장번호 + `"처리 중"` | 403 / AP003 | ✅ |
| **S4-b** ★★ | B가 `POST` + A의 임시저장번호 + **`"임시저장"`** | 403 / AP003 | ✅ **R8 판정 통과** |
| **S5** | B가 회수 | 403 / **AP008** | ✅ 기존 동작 유지 |
| **S6-a** | A가 자기 임시저장 재저장 후 `_apr000` | 기안자와 일치 | ✅ |
| **S6-b** | A가 자기 임시저장 기안 전환 후 `_apr000` | 기안자와 일치 | ✅ |
| **S7** | 기존 에러코드 비회귀 (AP004·AP005·AP001) | 각 코드 | ✅ D11 포함 |
| **S8** | P2 기준선과 응답 동등성 | 구조·값 불변 | ✅ `receivedAll` **400 유지** |

**전 항목 통과. 미수행 0건.**

### S0-a — R7 판정 (신규 채번 분기에 검증을 넣지 않았음이 실증됐다)

```json
{"status":200,"message":"전자결재 기안 성공",
 "data":{"approvalNo":"2026-con00022","memberId":123,"approvalStatus":"PROCESSING",
         "approver":[{"approverNo":"2026-con00022_apr000","approverOrder":0,
                      "approverStatus":"APPROVED","memberId":123}, ...]}}
```

여기서 403이 났다면 신규 채번 분기에 소유자 검증을 잘못 넣은 것이고, **모든 신규 기안이 죽었을 것이다.**

### S4-b — R8 판정 (이 배치를 잡는 유일한 항목) ★★

```json
{"status":403,"code":"AP003","message":"해당 결재에 대한 권한이 없습니다."}
```

검증이 `if (initialStatus == ApprovalStatus.PROCESSING)` **안쪽**에 들어갔다면
`resaveTempSaved` 갈래가 열린 채 **S0~S8의 나머지를 전부 통과**했을 자리다.
**`POST` 두 갈래(상신·재저장)가 함께 닫혔음이 실측으로 확인됐다.**

### S6 — D8 실증 (`_apr000` 덮어쓰기 소멸)

| 문서 | 경로 | `approval.memberId` | `approver[0].memberId` |
|---|---|---|---|
| `2026-ims00019` | `PUT` (재임시저장) | 123 | **123** ✅ |
| `2026-ims00020` | `POST` + `"처리 중"` (`resubmit`) | 123 | **123** ✅ |

소유자 검증이 `dto.memberId`(호출자) == `approval.memberId`(기안자)를 보장하므로,
§3-1의 **기안자 불일치가 도달 불가**해졌다. `createChildren`을 손대지 않고 해소됐다(D8 논리 성립).

> 부수 확인: `2026-ims00020`이 **번호를 유지한 채** `PROCESSING`으로 전환됐다.
> Stage 6 D2=L1(임시저장→기안 전환 시 번호 유지)의 **비회귀**도 함께 확인됐다.

### D11 — 없는 번호 삭제의 before / after ⚠

```
before (2026-07-31, 변경 전)
  DELETE /approvals/2026-zzz99999
  → {"status":200,"message":"전자결재 삭제 성공","data":true}

after (2026-07-31, 변경 후)
  DELETE /approvals/2026-zzz99999
  → {"status":404,"code":"AP001","message":"해당 결재를 찾을 수 없습니다."}
```

**D7의 유일한 예외**이며 의도된 전환이다(§3-2).
`ApprovalRepository:46~49`의 벌크 JPQL DELETE가 0행에도 조용히 성공하던 경로가 닫혔다.

---

### 검증 시 주의 — 오염된 개발 데이터 (R10)

검증 과정에서 오염된 데이터가 개발 DB에 남아 있다. **S6 대상으로 쓰면 before/after 판정이 흐려진다.**

| 문서 | 상태 |
|---|---|
| `2026-ims00014` | 제3자(240501629) PUT으로 `approver[0].memberId`가 **호출자로 덮임** (최상위는 123 유지) |
| 임시저장함의 `"이진아로 X2 put 요청"` **2건** (00:25:27 · 00:32:38) | 동일 — `_apr000`이 호출자로 덮인 상태 |

→ **최상위 `memberId`와 `approver[0].memberId`가 다른 문서가 최소 3건 있다.**
정리 여부는 사용자 판단이다. S6은 **새로 만든 임시저장**으로 수행했다(§6-1).

**이번 검증(§6-1)이 추가로 남긴 문서** — 전부 정상 데이터이며 오염분이 아니다.
`2026-con00022` 외 검증 전용 기안·임시저장 약 20건. 화면 정리가 필요하면 삭제해도 무방하다.

### 프론트가 403을 삼킨다 (R4 / D10) — 알려진 결과

`ApprovalAPI.js:211~214`에 `throw`가 없어 **서버가 403을 내도 "결재 처리 완료" 모달이 뜬다.**
**신규 결함이 아니다** — AP004·AP005도 지금 같은 경로로 삼켜진다. 프론트는 수정하지 않는다.
→ **검증 판정은 화면이 아니라 응답 코드로 한다.**

---

## 7. 범위 외 — 손대지 않은 항목 (원문 유지 확인)

- 🚫 **`common/error/ErrorCode.java`** — **무변경.** AP003 재사용, 신규 0건 (D6). 다음 여유 번호는 AP012지만 쓰지 않았다
- 🚫 **`auth/**` 전체 미개봉** — 특히 `JwtAuthorizationFilter`의 `roleLessList:57`.
  8번째 원소가 `"/announces/{ancNo}, /approvals"`로 **쉼표가 따옴표 안에** 있어 어떤 URI에도 매칭되지 않고,
  **결과적으로 `/approvals`가 인증 필요 상태로 유지되고 있다. 현재 동작이 옳다.**
  "고치면" `/approvals`가 무인증으로 열려 훨씬 위험해진다. **파일을 열지 않았다**
- 🚫 **`enums/ApprovalStatus.java`의 `description`·`from()` 한글 매칭** — 무변경.
  프론트가 `"임시저장"`·`"처리 중"`을 보내므로 제거하면 기안이 전부 실패한다
- 🚫 **DTO의 Enum 타입 전환** — 봉인. Jackson 기본 역직렬화는 `name()`만 매칭한다
- **`createChildren`의 `_apr000` 생성 방식**(D8) — 무변경. 소유자 검증으로 §3-1의 기안자 불일치가
  **도달 불가**해지므로, 여기서 더 고치면 "온 김에"가 된다. **해소 여부는 S6에서 확인**
- **`DELETE`의 상태 가드**(D5) — 넣지 않았다. §8 잔여 위험 참조
- **`ApprovalQueryService`·`service/file/**`·`service/generator/**`·`entity/**`·`dto/**`·`repository/**`** — 무변경
- **`member/**`·`insite/**`·`src/test/**`·프론트엔드·DB 스키마** — 무변경
- **작업 B**(`jwt.key`, `PUT /resetPassword/{memberId}`) · **작업 C**(응답 password 4지점 + 로그 위생) ·
  **작업 E**(읽기 경로 인가) · **작업 D**(등재만) — 전부 이번 범위 밖

### 프론트 근거의 출처 — 이 리포에서 검증할 수 없다

본 보고서와 명세가 인용하는 `ApprovalDetail.js:121·129·198·222·265` · `ApprovalAPI.js:211~214`,
그리고 **"대결·위임·강제승인 화면 0건"** 판정은 **프론트 리포**(`LOG-IN-F-Refactoring`, `main`, `8c13156`)
소스 조사 결과다. **이 리포에는 프론트가 없으므로 여기서는 확인할 수 없다.**

또한 그 리포는 **리팩토링 커밋이 하나도 없는 2024-05-31 스냅샷**이고 죽은 라우트·오타 URL·에러 삼킴이
확인됐다. **프론트 관찰은 "지금 되던 게 안 되지는 않는다"(회귀 부재)에만 쓰고, "이것이 옳은 정책이다"의
근거로 쓰지 않는다**(§13 v3 ⚠).

---

## 8. 잔여 위험 · 관찰 (기록만 — 고치지 않았다)

### 🔴 잔여 위험 1 — 소유자 본인의 감사 기록 파괴 (D5)

**소유자 검증만으로는 기안자 본인이 자기 `APPROVED`·`REJECTED` 문서를 API로 직접 삭제하는 것을 막지 못한다.**
감사 기록 파괴라는 점에서 이번 작업과 같은 계열이고, **동기가 있는 쪽은 오히려 본인**이다.

이번에 상태 가드를 넣지 않은 이유는 **정책 결정이기 때문**이다.
- **결재 비즈니스 정책 변경**은 `spec.md` §7이 범위 밖으로 명시했다
- **허용 상태의 정의 자체가 결정 사항**이다. `TEMP_SAVED`만 허용하면 **회수한 문서(`WITHDRAWN`)도 못 지운다.**
  기안 → 회수 → 삭제는 자연스러운 흐름이다. 그러면 `+WITHDRAWN`은? `+REJECTED`는?
  **이 질문의 답은 코드에도 화면에도 없다.**

> 소유자 가드와의 비대칭: **소유자 가드는 결함을 고치는 것**(비소유자에게 삭제 버튼이 뜬다면 그것 자체가 결함)이고,
> **상태 가드는 정책을 추가하는 것**(소유자가 자기 `PROCESSING` 문서를 지우는 것은 정당할 수 있다)이다.
> → **삭제 가능 상태 정의는 후속 결정 사항이다.**
> 참고: `APPROVAL_INVALID_STATUS_TRANSITION`(**AP002**, 400)이 이미 있으므로 **신규 상수는 필요 없다.**

### 🔴 잔여 위험 2 — 인가는 인증 위에서만 성립한다 (R1)

§4 참조. **작업 B가 A 직후에 와야 하는 이유다.**

### 관찰 1 — `POST`에 남의 **비임시저장** 번호를 실으면 신규 채번으로 흘러간다

`existing != null`이지만 상태가 `TEMP_SAVED`가 아니면 조건문 밖으로 빠져 **신규 채번 분기**로 간다.
즉 남의 `PROCESSING` 결재번호를 실어 보내면 그 문서를 건드리지 않고 **호출자 소유의 새 결재가 생긴다.**

**보안 문제가 아니다** — 남의 문서는 변경되지 않고, 새로 생기는 문서의 기안자는 호출자 자신이다.
**기존 동작이며 이번 변경과 무관하다.** 명세가 지시하지 않았으므로 손대지 않았다.

### 관찰 2 — `resaveTempSaved`의 이중 검증 (D4로 허용)

`draft → resaveTempSaved` 경로에서 소유자 검증이 두 번 일어난다.
**각 public 메서드가 독립적으로 안전한 것이 우선**이다 — `resaveTempSaved`는 Controller에서도 직접 진입한다.
비용은 이미 로드된 엔티티의 int 비교 1회다.

### 관찰 3 — `processApprover`의 `ApproverStatus.from()` 500 경로가 일부만 닫혔다

§3-3 참조. **제3자**의 잘못된 상태 문자열은 이제 403이지만, **지정 결재자 본인**의 잘못된 값은
여전히 500/C999다(Stage 6 §8 관찰 5). 이번 작업의 목적이 아니므로 손대지 않았다. **이월.**

### 관찰 4 — `spec.md` §4-3에 등재된 `insite` 무성 0건 의심은 확인하지 않았다

`InsiteRepository:34·37`이 `'처리 중'`·`'대기'` 한글 리터럴로 비교한다는 등재 내용은
**작업 D 소관이고 `insite/**`는 §5 금지 범위**라 **파일을 열지 않았다.** DB 실측이 필요하다.

### 관찰 5 — `finalApproverDate`가 상태에 따라 반대로 나온다 (검증 중 발견)

§6-1의 응답을 나란히 놓으면 이렇다.

| 문서 | 상태 | `finalApproverDate` |
|---|---|---|
| `2026-ims00019` | `TEMP_SAVED` | `"2026-07-31 13:53:49"` (= `_apr000`의 일시) |
| `2026-con00022` · `2026-ims00020` | `PROCESSING` | `""` |

**구조가 같은 문서인데 결과가 반대다.** 임시저장은 아직 아무도 결재하지 않았으므로 오히려
값이 비어야 자연스럽고, 진행 중 문서에 값이 없는 것은 맞다.

`ApprovalQueryService`는 **이번 작업에서 무변경**이므로 **기존 동작이며 이번 변경과 무관하다.**
완료 보고서 §4에 이미 이월된 `finalApproverDate` 의미 어긋남([B] 이후 순서가 자유로워지며 발생)의
한 갈래로 보인다. **기록만 하고 손대지 않았다.**

---

## 9. 다음 단계 (사용자)

1. ~~**`bootRun` 재기동**~~ ✅ 완료 (§5)
2. ~~**수동 검증 S0~S8**~~ ✅ **전 항목 통과, 미수행 0건** (§6-1)
3. **커밋 2분할** — 파일을 섞지 않는다
   - ① **코드**: `ApprovalCommandService.java`, `ApprovalController.java`
   - ② **문서**: `docs/security/**`(spec·task·이 보고서), `AGENTS.md`, `CLAUDE.md`,
     `completed/approval-domain.md`, `reports/07-controller-report.md`
   - 커밋 전 `git diff --cached --stat`으로 ①에 `.java`가 **2개뿐**인지 확인
4. 푸시 → **작업 A 완료**
5. 이후 **작업 B** 착수 여부 결정 (`jwt.key` 평문 커밋 + `PUT /resetPassword/{memberId}` 인가 부재).
   배포된 인스턴스가 없어 실질 위험은 0이며, **공개 리포에 서명 키가 남아 있다는 점**이 착수 근거다

### 작업 A 최종 상태

| 지표 | Before | After |
|---|---|---|
| 신원 검증이 있는 쓰기 엔드포인트 | 5종 중 **1종** (회수) | **5종 전부** ✅ |
| 제3자 토큰의 쓰기 성공 | 4종에서 200 | **전부 403 / AP003** ✅ |
| `APPROVAL_UNAUTHORIZED`(AP003) | 선언만, 사용처 **0건** | **4곳 활성** ✅ |
| 신규 ErrorCode | — | **0건** ✅ |
| 권한 판단의 입력 | DTO 필드 또는 없음 | **메서드 파라미터** ✅ |
| `_apr000`의 `memberId` | 재저장 시 호출자로 덮임 | **기안자와 일치** ✅ (S6 실증) |
| 응답 JSON | — | **불변** (D11 예외 1건) ✅ |
| 정상 경로 | — | **불변** ✅ (S0 8종 전부 통과) |

**명세 §1의 성공 기준 8항목 전부 달성.**
