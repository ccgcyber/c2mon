/**
 * Copyright (c) 2015 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.c2mon.publisher.mobicall;

import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Load/reload mobicall linked alarm definitions from the database directly (for production use). The configuration
 * of this class requires a file database.properties at the root of the classpath. This file should contain
 * all properties for the database connection, except the password. To avoid presence in SVN, a hidden file
 * with the password property must be created in the working directory of the app.
 * 
 * @author mbuttner
 */
public class MobicallConfigLoaderDB implements MobicallConfigLoaderIntf {

    private static MobicallConfigLoaderDB loader;
    
    static final Logger LOG = LoggerFactory.getLogger(MobicallAlarm.class);
    
    private static final String DBPWD = ".dbpwd.properties";
    private static final String DBCONN = "/database.properties";
    
    private ConcurrentHashMap<String, MobicallAlarm> alarms;
    private Connection conn;

    //
    // --- CONSTRUCTION ------------------------------------------------------------------------
    //
    private MobicallConfigLoaderDB() throws Exception {
        alarms = new ConcurrentHashMap<String, MobicallAlarm>();
            
        Properties dbProps = new Properties();
        try (FileInputStream fisPwd = new FileInputStream(DBPWD)) {
            System.setProperty("oracle.net.tns_admin", "/etc");
            dbProps.load(this.getClass().getResourceAsStream(DBCONN));
                
            // show configuration in LOG without password!
            LOG.info(dbProps.toString());
            dbProps.load(fisPwd);
                    
            Class.forName(dbProps.getProperty("alarm.jdbc.driverClass"));   
            conn = DriverManager.getConnection(
                    dbProps.getProperty("alarm.jdbc.url"),
                    dbProps.getProperty("alarm.jdbc.user"),
                    dbProps.getProperty("alarm.jdbc.password"));
        } catch (Exception e) {
            throw e;
        }
    }
    
    public static MobicallConfigLoaderIntf getLoader() throws Exception {
        if (loader == null) {
            loader = new MobicallConfigLoaderDB();
        }
        return loader;
    }

    //
    // --- Implements MobicallConfigLoaderIntf -----------------------------------------------------------
    // 
    /**
     * Let clients check if a given alarm is in the map of alarms with Mobicall reference
     * @param alarmId <code>String</code> the triplet identifying the alarm
     * @return <code>MobicallAlarm</code> object matching the alarmId, or null if not found
     */
    @Override
    public synchronized MobicallAlarm find(String alarmId) {
        synchronized(alarms) {
            return alarms.get(alarmId);
        }
    }
    
    @Override
    public void close() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                LOG.warn("Failed to close DB connection", e);
            }
        }
    }
    
    /**
     * Try to connect to database and retrieve the latest set of Mobicall alarm definitions.
     */
    @Override
    public void loadConfig() {
        
        final String query = "select " +
                " alarm_id, " +
                " system_name, " +
                " identifier, " +
                " fault_code, " +
                " notification_id, " +
                " problem_Description " +
                "from " +
                " alarm_definitions_v " +
                "where  " +
                " notification_id is not null " +
                "order by 1";
        
        try (
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)
                ) {
            
            ConcurrentHashMap<String, MobicallAlarm> tmpAlarms = new ConcurrentHashMap<String, MobicallAlarm>();
            while (rs.next()) {
                MobicallAlarm ma = new MobicallAlarm(rs.getString("alarm_id"));
                ma.setSystemName(rs.getString("system_name"));
                ma.setIdentifier(rs.getString("identifier"));
                ma.setFaultCode(rs.getInt("fault_code"));
                ma.setNotificationId(rs.getString("notification_id"));
                ma.setProblemDescription(rs.getString("problem_description"));
                tmpAlarms.put(ma.getAlarmId(), ma);
                LOG.debug("Added {} to configuration", ma.getAlarmId());
            }            
            synchronized(alarms) {
                alarms = tmpAlarms;
                LOG.info("Size of Mobicall alarm map: " + alarms.size());
            }
        } catch (Exception e) {
            LOG.warn("Failed to reload the mobicall alarm definitions", e);
        }                    
    }

    
}