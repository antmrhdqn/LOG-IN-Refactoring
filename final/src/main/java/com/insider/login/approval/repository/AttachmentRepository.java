package com.insider.login.approval.repository;

import com.insider.login.approval.entity.Attachment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, String> {


    public List<Attachment> findByApprovalNo(String approvalNo);

    //저장명으로 첨부 조회 (다운로드 권한 판정용 — 요청 저장명이 실재하는 첨부인지 먼저 확인한다)
    Optional<Attachment> findByFileSavename(String fileSavename);

    @Modifying
    @Transactional
    @Query("DELETE FROM Apr_attachment a WHERE a.approvalNo = :approvalNo")
    public void deleteByApprovalNo(@Param("approvalNo") String approvalNo);
}
