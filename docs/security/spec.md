# 보안 결함 정리 — Spec

> 작성: 2026-07-31 / 이동: 2026-08-12 (`docs/security/approval/spec.md` → `docs/security/spec.md`,
> 작업 B 착수 시 D10 — 작업 A 산출물(`tasks/01-write-authz.md`·`reports/01-write-authz-report.md`)은
> 원래 위치 `docs/security/approval/`에 그대로 둔다)
> 선행: 전자결재 도메인 리팩토링 7단계 완료 (`docs/refactoring/completed/approval-domain.md`)
> 근거: 완료 보고서 §4 🔴 / `reports/07-controller-report.md` §6-2 / 2026-07-31 실측 (§3)
> **이것은 리팩토링이 아니다.** 새 작업 스트림이며, `plan.md`는 이번 규모에 불필요하여 본 문서에 흡수했다.

---

## 1. 배경

전자결재 도메인 리팩토링(2026-07-24 ~ 07-30, 7단계)이 종료됐다. spec이 열거한 결함
`[A][A-잔여][A-확장][B][C][D][F][G][H][I][J][K-persist][L][M]`은 전부 닫혔다.

그러나 **단계 7 검증 중, spec에 열거되지 않은 권한 결함이 쓰기 경로에 남아 있음**이 확인됐다.
완료 보고서 §3이 그 사실을 이렇게 기록했다.

> "spec 결함 목록에 없으니 없는 문제"로 읽지 말 것. 이 리팩토링은 열거된 결함을 닫았을 뿐이며,
> 열거 자체가 완전했다는 보장은 없다. 인증·권한 경계는 엔드포인트 단위로 다시 훑어야 한다.

이번 작업은 그 훑기의 결과다. **결재 도메인의 쓰기 엔드포인트 5종 중 4종에 호출자 신원 검증이 없다.**

---

## 2. 발견 경위 — 열거가 왜 불완전했나

spec `[A-확장]`은 "회수 API에 인증 정보가 없다"를 지적했고 단계 1·7이 그것을 닫았다.
**그러나 같은 지적이 나머지 쓰기 API에는 적용되지 않았다.**

단계 7의 검증 시나리오(S0~S9)는 사칭 차단을 **조회·기안 경로에서만** 확인했다.
그래서 `PUT /approvers`의 신원 미검증이 체크리스트 밖의 추가 확인에서야 드러났고,
이번 조사에서 **같은 계열 3건이 더** 나왔다.

완료 보고서 §6의 권고가 이 작업의 방법론이다.

> 앞으로 인증을 건드리는 작업은 **"쓰기 엔드포인트 전수 × 제3자 토큰"** 을 기본 항목으로 둘 것.

---

## 3. 실측 — 쓰기 엔드포인트 전수 (2026-07-30 · 07-31)

호출자는 **기안자도 지정 결재자도 참조자도 아닌 제3자**의 유효한 토큰이다.

| # | 엔드포인트 | 인증 추출 | 신원 검증 | 실측 결과 |
|---|---|---|---|---|
| 1 | `POST /approvals` **신규 채번 분기** | ✅ | (해당 없음) | 정상 — 기안자 = 토큰 사번 |
| 2 | `POST /approvals` **기존번호 분기** | ✅ | ❌ | **200.** 남의 임시저장을 그 사람 이름으로 상신 |
| 3 | `PUT /approvals/{approvalNo}` 재임시저장 | ✅ (**권한 판단에** 미사용) | ❌ | **200.** 남의 초안 제목·본문·양식·결재선·참조선 전량 덮어쓰기 |
| 4 | `PUT /approvals/{approvalNo}/status` 회수 | ✅ | ✅ **AP008** | 정상 (단계 1·7에서 해결됨) |
| 5 | `PUT /approvers/{approverNo}` 결재 처리 | ❌ | ❌ | **200.** 승인 시 문서 완결, 반려 시 즉시 종료 |
| 6 | `DELETE /approvals/{approvalNo}` | ❌ | ❌ **상태 검증도 없음** | **200.** `PROCESSING` 문서가 실제로 삭제됨 |

**5종 중 4종이 열려 있다.**

### 3-1. 공통 성격 — 감사 기록 위조

셋 다 "누가 했는가"가 실제와 다르게 남는다.

