# 작업 A: 쓰기 경로 권한 경계 정리 (Task 명세 · 확정본)

> 작성: 2026-07-31 / **v3 — S0-h 사전 확인 실측 반영** (v2: 명세 리뷰 반영)
> 선행: 전자결재 리팩토링 7단계 완료·커밋·푸시 (`e35e0b1` 코드 / `b314778` 잔해 / `94d7a18` 문서, origin/main 동기화)
> 근거: `spec.md` §3(실측)·§4(작업 분할), `completed/approval-domain.md` §4 🔴,
> `reports/07-controller-report.md` §6-2, 2026-07-31 Postman 실측
> 실행 도구: **Claude Code** — 본 확정본을 plan mode로 검토 후 실행

---

## v3 정정 (S0-h 사전 확인 실측 — 2026-07-31)

| # | 절 | 정정 |
|---|---|---|
| **v3-1** | §10 S0-h · §7 D5 | **삭제 버튼 노출 화면이 확정됐다.** `임시저장함(tempGiven)`에만 있고 **`내 결재함(given)`·`결재 대기함(received)`·`수신 참조함(receivedRef)`에는 없다.** → **S0-h 사전 조건 충족.** 소유자 검증이 정상 UI 흐름을 막지 않는다 |
| **v3-2** | §7 D5 | v2 근거 2("프론트 미확인")가 **해소됐다.** 그러나 **결정은 유지**한다 — 근거를 교체한다 (아래 ⚠) |
| **v3-3** | §13 | **프론트 관찰의 증거 한계**를 작업 원칙에 추가 |
| **v3-4** | §8 R10 | 오염 데이터 구체화 — `_apr000`이 덮인 문서가 최소 2건 |

> ⚠ **프론트 관찰을 정책 근거로 쓰지 않는다.**
> 이 리포는 리팩토링 커밋이 하나도 없는 **2024-05-31 스냅샷**이고, 죽은 라우트·오타 URL·에러 삼킴이
> 이미 확인됐다. **"프론트에 없으니 필요 없는 기능"으로 읽으면 안 된다.**
> 실제로 `내 결재함`에 삭제가 없어 **회수한 문서를 지울 UI 경로가 없다** — 이것이 정책인지 프론트
> 누락인지는 화면만 봐서 알 수 없다.
> → 프론트 관찰은 **"지금 되던 게 안 되지는 않는다"(회귀 부재)** 의 근거로만 쓴다.

---

## v2 정정 (착수 전 명세 리뷰에서 발견 — 실물 대조 결과)

리뷰 판정은 **치명 0건**이었다. 아래는 사실 오류와 검증 구멍의 정정분이다.

| # | 절 | 정정 |
|---|---|---|
| **v2-1** | §4 [A2] · §8 R3 · §11 P2 | **`deleteById`는 프레임워크 동작이 아니다.** `ApprovalRepository:45~49`가 **벌크 JPQL DELETE로 재정의**하고 있어, 없는 번호 삭제의 현재 응답은 `200 / true`로 **코드상 확정**된다. "실측하라"는 v1 서술은 부정확했다 → **D11 신설** |
| **v2-2** | §7 D5 | **근거 2가 사실과 달랐다.** `APPROVAL_INVALID_STATUS_TRANSITION`(**AP002**, 400, "현재 상태에서는 해당 처리를 할 수 없습니다.")이 이미 존재하므로 상태 가드에 신규 상수는 필요 없다. **결정은 유지**하되 근거를 교체하고, 소유자 가드와의 비대칭 근거를 명시 |
| **v2-3** | §10 S4 | **검증 매트릭스를 통과하면서 결함이 남는 배치가 있었다.** 구현자가 검증을 `if (initialStatus == PROCESSING)` **안쪽**에 넣으면 `POST + "임시저장"` 경로가 열린 채 S0~S8이 전부 통과한다 → **S4-b 추가** |
| **v2-4** | §10 S1·S6·S8 | S1에 사전 조건(`PENDING`) 누락 / S6이 PUT 경로만 봄 / S8의 비교 기준 시점 없음 → 각각 보완, **P2를 기준선 캡처로 교체** |
| **v2-5** | §7 D7 · D10 | D7은 "실패 경로만 추가"가 아니라 **성공→실패 전환 1건을 포함**한다(D11). D10의 "실질 영향 0"은 **[A1]에만** 성립한다 |
| **v2-6** | §3-1 · §4 [A3] · §5 | 라인·문구 정정 — `createChildren`은 `279~322`(`_apr000` 블록 `282~291`) / "서비스가 안 씀" → **"권한 판단에 미사용"**(`createChildren`은 실제로 쓴다) / Controller 수정 메서드는 **4개** |
| **v2-7** | `spec.md` §4 | **읽기 경로 인가 부재**가 어느 작업에도 없었다 → **작업 E로 신설 등재**. `insite`의 한글 리터럴 무성 0건 의심도 D에 등재 |

> 리뷰가 사전에 의심했으나 **안전으로 확인된 것 4가지** — `memberId`가 `int`라 `!=` 참조 비교 문제 없음 /
> `src/test`에 결재 테스트 0건이라 `compileTestJava` 파손 없음 / `/approvers`가 `roleLessList`에 없어
> `getCurrentMemberId()` NPE 없음 / 호출부 집계가 §0 표와 전부 일치.

---

## 0. 착수 상태

**착수 가능.** 미확인 파일 0건, 미확정 결정 0건.

| 확인 항목 | 상태 | 결과 |
|---|---|---|
| 쓰기 엔드포인트 전수 | ✅ | 5종. 4종에 신원 검증 없음 (§3) |
| 제3자 토큰 실측 | ✅ | 4종 전부 200 확인 (§3) |
| `ErrorCode.APPROVAL_UNAUTHORIZED` (AP003) | ✅ | **선언만 존재, 사용처 0건.** 403 |
| `ErrorCode.APPROVAL_INVALID_STATUS_TRANSITION` (AP002) | ✅ | 400. 상태 가드용으로 이미 존재 (D5) |
| `GlobalExceptionHandler`의 403 매핑 | ✅ | `HttpStatus.valueOf(errorCode.getStatus())` — AP008이 403으로 실측됨 |
| **`ApprovalRepository.deleteById` 재정의** | ✅ | **벌크 JPQL DELETE**(`:45~49`). 없는 행이면 조용히 0행 (D11) |
| `processApprover` 호출부 | ✅ | `ApprovalController:127` **1곳** |
| `resaveTempSaved` 호출부 | ✅ | `ApprovalController:101` + `ApprovalCommandService:94` **2곳** |
| `delete` / `draft` 호출부 | ✅ | 각각 `Controller:135` / `Controller:115` 1곳 |
| `memberId` 필드 타입 | ✅ | `Approval:28`·`Approver:42` 둘 다 **`int`** — `!=` 값 비교 |
| `src/test`의 결재 테스트 | ✅ | **0건** (단계 7에서 2파일 삭제) — 시그니처 변경 파급 없음 |
| `/approvers` 인증 필요 여부 | ✅ | `roleLessList`에 없음 → 토큰 필수. `getCurrentMemberId()` 안전 |
| Referencer의 권한 연결 | ✅ | **0건** — 참조자는 처리 권한과 무관 |
| 서버측 role 권한 판단 | ✅ | 결재 도메인 **0건** |
| 프론트 대결·위임·강제승인 | ✅ | **0건** (프론트 소스 실측 — 근거는 리포 외부) |
| **삭제 버튼 노출 화면 (S0-h 사전 조건)** | ✅ | **`임시저장함(tempGiven)`에만.** `given`·`received`·`receivedRef`에 없음 (2026-07-31 화면 실측) |
| AGENTS.md / CLAUDE.md 갱신 대상 | ✅ | §9 |

