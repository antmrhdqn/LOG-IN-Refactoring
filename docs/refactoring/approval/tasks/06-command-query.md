# 단계 6: God Class 분리 — Command / Query (Task 명세 · 확정본)

> 작성: 2026-07-29 / **v3 — D1~D9 전부 확정, P0 실측 완료**
> 선행: 단계 1·1.5·2·3·4·5 완료·커밋·푸시 (`80f60b7` 코드 / `0215951` 문서, origin/main 동기화)
> 근거: `spec.md` [A-잔여][B][C][D][F][G-비파일][L], `plan.md` §4(Command/Query),
> `leave-pattern.md` §4(dirty checking)·§8(Command/Query),
> `stage5_to_6_handover.md`, `05-generator.md` §8(D1 인계·D3 에러코드), `04-file-report.md` §4·§5-3·§9(이월)
> 결정: **§8 D1~D9 전부 확정** (D1=K1+F-a / D2=L1 / D3=a / D4=추천안 / D5=추천안 / D6=순서교정 포함 / D7=a / D8=a / D9=a)
> 실행 도구: **Claude Code (Opus 후보)** — 본 확정본을 plan mode로 검토 후 실행
>
> **v4·v5 정정 (착수 시 실물 확인에서 발견 — Claude Code plan mode 보고분)**
> - **v4 §7**: `APPROVER_NOT_FOUND`는 신규가 아니라 **기존 AP004**. 신규는 AP010·AP011 둘뿐
> - **v5 §11 S3**: "첨부 유지"는 오기. **첨부는 교체**(§5 [L] 본문이 맞다)
> - **v5 §5 [C-첨부]·§9 R2**: R2가 이론이 아니라 **실현되는 결함**임이 확인됨 → 처방 순서 8단계 확정
> - **v5 §9 R8**: `processApprover`의 `from()`은 **감싸지 않고 현행 유지**로 확정

---

## 0. 착수 상태

**착수 가능.** 미확인 파일 0건, 미확정 결정 0건.

| 확인 항목 | 상태 | 결과 |
|---|---|---|
| `ApprovalStatus.java` | ✅ | `from()`이 영문·한글 모두 수용. `canTransitionTo`에 **TEMP_SAVED→TEMP_SAVED 없음** |
| `ApproverStatus.java` | ✅ | `PENDING`/`APPROVED`/`REJECTED` 3개. `from()` 동일 패턴. `PENDING → APPROVED\|REJECTED`, 종료 상태 전이 불가 |
| `ErrorCode.java` | ✅ | AP001~AP009 사용 중. 신규는 **AP010부터** |
| `AGENTS.md` | ✅ | 갱신 대상 2곳 확정 (§10) |
| **P0 `save()` merge/persist 실측** | ✅ | **merge 확정** (§3) |

---

## 1. 목표

`ApprovalService`(현재 812줄)를 폐기하고 **`ApprovalCommandService`(쓰기) / `ApprovalQueryService`(읽기)** 로 분리한다.
분리 과정에서 spec.md가 Stage 6 소관으로 지정한 결함 [A-잔여][B][C][D][F][G-비파일][L]을 함께 해결한다.

**분리는 수단이지 목적이 아니다.** 파일만 둘로 쪼개고 [B][C][F]를 그대로 옮기면 Stage 6는 실패다.
반대로 결함만 고치고 God Class를 유지해도 실패다. 둘 다 해야 한다.

### 성공 기준

| 지표 | Before (현재) | After (목표) |
|---|---|---|
| `ApprovalService` 라인 수 | 812줄 | **삭제** |
| `ApprovalCommandService` | — | 200~400줄 |
| `ApprovalQueryService` | — | 200~400줄 |
| `Approval` 신규 저장 | **merge** (SELECT 후 INSERT, 충돌 시 조용한 UPDATE) | **persist** (충돌 시 제약 위반 → 롤백) |
| 상태 변경 방식 | `new Entity(...)` + `save()` | Entity 메서드 + dirty checking |
| 결재 완료 판정 | `i == size - 1` (인덱스) | 전원 APPROVED 판정 |
| 회수 시 타인 처리 검증 | **없음 (AP009 미사용)** | CommandService에서 검증 |
| 예외 처리 | `catch(Exception){log.info}` | `BusinessException` 위임 |
| 임시저장→기안 | 삭제 후 재생성 | 같은 결재번호 유지, 상태만 전이 |
| 재임시저장 옛 첨부 | 디스크에 orphan 잔존 | 정상 삭제 |

---

## 2. 6 vs 7 경계 (확정)

애매하면 이 표가 이긴다.

| 항목 | 단계 | 근거 |
|---|---|---|
| `ApprovalService` → Command/Query 분리 | **6** | plan §4 |
| [A-잔여] 회수 시 "타인 결재 처리 여부" 검증 (AP009 활성화) | **6** | spec L99–101 "단계 6의 CommandService에서" |
| [B] 결재 완료 판정 재설계 | **6** | spec "단계 6에서 해결" |
| [C] 수정의 dirty checking 전환 | **6** | 〃 |
| [D] 신규 기안 status 서버 강제 | **6** | 〃 |
| [F] `save()` → persist/dirty checking | **6** | 〃 |
| [G-비파일] `approvalDelete` 예외 삼키기 | **6** | 〃 |
| [L] 임시저장→기안 전환 번호 유지 | **6** | 〃 |
| [K] close-out (persist 전환 — 재시도는 제외, D1=K1) | **6** | `05-generator.md` §8 D1 인계 |
| **결재자/참조자 조립 루프의 서비스 이관** | **6** | `05-generator.md` §0 "루프 이동은 Stage 6" |
| Controller의 임시저장 분기(`wasTemp.equals("ims")`) 제거 | **6** | [L]의 일부 — 분기가 곧 삭제-후-재생성 로직 |
| Controller의 `formNo = "ims"` 결정 → 서비스로 이동 | **6** | [D]와 한 몸. Stage 5 D2("Controller 유지")를 이번에 이동 |
| 재임시저장 시 옛 첨부 디스크 삭제 순서 교정 | **6** | `04-file-report.md` §4 "Stage 6에서 해소" |
| `approvalDelete` 첨부 중복 조회 통합 | **6** | `04-file-report.md` §5-3 "Stage 6 정리 대상" |
| — 이하 Stage 7 — | | |
| [I] `@RequestHeader("memberId")` 백도어 제거 | **7** | spec "단계 7에서 해결" |
| [A-확장] 회수 API 인증 정보 | **7** | 〃 |
| [H] `getCurrentMemberId()` 헬퍼 추출 | **7** | 〃 |
| [J] 이모지 로그·`System.out.println` 정리 | **7** | 〃 |
| `dounloadFile` 메서드명 교정 | **7** | `04-file.md` D3 |
| [K] 재시도 (persist 위에 얹는 close-out) | **7 이후** | D1=K1 확정에 따른 이월 |
| Controller 라인 수 100~120줄 달성 | **7** | spec 성공 지표는 7 시점 |