- **#5**: `Approver` 행의 `memberId`는 이미 지정 결재자 것이고 `approve()`는 상태·일시만 바꾼다
  → 누가 호출해도 **결백한 결재자가 승인한 것으로 기록**된다
- **#2·#3**: `createChildren`이 `approvalDTO.getMemberId()`(= 호출자)로 기안자 행(`_apr000`)을 다시 만든다
  → **한 문서 안에서 기안자가 둘로 갈린다** (§3-2)

### 3-2. #3의 실측 원문 — 기안자 불일치

`2026-ims00014` (기안자 123 김동환, `TEMP_SAVED`)에 제3자(240501629 이진아) 토큰으로 PUT.

| 필드 | PUT 전 | PUT 후 |
|---|---|---|
| `approval.memberId` (기안자) | 123 김동환 | **123 김동환** (유지) |
| `approver[0].memberId` (`_apr000`, order 0, APPROVED) | 123 김동환 | **240501629 이진아** |

최상위는 `modifyDraft`가 건드리지 않아 유지되고, `_apr000`은 `createChildren`이 DTO의
호출자 사번으로 새로 만든다. **두 값의 출처가 갈린다.**

프론트는 `approver[0].memberId`로 기안자를 판정하므로(`ApprovalDetail.js:121`),
**화면과 서버가 서로 다른 사람을 기안자로 본다** — 호출자에게 회수 버튼이 뜨고,
누르면 서버가 최상위 `memberId`와 비교해 AP008로 막는다.

### 3-3. 심각도를 올리는 요소 — 식별자가 완전히 추측 가능하다

- `approvalNo` = `{연도}-{양식3자}-{5자리 순번}`, 양식 코드 7종은 `GET /approvals/forms`로 **누구나 조회**
- `approverNo` = `{결재번호}_apr{순번3자리}`
- 404(없음) / 400(이미 처리)로 응답이 갈려 **열거 결과를 구분**할 수 있다

**유효한 토큰 하나로 전사 결재를 열거하고, 승인·반려·수정·삭제할 수 있다.**

### 3-4. 측정 방식에 대한 기록

실측을 Claude Code에 맡기려 했으나 **사이버보안 안전장치가 인증 우회 재현을 공격으로 오탐**해
차단됐다. 사용자 수동 실행(Postman)으로 전환했다 — 분담표의 기본값이다.

`DELETE`는 되돌릴 수 없으므로 **검증 전용 문서를 새로 만들어** 수행했고,
상태(`PROCESSING`)를 삭제 **전에** 조회로 확정한 뒤 실행했다.

---

## 4. 작업 분할

발견된 결함이 도메인과 처방 성격에 따라 갈린다. **A는 이번 명세의 대상이고, B는 병행 대상이다.**

| 작업 | 내용 | 도메인 | 상태 |
|---|---|---|---|
| **A** | **쓰기 경로 권한 경계 4건** | `approval/**` | **완료** — `docs/security/approval/tasks/01-write-authz.md` |
| **B** | 비밀 정보 노출 차단(`jwt.key`·DB 계정 평문 / 응답 `password` **9지점** / 로그 **13지점**) + 비밀번호 경로 2종(`resetPassword`·`updateOwnPassword`) 인가 | `resources/`, `member/**`, `auth/**`, `approval/dto`(1파일), **`commute/**`** | **이번 명세** — `tasks/02-secret-exposure.md` (구 B+C 통합, `commute` 도메인 편입 — 이유는 해당 문서 §2·D13) |
| **E** | **읽기 경로 인가 — 상세 조회·파일 다운로드** | `approval/**` | 후속 · **정책 결정 선행** (§4-4) |
| D | 등재만 — 저장형 XSS, 인증 실패 200, CORS 전역 개방, 상태값 불일치 외 | — | §4-3 |

### 4-1. 작업 B (전반부) — `jwt.key`·비밀번호 경로. A의 신뢰 기반이지만, 실질 긴급도는 낮다

```
application.yml:44   jwt.key: ‹평문 커밋됨›
application.yml:45   jwt.time: 86400000   (24h, 블랙리스트 없음)
```

**백엔드·프론트 두 리포 모두 Public이다.** 이 키를 아는 사람은 임의 사번·임의 `role` 토큰을
위조할 수 있고, 그러면 작업 A가 넣을 `approver.getMemberId() != memberId` 검증은
**위조된 사번과 비교하게 된다.** 즉 **인가 검증은 인증의 무결성 위에서만 성립한다.**

