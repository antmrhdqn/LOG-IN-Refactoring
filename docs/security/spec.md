# 보안 결함 정리 — Spec

> 작성: 2026-07-31
> 이동 ①: 2026-08-12 (`docs/security/approval/spec.md` → `docs/security/spec.md`, 작업 B 착수 시 D10)
> 이동 ②: 2026-08-12 (작업 E 완료 후 **평평화**) — 작업 A 산출물도
> `docs/security/tasks/01-write-authz.md` · `docs/security/reports/01-write-authz-report.md`로 옮겼다.
> **`docs/security/` 아래에 도메인 하위 폴더를 두지 않는다** (규칙은 `CLAUDE.md`).
> 이동 ① 시점의 "원래 위치에 그대로 둔다"는 방침은 이동 ②로 대체됐다.
> 갱신: 2026-08-14 (작업 F 완료 — §4-5 신설, §4-3 등재 4건 추가)
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
| **A** | **쓰기 경로 권한 경계 4건** | `approval/**` | **완료** — `docs/security/tasks/01-write-authz.md` |
| **B** | 비밀 정보 노출 차단(`jwt.key`·DB 계정 평문 / 응답 `password` **9지점** / 로그 **13지점**) + 비밀번호 경로 2종(`resetPassword`·`updateOwnPassword`) 인가 | `resources/`, `member/**`, `auth/**`, `approval/dto`(1파일), **`commute/**`** | **완료** — `tasks/02-secret-exposure.md` (구 B+C 통합, `commute` 도메인 편입 — 이유는 해당 문서 §2·D13) |
| **E** | **읽기 경로 인가 — 상세 조회·파일 다운로드** | `approval/**` | **완료** — `tasks/03-read-authz.md` · 정책 확정(D1~D10) 후 착수 (§4-4) |
| **F** | **인증 실패 응답 정상화 (200 → 401)** | `auth/**` | **완료** — `tasks/04-auth-failure-status.md` (§4-5) |
| **05** | **인증 경계 정상화 — `roleLessList` 원소 12 → 4** | `auth/**` | **완료** — `tasks/05-authn-boundary.md` (§4-6). 로드맵 표기는 `G` |
| **06** | 🔴 **`POST /signUp` 무인증 계정 생성 차단** — 임의 `role`(ADMIN) 생성 조건이 대부분 성립한다 | `member/**` + 프론트 리포 | **미착수 · 확정.** 05가 닫지 못했다 — `MemberAPICalls.js:75`에 토큰이 없어 프론트 수정·ADMIN 인가·파괴적 실측이 함께 붙는다 (`tasks/05` D2) |
| D | 등재만 — 저장형 XSS, CORS 전역 개방, 상태값 불일치 외 | — | §4-3 |

> ⚠ **`POST /signUp`은 §4-3(등재만)에 넣지 않는다.** §4-3은 "닫을 계획 없음"의 대장인데
> 이 항목은 **작업 06으로 확정 승격**됐다. 같은 항목이 양쪽에 있으면 다음 세션이 어느 쪽을
> 진실로 읽을지 갈린다 — `GET /showAllMembersPage` 행에서 이미 겪은 사고다(§4-3 ⚠ 각주).

