package com.insider.login.approval.service;

import com.insider.login.approval.dto.*;
import com.insider.login.approval.entity.*;
import com.insider.login.approval.enums.ApprovalStatus;
import com.insider.login.approval.enums.ApproverStatus;
import com.insider.login.approval.repository.*;
import com.insider.login.common.error.ErrorCode;
import com.insider.login.common.error.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

/**
 * 전자결재 읽기 전용 서비스.
 * 목록·상세 조회와 양식·부서·사원 조회를 담당한다. 상태를 바꾸지 않는다.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class ApprovalQueryService {

    //목록 조회 페이지 크기 (정책 상수 — Stage 7에서 Controller의 condition Map에서 이관)
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ApprovalRepository approvalRepository;
    private final ApproverRepository approverRepository;
    private final AttachmentRepository attachmentRepository;
    private final ReferencerRepository referencerRepository;
    private final ApprovalMemberRepository approvalMemberRepository;
    private final ApprovalDepartmentRepository approvalDepartmentRepository;
    private final ApprovalPositionRepository approvalPositionRepository;
    private final FormRepository formRepository;
    private final ModelMapper modelMapper;

    public ApprovalQueryService(
            ApprovalRepository approvalRepository,
            ApproverRepository approverRepository,
            AttachmentRepository attachmentRepository,
            ReferencerRepository referencerRepository,
            ApprovalMemberRepository approvalMemberRepository,
            ApprovalDepartmentRepository approvalDepartmentRepository,
            ApprovalPositionRepository approvalPositionRepository,
            FormRepository formRepository,
            ModelMapper modelMapper) {
        this.approvalRepository = approvalRepository;
        this.approverRepository = approverRepository;
        this.attachmentRepository = attachmentRepository;
        this.referencerRepository = referencerRepository;
        this.approvalMemberRepository = approvalMemberRepository;
        this.approvalDepartmentRepository = approvalDepartmentRepository;
        this.approvalPositionRepository = approvalPositionRepository;
        this.formRepository = formRepository;
        this.modelMapper = modelMapper;
    }

    //양식 목록 조회
    public List<FormDTO> getFormList() {

        List<Form> formList = formRepository.findAll();

        return formList.stream()
                .map(form -> new FormDTO(form.getFormNo(), form.getFormName(), form.getFormShape()))
                .sorted((form1, form2) -> {
                    //non을 가장 위에 두고 그 외의 경우는 한글순으로 정렬
                    if ("non".equals(form1.getFormNo()) && !"non".equals(form2.getFormNo())) {
                        //form1이 "non"이고 form2가 "non"이 아니면 form1을 더 위로
                        return -1;
                    } else if (!"non".equals(form1.getFormNo()) && "non".equals(form2.getFormNo())) {
                        //form1이 "non"이 아니고 form2가 "non"이면 form2를 더 위로
                        return 1;
                    } else {
                        return form1.getFormName().compareTo(form2.getFormName());
                    }
                })
                .collect(Collectors.toList());
    }

    //양식번호로 양식 조회
    public FormDTO getForm(String formNo) {

        Form form = formRepository.findByFormNo(formNo)
                .orElseThrow(() -> new RuntimeException("양식을 찾을 수 없습니다. : " + formNo));

        return modelMapper.map(form, FormDTO.class);
    }

    //부서목록조회
    public List<DepartmentDTO> getDepartList() {

        List<DepartmentDTO> departmentDTOList = new ArrayList<>();

        List<Department> departmentList = approvalDepartmentRepository.findAllOrderedByDepartNo();

        for (int i = 0; i < departmentList.size(); i++) {
            Department department = departmentList.get(i);
            DepartmentDTO departmentDTO = new DepartmentDTO(department.getDepartNo(), department.getDepartName());

            departmentDTOList.add(i, departmentDTO);
        }

        return departmentDTOList;
    }

    //사원목록조회
    public List<MemberDTO> getMemberList(int departNo) {

        List<MemberDTO> memberDTOList = new ArrayList<>();
        // 부서정보 : 부서번호로 조회할 경우
        Department department = approvalDepartmentRepository.findById(departNo);

        //부서별 사원 목록
        List<Member> memberList = approvalMemberRepository.findByDepart(departNo);
        //한 부서의 멤버 목록 조회
        for (Member member : memberList) {
            Position position = approvalPositionRepository.findById(member.getPositionLevel()).orElse(null);

            MemberDTO memberDTO = new MemberDTO(member.getName(),
                    member.getMemberId(),
                    member.getPassword(),
                    member.getDepartNo(),
                    member.getPositionLevel(),
                    member.getEmployedDate(),
                    member.getAddress(),
                    member.getPhoneNo(),
                    member.getMemberStatus(),
                    member.getEmail(),
                    member.getMemberRole(),
                    new String(member.getImage_url(), StandardCharsets.UTF_8),
                    department.getDepartName(),
                    position.getPositionName()
            );

            memberDTOList.add(memberDTO);
        }

        return memberDTOList;
    }

    //한 전자결재 조회
    public ApprovalDTO getApproval(String approvalNo) {

        Approval approval = approvalRepository.findById(approvalNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        //기안자 정보 가져오기
        Member senderMember = approvalMemberRepository.findById(approval.getMemberId());
        //기안자의 부서 정보 가져오기
        Department senderDepart = approvalDepartmentRepository.findById(senderMember.getDepartNo());
        //기안자의 직급 정보 가져오기
        Position senderPosition = approvalPositionRepository.findById(senderMember.getPositionLevel()).orElse(null);

        //양식 정보 가져오기
        Form form = formRepository.findByFormNo(approval.getFormNo())
                .orElseThrow(() -> new RuntimeException("양식을 찾을 수 없습니다. : " + approval.getFormNo()));

        List<ApproverDTO> approver = new ArrayList<>();
        List<ReferencerDTO> referencer = new ArrayList<>();
        List<AttachmentDTO> attachment = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        //결재선은 결재 순번 오름차순으로 조회한다
        List<Approver> approverList = approverRepository.findByApprovalNoOrderByApproverOrderAsc(approvalNo);

        for (int i = 0; i < approverList.size(); i++) {

            //결재자 정보 가져오기
            Member receiverMember = approvalMemberRepository.findById(approverList.get(i).getMemberId());
            //결재자 부서 정보 가져오기
            Department receiverDepart = approvalDepartmentRepository.findById(receiverMember.getDepartNo());

            Position receiverPosition = approvalPositionRepository.findById(receiverMember.getPositionLevel()).orElse(null);

            ApproverDTO approverDTO = new ApproverDTO(approverList.get(i).getApproverNo(), approverList.get(i).getApprovalNo(), approverList.get(i).getApproverOrder(), approverList.get(i).getApproverStatus().name(), approverList.get(i).getApproverDate(), approverList.get(i).getMemberId(), receiverMember.getName(), receiverPosition.getPositionName(), receiverDepart.getDepartName(), approval.getRejectReason());

            approver.add(approverDTO);
        }

        List<Referencer> referencerList = referencerRepository.findByApprovalNo(approvalNo);
        for (int i = 0; i < referencerList.size(); i++) {

            //참조자 정보 가져오기
            Member referencerMember = approvalMemberRepository.findById(referencerList.get(i).getMemberId());
            //참조자 부서 정보 가져오기
            Department referencerDepart = approvalDepartmentRepository.findById(referencerMember.getDepartNo());

            Position referencerPosition = approvalPositionRepository.findById(referencerMember.getPositionLevel()).orElse(null);

            ReferencerDTO referencerDTO = new ReferencerDTO(referencerList.get(i).getRefNo(), referencerList.get(i).getApprovalNo(), referencerList.get(i).getMemberId(), referencerList.get(i).getRefOrder(), referencerMember.getName(), referencerPosition.getPositionName(), referencerDepart.getDepartName());
            referencer.add(referencerDTO);
        }

        List<Attachment> attachmentList = attachmentRepository.findByApprovalNo(approvalNo);
        for (int i = 0; i < attachmentList.size(); i++) {
            AttachmentDTO attachmentDTO = new AttachmentDTO(attachmentList.get(i).getFileNo(), attachmentList.get(i).getFileOriname(), attachmentList.get(i).getFileSavepath(), attachmentList.get(i).getFileSavename(), attachmentList.get(i).getApprovalNo());
            attachment.add(attachmentDTO);
        }

        //최종 승인/반려 날짜
        String finalApproverDate = "";
        if (approval.getApprovalStatus() == ApprovalStatus.APPROVED) {
            Approver lastApprover = approverList.stream()
                    .max(Comparator.comparingInt(Approver::getApproverOrder))
                    .orElseThrow(() -> new BusinessException(ErrorCode.APPROVER_NOT_FOUND));

            finalApproverDate = lastApprover.getApproverDate().format(formatter);
        } else if (approval.getApprovalStatus() == ApprovalStatus.REJECTED) {
            //상태가 반려인 사람의 처리날짜
            Approver rejectedApprover = approverRepository.findByApprovalNoAndApproverStatus(approvalNo, ApproverStatus.REJECTED)
                    .orElseThrow(() -> new BusinessException(ErrorCode.APPROVER_NOT_FOUND));

            finalApproverDate = rejectedApprover.getApproverDate().format(formatter);
        } else if (approval.getApprovalStatus() == ApprovalStatus.TEMP_SAVED) {
            finalApproverDate = approval.getApprovalDate().format(formatter);
        }
        log.info("마지막 " + approval.getApprovalStatus() + " 날짜 :  " + finalApproverDate);

        //진행중인 사람
        Pageable pageable = PageRequest.of(0, 1);
        List<Approver> approvers = approverRepository.findStandByApproversOrderAsc(approvalNo, pageable);
        Approver standByApprover = approvers.stream().findFirst().orElse(null);

        String standByMemberName = "";

        if (standByApprover != null) {

            Member standByMember = approvalMemberRepository.findById(standByApprover.getMemberId());
            standByMemberName = standByMember.getName();
        }

        return new ApprovalDTO(approval.getApprovalNo(), approval.getMemberId(), approval.getApprovalTitle(), approval.getApprovalContent(), approval.getApprovalDate(), approval.getApprovalStatus().name(), approval.getRejectReason(), approval.getFormNo(), form.getFormName(), senderDepart.getDepartName(), senderMember.getName(), senderPosition.getPositionName(), attachment, approver, referencer, finalApproverDate, standByMemberName);
    }

    //결재 목록 페이지 조회
    public Page<ApprovalDTO> getApprovalList(int memberId, Map<String, Object> condition, int pageNo) {
        //상신함 조회 : given / member_id : 나 / 임시저장 제외 / 현재 처리중(ApprovalNo인 Approver중 '대기' 중 가장 첫번째)인 결재자(이름, 직급) 보여주기
        //임시저장함 조회 : tempGiven / member_id : 나 / 임시저장만
        //전체수신함 조회 : receivedAll : approver_id : 나 / 내 approver_order가 1이상 / 해당 approval_no의 approver의 상태 중 '대기' 상태의 마지막이 자신의 approver_order 이후일 경우에만
        //결제대기내역 조회 : received / approver_id : 나 / 내 approver_order가 1이상 / approval 상태 = 처리중만, 해당 approval_no의 approver의 상태 중 '대기' 상태의 처음이 자신의 approver_order일 경우에만
        //수신참조내역 조회 : receivedRef / referencer_id : 나 / 임시저장, 회수 제외

        List<ApprovalDTO> approvalDTOList = new ArrayList<>();

        String flag = condition.get("flag").toString();
        String title = isNull(condition.get("title")) ? "" : condition.get("title").toString();

        String direction = isNull(condition.get("direction")) ? "" : condition.get("direction").toString();

        Sort sort = Sort.by("approvalDate");
        if ("ASC".equalsIgnoreCase(direction)) {
            sort = sort.ascending();
        } else {
            sort = sort.descending();
        }

        Pageable pageable = PageRequest.of(pageNo, DEFAULT_PAGE_SIZE);
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        Page<Approval> approvalPage = null;

        switch (flag) {
            case "given": {
                //결재 상신함 (내가 기안자 / 임시저장 제외)

                approvalPage = approvalRepository.findByMemberIdAndTitle(memberId, title, sortedPageable);

                approvalDTOList = listToDTO(approvalPage);

                break;
            }
            case "tempGiven": {
                // 임시저장함 (내가 기안자 / 임시저장만)

                approvalPage = approvalRepository.findTempByMemberIdAndTitle(memberId, title, sortedPageable);

                approvalDTOList = listToDTO(approvalPage);

                break;
            }
            case "receivedAll": {
                // 전체 수신함 (내가 결재자 / )

                break;
            }
            case "received": {
                // 결재 대기함 (내가 결재자 / approvalStatus="처리 중", approverStatus = "대기" 인 approver 중 가장 첫번째의 member_id가 나의 member_id와 같은 결재번호의 전자결재)
                //1. 내차례 (approverOrder > 0)
                //- approvalNo 가 "approvalNo"인 Approver 중에 ApproverStatus 가 "대기" 인 것 중 가장 처음이 나의 Approver_order(2)일때
                //- 단, approvalStatus = 처리중 일때

                //approvalStatus = "처리 중", approverStatus = "대기" 인 approver 중 가장 낮은 approver_order의 approver 목록 가져오기
                List<Approver> approverList = approverRepository.findStandByApprovalsByTitleNative(title);

                if (approverList.size() > 0 || !approverList.isEmpty()) {
                    for (int i = 0; i < approverList.size(); i++) {
                        Approver approver = approverList.get(i);

                        //가장 낮은 approver_order의 approver의 memberId 가 나와 같다면
                        if ((approver.getMemberId() == memberId)) {
                            //해당 전자결재 번호의 전자결재 정보를 가져오기
                            Approval approval = approvalRepository.findById(approver.getApprovalNo()).orElse(null);

                            ApprovalDTO approvalDTO = getApproval(approval.getApprovalNo());

                            approvalDTOList.add(approvalDTO);
                        }
                    }
                }

                if (direction == "ASC" || direction.equals("ASC")) {
                    approvalDTOList.sort(Comparator.comparing(ApprovalDTO::getApprovalDate).reversed());
                } else {
                    approvalDTOList.sort(Comparator.comparing(ApprovalDTO::getApprovalDate));
                }

                log.info("\nSERVICE (received) 결재대기 DTO 갯수 : " + approvalDTOList.size());

                int pageSize = pageable.getPageSize();
                int pageNumber = pageable.getPageNumber();
                int startOffset = pageNumber * pageSize;
                int endOffset = Math.min(startOffset + pageSize, approvalDTOList.size());

                List<ApprovalDTO> pageContent = approvalDTOList.subList(startOffset, endOffset);

                return new PageImpl<>(pageContent, pageable, approvalDTOList.size());

            }
            case "receivedRef": {
                // 수신 참조내역 (내가 참조자 / 임시저장, 회수 제외)

                pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
                approvalPage = referencerRepository.findApprovalsByMemberIdAndTitle(memberId, title, pageable);

                approvalDTOList = listToDTO(approvalPage);

                break;
            }

        }

        //지원하지 않는 fg 값(receivedAll 포함)은 switch를 그냥 빠져나와 approvalPage가 null이다
        if (approvalPage == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        long total = approvalPage.getTotalElements();

        PageImpl<ApprovalDTO> appPage = new PageImpl<>(approvalDTOList, sortedPageable, total);

        return appPage;
    }

    //사원 정보 (기안자 정보 조회)
    public MemberDTO getMember(int memberId) {

        Member member = approvalMemberRepository.findById(memberId);
        Department department = approvalDepartmentRepository.findById(member.getDepartNo());
        Position position = approvalPositionRepository.findById(member.getPositionLevel()).orElse(null);
        MemberDTO memberDTO = new MemberDTO(member.getName(),
                member.getMemberId(),
                member.getPassword(),
                member.getDepartNo(),
                member.getPositionLevel(),
                member.getEmployedDate(),
                member.getAddress(),
                member.getPhoneNo(),
                member.getMemberStatus(),
                member.getEmail(),
                member.getMemberRole(),
                new String(member.getImage_url(), StandardCharsets.UTF_8),
                department.getDepartName(),
                position.getPositionName());

        return memberDTO;
    }

    //전 사원 정보 (결재자 참조자 라인 조회할때 : 부서별 순서)
    public List<MemberDTO> getAllMemberList() {

        List<Object[]> results = approvalMemberRepository.findAllMembersWithDepartmentOrderedNative();

        List<MemberDTO> memberDTOList = results.stream()
                .map(this::convertToMemberDTO)
                .collect(Collectors.toList());

        for (MemberDTO memberDTO : memberDTOList) {

            Position position = approvalPositionRepository.findById(memberDTO.getPositionLevel()).orElse(null);

            memberDTO.setPositionName(position.getPositionName());
        }

        return memberDTOList;
    }

    //Page를 DTO로 변환
    private List<ApprovalDTO> listToDTO(Page<Approval> approvalPage) {

        List<ApprovalDTO> approvalDTOList;

        if (!approvalPage.getContent().isEmpty()) {
            approvalDTOList = approvalPage.getContent().stream()
                    .map(approval -> {
                        ApprovalDTO approvalDTO = getApproval(approval.getApprovalNo());
                        approvalDTO.setApprovalDate(approval.getApprovalDate());

                        return approvalDTO;
                    })
                    .collect(Collectors.toList());
        } else {
            approvalDTOList = Collections.emptyList();
        }

        return approvalDTOList;
    }

    private MemberDTO convertToMemberDTO(Object[] result) {

        String name = (String) result[1];
        int memberId = (int) result[2];
        String password = (String) result[3];
        int departNo = (int) result[4];
        String positionLevel = (String) result[5];
        Date employedDate = (java.sql.Date) result[6];
        String address = (String) result[7];
        String phoneNo = (String) result[8];
        String memberStatus = (String) result[9];
        String email = (String) result[10];
        String memberRole = (String) result[11];
        String imageUrl = new String((byte[]) result[12], StandardCharsets.UTF_8);
        String departName = (String) result[0];

        return new MemberDTO(name, memberId, password, departNo, positionLevel, employedDate, address, phoneNo, memberStatus, email, memberRole, imageUrl, departName);
    }
}
