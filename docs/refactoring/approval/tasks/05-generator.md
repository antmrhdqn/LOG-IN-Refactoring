# 단계 5: 결재번호 생성기 분리 — Task 명세 (결정 확정본)

> 작성: 2026-07-29 / Stage 4(파일 처리 분리) 완료·커밋·푸시·수동검증 종료 시점 기준
> 근거: `spec.md` [K](단계 5에서 해결) · `plan.md` §7(번호 채번 캡슐화) · `leave-pattern.md` §5(합성)·§9(에러코드)
> 결정: §8 **D1~D4 전부 확정**(D1=(ii) Stage 6 동반 close-out / D2=Controller / D3=ErrorCode 미수정 / D4=두 콜사이트 치환)
> 실행 도구: Claude Code (본 확정본을 plan mode로 검토 후 실행). API 시그니처만 초안 → plan mode에서 최종화.

---

## §0. 이 단계의 한 줄 정의

흩어진 채번 로직(결재번호·결재자번호·참조자번호·첨부파일번호·기안자 `_apr000` 포맷)을
`ApprovalNoGenerator`(@Component) **단일 진입점**으로 **동작 보존적으로** 모은다. **채번 "생성" 로직만** 이관 —
결재자/참조자 **DTO 조립 루프 이동은 Stage 6**. **[K] 동시성은 이번 단계에서 해결하지 않는다(D1=(ii))**: 방어물을 넣지 않고
현행을 보존하며, "고칠 지점을 한 곳으로 모으는 것"까지가 이번 단계의 [K] 기여다(실제 close-out은 Stage 6 [F]와 함께).

---

## §1. 대상 코드 (현재 = Stage 4 반영 버전 기준 라인)

> 라인은 인계 시점 기준이며, Claude Code가 repo 현재 파일로 재확인한다.

### ApprovalController.java
- **결재번호 생성 블록** L211–252: `YearFormNo` 조립 → `approvalService.selectApprovalNo(YearFormNo)` →
  `split("-")` → `replaceAll("\\D","")` → `+1` → `String.format("%05d", …)` → `setApprovalNo`. **← [K] 경쟁 지점.**
  - 임시저장이면 `formNo = "ims"` 치환 (L218–221)
  - `lastApprovalNo == null` 이면 순번 1로 시작 (L246–248)
- **결재자번호 루프** (insert) L275–284: `approverNo = approvalNo + "_apr" + %03d`  (L279)
- **참조자번호 루프** (insert) L286–294: `refNo = approvalNo + "_ref" + %03d`  (L290)
- **결재자번호 루프** (재임시저장) L155–164: `approverNo = … + "_apr" + %03d`  (L159)
- **참조자번호 루프** (재임시저장) L166–174: `refNo = … + "_ref" + %03d`  (L170)

### ApprovalService.java
- **`selectApprovalNo(String yearFormNo)`** L744–757: `findLastApprovalNo(...)` 위임(마지막 번호 조회).
  → 유일 호출처는 Controller 결재번호 블록. **Generator로 흡수 → 이 메서드 제거 대상.**
- **기안자 `_apr000`** (insert) L133–142: `senderApprover` 생성 시 `approvalNo.concat("_apr000")` (L134)
- **기안자 `_apr000`** (updateApproval) L378–387: `approvalNo.concat("_apr000")` (L379)
  ⚠️ **이 메서드(updateApproval)는 삭제-후-재생성 [C] 소관 = Stage 6.** 여기선 **포맷 치환만** (§4·D4 참조).

### ApprovalFileService.java
- **첨부파일번호 `_f%03d`** L79–80: `String fileNo = approvalNo + "_f" + %03d;` — 위 `// TODO: Stage 5 …` 주석.
  **Stage 4에서 임시 배치된 것.** → Generator로 흡수, TODO 주석 제거.

### ApprovalRepository.java (변경 없음 — 백킹 쿼리)
- **`findLastApprovalNo`** L51–53: `@Query("SELECT a.approvalNo FROM Approval a WHERE a.approvalNo LIKE %:yearFormNo% ORDER BY a.approvalNo DESC")`.
  Generator가 이 쿼리를 (직접 또는 [K] 방침에 따라 락 버전으로) 사용. **[K] 해결의 핵심 지점.**

