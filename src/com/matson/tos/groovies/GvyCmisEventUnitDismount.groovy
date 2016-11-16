/*
* Srno doer date  change
* A1    GR   07/18/10 change posting method
*/
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.atoms.EquipClassEnum

public class GvyCmisEventUnitDismount
{

    public String getAttachedEquipId(String xmlData,Object event, Object gvyBaseClass)
    {
        def xmlGvyString = xmlData
        try
        {
            def unit = event.getEntity()
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            def lkpLocType = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''
            //def locationStatus = gvyCmisUtil.getFieldValues(xmlGvyString, "locationStatus=")
            def locationStatus = lkpLocType.equals('TRUCK') ? '3' : ''
            println('Unit Mount Location ::'+locationStatus)
            if(!locationStatus.equals('3'))
            {
                getDetachedEquipId(xmlData,event,gvyBaseClass)
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    public String getDetachedEquipId(String xmlData,Object event, Object gvyBaseClass)
    {
        def xmlGvyString = xmlData
        def xmlGvyEquipment = '';

        def unit = event.getEntity()
        def unitId = unit.getFieldValue("unitId")
        def gvyCmisUtil = null
        try
        {
            gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            def gvyEventObj = event.getEvent()
            def gvyEventType = gvyEventObj.getEventTypeId()
            def eventNotes = gvyEventObj.getEventNote()
            eventNotes = eventNotes != null ? eventNotes : ''
            def equipId  = ''

            def lastIndx = eventNotes.indexOf(" ")
            if(lastIndx == -1)
            {
                equipId = eventNotes.length() ==  0 ? ''  : eventNotes
                println('equipId_event '+equipId)
            }
            else
            {
                equipId = eventNotes.substring(0,lastIndx)
                println('equipId '+equipId+'   lastIndx ::'+lastIndx)
            }

            def equipClassType = null
            try{
                equipClassType = equipId != null && !equipId.equals('swiped')  ? Equipment.loadEquipment( equipId) : null;
            }catch(Exception e){
                println("Dismounted/Swiped Owner Chassis ::"+equipId)
                equipClassType = null;
            }
            if(equipClassType == null){
                return
            }

            if(equipClassType instanceof com.navis.argo.business.reference.Chassis)
            {
                println('equipClassType inside chassis ::'+equipClassType)
                def chassisId = equipId != null ? equipId.substring(0,equipId.length()-1) : ''
                def chassisCd =  equipId != null ? equipId.substring(equipId.length()-1) : ''

                //Pass Record for Detached Equipment
                xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"unitClass=","CHASSIS")
                xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"chassisNumber=",chassisId)
                xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"chassisCd=",chassisCd)
                xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"sectionCode=",'%')

                if (gvyEventType.equals('UNIT_DISMOUNT')) {
                    xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"ctrNo=","null")
                }

            }// If for Chassis ends
            else if(equipClassType instanceof com.navis.argo.business.reference.Accessory)
            {
                println('equipClassType inside Accessory ::'+equipClassType)

                //Pass Record for Detached Equipment
                xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"unitClass=","ACCESSORY")
                xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"accessory=",equipId)
                xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"mgWeight=","%")

                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"unitClass=","CHASSIS")
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"sectionCode=",'%')

                if(gvyEventType.equals('UNIT_MOUNT'))
                {
                    xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"lastAction=","MGM")
                    xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"action=","MGM")
                }
                else if (gvyEventType.equals('UNIT_DISMOUNT'))
                {
                    //Sets Chassis values
                    xmlGvyString = setChassisValue(xmlGvyString,eventNotes,unitId,gvyCmisUtil)
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"accessory=","null")

                    xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"locationStatus=","6")
                    xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"truck=","null")
                    xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"chassisNumber=","null")
                    xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"chassisCd=","null")
                    xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"lastAction=","MGG")
                    xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"action=","MGG")
                }

            }//Else if for Accessory Ends
            else if(equipClassType instanceof com.navis.argo.business.reference.Container)
            {
                println('equipClassType inside Container ::'+equipClassType)
                xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"ctrNo=",equipId)

                //Pass Record for Detached Equipment
                xmlGvyEquipment = gvyCmisUtil.eventSpecificFieldValue(xmlGvyEquipment,"unitClass=","CONTAINER")
            }//Else if for Container Ends

            //Post Primary Unit Msg
            //println('xmlGvyString_Primary ::'+xmlGvyString)
            //gvyBaseClass.sendXml(xmlGvyString)
            gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"null")

            // Post Secondary unit Msg
            //println('xmlGvyEquipment ::'+xmlGvyEquipment)
            //gvyBaseClass.sendXml(xmlGvyEquipment)
            gvyCmisUtil.postMsgForAction(xmlGvyEquipment,gvyBaseClass,"null")

        }catch(Exception e){
            e.printStackTrace()
        }

        return xmlGvyString
    }

    public String setChassisValue(String xmlData, String eventNotes, String unitId, Object gvyCmisUtil)
    {
        def xmlGvyString = xmlData
        try
        {
            Equipment primaryClass = unitId != null ? Equipment.loadEquipment(unitId) : '';
            //Check to see if Container is the primary class
            if((primaryClass instanceof com.navis.argo.business.reference.Container) && eventNotes != null)
            {
                def chassisNbrIndx = eventNotes.indexOf("as")
                if(chassisNbrIndx != -1)
                {
                    //println('chassisNbrIndx ::'+chassisNbrIndx+' chassisNbrIndx+3 :::'+chassisNbrIndx+3)
                    def chassisNbr = eventNotes.substring(chassisNbrIndx+3,eventNotes.indexOf(" ",chassisNbrIndx+3))
                    chassisNbr = chassisNbr != null ? chassisNbr : ''
                    println("chassisNbr ::::::::::"+chassisNbr)
                    def chassisId = chassisNbr.substring(0,chassisNbr.length()-1)
                    def chassisCd = chassisNbr.substring(chassisNbr.length()-1)
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"chassisNumber=",chassisId)
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"chassisCd=",chassisCd)

                    return xmlGvyString;
                }
            }
        }
        catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString;
    }

}