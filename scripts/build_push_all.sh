#!/bin/bash

# TAG 입력값 처리 (첫 번째 인자, 없으면 latest)
# Input TAG processing (first argument, default to 'latest')
TAG=${1:-latest}

# NOTE: AWS configurations for ECR
# NOTE: AWS ECR 설정
AWS_ACCOUNT_ID=805616056609
AWS_REGION=us-east-1
ECR="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

# NOTE: ANSI Color Codes
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "===================================================="
echo -e "🚀 Building and pushing images with TAG: ${CYAN}$TAG${NC}"
echo "===================================================="

# ECR 인증 (Docker Login) - 푸시 전 반드시 필요
# ECR authentication (Docker Login) - required before push
echo -e "🔑 Logging in to ECR..."
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR" 2>&1

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ ECR 로그인 실패. AWS 자격 증명을 확인하세요.${NC}"
    echo -e "${RED}❌ ECR login failed. Check your AWS credentials.${NC}"
    exit 1
fi
echo -e "✅ ECR login successful."
echo ""

# 빌드 및 푸시를 수행하는 함수
# Function to perform build and push
build_and_push() {
    local SERVICE_NAME=$1
    local DOCKERFILE=$2
    local COLOR=$3
    local IMAGE_PATH="$ECR/pgdemo/$SERVICE_NAME:$TAG"

    echo -e "${COLOR}[$SERVICE_NAME]${NC} [START] Building..."
    
    # // NOTE: --progress=plain을 사용하여 병렬 실행 시 로그가 섞이지 않고 스트리밍되도록 설정
    # // Use --progress=plain to ensure logs are streamed clearly in parallel execution
    docker buildx build --progress=plain --platform linux/amd64 -f "$DOCKERFILE" -t "$IMAGE_PATH" --push . 2>&1 | while read -r line; do
        echo -e "${COLOR}[$SERVICE_NAME]${NC} $line"
    done
    
    # 파이프라인의 첫 번째 명령어(docker)의 종료 상태 확인
    # Check the exit status of the first command in the pipeline (docker)
    if [ ${PIPESTATUS[0]} -eq 0 ]; then
        echo -e "${COLOR}[$SERVICE_NAME]${NC} ✅ [SUCCESS] Finished build/push"
    else
        echo -e "${COLOR}[$SERVICE_NAME]${NC} ❌ [ERROR] Failed build/push"
        return 1
    fi
}

# 4개의 서비스를 병렬로 실행 (백그라운드 처리)
# Execute 4 services in parallel (background processing)
build_and_push "pg-main" "docker/Dockerfile.pg-main" "$BLUE" &
build_and_push "pg-admin" "docker/Dockerfile.pg-admin" "$GREEN" &
build_and_push "pg-nginx" "docker/Dockerfile.pg-nginx" "$YELLOW" &
build_and_push "pg-traffic" "docker/Dockerfile.pg-traffic" "$MAGENTA" &

# 모든 백그라운드 프로세스가 종료될 때까지 대기
# Wait for all background processes to finish
wait

echo "===================================================="
echo -e "🎉 ${CYAN}All builds and pushes completed.${NC}"
echo "===================================================="

