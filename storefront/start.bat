@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================
echo   赋签 MaxLabel 门面站 - 开发服务器
echo ============================================
echo.

rem ---- 1. 前置：Node / pnpm ----------------------------------------------
where node >nul 2>&1
if errorlevel 1 (
  echo [X] 没找到 node。请先安装 Node.js 20.19 以上版本。
  echo     本项目要求：node ^>=20.19.0，pnpm ^>=8.6.0
  goto :fail
)

for /f "tokens=1 delims=." %%v in ('node -p "process.versions.node"') do set NODE_MAJOR=%%v
if %NODE_MAJOR% LSS 20 (
  echo [X] Node 版本过低：当前大版本 %NODE_MAJOR%，本项目要求 ^>=20.19.0。
  echo     ^(Vite 8 需要 Node 20 以上^)
  goto :fail
)

where pnpm >nul 2>&1
if errorlevel 1 (
  echo [X] 没找到 pnpm。安装：npm i -g pnpm
  goto :fail
)
echo [OK] node v%NODE_MAJOR%.x / pnpm 已就绪

rem ---- 2. 依赖 ----------------------------------------------------------
if not exist "node_modules" (
  echo.
  echo [..] 首次运行，正在安装依赖（网络受限时可改用：
  echo      pnpm install --registry=https://registry.npmmirror.com ^)
  call pnpm install
  if errorlevel 1 (
    echo [X] 依赖安装失败。
    goto :fail
  )
)
echo [OK] 依赖已就绪

rem ---- 3. 后端（可选，缺失只警告不拦截） --------------------------------
rem  前端所有数据都来自 /app-api，后端不在就只剩空页面。
netstat -ano | findstr /c:":48080" | findstr /c:"LISTENING" >nul 2>&1
if errorlevel 1 (
  echo.
  echo [!] 后端未在 48080 监听 —— 页面能打开，但商品/订单等都会是空的。
  echo     另开一个窗口启动后端：
  echo       cd ..\yudao-cloud
  echo       java -jar yudao-server\target\yudao-server.jar
  echo     冷启动约 63 秒；需要远程 Redis 与 CynosDB 可达。
  echo     ^(切勿加 --spring.profiles.active=local，那会回落到本机 MySQL 而起不来^)
  echo.
) else (
  echo [OK] 后端 48080 已在监听
)

rem ---- 4. 启动 ----------------------------------------------------------
echo.
echo [..] 启动 Vite 开发服务器：http://localhost:5173
echo      其余常用命令：pnpm test ^| pnpm test:e2e ^| pnpm ts:check ^| pnpm lint
echo      按 Ctrl+C 停止。
echo.
call pnpm dev
goto :eof

:fail
echo.
pause
exit /b 1
