# 단계 6: God Class 분리 (Command / Query) — 작업 보고서

> 작업일: 2026-07-29
> 실행: Claude Code (Opus 5)
> 명세: `docs/refactoring/approval/tasks/06-command-query.md` (§8 D1~D9 확정본)
> 선행: 단계 1·1.5·2·3·4·5 완료·커밋·푸시 (`80f60b7`(코드)·`0215951`(문서))

---

## 1. 변경 파일

| 파일 | 구분 | 라인 수 |
|---|---|---|
| `approval/service/ApprovalCommandService.java` | **신규** | 337 |
| `approval/service/ApprovalQueryService.java` | **신규** | 456 |
| `approval/repository/ApprovalRepositoryCustom.java` | **신규** | 29 |
| `approval/repository/ApprovalRepositoryImpl.java` | **신규** | 25 |
| `approval/service/ApprovalService.java` | **삭제** | 812 → 0 |
| `approval/controller/ApprovalController.java` | 수정 | 339 → 253 (-86) |
| `approval/entity/Approval.java` | 수정 | +14 (`modifyDraft`) |
| `approval/repository/ApprovalRepository.java` | 수정 | +1 (프래그먼트 상속) |
| `approval/repository/ApproverRepository.java` | 수정 | +3 (정렬 쿼리) |
| `common/error/ErrorCode.java` | 수정 | +2 (AP010·AP011) |

> 신규 서비스 2개는 성공 기준 "각 200~400줄" 범위 안이다(주석·빈 줄 포함 기준 337 / 456).
> `ApprovalFileService`·`ApprovalNoGenerator`·`enums/**`·`entity/Approver.java`·`dto/**`·`src/test/**` **무변경**.

### git diff --stat (추적 중인 파일)

```
 .../approval/controller/ApprovalController.java    | 126 +---
 .../insider/login/approval/entity/Approval.java    |  14 +
 .../approval/repository/ApprovalRepository.java    |   2 +-
 .../approval/repository/ApproverRepository.java    |   3 +
 .../login/approval/service/ApprovalService.java    | 812 ---------------------
 .../com/insider/login/common/error/ErrorCode.java  |   2 +
 6 files changed, 40 insertions(+), 919 deletions(-)
```

신규 4개 파일은 아직 untracked. **§6 Scope 밖 파일은 diff에 없다.**

---

## 2. 신규 컴포넌트

### ApprovalCommandService (@Service, 클래스 레벨 `@Transactional`)

**Spring의 `org.springframework.transaction.annotation.Transactional`** 을 쓴다(R5).
기존 `ApprovalService`는 `jakarta.transaction.Transactional`이었다.

| 메서드 | 원본 | 해결 결함 |
|---|---|---|
| `draft(ApprovalDTO, List<MultipartFile>)` | `insertApproval` + Controller 채번·조립·임시저장 분기 | [D][F][K][L] |
| `resaveTempSaved(String, ApprovalDTO, List<MultipartFile>)` | `updateApproval` + Controller 조립 루프 | [C][F][C-첨부] |
| `withdraw(String, int)` | `updateApprovalStatus` | **[A-잔여]** |
| `processApprover(String, ApproverDTO)` | `updateApprover` | [B][F] |
| `delete(String)` | `approvalDelete` | [G-비파일] + 중복 조회 통합 |
| (private) `resubmit` / `clearChildren` / `createChildren` / `resolveInitialStatus` | — | [L]·[C] 공통화 |

### ApprovalQueryService (@Service, 클래스 레벨 `@Transactional(readOnly = true)`)

| 메서드 | 원본 |
|---|---|
| `getApproval(String)` | `selectApproval` |
| `getApprovalList(int, Map, int)` | `selectApprovalList` |
| `getFormList()` / `getForm(String)` | `selectFormList` / `selectForm` |
| `getDepartList()` / `getMemberList(int)` | `selectDepartList` / `selectMemberList` |
| `getMember(int)` / `getAllMemberList()` | `selectMember` / `selectAllMemberList` |
| (private) `listToDTO` / `convertToMemberDTO` | `ListToDTO` / `convertToMemberDTO` (public → private) |

> **D8-a 단방향 주입**: `ApprovalCommandService` → `ApprovalQueryService`. 역방향 없음.
> QueryService의 `readOnly = true`는 **쓰기 트랜잭션에 참여할 때 무시된다**(이미 시작된 tx에 join하므로
> readOnly 속성이 적용되지 않는다). 따라서 Command 경로에서 `getApproval`을 불러도 dirty checking이 막히지 않는다.

### ApprovalRepositoryCustom / ApprovalRepositoryImpl (F-a)

```java
public interface ApprovalRepositoryCustom {
    void insert(Approval approval);        // em.persist  ← [F][K]
    void clearPersistenceContext();        // em.clear    ← R2
}
```

- `ApprovalRepository extends JpaRepository<Approval, String>, ApprovalRepositoryCustom`
- 구현체 클래스명은 **`ApprovalRepositoryImpl`** (R10). 서비스에 `EntityManager`를 노출하지 않는다(F-b 미채택 원칙 유지).
- `bootRun` 정상 기동이 곧 프래그먼트 인식 확인이다(§5 참조).

---

## 3. 결함별 처리

### [F] + [K] — `save()` merge 남용 (D1 = K1 + F-a)

| 위치 | 변경 |
|---|---|
| `insertApproval`의 `approvalRepository.save(approval)` | → **`approvalRepository.insert(approval)`** (persist) |
| `insertApproval`의 자식 save (approver/referencer) | **유지** (§3.5 — [K] 방어에 불필요) |
| `updateApproval`의 `save(updateApproval)` | **삭제** — `modifyDraft` + dirty checking |
| `updateApprover`의 `save(approver)` (detached 새 객체) | **삭제** — `findByApproverNo` → `approve()`/`reject()` → dirty checking |
| `updateApprover`의 `save(approval)` (DTO로 재구성한 새 객체) | **삭제** — `findById` → `markAsApproved()`/`markAsRejected()` → dirty checking |

**[K] 재시도는 넣지 않았다**(D1=K1 준수). `ApprovalNoGenerator` 무변경, `AP013` 미추가.

### [A-잔여] — 회수 시 타인 처리 검증 (AP009 활성화)

```java
boolean processedByOthers = approverRepository.findByApprovalNo(approvalNo).stream()
        .filter(approver -> approver.getApproverOrder() != SENDER_APPROVER_ORDER)   // 기안자 _apr000 제외
        .anyMatch(approver -> approver.getApproverStatus() != ApproverStatus.PENDING);
```

**기안자(`approverOrder == 0`) 제외를 반드시 확인할 것**(R9). `_apr000`은 생성 시점부터 `APPROVED`이므로
제외하지 않으면 **모든 회수가 차단된다.** → §6 S10-(a)에서 먼저 검증.

**검증 순서**: AP009(타인 처리) 확인 → `approval.withdraw(memberId)`(AP008 본인 확인 + AP002 전이 확인).
따라서 "본인이 아니면서 이미 처리된" 경우에는 **AP009가 먼저** 난다. 각각 단독 케이스는 기존과 동일.

### [B] — 결재 완료 판정 (D5)

