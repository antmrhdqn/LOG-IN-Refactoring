package com.insider.login.approval.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ApproverDTO {

    //ApproverDTO

    private String approverNo;              //결재자 번호
    private String approvalNo;              //결재번호
    private int approverOrder;              //결재 순번
    private String approverStatus;          //결재 처리 상태
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approverDate;     //결재처리일시
    private int memberId;                   //결재자 사번

    private String name;                    //결재자 이름
    private String positionName;            //결재자 직급명
    private String departName;              //결재자 부서명

    private String rejectReason;            //결재자 반려사유

    public ApproverDTO () {}

    public ApproverDTO(String approverNo, String approvalNo, int approverOrder, String approverStatus, LocalDateTime approverDate, int memberId) {
        this.approverNo = approverNo;
        this.approvalNo = approvalNo;
        this.approverOrder = approverOrder;
        this.approverStatus = approverStatus;
        this.approverDate = approverDate;
        this.memberId = memberId;
    }

    public ApproverDTO(String approverNo, String approvalNo, int approverOrder, String approverStatus, LocalDateTime approverDate, int memberId, String name, String positionName, String departName, String rejectReason) {
        this.approverNo = approverNo;
        this.approvalNo = approvalNo;
        this.approverOrder = approverOrder;
        this.approverStatus = approverStatus;
        this.approverDate = approverDate;
        this.memberId = memberId;
        this.name = name;
        this.positionName = positionName;
        this.departName = departName;
        this.rejectReason = rejectReason;
    }

}
