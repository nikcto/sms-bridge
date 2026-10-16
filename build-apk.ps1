$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$tools = Join-Path $root ".android-tools"
$sdk = Join-Path $tools "sdk"
$gradle = Join-Path $tools "gradle-8.9"
New-Item -ItemType Directory -Force $tools | Out-Null

function Find-Java {
  $cmd = Get-Command java -ErrorAction SilentlyContinue
  if ($cmd) { return Split-Path (Split-Path $cmd.Source -Parent) -Parent }
  $candidates = @("$env:ProgramFiles\Microsoft\jdk-17*", "$env:ProgramFiles\Java\jdk-17*")
  foreach ($pattern in $candidates) { $found = Get-ChildItem $pattern -Directory -ErrorAction SilentlyContinue | Select-Object -First 1; if ($found) { return $found.FullName } }
  return $null
}

$javaHome = Find-Java
if (-not $javaHome) {
  Write-Host "Installing Microsoft OpenJDK 17..."
  winget install --id Microsoft.OpenJDK.17 --exact --accept-source-agreements --accept-package-agreements
  $javaHome = Find-Java
}
if (-not $javaHome) { throw "JDK 17 not found. Restart PowerShell after winget installation and run again." }
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

$gradleBat = Join-Path $gradle "bin\gradle.bat"
if (-not (Test-Path $gradleBat)) {
  $zip = Join-Path $tools "gradle.zip"
  Invoke-WebRequest "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -OutFile $zip
  Expand-Archive $zip -DestinationPath $tools -Force
  Remove-Item $zip -Force
}

$sdkmanager = Join-Path $sdk "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $sdkmanager)) {
  $zip = Join-Path $tools "cmdline-tools.zip"
  Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $zip
  New-Item -ItemType Directory -Force (Join-Path $sdk "cmdline-tools\latest") | Out-Null
  $tmp = Join-Path $tools "cmdline-tmp"
  Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
  Expand-Archive $zip -DestinationPath $tmp -Force
  Move-Item (Join-Path $tmp "cmdline-tools\*") (Join-Path $sdk "cmdline-tools\latest") -Force
  Remove-Item $zip,$tmp -Recurse -Force
}
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk
$env:Path = "$sdk\platform-tools;$sdk\cmdline-tools\latest\bin;$env:Path"
"y`ny`ny`ny`ny`ny`n" | & $sdkmanager --licenses | Out-Null
& $sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

Push-Location (Join-Path $root "android")
try {
  if (-not (Test-Path "gradlew.bat")) { & $gradleBat wrapper --gradle-version 8.9 }
  & .\gradlew.bat assembleDebug --no-daemon
  $apk = Join-Path (Get-Location) "app\build\outputs\apk\debug\app-debug.apk"
  Write-Host "`nAPK готов:`n$apk" -ForegroundColor Green
} finally { Pop-Location }