- 인덱스 판정 `if (i == approverList.size() - 1)` **폐기**
- 승인 시 `findByApprovalNoOrderByApproverOrderAsc`로 전원 재조회 → `allMatch(APPROVED)`일 때만 `markAsApproved()`
- 반려는 순서 무관 즉시 `markAsRejected(reason)` (현행 유지)
- 정렬 쿼리 `findByApprovalNoOrderByApproverOrderAsc` 신설 — Query 경로의 결재선 순서에도 사용
- **명시적 `flush()` 없음** — §3.3에서 파생 쿼리의 auto-flush가 실증됐다
- **순서 강제(내 차례 아니면 승인 거부)는 넣지 않았다** — D5 확정, spec "결재 비즈니스 정책 변경" 범위 밖

### [C] — 수정이 삭제 후 재생성 (D4)

`Approval.modifyDraft(title, content, formNo)` 신설. `delete` + `new Approval(...)` + `save` 제거.

- **상태 가드가 두 겹**이다:
  1. `resaveTempSaved` 진입부 — `TEMP_SAVED`가 아니면 **디스크 파일을 지우기 전에** AP011
  2. `modifyDraft` 내부 — 방어용
  > 가드를 엔티티 안에만 두면 `clearChildren`(디스크 삭제 포함) **다음에** 걸린다.
  > tx 롤백은 DB만 되돌리고 `Files.deleteIfExists`는 되돌리지 못하므로 **첨부가 영구 유실된다.**
- **요청의 status는 읽지 않는다** — `canTransitionTo`에 `TEMP_SAVED → TEMP_SAVED`가 없어 반영하면 항상 실패한다
- `approvalDate`는 `modifyDraft` 내부에서 `now()`로 갱신 — 현행 `updateApproval` 동작 보존
- `rejectReason` / `memberId`는 건드리지 않는다 — 현행이 기존 값을 유지하던 것과 동일
- 자식(결재자·참조자·첨부)은 **전량 교체 유지**

### [C-첨부] — 재임시저장 시 옛 파일 orphan (D6)

`clearChildren()`에서 **디스크 삭제를 첨부 DB 행 삭제보다 먼저** 수행하도록 순서를 교정했다.

```java
approvalFileService.deleteByApprovalNo(approvalNo);   // 디스크 (내부에서 findByApprovalNo)
attachmentRepository.deleteByApprovalNo(approvalNo);  // DB 행
approverRepository.deleteByApprovalNo(approvalNo);
referencerRepository.deleteByApprovalNo(approvalNo);
approvalRepository.clearPersistenceContext();         // ← R2
```

**`ApprovalFileService` 자체는 무변경.** 호출 순서만 바꿨다.

### ⚠ R2 — 순서 교정이 만든 새 문제와 그 처리 (중요)

순서만 뒤집으면 **`StaleStateException`(500)이 난다.** 경로는 이렇다.

1. `approvalFileService.deleteByApprovalNo()`가 내부에서 `attachmentRepository.findByApprovalNo()`를 호출
   → **`Attachment` 엔티티들이 영속성 컨텍스트에 managed 상태로 올라온다**
2. `attachmentRepository.deleteByApprovalNo()`는 `@Modifying` 벌크 JPQL이라 **DB 행만 지우고
   영속성 컨텍스트의 엔티티는 남겨둔다**(`clearAutomatically` 기본값 false)
3. `approvalFileService.store()`가 같은 PK(`_f001`, `_f002` — 인덱스 기반 재채번)로 `save()`
   → merge가 **남아 있는 stale 엔티티**를 찾아 상태를 덮어씀 → flush 시 **이미 삭제된 행에 UPDATE**

첨부 2건 → 2건 재임시저장(= S11 시나리오)에서 결정적으로 실패한다.

**처리**: 자식 삭제 직후 `approvalRepository.clearPersistenceContext()`로 영속성 컨텍스트를 비우고,
`Approval`을 **재조회**해서 관리 상태로 만든 뒤 `modifyDraft` / `submitFromTempSaved`를 호출한다.

`@Modifying(clearAutomatically = true)`는 **채택하지 않았다** — `Approval`까지 detach시켜
`modifyDraft`의 dirty checking을 깨뜨린다.

### [D] — 임시저장 status 클라이언트 신뢰 (D3-a)

```java
private ApprovalStatus resolveInitialStatus(String requestedStatus) {
    ApprovalStatus status;
    try { status = ApprovalStatus.from(requestedStatus); }
    catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.APPROVAL_INVALID_INITIAL_STATUS); }
    if (status != ApprovalStatus.TEMP_SAVED && status != ApprovalStatus.PROCESSING) {
        throw new BusinessException(ErrorCode.APPROVAL_INVALID_INITIAL_STATUS);
    }
    return status;
}
```

- `from()`이 영문·한글을 모두 수용하므로 **`"임시저장"`과 `TEMP_SAVED` 둘 다 통과**한다
- `ims` 분기를 **Enum 판정**으로 재작성 → Controller의 한글 리터럴 `"임시저장"` 제거
  (**Stage 5 [관찰] 해소**: 영문 `TEMP_SAVED` 전송 시 `formNo=ims`가 안 붙던 문제)
- 판정 위치를 **CommandService로 이동** (Stage 5 D2의 "Controller 유지"를 이번에 변경)
- `from()`의 `IllegalArgumentException`을 **AP010으로 변환**(R8 전반). `null` 전송도 AP010이 된다(기존은 NPE→500)
- **`FORM_NO` 컬럼에는 실제 양식번호가 들어간다** — 채번용 `ims`는 결재번호에만 쓴다(현행 동작 그대로)

### [G-비파일] — 예외 삼키기

`approvalDelete`의 바깥 try 1개 + 안쪽 try 4개 **전부 제거**. 예외는 `GlobalExceptionHandler`로 전파된다.

- **첨부 중복 조회 통합**(`04-file-report.md` §5-3 이월): 가드용 `attachmentRepository.findByApprovalNo` 제거.
  `if (!attachmentList.isEmpty())` 가드도 함께 제거했다 — 0건 벌크 DELETE는 no-op이고 try-catch가 없어져
  **동작상 차이가 없다**
- **반환 타입 `boolean` 유지** — 응답이 `ResponseMessage<Boolean>`이므로 시그니처를 바꾸면 JSON이 바뀐다

### [L] — 임시저장 → 기안 전환 (D2 = L1)

Controller의 `substring(5, 8)` / `wasTemp.equals("ims")` / `approvalDelete` 호출 **전부 제거**.
CommandService가 판단한다.

```java
if (requestedApprovalNo != null && !requestedApprovalNo.isBlank()) {
    Approval existing = approvalRepository.findById(requestedApprovalNo).orElse(null);
    if (existing != null && existing.getApprovalStatus() == ApprovalStatus.TEMP_SAVED) {
        //둘 다 같은 결재번호를 유지한다. 기안이냐 임시저장이냐는 요청 상태(사용자 의도)가 가른다
        if (initialStatus == ApprovalStatus.PROCESSING) {
            return resubmit(requestedApprovalNo, approvalDTO, files);      // 전환
        }
        return resaveTempSaved(requestedApprovalNo, approvalDTO, files);   // 임시저장 재저장
    }
}
```

