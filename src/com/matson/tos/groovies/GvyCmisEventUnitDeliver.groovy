package com.matson.tos.groovies
/*
 * SrNo Doer Date      Change
 * A1   GR   06/21/10  Handeled Null for OB TruckId
 * A2   GR   09/09/10  Gems SIT Change : MDA Check on Group Code. Removed check on hold
 * A3   GR   02/14/11  Node2 issue : Added Outgate action to message to post from General notice
 * A4   GR   04/12/11  Added Vesseltype before setting vesvoy for OGT Msg
 * A5   GR   04/06/11  Change loadport to facility Id to compute leg correctly.
 * A6   GR   06/21/11  Set BKG values on MTY Outgate POD,DEST,VESVOY
 * A7   GR   07/12/11  Set Vesvoy for Cmis
 * A8   GR   11/08/11  Add YB BARGE to flex02
 * A9   GR   12/05/11  renamed attribute flex02 to ybBarge
 * A10   GR   12/23/11 change for SIT-YB
 * A11   GR   03/20/12 Added Change for YB-SIT Outgate
 */
import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.RoutingPoint
import org.apache.log4j.Logger

public class GvyCmisEventUnitDeliver {

    //Event UNIT_DELIVER Condition
    /*
    For outgate events, set the CMIS action as follows:
    1] Regular HON outgate ? OGC
    2] Transfer to P2/SI/WO ? OGT (group = XFER*; dray status = dray out and back)
    3] SIT ? OGC (commodity = SIT; dray status = dray out and back)
    4] YB ? OGA (group =  YB; dray status = transfer to other facility)
    5] MDA ? OGF (MDA hold on equipment) // Only MDA HOLD Left
    6] Mty with booking number ? OGS
    7] Outgate chassis only - OGP
    */

    def ACTION = "action='null'"
    def LAST_ACTION = "lastAction='null'"
    def cmisActionList = ''
    boolean planDispChng = false
    def planDisp = null;
    def gvyCmisUtil = null;
    def facilityId = null;
    def freightkindKey = null;
    def bookingNumber = null;
    def group = null;
    def commodity = null;
    def equiClass = null;

    public String processUnitDeliver(String xmlGvyData, Object event, Object gvyBaseClass) {
        def xmlGvyString = xmlGvyData
        def unit = event.getEntity()

        try {
            def holdsList = unit.getFieldValue("unitAppliedHoldOrPermName")
            holdsList = holdsList != null ? holdsList : ''
            def carrierVisitGkey = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvdGkey")
            carrierVisitGkey = carrierVisitGkey != null ? carrierVisitGkey : ''

            def drayStatus = unit.getFieldValue("unitDrayStatus")
            drayStatus = drayStatus != null ? drayStatus.getKey() : ''

            group = unit.getFieldValue("unitRouting.rtgGroup.grpId")
            group = group != null ? group : ''

            commodity = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId")
            commodity = commodity != null ? commodity : ''

            def lkpSlot = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
            lkpSlot = lkpSlot != null ? lkpSlot : ''

            cmisActionList = gvyBaseClass.getGroovyClassInstance("GvyCmisListAction");
            gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            def gvyGateObj = gvyBaseClass.getGroovyClassInstance("GvyCmisGateData");
            def gateIdDesc = gvyGateObj.getGateId(carrierVisitGkey)
            gateIdDesc = gateIdDesc != null ? gateIdDesc : ''
            def gateLane = gateIdDesc; //PASSPASS, WO GATE, P2

            def gvyRtgObj = gvyBaseClass.getGroovyClassInstance("GvyCmisRtgProcessDetail");
            planDisp = gvyRtgObj.getPlanDisp(drayStatus, group, lkpSlot)

            //Mapping Cmis Action for OUT_GATE Event
            xmlGvyString = getUnitDeliver(xmlGvyString, unit, gvyCmisUtil, holdsList, gateLane, gvyBaseClass)
            //Out Gate Mapping Ends

        } catch (Exception e) {
            e.printStackTrace()
        }

        return xmlGvyString
    } //ProcessActionMapping Ends