---

## 1. 목표

결재 도메인의 **모든 쓰기 엔드포인트가 호출자 신원을 검증**하게 만든다.
`Approval.withdraw(memberId)`(단계 1) 선례를 나머지 4종에 적용한다.

**분리된 4건을 한꺼번에 닫는 것이 이 작업의 요점이다.** 하나만 막으면 나머지로 우회된다.
처방이 동일하고(호출자 사번 + AP003), 파일도 같고(Controller + CommandService), 같은 커밋이 자연스럽다.

### 성공 기준

| 지표 | Before | After |
|---|---|---|
| 신원 검증이 있는 쓰기 엔드포인트 | 5종 중 1종 | **5종 전부** |
| 제3자 토큰의 쓰기 성공 | 4종에서 200 | **전부 403 / AP003** |
| AP003 사용처 | **0건** | 4곳 |
| 신규 ErrorCode | — | **0건** |
| 권한 판단의 입력 | DTO 필드 또는 없음 | **메서드 파라미터로 명시 전달** |
| `_apr000`의 `memberId` | 재저장 시 호출자로 덮임 | 기안자와 항상 일치 |
| 응답 JSON 구조·값 | — | **불변** (실패 경로 추가 + **예외 1건** — D11) |
| 정상 경로(본인·지정 결재자) | — | **불변** ← 최우선 비회귀 |

---

## 2. 경계 (확정)

애매하면 이 표가 이긴다.

| 항목 | 작업 | 근거 |
|---|---|---|
| `PUT /approvers/{approverNo}` 지정 결재자 본인 확인 | **A** | 완료 보고서 §4 🔴 1 |
| `DELETE /approvals/{approvalNo}` 기안자 본인 확인 | **A** | §3 실측 |
| `PUT /approvals/{approvalNo}` 재임시저장 기안자 본인 확인 | **A** | §3 실측 |
| `POST /approvals` 기존번호 분기 기안자 본인 확인 | **A** | §3 실측 |
| — 이하 작업 A 밖 — | | |
| `jwt.key` 평문 커밋 / `PUT /resetPassword/{memberId}` 인가 | **B** | `resources/`·`member/**`. **A와 병행** |
| 응답 `password` 4지점 + 로그 위생 | **C** | 도메인이 갈리고 응답 JSON이 바뀐다 |
| **읽기 경로 인가 (상세 조회·파일 다운로드)** | **E** | `spec.md` §4-4. **정책 결정 선행** |
| `DELETE`의 상태 가드 (임시저장만 삭제) | **후속** | D5 |
| `createChildren`의 `_apr000` 생성 방식 자체 | **후속** | D8 — 소유자 검증으로 도달 불가해진다 |
| 저장형 XSS / 인증 실패 200 / CORS 전역 개방 / `insite` 무성 0건 | **D** | 등재만 |
| 결재 처리 기능 정지 (상태값 영·한 불일치) | **D** | 프론트 리포 소관 |
| `[K]` 재시도 / `receivedAll` / `finalApproverDate` / `[E]` | 이월 | 완료 보고서 §4 |

> **판단 규칙**: 작업 A는 **"이 요청을 보낸 사람이 그럴 자격이 있는가"** 만 묻는다.
> 상태 전이 규칙·결재 정책·응답 구조·열람 권한은 손대지 않는다. 애매하면 후속으로 미룬다.

### 범위 밖 (명시 · 🚫 하드 가드)

- 🚫 **`JwtAuthorizationFilter`** — `roleLessList:57`의 8번째 원소가 `"/announces/{ancNo}, /approvals"`로
  **쉼표가 따옴표 안에** 있어 어떤 URI에도 매칭되지 않는다. 결과적으로 `/approvals`가 인증 필요 상태로
  유지되고 있으며 **현재 동작이 옳다.** "고치면" `/approvals`가 무인증으로 열린다. **읽기만 허용.**
- 🚫 **`enums/ApprovalStatus.java`의 `description` 필드와 `from()`의 한글 매칭 분기** —
  프론트가 `"임시저장"`·`"처리 중"`을 보낸다. 제거하면 **기안이 전부 실패**한다.
- 🚫 **DTO의 Enum 타입 전환** — Jackson 기본 역직렬화는 `name()`만 매칭한다. 봉인.
- **프론트엔드 변경** — 리포가 다르다. D10 참조.
- `dto/**`, `enums/**`, `entity/**`, `repository/**`, `service/file/**`, `service/generator/**`
- `auth/**`, `member/**`, `insite/**`, DB 스키마, `src/test/**`, 새 기능

---

## 3. 실측 근거

호출자는 **기안자도 지정 결재자도 참조자도 아닌 제3자**의 유효한 토큰이다.

| # | 엔드포인트 | 결과 | 일시 |
|---|---|---|---|
| **(A1)** | `PUT /approvers/{approverNo}` | **200.** 승인 시 문서 `PROCESSING → APPROVED` 완결 / 반려 시 `REJECTED` 즉시 종료. 처리자는 **지정 결재자 사번으로 기록** | 07-30 (07 §6-2) |
| **(A2)** | `DELETE /approvals/{approvalNo}` | **200 "전자결재 삭제 성공".** 대상은 삭제 **전에** `PROCESSING` 확인. 프론트에서 소멸 확인 | 07-31 |
| **(A3)** | `PUT /approvals/{approvalNo}` | **200.** 제목·본문·양식·결재선·참조선 전량 교체됨 | 07-31 |
| **(A4)** | `POST /approvals` (body에 남의 `approvalNo` + `"처리 중"`) | **200.** 내용 반영, 최상위 `memberId` 유지 | 07-31 |

### 3-1. (A3)의 상세 — 기안자 불일치가 실물로 확인됐다

`2026-ims00014` (기안자 123, `TEMP_SAVED`)에 제3자(240501629) 토큰으로 PUT.

| 필드 | PUT 전 | PUT 후 |
|---|---|---|
| `approval.memberId` | 123 | **123** (유지) |
| `approver[0].memberId` (`_apr000`, order 0, APPROVED) | 123 | **240501629** ← 호출자로 덮임 |