- **판정 기준이 "번호 문자열이 `ims`인가" → "DB status가 `TEMP_SAVED`인가"로 바뀌었다** (§4 표 참조)
- **전환 여부는 요청 status가 가른다.** `TEMP_SAVED`로 온 재저장을 무조건 전환하면
  **사용자가 원하지 않은 상신**이 된다. 명세 §5 [L]은 전환 조건을 "그 결재가 `TEMP_SAVED`인가"로만
  적었지만, [D]가 status를 "임시저장이냐 기안이냐를 나르는 유일한 사용자 의도 신호"로 규정했으므로
  두 조항을 함께 읽어 의도를 반영했다.
  결과적으로 **POST로 오든 PUT으로 오든 임시저장 재저장의 동작이 같아진다**([C]/[L] 취지와 일치).
  (원본 Controller가 기존 ims 결재를 삭제한 **뒤** `if (approvalStatus.equals("임시저장")) formNo = "ims"`로
  새 ims 번호를 뽑던 분기가 있었다 — "POST로 임시저장 재저장" 흐름이 실재했다는 근거다.)
- 결재자·참조자·**첨부는 교체**(명세 §5 [L] 본문 기준. §11 S3의 "첨부 유지"와 문언이 엇갈려 사용자 확정을 받았다)
- **트레이드오프(D2=L1 의도)**: 임시저장은 `ims`로 채번되므로 전환 후에도 번호에 `ims`가 남고,
  `FORM_NO`는 실제 양식으로 갱신되어 **번호와 양식이 불일치**한다. 이력 추적 보존을 위해 수용한 결과다 → **S3에서 육안 확인**

---

## 4. 의도된 동작 변경 (성공 응답 JSON **구조** 불변 — 값·실패 경로만)

| 지점 | Before | After |
|---|---|---|
| 동시 기안 충돌 | merge → 앞 기안자 결재를 조용히 UPDATE, **200** | persist → 제약 위반 → **롤백 + 500** |
| 회수(타인이 이미 처리) | 그냥 회수됨 | **400 / AP009** |
| 결재 완료 판정 | 마지막 인덱스 승인 시 전체 APPROVED | **전원 APPROVED일 때만** |
| 임시저장 수정 | `delete` + `insert` (행 재생성) | **UPDATE** (PK·이력 보존) |
| 임시저장 → 기안 (POST + `PROCESSING`) | 삭제 후 새 번호 발급 | **같은 번호 유지** (번호에 `ims` 잔존 — 의도) |
| 임시저장 재저장 (POST + `TEMP_SAVED`) | 기존 결재 삭제 후 **새 ims 번호로 재생성** | **같은 번호 유지**, 상태도 `TEMP_SAVED` 유지 (PUT 경로와 동일 동작) |
| 기안 status 위조 (`APPROVED` 전송) | 그대로 저장 | **400 / AP010** |
| 기안 status 가 `null` | NPE → 500/C999 | **400 / AP010** |
| 재임시저장 옛 첨부 | 디스크 orphan 잔존 | **정상 삭제** |
| 삭제 중 실패 | 삼키고 `return false`, 부분 삭제 커밋 | 전파 + **전체 롤백** |
| 존재하지 않는 결재자 처리 | 조용히 마지막 항목/`null` 반환 | **404 / AP004** |
| 결재 처리 응답의 `approverDate`·`rejectReason` | 변경 **전** 값 (승인 직후에도 `approverDate`가 null) | 변경 **후** 값 (재조회 결과) |
| 회수 시 "본인 아님 + 이미 처리됨" 동시 | — | **AP009**가 먼저 (각각 단독 케이스는 기존과 동일) |
| **R6-a** 상세 조회 — 없는 결재번호 | `orElse(null)` 즉시 역참조 → NPE → **500/C999** | **404 / AP001** |
| **R6-b** 상세 조회 — 반려 결재인데 REJECTED 결재자가 없음 | `.orElse(null).format()` → NPE → **500/C999** | **404 / AP004** |
| PROCESSING 결재에 재임시저장(PUT) | 삭제 후 재생성으로 그냥 수행됨 | **400 / AP011** (디스크 삭제 전에 차단) |
| 이미 PROCESSING인 `…ims…` 번호로 다시 POST (이중 제출·재시도) | `substring(5,8)=="ims"` → **기존 결재를 삭제**하고 새 번호로 생성 (파괴적) | `TEMP_SAVED`가 아니므로 신규 경로 → **기존 결재 보존** + 새 기안 1건 생성 |

> ⚠ **R6-b 주의**: 결재는 존재하는데 **상세 조회 및 그 결재가 포함된 목록 조회**가 404가 된다.
> `listToDTO`가 목록 항목마다 `getApproval`을 호출하기 때문에, 상신함에 데이터가 깨진 반려 결재가
> **하나라도 있으면 목록이 통째로 죽는다.** 다만 **현행도 500으로 동일하게 죽으므로 회귀는 아니다.**
> 프론트가 "없는 결재"로 오해할 수 있어 §6 S8에 반려 결재 항목을 추가했다.

**성공 경로의 `{status, message, data}` 구조와 결재/결재자/참조자/첨부 메타 필드 형태는 이전과 동일.**

---

## 5. 빌드 · 부팅 결과

```
> cd final; .\gradlew.bat compileJava
BUILD SUCCESSFUL in 16s
1 actionable task: 1 executed
```

### bootRun — 포트 충돌로 1차 실패 후 8081에서 정상 기동

1차 `bootRun`은 **`Web server failed to start. Port 8080 was already in use.`** 로 실패했다.
확인 결과 **19:05에 시작된 별개의 java 프로세스(pid 22424)가 8080을 점유**하고 있었다(이번 작업과 무관).
해당 프로세스는 **종료하지 않고**, 포트만 바꿔 재기동했다.

```
> cd final; .\gradlew.bat bootRun --args='--server.port=8081'
... o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 8081 (http) with context path ''
... com.insider.login.Application            : Started Application in 8.594 seconds (process running for 9.083)
```

- **`Started Application` 확인 → R10(`ApprovalRepositoryImpl` 프래그먼트 인식) 통과.**
  프래그먼트를 못 찾으면 리포지토리 빈 생성 단계에서 기동이 실패하므로, 기동 성공이 곧 확인이다.
- 참고로 1차 실패 로그에서도 `Bootstrapping Spring Data JPA repositories` → `Initialized JPA EntityManagerFactory`
  → 시큐리티 필터체인까지 전부 통과했고, 실패는 **마지막 단계인 웹서버 포트 바인딩뿐**이었다.
- 확인 후 8081 프로세스 종료. **8080은 손대지 않았다.**

### 검색 확인 7종 (06-command-query.md §11 원문)

```powershell
cd final
$approval   = Get-ChildItem -Path .\src\main\java\com\insider\login\approval -Filter *.java -Recurse
$service    = Get-ChildItem -Path .\src\main\java\com\insider\login\approval\service -Filter *.java -Recurse
$controller = Get-ChildItem -Path .\src\main\java\com\insider\login\approval\controller -Filter *.java -Recurse
```

