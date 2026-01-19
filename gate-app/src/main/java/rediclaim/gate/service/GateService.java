package rediclaim.gate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rediclaim.gate.config.GateProperties;
import rediclaim.gate.dispatcher.DispatchUserDto;
import rediclaim.gate.dispatcher.RequestDispatcher;
import rediclaim.gate.repository.GateEnqueueDto;
import rediclaim.gate.repository.GateRedisRepository;
import rediclaim.gate.controller.dto.GateEnqueueResponse;
import rediclaim.gate.controller.dto.GateStatusResponse;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GateService {

    private final GateRedisRepository gateRedisRepository;
    private final GateProperties gateProperties;
    private final RequestDispatcher requestDispatcher;

    public GateEnqueueResponse enqueue(Long eventId, Long userId) {
        GateEnqueueDto result = gateRedisRepository.enqueueLua(eventId, userId);

        // 이미 대기열에 있던 사용자인지 여부에 따라 결과 반환
        if (result.enqueued()) {
            return new GateEnqueueResponse("ENQUEUED", result.rank() + 1);
        } else {
            return new GateEnqueueResponse("ALREADY_ENQUEUED", result.rank() + 1);
        }
    }

    public GateStatusResponse getStatus(Long eventId, Long userId) {
        // 1. 대기열 확인
        Long rank = gateRedisRepository.getRank(eventId, userId).orElse(null);
        if (rank != null) {
            return new GateStatusResponse("WAITING", rank + 1);
        }

        // 2. 처리열 확인 (Gate 입장에서는 PROCESSING, Client 입장에서는 '입장 중')
        if (gateRedisRepository.isProcessing(eventId, userId)) {
            return new GateStatusResponse("PROCESSING", null);
        }

        // 3. 둘 다 없음 (이미 발급 완료되었거나, 타임아웃, 혹은 미진입)
        // Issuer 쪽 DB까지 확인하거나, 클라이언트에게 '정보 없음' 리턴
        return new GateStatusResponse("UNKNOWN", null);
    }

    /**
     * 한번에 N명 처리: queue -> processing -> dispatcher 로 전달
     */
    public int dispatchOnce(Long eventId) {
        int rate = gateProperties.getDispatchQuantity();
        if (rate <= 0) return 0;

        // [User, Ticket, User, Ticket ...] 리스트 반환됨
        List<String> rawList = gateRedisRepository.popToProcessing(eventId, rate);
        if (rawList.isEmpty()) return 0;

        List<DispatchUserDto> dispatchUsers = new ArrayList<>();

        for (int i = 0; i < rawList.size(); i += 2) {
            String userIdStr = rawList.get(i);
            String ticketStr = rawList.get(i + 1);

            // Ticket 번호 유실 없이 그대로 전달 가능
            dispatchUsers.add(new DispatchUserDto(
                    Long.parseLong(userIdStr),
                    // Redis Score는 double이므로 소수점 제거 변환 필요할 수 있음
                    (long) Double.parseDouble(ticketStr)
            ));
        }

        requestDispatcher.dispatchBatch(eventId, dispatchUsers);
        return dispatchUsers.size();
    }
}
