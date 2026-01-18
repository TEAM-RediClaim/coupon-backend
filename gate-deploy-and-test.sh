#!/bin/bash

# ============================================
# Gate 모듈 단독 부하테스트 스크립트 (Issuer 제외)
# ============================================

set -e  # 에러 발생 시 즉시 종료

echo "🚀 ===== Gate 모듈 단독 배포 및 부하테스트 시작 ====="
echo ""

# ============================================
# Step 1: Kind 클러스터 확인/생성
# ============================================
echo "📦 Step 1: Kind 클러스터 확인/생성"
echo "===================================="

if kind get clusters | grep -q '^kind$'; then
    echo "✅ Kind 클러스터가 이미 존재합니다"
else
    echo "🔨 Kind 클러스터 생성 중..."
    kind create cluster --config k8s/kind-cluster.yml
    echo "✅ Kind 클러스터 생성 완료"
fi
echo ""

# ============================================
# Step 2: Namespace 생성
# ============================================
echo "📦 Step 2: Namespace 생성"
echo "=========================="

kubectl create namespace rediclaim 2>/dev/null || echo "✅ rediclaim namespace 이미 존재"
echo ""

# ============================================
# Step 3: 인프라 배포
# ============================================
echo "📦 Step 3: 인프라 배포"
echo "======================"

echo "🔨 Redis 배포 중..."
kubectl apply -f k8s/redis.yml
echo "✅ Redis 배포 완료"
echo ""

echo "🔨 Redis Commander 배포 중..."
kubectl apply -f k8s/redis-commander.yml
echo "✅ Redis Commander 배포 완료"
echo ""

echo "🔨 Kafka 배포 중..."
kubectl apply -f k8s/kafka.yml
echo "✅ Kafka 배포 완료"
echo ""

echo "🔨 Kafka UI 배포 중..."
kubectl apply -f k8s/kafka-ui.yml
echo "✅ Kafka UI 배포 완료"
echo ""

echo "🔨 MySQL 배포 중..."
kubectl apply -f k8s/mysql.yml
echo "✅ MySQL 배포 완료"
echo ""

# 인프라 시작 대기
echo "⏳ 인프라 시작을 기다리는 중... (30초)"
sleep 30
echo ""

# ============================================
# Step 4: 애플리케이션 배포 (Gate ONLY)
# ============================================
echo "📦 Step 4: 애플리케이션 배포 (Gate App Only)"
echo "=========================================="

echo "🔨 Gate App 배포 중..."
kubectl apply -f k8s/gate-app-deployment.yml
echo "✅ Gate App 배포 완료"
echo ""

echo "ℹ️ Issuer App 배포 건너뜀 (Gate 단독 테스트)"
echo ""

# ============================================
# Step 5: 포드 상태 확인
# ============================================
echo "📦 Step 5: 포드 상태 확인"
echo "========================"

echo "⏳ Gate 포드가 Running 상태가 될 때까지 대기 중..."
kubectl rollout status deployment/gate-app -n rediclaim --timeout=300s
echo ""

echo "🔍 현재 포드 상태:"
kubectl get pods -n rediclaim
echo ""

# ============================================
# Step 6: 로그 확인
# ============================================
echo "📦 Step 6: 로그 확인"
echo "==================="

echo "🔍 Gate App 로그 (최근 20줄):"
kubectl logs -n rediclaim -l app=gate-app --tail=20
echo ""

# ============================================
# Step 7: API 연결 테스트
# ============================================
echo "📦 Step 7: API 연결 테스트"
echo "==========================="

echo "🧪 Gate API 연결 테스트 (localhost:8000)..."
for i in {1..10}; do
    if curl -s http://127.0.0.1:8000/actuator/health > /dev/null 2>&1; then
        echo "✅ Gate API 응답 정상 (Health Check OK)"
        break
    else
        echo "⏳ 대기 중... ($i/10)"
        sleep 2
    fi
done
echo ""

# ============================================
# Step 8: K6 부하테스트 실행
# ============================================
echo "📦 Step 8: K6 부하테스트 실행"
echo "=============================="

if ! command -v k6 &> /dev/null; then
    echo "❌ K6이 설치되어 있지 않습니다"
    exit 1
fi

echo "🧪 부하테스트 시작..."
echo "📊 Redis 모니터링: http://localhost:8081"
echo "📊 Kafka 모니터링: http://localhost:8082"
echo "🚦 Gate 엔드포인트: http://localhost:8000"
echo ""
K6_WEB_DASHBOARD=true k6 run scripts/k6-gate-spike.js
echo ""
echo "다른 부하테스트 스크립트도 실행하려면 scripts/ 디렉토리를 확인하세요."
echo ""

echo "✅ ===== Gate 단독 부하테스트 완료 ====="
echo ""