| # | 패턴 | 대상 | 결과 |
|---|---|---|---|
| 1 | `ApprovalService` | approval | **0건** ✅ |
| 2 | `Repository\.save\(` | service | **4건 — 전부 정상**(아래) |
| 3 | `catch\s*\(\s*Exception` | approval | **1건 — 범위 밖**(아래) |
| 4 | `size\(\)\s*-\s*1` | approval | **0건** ✅ |
| 5 | `"(임시저장\|처리 중\|대기\|승인\|반려)"` | approval | **10건 — 전부 범위 밖 or 주석**(아래) |
| 6 | `WITHDRAW_ALREADY_PROCESSED` | approval | **1건** ✅ ([A-잔여] 구현 확인) |
| 7 | `substring\(5` | controller | **0건** ✅ |

**#2 상세 (4건, 전부 의도된 잔존)**

```
ApprovalCommandService.java:285  approverRepository.save(senderApprover);
ApprovalCommandService.java:300  approverRepository.save(approver);
ApprovalCommandService.java:314  referencerRepository.save(referencer);
ApprovalFileService.java:90      attachmentRepository.save(new Attachment(...));
```
→ 전부 **자식 엔티티 생성 경로**다(§3.5 — persist 전환 대상 아님).
**상태 변경 경로의 `save()`는 0건**이다. `ApprovalFileService`는 §6 금지 파일(무변경).

**#3 상세 (1건)**

```
ApprovalFileService.java:97      } catch (Exception e) {
```
→ `store()`의 보상 삭제 로직. **§6 금지 파일이라 무변경.** `approvalDelete`의 삼킴은 전부 제거됐다.

**#5 상세 (10건, 0건이 될 수 없는 항목)**

- `enums/ApprovalStatus.java` 4건 / `enums/ApproverStatus.java` 3건 → **Enum의 `description` 정의 자체.**
  §6 금지 파일이며, 이 값들이 있어야 `from()`의 한글 수용이 동작한다
- `ApprovalQueryService.java` 3건 (L303·305·308) → **주석 안**. §11이 "주석 무시 — 따옴표 안만"이라 명시했다.
  원본 `selectApprovalList`의 설명 주석을 원문 그대로 이관한 것이다

→ **코드 리터럴로서의 한글 상태값은 approval 패키지 전체에 0건이다.**

**#4 관련 메모**: 검색 #4의 의도는 "완료 판정에서 인덱스 판정 제거"다.
`updateApprover`의 `i == approverList.size() - 1`(완료 판정)은 전원 APPROVED 판정으로 대체됐고,
`selectApproval`의 최종승인일 계산 `approverList.get(approverList.size() - 1)`은
`approverList.stream().max(Comparator.comparingInt(Approver::getApproverOrder))`로 바꿨다
(ASC 정렬 리스트의 마지막 원소와 결과 동일). 검색 통과를 위해 다른 코드를 비틀지는 않았다.

---

## 6. 📋 수동 검증 체크리스트 (사용자 담당 — 06-command-query.md §11 원문)

> ⚠ `compileJava`·`bootRun` 통과는 **아무것도 증명하지 않는다**(단계 1.5). 아래 수동 검증이 본체다.

- [x] **S0. [F] persist 전환 확인** ★신설 — SQL 로그로
      기안 1건을 쏘고 로그를 본다. **`insert into approval` 앞에
      `select ... from approval where approval_no=?`가 없어야 한다.**
      (§3.1의 16.207 자리가 사라진다. 자식 엔티티의 merge SELECT는 그대로 남는 게 정상 — §3.5)

- [x] **S1. [B] 결재 순서 역전** ★핵심
      결재자 3명(order 1·2·3) 기안 → **order 3이 먼저 승인** →
      전체 결재 상태가 **`PROCESSING` 유지**여야 한다(현행은 `APPROVED`로 잘못 바뀜).
      이어서 1·2가 승인 → 그때 `APPROVED`.

- [x] **S2. [C] 수정 후 동일성 보존**
      임시저장 기안 → PUT으로 제목·내용·결재선 변경 →
      `APPROVAL_NO` **불변**, `APPROVAL` 행이 **삭제됐다 생기지 않았는지** SQL 로그로 확인
      (`update approval`이면 정상, `delete` + `insert`면 실패). **R2 재검증 지점.**

- [x] **S3. [L] 임시저장 → 기안 전환 번호 유지** ★
      임시저장(`2026-ims0000N`) → 같은 결재를 기안 →
      **결재번호 불변**, 상태 `TEMP_SAVED` → `PROCESSING`, 결재자·참조자 재구성,
      **첨부는 이번 POST에 실린 파일로 교체**(옛 첨부는 디스크·DB에서 삭제 — 현행 동작 보존).
      **번호에 `ims`가 남는다** — D2=L1의 의도된 결과인지 눈으로 확인.

- [x] **S4. [D] status 위조 차단**
      - `approvalStatus = "APPROVED"` / `"승인"` 전송 → **400 + AP010** (500/C999면 R8 미처리)
      - 정상 값 4가지(`PROCESSING`, `TEMP_SAVED`, `"처리 중"`, `"임시저장"`) 모두 통과
      - **한글 `"임시저장"`과 영문 `TEMP_SAVED` 둘 다 `formNo=ims`가 붙는지** ← Stage 5 [관찰] 해소 확인

- [ ] **S5. [G] 삭제 중 실패 시 롤백** — **미수행 확정**
      (가능하면) 첨부 DB 삭제 단계에서 실패를 유도 → **부분 삭제가 커밋되지 않고** 전체 롤백,
      응답은 에러 코드. 현행은 `return false`로 조용히 넘어간다.
      → 명세가 "가능하면"이라 단서를 달았고 실패 유도 수단이 없어 **미수행으로 확정**한다.

- [x] **S6. [K] 동시 기안** ★
      같은 폼으로 **동시에 2건 POST** → 한 건 성공 + **다른 건 500(롤백)**.
      **둘 다 200인데 DB 행이 1개면 실패**(merge 덮어쓰기가 남아 있다는 뜻).

- [x] **S7. [D] 프론트 실제 전송값 확인** (기록용)
      프론트에서 임시저장/기안을 눌러 요청 body의 `approvalStatus`가 한글인지 영문인지 확인.
      D3-a는 양쪽 모두 동작하므로 고칠 필요는 없고, 향후 DTO Enum 전환(D7-b) 판단 근거로 기록한다.

- [x] **S8. 회귀 — 응답 형식 동등성** ★
      아래 전부가 Stage 5와 **동일한 JSON 구조**를 반환해야 한다.
      - 상세 조회 (결재선 **순서** 포함 — R4)
      - 목록 조회 5종 (`given` / `tempGiven` / `received` / `receivedRef` / `receivedAll`)
      - 회수, 결재 처리(승인·반려), 삭제, 파일 다운로드
      - 무첨부 기안, 첨부 2건 기안
      - 최종승인일(`finalApproverDate`), 대기자(`standByApprover`) 표시

      **S8 추가 항목** — R6 교정과 [L] 판정 변경 때문에 새로 봐야 하는 것:
      - [x] **반려 결재 상세 조회** — `finalApproverDate`가 정상적으로 채워지고 200이 나오는지 (R6-b가 404를 내지 않는지)
      - [x] **상신함 목록에 반려 결재가 포함된 상태에서 목록 조회 → 200** (R6-b가 목록 전체를 죽이지 않는지)
      - [x] 없는 결재번호 상세 조회 → 500이 아니라 **404/AP001**인지 (R6-a)
      - [x] PROCESSING 결재에 PUT `/approvals/{no}` → **400/AP011**, 그리고 **디스크의 기존 첨부가 그대로 남아 있는지**
      - [x] 이미 PROCESSING인 `…ims…` 번호로 다시 POST → **기존 결재가 살아 있고** 새 기안 1건이 별도로 생기는지
      - [x] **임시저장 결재를 POST로 `status=TEMP_SAVED`(또는 `"임시저장"`)로 재저장**
            → 상태가 **`TEMP_SAVED`로 유지**되고 결재번호도 그대로인지
            (`PROCESSING`으로 넘어가면 실패 — 사용자가 원하지 않은 상신이다)
      - [x] **같은 결재를 POST로 `status=PROCESSING`(또는 `"처리 중"`)으로 전송** → 그때 비로소 전환되는지

