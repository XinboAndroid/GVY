import com.navis.framework.business.Roastery
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.api.IFlagType
import com.navis.argo.business.atoms.FlagPurposeEnum
import com.navis.security.business.user.BaseUser

/*
* A1   GR  Commented out Field  replaceQuotes
* A2   GR   12/13/11  Update HOLD FOR LNK
*/

//Class to Manipulate String Values 
public class GvyCmisCargoStatusUtil
{

    public String eventSpecificFieldValue(String xmlGvyData,String field,String newFieldValue)
    {
        String newValue = null;
        String oldValue = null;
        String xmlGvyString = xmlGvyData;
        int fieldIndx = xmlGvyString.indexOf(field);
        try
        {
            if(fieldIndx != -1)
            {
                int equalsIndx = xmlGvyString.indexOf("=",fieldIndx);
                int nextspace = xmlGvyString.indexOf("\"", equalsIndx+2);
                oldValue = xmlGvyString.substring(equalsIndx+2, nextspace);

                if(oldValue.equals("null") ){
                    newValue = newFieldValue;
                }
                else{
                    //CHECK FOR VALUE HERE
                    newValue = newFieldValue;
                }
                //System.out.println("Field ::"+field+"  oldValue ::"+oldValue+"  newValue :::"+newValue);
                //Replace Escape Char  in String
                newValue = replaceQuotesUtil(newValue)
                String oldXmlValue = field+"\""+oldValue+"\"";
                String newXmlValue = field+"\""+newValue+"\"";
                //println("oldXmlValue ::"+oldXmlValue+"  newXmlValue :::"+newXmlValue);
                xmlGvyString = xmlGvyString.replace(oldXmlValue, newXmlValue);
            }//IF Ends
        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString;
    }// Method eventSpecificFieldValue Ends

    public static String getFieldValues(String xmlGvyString, String field)
    {
        String fieldValue = ''
        try
        {
            def fieldIndx = xmlGvyString.indexOf(field);
            def equalsIndx = xmlGvyString.indexOf("=",fieldIndx);
            def nextspace = xmlGvyString.indexOf("\"", equalsIndx+2);
            fieldValue  = xmlGvyString.substring(equalsIndx+2, nextspace);
            //println("equalsIndx:"+equalsIndx+"  nextspace:"+nextspace+" oldValue:"+fieldValue);
        }catch(Exception e){
            e.printStackTrace()
        }
        return fieldValue;
    }

    public  String arrayToString(String[] a, String separator) {
        String result = "";
        if (a.length > 0) {
            result = a[0];    // start with the first element
            for (i in a) {
                result = result + separator + i;
            }
        }
        return result;
    }

    //Method Get Active Holds for Unit
    public String getUnitActiveHolds(Object unitbase)
    {
        def activeHoldList = "";
        try
        {
            def map = new HashMap()
            map.put('DRAY CANNOT LTV','')
            map.put('HOLD FOR LNK','HLD')
            map.put('CG_INSP','CG')
            map.put('OUTGATE','RD')

            def strBuffer = new StringBuffer()
            ServicesManager sm = (ServicesManager)Roastery.getBean("servicesManager");
            def flagIds = sm.getActiveFlagIds(unitbase);
            if (flagIds != null)
            {
                for(holdId in flagIds)
                {
                    def  iFlageType = sm.getFlagTypeById(holdId)
                    def flagPurpose =  iFlageType.getPurpose().getKey()
                    if(flagPurpose.equals('HOLD'))
                    {
                        def appHoldId = map.get(holdId) != null ? map.get(holdId) : holdId
                        strBuffer.append(appHoldId+',')
                    }
                }
            }//IF Ends
            activeHoldList = strBuffer.length() > 0 ? (''+strBuffer).substring(0,strBuffer.length()-1) : strBuffer
            //println("activeHoldList ::::"+activeHoldList)
        }catch(Exception e){
            e.printStackTrace()
        }
        return activeHoldList
    }//Method Ends


    public String formatField(String attrName,String attrValue )
    {
        try
        {
            def fmtValue = ''
            if(attrValue != null)
            {
                def attrFmtValue= replaceQuotes(attrValue)
                //def attrFmtValue= attrValue
                fmtValue = attrName+"=\""+attrFmtValue+"\" "
                return fmtValue;
            }
            else
            {
                return fmtValue;
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public  String replaceQuotes(Object message)
    {
        def msg = message.toString();
        def replaceAmp = msg.replaceAll('&', '&amp;');
        replaceAmp = replaceAmp.replaceAll('\'', '&apos;');
        replaceAmp = replaceAmp.replaceAll("<", "&lt;")
        replaceAmp =  replaceAmp.replaceAll(">", "&gt;")
        replaceAmp = replaceAmp.replaceAll("\"", "&quot;")
        return replaceAmp;
    }

    //Method verifies the
    public String getCsrUserRole(String userId)
    {
        try
        {
            BaseUser baseUser = new BaseUser()
            baseUser = baseUser.findBaseUser(userId)
            def groupArr = baseUser != null ? baseUser.getUserRoleNames() : null
            for(aGroup in groupArr)
            {
                if(aGroup.equals('No Email')){
                    println("User Has CSR No Email Group :"+aGroup)
                    return aGroup
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return null
    }

    /*
  * Methods Validates if POD is NIS port
  * and sends out messages to NIS mailing Groups for Holds
  */
    public boolean nisPortCheck(Object event, Object api)
    {
        boolean isNIS = false
        try
        {
            def unit = event.getEntity()
            def gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil")
            def curDischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            curDischPort = curDischPort != null ? curDischPort : ""
            isNIS  = gvyCmisUtil.isNISPort(curDischPort)
        }catch(Exception e){
            e.printStackTrace()
        }
        return isNIS
    }

    public  String replaceQuotesUtil(Object message)
    {
        def msg = message.toString();
        def replaceAmp = msg.replaceAll('&', '&amp;');
        replaceAmp = replaceAmp.replaceAll('\'', '&apos;');
        replaceAmp = replaceAmp.replaceAll("<", "&lt;")
        replaceAmp =  replaceAmp.replaceAll(">", "&gt;")
        replaceAmp = replaceAmp.replaceAll("\"", "&quot;")
        return replaceAmp;
    }

}