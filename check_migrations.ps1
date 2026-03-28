$files = Get-ChildItem backend/src/main/resources/db/migration -Filter "V*__*.sql"
$versions = @()
foreach ($file in $files) {
    if ($file.Name -match "^V(\d+)__") {
        $versions += [int]$matches[1]
    }
}
$versions = $versions | Sort-Object
$max = ($versions | Measure-Object -Maximum).Maximum
$duplicates = $versions | Group-Object | Where-Object { $_.Count -gt 1 } | Select-Object -ExpandProperty Name
$gaps = 1..$max | Where-Object { $_ -notin $versions }
Write-Host "Max version: $max"
Write-Host "Total files: $($versions.Count)"
Write-Host "Duplicates: $duplicates"
Write-Host "Gaps: $gaps"
