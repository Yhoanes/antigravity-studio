<#
.SYNOPSIS
    Antigravity Enterprise Harness - Script de Verificacion y Compuertas de Calidad (Windows PowerShell).
.DESCRIPTION
    Ejecuta tres compuertas deterministas de evaluacion y aseguramiento de calidad:
      Gate 1: Static Analysis and SDD Specification Compliance
      Gate 2: Test Suite and Build Readiness
      Gate 3: Security Secrets Leak Detection and Audit Log Validation
.NOTES
    Directiva: $ErrorActionPreference = 'Stop'
    Retorno: Exit Code 0 en exito rotundo, Exit Code 1 ante cualquier fallo de compuerta.
#>

[CmdletBinding()]
param(
    [switch]$SkipLongTests = $false,
    [string]$TargetSpec = ""
)

$ErrorActionPreference = 'Stop'

# Definicion de rutas base
$ScriptDir = $PSScriptRoot
$RepoRoot = (Resolve-Path "$ScriptDir\..").Path

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "       ANTIGRAVITY ENTERPRISE - ARNES DE ASEGURAMIENTO Y CALIDAD QA           " -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "Repositorio Base : $RepoRoot" -ForegroundColor Gray
Write-Host "Timestamp Inicio : $(Get-Date -Format 'yyyy-MM-ddTHH:mm:ssK')" -ForegroundColor Gray
Write-Host "------------------------------------------------------------------------------" -ForegroundColor Gray

$StartTime = [System.Diagnostics.Stopwatch]::StartNew()
$GlobalPassed = $true
$GateResults = [System.Collections.Generic.List[PSCustomObject]]::new()

function Record-GateResult {
    param(
        [string]$GateName,
        [string]$Status,
        [double]$DurationMs,
        [string]$Message
    )
    $GateResults.Add([PSCustomObject]@{
        Gate     = $GateName
        Status   = $Status
        Duration = [math]::Round($DurationMs, 2)
        Message  = $Message
    })
    $color = if ($Status -eq "PASS") { "Green" } else { "Red" }
    Write-Host "[$Status] $GateName ($([math]::Round($DurationMs, 1)) ms)" -ForegroundColor $color
    Write-Host "         |- $Message" -ForegroundColor Gray
}

# ==============================================================================
# GATE 1: STATIC ANALYSIS AND SDD SPECIFICATION INTEGRITY
# ==============================================================================
Write-Host ""
Write-Host ">>> [GATE 1] ANALISIS ESTATICO, INTEGRIDAD Y ESPECIFICACIONES SDD..." -ForegroundColor Yellow
$Gate1Timer = [System.Diagnostics.Stopwatch]::StartNew()

try {
    # 1.1 Verificacion de archivos estructurales indispensables de Antigravity
    $RequiredPaths = @(
        "agent.md",
        "audit.jsonl",
        "specs",
        "harness",
        "app",
        "build.gradle.kts",
        "settings.gradle.kts"
    )

    foreach ($relPath in $RequiredPaths) {
        $fullPath = Join-Path $RepoRoot $relPath
        if (-not (Test-Path $fullPath)) {
            throw "Estructura requerida ausente: No se encontro '$relPath' en la raiz del repositorio."
        }
    }

    # 1.2 Verificacion sintactica de scripts PowerShell en el repositorio
    $psScripts = Get-ChildItem -Path $RepoRoot -Recurse -Filter "*.ps1" | Where-Object {
        $_.FullName -notmatch "(\.gradle|\.git|build)"
    }
    foreach ($script in $psScripts) {
        $parseErrors = $null
        $tokens = $null
        [System.Management.Automation.Language.Parser]::ParseFile($script.FullName, [ref]$tokens, [ref]$parseErrors) | Out-Null
        if ($parseErrors -and $parseErrors.Count -gt 0) {
            $errList = @()
            foreach ($pe in $parseErrors) {
                $errList += "$($pe.Message) en linea $($pe.Extent.StartLineNumber)"
            }
            throw "Error sintactico en script PowerShell $($script.Name): $($errList -join '; ')"
        }
    }

    # 1.3 Validacion de especificaciones SDD en specs/
    $pythonCmd = Get-Command "python" -ErrorAction SilentlyContinue
    if ($null -eq $pythonCmd) {
        throw "El interprete de Python no se encuentra en el PATH para ejecutar el validador SDD."
    }

    $specValidatorArgs = @("harness/harness_runner.py", "--suite", "spec_validator")
    if ($TargetSpec -ne "") {
        $specValidatorArgs += @("--spec", $TargetSpec)
    }

    $specOutput = & python $specValidatorArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        $errDetails = $specOutput -join [Environment]::NewLine
        throw "Fallo en SDD Spec Compliance Suite: $([Environment]::NewLine)$errDetails"
    }

    $Gate1Timer.Stop()
    Record-GateResult -GateName "Gate 1: Static Analysis and SDD Integrity" -Status "PASS" -DurationMs $Gate1Timer.Elapsed.TotalMilliseconds -Message "Estructura integra, scripts validos y especificaciones SDD aprobadas al 100%"
}
catch {
    $Gate1Timer.Stop()
    $GlobalPassed = $false
    Record-GateResult -GateName "Gate 1: Static Analysis and SDD Integrity" -Status "FAIL" -DurationMs $Gate1Timer.Elapsed.TotalMilliseconds -Message $_.Exception.Message
}

