package net.mineyourmind.mrwisski;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.kitteh.vanish.VanishManager;
import org.kitteh.vanish.VanishPlugin;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.perm.PermissionsHandler;
import com.google.common.io.Files;

/** StaffTracker - A Bukkit plugin template
 * 
 *  @author MrWisski
 *  @version 0.0.1a 14 May 2015
 * 
 * */
public final class StaffTracker extends JavaPlugin {
	//
	public static enum MCVer {	UNKNOWN, 
								ONE_SIX_FOUR, 
								ONE_SEVEN_TEN, 
								ONE_EIGHT, ONE_EIGHT_ONE, ONE_EIGHT_TWO, ONE_EIGHT_THREE, ONE_EIGHT_FOUR}

	private final String SQL_RETRY_DEFAULT = "retry";
	private final int CONF_VER = 4;
	
	//Are we printing debugging/troubleshooting messages to the log?
	boolean debug = true;
	
	//If we're using essentials
	Plugin essentialsPlugin;
	Essentials essentials;
	//If we're using VanishNoPacket
	Plugin vanishPlugin;
	VanishPlugin vanish;
	VanishManager vanishMan;

	Permission permission = null;
	PermissionsHandler essPerms = null;
	
	//Flag to let us know that the server IS accepting connections, and its safe to schedule
	//our timer
	boolean firstLogin = true;
	
	//a BukkitTask for our update-all-records task.
	BukkitTask updateTimer = null;
	
	//Stash for the server name
	public static String MCServerName = "Unknown Server";
	
	public MCVer serverVersion = MCVer.UNKNOWN;
	//Our instance
	protected static StaffTracker instance;
	//Config file stuff
	private ConfigMan myConfFile;
	public static YamlConfiguration pluginConf;
	
	//Is this plugin enabled? Do its functions work?
	public boolean enabled = false;
	
	//Some more nice statics to have around.
	public static Server server;
	public static Logger Log;
	
	//SQL Initialization related variables
	public boolean useSQL = false;
	private SQLDB sqldb = null;
	
	//Storage array for keeping track of staff members on this server!
	Hashtable<String,StaffRecord> staffInd;
	
	//Cache for Staff groups
	List<String> staffGroups;
	
	/** Just a small function to ensure the tables exist, before we go pushing data to them.
	 * 
	 * @return boolean - true if successful, otherwise false.
	 */
	private boolean initSQLTables(){
		if(sqldb != null){
			if(!sqldb.isConnected()){
				if(!sqldb.connect()){
					return false;
				}
			}
				
			sqldb.createTable("CREATE TABLE IF NOT EXISTS " + pluginConf.getString("mysql.table") +
					"(PUUID varchar(36) NOT NULL," +
					"NAME varchar(16) NOT NULL,"+
					"SERVER varchar(32) NOT NULL,"+
					"SGROUP varchar(32) NOT NULL,"+
					"OP boolean NOT NULL,"+
					"LOGGEDIN boolean NOT NULL,"+
					"VANISH boolean NOT NULL,"+
					"CREATIVE boolean NOT NULL,"+
					"SOCIALSPY boolean NOT NULL,"+
					"TIMELOGGED int NOT NULL,"+
					"TIMEVANISH int NOT NULL,"+
					"TIMECREATIVE int NOT NULL,"+
					"TIMESOCIALSPY int NOT NULL,"+
					"PRIMARY KEY (NAME));");
			return true;
		}
		
		return false;
	}
	
