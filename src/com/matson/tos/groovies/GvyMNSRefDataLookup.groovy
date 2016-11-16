import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import javax.naming.Context;
import javax.naming.InitialContext;

/**
 *  A1 SKB 05/11/2009  Added POD lookup
 *  A2 SKB 06/25/2009  Removed connection to prevent memory leak
 *  A3 GR  10/21/10    Added Chassis RFID WSDL lookup Code
 Added Env Spec IP lookup code for JMS Server Connectivity
 *  A4 GR  08/19/11    Added ActiveMQ InitiaContextFactory Lookup
 */
class GvyMNSRefDataLookup {


    public Connection connect() {

        // Obtain our environment naming context
        Context initCtx = new InitialContext();
        Context envCtx = (Context) initCtx.lookup("java:comp/env");

        // Look up our data source
        DataSource ds = (DataSource)envCtx.lookup("jdbc.ds.tdp.nontx");

        // Allocate and use a connection from the pool
        Connection conn = ds.getConnection();
        return conn;

    }

    public void disconnect(Connection conn) {
        if(conn != null) conn.close();
    }

    public String lookup(String key, Connection conn) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            String sql = "select value from TOS_APP_PARAMETER where key = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1,key);
            //rs = stmt.executeQuery();
            //if(rs.next()) {
            //	  return rs.getString(1);
            // }

        } finally {
            if(stmt != null) stmt.close();
            //if(rs != null )rs.close();
        }
        return null;
    }



// Code for getNowServer
    public static String host;
    public static boolean dynamic = true;

    public static synchronized void initName() {
        GvyMNSRefDataLookup lookup = new GvyMNSRefDataLookup();
        Connection conn;
        try {
            conn = lookup.connect();
            host = lookup.lookup("NOW_URL", conn);

            String dynamicString =  lookup.lookup("NOW_DYNAMIC",conn);
            dynamic = Boolean.parseBoolean(dynamicString);
            println("DEBUG UPDT getNowServer "+host);

        } finally {
            lookup.disconnect(conn);
        }
        //println("initNowServer")
    }

    public getNowServer() {

        if(dynamic == true) {
            initName();
        }
        return host;

    }

    //A3
    public static String chasRfidHost;
    public static String n4TopicHost;
    public static boolean chasRfidDynamic = true;

    public static synchronized void initChasRfid() {
        GvyMNSRefDataLookup lookup = new GvyMNSRefDataLookup();
        Connection conn;
        try {
            conn = lookup.connect();
            chasRfidHost = lookup.lookup("CHAS_RFID_NOW_URL", conn);

            String dynamicChasStr =  lookup.lookup("NOW_DYNAMIC",conn);
            chasRfidDynamic = Boolean.parseBoolean(dynamicChasStr);
            println("DEBUG UPDT getNowServer "+chasRfidHost);

        } finally {
            lookup.disconnect(conn);
        }
        //println("initNowServer")
    }

    public getChasRfidUrl() {

        if(chasRfidDynamic == true) {
            initChasRfid();
        }
        //println("Called getChasRfidUrl");
        return chasRfidHost;

    }


    public static synchronized String getN4TopicAddress() {
        GvyMNSRefDataLookup lookup = new GvyMNSRefDataLookup();
        Connection conn;
        try {
            conn = lookup.connect();
            n4TopicHost = lookup.lookup("JMS_URL", conn);

        } finally {
            lookup.disconnect(conn);
        }
        return n4TopicHost
    }

    //A3 Ends
}