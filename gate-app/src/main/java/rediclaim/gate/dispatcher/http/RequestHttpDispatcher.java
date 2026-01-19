package rediclaim.gate.dispatcher.http;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import rediclaim.gate.dispatcher.DispatchUserDto;
import rediclaim.gate.dispatcher.RequestDispatcher;

import java.util.List;

@Slf4j
@ConditionalOnProperty(
        name = "gate.dispatch-mode",
        havingValue = "http"
)
@Service
@RequiredArgsConstructor
public class RequestHttpDispatcher implements RequestDispatcher {

    @Override
    public void dispatchBatch(Long eventId, List<DispatchUserDto> dtos) {
        // 미구현
    }
}
