-- ================================================================
-- [TASK 3] 부하 테스트 사전 데이터 세팅 SQL
--
-- 목적: gate-app 대기열 → issuer-api-app 트래픽 셰이핑 E2E 테스트
--
-- 실행 방법:
--   docker exec -i task3-mysql mysql -uroot -proot rediclaim < scripts/init-load-test-task3.sql
--
-- 생성 데이터:
--   users   : id=1 (CREATOR), id=2~2001 (CUSTOMER 2000명)
--   coupons : id=1, remaining_count=500
--
-- k6 실행 시 설정:
--   vus=2000, iterations=2000 → 각 VU 1회 실행, userId=2~2001
--   재고 500개: 500명 SUCCESS, 1500명 OUT_OF_STOCK
-- ================================================================

-- ── 기존 데이터 초기화 ──────────────────────────────────────────
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE user_coupon;
TRUNCATE TABLE coupons;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;

-- ── 프로시저 정의 ───────────────────────────────────────────────
DROP PROCEDURE IF EXISTS insert_load_test_data;

DELIMITER //

CREATE PROCEDURE insert_load_test_data(IN p_user_count INT, IN p_coupon_stock INT)
BEGIN
    DECLARE i INT DEFAULT 0;

    -- 1. Creator 유저 생성 (id=1)
    INSERT INTO users (name, user_type, created_date_time, modified_date_time)
    VALUES ('LoadTestCreator', 'CREATOR', NOW(), NOW());

    -- 2. 쿠폰 생성 (id=1, creator_id=1)
    INSERT INTO coupons (name, remaining_count, creator_id, created_date_time, modified_date_time)
    VALUES ('TASK3-TEST-COUPON', p_coupon_stock, 1, NOW(), NOW());

    -- 3. Customer 유저 생성 (id=2 ~ p_user_count+1)
    WHILE i < p_user_count DO
        INSERT INTO users (name, user_type, created_date_time, modified_date_time)
        VALUES (CONCAT('loadtest-user-', i), 'NORMAL', NOW(), NOW());
        SET i = i + 1;
    END WHILE;
END //

DELIMITER ;

-- ── 데이터 삽입 실행 ────────────────────────────────────────────
CALL insert_load_test_data(2000, 500);

-- ── 결과 검증 ──────────────────────────────────────────────────
SELECT '== 데이터 세팅 완료 ==' AS result;
SELECT 'users'      AS tbl, COUNT(*) AS cnt FROM users
UNION ALL
SELECT 'coupons'    AS tbl, COUNT(*) AS cnt FROM coupons
UNION ALL
SELECT 'user_coupon', COUNT(*) FROM user_coupon;

SELECT id, name, user_type FROM users WHERE id = 1;
SELECT id, name, remaining_count FROM coupons WHERE id = 1;