> **판단 규칙**: Stage 6는 Controller에서 **"서비스가 가져가야 할 재료"만** 뽑아낸다.
> Controller가 여전히 뚱뚱해도 6에서는 정상이다. 인증·헤더·로그는 손대지 않는다.
> 단, 조립 루프가 빠지면서 자연히 줄어드는 라인은 6의 부수 효과로 인정한다.

### 범위 밖 (명시)

- [E] 기안자 `_apr000` Approver 자동등록의 도메인 분리 → 스키마 변경 수반, 리팩토링 범위 밖
- `findLastApprovalNo`의 `LIKE %:yearFormNo%` 의미 변경 → 규칙 보존 (Stage 5 [K-부수] 기록만)
- **tx ↔ 디스크 원자성 전면 해결**(`store()` 반환 후 바깥 tx 롤백 시 orphan) → 별도 설계 주제 (D6)
- **자식 엔티티(`Approver`/`Referencer`/`Attachment`)의 persist 전환** → §3.5 관찰. [K] 방어에 불필요
- **임시저장 시 결재자가 `PENDING`으로 생성되는 것**(코드 L137 `//임시저장시엔?`) → 기존 동작 보존
- `selectApprovalList`의 `case "receivedAll"`이 비어 있어 `approvalPage`가 null인 채
  L660에 도달하는 **기존 NPE** → 기능 추가에 해당. **기록만 하고 고치지 않는다** (§9 R7)
- DB 스키마 변경, 프론트엔드 변경, 새 기능, `src/test/**`
- API 요청/응답 JSON 구조 변경 (spec 성공 기준: "기존 API 명세는 변경되지 않는다")

---

## 3. P0 실측 결과 — **merge 확정** ✅

### 3.1 증거

기안 1건(임시저장, 결재자 1·참조자 1·첨부 2)의 Hibernate SQL 로그. 시각 순.

| 시각 | 쿼리 | 해석 |
|---|---|---|
| 16.137 | `select approval_no from approval where approval_no like ? order by ... limit ?,?` | `nextApprovalNo` — 마지막 번호 조회 |
| **16.207** | **`select ... from approval where approval_no=?`** | ★ `approvalRepository.save(approval)`의 **merge 조회** |
| 16.236 / 16.241 | `select ... from approver where approver_no=?` ×2 | 결재자 2건 save의 merge 조회 |
| 16.245 | `select ... from referencer where ref_no=?` | 참조자 save의 merge 조회 |
| 16.258 / 16.265 | `select ... from apr_attachment where file_no=?` ×2 | 첨부 2건 save의 merge 조회 |
| 16.277~16.332 | member / department / position / form 조회 | `selectApproval` 시작 (**flush 유발 안 함** — 테이블 무관) |
| **16.347** | **`insert into approval (...)`** | ★ flush 시점에 비로소 INSERT |
| 16.358~16.368 | `insert into approver` ×2, `insert into referencer`, `insert into apr_attachment` ×2 | 〃 |
| 16.371 | `select ... from approver where approval_no=?` | `findByApprovalNo` — **이 파생 쿼리가 flush를 유발** |

**판정: merge 확정.** §3.2의 가설(`isNew()`가 ID != null → false → `em.merge()`)이 그대로 확인됐다.
INSERT 직전 **모든 저장 대상에 PK 단건 SELECT가 선행**한다.

### 3.2 이것이 뜻하는 것 — [K]는 이론이 아니라 실제 데이터 유실 경로

두 기안이 동시에 같은 `approvalNo`를 발급받으면:

1. 앞 기안이 커밋되어 `approval` 행이 존재하게 된다.
2. 뒤 기안의 `save()`가 16.207 자리에서 **그 행을 찾는다.**
3. merge는 찾은 행을 관리 상태로 붙이고 DTO 값을 덮어쓴다 → **INSERT가 아니라 UPDATE.**
4. 제약 위반 예외가 **나지 않는다.** 응답은 200이고, **앞 기안자의 결재는 조용히 사라진다.**

경쟁 구간도 좁지 않다. 번호 조회(16.137)와 실제 INSERT flush(16.347) 사이가 **약 210ms**이고,
여기에 HTTP 왕복이 더해진다.

→ **`05-generator.md` §2 [K-후속]이 경고한 시나리오가 실측으로 확정됐다.**
→ **재시도-on-예외는 현재 구조에서 절대 발동하지 않는다** (D1에서 재시도를 미루는 근거).

### 3.3 부수 확인 — [B]의 auto-flush 전제가 실증됨 ✅

16.371의 `findByApprovalNo`(파생 쿼리)가 **직전에 flush를 유발**했다(16.347~16.368의 INSERT 묶음).
반면 member/department/position/form 조회(16.277~16.332)는 테이블이 겹치지 않아 flush를 유발하지 않았다.

→ §5 [B]가 전제한 **"같은 tx 안에서 `approver.approve()` 직후 `findByApprovalNo`로 재조회하면 반영되어 있다"**가
Hibernate 동작으로 확인됐다. 별도 `flush()` 호출 없이 구현해도 된다. (보고서에 이 로그를 인용할 것)

### 3.4 부수 확인 — Stage 1.5 비회귀

16.406의 대기자 조회가 `and a1_0.approver_status='PENDING'`으로 렌더링됐다.
Stage 1.5의 Enum 상수 표기가 정상 동작 중이다.

### 3.5 관찰 (범위 밖, 기록만)

기안 1건에 **merge 전용 SELECT가 6회**(approval 1 + approver 2 + referencer 1 + attachment 2) 발생한다.
자식 엔티티까지 persist로 바꾸면 이 6회가 사라지지만, **[K] 방어에는 불필요**하고(부모 실패 시 tx 롤백)
Surgical 원칙상 이번에는 `Approval`만 전환한다. 성능 개선은 별도 주제로 기록만 한다.

---

## 4. 목표 구조

