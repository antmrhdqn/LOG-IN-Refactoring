package com.insider.login.approval.controller;

import com.insider.login.approval.dto.*;
import com.insider.login.approval.service.ApprovalCommandService;
import com.insider.login.approval.service.ApprovalQueryService;
import com.insider.login.approval.service.file.ApprovalFileService;
import com.insider.login.common.response.ResponseMessage;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping
@Slf4j
public class ApprovalController {

    private final ApprovalCommandService approvalCommandService;

    private final ApprovalQueryService approvalQueryService;

    private final ApprovalFileService approvalFileService;

    public ApprovalController(ApprovalCommandService approvalCommandService, ApprovalQueryService approvalQueryService, ApprovalFileService approvalFileService) {
        this.approvalCommandService = approvalCommandService;
        this.approvalQueryService = approvalQueryService;
        this.approvalFileService = approvalFileService;
    }


    @Tag(name = "폼 목록 조회", description = "폼 목록 조회")
    @GetMapping("/approvals/forms")
    public ResponseEntity<ResponseMessage<List<FormDTO>>> selectFormList() {

        return ResponseEntity.ok(ResponseMessage.success("폼 목록 조회 성공", approvalQueryService.getFormList()));
    }

    @Tag(name = "특정 폼 조회", description = "특정 폼 조회")
    @GetMapping("/approvals/forms/{formNo}")
    public ResponseEntity<ResponseMessage<FormDTO>> selectForm(@PathVariable(name = "formNo") String formNo) {

        return ResponseEntity.ok(ResponseMessage.success("특정 폼 조회 성공", approvalQueryService.getForm(formNo)));
    }


    //전자결재 상세 조회
    @Tag(name = "전자결재 상세 조회", description = "전자결재 상세 조회")
    @GetMapping("/approvals/{approvalNo}")
    public ResponseEntity<ResponseMessage<ApprovalDTO>> selectApprovalByNo(@PathVariable(name = "approvalNo") String approvalNo) {

        return ResponseEntity.ok(ResponseMessage.success("전자결재 상세 조회 성공", approvalQueryService.getApproval(approvalNo)));

    }

    @Tag(name = "전자결재 목록 조회", description = "전자결재 목록 조회")
    @GetMapping("/approvals")
    public ResponseEntity<ResponseMessage<Page<ApprovalDTO>>> selectApprovalList(@RequestParam("fg") String fg,
                                                          @RequestParam(name = "page", defaultValue = "0") int page,
                                                          @RequestParam(name = "title", defaultValue = "") String title,
                                                          @RequestParam(name = "direction", defaultValue = "DESC") String direction) {

        int memberId = getCurrentMemberId();

        Map<String, Object> condition = new HashMap<>();
        condition.put("flag", fg);
        condition.put("direction", direction);
        condition.put("title", title);

        return ResponseEntity.ok(ResponseMessage.success("상신 목록 조회 성공",
                approvalQueryService.getApprovalList(memberId, condition, page)));

    }


    @Tag(name = "전자결재 회수", description = "회수")
    @PutMapping(value = "/approvals/{approvalNo}/status")
    public ResponseEntity<ResponseMessage<ApprovalDTO>> updateApprovalstatus(@PathVariable(name = "approvalNo") String approvalNo) {

        return ResponseEntity.ok(ResponseMessage.success("전자 결재 회수 성공",
                approvalCommandService.withdraw(approvalNo, getCurrentMemberId())));

    }

