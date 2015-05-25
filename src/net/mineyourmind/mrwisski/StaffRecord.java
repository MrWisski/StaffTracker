package net.mineyourmind.mrwisski;

import java.util.UUID;

import org.apache.commons.lang.time.DurationFormatUtils;



/** Class containing all the data about a particular staff member we track! */
public class StaffRecord {
	private boolean debug = false;
	
	private String name = "Unknown Player";
	private UUID uuid;
	private String server = "Unknown Server";
	private boolean isOP = false;
	private String group = "None";
		
	private boolean vanished = false;
	private boolean creative = false;
	private boolean social = false;
	private boolean loggedIn = false;
	
	private long timeInVanish = 0;
	private long timeInCreative = 0;
	private long timeInSocialSpy = 0;
	private long timeInGame = 0;

	// Is this record empty? used as a status return, as a null record is an error condition.
	private boolean empty = false;
	// If this record is currently being pushed to the SQL - 
	// used externally to avoid nasty things like trying to get
	// the status of an offline player.
	private boolean isCommiting = false;
	
	//Flag for record ghosting - 
	//If this is a ghost record, it never gets commited to db.
	//In order to come out of ghost mode, we have to supply data from the DB, which will be
	//merged in with the current data.
	//Use ghost mode when we have a DB connection failure when we're trying to retrieve records
	//or when the data in the DB indicates we already have a connection elsewhere.
	private boolean ghost = false;
	
	private long enterVanish = 0;
	private long enterCreative = 0;
	private long enterSocialSpy = 0;
	private long loginTime = 0;
	
	StaffRecord(){
		empty = true;
	}
	
	StaffRecord(boolean isGhost, UUID id, String name, String server, String group, boolean vanished, boolean creativemode, boolean socialspy){
		this.ghost = isGhost;
		this.uuid = id;
		this.name = name;
		setGroup(group);
		this.server = server;
		setLoggedIn(true);
		setVanish(vanished);
		setCreative(creativemode);
		setSocialSpy(socialspy);
	}
	
	/** This constructor is used from data pulled from the DB. */
	StaffRecord(boolean isGhost, String id, String name, String server, boolean isOp, String group, boolean isOnline, long timeon, long timevanish, long timecreative, long timesocial){
		this.ghost = isGhost;
		this.uuid = UUID.fromString(id);
		this.name = name;
		this.server = server;
		this.isOP = isOp;
		this.group = group;
		this.setLoggedIn(isOnline);
		if(isGhost){
			//If we're ghosting, ignore any time in data - this will be overwritten later when
			//we merge. we ONLY care about the total time for THIS SESSION.
			this.timeInGame = 0;
			this.timeInVanish = 0;
			this.timeInCreative = 0;
			this.timeInSocialSpy = 0;
		} else {
			this.timeInGame = timeon;
			this.timeInVanish = timevanish;
			this.timeInCreative = timecreative;
			this.timeInSocialSpy = timesocial;
			
		}
		
	}
	
	public boolean unGhost(String name, long timeon, long timevanish, long timecreative, long timesocial){
		StaffTracker.Log.info("unGhost : passed timeon is : " + timeon);
		if(this.name.equals(name) && this.ghost){
			this.timeInGame += timeon;
			this.timeInVanish += timevanish;
			this.timeInCreative += timecreative;
			this.timeInSocialSpy += timesocial;
			this.ghost = false;
			return true;
		} else {
			StaffTracker.Log.warning("this.name = [" + this.name + "] passed in name = [" + name + "]");
			StaffTracker.Log.warning("this.ghost = " + (this.ghost ? "true" : "false"));
			return false;
		}
		
	}
	
	//////////////////////////////////
	//SETTERS AND GETTERS - Pretty standard stuff here
	//WARNING : You are entering a NO COMMENT ZONE ^_^
	//////////////////////////////////
	//TODO : sanity checking on input before release, size limits, cleaning, etc.
	
	public boolean isEmpty(){return this.empty;}
	
	public boolean getGhost(){return ghost;}
	public void setGhost(){ghost = true;}
	
	public String getGroup(){return group;}
	public void setGroup(String group){
		if(group != "")
			this.group = group;
		else
			this.group = "Staff";
	}
	
	public String getName(){return name;}
	public UUID getUUID(){return uuid;}

	public String getServer(){return server;}
	public void setServer(String server){this.server = server;}

	
	public void setOp(boolean op){
		this.isOP = op;
		
	}
	public boolean getOp(){
		return this.isOP;
	}
	
