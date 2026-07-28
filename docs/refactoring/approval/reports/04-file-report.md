# 단계 4: 파일 처리 분리 — 작업 보고서

> 작업일: 2026-07-29
> 실행: Claude Code (Opus 5)
> 명세: `docs/refactoring/approval/tasks/04-file.md` (§8 D1~D6 확정본)
> 선행: 단계 1·1.5·2·3 완료·커밋·푸시 (`eee0e10` origin/main)

---

## 1. 변경 파일

| 파일 | 구분 | 라인 수 |
|---|---|---|
| `approval/service/file/ApprovalFileService.java` | **신규** | 138 |
| `approval/controller/ApprovalController.java` | 수정 | 455 → 264 (-191) |
| `approval/service/ApprovalService.java` | 수정 | 1,033 → 603 (-430) |

`ErrorCode.java` 미수정(AP006/AP007 기존재). `Attachment`/`AttachmentDTO`/`AttachmentRepository` 무변경.
`src/test/**` 무변경.

### git diff --stat (신규 파일 제외, 추적 중인 2개 파일)

```
 .../approval/controller/ApprovalController.java    | 122 ++---------
 .../login/approval/service/ApprovalService.java    | 235 ++-------------------
 2 files changed, 27 insertions(+), 330 deletions(-)
```

신규 파일은 아직 untracked (`?? final/src/main/java/com/insider/login/approval/service/file/`).

---

## 2. 신규 `ApprovalFileService` (@Component)

파일 경로 설정(`file.upload-dir` / `file.file-dir`)과 디스크 I/O를 **이 컴포넌트만** 안다.
생성자 주입(`AttachmentRepository` 1개). 상속 없음. `ModelMapper`는 주입하지 않고
`Attachment` 전체 필드 생성자를 직접 호출한다(결과 동일, 의존 1개 감소).

| 메서드 | 동작 | 실패 처리 |
|---|---|---|
| `List<AttachmentDTO> store(String approvalNo, List<MultipartFile> files)` | fileNo 채번 → 디렉토리 보장 → `Files.copy` → `Attachment` DB 저장 → 조립된 DTO 반환. `files`가 null/empty면 빈 리스트 | 디스크·DB 어느 쪽이든 실패 시 **이번 호출에서 이미 쓴 파일 전부 보상 삭제** 후 `BusinessException(APPROVAL_FILE_UPLOAD_FAILED)` (AP006/500). 원인 예외는 `log.error`로 기록 |
| `void deleteByApprovalNo(String approvalNo)` | `findByApprovalNo` → **파일마다 개별** `Files.deleteIfExists`. DB 행은 건드리지 않음 | **비치명(D2)** — throw 안 함. 실패 건마다 `log.warn` + 고정 접두어 `[APPROVAL_FILE_ORPHAN]` + savename/전체경로 |
| `FileDownload loadAsResource(String savename, String oriname)` | 베이스 경로를 **자기 `@Value`로 조립**(클라이언트 savepath 미수신, C-3) → `UrlResource` → 존재 확인 → MIME 판별 → 파일명 URL 인코딩 | 미존재 → `BusinessException(APPROVAL_FILE_NOT_FOUND)` (AP007/404). 그 외 IO 예외는 전파(→ 500/C999) |

`public record FileDownload(Resource resource, String contentType, String encodedFileName)` — 중첩 선언이므로
**신규 파일은 여전히 1개**(§4 "신규 유일 파일" 준수).

### 경로 조립 — 응답 동등성의 핵심

- DB·응답 JSON에 담기는 `fileSavepath` = `UPLOAD_DIR + FILE_DIR` **문자열 그대로** → `"C:/login/upload"`
- 실제 디스크 I/O = `Paths.get(UPLOAD_DIR + FILE_DIR).resolve(savename)` (F5의 `java.io.File` + `"/"` 하드코딩 제거)

> `Paths.get(...).toString()`을 DB 값으로 쓰면 Windows에서 `C:\login\upload`가 되어 **DB 값과 응답 JSON이 달라진다.**
> 두 용도를 의도적으로 분리했다.

---

## 3. 변경 지점 요약

### ApprovalController.java

