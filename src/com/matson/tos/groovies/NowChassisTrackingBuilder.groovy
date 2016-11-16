/*
* Change History
* Test Prod URL Call
* Stripped chassis CheckDigit
* Need to test ChasType flavours
* Pulled out transit state from message

* Srno  Doer Date      Change
* A1    GR   050510    Opend JMS Messages flow to NOW
*                      Dismount being called at the Gate for MG'S
* A2    GR   05/28/10  Added Bare Chassis passing code to NOW
* A3    GR   06/04/10  Added passpass Gate Code Check
* A4    GR   07/06/10  Added Chassis Type BM
* A5    GR   07/08/10  UNIT_LOAD added to EventBound for Now list to pass
* A6    GR   10/21/10  Added RefDataLookup Code for ChassisRfid WSDL
* A8    GR   08/16/11  2.1 Updated Email Method
* A7    GR   08/16/11  Added Chassis Axis-WS Code
*/

import com.navis.inventory.business.units.UnitEquipment
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper;
import nowsol_ChasTrack.*;
import com.navis.argo.business.model.Yard
import com.navis.xpscache.yardmodel.api.IXpsYardBin;
import com.navis.xpscache.yardmodel.api.IXpsYardBlock;
import com.navis.xpscache.yardmodel.api.IXpsYardModel;
import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.spatial.business.api.IBinModel;
import com.navis.spatial.business.model.AbstractBin;
import com.navis.spatial.business.model.block.AbstractBlock;
import com.navis.spatial.business.model.block.BinModelHelper;
import com.navis.yard.business.model.*;


public class NowChassisTrackingBuilder extends GroovyInjectionBase{

    def equiDetached = null;
    def emailSender = null;
    private static final String emailTo = '1aktosdevteam@matson.com';
//private static final String emailTo = '1aktosdevteam@matson.com';

/*String nowMessage ="<?xml version='1.0' encoding='UTF-8'?>"+
"<argo:snx xmlns:argo='http://www.navis.com/argo' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:schemaLocation='http://www.navis.com/argo snx.xsd'>"+
"<unit transit-state='YARD' unique-key='MATZ9022294' snx-update-note='Test' id='MATZ9022294'>"+
"  <equipment eqid='MATZ9022294' role='PRIMARY' height-mm='2591' type='C40' class='CHS' />"+
"  <position slot='A1218' location='SI' loc-type='YARD' />"+
"</unit></argo:snx>"; */


