param([string]$Name = 'current')
$a='C:/Users/dungk/AppData/Local/Android/Sdk/platform-tools/adb.exe'
& $a shell uiautomator dump /sdcard/qa.xml | Out-Null
& $a pull /sdcard/qa.xml "$PSScriptRoot/$Name.xml" | Out-Null
[xml]$x=Get-Content "$PSScriptRoot/$Name.xml"
$x.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' -or $_.class -eq 'android.widget.EditText' } | ForEach-Object { "$($_.text) | $($_.'content-desc') | $($_.bounds) | enabled=$($_.enabled)" }
& $a shell screencap -p /sdcard/qa.png
& $a pull /sdcard/qa.png "$PSScriptRoot/$Name.png" | Out-Null