	/**Internal function to initialize the SQL class.
	 * 
	 *  @return boolean - true if successful, otherwise false.
	 * 
	 * */
	private boolean initSQL(){
		//Well, this could be an initial run, or we could be called a second time on certain
		//Bukkit servers, so lets see where we are.
		if(debug) this.getLogger().info("DEBUG : initSQL()");
		if(sqldb == null){
			if(debug) this.getLogger().info("DEBUG : initSQL() - sqldb == null");
			//Run some sanity checks before we go flinging data into the DB :|
			if(	pluginConf.getString("mysql.driver") == "" || 
				pluginConf.getString("mysql.db") == "" ||
				pluginConf.getString("mysql.user") == "" || 
				pluginConf.getString("mysql.ip") == "" ||
				pluginConf.getString("mysql.port") == ""){
				//Password CAN be "" ... this would be stupid and/or insane, but still valid.
				Log.severe("Invalid configuration details for SQL access - Empty strings instead of required data!");
				return false;
			}
			
			//Attempt to initialize the SQL Database...
			sqldb = new SQLDB(pluginConf.getString("mysql.ip"), 
								pluginConf.getString("mysql.port"), 
								pluginConf.getString("mysql.user"), 
								pluginConf.getString("mysql.pass"), 
								pluginConf.getString("mysql.driver"),
								pluginConf.getString("mysql.db"));
			
			if(sqldb.connect()){
				Log.info("SQL DB Connectivity Established!");
				if(this.initSQLTables()){
					return true;
				} else {
					Log.severe("Failed to initialize table! :(");
					return false;
				}
			} else {
				if(debug) this.getLogger().info("DEBUG : initSQL() before handleSQLConnectionFailure()");
				return handleSQLConnectionFailure();
			}
	
		} else {
			//Technically, we could reach here if the server supports reloading plugins.
			//Lets see if we've got a valid configuration/connection.
			if(sqldb.isConnected()){
				Log.info("Previous SQL Connection is still valid!");
				return true;
			} else {
				Log.info("Attempting to re-establish connection to Database...");
				if(sqldb.connect()){
					Log.info("SQL DB Connectivity Re-established!");
					return true;
				} else
					return handleSQLConnectionFailure();
			}
		}
	}
	
	
	private boolean initHooks(){
		if(debug) this.getLogger().info("DEBUG : initHooks()");
		boolean tryEssentialsPerms = true;
		
		if(Bukkit.getPluginManager().getPlugin("Vault") != null){
			//We've got vault - WOOT
			RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
			if (permissionProvider != null) {
				permission = permissionProvider.getProvider();
				this.getLogger().info("Using Vault permissions provider!");
				tryEssentialsPerms = false;
			} else {
				if(debug) this.getLogger().info("DEBUG : Couldn't find vault permissions provider - will try using essentials!");

			}
		} else {
			if(debug) this.getLogger().info("DEBUG : Vault plugin is not installed.");
		}
		
		//Check and see if we're using essentials
		if(pluginConf.getBoolean("plugin.useessentials")){
			
			this.essentialsPlugin = Bukkit.getPluginManager().getPlugin("Essentials");
			if(this.essentialsPlugin == null){
				Log.severe("Tried to use Essentials - couldn't get the Plugin!");
				return false;
			}

			if (essentialsPlugin.isEnabled() && (essentialsPlugin instanceof Essentials)) {
				this.essentials = (Essentials) essentialsPlugin;
				Log.info("Using Essentials!");
				if(tryEssentialsPerms){
					this.essPerms = essentials.getPermissionsHandler();
					if(pluginConf.getBoolean("stafftracker.staffGroups") && this.essPerms == null){
						Log.severe("Fallback to Essentials Permissions failed : Disabling plugin as it cannot operate as configured!");
						Log.severe("Either DISABLE group permissions config, or INSTALL vault or essentials!");
						return false;	
					}
				}
			} else {
				// Disable the plugin

				if(!this.essentialsPlugin.isEnabled()){
					Log.severe("Could not use Essentials : Plugin is not enabled!");
					return false;

				} else {
					Log.severe("Could not use Essentials : Plugin is not an instance of Essentials?!");
					return false;
				}
			}
		} else if(tryEssentialsPerms) {
			if(pluginConf.getBoolean("stafftracker.staffGroups"))
			Log.severe("Fallback to Essentials Permissions failed : Disabling plugin as it cannot operate as configured!");
			Log.severe("Either DISABLE group permissions config, or install vault or essentials!");
			return false;
			
		}
		
		//Check and see if we're using Vanish No Packet
		if(pluginConf.getBoolean("plugin.usevanishnopacket")){
			this.vanishPlugin = Bukkit.getPluginManager().getPlugin("VanishNoPacket");
			if(this.vanishPlugin == null){
				Log.severe("Tried to use VanishNoPacket - couldn't get the Plugin!");
				return false;
			}

			if (vanishPlugin.isEnabled() && (vanishPlugin instanceof VanishPlugin)) {
				this.vanish = (VanishPlugin) vanishPlugin;
				this.vanishMan = vanish.getManager();
				Log.info("Using VanishNoPacket!");
			} else {
				// Disable the plugin

				if(!this.vanishPlugin.isEnabled()){
					Log.severe("Could not use VanishNoPacket : Plugin is not enabled!");
					return false;

				} else {
					Log.severe("Could not use VanishNoPacket : Plugin is not an instance of VanishPlugin?!");
					return false;
				}
			}
		}
		
		return true;
	}


