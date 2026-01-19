package rediclaim.gate.controller.dto;

public record GateEnqueueResponse(
        String status,
        Long rank
) {
}