**원인**: `modifyDraft`는 최상위 `memberId`를 건드리지 않고,
`createChildren`(`CommandService:279~322`, `_apr000` 생성 블록 `282~291`)이
`approvalDTO.getMemberId()`(Controller가 호출자로 세팅)로 `_apr000`을 새로 만든다. **출처가 갈린다.**

→ **한 문서 안에서 기안자가 둘이 된다.** 프론트는 `approver[0].memberId`로 기안자를 판정하므로
(`ApprovalDetail.js:121`) 화면과 서버가 다른 사람을 기안자로 본다.

> (A4)도 같은 경로(`resubmit → createChildren`)이므로 동일 현상이 예상되나 **`_apr000` 실측은 미수행**이다.
> → **S6-b에서 확인한다.**

### 3-2. 측정 방식

Claude Code로 실측을 시도했으나 **사이버보안 안전장치가 인증 우회 재현을 공격으로 오탐**해 차단됐다.
사용자 수동 실행(Postman)으로 전환했다 — 분담표의 기본값이다. **§10 검증도 동일하다.**

---

## 4. 처방

### 공통 원칙

1. **검증은 서비스 계층에서 한다.** Controller는 `getCurrentMemberId()`로 뽑아 넘기기만 한다
   (단계 7이 만든 헬퍼, `ApprovalController:175~177`).
2. **호출자 사번은 메서드 파라미터로 명시 전달한다.** DTO 필드를 권한 판단에 쓰지 않는다 (D2).
3. **검증 순서: 존재(404) → 신원(403) → 상태(400).**
   대상 행을 읽어야 소유자를 알 수 있으므로 존재 검증이 먼저일 수밖에 없다 (D3).
4. **파괴적 부작용보다 앞에 둔다.** 특히 디스크 파일 삭제·자식 벌크 삭제 **전에** 막아야 한다.
   `resaveTempSaved`의 기존 주석이 이미 그 이유를 적어 두었다 —
   *"롤백은 DB만 되돌리고 삭제된 파일은 되돌리지 못한다."*

---

### [A1] `PUT /approvers/{approverNo}` — 지정 결재자 본인 확인

| 위치 | 현재 | 처방 |
|---|---|---|
| `ApprovalController:121~128` | 인증 추출 **없음** | `getCurrentMemberId()`를 뽑아 전달 |
| `ApprovalCommandService:185` | `processApprover(String, ApproverDTO)` | `processApprover(String, ApproverDTO, int memberId)` |

서비스 처방 — `approver` 조회(기존 AP004) **직후**:

```
approver.getMemberId() != memberId  →  BusinessException(APPROVAL_UNAUTHORIZED)   // AP003 / 403
```

- 기존 AP004(존재)·AP005(상태 전이) 가드는 **그대로 둔다.** 순서만 존재 → 신원 → 상태
- 호출부는 Controller 1곳이므로 시그니처 변경 파급은 1줄

> **부수 개선 (기록용)**: 신원 검증이 `ApproverStatus.from()`보다 앞서게 되어,
> 제3자가 잘못된 상태 문자열을 보냈을 때의 **500/C999(Stage 6 §8 관찰 5)가 403으로 바뀐다.**
> 의도한 효과는 아니지만 방향이 옳다. 보고서에 기록할 것.

---

### [A2] `DELETE /approvals/{approvalNo}` — 기안자 본인 확인

| 위치 | 현재 | 처방 |
|---|---|---|
| `ApprovalController:131~136` | 인증 추출 **없음** | `getCurrentMemberId()`를 뽑아 전달 |
| `ApprovalCommandService:224~235` | `delete(String)` — **가드 0줄** | `delete(String, int memberId)` |

서비스 처방 — 메서드 **첫머리**, `approvalFileService.deleteByApprovalNo()` **앞**:

```
1. approvalRepository.findById(approvalNo)
       .orElseThrow(APPROVAL_NOT_FOUND)                  ← 신설 (AP001 / 404)
2. approval.getMemberId() != memberId  →  AP003 / 403     ← 신설
3. (이하 기존 삭제 순서 그대로)
```

> ⚠ **동작 변경 1건 — D11.** `ApprovalRepository:45~49`가 `deleteById`를 **벌크 JPQL DELETE로 재정의**해
> 없는 행이면 조용히 0행 삭제로 끝난다. 즉 현재 없는 번호 삭제는 **`200 / true`** 다.
> 1을 넣으면 이 경로가 **404 / AP001**로 바뀐다. **수용하되 보고서에 before/after를 명시한다.**

- **상태 가드(임시저장만 삭제)는 이번에 넣지 않는다** — D5
- 영속성: `findById`로 managed 상태가 되지만 이후 벌크 DELETE만 실행되고 엔티티는 변경되지 않아
  flush 시 부활 SQL이 없다 (리뷰 확인)

---

### [A3] `PUT /approvals/{approvalNo}` 재임시저장 — 기안자 본인 확인

| 위치 | 현재 | 처방 |
|---|---|---|
| `ApprovalController:93~105` | L99가 `approvalDTO.setMemberId(getCurrentMemberId())` — **권한 판단에 미사용** | `memberId`를 **파라미터로도** 전달 |
| `ApprovalCommandService:130~154` | `resaveTempSaved(String, ApprovalDTO, List<MultipartFile>)` | `+ int memberId` |

> ⚠ L99의 값이 아예 안 쓰이는 것은 아니다. `createChildren`이 `_apr000` 생성에 쓴다(§3-1).
> **권한 판단에 쓰이지 않을 뿐이다.** D2가 `setMemberId` 유지를 결정한 근거와 직결된다.

서비스 처방 — 조회(기존 AP001) **직후**, `TEMP_SAVED` 가드 **앞**:

```
1. findById → orElseThrow(APPROVAL_NOT_FOUND)             기존 (AP001 / 404)
2. approval.getMemberId() != memberId → AP003 / 403       ← 신설
3. approvalStatus != TEMP_SAVED → AP011 / 400             기존
4. clearChildren(...) 이하 기존 순서 그대로
```

> **2를 3보다 앞에 두는 이유**: 남의 문서인지 여부가 상태보다 먼저 판정돼야 한다.
> 3이 먼저면 제3자에게 "그 문서는 임시저장이 아니다"라는 정보를 준다.
> 그리고 둘 다 **디스크 삭제(4) 앞**이어야 한다.
> 검증은 `clearChildren`의 `entityManager.clear()`(`ApprovalRepositoryImpl:22~24`) 앞이라 안전하다.

⚠ **호출부가 2곳이다.**
- `ApprovalController:101` — 외부 진입. 호출자 사번 전달
- `ApprovalCommandService:94` — `draft` 내부. `draft`가 받은 `memberId`를 그대로 넘긴다
  (이 경로는 `draft`에서 이미 검증되므로 **이중 검증**이 된다. 비용은 비교 1회, 허용 — D4)

---

### [A4] `POST /approvals` 기존번호 분기 — 기안자 본인 확인

