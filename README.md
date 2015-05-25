# StaffTracker
StaffTracker, A Bukkit Plugin for managing staff across multiple servers.

This project is currently in ALPHA testing, but will be moving to BETA soon!

  	Built with bukkit-1.6.4-R2.0
   
    Hard Plugin Dependencies : None! ^_^
    Soft Plugin Dependencies :
    	Essentials (2.14)(Vanish provider, backup group permissions support, if configured for groups)
    	VanishNoPacket (Vanish provider)
    	Vault (1.4.2) (Primary group permissions support, if configured for groups)  
    
    Tested extensively on Spigot 1.8.4, 1.7.10, and 1.6.4, as well as
    Cauldron 1.7.10 and 1.6.4 during Alpha.
    
    Pending Feature list :
    	Non-SQL storage method for standalone, non-networked servers.
    	Small class to handle tying together all the various permissions providers we could
    		encounter in the "wild" - this is more to neaten up the code than anything else
    	"Request" specific staff member to a server - /st need <member name>
    	"Request" specific staff group to a server - /st need <group name>
    	"Request" anyone at all to a server - /st need
    	Track AFK status.
    	Configure staff group DISPLAY names, (ie, perm is "mod", but displays as Moderator.
    	Configure staff display color settings (mod blue, admin red, op yellow, etc).
  		
  		How about a configurable list of commands to track, to cover all this :
  			Track /OI (open inventory) command usage.	
  			Track item spawn ins from NEI cheat mode, /give, and creative?
    		Track /kill?
    	
    
    May become pending features :
    	WorldBorder bypass state?
		Feel free to make suggestions!