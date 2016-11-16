/*

Groovy code called at the end of the truck outgate transaction. It submits an XML document to NOW Solutions Web service using SOAP

Amine Nebri, anebri@navis.com - June 25 2008

*/
/*
* Srno   Doer  Date          Desc
* A1     GR    04/21/2010    Added Method and Changes to pass ChasType and NO-EIT transactions
* A2     GR    06/04/2010    Added eitString='0' to allow PassPass Gate Trans to Go through
* A3     GR    11/01/2010    TOS2.1 : CHANGE FOR WS
*/

import com.navis.apex.business.model.GroovyInjectionBase
//import com.matson.nowsol.*
import N4Gate.matsongate_n4.*;

public class NOWOutgateProcessor  extends GroovyInjectionBase
{
    private static proxy = null;

    public void execute(inDao, api, departure)
    {
        println("\nNOWProcessor: --Executing Groovy Gate Task---")

        def eitString = inDao.tv.tvdtlsBatNbr

        // We exit if the Bat Number is not numeric or <100 and >200
        try
        {
            if (eitString == null)
            {
                println("NOWProcessor: Reassigning eit=0!\n\n")
                //return - A1
            }

            int eitId = eitString != null ? eitString.toInteger() : 0  //A1

            if (eitId < 101 || eitId >200)
            {
                println("NOWProcessor: Skipping eit "+eitId+"!\n\n")
                //return --A1
            }
        }
        catch (Exception e)
        {
            println("NOWProcessor: Skipping non numeric id  "+eitString+"!\n\n"+e)
            return
        }

        def url = api.getGroovyClassInstance("GvyRefDataLookup").getNowServer();
        if(url == null) {
            println("NOWOutgateProcessor: Skipping no now server  !\n\n");
            return;
        }

        // Build the message by calling NOWMessageBuilder
        def msg = api.getGroovyClassInstance("NOWMessageBuilder").formOutgateMessage(inDao)
        def result

        println("NOWOutgateProcessor: invoking NOW WebService with: " + msg)
        def time = System.currentTimeMillis();
        // Invoke NOW


        // 07/31/09 Switched to BEA service
        try {
            if(proxy == null)  {
                try {
                    //def s = new N4Gate_Service_Impl(url);
                    //proxy = s.getN4Gate()

                    N4Gate_ServiceLocator gateSrvLoc = new N4Gate_ServiceLocator();
                    gateSrvLoc.setN4GateEndpointAddress(url);
                    proxy = gateSrvLoc.getN4Gate();

                } catch (Exception pe) {
                    //def s = new N4Gate_Service_Impl(url);
                    //proxy = s.getN4Gate()
                    N4Gate_ServiceLocator gateSrvLoc = new N4Gate_ServiceLocator();
                    gateSrvLoc.setN4GateEndpointAddress(url);
                    proxy = gateSrvLoc.getN4Gate();

                }
            }
            api.log("Proxy="+proxy);

            try {
                result = proxy.process_tracking(msg);
            } catch (Exception re) {
                result = proxy.process_tracking(msg);
            }
        }
        catch (Exception e) {
            proxy = null;
            e.printStackTrace();
            throw new Exception("NOWOutgateProcessor: Could not connect to NOW: " + e.getClass());

        }


        // Test for null result

        time = System.currentTimeMillis() - time;


        println("NOWOutgateProcessor:("+(double)time/1000.0+") NOW result is: " + result)

        if (!result)
        {
            throw new Exception("NOW WebService returned null")
        }

        // Examine the result document
        def resultDoc = new XmlParser().parseText(result)

        def status = resultDoc.'truck-departure-response'[0].'@status'

        println("NOWProcessor: status is " + status)

        if (status == "3")
        {
            def error = resultDoc.'truck-departure-response'[0].messages.message[0].'@text'

            println("NOWOutgateProcessor: error is: " + error)
            throw new Exception(error)
        }

        time = System.currentTimeMillis() - time;
        time = (double)time/1000.0;
        println("NOWOutgateProcessor: success! Parse Time="+time+"\n\n")
    }
}