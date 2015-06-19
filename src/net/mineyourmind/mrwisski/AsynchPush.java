package net.mineyourmind.mrwisski;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;


public class AsynchPush implements Runnable {

	private SQLDB sqldb;
	private List<StaffRecord> rl = new ArrayList<StaffRecord>();
	private String tableName;
	public boolean failure = false;
	public boolean state = false;
	private BukkitScheduler bs;
	
	
	//For efficiencies sake, this flag sets if we're running in synchronous mode - IE
	//we have to push an update in the main server thread due to the server shutting down.
	private boolean runSynch = false;
	
	//List of staffnames that need to be removed from staffInd IF we succeed.
	public List<String> removelist = new ArrayList<String>();	

	//To use, create the class, and construct it normally...and then .run(). should push to DB
	//in the main thread.
	public void runSynch(){
		runSynch = true;
	}
	
	private void callHome(final String msg){
		if(!runSynch){
			BukkitRunnable r = new BukkitRunnable() {
				String m = msg;
				@Override
				public void run() {
					if(StaffTracker.instance.debug) StaffTracker.Log.info(m);
				}
			};

			bs.scheduleSyncDelayedTask(StaffTracker.instance, r, 0);
		} else {
			if(StaffTracker.instance.debug) StaffTracker.Log.info(msg);
		}
	}

	AsynchPush(SQLDB db, BukkitScheduler sched, List<StaffRecord> rlist, String table, List<String> RemoveList){
		sqldb = db;
		bs = sched;
		//make a local copy of the list.
		for(StaffRecord r : rlist){
			rl.add(r);
		}
		tableName = table;
		//and another local copy
		for(String s : RemoveList){
			removelist.add(s);
		}
		
	}
	
	private boolean recordExists(String name){
		Statement s = null;
		ResultSet rs = null;
		boolean ret = false;
		try{
			s = sqldb.getConnection().createStatement();
			rs = s.executeQuery("select * from " + tableName + " where name = '" + name + "'");
			if(rs.next()){
				this.callHome("SYNCH DEBUG : Record exists for "+ name +"!");
				ret = true;
			} else {
				this.callHome("SYNCH DEBUG : Record does not exist for "+ name + "!");
				ret = false;
			}
			rs.close();
			s.close();
			return ret;
		} catch (SQLException e){
			this.callHome("SYNCH DEBUG : Exception in recordexists - " + e.getMessage());
			return false;
		}
	}
	
	@Override
	public void run() {
		Savepoint JustInCase = null;
		this.callHome("SYNCH DEBUG : Starting Database Asynch Update. Have " + rl.size() + " records to update!");
		if(!sqldb.isConnected()){
			if(!sqldb.connect()){
				this.failure = true;
				this.callHome("SYNCH DEBUG : Couldn't connect to DB.");
				return;
			}
		}
		
		try{
			//start our transaction
			sqldb.getConnection().setAutoCommit(false);
			//set our save point
			JustInCase = sqldb.getConnection().setSavepoint();
			
			//iterate through all the pending
			for(StaffRecord r:rl){
				if(!push(r)){
					this.failure = true;
					this.callHome("SYNCH DEBUG : Failed to push(r)");
					break;
				} 
			}
		} catch(Exception e) {
			this.state = false;
			this.failure = true;
			this.callHome("SYNCH DEBUG : Exception push(r)ing - " + e.getMessage());
		}
		
		if(this.failure){
			//We'll try again later. might even succeed.
			this.callHome("SYNCH DEBUG : this.failure = true :(");
			this.state = false;
			try {
				if(JustInCase != null){
					sqldb.getConnection().rollback(JustInCase);
					sqldb.getConnection().setAutoCommit(true);
					this.callHome("SYNCH DEBUG : Rolled back transaction.");
				}
			} catch (SQLException ignored) {}
		} else {
			//We succeeded. so far. yay us.
			this.state = true;
			this.callHome("SYNCH DEBUG : this.failure = false! yay!");
			try {
				sqldb.getConnection().commit();
				this.callHome("SYNCH DEBUG : Commited transaction.");
			} catch (SQLException e) {
				try {
					this.callHome("SYNCH DEBUG : Exception during commit - " + e.getMessage());
					if(JustInCase != null){
						sqldb.getConnection().rollback(JustInCase);
						sqldb.getConnection().setAutoCommit(true);
						this.callHome("SYNCH DEBUG : Rolled back transaction");
					}
				} catch (SQLException ignored) {}
				//We failed to commit so...rollback, fail, and try later.
				this.state = false;
				this.failure = true;
				this.callHome("SYNCH DEBUG : Synch push failed.");
			}
		}
		
		//finally, set autocommit back to true
		try {
			sqldb.getConnection().setAutoCommit(true);
		} catch (SQLException e) {}
		
		if(state && !failure){
			this.callHome("SYNCH DEBUG : Finished Database Asynch Update Successfully");
		} else {
			this.callHome("SYNCH DEBUG : Finished Database Asynch Update With Failures. :<");
		}
		
		StaffTracker.dbPushActive = false;
	}
	
	private boolean push(StaffRecord r){
		PreparedStatement update = null;
		try{

			if(this.recordExists(r.getName())) {
				update = sqldb.getConnection().prepareStatement("UPDATE " + tableName +
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
								" PGROUP = ?, " + 
								" COMMANDCSV = ? " +
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
				update.setString(12, r.commandToString());
				update.setString(13, r.getName());
				update.executeUpdate();	
			} else{
				update = sqldb.getConnection().prepareStatement(
						"INSERT INTO " + tableName + "("+
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
								"TIMESOCIALSPY, "+
								"COMMANDCSV) " + 
						"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
				update.setString(14, r.commandToString());
				update.executeUpdate();
			}
		} catch (SQLException e) {
			this.callHome("SYNCH DEBUG : Exception in push for " + r.getName() + " - " + e.getMessage());
			//Log.severe("Failed AsynchPush to DB T_T");
			return false;
		} finally {
			try{
				update.close();
				this.callHome("SYNCH DEBUG : update for " + r.getName() + " pushed!");
			} catch (SQLException e) {
				this.callHome("SYNCH DEBUG : Exception closing update - " + e.getMessage());
				//Log.severe("Failed ASynchPush closing the update T_T");
				return false;
				
			}
		}
		return true;
	}

}