    public void nowMessagesProcessor(Object event, Object api){
        def unit  = event.getEntity()
        //1. isUserEvntRecorder
        //2. xmlBuilder
        //Client code call to post Messages
        try{
            boolean isEvntBoundForNow = isEventBoundForNow(event)
            boolean isUserRecorder = isEvntRecUser(event)
            println("isEvntBoundForNow="+isEvntBoundForNow+"   isUserRecorder:"+isUserRecorder)
            if(!isUserRecorder){
                println("UserRecorder : User Condition not satisfied")
                return;
            }else if(!isEvntBoundForNow){
                println("Event Condition not satisfied")
                return;
            }

            String nowMessage = xmlBuilder(unit,equiDetached,'')
            String nowSnx = setSnx(nowMessage)

            //postNowMsg(nowSnx)

        }catch(Exception e){
            e.printStackTrace();
            emailSender = emailSender != null ? emailSender : getGroovyClassInstance("EmailSender")
            emailSender.custSendEmail(emailTo, "N4ToNow: Error Unit "+(unit != null ? unit.unitId : "UNIT OBJ NULL")+" Message Processing Error ", e.getMessage());
        }

    }

/*
* Method to build xml for NOW service snx format
*/
    public String xmlBuilder(Object unitObj, Object equiDetached, String notes){
        def snx = null;
        def unit = null;
        try{
            if(equiDetached != null){
                unit = equiDetached
            }else{
                unit = unitObj
            }
            def chasType = null;
            def unitId = unit.unitId
            def length = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqLengthMm")
            def height = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqHeightMm")
            def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
            transitState = transitState != null ? transitState.getKey() : ''
            def tState = transitState.split("_")
            transitState = tState[1]
            def primEqType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId")
            def primEqRole = unit.getFieldValue("unitPrimaryUe.ueEqRole")
            primEqRole = primEqRole != null ? primEqRole.getKey() : ''
            def primaryClass = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
            def primaryClassCode = EquipClassEnum.CONTAINER.equals(primaryClass) ? "CTR" : (EquipClassEnum.CHASSIS.equals(primaryClass) ? "CHS" : "CTR")
            if("CHS".equals(primaryClassCode)){
                UnitEquipment unitEquipment = unit.getUnitPrimaryUe();
                Equipment primChasEq = unitEquipment.ueEquipment
                chasType = getChassisType(primChasEq, primaryClass,unit)
                unitId = unitId.substring(0,unitId.length()-1)
                //println("CHS - unitEquipment="+unitEquipment+"  primChasEq="+primChasEq+" chasType="+chasType)
            }

            //Secondary Equi Values
            UnitEquipment unitEquipment = unit.getUnitCarriageUe();
            def chasid = null; def chasIdNoChkDigit = null; def chaslength = null; def chasHeight = null; def chasRole = null;
            def chasClass = null; def chasTypeIso= null;

            if(unitEquipment != null){
                Equipment chasEq = unitEquipment.ueEquipment
                chasid = chasEq.eqIdFull
                chasIdNoChkDigit = chasid.substring(0,chasid.length()-1)
                chaslength = chasEq.eqLengthMm
                chasHeight = chasEq.eqHeightMm
                chasRole = unitEquipment.ueEqRole
                chasRole = chasRole != null ? chasRole.getKey() : ''
                chasClass = chasEq.eqEquipType.eqtypClass
                chasClass = EquipClassEnum.CHASSIS ? "CHS" : ''
                chasTypeIso = chasEq.eqEquipType.eqtypId
                chasType = getChassisType(chasEq, primaryClass, unit)
                //println("chasIdNoChkDigit="+chasIdNoChkDigit+"chaslength="+chaslength+"chasHeight="+chasHeight+"chasRole="+chasRole+"chasClass="+chasClass+"chasTypeIso="+chasTypeIso+" chasType="+chasType)
            }

            def location=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")
            location = location != null ? location : ''
            def locType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            locType = locType != null ? locType.getKey() : ''
            //lkpSlot = lkpSlot != null ? lkpSlot : ''
            //println("unitId="+unitId+"length="+length+"height="+height+"transitState="+transitState+"primEqType="+primEqType+"primEqRole="+primEqRole+"primaryClassCode="+primaryClassCode+"  equiDetached="+equiDetached)
            def lkpSlot = null
            if('VESSEL'.equals(locType)){
                lkpSlot = unit.unitActiveUfv.ufvLastKnownPosition.posSlot
                lkpSlot = lkpSlot != null ? lkpSlot : ''
            }else{
                lkpSlot = formatYardPosition(unit)
            }


// build XML using Groovy built-in capabilities
            def writer = new StringWriter()
            def builder = new groovy.xml.MarkupBuilder(writer)
            def addNotes = notes
            def snxnotes = getSnxNotes(primaryClass,length,chaslength,chasType,addNotes)

            builder.unit(id: unitId, 'unique-key': unitId, 'snx-update-note': snxnotes)
                    {
                        equipment(eqid: unitId, class:primaryClassCode, role:primEqRole, type: primEqType, 'height-mm': height)
                        if(unitEquipment != null){
                            equipment(eqid: chasIdNoChkDigit, class:chasClass, role:chasRole, type: chasTypeIso, 'height-mm': chasHeight)
                        }
                        position('loc-type': locType, location: location, slot : lkpSlot)
                    }

            snx = writer.toString()


        }catch(Exception e){
            throw e;
        }
        return snx
    }//Method Ends


/*
* Method Sends Data back to the NOW service
  2.1 - if doer contains Gate dont send
  2.2 - if Doer is XPS dont send
  2.3 - if Doer is Jms/Snx(Steves Method Dont send)
*/
    public boolean isEvntRecUser(Object event)
    {
        boolean isUser = true;
        def doer = event.getEvent().getEvntAppliedBy();
        def evntNotes = event.getEvent().getEventNote();
        evntNotes = evntNotes != null ? evntNotes : ''

        //Dont pass NOW Message for below Event Recorders
        if(doer.contains('WO') || doer.contains('wo') || doer.contains('GATE') || doer.contains('now') || doer.contains('xps') || doer.contains('gate') || doer.contains('passpass') || doer.contains('PASS')){
            isUser = false;
        }
        return isUser;
    }