```
approval/
├─ controller/
│   └─ ApprovalController.java          (수정: 조립 루프·임시저장 분기 제거, 위임만)
├─ service/
│   ├─ ApprovalCommandService.java      ★ 신규 (쓰기)
│   ├─ ApprovalQueryService.java        ★ 신규 (읽기)
│   ├─ ApprovalService.java             ✗ 삭제
│   ├─ file/ApprovalFileService.java    (무변경 — 호출 순서만 교정)
│   └─ generator/ApprovalNoGenerator.java (무변경 — D1=K1이므로 재시도 없음)
├─ entity/  Approval.java (수정 메서드 추가 — D4), Approver.java (무변경)
├─ enums/   무변경
└─ repository/
    ├─ ApprovalRepository.java          (+ Custom 프래그먼트 — D1 F-a)
    ├─ ApprovalRepositoryCustom.java    ★ 신규
    ├─ ApprovalRepositoryImpl.java      ★ 신규
    └─ ApproverRepository.java          (+ 정렬 쿼리 — D5)
```

### 4.1 메서드 배치

**`ApprovalCommandService`** — 클래스 레벨 `@Transactional`
(**Spring의** `org.springframework.transaction.annotation.Transactional`. §9 R5)

| 신규 메서드 | 원본 | 해결 결함 |
|---|---|---|
| `draft(ApprovalDTO, List<MultipartFile>)` | `insertApproval` + Controller 채번·조립·임시저장 분기 | [D][F][K][L] |
| `resaveTempSaved(String, ApprovalDTO, List<MultipartFile>)` | `updateApproval` + Controller `updateApprovalTemp` 조립 루프 | [C][F][C-첨부] |
| `withdraw(String, int)` | `updateApprovalStatus` | **[A-잔여]** |
| `processApprover(String, ApproverDTO)` | `updateApprover` | [B][F] |
| `delete(String)` | `approvalDelete` | [G-비파일] + 중복 조회 통합 |

**`ApprovalQueryService`** — 클래스 레벨 `@Transactional(readOnly = true)`

| 신규 메서드 | 원본 |
|---|---|
| `getApproval(String)` | `selectApproval` |
| `getApprovalList(int, Map, int)` | `selectApprovalList` |
| `getFormList()` / `getForm(String)` | `selectFormList` / `selectForm` |
| `getDepartList()` / `getMemberList(int)` | `selectDepartList` / `selectMemberList` |
| `getMember(int)` / `getAllMemberList()` | `selectMember` / `selectAllMemberList` |
| (private) DTO 조립 헬퍼 | `ListToDTO`, `convertToMemberDTO` |

### 4.2 Command → Query 의존 (D8=a 확정)

현재 `insertApproval`·`updateApproval`·`updateApprovalStatus`·`updateApprover`는 **모두
마지막에 `selectApproval(...)`을 호출해 응답 DTO를 만든다.**
→ **CommandService가 QueryService를 주입한다. 단방향만 허용, 역방향 금지.**

---

## 5. 결함별 재설계

### [F] + [K] — `save()` 남용과 채번 동시성 (D1 = K1 + F-a)

**확정 처방**: `Approval` 신규 저장을 **persist로 전환**한다. **재시도는 넣지 않는다.**

| 위치 | 현재 | 처방 |
|---|---|---|
| `insertApproval` L134 `save(approval)` | **merge 확정**(§3) | ★ **persist** — `ApprovalRepositoryCustom.insert(Approval)` |
| `insertApproval` L147/163/176 자식 save | merge | **유지** (§3.5 — [K] 방어에 불필요) |
| `updateApproval` L376 `save(updateApproval)` | delete 후 새 객체 생성 | **삭제** — 조회한 엔티티에 dirty checking ([C]) |
| `updateApprover` L520 `save(approver)` | **detached 새 객체**에 `approve()` 호출 후 save | **삭제** — `findByApproverNo` → 메서드 호출 → dirty checking |
| `updateApprover` L525 `save(approval)` | **DTO로 재구성한 새 Approval**에 `markAsApproved()` | **삭제** — 동일 |

**persist 구현 (F-a)**

```
ApprovalRepositoryCustom.java   : void insert(Approval approval);
                                  void clearPersistenceContext();   ← R2 대응 (v5 추가)
ApprovalRepositoryImpl.java     : @PersistenceContext EntityManager em;
                                  → em.persist(approval);  /  em.clear();
ApprovalRepository extends JpaRepository<Approval, String>, ApprovalRepositoryCustom
```

> 명명 규칙 주의: 구현체 이름은 **반드시 `ApprovalRepositoryImpl`** 이어야 Spring Data가 프래그먼트로 인식한다.

**전환 후 기대 동작**: 동시 기안 충돌 시 flush/commit 시점에 제약 위반 → **tx 롤백 + 500**.
조용한 덮어쓰기가 사라진다. 뒤 사용자는 수동으로 다시 기안한다.

> `updateApprover`가 가장 나쁘다. DTO에서 `new Approver(...)` / `new Approval(...)`로
> **영속성 컨텍스트 바깥의 객체**를 만든 뒤 상태 전이 메서드를 호출하고 save로 밀어넣는다.
> Stage 1이 만든 Entity 메서드가 **dirty checking과 연결되지 않은 채** 쓰이고 있다. 이걸 끊는 게 [F]의 핵심이다.

---

### [A-잔여] 회수 시 타인 처리 여부 검증 — AP009 활성화

`AP009 = APPROVAL_WITHDRAW_ALREADY_PROCESSED`가 **정의만 되어 있고 던지는 코드가 없다.**
spec.md L96–101이 이유를 설명한다.

> [A] 회수 검증 누락 → `Approval.withdraw(memberId)`에서 본인 확인 + 상태 전이 검증
> → **"결재자 처리 여부" 검증은 단계 6의 CommandService에서** (Entity가 결재자 컬렉션을 알 필요 없도록 책임 분리)

현재 `updateApprovalStatus` L448에 `//***** 나를 제외한 다른 사람이 한사람이라도 처리했을 경우 회수 불가능`
주석만 남아 있다.

**처방**: `withdraw`에서 해당 `approvalNo`의 결재자를 조회해
**기안자(`approverOrder == 0`)를 제외한** 결재자 중 하나라도 `PENDING`이 아니면
`BusinessException(APPROVAL_WITHDRAW_ALREADY_PROCESSED)`.

> ⚠ **기안자 제외는 필수다.** `_apr000`은 생성 시점부터 `APPROVED`이므로 제외하지 않으면
> **모든 회수가 차단된다.** (§9 R9, §11 S10-a에서 반드시 검증)

---

### [B] — 결재 완료 판정 (D5 확정)

현재 `updateApprover` L503: `if (i == approverList.size() - 1)`. 문제가 **두 겹**이다.

1. **인덱스 기반 판정** — 앞 결재자가 미처리여도 마지막 사람이 승인하면 전체 승인 (spec [B] 원문)
2. **정렬 미보장** — `approverList`는 `findByApprovalNo`(= `ORDER BY` 없음) 결과다.
   "마지막 인덱스"가 "가장 큰 approverOrder"라는 보장이 애초에 없다.
   같은 리스트가 `selectApproval` L317 최종승인일 계산(`get(size()-1)`)에도 쓰인다.