| 위치 | 현재 | 처방 |
|---|---|---|
| `ApprovalController:108~119` | L112가 `approvalDTO.setMemberId(getCurrentMemberId())` | **유지** + `memberId`를 파라미터로도 전달 |
| `ApprovalCommandService:83~96` | 기존번호 분기에서 `existing`을 로드하지만 소유자 확인 없음 | `existing` 로드 직후 검증 |

서비스 처방 — `draft(ApprovalDTO, List<MultipartFile>, int memberId)`:

```
if (requestedApprovalNo 가 있고) {
    existing = findById(...).orElse(null)

    if (existing != null && existing.getApprovalStatus() == TEMP_SAVED) {

        ★ existing.getMemberId() != memberId  →  AP003 / 403      ← 신설
        ★ 반드시 이 위치. PROCESSING 분기 판정보다 "앞"이다

        if (initialStatus == PROCESSING) { resubmit(...) }
        else                             { resaveTempSaved(...) }
    }
}
```

> ★★ **검증을 `if (initialStatus == PROCESSING)` 안쪽에 넣지 마라.**
> 그러면 `POST + "임시저장"` 경로(= `resaveTempSaved` 갈래)가 열린 채로 남고,
> **검증 시나리오를 전부 통과하면서 결함이 살아 있다.** → §10 **S4-b**가 이 배치를 잡는다.

> ★ **신규 채번 분기(`CommandService:99~121`)에는 검증을 넣지 않는다.**
> 그 경로의 `memberId`는 **언제나 호출자 자신**이라 비교 대상이 없다.
> 넣으면 **모든 신규 기안이 실패한다.** (R7 — 착수 전 체크 3번)

- `resubmit`(private)에는 추가 검증을 넣지 않는다. `draft`가 이미 검증했다. **주석으로 명시할 것**

---

## 5. Scope — 수정 허용 파일

**수정 (2개뿐)**
- `approval/controller/ApprovalController.java` — **4개 메서드**
  (신규 인증 추출 2개: `updateApprover`·`deleteApproval` / 기존 추출값 인자 전달 2개: `updateApprovalTemp`·`insertApproval`)
- `approval/service/ApprovalCommandService.java` — 4개 메서드 시그니처 + 검증 4곳

**신규**: 없음
**삭제**: 없음

**금지 (손대지 않음)**
- `common/error/ErrorCode.java` — **AP003 재사용. 신규 상수 0건** (D6)
- `approval/entity/**`, `approval/enums/**`, `approval/dto/**`, `approval/repository/**`
- `approval/service/ApprovalQueryService.java`, `service/file/**`, `service/generator/**`
- `auth/**`, `member/**`, `insite/**`, `common/**`(ErrorCode 포함), `src/test/**`, 프론트엔드, DB 스키마

> **Scope가 2파일이다.** 리뷰가 호출부 전수 + `src/test` 결재 테스트 0건으로 **"정말 2개로 끝난다"** 를
> 확인했다. diff에 세 번째 코드 파일이 등장하면 범위 이탈이다. 문서 파일은 §9에 따로 있다.

---

## 6. ErrorCode — 재사용만, 신규 0건

| 상수 | 코드 | HTTP | 이번 용도 |
|---|---|---|---|
| `APPROVAL_UNAUTHORIZED` | **AP003** (기존) | 403 | **4곳 전부.** 선언만 있고 사용처가 0건이었다 — 이번에 활성화 |
| `APPROVAL_NOT_FOUND` | AP001 (기존) | 404 | [A2]에 신설되는 존재 검증 |
| `APPROVER_NOT_FOUND` | AP004 (기존) | 404 | [A1] 기존 유지 |
| `APPROVAL_MODIFY_NOT_ALLOWED` | AP011 (기존) | 400 | [A3] 기존 유지 |
| ~~`APPROVAL_INVALID_STATUS_TRANSITION`~~ | AP002 | 400 | **이번엔 쓰지 않는다.** 상태 가드는 후속(D5) |

- 메시지 `"해당 결재에 대한 권한이 없습니다."`가 AP003 4곳 모두에 문구상 맞다
- **`ErrorCode.java`를 수정하지 않는다.** 다음 여유 번호는 AP012지만 이번엔 필요 없다
- `GlobalExceptionHandler:21~28`이 `HttpStatus.valueOf(errorCode.getStatus())`로 매핑하므로
  AP003은 403으로 나간다 (같은 계열 AP008이 403으로 실측됨)

---

## 7. 결정 사항 (D1~D11 전 항목 확정)

### D1. 검증 주체 — ✅ **확정: 각 자원의 소유자 본인만**

| 엔드포인트 | 허용되는 사람 | 비교 대상 |
|---|---|---|
| `PUT /approvers/{no}` | **지정 결재자 본인** | `approver.getMemberId()` |
| `DELETE /approvals/{no}` | **기안자 본인** | `approval.getMemberId()` |
| `PUT /approvals/{no}` | **기안자 본인** | `approval.getMemberId()` |
| `POST /approvals` 기존번호 | **기안자 본인** | `existing.getMemberId()` |

- **참조자에게 처리 권한을 주지 않는다.** `referencerRepository`가 권한 판단에 쓰이는 곳이 코드 전체에 0건이다
- **기안자에게 결재 처리 권한을 주지 않는다.** 기안자가 자기 `_apr000`을 호출해도
  `APPROVED → *` 전이가 전부 false(`ApproverStatus:19`)라 AP005 → **동작 변화 없음**
- **role/ADMIN 예외를 넣지 않는다.** 서버에서 role로 권한을 판단하는 지점은 결재 도메인에 0건이고,
  프론트에도 관리자 강제승인 화면이 없다(소스 실측). 넣을 근거가 없다

### D2. 호출자 사번 전달 방식 — ✅ **확정: 명시적 메서드 파라미터**

`withdraw(approvalNo, memberId)` 선례를 따른다. **DTO 필드를 권한 판단에 쓰지 않는다.**

> 근거: `approvalDTO.getMemberId()`로 판단하면 spec `[D]`가 고친 **"DTO 값 신뢰"가 그대로 재발**한다.
> 지금은 Controller가 그 필드를 덮어쓰지만, 그 사실이 서비스 시그니처에 드러나지 않는다.
> 권한의 입력은 시그니처에 보여야 한다.

Controller의 기존 `setMemberId` 호출(L99·L112)은 **유지한다.** `createChildren`이 `_apr000` 생성에
쓰고 있어, 제거하면 그 경로까지 손대야 해서 회귀면이 넓어진다 (D8과 짝).

### D3. 검증 순서 — ✅ **확정: 존재(404) → 신원(403) → 상태(400)**

대상 행을 읽어야 소유자를 알 수 있으므로 **존재 검증이 먼저일 수밖에 없다.** 구조적 제약이다.
신원을 상태보다 앞에 두는 이유는 §4 [A3] 참조.

### D4. 이중 검증 허용 — ✅ **확정: 허용한다**

`draft → resaveTempSaved` 경로에서 검증이 두 번 일어난다.
**각 public 메서드가 독립적으로 안전한 것이 우선**이다 — `resaveTempSaved`는 Controller에서도 직접 진입한다.
비용은 이미 로드된 엔티티의 int 비교 1회다.

