Add-Type -AssemblyName System.Windows.Forms
$notification = New-Object System.Windows.Forms.NotifyIcon
$notification.Icon = [System.Drawing.SystemIcons]::Information
$notification.BalloonTipIcon = 'Info'
$notification.BalloonTipText = '{0}'
$notification.BalloonTipTitle = '{1}'
$notification.Visible = $True
$notification.ShowBalloonTip(1000)