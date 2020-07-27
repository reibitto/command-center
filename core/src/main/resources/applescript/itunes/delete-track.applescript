global addenda

tell application "iTunes"
	set is_playing to player state is not stopped
	
	#if player state is not stopped then
	#display dialog "Are you SURE you want to delete every copy of the currently playing track and move its file to the Trash?" default button 1 with icon 1
	set ofi to fixed indexing
	set fixed indexing to true
	
	try
		set dbid to database ID of current track
		set cla to class of current track
		try
			set floc to (get location of current track)
		end try
		try
			delete (some track of library playlist 1 whose database ID is dbid)
		end try
		set addenda to "Done. The track has been deleted."
		if cla is file track then
			#set my addenda to "Done. The track has been deleted and its file has been moved to the Trash."
			set addenda to null
			my delete_the_file(floc)
		end if
	on error
		set addenda to "The track could not be deleted."
	end try
	
	set fixed indexing to ofi
	
	if addenda is not null then
		display dialog addenda buttons {"Thanks"} default button 1 with icon 1
	end if
	#end if
	
	if is_playing then
		tell application "iTunes" to play
	end if
end tell

to delete_the_file(floc)
	try
		-- tell application "Finder" to delete floc
		do shell script "mv " & quoted form of POSIX path of (floc as string) & " " & quoted form of POSIX path of (path to trash as string)
	on error
		set addenda to "Done. However, the file could not be moved to the Trash."
	end try
end delete_the_file