### D5. `DELETE`의 상태 가드 — ✅ **확정: 이번엔 넣지 않는다** (v2에서 근거 교체)

`@Tag`는 "전자결재 임시저장 삭제"라고 적혀 있으나 코드에 `TEMP_SAVED` 검사가 없고,
실측에서 `PROCESSING` 문서가 삭제됐다. 그럼에도 이번엔 소유자 검증만 한다.

**근거 1 — 결재 정책 변경에 해당한다.** 어떤 상태의 결재를 삭제할 수 있는지는 비즈니스 규칙이며,
`spec.md` §7이 "결재 비즈니스 정책의 변경"을 범위 밖으로 명시했다.

**근거 2 — 허용 상태의 정의가 정책 결정이다.** `TEMP_SAVED`만 허용하면 **회수한 문서(`WITHDRAWN`)도
못 지운다.** 기안 → 회수 → 삭제는 자연스러운 흐름이다. 그러면 `+WITHDRAWN`은? `+REJECTED`는?
**이 질문의 답은 코드에도 화면에도 없다.** 2줄짜리 작업이 아니다.

> **v3 정정**: v2의 근거 2는 "프론트에서 삭제 버튼 노출 조건이 미확인"이었다. **확인됐다** —
> `임시저장함`에만 있다. 즉 상태 가드를 넣어도 **지금 UI는 안 깨진다.**
> **그러나 그것이 "넣어도 된다"의 근거는 아니다.** 프론트가 기능을 빠뜨렸을 가능성이 있다 —
> 실제로 `내 결재함`에 삭제가 없어 **회수 문서를 지울 경로가 없다.** 프론트 관찰은 회귀 판정에만 쓴다(v3 ⚠).

> 🔴 **잔여 위험 (보고서에 명시할 것)**: 소유자 검증만으로는 **기안자 본인이 자기 `APPROVED`·`REJECTED`
> 문서를 API로 직접 삭제하는 것**을 막지 못한다. 감사 기록 파괴라는 점에서 이번 작업과 같은 계열이고,
> **동기가 있는 쪽은 오히려 본인**이다. 삭제 가능 상태 정의는 **후속 결정 사항**이다.

> ~~v1의 근거 "AP011 문구가 어긋나 AP012 신설이 필요해진다"는 **사실이 아니었다.**~~
> `APPROVAL_INVALID_STATUS_TRANSITION`(**AP002**, 400)이 이미 존재하고 `Approval`의 상태 전이 메서드가
> 전부 이것을 쓴다. **후속 작업에서 상태 가드를 넣을 때 신규 상수는 필요 없다.**

**⚠ 소유자 가드와의 비대칭에 대하여** (리뷰 지적):
"프론트 미확인"이라는 같은 이유가 [A2]의 소유자 가드에도 적용되지 않느냐는 지적이 있었다. 구분은 이렇다.

| | 성격 | 미확인일 때의 판단 |
|---|---|---|
| **소유자 가드** | **결함을 고치는 것** | 비소유자에게 삭제 버튼이 뜬다면 **그것 자체가 결함**이다. 막는 것이 옳다 |
| **상태 가드** | **정책을 추가하는 것** | 소유자가 자기 `PROCESSING` 문서를 지우는 것은 **정당할 수 있다.** 막으면 기능 제거다 |

→ **확인은 끝났다.** 삭제 버튼은 `임시저장함`에만 있다(§0). §10 S0-h는 사전 조건이 아니라
**확인된 사실**로 기록한다.

### D6. ErrorCode — ✅ **확정: AP003 재사용, 신규 0건**

### D7. 응답 형식 — ✅ **확정: 성공 응답 JSON 구조·값 불변, 단 D11 예외 1건**

시그니처 변경은 서비스 내부이며 응답 DTO는 그대로다.
`delete`의 반환 타입 `boolean`도 유지한다 — `ResponseMessage<Boolean>`이 바뀌면 안 된다.

> ⚠ v1은 "실패 경로만 추가"라고 적었으나 정확하지 않다. **성공 → 실패 전환이 1건 포함된다**(D11).

### D8. `_apr000` 덮어쓰기 — ✅ **확정: `createChildren`을 손대지 않는다**

§3-1의 기안자 불일치는 **소유자 검증이 들어가면 도달 불가해진다.**
Controller가 DTO에 세팅하는 `memberId`(호출자) == `approval.memberId`(기안자)가 보장되기 때문이다.
(리뷰가 논리 성립을 확인했다.)

> 검증을 통과한 뒤에도 남는 것은 "재임시저장이 `_apr000`을 재생성한다"는 **구조**뿐이고,
> 관측 가능한 결함이 아니다. 여기서 `createChildren`까지 고치면 "온 김에"가 된다.
> **§10 S6에서 실제로 해소됐는지 확인한다** (PUT·POST 양쪽).

### D9. 문서 정정 — ✅ **확정: 이번 문서 커밋에 포함**

§9-3의 정정 4건은 **리팩토링 최종 기록의 정확성 문제**다. 이번에 닫는다. 코드 커밋과 분리한다.

### D10. 프론트 대응 — ✅ **확정: 프론트 리포를 열지 않는다** (v2에서 문구 축소)

`ApprovalAPI.js:211~214`에 `throw`가 없어 **서버가 403을 내도 "결재 처리 완료" 모달이 뜬다.**
그러나 이것은 **신규 결함이 아니다** — AP004·AP005도 지금 같은 경로로 삼켜진다.

> ⚠ v1의 "실질 영향 0"은 **[A1]에만** 성립한다. 상태값 불일치로 승인·반려 버튼이 렌더되지 않아
> `PUT /approvers`에 정상 UI로 도달할 경로가 없기 때문이다.
> **[A2]·[A3]·[A4]는 정상 UI에서 도달하는 경로다.** 다만 소유자 검증을 통과하는 정상 사용자가
> 403을 받을 경로가 없으므로 실질 영향은 여전히 낮다 — **근거가 다르다.**

→ **알려진 결과로 §10 검증 체크리스트와 보고서에 명시한다.** 검증 판정은 **응답 코드로** 한다.

### D11. `DELETE`의 "없는 번호" 응답 — ✅ **확정: 404/AP001 수용** (v2 신설)

`ApprovalRepository:45~49`가 `deleteById`를 **벌크 JPQL DELETE로 재정의**하고 있다.

```java
@Modifying @Transactional
@Query("DELETE FROM Approval a WHERE a.approvalNo = :approvalNo")
void deleteById(@Param("approvalNo") String approvalNo);
```

매칭 행이 없어도 0행 삭제로 조용히 끝난다 → **현재 응답은 `200 / "전자결재 삭제 성공" / true`** 이며,
이는 프레임워크 버전 의존이 아니라 **코드로 확정**된다.