    /* Method to validate Event Message Posting
    * Send unit Mount and Dismounts for only Chassis[Carriage]
    * Send yard Moves only for Bare chassis
    */
    public boolean isEventBoundForNow(Object event){
        def isBoundForNow = false

        def eventType = event.getEvent().getEventTypeId()
        def evntNotes = event.getEvent().getEventNote();
        evntNotes = evntNotes != null ? evntNotes : ''
        def unit = event.getEntity();
        def primaryClass = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
        def isClassChassis = EquipClassEnum.CHASSIS.equals(primaryClass) ? true : false

        if(eventType.equals('UNIT_MOUNT') && unit.getUnitCarriageUe() != null){
            isBoundForNow = true;
        }else if (eventType.equals('UNIT_DISMOUNT') && !evntNotes.contains('swiped') && !evntNotes.startsWith('MATG')){
            equiDetached = getEquiDetached(event)
            def unitClass = equiDetached != null ? equiDetached.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass") : null
            if(EquipClassEnum.CHASSIS.equals(unitClass)){
                isBoundForNow = true;
            }
        }else if(eventType.equals('UNIT_YARD_MOVE') && isClassChassis){
            isBoundForNow = true;
        }else if(eventType.equals('UNIT_SNX_UPDATE') && isClassChassis){ //A1
            isBoundForNow = true;
        }else if(eventType.equals('UNIT_LOAD') && isClassChassis){ //A2
            isBoundForNow = true;
        }
        return isBoundForNow
    }


//Formats Messages SNX String
    public static String setSnx( String xml)
    {
        if ( xml == null)
            return null;

        StringBuffer ret = new StringBuffer();

        ret.append( "<?xml version='1.0' encoding='UTF-8'?>");
        ret.append( "<argo:snx xmlns:argo='http://www.navis.com/argo' ");
        ret.append( "xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' ");
        ret.append( "xsi:schemaLocation='http://www.navis.com/argo snx.xsd'>");
        ret.append(xml);
        ret.append( "</argo:snx>");
        return ret.toString();
    }

//Test 1. Triaxel and Mg Triaxel Setting
    public String getChassisType(Object chasEq, Object primaryClass, Object unit){
        def chasType = null;
        def chasId = chasEq.eqIdFull
        def isTriaxle = chasEq.eqEquipType.eqtypIsChassisTriaxle
        def acryId = null
        if(EquipClassEnum.CHASSIS.equals(primaryClass)){
            acryId = unit.getUnitAcryEquipIds()
        }else if (EquipClassEnum.CONTAINER.equals(primaryClass)){
            acryId = unit.getUnitChsAcryId()
        }

        if(chasId.startsWith('YC89')){chasType = "BM"; }
        else if(acryId != null && isTriaxle){ chasType = "MGX"; }
        else if(isTriaxle){ chasType = "TX"; }
        else if(acryId != null ){ chasType = "MG"; }
        else{ chasType = "STD" }

        //println("primaryClass="+primaryClass+" acryId="+acryId+" isTriaxle="+isTriaxle)
        return chasType
    }

/*
* 1. Method sets notes with : Cntr & Chas Length, ChasType
* 2. Sets notes for rowRefresh
*/
    public String getSnxNotes(Object primaryClass,Object length, Object chaslength, Object chasType, String addNotes)
    {
        def chassisType = chasType != null ? 'ChassType:'+chasType : 'ChassType:NA'
        String cntrLength = (EquipClassEnum.CONTAINER.equals(primaryClass) ? mmtoFeetConversion(length) : '')
        String chassLength = (EquipClassEnum.CHASSIS.equals(primaryClass) ? mmtoFeetConversion(length) : (chaslength != null ? mmtoFeetConversion(chaslength) : ''))
        String cntrLengthStr = cntrLength.trim().length() > 0  ? 'CntrLength:'+cntrLength : 'CntrLength:NA'
        String chassLengthStr = chassLength.trim().length() > 0 ? 'ChassLength:'+chassLength : 'ChassLength:NA'
        def notes = chassisType+','+cntrLengthStr+','+chassLengthStr+','+addNotes
        return notes
    }

/*
* Method Returns Detached Equip Unit Object
*/
    public Object getEquiDetached(Object event){
        def eventNotes = event.getEvent().getEventNote()
        def equid = eventNotes.split(' ')
        def injBase = new GroovyInjectionBase();
        def complex = ContextHelper.getThreadComplex();
        def unitFinder = injBase.getUnitFinder();
        def eq = Equipment.loadEquipment(equid[0]);
        if(!EquipClassEnum.CHASSIS.equals(eq.eqClass)){
            return null;
        }
        def unit = unitFinder.findActiveUnit(complex, eq);
        return unit
    }

//MM to FEET conversion
    public String mmtoFeetConversion(Object length){
        def feetLngth = length * 0.0032808399
        feetLngth = Math.round(feetLngth)
        return ""+feetLngth
    }


