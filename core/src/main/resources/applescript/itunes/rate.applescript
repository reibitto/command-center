tell application "System Events"
	if (name of processes) contains "iTunes" then
		set iTunesRunning to true
	else
		set iTunesRunning to false
	end if
end tell

if iTunesRunning then
	tell application "iTunes"
		set rating of current track to {0} * 20
	end tell
end if