> ⚠ **다만 배포된 인스턴스가 없고 배포 예정도 없다**(2026-07-31 확인).
> 공격 대상이 존재하지 않으므로 **실질 위험은 0**이며, 성격은 "배포 전에 갚아야 할 부채"다.
> → **A와 병행할 이유는 없다. A 완료 후 착수한다.**

**A가 무의미해지는 것은 아니다.** 키를 모르는 정상 사용자의 우회를 막고 감사 기록을 지킨다.
다만 **"신원 검증을 도입했다"는 기술은 이 전제 위에서만 참**이며, 보고서에 명시해야 한다(§8 R1).

같은 묶음의 다른 한 건은 성격이 다르다. `PUT /resetPassword/{memberId}`(`MemberController:142~158`)는
`@PathVariable` 사번을 그대로 써서 `encode("0000")`으로 저장한다. **호출자 == 대상 검증도, role 검증도 없다.**
어떤 인증 사용자든 남의 계정을 탈취할 수 있다 — 배포 시 파급은 A보다 넓고, 처방은 몇 줄이다.

작업 B의 결정 항목: 키 로테이션 시 **발급된 모든 토큰이 즉시 무효**가 되고(만료 24h, 블랙리스트 없음),
**git 히스토리에 남은 키는 파일 수정으로 지워지지 않는다.**

### 4-2. 작업 B (후반부, 구 작업 C) — 응답 `password` 노출 + 로그 위생

완료 보고서 §4 🔴 2는 `GET /approvals/members*`의 `password` 노출을 지적했다.
그러나 조사 결과 **같은 결함이 결재 도메인 밖에도 있다.**

> ⚠ **정정 (2026-08-12, `tasks/02-secret-exposure.md` v3 확정).** 아래 4지점은 초안 시점의
> 일부 목록이다. 이후 `commute/**`(3경로)와 `getTokenInfo`가 추가로 확인돼 **총 9지점**이 됐고,
> 전수 목록·라인 번호·기준선 실측은 `tasks/02-secret-exposure.md` §3-2에 있다. 이 절의 4지점
> 표는 **역사적 기록으로만 남긴다.**

| 지점 | 내용 |
|---|---|
| `ApprovalController:140·147` | `approval.dto.MemberDTO` — `getPassword()` 직렬화 |
| `MemberController:176~188` | `GET /members/{memberId}` — 인가 없음, `member.dto.MemberDTO` |
| `MemberController:373~409` | `GET /showAllMembersPage` — `ShowMemberDTO`에도 `getPassword()` 존재 |
| `ChatRoomController:35` | `List<MemberDTO>` 반환 |

**결재 쪽만 막아도 해시는 계속 나간다.** 부분 조치는 "고쳤다"는 오해만 만든다.
또한 응답 `password` 제거는 **응답 JSON 구조 변경**이라, 응답 불변을 지켜온 A와 검증 성격이 다르다.

함께 닫을 로그 위생 (완료 보고서 §4 ⚪의 "해시 3곳"은 과소집계였다):

> ⚠ **정정 (2026-08-12).** 아래는 초안 시점 집계다. 확정 집계(활성 11건 + 도달 불가 2건 + `toString()`
> 마스킹 5파일)는 `tasks/02-secret-exposure.md` §4 [B4]에 있다.

- **평문 비밀번호 4곳** — `CustomAuthenticationProvider:35`, `MemberController:317`(신규 비밀번호 2개 + 현재),
  `MemberController:477`, `MemberService:272`
- **전 사원 해시 일괄 출력** — `MemberService:191·193` (`findAll()` 전체, 한 요청에 2회)
- **토큰 전문 3곳(활성)** — `CustomAuthSuccessHandler:38`, `TestController:27·45`
  (+ `CustomAuthSuccessHandler:38` 주석에 실제 형태 JWT 하드코딩)
- **SQL 로그** — `show-sql=true` + `hibernate.sql=debug`로 `password` 컬럼이 SELECT 목록에 노출

### 4-3. 작업 D — 등재만

