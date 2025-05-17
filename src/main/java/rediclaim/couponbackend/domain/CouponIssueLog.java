package rediclaim.couponbackend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "couponIssueLogs")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueLog {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private LocalDateTime requestTime;

    @Column
    private LocalDateTime issueTime;

    @Column(nullable = false)
    private boolean issueStatus;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private boolean isLastAttempt;

    public void changeIssueStatus(boolean issueStatus) {
        this.issueStatus = issueStatus;
    }

    public void setIssueTime(LocalDateTime issueTime) {
        this.issueTime = issueTime;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public void markLastAttempt() {
        this.isLastAttempt = true;
    }
}
