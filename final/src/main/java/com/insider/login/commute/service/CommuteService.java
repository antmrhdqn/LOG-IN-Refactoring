package com.insider.login.commute.service;

import com.insider.login.commute.dto.*;
import com.insider.login.commute.entity.Commute;
import com.insider.login.commute.entity.CommuteDepartment;
import com.insider.login.commute.entity.Correction;
import com.insider.login.commute.repository.*;

import com.insider.login.commute.entity.CommuteMember;

import com.insider.login.notice.dto.NoticeDTO;
import com.insider.login.notice.service.NoticeService;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CommuteService {

    private final CommuteRepository commuteRepository;
    private final CorrectionRepository correctionRepository;
    private final CommuteDepartmentRepository commuteDepartmentRepository;
    private final CommuteMemberRepository commuteMemberRepository;
    private final NoticeService noticeService;
    private final CommuteMemberService commuteMemberService;
    private final ModelMapper modelMapper;

    public CommuteService(CommuteRepository commuteRepository,
                          ModelMapper modelMapper,
                          CorrectionRepository correctionRepository,
                          CommuteDepartmentRepository commuteDepartmentRepository,
                          CommuteMemberRepository commuteMemberRepository,
                          NoticeService noticeService,
                          CommuteMemberService commuteMemberService) {

        this.commuteRepository = commuteRepository;
        this.modelMapper = modelMapper;
        this.correctionRepository = correctionRepository;
        this.commuteDepartmentRepository = commuteDepartmentRepository;
        this.commuteMemberRepository = commuteMemberRepository;
        this.noticeService = noticeService;
        this.commuteMemberService = commuteMemberService;
    }

    @Transactional
    public Map<String, Object> insertTimeOfCommute(CommuteDTO newCommute) {

        log.info("[CommuteService] insertTimeOfCommute");
        log.info("[CommuteService] CommuteDTO : " + newCommute.getMemberId());

        Map<String, Object> result = new HashMap<>();

        try {
            CommuteMember commuteMember = commuteMemberRepository.findByMemberId(newCommute.getMemberId());
            Commute commute = new Commute(
                    newCommute.getWorkingDate(),
                    newCommute.getStartWork(),
                    newCommute.getWorkingStatus(),
                    newCommute.getTotalWorkingHours(),
                    commuteMember
                        );

            commuteRepository.save(commute);

            result.put("result", true);

        } catch (Exception e) {
            result.put("result", false);
        }

        log.info("[CommuteService] commuteNo : " + newCommute.getCommuteNo());
        log.info("[CommuteService] insertTimeOfCommute End ===========");
        return result;
    }

    @Transactional
    public Map<String, Object> updateTimeOfCommuteByCommuteNo(int commuteNo , UpdateTimeOfCommuteDTO updateTimeOfCommute) {

        Map<String, Object> result = new HashMap<>();

        Commute commute = commuteRepository.findByCommuteNo(commuteNo);

        log.info("update 전 : " , commute);

        if(commute != null) {
            CommuteDTO commuteDTO = modelMapper.map(commute, CommuteDTO.class);

            commuteDTO.setCommuteNo(commuteNo);
            commuteDTO.setEndWork(updateTimeOfCommute.getEndWork());
            commuteDTO.setWorkingStatus(updateTimeOfCommute.getWorkingStatus());
            commuteDTO.setTotalWorkingHours(updateTimeOfCommute.getTotalWorkingHours());

            Commute updateCommute = modelMapper.map(commuteDTO, Commute.class);
            commuteRepository.save(updateCommute);

            log.info("update 후 : ", commuteRepository.save(updateCommute));

            result.put("result", true);

        } else {
            result.put("result", false);
        }

        return result;
    }

    @Transactional
    public Map<String, Object> selectCommuteListByDepartNo(int departNo, LocalDate startDayOfMonth, LocalDate endDayOfMonth) {

        log.info("[CommuteService] selectCommuteListByDepartNo");
        log.info("[CommuteService] departNo : ", departNo);

        // 1. 부서 정보 조회
        CommuteDepartment commuteDepartment = commuteDepartmentRepository.findByDepartNo(departNo);

        // 2. 부서에 속한 구성원 정보 조회
        List<CommuteMember> commuteMemberList = commuteDepartment.getCommuteMemberList();

        // 3. 구성원별 출퇴근 내역 조회
        List<CommuteDTO> commuteDTOList = new ArrayList<>();
        List<CommuteMemberDTO> commuteMemberDTOList = new ArrayList<>();

        for (CommuteMember commuteMember : commuteMemberList) {
            List<Commute> commuteList = commuteMember.getCommuteList();
            List<Commute> filteredCommuteList = commuteList.stream()
                    .filter(c -> c.getWorkingDate().isAfter(startDayOfMonth.minusDays(1)) && c.getWorkingDate().isBefore(endDayOfMonth.plusDays(1)))
                    .collect(Collectors.toList());

            List<CommuteDTO> memberCommuteDTOList = filteredCommuteList.stream()
                    .map(c -> modelMapper.map(c, CommuteDTO.class))
                    .collect(Collectors.toList());
            commuteDTOList.addAll(memberCommuteDTOList);

            CommuteMemberDTO commuteMemberDTO = modelMapper.map(commuteMember, CommuteMemberDTO.class);
            commuteMemberDTOList.add(commuteMemberDTO);
        }

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("commute", commuteDTOList);
        responseMap.put("department", modelMapper.map(commuteDepartment, CommuteDepartmentDTO.class));
        responseMap.put("members", commuteMemberDTOList);

        log.info("[CommuteService] selectCommuteListByDepartNo End ==================");
        return responseMap;
    }

    @Transactional
    public List<CommuteDTO> selectCommuteListByMemberId(int memberId, LocalDate startWeek, LocalDate endWeek) {

        log.info("[CommuteService] selectCommuteListByMemberId");
        log.info("[CommuteService] memberId : " , memberId);

        // 1. 멤버 정보 조회
        CommuteMember findMemberByMemberId = commuteMemberRepository.findByMemberId(memberId);

        // 2. 해당 멤버의 출퇴근 내역 조회
        List<Commute> commuteList = findMemberByMemberId.getCommuteList().stream()
                .filter(commute -> commute.getWorkingDate().isAfter(startWeek.minusDays(1)) && commute.getWorkingDate().isBefore(endWeek.plusDays(1)))
                .collect(Collectors.toList());

        // 3. CommuteDTO 리스트로 변환
        List<CommuteDTO> commuteDTOList = commuteList.stream()
                .map(commute -> modelMapper.map(commute, CommuteDTO.class))
                .collect(Collectors.toList());

        log.info("[CommuteService] selectCommuteListByMemberId End ================");

        return commuteDTOList;
    }

    @Transactional
    public Map<String, Object> insertRequestForCorrect(CorrectionDTO newCorrection) {

        log.info("[CommuteService] insertRequestForCorrect");
        log.info("[CommuteService] newCorrection : {}", newCorrection);

        Map<String, Object> result = new HashMap<>();

        try {

            int commuteNo = newCorrection.getCommuteNo();
            log.info("[CommuteService] commuteNo : " + commuteNo);
            Commute commute = commuteRepository.findByCommuteNo(commuteNo);

            CorrectionDTO correctionDTO = new CorrectionDTO();

            correctionDTO.setCommuteNo(commuteNo);
            correctionDTO.setReasonForCorr(newCorrection.getReasonForCorr());
            correctionDTO.setCorrRegistrationDate(newCorrection.getCorrRegistrationDate());
            correctionDTO.setCorrStatus(newCorrection.getCorrStatus());

            /** 1. 출퇴근 내역에서 출근 시간과 퇴근 시간이 모두 존재하는 경우 */
            if(commute.getStartWork() != null && commute.getEndWork() != null) {

                /** 1-1. 출근 시간과 퇴근시간 모두 정정 요청 */
                if(newCorrection.getReqStartWork() != null && newCorrection.getReqEndWork() != null) {
                    correctionDTO.setReqStartWork(newCorrection.getReqStartWork());
                    correctionDTO.setReqEndWork(newCorrection.getReqEndWork());

                /** 1-2. 출근 시간만 정정 요청 */
                } else if(newCorrection.getReqStartWork() != null && newCorrection.getReqEndWork() == null) {
                    correctionDTO.setReqStartWork(newCorrection.getReqStartWork());

                /** 1-3. 퇴근 시간만 정정 요청 */
                } else if(newCorrection.getReqEndWork() != null && newCorrection.getReqStartWork() == null) {
                    correctionDTO.setReqEndWork(newCorrection.getReqEndWork());

                /** 1-4. 출퇴근 정정 요청 시간이 존재하지 않는 경우 */
                } else {
                    System.out.println("출퇴근 정정 요청 시간 null ! + 클라이언트 서버에서 경고 문구로 필터링 됨");
                }

            /** 2. 출퇴근 내역에서 출근 시간만 존재하는 경우 */
            } else if(commute.getStartWork() != null && commute.getEndWork() == null) {

                /** 2-1. 출근 시간만 정정 요청 */
                if(newCorrection.getReqStartWork() != null & newCorrection.getReqEndWork() == null) {
                    correctionDTO.setReqStartWork(newCorrection.getReqStartWork());

                /** 2-2. 나머지 경우 (퇴근 시간이 존재하지 않는데 퇴근 시간 정정 요청하는 경우, 출근 시간과 퇴근 시간을 모두 요청하는 경우) */
                } else {
                    System.out.println("나머지 경우 : {} " + newCorrection);
                    System.out.println("출퇴근 정정 요청 error !!!!!!!!!!!!!!!!!! + 클라이언트 서버에서 정정 요청 퇴근 시간 입력 필드 보이지 않도록 처리");
                }

            /** 3. 출퇴근 내역이 존재하지 않는 경우 => 해당 일자에 근무를 했지만, 실수로 출퇴근을 입력하지 않고 날짜가 지나간 경우에 필요 => 오류로 인해 다른 로직으로 변경 */
            } else {
                System.out.println("기존 출퇴근 내역이 null!!!!!!!!!!!");
            }

            correctionRepository.save(modelMapper.map(correctionDTO, Correction.class));
            result.put("result", true);

            /** 출퇴근 정정 요청 등록 시 처리자에게 알림 */

            List<CommuteMember> adminCommuteMembers = commuteMemberService.getAdminMembers();
            for(CommuteMember adminCommuteMember : adminCommuteMembers) {
                NoticeDTO newNotice = new NoticeDTO();

                System.out.println("adminMember : " + adminCommuteMember);

                newNotice.setMemberId(adminCommuteMember.getMemberId());
                newNotice.setNoticeType("출퇴근");
                newNotice.setNoticeContent("새로운 출퇴근 정정 요청이 있습니다. 확인해주세요.");
                newNotice.setNoticeDateTime(LocalDateTime.now());

                noticeService.insertNewNotice(newNotice);
                System.out.println("newNotice : " + newNotice);
            }

            result.put("notice", adminCommuteMembers);
            System.out.println(adminCommuteMembers);
            log.info("[CommuteService] 출퇴근 정정 요청 등록 후 ");

        } catch (Exception e ) {
            System.out.println("출퇴근 정정 요청 Error");
            e.printStackTrace();
            result.put("result", false);
        }

        log.info("[CommuteService] insertRequestForCorrect End ================");

        return result;
    }

    @Transactional
    public Map<String, Object> insertNewCorrect(NewCorrectionDTO newCorrection) {
        log.info("[CommuteService] insertNewCorrect");
        log.info("[CommuteService] newCorrection : {}", newCorrection);

        Map<String, Object> result = new HashMap<>();

        NewCommuteDTO newCommuteDTO = new NewCommuteDTO();

        newCommuteDTO.setMemberId(newCorrection.getMemberId());
        newCommuteDTO.setWorkingDate(newCorrection.getWorkingDate());
        newCommuteDTO.setStartWork(LocalTime.MIDNIGHT);
        newCommuteDTO.setEndWork(LocalTime.MIDNIGHT);
        newCommuteDTO.setWorkingStatus("출퇴근 미입력으로 정정 대기 중");
        newCommuteDTO.setTotalWorkingHours(0);

        System.out.println("newCommuteDTO : " + newCommuteDTO);

        // 출퇴근 db에 임의로 저장
        Commute savedCommute = commuteRepository.save(modelMapper.map(newCommuteDTO, Commute.class));

        int commuteNo = savedCommute.getCommuteNo();

        System.out.println("commuteNo : " + commuteNo);

        NewCorrectionDTO newCorrectionDTO = new NewCorrectionDTO();

        newCorrectionDTO.setCommuteNo(commuteNo);
        newCorrectionDTO.setMemberId(newCorrection.getMemberId());
        newCorrectionDTO.setReqStartWork(newCorrection.getReqStartWork());
        newCorrectionDTO.setReqEndWork(newCorrection.getReqEndWork());
        newCorrectionDTO.setReasonForCorr(newCorrection.getReasonForCorr());
        newCorrectionDTO.setCorrRegistrationDate(newCorrection.getCorrRegistrationDate());
        newCorrectionDTO.setCorrStatus(newCorrection.getCorrStatus());

        System.out.println("newCorrectionDTO : " + newCorrectionDTO);

        // 출퇴근 정정 db에 임의로 저장
        correctionRepository.save(modelMapper.map(newCorrectionDTO, Correction.class));

        result.put("result", true);

        return result;
    }

    @Transactional
    public Map<String, Object> updateProcessForCorrectByCorrNo(int corrNo, UpdateProcessForCorrectionDTO updateProcessForCorrection) {

        log.info("[CommuteService] updateProcessForCorrectByCorrNo");
        log.info("[CommuteService] updateProcessForCorrection : {} ", updateProcessForCorrection);

        Map<String, Object> result = new HashMap<>();

        Correction correction = correctionRepository.findByCorrNo(corrNo);

        int commuteNo = correction.getCommute().getCommuteNo();
        Commute commute = commuteRepository.findByCommuteNo(commuteNo);

        CorrectionDTO correctionDTO = modelMapper.map(correction, CorrectionDTO.class);

        CommuteDTO commuteDTO = modelMapper.map(commute, CommuteDTO.class);

        if(correction != null && commute != null) {

            /** 1. 출퇴근 정정 요청 내역 update */
            correctionDTO.setCorrStatus(updateProcessForCorrection.getCorrStatus());
            correctionDTO.setCorrProcessingDate(updateProcessForCorrection.getCorrProcessingDate());

            /** 2. 출퇴근 내역 update */
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

            /** 1-1. 출퇴근 정정 처리 - 승인 */
            if(updateProcessForCorrection.getReasonForRejection() == null) {

                Correction updateCorrection = modelMapper.map(correctionDTO, Correction.class);
                correctionRepository.save(updateCorrection);

                /** 1-1-1. 출근 시간만 존재할 때 => 출근 시간만 정정 가능 */
                if(commute.getStartWork() != null && commute.getEndWork() == null) {

                    LocalTime updateStartWork = LocalTime.parse(correction.getReqStartWork(), formatter);
                    commuteDTO.setStartWork(updateStartWork);

                    log.info("[CommuteService] 출근시간만 존재할 때 출근시간 정정 처리 후 ");

                    /** 1-1-2. 출근시간, 퇴근시간 모두 존재할 때 */
                } else if (commute.getStartWork() != null && commute.getEndWork() != null) {

                    /** 1-1-2-1. 출근시간, 퇴근시간 모두 정정 */
                    if(correction.getReqStartWork() != null && correction.getReqEndWork() != null) {

                        LocalTime updateStartWork = LocalTime.parse(correction.getReqStartWork(), formatter);
                        LocalTime updateEndWork = LocalTime.parse(correction.getReqEndWork(), formatter);

                        Duration workingDuration = Duration.between(updateStartWork, updateEndWork).minusHours(1);
                        int totalWorkingHours = (int) workingDuration.toMinutes();

                        commuteDTO.setStartWork(updateStartWork);
                        commuteDTO.setEndWork(updateEndWork);
                        commuteDTO.setWorkingStatus("퇴근");                  // 실수로 출퇴근을 하지 않고 날짜가 지나서 출퇴근 시간을 정상으로 정정 요청 할 때
                        commuteDTO.setTotalWorkingHours(totalWorkingHours);

                        log.info("[CommuteService] 출근시간, 퇴근시간 모두 정정 처리 후 ");

                        /** 1-1-2-2. 출근시간만 정정 */
                    } else if (correction.getReqStartWork() != null && correction.getReqEndWork() == null) {

                        LocalTime updateStartWork = LocalTime.parse(correction.getReqStartWork(), formatter);
                        LocalTime originalEndWork = commute.getEndWork();

                        Duration workingDuration = Duration.between(updateStartWork, originalEndWork).minusHours(1);
                        int totalWorkingHours = (int) workingDuration.toMinutes();

                        commuteDTO.setStartWork(updateStartWork);
                        commuteDTO.setTotalWorkingHours(totalWorkingHours);
                        log.info("[dddddddddddddddd]" + commuteDTO);
                        log.info("[CommuteService] 출근시간만 정정 처리 후 ");

                        /** 1-1-2-3. 퇴근시간만 정정 */
                    } else if (correction.getReqStartWork() == null && correction.getReqEndWork() != null) {

                        LocalTime originalStartWork = commute.getStartWork();
                        LocalTime updateEndWork = LocalTime.parse(correction.getReqEndWork(), formatter);

                        Duration workingDuration = Duration.between(originalStartWork, updateEndWork).minusHours(1);
                        int totalWorkingHours = (int) workingDuration.toMinutes();

                        commuteDTO.setEndWork(updateEndWork);
                        commuteDTO.setTotalWorkingHours(totalWorkingHours);

                        log.info("[CommuteService] 퇴근시간만 정정 처리 후 ");

                    /** 1-1-2-4. 출퇴근 정정 요청 시간 모두 null */
                    } else {
                        System.out.println("출퇴근 정정 요청 시간 모두 null ! + 클라이언트 서버에서 경고 문구로 필터링 됨");
                    }

                /** 1-1-3. 출근시간, 퇴근시간 모두 존재하지 않을 때 */
                } else {
                    System.out.println("출퇴근 시간 모두 null !!!!!!!!");
                }

                log.info("[CommuteService] Correction update 승인 처리 후 ");

            /** 1-2. 출퇴근 정정 처리 - 반려 */
            } else {
                correctionDTO.setReasonForRejection(updateProcessForCorrection.getReasonForRejection());

                Correction updateCorrection = modelMapper.map(correctionDTO, Correction.class);
                correctionRepository.save(updateCorrection);

                log.info("[CommuteService] Correction update 반려 처리 후 ");
            }

            Commute updateCommute = modelMapper.map(commuteDTO, Commute.class);

            commuteRepository.save(updateCommute);

            log.info("[CommuteService] Commute update 후 ");

            /** 출퇴근 정정 처리 시 요청자에게 알림 */
            NoticeDTO newNotice = new NoticeDTO();
            newNotice.setMemberId(commuteDTO.getMemberId());
            newNotice.setNoticeType("출퇴근");
            newNotice.setNoticeContent("출퇴근 정정 요청이 처리되었습니다. 확인해주세요.");
            newNotice.setNoticeDateTime(LocalDateTime.now());

            noticeService.insertNewNotice(newNotice);

            result.put("result", true);
            result.put("notice", newNotice);

        } else {
            result.put("result", false);

            log.info("[CommuteService] Error !!! ");
        }

        log.info("[CommuteService] updateProcessForCorrectByCorrNo End ================");

        return result;
    }

    @Transactional
    public Map<String, Object> selectRequestForCorrectList(LocalDate startDayOfMonth, LocalDate endDayOfMonth, Pageable pageable) {
        log.info("[CommuteService] selectRequestForCorrectList");

        // 1. correction 데이터 조회 (페이징 적용)
        Page<Correction> correctionPage = correctionRepository.findAllByCorrRegistrationDateBetween(startDayOfMonth, endDayOfMonth, pageable);
        List<Correction> correctionList = correctionPage.getContent();
        List<CorrectionDTO> correctionDTOList = correctionList.stream()
                .map(correction -> modelMapper.map(correction, CorrectionDTO.class))
                .collect(Collectors.toList());
        System.out.println("correctionDTOList:");
        correctionDTOList.forEach(System.out::println);

        // 2. correction에 해당하는 commute 데이터 조회
        List<Integer> commuteNoList = correctionDTOList.stream()
                .map(CorrectionDTO::getCommuteNo)
                .collect(Collectors.toList());
        List<Commute> commuteList = commuteRepository.findByCommuteNoIn(commuteNoList);
        List<CommuteDTO> commuteDTOList = commuteList.stream()
                .map(commute -> modelMapper.map(commute, CommuteDTO.class))
                .collect(Collectors.toList());
        System.out.println("commuteDTOList:");
        commuteDTOList.forEach(System.out::println);

        // 3. commute에 해당하는 멤버 정보 조회
        Set<Integer> memberIds = commuteDTOList.stream()
                .map(CommuteDTO::getMemberId)
                .collect(Collectors.toSet());
        List<CommuteMember> memberList = commuteMemberRepository.findByMemberIdIn(memberIds);
        List<CommuteMemberDTO> memberDTOList = memberList.stream()
                .map(member -> modelMapper.map(member, CommuteMemberDTO.class))
                .collect(Collectors.toList());

        // 4. 멤버들의 부서 정보 조회
        Set<Integer> departNoSet = memberDTOList.stream()
                .map(CommuteMemberDTO::getCommuteDepartment)
                .map(CommuteDepartmentDTO::getDepartNo)
                .collect(Collectors.toSet());
        List<CommuteDepartment> departList = commuteDepartmentRepository.findByDepartNoIn(departNoSet);
        List<CommuteDepartmentDTO> departDTOList = departList.stream()
                .map(depart -> modelMapper.map(depart, CommuteDepartmentDTO.class))
                .collect(Collectors.toList());
        System.out.println("departDTOList:");
        departDTOList.forEach(System.out::println);

        Map<String, Object> responseMap = new HashMap<>();
//        responseMap.put("correction", correctionDTOList);
        responseMap.put("currentPage", correctionPage.getNumber());
        responseMap.put("totalItems", correctionPage.getTotalElements());
        responseMap.put("totalPages", correctionPage.getTotalPages());
        responseMap.put("commute", commuteDTOList);
        responseMap.put("member", memberDTOList);
        responseMap.put("depart", departDTOList);

        return responseMap;
    }

    @Transactional
    public Map<String, Object> selectRequestForCorrectListByMemberId(int memberId, LocalDate startDayOfMonth, LocalDate endDayOfMonth, Pageable pageable) {
        log.info("[CommuteService] selectRequestForCorrectListByMemberId");
        log.info("[CommuteService] memberId : {} ", memberId);

        CommuteMember findMemberByMemberId = commuteMemberRepository.findByMemberId(memberId);
        List<Commute> commuteList = findMemberByMemberId.getCommuteList();

        List<CommuteDTO> commuteDTOList = commuteList.stream()
                .filter(commute -> commute.getWorkingDate().isAfter(startDayOfMonth.minusDays(1)) && commute.getWorkingDate().isBefore(endDayOfMonth.plusDays(1)))
                .map(commute -> modelMapper.map(commute, CommuteDTO.class))
                .collect(Collectors.toList());

        List<Correction> correctionList = commuteList.stream()
                .map(Commute::getCorrection)
                .filter(Objects::nonNull)
                .filter(correction -> correction.getCorrRegistrationDate().isAfter(startDayOfMonth.minusDays(1)) && correction.getCorrRegistrationDate().isBefore(endDayOfMonth.plusDays(1)))
                .collect(Collectors.toList());

        Page<CorrectionDTO> correctionDTOPage = new PageImpl<>(
                correctionList.stream()
                        .map(correction -> modelMapper.map(correction, CorrectionDTO.class))
                        .collect(Collectors.toList()),
                pageable,
                correctionList.size()
        );

        Map<String, Object> responseMap = new HashMap<>();

        if (commuteList != null) {
//            responseMap.put("correction",correctionDTOPage.getContent());
            responseMap.put("currentPage", correctionDTOPage.getNumber());
            responseMap.put("totalItems", correctionDTOPage.getTotalElements());
            responseMap.put("totalPages", correctionDTOPage.getTotalPages());
            responseMap.put("commute", commuteDTOList);
            return responseMap;

        } else {
            responseMap.put("result", false);
            return responseMap;
        }
    }

    @Transactional
    public CorrectionDTO selectRequestForCorrectByCorrNo(int corrNo) {
        log.info("[CommuteService] selectRequestForCorrectByCorrNo");
        log.info("[CommuteService] corrNo : {} ", corrNo);

        Correction correction = correctionRepository.findByCorrNo(corrNo);

        CorrectionDTO correctionDTO = modelMapper.map(correction, CorrectionDTO.class);

        return correctionDTO;
    }

    @Transactional
    public CommuteDTO selectCommuteDetailByCommuteNo(int commuteNo) {
        log.info("[CommuteService] selectCommuteDetailByCommuteNo");
        log.info("[CommuteService] commuteNo : ", commuteNo);

        Commute findCommuteByCommuteNo = commuteRepository.findByCommuteNo(commuteNo);

        CommuteDTO commuteDTO = modelMapper.map(findCommuteByCommuteNo, CommuteDTO.class);

        log.info("commuteNo : " + commuteDTO.getCommuteNo());

        if (commuteDTO != null) {
            return commuteDTO;
        } else {
            return null;
        }
    }


}