### Approval.java (참조 — 변경 없음)
- **`approvalNo`가 `@Id`(PK)** (L23–25). → DB 레벨에서 이미 유니크. [K] 옵션 (a)에 **스키마 변경 불요**의 근거.

### 참조(변경 없음)
- `leave-pattern.md` §5(@Component 합성 주입), §9(에러코드 명명)
- `application.yml`: `generate-ddl: false` (DDL 수동 관리 — 스키마 변경은 수동 스크립트+인간 실행이 필요, plan §DB마이그레이션)

---

## §2. 분석으로 확정된 문제

> **[K] 방침 확정(2026-07-29): D1 = (ii) — 동시성 close-out은 Stage 6(write 경로 persist 전환)와 함께.**
> 따라서 **Stage 5는 [K] 메커니즘(락/재시도)을 구현하지 않는다.** Stage 5의 [K] 기여는 **채번 단일 진입점 확보**(Stage 6가
> 손댈 지점을 하나로) + **현행 동작 100% 보존**이다. 이유: 아래 K-후속 때문에 지금 재시도 골격을 넣으면 **아무것도 못 잡는
> 무성 방어물**(단계 1.5식 함정)이 된다. (§8 D1 참조)

- **[K] 결재번호 동시성 (read-modify-write 경쟁)**: "마지막 조회 → +1 → 포맷 → 저장"이 원자적이지 않다.
  두 기안이 동시에 같은 `lastApprovalNo`를 읽으면 같은 다음 번호를 만든다. → **본 단계 미해결(현행 유지), Stage 6로 이월.**
- **[K-후속 — Stage 6 인계 메모]** `approvalNo`는 PK이므로 **true 중복 행**은 DB가 막지만, 충돌의 **표면화 방식**이
  write 경로에 의존한다. `approvalRepository.save(Approval)`는 assigned String `@Id` + `@Version` 부재 조건에서
  Spring Data JPA가 `isNew()==false`로 판단해 `merge()`로 흐를 수 있고, 그 경우 **중복은 예외가 아니라 기존 행 UPDATE**
  (= 뒤 기안자가 앞 기안자 결재를 조용히 덮어씀)로 나타날 수 있다.
  → **Stage 6가 [F](save→persist/dirty checking) 전환 시, 이 merge 여부를 실측하고 재시도-on-예외가 실효화되는지 확인**해야 한다.
  Stage 5는 이 경로에 의존하지 않으므로 **본 단계 착수의 블로커가 아니다**(가정 없이 현행 보존만 하면 됨). (단계 1.5 교훈: 프레임워크 동작 가정 금지)
- **[K-부수 관찰 — 범위 밖, 기록만]** 백킹 쿼리가 `LIKE %:yearFormNo%`(선행 와일드카드)라 인덱스 미사용 풀스캔이며,
  폼번호가 접두어 관계(예: `A01` vs `A011`)면 시리즈 간 교차 매칭 여지가 있다. **[K] 동시성과 별개의 잠재 결함**으로,
  본 단계에서 **채번 규칙/쿼리 의미를 바꾸지 않는다**(Surgical). 보고서에 관찰로 남긴다.

> 무성 실패 주의(단계 1.5): 동시성 결함은 **단일 요청 수동검증으로는 절대 드러나지 않는다.**
> §7에 "포맷 동등성"(필수) + "동시 요청 시나리오"(가능 시)를 명시한다.

---

## §3. 목표 설계 — `ApprovalNoGenerator`

`leave-pattern.md` §5(합성 우선, @Component 주입)를 따른다. `ApprovalFileService`가 "파일 경로/디스크 I/O만 안다"와
같은 결로, **번호 규칙(연도-폼번호-순번, 하위 순번 포맷)은 이 컴포넌트만 안다.** 어떤 클래스도 상속하지 않는다.

```
approval/service/generator/ApprovalNoGenerator.java   (@Component)
```

권고 공개 API (시그니처는 초안 — Claude Code plan mode에서 확정):