| 안 | 내용 | 판정 |
|---|---|---|
| **a** | **404/AP001 수용** | ✅ **확정** |
| b | `orElse(null)` — null이면 검증을 건너뛰고 기존 흐름 유지(200/true 보존) | 채택 안 함 |

**근거**: (b)는 **"없는 것을 지웠는데 성공을 반환"** 하는 형태다. 단계 1.5에서 가장 크게 데인
"조용한 성공/0건"과 같은 계열이고, 단계 7이 `receivedAll`을 400으로 **드러나게** 한 판단과도 어긋난다.
프론트 영향도 낮다 — `deleteApprovalAPI`는 에러를 `console.log`만 하고 화면 반응이 없어
404든 200이든 사용자에게 보이는 차이가 없다.

⚠ **D7의 유일한 예외다.** 보고서에 before/after를 명시한다.

---

## 8. 위험 목록

| # | 위험 | 대응 |
|---|---|---|
| **R1** ★ | **`jwt.key`가 공개 리포에 평문으로 커밋돼 있다.** 키를 아는 사람은 임의 사번 토큰을 위조할 수 있고, 그러면 이번 검증은 **위조된 사번과 비교**하게 된다 — **인가는 인증의 무결성 위에서만 성립한다** | **작업 B는 A 직후**(배포 인스턴스가 없어 실질 위험 0 — `spec.md` §4-1). 보고서에 "이 검증의 신뢰 기반은 토큰 무결성이며 현재 그것이 보장되지 않는다"를 **반드시 명시.** 작업 A가 무의미한 것은 아니다 — 정상 사용자의 우회를 막고 감사 기록을 지킨다 |
| **R2** ★ | **`resaveTempSaved` 호출부가 2곳**이다 (`Controller:101`, `CommandService:94`) | 시그니처 변경 시 두 곳 모두 확인. `draft`는 받은 `memberId`를 그대로 넘긴다. **P3→P4 순서가 안전장치다** |
| **R3** | `delete`에 `findById`가 신설되면서 **없는 번호의 응답이 `200 → 404`로 바뀐다** | D11에서 수용 확정. **보고서에 before/after 명시.** (v1의 "실측하라"는 부정확했다 — `ApprovalRepository:45~49`로 코드상 확정된다) |
| **R4** | 프론트가 403을 삼켜 **"결재 처리 완료" 모달**을 띄운다 | D10 — 알려진 결과. 프론트 수정 안 함. **검증 판정은 응답 코드로** 한다 |
| **R5** ★ | **결재 처리 기능이 이미 프론트에서 정지 상태**다(상태값 영·한 불일치). 이번 작업이 만든 회귀가 아니다 | **검증을 화면으로 할 수 없다.** §10은 전부 API 직접 호출이다 |
| **R6** | 검증에 **계정 3개**가 필요하다 — 기안자 A / 지정 결재자 Z / 무관한 제3자 B | 제3자 지위를 07 §6-2 방식으로 **먼저 확정**한다 (`given` 0건 / `receivedRef` 0건 / `approver` 배열에 없음) |
| **R7** ★★ | **`POST /approvals`의 신규 채번 분기에 검증을 넣으면 모든 신규 기안이 실패한다.** 그 경로의 `memberId`는 언제나 호출자 자신이라 비교 대상이 없다 | 검증은 **기존번호 분기 안에만.** §10 S0-a가 첫 확인 항목 |
| **R8** ★★ | **검증을 `if (initialStatus == PROCESSING)` 안쪽에 넣으면 `POST + "임시저장"` 경로가 열린 채 S0~S8을 전부 통과한다** | §4 [A4]의 위치를 정확히 지킬 것. **S4-b가 이 배치를 잡는 유일한 항목이다** |
| **R9** | **AP003의 첫 사용**이다. 매핑은 코드로 확인했으나 실측은 처음 | S1~S4에서 **403 + `"AP003"`** 을 눈으로 확인 |
| **R10** | 검증 과정에서 오염된 데이터가 개발 DB에 남아 있다 — `2026-ims00014` 외에도 임시저장함에 `_apr000`이 **호출자로 덮인 문서가 최소 2건**(`"이진아로 X2 put 요청"` 00:25:27·00:32:38). 최상위 `memberId`와 `approver[0].memberId`가 다르다 | 정리 여부는 사용자 판단. **보고서에 목록 기록.** S6 검증 시 이 문서들을 대상으로 쓰지 말 것(이미 오염돼 있어 before/after 판정이 흐려진다) |
| **R11** | `compileJava`·`bootRun` 통과는 **아무것도 증명하지 않는다.** 인증 경로 변경은 컴파일에 전혀 드러나지 않는다 (단계 7 교훈) | §10 수동 검증이 본체 |

---

## 9. 문서 갱신

### 9-1. AGENTS.md — 4지점

**① L9 "현재 작업" 절 제목**
```
before: ## 현재 작업: 전자결재(Approval) 도메인 리팩토링
after:  ## 현재 작업: 전자결재(Approval) 도메인 보안 결함 정리
```

**② L11~14 필수 읽기 순서** — 새 경로로 교체하고, `completed/approval-domain.md`를 최우선 참조로 올린다
```
after:  1. docs/refactoring/completed/approval-domain.md — 리팩토링 최종 기록 (§4 이월 목록이 출발점)
        2. docs/security/approval/spec.md — 무엇을, 왜 (plan은 spec에 흡수)
        3. docs/security/approval/tasks/{현재 작업}.md — 지금 할 작업
```

**③ L21~23 현재 진행 단계** — ⚠ **이미 낡아 있다.** 단계 7이 "현재"로 남아 있고 커밋 해시가 단계 6 기준이다
```
after:  ### 현재 진행 작업
        **작업 A: 쓰기 경로 권한 경계 정리** — `docs/security/approval/tasks/01-write-authz.md`
        (전자결재 리팩토링 7단계 전부 완료·커밋·푸시 — `e35e0b1`(코드)·`b314778`(잔해)·`94d7a18`(문서), origin/main 동기화)
```

**④ L25~35 단계 로드맵** — 리팩토링 로드맵을 **7/7 완료로 접고** 새 작업 스트림 목록으로 교체
```
after:  전자결재 리팩토링 1~7 ✅ 전부 완료 (docs/refactoring/completed/approval-domain.md)

        보안 결함 정리
        A. 쓰기 경로 권한 경계 정리 (현재)
        B. jwt.key 평문 커밋 · resetPassword 인가 (병행)
        C. 응답 password 노출 · 로그 위생
        E. 읽기 경로 인가 (정책 결정 선행)
        D. 등재만 — XSS · 인증 실패 200 · CORS · 상태값 불일치 · insite 무성 0건
```

> **⚠ L37~40 "완료된 리팩토링"의 죽은 링크 2건**(`error-handling.md`·`leave-domain.md`)은
> **타 도메인 소관이라 고치지 않는다.** 기록만.

### 9-2. CLAUDE.md — 보고서 경로 (L10~11)

