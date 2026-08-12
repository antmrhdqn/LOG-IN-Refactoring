# 작업 E: 읽기 경로 인가 — 작업 보고서

> 작업일: 2026-08-12
> 실행: Claude Code (Opus 5) / 검증: 사용자 (캡처 매트릭스 20항목)
> 명세: `docs/security/tasks/03-read-authz.md` (**확정본 v3** — D1~D10 확정)
> 선행: 작업 A 쓰기 경로 인가(`91de70c`·`8731d37`) · 작업 B 비밀 노출 차단(`3e2db66`·`4c2b50b`)
> **상태: 구현·빌드·검증 전 항목 완료** (캡처 20항목 PASS · S2 쓰기 스모크 · S3 로그 구분).
> **미수행 0건. 커밋·푸시가 남았다** (§6-3)

---

## 1. 변경 파일

**코드 3개** (명세 §5 그대로. 4번째 코드 파일 없음):

| 파일 | 처방 |
|---|---|
| `approval/service/ApprovalQueryService.java` | [E0] `canRead` / [E1] `getApproval` 오버로드 + 가시성 / [E2] `getReadableAttachment` |
| `approval/controller/ApprovalController.java` | [E1] 상세 호출 시그니처 / [E2] 다운로드 인가 + DB값 전달 |
| `approval/repository/AttachmentRepository.java` | [E2] `findByFileSavename` |

**문서**: `docs/security/reports/02-secret-exposure-report.md`(§9-1 정정), 본 보고서(신규).
`spec.md`·`AGENTS.md` 갱신은 §6에 남겼다.

### git diff --stat (코드)

```
 .../approval/controller/ApprovalController.java    | 11 +++-
 .../approval/repository/AttachmentRepository.java  |  4 ++
 .../approval/service/ApprovalQueryService.java     | 63 +++++++++++++++++++++-
 3 files changed, 74 insertions(+), 4 deletions(-)
```

**`ApprovalCommandService.java` 무변경** — 명세 §5·R2의 판정 기준이다. [E1]을 `private`이 아니라
`package-private`으로 구현했으므로 호출 5건(`:131`·`:170`·`:193`·`:239`·`:291`)이 무수정으로 컴파일됐다.

`JwtAuthorizationFilter`·`WebSecurityConfig`·`ErrorCode.java`·`GlobalExceptionHandler`·
`ApprovalFileService`·`getApprovalList`의 `switch` 분기·`src/test/**`·DB 스키마·프론트엔드
**전부 무변경.**

> **라인 드리프트 주의**: `ApprovalQueryService`가 순증 59줄(+63 −4)이라 아래쪽 코드가 밀렸다.
> 명세 v2가 인용한 `listToDTO:415`·`received:314`는 현재 **`:474`·`:373`**이다.
> **위치만 밀렸을 뿐 두 호출은 무변경**이며, 밀린 폭이 순증과 정확히 일치한다.

---

## 2. 구현 내용

### [E0] `canRead` — `ApprovalQueryService:167`

```
1) approval.getMemberId() == memberId              → true   (기안자)
2) approvalStatus == TEMP_SAVED                    → false  (기안자 외 전원)
3) 결재선(approverRepository.findByApprovalNo)에 있음 → true
4) 참조선(referencerRepository.findByApprovalNo)에 있음 → true
5) 그 외                                            → false
```

