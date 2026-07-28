# 단계 4: 파일 처리 분리 — Task 명세 (초안)

> 작성일: 2026-07-29
> 선행: 단계 1·1.5·2·3 완료·커밋·푸시 (`eee0e10` origin/main)
> 산출물: `ApprovalFileService` (@Component). 파일 업로드·삭제·다운로드 로직 격리.
> 성격: 구조 추출(behavior-preserving on happy path) + [G]의 **파일 저장/삭제 부분** 정상화
>       + `dounloadFile` try-catch 제거. **실패 경로 동작은 의도적으로 바뀐다**(§8 결정 D1).

---

## §0. 이 단계의 한 줄 정의

Controller에 인라인된 첨부 메타데이터 조립과 `ApprovalService`에 뒤섞인 디스크 I/O를
`ApprovalFileService`(@Component)로 뽑아내고, 그 과정에서 파일 저장·삭제의 **예외 삼킴([G] 파일부분)**과
`dounloadFile`의 try-catch를 공통 에러 체계로 정상화한다. **성공 응답 JSON은 불변**, 실패 경로만 바뀐다.

경계: insert/update의 "삭제 후 재생성"([C]), 비원자적 save·dirty checking([F])은 **단계 6 소관 — 건드리지 않는다.**
채번([K])은 **단계 5**, 회수 인증([A-확장])은 **단계 7**. 이 단계에서 손대지 않는다.

---

## §1. 대상 코드 (현재 = Stage 3 반영 버전 기준 라인)

### ApprovalController.java
- `fileStorageLocation` 필드 — L40 (死필드, §2 F8)
- `updateApprovalTemp`(재임시저장)의 첨부 DTO 조립 루프 — L188–212 (`savePath` 계산 + `_f%03d` 채번 + AttachmentDTO 세팅)
- `insertApproval`(기안)의 첨부 DTO 조립 루프 — L333–357 (위와 거의 동일 중복)
- `dounloadFile` — L404–454 (try-catch 삼킴)

### ApprovalService.java
- `insertApproval` 파일 저장 블록 — L181–261 (`Files.copy` 삼킴 L225–230, 실패 시 `deleteFile` 보상 L244–256)
- `updateApproval` 파일 처리 블록 — L506–607 (기존 파일 `deleteFile` L523–527, 저장 루프 L536–606; `Files.copy` **재던짐** L570–576)
- `approvalDelete`의 첨부 파일 삭제 조립 — L877–886
- `deleteFile` — L929–952 (`java.io.File` + 하드코딩 슬래시 + throw 주석처리)

### 참조(변경 없음)
- `Attachment` 엔티티 / `AttachmentDTO` / `AttachmentRepository`
- `application.yml` — `file.upload-dir: "C:/login/"`, `file.file-dir: "upload"` → savePath = `C:/login/upload`

---

## §2. 분석으로 확정된 문제

