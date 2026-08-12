# 작업 E: 읽기 경로 인가 (Task 명세 · **확정본 v3** — ✅ 실행 완료)

> 상위: `docs/security/spec.md` §4-4
> 선행: 작업 A(쓰기 경로 인가, `91de70c`) · 작업 B(비밀 노출 차단, `3e2db66`)
> 기준선: `C:\temp\read-authz\baseline\` — **20항목 캡처 완료 · 결정성 검증 통과**
> **결과: 캡처 20항목 전 항목 PASS · S2·S3 통과 · 코드 3파일 · 신규 ErrorCode 0건**
> → `docs/security/reports/03-read-authz-report.md`

---

## v3 정정 (실행 후 · 2026-08-12)

| # | v2 | 실물 | 조치 |
|---|---|---|---|
| **v3-1** | §9-1이 검증 계정 정보의 출처를 **2곳**이라 했다 | `securityB_to_next_handover.md`는 **리포에 없다.** 세션 인계용으로만 존재했다 | 리포 파일(`docs/security/reports/02-secret-exposure-report.md` §5) **1곳**이 맞다. 정정도 그곳에만 수행했다 |
| **v3-2** | §9-1(b) "비밀번호 변경 3계정을 기록" | 그 파일 `:386`에 **이미 Z·B·123 셋 다 기록돼 있었다** | 착수 시점에 충족 상태. 문장 무변경 |
| **v3-3** | R10 "실값을 남기지 않는다" | `:386`에 임시 비밀번호 실값이 이미 커밋·푸시돼 있다 | **마스킹하지 않는다**(사용자 승인). D1에서 history rewrite를 배제했으므로 실효가 없고, 다음 세션의 로그인을 막는다. **R10은 새로 작성하는 기록에 적용된다** |

> §9-1(a)만 수행했다 — 999001 정리 SQL에 🚫 실행 금지 표시.
> **그 외 v2의 라인 번호·전제는 전부 실물과 일치**했다. `getApproval(String)` 호출부도 명시한 7곳이 전부였다.

---

## v2 정정 (명세 리뷰 세션 · 읽기 전용 대조 결과)

| # | v1 | v2 | 근거 |
|---|---|---|---|
| **v2-1** ★ | [E1] `getApproval(String)`을 **`private`** 으로 | **`package-private`**(접근 제어자 제거) | 호출부가 `ApprovalCommandService`에 **5건**(`:131`·`:170`·`:193`·`:239`·`:291`). 같은 패키지라 접근 제어자만 지우면 **무수정 컴파일**되고 Controller는 막힌다. `private`이면 하드 가드와 정면 충돌해 P2에서 멈춘다 |
| **v2-2** ★ | [E2] 판정 위치는 "구현 재량" | **`ApprovalQueryService`로 고정** (D10) | `ApprovalController` 생성자는 서비스 3개뿐, **리포지토리가 없다**. "재량"으로 두면 구현자가 Controller에 리포지토리를 주입할 여지가 남는다 |
| **v2-3** | §0 미확인 1건 + R6 | **해소·삭제** | `ReferencerRepository.findByApprovalNo(String):19` · `ApproverRepository:20` 둘 다 존재 |
| **v2-4** | `ApprovalFileService:126` / `ApprovalController:56~61` | **`:125`** / **`:54~61`**(시그니처 `:57`, 호출 `:59`) | 라인 드리프트 (R5가 예고) |
| **v2-5** | §9-1 "인계 문서 §2" · 비밀번호 변경 **1계정** | **출처 2곳 병기** · **3계정** | 리포 파일은 `02-secret-exposure-report.md` §5(`:381~386`). `:386`이 Z·B·123 셋을 적고 있다 |
| **v2-6** | — | §2에 **`AttachmentDTO`의 서버 절대 경로 노출** 등재 | `ApprovalQueryService:206` |
| **v2-7** | — | §8에 **D3 실증 부족**(반려·회수 미검증) 등재 | 매트릭스에 #05(승인)만 있다 |

> **미확정 0건.** 착수 가능.

---

## 0. 착수 상태

| 확인 항목 | 상태 | 결과 |
|---|---|---|
| 열람 정책 P1~P8 | ✅ | §7 D1~D10으로 확정 |
| 기준선 캡처 | ✅ | **20항목.** 2회 캡처 전 항목 해시 동일 → **해시 판정 사용 가능** |
| 관계 판정 데이터 | ✅ | `Approval.memberId`(`:19`·`:28`) / `Approver` / `Referencer` — **전부 기존 조회로 가능** |
| `ApprovalQueryService` 주입 리포지토리 | ✅ | `approverRepository`(`:37`) · `referencerRepository`(`:39`) **이미 주입돼 있다** |
| `ApproverRepository.findByApprovalNo` | ✅ | `:20` 존재 |
| `ReferencerRepository.findByApprovalNo` | ✅ | `:19` 존재 |
| `AttachmentRepository` | ✅ | `findByApprovalNo`·`deleteByApprovalNo` 둘뿐. **`fileSavename` 조회 없음 → 신규 1건 필요** |
| `Attachment.fileSavename` | ✅ | `:25` — 파생 쿼리 성립. **DB unique 제약 없음**(R4) |
| **`getApproval(String)` 호출부** | ✅ | **7곳.** Controller `:59` / QueryService `:314`·`:415` / **CommandService `:131`·`:170`·`:193`·`:239`·`:291`** |
| **패키지 관계** | ✅ | Query·Command 서비스 **동일 패키지**(`approval.service`), Controller는 `approval.controller` → **package-private 성립**(D1) |
| **`ApprovalController` 생성자** | ✅ | 서비스 3개뿐. **리포지토리 없음** → D10의 근거 |
| `listToDTO` | ✅ | **`:415`에서 항목마다 `getApproval()` 호출.** 목록 5종이 전부 이 경로를 탄다 |
| `received` 분기 | ✅ | `:314`도 `getApproval()` 직접 호출 |
| `MemberRole` | ✅ | `MEMBER` / `ADMIN` / **`ALL("MEMBER,ADMIN")`** — role 분기를 쓰지 않는 이유(D2) |
| `WebSecurityConfig` | ✅ | **`authorizeHttpRequests` 없음.** 인가는 코드로만 가능 |
| `GlobalExceptionHandler` | ✅ | catch-all `Exception` → 500/C999. `@PreAuthorize` 금지 근거 |
| ErrorCode | ✅ | AP001(404) `:41` / AP003(403) `:43` / AP007(404) `:47` — **신규 0건 성립** |
| 트랜잭션 | ✅ | QueryService `@Transactional(readOnly = true)` / CommandService `@Transactional` — D1 주석 참조 |
| `src/test/**` | ✅ | `getApproval` 호출 **0건** → `compileTestJava` 영향 없음 |
| 검증 계정 4종 | ✅ | **A**=240501544(MEMBER) / **Z**=240501629(ADMIN) / **R**=123(ADMIN) / **X**=999001(ADMIN) |
| 검증 문서 3종 | ✅ | **D1**=`2026-non00003` / **D2**=`2026-ims00021` / **D3**=`2026-non00004` |

---

## 1. 목표

**결재 문서와 첨부파일을 관계자만 읽게 한다.**

작업 A가 쓰기 경로를 닫았으나 읽기는 그대로다. 남의 문서를 수정하지는 못해도
**본문·결재선·참조선·첨부를 전부 읽을 수 있다.** 같은 훑기에서 나온 같은 계열의 결함이다.

기준선 캡처에서 **첨부 경로가 예상보다 심각**한 것이 드러났다(§3-3). 파일 경로를 최우선으로 둔다.

### 성공 기준

| 지표 | Before (실측) | After |
|---|---|---|
| 상세 조회 — 무관한 제3자 | **200, 기안자와 동일한 1425B** | **404 / AP001** |
| 상세 조회 — 무관한 ADMIN | **200** (#14) | **404 / AP001** |
| 상세 조회 — 남의 임시저장 (결재선에 있어도) | **200** (#15) | **404 / AP001** |
| 첨부 다운로드 — 무관한 제3자 | **200, 55048B PNG 원본** (#16) | **404 / AP007** |
| 첨부 다운로드 — 빈 `savename` | **200, 업로드 디렉터리 파일명 목록** (#19) | **404 / AP007** |
| 첨부 다운로드 — `savename=".."` | **200, 베이스 경로 밖** (#20) | **404 / AP007** |
| 정상 경로 — 기안자·결재자·참조자 상세 | — | **불변 (SHA256 동일)** ← 최우선 비회귀 |
| 정상 경로 — 목록 5종 | — | **불변 (SHA256 동일)** |
| 정상 경로 — 결재 완료한 결재자의 상세 (#05) | — | **불변** ← 목록에 없어도 열린다 |
| 정상 경로 — 참조자의 첨부 다운로드 (#07) | — | **불변** |
| **`ApprovalCommandService`** | — | **무수정** ← v2-1의 판정 기준 |
| 신규 ErrorCode | — | **0건** (AP001·AP007 재사용) |
| 목록 조회 로직 | — | **불변** (D1) |

---

## 2. 경계 (확정)

애매하면 이 표가 이긴다.

| 항목 | 작업 | 근거 |
|---|---|---|
| `GET /approvals/{approvalNo}` 인가 | **E** | `spec.md` §4-4 |
| `GET /approvals/files` 인가 | **E** | 〃 |
| 빈 `savename` 디렉터리 노출 · 경로 이탈 | **E** | 위 인가 처방이 **같은 지점에서** 닫는다(§3-3) |
| — 이하 작업 E 밖 — | | |
| `GET /approvals/members` · `/approvals/members/{id}` 인가 | **후속** | 회원 조회. 결재 문서가 아니다 |
| `GET /members/{id}` · `/api/rooms/members` · `/commutes` 인가 | **후속** | `spec.md` §4-4 후반. 도메인이 다르다 |
| **`AttachmentDTO`가 서버 절대 경로(`fileSavepath`)를 응답에 싣는 것** | **등재만** | `ApprovalQueryService:206`. D5(응답 축소 안 함)에 따라 유지. **[E2] 이후 관계자만 보게 되어 등급은 내려간다** (v2-6) |
| `receivedAll` 미구현 | **이월** | 완료 보고서 §4. **400/C001이 현재 동작이고, 그대로 둔다** |
| `finalApproverDate`가 임시저장에 찍히는 것 | **등재만** | §3-4 |
| ADMIN·감사 role의 전체 열람 | **등재만** | D2 — 스키마 영향 |
| 저장형 XSS / 인증 실패 200 / CORS / `TestController` | **D** | 등재 유지 |
| 응답 필드 축소 | **하지 않는다** | D5 |

> **판단 규칙**: 작업 E는 **"이 문서를 읽을 자격이 있는가"** 하나만 묻는다.
> 무엇을 보여줄지(응답 구조)·목록 로직·프론트는 손대지 않는다.

### 범위 밖 (명시 · 🚫 하드 가드)

- 🚫 **`JwtAuthorizationFilter`** — `roleLessList`는 문자열 완전 일치다. 고치면 `/approvals`와
  회원 조회·프로필 수정이 **동시에 무인증으로 열린다.** **읽기만 허용.**
- 🚫 **`WebSecurityConfig`** — 인증 게이트가 위 필터 하나뿐. 경로 정책 추가는 범위 밖
- 🚫 **`@PreAuthorize`** — 거부가 500이 된다 (실측)
- 🚫 **`ErrorCode.java`** — 신규 0건. AP001·AP007 재사용
- 🚫 **`GlobalExceptionHandler.java`**
- 🚫 **`getApprovalList`의 `switch` 분기 로직** — 목록은 이미 관계로 필터링돼 있다. 불변 (D1)
- 🚫 **`ApprovalCommandService`** — 쓰기 경로는 작업 A에서 닫혔다.
  **`getApproval` 호출 5건은 손대지 않는다.** package-private 전환이 옳게 됐다면 **무수정으로 컴파일된다**(D1)
- 🚫 **`ApprovalFileService`** — [E2]는 `loadAsResource` **호출 지점**을 바꾼다.
  `store`·`deleteByApprovalNo`·`savePath`·`loadAsResource` 본문은 열지 않는다
- 🚫 **`ApprovalController`에 리포지토리 주입** — 계층 경계를 깬다 (D10)
- **프론트엔드 변경** — 리포가 다르다
- `src/test/**`, DB 스키마, 새 기능

---

## 3. 실측 근거

전부 `C:\temp\read-authz\baseline\` 20항목 캡처(2회, 해시 동일). 콘솔 로그는 같은 폴더.

### 3-1. 상세 조회에 인가가 **0**이다

`ApprovalController:54~61`의 `selectApprovalByNo`는 `getCurrentMemberId()`를 **호출조차 하지 않는다**
(시그니처 `:57`, 호출 `:59`). `ApprovalQueryService.getApproval(String)`(`:151`)도 `memberId`를 받지 않는다.

| # | 호출자 | 문서 | 결과 |
|---|---|---|---|
| 01 | 기안자 A | D1 | 200 · **1425B** |
| 02 | 결재자 Z | D1 | 200 · **1425B** |
| 03 | 참조자 R | D1 | 200 · **1425B** |
| **13** | **제3자 X** | D1 | 200 · **1425B** |

**바이트 단위로 같다.** 관계 유무가 응답에 아무 영향이 없다.

| # | 호출자 | 문서 | 결과 | 실증 |
|---|---|---|---|---|
| 05 / **14** | 결재자 Z / **무관 ADMIN R** | D3 | 둘 다 200 · **1036B** | **P1** — role이 아니라 관계가 기준이어야 한다 |
| 04 / **15** | 기안자 A / **결재자 Z** | D2 (임시저장) | 둘 다 200 · **1008B** | **P2** — 임시저장에도 결재선이 저장된다 |

D2의 결재선에는 Z가 실제로 올라가 있다(`apr001`). 그럼에도 상신 전 초안이므로 차단해야 한다.

### 3-2. 첨부 다운로드에 인가가 **0**이다

`ApprovalController`의 `downloadFile`(`:165`)은 `getCurrentMemberId()`를 호출하지 않고,
`ApprovalFileService.loadAsResource(savename, oriname)`에 **요청 파라미터를 그대로 넘긴다.**

| # | 호출자 | 결과 |
|---|---|---|
| 06 | 기안자 A | 200 · 55048B · 매직 `89504e47` |
| 07 | 참조자 R | 200 · **55048B** |
| **16** | **제3자 X** | 200 · **55048B** |

제3자가 **원본 PNG를 그대로 받는다.**

### 3-3. 저장명 열거 + 경로 이탈 ★ — 결함이 한 등급 위다

| # | 요청 | 결과 |
|---|---|---|
| **19** | `fileSavename=` (빈 문자열) | **200 · 1184B** — UUID 32자 + 확장자(`png`/`pdf`/`txt`/`jpg`)가 줄바꿈으로 나열. **업로드 디렉터리 파일명 목록** |
| **20** | `fileSavename=..` | **200 · 80B** — 텍스트. 베이스 경로(`C:/login/upload`) **밖**을 서빙 |
| 18 | 없는 savename | 404 · AP007 (정상) |

원인은 `ApprovalFileService:125`:

```java
Path filePath = Paths.get(savePath()).resolve(savename).normalize();
```

`normalize()`는 있으나 **베이스 경로 포함 검사가 없다.** 빈 문자열은 디렉터리 자신으로,
`..`는 상위로 해석되고, `UrlResource.exists()` 검사(`:134`)가 디렉터리에 대해 `true`라 통과한다.

**결합 시나리오** — 유효한 토큰 하나로:

```
① fileSavename=      → 전체 저장명 목록 확보
② 목록의 임의 savename → 200 (인가 0)
```

**전 사원의 모든 결재 첨부를 열람할 수 있다.** 결재 첨부에는 인사·계약류가 들어간다.
저장명이 UUID라 추측 불가한 것이 사실상 유일한 보호였는데, ①이 그것을 무력화한다.

> ⚠ `19.bin`·`20.bin`은 **실제 저장명과 서버 디렉터리 구조**다. 리포·보고서·명세 어디에도
> 내용을 옮기지 않는다. `C:\temp\read-authz\`에 둔다.

### 3-4. 목록은 이미 관계로 필터링돼 있다 — 그러나 상세보다 좁다

| flag | 필터 | 비고 |
|---|---|---|
| `given` | 기안자 = 나, 임시저장 제외 | |
| `tempGiven` | 기안자 = 나, 임시저장만 | |
| `receivedAll` | **미구현** | `:291~294`가 `break`만 있어 `approvalPage`가 null → `:353~354`에서 **400/C001** (#10 실측) |
| `received` | **지금 내 차례**인 결재만 | 이미 처리한 건은 **빠진다** |
| `receivedRef` | 참조자 = 나 | |

**`receivedAll`이 비어 있어서, 결재를 마친 문서는 어떤 목록에도 뜨지 않는다.**
상세를 목록과 정확히 일치시키면 결재자가 자기가 승인한 문서를 못 보게 된다 → **D3의 근거.**

> **등재만**: D2(TEMP_SAVED)의 `finalApproverDate`에 값이 찍힌다(`getApproval:224~226`).
> D1(PROCESSING)은 `""`인데 결재선 모양은 같다. **원인 미확인. 작업 E 범위 밖.**
> 캡처 판정에는 영향 없다(before/after 동일).

---

## 4. 처방

### 공통 원칙

- **판정은 서비스 계층에서.** `WebSecurityConfig`에 인가 규칙이 없고 `@PreAuthorize`는 금지다
- **role을 보지 않는다.** 관계만 본다 (D2)
- **차단은 404.** 존재를 알려주지 않는다 (D4)
- **신규 ErrorCode 0건**

### [E0] 관계 판정 헬퍼 — `ApprovalQueryService`

```
private boolean canRead(Approval approval, int memberId)

  1) approval.getMemberId() == memberId                            → true  (기안자)
  2) approval.getApprovalStatus() == TEMP_SAVED                    → false (기안자 외 전원)
  3) approverRepository.findByApprovalNo(...)   에 memberId 있음    → true
  4) referencerRepository.findByApprovalNo(...) 에 memberId 있음    → true
  5) 그 외                                                          → false
```

순서가 중요하다. **(2)가 (3)·(4)보다 앞**이어야 한다 — 임시저장에도 결재선이 저장되기 때문이다(§3-1).

- 기안자 판정은 `Approval.memberId` 비교로 **명시적으로** 한다.
  `apr000`이 기안자라는 현재 구현에 의존하지 않는다
- 신규 리포지토리 메서드 **불필요** (`ApproverRepository:20` · `ReferencerRepository:19`)

### [E1] 상세 조회 인가

**진입점을 분리한다.** 기존 `getApproval(String)`은 **package-private** 내부용으로 남기고,
인가가 붙은 public 오버로드를 추가한다.

```java
// 새 진입점 — Controller 전용
public ApprovalDTO getApproval(String approvalNo, int memberId) {
    Approval approval = approvalRepository.findById(approvalNo)
            .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

    if (!canRead(approval, memberId)) {
        log.warn("결재 상세 열람 권한 없음 - approvalNo: {}, memberId: {}", approvalNo, memberId);
        throw new BusinessException(ErrorCode.APPROVAL_NOT_FOUND);
    }
    return getApproval(approvalNo);   // 기존 로직 그대로 위임
}

// 기존 — public 에서 접근 제어자 제거(package-private)
ApprovalDTO getApproval(String approvalNo) { ... }
```

- `ApprovalController:59`가 새 오버로드를 호출하도록 변경
- **`listToDTO:415`와 `received:314`는 그대로 둔다** — 목록은 이미 관계로 필터링돼 있고,
  여기에 인가를 얹으면 목록 5종이 항목마다 판정을 돌게 된다 (D1·R1)
- **`ApprovalCommandService` 5건도 그대로 둔다.** 같은 패키지라 무수정으로 컴파일된다

> ⚠ **`private`이 아니라 `package-private`이다.** `private`으로 낮추면 `ApprovalCommandService`
> `:131`·`:170`·`:193`·`:239`·`:291`이 깨지고, 그 파일은 하드 가드다 (v2-1).
>
> **완전 봉인이 아니다.** `approval.service` 패키지 안에서는 여전히 호출 가능하다.
> 그것이 의도다 — 쓰기 경로는 작업 A에서 이미 인가를 통과한 뒤 DTO를 만들 뿐이다.

**로그**: 차단 시 `log.warn`으로 "없음"과 "권한 없음"을 구분 기록한다. 응답은 동일하다.

### [E2] 첨부 다운로드 인가 ★ 최우선

**(1) `AttachmentRepository`에 조회 1건 추가**

```java
Optional<Attachment> findByFileSavename(String fileSavename);
```

**(2) `ApprovalQueryService`에 다운로드 판정 메서드 추가** (D10 — Controller가 아니다)

```
public Attachment getReadableAttachment(String fileSavename, int memberId)

  1. attachmentRepository.findByFileSavename(fileSavename)
       없으면 → BusinessException(APPROVAL_FILE_NOT_FOUND)      // #18·#19·#20 이 여기서 닫힌다
  2. approvalRepository.findById(attachment.getApprovalNo())
       없으면 → BusinessException(APPROVAL_FILE_NOT_FOUND)
  3. canRead(approval, memberId) 아니면
       → log.warn(…) 후 BusinessException(APPROVAL_FILE_NOT_FOUND)   // #16
  4. return attachment;
```

**(3) `ApprovalController.downloadFile` 변경**

```
Attachment attachment = approvalQueryService.getReadableAttachment(fileSavename, getCurrentMemberId());

ApprovalFileService.FileDownload fileDownload = approvalFileService.loadAsResource(
        attachment.getFileSavename(),      // ← DB값
        attachment.getFileOriname());      // ← DB값 (D8)
```

**1단계가 §3-3의 두 결함을 구조적으로 닫는다.** 빈 문자열도 `..`도 `Attachment` 행이 없으므로
파일시스템에 닿기 전에 404다. 그리고 `resolve()`에 들어가는 값이
**요청 파라미터가 아니라 DB에 저장된 UUID**가 된다 (D7).

- `fileSavepath` 파라미터는 지금처럼 **받기만 하고 쓰지 않는다** (요청 형태 유지)
- `ApprovalFileService`는 **열지 않는다.** 호출 인자만 바뀐다
- 반환 타입을 `Attachment` 엔티티로 둘지 DTO로 둘지는 구현 재량. **다만 Controller에
  리포지토리를 주입하지 않는다**는 것은 재량이 아니다 (D10)

---

## 5. Scope — 수정 허용 파일

| # | 파일 | 변경 |
|---|---|---|
| 1 | `approval/controller/ApprovalController.java` | 상세 호출 시그니처(`:59`) · 다운로드 인가(`:165`) |
| 2 | `approval/service/ApprovalQueryService.java` | `canRead` + `getApproval` 오버로드 + 가시성 + `getReadableAttachment` |
| 3 | `approval/repository/AttachmentRepository.java` | `findByFileSavename` 1줄 |

**코드 3파일.** 이보다 넓어지면 중단하고 보고한다.
특히 **`ApprovalCommandService`가 diff에 나타나면 [E1]을 `private`으로 잘못 구현한 것이다.**

---

## 6. ErrorCode — 재사용만, 신규 0건

| 상황 | 코드 | HTTP |
|---|---|---|
| 상세 — 없음 / 권한 없음 | `APPROVAL_NOT_FOUND` (AP001) | 404 |
| 파일 — 없음 / 권한 없음 / 빈 값 / 경로 이탈 | `APPROVAL_FILE_NOT_FOUND` (AP007) | 404 |

`APPROVAL_UNAUTHORIZED`(AP003, 403)는 **쓰기 경로 전용으로 유지**한다. 읽기에서는 쓰지 않는다(D4).

---

## 7. 결정 사항

### D1. 인가 판정 위치 — **새 public 오버로드 + 기존 메서드 package-private**

`listToDTO:415`가 목록 항목마다 `getApproval()`을 부른다. 기존 메서드에 인가를 넣으면
**목록 5종이 전부 판정을 N번 돈다.** 성능도 문제지만, 더 큰 위험은 목록에 판정을 통과하지
못하는 항목이 하나라도 섞이면 **목록 전체가 404가 되는 것**이다(R1).

목록은 이미 관계로 필터링돼 있으므로 인가가 필요 없다. **진입점에서만 판정한다.**

가시성은 **`package-private`** 이다(v2-1). Controller(`approval.controller`)는 막히고,
`ApprovalCommandService`(`approval.service`, 호출 5건)는 무수정으로 통과한다.

> **트랜잭션 부수 효과 — 무해.** 프록시 기반 `@Transactional`은 non-public 메서드에 속성을
> 부여하지 않는다. 그러나 CommandService의 5건은 이미 쓰기 트랜잭션 **안에서** 도는 참여
> 호출이고, 참여 트랜잭션에서 `readOnly`는 어차피 무시된다. 동작은 변하지 않는다.
> 새 진입점(`public`)은 정상적으로 `readOnly = true`를 받는다.

### D2. role 예외 — **없음** (P1 안 1)

기안자·결재자·참조자 셋만. ADMIN도 관계 없으면 차단한다.

- 판정 데이터가 전부 이미 있다 — **신규 조회 0건**
- ADMIN 예외를 넣으면 취약점이 role 하나 뒤로 물러날 뿐이다. 작업 B에서 `resetPassword`를
  ADMIN으로 닫았으므로, ADMIN 하나가 계정 초기화 + 전사 문서 열람을 동시에 갖는다
- `MemberRole.ALL("MEMBER,ADMIN")` 때문에 ADMIN 판정에 콤마 split이 얹힌다
- 업계 관행도 시스템 관리자 ≠ 문서 열람자다
- 부서장·감사 role은 스키마 영향 → `spec.md` §4-3 **등재만**

### D3. 상태 종속 — **관계 존재 기준, 단 `TEMP_SAVED`는 기안자 전용** (P2·P3)

승인·반려·회수 후에도 관계자는 계속 읽는다(감사·이력). 상세는 목록보다 **약간 넓다** —
`receivedAll` 미구현 때문에 결재를 마친 문서가 목록에 없기 때문이다(§3-4).

### D4. 차단 응답 — **404** (P5)

- `approvalNo`가 완전히 추측 가능하다. 403은 "존재한다"를 확인해준다
- **GET은 부작용이 없어 열거 비용이 사실상 0**인 반면, 쓰기는 부작용 때문에 열거 수단이 되기 어렵다.
  **읽기 404 / 쓰기 403의 불일치는 근거 있는 구분**이지 실수가 아니다
- AP001·AP007 재사용 → 신규 0건
- 프론트는 에러를 삼키므로 회귀 위험 0
- 대신 **서버 로그에서 "없음"과 "권한 없음"을 구분**한다

### D5. 응답 축소 — **하지 않는다** (P8)

전부 아니면 전무. 부분 권한 개념을 도입하면 응답 구조 변경 절차가 또 붙는다.

### D6. `findByFileSavename` 반환 타입 — **`Optional`**

저장명은 UUID 32자라 중복이 사실상 없다. 다만 **DB unique 제약이 없어** 중복 시
`NonUniqueResultException` → catch-all → 500이다(R4). 확률이 무시할 수준이라 `Optional`을 택한다.

### D7. 경로 이탈 — **명시 조항을 두지 않는다**

[E2] 1단계가 구조적으로 닫는다. `resolve()`에 들어가는 값이 요청 파라미터가 아니라
**DB에 저장된 UUID**가 되므로, 이탈 자체가 성립하지 않는다.

베이스 경로 포함 검사를 따로 넣는 것은 "경로가 여전히 사용자 통제 하에 있다"는 잘못된 인상을 준다.
**대신 #19·#20을 검증 항목으로 고정**해서 회귀를 잡는다.

> ⚠ 만약 구현이 [E2]와 달리 **요청의 `savename`을 그대로 `loadAsResource`에 넘기면**
> 경로 이탈이 되살아난다. §12 체크 항목으로 못박는다.

### D8. `oriname` 출처 — **DB값**

`Content-Disposition`의 파일명을 요청 파라미터가 아니라 `Attachment.fileOriname`에서 가져온다.
요청 파라미터를 그대로 헤더에 넣는 구조를 남길 이유가 없다.

> 검증 캡처는 실제 `oriname`을 보내므로 **응답이 동일**하다. 해시 판정에 영향 없다.

### D9. 문서 배치 — `docs/security/tasks/03-read-authz.md`

보고서는 `docs/security/reports/03-read-authz-report.md`.

### D10. [E2] 판정 위치 — **`ApprovalQueryService`. 재량 아님** (v2-2)

`ApprovalController` 생성자는 서비스 3개만 받는다. **리포지토리가 없다.**
1·2단계를 Controller에 두려면 리포지토리 2개를 새로 주입해야 하는데, 계층 경계를 깨고
`canRead`의 노출 범위도 넓어진다.

**Controller에 리포지토리를 주입하지 않는다.** 하드 가드로 승격한다.

---

## 8. 위험 목록

| # | 위험 | 대응 |
|---|---|---|
| **R1** | `getApproval`에 인가를 넣으면 **목록 5종이 통째로 깨진다.** `receivedRef`가 임시저장을 거르지 않으면 참조자 목록이 404가 된다 | D1 — 진입점 분리. 검증 #08~#12 해시 동일로 확인 |
| **R2** | [E1]을 **`private`** 으로 구현하면 `ApprovalCommandService` 5건이 깨진다. 그 파일은 하드 가드라 고칠 수 없다 | **`package-private`**(D1). diff에 `ApprovalCommandService`가 나타나면 **잘못 구현한 것** |
| **R3** | `canRead`가 조회 2건을 추가로 돈다 | 상세 진입점 1회뿐. 목록은 타지 않는다(D1) |
| **R4** | `fileSavename` **DB unique 제약 없음** → 중복 시 500 | D6. 확률 무시 수준. **등재만** |
| **R5** | 라인 번호가 앞선 작업으로 밀린다 (v2-4가 실제 사례) | **인용 전 실물 재확인** |
| **R6** | **D3의 실증이 #05(승인 완료) 한 건뿐이다.** 반려·회수 상태는 매트릭스에 없다 | `canRead`가 `TEMP_SAVED`만 보므로 구현상 깨질 여지는 낮다. **기준선이 잠겨 항목 추가는 하지 않는다. 잔여 위험으로 등재** (v2-7) |
| **R7** | 인증 실패가 **HTTP 200 + `{"status":401}`** | 캡처 스크립트가 자동 검출. 토큰 수명 24h |
| **R8** | 인가 검증을 `try` 블록 안에 넣으면 404가 500이 된다 (작업 B R1 선례) | §12 체크 |
| **R9** | 환경변수 3종(`JWT_KEY`·`DB_USERNAME`·`DB_PASSWORD`) 없으면 `bootRun` 기동 실패 | 착수 전 확인 |
| **R10** | `19.bin`·`20.bin`에 실제 저장명·서버 구조가 들어 있다 | 리포 밖 유지. 문서에 내용 미기재 |

---

## 9. 문서 갱신

### 9-1. 검증 계정 정리 — ⚠ **먼저 고칠 것** (v2-5)

**출처 2곳.** 리포 파일이 주 출처다.

| 문서 | 위치 |
|---|---|
| `docs/security/reports/02-secret-exposure-report.md` | **§5 `:381~386`** ← 리포 파일 |
| `securityB_to_next_handover.md` | §2 `:76~83` ← 세션 인계용 |

**(a) 999001 삭제 SQL을 실행 금지로 표시**

```sql
DELETE FROM transferred_history WHERE member_id = 999001;
DELETE FROM member_info         WHERE member_id = 999001;
```

작업 B 검증 계정은 **이미 삭제됐고**, 같은 사번이 **작업 E 검증용 ADMIN 계정으로 재생성**됐다.
방치하면 다음 세션이 검증 계정을 지운다.
업로드 파일 `C:/login/upload/999001_*.png`는 작업 B 잔여물과 구분되지 않으므로 함께 보류한다.

**(b) 비밀번호 변경 계정은 1건이 아니라 3건이다**

`02-secret-exposure-report.md:386`이 **Z(240501629)·B(240501544)·123** 셋을 적고 있다.
한 건만 적으면 다음 세션이 Z와 123 로그인에서 막힌다.
**실값은 옮기지 않고 "변경됨"으로만** 기록한다(R10).

### 9-2. `spec.md`

- §4-4 → 작업 E 진행 중으로 갱신
- §4-3 등재 추가:
  - **ADMIN·감사 role 전체 열람**(D2)
  - **`finalApproverDate` 임시저장 이상**(§3-4)
  - **`AttachmentDTO`의 서버 절대 경로 노출**(§2 · v2-6)

### 9-3. `AGENTS.md`

- "현재 진행 작업" → 작업 E
- 로드맵 E 상태 갱신

### 9-4. 보고서

`docs/security/reports/03-read-authz-report.md`

---

## 10. 검증

### 자동 검증

```powershell
cd final
.\gradlew.bat compileJava
.\gradlew.bat compileTestJava
.\gradlew.bat bootRun
```

> ⚠ **`compileJava` 통과는 아무것도 증명하지 않는다.** 인가는 컴파일에 드러나지 않는다.
> 단계 1.5의 JPQL 무성 실패가 선례다.
> **단, [E1]의 가시성 실수는 컴파일이 잡는다** — `private`이면 `ApprovalCommandService`에서 깨진다.

### 검색 확인 (PowerShell 5.1)

```powershell
cd final\src\main\java\com\insider\login

# 1. @PreAuthorize 신규 도입 0건 (기존 2건에서 늘지 않았는지)
Get-ChildItem -Recurse -Filter *.java | Select-String "@PreAuthorize" | Measure-Object

# 2. 신규 ErrorCode 0건 — AP012 이상이 없어야 한다
Select-String -Path common\error\ErrorCode.java -Pattern "AP0"

# 3. 403/AP003 이 읽기 경로에 쓰이지 않았는지 (쓰기 전용 유지)
Get-ChildItem -Recurse -Filter *.java | Select-String "APPROVAL_UNAUTHORIZED"

# 4. Controller 에 리포지토리가 주입되지 않았는지 (D10)
Select-String -Path approval\controller\ApprovalController.java -Pattern "Repository"

# 5. 범위 이탈 — 코드 3파일. ApprovalCommandService 가 있으면 R2
cd ..\..\..\..\..\..
git status --short
```

### 수동 검증 — 캡처 매트릭스 20항목 (사용자 담당)

> **전부 API 직접 호출.** 결재 처리 기능이 프론트에서 정지 상태다.
> 계정: **A**=240501544 / **Z**=240501629 / **R**=123 / **X**=999001
> ⚠ **A·Z·123은 비밀번호가 변경돼 있다**(§9-1). 로그인 실패 시 이것부터 의심할 것.
> **판정은 응답 코드가 아니라 본문·해시로 한다** — 인증 실패가 200으로 나간다.

```powershell
cd C:\temp\read-authz
.\capture-read-authz.ps1 -Phase after *>&1 | Tee-Object -FilePath .\after-console.txt
.\capture-read-authz.ps1 -Compare
```

**S0. 정상 경로 비회귀 ★★ — 가장 중요. 차단보다 먼저 본다**

| # | 항목 | 기대 |
|---|---|---|
| 01·02·03 | 상세 — 기안자 · 결재자 · 참조자 (D1) | **SHA256 동일** |
| 04 | 상세 — 기안자의 임시저장 (D2) | **SHA256 동일** |
| **05** | 상세 — **결재 완료한 결재자** (D3) | **SHA256 동일** ← D3 실증. 목록에 없어도 열린다 |
| 06 | 파일 — 기안자 | **SHA256 동일** (55048B PNG) |
| **07** | 파일 — **참조자** | **SHA256 동일** ← P6 실증 |
| 08~12 | 목록 5종 | **SHA256 동일** ← **R1이 여기서 잡힌다** |
| 17·18 | 없는 번호 · 없는 savename | **SHA256 동일** (이미 404) |

**S1. 차단**

| # | 항목 | 기대 |
|---|---|---|
| 13 | 상세 — 제3자 | **404 / AP001** |
| 14 | 상세 — 무관 ADMIN | **404 / AP001** ← D2 실증 |
| 15 | 상세 — 남의 임시저장 (결재선에 있음) | **404 / AP001** ← D3 실증 |
| 16 | 파일 — 제3자 | **404 / AP007** |
| **19** | 파일 — 빈 savename | **404 / AP007** ← 디렉터리 노출 차단 |
| **20** | 파일 — `savename=".."` | **404 / AP007** ← 경로 이탈 차단 |

> **판정: 01~12·17·18 해시 전부 동일 그리고 13~16·19·20 전부 404.**
> 조건절이 아니라 **숫자로 판정한다.** `-Compare`가 자동으로 낸다.
> 해시가 깨지면 `NN.shape.txt`를 `Compare-Object`로 국소화한다.

**S2. 쓰기 경로 스모크** — [E1]의 package-private 전환이 CommandService 5건에 영향을 주지 않았는지.
기안 1건 + 재임시저장 + 회수 → 각 200이고 응답에 결재선이 실리는지 확인.

**S3. 로그 확인** — 차단 시 `log.warn`에 "권한 없음"이 남는지. 응답만으로는 구분되지 않는다.

---

## 11. 실행 순서

```
P0. 기준선 캡처 (20항목 × 2회)              ✅ 완료 · 해시 판정 가능 확인
      ↓  ── 여기까지가 되돌릴 수 없는 단계 ──
P1. 검증 계정 정리 문서 정정 (§9-1)          ← 먼저. 방치하면 검증 계정이 사라진다
      ↓
P2. [E0] canRead 헬퍼 + [E1] 상세 인가
      · package-private 전환 후 compileJava 로 CommandService 무영향 확인 (R2)
      · 목록이 안 깨지는지 여기서 확인 (R1)
      ↓
P3. [E2] 첨부 인가 + findByFileSavename + getReadableAttachment
      · #19·#20 이 함께 닫힌다
      ↓
P4. compileJava + compileTestJava + bootRun + 검색 확인 5종
      ↓
P5. after 캡처 + -Compare + S2·S3            ← 사용자
      ↓
P6. 보고서 + spec.md·AGENTS.md 갱신 + 커밋·푸시
```

> **P2 → P3 순서**: 상세 인가가 `canRead`를 만들고, 첨부 인가가 그것을 재사용한다.
> P2에서 목록 회귀(R1)와 가시성 사고(R2)를 먼저 털어내면 P3 이후 원인을 구분할 필요가 없다.

**커밋 분리**: **코드 / 문서** 2커밋. 커밋·푸시는 **사용자가 한다.**

---

## 12. 착수 전 체크 (Claude Code)

1. §7 D1~D10은 **확정됐다.** 더 나은 안이 떠올라도 바꾸지 말고 **보고**해라.
2. ⚠ **`getApproval(String)`을 `private`이 아니라 `package-private`(접근 제어자 제거)으로 했는가?**
   (R2 — `private`이면 `ApprovalCommandService` 5건이 깨진다)
3. ⚠ **`ApprovalController`에 리포지토리를 주입하지 않았는가?** (D10)
4. **인가 검증을 `try` 블록 안에 넣지 않았는가?** (R8 — 404가 500이 된다)
5. **`listToDTO:415`·`received:314`의 `getApproval` 호출을 건드리지 않았는가?** (D1·R1)
6. **`@PreAuthorize`를 새로 도입하지 않았는가?**
7. **role(`ADMIN`/`MEMBER`) 분기를 넣지 않았는가?** (D2 — 관계만 본다)
8. **`canRead`에서 `TEMP_SAVED` 검사가 결재선·참조선 검사보다 앞에 있는가?** ([E0] 2단계)
9. ⚠ **`loadAsResource`에 요청 파라미터를 그대로 넘기지 않았는가?**
   **DB에서 읽은 `fileSavename`·`fileOriname`을 넘겨야 한다** (D7·D8 — 아니면 경로 이탈이 되살아난다)
10. **403/AP003을 읽기 경로에 쓰지 않았는가?** (D4 — 404다)
11. 🚫 `JwtAuthorizationFilter` / `WebSecurityConfig` / `ErrorCode.java` /
    `GlobalExceptionHandler` / `ApprovalFileService` / **`ApprovalCommandService`** 를 **열지 않았는가?**
12. diff의 코드 파일이 **3개인가?** (§5) **`ApprovalCommandService`가 있으면 2번을 틀린 것이다.**
13. ⚠ **검증 시 에러 본문을 성공으로 오판하지 마라.** 만료 토큰은 **HTTP 200 + `{"status":401}`**다.
14. **기준선은 `C:\temp\read-authz\baseline\`에 20항목 있다.** 덮어쓰지 마라.

### 중단하고 보고할 상황

- 이 문서에 없는 요구사항이 필요하다고 판단될 때
- `getApproval(String)`의 **7곳 외** 호출부를 발견했을 때
- 목록 5종 중 하나라도 깨질 때 (R1)
- `ApprovalCommandService`를 고쳐야만 컴파일이 통과할 때 (R2 — 가시성을 다시 보라)
- 기존 동작을 바꿔야만 컴파일이 통과할 때
- 이 문서와 실물이 어긋날 때 — **실물이 정답이고, 문서를 고친다**

추측으로 메우지 마라.

---

## 13. 작업 원칙 리마인더

- **Surgical**: 이 문서가 요구하는 것만. "온 김에" 금지. 눈에 보여도 범위 밖이면 **기록만**
- **파일이 진실의 원천**: 대화에만 있는 결정은 유실 전제
- **가정 금지**: 참조 문서도, **명세도** 실물과 다를 수 있다. v2 정정 7건이 그 사례다.
  **특히 라인 번호는 앞선 작업이 밀어 놓는다**
- **명세는 실행 전에 리뷰한다**: 읽기 전용 세션과 실행 세션을 분리한다.
  이번 리뷰가 **`getApproval` 호출부 5건**을 잡았다 — 없었으면 P2에서 멈췄을 것이다
- **무성 실패 주의**: `compileJava`·`bootRun` 통과는 아무것도 증명하지 않는다.
  **인가 변경은 컴파일에 전혀 드러나지 않는다**
- **조건절이 아니라 숫자로 판정한다**: 20항목 해시 대조가 그것이다
- **양방향 검증**: 차단만 보고 정상 경로를 놓치면 기능이 죽는다. **S0을 S1보다 먼저 한다**
- **자격증명·PII 취급**: 보고서는 origin에 푸시된다. 토큰·저장명·서버 경로를 남기지 않는다
- **작업 완료 = 커밋 + 푸시.**