> ⚠ **작업 06 착수 조건 2건** (05 준비 과정에서 확인. 실측 절차에 직결된다)
>
> **① 회원 삭제 API가 없다.** `MemberController`에 `@DeleteMapping` **0건**
> (전 코드베이스 9건 중 `member` 패키지 0건. `NoticeController:39`의
> `/members/{memberId}/notices`는 **공지** 삭제다). `MemberService.deleteMemberById`(**`:218`**)는
> 존재하나 호출자가 `scheduleMemberDeletion`(**`:158`에서 호출** — 퇴직 3년 뒤 예약, 자신은 `:109`에서만
> 불린다) 하나뿐이다. ⇒ 파괴적 실측으로 계정이 생성되면 **DB 직접 삭제**여야 하고,
> 분담표상 **DB 작업은 사용자**다. `member_info` 외 FK 테이블(`transferred_history` 등)과,
> `cascade = PERSIST`로 신규 `Department`가 함께 생겼을 가능성까지 삭제 대상에 넣는다.
> 작업 A의 `DELETE` 실측(§3-4)이 선례이며 그때도 수동 실행이 기본값이었다.
>
> **② 실측용 사번 대역을 미리 정한다.** ⚠ **`999001`·`999002`는 쓰지 말 것** — 05 화면 검증에서
> 채팅 상대 목록에 실재로 떴다(`제3자X`·`제3자`). 착수 시 `GET /showAllMembersPage`로
> 기존 사번을 전수 확인한 뒤 겹치지 않는 대역을 고른다. 실사원과 섞이면 삭제가 위험해진다.

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
| CORS 전역 개방 | `HeaderFilter:14` `Allow-Origin: *`, `WebConfig`의 origin 제한보다 앞선다 | 작업 B 파괴적 실측(E-1·E-2) 응답 헤더에서 **재확인** — `tasks/02` §3-5 |
| 죽은/무효 권한 코드 | `Position.java:11` `@PreAuthorize` (엔티티라 무효 + SpEL 오류), `JwtTokenInterceptor` 도달 불가 | `JwtTokenInterceptor`의 도달 불가는 작업 B에서 **직접 확인**됐다 — `WebConfig`가 `@Bean`으로 객체만 만들고 `addInterceptors()` 오버라이드가 코드베이스 어디에도 없다 (`reports/02` §4) |
| `TestController` 잔존 | `/test`·`/getMemberInfo`·`/getToken` — 프로덕션 소스 | 작업 B는 토큰 전문 로그 2곳만 제거했다. 엔드포인트 제거는 미착수 (`tasks/02` D12) |
| `TestController`가 비-ADMIN 요청에 403이 아니라 500을 반환 | `@PreAuthorize` 거부 시 `AccessDeniedException`이 `GlobalExceptionHandler`의 catch-all에 걸린다 | **실측 확인** — MEMBER 토큰으로 `GET /test` → `STATUS: 500`. 이 때문에 작업 B는 `@PreAuthorize` 대신 명시적 코드 검증을 택했다 (`tasks/02` D3-1) |
| **결재 처리 기능 정지** | 서버는 영문 Enum 응답, 프론트는 한글 비교 | §5 |
| **`insite` 집계의 무성 0건 의심** | `InsiteRepository:34·37`이 `'처리 중'`·`'대기'` **한글 리터럴**로 비교한다. 단계 1 이후 컬럼값은 `PROCESSING`·`PENDING`이다 | **단계 1.5 `[M]`과 동일 계열.** `insite`는 쓰기 0건이라 작업 A 범위 밖이나, DB 실측 필요 |
| ~~`GET /showAllMembersPage`가 무인증~~ | ~~`roleLessList`(`JwtAuthorizationFilter:57`)~~ | ✅ **닫혔다. §4-6으로 이동** (작업 05). 무토큰 요청이 31,852B → **401 / 90B**로 전환됐다 |
| `ModelMapper`의 `setAmbiguityIgnored(true)` 전역 영향 | `config/BeanConfig.java:29` | 매핑이 틀려도 예외 없이 진행된다. `resetPassword`가 `DTO → Entity → save()`로 전체를 덮어쓰는 경로에 걸려 있다. 작업 B S7에서 **유실 없음이 실측 확인**됐으나 구조적 위험은 남는다 (`tasks/02` R6) |
| `CommuteController`가 응답 맵에서 원본 컬렉션 키(`member`/`members`/`notice`)를 제거하지 않는 구조 | `CommuteController.java` 다수 지점 | Controller가 `getName()`만 꺼내 쓰면서 원본을 맵에 남겨 컬렉션 전체가 응답에 실린다. `tasks/02` D13 — **작업 B는 필드(직렬화)만 막고 키 구조는 그대로 뒀다** |
| `CommuteController:232~234` 부서 전체 PII 대량 출력 | `GET /corrections` 부서별 분기에서 `responseMap.forEach(println)` | `password`는 작업 B의 `@ToString(exclude)`로 닫혔으나, **부서원 전원의 이름·주소·연락처·이메일**이 콘솔에 남는다. 비밀 정보가 아니라 작업 B 범위 밖으로 판정 (`reports/02` §4) |
| 프로필 이미지가 CWD 의존 **상대 경로**로 저장 | `FRONT-LOGIN/public/img`를 가리키는 상대 경로. `file.upload-dir`(`C:/login/`)을 쓰지 않는다 | `bootRun` 실행 위치에 따라 **리포 안에 사용자 업로드 파일이 쌓인다**(작업 B 검증 중 `final_clone2/` 생성·삭제). 후속 과제 둘: 저장 경로 교정 / `.gitignore` 등재 — 작업 B는 D2로 `.gitignore` 무변경 확정 (`reports/02` §4) |
| **`AttachmentDTO`가 서버 절대 경로를 응답에 싣는다** | `ApprovalQueryService`의 상세 조회 첨부 매핑 — `fileSavepath`(= `UPLOAD_DIR + FILE_DIR`) | 작업 E D5(응답 축소 안 함)에 따라 유지. **작업 E 이후 관계자만 보게 되어 등급은 내려갔다** (`reports/03` §7) |
| **`finalApproverDate`가 임시저장 문서에 찍힌다** | 상세 조회의 최종 승인일 계산 분기 | 결재선 모양이 같은 `PROCESSING` 문서는 `""`인데 `TEMP_SAVED`는 값이 들어간다. **원인 미확인.** 작업 E 기준선 캡처에서 실측 (`tasks/03` §3-4) |
| **ADMIN·감사 role의 전체 열람** | — | 작업 E D2가 **도입하지 않기로 확정**했다. 부서장·감사 role은 스키마 영향이 있고, ADMIN 예외는 취약점을 role 하나 뒤로 물릴 뿐이다 (`tasks/03` §7 D2) |
| **기안자 본인을 참조선에 등록할 수 있다** | 기안·재임시저장 시 `referencer`에 기안자 사번을 넣으면 그대로 저장된다 | 작업 E S2 스모크에서 관찰. 기능상 무해(열람 판정은 기안자 단계에서 이미 통과)하나 서버가 막지 않는다. **코드 대조 없이 관찰만** (`reports/03` §6-1) |
| **필터 예외 시 스택트레이스 전문 노출** | `CustomAuthenticationFilter`의 `IOException → RuntimeException`이 필터를 빠져나가 Spring 기본 `/error`로 떨어진다 | **작업 F 기준선 실측** — 인증 없이 `POST /login` 하나로 **8~9KB 스택트레이스**(내부 패키지 구조·라이브러리)가 나온다. `@RestControllerAdvice`는 DispatcherServlet 밖 필터 예외에 닿지 않는다 (`tasks/04` §3-3) |
| **만료 토큰과 위조 토큰이 구분되지 않는다** | `TokenUtils.isValidToken()`이 `ExpiredJwtException`·`JwtException`을 삼키고 `false`만 반환 → `jsonResponseWrapper`의 `Token Expired`·`SignatureException` 분기가 **도달 불가능한 죽은 코드** | **작업 F 기준선 실측** — 캡처 `09`와 `10`의 응답 본문이 **바이트 단위로 동일**(해시 `6c000f76`) |
| **로그인 실패 메시지에 의한 계정 열거** | `CustomAuthFailureHandler`가 "존재하지 않는 사용자입니다" / "아이디 또는 비밀번호가 틀립니다"를 구분해 응답한다 | **작업 F 기준선 실측** — 캡처 `14`(62B) ≠ `15`(55B). 작업 F는 **상태 코드를 401로 단일화해 열거 축을 늘리지 않았고**(D5), 본문은 무변경으로 뒀다 |
| **죽은 `ErrorCode` 상수 3건** | `ErrorCode.java:21~23` — `EXPIRED_TOKEN`(M002) · `INVALID_TOKEN`(M003) · `UNSUPPORTED_TOKEN`(M004) | **작업 F 검색 확인 실측** — 401로 선언돼 있으나 **사용처 0건**. 작업 F는 되살리지 않았다 (D3 — 본문 무변경이라 꺼낼 자리가 없다) |
| 이월분 | `[K]` 재시도, `receivedAll` 미구현, `finalApproverDate` 의미, `[E]`, tx↔디스크 원자성, 테스트 부재 | 완료 보고서 §4. **`receivedAll` 미구현은 작업 E에서 400/C001로 실측 확인**됐고, 상세가 목록보다 넓은 이유가 여기에 있다 (`tasks/03` D3) |
| **[정]** 공지 `PUT`/`DELETE` 인가 0건 | `AnnounceController:145` · `:154`. `announce` 패키지 인가 검증 **전수 0건** | 임의 **인증** 사용자가 타인 공지를 수정·삭제할 수 있다. 작업 05는 **인증**만 다뤘다(D3) |
| **[정]** `/wss/chatting` 인증 도입이 구조적으로 불가 | `WebSocketConfig:24` · `Room.js:50` | 브라우저 `WebSocket` 생성자는 커스텀 헤더를 지정할 수 없다. 서브프로토콜·쿼리 토큰·`HandshakeInterceptor` 중 택1 + 프론트 수정이 필요하다. **선례로 승격하지 않는다**(`tasks/05` D6). ⚠ **코드 근거 있음, 실증 없음** — 작업 05 화면 검증에서 방 생성 500으로 핸드셰이크에 도달하지 못했다 |
| **[정]** `WebConfig:26` `addResourceHandler("**")` | 매핑 실패 요청을 정적 핸들러가 삼켜 `throw-exception-if-no-handler-found`를 무력화할 수 있다 | ⚠ **'정적 대조' 등급을 유지한다.** 작업 05 캡처 `13`·`14`·`20`·`21`이 전부 **500 / 84B / `C999`**로 동일해, 정적 핸들러가 삼킨 것인지 `NoHandlerFoundException`인지 **본문으로 구분되지 않았다**(`reports/05` §5-4). 승격하려면 실재 정적 리소스 경로 요청이나 기동 로그 대조가 따로 필요하다 |
| **[정]** `PUT /members/updateProfile/{memberId}` 인가 0건 | `MemberController:275~326` | 인증된 아무 사원이나 남의 부서·직급·입사일을 덮어쓰고 `TransferredHistory`까지 바꾼다. 작업 05가 **인증**은 걸었다(원소가 죽어 있었으므로 원래부터 401) |
| **[정]** `GET /downloadMemberInfo` 인가 0건 | `MemberController:420~479` | 전 사원 사번·이름·이메일·주소·전화번호 엑셀 |
| **[정]** `ProposalApi.js:7·20·33` 토큰 플레이스홀더 | `'BEARER YOUR_TOKEN_HERE'` 하드코딩 (프론트 리포) | **보안 결함이 아니라 작업 F가 드러낸 회귀다** — F 이전엔 200으로 조용히 실패했고 이후엔 401로 실패한다 |
| **[정]** `ErrorCode.NOT_FOUND_ENDPOINT`(404 · `C004`)가 도달 불가 | `ErrorCode.java:11` + `GlobalExceptionHandler:61~68` | 핸들러 없는 경로가 **404가 아니라 500 / `C999`**로 떨어진다(작업 05 캡처 `13`·`14`). **전용 핸들러는 있는데도** 그렇다 ⇒ `NoHandlerFoundException`이 던져지지 않고 catch-all에 걸린다. ⚠ **`M002`~`M004`("죽은 상수 3건")와 같은 행에 묶지 않는다** — 그쪽은 **참조 0건**, 이쪽은 **참조는 있으나 도달 불가**라 처방이 다르다 |
| **[기]** 멀티파트 파트 본문이 UTF-8로 디코딩되지 않는다 | `POST /announces`의 `@RequestPart("announceDTO") String`. 파트에 `Content-Type` charset이 없으면 ISO-8859-1로 읽힌다 | **작업 05 준비 세션 실측**(M3) — `ancNo=26` 등록 시 제목·내용·작성자가 전부 깨져 저장됐다. ⚠ **백엔드 한정.** 프론트 경로(`AncAPICalls.js:32~38`) 재현 여부는 **미판명**이다 — 화면 검증 2번이 판명할 예정이었으나 목록이 뜨지 않아 등록까지 도달하지 못했다(`reports/05` §5-6) |
| **[기]** `POST /signUp`이 필수 파트 누락에 500을 반환 | `MemberController:83`. `consumes` 제약이 없어 핸들러 매핑까지 도달 → `MissingServletRequestPartException` → catch-all → `C999` | **작업 05 실측**(M2). **400이어야 할 자리다.** `POST /announces`는 같은 조건에서 `consumes` 덕에 **415/`C007`**로 끊긴다 — 같은 계열에서 응답이 갈린다. **작업 06에서 함께 본다** |
| **[기]** `PUT /announces/{ancNo}`가 제목·내용만 덮어쓴다 | `AnnounceService.updateAnc:127~149`. `ancWriter`·`ancDate`·`filePath`·`hits`는 기존 값이 재저장된다 | **작업 05 실측**(M4). 수정 API로 작성자 오기를 정정할 수 없다. 기능 결함이며 보안 결함은 아니다 |
| **[화]** ★ 프론트 모듈 레벨 `const headers`가 **로드 시점에 고정**된다 | `MemberAPICalls.js:10` · `AncAPICalls.js:8` · `ChattingAPICalls.js:15` (프론트 리포) | **작업 05 화면 검증 실측** — import 시점 1회 평가라 `/login`에서 로드되면 **`Bearer null`로 고정**된다. ⇒ **무인증 개방이 이 결함을 가려주고 있었고, 05가 경계를 닫으며 드러났다.** 선례 **`S10`**의 근거 (`precedents.md`) |
| **[화]** 공지 목록 렌더링 결함 | 프론트 리포. `App.js:88` `React.jsx: type is invalid` 동반 | **작업 05 화면 검증 실측** — 서버는 **200 / 108KB**를 보내는데 화면은 **0건**이고 페이지네이션이 `-9 … 0`으로 렌더된다. 같은 108KB 요청이 **6회 중복 호출**된다 |
| **[화]** `GET`/`POST /api/rooms/` 500 | 채팅 방 생성·조회 | **작업 05 화면 검증 실측** — `/api/rooms/members`는 200(인증 통과)인데 방 생성이 500이다. **채팅 방 생성 불가.** `/api/rooms/**`는 `roleLessList` 원소였던 적이 없어 작업 05와 무관하다 |