| 메서드 | 책임 | 비고 |
|---|---|---|
| `String nextApprovalNo(int year, String formNo)` | (year, formNo) 시리즈의 다음 결재번호 `YYYY-{formNo}{%05d}` 생성. **마지막 번호 조회 + 파싱 + 증분 + 포맷**을 이 메서드가 단독 소유. 첫 건이면 순번 1. **동시성 메커니즘은 넣지 않는다(D1-(ii)) — 현행 로직 그대로 흡수.** | `findLastApprovalNo` 의존. Controller의 파싱(`split`/`replaceAll("\\D","")`/`+1`/`%05d`)과 Service의 `selectApprovalNo`(마지막 읽기)를 **원문 그대로** 여기로 흡수, `selectApprovalNo`는 Service에서 제거 |
| `String senderApproverNo(String approvalNo)` | `{approvalNo}_apr000` (기안자 자동등록 — **포맷만** 이관) | [E] 도메인 관계는 **불변**(스키마 변경, 리팩토링 범위 밖). 순수 문자열 규칙만 이동 |
| `String approverNo(String approvalNo, int order)` | `{approvalNo}_apr{%03d}` | order = 루프 `i+1` |
| `String referencerNo(String approvalNo, int order)` | `{approvalNo}_ref{%03d}` | order = 루프 `i+1` |
| `String fileNo(String approvalNo, int index)` | `{approvalNo}_f{%03d}` | Stage 4 `FileService.store()` 임시 배치분 흡수 |

- **결재번호 포맷 규칙 보존**: `Year + "-" + formNo + String.format("%05d", seq)`. 하위 번호 `_apr/_ref/_f + %03d`,
  기안자 `_apr000`. **규칙 자체는 바꾸지 않고 위치만 이동**(포맷 동등성 = §6 핵심 기준).
- **"임시저장 → formNo=ims" 매핑은 호출자(Controller) 책임으로 유지** — 상태→폼 매핑이지 번호 규칙이 아니다.
  Generator는 이미 해석된 `formNo`를 받는다. (경계 판단 = §8 D2)
- **[K] 동시성 메커니즘 없음(D1-(ii) 확정)**: Generator는 현행 채번 로직을 **동작 보존적으로** 흡수만 한다. 락·재시도·`existsById`
  선검사 등 **어떤 방어물도 추가하지 않는다**(무성 방어물 방지). Stage 5 이후 채번이 한 지점에 모여 있으므로, Stage 6가 [F]와 함께
  여기서 재시도+persist로 [K]를 닫는다. 즉 Stage 5의 [K] 산출물은 **"고칠 지점의 단일화"**다.

### 호출부 이후 모습(요지)
- **Controller `insertApproval`**: L211–248 블록 → `int year = LocalDate.now().getYear();` +
  (임시저장이면 formNo=ims 결정) + `String approvalNo = approvalNoGenerator.nextApprovalNo(year, formNo);` 로 축약.
  결재자/참조자 **루프는 그대로 두고**, 루프 안 채번 문자열만 `approvalNoGenerator.approverNo(approvalNo, i+1)` /
  `referencerNo(approvalNo, i+1)` 로 치환. (루프 자체 서비스 이관 = Stage 6)
- **Controller `updateApprovalTemp`**: 결재자/참조자 루프의 채번 문자열만 동일 치환. approvalNo 신규 생성 없음(기존 재사용).
- **Service `insertApproval`**: `_apr000` 리터럴(L134) → `approvalNoGenerator.senderApproverNo(approvalNo)`.
- **Service `selectApprovalNo`(L744–757)**: 제거(유일 호출처가 Generator로 흡수됨). `findLastApprovalNo`(Repository)는 존치.
- **FileService `store()`**: `_f%03d` 인라인(L79–80) → `approvalNoGenerator.fileNo(approvalNo, i+1)`, TODO 주석 제거.
  FileService가 Generator를 주입받는다.

---

## §4. 작업 범위

### 생성
- `approval/service/generator/ApprovalNoGenerator.java` **(신규 유일 파일)**
- `ErrorCode.java`: **수정 없음 확정(D3).** 첨부 `ErrorCode.java` 확인 결과 **AP001~AP009 전부 사용 중**
  (AP008=`APPROVAL_WITHDRAW_NOT_OWNER`, AP009=`APPROVAL_WITHDRAW_ALREADY_PROCESSED`). 채번 실패용 코드는 없으나,
  D1-(ii)로 **재시도가 Stage 5에 없으므로 신규 코드도 불필요**. (필요해지면 `APPROVAL_NO_GENERATION_FAILED(…"AP010"…)`를
  **Stage 6**에서 재시도 구현과 함께 추가.)

