@echo off
set /p github_url="Vstavte posilannya na vash GitHub repo (napr. https://github.com/user/repo.git): "

echo Initializing Git...
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin %github_url%

echo Pushing to GitHub...
git push -u origin main

echo.
echo ======================================================
echo USPISHNO! Teper dodajte sekreti na GitHub:
echo SSH_HOST = 5.253.188.23
echo SSH_USER = sxnsh1ness
echo SSH_PASSWORD = 72EzY7KhScBy
echo ======================================================
pause
