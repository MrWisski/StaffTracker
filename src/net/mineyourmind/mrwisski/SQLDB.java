package net.mineyourmind.mrwisski;

import java.sql.*;
import java.util.logging.Logger;


/** SQL Database Access Class for Minecraft Bukkit
 * 
 * @author MrWisski
 * @version 0.1.0a - 11 May 2015
 * */
public class SQLDB {
	//our logger instance.
	private Logger log;

	//various connection related variables
	private Connection conn = null;				// our connection
	private String address;						// IP Address
	private int port;							// Port
	private String user;						// User Name for DB
	private String pass;						// Password for DB
	private String driver;						// Requested Driver
	private String database = "";				// Database we'll be using
	private String url;							// URL for the connection.

	//various SQL related variables
	DatabaseMetaData metadata;					// Meta for the DB
	private Statement statement = null;
	//private PreparedStatement preparedStatement = null;
	private ResultSet resultSet = null;


	public boolean connected = false;

	public void createTable(String sqltablegen){

	    try {
			statement = conn.createStatement();
			statement.execute(sqltablegen);
			statement.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	    
	}
	
	/**Execute a query on the database
	 * 
	 *  USE CAUTION USING THIS FUNCTION! Executing unsanitized queries on the database
	 *  is STUPID and DANGEROUS. DO NOT, under ANY CIRCUMSTANCE, allow outside user-supplied
	 *  data to be incorporated into query!
	 *  
	 *  @param String query - a SANITIZED and SAFE query to execute on the database.
	 * 
	 * */
	public ResultSet query(String query){
		try {
			statement = conn.createStatement();
			
			resultSet = statement.executeQuery(query);
			
			statement.close();
			return resultSet;
			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	      // Result set get the result of the SQL query
	     
		
		return null;
	}
	
	public boolean isConnected(){

		try {
			if(conn.isValid(2)){
				// It is! hurrah!
				this.connected = true;
				return true;
			} else {
				// buuuuu! we're no longer connected! 
				this.connected = false;
				return false;
			}
		} catch (SQLException e) {
			// again, only throws if timeout is less than 0. never get here.
			return false;
		}

	}



	/** Constructor for this class - Should be pretty self explanatory */
	public SQLDB(String SQLAddress, String SQLPort, String SQLUser, String SQLPassword,String SQLDriver,String SQLDatabase){
		log = StaffTracker.Log;

		address = SQLAddress;
		try {
			port = Integer.parseInt(SQLPort);
		} catch (NumberFormatException e) {
			log.severe("SQLDB - Could not convert " + SQLPort + " to a number, using default 3306 port.");
			port = 3306;
		}

		user = SQLUser;
		pass = SQLPassword;
		driver = SQLDriver;
		database = SQLDatabase;
		url = "jdbc:mysql://" + address + ":" + port + "/" + database;
		this.connected = false;
	}

	public boolean connect(){
		//Driver drv = null;
		//drv = DriverManager.getDriver(url);
		//DriverManager.registerDriver(drv);

		//Make sure the jdbc driver is initialized.
		try{
			Class.forName(driver);
		} catch(ClassNotFoundException e) {
			log.severe("SQLDB - Could not initialize the JDBC driver named " + driver + "! Cannot connect!!");
			return false;
		}

		//Try to actually connect to the DB in question
		try{
			conn = DriverManager.getConnection(url,user,pass);
		} catch(SQLException e) {
			log.severe("SQLDB - Error getting connection to server! Cannot connect!");
			return false;
		}


		//Validate the connection, and return the result!
		try {
			if(conn.isValid(2)){
				metadata = conn.getMetaData();
				this.connected = true;
				return true;
			} else {
				this.connected = false;
				return false;
			}
		} catch (SQLException e) {
			//Throws exception if the timeout is less than 0...we'll never get here
			this.connected = false;
			return false;
		}
	}

	public Connection getConnection(){
		return conn;
	}

	public void close() {
		try {

			if (resultSet != null) 
				resultSet.close();

			if (statement != null) 
				statement.close();

			if (conn != null)
				conn.close();

		} catch (Exception e) {

		}
	}

	public void switchDatabase(String database){
		
		if(!isConnected()){
			if(!this.connect()){
				this.connected = false;
				this.log.severe("Could not establish a connection to the SQL Server to switch databases!");
				return;
			}
		}
		
			try {
				conn.setCatalog(database);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
			
			this.database = database;
		
	}

}