	/**Internal function for initializing the configuration files, including version checking. */
	private void initConfig(){
		if(debug) this.getLogger().info("DEBUG : initConfig()");
		//This is just to initialize the path, and default config, IF NEEDS BE!
		//createConfigPath does nothing if the files already exist!
		File configPath = getDataFolder();
		File configFile = new File(configPath, "config.yml");
		
		//Read our internal config file to a string.
		String conf = StaffTracker.getStringFromStream(this.getResource("config.yml"));

		// We use this static helper to ensure that the path and file both exist.
		// If they don't, we pull a config from inside the jar, and save it as the new config.
		ConfigMan.createConfigPath(configFile, configPath,conf,this);
		
		//Set up the plugin's ConfigManager config file.
		this.myConfFile = new ConfigMan(configFile,getLogger());
		
		//Attempt to read in the config on disk.
		if(myConfFile.loadConfig()){
			if(myConfFile.getConfig().getInt("configver") == this.CONF_VER){
				//If we're using the proper config ver, make this the official config!
				pluginConf = myConfFile.getConfig();
			} else {
				this.replaceConfig();
			}
			
		} else {
			getLogger().warning("Warning : Failed to load config? This is almost certainly very, very bad.");
		}
		
		
		
	}
	
	/**Handles a request from the Bukkit server to enable the plugin. */
	@Override
	public void onEnable() {
		if(debug) this.getLogger().info("DEBUG : onEnable()");
		//Setup our instance var
		instance = this;
		
		//Setup the log
		Log = getLogger();
		
		if(debug) this.getLogger().info("DEBUG : scraping server statistics (server 'name' and MC version)");
		//Stash the server!
		server = getServer();
		String p = new File(".").getAbsoluteFile().getParentFile().getName();
		Log.info("P = " + p);
		
		if(server.getServerName().equals("Unknown Server")){
			if(debug) this.getLogger().info("DEBUG : Server operator forgot to set a name in server.properties - using server directory name");
			MCServerName = p;
		} else {
			MCServerName = server.getServerName();
		}
		
		
		if(debug) this.getLogger().info("DEBUG : getting server MC version.");
		//Stash and store the current MC version of the server.
		String Ver = server.getVersion();
		if(Ver.endsWith("(MC: 1.6.4)")){
			this.serverVersion = MCVer.ONE_SIX_FOUR;
		} else if(Ver.endsWith("(MC: 1.7.10)")){
			this.serverVersion = MCVer.ONE_SEVEN_TEN;
		} else if(Ver.endsWith("(MC: 1.8)")){
			this.serverVersion = MCVer.ONE_EIGHT;
		} else if(Ver.endsWith("(MC: 1.8.1)")){
			this.serverVersion = MCVer.ONE_EIGHT_ONE;
		}else if(Ver.endsWith("(MC: 1.8.2)")){
			this.serverVersion = MCVer.ONE_EIGHT_TWO;
		}else if(Ver.endsWith("(MC: 1.8.3)")){
			this.serverVersion = MCVer.ONE_EIGHT_THREE;
		}else if(Ver.endsWith("(MC: 1.8.4)")){
			this.serverVersion = MCVer.ONE_EIGHT_FOUR;
		}
		
		switch(this.serverVersion){
		case ONE_SIX_FOUR :
			getLogger().info("Server version detected as 1.6.4");
			break;
		case ONE_SEVEN_TEN :
			getLogger().info("Server version detected as 1.7.10");
			break;
		case ONE_EIGHT :
			getLogger().info("Server version detected as 1.8");
			break;
		case ONE_EIGHT_ONE :
			getLogger().info("Server version detected as 1.8.1");
			break;
		case ONE_EIGHT_TWO :
			getLogger().info("Server version detected as 1.8.2");
			break;
		case ONE_EIGHT_THREE :
			getLogger().info("Server version detected as 1.8.3");
			break;
		case ONE_EIGHT_FOUR :
			getLogger().info("Server version detected as 1.8.4");
			break;
		case UNKNOWN :
			getLogger().info("Server version failed detection.");
			break;
			
		}
		
		this.initConfig();
		
		//Will you override the developer's wishes, and turn on Debug mode?
		this.debug = pluginConf.getBoolean("plugin.debug");
		
		//Only need to do a lot of this stuff if the plugin is configured to be enabled.	
		if(pluginConf.getBoolean("plugin.enabled")){
			if(debug) this.getLogger().info("DEBUG : plugin.enabled == true");
			//Let the rest of the plugin know we are in fact enabled.
			this.enabled = true;

			if(!initHooks()){
				if(debug) this.getLogger().info("DEBUG : failed to initHooks()");
				this.enabled = false;
				return;
			}
			
			
			//Will this plugin use SQL related services?
			if(pluginConf.getBoolean("plugin.usesql")){
				if(debug) this.getLogger().info("DEBUG : configured to use SQL.");
				//Try to bring SQL online, then.
				if(initSQL()){
					//Success!
					Log.info("SQL initialized successfully!");
					useSQL = true;
				} else {
					//This plugin is configured to use SQL, however, we can't.
					//Disable the plugin
					Log.severe("Failed to initialize SQL - Please check server log for errors!");
					Log.info("StaffTracker is DISABLED due to error condition in SQL!");
					this.enabled = false;
					return;
				}
			} else {
				if(debug) this.getLogger().info("DEBUG : not configured to use SQL");
				useSQL = false;
			}
						
			
		} else {
			this.enabled = false;
			getLogger().info("StaffTracker disabled via config!");
			return;
		}
		
		//Initialize the staff index - if its not null, we've been here before!
		if(staffInd == null)
			staffInd = new Hashtable<String, StaffRecord>();
		
		if(debug) this.getLogger().info("DEBUG : essentials and staffgroup sanity check");
		
		if(staffGroups == null && pluginConf.getBoolean("stafftracker.staffGroup") == true){
			//since we need Essentials to do group checking, lets just make sure essentials
			//was *actually* enabled in the config, and we have a valid link to it
			if(this.essPerms != null || this.permission != null){
				staffGroups = pluginConf.getStringList("stafftracker.staffGroups");
			} else {
				//Honestly, we should have caught this earlier than here, but...better safe than
				//NPE, yeah? ^_^
				this.enabled = false;
				Log.severe("Configued to use permissions groups, but couldn't get a permissions provider (Essentials or vault)! ABORTING!");
				return;
			}
		}
		
		if(debug) this.getLogger().info("DEBUG : register events");
		//Setup our events
		server.getPluginManager().registerEvents(new EventListener(), this);
		
		//We set up our timer to run AFTER the first player logs in, so look for that
		//bit of initialization in playerJoin()
		
		getLogger().info("StaffTracker enabled successfully!");
	}
 
