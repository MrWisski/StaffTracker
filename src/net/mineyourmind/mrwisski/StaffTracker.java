package net.mineyourmind.mrwisski;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import net.milkbowl.vault.permission.Permission;
import net.mineyourmind.mrwisski.StaffRecord.ghostReason;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
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

/** StaffTracker - A configurable Bukkit plugin to keep track of server Staff members, network wide.
 * 
 *  @author MrWisski
 *  @version 0.0.7a 14 May 2015
 *   
 * */
public final class StaffTracker extends JavaPlugin {
	
	public static enum MCVer {	UNKNOWN, 
								ONE_SIX_FOUR, 
								ONE_SEVEN_TEN, 
								ONE_EIGHT, ONE_EIGHT_ONE, ONE_EIGHT_TWO, ONE_EIGHT_THREE, ONE_EIGHT_FOUR}

	private final String SQL_RETRY_DEFAULT = "retry";
	private final int CONF_VER = 6;
	
	//Are we printing debugging/troubleshooting messages to the log?
	//This is set from the config, but influences messages BEFORE the config is read.
	boolean debug = false;
	
	private List<StaffRecord> dbPushes = new ArrayList<StaffRecord>();
	private List<AsynchPush> pendingAsynchs = new ArrayList<AsynchPush>();
	
	//If we're using essentials
	Plugin essentialsPlugin;
	Essentials essentials;
	//If we're using VanishNoPacket
	Plugin vanishPlugin;
	VanishPlugin vanish;
	VanishManager vanishMan;
	
	public static boolean dbPushActive = false;

	// TODO : Decide if we REALLY need vault, I think we can squeeze by with just
	// Essentials. The options ARE nice though.
	Permission permission = null;
	PermissionsHandler essPerms = null;
	
	//Flag to let us know that the server IS accepting connections, and its safe to schedule
	//our timer
	boolean firstLogin = true;
	
	//a BukkitTask for our update-all-records task.
	public BukkitTask updateTimer = null;
	
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
	
	//Storage array for keeping track of staff members
	public Hashtable<String,StaffRecord> staffInd = new Hashtable<String,StaffRecord>();
	public Hashtable<String,String> commandInd = new Hashtable<String,String>();
	
	//Cache for Staff groups, read in from config
	private List<String> staffGroups;
	//TODO : same thing, but with various perms, for the stafftracker.staffPerms list
	
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
					"PGROUP varchar(32) NOT NULL,"+
					"OP boolean NOT NULL,"+
					"LOGGEDIN boolean NOT NULL,"+
					"VANISH boolean NOT NULL,"+
					"CREATIVE boolean NOT NULL,"+
					"SOCIALSPY boolean NOT NULL,"+
					"TIMELOGGED int NOT NULL,"+
					"TIMEVANISH int NOT NULL,"+
					"TIMECREATIVE int NOT NULL,"+
					"TIMESOCIALSPY int NOT NULL,"+
					"COMMANDCSV text NULL, " +
					"REMOVE boolean NULL," +
					"PRIMARY KEY (NAME));");
			