### 수정
- `ApprovalController.java` — 결재번호 생성 블록(L211–248) Generator 위임, 결재자/참조자 루프 **내부 채번 문자열만** 치환(2개 메서드)
- `ApprovalService.java` — `selectApprovalNo` 제거, `insertApproval`의 `_apr000`(L134) 치환
- `ApprovalFileService.java` — `_f%03d` 임시 배치(L79–80) Generator 위임 + TODO 제거 + Generator 주입

### 범위 외 (건드리지 말 것)
- 결재자/참조자 **DTO 조립 루프 자체의 서비스 이관**(얇은 컨트롤러) → **Stage 6**
- `updateApproval`의 삭제-후-재생성 구조([C]), `save()` merge/persist 전환·dirty checking([F]) → **Stage 6**
  · 단, `updateApproval` L379의 `_apr000` **포맷 치환**은 채번 일원화 목적상 이번 단계에서 수행(구조 불변, §8 D4)
- 백킹 쿼리 `findLastApprovalNo`의 **LIKE/정렬 의미 변경**([K-부수 관찰]) → 하지 않음(규칙 보존)
- 기안자 Approver 자동등록 **도메인 분리**([E]) → 리팩토링 범위 밖(스키마 변경). 포맷만 이동, 관계 불변.
- 회수 인증([A-확장]) → Stage 7 / 이모지 로그([J]) → Stage 7 / `src/test/**`

---

## §5. 구현 지침

1. **포맷 동등성(최우선)**: 기안/재임시저장 후 생성되는 `approvalNo`·`approverNo`·`refNo`·`fileNo`·기안자 `_apr000`이
   **이전과 문자·자릿수까지 동일**해야 한다(`YYYY-{form}{5자리}`, `_apr{3자리}`, `_ref{3자리}`, `_f{3자리}`). 규칙 이동만, 변경 금지.
2. **성공 응답 JSON 불변**: `{status,message,data}` 구조와 결재/결재자/참조자/첨부 메타 필드 형태가 이전과 동일.
3. **단일 진입점**: 치환 후 Controller/Service/FileService 어디에도 **인라인 채번 문자열이 남지 않아야** 한다
   (`"_apr"`,`"_ref"`,`"_f"`,`"_apr000"`,`"%05d"` 조립, `selectApprovalNo` 호출 0건). §6 grep로 검증.
4. **[K] 메커니즘 미구현(D1-(ii) 확정)**: 락·재시도·선검사 **금지**. Generator는 현행 채번 로직을 동작 보존적으로 흡수만 한다.
   재시도+persist를 통한 실제 close-out은 Stage 6. (지금 넣으면 merge 경로에서 못 잡는 무성 방어물이 됨 — §2)
5. **write 경로 불가침**: `approvalRepository.save(...)`의 merge/persist 전환, `@Version` 추가, tx 경계 재설계는 **하지 않는다**([F]=Stage 6).
   [K] 처리는 Generator의 "읽기+생성" 경계 안에서 닫는 것을 우선한다.
6. leave-pattern §5 준수: `@Component` + 생성자 주입, 상속 금지. Generator는 상태 없는(stateless) 순수 채번 컴포넌트를 지향
   ([K] 방침이 인스턴스 락을 요구하면 그 락 필드만 예외).
7. **`updateApproval`의 [C] 구조 불변**: L379 `_apr000` 치환은 문자열 조립부만 교체하고, 그 메서드의 삭제-후-재생성 흐름·
   순서·save 방식은 **원문 그대로 둔다**(Stage 6 소관). 재정렬·정리 금지.

---

## §6. Success Criteria

