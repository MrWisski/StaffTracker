package net.mineyourmind.mrwisski;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public class ASynchPush implements Runnable {

	private SQLDB sqldb;
	private StaffRecord r;
	private String tableName;
	private boolean debug = false;
	private Logger Log = null;
	
	ASynchPush(SQLDB db, StaffRecord r, String table, boolean debug, Logger log){
		sqldb = db;
		this.r = r;
		tableName = table;
		this.debug = debug;
		Log = log;
	}
	
	private boolean recordExists(String name){
		if(debug) Log.info("DEBUG : recordExists()");
		Statement s = null;
		ResultSet rs = null;
		boolean ret = false;
		try{
			s = sqldb.getConnection().createStatement();
			rs = s.executeQuery("select * from " + tableName + " where name = '" + name + "'");
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
	
	@Override
	public void run() {
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
			//Log.severe("Failed AsynchPush to DB T_T");
		} finally {
			try{
				update.close();
			} catch (SQLException e) {
				//Log.severe("Failed ASynchPush closing the update T_T");
				
			}
		}

		//Log.info("Asynch Push completed without error.");
	}

}
