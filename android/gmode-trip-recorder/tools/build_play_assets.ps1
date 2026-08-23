param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$outputDirectory = Join-Path $ProjectRoot 'play-store'
$screenshotPath = Join-Path $outputDirectory 'screenshots\01-attitude-dashboard.png'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

function New-RoundedRectanglePath {
    param([System.Drawing.RectangleF]$Rectangle, [float]$Radius)
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $diameter = $Radius * 2
    $arc = [System.Drawing.RectangleF]::new($Rectangle.X, $Rectangle.Y, $diameter, $diameter)
    $path.AddArc($arc, 180, 90)
    $arc.X = $Rectangle.Right - $diameter
    $path.AddArc($arc, 270, 90)
    $arc.Y = $Rectangle.Bottom - $diameter
    $path.AddArc($arc, 0, 90)
    $arc.X = $Rectangle.X
    $path.AddArc($arc, 90, 90)
    $path.CloseFigure()
    return $path
}

function Draw-GmodeMark {
    param(
        [System.Drawing.Graphics]$Graphics,
        [float]$CenterX,
        [float]$CenterY,
        [float]$Radius
    )
    $red = [System.Drawing.Color]::FromArgb(226, 11, 23)
    $white = [System.Drawing.Color]::White
    $dark = [System.Drawing.Color]::FromArgb(10, 10, 10)
    $ringPen = [System.Drawing.Pen]::new($red, $Radius * 0.10)
    $ringPen.StartCap = 'Round'
    $ringPen.EndCap = 'Round'
    $Graphics.DrawEllipse($ringPen, $CenterX - $Radius, $CenterY - $Radius, $Radius * 2, $Radius * 2)
    $innerPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(90, 255, 255, 255), $Radius * 0.012)
    $Graphics.DrawEllipse($innerPen, $CenterX - $Radius * 0.78, $CenterY - $Radius * 0.78, $Radius * 1.56, $Radius * 1.56)
    for ($index = 0; $index -lt 32; $index++) {
        $angle = ($index * 11.25 - 90) * [Math]::PI / 180
        $outer = $Radius * 0.91
        $inner = if ($index % 4 -eq 0) { $Radius * 0.72 } else { $Radius * 0.79 }
        $tickPen = [System.Drawing.Pen]::new($white, $(if ($index % 4 -eq 0) { $Radius * 0.022 } else { $Radius * 0.011 }))
        $Graphics.DrawLine(
            $tickPen,
            $CenterX + [Math]::Cos($angle) * $inner,
            $CenterY + [Math]::Sin($angle) * $inner,
            $CenterX + [Math]::Cos($angle) * $outer,
            $CenterY + [Math]::Sin($angle) * $outer
        )
        $tickPen.Dispose()
    }
    $pinBrush = [System.Drawing.SolidBrush]::new($white)
    $Graphics.FillEllipse($pinBrush, $CenterX - $Radius * 0.19, $CenterY - $Radius * 0.30, $Radius * 0.38, $Radius * 0.38)
    $pinPath = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $pinPath.AddPolygon([System.Drawing.PointF[]]@(
        [System.Drawing.PointF]::new($CenterX - $Radius * 0.16, $CenterY - $Radius * 0.02),
        [System.Drawing.PointF]::new($CenterX + $Radius * 0.16, $CenterY - $Radius * 0.02),
        [System.Drawing.PointF]::new($CenterX, $CenterY + $Radius * 0.42)
    ))
    $Graphics.FillPath($pinBrush, $pinPath)
    $holeBrush = [System.Drawing.SolidBrush]::new($dark)
    $Graphics.FillEllipse($holeBrush, $CenterX - $Radius * 0.075, $CenterY - $Radius * 0.205, $Radius * 0.15, $Radius * 0.15)
    $ringPen.Dispose(); $innerPen.Dispose(); $pinBrush.Dispose(); $pinPath.Dispose(); $holeBrush.Dispose()
}