- [x] **S9. Stage 1.5 비회귀**
      결재 대기함·임시저장함 조회/검색/카운트가 **0건으로 조용히 죽지 않는지** 확인.

- [x] **S10. [A-잔여] 회수 검증** ★ — **양방향 필수**
      - [x] **(a) 아무도 처리하지 않은 결재 회수 → 성공해야 한다.**
            실패하면 기안자 `_apr000` 제외를 빠뜨린 것 (R9). **(b)보다 먼저 확인할 것.**
      - [x] (b) 결재자 1명이 승인한 뒤 회수 → **400 + AP009**로 막혀야 한다 (현행은 그냥 회수됨)
      - [x] (c) 본인 아닌 사람이 회수 → 기존대로 AP008

- [x] **S11. [C-첨부] 재임시저장 옛 파일 삭제** ★
      첨부 2건으로 임시저장 → 다른 첨부 2건으로 재임시저장 →
      **디스크에서 옛 파일 2건이 사라지고 새 파일 2건만 남는지** 육안 확인.
      `04-file.md` §7-2가 Stage 4에서 미통과였던 항목 — **이번에 통과해야 한다.**

---

## 6-1. 자동 수행 결과 (Claude Code 실행 — 2026-07-29)

실 개발 DB·실 서버(`bootRun --args="--logging.level.org.hibernate.SQL=DEBUG"`, 포트 8080)에서 수행했다.
인증은 `Authorization: BEARER <token>` — 토큰은 `POST /login` 응답 body의 `token` 필드 및 동일 이름 응답 헤더로 온다(시큐리티 코드로 확인).

| 항목 | 판정 | 한 줄 근거 |
|---|---|---|
| S0 persist 전환 | **통과** | `insert into approval` 앞에 approval 단건 SELECT **0건** |
| S1 결재 순서 역전 | **통과** | order 3 선승인 후에도 `PROCESSING` 유지, 전원 승인 시에만 `APPROVED` |
| S2 수정 동일성 | **통과** | `update approval` 1건, `delete`/`insert` 0건 |
| S3 전환 번호 유지 | **통과** | `2026-ims00004` 번호 불변, TEMP_SAVED 재저장은 상태 유지·PROCESSING 요청에만 전환 |
| S4 status 위조 차단 | **통과** | 위조 2종 → 400/AP010, 정상 4종 통과, 한글·영문 모두 `ims` 채번 |
| S5 삭제 중 실패 롤백 | **미수행 확정** | 실패 유도 수단 없음 (§11 "가능하면") |
| S6 동시 기안 | **통과** | 200 + 500(`Duplicate entry` 롤백), DB 1행이 **선행 건 데이터** |
| S7 프론트 전송값 | **통과(기록용)** | 프론트가 **한글 상태값**을 보냄 확인 (사용자 수행) |
| S8 회귀 | **통과** | API 응답 전부 확인(Claude Code) + **화면 표시 확인(사용자)** |
| S9 Stage 1.5 비회귀 | **통과** | 임시저장함·대기함·참조함 조회/검색/카운트 모두 유효 건수 반환 |
| S10 회수 검증 | **통과** | (a) 200 회수 성공 / (b) 400·AP009 / (c) 403·AP008 |
| S11 옛 첨부 삭제 | **통과** | 옛 파일 2건 디스크에서 소멸, 새 2건 생성 |

### S0 — `insert into approval` 앞뒤 SQL 로그 원문

```
  1 [21:55:41.308] select a1_0.approval_no from approval a1_0 where a1_0.approval_no like ? escape '' order by a1_0.approval_no desc limit ?, ?
  2 [21:55:41.338] select ... from approver a1_0 where a1_0.approver_no=?
  3 [21:55:41.347] select ... from approver a1_0 where a1_0.approver_no=?
  4 [21:55:41.349] select ... from approver a1_0 where a1_0.approver_no=?
  5 [21:55:41.351] select ... from approver a1_0 where a1_0.approver_no=?
  6 [21:55:41.354] select r1_0.ref_no, ... from referencer r1_0 where r1_0.ref_no=?
  7 [21:55:41.364] select a1_0.file_no, ... from apr_attachment a1_0 where a1_0.file_no=?
  8 [21:55:41.370] select a1_0.file_no, ... from apr_attachment a1_0 where a1_0.file_no=?
  9 [21:55:41.373] select m1_0.member_id, ... from member_info m1_0 where m1_0.member_id=?
 10 [21:55:41.377] select d1_0.depart_no, d1_0.depart_name from department_info d1_0 where d1_0.depart_no=?
 11 [21:55:41.379] select p1_0.position_level, p1_0.position_name from position_info p1_0 where p1_0.position_level=?
 12 [21:55:41.394] select f1_0.form_no, f1_0.form_name, f1_0.form_shape from form f1_0 where f1_0.form_no=?
 13 [21:55:41.406] insert into approval (approval_content, approval_date, approval_status, approval_title, form_no, member_id, reject_reason, approval_no) values (?, ?, ?, ?, ?, ?, ?, ?)
 14 [21:55:41.431] insert into approver (...)
 15 [21:55:41.435] insert into approver (...)
 16 [21:55:41.436] insert into approver (...)
 17 [21:55:41.438] insert into approver (...)
 18 [21:55:41.441] insert into referencer (...)
 19 [21:55:41.443] insert into apr_attachment (...)
 20 [21:55:41.446] insert into apr_attachment (...)
```

단건 SELECT 집계 — **approval: 0** / approver: 4 / referencer: 1 / attachment: 2.

`ApprovalService` 시절 `insert into approval` 직전에 있던 `select ... from approval where approval_no=?`
(명세 §3.1의 16.207 자리)가 **사라졌다.** 자식 엔티티의 merge SELECT는 남아 있으며 §3.5대로 정상이다.
1번은 채번(`findLastApprovalNo`), 9~12번은 응답 조립(`getApproval`) 시작분이다.

### S1 — 결재 순서 역전

대상 `2026-con00003` (결재자 order 1·2·3).

| 단계 | 요청 | 응답 | 전체 상태 | 결재자 상태 |
|---|---|---|---|---|
| 1 | `PUT /approvers/2026-con00003_apr003` `{"approverStatus":"APPROVED"}` | 200 | **`PROCESSING` 유지** | 0:APPROVED 1:PENDING 2:PENDING **3:APPROVED** |
| 2 | `PUT .../_apr001` APPROVED | 200 | `PROCESSING` 유지 | 0·1·3 APPROVED, 2 PENDING |
| 3 | `PUT .../_apr002` APPROVED | 200 | **`APPROVED`** | 전원 APPROVED |