| 항목 | 지점 | 비고 |
|---|---|---|
| 저장형 XSS | `ApprovalDetail.js:198` `dangerouslySetInnerHTML` + 서버 sanitize 0건 | 의존성 추가·허용 태그 정책·기존 데이터 처리가 붙는다 |
| 인증 실패가 HTTP 200 | `JwtAuthorizationFilter:115~124`, `CustomAuthFailureHandler:20~66` | `setStatus()` 미호출. 본문에만 `{"status":401}`. **작업 B 검증에서 재확인** — 만료·미제시 토큰이 200으로 나가 캡처·검증 시 성공으로 오판할 수 있다 (`tasks/02` R16) |
| CORS 전역 개방 | `HeaderFilter:14` `Allow-Origin: *`, `WebConfig`의 origin 제한보다 앞선다 | 작업 B 파괴적 실측(E-1·E-2) 응답 헤더에서 **재확인** — `tasks/02` §3-5 |
| 죽은/무효 권한 코드 | `Position.java:11` `@PreAuthorize` (엔티티라 무효 + SpEL 오류), `JwtTokenInterceptor` 도달 불가 | `JwtTokenInterceptor`의 도달 불가는 작업 B에서 **직접 확인**됐다 — `WebConfig`가 `@Bean`으로 객체만 만들고 `addInterceptors()` 오버라이드가 코드베이스 어디에도 없다 (`reports/02` §4) |
| `TestController` 잔존 | `/test`·`/getMemberInfo`·`/getToken` — 프로덕션 소스 | 작업 B는 토큰 전문 로그 2곳만 제거했다. 엔드포인트 제거는 미착수 (`tasks/02` D12) |
| `TestController`가 비-ADMIN 요청에 403이 아니라 500을 반환 | `@PreAuthorize` 거부 시 `AccessDeniedException`이 `GlobalExceptionHandler`의 catch-all에 걸린다 | **실측 확인** — MEMBER 토큰으로 `GET /test` → `STATUS: 500`. 이 때문에 작업 B는 `@PreAuthorize` 대신 명시적 코드 검증을 택했다 (`tasks/02` D3-1) |
| **결재 처리 기능 정지** | 서버는 영문 Enum 응답, 프론트는 한글 비교 | §5 |
| **`insite` 집계의 무성 0건 의심** | `InsiteRepository:34·37`이 `'처리 중'`·`'대기'` **한글 리터럴**로 비교한다. 단계 1 이후 컬럼값은 `PROCESSING`·`PENDING`이다 | **단계 1.5 `[M]`과 동일 계열.** `insite`는 쓰기 0건이라 작업 A 범위 밖이나, DB 실측 필요 |
| `GET /showAllMembersPage`가 무인증 (전 사원 90명 조회 가능) | `roleLessList`(`JwtAuthorizationFilter:57`)에 문자열이 **그대로 등재돼 있고**, `{memberId}` 같은 자리표시자가 없어 실제 URI와 완전 일치로 매칭된다 | **토큰 없이 200 실측 확인**(`tasks/02` R11 · P0 기준선 `06`). 🚫 `roleLessList`를 고쳐 닫으려 하지 말 것 — 인증 추가는 **작업 E**의 정책 결정에 속한다 |
| `ModelMapper`의 `setAmbiguityIgnored(true)` 전역 영향 | `config/BeanConfig.java:29` | 매핑이 틀려도 예외 없이 진행된다. `resetPassword`가 `DTO → Entity → save()`로 전체를 덮어쓰는 경로에 걸려 있다. 작업 B S7에서 **유실 없음이 실측 확인**됐으나 구조적 위험은 남는다 (`tasks/02` R6) |
| `CommuteController`가 응답 맵에서 원본 컬렉션 키(`member`/`members`/`notice`)를 제거하지 않는 구조 | `CommuteController.java` 다수 지점 | Controller가 `getName()`만 꺼내 쓰면서 원본을 맵에 남겨 컬렉션 전체가 응답에 실린다. `tasks/02` D13 — **작업 B는 필드(직렬화)만 막고 키 구조는 그대로 뒀다** |
| `CommuteController:232~234` 부서 전체 PII 대량 출력 | `GET /corrections` 부서별 분기에서 `responseMap.forEach(println)` | `password`는 작업 B의 `@ToString(exclude)`로 닫혔으나, **부서원 전원의 이름·주소·연락처·이메일**이 콘솔에 남는다. 비밀 정보가 아니라 작업 B 범위 밖으로 판정 (`reports/02` §4) |
| 프로필 이미지가 CWD 의존 **상대 경로**로 저장 | `FRONT-LOGIN/public/img`를 가리키는 상대 경로. `file.upload-dir`(`C:/login/`)을 쓰지 않는다 | `bootRun` 실행 위치에 따라 **리포 안에 사용자 업로드 파일이 쌓인다**(작업 B 검증 중 `final_clone2/` 생성·삭제). 후속 과제 둘: 저장 경로 교정 / `.gitignore` 등재 — 작업 B는 D2로 `.gitignore` 무변경 확정 (`reports/02` §4) |
| 이월분 | `[K]` 재시도, `receivedAll` 미구현, `finalApproverDate` 의미, `[E]`, tx↔디스크 원자성, 테스트 부재 | 완료 보고서 §4 |