**처방**
- 처리 대상 `Approver`를 `findByApproverNo`로 조회 → 없으면 `APPROVER_NOT_FOUND`(AP011)
- `approve()` / `reject()` 호출 (dirty checking)
- 승인인 경우: 해당 `approvalNo`의 **모든 Approver를 재조회해 전원 `APPROVED`인지** 판정.
  전원이면 `approval.markAsApproved()`. 기안자 `_apr000`은 항상 APPROVED이므로 자연히 포함된다.
- 반려인 경우: 순서와 무관하게 즉시 `approval.markAsRejected(reason)` (현행 유지)
- **정렬 쿼리 추가**: `findByApprovalNoOrderByApproverOrderAsc` — Query 경로의 결재선 순서와
  최종승인일 계산에 사용

> **flush**: §3.3에서 파생 쿼리의 auto-flush가 실증됐다. 명시적 `flush()` 불필요.
> `ApproverStatus.canTransitionTo`가 `APPROVED/REJECTED → false`이므로 **중복 처리는 엔티티가 막는다**(기존 동작).

---

### [C] — 수정이 삭제 후 재생성 (D4 확정)

현재 `updateApproval`: 자식 4종 삭제 → `approvalRepository.delete(existingApproval)` →
`new Approval(...)` → `save`. **PK가 같아도 행이 지워졌다 다시 생기므로** 이력·외부 참조가 끊긴다.
게다가 `existingApproval`을 delete한 뒤에도 계속 참조해 필드를 읽는다(L375).

**처방**
- 조회한 `Approval`을 **삭제하지 않고** 내용만 갱신 → `Approval.modifyDraft(title, content, formNo)` 신설
- **상태 가드**: `TEMP_SAVED`가 아니면 `APPROVAL_MODIFY_NOT_ALLOWED`(AP012)
- **요청의 status는 무시** — 아래 ★ 참조
- 자식(결재자·참조자·첨부)은 **전량 교체 유지** (번호가 순번 기반이라 diff는 과설계)
- 첨부 삭제 순서 교정 → [C-첨부]

> ★ **"status 무시"는 선택이 아니라 제약이다.**
> `ApprovalStatus.canTransitionTo`에 **`TEMP_SAVED → TEMP_SAVED` 전이가 없다**
> (`TEMP_SAVED -> target == PROCESSING`만 허용). 현행은 `new Approval(...)`로 상태를 직접 세팅해
> 전이 검증을 우회하지만, dirty checking으로 바꾸면서 요청 status를 반영하려 들면
> **재임시저장이 항상 예외로 실패한다.** `resaveTempSaved`는 상태를 건드리지 않는다.

---

### [C-첨부] — 재임시저장 시 옛 파일 orphan (D6 확정: 순서 교정 포함)

`04-file-report.md` §4가 **Stage 6 이월로 명시**한 항목이다.

현재 `updateApproval`은 L365에서 `attachmentRepository.deleteByApprovalNo(approvalNo)`로
**첨부 DB 행을 먼저 지운다.** 그 뒤 L433에서 `approvalFileService.deleteByApprovalNo(approvalNo)`를
호출하는데, 이 메서드는 내부에서 `findByApprovalNo`로 삭제 대상을 조회한다 → **이미 0건** →
**아무 파일도 지우지 않는다.** 재임시저장할 때마다 옛 첨부가 디스크에 영구히 쌓인다.

**처방**: 디스크 삭제(`approvalFileService.deleteByApprovalNo`)를 **첨부 DB 행 삭제보다 먼저** 수행한다.
[C] 재설계로 어차피 이 메서드의 순서를 다시 짜므로 추가 비용이 거의 없다.
**`ApprovalFileService` 자체는 무변경** — 호출 순서만 교정한다.

> ⚠ **순서 교정이 R2를 실현시킨다.** `deleteByApprovalNo`는 내부에서 `findByApprovalNo`로
> `Attachment` 엔티티를 **영속성 컨텍스트에 올린다.** 이어지는 벌크 DELETE는 컨텍스트를 우회하므로
> 그 엔티티들이 관리 상태로 남고, `store()`가 같은 PK로 save→merge 하면 **삭제된 행에 UPDATE**가
> 나가 `StaleStateException`이 터진다. 아래 순서를 반드시 지킬 것.

**`resaveTempSaved` 처방 순서 (R2 대응 확정 — v5)**

```
1. Approval 조회 + TEMP_SAVED 가드
2. 첨부 디스크 삭제      approvalFileService.deleteByApprovalNo()   ← Attachment 가 컨텍스트에 올라옴
3. 자식 DB 행 벌크 삭제   attachment / approver / referencer
4. 영속성 컨텍스트 비우기  ★ approvalRepository.clearPersistenceContext()
5. Approval 재조회        ← 4에서 detach 됐으므로 반드시 다시 읽는다
6. approval.modifyDraft(...)  ← dirty checking
7. 결재자·참조자 재삽입
8. 첨부 재저장            approvalFileService.store()
```

- 4의 `clear()`는 **`Approval`을 포함해 컨텍스트 전체를 비운다.** 그래서 5의 재조회와
  6의 `modifyDraft`가 **clear 이후**여야 한다. 순서를 바꾸면 수정 내용이 유실된다.
- `@Modifying(clearAutomatically = true)`는 **채택하지 않는다.** 리포지토리 메서드의 모든 호출자
  (`delete()` 경로 포함)에 전역으로 영향을 주고, `Approval`까지 detach시켜 dirty checking을 깬다.
- `clearPersistenceContext()`는 D1의 `ApprovalRepositoryCustom`에 **두 번째 메서드로** 둔다
  (F-a 원칙 — 서비스에 `EntityManager`를 노출하지 않는다).

> `04-file.md` §7-2 수동 검증("기존 파일 전부 삭제")은 Stage 4에서 **"아직 통과하지 않는 것이 정상"**이었다.
> **Stage 6에서 통과해야 한다.** → §11 S11

---

### [D] — 임시저장 status 클라이언트 신뢰 (D3=a 확정)

현재 Controller L215·221:

```java
String approvalStatus = approvalDTO.getApprovalStatus();
if (approvalStatus.equals("임시저장")) { formNo = "ims"; }
```

그리고 Service L128에서 `approvalDTO.getApprovalStatus()`가 **그대로** 저장된다.
클라이언트가 `APPROVED`를 보내면 결재 없이 승인 상태로 시작한다.

