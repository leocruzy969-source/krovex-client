param(
    [Parameter(Mandatory)][string]$OriginalJar,
    [Parameter(Mandatory)][string]$OutputJar,
    [Parameter(Mandatory)][string]$MicrosoftClientId
)

$ErrorActionPreference = 'Stop'
$clientId = $MicrosoftClientId.Trim()
if ($clientId -notmatch '^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$' -or
    $clientId -eq [Guid]::Empty.ToString()) {
    throw 'Supply the public Application (client) ID from the Krovex registration.'
}
$original = (Resolve-Path -LiteralPath $OriginalJar).ProviderPath
$output = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputJar)
if (Test-Path -LiteralPath $output) { throw 'Choose a new output JAR path.' }

$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$staging = Join-Path $tempRoot ('krovex-release-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $staging | Out-Null
try {
    $classes = Join-Path $staging 'classes'
    $registration = Join-Path $classes 'krovex-release'
    New-Item -ItemType Directory -Path $registration | Out-Null
    Set-Content -LiteralPath (Join-Path $registration 'microsoft-client-id.txt') -Value $clientId -Encoding ascii
    $sources = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'com') -Recurse -Filter '*.java' |
        ForEach-Object FullName
    & javac -encoding UTF-8 -cp $original -d $classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
    $decoderVersion = '3.15.3'
    $dependencies = @{
        imageio = @('imageio-webp', 'imageio-core', 'imageio-metadata')
        common = @('common-lang', 'common-io', 'common-image')
    }
    foreach ($group in $dependencies.Keys) {
        foreach ($artifact in $dependencies[$group]) {
            $dependency = Join-Path $staging "$artifact.jar"
            $url = "https://repo.maven.apache.org/maven2/com/twelvemonkeys/$group/$artifact/$decoderVersion/$artifact-$decoderVersion.jar"
            Invoke-WebRequest -Uri $url -OutFile $dependency
            Push-Location $classes
            try {
                & jar --extract --file $dependency com
                if ($LASTEXITCODE -ne 0) { throw "Could not package $artifact." }
            } finally { Pop-Location }
        }
    }
    $assembled = Join-Path $staging 'Krovex-Launcher.jar'
    Copy-Item -LiteralPath $original -Destination $assembled
    & jar --update --file $assembled -C $classes . -C $PSScriptRoot assets
    if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed.' }
    Copy-Item -LiteralPath $assembled -Destination $output
} finally {
    $resolved = (Resolve-Path -LiteralPath $staging).ProviderPath
    if ([string]::Equals($resolved, $staging, [StringComparison]::OrdinalIgnoreCase) -and
        [string]::Equals([IO.Path]::GetDirectoryName($resolved).TrimEnd('\'), $tempRoot.TrimEnd('\'),
            [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
