/*
* Srno  Doer  Date       comment
* A1    GR    07/15/10   Added Method process Haz messages xml output
* A2    GR    09/08/10   Added flex01 back to the xml message
* A3    GR    10/24/10   Fix : Kel found HazF not being set
* A4    GR    01/11/10   HAZ Proper Name check to set REG  correclty
                         Comment out Sending EDT to Cmis Gems
*/
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.argo.business.api.ArgoUtils
import com.navis.framework.metafields.MetafieldId
import com.navis.services.business.event.EventFieldChange
import com.navis.inventory.business.imdg.ImdgClass
import com.navis.inventory.business.imdg.HazardItem;
import com.navis.inventory.business.imdg.Hazards;
import com.navis.framework.business.Roastery

public class GvyCmisMostHazardousItem
{

    public boolean isMostHazardousItem(Object event){
        def isMostHaz = false
        try{
            def unit = event.getEntity()
            def mostHazItem = unit.getUnitGoods().getGoodsMostSevereHazardClass()

            def currItem = getHazardousItem(event)
            def currHzrdiImdg  = currItem != null ? currItem : ''
            if(currHzrdiImdg.equals(mostHazItem)) {
                isMostHaz = true
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return isMostHaz
    }

    public String getHazardousItem(Object event){

        def hazImdg = ''
        def hazUnNum = ''
        def prev  = ''
        def gvyEventObj = event.getEvent()
        def eventType =  gvyEventObj.getEventTypeId()
        def unit = event.getEntity()
        def eventNotes = gvyEventObj.getEventNote()

        try
        {
            Set changes =  gvyEventObj.getFieldChanges()
            Iterator iterator = changes.iterator();
            while(iterator.hasNext())
            {
                EventFieldChange fieldChange = (EventFieldChange)iterator.next();
                String fieldName = fieldChange.getMetafieldId()
                MetafieldId mfId =  MetafieldIdFactory.valueOf(fieldName);
                if(eventType.equals('UNIT_HAZARDS_INSERT')){
                    prev = ArgoUtils.getPropertyValueAsUiString(mfId, fieldChange.getEvntfcNewVal());
                }else if(eventType.equals('UNIT_HAZARDS_UPDATE')){
                    if(eventNotes != null){
                        def startIndex = eventNotes.indexOf("[") != -1 ? eventNotes.indexOf("[") : 0
                        def endIndex = eventNotes.indexOf("]") != -1 ? eventNotes.indexOf("]") : 0
                        def updtImdg = eventNotes.substring(startIndex+1,endIndex)
                        return updtImdg
                    }
                }
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
        }catch(Exception e){
            e.printStackTrace();
        }
        return hazImdg
    }//Method Ends


    //Method to write out Haz Messages
    public void processMostHazItem(Object xmlGvyString, Object gvyEventUtil,Object api, Object event)
    {

        def gvyShipDetail = api.getGroovyClassInstance("GvyCmisShipmentDetail");
        def gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil");
        def gvyEventObj = event.getEvent()
        def eventType =  gvyEventObj.getEventTypeId()
        def unit = event.getEntity()
        def action = 'HZU';

        //HAZF
        def isHazardous=unit.getFieldValue("unitGoods.gdsIsHazardous")
        isHazardous = isHazardous == true ? 'Y' : null
        try{

            def hazItem = gvyShipDetail.getMostHazItem(unit)
            //HAZ IMDG
            def mostHazItem =  hazItem != null ? hazItem.hzrdiImdgClass.key : null
            mostHazItem = mostHazItem != null  ? mostHazItem : null

            //HAZ Proper Name A4
            def properName = hazItem != null ? hazItem.hzrdiProperName : ''
            properName = properName != null ? properName : ''

            //HazDesc
            def hazardItemDesc = hazItem != null ? hazItem.getDescription() : null
            if(hazardItemDesc != null){
                hazardItemDesc = hazardItemDesc.indexOf(' ') != -1 ? hazardItemDesc.substring(hazardItemDesc.indexOf(' ')+1) : null
            }
            if(!eventType.equals('UNIT_HAZARDS_DELETE') && (mostHazItem == null || hazardItemDesc == null || mostHazItem.equals('X') || hazardItemDesc.contains('Unknown'))){
                return;
            }

            //HAZ UNNUM
            def mostHazNum = hazItem != null ? hazItem.hzrdiUNnum : null

            //HAZ NBR Type
            def hzrdItemNbrType = hazItem != null ? hazItem.hzrdiNbrType.key : null

            //HazReg  //A4
            def hazardItemRegs = hazardItemDesc != null && (hazardItemDesc.contains('Liquid') || properName.contains('Liquid') || properName.contains('LIQUID')) ? 'DOT' : 'IMO'

            Set changes =  gvyEventObj.getFieldChanges()
            Iterator iterator = changes.iterator();
            def prev  = ''
            while(iterator.hasNext())
            {
                EventFieldChange fieldChange = (EventFieldChange)iterator.next();
                String fieldName = fieldChange.getMetafieldId()
                MetafieldId mfId =  MetafieldIdFactory.valueOf(fieldName);
                if(eventType.equals('UNIT_HAZARDS_INSERT') || eventType.equals('UNIT_HAZARDS_UPDATE')){
                    prev = ArgoUtils.getPropertyValueAsUiString(mfId, fieldChange.getEvntfcNewVal());
                }else{
                    prev = ArgoUtils.getPropertyValueAsUiString(mfId, fieldChange.getEvntfcPrevVal());
                }

                if (prev != null){
                    if(fieldName.equals("hzrdiImdgClass")){
                        mostHazItem = prev.substring(0,prev.indexOf(' '));
                        hazardItemDesc = prev.indexOf(' ') != -1 ? prev.substring(prev.indexOf(' ')+1) : null
                        hazardItemRegs = hazardItemDesc != null ? (hazardItemDesc.contains('Liquid') || properName.contains('Liquid') || properName.contains('LIQUID')) ? 'DOT' : 'IMO' : null
                    }
                    else if(fieldName.equals("hzrdiUNnum")){
                        mostHazNum = prev
                        hzrdItemNbrType = 'UN'
                    }//Else If Ends
                }//IF Ends
            }//While


            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"flex01=",hzrdItemNbrType)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"hazF=",isHazardous)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"hazImdg=",mostHazItem)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"hazUnNum=",mostHazNum)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"hazDesc=",hazardItemDesc)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"hazRegs=",hazardItemRegs)

            if(eventType.equals('UNIT_HAZARDS_DELETE')){  action='HZD'  }

            gvyCmisUtil.postMsgForAction(xmlGvyString,api,action)
            //A4 - gvyCmisUtil.postMsgForAction(xmlGvyString,api,'null')

            // Also Check how the Ingate is calling the HAZ messages creation
        }catch(Exception e){
            e.printStackTrace();
        }
    }//Method Ends

}//Class Ends