> **작업 B(`3e2db66`·`4c2b50b`) 이후 갱신.** 위 항목 중 `showAllMembersPage` 무인증·CORS 전역 개방·
> `TestController` 500·`JwtTokenInterceptor` 도달 불가는 **작업 B 검증에서 실측으로 재확인**됐다.
> 나머지는 코드 대조 수준의 등재이며 실측되지 않았다 — **이 구분을 유지할 것.**
>
> **작업 E 이후 갱신.** `finalApproverDate` 임시저장 이상·`receivedAll` 400/C001·기안자 참조선 등록은
> **작업 E 기준선 캡처와 스모크에서 실측**됐다. `AttachmentDTO` 절대 경로·ADMIN 열람은 코드 대조 및
> 정책 결정 결과다.
>
> ⚠ `GET /showAllMembersPage` 무인증 행의 "인증 추가는 작업 E의 정책 결정에 속한다"는 서술은
> **더 이상 유효하지 않다.** 작업 E는 `approval/**` 읽기 경로만 다뤘고, `member/**`·`commute/**`의
> 읽기 인가는 **후속 과제로 남아 있다** (§4-4 경계 참조).
>
> **작업 F 이후 갱신.** "인증 실패가 HTTP 200" 행은 **닫혔고 §4-5로 이동**했다.
> 위 신규 4건은 전부 **작업 F의 기준선 캡처·검색 확인에서 실측**된 것이며, 코드 대조 수준의
> 등재와 구분해 읽을 것.
>
> **작업 05 이후 갱신 — 등재 13건 추가. 출처 등급을 표기했다.**
> 이 표는 지금까지 "실측된 것 / 코드 대조 수준의 등재"를 인용문으로만 구분해 왔으나,
> 작업 05분부터는 **행 머리에 등급을 붙인다.** 건수가 늘어 인용문만으로는 어느 행이
> 어느 등급인지 되짚기 어려워졌기 때문이다.
>
> | 표기 | 뜻 | 건수 |
> |---|---|---|
> | **[정]** | **정적 대조** — 코드를 읽어 확인. 실행하지 않았다 | **7** |
> | **[기]** | **기준선 실측** — 작업 05 준비·기준선 캡처에서 실제 응답을 받았다 (M2·M3·M4) | **3** |
> | **[화]** | **화면 검증 실측** — 브라우저에서 관찰했다 (2026-08-21) | **3** |
>
> `C004` 도달 불가는 작업 05 캡처(`13`·`14`)가 **드러냈으나** 판정 근거는 코드 대조(전용 핸들러가
> 있는데도 500)라 **[정]**으로 넣었다. 캡처가 계기인 것과 캡처가 근거인 것은 다르다.
>
> `GET /showAllMembersPage` 무인증 행은 **닫혔고 §4-6으로 이동**했다(취소선으로 자리만 남겼다).
> ⚠ **[화] 3건의 프론트 라인 번호는 별도 리포(`LOG-IN-F-Refactoring`) 기준이며 관찰 시점 값이다.**
> 이 리포의 어떤 검증도 그 드리프트를 잡지 못한다 — **재확인 없이 인용하지 말 것.**

