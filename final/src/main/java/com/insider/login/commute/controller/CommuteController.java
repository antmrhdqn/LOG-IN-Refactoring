package com.insider.login.commute.controller;

import com.insider.login.common.CommonController;
import com.insider.login.common.response.ResponseMessage;
import com.insider.login.commute.dto.*;
import com.insider.login.commute.service.CommuteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.Charset;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class CommuteController {

    private final CommuteService commuteService;

    @Autowired
    public CommuteController(CommuteService commuteService) {
        this.commuteService = commuteService;
    }

    /**
     * 출근 시간 등록
     */
    @PostMapping("/commutes")
    public ResponseEntity<ResponseMessage> insertTimeOfCommute(@RequestBody CommuteDTO newCommute) {
        return ResponseEntity.ok().body(new ResponseMessage(200, "등록 성공", commuteService.insertTimeOfCommute(newCommute)));
    }

    /**
     * 퇴근 시간 등록 (update)
     */
    @PutMapping("/commutes/{commuteNo}")
    public ResponseEntity<ResponseMessage> updateTimeOfCommute(@PathVariable("commuteNo") int commuteNo,
                                                               @RequestBody UpdateTimeOfCommuteDTO updateCommute) {
        return ResponseEntity.ok().body(new ResponseMessage(200, "추가 등록 성공", commuteService.updateTimeOfCommuteByCommuteNo(commuteNo, updateCommute)));
    }

    /**
     * 출퇴근 내역 조회 (부서별, 회원별)
     */
    @GetMapping("/commutes")
    public ResponseEntity<ResponseMessage> selectCommuteList(@RequestParam(value = "target") String target,
                                                             @RequestParam(value = "targetValue", required = false) String targetValue,
                                                             @RequestParam(value = "date") LocalDate date) {

        System.out.println("date : " + date);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));

        Map<String, Object> result = new HashMap<>();

        /** 멤버별 출퇴근 내역 조회시 주간 조회에 사용할 변수들 */
        LocalDate startWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endWeek = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        /** 전체 출퇴근 내역 조회시 월간 조회에 사용할 변수들 */
        LocalDate startDayOfMonth = date.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate endDayOfMonth = date.with(TemporalAdjusters.lastDayOfMonth());

        if ("depart".equals(target)) {
            int departNo = Integer.parseInt(targetValue);
            result = commuteService.selectCommuteListByDepartNo(departNo, startDayOfMonth, endDayOfMonth);
//            result.put("result", commuteService.selectCommuteListByDepartNo(departNo, startDayOfMonth, endDayOfMonth));   // 부서별

            // 가공 과정
//            result.forEach((key, value) -> {
//                System.out.println(key + " : " + value);
//            });
            List<Map<String, Object>> processedMembersList = new ArrayList<>();
            List<CommuteMemberDTO> members = (List<CommuteMemberDTO>) result.get("members");
            List<CommuteDTO> commuteList = (List<CommuteDTO>) result.get("commute");

            for (CommuteMemberDTO member : members) {
                Map<String, Object> processedMember = new HashMap<>();
                processedMember.put("name", member.getName());

                List<Map<String, Object>> processedCommuteList = new ArrayList<>();
                for (CommuteDTO commute : commuteList) {
                    if (commute.getMemberId() == member.getMemberId()) {
                        Map<String, Object> processedCommute = new HashMap<>();
                        processedCommute.put("workingDate", commute.getWorkingDate());
                        processedCommute.put("startWork", commute.getStartWork());
                        processedCommute.put("endWork", commute.getEndWork());
                        processedCommute.put("totalWorkingHours", commute.getTotalWorkingHours());

                        processedCommuteList.add(processedCommute);
                        System.out.println("출퇴근 가공" + processedCommuteList);
                    }
                }

                processedMember.put("commuteList", processedCommuteList);
                processedMembersList.add(processedMember);
            }

            result.put("result", processedMembersList);

        } else if ("member".equals(target)) {
            int memberId = Integer.parseInt(targetValue);
            result.put("result", commuteService.selectCommuteListByMemberId(memberId, startWeek, endWeek));     // 회원별

        } else {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "잘못된 요청");
            ResponseMessage responseMessage = new ResponseMessage(400, "잘못된 요청", error);

            return new ResponseEntity<>(responseMessage, headers, HttpStatus.BAD_REQUEST);
        }

        ResponseMessage responseMessage = new ResponseMessage(200, "조회 성공", result);

        return new ResponseEntity<>(responseMessage, headers, HttpStatus.OK);
    }

    /**
     * 출퇴근 시간 정정 요청 등록 (출퇴근 내역 존재)
     */
    @PostMapping("/corrections")
    public ResponseEntity<ResponseMessage> insertRequestForCorrect(@RequestBody CorrectionDTO newCorrection) {
        System.out.println("newCorrection : {} " + newCorrection);
        return ResponseEntity.ok().body(new ResponseMessage(200, "등록 성공", commuteService.insertRequestForCorrect(newCorrection)));
    }

    /** 출퇴근 시간 정정 요청 등록 (출퇴근 내역 미존재) */
    @PostMapping("/corrections/newCorrection")
    public ResponseEntity<ResponseMessage> insertNewCorrect(@RequestBody NewCorrectionDTO newCorrection) {
        System.out.println(" 컨트롤러 newCorrection : {} " + newCorrection);
        return ResponseEntity.ok().body(new ResponseMessage(200, "등록 성공", commuteService.insertNewCorrect(newCorrection)));
    }

    /**
     * 출퇴근 시간 정정 처리 등록 (update)
     */
    @PutMapping("/corrections/{corrNo}")
    public ResponseEntity<ResponseMessage> updateProcessForCorrectByCorrNo(@PathVariable("corrNo") int corrNo,
                                                                           @RequestBody UpdateProcessForCorrectionDTO updateCorrection) {
        return ResponseEntity.ok().body(new ResponseMessage(200, "정정 처리 성공", commuteService.updateProcessForCorrectByCorrNo(corrNo, updateCorrection)));
    }

    /**
     * 출퇴근 시간 정정 내역 조회 (전체, 멤버별)
     */
    @GetMapping("/corrections")
    public ResponseEntity<ResponseMessage> selectRequestForCorrectList(@RequestParam(value = "memberId", required = false) Integer memberId,
                                                                       @RequestParam(value = "date") LocalDate date,
                                                                       @RequestParam(value = "page", defaultValue = "0", required = false) int page,
                                                                       @RequestParam(value = "size", defaultValue = "10", required = false) int size,
                                                                       @RequestParam(value = "sort", defaultValue = "sort", required = false) String sort,
                                                                       @RequestParam(value = "direction", defaultValue = "DESC", required = false) String direction) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));

        Pageable pageable = CommonController.getPageable(page, size, sort, direction);
        Map<String, Object> responseMap;

        LocalDate startDayOfMonth = date.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate endDayOfMonth = date.with(TemporalAdjusters.lastDayOfMonth());

        if (memberId != null) {
            /** 멤버별 본인 내역 조회 */
            responseMap = commuteService.selectRequestForCorrectListByMemberId(memberId, startDayOfMonth, endDayOfMonth, pageable);

            // 가공 과정
            responseMap.forEach((key, value) -> {
                System.out.println(key + " : " + value);
            });
            List<Map<String, Object>> processedCorrectionList = new ArrayList<>();
            List<CommuteDTO> commuteList = (List<CommuteDTO>) responseMap.get("commute");

            for (CommuteDTO commute : commuteList) {
                if (commute.getCorrection() != null) {
                    Map<String, Object> processedCommute = new HashMap<>();
                    processedCommute.put("workingDate", commute.getWorkingDate());
                    if (commute.getStartWork() != null) {
                        processedCommute.put("startWork", commute.getStartWork());
                    }
                    if (commute.getEndWork() != null) {
                        processedCommute.put("endWork", commute.getEndWork());
                    }
                    if (commute.getCorrection().getReqStartWork() != null) {
                        processedCommute.put("reqStartWork", commute.getCorrection().getReqStartWork());
                    }
                    if (commute.getCorrection().getReqEndWork() != null) {
                        processedCommute.put("reqEndWork", commute.getCorrection().getReqEndWork());
                    }
                    processedCommute.put("reasonForCorr", commute.getCorrection().getReasonForCorr());
                    processedCommute.put("corrRegistrationDate", commute.getCorrection().getCorrRegistrationDate());
                    processedCommute.put("corrStatus", commute.getCorrection().getCorrStatus());
                    if (commute.getCorrection().getReasonForRejection() != null) {
                        processedCommute.put("reasonForRejection", commute.getCorrection().getReasonForRejection());
                    }
                    if (commute.getCorrection().getCorrProcessingDate() != null) {
                        processedCommute.put("corrProcessingDate", commute.getCorrection().getCorrProcessingDate());
                    }
                    processedCommute.put("corrNo", commute.getCorrection().getCorrNo());
                    processedCorrectionList.add(processedCommute);


                } else {
                    System.out.println("해당 출퇴근 내역에 정정 내역이 없음!!!!");
                }
            }

            responseMap.put("result", processedCorrectionList);

            responseMap.forEach((key, value) -> {
                System.out.println(key + " : " + value);
            });

        } else {
            /** 부서별 내역 조회 */
            responseMap = commuteService.selectRequestForCorrectList(startDayOfMonth, endDayOfMonth, pageable);
            // 가공 과정
            responseMap.forEach((key, value) -> {
                System.out.println(key + " : " + value);
            });

            List<Map<String, Object>> processedCorrectionList = new ArrayList<>();
            List<CommuteMemberDTO> members = (List<CommuteMemberDTO>) responseMap.get("member");
            List<CommuteDepartmentDTO> departs = (List<CommuteDepartmentDTO>) responseMap.get("depart");
            List<CommuteDTO> commuteList = (List<CommuteDTO>) responseMap.get("commute");

            for (CommuteDTO commute : commuteList) {
                Map<String, Object> processedCommute = new HashMap<>();
                processedCommute.put("workingDate", commute.getWorkingDate());
                if (commute.getStartWork() != null) {
                    processedCommute.put("startWork", commute.getStartWork());
                }
                if (commute.getEndWork() != null) {
                    processedCommute.put("endWork", commute.getEndWork());
                }

                if (commute.getCorrection() != null) {
                    if (commute.getCorrection().getReqStartWork() != null) {
                        processedCommute.put("reqStartWork", commute.getCorrection().getReqStartWork());
                    }
                    if (commute.getCorrection().getReqEndWork() != null) {
                        processedCommute.put("reqEndWork", commute.getCorrection().getReqEndWork());
                    }
                    processedCommute.put("reasonForCorr", commute.getCorrection().getReasonForCorr());
                    processedCommute.put("corrRegistrationDate", commute.getCorrection().getCorrRegistrationDate());
                    processedCommute.put("corrStatus", commute.getCorrection().getCorrStatus());
                    if (commute.getCorrection().getReasonForRejection() != null) {
                        processedCommute.put("reasonForRejection", commute.getCorrection().getReasonForRejection());
                    }
                    if (commute.getCorrection().getCorrProcessingDate() != null) {
                        processedCommute.put("corrProcessingDate", commute.getCorrection().getCorrProcessingDate());
                    }
                    processedCommute.put("corrNo", commute.getCorrection().getCorrNo());
                }

                for (CommuteMemberDTO member : members) {
                    if (commute.getMemberId() == member.getMemberId()) {
                        processedCommute.put("name", member.getName());
                    }
                }

                for (CommuteDepartmentDTO depart : departs) {
                    processedCommute.put("departName", depart.getDepartName());
                }
                processedCorrectionList.add(processedCommute);
            }

            responseMap.put("result", processedCorrectionList);
        }

        ResponseMessage responseMessage = new ResponseMessage(200, "조회 성공", responseMap);

        return new ResponseEntity<>(responseMessage, headers, HttpStatus.OK);
    }

    /**
     * 출퇴근 시간 정정 요청 상세 조회
     */
    @GetMapping("/corrections/{corrNo}")
    public ResponseEntity<ResponseMessage> selectRequestForCorrectByCorrNo(@PathVariable("corrNo") int corrNo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));

        CorrectionDTO correction = commuteService.selectRequestForCorrectByCorrNo(corrNo);

        Map<String, Object> result = new HashMap<>();
        result.put("result", correction);

        ResponseMessage responseMessage = new ResponseMessage(200, "조회 성공", result);

        return new ResponseEntity<>(responseMessage, headers, HttpStatus.OK);
    }

    /**
     * 출퇴근 내역 상세 조회
     */
    @GetMapping("/commutes/{commuteNo}")
    public ResponseEntity<ResponseMessage> selectCommuteDetailByCommuteNo(@PathVariable("commuteNo") int commuteNo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));

        CommuteDTO commute = commuteService.selectCommuteDetailByCommuteNo(commuteNo);

        Map<String, Object> result = new HashMap<>();
        result.put("result", commute);

        ResponseMessage responseMessage = new ResponseMessage(200, "조회 성공", result);

        return new ResponseEntity<>(responseMessage, headers, HttpStatus.OK);
    }

}
