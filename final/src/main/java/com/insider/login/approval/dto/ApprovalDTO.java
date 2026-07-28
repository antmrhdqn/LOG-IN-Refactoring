package com.insider.login.approval.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class ApprovalDTO {

    //ApprovalDTO

    // 결재번호, 기안자 사번(*member_info), 제목, 기안 내용, 작성일시, 상태, 반려사유, 양식번호(*form),
    // 첨부파일(*List(apr_attachment)), 결재선(*List(approver)), 참조선(*List(referencer))



    private String approvalNo;                  //결재번호
    private int memberId;                       //기안자사번
    private String approvalTitle;               //제목
    private String approvalContent;             //기안 내용
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvalDate;          //작성일시
    private String approvalStatus;              //상태
    private String rejectReason;                //반려사유
    private String formNo;                      //양식번호

    private String formName;                    //양식이름

    private String departName;                  //기안자 부서
    private String name;                        //기안자 이름
    private String positionName;                //기안자 직급명


    private List<AttachmentDTO> attachment;     //첨부파일
    private List<ApproverDTO> approver;         //결재선
    private List<ReferencerDTO> referencer;     //참조선

    private String finalApproverDate;                //최종승인날짜
    private String standByApprover;             //진행중(대기)인 사람

    public ApprovalDTO(){}


    public ApprovalDTO(String approvalNo, int memberId, String approvalTitle, String approvalContent, LocalDateTime approvalDate, String approvalStatus, String rejectReason, String formNo, String formName, String departName, String name, String positionName, List<AttachmentDTO> attachment, List<ApproverDTO> approver, List<ReferencerDTO> referencer, String approverDate, String standByApprover) {
        this.approvalNo = approvalNo;
        this.memberId = memberId;
        this.approvalTitle = approvalTitle;
        this.approvalContent = approvalContent;
        this.approvalDate = approvalDate;
        this.approvalStatus = approvalStatus;
        this.rejectReason = rejectReason;
        this.formNo = formNo;
        this.formName = formName;
        this.departName = departName;
        this.name = name;
        this.positionName = positionName;
        this.attachment = attachment;
        this.approver = approver;
        this.referencer = referencer;
        this.finalApproverDate = approverDate;
        this.standByApprover = standByApprover;
    }

}