$icon = [System.Drawing.Bitmap]::new(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($icon)
$graphics.SmoothingMode = 'AntiAlias'
$graphics.Clear([System.Drawing.Color]::FromArgb(255, 3, 3, 3))
$backgroundPath = New-RoundedRectanglePath -Rectangle ([System.Drawing.RectangleF]::new(18, 18, 476, 476)) -Radius 92
$backgroundBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
    [System.Drawing.RectangleF]::new(0, 0, 512, 512),
    [System.Drawing.Color]::FromArgb(255, 32, 32, 32),
    [System.Drawing.Color]::FromArgb(255, 2, 2, 2),
    90
)
$graphics.FillPath($backgroundBrush, $backgroundPath)
Draw-GmodeMark -Graphics $graphics -CenterX 256 -CenterY 250 -Radius 176
$font = [System.Drawing.Font]::new('Arial', 40, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$format = [System.Drawing.StringFormat]::new(); $format.Alignment = 'Center'
$graphics.DrawString('GMODE', $font, [System.Drawing.Brushes]::White, [System.Drawing.RectangleF]::new(0, 423, 512, 55), $format)
$icon.Save((Join-Path $outputDirectory 'app-icon-512.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$format.Dispose(); $font.Dispose(); $backgroundBrush.Dispose(); $backgroundPath.Dispose(); $graphics.Dispose(); $icon.Dispose()

if (-not (Test-Path -LiteralPath $screenshotPath)) {
    throw "Dashboard screenshot is required before creating the feature graphic: $screenshotPath"
}

$feature = [System.Drawing.Bitmap]::new(1024, 500, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$graphics = [System.Drawing.Graphics]::FromImage($feature)
$graphics.SmoothingMode = 'AntiAlias'
$graphics.InterpolationMode = 'HighQualityBicubic'
$graphics.Clear([System.Drawing.Color]::FromArgb(3, 3, 3))
$screenshot = [System.Drawing.Image]::FromFile($screenshotPath)
$destination = [System.Drawing.Rectangle]::new(405, 86, 595, 335)
$source = [System.Drawing.Rectangle]::new(0, 0, $screenshot.Width, $screenshot.Height)
$graphics.DrawImage($screenshot, $destination, $source, [System.Drawing.GraphicsUnit]::Pixel)
$redPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(226, 11, 23), 4)
$graphics.DrawRectangle($redPen, $destination)
Draw-GmodeMark -Graphics $graphics -CenterX 130 -CenterY 135 -Radius 72
$titleFont = [System.Drawing.Font]::new('Arial', 46, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$bodyFont = [System.Drawing.Font]::new('Arial', 25, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$redBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(226, 11, 23))
$graphics.DrawString('GMODE', $titleFont, [System.Drawing.Brushes]::White, 34, 235)
$graphics.DrawString('TRIP RECORDER', $titleFont, $redBrush, 34, 292)
$graphics.DrawString("OFFLINE GPS  |  3D ATTITUDE`nHOME ASSISTANT SYNC", $bodyFont, [System.Drawing.Brushes]::LightGray, 39, 370)
$feature.Save((Join-Path $outputDirectory 'feature-graphic-1024x500.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$redBrush.Dispose(); $bodyFont.Dispose(); $titleFont.Dispose(); $redPen.Dispose(); $screenshot.Dispose(); $graphics.Dispose(); $feature.Dispose()

$iconInfo = [System.Drawing.Image]::FromFile((Join-Path $outputDirectory 'app-icon-512.png'))
$featureInfo = [System.Drawing.Image]::FromFile((Join-Path $outputDirectory 'feature-graphic-1024x500.png'))
try {
    if ($iconInfo.Width -ne 512 -or $iconInfo.Height -ne 512) { throw 'App icon dimensions are invalid' }
    if ($featureInfo.Width -ne 1024 -or $featureInfo.Height -ne 500) { throw 'Feature graphic dimensions are invalid' }
} finally {
    $iconInfo.Dispose(); $featureInfo.Dispose()
}

Write-Host 'Google Play assets generated and dimension-verified.'
