$entityDir = "Z:\Livora website\livora-backend\backend\src\main\java"
$dbDump = Get-Content "Z:\Livora website\livora-backend\db_schema_dump.txt"

# Parse DB schema into hashtable: table -> set of columns
$dbSchema = @{}
foreach ($line in $dbDump) {
    if ($line -match '^\s+(\w+)\s*\|\s*(\w+)\s*\|') {
        $tbl = $matches[1].Trim()
        $col = $matches[2].Trim()
        if ($tbl -eq "table_name" -or $col -eq "column_name") { continue }
        if (-not $dbSchema.ContainsKey($tbl)) { $dbSchema[$tbl] = @{} }
        $dbSchema[$tbl][$col] = $true
    }
}

Write-Host "DB tables found: $($dbSchema.Keys.Count)"

# Parse entities
$entityFiles = Get-ChildItem $entityDir -Recurse -Filter "*.java"
$results = @()
$tablesChecked = @{}

foreach ($f in $entityFiles) {
    $content = Get-Content $f.FullName -Raw
    if ($content -notmatch '@Entity' -or $content -notmatch '@Table\(name') { continue }
    
    # Extract table name
    if ($content -notmatch '@Table\(name\s*=\s*"([^"]+)"') { continue }
    $tableName = $matches[1]
    
    if (-not $dbSchema.ContainsKey($tableName)) {
        Write-Host "WARNING: Table '$tableName' from $($f.Name) NOT FOUND in DB"
        continue
    }
    
    $tablesChecked[$tableName] = $true
    $dbCols = $dbSchema[$tableName]
    
    # Extract @Column(name = "...") 
    $colMatches = [regex]::Matches($content, '@Column\([^)]*name\s*=\s*"([^"]+)"')
    foreach ($m in $colMatches) {
        $col = $m.Groups[1].Value
        if (-not $dbCols.ContainsKey($col)) {
            $results += [PSCustomObject]@{ Table=$tableName; MissingColumn=$col; Source="@Column" }
        }
    }
    
    # Extract @JoinColumn(name = "...")
    $jcMatches = [regex]::Matches($content, '@JoinColumn\([^)]*name\s*=\s*"([^"]+)"')
    foreach ($m in $jcMatches) {
        $col = $m.Groups[1].Value
        if (-not $dbCols.ContainsKey($col)) {
            $results += [PSCustomObject]@{ Table=$tableName; MissingColumn=$col; Source="@JoinColumn" }
        }
    }
}

Write-Host ""
Write-Host "=== MISSING COLUMNS REPORT ==="
$results | Sort-Object Table, MissingColumn | Format-Table -AutoSize

Write-Host ""
Write-Host "Total missing: $($results.Count)"
