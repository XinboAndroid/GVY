/*
* Sr  Doer  Date        Chagne
* A1  GR    08/18/2010  Added LocationStatus=1
* A2  GR    11/03/2010  Adding Actual Vessel,Voyage and leg
* A3  GR    07/11/2011  Adding Set vesvoy method Change
* A4  RI    01/06/2014  Added logic to substract 2 min from OGT message
*/
import com.navis.apex.business.model.GroovyInjectionBase;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.Calendar;
import org.apache.log4j.Logger


public class GvyCmisEventFructoseMty extends GroovyInjectionBase {

    def ACTION = "action='null'"
    def LAST_ACTION = "lastAction='null'"

    public String getOGT(String xmlGvyData)
    {
        def  xmlGvyString = xmlGvyData
        try{

            def aDate = getFieldValues(xmlGvyString, "aDate=");
            def aTime = getFieldValues(xmlGvyString, "aTime=");
            def lastADate = getFieldValues(xmlGvyString, "lastADate=");
            def lastATime = getFieldValues(xmlGvyString, "lastATime=");

            LOGGER.warn("Time for OGT Details  >>>>>>>>>>>:"+aDate+"::"+aTime+"::"+lastADate+"::"+lastATime);

            def oldATime = "aTime='"+aTime+"'";
            def oldLastATime = "lastATime='"+lastATime+"'";
            LOGGER.warn("<<<<<<<<<aTime>>>>>>>>>>"+ aTime);
            SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
            Date d = df.parse(aTime);
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            cal.add(Calendar.MINUTE, -2);
            String aTimeNew = df.format(cal.getTime());
            LOGGER.warn("<<<<<<<<<newTime>>>>>>>>>>"+ aTimeNew);

            LOGGER.warn('<<<<<OGT>>>>>>>'+xmlGvyString)
            def truck = getFieldValues(xmlGvyString, "truck=");
            def newATime = "aTime='"+aTimeNew+"'";
            def newLastATime = "lastATime='"+aTimeNew+"'";

            def truckOld = "truck='"+truck+"'";

            xmlGvyString = xmlGvyString.replace(truckOld,"truck='ZZZZ'");
            xmlGvyString = xmlGvyString.replace(LAST_ACTION,"lastAction='OGT'");
            xmlGvyString = xmlGvyString.replace(ACTION,"action='OGT'")
            xmlGvyString = xmlGvyString.replace(oldATime,newATime)
            xmlGvyString = xmlGvyString.replace(oldLastATime,newLastATime)
            LOGGER.warn('<<<<<OGT afterUpdate>>>>>>>'+xmlGvyString)

        }catch(Exception e){
            e.printStackTrace()
        }

        return xmlGvyString
    }

