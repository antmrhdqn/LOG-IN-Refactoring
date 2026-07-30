# 단계 7: Controller 슬림화 (Task 명세 · 확정본)

> 작성: 2026-07-30 / **v4 — D1~D15 전부 확정. 수동 검증 S4 실측으로 D9 예측 정정**
> 선행: 단계 1·1.5·2·3·4·5·6 완료·커밋·푸시 (`2446c73` 코드 / `43e3a61` 문서, origin/main 동기화)
> 근거: `spec.md` [I][A-확장][H][J], `plan.md` §5(컨트롤러는 얇게), `leave-pattern.md` §7,
> `stage6_to_7_handover.md` §2·§3·§4, `06-command-query-report.md` §6-1 S8·§8 관찰 1~14
> 조사: 2026-07-30 Claude Code 정적 조사 (플랜모드, 수정 0건) — 결과는 §3
> **리팩토링 마지막 단계.** 종료 문서 작성까지 포함한다(D12).
>
> **v4 정정 (수동 검증 S0~S9 실측 반영 — 2026-07-30)**
> - **D9의 예측이 빗나갔다.** `page=`(빈 문자열)은 400이 아니라 **200 · 0페이지**다.
>   Spring의 `AbstractNamedValueMethodArgumentResolver`가 **빈 문자열을 `defaultValue`로 대체**한다
>   (바인딩 실패가 일어나지 않는다). **코드 결함이 아니며**, 500이 사라진 목적은 달성됐다. §5·§9 R5·§11 S4 정정
> - **S4에서 기존 데이터 결손 1건 발견** — `2024-con00002`가 `getApproval`에서 404/AP004.
>   **단계 7 회귀가 아니다**(`getApproval` 무변경, 06 보고서 §4 R6-b가 예고한 현상).
>   범위 밖이므로 고치지 않고 완료 보고서 §4 🟠에 기록
>
> **v3 정정 (착수 시 plan mode 보고분 반영 — 2026-07-30)**
> - **§5 `[J]`에 "진입 추적 로그"가 빠져 있었다.** 이모지·`System.out.println`·노이즈 주석만 열거해
>   `log.info("****컨트롤러 들어왔어")` 류가 명시 목록 밖이었다. **사용자 확정 = 제거(로그 범위 B).** 본문 보강
> - **쓰기 성공 로그 처방 확정**: DTO 덤프 2건은 **`result.getApprovalNo()`로 축약**해 2줄 유지 (완전 제거 아님)
> - **QueryService 로그 유지 지점 명시**: L224 · L333 은 원문 유지, L250 · L269 는 제거
> - §11 검색 #4 주의 추가(`SecurityContextHolder`는 import 줄도 매칭), §10-2에 `completed/` 부재 반영
>
> **v2 변경 (2026-07-30 프론트 DevTools 실측 반영 — §3-7)**
> - **D1 = 분기 A 확정.** 프론트는 `memberId` 헤더를 **보내지 않는다**(목록·기안 3요청 실측)
> - `결재 수신함` = **`fg=received`** 확정 → R7 가드가 정상 경로를 건드리지 않음이 실측으로 확인됨
> - §11 S0(사칭 차단 검증) **활성화**, §0 게이트 해제

---

## 0. 착수 상태

**착수 가능.** 미확인 코드 0건, 미확정 결정 0건.

| 확인 항목 | 상태 | 결과 |
|---|---|---|
| 워킹 트리 | ✅ | 클린. HEAD = `43e3a61` |
| `ApprovalController.java` 엔드포인트별 라인 | ✅ | 12개 / 253줄 (§3-1) |
| `SecurityContextHolder` 중복 | ✅ | Controller 4곳, 패턴 3종 (§3-2) |
| `@RequestHeader("memberId")` | ✅ | **2곳뿐** — 목록·기안 (§3-2) |
| `System.out.println` | ✅ | **12건** = Controller 5 + QueryService 7 (§3-3) |
| `getApprovalList` switch `default` | ✅ | **없음** → NPE 경로 2개 (§3-4) |
| `MemberDTO.toString()` password | ✅ | **포함** — 로그 유출 확정 (§3-5) |
| `src/test` 파손 | ✅ | 2개 파일 615줄 (§3-6) |
| ErrorCode 여유 번호 | ✅ | AP012 (이번 단계 **미사용**) |
| **프론트가 `memberId` 헤더를 보내는가** | ✅ | **보내지 않는다** — 목록·기안 3요청 실측 (§3-7) → **D1 = 분기 A** |
| `결재 수신함`의 `fg` 값 | ✅ | **`fg=received`** (§3-7) |

---

## 1. 목표

`ApprovalController`(253줄)의 역할을 **요청 파싱 → 인증 정보 추출 → 서비스 위임 → 응답 구성**으로 한정한다.
동시에 spec.md가 단계 7 소관으로 지정한 `[I][A-확장][H][J]`를 해결한다.

**이번 단계의 성격은 앞 단계들과 다르다.** Stage 6까지는 비즈니스 로직을 옮기는 일이었다.
Stage 7은 **인증 경로를 건드린다.** 무성 실패가 아니라 **장애와 보안 결함**이 실패 양식이다.
따라서 착수 게이트(D1)와 전 API 재검증(§11 S1)이 이 단계의 본체다.

### 성공 기준

| 지표 | Before (현재) | After (목표) |
|---|---|---|
| `ApprovalController` 라인 수 | 253줄 | **140~155줄** (D4 — spec 원지표 100~120 개정) |
| Controller 내 비즈니스 로직 | `limit = 10` 정책 상수 | **0** |
| `SecurityContextHolder` 등장 | **4곳 / 패턴 3종** | **1곳** (`getCurrentMemberId()` 내부) |
| `@RequestHeader("memberId")` | **2곳 (인증 우회)** | **0** |
| `System.out.println` (approval 하위) | **12건** | **0** |
| 이모지 로그 | 다수 | **0** |
| 비밀번호 로그 출력 | **`log.info("memberDTO" + memberDTO)`** | **제거** |
| 미지원 `fg` 값 응답 | **500 / C999 (NPE)** | **400 / C001** |
| 빈 `page` 값 응답 | **500 (NumberFormatException)** | **200 · 0페이지** (v4 정정) |
| `dounloadFile` 메서드명 | 오타 | `downloadFile` (매핑 경로 불변) |
| `compileTestJava` | 실패 (참조 불가 2파일) | **통과** (D15, 분리 커밋) |

> **응답 JSON 구조는 변경되지 않는다** (spec 성공 기준). 바뀌는 것은 실패 경로의 상태 코드
> (미지원 `fg`: 500→400, 빈 `page`: 500→**200 · 0페이지** — v4 정정)와
> **헤더로 사번을 위조했을 때의 결과**뿐이다.
> 프론트는 헤더를 보내지 않으므로(§3-7) **정상 사용 경로의 응답은 값까지 동일하다.**

---

## 2. 7 vs 범위 밖 경계 (확정)

애매하면 이 표가 이긴다.