### 4-4. 작업 E — 읽기 경로 인가 ✅ **완료 (2026-08-12)**

작업 A의 명세 리뷰 중, 같은 훑기에서 **읽기 경로에도 인가가 없음**이 확인됐다.

| 지점 | 내용 |
|---|---|
| `ApprovalController:54~61` | `GET /approvals/{approvalNo}` — 기안자·결재자·참조자 여부를 **묻지 않는다.** `memberId`를 아예 받지 않고 본문 전문·결재선·참조선·첨부 목록을 반환한다 |
| `ApprovalController:165` + `ApprovalFileService:125` | `GET /approvals/files` — `fileSavename`만 있으면 내려준다. 권한 검사 0줄. savename은 UUID라 추측 불가하지만 **위 상세 조회 응답에 그대로 실린다** |

→ §3-3의 "결재번호가 완전히 추측 가능하다"가 **쓰기뿐 아니라 열람에도 그대로 적용된다.**
유효한 토큰 하나로 전사 결재의 본문과 첨부를 열람할 수 있다.

**작업 A에 넣지 않은 이유**: "누가 결재를 열람할 수 있는가"는 **정책 결정**이다
(기안자 + 결재자 + 참조자만? 부서장은? 감사 목적 열람은?). 그 결정에 따라 목록 조회와
상세 조회의 동작이 함께 바뀌므로 별도 명세로 분리했다.

