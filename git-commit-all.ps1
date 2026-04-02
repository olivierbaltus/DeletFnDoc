#!/usr/bin/env pwsh
# Script pour commiter et pusher tous les changements
# Usage: .\git-commit-all.ps1 "Message de commit"

param(
    [string]$CommitMessage = "feat: Add Maven profiles for FileNet configuration (DEV, HOMOL, PROD)"
)

Write-Host "=== Git Commit & Push Script ===" -ForegroundColor Cyan
Write-Host ""

# Vérifier si on est dans un repo git
if (-not (Test-Path ".git")) {
    Write-Host "ERREUR: Ce n'est pas un dépôt Git!" -ForegroundColor Red
    exit 1
}

# Afficher le statut actuel
Write-Host "Statut actuel:" -ForegroundColor Yellow
git status --short

Write-Host ""
Write-Host "Ajout de tous les fichiers (sauf target/ et checkpoint.db)..." -ForegroundColor Yellow

# Ajouter tous les fichiers sources et documentation
git add src/
git add pom.xml
git add *.md
git add *.ps1
git add *.sh
git add *.bat
git add .gitignore

# S'assurer que target/ et checkpoint.db ne sont pas ajoutés
git reset HEAD target/ 2>$null
git reset HEAD checkpoint.db 2>$null

Write-Host ""
Write-Host "Fichiers à commiter:" -ForegroundColor Yellow
git status --short

Write-Host ""
$confirm = Read-Host "Voulez-vous continuer avec le commit? (O/N)"

if ($confirm -ne "O" -and $confirm -ne "o") {
    Write-Host "Opération annulée." -ForegroundColor Yellow
    exit 0
}

# Commit
Write-Host ""
Write-Host "Commit en cours..." -ForegroundColor Yellow
git commit -m "$CommitMessage"

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR lors du commit!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Commit réussi!" -ForegroundColor Green

# Push
Write-Host ""
$push = Read-Host "Voulez-vous pusher vers origin? (O/N)"

if ($push -eq "O" -or $push -eq "o") {
    Write-Host "Push en cours..." -ForegroundColor Yellow
    git push origin main
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "✓ Push réussi!" -ForegroundColor Green
        Write-Host ""
        Write-Host "Sur l'autre machine, exécutez:" -ForegroundColor Cyan
        Write-Host "  git pull origin main" -ForegroundColor White
    } else {
        Write-Host ""
        Write-Host "ERREUR lors du push!" -ForegroundColor Red
        Write-Host "Vous pouvez pusher manuellement avec: git push origin main" -ForegroundColor Yellow
    }
} else {
    Write-Host ""
    Write-Host "Push annulé. Vous pouvez pusher plus tard avec:" -ForegroundColor Yellow
    Write-Host "  git push origin main" -ForegroundColor White
}

Write-Host ""
Write-Host "=== Terminé ===" -ForegroundColor Cyan