| 항목 | 단계 | 근거 |
|---|---|---|
| `[I]` `@RequestHeader("memberId")` 제거 (2곳) | **7** | spec "단계 7에서 해결". D1 = 분기 A 확정 |
| `[A-확장]` 회수 API 인증 정보 | **7** | spec. 이미 SecurityContext에서 추출 중 → **헬퍼로 정리만** |
| `[H]` `getCurrentMemberId()` 헬퍼 추출 (4곳 → 1곳) | **7** | spec |
| `[J]` 이모지 로그 · `System.out.println` 정리 | **7** | spec. **Controller + QueryService 양쪽** (D10) |
| `MemberDTO` password **로그** 제거 (QueryService L394) | **7** | `[J]`의 일부 (D14-a) |
| `dounloadFile` → `downloadFile` 개명 | **7** | `04-file.md` D3 |
| R7 `receivedAll`/미지원 `fg` NPE 가드 | **7** | `[G]`(500 방치 → BusinessException 위임)의 잔여 적용 (D2) |
| `limit = 10` 정책 상수를 QueryService로 이관 | **7** | plan §5 "비즈니스 로직 완전 제거" (D8) |
| `page` 파라미터 `String` → `int` | **7** | 요청 파싱은 Controller 본연 역할 (D9) |
| 깨진 `src/test` 2파일 삭제 | **7** | D15 — **단, 커밋 분리** |
| 리팩토링 종료 문서 작성 | **7** | D12 |
| — 이하 범위 밖 — | | |
| `[K]` 재시도 | **이후 별도 작업** | D3. tx 바깥 진입점 필요 → 독립 단계 |
| `receivedAll` **실제 구현** | 범위 밖 | 기능 추가 (spec Out of Scope) |
| `finalApproverDate` 계산 방식 교정 | 범위 밖 | D11. 응답 **값** 변경 + 표시 정책 |
| `MemberDTO` **응답 JSON**의 password 제거 | 범위 밖 | D14-b. "API 응답 불변" 원칙 → 종료 문서 권고 |
| `getForm`의 `RuntimeException` | 범위 밖 | D13. 정상 전파되는 예외 → `[G]` 대상 아님 |
| `auth/**` 전체 | **금지** | §6 — 특히 `roleLessList` (R2·§9) |
| `enums/ApprovalStatus.java`의 `description` | **절대 금지** | 인계 §3-1. 제거하면 **기안이 전부 실패** |
| DTO Enum 전환 (D7-b) | **봉인** | 인계 §3-2 |
| 프론트엔드 · DB 스키마 · 새 기능 | 범위 밖 | spec |

> **판단 규칙**: Stage 7은 **Controller에서 빼는 일**과 **`[J]` 로그 정리**만 한다.
> QueryService를 여는 것은 D2·D8·D10 세 항목 때문이며, **그 세 지점 외의 라인은 원문 그대로 둔다**(R9).

---

## 3. 조사 결과 (2026-07-30, Claude Code 정적 조사)

### 3-1. Controller 라인 구성 — 상위 3개가 절반

| # | 엔드포인트 | 메서드 | 줄 수 |
|---|---|---|---|
| — | package·import·필드·생성자 | — | 38 |
| 1 | `GET /approvals/forms` | `selectFormList` | 8 |
| 2 | `GET /approvals/forms/{formNo}` | `selectForm` | 6 |
| 3 | `GET /approvals/{approvalNo}` | `selectApprovalByNo` | 8 |
| **4** | `GET /approvals` | `selectApprovalList` | **44** |
| 5 | `PUT /approvals/{approvalNo}/status` | `updateApprovalstatus` | 11 |
| **6** | `PUT /approvals/{approvalNo}` | `updateApprovalTemp` | **35** |
| **7** | `POST /approvals` | `insertApproval` | **35** |
| 8 | `PUT /approvers/{approverNo}` | `updateApprover` | 11 |
| 9 | `DELETE /approvals/{approvalNo}` | `deleteApproval` | 7 |
| 10 | `GET /approvals/members/{memberId}` | `selectMember` | 6 |
| 11 | `GET /approvals/members` | `selectAllMembers` | 7 |
| 12 | `GET /approvals/files` | `dounloadFile` | 21 |

**#4·#6·#7 = 114줄로 본문의 절반.** 사유는 전부 동일하다 — 인증 추출 블록 + 로그 + Map/DTO 세팅.
나머지 9개는 이미 6~21줄로 바닥에 가깝다. → **D4 지표 개정의 근거.**

### 3-2. 인증 추출 4곳 · 패턴 3종

| 라인 | 메서드 | 형태 |
|---|---|---|
| 79 | `selectApprovalList` | `Authentication` 변수 경유 + **헤더 fallback** |
| 119 | `updateApprovalstatus` | 한 줄 축약형 (`// TODO: Stage 7에서 제거` 주석 있음) |
| 143 | `updateApprovalTemp` | `Authentication` 변수 경유, **헤더 분기 없음** |
| 176 | `insertApproval` | `Authentication` 변수 경유 + **헤더 fallback** |

`@RequestHeader("memberId")`는 **L72(목록)·L164(기안) 2곳뿐**이다.
→ **회수·재임시저장은 이미 SecurityContext 전용으로 동작 중이다.** D1 판단의 간접 증거(§5 [I]).

### 3-3. `System.out.println` 12건 (정정)

| 파일 | 라인 | 건수 |
|---|---|---|
| `ApprovalController.java` | 97, 103, 106, 167, 238 | **5** |
| `ApprovalQueryService.java` | 251, 270, 282, 361, 363, 366, 368 | **7** |
| `ApprovalCommandService.java` | — | **0** |

**서러게이트 페어(이모지) 라인 7줄** — Controller 103·117·130·166·167·202·238.
`System.out.println` 12건과 **교집합이 있다**(103·167·238은 이모지를 품은 println).
두 숫자를 나란히 적을 때는 별개 집합이 아님을 명시한다.

> 조사 보고 본문은 "Controller 4 / QueryService 8"로 적었으나 **라인 목록은 5 + 7**이다.
> 실물 재확인 결과 **5 + 7 = 12**가 맞다. 합계는 동일. 검색 기준선은 **12(5+7)** 로 확정한다.

### 3-4. R7 — NPE 진입 경로가 **2개**다

`ApprovalQueryService.getApprovalList` L277~371:

- `case "receivedAll"`은 주석 + `break`만 있고 본문이 없다 (L297~301)
- **`switch`에 `default` 절이 없다** (L356)
- `case "received"`만 조기 `return`한다 → 가드에 도달하지 않는다
- 나머지는 L358 `approvalPage.getTotalPages()`로 낙하

→ **`fg=receivedAll`뿐 아니라 `fg=zzz`·`fg=`(빈 문자열)도 전부 500/C999.**
`fg` 자체는 `@RequestParam` 필수라 누락 시엔 400.

> **이 사실이 D2를 단순하게 만든다.** `receivedAll` 화면의 존재 여부와 무관하게 가드는 정당하다.
> 화면이 있으면 500이 400이 되고(둘 다 실패인 것은 같다), 없으면 오타 방어가 공짜로 붙는다.

