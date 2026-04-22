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

pause
