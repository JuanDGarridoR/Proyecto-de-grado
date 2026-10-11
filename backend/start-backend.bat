@echo off
REM Arranca los nueve servicios del backend, cada uno en su propia ventana
REM de consola. %~dp0 es la carpeta de este script, así que funciona sin
REM importar desde dónde se ejecute.

title VITA+ Backend

echo ========================================
echo          INICIANDO VITA+ BACKEND
echo ========================================
echo.

start "Acompanante Service" cmd /k "cd /d %~dp0acompanante-service && .\mvnw.cmd spring-boot:run"

start "Actividad Service" cmd /k "cd /d %~dp0actividad-service && .\mvnw.cmd spring-boot:run"

start "Analitica Service" cmd /k "cd /d %~dp0analitica-service && .\mvnw.cmd spring-boot:run"

start "API Gateway" cmd /k "cd /d %~dp0api-gateway && .\mvnw.cmd spring-boot:run"

start "Auth Service" cmd /k "cd /d %~dp0auth-service && .\mvnw.cmd spring-boot:run"

start "Mensajeria Service" cmd /k "cd /d %~dp0mensajeria-service && .\mvnw.cmd spring-boot:run"

start "Organizacion Service" cmd /k "cd /d %~dp0organizacion-service && .\mvnw.cmd spring-boot:run"

start "Persona Mayor Service" cmd /k "cd /d %~dp0persona-mayor-service && .\mvnw.cmd spring-boot:run"

start "Salud Service" cmd /k "cd /d %~dp0salud-service && .\mvnw.cmd spring-boot:run"

start "Voluntario Service" cmd /k "cd /d %~dp0voluntario-service && .\mvnw.cmd spring-boot:run"

echo.
echo Todos los servicios fueron iniciados.