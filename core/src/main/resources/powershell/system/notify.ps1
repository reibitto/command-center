Add-Type -AssemblyName System.Windows.Forms
$notification = New-Object System.Windows.Forms.NotifyIcon
$notification.Icon = [System.Drawing.SystemIcons]::Information
$notification.BalloonTipIcon = 'Info'
$notification.BalloonTipText = '{0}'
$notification.BalloonTipTitle = '{1}'
$notification.Visible = $True
$notification.ShowBalloonTip(1000)

# Keep the process alive long enough for Windows to render the balloon tip
Start-Sleep -Seconds 3
