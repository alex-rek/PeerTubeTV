Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$projectRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $env:USERPROFILE 'Documents\Codex\PeerTubeTV-signing'
$keystorePath = Join-Path $signingDirectory 'PeerTubeTV-release.jks'
$keyAlias = 'peertubetv'
$keyAlreadyExists = Test-Path -LiteralPath $keystorePath

$form = New-Object System.Windows.Forms.Form
$form.Text = 'PeerTubeTV - Sign release'
$form.Size = New-Object System.Drawing.Size(530, 270)
$form.StartPosition = 'CenterScreen'
$form.TopMost = $true
$form.MinimizeBox = $false
$form.MaximizeBox = $false

$label = New-Object System.Windows.Forms.Label
$label.Text = if ($keyAlreadyExists) { "Use the existing signing key:`r`n$keystorePath`r`nEnter the password you used when creating it." } else { "A new signing key will be created at:`r`n$keystorePath`r`nChoose a strong password and keep it safe for future updates." }
$label.Location = New-Object System.Drawing.Point(16, 16)
$label.Size = New-Object System.Drawing.Size(480, 64)
$form.Controls.Add($label)

$passwordLabel = New-Object System.Windows.Forms.Label
$passwordLabel.Text = if ($keyAlreadyExists) { 'Keystore password:' } else { 'New keystore password:' }
$passwordLabel.Location = New-Object System.Drawing.Point(16, 98)
$passwordLabel.Size = New-Object System.Drawing.Size(180, 24)
$form.Controls.Add($passwordLabel)

$passwordBox = New-Object System.Windows.Forms.TextBox
$passwordBox.Location = New-Object System.Drawing.Point(200, 96)
$passwordBox.Size = New-Object System.Drawing.Size(290, 24)
$passwordBox.UseSystemPasswordChar = $true
$form.Controls.Add($passwordBox)

$confirmLabel = New-Object System.Windows.Forms.Label
$confirmLabel.Text = 'Confirm password:'
$confirmLabel.Location = New-Object System.Drawing.Point(16, 140)
$confirmLabel.Size = New-Object System.Drawing.Size(180, 24)
$form.Controls.Add($confirmLabel)

$confirmBox = New-Object System.Windows.Forms.TextBox
$confirmBox.Location = New-Object System.Drawing.Point(200, 138)
$confirmBox.Size = New-Object System.Drawing.Size(290, 24)
$confirmBox.UseSystemPasswordChar = $true
$confirmLabel.Visible = -not $keyAlreadyExists
$confirmBox.Visible = -not $keyAlreadyExists
$form.Controls.Add($confirmBox)

$ok = New-Object System.Windows.Forms.Button
$ok.Text = if ($keyAlreadyExists) { 'Build signed APK' } else { 'Create key and build signed APK' }
$ok.Location = New-Object System.Drawing.Point(200, $(if ($keyAlreadyExists) { 150 } else { 184 }))
$ok.Size = New-Object System.Drawing.Size(290, 32)
$ok.DialogResult = [System.Windows.Forms.DialogResult]::OK
$form.Controls.Add($ok)
$form.AcceptButton = $ok
$form.Add_Shown({ $passwordBox.Focus() })

if ($form.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { exit 1 }

$password = $passwordBox.Text
$confirmation = $confirmBox.Text
$passwordBox.Clear()
$confirmBox.Clear()
$form.Dispose()

if (-not $keyAlreadyExists -and $password.Length -lt 8) {
    [System.Windows.Forms.MessageBox]::Show('Use at least 8 characters for the keystore password.', 'PeerTubeTV', 'OK', 'Error') | Out-Null
    exit 1
}
if (-not $keyAlreadyExists -and $password -cne $confirmation) {
    [System.Windows.Forms.MessageBox]::Show('The passwords do not match.', 'PeerTubeTV', 'OK', 'Error') | Out-Null
    exit 1
}
if (-not $keyAlreadyExists -and (Test-Path -LiteralPath $keystorePath)) {
    [System.Windows.Forms.MessageBox]::Show("Signing key already exists; it will not be overwritten:`r`n$keystorePath", 'PeerTubeTV', 'OK', 'Error') | Out-Null
    exit 1
}

New-Item -ItemType Directory -Force -Path $signingDirectory -ErrorAction Stop | Out-Null
$javaHome = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($javaHome)) { $javaHome = 'C:\Program Files\Android\Android Studio\jbr' }
$keytool = Join-Path $javaHome 'bin\keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) {
    [System.Windows.Forms.MessageBox]::Show('keytool.exe was not found in the Android Studio JBR.', 'PeerTubeTV', 'OK', 'Error') | Out-Null
    exit 1
}

$env:KEYSTORE_PATH = $keystorePath
$env:KEY_ALIAS = $keyAlias
$env:KEYSTORE_PASSWORD = $password
$env:KEY_PASSWORD = $password
$env:KEYTOOL_STOREPASS = $password
$env:KEYTOOL_KEYPASS = $password
$password = $null
$confirmation = $null

$buildExitCode = 1
$buildLogPath = Join-Path $projectRoot 'release-build.log'
Set-Content -LiteralPath $buildLogPath -Value "PeerTubeTV release build started: $(Get-Date -Format o)" -Encoding UTF8
try {
    if (-not $keyAlreadyExists) {
        & $keytool -genkeypair -v -keystore $keystorePath -alias $keyAlias -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=PeerTubeTV Release' -storepass:env KEYTOOL_STOREPASS -keypass:env KEYTOOL_KEYPASS
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the signing key.' }
    }

    Push-Location $projectRoot
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaHome\bin;$env:PATH"
    & (Join-Path $projectRoot 'gradlew.bat') ':app:assembleRelease' 2>&1 | Tee-Object -FilePath $buildLogPath -Append
    $buildExitCode = $LASTEXITCODE
} catch {
    Write-Error $_
    Add-Content -LiteralPath $buildLogPath -Value "`r`nBuild script error: $($_.Exception.Message)"
} finally {
    if ((Get-Location).Path -eq $projectRoot) { Pop-Location }
    Remove-Item Env:KEYSTORE_PATH, Env:KEY_ALIAS, Env:KEYSTORE_PASSWORD, Env:KEY_PASSWORD, Env:KEYTOOL_STOREPASS, Env:KEYTOOL_KEYPASS -ErrorAction SilentlyContinue
}

if ($buildExitCode -eq 0) {
    [System.Windows.Forms.MessageBox]::Show("Signed APK built successfully.`r`nKeystore: $keystorePath`r`nAPK: app\build\outputs\apk\release\app-release.apk`r`nKeep the keystore and password: future updates must use this same key.", 'Done', 'OK', 'Information') | Out-Null
} else {
    [System.Windows.Forms.MessageBox]::Show("Build failed with exit code $buildExitCode.`r`nDetails saved to:`r`n$buildLogPath", 'Build error', 'OK', 'Error') | Out-Null
    Write-Host "Build failed. Details: $buildLogPath" -ForegroundColor Red
    Read-Host 'Press Enter to close this window'
}
exit $buildExitCode