	/** Handles a request from the Bukkit server to disable the plugin - Server is generally
	 * shutting down, so clean up everything here! */
	@Override
	public void onDisable() {
		if(debug) this.getLogger().info("DEBUG : onDisable()");
		//Clean up database related stuff, if we're using it.
		if(this.useSQL)
			if(this.sqldb != null)
				this.sqldb.close();
		
		this.enabled = false;
		getLogger().info("StaffTracker disabled successfully!");
	}
		
	/** Displays the help command for this plugin */
	private void commandHelp(CommandSender sender, String label){
		sender.sendMessage("StaffTracker Help :");
		sender.sendMessage("~~~~~~~~~~~~~~~~~~~");
		sender.sendMessage(label + " help - this screen");
		if(debug) sender.sendMessage(label + " dumprec <playername> - Displays internal staff recordset, or an entry for player <playername>, if one exists.");
		if(debug) sender.sendMessage(label + " updateall - Forces internal staff recordset to be updated.");

	}
	
	/** Command to dump an internal StaffRecord - Only works if debug == true! */
	private void commandDumpRec(CommandSender sender, String[] args){
		if(!debug) return;
		
		if(args.length == 2){
			if(staffInd.containsKey(args[1])){
				sender.sendMessage(staffInd.get(args[1]).toString());
			} else {
				sender.sendMessage("Player is not logged into this server, or isn't detected as staff!");
			}
			
		} else {
			Enumeration<StaffRecord> staffe = staffInd.elements();
			while(staffe.hasMoreElements()){
				StaffRecord r = staffe.nextElement();
				
				sender.sendMessage("\n" + r.toString() + "\n");
			}
		}	
	}
	