### 3-5. `MemberDTO` password 유출

| 항목 | 결과 |
|---|---|
| Lombok `@Data`/`@ToString` | 없음 — `toString()`은 **수동 작성** (L185~203) |
| password 포함 | **포함** (L190 `", password='" + password + '\''`) |
| 값 주입 | `getMember` L379~392가 `member.getPassword()`를 3번째 인자로 전달 |
| **로그 유출** | `ApprovalQueryService:394` `log.info("memberDTO" + memberDTO)` → **확정** |
| **응답 유출** | `getPassword()` getter 존재 + `MemberDTO` 직렬화 → 응답 JSON에도 실린다 |

→ 로그는 이번에 제거(D14-a). **응답 노출은 범위 밖**이며 종료 문서에 보안 권고로 남긴다(D14-b).

### 3-6. 그 외 (기록만 — 손대지 않는다)

| 항목 | 사실 | 처리 |
|---|---|---|
| `roleLessList` 오기 | `JwtAuthorizationFilter:57` 8번째 원소가 `"/announces/{ancNo}, /approvals"` — 쉼표가 따옴표 안. `contains()` 완전 일치이므로 **어떤 URI에도 매칭되지 않아** `/approvals`는 인증 필요 상태로 유지된다(=현재 동작이 옳다) | **절대 금지** (R2) |
| 템플릿 원소 | 같은 리스트의 `"/members/{memberId}"` 등도 매칭 불가 | 기록만 |
| `JwtTokenInterceptor` | `WebConfig`에 빈 등록만 있고 `addInterceptors` 오버라이드가 리포 전체에 0건 → **죽은 코드 확정** | 기록만 (`auth/**`) |
| `@EnableWebMvc` | `WebConfig:14` — Boot 자동설정을 끈다 | 기록만 |
| `direction == "ASC"` | QueryService L327 참조 비교. `|| direction.equals("ASC")`가 뒤에 있어 결과는 정상 | 기록만 (R9) |
| `@Tag` 누락 | #10·#11 (`selectMember`·`selectAllMembers`) | **추가하지 않는다** — 줄만 늘고 spec 근거 없음 |
| `@RequestMapping` | L23에 값 없이 부착. 경로는 메서드마다 전체 표기 | 기록만 — 묶으면 `/approvers/{approverNo}`가 어긋난다 |
| 프론트 위치 | 리포 외부 (`WebConfig:53` `allowedOrigins("http://localhost:3000")`) | D1은 DevTools로만 확인 가능 |

### 3-7. 프론트 실측 (2026-07-30 DevTools · 사용자 수행) — **D1 게이트 해제**

`localhost:3000` → `localhost:8080` 요청 3건의 Request Headers 원문 확인.

| 요청 | 화면 | `memberId` 헤더 | `authorization` |
|---|---|---|---|
| `GET /approvals?fg=given&page=0&title=&direction=DESC` | 결재 상신함 | **없음** | `Bearer …` 있음 |
| `GET /approvals?fg=received&page=0&title=&direction=DESC` | 결재 수신함 | **없음** | `Bearer …` 있음 |
| `POST /approvals` (`multipart/form-data`) | 기안/임시저장 | **없음** | `Bearer …` 있음 |

**확정된 사실 넷.**

1. **프론트는 `memberId` 헤더를 보내지 않는다.** 세 요청 모두 브라우저 표준 헤더 + `authorization` 뿐이다.
   → `selectApprovalList`·`insertApproval`은 실사용에서 **항상 `memberIdstr == null` 분기**를 탄다.
   즉 **`@RequestHeader` 파라미터는 정상 클라이언트가 쓰지 않는 순수한 공격 표면**이다. → **D1 = 분기 A**
2. **전역 인터셉터가 헤더를 붙이지 않는다.** axios 공통 헤더 구조가 있었다면 세 요청 전부에 나타났을 것이다.
3. **`cookie` 헤더가 없다.** 세션 없이 토큰만으로 인증한다 — `WebSecurityConfig`의 `STATELESS`와 일치.
4. **`결재 수신함` = `fg=received`.** 목록 파라미터 4종(`fg`·`page`·`title`·`direction`)이 그대로 실려 온다
   (`title=`은 **빈 문자열**로 전송). → R7 가드가 정상 경로를 건드리지 않음이 실측으로 확인됐다(R3).

> **미확인 1건 (기록만)**: `receivedAll`을 호출하는 화면의 존재 여부는 여전히 확정되지 않았다.
> 다만 §3-4에서 NPE 경로가 2개임이 확인되어 **D2는 이 결과와 무관하게 정당하다.**
> 종료 문서에는 "호출 화면 존재 여부 미확인, 호출 시 400"으로 적는다.

---

## 4. 목표 구조

```
approval/
├─ controller/
│   └─ ApprovalController.java          (수정: 인증 추출 1곳화, 헤더 제거, 로그 정리, 개명)
├─ service/
│   ├─ ApprovalCommandService.java      ✗ 무변경 (금지)
│   ├─ ApprovalQueryService.java        (수정: R7 가드 · limit 상수 · 로그 정리 — 3지점만)
│   ├─ file/ApprovalFileService.java    ✗ 무변경 (금지)
│   └─ generator/ApprovalNoGenerator.java ✗ 무변경 (금지 — D3, 재시도 없음)
├─ entity/ enums/ dto/ repository/       ✗ 전부 무변경 (금지)
```

**신규 파일 없음. ErrorCode 추가 없음(AP012 미사용).**

### 4.1 Controller 목표 형태

```java
// 엔드포인트 12개 — 각 본문은 위임 1~3줄
// 인증 추출은 아래 한 곳뿐
private int getCurrentMemberId() {
    return Integer.parseInt(SecurityContextHolder.getContext().getAuthentication().getName());
}
```

- `Authentication` 지역 변수 · `int memberId = 0;` 초기화 · 헤더 fallback 분기 **전부 제거**
- `getCurrentMemberId()`는 **Controller private 메서드**로 둔다 (D7)

---

## 5. 결함별 처리

### `[I]` `@RequestHeader("memberId")` 백도어 — ✅ **분기 A 확정 (D1)**

**결함의 정확한 성격** — 조사로 확정됐다. spec은 "인증 시스템 무력화"로 적었으나,
`roleLessList`가 `/approvals`를 실제로 매칭하지 못하므로(§3-6) **토큰 없이는 컨트롤러에 도달조차 못 한다.**
따라서 실제 피해는 **"유효한 토큰 + 남의 사번 헤더 → 남의 결재함 열람"** 이라는 수평적 권한 상승이다.
`06-command-query-report.md` §6-1 S8이 이미 실측해 놨다 — `received` 3건(토큰) vs 5건(헤더 241811),
`receivedRef` 0건(토큰) vs 7건(헤더 240401001). **다른 사람의 목록이 그대로 반환됐다.**

**게이트 결과 (§3-7)**: 프론트는 목록·기안 어디에도 `memberId` 헤더를 보내지 않는다.
→ 두 엔드포인트는 실사용에서 이미 SecurityContext 경로로만 동작 중이다.

