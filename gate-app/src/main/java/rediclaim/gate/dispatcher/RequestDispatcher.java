package rediclaim.gate.dispatcher;

import java.util.List;

public interface RequestDispatcher {

    void dispatchBatch(Long eventId, List<DispatchUserDto> dtos);
}