    public String formatYardPosition(Object unit)
    {
        String binName=null; String tierName=null;
        String finalSlotFmt = null;
        try{
            def locId = unit.unitActiveUfv.ufvLastKnownPosition.posLocId
            def positionLocType = unit.unitActiveUfv.ufvLastKnownPosition.posLocType
            def inYardSlot = unit.unitActiveUfv.ufvLastKnownPosition.posSlot
            if(LocTypeEnum.VESSEL.equals(positionLocType)){
                return finalSlotFmt = inYardSlot
            }else if(LocTypeEnum.TRUCK.equals(positionLocType)){
                return finalSlotFmt = "TRUCK"
            }

            if(inYardSlot == null || inYardSlot.trim().length() == 0){ return ''; }
            println ("inYardSlot ------------------> "+ inYardSlot);
            def inFacility = ContextHelper.getThreadFacility()
            Yard inYard =  Yard.findYard("SI", inFacility);
            int lastDot = inYardSlot.lastIndexOf('.');

            if (inYardSlot.startsWith("S") || inYardSlot.startsWith("V") || inYardSlot.startsWith("O")){
                binName = inYardSlot.replace('.','');
                tierName = "";
            }
            else {
                if (lastDot > 0) {
                    binName = inYardSlot.substring(0, lastDot);
                    tierName = inYardSlot.substring(lastDot + 1, inYardSlot.length());
                } else {
                    binName = inYardSlot;
                    tierName = "";
                }
            }
            String formattedYardSlotName = binName;
            finalSlotFmt = formattedYardSlotName.replace('-','.')
            int lastDotPos = finalSlotFmt.lastIndexOf('.');
            if(finalSlotFmt.length() > 5){
                println("If  finalSlotFmt---------------------> "+ finalSlotFmt);
                finalSlotFmt  = finalSlotFmt.substring(0,2)+"."+finalSlotFmt.substring(2,4)+"."+finalSlotFmt.substring(4);
            } else {
                println("Else finalSlotFmt ---------------------> "+ finalSlotFmt);
                finalSlotFmt  = finalSlotFmt.substring(0,3)+"."+finalSlotFmt.substring(3);
            }

            //if(lastDotPos > 4){
            //finalSlotFmt = finalSlotFmt.replace('.','')
            //finalSlotFmt  = finalSlotFmt.substring(0,2)+"."+finalSlotFmt.substring(2,4)+"."+finalSlotFmt.substring(4)
            //}

            /*YardBinModel yardModel =  com.navis.yard.business.model.YardBinModel.findYardBinModelFromYardCodeAndOwner("SI", inYard)
            //IXpsYardModel yardModel = inYard.getYardModel();
            binName = yardModel.getUiFullPositionWithTier(BinNameTypeEnum.STANDARD, binName);
            def inCtrLength = unit.getBasicLength()
            int lastDot = inYardSlot.lastIndexOf('.');
            if (lastDot > 0) {
             binName = inYardSlot.substring(0, lastDot);
             tierName = inYardSlot.substring(lastDot + 1, inYardSlot.length());
            } else {
             binName = inYardSlot;
             tierName = "";
            }

            AbstractBin bin = yardModel.findDescendantBinFromInternalSlotString(binName, null);
            //IXpsYardBin bin = yardModel.getBin(binName);
            if(bin == null){
                throw new Exception("Unit="+unit.unitId+" has an Invalid Yard Position Please Correct N4");
            }
            //String formattedYardSlotName = bin.getUiFullPositionWithTier(inCtrLength, tierName);
        String formattedYardSlotName = binName;
            finalSlotFmt = formattedYardSlotName.replace('-','.')
            int lastDotPos = finalSlotFmt.lastIndexOf('.');
            if(lastDotPos > 4){
                finalSlotFmt = finalSlotFmt.replace('.','')
                finalSlotFmt  = finalSlotFmt.substring(0,2)+"."+finalSlotFmt.substring(2,4)+"."+finalSlotFmt.substring(4)
            }*/

        }catch(Exception e){
            throw e;
        }
        println("finalSlotFmt ---------------------> "+ finalSlotFmt);
        return finalSlotFmt;
    }//Method Ends

    //Method - makes call to NOW webservice
    public void postNowMsg(String nowSnx){
        try
        {
            //move to RefData
            println("nowsnx="+nowSnx);

            if(nowSnx == null && nowSnx.trim().length() == 0){
                return;
            }


            //String wsdlurl = "http://192.168.170.244:9301/?wsdl";
            String wsdlurl = getGroovyClassInstance("GvyRefDataLookup").getChasRfidUrl(); //A6
            println("wsdlurl : NOW ::: "+ wsdlurl);
            ChasTrack_ServiceLocator chasSrvLoc = new ChasTrack_ServiceLocator();
            println("chasProxy :::::: "+chasSrvLoc);
            chasSrvLoc.setChasTrackEndpointAddress(wsdlurl);
            ChasTrack_PortType chasProxy = chasSrvLoc.getChasTrack();
            println("chasProxy :::::: "+chasProxy);
            String resultValue = chasProxy.updateChassis(nowSnx);
            //ChasTrack_Service_Impl chasTrackImpl = new ChasTrack_Service_Impl(wsdlurl);
            //ChasTrack_PortType chasTrackPortType = chasTrackImpl.getChasTrack();
            //String resultValue = chasTrackPortType.updateChassis(nowSnx);
            println("resultValue="+resultValue);


        }catch(Exception e){
            e.printStackTrace()
            throw e;
        }
    }

}//Class Ends