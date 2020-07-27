tell application "iTunes"
  set currentTrack to the current track

  if exists name of currentTrack then
  -- TODO: Escape tabs
    return (name of currentTrack) & "\t" & (artist of currentTrack) & "\t" & (album of currentTrack) & "\t" & (rating of currentTrack)
  end if
end tell