여기에 Stage 5 **[관찰]**이 겹친다: 이 분기는 **한글 리터럴에만** 반응하므로
영문 `TEMP_SAVED`를 보내면 formNo가 `ims`로 안 바뀐다. Stage 1 Enum화의 잔여 불일치다.

**처방**
- 입력을 `ApprovalStatus.from()`으로 **정규화** — 영문·한글 양쪽 수용(확인 완료)
- **화이트리스트**: `TEMP_SAVED` / `PROCESSING` 두 값만 허용, 그 외 `APPROVAL_INVALID_INITIAL_STATUS`(AP010)
- `from()`이 던지는 `IllegalArgumentException`을 감싸 AP010으로 변환 (§9 R8)
- `ims` 분기를 **Enum 판정**으로 재작성 → 한글 리터럴 잔재 제거
- 판정 위치를 **CommandService로 이동** (Stage 5 D2의 "Controller 유지"를 이번에 변경)

---

### [G-비파일] — 예외 삼키기

`approvalDelete` L707~744: 바깥 try 하나 + 안쪽 try 4개, 전부 `log.info("...오류")`로 삼키고
바깥은 `return false`. **트랜잭션 안에서 예외를 삼켜 롤백이 안 되므로 부분 삭제가 커밋된다.**

**처방**
- 중첩 try-catch 전부 제거, 예외 전파 → `GlobalExceptionHandler`
- `04-file-report.md` §5-3이 이월한 **첨부 중복 조회** 통합
  (`attachmentList.isEmpty()` 가드용 조회 + FileService 내부 조회)
- **반환 타입 `boolean` 유지** — 현재 API가 `ResponseMessage<Boolean>`이므로 시그니처를 바꾸면
  응답 JSON이 바뀐다(spec 성공 기준 위반). 성공 시 항상 `true`, 실패는 예외

---

### [L] — 임시저장→기안 전환 (D2=L1 확정)

현재 Controller L199~213:

```java
String wasTemp = originApprovalNo.substring(5, 8);
if (wasTemp.equals("ims")) { approvalService.approvalDelete(originApprovalNo); }
```

→ 기존 결재를 **삭제**하고, 아래에서 **새 번호를 채번**해 새로 만든다. 번호가 바뀌어 이력이 끊긴다.
`substring(5, 8)`은 번호 포맷에 하드코딩된 문자열 파싱이다.

**처방**: `POST /approvals`에 `approvalNo`가 실려 오고 그 결재가 `TEMP_SAVED`면
**같은 결재번호를 유지한 채** `approval.submitFromTempSaved()`(Stage 1에서 이미 존재)를 호출한다.
`canTransitionTo`가 `TEMP_SAVED → PROCESSING`을 허용하므로 그대로 통과한다.
결재자·참조자·첨부는 교체. Controller의 분기·삭제 호출 제거, CommandService가 판단한다.

> **⚠ 확정된 트레이드오프 (D2=L1)**: 임시저장은 `formNo = "ims"`로 채번되므로 번호가 `2026-ims00001`이다.
> 번호를 유지하면 **정식 기안 문서의 결재번호에 `ims`가 영구히 남고**, `FORM_NO` 컬럼은 실제 양식으로
> 갱신되므로 **번호와 양식이 불일치**한다. 이력 추적 보존을 위해 **의도적으로 수용한 결과**다.
> §11 S3에서 눈으로 확인한다.

---

## 6. Scope — 수정 허용 파일

**신규**
- `approval/service/ApprovalCommandService.java`
- `approval/service/ApprovalQueryService.java`
- `approval/repository/ApprovalRepositoryCustom.java`
- `approval/repository/ApprovalRepositoryImpl.java`

**수정**
- `approval/controller/ApprovalController.java` (조립 루프·임시저장 분기·채번 호출 제거, 위임)
- `approval/entity/Approval.java` (`modifyDraft` 추가)
- `approval/repository/ApprovalRepository.java` (Custom 프래그먼트 상속)
- `approval/repository/ApproverRepository.java` (정렬 쿼리 추가)
- `common/error/ErrorCode.java` (AP010~AP012)

**삭제**
- `approval/service/ApprovalService.java`

**금지 (손대지 않음)**
- `ApprovalFileService.java` — **호출 순서만 바꾸고 이 파일 자체는 무변경**
- `ApprovalNoGenerator.java` — D1=K1이므로 재시도 없음, 무변경
- `enums/ApprovalStatus.java`, `enums/ApproverStatus.java`, `entity/Approver.java`
- `dto/**` (D7=a)
- `src/test/**`, 프론트엔드, DB 스키마

---

## 7. ErrorCode — 기존 재사용 2건 + 신규 2건 (v4 정정)

> **v4 정정**: v3은 `APPROVER_NOT_FOUND`를 AP011 신규로 적었으나, **`ErrorCode.java`에 이미
> `APPROVER_NOT_FOUND = AP004`(404)가 존재한다.** 중복 상수라 컴파일도 되지 않는다.
> 착수 시 실물 확인에서 발견됨. 아래가 확정본이다.

### 기존 상수 재사용 (추가하지 말 것)

| 상수 | 코드 | 이번 단계 용도 |
|---|---|---|
| `APPROVER_NOT_FOUND` | **AP004** (기존, 404) | [B] 처리 대상 결재자 없음 — 현재는 조용히 null 반환 |
| `APPROVAL_WITHDRAW_ALREADY_PROCESSED` | **AP009** (기존, 400) | [A-잔여] — 정의만 되어 있고 던지는 코드가 없었다. **이번에 활성화** |

### 신규 추가 — **번호 연속, 구멍 없이**

| 상수 | 코드 | 용도 | HTTP |
|---|---|---|---|
| `APPROVAL_INVALID_INITIAL_STATUS` | **AP010** | [D] 신규 기안 status 화이트리스트 위반 | 400 |
| `APPROVAL_MODIFY_NOT_ALLOWED` | **AP011** | [C] TEMP_SAVED가 아닌 결재 수정 시도 | 400 |

- **번호를 비워두지 않는다.** `leave-pattern.md` §9의 "같은 도메인의 에러는 번호 순서대로"를 따른다.
  v3 표의 번호는 예약이 아니라 제안이었다(§7이 "착수 시점에 다시 열어 최종 확정한다"고 명시했다).
- `APPROVAL_NO_GENERATION_FAILED`는 **추가하지 않는다** — D1=K1이라 재시도가 없다.

---

## 8. 결정 사항 (D1~D9 전 항목 확정 — 2026-07-29)

### ⭐ D1. [K] close-out — **✅ 확정: K1(persist 전환만) + F-a(커스텀 프래그먼트)**

**확정 내용**: `Approval` 신규 저장을 persist로 전환한다. **재시도는 이번 단계에 넣지 않는다.**