#### 착수 후 실측으로 드러난 것 — 초안 시점보다 심각했다

기준선 캡처 20항목(`tasks/03-read-authz.md` §3)에서 **첨부 경로가 초안의 서술을 넘어선다**는 것이
확인됐다.

| 실측 | 내용 |
|---|---|
| 저장명 전수 노출 | `fileSavename=`(빈 문자열)로 요청하면 `resolve("")`가 업로드 디렉터리 자신을 가리키고, `UrlResource.exists()`가 디렉터리에 `true`를 돌려줘 **파일명 목록 전체가 200으로 나갔다** |
| 베이스 경로 이탈 | `fileSavename=..`도 같은 이유로 **베이스 경로 밖을 서빙**했다. `normalize()`는 있으나 포함 검사가 없다 |

→ "savename은 UUID라 추측 불가"가 **사실상 유일한 보호였는데, 그 전제가 성립하지 않았다.**
목록 확보 → 임의 첨부 획득으로 이어지는 결합 경로가 존재했다.

#### 확정된 정책 (D1~D10 — 상세는 `tasks/03-read-authz.md` §7)

| # | 결정 |
|---|---|
| 열람 주체 | **기안자 + 결재자 + 참조자.** role 예외 없음 — **ADMIN도 관계 없으면 차단** |
| 상태 종속 | 관계 존재 기준. 승인·반려·회수 후에도 유지. 단 **`TEMP_SAVED`는 기안자 전용** |
| 목록과의 관계 | 상세가 목록보다 **약간 넓다.** `receivedAll` 미구현으로 결재를 마친 문서가 목록에 없기 때문 |
| 차단 응답 | **404** (상세 AP001 / 파일 AP007). 읽기는 GET이라 열거 비용이 0이므로 존재를 은폐한다. **쓰기 403/AP003은 그대로 유지** |
| 파일 | 문서 열람 권한과 동일. `savename` → `Attachment` 조회 → 소속 결재 → 관계 판정 |
| 응답 축소 | **하지 않는다.** 전부 아니면 전무 |
| 신규 ErrorCode | **0건** |

