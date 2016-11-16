import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.argo.business.api.ArgoUtils
import com.navis.framework.metafields.MetafieldId
import com.navis.services.business.event.EventFieldChange
import com.navis.inventory.business.imdg.ImdgClass
import com.navis.inventory.business.imdg.HazardItem;
import com.navis.inventory.business.imdg.Hazards;


public class GvyCmisEventHazards
{

    public synchronized String createHazMessage(Object event, Object gvyBaseClass, String xmlGvyData)
    {
        def hazImdg = ''
        def hazUnNum = ''
        def prev  = ''
        def gvyEventObj = event.getEvent()
        def unit = event.getEntity()
        def eventType =  gvyEventObj.getEventTypeId()
        def xmlGvyString = xmlGvyData

        try
        {

            Set changes =  gvyEventObj.getFieldChanges()
            Iterator iterator = changes.iterator();
            while(iterator.hasNext())
            {
                //println('Inside While loop :')
                EventFieldChange fieldChange = (EventFieldChange)iterator.next();
                String fieldName = fieldChange.getMetafieldId()
                MetafieldId mfId =  MetafieldIdFactory.valueOf(fieldName);
                if(eventType.equals('UNIT_HAZARDS_INSERT')){
                    prev = ArgoUtils.getPropertyValueAsUiString(mfId, fieldChange.getEvntfcNewVal());
                }else{
                    prev = ArgoUtils.getPropertyValueAsUiString(mfId, fieldChange.getEvntfcPrevVal());
                }
                //println('mfId :'+mfId+'   prev:'+prev)
                if (prev != null){
                    if(fieldName.equals("hzrdiImdgClass")){
                        String [] imdg  = prev.split(" ")
                        hazImdg = imdg != null ? imdg[0] : ''
                        hazImdg = hazImdg.equals('Unknown') ? 'HAZ' : hazImdg
                    }
                    else if(fieldName.equals("hzrdiUNnum")){
                        hazUnNum = prev
                    }
                }
            }

            def utilObj = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            xmlGvyString = utilObj.eventSpecificFieldValue(xmlGvyString,"hazImdg=",hazImdg)
            xmlGvyString = utilObj.eventSpecificFieldValue(xmlGvyString,"hazUnNum=",hazUnNum)

            //Haz Item Nbr Type
            def hzrdItemNbrType = getHzrdItemNbrType(unit, hazImdg, hazUnNum)
            xmlGvyString = utilObj.eventSpecificFieldValue(xmlGvyString,"flex01=",hzrdItemNbrType)

            //MSG FOR ACETS
            def xmlGvyAcetsMsg = xmlGvyString
            if(eventType.equals('UNIT_HAZARDS_INSERT')){
                xmlGvyAcetsMsg = xmlGvyAcetsMsg.replace("lastAction='null'","lastAction='HZU'");
                xmlGvyAcetsMsg = xmlGvyAcetsMsg.replace("action='null'","action='HZU'")
            }
            else{
                xmlGvyAcetsMsg = xmlGvyAcetsMsg.replace("lastAction='null'","lastAction='HZD'");
                xmlGvyAcetsMsg = xmlGvyAcetsMsg.replace("action='null'","action='HZD'")
            }

            if(xmlGvyAcetsMsg != null && xmlGvyAcetsMsg.length() > 0)
            {  gvyBaseClass.sendXml(xmlGvyAcetsMsg)  }
            if(xmlGvyString != null && xmlGvyString.length() > 0)
            {  gvyBaseClass.sendXml(xmlGvyString)  }

        }catch(Exception e){
            e.printStackTrace()
        }

        return xmlGvyString
    }

    public synchronized String updateHazardousMsg(Object event, Object gvyBaseClass, String xmlGvyData)
    {
        def gvyEventObj = event.getEvent()
        def eventNotes = gvyEventObj.getEventNote()
        def updtImdg = ''
        def updtUNnum = ''
        def xmlGvyString = xmlGvyData
        def unit = event.getEntity()

        try
        {
            if(eventNotes != null){
                def startIndex = eventNotes.indexOf("[") != -1 ? eventNotes.indexOf("[") : 0
                def endIndex = eventNotes.indexOf("]") != -1 ? eventNotes.indexOf("]") : 0
                updtImdg = eventNotes.substring(startIndex+1,endIndex)
            }

            if(eventNotes != null){
                String[] eventString = eventNotes.split(" ");
                int length = eventString.length;
                updtUNnum = eventString[length -1];
            }

            def utilObj = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            xmlGvyString = utilObj.eventSpecificFieldValue(xmlGvyString,"hazImdg=",updtImdg)
            xmlGvyString = utilObj.eventSpecificFieldValue(xmlGvyString,"hazUnNum=",updtUNnum)

            //Haz Item Nbr Type
            def hzrdItemNbrType = getHzrdItemNbrType(unit, updtImdg, updtUNnum)
            xmlGvyString = utilObj.eventSpecificFieldValue(xmlGvyString,"flex01=",hzrdItemNbrType)

            //MSG FOR ACETS
            def xmlGvyAcetsMsg = xmlGvyString
            xmlGvyAcetsMsg = xmlGvyAcetsMsg.replace("lastAction='null'","lastAction='HZU'");
            xmlGvyAcetsMsg = xmlGvyAcetsMsg.replace("action='null'","action='HZU'")

            gvyBaseClass.sendXml(xmlGvyAcetsMsg)
            gvyBaseClass.sendXml(xmlGvyString)

            return  xmlGvyString

        }catch(Exception e){
            e.printStackTrace()
        }

    }

    public String getHzrdItemNbrType(Object unit, String imdg, String unNaNbr)
    {
        def hzrdItemNbrType = '%'
        try
        {
            def hazardsList = unit.getUnitGoods() != null ? unit.getUnitGoods().getGdsHazards() : null
            if(hazardsList != null){
                def hazardItem = hazardsList.findHazardItem(ImdgClass.getEnum(imdg),unNaNbr)
                hzrdItemNbrType = hazardItem != null ? hazardItem.hzrdiNbrType.key : '%'
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return hzrdItemNbrType
    }//Method Ends

}
