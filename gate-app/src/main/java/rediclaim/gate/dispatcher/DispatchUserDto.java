package rediclaim.gate.dispatcher;

public record DispatchUserDto(
        Long userId,
        Long rank       // 유저의 순위
) {
}