    public String getIGT(String xmlGvyData,Object unit)
    {
        def  xmlGvyString = xmlGvyData
        try{

            def aDate = getFieldValues(xmlGvyString, "aDate=");
            def aTime = getFieldValues(xmlGvyString, "aTime=");
            def lastADate = getFieldValues(xmlGvyString, "lastADate=");
            def lastATime = getFieldValues(xmlGvyString, "lastATime=");

            LOGGER.warn("Time for IGT Details >>>>>>>>>>>:"+aDate+"::"+aTime+"::"+lastADate+"::"+lastATime);

            def commodityId=unit.getFieldValue("unitGoods.gdsCommodity.cmdyId")
            def commodity = commodityId != null ? commodityId : ''
            def lineOperator=unit.getFieldValue("unitLineOperator.bzuId")

            def cargoNotes = getFieldValues(xmlGvyString, "cargoNotes=");
            def locationRow = getFieldValues(xmlGvyString, "locationRow=");
            def truck = getFieldValues(xmlGvyString, "truck=");
            def locationStatus = getFieldValues(xmlGvyString, "locationStatus=");
            def action = getFieldValues(xmlGvyString, "action=");
            def lastAction = getFieldValues(xmlGvyString, "lastAction=");

            def cargoNotesOld = "cargoNotes='"+cargoNotes+"'";
            def locationRowOld = "locationRow='"+locationRow+"'";
            def truckOld = "truck='"+truck+"'";
            def actionOld = "action='"+action+"'";
            def lastActionOld = "lastAction='"+lastAction+"'";
            def locationStatusOld = "locationStatus='"+locationStatus+",";

            xmlGvyString = xmlGvyString.replace(cargoNotesOld,"cargoNotes='"+commodity+"'");
            xmlGvyString = xmlGvyString.replace(locationRowOld,"locationRow='"+lineOperator+"'");
            xmlGvyString = xmlGvyString.replace(truckOld,"truck='ZZZZ'");
            xmlGvyString = xmlGvyString.replace(locationStatusOld,"locationStatus='1'");
            xmlGvyString = xmlGvyString.replace(lastActionOld,"lastAction='IGT'");
            xmlGvyString = xmlGvyString.replace(actionOld,"action='IGT'")
            xmlGvyString = xmlGvyString.replace("msgType='FRUCTOSE_LOAD'","msgType='FRUCTOSE_MTY'")
            //println('<<<<<IGT>>>>>>>'+xmlGvyString)
            //A2 Adding Actual Vessel,Voyage and leg
            def gvyCmisUtil = getGroovyClassInstance("GvyCmisUtil");
            def carrierId = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")

            def obVesClass = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
            obVesClass = obVesClass != null ? obVesClass.getKey() : ''
            xmlGvyString = gvyCmisUtil.setVesvoyFields(unit, xmlGvyString, carrierId, obVesClass) //A3

        }catch(Exception e){
            e.printStackTrace()
        }

        return xmlGvyString
    }

    public String getEDT(String xmlGvyData)
    {
        def  xmlGvyString = xmlGvyData
        try
        {
            def truck = getFieldValues(xmlGvyString, "truck=");
            def dir = getFieldValues(xmlGvyString, "dir=");
            def truckOld = "truck='"+truck+"'";

            xmlGvyString = xmlGvyString.replace(truckOld,"truck='null'");
            xmlGvyString = xmlGvyString.replace("lastAction='IGT'","lastAction='EDT'");
            xmlGvyString = xmlGvyString.replace("action='IGT'","action='EDT'")
            xmlGvyString = xmlGvyString.replace("msgType='FRUCTOSE_LOAD'","msgType='FRUCTOSE_MTY'")
            // println('<<<<<EDT>>>>>>>'+xmlGvyString)

        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString
    }

    public String getYMV(String xmlGvyData, Object utilObj)
    {
        def  xmlGvyString = xmlGvyData
        try
        {
            xmlGvyString = utilObj.addEventSpecificFldValue(xmlGvyString,"lastAction=","YMV",null,null,null);
            xmlGvyString = utilObj.addEventSpecificFldValue(xmlGvyString,"action=","YMV",null,null,null);
            xmlGvyString = utilObj.addEventSpecificFldValue(xmlGvyString,"msgType=","FRUCTOSE_MTY",null,null,null);
            //println('<<<<<YMV>>>>>>>'+xmlGvyString)

        }catch(Exception e){
            e.printStackTrace()
        }

        return xmlGvyString
    }

    public static String getFieldValues(String xmlGvyString, String field)
    {
        def fieldIndx = xmlGvyString.indexOf(field);
        def equalsIndx = xmlGvyString.indexOf("=",fieldIndx);
        def lastChar = xmlGvyString.indexOf("'", equalsIndx+2);
        String fieldValue  = xmlGvyString.substring(equalsIndx+2, lastChar);
        //println("equalsIndx:"+equalsIndx+"  lastChar:"+lastChar+" oldValue:"+fieldValue);
        return fieldValue;
    }

    private static final Logger LOGGER = Logger.getLogger(GvyCmisEventFructoseMty.class);
}