    @Tag(name = "전자결재 재 임시저장", description = "재 임시저장")
    @PutMapping(value = "/approvals/{approvalNo}")
    public ResponseEntity<ResponseMessage<ApprovalDTO>> updateApprovalTemp(@PathVariable(name = "approvalNo") String approvalNo,
                                                          @RequestPart(name = "approvalDTO") ApprovalDTO approvalDTO,
                                                          @RequestPart(name = "multipartFile", required = false) List<MultipartFile> multipartFile) {

        approvalDTO.setApprovalNo(approvalNo);
        approvalDTO.setMemberId(getCurrentMemberId());

        ApprovalDTO result = approvalCommandService.resaveTempSaved(approvalNo, approvalDTO, multipartFile);
        log.info("결재 임시저장 수정 성공: " + result.getApprovalNo());
        return ResponseEntity.ok(ResponseMessage.success("결재 임시저장 수정 결과 성공", result));

    }

    @Tag(name = "전자결재 기안", description = "기안")
    @PostMapping("/approvals")
    public ResponseEntity<ResponseMessage<ApprovalDTO>> insertApproval(@RequestPart("approvalDTO") ApprovalDTO approvalDTO,
                                                      @RequestPart(value = "multipartFile", required = false) List<MultipartFile> multipartFile) {

        approvalDTO.setMemberId(getCurrentMemberId());

        //채번, 임시저장 -> 기안 전환 판정, 결재선·참조선 구성은 서비스가 책임진다
        ApprovalDTO result = approvalCommandService.draft(approvalDTO, multipartFile);
        log.info("결재 기안 성공: " + result.getApprovalNo());
        return ResponseEntity.ok(ResponseMessage.success("전자결재 기안 성공", result));

    }

    @Tag(name = "전자결재 처리", description = "결재처리")
    @PutMapping("/approvers/{approverNo}")
    public ResponseEntity<ResponseMessage<ApproverDTO>> updateApprover(@PathVariable(name = "approverNo") String approverNo,
                                                      @RequestBody ApproverDTO approverDTO) {

        return ResponseEntity.ok(ResponseMessage.success("전자결재" + approverDTO.getApproverStatus() + "처리 완료",
                approvalCommandService.processApprover(approverNo, approverDTO)));
    }

    @Tag(name = "전자결재 삭제", description = "전자결재 임시저장 삭제")
    @DeleteMapping("/approvals/{approvalNo}")
    public ResponseEntity<ResponseMessage<Boolean>> deleteApproval(@PathVariable(name = "approvalNo") String approvalNo) {

        return ResponseEntity.ok(ResponseMessage.success("전자결재 삭제 성공",
                approvalCommandService.delete(approvalNo)));
    }


    @GetMapping("/approvals/members/{memberId}")
    public ResponseEntity<ResponseMessage<MemberDTO>> selectMember(@PathVariable(name = "memberId") int memberId) {

        return ResponseEntity.ok(ResponseMessage.success("사원 조회 성공",
                approvalQueryService.getMember(memberId)));
    }

    @GetMapping("/approvals/members")
    public ResponseEntity<ResponseMessage<List<MemberDTO>>> selectAllMembers() {
        return ResponseEntity.ok(ResponseMessage.success("전 사원 부서순 조회 성공",
                approvalQueryService.getAllMemberList()));


    }

    @Tag(name = "파일 다운로드", description = "파일 다운로드")
    @GetMapping("/approvals/files")
    public ResponseEntity<Resource> downloadFile(@RequestParam(name = "fileSavepath") String fileSavepath,
                                                 @RequestParam(name = "fileSavename") String fileSavename,
                                                 @RequestParam(name = "fileOriname") String fileOriname){

        //fileSavepath 는 요청 형태 유지를 위해 받기만 하고 사용하지 않는다(베이스 경로는 파일 서비스가 소유)
        ApprovalFileService.FileDownload fileDownload = approvalFileService.loadAsResource(fileSavename, fileOriname);

        //HttpHeaders 설정
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileDownload.encodedFileName() + "\"");
        headers.add(HttpHeaders.CONTENT_TYPE, fileDownload.contentType());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(fileDownload.contentType()))
                .body(fileDownload.resource());
    }

    //인증 정보에서 현재 로그인한 사원의 사번을 꺼낸다
    private int getCurrentMemberId() {
        return Integer.parseInt(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
