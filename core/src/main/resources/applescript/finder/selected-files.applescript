tell application "Finder"
  set selectedItems to selection
  set paths to ""
  repeat with selectedItem in selectedItems
    set paths to (paths & (POSIX path of (selectedItem as text)) & "\n")
  end repeat

  return paths
end tell