	public void setCommitting(boolean status){ this.isCommiting = status;}
	public boolean getCommitting(){return this.isCommiting;}
	
	public boolean getLoggedIn(){return loggedIn;}
	
	public void setLoggedIn(boolean state){
		if(debug) StaffTracker.Log.info("setloggedin() timeingame = " + this.timeInGame);
		if(debug) StaffTracker.Log.info("login time = " + this.loginTime);
		if(debug) StaffTracker.Log.info("sys   time = " + System.currentTimeMillis());
		if(loggedIn != state){
			if(debug) StaffTracker.Log.info("loggedin != state");
			if(state){
				if(debug) StaffTracker.Log.info("state == true");
				loggedIn = true;
				loginTime = System.currentTimeMillis();
				
			} else {
				if(debug) StaffTracker.Log.info("state == false");
				loggedIn = false;
				timeInGame += (System.currentTimeMillis() - loginTime);
				
			}
		} else if (loggedIn == state && loggedIn == true ){
			if(debug) StaffTracker.Log.info("loggedin == state, and loggedIn == true");
			timeInGame += (System.currentTimeMillis() - loginTime);
			loginTime = System.currentTimeMillis();
			
		}
		if(debug) StaffTracker.Log.info("setloggedin() timeingame = " + this.timeInGame);
	}
	
	public void setLogOffAt(long time){
		if(loggedIn){
			if(time > loginTime)
				timeInGame += (time - loginTime);
			else
				StaffTracker.Log.warning("setLogOffAt() time occurs BEFORE user logged in!");
			
			loggedIn = false;
			
		}
	}
	
	public void setVanishOffAt(long time){
		if(vanished){
			if(time > enterVanish)
				timeInVanish += (time - enterVanish);
			else
				StaffTracker.Log.warning("setVanishOffAt() time occurs BEFORE user entered vanish!");
			
			vanished = false;
			
		}
	}
	
	public void setVanish(boolean state){
		if(this.vanished != state){
			if(state){
				vanished = true;
				enterVanish = System.currentTimeMillis();
				
			} else {
				vanished = false;
				timeInVanish += (System.currentTimeMillis() - enterVanish);
				
			}
		} else if(vanished){
			timeInVanish += (System.currentTimeMillis() - enterVanish);
			enterVanish = System.currentTimeMillis();
			
		}
	}
	public boolean getVanish(){return vanished;}
	
	
	public void setCreativeOffAt(long time){
		if(creative){
			if(time > enterCreative)
				timeInCreative += (time - enterCreative);
			else
				StaffTracker.Log.warning("setCreativeOffAt() time occurs BEFORE user entered vanish!");
			
			creative = false;
			
		}
	}
	
	public void setCreative(boolean state){
		if(this.creative != state){
			if(state){
				creative = true;
				enterCreative = System.currentTimeMillis();
				
			} else {
				//Player has left creative mode.
				creative = false;
				timeInCreative += (System.currentTimeMillis() - enterCreative);
				
			}
		} else if(creative){
			timeInCreative += (System.currentTimeMillis() - enterCreative);
			enterCreative = System.currentTimeMillis();
			
		}
	}
	
	public boolean getCreative(){return creative;}
	
	public void setSocialSpyOffAt(long time){
		if(social){
			if(time > enterSocialSpy)
				timeInSocialSpy += (time - enterSocialSpy);
			else
				StaffTracker.Log.warning("setSocialSpyOffAt() time occurs BEFORE user entered Social Spy!");
			
			social = false;
			
		}
	}
	
	public void setSocialSpy(boolean state){
		if(this.social != state){
			if(state){
				social = true;
				enterSocialSpy = System.currentTimeMillis();
				
			} else {
				//Player has left creative mode.
				social = false;
				timeInSocialSpy += (System.currentTimeMillis() - enterSocialSpy);
				
			}
		} else if(social){
			timeInSocialSpy += (System.currentTimeMillis() - enterSocialSpy);
			enterSocialSpy = System.currentTimeMillis();
			
		}
	}

	public boolean getSocialSpy(){return social;}
	
	public long getTimeOnline(){return this.timeInGame;}
	public long getTimeInVanish(){return this.timeInVanish;}
	public long getTimeInCreative(){return this.timeInCreative;}
	public long getTimeInSocialSpy(){return this.timeInSocialSpy;}
	