//			CREATE TABLE IF NOT EXISTS stafftracker (PUUID varchar(36) NOT NULL, NAME varchar(16) NOT NULL, SERVER varchar(32) NOT NULL, PGROUP varchar(32) NOT NULL, OP boolean NOT NULL, LOGGEDIN boolean NOT NULL, VANISH boolean NOT NULL, CREATIVE boolean NOT NULL, SOCIALSPY boolean NOT NULL, TIMELOGGED int NOT NULL, TIMEVANISH int NOT NULL, TIMECREATIVE int NOT NULL, TIMESOCIALSPY int NOT NULL, COMMANDCSV text, PRIMARY KEY (NAME));
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
		if(debug) this.getLogger().info("DEBUG : initSQL()");
		//Well, this could be an initial run, or we could be called a second time on certain
		//Bukkit servers, so lets see where we are.
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
			
			//get the bukkit plugin for essentials
			this.essentialsPlugin = Bukkit.getPluginManager().getPlugin("Essentials");
			if(this.essentialsPlugin == null){
				Log.severe("Tried to use Essentials - couldn't get the Plugin!");
				return false;
			}

			//Is it enabled? is it an instance of the thing we thing its an instance of?
			if (essentialsPlugin.isEnabled() && (essentialsPlugin instanceof Essentials)) {
				this.essentials = (Essentials) essentialsPlugin;
				Log.info("Using Essentials!");
				if(tryEssentialsPerms){
					this.essPerms = essentials.getPermissionsHandler();
					if(pluginConf.getBoolean("stafftracker.staffGroup") && this.essPerms == null){
						Log.severe("Fallback to Essentials Permissions failed : Disabling plugin as it cannot operate as configured!");
						Log.severe("Either DISABLE group permissions config, or INSTALL vault or essentials!");
						return false;	
					} else {
						Log.info("Could not get the essentials permission provider. This might be problematic later.");
					}
				}
			} else {
				// Disable the plugin, but give the end user a nice reason.

				if(!this.essentialsPlugin.isEnabled()){
					Log.severe("Could not use Essentials : Plugin is not enabled!");
					return false;

				} else {
					Log.severe("Could not use Essentials : Plugin is not an instance of Essentials?!");
					return false;
				}
			}
		} else if(tryEssentialsPerms) {
			if(pluginConf.getBoolean("stafftracker.staffGroup")){
				Log.severe("Fallback to Essentials Permissions failed : Disabling plugin as it cannot operate as configured!");
				Log.severe("Either DISABLE group permissions config, or install vault or essentials!");
				return false;
			}
		}
		
		//Check and see if we're using Vanish No Packet
		if(pluginConf.getBoolean("plugin.usevanishnopacket")){
			//get the bukkit plugin for VNP
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
				// Disable the plugin, but give a nice reason why

				if(!this.vanishPlugin.isEnabled()){
					Log.severe("Could not use VanishNoPacket : Plugin is not enabled!");
					return false;

				} else {
					Log.severe("Could not use VanishNoPacket : VNP Plugin we got from bukkit is not an instance of VanishNoPacket? Check your VNP version!");
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
			//TODO : Do we want to abort loading here, or can we wing it? I've yet to see this
			// come up in testing so it may never even come up.
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
		
		//This used to be later, but honestly, the SQL needs it for initialization, so...
		if(debug) this.getLogger().info("DEBUG : scraping server statistics (server 'name' and MC version)");
		
		//Stash the server!
		server = getServer();
		String p = new File(".").getAbsoluteFile().getParentFile().getName();
		if(debug) Log.info("DEBUG : fallback server name = " + p);
		
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
		if(!pluginConf.getBoolean("plugin.enabled")){
			this.enabled = false;
			getLogger().info("StaffTracker disabled via config!");
			return;
		}
		
		if(debug) this.getLogger().info("DEBUG : plugin.enabled == true");
		//Let the rest of the plugin know we are in fact enabled.
		this.enabled = true;

		if(!initHooks()){
			//initHooks prints to the error log, so even this debug print is
			//maybe unneeded.
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
					
		
		
		//Initialize the staff index - if its not null, we've been here before!
		if(staffInd == null)
			staffInd = new Hashtable<String, StaffRecord>();
		
		//Initialize the command index
		ConfigurationSection confsec = pluginConf.getConfigurationSection("tracks");
		int count = 0;
		if(confsec != null){
			for(String ss : confsec.getKeys(true)){
				this.commandInd.put(ss, confsec.getString(ss));
				count++;
				//Log.info("Key : " + ss + " -- value : " + confsec.getString(ss));
			}
			if(count > 0)
				this.getLogger().info("Tracking usage statistics for " + count + " command" + (count == 1 ? ".":"s."));
			else
				this.getLogger().info("Command tracking disabled : No commands defined!");
			
		} else {
			this.getLogger().info("Command tracking disabled : No commands defined!");
		}
		
		if(debug) this.getLogger().info("DEBUG : essentials and staffgroup sanity check");
		
		if(pluginConf.getBoolean("stafftracker.staffGroup") == true){
			//since we need Essentials to do group checking, lets just make sure essentials
			//was *actually* enabled in the config, and we have a valid link to it
			//ADDED : vault's permissions - we can use either one.
			//TODO : Replace with a perms gateway class to handle this mess.
			if(this.essPerms != null || this.permission != null){
				this.staffGroups = pluginConf.getStringList("stafftracker.staffGroups");
				String g = "";
				for(String s : staffGroups){
					g += "'" + s + "', ";
				}
				if(debug) Log.info("Got " + staffGroups.size() + " permission group entries : " + g);
			} else {
				//Honestly, we should have caught this earlier than here, but...better safe than
				//NPE, yeah? ^_^
				this.enabled = false;
				Log.severe("Configured to use permissions groups, but couldn't get a permissions provider (Essentials or vault)! ABORTING!");
				return;
			}
		}
		
		//We need to see if we exited gracefully last time we shut down.
		int retrycount = 0;
		
		while(this.recordsChecked == false){
			if(debug) this.getLogger().info("DEBUG : Checking for outdated records, try #" + retrycount+1);
			this.checkRecords();
			
			retrycount++;
			if(retrycount > 3){
				this.recordsChecked = true;
				this.enabled = false;
				if(debug) this.getLogger().info("DEBUG : Failed retry count on checkRecords() attempt. Disabling plugin :(");
			}
		}
		
		if(this.enabled){
			if(debug) this.getLogger().info("DEBUG : register events");
			//Setup our events
			server.getPluginManager().registerEvents(new EventListener(), this);

			//We set up our timer to run AFTER the first player logs in, so look for that
			//bit of initialization in playerJoin()

			getLogger().info("StaffTracker enabled successfully!");
		} else {
			getLogger().severe("Plugin is NOT ENABLED. This is likely due to a DB connection failure.");
		}
	}
 
	/** Handles a request from the Bukkit server to disable the plugin - Server is generally
	 * shutting down, so clean up everything here! */
	@Override
	public void onDisable() {
		if(debug) this.getLogger().info("DEBUG : onDisable()");
		//Make sure we stop our task.
		if(this.updateTimer != null)
			this.updateTimer.cancel();
		//Clean up database related stuff, if we're using it.
		if(this.useSQL)
			if(this.sqldb != null){
				//Update all the records, and save them to DB.
				this.pushAllRecords();
				//close out all our DB stuff.
				this.sqldb.close();
			}
		
		//Just to be nice.
		this.enabled = false;
		//Just so ya know...
		getLogger().info("StaffTracker disabled successfully!");
	}
		
	public boolean hasPerm(String name, String perm){
		if(debug) Log.info("hasPerm(" + name + ", " + perm + ")");
		
		Player p = this.getServer().getPlayer(name);
		if(p == null){
			Log.warning("Player '" + name + "' did not return a valid player!");
			return false;
		}
		
		if(this.permission != null){ // use vault!
			
			return this.permission.has(p, perm);
			
		} else if(pluginConf.getBoolean("plugin.useessentials") && this.essPerms != null){ // use essentials!
			return essPerms.hasPermission(p, perm);
		} else {
			return p.hasPermission(perm);
		}
		
		
	}
	
	/** Displays the help command for this plugin */
	private void commandHelp(CommandSender sender, String label){
		//TODONE : Colorize!
		sender.sendMessage("§2[Staff§aTracker§2] §2Staff§aTracker §2Help :");
		sender.sendMessage("§2[Staff§aTracker§2] §a~~~~~~~~~~~~~~~~~~~");
		sender.sendMessage("§2[Staff§aTracker§2] §2/" + label + " help §a- This screen!");
		if(sender.isOp()) sender.sendMessage("§2[Staff§aTracker§2] §2/" + label + " reload §a- Re-loads the configs from disk - some settings still require a restart! (Requires Op)");
		if(debug && sender.isOp()) sender.sendMessage("§2[Staff§aTracker§2] §c/" + label + " dumprec <Player Name> - Displays internal staff recordset, or an entry for player <playername>, if one exists.");
		if(debug && sender.isOp()) sender.sendMessage("§2[Staff§aTracker§2] §c/" + label + " updateall - Forces internal staff recordset to be updated. (Requires Op)");
		sender.sendMessage("§2[Staff§aTracker§2] §2/" + label + " list §a- Shows all online staff members.");
		if(getServer().getConsoleSender() == sender || (sender instanceof Player && this.hasPerm(sender.getName(), "stafftracker.viewdetails"))) 
			sender.sendMessage("§2[Staff§aTracker§2] §2/" + label + " show <Player Name> §a- Shows stats for a given staffmember.");
		//Disabled for now...
		//sender.sendMessage("§2[Staff§aTracker§2] §2/" + label + " remove <Player Name> §a- Removes a staff member from the DB.");
	}
	
	/** Command to dump an internal StaffRecord - Only works if debug == true! */
	private void commandDumpRec(CommandSender sender, String[] args){
		if(!debug) return;
		
		if(args.length == 2){
			if(staffInd.containsKey(args[1])){
				sender.sendMessage(staffInd.get(args[1]).toString());
			} else {
				sender.sendMessage("§2[Staff§aTracker§2] §cPlayer is not logged into this server, or isn't detected as staff!");
			}
			
		} else {
			Enumeration<StaffRecord> staffe = staffInd.elements();
			while(staffe.hasMoreElements()){
				StaffRecord r = staffe.nextElement();
				
				sender.sendMessage("§2[Staff§aTracker§2] \n" + r.toString() + "\n");
			}
		}	
	}
	
	/** Command to force an updateAllRecords() - Only works if the config is in Debug mode!*/
	public void commandForceUpdateAllRecords(CommandSender sender){
		if(debug){
			Log.info(sender.getName() + " Forced internal recordset update.");
			this.updateAllRecords();
			sender.sendMessage("§2[Staff§aTracker§2] All records updated!");
		}
		
	}
	/** List current staffmembers online */
	public void commandList(CommandSender sender){
		if(debug) Log.info("DEBUG : commandList()");
		if(getServer().getConsoleSender() == sender || (sender instanceof Player && this.isStaff((Player)sender))){
			if(debug) Log.info("== console, sender instanceof Player & isstaff()");
			if(!sqldb.isConnected()){
				if(!sqldb.connect()){
					sender.sendMessage("§2[Staff§aTracker§2] §cSorry, couldn't establish a connection to the database. Please try again in a little while!");
					return;
				}				
				
			} 
			
			ResultSet rs = null;
			Statement s = null;
			try {
				s = sqldb.getConnection().createStatement();
				rs = s.executeQuery("select * from " + pluginConf.getString("mysql.table") + " where LOGGEDIN = true");
				int numrec = 0;
				while(rs.next()){
					numrec++;
					sender.sendMessage("§2[Staff§aTracker§2] §2" + rs.getString("PGROUP") + " §f| §a" + rs.getString("NAME") + "§f | §9" + rs.getString("SERVER"));
				}
				
				if(numrec == 0){
					//This makes sense because we may have it configurable that non-staff can
					//access this command...
					sender.sendMessage("§2[Staff§aTracker§2] §cSorry, no staff are online right now!");
				} else {
					sender.sendMessage("§2[Staff§aTracker§2] §2Displayed §a" + numrec + "§2 records.");
				}
				rs.close();
				s.close();
			} catch (SQLException e) {
				e.printStackTrace();
				sender.sendMessage("§2[Staff§aTracker§2] §cSorry, there was an internal error when attempting the command - please check the server console or log file for details!");
			}
			
		} else {
			sender.sendMessage("§2[Staff§aTracker§2] §cSorry, currently, only staff members can use this command.");
		}
	}
	
	public void commandShow(CommandSender sender, String[] args){
		if(debug) Log.info("DEBUG : commandShow()");
		if(args.length == 1){
			sender.sendMessage("§2[Staff§aTracker§2] §cUsage is /st show <staff member name>.");
			return;
		}
		
		//Console or staff only!
		if(getServer().getConsoleSender() == sender || (sender instanceof Player && this.isStaff((Player)sender))){
			
			if(!sqldb.isConnected()){
				if(!sqldb.connect()){
					sender.sendMessage("§2[Staff§aTracker§2] §cSorry, couldn't establish a connection to the database. Please try again in a little while!");
					return;
				}				
				
			} 
			
			ResultSet rs = null;
			PreparedStatement query = null;
			try {
				if(this.recordExists(args[1])) {
					query = sqldb.getConnection().prepareStatement("SELECT * FROM " + pluginConf.getString("mysql.table") +
									" WHERE NAME = ?");
					
					query.setString(1, args[1]);
					rs = query.executeQuery();
					if(rs.next()){
						StaffRecord r = new StaffRecord(false, ghostReason.NA,rs.getString("PUUID"), rs.getString("NAME"), rs.getString("SERVER"), rs.getBoolean("OP"), rs.getString("PGROUP"), rs.getBoolean("LOGGEDIN"),rs.getLong("TIMELOGGED"), rs.getLong("TIMEVANISH"),	rs.getLong("TIMECREATIVE"),	rs.getLong("TIMESOCIALSPY"), rs.getString("COMMANDCSV"));
						r.setVanish(rs.getBoolean("VANISH"));
						r.setCreative(rs.getBoolean("CREATIVE"));
						r.setSocialSpy(rs.getBoolean("SOCIALSPY"));
						String res = r.toStringNice();
						sender.sendMessage(res);
						rs.close();
						query.close();
					}  else {
						if(debug) Log.info("DEBUG : commandShow(" + args[1] + ") - record for this player exists, recordset returned no results T_T");
						sender.sendMessage("§2[Staff§aTracker§2] §cSorry, but for some reason, the record for that staff member couldn't be pulled from the database.");
						rs.close();
						query.close();				
					}
				} else {
					sender.sendMessage("§2[Staff§aTracker§2] §cSorry, but no record for that staff member exists. Check your spelling, and try again!");
				}
			} catch (SQLException e) {
				e.printStackTrace();
				sender.sendMessage("§2[Staff§aTracker§2] §cSorry, but an internal error has occured. Please check the console, or the server log, for more details.");
			}
			
			
		} else {
			sender.sendMessage("§2[Staff§aTracker§2] §cSorry, currently, only staff members can use this command.");
		}
	}
	
	/** Removes a staff member from the DB, permanently. REQUIRES A CONFIRMATION COMMAND! */
	@SuppressWarnings("unused")
	private void commandRemove(CommandSender sender, String[] args){
		if(args.length != 2){
			sender.sendMessage("§2[Staff§aTracker§2] §cUsage is /st remove <staff member name>");
			return;
		}
		if(args[1].length() == 0){
			sender.sendMessage("§2[Staff§aTracker§2] §cUsage is /st remove <staff member name>");
			return;
		}
		if(!this.recordExists(args[1])){
			sender.sendMessage("§2[Staff§aTracker§2] §cCould not find a record for a staffmember named " + args[1] + ".");
			sender.sendMessage("§2[Staff§aTracker§2] §aCheck your spelling and try again!");
			return;
		}
		// generate a confirm string
		String conf = this.generateRandomNumbers(4);
		//put them into the confirm tracking
		this.removeConfirm.put(conf, args[1]);
		sender.sendMessage("§2[Staff§aTracker§2] §aPlease enter the command §e/st confirm " + conf +"§a to confirm deletion of this record.");
		sender.sendMessage("§2[Staff§aTracker§2] §cPlease be advised this is a §4PERMANENT§c operation, and cannot be undone.");
		sender.sendMessage("§2[Staff§aTracker§2] §cDeleted data cannot be recovered!");
	}
	
	/** for /st remove <name> confirmation. */
	private HashMap<String, String> removeConfirm = new HashMap<String, String>();
	
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
					case "debug":
						if(getServer().getConsoleSender() == sender){
							this.debug = !this.debug;
						} else if(sender instanceof Player && this.isStaff((Player)sender) && sender.isOp()){
							this.debug = !this.debug;
						}
						
						break;
					case "confirm":
						if(sender instanceof Player){
							if(!sender.hasPermission("stafftracker.remove")){
								sender.sendMessage("§2[Staff§aTracker§2] §cYou don't have the permissions required to do this!");
								return true;
							}
						}
						
						//If there isn't an arg[1] at all
						if(args.length != 2){
							sender.sendMessage("§2[Staff§aTracker§2] §cPlease enter your confirmation code!");
							return true;
						}
						//if there is (?), but its nothing
						if(args[1].length() == 0){
							sender.sendMessage("§2[Staff§aTracker§2] §cPlease enter your confirmation code!");
							return true;
						}
						//is this a valid confirm code?
						if(removeConfirm.containsKey(args[1])){
							//does it STILL point to a valid record?
							if(this.recordExists(removeConfirm.get(args[1]))){
								if(this.removeRecordFromDB(removeConfirm.get(args[1]))){
									sender.sendMessage("§2[Staff§aTracker§2] §aSuccessfully removed record for staff member " + removeConfirm.get(args[1]));
									removeConfirm.remove(args[1]);
									//print a message to the console/system log.
									Log.info("Player " + sender.getName() + " deleted record for staff member " + removeConfirm.get(args[1]));
									return true;
								} else {
									sender.sendMessage("§2[Staff§aTracker§2] §cSomething went wrong trying to remove that record - most likely a DB connection failure. Please try your confirmation code at a later time!");
								}
							} else {
								sender.sendMessage("§2[Staff§aTracker§2] §cSeems like you've been ninja'd - that record no longer exists! :o");
							}
						} else {
							sender.sendMessage("§2[Staff§aTracker§2] §cCouldn't find that confirmation code! Please check the sequence and try again!");
						}


						break;
					case "remove":
						sender.sendMessage("§2[Staff§aTracker§2] [StaffTracker] This command is COMING SOON!");
						return true;
						/*
						if(sender instanceof Player){
							if(sender.hasPermission("stafftracker.remove")){
								this.commandRemove(sender, args);
							} else {
								sender.sendMessage("§2[Staff§aTracker§2] §cYou don't have the permissions required to do this!");
							}
						} else {
							//console.
							this.commandRemove(sender, args);
						}
						break;*/
					case "dumprec":
						if(debug && sender.isOp()) commandDumpRec(sender,args);
						else sender.sendMessage("§2[Staff§aTracker§2] §cCommand requires the plugin be in Debug mode and you to be a server op!");
						break;
					case "updateall":
						if(debug && sender.isOp()) commandForceUpdateAllRecords(sender);
						else sender.sendMessage("§2[Staff§aTracker§2] §cCommand requires the plugin be in Debug mode and you to be a server op!");
						break;
					case "list":
						commandList(sender);
						break;
					case "show":
						if(getServer().getConsoleSender() == sender || (sender instanceof Player && this.hasPerm(sender.getName(), "stafftracker.viewdetails"))){
							commandShow(sender, args);
						} else {
							sender.sendMessage("§2[Staff§aTracker§2] §cYou don't have permission for that command!");
						}
						
						break;
					case "reload":
						if(sender.isOp()){
							this.myConfFile.loadConfig();
							pluginConf = myConfFile.getConfig();
							sender.sendMessage("§2[Staff§aTracker§2] §aConfigs reloaded!");
							Log.info(sender.getName() + " forced a configuration reload from disk!");
						} else {
							sender.sendMessage("§2[Staff§aTracker§2] §cYou have to be a server op to do this!");
						}
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
	
	public void onOtherCommand(String Command, Player p, ArrayList<String> Arguments){
		String clc = Command.toLowerCase();
		
		if(debug) Log.info("Checking on command " + clc);
		//Check to see if this is a command we're tracking, and a player (who is staff) using the command.
		if(	p != null && 
				this.staffInd.containsKey(p.getName()) &&
				this.commandInd.containsKey(clc)){
			//OK, it is a command we're tracking
			//Lets see if its in the state we're looking for.
			switch(commandInd.get(clc)){
			case "all" :
				if(debug) Log.info("All : " + clc);
				staffInd.get(p.getName()).incCommand(clc);
				break;
			case "self" :
				if(debug) Log.info("Self : " + clc);
				if(!Arguments.isEmpty() && Arguments.get(0).equalsIgnoreCase(p.getName())){
					staffInd.get(p.getName()).incCommand(clc);
					if(debug) Log.info("Is Self.");
				} else {
					if(debug) Log.info("Is Not Self.");
				}
				break;
			case "other" :
				if(debug) Log.info("Other : " + clc);
				
				if(!Arguments.isEmpty() && !Arguments.get(0).equalsIgnoreCase(p.getName())){
					staffInd.get(p.getName()).incCommand(clc);
					if(debug) Log.info("Is Other.");
				} else {
					if(debug) Log.info("Is Not Other.");
				}
				break;
			}				
		}
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
			this.updateTimer = new ScheduledTask(this).runTaskTimer(this, interval, interval);
			
		}

		//Do we already have this player in the index?
		//Note, if we still haven't checked records, we need to ghost this staff member
		//who has logged in before we finish!
		if(staffInd.containsKey(p.getName())){

			StaffRecord rec = staffInd.get(p.getName());

			if(rec.getLoggedIn()){
				Log.warning("Staff member " + p.getName() + "just logged in...and the player is marked already as logged in in the staff record. We've missed the exit event, somehow.");
				//TODO Decide what to do in this case :(
			} 
			
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
			this.pushRecordToDB(rec);
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
			StaffRecord r = null;
			// Is there a record for this player sitting in the DB?
			if(this.recordExists(p.getName())){
				//OOOH. well, lets pull it.
				r = this.pullRecordFromDB(p);
				if(debug) Log.info("Pulled timeon from db = " + r.getTimeOnline());
				
				//If there's an error, or we couldn't connect to the db (waaaat)
				//the record will be null, and we'll catch it down the below.
				//FIXEDIT : catch it here, flag it, run a temporary copy, and then merge with the DB version once we can connect.
				if(r != null){
					if(debug) Log.info("DEBUG : loggedin : " + (r.getLoggedIn() ? "yes" : "no") + " server : " + r.getServer()); 
					if(r.getLoggedIn() && r.getServer() != StaffTracker.MCServerName){
						//:facepalm: SOMEONE is logged in on another server.
						//So, we ghost this record, and wait for the other log in session
						//to terminate, since its likely either a crash (which will update
						//when the server comes back up, or a dual login, and we don't want
						//to lose any data with competing writes.
						r.setGhost(ghostReason.ANOTHERSERVER);
						if(debug) Log.warning("Record for player " + r.getName() + " is ghosting do to a login on another server!");
					}
					r.setServer(MCServerName);
					r.setOp(p.isOp());

					//If we're using staff Groups, it'd be nice to add which group they're in
					//to the staff record....
					String sgroup = "";
					if(pluginConf.getBoolean("stafftracker.staffGroup")){
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

					r.setGroup(sgroup);
					r.setLoggedIn(true);

					//At this point, we can pass this record off to update record to
					//fill in the rest of the blanks.
					this.updateRecord(p, r);

					if(r.getGhost()){
						if(debug) Log.info("DEBUG : Retrieved staff member : " + r.getName() + "(" + r.getUUID().toString() + ") from database. *** IN GHOST MODE ***");
					} else {
						if(debug) Log.info("DEBUG : Retrieved staff member : " + r.getName() + "(" + r.getUUID().toString() + ") from database.");
						this.pushRecordToDB(r);
					}
				}
			}

			if(r == null){

				//If we're using staff Groups, it'd be nice to add which group they're in
				//to the staff record....
				String sgroup = "";
				if(pluginConf.getBoolean("stafftracker.staffGroup")){
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
				r = new StaffRecord(false,ghostReason.NA,p.getUniqueId(), p.getName(), MCServerName, sgroup, false, p.getGameMode() == GameMode.CREATIVE, essentials.getUser(p).isSocialSpyEnabled());

				r.setOp(p.isOp());		

				//we can now send this off to UpdateRecord to fill in all the rest.
				
				this.updateRecord(p, r);
				this.pushRecordToDB(r);
				if(debug) Log.info("DEBUG : Added staff member : " + r.getName() + "(" + r.getUUID().toString() + ")");
			}
		}

	}

	/** Notification from the eventlistener that a player has quit.
	 * 
	 * @param Player p - the player that quit
	 * */
	public void playerQuit(Player p){
		if(debug) this.getLogger().info("DEBUG : playerQuit()");
		
		//Is this player a staff member we're tracking?
		if(staffInd.containsKey(p.getName())){
			//It is! OK, lets grab the record...
			StaffRecord r = staffInd.get(p.getName());
			
			//...finalize the players stuff...
			r.setLoggedIn(false);
			r.setCreative(false);
			r.setSocialSpy(false);
			r.setVanish(false);
			//...do one last update on the record...
			r.updateTime();
			//...push it to the database...
			if(this.pushRecordToDB(r)){
				//...and remove it!
				staffInd.remove(p.getName());
			} else {
				//Well we couldn't push the record to DB, but thats ok
				//We'll just try later.
				r.setCommitting(false);
			}
			
			
		}
	}

	/** Pushes all the records we're storing to the DB - called at plugin shutdown.
	 * IS NOT ASYNCH! Don't call this unless we really -have- to.
	 * */
	private void pushAllRecords(){
		Enumeration<StaffRecord> staffe = staffInd.elements();
		
		//get EVERY record into the pushDB.
		while(staffe.hasMoreElements()){
			StaffRecord r = staffe.nextElement();
			this.pushRecordToDB(r);
		}
		
		//Not used, but needed for the push.
		List<String> removelist = new ArrayList<String>();
		//and finally, push our new thread
		AsynchPush newpush = new AsynchPush(sqldb, this.getServer().getScheduler(),dbPushes, pluginConf.getString("mysql.table"), removelist);
		//We call back from the asynch thread a lot - this lets the class know to NOT do that.
		newpush.runSynch();
		newpush.run();
		if(newpush.failure == false && newpush.state == true){
			Log.info("Pushed all records to the Database.");
		} else {
			Log.severe("Failed to push records to the database - we've lost some data here.");
		}
	}
	
	/** On a timer, we update all the records we have - this is the function that gets
	 *  called from our scheduled task. 
	 *  
	 */
	
	public void updateAllRecords(){
				
		if(debug) this.getLogger().info("DEBUG : updateAllRecords()");
		
		if(staffInd.isEmpty() && this.dbPushes.isEmpty()){
			if(debug) Log.info("Staff Index is empty, and no record pushes! No work to do.");
			return;
		}

		//Make sure we're connected to the DB.
		if(!sqldb.connected){
			if(!sqldb.connect()){
				return;
			}
		}
		
		List<String> removelist = new ArrayList<String>();	
		
		Enumeration<StaffRecord> staffe = staffInd.elements();
		
		while(staffe.hasMoreElements()){
			StaffRecord r = staffe.nextElement();
			if(this.recordsChecked && this.getServer().getPlayer(r.getName()).isOnline() != r.getLoggedIn()){
				if(debug) Log.info("Detected online status mismatch between record and reality! CORRECTING!");
				r.setLoggedIn(this.getServer().getPlayer(r.getName()).isOnline());
			}
			
			//Standard expected record - logged in, and not ghosted
			if(r.getLoggedIn() && !r.getGhost()){
				this.updateRecord(server.getPlayer(r.getName()), r);
				this.pushRecordToDB(r);
			//Logged in, and ghosted.
			} else if(r.getLoggedIn() && r.getGhost()){
				//we need to merge the DB records with this one, so lets try to merge them!
				StaffRecord r2 = this.pullRecordFromDB(server.getPlayer(r.getName()));
				//test for DB error
				if(r2 != null){ 
					//test for record existance (did we pull a nonexistant record?
					if(!r2.isEmpty()){ 
						if(r2.getLoggedIn() && r2.getServer() != StaffTracker.MCServerName){
							//Erroring state still exists.
							if(debug) Log.warning("Player " + r2.getName() + " is still logged in to server " + r2.getServer());
							
						} else {

							if(r.unGhost(r2.getName(), r2.getTimeOnline(), r2.getTimeInVanish(), r2.getTimeInCreative(), r2.getTimeInSocialSpy())){
								//records merged! Update record normally.
								this.updateRecord(server.getPlayer(r.getName()), r);
								r.setCommitting(true);
								staffInd.put(r.getName(), r);
								this.pushRecordToDB(r);
								r.setCommitting(false);
								staffInd.put(r.getName(), r);
								if(this.debug) Log.info("DEBUG : Unghosted record for " + r.getName() + "!");
							} else {
								Log.warning("WARNING : Unghosting record for " + r.getName() + " failed! This data is likely a lost cause, but will try again lately.");
							}
						}

						
					} else {
						// Somewhere, somehow, we ghosted this record when we shouldn't have.
						// If our logic is bulletproof, then we'll never get here.
						// However, this is a fail of the highest order if we ever see this message.
						Log.severe("********************************************************");
						Log.severe("Fail detected : We ghosted a record we can never unghost!");
						Log.severe("Affected player : " + r.getName());
						Log.severe("Leaving record in play - its REALLY imporant this is addressed!");
						Log.severe("********************************************************");
					}
				} else {
					if(this.debug) Log.info("DEBUG : Failed to unghost record for " + r.getName() + " : Will try again next update!");

				}
			//NOT logged in. :|
			} else {
				//try to push this record to the DB again.
				if(pushRecordToDB(r)){
					if(!r.getLoggedIn())
						removelist.add(r.getName());
					else 
						if(debug) Log.info("DEBUG : did not add " + r.getName() + " to remove list, as player is ONLINE!");
					
				} else {
					//Failed again. Thank goodness we're persistant! :D
					r.setCommitting(false);
					staffInd.put(r.getName(), r);
				}
			}
		}
		
		//We need to clean up from previous runs.
		List<AsynchPush> pushremlist = new ArrayList<AsynchPush>();
		
		for(AsynchPush p : pendingAsynchs){
			//if we've already got a push going, we can cause issues spawning out 2 threads to try and
			//access the DB at the same time, so detect that, and abort if need be.
			if(StaffTracker.dbPushActive == true){
				if(this.debug) Log.info("DEBUG : Already pushing! aborting!");
				break;
			}
			if(p.failure == false && p.state == true){
				//This push succeeded!
				if(!p.removelist.isEmpty()){
					//If we have any players that need to be removed...
					for(String n : p.removelist){
						//The only reason to remove a player is because he's offline.
						Player pl = this.getServer().getPlayer(n);
						if(pl != null && !pl.isOnline()){
							staffInd.remove(n);
							if(this.debug) Log.info("Removed staff member " + n + " from staff index!");
						} else {
							if(debug) Log.info("Did not remove " + n + " as that player is still online!");
						}
					}
					
					p.removelist.clear();
				}
				//Lets avoid concurrent modification exceptions, shall we?
				pushremlist.add(p);
			} else if(p.failure = true && StaffTracker.dbPushActive == false) {
				//this push failed - try again!
				p.failure = false;
				p.state = false;
				Bukkit.getScheduler().runTaskAsynchronously(this, p);
				if(debug) Log.info("DEBUG : Spawned another asynch thread to push (failed) data to DB!");
				//since we've just spawned a new thread, break here so we don't spawn another!
				break;
			}
		}
		
		//clear out any successfull pushes here.
		for(AsynchPush p : pushremlist){
			pendingAsynchs.remove(p);
		}
		
		//and finally, push our new thread
		AsynchPush newpush = new AsynchPush(sqldb, this.getServer().getScheduler(),dbPushes, pluginConf.getString("mysql.table"), removelist);
		this.pendingAsynchs.add(newpush);
		if(!StaffTracker.dbPushActive){
			Bukkit.getScheduler().runTaskAsynchronously(this, newpush);
			if(debug) Log.info("DEBUG : Spawned asynch thread to push data to DB!");
		}
		//clear out our dbPushes, since we've already got these pushes scheduled
		dbPushes.clear();
	}		
	

	///////////////////////////////////////////////////////////////////////////////////
	// HELPER FUNCTIONS
	///////////////////////////////////////////////////////////////////////////////////
	public boolean recordsChecked = false;
	
	/** This function takes our string of tracked commands, and makes sure only currently tracked commands
	 * 	are included. */
	private String cleanTracking(String curtracks){
		String ret = "";
		String[] tempstring = curtracks.split(",", -1);
		
		for(String s : tempstring){
			String[] tempstring2 = s.split(":", -1);
			if(this.commandInd.containsKey(tempstring2[0])){
				//This is a valid command we're tracking. awesome.
				ret = ret + s + ",";
			} else {
				//No longer a valid command - so we'll just let it evaporate
			}
		}
		
		return ret;
	}
	
	/** This function should ONLY be run on plugin startup! It checks to see if we exited gracefully last shutdown, and if not, closes the records out.*/
	private void checkRecords(){
		if(debug) Log.info("DEBUG : checkRecords()");
		
		Statement s = null;
		ResultSet rs = null;
		
		//Make sure we're connected to the DB.
		if(!sqldb.connected){
			if(!sqldb.connect()){
				//FIXEDIT : If this returns null because we can't connect to the DB, and we use this returning null to signify that there's no record in the DB, we have an issue - Need to make this NOT AN ISSUE. :(
				//now checks recordsChecked at the call, and makes three attempts to checkrecords before failing.
				recordsChecked = false;
				if(debug) Log.info("DEBUG : Couldn't connect to DB. This is kind of critical that we check this - If we can't connect, Plugin will be disabled :(");
				return;
			}
		}
		
		try{
			//read in all DB records from this server, where loggedin = true.
			s = sqldb.getConnection().createStatement();
			rs = s.executeQuery("select * from " + pluginConf.getString("mysql.table") + " where SERVER = '" + MCServerName + "' AND LOGGEDIN = true");
			while(rs.next()){
				
				StaffRecord r = new StaffRecord(false, ghostReason.NA,rs.getString("PUUID"), rs.getString("NAME"), rs.getString("SERVER"), rs.getBoolean("OP"), rs.getString("PGROUP"), rs.getBoolean("LOGGEDIN"),rs.getLong("TIMELOGGED"), rs.getLong("TIMEVANISH"),	rs.getLong("TIMECREATIVE"),	rs.getLong("TIMESOCIALSPY"), rs.getString("COMMANDCSV"));
				r.setCreative(false);
				r.setLoggedIn(false);
				r.setVanish(false);
				r.setSocialSpy(false);
				//Write it to the staff Index.
				this.staffInd.put(r.getName(), r);
					
			} 

			rs.close();
			s.close();			
			//Now, we write the records back out to the DB, with the correct information.
			//Since noone is online, this will clear out the staff index.
			this.updateAllRecords();
			
			// This is kind of important - since we check it in update all records ;)
			this.recordsChecked = true;
			
		} catch(SQLException e){
			e.printStackTrace();
		}
	}
	
	private boolean recordExists(String name){
		if(debug) Log.info("DEBUG : recordExists()");
		Statement s = null;
		ResultSet rs = null;
		boolean ret = false;
		try{
			s = sqldb.getConnection().createStatement();
			rs = s.executeQuery("select * from " + pluginConf.getString("mysql.table") + " where name = '" + name + "'");
			if(rs.next()){
				ret = true;
			} else {
				ret = false;
			}
			rs.close();
			s.close();
			return ret;
		} catch (SQLException e){
			return false;
		}
	}
	
	public StaffRecord pullRecordFromDB(Player p){
		if(debug) Log.info("DEBUG : pullRecordFromDB()");
				
		Statement s = null;
		ResultSet rs = null;
		
		//Make sure we're connected to the DB.
		if(!sqldb.connected){
			if(!sqldb.connect()){
				//FIXED : null on error, empty record on no-record state.
				return null;
			}
		}
		try{
			s = sqldb.getConnection().createStatement();
			rs = s.executeQuery("select * from " + pluginConf.getString("mysql.table") + " where name = '" + p.getName() + "'");
			if(rs.next()){
				String cleantracks = this.cleanTracking(rs.getString("COMMANDCSV"));
				StaffRecord r = new StaffRecord(false, ghostReason.NA,rs.getString("PUUID"), rs.getString("NAME"), rs.getString("SERVER"), rs.getBoolean("OP"), rs.getString("PGROUP"), rs.getBoolean("LOGGEDIN"),rs.getLong("TIMELOGGED"), rs.getLong("TIMEVANISH"),	rs.getLong("TIMECREATIVE"),	rs.getLong("TIMESOCIALSPY"), cleantracks);
				rs.close();
				s.close();
				return r;
				
			}  else {
				if(debug) Log.info("DEBUG : No record to Pull from DB.");
				rs.close();
				s.close();
				
				//returns a StaffRecord with .empty == true;
				return new StaffRecord();
			}
		} catch(SQLException e){
			e.printStackTrace();
		}
		
		return null;
	}

	/** Pushes StaffRecord r to the database. */
	public boolean pushRecordToDB(StaffRecord r){
		if(debug) Log.info("DEBUG : pushRecordtoDB()");
		StaffRecord replace = null;
		
		for(StaffRecord c : dbPushes){
			if(c.getName().matches(r.getName())){
				replace = c;
			}
		}
		
		if(replace != null){
			if(debug) Log.info("Replacing record for " + r.getName());
			dbPushes.remove(replace);
			this.dbPushes.add(r);
		} else {
			if(debug) Log.info("DEBUG : Added " + r.getName() + " to the record push list");
			this.dbPushes.add(r);
		}
		
		return true;
	}

	/** Deletes staffmember record from the DB*/
	public boolean removeRecordFromDB(String Name){
		if(debug) Log.info("DEBUG : removeRecordFromDB()");
		
		//Make sure we're connected to the DB.
		if(!sqldb.connected){
			if(!sqldb.connect()){
				return false;
			}
		}
		String query = "delete from " + pluginConf.getString("mysql.table") + " where NAME = ?";
	    PreparedStatement preparedStmt;
	    
		try {
			preparedStmt = this.sqldb.getConnection().prepareStatement(query);
			preparedStmt.setString(1, Name);
			preparedStmt.execute();
			sqldb.close();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	      
		
		
	}

	public boolean isStaff(Player p){
		//do we need to add this player to the index?
		if(debug) Log.info("DEBUG : isStaff()");
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
		if(debug) Log.info("DEBUG : r.timein = " + r.getTimeOnline());
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

		//...and/or from essentials (/evanish)
		if(pluginConf.getBoolean("plugin.useessentials")){
			//if we're already vanished, don't worry about re-confirming!
			if(isVanished == false){
				if(debug) this.getLogger().info("DEBUG : checking essentials for vanished players...");
				List<String> vp = this.essentials.getVanishedPlayers();

				if(vp.contains(p.getName())){
					isVanished = true;
					if(debug) this.getLogger().info("DEBUG : Found player - he/she is vanished.");
				}
			}
		} else {
			if(debug) this.getLogger().info("DEBUG : Not configured to use essentials.");
		}

		if(debug) this.getLogger().info("DEBUG : isVanish = " + (isVanished ? "True" : "False"));

		r.setOp(p.isOp());

		//update record if need be for creative.
		r.setCreative(p.getGameMode() == GameMode.CREATIVE);

		//update record if need be for vanish.
		r.setVanish(isVanished);

		//update record if need be for socialspy
		r.setSocialSpy(essentials.getUser(p).isSocialSpyEnabled());
		
		r.setLoggedIn(this.getServer().getPlayer(p.getName()).isOnline());

		//If we're using staff Groups, it'd be nice to update which group they're in
		//to the staff record....
		String sgroup = "Staff";
		if(pluginConf.getBoolean("stafftracker.staffGroup")){
			if(this.permission != null){
				String pgroups[] = permission.getPlayerGroups(p);
				for(String s : pgroups){
					if(debug) Log.info("Checking vault perm group " + s);
						if(this.staffGroups.contains(s)){
							if(debug) Log.info("Yup.");
						
							sgroup = s;
							break;
						}
					if(debug) Log.info("Nope.");
				}
			} else {
				List<String> pgroups = essentials.getPermissionsHandler().getGroups(p);
				for(String s : pgroups){
					if(debug) Log.info("Checking essentials perm group " + s);
						if(this.staffGroups.contains(s)){
							if(debug) Log.info("Yup.");
						
							sgroup = s;
							break;
						}
					if(debug) Log.info("Nope.");
				}
			}
		}

		r.setGroup(sgroup);	
		
		//finally, update the record. otherwise, what's the point, right?
		staffInd.put(p.getName(), r);
		
		if(debug) StaffTracker.Log.info("DEBUG : after put : time in = " + r.getTimeOnline());
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
	
	/**Generates a string of random numbers, suitable for a confirmation string
	 * IE, len of 3 might return a string like "184" */
	public String generateRandomNumbers(int len){
		String ret = "";
		
		for(int x = 0; x < len; x++){
			int n = (int)(Math.random() * 10);
			ret += Integer.toString(n);
		}
		
		return ret;
	}
}
