package com.insider.login.approval.repository;

import com.insider.login.approval.entity.Approval;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, String> {

    //내가 쓴 기안 목록
    @Query("SELECT a FROM Approval a WHERE a.memberId = :memberId AND a.approvalStatus != com.insider.login.approval.enums.ApprovalStatus.TEMP_SAVED")
    List<Approval> findByMemberId(@Param("memberId") int memberId);

    //내가 쓴 기안 목록 제목 검색 + 페이지
    @Query("SELECT a FROM Approval a " +
            "WHERE a.memberId = :memberId " +
            "AND a.approvalStatus != com.insider.login.approval.enums.ApprovalStatus.TEMP_SAVED " +
            "AND a.approvalTitle LIKE %:title%")
    Page<Approval> findByMemberIdAndTitle(@Param("memberId") int memberId, @Param("title") String title, Pageable pageable);

    //내가 쓴 기안 목록 갯수
    @Query("SELECT COUNT(a) FROM Approval a WHERE a.memberId = :memberId AND a.approvalStatus != com.insider.login.approval.enums.ApprovalStatus.TEMP_SAVED AND a.approvalTitle LIKE %:title%")
    int countByMemberIdAndTItle(@Param("memberId") int memberId, @Param("title")String title);

    //내 임시저장
    @Query("SELECT a FROM Approval a WHERE a.memberId = :memberId AND a.approvalStatus = com.insider.login.approval.enums.ApprovalStatus.TEMP_SAVED")
    List<Approval> findTempByMemberId(@Param("memberId") int memberId);

    //임시저장 제목 검색 + 페이지
    @Query("SELECT a FROM Approval a WHERE a.memberId = :memberId AND a.approvalStatus = com.insider.login.approval.enums.ApprovalStatus.TEMP_SAVED AND a.approvalTitle LIKE %:title%")
    Page<Approval> findTempByMemberIdAndTitle(@Param("memberId") int memberId, @Param("title")String title, Pageable pageable);

    //내 임시저장 목록 갯수
    @Query("SELECT COUNT(a) FROM Approval a WHERE a.memberId = :memberId AND a.approvalStatus = com.insider.login.approval.enums.ApprovalStatus.TEMP_SAVED AND a.approvalTitle LIKE %:title%")
    int countTempByMemberIdAndTitle(@Param("memberId")int memberId, @Param("title")String title);

    //전자결재 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM Approval a WHERE a.approvalNo = :approvalNo")
    void deleteById(@Param("approvalNo") String approvalNo);

    //해당 연도+폼 번호 로 가장 마지막에 등록된 결재번호 가져오기
    @Query("SELECT a.approvalNo FROM Approval a WHERE a.approvalNo LIKE %:yearFormNo% ORDER BY a.approvalNo DESC")
    List<String> findLastApprovalNo(@Param("yearFormNo") String yearFormNo, Pageable pageable);
}