	/** force an update of all the various times tracked by this record. */
	public void updateTime(){
		if(social){
			timeInSocialSpy += (System.currentTimeMillis() - enterSocialSpy);
			enterSocialSpy = System.currentTimeMillis();
			
		}
		
		if(creative){
			timeInCreative += (System.currentTimeMillis() - enterCreative);
			enterCreative = System.currentTimeMillis();
			
		}
		
		if(vanished){
			timeInVanish += (System.currentTimeMillis() - enterVanish);
			enterVanish = System.currentTimeMillis();
			
		}
		
		if(loggedIn){
			timeInGame += (System.currentTimeMillis() - loginTime);
			loginTime = System.currentTimeMillis();
			
		}
	}
	
	/** a nice simple method to turn a duration into a xhymzs readout*/
	public String toHMSFormat(long timein){
		String in = DurationFormatUtils.formatDuration(timein,"y:M:d:H:m:s");
	
		String out = "";
		String[] sp = in.split(":");
		String[] t = {"Y","M","D","h","m","s"};
		
		for(int i = 0;i < sp.length;i++)
			if(Long.parseLong(sp[i]) != 0)
				out += sp[i] + t[i];
		
		if(out == "")
			out = "0s";
		return out;
	}
	
	public String toString(){
		String out = "";
		
		out =
				"§2Name : §9" + this.name +
				"\n§2UUID : §9" + this.uuid.toString() +
				"\n§2Logged In : " + (this.loggedIn ? "§aYes" : "§cNo") +
				"\n§2Time Online Total : §9" + toHMSFormat(this.timeInGame) + " (" + this.timeInGame + ")" +
				"\n§2Is OP : §9" + (this.isOP ? "§aYes" : "§cNo") +
				"\n§2In Vanished : §9" + (vanished ? "§aYes" : "§cNo") +
				"\n§2Time In Vanish Total : §9" + toHMSFormat(this.timeInVanish) +
				"\n§2In Creative : §9" + (creative ? "§aYes" : "§cNo") +
				"\n§2Time In Creative Total : §9" + toHMSFormat(this.timeInCreative) +
				"\n§2Socialspy On : §9" + (social ? "§aYes" : "§cNo") +
				"\n§2Time In Socialspy Total : §9" + toHMSFormat(this.timeInSocialSpy) +
				"§r";
				
		return out;
				
	}
	
	public String toStringNice(){
		String out = "";
		
		out =	"§2Name : §9" + this.name +
				"\n§2Staff Group : §9" + this.group +
				"\n§2Is Online : §9" + (this.loggedIn ? "§aYes" : "§cNo") +
				"\n§2Is OP : §9" + (this.isOP ? "§aYes" : "§cNo") +
				"\n§2In Vanished : §9" + (vanished ? "§aYes" : "§cNo") +
				"\n§2In Creative : §9" + (creative ? "§aYes" : "§cNo") +
				"\n§2Socialspy On : §9" + (social ? "§aYes" : "§cNo") +
				"\n§2Time Online Total : §9" + toHMSFormat(this.timeInGame);
				
		if(this.timeInVanish < this.timeInGame){
			float perc = ((float)this.timeInVanish / (float)this.timeInGame) * 100.0f;
			out += "\n§2Time In Vanish Total : §9" + toHMSFormat(this.timeInVanish) + " (" + String.format("%.0f", perc) + "%)"; 
		} else {
			out += "\n§2Time In Vanish Total : §9" + toHMSFormat(this.timeInVanish);
		}
		
		if(this.timeInCreative < this.timeInGame){
			float perc = ((float)this.timeInCreative / (float)this.timeInGame) * 100.0f;
			out += "\n§2Time In Creative Total : §9" + toHMSFormat(this.timeInCreative) + " (" + String.format("%.0f", perc) + "%)"; 
		} else {
			out += "\n§2Time In Creative Total : §9" + toHMSFormat(this.timeInCreative);
		}
		
		if(this.timeInSocialSpy < this.timeInGame){
			float perc = ((float)this.timeInSocialSpy / (float)this.timeInGame) * 100.0f;
			out += "\n§2Time In SocialSpy Total : §9" + toHMSFormat(this.timeInSocialSpy) + " (" + String.format("%.0f", perc) + "%)"; 
		} else {
			out += "\n§2Time In SocialSpy Total : §9" + toHMSFormat(this.timeInSocialSpy);
		}
		
		out += "§r";
				
		return out;
				
	}
	
}
