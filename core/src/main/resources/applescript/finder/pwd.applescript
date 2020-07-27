tell application "Finder"
  set current_window to window 1
  return (POSIX path of (target of current_window as alias))
end tell