**처방 (분기 A)**
- **L72·L164의 `@RequestHeader(... "memberId" ...) String memberIdstr` 파라미터를 제거**한다
- 두 메서드의 `int memberId = 0;` + `if (memberIdstr == null) { ... } else { ... }` fallback 분기를
  **`int memberId = getCurrentMemberId();` 한 줄로 대체**한다
- 회수·재임시저장(L119·L143)도 같은 헬퍼를 쓰도록 정리한다 → `[H]`

> **프론트가 헤더를 보내더라도 안전하다.** `@RequestHeader` 파라미터를 지우면 Spring은 들어온 헤더를
> **조용히 무시**한다(400 아님). 따라서 이 변경은 프론트 배포와 **순서 의존성이 없다.**
>
> **보조 증거**: 회수·재임시저장 2개 엔드포인트는 애초에 헤더 백도어가 없고(§3-2) 정상 동작 중이다.
> 프론트가 토큰만으로 사번을 서버에 맡기는 구조라는 것이 두 경로에서 일치한다.

---

### `[H]` + `[A-확장]` 인증 추출 중복 → 헬퍼

4곳 · 패턴 3종(§3-2)을 `getCurrentMemberId()` **한 곳**으로 모은다.
`[A-확장]`은 Stage 1에서 `Approval.withdraw(memberId)`가 생기고 Controller가 이미 추출 중이므로
**헬퍼로 정리만 하면 닫힌다** (L119의 `// TODO: Stage 7에서 제거` 주석도 함께 제거).

**구현 (D5·D6·D7 확정)**
- `authentication.getName()` 파싱을 유지한다 — S8에서 검증된 경로다.
  `TokenUtils.getTokenInfo()`는 principal을 `DetailsMember`로 캐스팅해 인증 내부 구조에 결합되며 미검증 → **채택 안 함**
- **인증 부재 처리를 새로 만들지 않는다.** 필터가 컨트롤러 도달 전에 401을 내므로 `authentication == null`은
  도달 불가 경로다. `AP012`를 신설하지 않는다. **도달 불가 분기에 에러 처리를 창작하지 않는다**(§14 Surgical)
- 위치는 **Controller private 메서드** — spec `[H]`가 "헬퍼 메서드(`getCurrentMemberId()`)"로 문자 그대로 지정.
  공통 유틸·`ArgumentResolver`는 타 도메인 컨트롤러까지 파급되므로 범위 밖

---

### `[J]` 이모지 로그 · `System.out.println` 정리 (D10)

**대상 (v3 확정 — 로그 범위 B)**: `System.out.println` 12건 전부 + 이모지 로그 전부 +
**이모지가 없는 진입 추적 로그까지**. (v2는 마지막 항목이 명시 목록에서 빠져 있었다)

**Controller**
- 이모지 로그·`System.out.println` 전부 (103·117·130·166·167·202·238)
- **진입 추적 로그** — `log.info("폼 목록 조회 controller 들어왔다")`, `log.info("****컨트롤러 들어왔어")`,
  `log.info("현재 pageNo : " + pageNo)` 류 (L44·L73·L98·L132)
- 노이즈 주석: `//기안자사번`, `//현재 사용자의 인증 정보 가져오기`, `//인증 정보에서 사용자의 식별 정보 가져오기`
- **DTO 덤프 2건 → 결재번호만 남긴다** (완전 제거 아님):
  `log.info("결재 기안 성공: " + result.getApprovalNo());`
  `log.info("결재 임시저장 수정 성공: " + result.getApprovalNo());`
  > **왜 남기는가**: `ApprovalCommandService`는 무변경(§6)인데 그쪽 로그는 전환·재저장·처리·삭제만
  > `approvalNo`를 찍고 **신규 기안 경로는 찍지 않는다**(`"첨부파일 비어있음?"`뿐).
  > Controller 로그를 통째로 지우면 **신규 기안의 결재번호가 로그 어디에도 남지 않는다.**
- **유지할 주석**: L189 `//채번, 임시저장 -> 기안 전환 판정, …`, L240 `//fileSavepath 는 …`
  — Stage 6이 남긴 위임 근거 설명이며 노이즈가 아니다
- **유지할 로직 라인**: `approvalDTO.setMemberId(...)`·`approvalDTO.setApprovalNo(...)` 는 **로그가 아니다.**
  명세에 없는 제거를 하지 않는다(§14 Surgical). 관찰 사항이 있으면 보고서에 기록만

**QueryService — 로그 문장만. 로직은 한 줄도 바꾸지 않는다.**
- `System.out.println` 7건 제거 (251·270·282·361·363·366·368)
- println과 **문구가 같은** `log.info` 2건 제거 (L250 · L269)
- **L394 `log.info("memberDTO" + memberDTO)` 제거** (D14-a)
  — `MemberDTO.toString()`이 password를 포함한다(§3-5). 로그 정돈이 아니라 **정보 유출 차단**이다
- **L224 · L333 은 원문 유지** (최종 처리일자 / 결재대기 건수 — 진단 가치가 있다)
- **응답 JSON의 password는 건드리지 않는다** (D14-b, 범위 밖 → 종료 문서 보안 권고 최우선 항목)

**`ApprovalCommandService`의 기존 로그는 무변경** (§6 금지).

---

### R7 — 미지원 `fg` 값 NPE 가드 (D2 = 2-b)

`getApprovalList`의 `switch` 뒤, `approvalPage`를 역참조하기 **전에** 가드를 둔다.

```
switch (flag) { ... }            // 원문 유지 (case 내부 손대지 않음)

if (approvalPage == null) {       // ★ 신설
    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
}

int totalPage = approvalPage.getTotalPages();
```

- **기존 상수 `INVALID_INPUT_VALUE`(C001, 400) 재사용.** 새 ErrorCode를 만들지 않는다
- `case "received"`는 조기 `return`이라 이 가드에 도달하지 않는다 → 결재 대기함 영향 없음
- `given`/`tempGiven`/`receivedRef`는 리포지토리가 `Page`를 반환하므로 non-null 보장
- **`receivedAll`을 구현하지 않는다.** 빈 `case`는 원문 그대로 둔다

> **왜 빈 `Page` 반환이 아닌가**: 미구현을 정상 200으로 위장하면 "조용한 0건"이 된다.
> 이 프로젝트가 단계 1.5에서 가장 크게 데인 실패 양식이며, 그때 얻은 교훈에 정면으로 반한다.

---

### `limit = 10` 정책 상수 이관 (D8)

Controller L90~94의 `condition` Map 조립 자체는 요청 파싱으로 볼 수 있으나,
**`limit = 10`은 정책 상수**이므로 Controller에 있을 이유가 없다.

- Controller: `condition.put("limit", 10);` **제거**
- QueryService: `int limit = (Integer) condition.get("limit");` → **클래스 상수**(`DEFAULT_PAGE_SIZE = 10`) 사용