| # | 항목 | 확인 방법 |
|---|---|---|
| 1 | `ApprovalNoGenerator` 생성, `@Component` 주입(Controller/Service/FileService) | 육안 + bootRun 빈 등록 |
| 2 | Controller/Service/FileService에 인라인 채번 0건 (`"_apr"`/`"_ref"`/`"_f"`/`"_apr000"`/`"%05d"` 조립, `selectApprovalNo` 호출) | Select-String |
| 3 | `ApprovalService.selectApprovalNo` 제거 (Generator로 흡수), `findLastApprovalNo`(Repository) 존치 | Select-String + 육안 |
| 4 | `ApprovalFileService`의 `// TODO: Stage 5 …` 주석 및 인라인 `_f` 조립 제거, Generator 위임 | Select-String |
| 5 | **포맷 동등성**: 생성 번호가 이전과 동일 형식(`YYYY-{form}{5}`,`_apr{3}`,`_ref{3}`,`_f{3}`,`_apr000`) | §7 수동 API(응답·DB 육안 대조) |
| 6 | 채번이 Generator 단일 진입점으로 모임(Stage 6가 손댈 지점 1곳) + [K] **현행 대비 비회귀**(방어물 추가 0, 형식·증분 보존) | 코드 리뷰 + §7-6 |
| 7 | write 경로(`save` 방식·tx·`@Version`) 원문 그대로 (Stage 6 불가침) | Select-String/육안 |
| 8 | `compileJava` BUILD SUCCESSFUL | 빌드 |
| 9 | `bootRun` 정상 기동(`Started Application`) | 부팅 |
| 10 | 성공 응답 JSON 이전과 동일 | §7 수동 API |
| 11 | Stage 1/1.5/2/3/4 회귀 없음, 범위 외(§4) 라인 원문 그대로 | Select-String/육안 |

---

## §7. 수동 검증 (무성 실패 대비 — 필수)

`compileJava`·`bootRun`만으론 채번 무성 실패(형식 어긋남·동시성)를 못 잡는다(단계 1.5 교훈). 아래를 Postman/cURL로.
명령은 Windows/PowerShell 5.1 기준.

1. **일반 기안(첨부 2개, 결재자 2·참조자 1)** → 200. 응답·DB에서 `approvalNo=YYYY-{form}00001`류,
   `_apr000`(기안자)·`_apr001`·`_apr002`, `_ref001`, `_f001`·`_f002`가 **이전과 동일 형식**인지 대조.
2. **연속 기안(같은 폼 2건)** → 순번이 `…00001` → `…00002`로 **정확히 +1** 증가(파싱/증분 보존).
3. **임시저장 기안** → `formNo=ims` 반영된 `YYYY-ims{5}` 형식.
4. **재임시저장(PUT /approvals/{approvalNo})** → approvalNo **불변**(신규 생성 안 함), 결재자/참조자 번호 재구성 형식 동일.
5. **첫 건(해당 연도+폼 최초 기안)** → 순번 1로 시작(널 분기 보존).
6. **[K] 비회귀 확인(D1-(ii))**: 이번 단계는 동시성을 **해결하지 않는다** — 통과선은 **현행 대비 비회귀**다.
   즉 번호 형식·순번 증분이 Stage 4와 동일하고, **방어물(락/재시도)이 추가되지 않았음**을 코드로 확인.
   (선택) 같은 폼 동시 POST 2건을 쏴 **현행과 동일한 경쟁 동작**임을 확인해도 되나, 개선을 기대하지 않는다.
   **잔여 동시성 창(및 §2 merge 위험)은 보고서에 Stage 6 이월로 명시.**
7. 무첨부 기안 / 상세·목록 조회 / 회수 / 결재처리 / 삭제 → **회귀 없음**.

---

## §8. 결정 사항 (전 항목 확정 — 2026-07-29)

> D1~D4 확정. Claude Code는 이 확정본 기준으로 plan mode 검토 → 구현. (D2·D4는 추천안대로 확정.)

### ⭐ D1. [K] 동시성 해결책 — **✅ 확정: (ii) — Stage 6와 함께 close-out**

**확정 내용**: 동시성 close-out을 **Stage 6(write 경로 persist 전환 [F])와 함께** 닫는다. → **Stage 5는 [K] 메커니즘을
구현하지 않는다.** Generator는 현행 채번을 **동작 보존적으로 흡수만** 하고(방어물 0), 채번이 한 지점에 모임으로써
**Stage 6가 재시도+persist로 [K]를 닫을 단일 지점**을 만든다. 방향성은 아래 (a)이며, 그 실효(충돌=예외)는 Stage 6의 persist
전환이 전제라 이번 단계에서 재시도를 넣으면 merge 경로에서 못 잡는 무성 방어물이 되므로 **의도적으로 미룬다.**

참고용 후보 비교(확정 근거):