	public void commandForceUpdateAllRecords(CommandSender sender){
		if(debug){
			Log.info(sender.getName() + " Forced internal recordset update.");
			this.updateAllRecords();
			sender.sendMessage("All records updated!");
		}
		
	}
	
	/** Notification from Bukkit server that a command has been entered.*/
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(debug) this.getLogger().info("DEBUG : onCommand");
		//TODO Set up plugin commands.
		
		if (cmd.getName().equalsIgnoreCase("st")) {
			if (getServer().getConsoleSender() == sender || sender instanceof Player) {
				if(args.length == 0){
					commandHelp(sender, label);
				} else {
					switch(args[0]){
					case "dumprec":
						if(debug) commandDumpRec(sender,args);
						else sender.sendMessage("Command requires the plugin be in Debug mode!");
						break;
					case "updateall":
						if(debug) commandForceUpdateAllRecords(sender);
						else sender.sendMessage("Command requires the plugin be in Debug mode!");
						break;
					default:
						commandHelp(sender, label);
						break;
						
					}
				}

			} else {
				Log.warning("Sender is not the server console, or a player? @_@ Are the creepers sending commands now?");
				return false;
			}
			return true;
		}
		return false;
	}
	
	/** Notification from the eventlistener that a player has joined.
	 * 
	 * @param Player p - the player that has joined
	 * */
	public void playerJoin(Player p){
		if(debug) this.getLogger().info("DEBUG : playerJoin()");
		if(this.firstLogin){
			firstLogin = false;
			//our updateInterval is in seconds - multiply by 20 to get the number of ticks we
			//need to make it kinda make sense.
			long interval = pluginConf.getLong("stafftracker.updateInterval") * 20;
			Log.info("Starting update timer!");
			
			//start our timer!
			@SuppressWarnings("unused")
			BukkitTask updateTimer = new ScheduledTask(this).runTaskTimer(this, interval, interval);
		}
		
		//Do we already have this player in the index?
		if(staffInd.containsKey(p.getName())){
			
			StaffRecord rec = staffInd.get(p.getName());
			
			if(rec.getLoggedIn()){
				Log.warning("Staff member " + p.getName() + "just logged in...and the player is marked already as logged in. We've missed the exit event, somehow.");
				//TODO Decide what to do in this case :(
			} else {
				rec.setLoggedIn(true);
				boolean isVanished = false;
				//get the vanish state.
				if(pluginConf.getBoolean("plugin.usevanishnopacket")){
					isVanished = this.vanishMan.isVanished(p);
				}
				if(pluginConf.getBoolean("plugin.useessentials")){
					List<String> vp = this.essentials.getVanishedPlayers();
					if(vp.contains(p.getName())){
						isVanished = true;
					}
				}
				
				rec.setCreative(p.getGameMode() == GameMode.CREATIVE);
				rec.setVanish(isVanished);
				rec.setSocialSpy(essentials.getUser(p).isSocialSpyEnabled());

				staffInd.put(p.getName(), rec);
			}
		} else {

			if(!isStaff(p)){
				if(pluginConf.getBoolean("extra.staffOnlySocialSpy")){
					if(pluginConf.getBoolean("plugin.useessentials")){
						if(essentials.getUser(p).isSocialSpyEnabled() == true){
							essentials.getUser(p).setSocialSpyEnabled(false);
							Log.info("Set player " + p.getName() + " SocialSpy to OFF - Player is not identified as a STAFF MEMBER.");
							p.sendMessage("[StaffTracker] Enforced Staff-only SocialSpy usage. Your SocialSpy has been turned off.");
							p.sendMessage("[StaffTracker] If you are currently a staff member, inform an Administrator - he needs to update the config!");
						}
					}
				}
				return;			
			}

			boolean isVanished = false;
			
			//get the vanish state.
			if(pluginConf.getBoolean("plugin.usevanishnopacket")){
				isVanished = this.vanishMan.isVanished(p);
			}
			if(pluginConf.getBoolean("plugin.useessentials")){
				List<String> vp = this.essentials.getVanishedPlayers();
				if(vp.contains(p.getName())){
					isVanished = true;
				}
			}
			
			//If we're using staff Groups, it'd be nice to add which group they're in
			//to the staff record....
			String sgroup = "";
			if(pluginConf.getBoolean("stafftracker.staffGroups")){
				List<String> pgroups = essentials.getPermissionsHandler().getGroups(p);
				

				Iterator<String> groupItr = pgroups.iterator();
				while(groupItr.hasNext())  {
					sgroup = groupItr.next();

					if(this.staffGroups.contains(sgroup)){
						break;
					}

					sgroup = "";

				}
			}
			
			//insert our group. (if any)
			if(sgroup == "")
				sgroup = "Staff";				
			
			//We now have enough information to build our index entry.
			StaffRecord r = new StaffRecord(p.getUniqueId(), p.getName(), MCServerName, sgroup, isVanished, p.getGameMode() == GameMode.CREATIVE, essentials.getUser(p).isSocialSpyEnabled());
			
			r.setOp(p.isOp());			
				
			staffInd.put(p.getName(), r);
			if(debug) Log.info("DEBUG : Added staff member : " + r.getName() + "(" + r.getUUID().toString() + ")");
		}

	}

	/** Notification from the eventlistener that a player has quit.
	 * 
	 * @param Player p - the player that quit
	 * */
	public void playerQuit(Player p){
		if(debug) this.getLogger().info("DEBUG : playerQuit()");
		
		if(staffInd.containsKey(p.getName())){
			StaffRecord r = staffInd.get(p.getName());
			r.setLoggedIn(false);
			r.setCreative(false);
			r.setSocialSpy(false);
			r.setVanish(false);
			r.updateTime();
			r.setCommitting(true);
			
		}
	}

	/** On a timer, we update all the records we have - this is the function that gets
	 *  called from our scheduled task. 
	 *  
	 */
	public void updateAllRecords(){
		if(debug) this.getLogger().info("DEBUG : updateAllRecords()");
		Enumeration<StaffRecord> staffe = staffInd.elements();
		while(staffe.hasMoreElements()){
			StaffRecord r = staffe.nextElement();
			this.updateRecord(server.getPlayer(r.getName()), r);
		}
		
	}

	///////////////////////////////////////////////////////////////////////////////////
	// HELPER FUNCTIONS
	///////////////////////////////////////////////////////////////////////////////////


	public boolean pushRecordToDB(StaffRecord r){
		//Let the rest of everything know - WE ARE COMMITTING TO DB!
		r.setCommitting(true);
		staffInd.put(r.getName(), r);
		
		java.sql.PreparedStatement update;
		try {
			update = sqldb.getConnection().prepareStatement(
			        "INSERT INTO tableName(colA, colB) VALUES (?, ?)");
			update.setInt(1, 75); 
			update.setString(2, "Colombian"); 
			update.executeUpdate();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
		
		
		return true;
	}
	
	public boolean isStaff(Player p){
		//do we need to add this player to the index?

		if(pluginConf.getBoolean("stafftracker.staffOp")){
			if(p.isOp())
				return true;
			
		}
		if(pluginConf.getBoolean("stafftracker.staffPerm")){
			if(p.isPermissionSet(pluginConf.getString("stafftracker.staffPerms")))
				return true;
		}
		if(pluginConf.getBoolean("stafftracker.staffGroup")){
			try{
				List<String> pgroups = essentials.getPermissionsHandler().getGroups(p);
				String sgroup = "";

				Iterator<String> groupItr = pgroups.iterator();
				while(groupItr.hasNext())  {
					sgroup = groupItr.next();

					if(this.staffGroups.contains(sgroup)){
						return true;
					}

				}
			} catch(Exception e){
				//Well, we're probably not using a proper version of 
				//essentials, if we're here. :(
				Log.severe("Caught exception : " + e.getMessage());
			}
		}
		
		return false;
	}
	
	/** Helper function to update a staffmember record entry */
	private void updateRecord(Player p, StaffRecord r){
		if(debug) this.getLogger().info("DEBUG : updateRecord()");
		if(r.getCommitting()){
			if(debug) this.getLogger().info("DEBUG : This record for " + r.getName() + " is being commited to the DB. No need to update it!");
			return;
		}
		
		boolean isVanished = false;

		//get the current vanish state from VNP....
		if(pluginConf.getBoolean("plugin.usevanishnopacket")){
			if(debug) this.getLogger().info("DEBUG : Checking VNP for if staff member is vanished.");
			isVanished = this.vanishMan.isVanished(p);

		} else {
			if(debug) this.getLogger().info("DEBUG : Not configured to use VNP.");
		}

		//...and from essentials (/evanish)
		if(pluginConf.getBoolean("plugin.useessentials")){
			//if we're already vanished, don't worry about re-confirming!
			if(isVanished == false){
				if(debug) this.getLogger().info("DEBUG : checking essentials for vanished players...");
				List<String> vp = this.essentials.getVanishedPlayers();
				for(String n : vp){
					if(debug) this.getLogger().info(n + "\n");
				}
				if(vp.contains(p.getName())){
					isVanished = true;
					if(debug) this.getLogger().info("DEBUG : Found player - he/she is vanished.");
				}
			}
		} else {
			if(debug) this.getLogger().info("DEBUG : Not configured to use essentials.");
		}

		if(debug) this.getLogger().info("DEBUG : isVanish = " + (isVanished ? "True" : "False"));


		//update record if need be for creative.
		r.setCreative(p.getGameMode() == GameMode.CREATIVE);

		//update record if need be for vanish.
		r.setVanish(isVanished);

		//update record if need be for socialspy
		r.setSocialSpy(essentials.getUser(p).isSocialSpyEnabled());

		r.setLoggedIn(true);

		//finally, update the record. otherwise, what's the point, right?
		staffInd.put(p.getName(), r);
	}

	/** Getter for the class instance
	 * 
	 * @return StaffTracker - The current instance of this particular class.
	 */
	public static StaffTracker getInstance() {
		return instance;
	}

	/**Helper function to return a string from an InputStream.
	 * 
	 *  @param InputStream in - Input stream to convert to String
	 *  @return String - the contents of the input stream, as a String
	 *  @author MrWisski
	 * */
	static String getStringFromStream(InputStream in){
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		StringBuilder out = new StringBuilder();
		String line;
		try {
			while ((line = reader.readLine()) != null) {

				out.append(line + "\n");
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return out.toString();

	}

	/** Helper function to make code more readable - Handles configurable SQL Connection Failures
	 * @return boolean - true - try later, false - fail and disable plugin. 
	 * */
	private boolean handleSQLConnectionFailure(){
		if(debug) this.getLogger().info("DEBUG : handleSQLConnectionFailure()");
		// Make sure we have a valid config setting for SQL Failures.
		// If we don't, use the internal setting.
		String ONC = pluginConf.getString("mysql.onNoConnect");
		if(ONC != "retry" && ONC != "fail")
			ONC = this.SQL_RETRY_DEFAULT;

		// How are we configured to deal with connection failures?
		if( ONC == "retry"){
			//We retry! excellent!
			Log.warning("SQL DB Connection Attempt failed - Will retry later.");
			return true;

		} else {
			//We fail! excellent!
			Log.severe("SQL DB Connection Attempt failed - Configured for failure. Aborting!");
			return false;

		} 
	}

	/** Helper function for handling config version mismatches. */
	private void replaceConfig(){
		if(debug) this.getLogger().info("DEBUG : replaceConfig()");
		getLogger().severe("**********************************************");
		getLogger().severe("Configuration file version mismatch detected!"  );
		getLogger().severe("Backing up current config, and replacing!" ); 
		getLogger().severe("Plugin will be disabled! Review plugin config");
		getLogger().severe("and re-enable!");
		getLogger().severe("**********************************************");
		File configPath = getDataFolder();
		File configFile = new File(configPath, "config.yml");
		File configBackup = new File(configPath, "config.old");

		//Read our internal config file to a string.
		String conf = StaffTracker.getStringFromStream(this.getResource("config.yml"));

		try {
			Files.copy(configFile, configBackup);
		} catch (IOException e) {
			e.printStackTrace();
		}

		configFile.delete();

		// We use this static helper to ensure that the path and file both exist.
		// If they don't, we pull a config from inside the jar, and save it as the new config.
		ConfigMan.createConfigPath(configFile, configPath,conf,this);

		//Set up the plugin's ConfigManager config file.
		this.myConfFile = new ConfigMan(configFile,getLogger());

		//Attempt to read in the config on disk.
		if(myConfFile.loadConfig()){
			if(myConfFile.getConfig().getInt("configver") == this.CONF_VER){
				//If we're using the proper config ver, make this the official config!
				pluginConf = myConfFile.getConfig();
			} else {
				Log.warning("Developer Sillyness detected : Please inform the plugin dev he forgot to update the CONFIG VERSION IN HIS SOURCE!");
				pluginConf = myConfFile.getConfig();
			}

		}

	}
}
