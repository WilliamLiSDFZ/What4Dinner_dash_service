#!/bin/bash

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示使用说明
show_usage() {
    cat << EOF
使用方法: ./deploy.sh [选项]

选项:
  docker      Docker 模式运行 (默认)
  build       仅构建 Docker 镜像
  stop        停止 Docker 容器
  clean       清理 Docker 资源
  logs        查看应用日志
  restart     重启 Docker 容器
  help        显示此帮助信息

示例:
  ./deploy.sh               # Docker 模式
  ./deploy.sh docker        # Docker 模式
  ./deploy.sh build         # 仅构建镜像
  ./deploy.sh stop          # 停止容器
  ./deploy.sh logs          # 查看日志
  ./deploy.sh clean         # 清理资源

EOF
}

# 检查 Docker 是否安装
check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "未找到 Docker，请先安装 Docker"
        print_info "访问 https://docs.docker.com/get-docker/ 获取安装说明"
        exit 1
    fi
}

# 构建 Docker 镜像
build_docker() {
    print_info "构建 Docker 镜像..."
    check_docker

    docker compose build

    if [ $? -eq 0 ]; then
        print_success "Docker 镜像构建成功"
    else
        print_error "Docker 镜像构建失败"
        exit 1
    fi
}

# Docker 模式运行
start_docker() {
    print_info "启动 Docker 模式..."
    check_docker

    # 停止旧容器并构建新镜像
    docker compose down 2>/dev/null || true
    build_docker

    # 运行应用容器
    print_info "启动应用容器..."
    docker compose up -d

    if [ $? -eq 0 ]; then
        print_success "应用已启动"
        print_info "应用地址: http://localhost:8082"
        print_info "查看日志: ./deploy.sh logs"
        print_info "停止应用: ./deploy.sh stop"
    else
        print_error "应用启动失败"
        exit 1
    fi
}

# 停止 Docker 容器
stop_docker() {
    print_info "停止应用容器..."
    check_docker

    docker compose down

    if [ $? -eq 0 ]; then
        print_success "应用已停止"
    else
        print_error "停止应用失败"
        exit 1
    fi
}

# 查看日志
view_logs() {
    print_info "查看应用日志 (Ctrl+C 退出)..."
    check_docker

    docker compose logs -f app
}

# 重启容器
restart_docker() {
    print_info "重启应用容器..."
    check_docker

    docker compose restart app

    if [ $? -eq 0 ]; then
        print_success "应用已重启"
    else
        print_error "重启应用失败"
        exit 1
    fi
}

# 清理 Docker 资源
clean_docker() {
    print_info "清理 Docker 资源..."
    check_docker

    # 停止并删除容器
    docker compose down

    # 删除镜像
    read -r -p "是否删除镜像? (y/N): " confirm
    if [[ $confirm == [yY] || $confirm == [yY][eE][sS] ]]; then
        docker rmi what4dinner-dash-service:latest 2>/dev/null || true
        print_success "镜像已删除"
    fi

    print_success "Docker 资源已清理"
}

# 主函数
main() {
    print_info "=== What4Dinner Dash Service ==="

    MODE=${1:-docker}

    case $MODE in
        docker)
            start_docker
            ;;
        build)
            build_docker
            ;;
        stop)
            stop_docker
            ;;
        logs)
            view_logs
            ;;
        restart)
            restart_docker
            ;;
        clean)
            clean_docker
            ;;
        help|--help|-h)
            show_usage
            ;;
        *)
            print_error "未知选项: $MODE"
            echo ""
            show_usage
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"