> ⚠ **두 변경은 한 커밋에서 동시에 해야 한다.** 한쪽만 하면 `(Integer) null` 역참조로
> **목록 5종 전부 500**이 된다 (R4).
> `condition` Map의 나머지(`flag`/`title`/`direction`)와 `getApprovalList` 시그니처는 **불변**이다
> — 전면 교체는 이득 대비 회귀 위험이 크다.

---

### `page` 파라미터 파싱 (D9)

`@RequestParam(name = "page", defaultValue = "0") String page` + `Integer.parseInt(page)`
→ `@RequestParam(name = "page", defaultValue = "0") int page`

- **동작 변경 1건 (v4 정정)**: `page=`(빈 문자열)가 현재 `NumberFormatException` → **500**이었으나,
  전환 후에는 **200 · 0페이지**가 된다. v3까지는 "바인딩 실패 → 400"으로 예측했으나 **틀렸다** —
  Spring의 `AbstractNamedValueMethodArgumentResolver`가 빈 문자열을 **`defaultValue`("0")로 대체**하므로
  바인딩 실패 자체가 일어나지 않는다. `page` 파라미터를 생략한 것과 동일한 결과이며 사용자 영향은 없다.
  → 보고서에 명기 + §11 S4에서 확인
- `direction`·`title`·`fg`는 **불변**

---

### `dounloadFile` → `downloadFile` 개명

- **메서드명만 바꾼다.** `@GetMapping("/approvals/files")` 매핑 경로와 파라미터 3종은 **불변**
  → 프론트 영향 없음
- `fileSavepath`를 받기만 하고 쓰지 않는 현행 유지 (요청 형태 보존 — Stage 6 결정)

---

### 깨진 `src/test` 2파일 삭제 (D15)

| 파일 | 줄 | 참조 |
|---|---|---|
| `src/test/.../approval/service/ApprovalServiceTest.java` | 390 | `ApprovalService` 필드 + 호출 17곳 |
| `src/test/.../approval/controller/ApprovalControllerTest.java` | 225 | `import ApprovalService`, `@MockBean`, stub 3곳 |

- 두 파일은 **컴파일조차 되지 않으므로 검증 가치가 0**이다. 자산을 버리는 게 아니라 잔해를 치운다
- ⚠ **파손 시점을 문서에 인용할 때는 실물을 먼저 확인한다.** 06 보고서 §8 관찰 1은 "Stage 5 시점에 이미 실패"라고
  적었다. 그보다 앞선 시점을 주장하려면 근거 문서가 리포에 **실제로 있는지** 확인한 뒤 인용한다
  (없으면 "Stage 5 이전부터 실패 — 정확한 시점 미확인"으로 적는다)
- **삭제 커밋을 코드 커밋과 분리한다** (R8) — 지금까지 모든 task가 `src/test/**`를 금지해 왔으므로
  diff 리뷰에서 "리팩토링 / 테스트 잔해 정리"가 섞이면 안 된다
- **테스트를 새로 쓰지 않는다** (spec Out of Scope)
- 결재 외 도메인 테스트 17개 파일은 **손대지 않는다**

---

## 6. Scope — 수정 허용 파일

**수정**
- `approval/controller/ApprovalController.java` — **주 대상**
- `approval/service/ApprovalQueryService.java` — **3지점만**: R7 가드(D2) / `limit` 상수(D8) / 로그(D10·D14-a)
- `AGENTS.md` (§10)

**삭제 (별개 커밋)**
- `src/test/java/com/insider/login/approval/service/ApprovalServiceTest.java`
- `src/test/java/com/insider/login/approval/controller/ApprovalControllerTest.java`

**신규 (문서)**
- `docs/refactoring/approval/reports/07-controller-report.md`
- `docs/refactoring/completed/approval-domain.md` (D12)

**금지 (손대지 않음)**
- **`auth/**` 전체** — `JwtAuthorizationFilter`(특히 `roleLessList`)·`WebConfig`·`JwtTokenInterceptor`·
  `WebSecurityConfig`·`TokenUtils`
- `approval/service/ApprovalCommandService.java` — **무변경**
- `ApprovalFileService.java`, `ApprovalNoGenerator.java`
- `enums/**` — **특히 `ApprovalStatus.description`과 `from()`의 한글 매칭 분기**
- `dto/**` (`MemberDTO` 포함 — 응답 password는 D14-b로 이월), `entity/**`, `repository/**`
- `common/error/ErrorCode.java` — **AP012 신설 없음**
- 프론트엔드, DB 스키마, `src/test/**`의 나머지 파일

---

## 7. ErrorCode — 신규 추가 없음

| 상수 | 코드 | 이번 단계 용도 |
|---|---|---|
| `INVALID_INPUT_VALUE` | **C001** (기존, 400) | D2 — 미지원 `fg` 값 |

- **`AP012`는 사용하지 않는다.** 헬퍼의 인증 부재는 도달 불가 경로이므로 코드를 만들지 않는다(D6)
- `ErrorCode` 생성자는 **`ErrorCode(int status, String code, String message)`** 다.
  `leave-pattern.md` §9 예시는 순서가 다르다 — **문서를 믿으면 컴파일이 깨진다**(06-report §8 관찰 2)

---

## 8. 결정 사항

### D1. `[I]` 헤더 제거 — **✅ 확정: 분기 A (즉시 제거)**
프론트 DevTools 실측(§3-7)에서 목록·기안 3요청 모두 `memberId` 헤더가 **없음**으로 확인됐다.
정상 클라이언트가 쓰지 않는 파라미터이므로 제거에 프론트 영향이 없고, 헤더가 오더라도 Spring이 무시한다.
→ **프론트 배포와 순서 의존성 없음.**

### D2. R7 `receivedAll`/미지원 `fg` — **✅ 확정: 2-b (null 가드 → C001)**
§3-4에서 NPE 경로가 2개임이 확인되어 `receivedAll` 화면 존재 여부와 **무관하게** 정당해졌다.
§3-7에서 `결재 수신함 = fg=received`가 확인되어 **가드가 정상 경로에 닿지 않음**도 실측으로 뒷받침됐다.
빈 `Page` 반환(2-c)은 "조용한 0건"이라 **채택 안 함.** 구현(2-d)은 기능 추가로 범위 밖.

### D3. `[K]` 재시도 — **✅ 확정: 제외 (3-a)**
① Stage 7은 인증 경로를 건드려 회귀 위험이 이미 최대치다. 재시도를 얹으면 실패 원인 분리가 안 된다
(Stage 6 §12가 P4를 먼저 둔 논리와 동일). ② persist 전환 + S6 실측으로 **데이터 유실 경로는 이미 없다.**
남은 것은 드문 500이라는 UX 비용. ③ Controller try-catch 재시도는 `leave-pattern` §7·spec `[G]`를
이번 단계가 정면 위반하는 형태가 된다. → **리팩토링 종료 후 독립 단계.**

