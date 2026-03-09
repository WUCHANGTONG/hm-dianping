@echo off
chcp 65001 >nul
echo ==========================================
echo    黑马点评微服务 - 全量测试脚本
echo ==========================================
echo.

set MVN_CMD=mvn test -q

REM 检查Maven
%mvn_cmd% -version >nul 2>&1
if errorlevel 1 (
    echo [错误] Maven 未安装或不在PATH中
    pause
    exit /b 1
)

echo 测试分类：
echo   [1] 用户服务测试
echo   [2] 商铺服务测试
echo   [3] 评论服务测试
echo   [4] 秒杀服务测试
echo   [5] 搜索服务测试
echo   [6] 运行全部测试
echo   [0] 退出
echo.

set /p choice=请选择要运行的测试(0-6):

if "%choice%"=="0" goto :eof
if "%choice%"=="1" goto :user_test
if "%choice%"=="2" goto :shop_test
if "%choice%"=="3" goto :comment_test
if "%choice%"=="4" goto :seckill_test
if "%choice%"=="5" goto :search_test
if "%choice%"=="6" goto :all_test

echo 无效的选择
goto :eof

:user_test
echo.
echo ========== 运行用户服务测试 ==========
cd hm-user-service
%mvn_cmd% -Dtest=UserServiceTest
echo.
echo ========== 运行关注服务测试 ==========
%mvn_cmd% -Dtest=FollowServiceTest
cd ..
pause
goto :eof

:shop_test
echo.
echo ========== 运行商铺服务测试 ==========
cd hm-shop-service
%mvn_cmd% -Dtest=ShopServiceTest
echo.
echo ========== 运行商铺类型测试 ==========
%mvn_cmd% -Dtest=ShopTypeServiceTest
cd ..
pause
goto :eof

:comment_test
echo.
echo ========== 运行笔记服务测试 ==========
cd hm-comment-service
%mvn_cmd% -Dtest=BlogServiceTest
cd ..
pause
goto :eof

:seckill_test
echo.
echo ========== 运行秒杀服务测试 ==========
cd hm-seckill-service
%mvn_cmd% -Dtest=SeckillKafkaTest
echo.
echo ========== 运行优惠券测试 ==========
%mvn_cmd% -Dtest=VoucherServiceTest
cd ..
pause
goto :eof

:search_test
echo.
echo ========== 运行搜索服务测试 ==========
cd hm-search-service
%mvn_cmd% -Dtest=SearchServiceTest
echo.
echo ========== 运行推荐服务测试 ==========
%mvn_cmd% -Dtest=RecommendServiceTest
cd ..
pause
goto :eof

:all_test
echo.
echo ========== 运行全部测试 ==========
echo.
echo [1/7] 用户服务测试...
cd hm-user-service
%mvn_cmd% -Dtest=UserServiceTest,FollowServiceTest
cd ..

echo [2/7] 商铺服务测试...
cd hm-shop-service
%mvn_cmd% -Dtest=ShopServiceTest,ShopTypeServiceTest
cd ..

echo [3/7] 评论服务测试...
cd hm-comment-service
%mvn_cmd% -Dtest=BlogServiceTest
cd ..

echo [4/7] 秒杀服务测试...
cd hm-seckill-service
%mvn_cmd% -Dtest=SeckillKafkaTest,VoucherServiceTest
cd ..

echo [5/7] 搜索服务测试...
cd hm-search-service
%mvn_cmd% -Dtest=SearchServiceTest,RecommendServiceTest
cd ..

echo.
echo ==========================================
echo    全部测试运行完成！
echo ==========================================
pause
goto :eof