> **작업 B(`3e2db66`·`4c2b50b`) 이후 갱신.** 위 항목 중 `showAllMembersPage` 무인증·CORS 전역 개방·
> `TestController` 500·`JwtTokenInterceptor` 도달 불가는 **작업 B 검증에서 실측으로 재확인**됐다.
> 나머지는 코드 대조 수준의 등재이며 실측되지 않았다 — **이 구분을 유지할 것.**

### 4-4. 작업 E — 읽기 경로 인가 (신설)

작업 A의 명세 리뷰 중, 같은 훑기에서 **읽기 경로에도 인가가 없음**이 확인됐다.

| 지점 | 내용 |
|---|---|
| `ApprovalController:56~61` | `GET /approvals/{approvalNo}` — 기안자·결재자·참조자 여부를 **묻지 않는다.** `memberId`를 아예 받지 않고 본문 전문·결재선·참조선·첨부 목록을 반환한다 |
| `ApprovalController:154~172` + `ApprovalFileService:123~153` | `GET /approvals/files` — `fileSavename`만 있으면 내려준다. 권한 검사 0줄. savename은 UUID라 추측 불가하지만 **위 상세 조회 응답에 그대로 실린다** |

→ §3-3의 "결재번호가 완전히 추측 가능하다"가 **쓰기뿐 아니라 열람에도 그대로 적용된다.**
유효한 토큰 하나로 전사 결재의 본문과 첨부를 열람할 수 있다.

**작업 A에 넣지 않는 이유**: "누가 결재를 열람할 수 있는가"는 **정책 결정**이다
(기안자 + 결재자 + 참조자만? 부서장은? 감사 목적 열람은?). 그 결정에 따라 목록 조회와
상세 조회의 동작이 함께 바뀌므로 별도 명세가 필요하다.

> 이 항목을 등재하는 이유는 §1의 경계 그 자체다 — **"열거에 없으니 없는 문제"로 읽히는 것을 막는다.**

---

## 5. ⚠ 확인된 사실 — 결재 처리 기능이 현재 프론트에서 정지 상태다

```
서버 응답   ApprovalQueryService:185·242   .name()      → "PENDING" / "PROCESSING"
프론트      ApprovalDetail.js:129          === '대기'   → 항상 false
            ApprovalDetail.js:222·265      {canApproveOrReject && ...}  → 미렌더
```

**2026-07-31 실측**: 관리자 계정의 결재 수신함에서 사원이 상신한 결재 상세를 열어도
**승인·반려 버튼이 없다.** 상세 조회 응답의 `approverStatus`는 `"APPROVED"`·`"PENDING"`,
`approvalStatus`는 `"TEMP_SAVED"` — 전부 영문이다.

단계 1의 Enum 전환(2026-07-24)이 2024-05-31 시점 프론트와 어긋난 결과다.

> **완료 보고서 §5-5 정정 대상.** 그 문서는 이를 "목록 상태 컬럼 표시 매핑 문제"로 기록했으나,
> 같은 원인이 `canApproveOrReject`를 항상 false로 만든다. **표시 문제가 아니라 기능 정지다.**

이 사실이 작업 A에 미치는 영향 둘.

1. **검증을 화면으로 할 수 없다.** 검증은 전부 API 직접 호출(Postman)로 수행한다
2. **403 도입의 화면 회귀 위험이 0이다.** 정상 UI에서 `PUT /approvers`에 도달할 경로가 없다

---

## 6. 목표

### 6-1. 작업 A의 목표