`finalApproverDate = 2026-07-29 21:57:13`.

> **관찰(범위 밖, 기록만)**: 이 값은 **가장 큰 `approverOrder`의 처리일시**다(D5 처방대로 정렬 리스트의 max).
> 실제 처리시각은 order3=21:57:13, order1=21:57:14, order2=21:57:14로, **마지막으로 승인한 사람은
> order 2(21:57:14)인데 표시는 order 3의 21:57:13**이 된다. [B]로 순서가 자유로워지면서 "최종승인일"의
> 의미가 어긋날 수 있다. 명세가 정한 계산 방식을 그대로 따랐으므로 이번 단계에서는 고치지 않는다.

### S10-(a) — 회수 성공 응답 원문

```
PUT /approvals/2026-con00004/status      → HTTP 200
{"status":200,"message":"전자 결재 회수 성공","data":{ ... }}
  approvalNo     : 2026-con00004
  approvalStatus : WITHDRAWN
  approver       : [(0,'APPROVED'), (1,'PENDING'), (2,'PENDING')]
```

기안자 `_apr000`이 `APPROVED`인데도 회수가 성공했다 → **`approverOrder == 0` 제외가 동작한다(R9 회피 확인).**

- (b) `2026-con00005` — 결재자1 승인 후 회수 → `HTTP 400 {"status":400,"code":"AP009","message":"이미 처리된 결재는 회수할 수 없습니다."}`
- (c) `2026-con00006` — 사번 123 토큰으로 회수 → `HTTP 403 {"status":403,"code":"AP008","message":"기안자 본인만 결재를 회수할 수 있습니다."}`

### S11 — 디스크 파일 before / after

대상 `2026-ims00005`. 첨부 2건(fileA·fileB) → PUT으로 2건(fileC·fileD) 교체.

```
===== BEFORE =====
857d5a4f0f5a468bbc580e9129eb7248.txt   존재=True
f97a6e71319c4a7287f0773994491810.txt   존재=True
(업로드 폴더 전체 파일 수: 27)

===== AFTER =====
[옛 파일]
857d5a4f0f5a468bbc580e9129eb7248.txt   존재=False
f97a6e71319c4a7287f0773994491810.txt   존재=False
[새 파일]
9c47b233653e4c7b803911b2e52d4c89.txt   존재=True
175754ebd0f143678e9e87843b67c0a8.txt   존재=True
(업로드 폴더 전체 파일 수: 27  ← 옛2 삭제 + 새2 추가로 정합)
```

`04-file.md` §7-2가 Stage 4에서 미통과였던 항목이 **이번에 통과했다.**

### S2 — 같은 요청의 SQL (R2 재검증)

```
update approval      : 1건   ← dirty checking
delete from approval : 0건
insert into approval : 0건
delete from apr_attachment : 1 / delete from approver : 1 / delete from referencer : 1
insert into apr_attachment : 2
```

첨부 PK(`_f001`·`_f002`)가 재사용되는데도 `StaleStateException` 없이 INSERT 2건이 나갔다
→ **`clearPersistenceContext()`가 R2를 실제로 막고 있음이 확인됐다.**

### S4 — status 화이트리스트

| 전송값 | 결과 |
|---|---|
| `APPROVED` | **400 / AP010** |
| `"승인"` | **400 / AP010** |
| `PROCESSING` | 200 · `2026-con00007` · formNo(DB) `con` |
| `TEMP_SAVED` | 200 · **`2026-ims00006`** · formNo(DB) `con` |
| `"처리 중"` | 200 · `2026-con00008` |
| `"임시저장"` | 200 · **`2026-ims00007`** |

**한글·영문 양쪽 모두 `ims` 채번**이 붙었다 → Stage 5 [관찰](영문 전송 시 `ims`가 안 붙던 문제) **해소 확인.**
위조 시도가 500/C999가 아니라 400/AP010이므로 **R8 전반부도 처리됐다.**

### S3 — 전환 판정

| 단계 | 요청 | 결재번호 | 상태 |
|---|---|---|---|
| 1 | POST, `TEMP_SAVED` | `2026-ims00004` 발급 | TEMP_SAVED |
| 2 | POST + 같은 번호, `TEMP_SAVED` | **불변** | **TEMP_SAVED 유지** ← 상신되지 않음 |
| 3 | POST + 같은 번호, `PROCESSING` | **불변** | **PROCESSING 전환** |

전환 후에도 번호는 `2026-ims00004`, `FORM_NO`는 `con` → **번호와 양식 불일치**가 눈으로 확인된다(D2=L1 의도).

### S6 — 동시 기안

```
A: HTTP 200  → 2026-rei00001 "S6 동시기안 A"
B: HTTP 500  → {"status":500,"code":"C999", ...}

로그: Duplicate entry '2026-rei00001' for key 'approval.PRIMARY'
      → DataIntegrityViolationException / ConstraintViolationException
            / SQLIntegrityConstraintViolationException

DB 확인: 2026-rei00001 의 내용이 "S6 동시기안 A" (선행 건이 살아 있음)
         제목 검색 결과 총 1건 (B는 롤백되어 미생성)
```

merge 시절의 **"둘 다 200인데 뒤 건이 앞 건을 조용히 덮어씀"**이 사라지고 제약 위반 → 롤백으로 바뀌었다.

### S8 — API 응답 회귀 (화면 표시는 사용자 확인 필요)

| 항목 | 결과 |
|---|---|
| 목록 `given` | 200 · totalElements 23 |
| 목록 `tempGiven` | 200 · 8건 |
| 목록 `received` | 200 · 3건(토큰) / 5건(헤더 241811) |
| 목록 `receivedRef` | 200 · 0건(토큰) / 7건(헤더 240401001) |
| 목록 `receivedAll` | **500 / C999 — R7 기존 NPE**(아래) |
| 상세 조회 | 200 · 결재선 order 0→3 오름차순 |
| 반려 결재 상세 | **200** · `finalApproverDate` 정상 · `rejectReason` 반영 (R6-b 미발동) |
| 반려 포함 상신함 목록 | **200** · 반려건 포함 확인 |
| 없는 번호 상세 | **404 / AP001** (R6-a) |
| PROCESSING 결재에 PUT | **400 / AP011** + **디스크 첨부 2건 잔존 확인** |
| 이미 PROCESSING인 ims 재 POST | 200 · 기존 `2026-ims00004` 보존 + 신규 `2026-con00010` 생성 |
| POST + TEMP_SAVED 재저장 | 번호·상태 유지 |
| POST + PROCESSING | 그때 전환 |
| 무첨부 기안 | 200 · `attachment: []` |
| 첨부 2건 기안 | 200 · `_f001`·`_f002` |
| 결재 처리(승인·반려) | 200 · ApproverDTO 반환 |
| 회수 | 200 |
| 삭제 | 200 · `{"data":true}` → 이후 상세 404 |
| 파일 다운로드 | 200 · `text/plain;charset=UTF-8` · 내용 일치 |
| 없는 파일 다운로드 | 404 / AP007 |
| `finalApproverDate`·`standByApprover` | 목록·상세 모두 표시됨 |

