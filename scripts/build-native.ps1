$SRC_ROOT = "src\main\native\c"
$OUT_ROOT = "target\native"
$EXT = "dll"

Write-Host "[INFO] Detected Windows system"

$dirs = Get-ChildItem -Path $SRC_ROOT -Directory

foreach ($dir in $dirs) {
    $NAME = $dir.Name
    $SRC_PATH = $dir.FullName

    $cFiles = Get-ChildItem -Path $SRC_PATH -Recurse -Include *.c
    $SOURCES = @()
    foreach ($file in $cFiles) {
        $SOURCES += $file.FullName
    }

    $OUT_DIR = Join-Path $OUT_ROOT $NAME
    $OUT_FILE = Join-Path $OUT_DIR "$NAME.$EXT"

    New-Item -ItemType Directory -Force -Path $OUT_DIR | Out-Null

    Write-Host "[BUILD] $NAME → $OUT_FILE"
    gcc -shared -O2 -std=c11 $SOURCES -o $OUT_FILE
}

Write-Host "[SUCCESS] Native build finished"