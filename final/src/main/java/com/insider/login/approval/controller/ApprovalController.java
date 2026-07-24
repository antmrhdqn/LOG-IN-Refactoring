package com.insider.login.approval.controller;

import com.insider.login.approval.dto.*;
import com.insider.login.approval.service.ApprovalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping
@Slf4j
public class ApprovalController {


    @Value("${file.upload-dir}")
    private String UPLOAD_DIR;

    @Value("${file.file-dir}")
    private String FILE_DIR;

    private final Path fileStorageLocation = Paths.get(UPLOAD_DIR + FILE_DIR).toAbsolutePath();

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }


    @Tag(name = "폼 목록 조회", description = "폼 목록 조회")
    @GetMapping("/approvals/forms")
    public ResponseEntity<ResponseDTO> selectFormList() {

        log.info("폼 목록 조회 controller 들어왔다");

        return ResponseEntity.ok().body(new ResponseDTO(HttpStatus.OK, "폼 목록 조회 성공", approvalService.selectFormList()));
    }

    @Tag(name = "특정 폼 조회", description = "특정 폼 조회")
    @GetMapping("/approvals/forms/{formNo}")
    public ResponseEntity<ResponseDTO> selectForm(@PathVariable(name = "formNo") String formNo) {

        return ResponseEntity.ok().body(new ResponseDTO(HttpStatus.OK, "특정 폼 조회 성공", approvalService.selectForm(formNo)));
    }


    //전자결재 상세 조회
    @Tag(name = "전자결재 상세 조회", description = "전자결재 상세 조회")
    @GetMapping("/approvals/{approvalNo}")
    public ResponseEntity<ResponseDTO> selectApprovalByNo(@PathVariable(name = "approvalNo") String approvalNo) {
       /* ApprovalDTO approvalDTO = approvalService.selectApproval(approvalNo);
        log.info("approvalDTO: " + approvalDTO);*/

        return ResponseEntity.ok().body(new ResponseDTO(HttpStatus.OK, "전자결재 상세 조회 성공", approvalService.selectApproval(approvalNo)));

    }

    @Tag(name = "전자결재 목록 조회", description = "전자결재 목록 조회")
    @GetMapping("/approvals")
    public ResponseEntity<ResponseDTO> selectApprovalList(@RequestParam("fg") String fg,
                                                          @RequestParam(name = "page", defaultValue = "0") String page,
                                                          @RequestParam(name = "title", defaultValue = "") String title,
                                                          @RequestParam(name = "direction", defaultValue = "DESC") String direction,
                                                          @RequestHeader(value = "memberId", required = false) String memberIdstr) {
        log.info("****컨트롤러 들어왔어");

        int memberId = 0;

        if (memberIdstr == null) {
            //현재 사용자의 인증 정보 가져오기
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            log.info("memberId: " + authentication.getName());

            //인증 정보에서 사용자의 식별 정보 가져오기
            memberId = Integer.parseInt(authentication.getName());

        } else {
            memberId = Integer.parseInt(memberIdstr);
        }
        log.info("현재 사용자 : " + memberId);

        Map<String, Object> condition = new HashMap<>();
        condition.put("flag", fg);
        condition.put("limit", 10);
        condition.put("direction", direction);
        condition.put("title", title);

        int pageNo = Integer.parseInt(page);
        System.out.println("현재 pageNo : " + pageNo);
        log.info("현재 pageNo : " + pageNo);


        Page<ApprovalDTO> approvalDTOPage = approvalService.selectApprovalList(memberId, condition, pageNo);

        System.out.println("🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈Page 총 페이지 controller : " + approvalDTOPage.getTotalPages());
//        log.info("approvalDTOPage : " + approvalDTOPage.getContent());

        ResponseDTO response = new ResponseDTO(HttpStatus.OK, "상신 목록 조회 성공", approvalDTOPage);
        System.out.println("조회성공");
        return ResponseEntity.ok().body(response);

    }


    @Tag(name = "전자결재 회수", description = "회수")
    @PutMapping(value = "/approvals/{approvalNo}/status")
    public ResponseEntity<ResponseDTO> updateApprovalstatus(@PathVariable(name = "approvalNo") String approvalNo) {


        log.info("🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉회수 컨트롤러 들어왔어");
        // TODO: Stage 7에서 제거
        int memberId = Integer.parseInt(SecurityContextHolder.getContext().getAuthentication().getName());
        return ResponseEntity.ok().body(new ResponseDTO(HttpStatus.OK, "전자 결재 회수 성공", approvalService.updateApprovalStatus(approvalNo, memberId)));

    }

    @Tag(name = "전자결재 재 임시저장", description = "재 임시저장")
    @PutMapping(value = "/approvals/{approvalNo}")
    public ResponseEntity<ResponseDTO> updateApprovalTemp(@PathVariable(name = "approvalNo") String approvalNo,
                                                          @RequestPart(name = "approvalDTO") ApprovalDTO approvalDTO,
                                                          @RequestPart(name = "multipartFile", required = false) List<MultipartFile> multipartFile) {

        log.info("🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉재 임시저장 컨트롤러 들어왔어");

        log.info("기존 approval Form : " + approvalNo.substring(5, 8));
        log.info("새로운 approval Form : " + approvalDTO.getFormNo());


        approvalDTO.setApprovalNo(approvalNo);


        //기안자사번
        //현재 사용자의 인증 정보 가져오기
        int memberId = 0;

        //현재 사용자의 인증 정보 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("memberId: " + authentication.getName());

        //인증 정보에서 사용자의 식별 정보 가져오기
        memberId = Integer.parseInt(authentication.getName());


        log.info("현재 사용자 : " + memberId);

        approvalDTO.setMemberId(memberId);


        //결재자번호(결재번호+_apr+순번)
        List<ApproverDTO> approverDTOList = approvalDTO.getApprover();
        for (int i = 0; i < approverDTOList.size(); i++) {
            ApproverDTO approverDTO = approverDTOList.get(i);
            approverDTO.setApproverNo(approvalDTO.getApprovalNo() + "_apr" + String.format("%03d", (i + 1)));
            approverDTO.setApprovalNo(approvalDTO.getApprovalNo());
            approverDTO.setApproverStatus("대기");
            approverDTO.setApproverOrder(i + 1);
        }
        approvalDTO.setApprover(approverDTOList);

        //참조자번호(결재번호+_ref+순번)
        List<ReferencerDTO> referencerDTOList = approvalDTO.getReferencer();
        for (int i = 0; i < referencerDTOList.size(); i++) {
            ReferencerDTO referencerDTO = referencerDTOList.get(i);
            referencerDTO.setRefNo(approvalDTO.getApprovalNo() + "_ref" + String.format("%03d", (i + 1)));
            referencerDTO.setApprovalNo(approvalDTO.getApprovalNo());
            referencerDTO.setRefOrder(i + 1);
        }
        approvalDTO.setReferencer(referencerDTOList);

        List<AttachmentDTO> attachmentDTOList = new ArrayList<>();

        String savePath = UPLOAD_DIR + FILE_DIR;

        //첨부파일번호(결재번호+_f+순번)
//        if(multipartFile == null){
//            multipartFile = Collections.emptyList();
//        }

        if (multipartFile != null && !multipartFile.isEmpty()) {
            log.info("multipartFile 있나요 : " + !multipartFile.isEmpty());
            for (int i = 0; i < multipartFile.size(); i++) {
                MultipartFile oneFile = multipartFile.get(i);

                AttachmentDTO attachmentDTO = new AttachmentDTO();
                attachmentDTO.setFileNo(approvalDTO.getApprovalNo() + "_f" + String.format("%03d", (i + 1)));
                attachmentDTO.setFileOriname(oneFile.getOriginalFilename());
                attachmentDTO.setFileSavename(oneFile.getName());
                attachmentDTO.setFileSavepath(savePath);
                attachmentDTO.setApprovalNo(approvalDTO.getApprovalNo());

                attachmentDTOList.add(attachmentDTO);
            }
            approvalDTO.setAttachment(attachmentDTOList);
        }
        ApprovalDTO result = null;

        try {
            result = approvalService.updateApproval(approvalNo, approvalDTO, multipartFile);
            log.info("결재 임시저장 수정 결과 성공: " + result);
            return ResponseEntity.ok().body(new ResponseDTO(HttpStatus.OK, "결재 임시저장 수정 결과 성공", result));

        } catch (Exception e) {
            log.info("결재 임시저장 수정 결과 실패 : " + result);
            return ResponseEntity.badRequest().body(new ResponseDTO(HttpStatus.OK, e.getMessage(), result));
        }

    }

    @Tag(name = "전자결재 기안", description = "기안")
    @PostMapping("/approvals")
    public ResponseEntity<ResponseDTO> insertApproval(@RequestPart("approvalDTO") ApprovalDTO approvalDTO,
                                                      @RequestPart(value = "multipartFile", required = false) List<MultipartFile> multipartFile,
                                                      @RequestHeader(name = "memberId", required = false) String memberIdstr) {

        log.info("🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉등록 컨트롤러 들어왔어");
        System.out.println("🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉컨트롤러 들어왔어");


        // 처음부터 처리 : approvalNo 가 없음 => 기안/임시저장 됨
        // 임시저장 -> 기안 : approvalNo 가 있음 => 기존 approvalNo 를 꺼내서 ims 일 경우 삭제
        // 기존 approvalNo
        String originApprovalNo = approvalDTO.getApprovalNo();

        if(originApprovalNo != null){
            //기존 결재번호가 있다면
            log.info("기존 결재번호 있단다 : " + originApprovalNo);
            //임시저장(ims) 이었다면
            String wasTemp =  originApprovalNo.substring(5, 8);
            log.info("기존 폼 번호 : " + wasTemp);

            if(wasTemp.equals("ims")){
                //기존 전자결재 삭제
                approvalService.approvalDelete(originApprovalNo);
                log.info("기존 전자결재 삭제됨 ! : "+ originApprovalNo);
            }
        }

        String approvalStatus = approvalDTO.getApprovalStatus();
        String YearFormNo = "";
        int Year = LocalDate.now().getYear();

        //전자결재 번호(연도+_양식번호+순번)
        String formNo = approvalDTO.getFormNo();

        if(approvalStatus.equals("임시저장")) {
            //전자결재 번호(연도+_ims+순번)
            formNo = "ims";
        }

        YearFormNo = Year + "-" + formNo;
        log.info("YearFormNo : " + YearFormNo);

        String lastApprovalNo = approvalService.selectApprovalNo(YearFormNo);

        log.info("lastApprovalNo : " + lastApprovalNo);

        String approvalNo = "";
        if(lastApprovalNo != null){
            String[] parts = lastApprovalNo.split("-");
            String lastPart = parts[parts.length - 1];


            String sequenceString = lastPart.replaceAll("\\D", "");
            log.info("sequenceString: " + sequenceString);

            int sequenceNumber = Integer.parseInt(sequenceString) + 1;
            log.info("늘어난 번호 : " + sequenceNumber);


            approvalNo = Year + "-" + formNo + String.format("%05d", sequenceNumber);
            log.info("approvalNo: " + approvalNo);
        }
        else{
            approvalNo = Year + "-" + formNo + String.format("%05d", 1);
        }

        System.out.println("등록 하는 전자결재 번호 : " + approvalNo);

        approvalDTO.setApprovalNo(approvalNo);


        //기안자사번
        //현재 사용자의 인증 정보 가져오기
        int memberId = 0;

        if (memberIdstr == null) {
            //현재 사용자의 인증 정보 가져오기
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            log.info("memberId: " + authentication.getName());

            //인증 정보에서 사용자의 식별 정보 가져오기
            memberId = Integer.parseInt(authentication.getName());

        } else {
            memberId = Integer.parseInt(memberIdstr);
        }
        log.info("현재 사용자 : " + memberId);

        approvalDTO.setMemberId(memberId);


        //결재자번호(결재번호+_apr+순번)
        List<ApproverDTO> approverDTOList = approvalDTO.getApprover();
        for (int i = 0; i < approverDTOList.size(); i++) {
            ApproverDTO approverDTO = approverDTOList.get(i);
            approverDTO.setApproverNo(approvalNo + "_apr" + String.format("%03d", (i + 1)));
            approverDTO.setApprovalNo(approvalNo);
            approverDTO.setApproverStatus("대기");
            approverDTO.setApproverOrder(i + 1);
        }
        approvalDTO.setApprover(approverDTOList);

        //참조자번호(결재번호+_ref+순번)
        List<ReferencerDTO> referencerDTOList = approvalDTO.getReferencer();
        for (int i = 0; i < referencerDTOList.size(); i++) {
            ReferencerDTO referencerDTO = referencerDTOList.get(i);
            referencerDTO.setRefNo(approvalNo + "_ref" + String.format("%03d", (i + 1)));
            referencerDTO.setApprovalNo(approvalNo);
            referencerDTO.setRefOrder(i + 1);
        }
        approvalDTO.setReferencer(referencerDTOList);

        List<AttachmentDTO> attachmentDTOList = new ArrayList<>();

        String savePath = UPLOAD_DIR + FILE_DIR;

        //첨부파일번호(결재번호+_f+순번)
//        if(multipartFile == null){
//            multipartFile = Collections.emptyList();
//        }

        if (multipartFile != null && !multipartFile.isEmpty()) {
            log.info("multipartFile 있나요 : " + !multipartFile.isEmpty());
            for (int i = 0; i < multipartFile.size(); i++) {
                MultipartFile oneFile = multipartFile.get(i);

                AttachmentDTO attachmentDTO = new AttachmentDTO();
                attachmentDTO.setFileNo(approvalNo + "_f" + String.format("%03d", (i + 1)));
                attachmentDTO.setFileOriname(oneFile.getOriginalFilename());
                attachmentDTO.setFileSavename(oneFile.getName());
                attachmentDTO.setFileSavepath(savePath);
                attachmentDTO.setApprovalNo(approvalNo);

                attachmentDTOList.add(attachmentDTO);
            }
            approvalDTO.setAttachment(attachmentDTOList);
        }
        ApprovalDTO result = null;

        try {
            result = approvalService.insertApproval(approvalDTO, multipartFile);
            log.info("결재 기안 결과 성공: " + result);
            return ResponseEntity.ok().body(new ResponseDTO(HttpStatus.OK, "전자결재 기안 성공", result));

        } catch (Exception e) {
            log.info("결재 기안 결과 실패 : " + result);
            return ResponseEntity.badRequest().body(new ResponseDTO(HttpStatus.OK, e.getMessage(), result));
        }

    }

    @Tag(name = "전자결재 처리", description = "결재처리")
    @PutMapping("/approvers/{approverNo}")
    public ResponseEntity<ResponseDTO> updateApprover(@PathVariable(name = "approverNo") String approverNo,
                                                      @RequestBody ApproverDTO approverDTO) {


        log.info("🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉결재 컨트롤러 들어왔어");

        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("approverStatus", approverDTO.getApproverStatus());
        statusMap.put("rejectReason", approverDTO.getRejectReason());

        return ResponseEntity.ok().body(new ResponseDTO(HttpStatus.OK, "전자결재" + approverDTO.getApproverStatus() + "처리 완료",
                approvalService.updateApprover(approverNo, statusMap)));
    }

    @Tag(name = "전자결재 삭제", description = "전자결재 임시저장 삭제")
    @DeleteMapping("/approvals/{approvalNo}")
    public ResponseEntity<ResponseDTO> deleteApproval(@PathVariable(name = "approvalNo") String approvalNo) {

        return ResponseEntity.ok().body(new ResponseDTO(HttpStatus.OK, "전자결재 삭제 성공",
                approvalService.approvalDelete(approvalNo)));
    }


    @GetMapping("/approvals/members/{memberId}")
    public ResponseEntity<ResponseDTO> selectMember(@PathVariable(name = "memberId") int memberId) {

        return ResponseEntity.ok().body(new ResponseDTO(HttpStatus.OK, "사원 조회 성공",
                approvalService.selectMember(memberId)));
    }

    @GetMapping("/approvals/members")
    public ResponseEntity<ResponseDTO> selectAllMembers() {
        return ResponseEntity.ok().body(new ResponseDTO(HttpStatus.OK, "전 사원 부서순 조회 성공",
                approvalService.selectAllMemberList()));


    }

    @Tag(name = "파일 다운로드", description = "파일 다운로드")
    @GetMapping("/approvals/files")
    public ResponseEntity<Resource> dounloadFile(@RequestParam(name = "fileSavepath") String fileSavepath,
                                                 @RequestParam(name = "fileSavename") String fileSavename,
                                                 @RequestParam(name = "fileOriname") String fileOriname){

        System.out.println("🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈파일 컨트롤러 들어왔어🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈🎈");
        try{

            System.out.println("fileSavepath : " + fileSavepath +", fileSavename : " + fileSavename + ", fileOriname :" + fileOriname);
            Path filePath = Paths.get(fileSavepath).resolve(fileSavename).normalize();
            System.out.println("filePath: " + filePath);
            Resource resource = new UrlResource(filePath.toUri());

            if(resource.exists()){
                //파일의 MIME 타입을 감지
                String contentType = Files.probeContentType(filePath);

                System.out.println("파일 타입 : " + contentType);

                if(contentType == null){
                    contentType = "application/octet-stream";
                }

                //파일 이름 인코딩
                String encodedFileName = URLEncoder.encode(fileOriname, "UTF-8").replaceAll("\\+", "%20");

                //HttpHeaders 설정
                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"");
                headers.add(HttpHeaders.CONTENT_TYPE, contentType);



                return ResponseEntity.ok()
                        .headers(headers)
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(resource);
            }else{
                System.out.println("파일이 존재하지 않습니다.");
                return ResponseEntity.notFound().build();
            }
        }catch(UnsupportedEncodingException e){
            System.out.println("파일 이름 인코딩 중 오류 발생 : " + e.getMessage());
            return ResponseEntity.badRequest().build();

        }catch(Exception e){
            System.out.println("파일 다운로드 중 오류 발생: " + e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
