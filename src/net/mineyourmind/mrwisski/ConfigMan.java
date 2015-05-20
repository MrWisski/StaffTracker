package net.mineyourmind.mrwisski;

import java.io.BufferedWriter;
import java.io.File;


import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import com.google.common.io.Files;


// Config management class to bypass bukkit 1.6.4's
// Yaml config read/write issues.
/** Config Management class to bypass bukkit 1.6.4's YAML config read/write issues
 *
 * @author MrWisski
 * @version 1.0r1 May 10, 2015.
 */
public class ConfigMan {
	//Internal use only vars
	private YamlConfiguration thisConfig = null;
	private File thisConfigFile = null;
	private Logger Log = null;
	private boolean debug = false;

	/** Denotes if this configman instance is running a valid config */
	public boolean configLoaded = false;

	// Internal : Stores current config to disk.
	private boolean saveFile(){
		// Do we even have data to save?
		if(thisConfig == null){
			Log.severe("ConfigMan : saveFile - Configuration is NULL! ABORTING!");
			return false;
		}

		String s = thisConfig.saveToString();

		//Lets make sure there's actual data here first...
		if(s.length() == 0){
			Log.severe("ConfigMan : saveFile() - Configuration contains no data! ABORTING!");
			return false;
		}


		//Lets go ahead and try to write this data out then...
		try {
			Files.write(s.getBytes(), this.thisConfigFile);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return true;
	}


	//Internal - Read in a YAML configuration file.
	private boolean readFile(){
		//Make sure we have a file to read in, before we try reading it in.
		if(this.thisConfigFile == null){
			Log.severe("ConfigMan - readFile() called, with no config file set! ABORTING!");
			return false;
		}
		if(!this.thisConfigFile.exists()){
			Log.severe("ConfigMan - Could not read config file at : " + this.thisConfigFile.getAbsolutePath());
			return false;
		}
		if(this.thisConfigFile.length() == 0){
			Log.info("ConfigMan - Config file is zero length - Nothing to read!");
			return false;
		}


		if(this.debug) Log.info("ConfigMan Debug - Read of configuration file requested : File is " + this.thisConfigFile.length() + " bytes long.");
		String conf = null;

		try {
			conf = Files.toString(this.thisConfigFile, Charset.defaultCharset());
		} catch (IOException e1) {

			e1.printStackTrace();
		}

		if(this.debug) Log.info("ConfigMan Debug - Config String : " + conf);
		if(thisConfig == null)
			thisConfig = new YamlConfiguration();

		try {
			thisConfig.loadFromString(conf);
		} catch (InvalidConfigurationException e) {

			Log.info(e.getMessage());
			e.printStackTrace();

		}
		this.configLoaded = true;
		return true;

	}

	/** Static helper function to ensure the config path and config file are valid.
	 *  Quite useful for loading the config on Enable.
	 *  
	 * */
	public static void createConfigPath(File configFile, File configPath,String defConfig,StaffTracker plugin){

		if(!configPath.exists()){
			configPath.mkdir();
		}
		if(!configFile.exists()){
			//configFile.createNewFile();

			//Lets make sure there's actual data here first...
			if(defConfig.length() == 0){
				//Nope. default config is empty. sad pandas for us. oh well.
				plugin.getLogger().info("Config Manager : Default config is 0 len.");
				return;
			}


			//Lets go ahead and try to write this data out then...
			try {
				FileWriter fw = new FileWriter(configFile.getAbsoluteFile());
				BufferedWriter bw = new BufferedWriter(fw);
				bw.write(defConfig);
				bw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/** Config Man Constructor 
	 * 
	 * @param fileName 		Path + filename of the configuration file to load in.
	 * @param useLog 		Logger from main class to use when printing error/info messages.
	 * */
	public ConfigMan(String fileName,Logger useLog){
		if(useLog == null){
			Log.severe("ConfigMan - Null logger passed to constructor - THIS IS UNACCEPTABLE!");
		} else if(fileName.length() == 0){
			Log.severe("ConfigMan - empty string was passed to constructor for filename - THIS IS UNNACEPTABLE!");
		} else {
			this.Log = useLog;
			this.thisConfigFile = new File(fileName);

		}

	}

	/** Config Man Constructor 
	 * 
	 * @param f     		File of the configuration file to load in.
	 * @param useLog 		Logger from main class to use when printing error/info messages.
	 * */
	public ConfigMan(File f, Logger useLog){
		if(useLog == null){
			Log.severe("ConfigMan - null logger passed to constructor - THIS IS UNACCEPTABLE!");
		} else if(f == null){
			Log.severe("ConfigMan - null File was passed to constructor - THIS IS UNNACEPTABLE!");
		} else {
			this.thisConfigFile = f;
			this.Log = useLog;
		}
	}

	/** Write the current Yaml config to a file on disk */
	public boolean writeConfig(){
		return this.saveFile();
	}

	/** Read the file on disk into the current Yaml Config */
	public boolean loadConfig(){
		return this.readFile();
	}

	/** returns the current YamlConfiguration. Will call loadConfig if the config is not already loaded. 
	 * 
	 * @return Currently stored YamlConfiguration - use loadConfig before calling this if you NEED an up to date copy of the config! */
	public YamlConfiguration getConfig(){
		if(this.configLoaded) {
			return this.thisConfig;
		} else {
			boolean state = this.readFile();
			if(state){
				return this.thisConfig;
			} else {
				Log.severe("ConfigMan : Configuration could not be loaded from disk - getConfig is returning NULL.");
				return null;
			}
		}
	}

	/** sets the current YamlConfiguration 
	 * 
	 * @param YamlConfiguration newConfig - New configuration. WARNING - this will replace the old config if writeConfig() is called!
	 * */
	public void setConfig(YamlConfiguration newConfig){
		this.thisConfig = newConfig;
		this.configLoaded = true;
	}

	/** sets the current YamlConfiguration from a string.
	 * 
	 * @param String newConfig - New configuration. WARNING - this will replace the old config if writeConfig() is called!
	 */ 
	public boolean setConfigFromString(String newConfig){

		if(thisConfig == null && this.thisConfigFile != null){
			thisConfig = new YamlConfiguration();

		} else if(thisConfig == null && this.thisConfigFile == null){

			return false;
		}

		try {
			this.thisConfig.loadFromString(newConfig);
			return true;
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
			return false;
		}
	}
}