```
before: - 작업 완료 시 보고서를 `docs/refactoring/{도메인}/reports/{단계}-report.md`에
          UTF-8로 저장할 것
after:  - 작업 완료 시 보고서를 `docs/{작업 스트림}/{도메인}/reports/{작업}-report.md`에
          UTF-8로 저장할 것
          (예: docs/refactoring/approval/reports/07-controller-report.md,
               docs/security/approval/reports/01-write-authz-report.md)
```

> `docs/refactoring/` 접두사가 하드코딩돼 있어 `{도메인}` 치환만으로는 `docs/security/`가 나오지 않는다.
> **보안 작업을 `refactoring/` 아래 두는 회피안은 채택하지 않는다** — 경로가 사실과 달라지고,
> 이 프로젝트가 계속 기록해온 문서 드리프트를 스스로 만드는 셈이다.

### 9-3. 정정 4건 (D9 — 이번 문서 커밋에 포함)

| # | 대상 | 정정 내용 |
|---|---|---|
| 1 | `completed/approval-domain.md:148` · `reports/07-controller-report.md:183` | "평문 비밀번호가 **로그 파일에 적재**되고 있었다" → **앱에는 파일 appender가 없다.** `logback-*.xml` 부재, `application.yml`에 `logging.file.*` 없음. 유출 경로는 **콘솔/stdout**이다. 피해 크기 판단이 달라진다 |
| 2 | `completed/approval-domain.md` §4 ⚪ | "로그인 경로가 bcrypt 해시를 로그로 출력 — 3곳" → **과소집계.** 평문 비밀번호 4곳 + 전 사원 해시 일괄 출력(`MemberService:191·193`) + 토큰 전문 3곳(활성)이 목록에 없다 |
| 3 | `completed/approval-domain.md` §5-5 | "목록 상태 컬럼에 영문 Enum 노출 — **프론트 표시 매핑 문제**" → **기능 정지.** 같은 원인이 `canApproveOrReject`를 항상 false로 만들어 **승인·반려 버튼이 렌더되지 않는다**(2026-07-31 실측) |
| 4 | `completed/approval-domain.md` §3 | "spec 결함 목록이 완전하지 않았다"의 실제 규모 — `PUT /approvers` 1건이 아니라 **쓰기 5종 중 4종**이었고, **읽기 경로에도 인가가 없다**(작업 E) |

### 9-4. 보고서

`docs/security/approval/reports/01-write-authz-report.md`
형식은 `reports/07-controller-report.md`를 따른다. 수동 검증 체크리스트는 §10 S0~S8을 **원문 그대로** 옮긴다.

> **프론트 실측 근거는 리포 외부다.** `ApprovalDetail.js`·`ApprovalAPI.js` 인용과
> "대결·위임·강제승인 0건" 판정은 프론트 리포(`LOG-IN-F-Refactoring`, `main`, `8c13156`) 소스 조사 결과임을
> 보고서에 명시할 것. 이 리포에서는 검증할 수 없다.

---

## 10. 검증

### 자동 검증

```powershell
cd final
.\gradlew.bat compileJava
.\gradlew.bat compileTestJava
.\gradlew.bat bootRun
```

> ⚠ 세 개가 통과했다는 것은 **아무것도 증명하지 않는다.** 인증 경로 변경은 컴파일·기동에 전혀 드러나지 않는다.

**검색 확인 (PowerShell 5.1)**

> ⚠ `Select-String`에는 `-Recurse`/`-Include`가 없다. `Get-ChildItem ... -Recurse | Select-String` 형태로 쓴다.

```powershell
cd final
$approval   = Get-ChildItem -Path .\src\main\java\com\insider\login\approval -Filter *.java -Recurse
$controller = Get-ChildItem -Path .\src\main\java\com\insider\login\approval\controller -Filter *.java -Recurse
$command    = Get-ChildItem -Path .\src\main\java\com\insider\login\approval\service -Filter ApprovalCommandService.java -Recurse

# 1. AP003 이 드디어 사용되는지 (4건이어야 한다)
$approval | Select-String -Pattern "APPROVAL_UNAUTHORIZED" -Encoding UTF8

# 2. Controller 의 인증 추출 지점 (쓰기 5종 전부에 있어야 한다)
$controller | Select-String -Pattern "getCurrentMemberId" -Encoding UTF8

# 3. 권한 판단에 DTO 를 쓰고 있지 않은지 눈으로 확인
$command | Select-String -Pattern "approvalDTO\.getMemberId\(\)" -Encoding UTF8

# 4. ErrorCode 무변경 확인 (0건이어야 한다)
Get-ChildItem -Path .\src\main\java\com\insider\login\common\error -Filter ErrorCode.java -Recurse |
    Select-String -Pattern "AP012" -Encoding UTF8

# 5. 범위 이탈 확인 (코드는 2파일이어야 한다)
git status
git diff --stat
```

### 수동 검증 — 시나리오 (사용자 담당 · **전부 API 직접 호출**)

> **화면으로 검증할 수 없다** (R5). Postman 등으로 수행한다.
> 계정 3개: **A**=기안자 / **Z**=지정 결재자 / **B**=무관한 제3자.
> 시작 전 **B의 제3자 지위를 확정**한다 — `given` 0건, `receivedRef` 0건, 대상 문서의 `approver[].memberId`에 B 없음.
> **판정은 응답 코드로 한다** — 프론트는 403을 삼킨다(D10).

---

**S0. 정상 경로 비회귀** ★★ **가장 중요. 먼저 한다**

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

**S1. [A1] 제3자 결재 처리 차단** ★
B 토큰으로 `PUT /approvers/{Z의 approverNo}` → **403 + `"AP003"`**

> ⚠ **사전 조건**: Z의 `approverNo`는 반드시 **`PENDING`** 이어야 한다.
> 이미 처리된 건이면 검증을 상태 검증 **뒤에** 잘못 배치해도 400/AP005가 나와
> "차단됨"으로 오판하게 된다. **응답 코드가 AP005면 검증 배치가 틀린 것이다.**

**S2. [A2] 제3자 삭제 차단** ★
B 토큰으로 `DELETE /approvals/{A의 결재}` → **403 + `"AP003"`**, 문서가 **남아 있는지** 재조회로 확인

**S3. [A3] 제3자 재임시저장 차단** ★
B 토큰으로 `PUT /approvals/{A의 임시저장}` → **403 + `"AP003"`**
→ 재조회해서 **제목·본문·결재선·첨부가 그대로**인지 확인
(막혔는데 자식이 지워졌으면 검증 위치가 디스크 삭제 뒤에 있는 것이다)

**S4. [A4] 제3자 상신·덮어쓰기 차단** ★★ **두 방향 모두 필수**

| # | 요청 | 기대 | 이 항목이 잡는 것 |
|---|---|---|---|
| **S4-a** | B 토큰 `POST /approvals` + A의 임시저장 `approvalNo` + **`"처리 중"`** | **403 + AP003** | `resubmit` 갈래 |
| **S4-b** ★ | B 토큰 `POST /approvals` + A의 임시저장 `approvalNo` + **`"임시저장"`** | **403 + AP003** | **`resaveTempSaved` 갈래.** 검증을 `if (initialStatus == PROCESSING)` 안쪽에 넣었으면 **여기서만 200이 난다** (R8) |

