package com.directorymonitor.listener;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class ConnectionHandler implements ServletContextListener {

	Connection con;
	
	public void contextDestroyed(ServletContextEvent sce)  { 
        try {
        	if(con != null)
			con.close();
		} catch (SQLException e) {
			System.out.println("ERROR: Not able to close Database connection.");
		}
    }

    public void contextInitialized(ServletContextEvent sce)  { 
    	connection(sce);
    }
    
    public void connection(ServletContextEvent sce){
        try {
            Class.forName("com.mysql.jdbc.Driver");
            System.out.println("SUCCESS: Driver loaded successfully");
        } catch (ClassNotFoundException ex) {
          System.out.println("ERROR: SQLDriver not loaded successfully.");
        } 
        try {
        	con = DriverManager.getConnection("jdbc:mysql://localhost:3307/directorymonitor?useSSL=false&requireSSL=false","root","root");
        	sce.getServletContext().setAttribute("datacon",con);
            System.out.println("SUCCESS: Database connected successfully.");
        } catch (SQLException ex) {
        	ex.printStackTrace();
           System.out.println("ERROR: Connection establishment failed.");
        }
    }
}
