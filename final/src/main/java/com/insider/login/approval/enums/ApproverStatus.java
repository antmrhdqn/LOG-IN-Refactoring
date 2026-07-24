package com.insider.login.approval.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApproverStatus {

    PENDING("대기"),
    APPROVED("승인"),
    REJECTED("반려");

    private final String description;

    public boolean canTransitionTo(ApproverStatus target) {
        return switch (this) {
            case PENDING -> target == APPROVED || target == REJECTED;
            case APPROVED, REJECTED -> false;
        };
    }

    public static ApproverStatus from(String value) {
        for (ApproverStatus status : values()) {
            if (status.name().equals(value) || status.description.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown approver status: " + value);
    }
}