| 옵션 | 내용 | 스키마 변경 | 스코프 적합(5 경계) | 검증 가능성(현 도구) | 다중 인스턴스 | 판정 |
|---|---|---|---|---|---|---|
| **(a) PK 유니크(기존)+재시도** | 충돌 시 다음 번호로 재시도. approvalNo는 이미 PK라 스키마 불요 | **불요** ✅ | **부분** — 충돌 표면화가 write(persist)에 의존 → [F]/Stage 6 얽힘 | 예외 재현이 어려움(merge면 예외 대신 UPDATE) | ✅ 견딤 | **추천(방향)** — 단 실효는 D1-(ii) 전제 |
| **(b) @Lock(비관적)** | 마지막 번호 읽기 구간 직렬화(`PESSIMISTIC_WRITE`) | 불요 | 양호 — Generator 읽기 경계 안 | **낮음** — 선행 와일드카드 LIKE 풀스캔의 gap-lock 동작이 격리수준·인덱스 의존, **동시성 테스트 없이 검증 불가**(무성 실패 위험) | ✅ 견딤 | 조건부 |
| **(c) DB 시퀀스/번호 테이블** | 원자적 채번 | **필요** ❌ | **범위 밖** — `spec.md` "DB 스키마 변경 Out of Scope"(L64–72), `generate-ddl:false` → 수동 스크립트+인간 실행 | 높음 | ✅ 견딤 | **범위 밖(스키마)** — 장기 정답, 별도 스코프 협의 필요 |
| **(d) 앱 레벨 락** | Generator 내 `synchronized`/`ReentrantLock` | 불요 | 양호 — Generator 안에서 닫힘 | **높음** — 단일 JVM 모니터라 추론으로 확실, 실패모드가 "명시적 한계"(다중 인스턴스)뿐 | ❌ 단일 노드만 | 스톱갭 |

**확정에 따른 Stage 5 통과선**: **포맷 동등성 + 순번 증분 보존 + [K] 비회귀(방어물 추가 0).** 동시성 개선은 기대하지 않는다.
**Stage 6 인계**: (a) 재시도 + [F] persist 전환을 함께 구현, §2 K-후속(merge 여부) 실측, 필요 시 AP010 신규(D3).
(b)는 현 LIKE 쿼리에서 검증 불가(단계 1.5식 무성 실패 위험), (c)는 스키마 변경으로 범위 밖, (d)는 단일 노드 한정 — 모두 채택 안 함.

### D2. "임시저장 → formNo=ims" 매핑 위치 — ✅ 확정
- **호출자(Controller) 유지.** 상태→폼 매핑은 번호 규칙이 아님. Generator는 해석된 `formNo`만 받는다.

### D3. 채번 실패 에러코드 — ✅ 확정: `ErrorCode.java` 미수정
- 첨부 확인 결과 **AP001~AP009 전부 사용 중**(AP008=회수 본인확인, AP009=회수 상태). 채번 실패용 코드는 없으나,
  D1-(ii)로 **재시도가 Stage 5에 없어 신규 코드 불필요.** (Stage 6에서 재시도 구현 시 `APPROVAL_NO_GENERATION_FAILED`를
  AP010으로 추가.)

### D4. `updateApproval`(Stage 6 메서드)의 `_apr000` 포맷 치환 — ✅ 확정: 두 콜사이트 모두 치환
- insert L134 + updateApproval L379 모두 `senderApproverNo(approvalNo)`로 치환(단일 진입점). 단 **문자열 조립부만 교체**,
  updateApproval의 [C] 구조(삭제-후-재생성)는 **불변**(Stage 6). 재정렬 금지.

---

## §9. 착수 전 체크 (Claude Code)
1. §8 D1~D4 **확정 완료**(D1=(ii), D2=Controller, D3=ErrorCode 미수정, D4=두 콜사이트 치환) — 확정본대로 진행.
2. **[K] 방어물(락/재시도/선검사) 0건**으로 흡수만 했는가? (넣었으면 D1-(ii) 위반 → 중단·보고)
3. 범위 외(§4) 라인은 원문 그대로 두는가? (write 경로 `save`·tx·`@Version`, 루프 이관, [C]/[E], 쿼리 의미)
4. `selectApprovalNo` 제거 후 `nextApprovalNo`가 파싱/증분/널분기를 **원문 그대로** 보존하는가?
5. 검증에 §7-5(포맷 동등성)·§7-6(비회귀)을 포함했는가? 잔여 동시성 창을 보고서에 Stage 6 이월로 명시하는가?
6. 예상 못 한 상황(예: 채번 호출부가 §1 목록 외에 더 있음)은 추측 말고 **중단·보고**.