부서장 열람·감사 role은 스키마 영향이 있어 도입하지 않았다 → §4-3 등재.

#### 결과

코드 3파일(`ApprovalController`·`ApprovalQueryService`·`AttachmentRepository`).
캡처 20항목 전 항목 PASS + 쓰기 스모크·로그 구분 확인.
저장명 열거와 경로 이탈은 **별도 조항 없이** `Attachment` 조회 단계가 구조적으로 닫았다
(요청 파라미터가 파일시스템에 닿지 않는다).

보고서: `docs/security/reports/03-read-authz-report.md`

> 이 항목을 등재했던 이유는 §1의 경계 그 자체였다 — **"열거에 없으니 없는 문제"로 읽히는 것을 막는다.**
> 실제로 착수 후 실측에서 초안이 몰랐던 두 결함이 나왔다.

### 4-5. 작업 F — 인증 실패 응답 정상화 ✅ **완료 (2026-08-14)**

명세 `tasks/04-auth-failure-status.md` · 보고서 `reports/04-auth-failure-status-report.md`

§4-3에 "인증 실패가 HTTP 200"으로 등재돼 있던 항목이다. 작업 B 검증(`tasks/02` R16)이
**"만료·미제시 토큰이 200으로 나가 캡처·검증 시 성공으로 오판할 수 있다"**고 지적했고,
그 지적이 이 작업의 착수 근거가 됐다.

#### 결함

프로덕션에서 `setStatus`를 부르는 곳은 `CustomAuthenticationFilter:43`(400, 이미 올바름)
**단 1곳**이고 `AuthenticationEntryPoint`도 0건이었다 — **아무도 401을 세팅하지 않았다.**

| 지점 | 증상 |
|---|---|
| `JwtAuthorizationFilter` `catch` | 본문에만 `{"status":401,…}`. HTTP 상태는 200 |
| `CustomAuthFailureHandler` | **본문에 401조차 없다**(`{"failType":…}`). 한 단계 더 나쁘다 |

후자 때문에 `Login.js`의 `response.status === 401` 분기는 **한 번도 실행된 적 없는 죽은 코드**였다.

#### 처방 — 코드 2파일, `git diff` +9 / −0

- **[F1]** `JwtAuthorizationFilter` `catch` 진입 직후 — `isCommitted()` 가드 + `SC_UNAUTHORIZED`
- **[F2]** `CustomAuthFailureHandler` — `getWriter()` 호출 **앞**에 `SC_UNAUTHORIZED`

**기존 라인 수정·삭제 0줄. 신규 `import` 0건. `ErrorCode` 사용 0건.**

#### 확정된 정책 (후속 작업이 따를 선례)

- **응답 본문은 건드리지 않는다.** 상태 코드만 바로잡으면 판정이 "본문 해시 동일 + 상태 코드"
  두 숫자로 끝난다
- **인증 실패는 예외 종류와 무관하게 401 단일.** 상태 코드로 계정 존재 여부를 흘리지 않는다.
  프론트가 `=== 401` 하나만 보므로, 403으로 갈래를 내면 그 경우만 조용히 실패한다
- **필터·핸들러에서는 `ErrorCode`/`ErrorResponse`로 위임할 수 없다.** `@RestControllerAdvice`는
  DispatcherServlet 밖에 닿지 않는다 — 같은 기준선 안에서 대조 실증됐다
  (캡처 `07` = `ErrorResponse` C999 90B / `16`·`17` = Spring 기본 에러 8~9KB)
- **`catch`가 커밋된 응답에 도달할 수 있으면 `isCommitted()` 가드를 둔다.** 없으면 `setStatus`가
  조용히 무시되고, 무시됐다는 사실도 남지 않는다

#### 검증

