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

/** StaffTracker - A configurable Bukkit plugin to keep track of server Staff members, network wide.
 * 
 * 	Built with bukkit-1.6.4-R2.0
 * 
 *  Hard Plugin Dependencies :
 *  	None! ^_^
 *  Soft Plugin Dependencies :
 *  	Essentials (2.14)(Vanish provider, backup group permissions support, if configured for groups)
 *  	VanishNoPacket (Vanish provider)
 *  	Vault (1.4.2) (Primary group permissions support, if configured for groups)  
 *  
 *  Tested extensively on 1.8.4 Spigot during Alpha.
 *  Will test on 1.7.10 & 1.6.4 as well, prior to moving to beta.
 *  
 *  Pending Feature list :
 *  	Move to SQL transactions? BEFORE BETA.
 *  	Test 1.7.10 & 1.6.4 BEFORE BETA.
 *  
 *  After Beta :
 *  	Non-SQL storage method for standalone, non-networked servers.
 *  	Small class to handle tying together all the various permissions providers we could
 *  		encounter in the "wild" - this is more to neaten up the code than anything else
 *  	"Request" specific staff member to a server - /st need <member name>
 *  	"Request" specific staff group to a server - /st need <group name>
 *  	"Request" anyone at all to a server - /st need
 *  	Track AFK status.
 *  	Configure staff group DISPLAY names, (ie, perm is "mod", but displays as Moderator.
 *  	Configure staff display color settings (mod blue, admin red, op yellow, etc).
 *		
 *		How about a configurable list of commands to track, to cover all this :
 *		{
 *			Track /OI (open inventory) command usage.	
 *			Track item spawn ins from NEI cheat mode, /give, and creative?
 *  		Track /kill?
 *  	}
 *  
 *  May become pending features :
 *  	WorldBorder bypass state?
 *  	
 *  
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
	private final int CONF_VER = 5;
	
	//Are we printing debugging/troubleshooting messages to the log?
	//This is set from the config, but influences messages BEFORE the config is read.
	boolean debug = true;
	
	//If we're using essentials
	Plugin essentialsPlugin;
	Essentials essentials;
	//If we're using VanishNoPacket
	Plugin vanishPlugin;
	VanishPlugin vanish;
	VanishManager vanishMan;

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
	Hashtable<String,StaffRecord> staffInd;
	
	//Cache for Staff groups, read in from config
	List<String> staffGroups;
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
					if(pluginConf.getBoolean("stafftracker.staffGroups") && this.essPerms == null){
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
			if(pluginConf.getBoolean("stafftracker.staffGroups"))
			Log.severe("Fallback to Essentials Permissions failed : Disabling plugin as it cannot operate as configured!");
			Log.severe("Either DISABLE group permissions config, or install vault or essentials!");
			return false;
			
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
			//ADDED : vault's permissions - we can use either one.
			//TODO : Replace with a perms gateway class to handle this mess.
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
		//Clean up database related stuff, if we're using it.
		if(this.useSQL)
			if(this.sqldb != null){
				
				this.sqldb.close();
				//TODO : Save out all StaffRecords
			}
		
		this.enabled = false;
		getLogger().info("StaffTracker disabled successfully!");
	}
		
	/** Displays the help command for this plugin */
	private void commandHelp(CommandSender sender, String label){
		//TODONE : Colorize!
		sender.sendMessage("�2Staff�aTracker �2Help :");
		sender.sendMessage("�a~~~~~~~~~~~~~~~~~~~");
		sender.sendMessage("�2/" + label + " help �a- This screen!");
		if(debug) sender.sendMessage("�c/" + label + " dumprec <playername> - Displays internal staff recordset, or an entry for player <playername>, if one exists.");
		if(debug) sender.sendMessage("�c/" + label + " updateall - Forces internal staff recordset to be updated.");
		sender.sendMessage("�2/" + label + " list �a- Shows all online staff members.");
		sender.sendMessage("�2/" + label + " show <Playername> �a- Shows stats for a given staffmember.");
	}
	
	/** Command to dump an internal StaffRecord - Only works if debug == true! */
	private void commandDumpRec(CommandSender sender, String[] args){
		if(!debug) return;
		
		if(args.length == 2){
			if(staffInd.containsKey(args[1])){
				sender.sendMessage(staffInd.get(args[1]).toString());
			} else {
				sender.sendMessage("�cPlayer is not logged into this server, or isn't detected as staff!");
			}
			
		} else {
			Enumeration<StaffRecord> staffe = staffInd.elements();
			while(staffe.hasMoreElements()){
				StaffRecord r = staffe.nextElement();
				
				sender.sendMessage("\n" + r.toString() + "\n");
			}
		}	
	}
	
	/** Command to force an updateAllRecords() - Only works if the config is in Debug mode!*/
	public void commandForceUpdateAllRecords(CommandSender sender){
		if(debug){
			Log.info(sender.getName() + " Forced internal recordset update.");
			this.updateAllRecords();
			sender.sendMessage("All records updated!");
		}
		
	}
	/** List current staffmembers online */
	public void commandList(CommandSender sender){
		if(debug) Log.info("DEBUG : commandList()");
		if(getServer().getConsoleSender() == sender || (sender instanceof Player && this.isStaff((Player)sender))){
			if(debug) Log.info("== console, sender instanceof Player & isstaff()");
			if(!sqldb.isConnected()){
				if(!sqldb.connect()){
					sender.sendMessage("�cSorry, couldn't establish a connection to the database. Please try again in a little while!");
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
					sender.sendMessage("�2" + rs.getString("PGROUP") + " �f| �a" + rs.getString("NAME") + "�f | �9" + rs.getString("SERVER"));
				}
				
				if(numrec == 0){
					//This makes sense because we may have it configurable that non-staff can
					//access this command...
					sender.sendMessage("�cSorry, no staff are online right now!");
				} else {
					sender.sendMessage("�2Displayed �a" + numrec + "�2 records.");
				}
				rs.close();
				s.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
		} else {
			sender.sendMessage("�cSorry, currently, only staff members can use this command.");
		}
	}
	
	public void commandShow(CommandSender sender, String[] args){
		if(debug) Log.info("DEBUG : commandShow("+args[1]+")");
		
		//Console or staff only!
		if(getServer().getConsoleSender() == sender || (sender instanceof Player && this.isStaff((Player)sender))){
			
			if(!sqldb.isConnected()){
				if(!sqldb.connect()){
					sender.sendMessage("�cSorry, couldn't establish a connection to the database. Please try again in a little while!");
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
						StaffRecord r = new StaffRecord(false, rs.getString("PUUID"), rs.getString("NAME"), rs.getString("SERVER"), rs.getBoolean("OP"), rs.getString("PGROUP"), rs.getBoolean("LOGGEDIN"),rs.getLong("TIMELOGGED"), rs.getLong("TIMEVANISH"),	rs.getLong("TIMECREATIVE"),	rs.getLong("TIMESOCIALSPY"));
						r.setVanish(rs.getBoolean("VANISH"));
						r.setCreative(rs.getBoolean("CREATIVE"));
						r.setSocialSpy(rs.getBoolean("SOCIALSPY"));
						String res = r.toStringNice();
						sender.sendMessage(res);
						rs.close();
						query.close();
					}  else {
						if(debug) Log.info("DEBUG : commandShow(" + args[1] + ") - record for this player exists, recordset returned no results T_T");
						sender.sendMessage("�cSorry, but for some reason, the record for that staff member couldn't be pulled from the database.");
						rs.close();
						query.close();				
					}
				} else {
					sender.sendMessage("�cSorry, but no record for that staff member exists. Check your spelling, and try again!");
				}
			} catch (SQLException e) {
				e.printStackTrace();
				sender.sendMessage("�cSorry, but an internal error has occured. Please check the console, or the server log, for more details.");
			}
			
			
		} else {
			sender.sendMessage("�cSorry, currently, only staff members can use this command.");
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
						else sender.sendMessage("�cCommand requires the plugin be in Debug mode!");
						break;
					case "updateall":
						if(debug) commandForceUpdateAllRecords(sender);
						else sender.sendMessage("�cCommand requires the plugin be in Debug mode!");
						break;
					case "list":
						commandList(sender);
						break;
					case "show":
						commandShow(sender, args);
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
						r.setGhost();
						if(debug) Log.warning("Record for player " + r.getName() + " is ghosting do to a login on another server!");
					}
					r.setServer(MCServerName);
					r.setOp(p.isOp());

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
				r = new StaffRecord(false,p.getUniqueId(), p.getName(), MCServerName, sgroup, false, p.getGameMode() == GameMode.CREATIVE, essentials.getUser(p).isSocialSpyEnabled());

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

	/** On a timer, we update all the records we have - this is the function that gets
	 *  called from our scheduled task. 
	 *  
	 */
	public void updateAllRecords(){
		if(debug) this.getLogger().info("DEBUG : updateAllRecords()");
		List<String> removelist = new ArrayList<String>();		
		
		Enumeration<StaffRecord> staffe = staffInd.elements();
		while(staffe.hasMoreElements()){
			StaffRecord r = staffe.nextElement();
			//Standard expected record - logged in, and not ghosted
			if(r.getLoggedIn() && !r.getGhost()){
				this.updateRecord(server.getPlayer(r.getName()), r);
				r.setCommitting(true);
				staffInd.put(r.getName(), r);
				this.pushRecordToDB(r);
				r.setCommitting(false);
				staffInd.put(r.getName(), r);
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
						// However, this is a fail of the highest order, if we get here.
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
					removelist.add(r.getName());
				} else {
					//Failed again. Thank goodness we're persistant! :D
					r.setCommitting(false);
				}
			}
		}
		//Clean up the staff index if we had to commit!
		if(!removelist.isEmpty()){
			for(String n : removelist){
				staffInd.remove(n);
			}
		}
		
	}

	///////////////////////////////////////////////////////////////////////////////////
	// HELPER FUNCTIONS
	///////////////////////////////////////////////////////////////////////////////////
	public boolean recordsChecked = false;
	
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
				
				StaffRecord r = new StaffRecord(false, rs.getString("PUUID"), rs.getString("NAME"), rs.getString("SERVER"), rs.getBoolean("OP"), rs.getString("PGROUP"), rs.getBoolean("LOGGEDIN"),rs.getLong("TIMELOGGED"), rs.getLong("TIMEVANISH"),	rs.getLong("TIMECREATIVE"),	rs.getLong("TIMESOCIALSPY"));
				r.setCreative(false);
				r.setLoggedIn(false);
				r.setVanish(false);
				r.setSocialSpy(false);
				//Write it to the staff Index.
				this.staffInd.put(r.getName(), r);
					
			} 

			rs.close();
			s.close();
			// This is kind of important - since we check it in update all records ;)
			this.recordsChecked = true;
			
			//Now, we write the records back out to the DB, with the correct information.
			//Since noone is online, this will clear out the staff index.
			this.updateAllRecords();
			
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
				StaffRecord r = new StaffRecord(false, rs.getString("PUUID"), rs.getString("NAME"), rs.getString("SERVER"), rs.getBoolean("OP"), rs.getString("PGROUP"), rs.getBoolean("LOGGEDIN"),rs.getLong("TIMELOGGED"), rs.getLong("TIMEVANISH"),	rs.getLong("TIMECREATIVE"),	rs.getLong("TIMESOCIALSPY"));
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

	//TODO : push Records that need updates to a pool, commit all the records at once
	// This works, but its likely naive as hell...but IT WORKS. ;)
	public boolean pushRecordToDB(StaffRecord r){
		//Let the rest of everything know - WE ARE COMMITTING TO DB!
		r.setCommitting(true);
		staffInd.put(r.getName(), r);
		if(debug) Log.info("DEBUG : pushRecordtoDB()");

		PreparedStatement update = null;

		//Make sure we're connected to the DB.
		if(!sqldb.connected){
			if(!sqldb.connect()){
				return false;
			}
		}

		try{

			if(this.recordExists(r.getName())) {
				update = sqldb.getConnection().prepareStatement("UPDATE " + pluginConf.getString("mysql.table") +
								" set SERVER = ?, " + 
								" OP = ?," +
								" LOGGEDIN = ?," +
								" VANISH = ?," +
								" CREATIVE = ?, " +
								" SOCIALSPY = ?, " +
								" TIMELOGGED = ?, " +
								" TIMEVANISH = ?, " +
								" TIMECREATIVE = ?, " +
								" TIMESOCIALSPY = ?, " +
								" PGROUP = ? " + 
								"where NAME = ?");
				update.setString(1, r.getServer());
				update.setBoolean(2, r.getOp());
				update.setBoolean(3, r.getLoggedIn());
				update.setBoolean(4, r.getVanish());
				update.setBoolean(5, r.getCreative());
				update.setBoolean(6, r.getSocialSpy());
				update.setLong(7,r.getTimeOnline());
				update.setLong(8, r.getTimeInVanish());
				update.setLong(9, r.getTimeInCreative());
				update.setLong(10, r.getTimeInSocialSpy());
				update.setString(11, r.getGroup());
				update.setString(12, r.getName());
				update.executeUpdate();
	
			} else{
				update = sqldb.getConnection().prepareStatement(
						"INSERT INTO " + pluginConf.getString("mysql.table") + "("+
								"PUUID, " +
								"NAME, "+
								"SERVER, "+
								"PGROUP, "+
								"OP, "+
								"LOGGEDIN, "+
								"VANISH, "+
								"CREATIVE, "+
								"SOCIALSPY, "+
								"TIMELOGGED, "+
								"TIMEVANISH, "+
								"TIMECREATIVE, "+
								"TIMESOCIALSPY) "+
						"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
				update.setString(1, r.getUUID().toString());
				update.setString(2, r.getName());
				update.setString(3, r.getServer());
				update.setString(4, r.getGroup());
				update.setBoolean(5, r.getOp());
				update.setBoolean(6, r.getLoggedIn());
				update.setBoolean(7, r.getVanish());
				update.setBoolean(8, r.getCreative());
				update.setBoolean(9, r.getSocialSpy());
				update.setLong(10, r.getTimeOnline());
				update.setLong(11, r.getTimeInVanish());
				update.setLong(12, r.getTimeInCreative());
				update.setLong(13, r.getTimeInSocialSpy());
				update.executeUpdate();
	
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		} finally {
			try{
				update.close();
			} catch (SQLException e) {
				
			}
		}

		return true;
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
		
		StaffTracker.Log.info("after put : time in = " + r.getTimeOnline());
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
