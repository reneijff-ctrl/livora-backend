$file = "Z:\Livora website\livora-backend\backend\src\main\java\com\joinlivora\backend\chargeback\ChargebackService.java"
$lines = Get-Content $file -Encoding UTF8
$out = [System.Collections.Generic.List[string]]::new()
$skip = $false
foreach ($line in $lines) {
    if ($line -match '^\s+private void persistLegacyChargeback\(') {
        $skip = $true
        continue
    }
    if ($skip -and $line -match '^\s+private void persistFraudChargeback\(') {
        $skip = $false
    }
    if (-not $skip) {
        $out.Add($line)
    }
}
[System.IO.File]::WriteAllLines($file, $out, [System.Text.UTF8Encoding]::new($false))
Write-Host "Done. Lines before: $($lines.Count), after: $($out.Count)"
