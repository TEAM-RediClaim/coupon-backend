package rediclaim.gate.dispatcher.kafka;

public record IssueRequestMessage(
        Long eventId,
        Long userId,
        Long rank
) {
}
