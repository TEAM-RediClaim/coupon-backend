package rediclaim.gate.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import rediclaim.gate.service.GateService;
import rediclaim.gate.controller.dto.GateEnqueueResponse;
import rediclaim.gate.controller.dto.GateStatusResponse;

@RestController
@RequiredArgsConstructor
public class GateController {

    private final GateService gateService;

    @PostMapping("/gate/events/{eventId}/enqueue")
    public GateEnqueueResponse enqueue(@PathVariable Long eventId, @RequestParam Long userId) {
        return gateService.enqueue(eventId, userId);
    }

    @GetMapping("/gate/events/{eventId}/rank")
    public GateStatusResponse getStatus(@PathVariable Long eventId, @RequestParam Long userId) {
        return gateService.getStatus(eventId, userId);
    }
}