근거 세 가지.
1. §3 실측으로 [K]의 실제 피해가 **"조용한 덮어쓰기"**임이 확정됐다. persist 전환만으로 그 경로가
   사라지고 충돌은 롤백되는 500이 된다. **데이터는 안전해진다.**
2. 재시도는 **진입점이 정리된 뒤**에 붙이는 게 훨씬 싸다. `draft()`가 `@Transactional`인데
   그 안에서 제약 위반을 잡아 재시도하면 **이미 tx가 rollback-only로 오염되어 반드시 실패한다.**
   재시도 루프는 tx 바깥에 있어야 하므로 비-tx 진입 빈 추가 또는 self-proxy가 필요하고,
   이는 "God Class를 Command/Query 둘로 정리한다"는 이번 단계의 목표 구조를 흐린다.
3. Surgical — Stage 6는 이미 [A-잔여][B][C][D][F][G][L] 7개를 들고 있다.

> **Stage 5 인계와의 관계**: `05-generator.md` §8 D1은 Stage 6 인계를 "(a) 재시도 + [F] persist 전환"으로
> 적었다. K1은 방향 (a)를 바꾸는 게 아니라 **분할 실행**이다. persist 전환을 6에서 끝내고,
> 재시도는 Controller가 얇아진 뒤(7 이후)에 진입점 한 곳에 붙인다.

**구현 방식 F-a 확정**: `ApprovalRepositoryCustom` + `ApprovalRepositoryImpl`(`EntityManager.persist`).
서비스에 JPA API를 노출하지 않고 리포지토리 계약을 유지한다.
F-b(서비스에 EntityManager 주입)·F-c(`Persistable` 구현, 엔티티에 `@Transient isNew` 침투)는 채택 안 함.

### D2. [L] 전환 시 결재번호 정책 — **✅ 확정: L1 (번호 유지)**
spec [L] 문언대로 같은 결재번호를 유지한다. **기안 문서 번호에 `ims`가 남는 것을 수용한다**
(이력 추적 보존 > 번호 미관). L2(채번 규칙 변경)는 Stage 5 보존 규칙을 건드리므로 범위 밖.

### D3. [D] status 강제 범위 — **✅ 확정: D3-a**
화이트리스트(`TEMP_SAVED`/`PROCESSING`) + `from()` 정규화 + `ims` 분기 Enum 재작성.
`from()`이 영문·한글을 모두 수용하므로 하위호환 설계 불요.

### D4. [C] 수정 경로 재설계 — **✅ 확정: 추천안 전체**
`modifyDraft` 신설 / 상태 가드(AP012) / **요청 status 무시(제약)** / 자식 전량 교체 유지 / 첨부 순서 교정.

### D5. [B] 판정과 정렬 — **✅ 확정: 추천안**
전원 APPROVED 판정 + 정렬 쿼리 추가. **순서 강제(내 차례 아니면 승인 거부)는 넣지 않는다**
— spec 범위 외 "결재 비즈니스 정책의 변경"에 해당. 별도 항목으로 기록만.

### D6. 첨부 orphan / tx↔디스크 원자성 — **✅ 확정: 순서 교정 + 중복 조회 통합 포함, 전면 해결은 이월**
`04-file-report.md` §4·§5-3의 이월 항목을 닫는다. tx↔디스크 원자성(`TransactionSynchronization`)은
별도 설계 주제로 남기고 `[APPROVAL_FILE_ORPHAN]` 로그 관찰을 유지한다.

### D7. DTO / 매핑 정리 범위 — **✅ 확정: D7-a (DTO 시그니처 불변)**
`// TODO: Stage 6에서 제거` 주석(L283·341·374·450·497·507·513·687)은 수동 조립 코드가
Query/Command로 이동하며 자연 해소된다. **DTO를 Enum 타입으로 바꾸지 않는다** — Jackson 기본
역직렬화는 `from()`을 거치지 않아 `name()`만 매칭하므로 **한글 전송 경로가 깨진다**.

### D8. Command → Query 의존 방향 — **✅ 확정: D8-a (단방향 주입)**
CommandService가 QueryService를 주입. 역방향 금지.
QueryService의 `readOnly = true`는 **쓰기 tx에 참여할 때 무시**된다는 점을 보고서에 명시한다.

### D9. [A-잔여] 회수 검증 — **✅ 확정: D9-a**
기안자(`approverOrder == 0`) 제외, 나머지 중 하나라도 `PENDING`이 아니면 AP009.
**동작 변경 항목** — 지금까지 되던 회수가 막힌다. §11 S10에서 양방향 검증 필수.

---

## 9. 위험 목록

| # | 위험 | 대응 |
|---|---|---|
| **R1** | **무성 실패** — [A-잔여]·[B]·[C]·[K]는 단일 요청·해피패스로는 드러나지 않는다 (단계 1.5 교훈) | §11 S1~S6·S10~S11을 **반드시** 수행 |
| **R2** ★ | **실현 확정(v5).** D6 순서 교정으로 `ApprovalFileService.deleteByApprovalNo`가 `findByApprovalNo`로 `Attachment` 엔티티를 **영속성 컨텍스트에 올린다.** 이어지는 `@Modifying` 벌크 DELETE는 컨텍스트를 우회하므로 그 엔티티가 **관리 상태로 남는다.** 그 뒤 `store()`가 같은 PK(`_f001`)로 save→merge 하면 merge가 컨텍스트에서 그 인스턴스를 찾아 상태를 덮어쓰고, flush 때 **이미 삭제된 행에 UPDATE**가 나가 0건 → `StaleStateException`(500) | **§5 [C-첨부]의 처방 순서를 따른다** — 자식 삭제 직후 영속성 컨텍스트를 비우고 `Approval`을 재조회한 뒤 `modifyDraft`. `@Modifying(clearAutomatically = true)`는 **채택하지 않는다**(전역 영향 + `Approval`까지 detach). S2·S11을 SQL 로그로 검증할 것 |
| **R3** | `ApprovalService` 삭제로 컴파일이 광범위하게 깨진다 | Controller를 같은 커밋에서 함께 수정. 단계 완료 시점에 빌드 통과 유지 |
| **R4** | [B] 정렬 추가로 **응답 결재선 배열 순서가 바뀔 수 있음** | §11 S8에서 프론트 표시 확인 |
| **R5** | 현재 `jakarta.transaction.Transactional` 사용 중. **`readOnly` 속성이 없다** | QueryService는 `org.springframework.transaction.annotation.Transactional(readOnly = true)`. import 혼용 주의 |
| **R6** | `selectApproval`의 `findById(...).orElse(null)` 후 즉시 역참조 → NPE. 반려 시 `.orElse(null).format()`도 NPE | `orElseThrow(APPROVAL_NOT_FOUND)`로 최소 교정(404). 동작 변경이므로 보고서에 명기 |
| **R7** | `case "receivedAll"`이 비어 있어 `approvalPage`가 null인 채 L660 도달 → 기존 NPE | **범위 밖. 고치지 않는다.** 원문 그대로 옮기고 보고서에 기록 |
| **R8** | `ApprovalStatus.from()` / `ApproverStatus.from()` 모두 실패 시 **`IllegalArgumentException`**(BusinessException 아님) → 그대로 두면 500/C999 | **[D] 경로만 AP010으로 변환한다(필수).** `processApprover`의 `ApproverStatus.from()`은 **감싸지 않고 현행 유지(500/C999)** — v5 확정. 비대칭이 아니라 범위 차이다: [D]는 화이트리스트 검증 자체가 이번 단계 요구사항이라 실패 코드 정의가 따라오지만, `processApprover`는 [B](완료 판정)·[F](dirty checking)만 대상이고 입력 검증은 요구사항이 아니다. 또한 이 예외는 **삼켜지지 않고 정상 전파되어 롤백되므로 [G]의 대상도 아니다.** 보고서에 잔여로 기록 |
| **R9** | [A-잔여]에서 기안자 `_apr000`(항상 APPROVED)을 제외하지 않으면 **모든 회수가 차단**된다 | `approverOrder == 0` 제외를 코드·검증 양쪽에서 확인 (§11 S10-a) |
| **R10** | `ApprovalRepositoryImpl` 이름을 틀리면 Spring Data가 프래그먼트를 못 찾아 **기동 실패** | bootRun 성공이 곧 확인. 실패 시 클래스명·패키지 위치 점검 |