### D4. Controller 라인 지표 — **✅ 확정: 140~155줄로 개정 (4-c)**
바닥값 추산: 헤더 블록 37 + 엔드포인트 12개 정리 후 ~105 + 헬퍼 4 ≈ **146줄**.
원지표 100~120은 Controller가 **470줄이고 채번·조립을 품고 있던 시절**에 잡은 숫자다.
그 로직은 Stage 5·6에서 이미 빠져나갔고, 남은 것은 성격이 다르다(§3-1).
**`@Tag` 10개를 버리지 않으면 도달 불가.** 컨트롤러 분할(4-b)은 숫자를 위한 분할이라 채택 안 함.
**`spec.md`는 고치지 않는다** — 사후 개정은 이력을 흐린다. 개정 근거를 이 문서와 종료 문서에 남긴다.

> **v3 주의**: plan mode 추산은 **≈150~165줄**로 목표 상한을 넘본다.
> **지표를 여기서 또 개정하지 않는다.** 두 번 연속 개정은 지표를 무의미하게 만든다.
> P6에서 **실측한 숫자를 그대로 보고**하고, 초과 시 종료 문서에 "목표 140~155 / 실적 N줄 / 사유"로 적는다.
> 숫자를 맞추기 위한 압축·분할·`@Tag` 삭제는 **금지**(R10).

### D5. `[H]` 헬퍼 구현 — **✅ 확정: `authentication.getName()` 파싱**
S8에서 검증된 경로. `TokenUtils.getTokenInfo()`는 결합도↑·미검증으로 채택 안 함.

### D6. 헬퍼의 인증 부재 처리 — **✅ 확정: 현행 유지, 신규 코드 없음**
필터가 도달을 막으므로 불가 경로. `AP012` 미사용. 보고서에 잔여로 기록.

### D7. 헬퍼 위치 — **✅ 확정: Controller private 메서드**
spec `[H]`의 문언 그대로. 공통화는 타 도메인 파급 → 범위 밖.

### D8. `condition` Map — **✅ 확정: `limit = 10`만 이관**
Map 조립·시그니처는 불변. 전면 교체는 목록 5종 회귀 위험 대비 이득이 작다.

### D9. `page` 파라미터 — **✅ 확정: `String` → `int`**
동작 변경 1건(빈 문자열 500 → 400)을 보고서에 명기하고 S4에서 확인.

### D10. `[J]` 범위 — **✅ 확정: Controller + QueryService (로그 문장만)**
`System.out.println` 12건(5+7) 전부. `ApprovalCommandService`는 무변경.

### D11. `finalApproverDate` — **✅ 확정: 이월 (기록만)**
`[B]` 이후 "가장 큰 `approverOrder`의 처리일시"가 실제 마지막 승인자와 어긋난다
(S1 실측: order3=21:57:13, order1·2=21:57:14 → 표시는 21:57:13).
응답 **값**이 바뀌고 표시 정책에 걸리므로 종료 문서에 known issue로 남긴다.

### D12. 리팩토링 종료 문서 — **✅ 확정: 작성**
`docs/refactoring/completed/approval-domain.md`.
`completed/error-handling.md`·`leave-domain.md` 선례를 따른다. 내용은 §10-2.

### D13. `getForm`의 `RuntimeException` — **✅ 확정: 기록만**
전환하려면 `FORM_NOT_FOUND` 신설이 필요하고, 이 예외는 **삼켜지지 않고 정상 전파**되므로
`[G]`의 대상이 아니다. 범위 밖.

### D14. `MemberDTO` password — **✅ 확정: 로그만 제거(a), 응답은 이월(b)**
(a) `QueryService:394` 로그 제거 — `[J]` 그 자체.
(b) 응답 JSON의 password 필드는 **spec "기존 API 응답 불변"** 때문에 이번에 건드리지 않는다.
리팩토링 마지막 단계에서 스스로 그 원칙을 깨지 않는다. **종료 문서에 보안 권고 최우선 항목으로 기록.**

### D15. 깨진 `src/test` 2파일 — **✅ 확정: 삭제, 커밋 분리**
컴파일 불가 = 가치 0. 새 테스트는 쓰지 않는다.

---

## 9. 위험 목록

