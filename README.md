# StaffTracker
StaffTracker, A Bukkit Plugin for managing staff across multiple servers.

This project is currently in ALPHA testing, but will be moving to BETA soon!

  	Built with bukkit-1.6.4-R2.0
	Hard Plugin Dependencies : None! ^_^
    Soft Plugin Dependencies :
    	Essentials (2.14)(Vanish provider, backup group permissions support, if configured for groups)
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
		
	Reads/writes from a configured mySQL database. synch reads/asynch writes to prevent
	clogging up the main server thread.
	
	
#Pending Feature list :
	Configuration additions :
		1. Allow configuration of server name in config.
		2. Non-SQL storage method for standalone, non-networked servers.
		3. 	Configure staff group DISPLAY names, (perm is "mod", display as "Moderator"
		4. Configure staff display color settings (mod blue, admin red, op yellow, etc).
		5. Allow single permission list, same as permission groups.
	
	Configurable command tracking :
		Command line : "oi", "kill", "op"
		TrackState : "All", "Self-Only", "Other-Only", "Self-Ignore", "Other-Ignore"
		Will keep track of how many times the command was used in applicable track states.

	"Request" specific staff member to a server - /st need <member name>
	"Request" specific staff group to a server - /st need <group name>
	"Request" anyone at all to a server - /st need
	
	Track AFK state/duration.

    
	May become pending features :
    	WorldBorder bypass state?
    	Give players access to a staff list command, that will show all non-vanished
    	staff?
    	
    	

Feel free to make suggestions!