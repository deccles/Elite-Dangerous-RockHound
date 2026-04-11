# Regenerates src/main/resources/edo.ico from the same zoomed asset as the in-app window icon
# (org/dce/ed/RockHound-window.png). Requires ImageMagick 7+ on PATH (`magick`).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$src = Join-Path $root "src/main/resources/org/dce/ed/RockHound-window.png"
$out = Join-Path $root "src/main/resources/edo.ico"
if (-not (Test-Path $src)) { throw "Missing: $src" }
$magick = Get-Command magick -ErrorAction SilentlyContinue
if (-not $magick) { throw "ImageMagick 'magick' not found on PATH." }
& $magick.Source $src -background none -define icon:auto-resize=256,128,96,64,48,32,16 $out
Write-Host "Wrote $out"
