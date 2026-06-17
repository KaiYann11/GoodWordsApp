@echo off
setlocal
cd /d "%~dp0"
node app_good_words_server.mjs --host 127.0.0.1 --port 8765 --seed