    //UNIT_DELIVER
    private String getUnitDeliver(String xmlGvyData, Object unit, Object gvyCmisUtil, String holdsList, String gateLane, Object gvyBaseClass) {
        def xmlGvyString = xmlGvyData

        def drayStatus = unit.getFieldValue("unitDrayStatus")
        drayStatus = drayStatus != null ? drayStatus.getKey() : ''

        def _freightkind = unit.getFieldValue("unitFreightKind")
        freightkindKey = _freightkind != null ? _freightkind.getKey() : ''

        facilityId = unit.getFieldValue("unitActiveUfv.ufvFacility.fcyId")
        facilityId = facilityId != null ? facilityId : ''

        System.out.LOGGER.info("TESTING facilityId::::" + facilityId);

        bookingNumber = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr");
        def obTruckId = unit.getFieldValue("unitActiveUfv.ufvActualObCv.carrierOperatorId")
        obTruckId = obTruckId != null ? obTruckId : ''

        equiClass = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
        equiClass = equiClass != null ? equiClass.getKey() : ''

        //Assigning the outgate trucker Id
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "truck=", obTruckId);

        //Accessory MG check
        boolean mgFlag = acryMgCheck(unit, equiClass)
        if (mgFlag) {
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationRun=", 'MG')
        }

        //Gate Lane Field manipulation
        if (gateLane.equals("PASSPASS")) {
            xmlGvyString = setPassPassGateFields(xmlGvyString, gvyCmisUtil)
        } else if (gateLane.equals("PIER2")) {
            xmlGvyString = setP2GateFields(unit, xmlGvyString, gvyCmisUtil)
        } else if (gateLane.equals("WO GATE")) {
            xmlGvyString = setWOGateFields(unit, xmlGvyString, gvyCmisUtil)
        }

        //CHASSIS
        if (equiClass.equals('CHASSIS')) {
            xmlGvyString = setOutgateAction(xmlGvyString, "OGP")
            xmlGvyString = setOgpOutgate(xmlGvyString, gvyCmisUtil)
        }
        //MDA_HOLD
        def holdPresent = holdsList != null ? holdsList.indexOf("MDA") : -1