| 지점 (변경 전 라인) | 변경 |
|---|---|
| L34–38 `@Value("${file.upload-dir}")`, `@Value("${file.file-dir}")` | 삭제 (파일 서비스로 이관) |
| L40 `fileStorageLocation` | **삭제** — F8 死필드 + `@Value` 주입 전 이니셜라이저 실행으로 `Paths.get("nullnull")`이 되던 초기화 버그 |
| L42–46 생성자 | `ApprovalFileService` 주입 추가 |
| L188–212 `updateApprovalTemp` 첨부 DTO 조립 루프 | **블록 삭제** (F1). 호출부 `approvalService.updateApproval(approvalNo, approvalDTO, multipartFile)` 무변경 |
| L333–357 `insertApproval` 첨부 DTO 조립 루프 (거의 동일 중복) | **블록 삭제** (F1). 호출부 무변경 |
| L404–454 `dounloadFile` | try-catch 제거(F7), `approvalFileService.loadAsResource(fileSavename, fileOriname)` 위임 후 헤더·`ResponseEntity` 조립만 수행 |

- `@RequestParam(name = "fileSavepath")`는 **시그니처에 그대로 남기고 서비스로 넘기지 않는다**(요청 형태 하위호환, C-3).
  주석으로 명시. → 클라이언트가 경로를 조작해도 서비스 베이스 경로로만 해석되므로 **경로조작이 부수적으로 차단**된다.
- **메서드명 `dounloadFile`은 교정하지 않았다**(D3 — Stage 7 소관). 매핑 `/approvals/files` 불변.
- 이번 변경으로 unused가 된 import만 정리: `Value`, `UrlResource`, `Files`, `Path`, `Paths`,
  `URLEncoder`, `UnsupportedEncodingException`.

### ApprovalService.java

| 지점 (변경 전 라인) | 변경 |
|---|---|
| L37–41 `@Value("${file.*}")` 2개 | 삭제 |
| 생성자 | `ApprovalFileService` 주입 추가 |
| L181–261 `insertApproval` 파일 저장 블록 (81줄) | `approvalDTO.setAttachment(approvalFileService.store(approvalDTO.getApprovalNo(), files)); result = 1;` **2줄로 대체** → F3(삼킴) 해소 |
| L509–527 `updateApproval` 기존 파일 삭제 | `approvalFileService.deleteByApprovalNo(approvalNo);` **1줄로 대체** (§4 참조 — 순서 재정렬 없음) |
| L532–606 `updateApproval` 저장 루프 (75줄) | insert와 **동일한 `store()` 호출**로 대체 → F4(insert 삼킴 vs update 재던짐 불일치) 단일 경로로 수렴 |
| L877–886 `approvalDelete` 첨부 삭제 조립 | `approvalFileService.deleteByApprovalNo(approvalNo)`로 대체 → **F6 실효 수정(첨부 전부 삭제)** |
| L916–920 `approvalDelete`의 `catch (IOException e)` | **제거** (§5 참조) |
| L929–952 `deleteFile(List<Map<String,String>>)` | **메서드 삭제** — 파일 서비스로 이관. 호출처 4곳(L250/524/596/886) 전부 이번에 치환, 외부 사용처 없음(grep 확인) |

- 이번 변경으로 unused가 된 import만 정리: `java.io.File`, `java.io.IOException`, `java.nio.file.Files`,
  `java.nio.file.Path`, `java.nio.file.Paths`, `Value`. `MultipartFile`은 시그니처에 남아 유지.

---

## 4. ⚠️ `updateApproval` 잔존 orphan (04-file.md §5-8 — Stage 6 이월)

`updateApproval`은 **L442에서 첨부 DB 행을 먼저 삭제**(`attachmentRepository.deleteByApprovalNo`)한 뒤,
그 아래에서 삭제 대상 목록을 만든다. 그 시점의 조회는 **이미 0건**이므로 옛 파일은 원래부터 디스크에 남는다.