---

## 10. AGENTS.md 갱신 (Stage 5 → Stage 6)

**(1) "현재 진행 단계" 블록 (L21–23)**

before:
```markdown
### 현재 진행 단계
**단계 5: 결재번호 생성기 분리** — `docs/refactoring/approval/tasks/05-generator.md`
(단계 1·1.5·2·3 완료 / 단계 4 파일 처리 분리 완료·커밋·푸시 — `89d27b8`(코드)·`c30ea54`(문서)·`+.gitignore`, origin/main 동기화)
```

after:
```markdown
### 현재 진행 단계
**단계 6: God Class 분리 (Command / Query)** — `docs/refactoring/approval/tasks/06-command-query.md`
(단계 1·1.5·2·3·4 완료 / 단계 5 결재번호 생성기 분리 완료·커밋·푸시 — `80f60b7`(코드)·`0215951`(문서), origin/main 동기화)
```

**(2) 단계 로드맵 (L25–35)**

before:
```
5. 결재번호 생성기 분리 (현재)
6. God Class 분리 (Command / Query)
```

after:
```
5. 결재번호 생성기 분리 ✅
6. God Class 분리 (Command / Query) (현재)
```

> AGENTS.md 갱신은 **Stage 6 문서 커밋에 포함**한다(Stage 5가 `0215951`에서 그렇게 했다).

---

## 11. 검증

### 자동 검증

```powershell
cd final
.\gradlew.bat compileJava
.\gradlew.bat bootRun
```

`compileJava` BUILD SUCCESSFUL + `Started Application` 로그 확인
(기동 성공이 곧 `ApprovalRepositoryImpl` 프래그먼트 인식 확인 — R10).

**검색 확인 (PowerShell 5.1)**

> ⚠ **`Select-String`에는 `-Recurse`/`-Include`가 없다**(프로젝트 `CLAUDE.md` 명시).
> 반드시 `Get-ChildItem ... -Recurse | Select-String` 파이프 형태로 쓴다.

```powershell
cd final
$approval   = Get-ChildItem -Path .\src\main\java\com\insider\login\approval -Filter *.java -Recurse
$service    = Get-ChildItem -Path .\src\main\java\com\insider\login\approval\service -Filter *.java -Recurse
$controller = Get-ChildItem -Path .\src\main\java\com\insider\login\approval\controller -Filter *.java -Recurse

# 1. ApprovalService 잔존 참조 0건
$approval | Select-String -Pattern "ApprovalService" -Encoding UTF8

# 2. save() 잔존 — 눈으로 확인 (자식 생성 경로만 남고, 상태 변경 경로에는 0건)
$service | Select-String -Pattern "Repository\.save\(" -Encoding UTF8

# 3. 예외 삼키기 잔존 0건
$approval | Select-String -Pattern "catch\s*\(\s*Exception" -Encoding UTF8

# 4. 인덱스 기반 완료 판정 잔존 0건
$approval | Select-String -Pattern "size\(\)\s*-\s*1" -Encoding UTF8

# 5. 한글 상태 "문자열 리터럴" 잔존 0건 (주석 무시 — 따옴표 안만)
$approval | Select-String -Pattern '"(임시저장|처리 중|대기|승인|반려)"' -Encoding UTF8

# 6. AP009 가 드디어 사용되는지 (0건이면 [A-잔여] 미구현)
$approval | Select-String -Pattern "WITHDRAW_ALREADY_PROCESSED" -Encoding UTF8

# 7. 임시저장 분기의 substring 파싱 제거 확인 0건
$controller | Select-String -Pattern "substring\(5" -Encoding UTF8
```

> ⚠ `compileJava`·`bootRun` 통과는 **아무것도 증명하지 않는다**(단계 1.5). 아래 수동 검증이 본체다.

### 수동 검증 — 시나리오 (사용자 담당)

**S0. [F] persist 전환 확인** ★신설 — SQL 로그로
기안 1건을 쏘고 로그를 본다. **`insert into approval` 앞에
`select ... from approval where approval_no=?`가 없어야 한다.**
(§3.1의 16.207 자리가 사라진다. 자식 엔티티의 merge SELECT는 그대로 남는 게 정상 — §3.5)

**S1. [B] 결재 순서 역전** ★핵심
결재자 3명(order 1·2·3) 기안 → **order 3이 먼저 승인** →
전체 결재 상태가 **`PROCESSING` 유지**여야 한다(현행은 `APPROVED`로 잘못 바뀜).
이어서 1·2가 승인 → 그때 `APPROVED`.

**S2. [C] 수정 후 동일성 보존**
임시저장 기안 → PUT으로 제목·내용·결재선 변경 →
`APPROVAL_NO` **불변**, `APPROVAL` 행이 **삭제됐다 생기지 않았는지** SQL 로그로 확인
(`update approval`이면 정상, `delete` + `insert`면 실패). **R2 재검증 지점.**

