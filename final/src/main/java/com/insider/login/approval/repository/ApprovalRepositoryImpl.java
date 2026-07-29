package com.insider.login.approval.repository;

import com.insider.login.approval.entity.Approval;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * {@link ApprovalRepositoryCustom} 구현체.
 * 클래스명이 {@code ApprovalRepositoryImpl} 이어야 Spring Data 가 프래그먼트로 인식한다(개명 금지).
 */
public class ApprovalRepositoryImpl implements ApprovalRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void insert(Approval approval) {
        entityManager.persist(approval);
    }

    @Override
    public void clearPersistenceContext() {
        entityManager.clear();
    }
}
