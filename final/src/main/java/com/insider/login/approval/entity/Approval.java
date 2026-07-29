package com.insider.login.approval.entity;

import com.insider.login.approval.enums.ApprovalStatus;
import com.insider.login.common.error.ErrorCode;
import com.insider.login.common.error.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity(name="Approval")
@Table(name="APPROVAL")
@Getter
public class Approval {
    //approval 엔티티

    @Id
    @Column(name= "APPROVAL_NO")
    private String approvalNo;          //결재번호

    @Column(name="MEMBER_ID")
    private int memberId;               //기안자사번

    @Column(name="APPROVAL_TITLE")
    private String approvalTitle;       //제목

    @Column(name="APPROVAL_CONTENT")
    private String approvalContent;     //기안 내용

    @Column(name="APPROVAL_DATE")
    private LocalDateTime approvalDate; //작성 일시

    @Enumerated(EnumType.STRING)
    @Column(name="APPROVAL_STATUS", nullable = false)
    private ApprovalStatus approvalStatus;      //상태

    @Column(name="REJECT_REASON")
    private String rejectReason;        //반려사유

    @Column(name="FORM_NO")
    private String formNo;             //양식번호

    protected Approval(){}

    @Builder
    public Approval(String approvalNo, int memberId, String approvalTitle, String approvalContent, LocalDateTime approvalDate, ApprovalStatus approvalStatus, String rejectReason, String formNo) {
        this.approvalNo = approvalNo;
        this.memberId = memberId;
        this.approvalTitle = approvalTitle;
        this.approvalContent = approvalContent;
        this.approvalDate = approvalDate;
        this.approvalStatus = approvalStatus;
        this.rejectReason = rejectReason;
        this.formNo = formNo;
    }

    public Approval(String approvalNo, int memberId, String approvalTitle, String approvalContent, LocalDateTime approvalDate, String approvalStatus, String rejectReason, String formNo) {
        this(approvalNo, memberId, approvalTitle, approvalContent, approvalDate, ApprovalStatus.from(approvalStatus), rejectReason, formNo);
    }

    public void withdraw(int memberId) {
        if (this.memberId != memberId) {
            throw new BusinessException(ErrorCode.APPROVAL_WITHDRAW_NOT_OWNER);
        }
        if (!approvalStatus.canTransitionTo(ApprovalStatus.WITHDRAWN)) {
            throw new BusinessException(ErrorCode.APPROVAL_INVALID_STATUS_TRANSITION);
        }
        this.approvalStatus = ApprovalStatus.WITHDRAWN;
    }

    /**
     * 임시저장 상태의 기안 내용을 수정한다(삭제 후 재생성이 아니라 dirty checking 으로 갱신).
     * 반려사유와 기안자는 유지한다.
     */
    public void modifyDraft(String approvalTitle, String approvalContent, String formNo) {
        if (approvalStatus != ApprovalStatus.TEMP_SAVED) {
            throw new BusinessException(ErrorCode.APPROVAL_MODIFY_NOT_ALLOWED);
        }
        this.approvalTitle = approvalTitle;
        this.approvalContent = approvalContent;
        this.formNo = formNo;
        this.approvalDate = LocalDateTime.now();
    }

    public void submitFromTempSaved() {
        if (!approvalStatus.canTransitionTo(ApprovalStatus.PROCESSING)) {
            throw new BusinessException(ErrorCode.APPROVAL_INVALID_STATUS_TRANSITION);
        }
        this.approvalStatus = ApprovalStatus.PROCESSING;
    }

    public void markAsApproved() {
        if (!approvalStatus.canTransitionTo(ApprovalStatus.APPROVED)) {
            throw new BusinessException(ErrorCode.APPROVAL_INVALID_STATUS_TRANSITION);
        }
        this.approvalStatus = ApprovalStatus.APPROVED;
    }

    public void markAsRejected(String rejectReason) {
        if (!approvalStatus.canTransitionTo(ApprovalStatus.REJECTED)) {
            throw new BusinessException(ErrorCode.APPROVAL_INVALID_STATUS_TRANSITION);
        }
        this.approvalStatus = ApprovalStatus.REJECTED;
        this.rejectReason = rejectReason;
    }
}
