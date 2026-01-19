package rediclaim.gate.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import rediclaim.gate.repository.GateEnqueueDto;
import rediclaim.gate.repository.GateRedisRepository;
import rediclaim.gate.controller.dto.GateEnqueueResponse;
import rediclaim.gate.controller.dto.GateStatusResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate Controller 통합 테스트
 * - GateRedisRepository를 Mock으로 처리
 * - getStatus()를 통해 queue/processing 상태를 함께 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Gate Controller 통합 테스트")
class GateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GateRedisRepository gateRedisRepository;

    @BeforeEach
    void setUp() {
        reset(gateRedisRepository);
    }

    // ============= Enqueue 테스트 =============

    @Test
    @DisplayName("사용자가 정상적으로 큐에 입장할 수 있다")
    void testEnqueueSuccess() throws Exception {
        // given
        Long eventId = 1001L;
        Long userId = 100L;
        // enqueueLua()는 GateEnqueueDto(isNew=true, rank=0L) 반환
        when(gateRedisRepository.enqueueLua(eventId, userId))
                .thenReturn(new GateEnqueueDto(true, 0L));

        // when
        String response = mockMvc.perform(
                post("/gate/events/{eventId}/enqueue", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then
        GateEnqueueResponse result = objectMapper.readValue(response, GateEnqueueResponse.class);
        assertThat(result.status()).isEqualTo("ENQUEUED");
        assertThat(result.rank()).isEqualTo(1L);  // 1-based (0 + 1)

        // Mock 호출 검증
        verify(gateRedisRepository, times(1)).enqueueLua(eventId, userId);
    }

    @Test
    @DisplayName("동일 사용자 중복 입장은 방지된다")
    void testEnqueueDuplicate() throws Exception {
        // given
        Long eventId = 1001L;
        Long userId = 100L;
        // enqueueLua()는 GateEnqueueDto(isNew=false, rank=0L) 반환 (중복)
        when(gateRedisRepository.enqueueLua(eventId, userId))
                .thenReturn(new GateEnqueueDto(false, 0L));

        // when
        String response = mockMvc.perform(
                post("/gate/events/{eventId}/enqueue", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then
        GateEnqueueResponse result = objectMapper.readValue(response, GateEnqueueResponse.class);
        assertThat(result.status()).isEqualTo("ALREADY_ENQUEUED");
        assertThat(result.rank()).isEqualTo(1L);
    }

    @Test
    @DisplayName("여러 사용자 입장 시 순서가 유지된다")
    void testMultipleUsersEnqueue() throws Exception {
        // given
        Long eventId = 1001L;
        Long user1 = 100L;
        Long user2 = 200L;
        Long user3 = 300L;

        // user1 enqueue
        when(gateRedisRepository.enqueueLua(eventId, user1))
                .thenReturn(new GateEnqueueDto(true, 0L));

        // when & then - user1
        String response1 = mockMvc.perform(
                post("/gate/events/{eventId}/enqueue", eventId)
                        .param("userId", String.valueOf(user1))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        GateEnqueueResponse result1 = objectMapper.readValue(response1, GateEnqueueResponse.class);
        assertThat(result1.rank()).isEqualTo(1L);

        // user2 enqueue
        when(gateRedisRepository.enqueueLua(eventId, user2))
                .thenReturn(new GateEnqueueDto(true, 1L));

        String response2 = mockMvc.perform(
                post("/gate/events/{eventId}/enqueue", eventId)
                        .param("userId", String.valueOf(user2))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        GateEnqueueResponse result2 = objectMapper.readValue(response2, GateEnqueueResponse.class);
        assertThat(result2.rank()).isEqualTo(2L);

        // user3 enqueue
        when(gateRedisRepository.enqueueLua(eventId, user3))
                .thenReturn(new GateEnqueueDto(true, 2L));

        String response3 = mockMvc.perform(
                post("/gate/events/{eventId}/enqueue", eventId)
                        .param("userId", String.valueOf(user3))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        GateEnqueueResponse result3 = objectMapper.readValue(response3, GateEnqueueResponse.class);
        assertThat(result3.rank()).isEqualTo(3L);
    }

    // ============= Status 조회 테스트 (getStatus 메서드) =============

    @Test
    @DisplayName("WAITING 상태: 입장한 사용자의 상태와 순번을 조회할 수 있다")
    void testGetStatusWaiting() throws Exception {
        // given
        Long eventId = 1001L;
        Long userId = 100L;

        // queue에 있는 경우
        when(gateRedisRepository.getRank(eventId, userId)).thenReturn(Optional.of(0L));  // 첫 번째
        when(gateRedisRepository.isProcessing(eventId, userId)).thenReturn(false);

        // when
        String response = mockMvc.perform(
                get("/gate/events/{eventId}/rank", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then
        GateStatusResponse result = objectMapper.readValue(response, GateStatusResponse.class);
        assertThat(result.status()).isEqualTo("WAITING");
        assertThat(result.rank()).isEqualTo(1L);  // 1-based
    }

    @Test
    @DisplayName("PROCESSING 상태: dispatcher로 전달된 사용자의 상태 조회")
    void testGetStatusProcessing() throws Exception {
        // given
        Long eventId = 1001L;
        Long userId = 100L;

        // queue에는 없지만 processing 중인 경우
        when(gateRedisRepository.getRank(eventId, userId)).thenReturn(Optional.empty());
        when(gateRedisRepository.isProcessing(eventId, userId)).thenReturn(true);

        // when
        String response = mockMvc.perform(
                get("/gate/events/{eventId}/rank", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then
        GateStatusResponse result = objectMapper.readValue(response, GateStatusResponse.class);
        assertThat(result.status()).isEqualTo("PROCESSING");
        assertThat(result.rank()).isNull();  // PROCESSING 중에는 rank 없음
    }

    @Test
    @DisplayName("UNKNOWN 상태: 미입장 또는 이미 발급 완료된 사용자")
    void testGetStatusUnknown() throws Exception {
        // given
        Long eventId = 1001L;
        Long userId = 100L;

        // queue와 processing 모두에 없는 경우
        when(gateRedisRepository.getRank(eventId, userId)).thenReturn(Optional.empty());
        when(gateRedisRepository.isProcessing(eventId, userId)).thenReturn(false);

        // when
        String response = mockMvc.perform(
                get("/gate/events/{eventId}/rank", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then
        GateStatusResponse result = objectMapper.readValue(response, GateStatusResponse.class);
        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.rank()).isNull();
    }

    // ============= Race Condition 시나리오 테스트 =============

    @Test
    @DisplayName("Enqueue 후 처리 중(PROCESSING)일 때 상태 조회하면 PROCESSING이 반환된다")
    void testRaceConditionEnqueueToProcessing() throws Exception {
        // given
        Long eventId = 1001L;
        Long userId = 100L;

        // Step 1: Enqueue (큐에 추가)
        when(gateRedisRepository.enqueueLua(eventId, userId))
                .thenReturn(new GateEnqueueDto(true, 0L));

        String enqueueResponse = mockMvc.perform(
                post("/gate/events/{eventId}/enqueue", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GateEnqueueResponse enqueueResult = objectMapper.readValue(enqueueResponse, GateEnqueueResponse.class);
        assertThat(enqueueResult.status()).isEqualTo("ENQUEUED");

        // Step 2: Dispatcher가 queue -> processing으로 이동시킴
        // 이제 queue에는 없고 processing 상태
        when(gateRedisRepository.getRank(eventId, userId)).thenReturn(Optional.empty());
        when(gateRedisRepository.isProcessing(eventId, userId)).thenReturn(true);

        // when - getStatus 호출
        String statusResponse = mockMvc.perform(
                get("/gate/events/{eventId}/rank", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then - PROCESSING 상태로 반환되어야 함 (UNKNOWN이 아님)
        GateStatusResponse statusResult = objectMapper.readValue(statusResponse, GateStatusResponse.class);
        assertThat(statusResult.status()).isEqualTo("PROCESSING");
        assertThat(statusResult.rank()).isNull();
    }

    // ============= 다중 이벤트 테스트 =============

    @Test
    @DisplayName("서로 다른 이벤트의 큐는 독립적이다")
    void testIndependentEventQueues() throws Exception {
        // given
        Long event1 = 1001L;
        Long event2 = 1002L;
        Long user = 100L;

        // event1에서 user는 첫 번째
        when(gateRedisRepository.enqueueLua(event1, user))
                .thenReturn(new GateEnqueueDto(true, 0L));

        // event2에서도 user는 첫 번째 (독립적)
        when(gateRedisRepository.enqueueLua(event2, user))
                .thenReturn(new GateEnqueueDto(true, 0L));

        // when & then - event1
        String response1 = mockMvc.perform(
                post("/gate/events/{eventId}/enqueue", event1)
                        .param("userId", String.valueOf(user))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        GateEnqueueResponse result1 = objectMapper.readValue(response1, GateEnqueueResponse.class);
        assertThat(result1.rank()).isEqualTo(1L);

        // when & then - event2
        String response2 = mockMvc.perform(
                post("/gate/events/{eventId}/enqueue", event2)
                        .param("userId", String.valueOf(user))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        GateEnqueueResponse result2 = objectMapper.readValue(response2, GateEnqueueResponse.class);
        assertThat(result2.rank()).isEqualTo(1L);  // 같은 user지만 event2에서는 1번
    }

    // ============= FIFO 순서 테스트 =============

    @Test
    @DisplayName("먼저 입장한 사용자가 더 낮은 순번을 가진다 (FIFO)")
    void testFIFOOrder() throws Exception {
        // given
        Long eventId = 1001L;

        // 10명이 순서대로 입장
        for (int i = 1; i <= 10; i++) {
            Long userId = (long) (100 + i);
            when(gateRedisRepository.enqueueLua(eventId, userId))
                    .thenReturn(new GateEnqueueDto(true, (long) (i - 1)));
        }

        // when & then
        for (int i = 1; i <= 10; i++) {
            Long userId = (long) (100 + i);
            String response = mockMvc.perform(
                    post("/gate/events/{eventId}/enqueue", eventId)
                            .param("userId", String.valueOf(userId))
            )
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            GateEnqueueResponse result = objectMapper.readValue(response, GateEnqueueResponse.class);
            assertThat(result.rank()).isEqualTo((long) i);  // 1-based
        }
    }

    // ============= 일관성 테스트 =============

    @Test
    @DisplayName("enqueue 응답의 rank와 getStatus 조회 결과가 일치한다")
    void testConsistencyBetweenEnqueueAndGetStatus() throws Exception {
        // given
        Long eventId = 1001L;
        Long userId = 100L;

        when(gateRedisRepository.enqueueLua(eventId, userId))
                .thenReturn(new GateEnqueueDto(true, 0L));

        // when - enqueue
        String enqueueResponse = mockMvc.perform(
                post("/gate/events/{eventId}/enqueue", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GateEnqueueResponse enqueueResult = objectMapper.readValue(enqueueResponse, GateEnqueueResponse.class);

        // when - getStatus
        when(gateRedisRepository.getRank(eventId, userId)).thenReturn(Optional.of(0L));
        when(gateRedisRepository.isProcessing(eventId, userId)).thenReturn(false);

        String statusResponse = mockMvc.perform(
                get("/gate/events/{eventId}/rank", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GateStatusResponse statusResult = objectMapper.readValue(statusResponse, GateStatusResponse.class);

        // then
        assertThat(statusResult.rank()).isEqualTo(enqueueResult.rank());
        assertThat(statusResult.status()).isEqualTo("WAITING");
    }

    // ============= 여러 상태 변화 테스트 =============

    @Test
    @DisplayName("사용자가 WAITING -> PROCESSING -> UNKNOWN 상태로 전환된다")
    void testStateTransitions() throws Exception {
        // given
        Long eventId = 1001L;
        Long userId = 100L;

        // Phase 1: Enqueue (WAITING 상태)
        when(gateRedisRepository.enqueueLua(eventId, userId))
                .thenReturn(new GateEnqueueDto(true, 0L));

        mockMvc.perform(
                post("/gate/events/{eventId}/enqueue", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk());

        // Phase 2: 상태 조회 - WAITING
        when(gateRedisRepository.getRank(eventId, userId)).thenReturn(Optional.of(0L));
        when(gateRedisRepository.isProcessing(eventId, userId)).thenReturn(false);

        String waitingResponse = mockMvc.perform(
                get("/gate/events/{eventId}/rank", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GateStatusResponse waitingStatus = objectMapper.readValue(waitingResponse, GateStatusResponse.class);
        assertThat(waitingStatus.status()).isEqualTo("WAITING");

        // Phase 3: Dispatcher에 의해 processing으로 이동
        when(gateRedisRepository.getRank(eventId, userId)).thenReturn(Optional.empty());
        when(gateRedisRepository.isProcessing(eventId, userId)).thenReturn(true);

        String processingResponse = mockMvc.perform(
                get("/gate/events/{eventId}/rank", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GateStatusResponse processingStatus = objectMapper.readValue(processingResponse, GateStatusResponse.class);
        assertThat(processingStatus.status()).isEqualTo("PROCESSING");

        // Phase 4: Issuer 처리 완료 후 (양쪽 모두 없음)
        when(gateRedisRepository.getRank(eventId, userId)).thenReturn(Optional.empty());
        when(gateRedisRepository.isProcessing(eventId, userId)).thenReturn(false);

        String unknownResponse = mockMvc.perform(
                get("/gate/events/{eventId}/rank", eventId)
                        .param("userId", String.valueOf(userId))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GateStatusResponse unknownStatus = objectMapper.readValue(unknownResponse, GateStatusResponse.class);
        assertThat(unknownStatus.status()).isEqualTo("UNKNOWN");
    }
}
