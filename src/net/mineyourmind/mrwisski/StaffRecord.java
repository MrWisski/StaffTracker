package net.mineyourmind.mrwisski;

import java.util.UUID;

import org.apache.commons.lang.time.DurationFormatUtils;



/** Class containing all the data about a particular staff member we track! */
public class StaffRecord {
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
	private long timeIn = 0;

	// Has the record changed since the last SQL out?
	private boolean dirty = true;
	// If this record is currently being pushed to the SQL - 
	// used externally to avoid nasty things like trying to get
	// the status of an offline player.
	private boolean isCommiting = false;
	
	private long enterVanish = 0;
	private long enterCreative = 0;
	private long enterSocialSpy = 0;
	private long loginTime = 0;
	
	StaffRecord(UUID id, String name, String server, String group, boolean vanished, boolean creativemode, boolean socialspy){
		this.uuid = id;
		this.name = name;
		this.group = group;
		this.server = server;
		setLoggedIn(true);
		setVanish(vanished);
		setCreative(creativemode);
		setSocialSpy(socialspy);
	}
	
	//////////////////////////////////
	//SETTERS AND GETTERS - Pretty standard stuff here
	//WARNING : You are entering a NO COMMENT ZONE ^_^
	//////////////////////////////////
	
	public String getGroup(){return group;}
	public String getName(){return name;}
	public String getServer(){return server;}
	public UUID getUUID(){return uuid;}
	
	public void setOp(boolean op){
		this.isOP = op;
		dirty = true;
	}
	public boolean getOp(){
		return this.isOP;
	}
	
	public void setCommitting(boolean status){ this.isCommiting = status;}
	public boolean getCommitting(){return this.isCommiting;}
	
	public boolean getDirty(){return dirty;}
	
	public boolean getLoggedIn(){return loggedIn;}
	public void setLoggedIn(boolean state){
		if(loggedIn != state){
			if(state){
				loggedIn = true;
				loginTime = System.currentTimeMillis();
				dirty = true;
			} else {
				loggedIn = false;
				timeIn += (System.currentTimeMillis() - loginTime);
				dirty = true;
			}
		} else if (loggedIn){
			timeIn += (System.currentTimeMillis() - loginTime);
			loginTime = System.currentTimeMillis();
			dirty = true;
		}
	}
	
	public void setLogOffAt(long time){
		if(loggedIn){
			if(time > loginTime)
				timeIn += (time - loginTime);
			else
				StaffTracker.Log.warning("setLogOffAt() time occurs BEFORE user logged in!");
			
			loggedIn = false;
			dirty = true;
		}
	}
	
	public void setVanishOffAt(long time){
		if(vanished){
			if(time > enterVanish)
				timeInVanish += (time - enterVanish);
			else
				StaffTracker.Log.warning("setVanishOffAt() time occurs BEFORE user entered vanish!");
			
			vanished = false;
			dirty = true;
		}
	}
	
	public void setVanish(boolean state){
		if(this.vanished != state){
			if(state){
				vanished = true;
				enterVanish = System.currentTimeMillis();
				dirty = true;
			} else {
				vanished = false;
				timeInVanish += (System.currentTimeMillis() - enterVanish);
				dirty = true;
			}
		} else if(vanished){
			timeInVanish += (System.currentTimeMillis() - enterVanish);
			enterVanish = System.currentTimeMillis();
			dirty = true;
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
			dirty = true;
		}
	}
	
	public void setCreative(boolean state){
		if(this.creative != state){
			if(state){
				creative = true;
				enterCreative = System.currentTimeMillis();
				dirty = true;
			} else {
				//Player has left creative mode.
				creative = false;
				timeInCreative += (System.currentTimeMillis() - enterCreative);
				dirty = true;
			}
		} else if(creative){
			timeInCreative += (System.currentTimeMillis() - enterCreative);
			enterCreative = System.currentTimeMillis();
			dirty = true;
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
			dirty = true;
		}
	}
	
	public void setSocialSpy(boolean state){
		if(this.social != state){
			if(state){
				social = true;
				enterSocialSpy = System.currentTimeMillis();
				dirty = true;
			} else {
				//Player has left creative mode.
				social = false;
				timeInSocialSpy += (System.currentTimeMillis() - enterSocialSpy);
				dirty = true;
			}
		} else if(social){
			timeInSocialSpy += (System.currentTimeMillis() - enterSocialSpy);
			enterSocialSpy = System.currentTimeMillis();
			dirty = true;
		}
	}

	public boolean getSocialSpy(){return social;}
	
	/** force an update of all the various times tracked by this record. */
	public void updateTime(){
		if(social){
			timeInSocialSpy += (System.currentTimeMillis() - enterSocialSpy);
			enterSocialSpy = System.currentTimeMillis();
			dirty = true;
		}
		
		if(creative){
			timeInCreative += (System.currentTimeMillis() - enterCreative);
			enterCreative = System.currentTimeMillis();
			dirty = true;
		}
		
		if(vanished){
			timeInVanish += (System.currentTimeMillis() - enterVanish);
			enterVanish = System.currentTimeMillis();
			dirty = true;
		}
		
		if(loggedIn){
			timeIn += (System.currentTimeMillis() - loginTime);
			loginTime = System.currentTimeMillis();
			dirty = true;
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
				"\n§2Time Online Total : §9" + toHMSFormat(this.timeIn) +
				"\n§2Is OP : §9" + (this.isOP ? "§aYes" : "No") +
				"\n§2In Vanished : §9" + (vanished ? "§aYes" : "§cNo") +
				"\n§2Time In Vanish Total : §9" + toHMSFormat(this.timeInVanish) +
				"\n§2In Creative : §9" + (creative ? "§aYes" : "§cNo") +
				"\n§2Time In Creative Total : §9" + toHMSFormat(this.timeInCreative) +
				"\n§2Socialspy On : §9" + (social ? "§aYes" : "§cNo") +
				"\n§2Time In Socialspy Total : §9" + toHMSFormat(this.timeInSocialSpy) +
				"\n§2Record Dirty : §9" + (dirty ? "§aYes" : "§cNo") +
				"§r";
				
		return out;
				
	}
	
}