| # | 위험 | 대응 |
|---|---|---|
| **R1** | **`[I]` 제거로 프론트 장애** | **§3-7 실측으로 해소됨** — 프론트가 헤더를 보내지 않고, 보내더라도 Spring이 무시한다. 잔여 위험은 실측하지 않은 화면(전체수신함 등)이 헤더를 쓰는 경우뿐 → **S1에서 전 API를 토큰만으로 재검증**해 닫는다 |
| **R2** 🚫 | **`roleLessList` 오기를 "고치는" 것** — `"/approvals"`가 실제로 roleLess에 들어가면 **토큰 없이 컨트롤러 도달**이 가능해져 `memberId` 헤더가 **진짜 무인증 백도어**가 된다 | **`auth/**` 전체 금지(§6).** 발견해도 읽고 보고만. 수정 제안조차 하지 않는다 |
| **R3** | R7 가드가 정상 경로를 막음 | `case "received"`는 조기 `return`이라 가드 미도달. 나머지 3종은 `Page` non-null 보장. **S2·S3 양방향 확인** |
| **R4** ★ | **`limit` 이관을 한쪽만 하면** `(Integer) null` 역참조 → **목록 5종 전부 500** | Controller 제거와 QueryService 상수화를 **같은 변경으로** 처리. S3에서 페이지 크기 10 확인 |
| **R5** | `page` `int`화로 빈 문자열 응답이 500 → **200 · 0페이지** (v4 정정 — 400이 아니다) | 의도된 동작 변경. 보고서 명기 + S4 |
| **R6** | 로그 정리 중 **UTF-8 깨짐** — 이모지·한글이 섞인 줄을 편집한다 | 편집 후 파일 인코딩 확인. 한글 주석·로그가 깨졌는지 육안 확인 (§11 검색 #8) |
| **R7** | 개명 시 **매핑 경로까지** 바꾸면 프론트 장애 | `dounloadFile` → `downloadFile`은 **메서드명만.** `/approvals/files` 불변. S7 |
| **R8** | `src/test` 삭제를 코드 커밋에 섞으면 diff 리뷰가 흐려진다 | **커밋 분리** (§12 P8) |
| **R9** ★ | **QueryService를 여는 순간 "온 김에" 유혹** — `getForm`의 RuntimeException, `direction == "ASC"` 참조 비교, `receivedAll` 구현, `finalApproverDate`, 응답 password | **§6의 3지점 외 라인은 원문 그대로.** 발견 항목은 보고서 §관찰에 기록만 |
| **R10** | Controller 라인 목표 미달 → 억지 압축 | **D4가 지표 개정 근거다.** `@Tag` 삭제·줄바꿈 압축·컨트롤러 분할 금지. 미달이면 숫자를 보고하고 사유를 적는다 |
| **R11** | **무성 실패** — 인증 경로 변경은 `compileJava`·`bootRun`으로 전혀 검증되지 않는다 | **S1(전 API 토큰 전용 재검증)이 이 단계의 본체다.** 생략 불가 |

---

## 10. 문서 갱신

### 10-1. AGENTS.md (Stage 6 → Stage 7)

**(1) "현재 진행 단계"**

before:
```markdown
### 현재 진행 단계
**단계 6: God Class 분리 (Command / Query)** — `docs/refactoring/approval/tasks/06-command-query.md`
(단계 1·1.5·2·3·4 완료 / 단계 5 결재번호 생성기 분리 완료·커밋·푸시 — `80f60b7`(코드)·`0215951`(문서), origin/main 동기화)
```

after:
```markdown
### 현재 진행 단계
**단계 7: Controller 슬림화** — `docs/refactoring/approval/tasks/07-controller.md`
(단계 1·1.5·2·3·4·5 완료 / 단계 6 God Class 분리 완료·커밋·푸시 — `2446c73`(코드)·`43e3a61`(문서), origin/main 동기화)
```

**(2) 단계 로드맵**

before:
```
6. God Class 분리 (Command / Query) (현재)
7. Controller 슬림화
```

after:
```
6. God Class 분리 (Command / Query) ✅
7. Controller 슬림화 (현재)
```

> 단계 완료 후 "완료된 리팩토링" 절에 `docs/refactoring/completed/approval-domain.md`를 추가한다.

### 10-2. 종료 문서 `completed/approval-domain.md` (D12)

> ⚠ **`docs/refactoring/completed/` 디렉터리 자체가 리포에 없다**(plan mode 확인).
> AGENTS.md "완료된 리팩토링"이 가리키는 `error-handling.md`·`leave-domain.md` **둘 다 존재하지 않는다.**
> → 선례 형식을 따를 수 없으므로 **아래 5개 항목 구성을 그대로** 따른다. 디렉터리는 새로 만든다.
> **AGENTS.md의 죽은 링크 2건은 고치지 않는다**(타 도메인 소관) — 보고서·종료 문서에 **문서 드리프트로 기록만.**

포함할 것:
1. **spec 성공 지표 대비 실적표** (라인 수·상태값·응답 체계·상태 전이·에러 처리) + **미달 항목과 사유**
   (Controller 라인 지표 개정 근거 — D4)
2. 단계별 커밋 해시 일람 (1 ~ 7)
3. 해결한 결함 `[A][A-잔여][B][C][D][F][G][H][I][J][K-persist][L][M]`
4. **미해결 · 이월 목록** — 우선순위 순
   - 🔴 **`MemberDTO` 응답의 password 노출** (D14-b) — 리팩토링 직후 착수 권고
   - 🟠 `receivedAll` 호출 화면 존재 여부 미확인 (§3-7) — 호출 시 400
   - `[K]` 재시도 (D3) / R7 `receivedAll` 미구현 / `finalApproverDate` 의미 어긋남 (D11)
   - `[E]` 기안자 도메인 분리 / tx↔디스크 원자성 / 자식 엔티티 persist 전환
   - `getForm` RuntimeException (D13) / 자동화 테스트 부재
   - `auth/**` 관찰: `roleLessList` 오기 · `JwtTokenInterceptor` 죽은 코드 · `@EnableWebMvc`
   - 문서 드리프트: `leave-pattern.md` §9 ErrorCode 예시
5. 프론트 제약 (인계 §3 — 한글 상태값, DTO Enum 봉인, `ims` 잔존, 영문 Enum 노출)

---

## 11. 검증

### 자동 검증

```powershell
cd final
.\gradlew.bat compileJava
.\gradlew.bat bootRun
```

`compileJava` BUILD SUCCESSFUL + `Started Application` 확인.
**`src/test` 2파일 삭제 후에는 `compileTestJava`도 통과해야 한다.**

```powershell
cd final
.\gradlew.bat compileTestJava
```

> 결재 외 도메인 테스트가 별개 사유로 깨져 있으면 **그 사실만 보고하고 손대지 않는다.**

**검색 확인 (PowerShell 5.1)**

> ⚠ `Select-String`에는 `-Recurse`/`-Include`가 없다. `Get-ChildItem ... -Recurse | Select-String` 형태로 쓴다.

```powershell
cd final
$approval   = Get-ChildItem -Path .\src\main\java\com\insider\login\approval -Filter *.java -Recurse
$controller = Get-ChildItem -Path .\src\main\java\com\insider\login\approval\controller -Filter *.java -Recurse

# 1. System.out.println 0건 (기준선 12 = Controller 5 + QueryService 7)
$approval | Select-String -Pattern "System\.out\.println" -Encoding UTF8

# 2. 이모지 0건 — 서러게이트 페어(BMP 밖 문자)만 매칭한다. 한글은 BMP라서 걸리지 않는다
$approval | Select-String -Pattern "[\uD800-\uDBFF]" -Encoding UTF8

# 3. memberId 헤더 0건
$approval | Select-String -Pattern "RequestHeader" -Encoding UTF8

# 4. SecurityContextHolder — 결과 2줄(import 1 + 헬퍼 1). "1건"은 사용 지점 1곳을 뜻한다
$approval | Select-String -Pattern "SecurityContextHolder" -Encoding UTF8

# 5. 메서드명 오타 0건
$approval | Select-String -Pattern "dounloadFile" -Encoding UTF8

# 6. limit 정책 상수가 Controller에서 사라졌는지 0건
$controller | Select-String -Pattern 'put\("limit"' -Encoding UTF8

# 7. AP012 신설 안 했는지 0건
Get-ChildItem -Path .\src\main\java -Filter *.java -Recurse | Select-String -Pattern "AP012" -Encoding UTF8

# 8. 삭제된 클래스 참조 0건 (src/test 삭제 후 전체)
Get-ChildItem -Path .\src -Filter *.java -Recurse | Select-String -Pattern "ApprovalService" -Encoding UTF8
```

> ⚠ `compileJava`·`bootRun` 통과는 **아무것도 증명하지 않는다**(단계 1.5).
> **이번 단계는 특히 그렇다** — 인증 경로 변경은 컴파일·기동에 전혀 드러나지 않는다(R11).

### 수동 검증 — 시나리오 (사용자 담당)

**S0. `[I]` 사칭 차단** ★핵심 보안 검증
사번 **X**의 토큰으로, `memberId` 헤더에 **타인 사번 Y**를 실어 `GET /approvals?fg=given` 호출
→ **X의 목록이 반환**되어야 한다. Y의 목록이 나오면 **제거 실패.**
`POST /approvals`도 동일하게 확인 → 기안자가 **X**로 기록되어야 한다.

**S1. 전 API 토큰 전용 재검증** ★**이 단계의 본체**
`memberId` 헤더를 **일절 보내지 않고** 12개 엔드포인트 전부 실행. Stage 6 S8과 **동일한 JSON 구조**.
- 양식 목록 / 특정 양식 / 상세 조회 / 사원 조회 / 전 사원 조회 / 파일 다운로드
- 목록 5종 (`given` `tempGiven` `received` `receivedRef` `receivedAll`)
- 기안(무첨부·첨부 2건) / 재임시저장 / 회수 / 결재 처리(승인·반려) / 삭제
- 최종승인일(`finalApproverDate`) · 대기자(`standByApprover`) 표시

**S2. R7 가드** — 양방향
- `fg=receivedAll` → **400 / C001** (현행 500/C999)
- `fg=zzz` → **400 / C001**
- `fg` 누락 → 400 (기존 `@RequestParam` 필수)

**S3. 목록 정상 경로 비회귀 + `limit` 이관 확인** ★
`given` / `tempGiven` / `received` / `receivedRef` **4종 전부 200**,
**페이지 크기 10 · `totalPages`·`totalElements`가 Stage 6와 동일**.
하나라도 500이면 R4(`limit` 이관 한쪽만 반영).

**S4. `page` 파라미터**
- `page` 누락 → 0페이지
- `page=2` → 정상
- **`page=`(빈 문자열) → 200 · 0페이지** (현행 500. 의도된 변경 — D9, **v4 정정**: 400이 아니다.
  Spring이 빈 문자열을 `defaultValue`로 대체한다)
- **`page=2` 등 끝 페이지까지 훑을 것** — 특정 행의 데이터 결손은 첫 페이지에서 드러나지 않는다
  (S4 실측: `2024-con00002` 때문에 `DESC page=2` / `ASC page=0`이 404/AP004. **단계 7 회귀 아님**)

**S5. SecurityContext 전용 경로 비회귀**
회수(`PUT /approvals/{no}/status`)·재임시저장(`PUT /approvals/{no}`)이 헤더 없이 정상 동작.
회수 양방향도 재확인 — 아무도 처리 안 한 건 회수 **성공**, 결재자 1명 승인 후 회수 **400/AP009**.

**S6. `[J]` 로그** — 기동 후 로그 육안 확인
- 콘솔에 **이모지·`System.out.println` 0건**
- **`GET /approvals/members/{memberId}` 호출 후 로그에 password가 찍히지 않는지** ★(D14-a)
- 한글 로그·주석이 **인코딩 깨짐 없이** 출력되는지 (R6)

**S7. 파일 다운로드** — 개명 후 `GET /approvals/files` 매핑 불변, 파일 내용 일치, 없는 파일 404/AP007

**S8. Stage 1.5 비회귀**
결재 대기함·임시저장함 **검색어별 건수**가 Stage 6 S9와 동일 (조용한 0건 없음).

**S9. 사원 조회 응답 기록** (D14-b 근거 수집 — 고치지 않는다)
`GET /approvals/members/{memberId}` 응답 JSON에 `password` 필드가 있는지, 값이 **평문인지 해시인지**
기록. 종료 문서 보안 권고의 우선순위 근거.

---

## 12. 실행 순서

```
P0. D1 게이트 — 프론트 memberId 헤더 확인      ✅ 완료 — 헤더 없음 = 분기 A (§3-7)
      ↓
P1. [H] 헬퍼 추출 (4곳 → getCurrentMemberId 1곳)   ← 동작 변경 없음. 먼저 한다
      ↓
P2. [I] 헤더 제거 2곳 (L72·L164)                   ← fallback 분기까지 함께 제거
      ↓
P3. [J] 로그 정리 (Controller 5 + QueryService 7) + password 로그 제거(D14-a)
      ↓
P4. R7 가드(D2) + limit 상수 이관(D8) + page int화(D9)
      ↓
P5. dounloadFile → downloadFile 개명
      ↓
P6. compileJava + bootRun + 검색 확인 8종 + Controller 라인 수 보고
      ↓
P7. 수동 검증 S0~S9 (§11)                          ← 사용자
      ↓
P8. src/test 2파일 삭제 + compileTestJava           ← 별개 커밋
      ↓
P9. 보고서 + 종료 문서 + AGENTS.md 갱신 + 커밋 + 푸시  ← 리팩토링 완료
```

> **P1을 P2보다 먼저 하는 이유**: 헬퍼 추출은 동작 변경이 없어 회귀 위험이 없다.
> 인증 추출을 먼저 한 곳으로 모아두면 P2에서 손댈 지점이 **1곳으로 줄고**, 문제가 생겼을 때
> "추출 탓인지 헤더 제거 탓인지" 구분할 수 있다. Stage 6 §12가 P4(읽기)를 먼저 둔 것과 같은 논리다.
>
> **커밋 3분할**: ① Stage 7 코드 ② `src/test` 잔해 삭제 ③ 문서(명세·보고서·종료 문서·AGENTS.md).
> Stage 5·6이 코드/문서를 분리한 방식을 따르고, 여기에 D15를 별도로 뗀다.

---

## 13. 착수 전 체크 (Claude Code)

1. **D1~D15가 전부 확정됐는가?** 확정본대로 진행. 더 나은 안이 떠올라도 임의로 바꾸지 말고 **보고**한다.
2. `auth/**`를 열지 않았는가? 특히 **`roleLessList`를 고치지 않았는가?** (R2 — 고치면 무인증 백도어가 된다)
3. `enums/ApprovalStatus.java`의 `description`·`from()` 한글 매칭을 건드리지 않았는가?
   (건드리면 **기안이 전부 실패**한다 — 인계 §3-1)
4. `ApprovalCommandService`·`ApprovalFileService`·`ApprovalNoGenerator`·`dto/**`가 diff에 없는가?
5. QueryService 변경이 **§6의 3지점(R7 가드 / `limit` 상수 / 로그)뿐**인가? (R9)
6. `limit` 이관을 **양쪽 동시에** 했는가? (R4 — 한쪽만이면 목록 5종 500)
7. `dounloadFile` 개명이 **메서드명만**이고 매핑 경로는 그대로인가? (R7)
8. ErrorCode를 추가하지 않았는가? (AP012 미사용 — D6)
9. `src/test` 삭제를 **별개 커밋**으로 분리했는가? (R8)
10. Controller 라인 수를 **실측해 보고했는가?** 목표 미달이면 억지로 줄이지 말고 숫자와 사유를 적는다 (R10)
11. 예상 못 한 상황은 추측 말고 **중단·보고**.

---

## 14. 작업 원칙 리마인더

- **Surgical**: 이 문서에 없는 요구사항을 창작하지 않는다. 도달 불가 분기에 에러 처리를 만들지 않는다(D6).
  눈에 보여도 범위 밖이면 **기록만** — `getForm` RuntimeException, `direction == "ASC"`,
  `finalApproverDate`, 응답 password, `auth/**` 관찰 전부.
- **단계 경계**: §2 표가 최종 권위.
- **파일이 진실의 원천**: 진행 중 발견한 결정은 이 문서 또는 보고서에 반영한다.
- **자동화 테스트 없음**: 새로 만들지 않는다. 검증 = `compileJava` + `bootRun` + **수동 API**.
- **무성 실패 주의**: 이번 단계는 **인증 경로**를 바꾼다. 컴파일·기동은 아무것도 증명하지 않는다.
  **모든 API를 토큰만으로 재검증**해야 한다(S1).
- **단계 완료 = 커밋 + 푸시.** 이번이 마지막 단계이므로 **종료 문서까지** 포함해야 완료다.