- 이 무효화의 근원은 **삭제-후-재생성([C]) 순서**로 **Stage 6 소관**이다.
- 따라서 Stage 4는 **순서를 재정렬하지 않고 현행 동작을 그대로 보존**했다.
  치환 후 `approvalFileService.deleteByApprovalNo(approvalNo)`도 내부 조회가 0건이라 **동일하게 아무것도 지우지 않는다.**
- **결과: 재임시저장 시 옛 첨부파일이 디스크에 orphan으로 남는 현행 동작은 그대로다.**
  §7-2 수동 검증에서 "기존 파일 전부 삭제"는 **아직 통과하지 않는 것이 정상**이다(Stage 6에서 해소).
- F6(map 재사용) 수정이 **실효를 내는 곳은 `approvalDelete`뿐**이며, 거기서는 첨부 N개가 전부 삭제된다.

---

## 5. 범위 관련 기록 (임시 처리 · 불가피한 변경)

1. **`_f%03d` 채번을 `ApprovalFileService.store()` 내부에 임시 배치**
   위치: `ApprovalFileService.java` `store()` 내 `// TODO: Stage 5 ApprovalNoGenerator 로 이관` 주석 지점.
   규칙(`approvalNo + "_f" + %03d`)은 **바꾸지 않고 위치만 이동**. plan §7상 첨부파일번호 채번은
   Stage 5 `ApprovalNoGenerator` 소관이며, Stage 5에서 흡수·제거된다. Generator를 새로 만들지 않았다.

2. **`approvalDelete`의 `catch (IOException e)` 제거 — 컴파일상 불가피**
   `deleteFile`이 사라지면 try 본문에서 checked `IOException`이 발생하지 않아
   *"exception IOException is never thrown in body of corresponding try statement"* 컴파일 에러가 난다.
   파일 삭제 경로에 직결된 catch이므로 제거했다. 바로 아래 `catch (Exception e) { return false; }`와
   referencer/approver/approval 삭제의 try-catch 삼킴([G], 변경 전 L896–913)은 **원문 그대로 유지**(Stage 6).

3. **`approvalDelete`에서 첨부 조회가 1회 중복**
   `if (!attachmentList.isEmpty())` DB 삭제 가드를 **보존**하기 위해 `attachmentRepository.findByApprovalNo`를
   그대로 두었고, 파일 서비스가 디스크 삭제를 위해 내부에서 한 번 더 조회한다.
   가드 제거는 동작 변경이므로 하지 않았다. 조회 통합은 Stage 6 정리 대상.

4. **확장자 없는 파일명 업로드**
   Before: `substring(-1)` → `StringIndexOutOfBounds` → 500/C999.
   After: `store()` 내부에서 잡혀 보상 삭제 후 **500/AP006**. 둘 다 500이며 에러 코드만 구체화된다.

---

## 6. 의도된 동작 변경 (성공 응답 JSON 불변 — 실패 경로만)

| 시나리오 | Before | After |
|---|---|---|
| 업로드 중 디스크 write 실패 | `log.info` 삼킴 → **파일 없는 DB 레코드 + 200 성공** | 보상 삭제 후 **500 / AP006** |
| 업로드 중 DB save 실패 | 부분 파일 잔존 | 보상 삭제 후 **500 / AP006** |
| 다운로드 — 파일 미존재 | `404 notFound` (본문 없음) | **404 / AP007 + ErrorResponse JSON** |
| 다운로드 — 인코딩/IO 오류 | `400 badRequest`로 뭉갬 | 전파 → **500 / C999** |
| 개별 파일 삭제 실패 | 무성(throw 주석처리) | 비치명 유지 + `[APPROVAL_FILE_ORPHAN]` WARN 로그 |
| 첨부 N개 결재 삭제 | **마지막 1개만** 디스크 삭제(F6) | **전부 삭제** |

성공 경로의 `{status, message, data}` 구조와 첨부 메타(`fileNo`/`fileOriname`/`fileSavepath`/`fileSavename`/`approvalNo`)는
DB에서 다시 읽어 조립되며, 저장 규칙·경로 문자열을 그대로 유지했으므로 **이전과 동일**하다.

---

## 7. 빌드 · 부팅 결과

```
> cd final; .\gradlew.bat compileJava
BUILD SUCCESSFUL in 14s
1 actionable task: 1 executed
```

