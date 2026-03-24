# Clean up redundant names in motion sounds
$directory = "C:\workspace\kotlin_workspace\skate\assets\sounds\motion"

# Fix concrete files (remove duplicate "concrete_")
Get-ChildItem "$directory\concrete_concrete_*.wav" | ForEach-Object {
    $oldName = $_.Name
    $newName = $oldName -replace '^concrete_concrete_', 'concrete_'
    Rename-Item -Path $_.FullName -NewName $newName
    Write-Host "Renamed: $oldName -> $newName"
}

# Fix grass files (remove duplicate "grass_")
Get-ChildItem "$directory\grass_grass_*.wav" | ForEach-Object {
    $oldName = $_.Name
    $newName = $oldName -replace '^grass_grass_', 'grass_'
    Rename-Item -Path $_.FullName -NewName $newName
    Write-Host "Renamed: $oldName -> $newName"
}

# Fix gravel files (remove duplicate "gravel_")
Get-ChildItem "$directory\gravel_gravel_*.wav" | ForEach-Object {
    $oldName = $_.Name
    $newName = $oldName -replace '^gravel_gravel_', 'gravel_'
    Rename-Item -Path $_.FullName -NewName $newName
    Write-Host "Renamed: $oldName -> $newName"
}

# Fix gravel_loose files (remove duplicate "gravel_")
Get-ChildItem "$directory\gravel_loose_gravel_*.wav" | ForEach-Object {
    $oldName = $_.Name
    $newName = $oldName -replace '^gravel_loose_gravel_', 'gravel_loose_'
    Rename-Item -Path $_.FullName -NewName $newName
    Write-Host "Renamed: $oldName -> $newName"
}

Write-Host "`nCleanup complete!"
