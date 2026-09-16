param (
    [string]$Mode = "prod"
)

$ErrorActionPreference = "Stop"

Write-Host "Iniciando compilacion canonica en modo: $Mode"

# Determinar variables segun el modo
if ($Mode -eq "prod") {
    $ConfigArg = "prod paid"
    $RspackMode = "production"
} elseif ($Mode -eq "dev") {
    $ConfigArg = "d paid"
    $RspackMode = "development"
} else {
    Write-Error "Modo no soportado. Usa 'prod' o 'dev'."
    exit 1
}

# Paso 1: Configurar modo
Write-Host "Paso 1: Configurando modo en nova-src..."
Push-Location nova-src
try {
    node ./utils/config.js $ConfigArg.Split(" ")[0] $ConfigArg.Split(" ")[1]
} catch {
    Write-Error "Fallo la configuracion."
    exit 1
}

# Paso 2: Ejecutar rspack
Write-Host "Paso 2: Ejecutando rspack en modo $RspackMode..."
try {
    npx rspack --mode $RspackMode
} catch {
    Write-Error "Fallo rspack."
    exit 1
}

# Paso 3: Ejecutar build cordova
Write-Host "Paso 3: Ejecutando build cordova..."
try {
    npx cordova build android --debug
} catch {
    Write-Error "Fallo cordova build."
    exit 1
}

# Paso 4: Extraer version
Write-Host "Paso 4: Extrayendo version de config.xml..."
[xml]$configXml = Get-Content .\config.xml
$Version = $configXml.widget.version
Write-Host "Version detectada: $Version"

Pop-Location

# Paso 5: Copiar APK
$ApkPath = "nova-src\platforms\android\app\build\outputs\apk\debug\app-debug.apk"
$DestPath = "GoogleAntigravity-v${Version}-ARM64.apk"
Write-Host "Paso 5: Copiando APK a $DestPath..."
Copy-Item -Path $ApkPath -Destination $DestPath -Force

# Paso 6: Validar tamano
Write-Host "Paso 6: Validando tamaño..."
$ApkFileInfo = Get-Item $DestPath
$ApkSizeMB = $ApkFileInfo.Length / 1MB
if ($ApkSizeMB -lt 35) {
    Write-Error "Error fatal: El tamano del APK es menor a 35 MB ($($ApkSizeMB) MB). Faltan libs nativas o assets PRoot."
    exit 1
}

# Paso 7: SHA256 y tamano
Write-Host "Paso 7: Calculando hash SHA256 y tamaño..."
$FileHash = Get-FileHash -Path $DestPath -Algorithm SHA256
Write-Host "Tamano exacto: $($ApkFileInfo.Length) bytes"
Write-Host "SHA256: $($FileHash.Hash)"

Write-Host "Compilacion exitosa."
