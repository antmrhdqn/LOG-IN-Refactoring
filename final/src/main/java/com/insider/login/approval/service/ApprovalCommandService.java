package com.insider.login.approval.service;

import com.insider.login.approval.dto.ApprovalDTO;
import com.insider.login.approval.dto.ApproverDTO;
import com.insider.login.approval.dto.ReferencerDTO;
import com.insider.login.approval.entity.Approval;
import com.insider.login.approval.entity.Approver;
import com.insider.login.approval.entity.Referencer;
import com.insider.login.approval.enums.ApprovalStatus;
import com.insider.login.approval.enums.ApproverStatus;
import com.insider.login.approval.repository.ApprovalRepository;
import com.insider.login.approval.repository.ApproverRepository;
import com.insider.login.approval.repository.AttachmentRepository;
import com.insider.login.approval.repository.ReferencerRepository;
import com.insider.login.approval.service.file.ApprovalFileService;
import com.insider.login.approval.service.generator.ApprovalNoGenerator;
import com.insider.login.common.error.ErrorCode;
import com.insider.login.common.error.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

import static java.time.LocalDateTime.now;

/**
 * 전자결재 쓰기 전용 서비스.
 * 기안·재임시저장·회수·결재처리·삭제를 담당한다. 조회 응답 조립은 {@link ApprovalQueryService} 에 위임한다.
 */
@Service
@Transactional
@Slf4j
public class ApprovalCommandService {

    //임시저장 결재에 부여하는 양식번호
    private static final String TEMP_SAVED_FORM_NO = "ims";

    //기안자가 결재자로 자동 등록될 때의 결재 순번
    private static final int SENDER_APPROVER_ORDER = 0;

    private final ApprovalRepository approvalRepository;
    private final ApproverRepository approverRepository;
    private final AttachmentRepository attachmentRepository;
    private final ReferencerRepository referencerRepository;
    private final ApprovalFileService approvalFileService;
    private final ApprovalNoGenerator approvalNoGenerator;
    private final ApprovalQueryService approvalQueryService;

    public ApprovalCommandService(
            ApprovalRepository approvalRepository,
            ApproverRepository approverRepository,
            AttachmentRepository attachmentRepository,
            ReferencerRepository referencerRepository,
            ApprovalFileService approvalFileService,
            ApprovalNoGenerator approvalNoGenerator,
            ApprovalQueryService approvalQueryService) {
        this.approvalRepository = approvalRepository;
        this.approverRepository = approverRepository;
        this.attachmentRepository = attachmentRepository;
        this.referencerRepository = referencerRepository;
        this.approvalFileService = approvalFileService;
        this.approvalNoGenerator = approvalNoGenerator;
        this.approvalQueryService = approvalQueryService;
    }

