-- ================================================================
-- [TASK 1] 부하 테스트 사전 데이터 세팅 SQL
--
-- 목적: k6 setup() 의 5000명 유저 생성이 멀티 서버 환경에서
--       setup timeout(60s)을 초과하는 문제를 해결하기 위해
--       DB에 직접 데이터를 삽입한다.
--
-- 실행 방법:
--   [단일 서버]
--   docker exec -i task1-mysql mysql -uroot -proot rediclaim < scripts/init-load-test.sql
--
--   [멀티 서버]
--   docker exec -i task1-multi-mysql mysql -uroot -proot rediclaim < scripts/init-load-test.sql
--
-- 생성 데이터:
--   users   : id=1 (CREATOR), id=2~10001 (CUSTOMER 10000명)
--   coupons : id=1, remaining_count=1000
--
-- k6 실행 시 환경변수:
--   COUPON_ID=1  MIN_USER_ID=2  USER_COUNT=10000
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
    VALUES ('MULTI-TEST-COUPON', p_coupon_stock, 1, NOW(), NOW());

    -- 3. Customer 유저 10000명 생성 (id=2 ~ p_user_count+1)
    WHILE i < p_user_count DO
        INSERT INTO users (name, user_type, created_date_time, modified_date_time)
        VALUES (CONCAT('loadtest-user-', i), 'NORMAL', NOW(), NOW());
        SET i = i + 1;
    END WHILE;
END //

DELIMITER ;

-- ── 데이터 삽입 실행 ────────────────────────────────────────────
CALL insert_load_test_data(10000, 1000);

-- ── 결과 검증 ──────────────────────────────────────────────────
SELECT '== 데이터 세팅 완료 ==' AS result;
SELECT 'users'      AS tbl, COUNT(*) AS cnt FROM users
UNION ALL
SELECT 'coupons'    AS tbl, COUNT(*) AS cnt FROM coupons
UNION ALL
SELECT 'user_coupon', COUNT(*) FROM user_coupon;

SELECT id, name, user_type FROM users WHERE id = 1;
SELECT id, name, remaining_count FROM coupons WHERE id = 1;
