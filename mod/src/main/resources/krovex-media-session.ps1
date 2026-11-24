# Krovex Client: reads Spotify's now-playing state from the Windows media session (the same source as
# the volume flyout), so no Spotify sign-in is needed. Prints one line per second:
#   none
#   <playing 0/1> TAB <position ms> TAB <duration ms> TAB <cover token> TAB <artist> TAB <title>
# The cover token changes whenever a new cover image was written to -CoverPath (64x64 PNG).
# Optional argument: toggle | next | previous  -> sends that command to Spotify once and exits.
param([string]$Command = '', [string]$CoverPath = '')
$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($operation, $resultType) {
    $task = $asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
    [void]$task.Wait(5000)
    $task.Result
}
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
$manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
if ($null -eq $manager) { [Console]::Out.WriteLine('none'); exit 1 }

function Find-Spotify {
    foreach ($session in $manager.GetSessions()) { if ($session.SourceAppUserModelId -match 'Spotify') { return $session } }
    return $null
}

if ($Command -ne '') {
    $session = Find-Spotify
    if ($null -ne $session) {
        switch ($Command) {
            'play'     { [void](Await ($session.TryPlayAsync()) ([bool])) }
            'pause'    { [void](Await ($session.TryPauseAsync()) ([bool])) }
            'toggle'   { [void](Await ($session.TryTogglePlayPauseAsync()) ([bool])) }
            'next'     { [void](Await ($session.TrySkipNextAsync()) ([bool])) }
            'previous' { [void](Await ($session.TrySkipPreviousAsync()) ([bool])) }
        }
    }
    exit 0
}

function Save-Cover($props, $path) {
    try {
        if ($null -eq $props.Thumbnail -or $path -eq '') { return '' }
        [void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
        $ras = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        if ($null -eq $ras) { return '' }
        $asStream = [System.IO.WindowsRuntimeStreamExtensions].GetMethod('AsStreamForRead', [Type[]]@([Windows.Storage.Streams.IInputStream]))
        $stream = $asStream.Invoke($null, @($ras))
        $ms = New-Object System.IO.MemoryStream
        $stream.CopyTo($ms)
        $ms.Position = 0
        Add-Type -AssemblyName System.Drawing
        $img = [System.Drawing.Image]::FromStream($ms)
        $bmp = New-Object System.Drawing.Bitmap 64, 64
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.DrawImage($img, 0, 0, 64, 64)
        $g.Dispose()
        $tmp = $path + '.tmp'
        $bmp.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png)
        $img.Dispose(); $bmp.Dispose(); $ms.Dispose()
        Move-Item -LiteralPath $tmp -Destination $path -Force
        return [string][DateTime]::UtcNow.Ticks
    } catch { return '' }
}
$lastKey = ''
$coverToken = ''

while ($true) {
    $line = 'none'
    $session = Find-Spotify
    if ($null -ne $session) {
        $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
        $info = $session.GetPlaybackInfo()
        $timeline = $session.GetTimelineProperties()
        if ($null -ne $props -and -not [string]::IsNullOrEmpty($props.Title)) {
            $playing = [int]($info.PlaybackStatus -eq 'Playing')
            $position = $timeline.Position.TotalMilliseconds
            if ($playing -eq 1) { $position += ([DateTimeOffset]::UtcNow - $timeline.LastUpdatedTime).TotalMilliseconds }
            $duration = $timeline.EndTime.TotalMilliseconds - $timeline.StartTime.TotalMilliseconds
            $key = $props.Artist + '|' + $props.Title
            if ($key -ne $lastKey) { $lastKey = $key; $coverToken = Save-Cover $props $CoverPath }
            $line = ('{0}' + [char]9 + '{1}' + [char]9 + '{2}' + [char]9 + '{3}' + [char]9 + '{4}' + [char]9 + '{5}') -f $playing, [int64]$position, [int64]$duration, $coverToken, $props.Artist, $props.Title
        }
    }
    [Console]::Out.WriteLine($line)
    [Console]::Out.Flush()
    Start-Sleep -Milliseconds 900
}