| # | 위치 | 문제 | 성격 | Stage 4 처리 |
|---|---|---|---|---|
| F1 | Ctrl L188–212 / L333–357 | 첨부 메타데이터 조립이 컨트롤러에 **인라인 + 거의 동일 중복**. `fileSavename=oneFile.getName()`(폼 필드명)은 Service가 UUID로 덮어써 무의미 | 구조 | **포함** — 조립 책임을 파일 서비스로 이관 |
| F2 | Svc L181–261 / L506–607 | 디스크 write(`Files.copy`)와 DB save(`attachmentRepository.save`)가 `@Transactional` 본문에 뒤섞임. plan §6 "I/O ↔ 트랜잭션 경계 분리" 미달 | 구조 | **포함(격리)** — 단, 트랜잭션 재설계는 §6 경계(아래 D4) |
| F3 | Svc L225–230 | **[G]-insert**: `Files.copy` 실패를 `log.info`로 삼킴 → 파일 없이 `attachmentRepository.save` 실행 → orphan DB 레코드 + 기안 성공 응답 | 결함(무성) | **포함** — BusinessException 전파 |
| F4 | Svc insert vs update | 동일해야 할 두 저장 루프가 불일치: insert는 삼킴(L228), update는 재던짐(L575) | 결함 | **포함** — 파일 서비스 단일 경로로 수렴 |
| F5 | Svc L929–952 | **[G]-delete**: `deleteFile`가 삭제 실패를 모으지만 throw가 **주석처리**(L948–951) → 무성. `java.io.File` + `savePath + "/" + name` 하드코딩 | 결함(무성) | **포함(권고, D2)** |
| F6 | Svc L515–521 / L879–884 | **map 재사용 버그**: `Map`을 루프 밖에서 1개 만들어 매 반복 같은 참조에 put 후 add → 리스트가 **마지막 파일명 N개**. 첨부 여러 개면 **마지막 하나만 실제 삭제**, 나머지 디스크 잔존 | 결함(무성) | **포함(권고, D2)** |
| F7 | Ctrl L404–454 | `dounloadFile` try-catch가 예외를 `badRequest`로 뭉갬(공통 체계 미준수). 메서드명 오타. `fileSavepath`를 클라이언트 쿼리파라미터로 그대로 받아 **경로조작 노출** 소지 | 결함 + 보안 | try-catch **제거·위임 포함**. 클라이언트 `savepath` 미수신(C-3)으로 **경로조작 부수 차단**. 메서드명 교정은 **단계 7 이월**(D3) |
| F8 | Ctrl L40 | `fileStorageLocation` **死필드 + 초기화 버그**: 필드 이니셜라이저가 `@Value` 주입 전 실행 → `Paths.get("nullnull")`. 어디에도 미사용 | 死코드 | **포함(권고, D5)** — 삭제 |

> F3·F5·F6는 모두 **compileJava·bootRun을 통과하고 런타임에 조용히 잘못 동작**하는 부류다. 단계 1.5의 교훈
> (무성 실패는 수동 API로만 잡힌다)이 그대로 적용된다 → §7 수동 검증에 orphan 확인을 넣는다.

---

## §3. 목표 설계 — `ApprovalFileService`

leave-pattern §5(합성 우선, @Component 주입)를 따른다. `ApprovalNoGenerator`가 "번호 규칙만 안다"와 같은 결로,
**파일 경로 설정(`file.upload-dir`/`file.file-dir`)과 디스크 I/O를 이 컴포넌트만 안다.**
→ Controller·ApprovalService는 `@Value("${file.*}")`를 더는 직접 읽지 않는다.

```
approval/service/file/ApprovalFileService.java   (@Component)
```

권고 공개 API (시그니처는 초안 — Claude Code plan에서 확정):

| 메서드 | 책임 | 실패 처리 |
|---|---|---|
| `List<AttachmentDTO> store(String approvalNo, List<MultipartFile> files)` | UUID 저장명 생성, 디렉토리 보장, 디스크 write, `Attachment` 저장, 저장된 메타 반환 | 실패 시 이번 호출에서 이미 쓴 파일 보상 삭제 후 `BusinessException(APPROVAL_FILE_UPLOAD_FAILED)` **(AP006, 기존 코드)** |
| `void deleteByApprovalNo(String approvalNo)` 또는 `void delete(List<String> savedNames)` | 결재번호로 첨부 조회 → 디스크 삭제. **F6 없이 파일마다 개별 처리** | **비치명(non-fatal)**: 개별 실패는 WARN 로그만, throw 안 함 (D2 확정). 삭제 실패로 결재 삭제/수정 전체를 막지 않음 |
| `ResourceDownload loadAsResource(String savename, String oriname)` | **베이스 경로는 서비스가 자기 `@Value`로 조립**(클라이언트 `savepath` 안 받음, C-3). `UrlResource` 생성·존재 확인·MIME 판별·헤더 구성 | 미존재 → `BusinessException(APPROVAL_FILE_NOT_FOUND)` **(AP007, 기존 코드)**(404). IO예외는 전파(→ 500/C999) |

> **ErrorCode 확인 완료(2026-07-29)**: repo `ErrorCode.java`에 `APPROVAL_FILE_UPLOAD_FAILED(500,"AP006")`,
> `APPROVAL_FILE_NOT_FOUND(404,"AP007")`가 **이미 존재.** → Stage 4는 `ErrorCode.java`를 수정하지 않는다.

