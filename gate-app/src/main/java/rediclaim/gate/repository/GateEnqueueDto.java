package rediclaim.gate.repository;

public record GateEnqueueDto(
        boolean enqueued,
        Long rank
) {
}
