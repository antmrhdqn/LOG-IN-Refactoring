package com.insider.login.approval.repository;

import com.insider.login.approval.entity.Approval;

/**
 * {@link ApprovalRepository} 의 커스텀 프래그먼트.
 * 구현체 이름은 반드시 {@code ApprovalRepositoryImpl} 이어야 Spring Data 가 프래그먼트로 인식한다.
 */
public interface ApprovalRepositoryCustom {

    /**
     * 신규 결재를 persist 로 저장한다.
     * <p>
     * {@code save()} 는 assigned String @Id 조건에서 merge 로 흐르므로, 같은 결재번호가 이미 있으면
     * 예외 없이 기존 행을 UPDATE 해버린다(= 앞 기안자의 결재를 조용히 덮어씀).
     * persist 는 충돌 시 제약 위반으로 드러나 트랜잭션이 롤백된다.
     */
    void insert(Approval approval);

    /**
     * 영속성 컨텍스트를 비운다.
     * <p>
     * 벌크 삭제({@code @Modifying})는 DB 행만 지우고 영속성 컨텍스트의 엔티티는 남겨둔다.
     * 그 상태에서 같은 PK 로 다시 저장하면 merge 가 남아 있는 엔티티를 찾아 이미 삭제된 행에
     * UPDATE 를 날리게 된다. 자식 전량 교체 직후 이 메서드로 컨텍스트를 정리한다.
     * 호출 후에는 엔티티가 전부 detached 이므로 반드시 재조회해서 써야 한다.
     */
    void clearPersistenceContext();
}