**`memberId` 헤더 경로와 토큰 전용 경로를 각각 실행**했고 `given`/`tempGiven`/`received`/`receivedRef` 모두
동일하게 200을 반환했다. **Stage 7이 헤더를 제거해도 SecurityContext 경로가 이미 동작함을 확인**했다.

> **`receivedAll` 500은 R7 — 기존 NPE이며 이번 단계가 만든 것이 아니다.**
>
> ```
> java.lang.NullPointerException: Cannot invoke "org.springframework.data.domain.Page.getTotalPages()"
>   because "approvalPage" is null
>   at com.insider.login.approval.service.ApprovalQueryService.getApprovalList(ApprovalQueryService.java:358)
> ```
>
> `case "receivedAll"`이 비어 있어 `approvalPage`가 null인 채 도달한다. 명세 §2·§9 R7이
> **"범위 밖. 고치지 않는다. 원문 그대로 옮기고 기록만"**이라 지시한 그 지점이다.

### S9 — Stage 1.5 비회귀 (조용한 0건 없음)

| 조회 | 검색어 | 결과 |
|---|---|---|
| 임시저장함 | (없음) | 8건 |
| 임시저장함 | `S11` | 1건 |
| 임시저장함 | `첨부교체`(한글) | 1건 |
| 임시저장함 | `ZZZZ` | 0건 (정상적인 무매칭) |
| 결재 대기함 | (없음) | 5건 |
| 결재 대기함 | `S10` / `S4` | 1건 / 2건 |
| 결재 대기함 | `이중제출`(한글) | 1건 |
| 수신참조함 | (없음) | 7건 |

검색어에 따라 건수가 정확히 갈리므로 **JPQL·네이티브 리터럴이 조용히 죽지 않았다.**

### S7 — 프론트 실제 전송값 (사용자 수행, 기록용)

`POST /approvals` multipart 의 `approvalDTO` 파트 원문:

```
임시저장 버튼 : {"approvalNo": null,            ..., "approvalStatus": "임시저장", ...}
기안 버튼     : {"approvalNo": "2026-ims00008", ..., "approvalStatus": "처리 중",  ...}
공통          : "formNo":"con", "approver":[{"memberId":241811}], "referencer":[]
```

**확정된 사실 두 가지.**

1. **프론트는 한글 상태값을 보낸다** (`"임시저장"` / `"처리 중"`).
   D3-a가 `ApprovalStatus.from()`으로 영문·한글을 모두 수용하도록 설계된 것이 실사용에서 필수임이 확인됐다.
2. **[L] 전환 경로가 프론트의 실제 주 경로다.** 기안 버튼이 임시저장 응답의 결재번호
   (`2026-ims00008`)를 그대로 실어 보낸다. → **D2=L1의 "번호에 `ims` 잔존"은 테스트 전용이 아니라
   실사용 경로에서 발생한다.**

### 프론트 화면 확인 (사용자 수행)

| 확인 항목 | 결과 |
|---|---|
| `2026-ims00004`(번호에 `ims`, 상태 `PROCESSING`)의 목록 노출 | **상신함에만 뜨고 임시저장함에는 안 뜬다** |
| 상세 화면 결재선 표시 순서 | **order 오름차순**(기안자 → 결재자1 → 결재자2) — **R4 확인** |
| 목록 상태 컬럼 | 영문 Enum(`PROCESSING`)이 **그대로 노출됨** (아래) |

- 프론트가 **번호 문자열이 아니라 상태로 목록을 거른다**는 뜻 → **D2=L1의 화면 위험 없음.**
- **상태 컬럼 영문 노출**은 Stage 1(Enum 전환)의 파급이며 **이번 단계가 만든 회귀가 아니다.**
  프론트 표시 매핑 문제이고 프론트 변경은 범위 밖이므로 **기록만** 한다.

이로써 §11 S8이 요구한 "화면 표시 회귀"까지 확인되어 **S8을 통과로 판정**했다.

### 이번 검증으로 개발 DB에 생성된 결재 (정리 필요 시 참고)

| 결재번호 | 용도 | 최종 상태 |
|---|---|---|
| `2026-con00003` | S0·S1 (첨부 2·결재자 3) | APPROVED |
| `2026-con00004` | S10-(a) | WITHDRAWN |
| `2026-con00005` | S10-(b) | PROCESSING (결재자1 승인됨) |
| `2026-con00006` | S10-(c) | PROCESSING |
| `2026-ims00004` | S3 전환 | PROCESSING (번호에 `ims` 잔존) |
| `2026-ims00005` | S2·S11 (첨부 교체) | TEMP_SAVED |
| `2026-con00007` | S4 영문 `PROCESSING` | PROCESSING |
| `2026-ims00006` | S4 영문 `TEMP_SAVED` | TEMP_SAVED |
| `2026-con00008` | S4 한글 "처리 중" | PROCESSING |
| `2026-ims00007` | S4 한글 "임시저장" | TEMP_SAVED |
| `2026-con00009` | S8 반려 | REJECTED |
| `2026-con00010` | S8 이중 제출 | PROCESSING |
| `2026-rei00001` | S6 동시 기안(성공분) | PROCESSING |
| `2026-sup00001` | S8 무첨부 → 삭제 검증 | **삭제됨** |
| `2026-ims00008` | **S7 프론트 임시저장 → 기안 전환** (사용자 수행) | PROCESSING (번호에 `ims` 잔존) |

디스크에는 `2026-con00003`의 첨부 2건, `2026-ims00004`의 1건, `2026-ims00005`의 2건이 남아 있다.

### 미수행 항목

- **S5** 삭제 중 실패 시 롤백 — 명세가 "가능하면"이라 단서를 달았고 실패 유도 수단이 없어 **미수행 확정**.

그 외 S0~S4·S6~S11은 전부 수행·통과했다 (S7과 화면 표시 확인은 사용자 수행).

---

## 7. 범위 외 — 손대지 않은 항목 (원문 유지 확인)

- **R7 기존 NPE**: `getApprovalList`의 `case "receivedAll"`이 비어 있어 `approvalPage`가 `null`인 채
  `approvalPage.getTotalPages()`에 도달한다. **기능 추가에 해당하므로 고치지 않고 원문 그대로 이관**했다.
- **[E]** 기안자 `_apr000` Approver 자동등록의 도메인 분리 → 스키마 변경 수반, 리팩토링 범위 밖
- **[K] 재시도** → D1=K1. persist만 하고 재시도는 Stage 7 이후 (진입점이 정리된 뒤 tx 바깥에 붙여야 한다)
- **자식 엔티티의 persist 전환** → §3.5 관찰. 기안 1건당 merge 전용 SELECT가 남아 있다
- **tx ↔ 디스크 원자성 전면 해결** (`store()` 반환 후 바깥 tx 롤백 시 orphan) → 별도 설계 주제.
  `[APPROVAL_FILE_ORPHAN]` 로그 관찰 유지
- **임시저장 시 결재자가 `PENDING`으로 생성되는 것** → 기존 동작 보존
- **`findLastApprovalNo`의 `LIKE %:yearFormNo%`** (선행 와일드카드 풀스캔, 폼번호 접두어 교차 매칭) → 규칙 보존
- **Stage 7 항목**: `@RequestHeader("memberId")` 백도어[I], 회수 API 인증[A-확장],
  `getCurrentMemberId()` 헬퍼[H], 이모지 로그·`System.out.println`[J], `dounloadFile` 메서드명,
  Controller 100~120줄 목표 → **전부 그대로 뒀다**(현재 253줄)
