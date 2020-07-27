tell application "iTunes"
  try
    set new_position to (get player position) + {0}

    if new_position < 0 then
      set new_position to 0
    end if

    set player position to new_position
  end try
end tell
