/*
* SrNo Doer  Date       Change
* A1   GR    08/22/10   SN4Q change : Add parameters to method getRetCustomerAssign and action CLS for Acets
* A2   GR    10/06/10   Added Vesvoy, Actual Values for UNIT_STUFF
* A3   KR    08/28/13   Added: Pass trucker to the WO TRANSFER EVENT.
*/

import com.navis.inventory.business.atoms.UfvTransitStateEnum;

public class GvyEventSpecificFldValue {

    //For COMMUNITY_SERVICE_UNASSIGN
    public String getCommunityServiceUnAssign(String xmlGvyData)
    {
        def  xmlGvyString = xmlGvyData
        xmlGvyString = addEventSpecificFldValue(xmlGvyString,"truck=","null",null,null,null);
        xmlGvyString = addEventSpecificFldValue(xmlGvyString,"consignee=","null",null,null,null);
        xmlGvyString = addEventSpecificFldValue(xmlGvyString,"shipper=","null",null,null,null);
        xmlGvyString = addEventSpecificFldValue(xmlGvyString,"cargoNotes=","null",null,null,null);
        return xmlGvyString;
    }

    //TRANSFER EVENT CHANGE
    public String getTransferEventChanges(String xmlGvyData, String trucker) // A3
    {
        def  xmlGvyString = xmlGvyData
        xmlGvyString = addEventSpecificFldValue(xmlGvyString,"truck=","%",null,null,null);

        return xmlGvyString;
    }

    //TRANSFER CANCEL EVENT
    public String getTransferCancelEvent(String xmlGvyData, String trucker, String commodity)
    {
        def  xmlGvyString = xmlGvyData
        xmlGvyString = addEventSpecificFldValue(xmlGvyString,"truck=","%",null,null,null);

        return xmlGvyString;
    }

    //YB ASSIGN/UNASSIGN EVENT
    public String getYBEvent(String xmlGvyData, String trucker, String msgType)
    {
        def  xmlGvyString = xmlGvyData
        if(trucker == null || trucker.length() == 0) {
            xmlGvyString = addEventSpecificFldValue(xmlGvyString,"truck=","%",null,null,null);
        }else{
            xmlGvyString = addEventSpecificFldValue(xmlGvyString,"truck=",trucker,null,null,null);
        }

        return xmlGvyString;
    }


