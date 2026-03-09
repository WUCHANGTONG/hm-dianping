@echo off
chcp 65001 >nul
echo ==========================================
echo    黑马点评微服务停止脚本
echo ==========================================
echo.

echo [1/2] 正在停止基础设施...
docker-compose down

echo.
echo [2/2] 清理完成！
echo.
echo 如需清理数据卷，请运行: docker-compose down -v
echo.

pause
