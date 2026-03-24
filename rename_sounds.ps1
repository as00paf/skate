# Rename motion sound files to clean names
$directory = "C:\workspace\kotlin_workspace\skate\assets\sounds\motion"

Get-ChildItem "$directory\*.wav" | ForEach-Object {
    $oldName = $_.Name
    
    # Remove prefix and suffix, clean up the name
    $newName = $oldName `
        -replace 'FEETHmn-', '' `
        -replace '_AUDIOELK-Fs ', '_' `
        -replace '_AUDIOELK_AUDIOELK\.wav$', '.wav' `
        -replace ' ', '_' `
        -replace '_+', '_' `
        -replace '^_|_$', ''
    
    $newName = $newName.ToLower()
    
    if ($oldName -ne $newName) {
        Rename-Item -Path $_.FullName -NewName $newName
        Write-Host "Renamed: $oldName -> $newName"
    }
}

Write-Host "`nRename complete!"