    //MDA_ASSIGN and  RETURN_TO_CUSTOMER_ASSIGN
    public String getRetCustomerAssign(String xmlGvyData,Object unit, Object gvyCmisUtil,Object gvyBaseClass)
    {
        def xmlGvyString = xmlGvyData
        try
        {
            def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''

            def locationStatus = lkpLocType.equals("TRUCK") ? '3' : ''
            def planDisp = getFieldValues(xmlGvyString, "planDisp=");
            if(locationStatus.equals('3')){
                xmlGvyString = addEventSpecificFldValue(xmlGvyString,"locationStatus=",planDisp,null,null,null);
                xmlGvyString = addEventSpecificFldValue(xmlGvyString,"planDisp=","null",null,null,null);
                //-- A1 IF on TRUCK & DEPARTED CLS ELSE PDU
                if(UfvTransitStateEnum.S70_DEPARTED.equals(unit.getFieldValue("unitActiveUfv.ufvTransitState"))){
                    xmlGvyString = addEventSpecificFldValue(xmlGvyString,"action=","CLS",null,null,null);
                    xmlGvyString = addEventSpecificFldValue(xmlGvyString,"lastAction=","CLS",null,null,null);
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }

        return xmlGvyString;
    }

    //UNIT_STUFF
    public String getEventUnitStuff(String xmlGvyData, String commodity, Object unit, Object event)
    {
        def  xmlGvyString = xmlGvyData
        def equiType=unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId")
        equiType = equiType != null ? equiType : ''
        commodity = commodity != null ? commodity : ''

        def eventObj = event.getEvent()
        def doer = eventObj.getEvntAppliedBy();
        def cmisAction = 'LDC'
        try
        {
            if(equiType.startsWith("F") && (commodity.equals('AUTO') || commodity.equals('AUTOCY') || commodity.equals('AUTOCON'))){
                if(!doer.contains('ACETS')){ cmisAction = 'LDR' }
                else{ cmisAction = 'ALDR' }
            }
            else if(commodity!= null && (commodity.equals('AUTO') || commodity.equals('AUTOCY') || commodity.equals('AUTOCON') ) ){
                if(!doer.contains('ACETS')){ cmisAction = 'LDA'  }
                else{  cmisAction = 'ALDA'  }
            }
            else{
                if(!doer.contains('ACETS')){ cmisAction = 'LDC'  }
                else{ cmisAction = 'ALDC' }
            }

            //A2
            def loadPort = unit.getFieldValue("unitRouting.rtgPOL.pointId")
            def dischargePort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")

            def unitRouting = unit.getUnitRouting();
            def declaredObCvObj = unitRouting.getRtgDeclaredCv();
            def facility = com.navis.argo.ContextHelper.getThreadFacility()
            //setting the Intended OB carrier
            unit.getUfvForFacilityNewest(facility).setUfvIntendedObCv(declaredObCvObj);
            unit.getUfvForFacilityNewest(facility).setUfvActualObCv(declaredObCvObj);

            def declaredObCv = ""+declaredObCvObj
            def actualVessel = declaredObCv.length() > 5 ? declaredObCv.substring(0,3) : 'null'
            def actualVoyage = declaredObCv.length() > 5 ? declaredObCv.substring(3) : 'null'
            def leg = loadPort+'_'+dischargePort


            //A2
            xmlGvyString = addEventSpecificFldValue(xmlGvyString,"vesvoy=",declaredObCv,null,null,null)
            xmlGvyString = addEventSpecificFldValue(xmlGvyString,"actualVessel=",actualVessel,null,null,null)
            xmlGvyString = addEventSpecificFldValue(xmlGvyString,"actualVoyage=",actualVoyage,null,null,null)
            xmlGvyString = addEventSpecificFldValue(xmlGvyString,"leg=",leg,null,null,null)

            xmlGvyString = addEventSpecificFldValue(xmlGvyString,"lastAction=",cmisAction,null,null,null);
            xmlGvyString = addEventSpecificFldValue(xmlGvyString,"action=",cmisAction,null,null,null);

        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString;
    }


    public static String getFieldValues(String xmlGvyString, String field)
    {
        String fieldValue = ''
        try
        {
            def fieldIndx = xmlGvyString.indexOf(field);
            def equalsIndx = xmlGvyString.indexOf("=",fieldIndx);
            def nextspace = xmlGvyString.indexOf("'", equalsIndx+2);
            fieldValue  = xmlGvyString.substring(equalsIndx+2, nextspace);
            //println("equalsIndx:"+equalsIndx+"  nextspace:"+nextspace+" oldValue:"+fieldValue);
        }catch(Exception e){
            e.printStackTrace()
        }
        return fieldValue;
    }

    public String addEventSpecificFldValue(String xmlGvyData,String field,String newFieldValue,String appendDir,String StripDir,String stripChar)
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
                int nextspace = xmlGvyString.indexOf("'", equalsIndx+2);
                oldValue = xmlGvyString.substring(equalsIndx+2, nextspace);
                //Append New Value
                if(appendDir != null )
                {
                    if(appendDir.equals("L")){
                        newValue =  oldValue.equals('null') ? newFieldValue : newFieldValue+oldValue;
                        //  println("Append_Left:"+newValue);
                    }
                    else if(appendDir.equals("R")){
                        newValue = oldValue.equals('null') ? newFieldValue : oldValue+newFieldValue;
                        //  println("Append_Right:"+newValue);
                    }
                }
                //Strip Value
                else if(StripDir != null)
                {
                    if(StripDir.equals("L"))
                    {
                        if(oldValue.startsWith(newFieldValue)){
                            newValue = oldValue.indexOf(stripChar) != -1 ?   oldValue.substring(oldValue.indexOf(stripChar)+1): oldValue;
                        }else{
                            newValue = oldValue.equals(newFieldValue) ? 'null' : oldValue ;
                        }
                        // println("Strip_Left:"+newValue);
                    }
                    else if(StripDir.equals("R"))
                    {
                        if(oldValue.endsWith(newFieldValue)){
                            newValue = oldValue.indexOf(stripChar) != -1 ? oldValue.substring(0,oldValue.indexOf(stripChar)): oldValue;
                        }else{
                            newValue = oldValue.equals(newFieldValue) ? 'null' : oldValue ;
                        }
                    }
                }//Strip Ends
                else if(oldValue.equals("null") ){
                    newValue = newFieldValue;
                }
                else{
                    //CHECK FOR VALUE HERE
                    newValue = newFieldValue;
                }
                println("oldValue ::"+oldValue+"  newValue :::"+newValue)
                String oldXmlValue = field+"'"+oldValue+"'";
                String newXmlValue = field+"'"+newValue+"'";
                // println("oldXmlValue ::"+oldXmlValue+"  newXmlValue :::"+newXmlValue);
                xmlGvyString = xmlGvyString.replace(oldXmlValue, newXmlValue);
            }//IF Ends
        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString;
    }// Method addEventSpecificFldValue Ends
}//Class Ends