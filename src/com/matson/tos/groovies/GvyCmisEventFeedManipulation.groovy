/*
* SrNo Doer  Date       Change
* A1   GR    05/24/10   Client vesvoy not to include ves Id if already exisit
* A2   GR    07/21/10   Added Clisnt Vesvoy HON Check
* A3   GR    08/22/10   SN4Q change : Added parameter gvyCmisUtil & gvyBaseClass to getRetCustomerAssign
* A4   GR    08/22/10   SN4Q change : Added CLS action for MDA & Return to Customer UnAssign
* A5   GR    11/04/10   SN4Q : set truck=Null for WO and P2
* A6   LC    12/05/12   Added setting Client actual vessel/voyage
* A7   LC    06/18/13   Created separate method for the CMIS_DATA_REFRESH
* A8   KR    08/28/13	Added: Pass trucker to the WO TRANSFER EVENT instead of null.
* A9   KR    07/09/15  Alaska Ports
*/

import com.navis.argo.ContextHelper
import com.navis.inventory.business.atoms.UfvTransitStateEnum

public class GvyCmisEventFeedManipulation {

    public String processCmisEventFeedManipulation(String eventType, String xmlGvyString, Object gvyBaseClass, Object event, Object unit, Object isUnitObj, String equiClass) {
        def xmlGvyData = xmlGvyString
        try {
            Object eventSpecObj = null;
            def groupCode = unit.getFieldValue("unitRouting.rtgGroup.grpId");
            groupCode = groupCode != null ? groupCode : ''
            def _commodityId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId")
            def commodityId = _commodityId != null ? _commodityId : ''
            def designatedTrucker = unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
            def _drayStatus = unit.getFieldValue("unitDrayStatus")
            def drayStatus = _drayStatus != null ? _drayStatus.getKey() : _drayStatus

            def dischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            dischPort = dischPort != null ? dischPort : ''

            //Added To Set the Dport to HON for Inbound units
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            def lkpLocType = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''
            def lkpCarrierId = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")

            def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
            transitState = transitState != null ? transitState.getKey() : ''

            def category = unit.getFieldValue("unitCategory")
            category = category != null ? category.getKey() : ''

            def locationStatus = ''
            if (lkpLocType.equals('VESSEL')) {
                locationStatus = getLocationStatusOnCell(gvyCmisUtil, lkpCarrierId, category, transitState)
                println("locationStatus ::::Feed Manipulation:" + locationStatus)
                if ('4'.equals(locationStatus)) {
                    xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData, "dischargePort=", ContextHelper.getThreadFacility().getFcyId())
                }
            }//If Ends

            //Setting the Client Vesvoy
            def vesselLineOperator = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdBizu.bzuId");
            if (vesselLineOperator != null && !vesselLineOperator.equals('MAT')) {
                def vesvoy = getClientVesVoy(gvyCmisUtil, dischPort, unit)
                xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData, "vesvoy=", vesvoy)
            }

            //def locationStatus = gvyCmisUtil.getFieldValues(xmlGvyData, "locationStatus=")