- 파일 저장명 규칙(UUID+확장자), 저장 경로 조립은 이 컴포넌트 내부로 캡슐화.
- 경로 조립은 `java.io.File` + `"/"` 하드코딩 대신 `Path`/`Paths.resolve` 사용(F5 정리분).
- **DB `Attachment` 저장은 `store()` 내부에서 수행**(디스크 write + Attachment save + 보상 삭제를 한 컴포넌트가 소유 — D4 확정, 옵션 X).
  이유: 현행 코드가 디스크·DB를 같은 루프에서 처리하므로 이 배치가 **동작 보존적**이고, `store()` 내부 실패는 자기 보상으로 닫힌다.
  → **잔여 한계**: `store()`가 성공 반환한 뒤 **바깥 tx가 롤백**되면 파일이 orphan으로 남는 창(window)이 남는다.
    이 tx↔디스크 원자성(transaction synchronization 등)은 tx 구조를 다시 짜는 **단계 6 소관**으로 명시 이월(§8 D4).

### Controller 이후 모습(요지)
- `insertApproval`/`updateApprovalTemp`: 첨부 DTO 조립 루프(F1) **제거**. `fileStorageLocation`(F8) 제거.
  파일 리스트를 서비스에 그대로 넘기고, 첨부 메타 조립은 파일 서비스가 담당.
- `dounloadFile`: try-catch 제거, `approvalFileService.loadAsResource(savename, oriname)` 위임 후 `ResponseEntity<Resource>` 구성.
  **클라이언트 `fileSavepath`는 서비스로 넘기지 않는다**(C-3 — 베이스 경로는 서비스 소유). 컨트롤러가 `@RequestParam fileSavepath`를
  받아도 무시(요청 형태 하위호환 유지). **메서드명 오타 `dounloadFile`는 Stage 4에서 교정하지 않는다**(D3 — 순수 미관 개선은
  Surgical 위반, Controller 슬림화 **단계 7**에서 자연 처리). 매핑 경로 `/approvals/files`는 불변.

---

## §4. 작업 범위

### 생성
- `approval/service/file/ApprovalFileService.java` **(신규 유일 파일)**
- `ErrorCode.java` **수정 불필요** — `APPROVAL_FILE_UPLOAD_FAILED`(AP006)·`APPROVAL_FILE_NOT_FOUND`(AP007) 기존재.

### 수정
- `ApprovalController.java` — F1 조립 루프 제거, F8 死필드 제거, `dounloadFile` 위임(F7 try-catch 제거)
- `ApprovalService.java` — insert/update 파일 저장 블록을 파일 서비스 호출로 대체, `deleteFile` 이관/제거,
  `approvalDelete`의 첨부 삭제 조립(F6) 정리

### 범위 외 (건드리지 말 것)
- insert/update의 "삭제 후 재생성"([C]), 비원자 save·dirty checking([F]) → **단계 6**
- 결재/결재자/참조자 채번, `_apr%03d`/`_ref%03d` 로직 → 단계 5·6
- 채번 동시성 [K] → **단계 5**
- 회수 인증 [A-확장] → **단계 7**
- **tx↔디스크 원자성**(`store()` 반환 후 바깥 tx 롤백 시 orphan 파일) → **단계 6**(tx 재설계와 함께)
- `dounloadFile` **메서드명 교정** → **단계 7**(Controller 슬림화)
- `src/test/**` (죽은 테스트 — test-suite-status.md 백로그)

> **경로조작(path traversal)**: C-3(loadAsResource가 클라이언트 savepath를 안 받고 서비스 베이스 경로만 사용)로
> **부수적으로 닫힌다.** 별도 보안 백로그 항목 불요. 단 저장명은 UUID라 traversal 문자가 섞일 여지가 없음을 확인만.

---

## §5. 구현 지침

1. **성공 응답 JSON 불변**: 기안/재임시저장/상세/삭제 응답의 `{status,message,data}` 구조와 첨부 메타 필드
   (`fileNo/fileOriname/fileSavepath/fileSavename/approvalNo`)가 이전과 동일해야 한다.
2. **파일 저장명·경로 규칙 보존**: UUID+확장자 저장명, `C:/login/upload` 경로. 규칙 자체는 바꾸지 않는다(위치만 이동).
3. **[G] 정상화 범위 한정**: "삼킴 → BusinessException"은 **파일 저장/삭제/다운로드**에 국한. 그 외 [G] 지점
   (referencer/approver 삭제 삼킴 등 approvalDelete L896–913)은 **단계 6**에 남긴다.