→ 둘 다 재조회해서 **`TEMP_SAVED` 유지**, 제목·본문 불변 확인

**S5. 회수 비회귀**
B 토큰으로 `PUT /approvals/{A의 결재}/status` → **403 + `"AP008"`** (기존 동작, 바뀌면 안 된다)

---

**S6. [D8] `_apr000` 무결성** ★ **PUT·POST 양쪽**

| # | 시나리오 | 확인 |
|---|---|---|
| **S6-a** | A가 자기 임시저장을 재저장 (`PUT`) | 재조회 시 **`approver[0].memberId` == `approval.memberId` == A** |
| **S6-b** | A가 자기 임시저장을 기안 전환 (`POST` + `"처리 중"`) | 동일 (`resubmit` 경로 — §3-1이 미실측으로 남긴 지점) |

(현행은 재저장 주체가 `_apr000`에 덮인다. 소유자 검증으로 해소돼야 한다 — §3-1)

**S7. 기존 에러코드 비회귀**

| 요청 | 기대 |
|---|---|
| 없는 `approverNo` | 404 / AP004 |
| 이미 처리된 결재자 재승인 (본인이) | 400 / AP005 |
| 없는 `approvalNo` 재임시저장 | 404 / AP001 |
| 본인의 `TEMP_SAVED` 아닌 결재 수정 | 400 / AP011 |
| **없는 `approvalNo` 삭제** | **404 / AP001** ← D11. **before는 `200/true`였다.** 보고서에 명시 |

**S8. 응답 형식 동등성**
**P2에서 캡처한 기준선**과 대조한다. 성공 경로의 JSON **구조·값이 동일**해야 한다.
- 상세 조회 / 목록 5종 / 삭제(`ResponseMessage<Boolean>`) / 결재 처리 응답의 `ApproverDTO`
- 무첨부·첨부 2건 기안
- **예외는 D11 1건뿐**이다. 그 외 차이가 나오면 회귀다

---

## 11. 실행 순서

```
P0. 실측                                    ✅ 완료 (§3)
P1. 결정 D1~D11                             ✅ 완료 (§7)
      ↓
P2. 변경 전 응답 기준선 캡처 (S8 대조용)      ← 사용자 (bootRun 상태에서 성공 경로 응답 저장)
      ↓
P3. CommandService 4개 메서드 시그니처 + 검증 4곳
      ↓
P4. Controller 4개 메서드에 getCurrentMemberId() 전달
      ↓
P5. compileJava + compileTestJava + bootRun + 검색 확인 5종
      ↓
P6. 수동 검증 S0~S8                          ← 사용자
      ↓
P7. 보고서 + AGENTS.md·CLAUDE.md 갱신 + 정정 4건
      ↓
P8. 커밋 + 푸시 (코드 / 문서 분리)            ← 작업 완료
```

> **P3을 P4보다 먼저 하는 이유**: 서비스 시그니처가 바뀌면 Controller가 컴파일 에러로 **빠짐없이 드러난다.**
> 반대로 하면 어느 호출부를 놓쳤는지 컴파일러가 알려주지 않는다. R2(`resaveTempSaved` 호출부 2곳)의 안전장치다.
>
> **P2가 필요한 이유**: S8의 "이전과 동일"은 비교 대상이 있어야 판정된다.
> 변경 후에 캡처하면 아무것도 증명하지 못한다.

---

## 12. 착수 전 체크 (Claude Code)

1. §7 D1~D11 **확정 완료** — 확정본대로 진행. 임의 변경 금지
2. **`ErrorCode.java`를 수정하지 않았는가?** (D6 — AP003 재사용, 신규 0건)
3. **`POST /approvals`의 신규 채번 분기에 검증을 넣지 않았는가?** (R7 — 넣으면 모든 신규 기안이 죽는다)
4. **`POST /approvals`의 검증이 `if (initialStatus == PROCESSING)` 바깥에 있는가?**
   (R8 — 안쪽이면 `"임시저장"` 경로가 열린 채 검증을 통과한다)
5. **`resaveTempSaved` 호출부 2곳을 모두 고쳤는가?** (R2)
6. 검증이 **디스크 파일 삭제·자식 벌크 삭제보다 앞**에 있는가? (§4 공통 원칙 4)
7. 권한 판단에 **`approvalDTO.getMemberId()`를 쓰지 않았는가?** (D2)
8. §5의 **코드 2파일 외에 손댄 파일이 없는가?** (`git diff --stat`으로 확인)
9. 🚫 `JwtAuthorizationFilter`·`ApprovalStatus`를 **수정하지 않았는가?**
10. 예상 못 한 상황은 추측 말고 **중단·보고**

### 중단하고 보고할 상황

- 이 문서에 없는 요구사항이 필요하다고 판단될 때
- 호출부·영향 범위가 명세에 적힌 것보다 넓을 때
- 기존 동작을 바꿔야만 컴파일이 통과할 때
- 소유자가 아닌 사람에게 삭제 버튼이 노출되는 것이 확인될 때 (S0-h 사전 조건)
- 이 메시지와 문서가 어긋날 때

추측으로 메우지 마라.

---

## 13. 작업 원칙 리마인더

- **Surgical**: 이 문서에 없는 요구사항을 창작하지 않는다. `DELETE` 상태 가드(D5)·`createChildren`(D8)·
  읽기 경로 인가(작업 E)처럼 눈에 보여도 범위 밖이면 **그대로 두고 기록만** 한다
- **경계**: §2 표가 최종 권위. 애매하면 후속으로 미룬다
- **파일이 진실의 원천**: 진행 중 발견한 결정은 이 문서 또는 보고서에 반영한다
- **가정 금지**: 참조 문서도 실물과 다를 수 있다(`leave-pattern.md` §9의 `ErrorCode` 인자 순서).
  **명세도 실물과 다를 수 있다** — v2 정정이 그 사례다. 어긋나면 **실물이 정답**이고, 명세를 고친다
- **프론트 관찰의 한계**: 프론트 리포는 리팩토링 커밋이 하나도 없는 **2024-05-31 스냅샷**이고
  죽은 라우트·오타 URL·에러 삼킴이 확인됐다. 화면에 없는 것이 **불필요한 기능이라는 뜻이 아니다.**
  프론트 관찰은 **"지금 되던 게 안 되지는 않는다"(회귀 부재)** 에만 쓰고,
  **"이것이 옳은 정책이다"의 근거로 쓰지 않는다.** API는 이 프론트 전용도 아니다
- **자동화 테스트 없음**: 새로 만들지 않는다. 검증 = `compileJava` + `compileTestJava` + `bootRun` + **수동 API**
- **자격증명 취급**: 보고서는 origin에 푸시된다. 토큰 전문·비밀번호·해시 실값을 남기지 않는다
- **작업 완료 = 커밋 + 푸시.**
