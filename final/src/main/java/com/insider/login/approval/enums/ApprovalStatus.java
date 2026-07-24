package com.insider.login.approval.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApprovalStatus {

    PROCESSING("처리 중"),
    APPROVED("승인"),
    REJECTED("반려"),
    WITHDRAWN("회수"),
    TEMP_SAVED("임시저장");

    private final String description;

    public boolean canTransitionTo(ApprovalStatus target) {
        return switch (this) {
            case TEMP_SAVED -> target == PROCESSING;
            case PROCESSING -> target == APPROVED || target == REJECTED || target == WITHDRAWN;
            case APPROVED, REJECTED, WITHDRAWN -> false;
        };
    }

    public static ApprovalStatus from(String value) {
        for (ApprovalStatus status : values()) {
            if (status.name().equals(value) || status.description.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown approval status: " + value);
    }
}
