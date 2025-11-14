@echo off
title MinguuOfflineJudge
chcp 65001 > nul
.\jre\bin\java.exe -Xmn8192m -Xmx8192m -Dfile.encoding=UTF-8 -Dstdin.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar main.jar