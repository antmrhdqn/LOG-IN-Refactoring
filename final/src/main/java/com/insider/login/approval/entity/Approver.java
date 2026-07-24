package com.insider.login.approval.entity;

import com.insider.login.approval.enums.ApproverStatus;
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

@Entity(name = "Approver")
@Table(name="APPROVER")
@Getter
public class Approver {

    //Approver엔티티

    @Id
    @Column(name="APPROVER_NO")
    private String approverNo;          // 결재자 번호

    @Column(name="APPROVAL_NO")
    private String approvalNo;          // 결재 번호

    @Column(name="APPROVER_ORDER")
    private int approverOrder;          // 결재 순번

    @Enumerated(EnumType.STRING)
    @Column(name="APPROVER_STATUS", nullable = false)
    private ApproverStatus approverStatus;      //결재 처리 상태

    @Column(name="APPROVER_DATE")
    private LocalDateTime approverDate; // 결재처리 일시

    @Column(name="MEMBER_ID")
    private int memberId;               // 결재자 사번

    protected Approver (){}

    @Builder
    public Approver(String approverNo, String approvalNo, int approverOrder, ApproverStatus approverStatus, LocalDateTime approverDate, int memberId) {
        this.approverNo = approverNo;
        this.approvalNo = approvalNo;
        this.approverOrder = approverOrder;
        this.approverStatus = approverStatus;
        this.approverDate = approverDate;
        this.memberId = memberId;
    }

    public Approver(String approverNo, String approvalNo, int approverOrder, String approverStatus, LocalDateTime approverDate, int memberId) {
        this(approverNo, approvalNo, approverOrder, ApproverStatus.from(approverStatus), approverDate, memberId);
    }

    public void approve() {
        if (!approverStatus.canTransitionTo(ApproverStatus.APPROVED)) {
            throw new BusinessException(ErrorCode.APPROVER_INVALID_STATUS_TRANSITION);
        }
        this.approverStatus = ApproverStatus.APPROVED;
        this.approverDate = LocalDateTime.now();
    }

    public void reject() {
        if (!approverStatus.canTransitionTo(ApproverStatus.REJECTED)) {
            throw new BusinessException(ErrorCode.APPROVER_INVALID_STATUS_TRANSITION);
        }
        this.approverStatus = ApproverStatus.REJECTED;
        this.approverDate = LocalDateTime.now();
    }
}