4. **트랜잭션 / 보상(옵션 X)**: insert/update의 `@Transactional`은 **유지**. `store()`가 디스크 write + Attachment save를
   소유하고, `store()` **내부** 실패 시 이번 호출에서 쓴 파일을 보상 삭제 후 AP006 전파(현 `deleteFile(fileList)` 로직 계승).
   `store()` 반환 **이후** 바깥 tx 롤백으로 생기는 orphan은 **단계 6**(tx synchronization)로 이월 — Stage 4는 여기까지.
   트랜잭션 경계 재설계(REQUIRES_NEW, afterCompletion 훅 등)는 **하지 않는다**.
7. **삭제 실패 로깅 추적성(C-2)**: F5 비치명 삭제 실패는 후속 정리가 긁어갈 수 있게 **고정 접두어**를 포함해 WARN 로깅한다.
   접두어(예: `[APPROVAL_FILE_ORPHAN]`)로 파일명·경로를 남긴다. (단순 "삭제 실패" 로그로 뭉개지 않는다.)
5. leave-pattern 준수: 컴포넌트는 `@Component` + 생성자 주입, 상속 금지, `@Value`는 파일 서비스가 단독 보유.
6. **첨부파일번호(`_f%03d`) 채번 = Stage 4/5 경계**: plan §7상 첨부파일번호 채번은 **Stage 5 `ApprovalNoGenerator`
   소관**이다. Stage 4는 F1 컨트롤러 루프를 걷어내면서 이 채번을 **`ApprovalFileService.store()` 내부에 임시로**
   둔다(plan "임시 수정" 전략). Stage 5에서 Generator로 흡수·제거. → Stage 4는 채번 **규칙을 바꾸지 않고 위치만 이동**,
   Generator를 새로 만들지 않는다.

---

## §6. Success Criteria

| # | 항목 | 확인 방법 |
|---|---|---|
| 1 | `ApprovalFileService` 생성, `@Component` 주입 | 육안 + bootRun 빈 등록 |
| 2 | Controller에 첨부 DTO 조립 루프 0건, `fileStorageLocation` 0건, `@Value("${file` 0건 | grep |
| 3 | `ApprovalService`에 `Files.copy`/`java.io.File`/`@Value("${file` 0건 (파일 서비스로 이관) | grep |
| 4 | 파일 저장 실패가 삼켜지지 않고 BusinessException으로 전파 | 코드 리뷰 + §7 수동(디스크 쓰기 실패 유도) |
| 5 | `deleteFile` 다중 첨부 시 **모든** 파일 삭제(F6 해소) | §7 수동(첨부 2개↑ 삭제 후 디스크 확인) |
| 6 | `dounloadFile` try-catch 제거, 미존재 파일 → 404(BusinessException) | grep + §7 수동 |
| 6b | `loadAsResource`가 클라이언트 `savepath` 미수신, 서비스 베이스 경로만 사용(C-3) | 코드 리뷰 + §7-8 |
| 6c | `store()` 내부 실패 시 이번 호출 파일 보상 삭제(디스크 orphan 0) | §7-7 |
| 7 | `compileJava` BUILD SUCCESSFUL | 빌드 |
| 8 | `bootRun` 정상 기동(`Started Application`) | 부팅 |
| 9 | 성공 응답 JSON 이전과 동일 | §7 수동 API |
| 10 | Stage 1/1.5/2/3 회귀 없음, 범위 외(§4) 라인 원문 그대로 | grep/육안 |

---

## §7. 수동 검증 (무성 실패 대비 — 필수)

`compileJava`·`bootRun`만으론 F3/F5/F6류 무성 실패를 못 잡는다(단계 1.5 교훈). 아래를 Postman/cURL로:

1. **기안(첨부 2개)** → 200, 디스크에 파일 2개 생성, 상세조회에 첨부 2건.
2. **재임시저장(첨부 교체)** → 기존 파일 **전부** 삭제 + 새 파일 저장(디스크 육안 확인 — F6 회귀 지점).
3. **삭제** → 첨부 **전부** 디스크에서 제거(잔존 orphan 0 — F6 회귀 지점).
4. **다운로드(정상 파일)** → 파일 스트림 200.
5. **다운로드(없는 파일)** → 404 + ErrorResponse JSON(AP007). (기존엔 badRequest/notFound였음 → 의도된 변경)
6. **디스크 저장 실패 유도**(업로드 디렉토리 권한 제거 후 기안) → 500/AP006, **orphan DB 레코드·부분 파일 없음** 확인.
7. **`store()` 내부 DB save 실패 유도**(예: 첨부 PK 중복 등 `attachmentRepository.save` 실패 상황) →
   AP006 전파 + **이번 호출에서 쓴 디스크 파일 보상 삭제 확인**(디스크 orphan 0). ← A-3/A-4 커버.
8. **다운로드 시 savepath 무시 확인**: 요청에 `fileSavepath`가 있든 없든(또는 조작해도) 서비스 베이스 경로 기준으로만
   해석되는지 확인(C-3 회귀 지점).
9. 무첨부 기안/재임시저장/상세/목록/회수/결재처리/사원조회 → 회귀 없음.

---

## §8. 결정 사항 (전 항목 확정 — 2026-07-29 / GPT 교차검증 반영)

> D1~D6 확정 + Gemini 교차검증 4건 반영: **A-3**(orphan)→D4 옵션 X 명문화+§7-7 테스트, **C-3**(savepath 캡슐화)→채택,
> **C-2**(삭제 로그 접두어)→§5-7, **B-3**(메서드명)→교정 철회·단계 7 이월. Claude Code는 이 확정본 기준으로 실행한다.

- **D1. ✅ 해결(2026-07-29)**: ErrorCode 신규 추가 불필요 — `APPROVAL_FILE_UPLOAD_FAILED`(AP006, 저장 실패),
  `APPROVAL_FILE_NOT_FOUND`(AP007, 다운로드 미존재)가 기존재. Stage 4는 `ErrorCode.java` 미수정.
  남은 승인 포인트는 "**실패 응답 동작 변경**(저장 삼킴→AP006 500, 다운로드 badRequest/notFound→AP007 404·전파), 성공 경로 불변"뿐. **권고: 승인.**
- **D2. ✅ 방향 확정**: F5/F6 **포함**하되 성격을 나눈다.
  · **F6(map 재사용)** = 순수 정확성 버그 → 무조건 수정(모든 첨부 삭제).
  · **F5(삭제 실패 무성)** = **비치명 유지** — 삭제 실패는 WARN 로그만, throw 안 함. 이유: 삭제 실패는 orphan **파일** 잔존(정리 문제)이지 DB 정합성 파손이 아니며, throw 시 스턱 파일 하나로 결재 삭제/재임시저장 전체가 막힘. (저장 실패만 치명 → AP006.)
  · **(C-2 반영)** WARN 로그에 **고정 접두어**(`[APPROVAL_FILE_ORPHAN]`) + 파일명/경로 포함 → 후속 정리·모니터링 추적성 확보(§5-7).
- **D3. ✅ 확정(교차검증 반영)**: `dounloadFile` **메서드명 교정 철회** — 순수 미관 개선이라 Surgical 위반(B-3),
  Controller 슬림화 **단계 7**에서 자연 처리. **경로조작 방어**는 C-3(loadAsResource가 클라이언트 savepath 미수신)로
  **부수적으로 닫힘** → 별도 보안 백로그 불요.
- **D4. ✅ 확정(옵션 X, A-3 반영)**: `store()`가 **디스크 write + Attachment save + 내부 보상**을 소유(동작 보존적).
  `store()` 반환 이후 바깥 tx 롤백으로 생기는 **orphan 파일 원자성**은 tx 재설계가 필요하므로 **단계 6**으로 명시 이월.
  REQUIRES_NEW·afterCompletion 훅 등 tx 재설계는 Stage 4에서 **안 함**. §7-7에 내부 DB-save 실패 보상 테스트 추가.
- **D5. F8 死필드 삭제**: 미사용 + 초기화 버그. **권고: 삭제.**
- **D6. ✅ 확정**: 인계 노트 §3의 "(4는 문제 [G]·[K]·[A-확장])" 표기는 부정확. 실제 매핑은
  **4 = [G]-파일부분 + dounloadFile**, [K]=5, [A-확장]=7. 이 명세 기준으로 진행.
