/*

Groovy code called at the end of the truck gatein transaction. It submits an XML document to NOW Solutions Web service using SOAP

Amine Nebri, anebri@navis.com - June 25 2008

A1 7/3/1/09 SKB Switch to BEA client
A2 03/30/10 GR  NON-EIT Alert mail
A3 04/06/10 GR  Commented out the Return for NON-EIT
                This would pass all gate Transaction to NOW
A3 04/21/10 GR  Added Verify=flase for NON-EIT
A4 04/28/10 KM  NON-EIT line up values and display time in HST
A5 05/01/10 GR  Moved noEitAlert to Arrived only units
A6 06/04/10 GR  Added eitId='0' to allow PassPass Gate Trans to Go through
                Dont Send Non-Eit Alerts for 0 value
A7 10/15/10 GR  NON-EIT to SKIP posting if NOW Is Down (tt#9382)
A8 11/02/10 GR  Error Messages Check for Duplicated Truck Visit
A9 02/11/11 GR  Added Additional Check for Dup truckvisit
A10 08/16/11 GR  replaced back to sendEmail
A11 08/16/11 GR Added Chassis Axis-WS Code
A12 10/24/11 GR Put back custSendEmail, noEitAlter and url mapping
A13 11/02/11 GR Added Road Biz Task for N4 to handel the Exceptions correctly
A14 11/03/11 GR Change for VALID and INVALID DUPLICATE TURCKVISIT
*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.util.RoadBizUtil
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.DateUtil;  //A4
import java.util.TimeZone;   //A4
import N4Gate.matsongate_n4.*;

public class NOWProcessor extends GroovyInjectionBase {
    private static proxy = null;
    private static eitEmailto = "1EITAlertAllGroup@matson.com;sysreports@matson.com";
    // private static eitEmailto = "1aktosdevteam@matson.com";
    public void execute(inDao, api, arrival, verify=true) {

        def eitString = inDao.tv.tvdtlsBatNbr
        int eitId = 0;

        // We exit if the Bat Number is not numeric or <100 and >200
        try {
            if (eitString == null) {
                println("NOWProcessor: ReAssigned Null eitString=0")
            }

            eitId = eitString != null ? eitString.toInteger() : 0 //A6
            if (eitId < 101 || eitId > 200) {
                verify=false
            }
        }
        catch (Exception e) {
            return
        }

        // Before continuing, make sure there are no errors exist in the main session
        if (RoadBizUtil.getMessageCollector().getMessageCount(MessageLevel.SEVERE) > 0) {
            return;
        }

        def url = api.getGroovyClassInstance("GvyRefDataLookup").getNowServer();

        if (url == null) {
            println("NOWProcessor: Skipping no now server  !\n\n")
        }

        // Build the message by calling NOWMessageBuilder
        def msg = null;
        if (arrival) {
            if (eitId != 0 && eitId < 101 || eitId > 200) {
                println("eitId ::" + eitId);
                //noEitAlert(inDao) // Commented this method to Stop NO EIT GIVEN AT GATE email TT EP000201681
            }
            msg = api.getGroovyClassInstance("NOWMessageBuilder").formIngateMessage(inDao, verify)
        } else {
            msg = api.getGroovyClassInstance("NOWMessageBuilder").formOutgateMessage(inDao)
        }

        def time = System.currentTimeMillis();
        // Invoke NOW
        println("NOWProcessor: invoking NOW WebService with: " + msg)
        //def proxy = new groovy.net.soap.SoapClient("http://192.168.170.175:10060/.wsdl")




        def result

        // 07/31/09 Switched to BEA service
        /*
        N4Gate_ServiceLocator gateSrvLoc = new N4Gate_ServiceLocator();
          gateSrvLoc.setN4GateEndpointAddress("http://192.168.170.244:10060/.wsdl");
          N4Gate_PortType n4gateProxy = gateSrvLoc.getN4Gate();
          String temp = n4gateProxy.process_tracking("TEST MSG");
         */
        try {
            if(proxy == null)  {
                try {
                    //A11 - def s = new N4Gate_Service_Impl(url);
                    //proxy = s.getN4Gate()
                    N4Gate_ServiceLocator gateSrvLoc = new N4Gate_ServiceLocator();
                    gateSrvLoc.setN4GateEndpointAddress(url);
                    proxy = gateSrvLoc.getN4Gate();
                } catch (Exception pe) {
                    //A7 if Now Retry fails Skip exception
                    if (eitId != 0 && eitId < 101 || eitId > 200) {
                        println("Skip Exception Dont Pass To Now")
                        return;
                    }else{
                        //A11 - def s = new N4Gate_Service_Impl(url);
                        //proxy = s.getN4Gate()
                        N4Gate_ServiceLocator gateSrvLoc = new N4Gate_ServiceLocator();
                        gateSrvLoc.setN4GateEndpointAddress(url);
                        proxy = gateSrvLoc.getN4Gate();
                    }
                }//Inner catch Ends
            }
            println("Proxy="+proxy);
            try {
                result = proxy.process_tracking(msg);
            } catch (Exception re) {
                re.printStackTrace();
                result = proxy.process_tracking(msg);
            }
        }
        catch (Exception e) {
            proxy = null;
            e.printStackTrace();
            throw new Exception("NOWProcessor: Could not connect to NOW: " + e.getClass());

        }

        // Test for null result

        time = System.currentTimeMillis() - time;


        println("NOWProcessor:(" + (double) time / 1000.0 + ") NOW result is: " + result)

        if (!result) {
            throw new Exception("NOW WebService returned null")
        }

        // Examine the result document
        def resultDoc = new XmlParser().parseText(result)

        def status = resultDoc.'truck-arrival-response'[0].'@status'

        println("NOWProcessor: status is " + status)

        if (status == "3") {
            def error = resultDoc.'truck-arrival-response'[0].messages.message[0].'@text'

            println("NOWProcessor: error is: " + error)
            String duptv = ""+error
            def errMsg = [eitString];
            PropertyKey EIT_NOT_WORKING = PropertyKeyFactory.valueOf("gate.eit_notworking");
            PropertyKey DUP_TRUCK_VISIT = PropertyKeyFactory.valueOf("gate.eit_duplicateTv");

            if(duptv.contains('Duplicate truck_visit')){ 			//A8,A9,A10
                RoadBizUtil.appendMessage(MessageLevel.SEVERE, DUP_TRUCK_VISIT, "DUPLICATE TRUCK VISIT");
                return;
            }else if(duptv.contains('duplicate')){
                println("--- BOGUS Duplicate Truck Visit ---")
            }else{
                RoadBizUtil.appendMessage(MessageLevel.SEVERE, EIT_NOT_WORKING, errMsg);
                return;
            }
        }

        time = System.currentTimeMillis() - time;
        time = (double) time / 1000.0;

        println("NOWProcessor: Completed. Parse Time=" + time + "\n\n")
    }


    public void noEitAlert(Object inDao){
        try{
            def trans = inDao.tran
            if(trans == null){
                return;
            }
            def cntrNbr = trans.tranCtrNbr
            def truckCmpyId = cntrNbr != null  ? trans.tranTrkcId : 'NA'
            if(cntrNbr == null){
                cntrNbr = trans.tranChsNbr
            }

            def timezone = trans.getTranComplex().getTimeZone()   //A4
            def eventTime = trans.tranCreated  //A4
            def dateTime = DateUtil.convertDateToLocalTime(eventTime, timezone)  //A4

            def truckvisit = trans.tranTruckVisit
            def truckId = truckvisit.tvdtlsTruckId
            def laneId = truckvisit.tvdtlsEntryLane.laneId
            if(laneId.equals('1') || laneId.equals('2') || laneId.equals('3') ){
                def emailSender = getGroovyClassInstance("EmailSender")
                def sub = "NO EIT GIVEN AT GATE "+laneId +" FOR "+cntrNbr;   //A4
                def body = 'Gate        : '+laneId+'\nEquipment   : '+cntrNbr+'\nTrucker     : '+truckCmpyId+'\nTrucker Id  : '+truckId+'\nIngate Date : '+dateTime  //A4
                emailSender.custSendEmail(eitEmailto, sub, body);  //A4
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
