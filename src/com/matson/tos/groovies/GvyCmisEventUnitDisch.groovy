/*
* A1  GR   02/03/2010  PASSPASS Gate Direct Disch & Outgate Scenario
                       Make Chassi=Null if a mount occured right before.
  A2  GR   06/04/2010  Removed MOunt check on PASSPASS Gate Messages
  A3  GR   10/20/10    Added BareChassis Check & NULL to Blank Chassis values
  A4  GR   12/09/10    Added Accessory Check & NULL to Blank Chassis values
  A5  GR   03/08/11    Suppress AVL on DFV for Transhipments
  A6  GR   10/30/11    TOS2.1 SET location=1 for all DFV
  A7  GR   10/30/11    TOS2.1 Overwrite UNIT_DISCH_COMPLETE with UNIT_DISCH
*/

import com.navis.apex.business.model.GroovyInjectionBase

public class GvyCmisEventUnitDisch
{


    public boolean setTranshipValuesforShipToShip(String xmlData, Object event, Object gvyBaseClass)
    {
        println("TESTINGDFV setTranshipValuesforShipToShip begin");
        def xmlGvyString = xmlData
        def msgPosted = false
        def unit = event.getEntity()
        try{
            def category = unit.getFieldValue("unitCategory")
            category = category != null ? category.getKey() : ''
            if(!category.equals('TRSHP'))  {
                return msgPosted
            }

            def ibVesClass = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
            ibVesClass = ibVesClass != null ? ibVesClass.getKey() : ''
            def obVesClass = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
            obVesClass = obVesClass != null ? obVesClass.getKey() : ''

            def aibcarrierId=unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId")
            def dibcarrierId=unit.getFieldValue("unitDeclaredIbCv.cvId")

            def aobcarrierId=unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")
            def intdObCarrierId=unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
            def dobcarrierId=unit.getFieldValue("unitRouting.rtgDeclaredCv.cvId")

            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");

            //VESVOY
            def aidObCarrierId = aobcarrierId != null ? aobcarrierId : (intdObCarrierId != null ? intdObCarrierId : dobcarrierId)
            def adIBCarrierId = aibcarrierId != null ? aibcarrierId : dibcarrierId

// Raghu Iyer : 12/11/2013 Added aobcarrierId.equals('GEN_VESSEL') in OR condition to fix the issue for DFV event posting to GEMS and MNS
            if(ibVesClass.equals('CELL') && (obVesClass.equals('CELL') || aobcarrierId.equals('GEN_VESSEL')) && category.equals('TRSHP')){
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"vesvoy=",adIBCarrierId)
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"misc1=",aidObCarrierId)

                gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"DFV")
                println("TESTINGDFV xmlGvyString 1 "+xmlGvyString);
                //Thread.sleep(4000);
                //gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"AVL")

                msgPosted = true
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        println("TESTINGDFV setTranshipValuesforShipToShip end");
        return msgPosted
    }


    public String passpassDisch(String xmlData,Object unit, Object gvyBaseClass, Object event)
    {
        println("TESTINGDFV passpassDisch begin");
        def xmlGvyString = xmlData
        def groupCode = unit.getFieldValue("unitRouting.rtgGroup.grpId")
        groupCode = groupCode != null ? groupCode : ''
        def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");

        def equiClass =unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
        equiClass = equiClass != null ? equiClass.getKey() : ''
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=","1") //A6
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"msgType=","UNIT_DISCH") //A7

        try
        {
            if(groupCode.equals('PASSPASS'))
            {
                //xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=","1")
                def loc = gvyCmisUtil.getFieldValues(xmlGvyString, "loc=")
                if(loc.equals('null'))
                {
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "loc=","CSX")                          //Setting the sec to 00 for disch DFV PassPass
                    def time = gvyCmisUtil.getFieldValues(xmlGvyString, "aTime=")
                    def timeSub = !time.equals('null') ? time.substring(0,6) : '';
                    def timeChngSec = timeSub.length() > 0 ? timeSub+"00" : '';
                    println("TIME ::"+time+"   TIME-1 ::"+timeSub+"    TIME-2 ::"+timeChngSec);
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "aTime=",timeChngSec)
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "lastATime=",timeChngSec)
                }
                //A2
                /*def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil")
                boolean isCntMounted = gvyEventUtil.holdEventProcessing(event,'UNIT_MOUNT', 2)
                if(isCntMounted){ */
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "chassisNumber=","null");
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "chassisCd=","null");
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "accessory=","null"); //A4

                //}
                println("TESTINGDFV xmlGvyString 2 "+xmlGvyString)
                prinln("TESTINGDFV passpassDisch end");
                return xmlGvyString
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        //A5 - 04/28/2010 Chassis Mounts Only for ALE should go up acets
        def aibcarrierId=unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId")

        if(aibcarrierId != null && !aibcarrierId.startsWith('ALE') && !'CHASSIS'.equals(equiClass)){ //A3
// Commented By Solomon - Start
            //  xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "chassisNumber=","null");
            //  xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "chassisCd=","null");
// Commented By Solomon - End
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "accessory=","null"); //A4
            println("TESTINGDFV in ALE xmlGvyString "+xmlGvyString)
        }
        return xmlGvyString
    }

}