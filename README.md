# StaffTracker
StaffTracker, A Bukkit Plugin for managing staff across multiple servers.

This project is currently in ALPHA testing, but will be moving to BETA soon!

  	Built with bukkit-1.6.4-R2.0
	Hard Plugin Dependencies : None! ^_^
    Soft Plugin Dependencies :
    	Essentials (2.14)(Vanish provider, fallback group permissions support, if configured for groups)
    	VanishNoPacket (Vanish provider)
    	Vault (1.4.2) (Primary group permissions support, if configured for groups)   
    	
	Tested extensively on Spigot 1.8.4, 1.7.10, and 1.6.4, as well as Cauldron 1.7.10 and 1.6.4 during Alpha.
	
#Features
	Determines if a player is a member of staff via :
		1. Configurable Vault/ZPerms permission groups
		2. Singular Permission
		3. Operator status
		
	Automatically determines the name of the server (either through server.properties, 	or server folder name), as well as Minecraft version.
		
	Tracks the following states (on/off) and duration (how long the member was in that
	state) for staff members :
		1. VanishNoPacket Vanish or Essentials Vanish
		2. Creative Game Mode
		3. Essentials SocialSpy	
	
	Configurable command tracking :
		Allows you to create a list of commands, the usage of which is tracked depending
		on the state that you assign for tracking.
		
		Added under the tracks: configuration section, the format is <command>:<state>,
		with state being :
			1. 'other' - tracks when the first argument is something other than the staff 				members name.
			2. 'self' - tracks when the first argument is the staff members name.
			3. 'all' - tracks regardless of arguments.
			
		Please note that this function is ONLY intercepting commands prior to being
		passed to their respective plugins for handling! It DOES NOT check for command
		success, nor does it care about valid arguments or anything. If you do not even
		have the OpenInventory plugin, but have /oi in the tracking list, if ANY player
		types /oi, the OI command track will be incremented! If you type /kill a, the kill
		command track will be incremented - unless YOUR in game name is 'a' ;)
		
		The player name is expected to be the first argument, directly after
		the space following the command. if the player name is an argument AFTER the first
		then you must use 'all' to get anything like expected results.
		
		Example configuration :
			tracks:
				kill: 'other' 	#tracks when a staff members kills someone, but not suicides
				oi: 'all'			#tracks all uses of /oi
				god: 'all'		#tracks all uses of /god
				op: 'other'		#tracks all uses of /op when used on other people.
			
	
	Stores data in a configured mySQL database. synch reads/asynch writes to prevent
	clogging up the main server thread. Use a single, global SQL server to allow your
	servers to share this data between them!
	
	
#Pending Feature list :
	Configuration additions :
		1. Allow configuration of server name in config.
		2. Allow single permission list, same as permission groups.
	
	Send the list of tracked commands to the StaffRecord class, so it can purge commands
	that we're no longer tracking and keep the DB clean.
	
	"Remove" command to remove a staff member from the DB.
	
	Various permissions, so that a base-level staff member can't pull up detailed 
	records for anyone/everyone, for instance.
		stafftracker.ViewDetails, stafftracker.RemoveRecord
	
	Staff Group Config to replace the current config section :
		
		#groups default and op are required.
		groups:
			#grouping for regular, non-staff players.
			default:
				#determines who can see who in /vanish using /st list
				priority: 0
				displayname: 'Player'
				displaycolor: '1'
				permgroup: ''
				perm: ''
				isop: false
			#default group for ops - only used when the player has op, but otherwise
			#doesn't fit into another group.
			op:
				priority: 0
				displayname: 'Non-Staff Op'
				displaycolor: '2'
				permgroup: ''
				perm: ''
				isop: true
			#Custom groups start here!
			mod:
				priority: 1
				displayname: 'Moderator'
				displaycolor: 'a'
				permgroup: 'moderator'
				perm: ''
				isop: false
			admin:
				priority: 2
				displayname: 'Admin'
				displaycolor: 'b'
				permgroup: 'admin'
				perm: ''
				isop: true
				
	Track AFK state/duration.
	
	Track God/Fly state/duration.
	
	Track states/durations per day, week, and month.

    
	May become pending features :
		Non-SQL storage method for standalone servers?
    	WorldBorder bypass state?
    	
    	Give players access to /st list command, that will show all non-vanished staff?
    	
    	Staff Request :
			Allows someone to put in a request for a staff member to come help.
		
			"Request" specific staff member to a server - /st need <member name> <reason>
			"Request" specific staff group to a server - /st need <group name> <reason>
			"Request" anyone at all to a server - /st neednow <reason>	
			Staff can answer a request with /st answer <request #>
    	
    	Give players access to /st need? Though, really, isn't that what we have ticket
    	systems for? ^_^
    	
    	

Feel free to make suggestions!