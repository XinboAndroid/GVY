/*
*  Srno  Changer Date        Desc
*  A1    GR      11/23/10    Set Actual Ves,Voy,leg for RHN action
							 1.Calling method parameters updated 2.Post call Updated
							 3. Depends on Class GvyCmisUtil Update A9
*/
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.argo.business.api.ArgoUtils
import com.navis.framework.metafields.MetafieldId
import com.navis.services.business.event.EventFieldChange


public class GvyCmisMessagesForAcets
{

    //UNIT_POSITION_CORRECTION
    public void unitPositionCorrection(String xmlGvyData, Object unit, Object gvyBaseClass,Object event, Object gvyEventUtil) //A1
    {
        def xmlGvyString = xmlGvyData
        try
        {
            def gvycmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            def transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
            transitState = transitState != null ? transitState.getKey() : ''
            def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            def locType = lkpLocType != null ? lkpLocType.getKey() : ''

            def vesselLineOperator = gvycmisUtil.vesselServiceOperator(unit)
            if( locType.equals('VESSEL') && vesselLineOperator.equals('MAT')  &&
                    (transitState.equals('S60_LOADED') || transitState.equals('S20_INBOUND')))
            {
                //xmlGvyString = xmlGvyString.replace("lastAction='null'","lastAction='RHN'");
                //xmlGvyString = xmlGvyString.replace("action='null'","action='RHN'")
                //println("xmlGvyString ::"+xmlGvyString)
                //gvyBaseClass.sendXml(xmlGvyString)
                gvycmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"RHN", unit, event, gvyEventUtil)
            }
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    public void unitStorageUpdate(String xmlData, Object gvyBaseClass)
    {
        try
        {
            def xmlGvyString = xmlData
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil")
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"lastAction=","FREE")
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"action=","FREE")
            gvyBaseClass.sendXml(xmlGvyString)
        }catch(Exception e){
            e.printStackTrace()
        }

    }

}