- **(2)가 (3)·(4)보다 앞이다.** 임시저장 문서에도 결재선이 저장돼 있어(기준선 #15), 순서를 바꾸면
  남의 초안이 결재자에게 열린다
- 기안자 판정은 `Approval.memberId` 비교로 **명시적으로** 한다. `apr000`이 기안자라는 현재 구현에
  의존하지 않는다
- **role(ADMIN/MEMBER) 분기 없음** (D2). 관계만 본다 — ADMIN도 관계가 없으면 차단된다
- **신규 리포지토리 조회 0건.** 이미 주입돼 있던 `approverRepository`·`referencerRepository`를 쓴다

### [E1] 상세 조회 인가 — 진입점 분리

| 위치 | 변경 |
|---|---|
| `ApprovalQueryService:152` | **신설** `public ApprovalDTO getApproval(String approvalNo, int memberId)` — 조회 → `canRead` → 기존 로직에 위임 |
| `ApprovalQueryService:192` | 기존 `getApproval(String)`에서 **`public` 제거** (package-private) |
| `ApprovalController:60` | 새 오버로드 호출 (`getCurrentMemberId()` 전달) |

**건드리지 않은 것**: `listToDTO`(현 `:474`)·`received`(현 `:373`)의 `getApproval` 호출.
목록은 이미 관계로 필터링돼 있고, 여기에 인가를 얹으면 목록 5종이 항목마다 판정을 돌게 되며
판정에 걸리는 항목이 하나라도 섞이면 **목록 전체가 404**가 된다(D1·R1).

**가시성이 `package-private`인 이유와 그 한계**: `ApprovalCommandService`가 같은
`approval.service` 패키지에서 5번 호출한다. 완전 봉인이 아니며, 패키지 안에서는 여전히
인가 없이 호출 가능하다. 쓰기 경로는 작업 A에서 이미 인가를 통과한 뒤 DTO를 만들 뿐이므로
그것이 의도된 상태다.

### [E2] 첨부 다운로드 인가

| 위치 | 변경 |
|---|---|
| `AttachmentRepository` | **신설** `Optional<Attachment> findByFileSavename(String)` |
| `ApprovalQueryService:288` | **신설** `public Attachment getReadableAttachment(String fileSavename, int memberId)` |
| `ApprovalController:167~172` | 판정 호출 후 **DB값**을 `loadAsResource`에 전달 |

```
1. findByFileSavename 없음        → AP007
2. 소속 결재 findById 없음         → AP007
3. canRead 실패                   → log.warn 후 AP007
4. return attachment
```

**1단계가 저장명 열거·경로 이탈을 구조적으로 닫는다.** 빈 문자열도 `..`도 `Attachment` 행이
없으므로 파일시스템에 닿기 전에 404다. 그리고 `resolve()`에 들어가는 값이 **요청 파라미터가 아니라
DB에 저장된 저장명**이 되므로 이탈 자체가 성립하지 않는다(D7). `Content-Disposition`의 파일명도
DB의 `fileOriname`을 쓴다(D8).

- 판정은 **`ApprovalQueryService`에 뒀다.** `ApprovalController` 생성자는 여전히 서비스 3개만 받으며
  **리포지토리를 주입하지 않았다**(D10 — 검색 확인 4번으로 검증)
- `ApprovalFileService`는 열지 않았다. `loadAsResource`의 **호출 인자만** 바뀐다
- `fileSavepath`는 지금처럼 받기만 하고 쓰지 않는다. `fileOriname`도 같은 상태가 됐다(요청 형태 유지)
- 인가 호출을 `try` 블록 안에 넣지 않았다(R8 — 404가 500이 되는 것을 막는다)

### ErrorCode — 신규 0건

| 상황 | 코드 | HTTP |
|---|---|---|
| 상세 — 없음 / 권한 없음 | `APPROVAL_NOT_FOUND` (AP001) | 404 |
| 파일 — 없음 / 권한 없음 / 빈 값 / 경로 이탈 | `APPROVAL_FILE_NOT_FOUND` (AP007) | 404 |

`APPROVAL_UNAUTHORIZED`(AP003, 403)는 쓰기 경로 전용으로 유지했다(D4).
**응답만으로는 "없음"과 "권한 없음"이 구분되지 않으므로, 서버 로그의 `log.warn`이 유일한 구분 수단이다.**

---

## 3. 검증 결과 (Claude Code 담당분)

### 자동 검증

| 항목 | 결과 |
|---|---|
| `compileJava` (P2 직후 — R2 판정) | **통과.** `ApprovalCommandService` 무수정 확인 |
| `compileJava` + `compileTestJava` (최종) | **통과** (`BUILD SUCCESSFUL`, exit 0) |
| `bootRun` | **정상 기동** — `Started Application in 10.769 seconds`, ERROR 0건 |

> `bootRun` 통과에는 컴파일보다 한 단계 더 되는 의미가 있다. Spring Data는 파생 쿼리를 기동 시
> 검증하므로, 신규 `findByFileSavename`의 속성명 오류였다면 `PropertyReferenceException`으로
> 기동이 실패했을 것이다. 로그에 해당 예외 0건.
>
> **그럼에도 인가 자체는 여전히 무증명이다.** 컴파일·기동 어디에도 드러나지 않는다.
> 실제 판정은 §5의 캡처 20항목이 했다.

### 검색 확인 5종 (명세 §10)

| # | 항목 | 결과 |
|---|---|---|
| 1 | `@PreAuthorize` 신규 도입 | **0건.** 기존 2건(`TestController:16`·`Position:11`) 그대로 |
| 2 | 신규 ErrorCode | **0건.** `AP001`~`AP011`, AP012 이상 없음 |
| 3 | `APPROVAL_UNAUTHORIZED` 사용처 | `ApprovalCommandService` 4곳(**쓰기 전용**) + 선언. **읽기 경로 0건** |
| 4 | `ApprovalController`의 `Repository` 문자열 | **0건** (D10 준수) |
| 5 | `git status --short` — 범위 이탈 | **코드 3파일 + 문서 1파일.** `ApprovalCommandService` 없음 |

---

## 4. 명세와 실물의 차이 — 1건

명세 §12는 "이 문서와 실물이 어긋날 때 — 실물이 정답이고, 문서를 고친다"고 정한다. 해당 1건:

**§9-1이 검증 계정 정보의 출처를 2곳이라 했으나, 실재하는 것은 1곳이다.**
`securityB_to_next_handover.md`는 리포에도 `C:\temp\` 어디에도 존재하지 않는다(확인함).
정정은 리포 파일(`docs/security/reports/02-secret-exposure-report.md`) 한 곳에만 수행했다.

또한 §9-1(b)가 요구한 "비밀번호 변경 3계정"은 그 파일 §5에 **이미 Z·B·123 셋 다 기록돼 있었다.**
(b)는 착수 시점에 이미 충족 상태였으므로 해당 문장은 건드리지 않았다(사용자 확인).

**수행한 정정 (§9-1 (a))**: 999001 정리 SQL에 🚫 실행 금지 표시를 붙였다. 작업 B 검증 계정은
이미 삭제됐고 **같은 사번이 작업 E 검증용 ADMIN 계정으로 재생성**돼 있어, 방치하면 다음 세션이
살아있는 검증 계정을 지운다. 업로드 파일도 구분이 불가능하므로 함께 보류로 표시했다.

착수 전 실물 대조에서 **명세 v2의 라인 번호·전제는 그 외 전부 일치**했다. 특히
`getApproval(String)` 호출부는 명세가 적시한 **7곳이 전부이고 그 외 0건**임을 확인했다
(`src/test/**` 포함 0건).

> **부수 판단 1건 (사용자 승인)**: `02-secret-exposure-report.md:386`에 검증 계정의 임시
> 비밀번호 실값이 남아 있으나 **마스킹하지 않았다.** 이미 커밋·푸시된 문장이고 작업 B D1에서
> history rewrite를 배제했으므로 실효가 없으며, 오히려 다음 세션의 로그인을 막는다.
> §9-1의 R10 원칙은 **새로 작성하는 기록**에 적용된다.

---

## 5. 캡처 20항목 판정 — **전 항목 PASS**

기준선 `C:\temp\read-authz\baseline\` (2회 캡처, 해시 동일) 대비 `after` 캡처를
`capture-read-authz.ps1 -Compare`로 대조했다.

### 차단 — 6건 전부 404 전환

| # | 항목 | Before | After |
|---|---|---|---|
| 13 | 상세 · 제3자 X · D1 | 200 / 1425B | **404 / 83B / AP001** |
| 14 | 상세 · 무관 ADMIN R · D3 | 200 / 1036B | **404 / 83B / AP001** ← **P1 실증** |
| 15 | 상세 · 결재자 Z · D2 임시저장 | 200 / 1008B | **404 / 83B / AP001** ← **P2 실증** |
| 16 | 파일 · 제3자 X · D1 | 200 / 55048B (PNG 원본) | **404 / 90B / AP007** |
| **19** | 파일 · 빈 savename | 200 / 1184B (**디렉터리 목록**) | **404 / 90B / AP007** |
| **20** | 파일 · `savename=".."` | 200 / 80B (**베이스 경로 밖**) | **404 / 90B / AP007** |

19·20이 닫힌 것은 [E2] 1단계(`findByFileSavename`)가 파일시스템 접근 **이전에** 차단하기
때문이다. 저장명 열거 → 임의 첨부 획득으로 이어지던 결합 경로가 사라졌다.

### 비회귀 — 14건 전부 유지

| # | 항목 | 판정 |
|---|---|---|
| 01·02·03 | 상세 · 기안자 / 결재자 / 참조자 · D1 | 해시 동일 |
| 04 | 상세 · 기안자 · D2 임시저장 | 해시 동일 |
| **05** | 상세 · **결재 완료한 결재자** · D3 | 해시 동일 ← **D3 실증**(목록에 없어도 열린다) |
| 06 | 파일 · 기안자 | 해시 동일 (55048B PNG) |
| **07** | 파일 · **참조자** | 해시 동일 ← **P6 실증** |
| 10 | 목록 · `receivedAll` | 해시 동일 (400/C001 유지) |
| 17·18 | 없는 번호 · 없는 savename | 해시 동일 (기존 404 유지) |
| **08·09·11·12** | **목록 4종** | **해시 불일치 → 정규화 후 동일. 아래 참조** |

### ⚠ 08·09·11·12 — 해시는 달랐으나 회귀가 아니다

`-Compare`가 이 넷을 `FAIL — 비회귀 깨짐`으로 표시했다. **오판이며, 원인은 검증 설계에 있다.**

**근거 1 — 길이가 완전히 같다.**

| # | before | after |
|---|---|---|
| 08 (`given`) | 4550자 | **4550자** |
| 09 (`tempGiven`) | 1232자 | **1232자** |
| 11 (`received`) | 12367자 | **12367자** |
| 12 (`receivedRef`) | 1619자 | **1619자** |

인가가 목록에 잘못 걸렸다면 항목이 빠져 **길이가 줄어든다.** 보존됐다는 것은 항목 수와
구조가 그대로라는 뜻이다.

**근거 2 — 차이가 키 순서 하나뿐이다.** 네 건의 첫 차이 지점이 모두 동일한 형태였다:

```
before : "offset":0,"paged":true,"unpaged":false,"last":true,…
after  : "offset":0,"unpaged":false,"paged":true,"last":true,…
```

`totalElements`(3 / 1 / 9 / 1) · `totalPages` · `size` · `content` 전부 일치.
**목록 항목이 하나도 빠지지 않았다 — R1(목록 회귀) 미발생.**

**근거 3 — 정규화 후 바이트 동일.**

```powershell
foreach ($n in '08','09','11','12') {
    $b = (Get-Content "$root\baseline\$n.json" -Raw) -replace '"unpaged":(\w+),"paged":(\w+)','"paged":$2,"unpaged":$1'
    $a = (Get-Content "$root\after\$n.json"    -Raw) -replace '"unpaged":(\w+),"paged":(\w+)','"paged":$2,"unpaged":$1'
    "{0} : {1}" -f $n, $(if ($b -ceq $a) { '동일 — PASS' } else { '여전히 다름' })
}
```

```
08 : 동일 — PASS
09 : 동일 — PASS
11 : 동일 — PASS
12 : 동일 — PASS
```

**원인**: `paged`/`unpaged`는 우리 코드의 필드가 아니라 Spring Data `Pageable`의
`isPaged()`/`isUnpaged()` **getter 기반 파생 속성**이다. Jackson은 이런 속성의 직렬화
순서를 보장하지 않으며, 순서는 **JVM 인스턴스마다 달라질 수 있다.**

기준선 2회(`baseline`·`baseline2`)가 일치했던 것은 **같은 프로세스 안에서 연속 캡처**했기
때문이다. 그 사이에 코드 수정과 `bootRun` 재기동이 있었다.

**→ 08·09·11·12 PASS. 20항목 전부 통과.**

---

## 6. 남은 일 (사용자)

### 6-1. S2 · S3 — **완료**

**S2. 쓰기 경로 스모크** — `package-private` 전환의 런타임 영향 없음 확인.
문서 `2026-ims00022` 하나로 4단계 연속 수행. 결재선에 결재자 Z(240501629)를 배치했다.

| 순서 | 요청 | 수행 | 결과 | 통과한 호출부 |
|---|---|---|---|---|
| 1 | `POST /approvals` (임시저장) | **화면** | 200 · `TEMP_SAVED` · `approver` **길이 2** | `:131` |
| 2 | `PUT /approvals/{no}` (재임시저장) | **Postman** | 200 · `TEMP_SAVED` · **길이 2** | `:170` |
| 3 | `POST /approvals` (같은 번호, 기안) | **Postman** | 200 · `PROCESSING` · **길이 2** | `:88` → `:291` |
| 4 | 회수 (`PUT /approvals/{no}/status`) | **화면** | `WITHDRAWN` 전이 확인 | `:193` |

네 단계 모두 `getApproval(String)`이 만든 DTO가 온전히 실렸다
(`apr000` 기안자 A + `apr001` 결재자 Z). **CommandService 5곳 중 4곳 실증.**
남은 `:239`는 결재 처리(승인·반려) 경로이며 같은 메서드를 호출하므로 추가 확인은
불필요하다고 판단했다.

> **수행 방식이 섞인 것은 판정에 영향이 없다.** 판정 대상은 서버 응답의 `approver` 구성과
> 상태 전이이고, 클라이언트 종류와 무관하게 같은 코드 경로를 탄다. 오히려 화면 경유는
> 프론트가 실제로 보내는 요청 형태가 통과함을 함께 보여준다.
>
> ⚠ **`spec.md` §5의 "검증을 화면으로 할 수 없다"는 과대 서술이다.**
> **정지한 것은 승인·반려에 한정되며, 기안(1)·회수(4)는 화면에서 정상 동작**함을 확인했다.
> "화면을 전부 못 쓴다"로 읽으면 후속 작업이 쓸 수 있는 검증 수단을 스스로 버리게 된다.
> → `spec.md` §5에 정정 반영함.

**S3. 로그 구분** — 응답이 404로 통일돼 있어 로그가 유일한 구분 수단이다(§2).
제3자 X(999001) 토큰으로 2건 호출.

| # | 요청 | 응답 | 로그 |
|---|---|---|---|
| S3-a | `GET /approvals/2026-non00003` | 404 / AP001 | `WARN c.i.l.a.service.ApprovalQueryService : 결재 상세 열람 권한 없음 - approvalNo: 2026-non00003, memberId: 999001` |
| S3-b | `GET /approvals/files?…` (D1 첨부) | 404 / AP007 | `WARN c.i.l.a.service.ApprovalQueryService : 결재 첨부 열람 권한 없음 - approvalNo: 2026-non00003, memberId: 999001` |

S3-b의 로그가 `approvalNo`를 담고 있다는 것은 **[E2]가 1단계(행 없음)가 아니라 3단계
(`canRead` 실패)에서 차단했다**는 증거다. `Attachment` → `Approval` → 관계 판정 순서가
모두 돌았다.

**→ 명세 §10의 수동 검증 전 항목 완료. 미수행 0건.**

> **관찰 (범위 밖)**: S2-2에서 기안자 본인(240501544)이 참조자로 등록됐다
> (`referencer[0].memberId = 240501544`). 요청 body의 값이 그대로 반영된 것으로 보이며
> 서버가 막지 않는다. 열람 판정은 기안자 단계에서 이미 통과하므로 기능상 무해하다.
> **코드 대조 없이 관찰만** — `spec.md` §4-3 등재.

### 6-2. 문서 갱신 — **완료**

| 문서 | 변경 |
|---|---|
| `spec.md` §4 표 | 작업 B·E를 **완료**로 |
| `spec.md` §4-4 | 작업 E 완료로 전면 갱신 — **착수 후 실측으로 드러난 2건**(저장명 전수 노출·경로 이탈)과 확정 정책 D1~D10 요약 추가 |
| `spec.md` §4-3 | 등재 **4건 추가** (§7) + 실측/코드대조 구분 주석 갱신 + `showAllMembersPage` 행의 "작업 E 소관" 서술 무효화 |
| `spec.md` §5 | **정정 추가** — 프론트 정지는 승인·반려 한정. 기안·회수는 화면 동작 |
| `AGENTS.md` | 현재 작업·로드맵 갱신, 필수 읽기 순서에 직전 명세·보고서 추가, **작업 E에서 확정된 선례 4건** 신설 |
| `tasks/03-read-authz.md` | **v3 정정 3건** 추가 (§9-1 출처 1곳 · (b) 기충족 · R10 적용 범위) + 헤더 완료 표시 |
| `reports/02-secret-exposure-report.md` | 999001 정리 SQL에 🚫 실행 금지 표시 (§4) |

### 6-3. 커밋 2분할

```powershell
cd C:\env\GitHub\INSIDER\LOG-IN-Refactoring

# ① 코드 — 정확히 3개여야 한다
git add final/src
git diff --cached --stat
git commit -m "..."

# ② 문서
git add -A docs/
git diff --cached --stat
git commit -m "..."
```

> ⚠ `docs/security/tasks/03-read-authz.md`는 아직 untracked다. ②에 함께 담긴다.
> ⚠ `C:\temp\read-authz\`의 기준선에는 **실제 첨부 저장명과 서버 디렉터리 구조**가 들어 있다
> (`19.bin`·`20.bin`). **리포 안으로 옮기지 말 것.**

---

## 7. 범위 밖 · 등재 항목

작업 E 범위 밖이므로 손대지 않았다. `spec.md` §4-3 편입을 제안한다.

| 항목 | 지점 | 비고 |
|---|---|---|
| **`AttachmentDTO`가 서버 절대 경로(`fileSavepath`)를 응답에 싣는다** | `ApprovalQueryService`의 상세 조회 첨부 매핑 | D5(응답 축소 안 함)에 따라 유지. **[E2] 이후 관계자만 보게 되어 등급은 내려갔다** |
| `finalApproverDate`가 임시저장 문서에 찍힌다 | 상세 조회의 최종 승인일 계산 분기 | 원인 미확인. before/after 동일하므로 캡처 판정에는 영향 없음 |
| ADMIN·감사 role의 전체 열람 | — | D2로 도입하지 않았다. 부서장·감사 role은 스키마 영향 |
| `receivedAll` 미구현 | `getApprovalList`의 `switch` | 현재 동작(400/C001)을 그대로 뒀다. **상세가 목록보다 약간 넓은 이유**(D3)가 여기에 있다 |
| `GET /approvals/members` · `/approvals/members/{id}` 인가 | — | 회원 조회. 결재 문서가 아니다 → 후속 |
| `GET /members/{id}` · `/api/rooms/members` · `/commutes` 인가 | — | 도메인이 다르다 → 후속 |

### 잔여 위험

| # | 위험 | 상태 |
|---|---|---|
| **R4** | `fileSavename`에 **DB unique 제약이 없다.** 중복 시 `findByFileSavename`이 `NonUniqueResultException` → catch-all → **500** | D6이 확률 무시 수준으로 판정하고 `Optional`을 택했다. **등재만** |
| **R6** | **D3(관계자는 처리 후에도 계속 읽는다)의 실증이 #05(승인 완료) 한 건뿐이다.** 반려·회수 상태는 캡처 매트릭스에 없다 | `canRead`가 상태 중 `TEMP_SAVED`만 보므로 구현상 깨질 여지는 낮다. 기준선이 잠겨 항목 추가는 하지 않았다. **잔여 위험으로 등재** |
| **R11** | **해시 대조 검증은 애플리케이션 재기동을 건너면 성립하지 않는다.** Jackson의 getter 기반 파생 속성(`paged`/`unpaged` 등) 순서가 JVM 인스턴스마다 달라진다 | §5에서 실제로 발생했다(FAIL 4건 → 전부 오판). **후속 작업에서 같은 방식을 쓸 경우, 기준선을 재기동 이후에 한 번 더 찍거나 비교 전 키 순서를 정규화할 것** |
| — | `getApproval(String)`은 **완전 봉인이 아니다.** `approval.service` 패키지 안에서는 인가 없이 호출 가능하다 | 의도된 상태(§2 [E1]). 향후 이 패키지에 서비스를 추가할 때 주의 |