    /**
     * 전자결재 기안(등록).
     * <p>
     * 요청에 결재번호가 실려 있고 그 결재가 임시저장 상태면 <b>같은 결재번호를 유지한 채</b> 기안으로 전환한다.
     * 그 외에는 새 결재번호를 채번해 신규로 만든다. 초기 상태는 서버가 결정한다(클라이언트 값 신뢰 금지).
     *
     * @param memberId 호출자 사번(인증 정보에서 추출). 기존 결재를 이어받는 경우에만 기안자 본인인지 확인한다.
     */
    public ApprovalDTO draft(ApprovalDTO approvalDTO, List<MultipartFile> files, int memberId) {

        ApprovalStatus initialStatus = resolveInitialStatus(approvalDTO.getApprovalStatus());

        String requestedApprovalNo = approvalDTO.getApprovalNo();

        //이미 임시저장된 결재가 실려 오면 결재번호를 유지한 채 처리한다.
        //기안으로 올릴지 임시저장으로 남길지는 요청 상태가 나르는 사용자 의도를 따른다.
        if (requestedApprovalNo != null && !requestedApprovalNo.isBlank()) {
            Approval existing = approvalRepository.findById(requestedApprovalNo).orElse(null);

            if (existing != null && existing.getApprovalStatus() == ApprovalStatus.TEMP_SAVED) {

                //남의 임시저장을 이어받는 것을 막는다. 상신이든 재저장이든 갈래를 가리기 "전"에 판정해야
                //두 경로가 함께 닫힌다.
                if (existing.getMemberId() != memberId) {
                    throw new BusinessException(ErrorCode.APPROVAL_UNAUTHORIZED);
                }

                if (initialStatus == ApprovalStatus.PROCESSING) {
                    log.info("임시저장 -> 기안 전환 : " + requestedApprovalNo);
                    //resubmit 은 private 이고 이 지점에서 이미 기안자 본인이 확인됐으므로 추가 검증을 두지 않는다
                    return resubmit(requestedApprovalNo, approvalDTO, files);
                }

                log.info("임시저장 재저장 : " + requestedApprovalNo);
                return resaveTempSaved(requestedApprovalNo, approvalDTO, files, memberId);
            }
        }

        //신규 기안 : 기안자가 곧 호출자라 대조할 상대가 없다. 여기에 소유자 검증을 넣으면 안 된다.
        //임시저장이면 양식번호를 ims 로 대체해 채번한다
        String formNo = (initialStatus == ApprovalStatus.TEMP_SAVED) ? TEMP_SAVED_FORM_NO : approvalDTO.getFormNo();
        String approvalNo = approvalNoGenerator.nextApprovalNo(LocalDate.now().getYear(), formNo);

        Approval approval = Approval.builder()
                .approvalNo(approvalNo)
                .memberId(approvalDTO.getMemberId())
                .approvalTitle(approvalDTO.getApprovalTitle())
                .approvalContent(approvalDTO.getApprovalContent())
                .approvalDate(now())
                .approvalStatus(initialStatus)
                .rejectReason(approvalDTO.getRejectReason())
                .formNo(approvalDTO.getFormNo())
                .build();

        //merge 가 아닌 persist 로 저장한다 (같은 번호 충돌 시 조용한 덮어쓰기가 아니라 롤백)
        approvalRepository.insert(approval);

        createChildren(approvalNo, approvalDTO);

        log.info("첨부파일 비어있음? " + (files == null || files.isEmpty()));
        approvalFileService.store(approvalNo, files);

        return approvalQueryService.getApproval(approvalNo);
    }

