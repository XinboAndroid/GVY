import com.navis.argo.ContextHelper

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import javax.naming.Context;
import javax.naming.InitialContext;
import com.navis.argo.business.model.Facility;

import com.navis.argo.business.model.GeneralReference
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.api.client.config.ClientConfig
import com.sun.jersey.api.client.config.DefaultClientConfig
import javax.mail.internet.MimeMessage
import javax.ws.rs.core.MediaType
import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.inventory.business.units.*;
import com.navis.argo.business.reference.*;

/**
 *  A1 SKB 05/11/2009  Added POD lookup
 *  A2 SKB 06/25/2009  Removed connection to prevent memory leak
 *  A3 GR  10/21/10    Added Chassis RFID WSDL lookup Code
 Added Env Spec IP lookup code for JMS Server Connectivity
 *  A4 GR  08/19/11    Added ActiveMQ InitiaContextFactory Lookup
 */
class GvyRefDataLookup {


    public Connection connect() {
        //String datasource = "jdbc.ds.tdp.nontx";

        /* Context ctx = null;
         Hashtable ht = new Hashtable();
         ht.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
         ctx = new InitialContext(ht);
         javax.sql.DataSource ds  = (javax.sql.DataSource) ctx.lookup (datasource);
         Connection conn = ds.getConnection();
         return conn; */


        // Obtain our environment naming context
        Context initCtx = new InitialContext();
        Context envCtx = (Context) initCtx.lookup("java:comp/env");

        // Look up our data source
        DataSource ds = (DataSource)envCtx.lookup("jdbc.ds.tdp.nontx");

        // Allocate and use a connection from the pool
        Connection conn = ds.getConnection();
        return conn;

    }

    public Connection connectTos() {

        // Obtain our environment naming context
        Context initCtx = new InitialContext();
        Context envCtx = (Context) initCtx.lookup("java:comp/env");

        // Look up our data source
        DataSource ds = (DataSource)envCtx.lookup("jdbc/apexDS");

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
            println("JMS_URL Query::"+sql);
            stmt = conn.prepareStatement(sql);
            stmt.setString(1,key);
            rs = stmt.executeQuery();
            if(rs.next()) {
                return rs.getString(1);
                println("JMS_URL Query return ::"+rs.getString(1));
            }

        } finally {
            if(stmt != null) stmt.close();
            if(rs != null )rs.close();
        }
        return null;
    }

    public String lookupPod(String dest) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Connection conn;
        def result = dest;
        try {
            conn = connect();
            String sql = "select pod1_id from TOS_DEST_POD_LOOKUP l where l.dest_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1,dest);
            rs = stmt.executeQuery();
            if(rs.next()) {
                result =  rs.getString(1);
                if(result == null) result = dest;
            }

        } finally {
            if(stmt != null) stmt.close();
            if(rs != null ) rs.close();
            disconnect(conn);
            conn = null;
        }

        return result;

    }


