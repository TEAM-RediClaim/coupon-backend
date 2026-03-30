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

    /**
     * issuer-worker-app 이 쿠폰 발급 처리 완료 후 호출하는 콜백 엔드포인트.
     * processing 상태에서 해당 유저를 제거한다.
     */
    @PostMapping("/gate/events/{eventId}/processing/complete")
    public void complete(@PathVariable Long eventId, @RequestParam Long userId) {
        gateService.removeFromProcessing(eventId, userId);
    }
}
