--[[
  선착순 쿠폰 발급 원자 연산 (Redis Lua Script)

  Redis는 Lua 스크립트를 단일 명령으로 처리하므로
  스크립트 실행 도중 다른 명령이 끼어들 수 없다.
  → 별도 분산 락 없이 중복 발급/초과 발급이 원천 차단된다.

  KEYS[1] : coupon:issued:{couponId}  — 발급 완료 userId SET
  KEYS[2] : coupon:stock:{couponId}   — 남은 재고 (String/Integer)
  ARGV[1] : userId (String)

  반환값:
     1  : 발급 성공
     0  : 재고 없음  (COUPON_OUT_OF_STOCK)
    -1  : 중복 발급  (USER_ALREADY_HAS_COUPON)
    -2  : 쿠폰 없음  (COUPON_NOT_FOUND — 재고 키 미존재)
--]]

-- 1. 재고 키 존재 여부 확인 (쿠폰이 Redis에 초기화되어 있는지 검증)
local stock = redis.call('GET', KEYS[2])
if stock == false then
    return -2
end

-- 2. 중복 발급 체크
local isDuplicate = redis.call('SISMEMBER', KEYS[1], ARGV[1])
if isDuplicate == 1 then
    return -1
end

-- 3. 재고 소진 체크
if tonumber(stock) <= 0 then
    return 0
end

-- 4. 재고 차감 + 발급 기록 (두 연산이 한 Lua 트랜잭션 안에서 실행)
redis.call('DECR', KEYS[2])
redis.call('SADD', KEYS[1], ARGV[1])

return 1