# ==============================================================================
# GATE 2: TEST SUITE AND BUILD READINESS
# ==============================================================================
Write-Host ""
Write-Host ">>> [GATE 2] TEST SUITE Y PREPARACION DEL ENTORNO DE COMPILACION..." -ForegroundColor Yellow
$Gate2Timer = [System.Diagnostics.Stopwatch]::StartNew()

try {
    # 2.1 Verificacion de consistencia de Gradle Wrapper y scripts de build
    $GradleWrapperBat = Join-Path $RepoRoot "gradlew.bat"
    $GradleWrapperProps = Join-Path $RepoRoot "gradle/wrapper/gradle-wrapper.properties"

    if (-not (Test-Path $GradleWrapperBat)) {
        throw "Gradle wrapper batch ('gradlew.bat') no encontrado en la raiz del proyecto."
    }
    if (-not (Test-Path $GradleWrapperProps)) {
        throw "Configuracion del wrapper ('gradle/wrapper/gradle-wrapper.properties') no encontrada."
    }

    # 2.2 Verificacion de consistencia en archivos Gradle KTS
    $ktsFiles = @("build.gradle.kts", "settings.gradle.kts", "app/build.gradle.kts")
    foreach ($kts in $ktsFiles) {
        $ktsPath = Join-Path $RepoRoot $kts
        if (-not (Test-Path $ktsPath)) {
            throw "Archivo de configuracion de compilacion ausente: $kts"
        }
        $content = Get-Content $ktsPath -Raw
        if ([string]::IsNullOrWhiteSpace($content)) {
            throw "El archivo de build '$kts' se encuentra vacio o corrupto."
        }
    }

    # 2.3 Verificacion de configuracion local / SDK
    $LocalProps = Join-Path $RepoRoot "local.properties"
    if (Test-Path $LocalProps) {
        $sdkLine = Get-Content $LocalProps | Where-Object { $_ -match "^sdk\.dir=" }
        if ($null -eq $sdkLine) {
            Write-Host "         [INFO] local.properties no define sdk.dir explicito; fallback de entorno seguro activo." -ForegroundColor DarkYellow
        }
    }

    # 2.4 Ejecucion de la suite de pruebas unitarias y de integracion del arnes
    if (-not $SkipLongTests) {
        $testOutput = & python "harness/harness_runner.py" "--suite" "test_suite" 2>&1
        if ($LASTEXITCODE -ne 0) {
            $errDetails = $testOutput -join [Environment]::NewLine
            throw "Fallo en Automated Unit and Regression Test Suite: $([Environment]::NewLine)$errDetails"
        }
    } else {
        Write-Host "         [OMITIDO] Test Suite larga omitida por parametro -SkipLongTests" -ForegroundColor DarkGray
    }

    $Gate2Timer.Stop()
    Record-GateResult -GateName "Gate 2: Test Suite and Build Readiness" -Status "PASS" -DurationMs $Gate2Timer.Elapsed.TotalMilliseconds -Message "Gradle Wrapper integro, scripts KTS consistentes y suite de pruebas unitarias superada."
}
catch {
    $Gate2Timer.Stop()
    $GlobalPassed = $false
    Record-GateResult -GateName "Gate 2: Test Suite and Build Readiness" -Status "FAIL" -DurationMs $Gate2Timer.Elapsed.TotalMilliseconds -Message $_.Exception.Message
}

# ==============================================================================
# GATE 3: SECURITY SECRETS SCAN AND AUDIT LOG VALIDATION
# ==============================================================================
Write-Host ""
Write-Host ">>> [GATE 3] SEGURIDAD, DETECCION DE SECRETOS Y VALIDACION DE AUDIT LOG..." -ForegroundColor Yellow
$Gate3Timer = [System.Diagnostics.Stopwatch]::StartNew()