```
> cd final; .\gradlew.bat bootRun
... o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port 8080 (http) with context path ''
... com.insider.login.Application            : Started Application in 10.051 seconds (process running for 10.661)
```

`ApprovalFileService` 빈이 `ApprovalController`·`ApprovalService` 양쪽에 생성자 주입으로 정상 등록됨
(미등록 시 기동 실패하므로 부팅 성공이 곧 확인). 기동 후 종료(Ctrl+C).

### grep 확인 (Success Criteria 2·3·6)

`ApprovalController.java` / `ApprovalService.java` 대상, 패턴
`@Value("${file` · `Files.copy` · `java.io.File` · `fileStorageLocation` · `deleteFile`:

```
→ 두 파일 모두 잔존 0건.
   매칭은 ApprovalFileService.java 3건뿐 (@Value 2건, Files.copy 1건) — 의도한 이관 결과.
```

---

## 8. 📋 수동 검증 체크리스트 (사용자 담당 — 04-file.md §7 원문)

`compileJava`·`bootRun`만으론 F3/F5/F6류 무성 실패를 못 잡는다(단계 1.5 교훈). 아래를 Postman/cURL로:

- [ ] 1. **기안(첨부 2개)** → 200, 디스크에 파일 2개 생성, 상세조회에 첨부 2건.
- [ ] 2. **재임시저장(첨부 교체)** → 기존 파일 **전부** 삭제 + 새 파일 저장(디스크 육안 확인 — F6 회귀 지점).
      ⚠️ **§4 참조** — 옛 파일 삭제는 [C] 구조상 Stage 4에서 여전히 동작하지 않는다(현행 보존, Stage 6 소관).
      이 항목에서 확인할 것은 **새 파일이 정상 저장되는지**이며, 옛 파일 잔존은 **기존과 동일한 알려진 동작**이다.
- [ ] 3. **삭제** → 첨부 **전부** 디스크에서 제거(잔존 orphan 0 — F6 회귀 지점). ← F6 실효 수정 지점
- [ ] 4. **다운로드(정상 파일)** → 파일 스트림 200.
- [ ] 5. **다운로드(없는 파일)** → 404 + ErrorResponse JSON(AP007). (기존엔 badRequest/notFound였음 → 의도된 변경)
- [ ] 6. **디스크 저장 실패 유도**(업로드 디렉토리 권한 제거 후 기안) → 500/AP006, **orphan DB 레코드·부분 파일 없음** 확인.
- [ ] 7. **`store()` 내부 DB save 실패 유도**(예: 첨부 PK 중복 등 `attachmentRepository.save` 실패 상황) →
      AP006 전파 + **이번 호출에서 쓴 디스크 파일 보상 삭제 확인**(디스크 orphan 0).
- [ ] 8. **다운로드 시 savepath 무시 확인**: 요청에 `fileSavepath`가 있든 없든(또는 조작해도) 서비스 베이스 경로 기준으로만
      해석되는지 확인(C-3 회귀 지점).
- [ ] 9. 무첨부 기안/재임시저장/상세/목록/회수/결재처리/사원조회 → 회귀 없음.

---

## 9. 범위 외 — 손대지 않은 항목 (원문 유지 확인)

- `insertApproval`/`updateApproval`의 삭제-후-재생성([C]), 비원자 save·dirty checking([F]) → **Stage 6**
- **tx ↔ 디스크 원자성**: `store()` 반환 후 바깥 tx 롤백 시 orphan 파일 (D4 옵션 X 잔여 한계) → **Stage 6**
- 결재/결재자/참조자 채번 `_apr%03d`/`_ref%03d`, 채번 동시성([K]) → **Stage 5·6**
- `approvalDelete`의 referencer/approver/approval 삭제 try-catch 삼킴([G] 비파일 부분) → **Stage 6**
- 회수 인증([A-확장]), `dounloadFile` 메서드명 교정, memberId 헤더 백도어([I]) → **Stage 7**
- `ErrorCode.java`, `Attachment`/`AttachmentDTO`/`AttachmentRepository` 시그니처, `@Transactional` 구성, `src/test/**`