기준선 17항목을 **코드 수정 전에** 캡처하고(`C:\temp\auth-status\`), 처방 후 `-Compare`로 판정했다.
**전 항목 PASS** — 전환 8건(전부 `hashSame = True`, 상태 코드만 바뀜) · 비회귀 9건.
화면 검증에서 `POST /login`이 401로 찍히고 alert은 처방 전과 동일함을 확인했다.

> **R11 정규화 재판정이 실제로 발동했다 — 예상과 반대 방향에서.**
> 명세는 인증 실패 본문(`json-simple`의 `JSONObject`, HashMap 기반)의 키 순서 변동을 우려했으나
> 08~15는 재기동을 건너서도 전부 해시가 동일했고, **정상 응답 쪽(`02` 목록, Jackson 파생 속성)이
> 흔들렸다.** 재판정이 없었으면 FAIL로 오판할 건이었다.

#### 경계

`roleLessList` · 응답 본문 · `CustomAuthSuccessHandler`(휴면 계정 200) ·
`CustomAuthenticationFilter`(400) · `server.error.include-stacktrace` · 프론트엔드는
**전부 범위 밖**으로 두었다. 이 중 스택트레이스 노출·계정 열거·만료/위조 미구분·죽은 `ErrorCode`
3건은 §4-3에 **실측 등급으로 등재**됐다.

> 여기서 범위 밖으로 둔 `roleLessList`가 **작업 05가 됐다**(§4-6).

### 4-6. 작업 05 — 인증 경계 정상화 ✅ **완료 (2026-08-21)**

명세 `tasks/05-authn-boundary.md` (v2) · 보고서 `reports/05-authn-boundary-report.md`

작업 F가 범위 밖으로 남겨 둔 `roleLessList`다. 작업 F는 **인증 실패의 응답 형식**을 고쳤고,
이 작업은 **애초에 인증을 요구하지 않던 경로**를 닫는다. 순서가 이 방향이어야 한다 —
F 이전이었다면 차단이 200으로 나가 캡처가 성공으로 오판했을 것이다.

#### 결함

`JwtAuthorizationFilter:57`의 `roleLessList` 원소 **12개**가 인증 없이 통과시킨다.
판정은 `contains(getRequestURI())` = **완전 문자열 일치 하나뿐**이고, 자리표시자·쿼리스트링·
HTTP 메서드를 보지 않는다. 그 결과 원소 12개가 **성격이 두 갈래로 갈렸다.**

| 갈래 | 원소 | 실태 |
|---|---|---|
| **살아 있던 것 (4)** | `/registDepart` · `/registPosition` · `/showAllMembersPage` · `/announces` | 무토큰 요청이 **실제로 통과**했다 |
| **죽어 있던 것 (4)** | `/members/{memberId}` · `"/announces/{ancNo}, /approvals"` · `/resetMemberPassword/{memberId}` · `/members/updateProfile/{memberId}` | 자리표시자·콤마·경로명 오기라 **어떤 URI와도 매칭된 적이 없다** |

닫은 것은 둘이다.

1. **토큰 없이 전 사원 90명의 이름·부서·직급·입사일·상태가 나갔다** — `GET /showAllMembersPage`
2. **토큰 없이 전사 공지를 등록할 수 있었다** — `POST /announces`. 원소 `/announces` 하나가
   `GET`(`AnnounceController:35`)과 `POST`(`:110`) **두 엔드포인트를 함께 열었고**,
   `announce` 패키지에는 인가 검증이 **전수 0건**이다

#### 처방 — 코드 1파일, `git diff` +9 / −1

**[G1]** `roleLessList` 선언 1줄 재작성 — 원소 **12 → 4** (`/signUp` · `/login` · `/` · `/wss/chatting`)
+ 잔존 사유 주석 8줄.

**판정문(`contains`)은 한 글자도 바꾸지 않았다.** 패턴 매처를 도입하면 죽어 있던 4개가
**되살아나** `/members/{id}`·`updateProfile`이 무인증으로 열린다 — 처방과 정반대가 된다.
신규 `import` 0건, `ErrorCode` **사용 0건**(401 본문은 작업 F가 넣은 `catch` 블록이 만든다).

#### 잔존 4개 — 서로 다른 이유로 남았다

| 원소 | 이유 |
|---|---|
| `/signUp` | 프론트가 이 호출에만 토큰을 붙이지 않는다 → **작업 06** |
| `/login` | 인증 시작점 |
| `/` | 루트 매핑이 없고 `WebConfig:26`과의 상호작용이 불확정. 닫는 실익 0 |
| `/wss/chatting` | 브라우저 `WebSocket`은 커스텀 헤더를 실을 수 없다. **프론트 수정으로도 닫히지 않는다** |

#### 검증

기준선 22항목을 **코드 수정 전에** 캡처하고(`C:\temp\authn-boundary\`), 처방 후 `-Compare`로 판정했다.
**전 항목 PASS.**

- **전환 5건** — 서로 다른 네 종류의 before(200 · 415 · 500 · 500)가 전부 **401 / 90B 한 본문으로
  수렴**했다. REF(캡처 `19`)와 해시 일치 = **필터의 같은 `catch` 블록을 통과했다**는 실증.
  ★ `10`: **31,852B → 90B** · `11`: **107,366B → 90B**
- **비회귀 9건** — 정상 경로 전부 before·after 바이트 동일
- **가설 실증 5건** — 죽어 있던 원소 경로가 before·after 모두 **401 / 90B, 해시 동일**.
  "매칭된 적이 없다"가 숫자로 확인됐다
- **동결 3건** — 잔존 원소 경로(`20`·`21`)가 401로 바뀌지 않았다 = 4개가 살아 있다

> **R11이 두 작업 연속으로 발동했다.** 결재 목록(`05`)이 재기동을 건너며 키 순서만 바뀌어
> `hashSame=False` · `canonSame=True`가 됐다. 작업 F의 `02`와 **같은 엔드포인트**다.
> 정규화 재판정이 없었으면 두 번 다 FAIL 오판이었다.

#### 확정된 정책 (후속 작업이 따를 선례)

**`S10`** — 프론트 회귀 판정은 정적 grep으로 끝나지 않는다. 헤더가 붙는지와 그 값이 유효한지는
다른 층이고, 모듈 최상단에서 조립된 헤더는 로드 시점에 고정된다 (`precedents.md`).

명세가 "닫을 4경로는 회귀 0"을 단정한 근거가 **정적 grep**이었는데, 화면 검증에서 나온
런타임 값은 **`Bearer null`**이었다. 작업 06이 `MemberAPICalls.js:75`를 두고 같은 판단을 한다.

#### ⚠ 확보하지 못한 것 — 2건

| 미달성 | 결과 |
|---|---|
| **화면 검증 2번(한글 공지 등록) 미수행** — 목록이 뜨지 않아 등록까지 도달하지 못했다 | **M3(멀티파트 UTF-8 미디코딩)의 프론트 재현 여부 미판명.** §4-3 등재는 **"백엔드 한정"을 유지**한다 |
| **D6(`/wss/chatting`) 실증 실패** — 방 생성이 500이라 핸드셰이크에 도달하지 못했다 | **"코드 근거 있음, 실증 없음"**으로 등재. 명세 §10이 예고한 공백이며, **선례 승격을 보류한 판단과 일관된다** |

화면 검증은 5항목 중 **3 통과 / 2 실패**이고, **두 실패 모두 401이 아니라 200·500**이라
인증 경계 처방의 회귀가 아니다 — 프론트 렌더링 결함과 채팅 서버 오류로 §4-3에 등재했다.

#### 경계

`POST /signUp` 차단(→ **작업 06**) · `/wss/chatting` 인증 도입 · 공지 `PUT`/`DELETE` 인가 ·
`member/**`·`commute/**` 읽기 **인가** · 응답 필드 축소는 **전부 범위 밖**이다.
이 작업이 다룬 것은 **인증**이고 **인가가 아니다** — 회원·공지 조회에는 결재 같은 "관계"가 없어
사내 그룹웨어에서는 **인증된 사원 전원**이 자연스러운 경계다(`tasks/05` D3).

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

> ⚠ **정정 (2026-08-12, 작업 E S2 스모크 실측).** 위 1번은 **과대 서술이다.**
> **정지한 것은 승인·반려(`canApproveOrReject`)에 한정된다.** 작업 E 검증에서
> **기안(`POST /approvals`)과 회수(`PUT /approvals/{no}/status`)는 화면에서 정상 동작**함을
> 확인했다(`reports/03` §6-1 — S2-1은 화면 임시저장, S2-4는 화면 회수).
>
> "화면을 전부 쓸 수 없다"로 읽으면 후속 작업이 쓸 수 있는 검증 수단을 스스로 버리게 된다.
> **승인·반려만 API 직접 호출이 필요하다.**

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
| **`docs/security/tools/capture-auth-status.ps1`** | 작업 F 전용 개조본. `capture-authz.ps1`에 **POST 본문·원문 Authorization 헤더·상태 코드 판정 그룹**을 추가했다. 판정 그룹이 4종(해시 불변 / 해시+200→401 / shape 동결 / 로그인 성공)이다 |
| **`docs/security/tools/capture-authz.ps1`** | **인가 작업 공용 검증 도구** — 응답 기준선 캡처 · SHA256 대조 · 키 순서 정규화 재판정. 작업 E에서 처음 사용(20항목). 매트릭스(`Get-Matrix`)만 갈아끼우면 다른 작업에 재사용된다.<br>⚠ 토큰(`tokens.ps1`)과 캡처물은 **리포 밖**(`C:\temp\`)에 둔다 — PII·실제 저장명·서버 경로가 들어간다 |
