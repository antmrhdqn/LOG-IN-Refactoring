package com.insider.login.approval.service.generator;

import com.insider.login.approval.repository.ApprovalRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 전자결재 관련 각종 번호(결재번호·결재자번호·참조자번호·첨부파일번호) 채번을 단독으로 소유하는 컴포넌트.
 * 채번 규칙(연도-폼번호-순번, 하위 순번 포맷)은 이 클래스만 안다.
 */
@Component
public class ApprovalNoGenerator {

    private final ApprovalRepository approvalRepository;

    public ApprovalNoGenerator(ApprovalRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    public String nextApprovalNo(int year, String formNo) {
        String yearFormNo = year + "-" + formNo;

        Pageable pageable = PageRequest.of(0, 1);
        List<String> results = approvalRepository.findLastApprovalNo(yearFormNo, pageable);
        String lastApprovalNo = results.isEmpty() ? null : results.get(0);

        String approvalNo;
        if (lastApprovalNo != null) {
            String[] parts = lastApprovalNo.split("-");
            String lastPart = parts[parts.length - 1];

            String sequenceString = lastPart.replaceAll("\\D", "");
            int sequenceNumber = Integer.parseInt(sequenceString) + 1;

            approvalNo = year + "-" + formNo + String.format("%05d", sequenceNumber);
        } else {
            approvalNo = year + "-" + formNo + String.format("%05d", 1);
        }

        return approvalNo;
    }

    public String senderApproverNo(String approvalNo) {
        return approvalNo + "_apr000";
    }

    public String approverNo(String approvalNo, int order) {
        return approvalNo + "_apr" + String.format("%03d", order);
    }

    public String referencerNo(String approvalNo, int order) {
        return approvalNo + "_ref" + String.format("%03d", order);
    }

    public String fileNo(String approvalNo, int index) {
        return approvalNo + "_f" + String.format("%03d", index);
    }
}