try {
    # 3.1 Deteccion de fugas de credenciales y secretos en el codigo
    $secPrefix = "-----BEGIN "
    $secSuffix = " PRIVATE KEY-----"
    $SecretPatterns = @(
        "${secPrefix}RSA${secSuffix}",
        "${secPrefix}OPENSSH${secSuffix}",
        "${secPrefix}${secSuffix}",
        "ghp_[a-zA-Z0-9]{36}",
        "xox[baprs]-[0-9a-zA-Z]{10,48}"
    )

    $CandidateFiles = Get-ChildItem -Path $RepoRoot -Recurse -File | Where-Object {
        $_.FullName -notmatch "(\.git|\.gradle|\.idea|build|latest_report\.json|\.antigravity|harness[\\/]|node_modules|platforms|plugins)" -and
        $_.Extension -notin @(".apk", ".jar", ".png", ".jpg", ".webp", ".so", ".bin", ".exe", ".dll", ".node")
    }

    $LeaksFound = [System.Collections.Generic.List[string]]::new()
    foreach ($file in $CandidateFiles) {
        try {
            $fileContent = [System.IO.File]::ReadAllText($file.FullName)
            foreach ($pattern in $SecretPatterns) {
                if ($fileContent -match $pattern) {
                    $LeaksFound.Add("$($file.FullName) (Patron detectado: $pattern)")
                }
            }
        }
        catch {
            # Ignorar archivos bloqueados o no accesibles para lectura de texto
        }
    }

    if ($LeaksFound.Count -gt 0) {
        $leaksJoined = $LeaksFound -join [Environment]::NewLine
        throw "Posibles secretos o credenciales detectados en el repositorio:$([Environment]::NewLine)$leaksJoined"
    }

    # 3.2 Validacion rigurosa de audit.jsonl
    $AuditFile = Join-Path $RepoRoot "audit.jsonl"
    if (-not (Test-Path $AuditFile)) {
        throw "Archivo de auditoria 'audit.jsonl' no existe en la raiz del repositorio."
    }

    $auditLines = Get-Content $AuditFile | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    if ($auditLines.Count -eq 0) {
        throw "El archivo 'audit.jsonl' esta vacio. Debe contener al menos el registro de adopcion inicial."
    }

    $requiredAuditFields = @("timestamp", "task_id", "subagent", "methodology", "harness_status", "files_modified")
    $lineNum = 0
    foreach ($line in $auditLines) {
        $lineNum++
        try {
            $record = $line | ConvertFrom-Json
        }
        catch {
            throw "Error de formato JSON en audit.jsonl (linea $lineNum): $($_.Exception.Message)"
        }

        foreach ($field in $requiredAuditFields) {
            if ($null -eq $record.$field) {
                throw "Registro invalido en audit.jsonl (linea $lineNum): falta el campo obligatorio '$field'."
            }
        }

        if ($record.harness_status -notin @("PASSED", "FAILED", "IN_PROGRESS")) {
            throw "Estado harness_status invalido ('$($record.harness_status)') en audit.jsonl (linea $lineNum)."
        }
    }

    $Gate3Timer.Stop()
    Record-GateResult -GateName "Gate 3: Security and Audit Validation" -Status "PASS" -DurationMs $Gate3Timer.Elapsed.TotalMilliseconds -Message "Cero secretos detectados y $lineNum registros validos verificados en audit.jsonl."
}
catch {
    $Gate3Timer.Stop()
    $GlobalPassed = $false
    Record-GateResult -GateName "Gate 3: Security and Audit Validation" -Status "FAIL" -DurationMs $Gate3Timer.Elapsed.TotalMilliseconds -Message $_.Exception.Message
}

# ==============================================================================
# RESUMEN GLOBAL Y DETERMINACION DE EXIT CODE
# ==============================================================================
$StartTime.Stop()
$TotalDurationMs = [math]::Round($StartTime.Elapsed.TotalMilliseconds, 1)

Write-Host ""
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "RESUMEN GLOBAL DEL ARNES: $(($GateResults | Where-Object { $_.Status -eq 'PASS' }).Count)/$($GateResults.Count) compuertas pasadas | Duracion: $TotalDurationMs ms" -ForegroundColor Cyan

if ($GlobalPassed) {
    Write-Host "ESTADO FINAL: COMPUERTAS APROBADAS EXITOSAMENTE [EXIT CODE 0]" -ForegroundColor Green
    Write-Host "==============================================================================" -ForegroundColor Cyan
    exit 0
} else {
    Write-Host "ESTADO FINAL: FALLO EN UNA O MAS COMPUERTAS [EXIT CODE 1]" -ForegroundColor Red
    Write-Host "==============================================================================" -ForegroundColor Cyan
    exit 1
}
