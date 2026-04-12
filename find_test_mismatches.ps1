Get-ChildItem -Path "backend\src\test\java" -Filter "*.java" -Recurse | ForEach-Object {
    $file = $_
    $content = Get-Content $file.FullName -Raw
    if ($content -match "package\s+([\w\.]+);") {
        $foundPackage = $Matches[1]
        $expectedPackage = ($file.DirectoryName -replace ".*backend\\src\\test\\java\\", "" -replace "\\", ".")
        if ($foundPackage -ne $expectedPackage) {
            Write-Output "Mismatch: $($file.FullName)"
            Write-Output "  Found:    $foundPackage"
            Write-Output "  Expected: $expectedPackage"
        }
    }
}