**S3. [L] 임시저장 → 기안 전환 번호 유지** ★
임시저장(`2026-ims0000N`) → 같은 결재를 기안 →
**결재번호 불변**, 상태 `TEMP_SAVED` → `PROCESSING`, 결재자·참조자 재구성,
**첨부는 이번 POST에 실린 파일로 교체**(옛 첨부는 디스크·DB에서 삭제 — 현행 동작 보존).
**번호에 `ims`가 남는다** — D2=L1의 의도된 결과인지 눈으로 확인.

**S4. [D] status 위조 차단**
- `approvalStatus = "APPROVED"` / `"승인"` 전송 → **400 + AP010** (500/C999면 R8 미처리)
- 정상 값 4가지(`PROCESSING`, `TEMP_SAVED`, `"처리 중"`, `"임시저장"`) 모두 통과
- **한글 `"임시저장"`과 영문 `TEMP_SAVED` 둘 다 `formNo=ims`가 붙는지** ← Stage 5 [관찰] 해소 확인

**S5. [G] 삭제 중 실패 시 롤백**
(가능하면) 첨부 DB 삭제 단계에서 실패를 유도 → **부분 삭제가 커밋되지 않고** 전체 롤백,
응답은 에러 코드. 현행은 `return false`로 조용히 넘어간다.

**S6. [K] 동시 기안** ★
같은 폼으로 **동시에 2건 POST** → 한 건 성공 + **다른 건 500(롤백)**.
**둘 다 200인데 DB 행이 1개면 실패**(merge 덮어쓰기가 남아 있다는 뜻).

**S7. [D] 프론트 실제 전송값 확인** (기록용)
프론트에서 임시저장/기안을 눌러 요청 body의 `approvalStatus`가 한글인지 영문인지 확인.
D3-a는 양쪽 모두 동작하므로 고칠 필요는 없고, 향후 DTO Enum 전환(D7-b) 판단 근거로 기록한다.

**S8. 회귀 — 응답 형식 동등성** ★
아래 전부가 Stage 5와 **동일한 JSON 구조**를 반환해야 한다.
- 상세 조회 (결재선 **순서** 포함 — R4)
- 목록 조회 5종 (`given` / `tempGiven` / `received` / `receivedRef` / `receivedAll`)
- 회수, 결재 처리(승인·반려), 삭제, 파일 다운로드
- 무첨부 기안, 첨부 2건 기안
- 최종승인일(`finalApproverDate`), 대기자(`standByApprover`) 표시

**S9. Stage 1.5 비회귀**
결재 대기함·임시저장함 조회/검색/카운트가 **0건으로 조용히 죽지 않는지** 확인.

**S10. [A-잔여] 회수 검증** ★ — **양방향 필수**
- **(a) 아무도 처리하지 않은 결재 회수 → 성공해야 한다.**
  실패하면 기안자 `_apr000` 제외를 빠뜨린 것 (R9). **(b)보다 먼저 확인할 것.**
- (b) 결재자 1명이 승인한 뒤 회수 → **400 + AP009**로 막혀야 한다 (현행은 그냥 회수됨)
- (c) 본인 아닌 사람이 회수 → 기존대로 AP008

**S11. [C-첨부] 재임시저장 옛 파일 삭제** ★
첨부 2건으로 임시저장 → 다른 첨부 2건으로 재임시저장 →
**디스크에서 옛 파일 2건이 사라지고 새 파일 2건만 남는지** 육안 확인.
`04-file.md` §7-2가 Stage 4에서 미통과였던 항목 — **이번에 통과해야 한다.**

---

## 12. 실행 순서

```
P0. save() merge/persist 실측                 ✅ 완료 — merge 확정 (§3)
P1. ApproverStatus.java 확인                   ✅ 완료 (§0)
P2. D1~D9 확정                                 ✅ 완료 (§8)
      ↓
P3. ErrorCode AP010~AP012 추가                 ← Claude Code
      ↓
P4. QueryService 분리 (읽기 먼저 — 동작 변경 없음, 안전)
      ↓
P5. CommandService 분리 + [A-잔여][B][C][D][F][G][L] 재설계
      · ApprovalRepositoryCustom/Impl 신설 (persist)
      · Approval.modifyDraft 추가
      · ApproverRepository 정렬 쿼리 추가
      ↓
P6. Controller 조립 루프·임시저장 분기 이관
      ↓
P7. ApprovalService 삭제 + compileJava + bootRun + grep
      ↓
P8. 수동 검증 S0~S11 (§11)                     ← 사용자
      ↓
P9. 보고서 작성 + AGENTS.md 갱신 + 커밋 + 푸시   ← 단계 완료
```

> **P4를 먼저 하는 이유**: 읽기 경로는 동작 변경이 없어 회귀 위험이 낮다.
> 여기서 분리 골격과 DTO 조립을 안정화한 뒤 P5의 재설계에 들어가면,
> 문제가 생겼을 때 "분리 때문인지 재설계 때문인지" 구분할 수 있다.

---

## 13. 착수 전 체크 (Claude Code)

1. §8 D1~D9 **확정 완료** — 확정본대로 진행. 임의 변경 금지.
2. **[K] 재시도를 넣지 않았는가?** (D1=K1 — 넣었으면 위반 → 중단·보고)
3. `resaveTempSaved`가 **요청의 status를 반영하지 않는가?** (반영하면 `TEMP_SAVED→TEMP_SAVED` 전이 없어 항상 실패)
4. [A-잔여] 검증에서 **기안자 `approverOrder == 0`을 제외했는가?** (R9)
5. 범위 외(§2·§6) 파일·라인은 원문 그대로 두는가? (`ApprovalFileService`·`ApprovalNoGenerator`·`enums`·`dto`)
6. `ApprovalRepositoryImpl` 클래스명이 정확한가? (R10)
7. 검증에 S0(persist 확인)·S1(순서 역전)·S10(회수 양방향)·S11(첨부 삭제)을 포함했는가?
8. 예상 못 한 상황은 추측 말고 **중단·보고**.

---

## 14. 작업 원칙 리마인더

- **Surgical**: 이 문서에 없는 요구사항을 창작하지 않는다. R7(`receivedAll` NPE)처럼
  눈에 보여도 범위 밖이면 **그대로 옮기고 기록만** 한다.
- **단계 경계**: §2 표가 최종 권위. 애매하면 Stage 7로 미룬다.
- **파일이 진실의 원천**: 진행 중 발견한 결정은 이 문서 또는 보고서에 반영한다.
- **자동화 테스트 없음**: 새로 만들지 않는다. 검증 = `compileJava` + `bootRun` + **수동 API**.
- **단계 완료 = 커밋 + 푸시.**
