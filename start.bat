@echo off
chcp 65001 >nul
echo ==========================================
echo    黑马点评微服务一键启动脚本
echo ==========================================
echo.

REM 检查Docker是否运行
docker info >nul 2>&1
if errorlevel 1 (
    echo [错误] Docker 未运行，请先启动 Docker Desktop
    pause
    exit /b 1
)

echo [1/4] 正在启动基础设施中间件...
docker-compose up -d

if errorlevel 1 (
    echo [错误] 启动基础设施失败
    pause
    exit /b 1
)

echo.
echo [2/4] 正在检查服务健康状态...
timeout /t 10 /nobreak >nul

REM 检查MySQL
docker exec mysql mysqladmin ping -uroot -proot >nul 2>&1
if errorlevel 1 (
    echo [警告] MySQL 可能还未完全启动，请稍等...
) else (
    echo [OK] MySQL 运行正常
)

REM 检查Redis
docker exec redis redis-cli -a 123456 ping >nul 2>&1
if errorlevel 1 (
    echo [警告] Redis 可能还未完全启动，请稍等...
) else (
    echo [OK] Redis 运行正常
)

REM 检查Nacos
curl -s http://localhost:8848/nacos/v1/ns/operator/metrics >nul 2>&1
if errorlevel 1 (
    echo [警告] Nacos 可能还未完全启动，请稍等...
) else (
    echo [OK] Nacos 运行正常
)

echo.
echo [3/4] 服务地址信息:
echo ------------------------------------------
echo API Gateway:     http://localhost:8090
echo Nacos控制台:     http://localhost:8848/nacos (nacos/nacos)
echo Sentinel控制台:  http://localhost:8858
echo RabbitMQ管理:    http://localhost:15672 (guest/guest)
echo Kibana:          http://localhost:5601
echo Kafka UI:        http://localhost:8080
echo MinIO控制台:     http://localhost:9001 (minioadmin/minioadmin)
echo ------------------------------------------

echo.
echo [4/4] 基础设施启动完成！
echo.
echo 下一步：请在IDEA中启动以下微服务：
echo   1. hm-gateway (端口: 8090)
echo   2. hm-user-service (端口: 8081)
echo   3. hm-shop-service (端口: 8082)
echo   4. hm-comment-service (端口: 8083)
echo   5. hm-seckill-service (端口: 8084)
echo   6. hm-search-service (端口: 8085)
echo.
echo 或使用命令: mvn spring-boot:run
echo.

pause