        //A2
        if (unit.getFieldValue("unitRouting.rtgGroup.grpId") in ['MDA']) {
            xmlGvyString = setOutgateAction(xmlGvyString, "OGF");
            xmlGvyString = setOgcOgsOgfOutgate(xmlGvyString, gvyCmisUtil, unit)
        }
        //TRANSFER EVENT
        if (group.equals('XFER-P2') || group.equals('XFER-WO') || group.equals('XFER-SI')) {
            xmlGvyString = setOutgateAction(xmlGvyString, "OGT")
            xmlGvyString = setOgtOutGate(xmlGvyString, gvyCmisUtil)
        }
        //A10 -SIT
        if (commodity.equals('SIT') && drayStatus.equals('OFFSITE')) {
            xmlGvyString = setOgcOgsOgfOutgate(xmlGvyString, gvyCmisUtil, unit)
        }
        //YB
        if (group.equals('YB') && drayStatus.equals('TRANSFER')) {
            xmlGvyString = setOutgateAction(xmlGvyString, "OGA")
            xmlGvyString = setOgaOutgate(xmlGvyString, gvyCmisUtil, unit, obTruckId)
        }
        //EMPTY
        if (freightkindKey.equals('MTY') && bookingNumber != null) {
            xmlGvyString = setOutgateAction(xmlGvyString, "OGS")
            xmlGvyString = setOgcOgsOgfOutgate(xmlGvyString, gvyCmisUtil, unit)
        }
        if (facilityId.equals(ContextHelper.getThreadFacility().getFcyId()) || "KQA".equalsIgnoreCase(facilityId)) {
            xmlGvyString = setOutgateAction(xmlGvyString, "OGC")
            xmlGvyString = setOgcOgsOgfOutgate(xmlGvyString, gvyCmisUtil, unit)
            if (commodity.equals('SIT') && group.equals('YB')) { //A10
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "3")
            }
        }


        return xmlGvyString
    }//Method unitDeliver Ends

    //A3 Starts- Add Outgate Action to Message
    public String setOutgateAction(String xml, String aAction) {
        def outXml = xml;
        gvyCmisUtil = gvyCmisUtil != null ? gvyCmisUtil : gvyCmisUtil.gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
        def tempvalue = gvyCmisUtil.getFieldValues(xml, "action=");
        if (tempvalue != null && !tempvalue.startsWith('OG')) {
            outXml = gvyCmisUtil.eventSpecificFieldValue(outXml, "action=", aAction);
            outXml = gvyCmisUtil.eventSpecificFieldValue(outXml, "lastAction=", aAction);
        }
        return outXml;
    }//A3 Ends

    //Method Validates if the Accessory is an MG
    public boolean acryMgCheck(Object unit, String equiClass) {
        def unitChasAcryType = ''
        def unitCtrEqptype = ''
        if (equiClass.equals('CONTAINER')) {
            //Check if unit has MG mounted on It
            unitCtrEqptype = unit.getUnitAcryEqtypeId() // For Container
            unitCtrEqptype = unitCtrEqptype != null ? unitCtrEqptype : ''
        } else if (equiClass.equals('CONTAINER')) {
            //Acry for unit Chassis
            def unitChasAcryObj = unit.getUnitChsAccessory();
            if (unitChasAcryObj != null) {
                unitChasAcryType = unitChasAcryObj.getEqEquipType() != null ? unitChasAcryObj.getEqEquipType().getEqtypId() : null
                unitChasAcryType = unitChasAcryType != null ? unitChasAcryType : ''
            }
        }
        if (unitCtrEqptype.startsWith('MG') || unitChasAcryType.startsWith('MG')) {
            return true;
        }
        return false;
    }

    //Method for OGC/OGS/OGF
    public String setOgcOgsOgfOutgate(String xmlGvyData, Object gvyCmisUtil, Object unit) {
        def xmlGvyString = xmlGvyData
        freightkindKey = freightkindKey != null ? freightkindKey : unit.getFieldValue("unitFreightKind").getKey();
        def bookingNbr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr")

        try {
            //Set Plan Disp and Location status
            if (!planDispChng) {
                if ('YB'.equals(group) && 'SIT'.equals(commodity)) {//A11
                } else if (planDisp != null && planDisp.length() > 0) {
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", planDisp)
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "planDisp=", "null")
                } else {
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "3")
                }
            }
            //A06 - Condition to Handel Actual VesVoy at MTY OUTGATE
            if (freightkindKey.equals('MTY') && bookingNbr != null) {
                def pod1 = null; def dest = null; def vesId = null;
                if (bookingNbr != null) {
                    pod1 = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoPod1.pointId");
                    dest = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoDestination");
                    vesId = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoVesselVisit.cvId");
                    LOGGER.info("pod1=" + pod1 + "   dest=" + dest + "   vesId=" + vesId)
                }

                if (bookingNbr != null && pod1 != null && dest != null && vesId != null) {
                    setBkgValueOnMtyOgt(unit, pod1, vesId, dest);
                    xmlGvyString = gvyCmisUtil.setVesvoyFields(unit, xmlGvyString, vesId, "CELL");
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "dischargePort=", pod1);
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "dPort=", dest);
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "vesvoy=", vesId); //A7
                }
            }
            return setChassisIdToPmd(unit, equiClass, xmlGvyString);

        } catch (Exception e) {
            e.printStackTrace()
        }
        return xmlGvyString;
    }


    public String setChassisIdToPmd(Object inUnit, String inEquiClass, String inXmlGvyString) {

        System.out.LOGGER.info("Calling getChassisIdFromUnit : " + inUnit + "inEquiClass" + inEquiClass);

        if (inEquiClass == null || inEquiClass == "" || inUnit == null) {

            System.out.LOGGER.info("found invalid input : " + inUnit + "inEquiClass" + inEquiClass);

            return inXmlGvyString;
        }

        def carriage = inEquiClass.equals('CONTAINER') ? inUnit.getUnitCurrentlyAttachedChassisId() : inUnit.getFieldValue("unitId");

        System.out.LOGGER.info("carriage :" + carriage);

        if ((carriage != null && carriage != "") && !"OWN".equalsIgnoreCase(carriage)) {

            def chassisId = carriage.substring(0, carriage.length() - 1)

            System.out.LOGGER.info("chassisId :" + chassisId);

            if (chassisId != null && chassisId != "") {
                System.out.LOGGER.info("setting chassisId :" + chassisId);
                inXmlGvyString = gvyCmisUtil.eventSpecificFieldValue(inXmlGvyString, "pmd=", chassisId);
            }
            System.out.LOGGER.info("returning :" + chassisId);
            return inXmlGvyString;

        }
        System.out.LOGGER.info("set null pmd :");
        return inXmlGvyString = gvyCmisUtil.eventSpecificFieldValue(inXmlGvyString, "pmd=", "null");

    }

    //Method for OGA
    public String setOgaOutgate(String xmlGvyData, Object gvyCmisUtil, Object unit, String obTruckId) {
        def xmlGvyString = xmlGvyData
        def intdObCarrierId = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
        //xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dischargePort=",'HON')
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "misc1=", obTruckId)
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "truck=", 'YBUU')
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "ybBarge=", intdObCarrierId) //A8
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "flex02=", intdObCarrierId) //A8
        def vesvoy = gvyCmisUtil.getFieldValues(xmlGvyString, "vesvoy=")
        if (planDisp != null && planDisp.length() > 0) { //A11
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", planDisp)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "planDisp=", "null")
        }
        if (vesvoy.length() == 0) {
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "actualVessel=", "")
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "actualVoyage=", "")
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "leg=", "")
        } else if (vesvoy.length() > 5) {
            xmlGvyString = gvyCmisUtil.setVesvoyFields(unit, xmlGvyString, vesvoy, "CELL");
        }

        return xmlGvyString
    }

    //Method for OGP
    public String setOgpOutgate(String xmlGvyData, Object gvyCmisUtil) {
        def xmlGvyString = xmlGvyData
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", '3')
        return xmlGvyString
    }

    //Method for OGT
    public String setOgtOutGate(String xmlGvyData, Object gvyCmisUtil) {
        def xmlGvyString = xmlGvyData
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "planDisp=", "null")
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", '3')
        return xmlGvyString
    }

    //Method Manipulates Fields for P2 gate
    public String setP2GateFields(Object unit, String xmlGvyData, Object gvyCmisUtil) {
        def xmlGvyString = xmlGvyData
        try {
            def category = unit.getFieldValue("unitCategory")
            category = category != null ? category.getKey() : ''

            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationTier=", "T2")
            if (category.equals('IMPRT')) {
                def dibcarrierId = unit.getFieldValue("unitDeclaredIbCv.cvId")
                dibcarrierId = dibcarrierId != null ? dibcarrierId : null
                def vesType = gvyCmisUtil.getVesselClassType(dibcarrierId) //A4
                if (vesType.equals('CELL')) {
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "vesvoy=", dibcarrierId)
                }
            } else if (category.equals('EXPRT')) {
                def dobcarrierId = unit.getFieldValue("unitRouting.rtgDeclaredCv.cvId")
                dobcarrierId = dobcarrierId != null ? dobcarrierId : null
                def vesType = gvyCmisUtil.getVesselClassType(dobcarrierId)
                if (vesType.equals('CELL')) {
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "vesvoy=", dobcarrierId)
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return xmlGvyString
    }

    //Method Manipulates Fields for WO gate
    public String setWOGateFields(Object unit, String xmlGvyData, Object gvyCmisUtil) {
        def xmlGvyString = xmlGvyData
        try {
            def category = unit.getFieldValue("unitCategory")
            category = category != null ? category.getKey() : ''

            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationTier=", "T3")
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "loc=", "WOA 1")
            if (category.equals('IMPRT')) {
                def dibcarrierId = unit.getFieldValue("unitDeclaredIbCv.cvId")
                dibcarrierId = dibcarrierId != null ? dibcarrierId : null
                def vesType = gvyCmisUtil.getVesselClassType(dibcarrierId)//A4
                if (vesType.equals('CELL')) {
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "vesvoy=", dibcarrierId)
                }
            } else if (category.equals('EXPRT')) {
                def dobcarrierId = unit.getFieldValue("unitRouting.rtgDeclaredCv.cvId")
                dobcarrierId = dobcarrierId != null ? dobcarrierId : null
                def vesType = gvyCmisUtil.getVesselClassType(dobcarrierId)
                if (vesType.equals('CELL')) {
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "vesvoy=", dobcarrierId)
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return xmlGvyString
    }

    //Method Manipulates Fields for PassPass gate
    public String setPassPassGateFields(String xmlGvyData, Object gvyCmisUtil) {
        def xmlGvyString = xmlGvyData
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "loc=", "null")
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "cell=", "null")
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "pmd=", "null")
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "truck=", "null")

        //Set Plan Disp and Location status
        if (planDisp != null && planDisp.length() > 0) {
            planDispChng = true
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", planDisp)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "planDisp=", "null")
        } else {
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "A")
        }

        return xmlGvyString
    }//Pass Pass method ends

    /*
     * A6 - Method Sets POD,Destination and vesvoy onto the N4 unit
     * For Empty Outgates with the Booking Information
    */

    public void setBkgValueOnMtyOgt(Object unit, Object pod1, String vesId, String dest) {
        if (pod1 != null && dest != null && vesId != null) {
            def routing = unit.getUnitRouting();
            routing.setRtgPOD1(RoutingPoint.findRoutingPoint(pod1));
            unit.setFieldValue("unitFlexString10", vesId);
            unit.setFieldValue("unitGoods.gdsDestination", dest);
        }
    }

    /**
     * This method triggers CLS Message for UNIT_LOAD to Client Vessels, here MAE Vessels
     * @param initialGvyXml Unit_deliver
     * @param event event
     * @param gvyBaseClass gvyBaseClass
     * @return CLS xml
     */
    public String processCLSForLoadClientVessel(String initialGvyXml, Object event, Object gvyBaseClass) {
        LOGGER.info("START -- processing GvyCmisEventUnitDeliver.processCLSForLoadClientVessel");
        def xmlGvyString = initialGvyXml;
        def unit = event.getEntity();

        /*def drayStatus = unit.getFieldValue("unitDrayStatus")
        drayStatus = drayStatus != null ? drayStatus.getKey() : ''
        LOGGER.info("drayStatus =======" + drayStatus);

        def posSlot = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
        posSlot = posSlot != null ? posSlot : ''
        LOGGER.info("posSlot =======" + posSlot);

        def rtgGroupId = unit.getFieldValue("unitRouting.rtgGroup.grpId")
        rtgGroupId = rtgGroupId != null ? rtgGroupId : ''
        LOGGER.info("rtgGroupId =======" + rtgGroupId);

        LOGGER.info("calling pdsp to calculate plan disposition");
        def GvyCmisRtgProcessDetail = gvyBaseClass.getGroovyClassInstance("GvyCmisRtgProcessDetail");
        def planDisp1 = GvyCmisRtgProcessDetail.getPlanDisp(drayStatus, rtgGroupId, posSlot)
        LOGGER.info("planDisp1 =======" + planDisp1);*/

        def outboundCarrierOperator = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvOperator.bzuId");
        def sendCLS = Boolean.FALSE;
        if ("MAE".equalsIgnoreCase(outboundCarrierOperator))
            sendCLS = Boolean.TRUE;

        def facilityId = unit.getFieldValue("unitActiveUfv.ufvFacility.fcyId")
        facilityId = facilityId != null ? facilityId : ''
        LOGGER.info("facilityId =======" + facilityId);

        if (sendCLS
                && (facilityId.equalsIgnoreCase('ANK') || facilityId.equalsIgnoreCase('DUT') || facilityId.equalsIgnoreCase('KDK'))) {
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "msgType=", "RETURN_TO_CUSTOMER_ASSIGN");
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "action=", "CLS");
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "lastAction=", "CLS");
        }
        LOGGER.info("groovy plug in  processUnitDeliver ");
        LOGGER.info("XML Message 	" + xmlGvyString);
        LOGGER.info("END -- processing GvyCmisEventUnitDeliver.processCLSForLoadClientVessel");
        return xmlGvyString;
    }

    private static final Logger LOGGER = Logger.getLogger(GvyCmisEventUnitDeliver.class);
}//Class Ends