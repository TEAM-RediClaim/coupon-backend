package rediclaim.gate.controller.dto;

public record GateStatusResponse(
        String status,
        Long rank
) {
}
