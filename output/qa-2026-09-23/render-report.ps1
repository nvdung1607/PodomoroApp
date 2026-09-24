$ErrorActionPreference = 'Stop'
$reportPath = Join-Path $PSScriptRoot 'BAO-CAO-KIEM-THU-FOCUSDO.md'
$body = (ConvertFrom-Markdown -LiteralPath $reportPath).Html
$template = @'
<!doctype html>
<html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Báo cáo kiểm thử FocusDo · 24/09/2026</title>
<style>
:root{color-scheme:light;--ink:#17212b;--muted:#53616e;--line:#dce3e8;--accent:#9c3218}
*{box-sizing:border-box}body{margin:0;background:#eef2f5;color:var(--ink);font:16px/1.7 system-ui,-apple-system,"Segoe UI",sans-serif}
header{background:#17212b;color:#fff;padding:34px max(24px,calc((100vw - 1100px)/2));border-bottom:5px solid #db6849}
header small{letter-spacing:.12em;text-transform:uppercase;color:#b9c9d7}header strong{display:block;font-size:30px;line-height:1.3;margin:7px 0}header p{margin:8px 0;color:#d2dce4}
main{max-width:1160px;background:white;margin:28px auto;padding:42px 48px;box-shadow:0 8px 32px #17212b0c;border:1px solid var(--line);border-radius:12px}
h1{font-size:32px;line-height:1.25}h2{font-size:24px;margin:46px 0 18px;padding-top:22px;border-top:2px solid var(--line)}h3{font-size:20px;line-height:1.4;margin:34px 0 12px;color:#7f2b17;border-left:4px solid #d97960;padding:8px 14px;background:#fff5f1}
p{margin:12px 0}a{color:#9b3218;text-underline-offset:3px}a:hover{color:#521608}strong{font-weight:650}
table{width:100%;border-collapse:collapse;display:block;overflow:auto;font-size:14px;margin:22px 0}th{background:#e9eef2;text-align:left}th,td{padding:12px 14px;border:1px solid var(--line);vertical-align:top;min-width:160px}tr:nth-child(even){background:#f8fafb}
code{font-size:.87em;background:#f0f3f6;border-radius:4px;padding:2px 5px;overflow-wrap:anywhere}pre{background:#17212b;color:#eef4f8;padding:20px;border-radius:8px;overflow:auto}pre code{background:none;padding:0}
img{display:block;max-width:100%;max-height:760px;object-fit:contain;object-position:left top;margin:18px 0 32px;border:1px solid var(--line);border-radius:8px;cursor:zoom-in}
li{margin:10px 0}footer{color:var(--muted);font-size:13px;border-top:1px solid var(--line);margin-top:36px;padding-top:18px}
.actions{display:flex;gap:12px;flex-wrap:wrap;margin:18px 0}.actions a,.actions button{font:inherit;border:1px solid #adbecb;background:#fff;color:#17212b;padding:8px 14px;border-radius:7px;text-decoration:none;cursor:pointer}
@media(max-width:700px){main{margin:0;border:0;border-radius:0;padding:24px 18px}h1{font-size:26px}h2{font-size:22px}h3{font-size:18px}header{padding:24px 18px}table{font-size:13px}}
@media print{body{background:#fff;font-size:10pt}main{margin:0;padding:0;border:0;box-shadow:none;max-width:none}header,.actions{display:none}h2,h3{break-after:avoid}table{display:table;font-size:9pt}th,td{min-width:0;padding:6px}img{max-height:650px;break-inside:avoid}a{color:inherit}pre{white-space:pre-wrap}footer{display:none}}
</style></head><body>
<header><small>Quality assurance · Android · Evidence-based review</small><strong>FocusDo — Báo cáo kiểm thử trải nghiệm</strong><p>32 phát hiện · Samsung API 36 · Kiểm thử thực tế, ảnh gốc và đối chiếu mã nguồn</p></header>
<main><div class="actions"><a href="BAO-CAO-KIEM-THU-FOCUSDO.md" download>Tải bản Markdown</a><button onclick="window.print()">In / Lưu PDF</button></div>
__BODY__
<footer>Báo cáo cục bộ. Không tải ảnh hay dữ liệu lên dịch vụ bên ngoài. Nhấp ảnh để xem ảnh gốc.</footer></main>
<script>document.querySelectorAll('img').forEach(img=>{const a=document.createElement('a');a.href=img.getAttribute('src');a.target='_blank';a.rel='noopener';img.parentNode.insertBefore(a,img);a.appendChild(img);});</script>
</body></html>
'@
$html = $template.Replace('__BODY__', $body)
$target = Join-Path $PSScriptRoot 'BAO-CAO-KIEM-THU-FOCUSDO.html'
[System.IO.File]::WriteAllText($target, $html, [System.Text.UTF8Encoding]::new($false))
$source = Get-Content -LiteralPath $reportPath -Raw
$ids = [regex]::Matches($source, '(?m)^### QA-\d+')
if ($ids.Count -ne 32) { throw "Expected 32 findings, found $($ids.Count)" }
foreach ($match in [regex]::Matches($source, '!\[[^\]]*\]\(([^)]+)\)')) {
    if (!(Test-Path -LiteralPath (Join-Path $PSScriptRoot $match.Groups[1].Value))) { throw "Missing image $($match.Groups[1].Value)" }
}
Get-Item -LiteralPath $reportPath,$target | Select-Object Name,Length
Write-Output "Verified: 32 findings; all embedded screenshots exist."