// Code for getNowServer
    public static String host;
    public static boolean dynamic = true;

    public static synchronized void initName() {
        GvyRefDataLookup lookup = new GvyRefDataLookup();
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
        GvyRefDataLookup lookup = new GvyRefDataLookup();
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
        //GvyRefDataLookup lookup = new GvyRefDataLookup();
        //Connection conn;
        try {
            //conn = lookup.connect();
            //n4TopicHost = lookup.lookup("JMS_URL", conn);
            Facility facility = Facility.findFacility(ContextHelper.getThreadFacility().getFcyId());
            n4TopicHost = facility.fcyJmsConnection.jmsProviderUrl;
            println("n4TopicHost from Topology :::"+n4TopicHost);
        } catch (Exception e){
            println("Error while getting JMS_URL from topology ::"+e);
        }
        return n4TopicHost
    }

    //A3 Ends

    public insertTdpLogData(String vesvoy,String processType, String startDate, String endDate, String status, Connection conn) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        GvyRefDataLookup lookup = new GvyRefDataLookup();
        try
        {

            try {

                String sql = "INSERT INTO TOS_PROCESS_LOGGER_MT (id,vesvoy,processtype,starttime,endtime,status) VALUES (tos_process_logger_mt_seq.NEXTVAL,?,?,TO_DATE(?,'MM/DD/YYYY HH24:MI:SS'),TO_DATE(?,'MM/DD/YYYY HH24:MI:SS'),?)";
                println("sql  "+sql);
                stmt = conn.prepareStatement(sql);
                stmt.setString(1,vesvoy);
                stmt.setString(2,processType);
                stmt.setString(3,startDate);
                stmt.setString(4,endDate);
                stmt.setString(5,status);
                stmt.executeQuery();
            } finally {
                if(stmt != null) stmt.close();
                lookup.disconnect(conn);
            }
        }
        catch (qe)
        {
            println("MatInsertClientVesselRecInTDP query error "+qe);
        }
        return null;
    }

    public deleteGenRefData(String Type, String vesvoy,Connection conn) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        GvyRefDataLookup lookup = new GvyRefDataLookup();
        try
        {

            try {

                String sql = "DELETE FROM TOSMGR.ARGO_GENERAL_REFERENCE WHERE ref_type = ? and id1 = ?";
                println("sql  "+sql);
                stmt = conn.prepareStatement(sql);
                stmt.setString(1,Type);
                stmt.setString(2,vesvoy);
                println(" stmt :: "+stmt);
                stmt.executeQuery();
            } finally {
                if(stmt != null) stmt.close();
                lookup.disconnect(conn);
            }
        }
        catch (qe)
        {
            println("deleteGenRefData query error "+qe);
        }
        return null;
    }

    public calcelPermision(String unit, String permisionKey,Connection conn) {
        PreparedStatement stmtQuery1 = null;
        PreparedStatement stmtQuery2 = null;

        GvyRefDataLookup lookup = new GvyRefDataLookup();
        try
        {

            try {

                String sql1 = "DELETE FROM TOSMGR.SRV_VETOS WHERE APPLIED_TO_NATURAL_KEY = ? and BLOCKED_FLAG_GKEY IN (SELECT GKEY FROM SRV_FLAGS WHERE APPLIED_TO_NATURAL_KEY  = ? AND FLAG_TYPE_GKEY = ?)";
                println("sql1  "+sql1);
                stmtQuery1 = conn.prepareStatement(sql1);
                stmtQuery1.setString(1,unit);
                stmtQuery1.setString(2,unit);
                stmtQuery1.setString(3,permisionKey);
                println(" stmt :: "+stmtQuery1);
                stmtQuery1.executeQuery();

                String sql2 = "DELETE FROM SRV_FLAGS WHERE APPLIED_TO_NATURAL_KEY  = ? AND FLAG_TYPE_GKEY = ?";
                println("sql2  "+sql2);
                stmtQuery2 = conn.prepareStatement(sql2);
                stmtQuery2.setString(1,unit);
                stmtQuery2.setString(2,permisionKey);
                println(" stmt :: "+stmtQuery2);
                stmtQuery2.executeQuery();
            } finally {
                if(stmtQuery2 != null) stmtQuery2.close();
                lookup.disconnect(conn);
            }

        }
        catch (qe)
        {
            println("deleteGenRefData query error "+qe);
        }
        return null;
    }

    public insertDiscrepancy(String vesVoy, String value,String unitId,String typeCode,String sealNbr,String tempReq,String grossWt,String tareWt,String unitVesVoy,Connection conn) {
        PreparedStatement stmt = null;
        PreparedStatement checkStmt = null;
        ResultSet rs = null;
        GvyRefDataLookup lookup = new GvyRefDataLookup();
        try
        {

            try {
                String checkData = "SELECT * FROM TOSCSTMMGR.TOS_CLIENT_VES_DISCREPANCY_MT WHERE VESVOY = ? AND VALUE = ? AND CONTAINER_ID = ?";
                println("checkData:::"+checkData);
                checkStmt = conn.prepareStatement(checkData);
                checkStmt.setString(1,vesVoy);
                checkStmt.setString(2,value);
                checkStmt.setString(3,unitId);
                rs = checkStmt.executeQuery();
                if(rs.next()) {
                    println("Resultset :: "+ rs);
                    println(rs.getString("CONTAINER_ID") + " Already exists in the table");
                    rs.close();
                    checkStmt.close()
                }
                else {
                    String sql = "INSERT INTO TOSCSTMMGR.TOS_CLIENT_VES_DISCREPANCY_MT VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    println("sqlQuery  "+sql);

                    stmt = conn.prepareStatement(sql);
                    stmt.setString(1,vesVoy);
                    stmt.setString(2,value);
                    stmt.setString(3,unitId);
                    stmt.setString(4,typeCode);
                    stmt.setString(5,sealNbr);
                    stmt.setString(6,tempReq);
                    stmt.setString(7,grossWt);
                    stmt.setString(8,tareWt);
                    stmt.setString(9,unitVesVoy);

                    println(" stmt :: "+stmt);
                    stmt.executeQuery();
                }
            } finally {
                if(stmt != null) stmt.close();
                lookup.disconnect(conn);
            }
        }
        catch (qe)
        {
            println("deleteGenRefData query error "+qe);
        }
        return null;
    }

    public ResultSet getDiscrepancyData(String vesVoy, Connection conn) {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            String sql = "SELECT * FROM TOSCSTMMGR.TOS_CLIENT_VES_DISCREPANCY_MT WHERE VESVOY = ? ORDER BY CONTAINER_ID, VALUE";
            println("SQL :::"+sql);
            stmt = conn.prepareStatement(sql);
            stmt.setString(1,vesVoy);
            rs = stmt.executeQuery();
        } catch(Exception e) {
            println("Excpetion in the query :::: "+ e);
        }
        return rs;
    }

    public delDiscrepancyData(String vesVoy, Connection conn) {
        PreparedStatement stmt = null;
        try {
            String sql = "DELETE FROM TOSCSTMMGR.TOS_CLIENT_VES_DISCREPANCY_MT WHERE VESVOY = ?"
            println("SQL :::"+sql);
            stmt = conn.prepareStatement(sql);
            stmt.setString(1,vesVoy);
            stmt.executeQuery();
        } catch(Exception e) {
            println("Excpetion in the query :::: "+ e);
        }
    }

    public String lookupNv1(String vesvoy, Connection conn) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        //Connection conn;
        def cnt = 0;
        try {
            //conn = connect();
            String sql = "select count(*) from tos_process_logger_mt l where l.vesvoy = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1,vesvoy);
            rs = stmt.executeQuery();

            if(rs.next()) {
                cnt =  rs.getString(1);
                if(cnt == null) cnt = 0;
            }

        } finally {
            if(stmt != null) stmt.close();
            if(rs != null ) rs.close();
        }
        return cnt;
    }

    public synchronized String lookupNv(String vesVoy) {
        GvyRefDataLookup lookup = new GvyRefDataLookup();
        Connection conn;
        def vesNvCnt = 0;
        try {
            conn = lookup.connect();
            vesNvCnt = lookup.lookupNv1(vesVoy, conn);
            println("nvCnt from TDP      :::"+vesNvCnt);
        } finally {
            lookup.disconnect(conn);
        }
        return vesNvCnt
    }

    public String lookupPodForAK(String dest) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Connection conn;
        def result = null;
        try {
            conn = connect();
            String sql = "select pod1_id from TOS_DEST_POD_LOOKUP l where l.dest_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1,dest);
            rs = stmt.executeQuery();
            if(rs.next()) {
                println("going inside");
                result =  rs.getString(1);

            }

        } finally {
            if(stmt != null) stmt.close();
            if(rs != null ) rs.close();
            disconnect(conn);
            conn = null;
        }
        println("db pod : "+result);
        return result;

    }

    public String lookupPodForAKinCAS(String dest, String unitId, String bkgId){
        def inj = new GroovyInjectionBase();
        def emailSender = inj.getGroovyClassInstance("EmailSender");
        def emailFrom = "1aktosdevteam@matson.com";
        def emailTo = "1aktosdevteam@matson.com";
        String stateCodeAK = "<stateCode>AK</stateCode>";
        String stateCode = "stateCode";
        String pod = "";
        GeneralReference genRef = GeneralReference.findUniqueEntryById("MATSON", "CASPODCHECK", "URL");
        URL url = new URL(genRef.getRefValue1() + dest);

        DefaultClientConfig clientConfig1 = new DefaultClientConfig();
        clientConfig1.getProperties().put(ClientConfig.PROPERTY_CONNECT_TIMEOUT, Integer.parseInt(genRef.getRefValue2()));
        clientConfig1.getProperties().put(ClientConfig.PROPERTY_READ_TIMEOUT, Integer.parseInt(genRef.getRefValue3()));
        Client client = Client.create(clientConfig1);

        WebResource resource = client.resource(url.toString());
        ClientResponse response = (ClientResponse) resource.accept(MediaType.APPLICATION_XML).get(ClientResponse.class);
        if (response.getStatus() != 200) {
            println("Request failed");
            println(response.toString());
        } else {
            println("Request Success");
            println(response.toString());
        }
        String xmlResponse = response.getEntity(String.class);
        println(xmlResponse);
        StringBuffer content = new StringBuffer();
        content.append("\nUnable to determine the right POD for the destination : "+dest+", for the unit "+unitId+".");
        content.append("\nPlease review or update the correct POD for this container manually.");
        content.append("\n\n");
        content.append(xmlResponse);

        def emailBody = content.toString();
        if(xmlResponse.contains(stateCode)){
            if(xmlResponse.contains(stateCodeAK)){
                pod = "ANK";
            }
            else {
                pod = "TAC";
            }
        }else{
            emailSender.custSendEmail(emailFrom,emailTo,"Error : Unable to determine POD during LNK process for unit "+unitId,emailBody);
        }
        println('POD ::: '+pod);
        return pod;
    }
    public void setUnitPod(Unit unit, String pod){
        def resolvedPod = RoutingPoint.findRoutingPoint(pod);
        unit.getUnitRouting().setRtgPOD1(resolvedPod);
    }

}