결재 도메인의 **모든 쓰기 엔드포인트가 호출자 신원을 검증**한다.
`Approval.withdraw(memberId)` 선례를 나머지 4종에 적용해 권한 경계를 균질하게 만든다.

### 6-2. 성공 기준

| 지표 | Before | After |
|---|---|---|
| 신원 검증이 있는 쓰기 엔드포인트 | 5종 중 **1종** (회수) | **5종 전부** |
| 제3자 토큰의 쓰기 성공 | 4종에서 200 | **전부 403 / AP003** |
| `ErrorCode.APPROVAL_UNAUTHORIZED` (AP003) | 선언만, 사용처 0건 | **활성** |
| 신규 ErrorCode | — | **0건** (AP003 재사용) |
| 권한 판단의 입력 | DTO 필드(`approvalDTO.getMemberId()`) 또는 없음 | **메서드 파라미터로 명시 전달** |
| `_apr000`의 `memberId` | 재저장 시 호출자로 덮임 | 기안자와 항상 일치 |
| 기존 API 응답 JSON | — | **구조·값 불변** (실패 경로 추가 + **예외 1건**: 없는 번호 삭제 `200 → 404` — `tasks/01` D11) |

추가로:
- `compileJava` + `compileTestJava` + `bootRun` 통과 유지
- 정상 경로(본인·지정 결재자) 동작 **불변** — 이것이 최우선 비회귀 항목이다

---

## 7. 범위 외 (Out of Scope)

- **프론트엔드 변경** — 리포가 다르고, 리팩토링 커밋이 하나도 없는 2024-05-31 스냅샷이다.
  403을 성공 모달로 표시하는 문제(`ApprovalAPI.js:211~214`에 `throw` 없음)는 **기록만** 한다
  (AP004·AP005도 지금 같은 경로로 삼켜진다 — 신규 결함이 아니다)
- **결재 비즈니스 정책 변경** — 결재선 규칙, 순서 강제, 삭제 가능 상태 조건
- **`DELETE`의 상태 가드** — 후속 (`tasks/01` D5)
- **DB 스키마 변경**, 새 기능, 자동화 테스트 신규 작성
- **`auth/**` · `member/**`** — 작업 B·C 소관
- 🚫 **`JwtAuthorizationFilter`의 `roleLessList`** — 8번째 원소의 쉼표 오기 때문에 어떤 URI에도
  매칭되지 않고, **결과적으로 `/approvals`가 인증 필요 상태로 유지되고 있다. 현재 동작이 옳다.**
  "고치면" `/approvals`가 무인증으로 열려 훨씬 위험해진다. **파일을 열지 않는다**
- 🚫 **`ApprovalStatus.description` 필드와 `from()`의 한글 매칭 분기** — 프론트가 `"임시저장"`·
  `"처리 중"`을 보낸다. 제거하면 **기안이 전부 실패**한다
- 🚫 **DTO의 Enum 타입 전환** — Jackson 기본 역직렬화는 `name()`만 매칭한다. 봉인

---

## 8. 비기능 요구사항

- 자동화 테스트가 없다. 검증은 `compileJava` + `compileTestJava` + `bootRun` + **수동 API**
- **권한 검증은 "쓰기 엔드포인트 전수 × {소유자, 제3자}" 매트릭스**로 확인한다.
  한 방향만 보면 안 된다 — 제3자 차단만 보고 정상 경로를 놓치면 기능이 죽는다
- **검증 계정 3개**가 필요하다: 기안자 A / 지정 결재자 Z / 무관한 제3자 B
- 보고서는 origin에 푸시된다. **토큰 전문·비밀번호·해시 실값을 남기지 않는다.**
  응답 인용 시 `"password": "‹생략›"`

---

## 9. 참조

| 문서 | 용도 |
|---|---|
| `docs/refactoring/completed/approval-domain.md` | §4 이월 목록 — 이 작업의 출발점 |
| `docs/refactoring/approval/reports/07-controller-report.md` | §6-2 제3자 승인 실측 원문 |
| `docs/refactoring/approval/spec.md` | `[A-확장]`이 왜 회수 API에만 적용됐는지 |
| `docs/reference/leave-pattern.md` | ⚠ §9 `ErrorCode` 예시는 실물과 인자 순서가 다르다. 실물은 `(int status, String code, String message)` |