    /**
     * 전자결재 임시저장 재저장.
     * <p>
     * 기존 결재를 삭제하지 않고 내용만 갱신한다(결재번호·이력 보존).
     * 요청의 상태값은 사용하지 않는다 — 임시저장은 임시저장으로 남는다.
     *
     * @param memberId 호출자 사번(인증 정보에서 추출). 기안자 본인만 수정할 수 있다.
     */
    public ApprovalDTO resaveTempSaved(String approvalNo, ApprovalDTO approvalDTO, List<MultipartFile> files, int memberId) {

        Approval approval = approvalRepository.findById(approvalNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        //상태보다 신원을 먼저 본다. 순서가 반대면 남의 문서의 상태를 알려주게 된다.
        if (approval.getMemberId() != memberId) {
            throw new BusinessException(ErrorCode.APPROVAL_UNAUTHORIZED);
        }

        //디스크 파일을 지우기 "전"에 막아야 한다. 롤백은 DB만 되돌리고 삭제된 파일은 되돌리지 못한다.
        if (approval.getApprovalStatus() != ApprovalStatus.TEMP_SAVED) {
            throw new BusinessException(ErrorCode.APPROVAL_MODIFY_NOT_ALLOWED);
        }

        clearChildren(approvalNo);

        //영속성 컨텍스트를 비웠으므로 다시 조회해서 관리 상태로 만든다
        Approval managedApproval = approvalRepository.findById(approvalNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        managedApproval.modifyDraft(approvalDTO.getApprovalTitle(), approvalDTO.getApprovalContent(), approvalDTO.getFormNo());

        createChildren(approvalNo, approvalDTO);

        log.info("첨부파일 비어있음? " + (files == null || files.isEmpty()));
        approvalFileService.store(approvalNo, files);

        return approvalQueryService.getApproval(approvalNo);
    }

    /**
     * 전자결재 회수.
     * 기안자 본인만 가능하며, 기안자를 제외한 결재자가 한 사람이라도 처리했으면 회수할 수 없다.
     */
    public ApprovalDTO withdraw(String approvalNo, int memberId) {

        Approval approval = approvalRepository.findById(approvalNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        //기안자(순번 0)는 등록 시점부터 승인 상태이므로 판정에서 제외한다
        boolean processedByOthers = approverRepository.findByApprovalNo(approvalNo).stream()
                .filter(approver -> approver.getApproverOrder() != SENDER_APPROVER_ORDER)
                .anyMatch(approver -> approver.getApproverStatus() != ApproverStatus.PENDING);

        if (processedByOthers) {
            throw new BusinessException(ErrorCode.APPROVAL_WITHDRAW_ALREADY_PROCESSED);
        }

        approval.withdraw(memberId);

        return approvalQueryService.getApproval(approvalNo);
    }

    /**
     * 전자결재 처리(승인/반려).
     * <p>
     * 승인은 <b>모든 결재자가 승인</b>했을 때에만 전체 결재를 승인으로 바꾼다(순번 인덱스 판정 아님).
     * 반려는 순서와 무관하게 즉시 전체 결재를 반려로 바꾼다.
     *
     * @param memberId 호출자 사번(인증 정보에서 추출). 지정 결재자 본인만 처리할 수 있다.
     */
    public ApproverDTO processApprover(String approverNo, ApproverDTO approverDTO, int memberId) {

        log.info("결재 처리 : " + approverNo);

        Approver approver = approverRepository.findByApproverNo(approverNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVER_NOT_FOUND));

        //지정 결재자 본인이 아니면 상태를 해석하기 전에 막는다.
        //처리자는 Approver 행의 memberId 로 기록되므로, 막지 않으면 남의 이름으로 감사 기록이 남는다.
        if (approver.getMemberId() != memberId) {
            throw new BusinessException(ErrorCode.APPROVAL_UNAUTHORIZED);
        }

        String approvalNo = approver.getApprovalNo();

        Approval approval = approvalRepository.findById(approvalNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        ApproverStatus nextStatus = ApproverStatus.from(approverDTO.getApproverStatus());

        if (nextStatus == ApproverStatus.APPROVED) {
            approver.approve();

            //재조회 시점의 auto-flush 로 위 승인이 반영된다
            boolean allApproved = approverRepository.findByApprovalNoOrderByApproverOrderAsc(approvalNo).stream()
                    .allMatch(each -> each.getApproverStatus() == ApproverStatus.APPROVED);

            if (allApproved) {
                approval.markAsApproved();
            }
        } else if (nextStatus == ApproverStatus.REJECTED) {
            approver.reject();
            approval.markAsRejected(approverDTO.getRejectReason());
        }

        return approvalQueryService.getApproval(approvalNo).getApprover().stream()
                .filter(dto -> approverNo.equals(dto.getApproverNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVER_NOT_FOUND));
    }

    /**
     * 전자결재 삭제.
     * 중간에 실패하면 예외를 전파해 트랜잭션 전체를 롤백한다(부분 삭제 커밋 금지).
     *
     * @param memberId 호출자 사번(인증 정보에서 추출). 기안자 본인만 삭제할 수 있다.
     */
    public boolean delete(String approvalNo, int memberId) {

        log.info("전자결재 삭제 : " + approvalNo);

        //디스크 파일 삭제보다 "앞"이다. 롤백은 DB만 되돌리고 삭제된 파일은 되돌리지 못한다.
        Approval approval = approvalRepository.findById(approvalNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        if (approval.getMemberId() != memberId) {
            throw new BusinessException(ErrorCode.APPROVAL_UNAUTHORIZED);
        }

        approvalFileService.deleteByApprovalNo(approvalNo);      //첨부파일 디스크 삭제
        attachmentRepository.deleteByApprovalNo(approvalNo);     //첨부파일 DB 삭제
        referencerRepository.deleteByApprovalNo(approvalNo);     //참조선 삭제
        approverRepository.deleteByApprovalNo(approvalNo);       //결재선 삭제
        approvalRepository.deleteById(approvalNo);               //전자결재 삭제

        return true;
    }

    /**
     * 임시저장 -> 기안 전환. 결재번호를 유지한 채 내용을 갱신하고 상태만 전이시킨다.
     * 결재자·참조자·첨부는 이번 요청 내용으로 교체한다.
     */
    private ApprovalDTO resubmit(String approvalNo, ApprovalDTO approvalDTO, List<MultipartFile> files) {

        clearChildren(approvalNo);

        Approval approval = approvalRepository.findById(approvalNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        approval.modifyDraft(approvalDTO.getApprovalTitle(), approvalDTO.getApprovalContent(), approvalDTO.getFormNo());
        approval.submitFromTempSaved();

        createChildren(approvalNo, approvalDTO);

        log.info("첨부파일 비어있음? " + (files == null || files.isEmpty()));
        approvalFileService.store(approvalNo, files);

        return approvalQueryService.getApproval(approvalNo);
    }

    /**
     * 결재자·참조자·첨부를 전량 삭제한다.
     * <p>
     * 디스크 파일 삭제를 첨부 DB 행 삭제보다 <b>먼저</b> 수행해야 한다 — 순서가 반대면 삭제 대상 조회가
     * 0건이 되어 옛 파일이 디스크에 영원히 남는다.
     * 삭제 후 영속성 컨텍스트를 비운다(같은 PK 로 다시 저장할 때 merge 가 삭제된 행을 UPDATE 하는 것을 막는다).
     */
    private void clearChildren(String approvalNo) {

        approvalFileService.deleteByApprovalNo(approvalNo);
        attachmentRepository.deleteByApprovalNo(approvalNo);
        approverRepository.deleteByApprovalNo(approvalNo);
        referencerRepository.deleteByApprovalNo(approvalNo);

        approvalRepository.clearPersistenceContext();
    }

    /**
     * 기안자(순번 0, 승인 상태) + 결재선 + 참조선을 만든다. 각 번호는 채번 컴포넌트가 소유한다.
     */
    private void createChildren(String approvalNo, ApprovalDTO approvalDTO) {

        //추가한 결재자 이외 기안자도 결재자에 넣기 => 첫 결재자(기안자)는 결재처리 상태를 승인으로
        Approver senderApprover = Approver.builder()
                .approverNo(approvalNoGenerator.senderApproverNo(approvalNo))
                .approvalNo(approvalNo)
                .approverOrder(SENDER_APPROVER_ORDER)
                .approverStatus(ApproverStatus.APPROVED)
                .approverDate(now())
                .memberId(approvalDTO.getMemberId())
                .build();

        approverRepository.save(senderApprover);

        //결재선 꺼내기
        List<ApproverDTO> approverDTOList = approvalDTO.getApprover();
        for (int i = 0; i < approverDTOList.size(); i++) {

            Approver approver = Approver.builder()
                    .approverNo(approvalNoGenerator.approverNo(approvalNo, i + 1))
                    .approvalNo(approvalNo)
                    .approverOrder(i + 1)
                    .approverStatus(ApproverStatus.PENDING)
                    .approverDate(null)
                    .memberId(approverDTOList.get(i).getMemberId())
                    .build();

            approverRepository.save(approver);
        }

        //참조선 꺼내기
        List<ReferencerDTO> referencerDTOList = approvalDTO.getReferencer();
        for (int i = 0; i < referencerDTOList.size(); i++) {

            Referencer referencer = new Referencer(
                    approvalNoGenerator.referencerNo(approvalNo, i + 1),
                    approvalNo,
                    referencerDTOList.get(i).getMemberId(),
                    i + 1
            );

            referencerRepository.save(referencer);
        }
    }

    /**
     * 신규 결재의 초기 상태를 서버가 결정한다.
     * 영문·한글 표기를 모두 받아들이되 임시저장/처리 중 두 가지만 허용한다.
     */
    private ApprovalStatus resolveInitialStatus(String requestedStatus) {

        ApprovalStatus status;
        try {
            status = ApprovalStatus.from(requestedStatus);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.APPROVAL_INVALID_INITIAL_STATUS);
        }

        if (status != ApprovalStatus.TEMP_SAVED && status != ApprovalStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.APPROVAL_INVALID_INITIAL_STATUS);
        }

        return status;
    }
}