- `dto/**`(D7-a), `enums/**`, `entity/Approver.java`, `ApprovalFileService`, `ApprovalNoGenerator`,
  `src/test/**`, 프론트엔드, DB 스키마 → **무변경**

---

## 8. 관찰 · Stage 7 인계

1. **`src/test/**`는 Stage 6 이전에 이미 컴파일 불가 상태였다.**
   `ApprovalServiceTest`가 Stage 5에서 제거된 `approvalService.selectApprovalNo(...)`와
   Stage 1에서 2-arg가 된 `updateApprovalStatus(String)`를 호출한다.
   `ApprovalService` 삭제로 깨짐이 늘어났지만 **compileTestJava가 이미 실패하던 상태**라 상태 변화는 없다.
   검증 수단인 `compileJava`는 테스트를 컴파일하지 않으므로 영향 없음. §6이 `src/test/**`를 금지해 손대지 않았다.
   → **테스트 트리 정리는 별도 작업으로 협의 필요.**

2. **`leave-pattern.md` §9의 `ErrorCode` 예시가 구현과 어긋나 있다.**
   문서는 `LEAVE_NOT_FOUND("L001", HttpStatus.NOT_FOUND, "…")` 순서로 적혀 있으나,
   실물 생성자는 **`ErrorCode(int status, String code, String message)`** 다.
   이번에는 실물 시그니처를 따랐다. **Stage 7에서 또 헷갈릴 수 있어 기록만 남긴다**(문서 수정은 범위 밖).

3. **`ErrorCode` 번호 재확정** — §7은 `APPROVER_NOT_FOUND`를 AP011로 신규 추가하라고 했으나
   **이미 AP004로 존재**했다(중복 상수는 컴파일 불가). §7 각주("번호는 착수 시점에 다시 열어 최종 확정")에 따라
   사용자 확정을 받아 **AP004를 재사용**하고, 신규는 **AP010(`APPROVAL_INVALID_INITIAL_STATUS`)·
   AP011(`APPROVAL_MODIFY_NOT_ALLOWED`)** 2개만 추가했다. AP012·AP013은 사용하지 않는다.

4. **명세 내부 문언 충돌** — §5 [L] "결재자·참조자·첨부는 교체" ↔ §11 S3 "결재자·참조자 재구성, 첨부 유지".
   사용자 확정으로 **§5 본문(교체)** 을 따랐다. 현행 동작(`approvalDelete` 후 재저장)과 동일한 결과다.

5. **R8 후반부는 처리하지 않았다.** `processApprover`의 `ApproverStatus.from()`은 잘못된 값에
   `IllegalArgumentException`을 던져 **500/C999**가 된다. 명세가 에러코드를 지정하지 않았고
   "현행도 500이므로 동작 변경은 아님"이라 했으므로 **감싸지 않고 현행을 유지**했다(사용자 확정).

6. **`listToDTO`의 죽은 대입 1줄 제거** — 원본 `ListToDTO`는
   `modelMapper.map(approval, ApprovalDTO.class)` 결과를 다음 줄에서 곧바로 `selectApproval(...)` 결과로
   덮어쓰고 있었다. 이관하면서 이 죽은 대입만 제거했다(동작 동일). `modelMapper`는 `getForm`에서 계속 쓴다.

7. **Controller의 주석 처리된 죽은 코드 1블록 제거** — `selectApprovalByNo`에 남아 있던
   `/* ApprovalDTO approvalDTO = approvalService.selectApproval(approvalNo); ... */`.
   삭제된 클래스를 가리키는 주석이라 §11 검색 #1을 0건으로 만들기 위해 함께 제거했다.

8. **`resubmit`의 기안자 출처가 두 갈래다.** 기존 `Approval.memberId`는 그대로 유지되지만
   (`modifyDraft`가 건드리지 않는다), `createChildren`이 만드는 기안자 Approver(`_apr000`)는
   `approvalDTO.getMemberId()`(= 요청자)를 쓴다. 보통 같은 사람이라 무해하지만 어긋날 여지가 있다.
   현행 동작(`updateApproval`도 `approvalDTO.getMemberId()`로 `_apr000`을 만들었다)과 동일해 이번에는 손대지 않았다. **기록만.**

9. **`updateApprovalTemp`의 로그 1줄 제거** — `log.info("기존 approval Form : " + approvalNo.substring(5, 8));`.
   §11 검색 #7이 controller 하위 `substring(5` **0건**을 요구하므로 제거했다. 로그 정리([J])는 Stage 7 소관이지만
   이 줄은 검색 기준에 직접 걸린다.

---

### S7(프론트 전송값 확인)으로 확정된 사항 — Stage 7 필독

10. **프론트는 한글 상태값을 보낸다** (`"임시저장"` / `"처리 중"` — §6-1 S7 원문).
    따라서 `ApprovalStatus.from()`의 **description(한글) 매칭은 제거 금지**다.
    없애는 순간 **기안이 전부 실패**한다. `enums/ApprovalStatus.java`의 `description` 필드와
    `from()`의 `status.description.equals(value)` 분기를 정리 대상으로 오해하지 말 것.

11. **D7-b(DTO를 Enum 타입으로 변경)는 봉인한다.**
    Jackson 기본 역직렬화는 `name()`만 매칭하므로 `"임시저장"`/`"처리 중"`이 오는 순간 깨진다.
    전환하려면 **`@JsonCreator` 추가** 또는 **프론트의 영문 전환**이 선행돼야 한다.
    (Stage 6는 D7-a로 DTO 시그니처를 불변 유지했고, 이 판단이 실측으로 뒷받침됐다.)

12. **[L] 전환 경로는 프론트의 실제 주 경로다.**
    작성 화면에서 임시저장 → 기안을 누르면 프론트가 임시저장 응답의 결재번호를 그대로 실어 보낸다
    (`approvalNo: "2026-ims00008"` 확인). 따라서 **D2=L1의 "번호에 `ims` 잔존"은 테스트 전용이 아니라
    실사용 경로에서 발생한다.** 다만 프론트가 번호가 아닌 **상태로 목록을 거르므로 화면 위험은 없다**
    (상신함에만 노출, 임시저장함 미노출 — 사용자 확인 완료).

13. **`draft()`의 전환 판정에 요청 status를 반영한 수정이 실사용 결함을 막았다.**
    임시저장 후 화면이 유지되므로 사용자가 **임시저장을 두 번 누르면** `approvalNo` + `"임시저장"`이 전송된다.
    수정 전 코드는 "실려온 결재가 TEMP_SAVED이면 무조건 전환"이었으므로
    **저장을 한 번 더 눌렀을 뿐인데 결재가 상신**됐을 것이고, 되돌리려면 회수해야 한다.
    이번 수정으로 상태가 유지된다(§6-1 S3 / S8 검증 항목에서 확인).

14. **목록 상태 컬럼에 영문 Enum(`PROCESSING`)이 그대로 노출된다.**
    Stage 1(Enum 전환)의 파급이며 **이번 단계가 만든 회귀가 아니다.**
    프론트 표시 매핑 문제이고 프론트 변경은 리팩토링 범위 밖이라 **기록만** 한다.