            //Need to Work on - override the vesvoy for Long Haul to NIS with service [LHH,OHK]
            /* def service = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.cvdService.srvcId");
            service = service != null ? service : ''
            if(service.equals('LHH')){
                   if(lkpLocType.equals())
                xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData,"vesvoy=",lkpCarrierId)
             }*/
            //Commented out as Fileds being set in generic Rules
/*       if(groupCode != null && groupCode.equals('TS')){
        eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
        xmlGvyData = eventSpecObj.getAfterTagStripAssign(xmlGvyData)
      }*/
            /*else if((groupCode.equals('XFER-P2') || groupCode.equals('XFER-WO') //A5
                || groupCode.equals('XFER-SI')) && drayStatus.equals('OFFSITE') ){
               eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
               xmlGvyData = eventSpecObj.getTransferEventChanges(xmlGvyData, designatedTrucker) //
            }
            else if(groupCode.equals('COMSVC')){
               eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyCmisUnitPropertyUpdate");
               xmlGvyData = eventSpecObj.AssignTrucker(xmlGvyData,gvyCmisUtil,unit )
            }
            else  if(groupCode.equals('TTNU') || groupCode.equals('MDA') || eventType.equals('RETURN_TO_CUSTOMER_ASSIGN')){
               eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
               xmlGvyData = eventSpecObj.getRetCustomerAssign(xmlGvyData,unit,gvyCmisUtil,gvyBaseClass) //A3
            }
            else if (groupCode.equals('YB') || eventType.equals('YB_UNASSIGN')) {
               eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
               xmlGvyData = eventSpecObj.getYBEvent(xmlGvyData,designatedTrucker,eventType)
            }*/
            //SIT EVENT
            else if (commodityId.equals('SIT') && drayStatus.equals('OFFSITE')) {
                println('INSIDE THE SIT ASSIGN CONDITION')
                eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventSIT");
                def appendObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
                xmlGvyData = eventSpecObj.processSitAssign(xmlGvyData, event, unit, commodityId, drayStatus)
            }
            /*else if(eventType.equals('TRANSFER_CANCEL')){
               eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
               xmlGvyData = eventSpecObj.getTransferCancelEvent(xmlGvyData, designatedTrucker, commodityId)
            }*/ else if (eventType.equals('COMMUNITY_SERVICE_UNASSIGN')) {
                eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
                xmlGvyData = eventSpecObj.getCommunityServiceUnAssign(xmlGvyData)
            } else if (eventType.equals('SIT_UNASSIGN')) {
                def eventSitObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventSIT");
                eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
                xmlGvyData = eventSitObj.processSitUnAssign(xmlGvyData, eventSpecObj)
            } else if (eventType.equals('MDA_UNASSIGN') || eventType.equals('RETURN_TO_CUSTOMER_UNASSIGN')) { //A4
                if (lkpLocType.equals("TRUCK") && UfvTransitStateEnum.S70_DEPARTED.equals(unit.getFieldValue("unitActiveUfv.ufvTransitState"))) {
                    xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData, "action=", "CLS")
                    xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData, "lastAction=", "CLS")
                    xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData, "locationStatus=", "3")
                    //IF ON TRUCK and DEPT then CLS ELSE PDD
                }
            }

            //EQUIP HOLD/RELEASE FIELD MANIPULATION
            def equipHold = gvyBaseClass.getGroovyClassInstance("GvyCmisEquipmentHoldEvents")
            xmlGvyData = equipHold.setEquipmentHoldFields(xmlGvyData, event, unit)


        } catch (Exception e) {
            e.printStackTrace()
        }
        return xmlGvyData
    }

    public String getLocationStatusOnCell(Object gvyCmisUtil, String lkpCarrierId, String category, String transitState) {
        def locationStatus = ''
        try {
            if (gvyCmisUtil != null && gvyCmisUtil.getVesselClassType(lkpCarrierId).equals('CELL')) {
                if (category.equals('THRGH')) {
                    locationStatus = '2'
                } else if (transitState.equals('S60_LOADED') || transitState.equals('S70_DEPARTED')) {
                    locationStatus = '2'
                } else if (transitState.equals('S20_INBOUND')) {
                    locationStatus = '4'
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return locationStatus
    }//Method Ends

    public String getClientVesVoy(Object gvyCmisUtil, String dischPort, Object unit) {
        def vesvoy = '%'; def intIbVesId = ''; def intIbVygNbr = '';
        try {
            /*def islandPort = gvyCmisUtil.isNISPort(dischPort)
            if (islandPort || 'HON'.equals(dischPort)){
               intIbVesId = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdVessel.vesId")
               intIbVygNbr = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdIbVygNbr")
            }else{*/
            intIbVesId = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdVessel.vesId")
            intIbVygNbr = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdObVygNbr")
            /*}*/
            vesvoy = intIbVygNbr.contains(intIbVesId) ? intIbVygNbr : intIbVesId + intIbVygNbr
        } catch (Exception e) {
            e.printStackTrace()
        }
        return vesvoy
    }//Method Ends

    /*
        A7 - Added for CMIS_DATA_REFRESH
    */

    public String processCmisEventFeedManipulationForRefresh(String eventType, String xmlGvyString, Object gvyBaseClass, Object event, Object unit, Object isUnitObj, String equiClass) {
        def xmlGvyData = xmlGvyString
        try {
            Object eventSpecObj = null;
            def groupCode = unit.getFieldValue("unitRouting.rtgGroup.grpId");
            groupCode = groupCode != null ? groupCode : ''
            def _commodityId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId")
            def commodityId = _commodityId != null ? _commodityId : ''
            def designatedTrucker = unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
            def _drayStatus = unit.getFieldValue("unitDrayStatus")
            def drayStatus = _drayStatus != null ? _drayStatus.getKey() : _drayStatus

            def dischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            dischPort = dischPort != null ? dischPort : ''

            //Added To Set the Dport to HON for Inbound units
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            def lkpLocType = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''
            def lkpCarrierId = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")

            def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
            transitState = transitState != null ? transitState.getKey() : ''

            def category = unit.getFieldValue("unitCategory")
            category = category != null ? category.getKey() : ''

            def locationStatus = ''
            if (!eventType.equals('UNIT_REROUTE')&& lkpLocType.equals('VESSEL')) {
                locationStatus = getLocationStatusOnCell(gvyCmisUtil, lkpCarrierId, category, transitState)
                println("locationStatus ::::Feed Manipulation:" + locationStatus)
                if ('4'.equals(locationStatus)) {
                    xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData, "dischargePort=", ContextHelper.getThreadFacility().getFcyId())
                }
            }//If Ends

            //Setting the Client Vesvoy, actualVessel, actualVoyage - A6
            def vesselLineOperator = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdBizu.bzuId");
            if (vesselLineOperator != null && !vesselLineOperator.equals('MAT')) {
                def vesvoy = getClientVesVoy(gvyCmisUtil, dischPort, unit)
                xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData, "vesvoy=", vesvoy)
                def actualVessel = vesvoy.length() > 5 ? vesvoy.substring(0, 3) : 'null';
                def actualVoyage = vesvoy.length() > 5 ? vesvoy.substring(3) : 'null';
                xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData, "actualVessel=", actualVessel);
                xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData, "actualVoyage=", actualVoyage);

            }

            /*else if((groupCode.equals('XFER-P2') || groupCode.equals('XFER-WO') //A5
                    || groupCode.equals('XFER-SI')) && drayStatus.equals('OFFSITE') ){
                eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
                xmlGvyData = eventSpecObj.getTransferEventChanges(xmlGvyData)
            }
            else if(groupCode.equals('COMSVC')){
                eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyCmisUnitPropertyUpdate");
                xmlGvyData = eventSpecObj.AssignTrucker(xmlGvyData,gvyCmisUtil,unit )
            }
            else  if(groupCode.equals('TTNU') || groupCode.equals('MDA') || eventType.equals('RETURN_TO_CUSTOMER_ASSIGN')){
                eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
                xmlGvyData = eventSpecObj.getRetCustomerAssign(xmlGvyData,unit,gvyCmisUtil,gvyBaseClass) //A3
            }
            else if (groupCode.equals('YB') || eventType.equals('YB_UNASSIGN')) {
                eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
                xmlGvyData = eventSpecObj.getYBEvent(xmlGvyData,designatedTrucker,eventType)
            }*/
            //SIT EVENT
            else if (commodityId.equals('SIT') && drayStatus.equals('OFFSITE')) {
                println('INSIDE THE SIT ASSIGN CONDITION')
                eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventSIT");
                def appendObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
                xmlGvyData = eventSpecObj.processSitAssign(xmlGvyData, event, unit, commodityId, drayStatus)
            } else if (eventType.equals('TRANSFER_CANCEL')) {
                eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
                xmlGvyData = eventSpecObj.getTransferCancelEvent(xmlGvyData, designatedTrucker, commodityId)
            } else if (eventType.equals('COMMUNITY_SERVICE_UNASSIGN')) {
                eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
                xmlGvyData = eventSpecObj.getCommunityServiceUnAssign(xmlGvyData)
            } else if (eventType.equals('SIT_UNASSIGN')) {
                def eventSitObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventSIT");
                eventSpecObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue");
                xmlGvyData = eventSitObj.processSitUnAssign(xmlGvyData, eventSpecObj)
            } else if (eventType.equals('MDA_UNASSIGN') || eventType.equals('RETURN_TO_CUSTOMER_UNASSIGN')) { //A4
                if (lkpLocType.equals("TRUCK") && UfvTransitStateEnum.S70_DEPARTED.equals(unit.getFieldValue("unitActiveUfv.ufvTransitState"))) {
                    xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData, "action=", "CLS")
                    xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData, "lastAction=", "CLS")
                    xmlGvyData = gvyCmisUtil.eventSpecificFieldValue(xmlGvyData, "locationStatus=", "3")
                    //IF ON TRUCK and DEPT then CLS ELSE PDD
                }
            }

            //EQUIP HOLD/RELEASE FIELD MANIPULATION
            def equipHold = gvyBaseClass.getGroovyClassInstance("GvyCmisEquipmentHoldEvents")
            xmlGvyData = equipHold.setEquipmentHoldFields(xmlGvyData, event, unit)


        } catch (Exception e) {
            e.printStackTrace()
        }
        return xmlGvyData
    }
}