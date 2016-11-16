/*
* Srno Doer Date       Change
* A1   GR   06/25/10   Added Check to Overwrite misc1=% Only for LH vessels
* A2   GR   08/24/10   Added GEN_TRUCK & BARGE to vesvoy=BLANK section
* A3   GR   08/30/10   Misc1 - value check for IB VygNbr
* A4   GR   09/13/10   vesvoy condition for SIT (YB units)
* A5   GR   08/26/10   Added BARGE to misc1=BLANK section
* A6   GR   09/17/10   Add Reroute criteria to set vesvoy for DAS
                       if POD updated and its HON or NIS then compute vesvoy for DAS
* A7   GR   10/03/10   Added null to actual veseel and voyage and compute for unit roll
* A8   GR   11/17/10   TRUCK value being set to Vessel under else condition
*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.vessel.business.atoms.VesselTypeEnum
import com.navis.vessel.business.operation.VesselClass

public class GvyCmisPositionProcessDetailNewVes {

//MISC1
    def misc1Value = '%'
    def aibcarrierMode = ''

    public String doIt(Object u, Object gvyTxtMsgFmt, String eventType, Object event) {
        def positionProFldAttr = ''
        try {
            def gvyBaseClass = new GroovyInjectionBase()
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");

            def obcarrierChng = gvyEventUtil.wasFieldChanged(event, "ufvIntendedObCv")
            obcarrierChng = obcarrierChng != null ? obcarrierChng : ''
            //A6
            //def podChng = gvyEventUtil.wasFieldChanged(event, "rtgPOD1")
            //podChng = podChng != null ? podChng : ''

            def previousDischPort = gvyEventUtil.getPreviousPropertyAsString(event, "rtgPOD1");
            def gvyDomQueryObj = gvyBaseClass.getGroovyClassInstance("GvyCmisDomainQueryUtil")
            previousDischPort = gvyDomQueryObj.lookupRtgPOD(previousDischPort)
            previousDischPort = previousDischPort != null ? previousDischPort : ""
            def category = u.getFieldValue("unitCategory");

            def currentDischPort = u.getFieldValue("unitRouting.rtgPOD1.pointId");
            /*def isHawaiiPort = UnitCategoryEnum.IMPORT.equals(category) &&
                               (ContextHelper.getThreadFacility().getFcyId().equals(previousDischPort) || gvyCmisUtil.isNISPort(previousDischPort)) ||
                               (ContextHelper.getThreadFacility().getFcyId().equals(currentDischPort) || gvyCmisUtil.isNISPort(currentDischPort))*/
            //A6 Ends
            //Verify Event Notes for unitRefersh
            def gvyEventObj = event.getEvent()
            def eventNotes = gvyEventObj.getEventNote()
            eventNotes = eventNotes != null ? eventNotes : ""

            //Verifying if the event Changes the position Field
            def gvyFldUpdtObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventFieldUpdateFilter");
            boolean evntUpdatesFlg = gvyFldUpdtObj.evntFilterOnPositionFldChng(eventType)
            if (!eventNotes.contains("Unit Correction") && evntUpdatesFlg
                    || ((eventType.equals('UNIT_REROUTE') /*&& !isHawaiiPort*/) && (eventType.equals('UNIT_REROUTE') && obcarrierChng.equals(Boolean.FALSE)))) {
                positionProFldAttr = positionPercentageValue(gvyTxtMsgFmt)
                return positionProFldAttr
            }

            def lkpLocType = u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            def lkpLocTypeKey = lkpLocType != null ? lkpLocType.getKey() : ''

            def lkpCarrierId = u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")

            def _category = u.getFieldValue("unitCategory")
            def categoryKey = _category != null ? _category.getKey() : ''

            def _transitState = u.getFieldValue("unitActiveUfv.ufvTransitState")
            def transitStateKey = _transitState != null ? _transitState.getKey() : ''

            def _drayStatus = u.getFieldValue("unitDrayStatus")
            def drayStatusKey = _drayStatus != null ? _drayStatus.getKey() : _drayStatus

            def _freightkind = u.getFieldValue("unitFreightKind")
            def freightkind = _freightkind != null ? _freightkind.getKey() : ''

            def dischPort = u.getFieldValue("unitRouting.rtgPOD1.pointId")
            dischPort = dischPort != null ? dischPort : ''

            def ibVesselType = u.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
            def ibVesselTypeKey = ibVesselType != null ? ibVesselType.getKey() : gvyCmisUtil.getVesClassTypeWithNoVisitEntry(u)

            def obVesselType = u.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
            def obVesselTypeKey = obVesselType != null ? obVesselType.getKey() : ''

            //ReArrange Fields Used by VESVOY / TRUCK / MISC
            aibcarrierMode = u.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCarrierMode")
            aibcarrierMode = aibcarrierMode != null ? aibcarrierMode.getKey() : ''
            def inBoundCarrier = aibcarrierMode.equals('VESSEL') ? (ibVesselTypeKey.trim().length() > 1 ? ibVesselTypeKey : 'GEN_VESSEL') : aibcarrierMode

            def aobcarrierMode = u.getFieldValue("unitActiveUfv.ufvActualObCv.cvCarrierMode")
            aobcarrierMode = aobcarrierMode != null ? aobcarrierMode.getKey() : ''
            def outBoundCarrier = aobcarrierMode.equals('VESSEL') ? (obVesselTypeKey.trim().length() > 1 ? obVesselTypeKey : 'GEN_VESSEL') : aobcarrierMode

            def aibcarrierId = ''
            def aibcarrierOperatorId = ''
            def aobcarrierId = ''

            //A11 - Change Made for Truck Code and TruckId switch on IB and OB carrier
            if (aibcarrierMode.equals('TRUCK')) {
                aibcarrierId = u.getFieldValue("unitActiveUfv.ufvActualIbCv.carrierOperatorId")
                aibcarrierOperatorId = u.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId")
            } else {
                aibcarrierId = u.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId")
                aibcarrierOperatorId = u.getFieldValue("unitActiveUfv.ufvActualIbCv.carrierOperatorId")
            }
            if (aobcarrierMode.equals('TRUCK')) {
                aobcarrierId = u.getFieldValue("unitActiveUfv.ufvActualObCv.carrierOperatorId")
            } else {
                aobcarrierId = u.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")
            }

            //A11 - def aibcarrierId=u.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId")
            //A11 - def aibcarrierOperatorId=u.getFieldValue("unitActiveUfv.ufvActualIbCv.carrierOperatorId")
            def dibcarrierId = u.getFieldValue("unitDeclaredIbCv.cvId")
            //A11- def aobcarrierId=u.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")
            def intdObCarrierId = u.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
            def dobcarrierId = u.getFieldValue("unitRouting.rtgDeclaredCv.cvId")

            //VESVOY
            def idOBCarrierId = intdObCarrierId != null ? intdObCarrierId : dobcarrierId
            def adIBCarrierId = aibcarrierId != null ? aibcarrierId : dibcarrierId

            //IB_VYG_NBR
            def ibVygNbr = ''
            def facility = u.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.cvdCv.cvFacility.fcyId")
            if (facility != null && facility.equals(ContextHelper.getThreadFacility().getFcyId())) {
                def vesId = u.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdVessel.vesId")
                def VygNbr = u.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdIbVygNbr")
                ibVygNbr = VygNbr != null && VygNbr.contains(vesId) ? VygNbr : vesId + VygNbr  //A4
                //println("facility ::"+facility+"  IbVygNbr::::"+ibVygNbr)
            }

            //def vesvoy = getVesVoy(lkpLocTypeKey,lkpCarrierId,categoryKey,transitStateKey,idOBCarrierId, aibcarrierId,dibcarrierId,freightkind, aibcarrierMode, u, gvyCmisUtil,dischPort)
            def vesvoy = '%'; // Changes made - 05/16/2013
            def vesvoyAttr = gvyTxtMsgFmt.doIt('vesvoy', vesvoy)
            //println('VESVOY : '+vesvoy)

            //TRUCK
            def truckValue = ''
            def aiObCarrierId = aobcarrierId != null ? aobcarrierId : intdObCarrierId
            //truckValue = getTruck(lkpLocTypeKey,drayStatusKey,transitStateKey,aiObCarrierId,aibcarrierId,u,gvyCmisUtil,dischPort,lkpCarrierId,outBoundCarrier)
            truckValue = '%'; // Changes made - 05/16/2013

            //Ship to Ship
            println('IB_CARRIER :' + inBoundCarrier + '  OB_CARRIER: ' + outBoundCarrier)
            println('LKP_LOC_TYPE:' + lkpLocTypeKey + 'LKP_CARRIER_ID:' + lkpCarrierId)
            def aidObCarrierId = aobcarrierId != null ? aobcarrierId : (intdObCarrierId != null ? intdObCarrierId : dobcarrierId)
            if (inBoundCarrier.equals('CELL') && outBoundCarrier.equals('CELL')) {
                //misc1Value= getShipToShip(lkpLocTypeKey,lkpCarrierId,aibcarrierId,aobcarrierId,aidObCarrierId,adIBCarrierId,gvyCmisUtil,dischPort,categoryKey)
                println('MISC1_1 : ' + misc1Value)
            }
            //Ship to Barge
            else if (inBoundCarrier.equals('CELL') && outBoundCarrier.equals('BARGE')) {
                ArrayList arrList = getShipToBarge(lkpLocTypeKey, lkpCarrierId, aobcarrierId, aidObCarrierId)
                //misc1Value= arrList.get(0)
                //println('MISC1_2 : '+misc1Value)
                truckValue = arrList.get(1).trim().length() > 1 ? arrList.get(1) : truckValue
                println('truckValue_2 : ' + truckValue)
            }
            //Barge to Ship
            else if (inBoundCarrier.equals('BARGE') && outBoundCarrier.equals('CELL')) {
                ArrayList arrList = getBargeToShip(lkpLocTypeKey, lkpCarrierId, adIBCarrierId, ibVygNbr)
                //misc1Value= arrList.get(0)
                //println('MISC1_3 : '+misc1Value)
                truckValue = arrList.get(1).trim().length() > 1 ? arrList.get(1) : truckValue
                println('truckValue_3 : ' + truckValue)
            }
            //Barge to Barge
            else if (inBoundCarrier.equals('BARGE') && outBoundCarrier.equals('BARGE')) {
                ArrayList arrList = getBargeToBarge(lkpLocTypeKey, lkpCarrierId, aibcarrierId, aobcarrierId, aidObCarrierId, adIBCarrierId, ibVygNbr)
                //misc1Value = arrList.get(0)
                //println('MISC1_4 : '+misc1Value)
                truckValue = arrList.get(1).trim().length() > 1 ? arrList.get(1) : truckValue
                println('truckValue_4 : ' + truckValue)
            }
            //Barge to AnyOtherCarrier
            else if (inBoundCarrier.equals('BARGE') && (outBoundCarrier.equals('TRUCK') || outBoundCarrier.equals('UNKNOWN') || outBoundCarrier.equals('GEN_VESSEL'))) {
                ArrayList arrList = getBargeToOtherCarriers(lkpLocTypeKey, lkpCarrierId, aibcarrierId, ibVygNbr)
                //misc1Value = arrList.get(0)
                //println('MISC1_5 : '+misc1Value)
                truckValue = arrList.get(1).trim().length() > 1 ? arrList.get(1) : truckValue
                println('truckValue_5 : ' + truckValue)
            }
            //TRUCK to BARGE
            else if (inBoundCarrier.equals('TRUCK') && outBoundCarrier.equals('BARGE')) {
                ArrayList arrList = getTruckToBarge(lkpLocTypeKey, lkpCarrierId, aidObCarrierId, aobcarrierId, idOBCarrierId)
                //misc1Value = arrList.get(0)
                //println('MISC1_6 : '+misc1Value)
                truckValue = arrList.get(1).trim().length() > 1 ? arrList.get(1) : truckValue
                println('truckValue_6 : ' + truckValue)
            }
            //REMOVE - Last Truck Check - (Put of the Ingate I think)
            /*  if(aibcarrierMode.equals('TRUCK')){
                      println('aibcarrierMode >>>'+aibcarrierMode+"     aibcarrierOperatorId >>>"+aibcarrierOperatorId)
                      truckValue = aibcarrierOperatorId
              } */
            if (truckValue.equals('GEN_TRUCK') || truckValue.equals('GEN_VESSEL')
                    || truckValue.equals('GEN_CARRIER')) {
                truckValue = ''
            }
            /* if('BARGE'.equals(misc1Value)){ //A3
                 misc1Value = ''
             }*/
            println("truckValueTESTING---" + truckValue)
            if (truckValue == null) {
                truckValue = '%'
            }
            truckValue = '%';//Changes made - 05/16/2013
            def truckAttr = gvyTxtMsgFmt.doIt('truck', truckValue)
            def vesClassType = getVesselClassType(lkpCarrierId) //A1
            //MISC1-Override misc1 value for Long haul vessel to NIS
            def misc1Attr = ''
            println("vesClassTypeBEFOREMISC1---" + vesClassType)
            println("misc1ValueBEFORESETTING----" + misc1Value)
            if (transitStateKey.equals("S60_LOADED") && !'BARGE'.equals(vesClassType)) { //A1
                misc1Attr = gvyTxtMsgFmt.doIt('misc1', '%')
            } else {
                //if (misc1Value == null ||''.equals(misc1Value)) {
                misc1Value = '%'
                //}
                //println("misc1ValueAFTERSETTING----"+misc1Value)
                misc1Attr = gvyTxtMsgFmt.doIt('misc1', misc1Value)
            }
            println('FINAL_TRUCK :' + truckValue + '  FINAL_MISC1:' + misc1Value)

            //LOAD PORT & DPORT
            def loadPort = u.getFieldValue("unitRouting.rtgPOL.pointId")
            def dischargePort = u.getFieldValue("unitRouting.rtgPOD1.pointId")

            //ACTUAL VESSEL,ACTUAL VOYAGE,LEG
            def actualVessel = '%'
            def actualVoyage = '%'
            def leg = '%'
            if (lkpLocTypeKey.equals('VESSEL') && 'BARGE'.equals(vesClassType)) {
                actualVessel = misc1Value.length() > 6 ? misc1Value.substring(0, 3) : 'null'  //A7
                actualVoyage = misc1Value.length() > 6 ? misc1Value.substring(3, 6) : 'null'
                //leg = misc1Value.length() > 6 ? misc1Value.substring(6) : 'null'
            } else if (lkpLocTypeKey.equals('VESSEL') && 'CELL'.equals(vesClassType)) {
                actualVessel = lkpCarrierId.length() > 5 ? lkpCarrierId.substring(0, 3) : 'null'
                actualVoyage = lkpCarrierId.length() > 5 ? lkpCarrierId.substring(3) : 'null'
                //leg = loadPort+'_'+dischargePort
            }
            actualVessel = '%';//Changes made - 05/16/2013
            actualVoyage = '%';//Changes made - 05/16/2013

            def actualVesselAttr = gvyTxtMsgFmt.doIt('actualVessel', actualVessel)
            def actualVoyageAttr = gvyTxtMsgFmt.doIt('actualVoyage', actualVoyage)
            def legAttr = gvyTxtMsgFmt.doIt('leg', leg)
            println('ACTUAL VESSEL : ' + actualVessel + ' ACTUAL VOYAGE: ' + actualVoyage + ' leg : ' + leg)

            positionProFldAttr = vesvoyAttr + truckAttr + misc1Attr + actualVesselAttr + actualVoyageAttr + legAttr
            println('positionProFldAttr : ' + positionProFldAttr)

        } catch (Exception e) {
            e.printStackTrace()
        }

        return positionProFldAttr


    }//Method doit() Ends

    private ArrayList getTruckToBarge(String lkpLocTypeKy, String lkpCarrId, String aidObCarrId, String aobcarrId, String idOBCarrId) {
        def misc1 = ''
        def truck = ''
        def bargeCode = ''
        ArrayList arrLst = new ArrayList()
        try {
            if (lkpLocTypeKy.equals('VESSEL') && getVesselClassType(lkpCarrId).equals('BARGE')) {
                misc1 = aidObCarrId
                bargeCode = misc1 != null && misc1.length() > 2 ? misc1.substring(0, 3) : ''
                truck = '9' + bargeCode
            } else if (lkpLocTypeKy.equals('TRUCK')) {
                misc1 = aobcarrId
            } else if (lkpLocTypeKy.equals('YARD')) {
                misc1 = idOBCarrId
            }
            arrLst.add(misc1)
            arrLst.add(truck)
        } catch (Exception e) {
            e.printStackTrace()
        }
        return arrLst
    }

    private ArrayList getBargeToOtherCarriers(String lkpLocTypeKy, String lkpCarrId, String aibcarrId, String IbVygNbr) {
        def misc1 = ''
        def truck = ''
        def bargeCode = ''
        ArrayList arrLst = new ArrayList()
        try {
            if (lkpLocTypeKy.equals('VESSEL') && getVesselClassType(lkpCarrId).equals('BARGE')) {
                misc1 = aibcarrId
                bargeCode = misc1 != null && misc1.length() > 2 ? misc1.substring(0, 3) : ''
                truck = '9' + bargeCode
                println("***LCROUCH TEST***");
                println("bargeCode-->" + bargeCode);
                println("truck--->" + truck);
                println("***LCROUCH TEST***");
            }
            if (lkpLocTypeKy.equals('VESSEL') || lkpLocTypeKy.equals('YARD')) {
                //A12-  misc1 = adIBCarrId
                misc1 = IbVygNbr
            }
            arrLst.add(misc1)
            arrLst.add(truck)
        } catch (Exception e) {
            e.printStackTrace()
        }
        return arrLst
    }

    private ArrayList getBargeToBarge(String lkpLocTypeKy, String lkpCarrId, String aibcarrId, String aobcarrId, String aidObCarrId, String adIBCarrId, String IbVygNbr) {
        def misc1 = ''
        def truck = ''
        def bargeCode = ''
        ArrayList arrLst = new ArrayList()
        try {
            if (lkpLocTypeKy.equals('VESSEL')) {
                if (lkpCarrId.equals(aibcarrId)) {
                    misc1 = adIBCarrId
                    bargeCode = misc1 != null && misc1.length() > 2 ? misc1.substring(0, 3) : ''
                    truck = '9' + bargeCode
                } else if (lkpCarrId.equals(aobcarrId)) {
                    misc1 = aidObCarrId
                    bargeCode = misc1 != null && misc1.length() > 2 ? misc1.substring(0, 3) : ''
                    truck = '9' + bargeCode
                }
            }
            if (lkpLocTypeKy.equals('YARD')) {
                //A12-  misc1 = adIBCarrId
                misc1 = IbVygNbr
            }
            arrLst.add(misc1)
            arrLst.add(truck)
        } catch (Exception e) {
            e.printStackTrace()
        }
        return arrLst
    }

    private ArrayList getBargeToShip(String lkpLocTypeKy, String lkpCarrId, String adIBCarrId, String IbVygNbr) {
        def misc1 = ''
        def truck = ''
        ArrayList arrLst = new ArrayList()
        try {
            if (lkpLocTypeKy.equals('VESSEL') && getVesselClassType(lkpCarrId).equals('BARGE')) {
                def bargeCode = misc1 != null && misc1.length() > 2 ? misc1.substring(0, 3) : ''
                truck = '9' + bargeCode
            }
            if (lkpLocTypeKy.equals('VESSEL') || lkpLocTypeKy.equals('YARD')) {
                //A12 - misc1 = adIBCarrId
                misc1 = IbVygNbr
            }
            arrLst.add(misc1)
            arrLst.add(truck)
        } catch (Exception e) {
            e.printStackTrace()
        }
        return arrLst
    }

    private ArrayList getShipToBarge(String lkpLocTypeKy, String lkpCarrId, String aobcarrId, String aidObCarrId) {
        def misc1 = ''
        def truck = ''
        ArrayList arrLst = new ArrayList()
        try {
            if (lkpLocTypeKy.equals('VESSEL') && getVesselClassType(lkpCarrId).equals('CELL')) {
                misc1 = aidObCarrId
            } else if (lkpLocTypeKy.equals('VESSEL') && getVesselClassType(lkpCarrId).equals('BARGE')) {
                misc1 = aidObCarrId
                def bargeCode = misc1 != null && misc1.length() > 2 ? misc1.substring(0, 3) : ''
                truck = '9' + bargeCode
            } else if (lkpLocTypeKy.equals('YARD')) {
                misc1 = aidObCarrId
            } else if (lkpLocTypeKy.equals('TRUCK')) {
                misc1 = aobcarrId
            }
            arrLst.add(misc1)
            arrLst.add(truck)
        } catch (Exception e) {
            e.printStackTrace()
        }
        return arrLst
    }

    private String getShipToShip(String lkpLocTypeKy, String lkpCarrId, String aibcarrId, String aobcarrId, String aidObCarrId, String adIBCarrId, Object gvyCmisUtil, String dischPort, String categoryKey) {
        def misc1 = ''
        try {
            if (lkpLocTypeKy.equals('VESSEL')) {
                /* REMOVE - LH NIS   if(gvyCmisUtil.isNISPort(dischPort)){
                            misc1 = aidObCarrId
                            def bargeCode = misc1 != null && misc1.length() > 2 ? misc1.substring(0,3) : ''
                            truck = '9'+bargeCode
                          } */
                if (lkpCarrId.equals(aibcarrId)) {
                    misc1 = aidObCarrId
                } else if (lkpCarrId.equals(aobcarrId)) {
                    misc1 = adIBCarrId
                }
            }
            if (lkpLocTypeKy.equals('YARD')) {
                misc1 = adIBCarrId
            }//Condition For SHIP-SHIP Through
            if (categoryKey.equals('THRGH') && aibcarrId.equals(aobcarrId)) {
                misc1 = ''
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return misc1
    }

    private String getTruck(String lkpLocTypeKy, String drayStatusKy, String transitStateKy, String obCarrierId, String ibCarrierId, Object unit, Object gvyCmisUtil, String dischPort, String lkpCarrierId, Object aobcarrierMode) {
        def truck = ''
        try {
            if (lkpLocTypeKy.equals('TRUCK')) {
                if (drayStatusKy.equals('TRANSFER') && transitStateKy.equals('S70_DEPARTED')) {
                    truck = 'YBUU'
                } else if (transitStateKy.equals('S30_ECIN') || transitStateKy.equals('S10_ADVISED')) {
                    truck = ibCarrierId
                } else {//A8 - If OBCarrierId is a VESSEL then dont set truck keep value NULL
                    if ('BARGE'.equals(aobcarrierMode)) {
                        def bargeCode = obCarrierId.substring(0, 3)
                        truck = '9' + bargeCode
                    } else if ('CELL'.equals(aobcarrierMode)) {
                        truck = 'null';
                    } else {
                        truck = obCarrierId;
                    }
                }
            } else if (lkpLocTypeKy.equals('VESSEL') || lkpLocTypeKy.equals('YARD')) {
                truck = unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
            }
            println('Setting For GetTrucker :' + truck)

        } catch (Exception e) {
            e.printStackTrace()
        }
        return truck
    }

    private String getVesVoy(String lkpLocTypeKy, String lkpCarrId, String categoryKy, String transitStateKy, String obCarrierId, String actIbCarrierId, String decIbCarrierId, String freightkind, String aibcarrierMode, Object u, Object gvyCmisUtil, String dischPort) {
        def vesVoy = ''
        def dIbcarrierMode = u.getFieldValue("unitDeclaredIbCv.cvCarrierMode")
        dIbcarrierMode = dIbcarrierMode != null ? dIbcarrierMode.getKey() : ""

        try {
            if (lkpLocTypeKy.equals('VESSEL') && (getVesselClassType(lkpCarrId).equals('CELL'))) {
                vesVoy = lkpCarrId
            } else if (lkpLocTypeKy.equals('YARD') || lkpLocTypeKy.equals('TRUCK') ||
                    (lkpLocTypeKy.equals('VESSEL') && (getVesselClassType(lkpCarrId).equals('BARGE')))) {
                if (categoryKy.equals('EXPRT') || categoryKy.equals('THRGH')) {
                    vesVoy = obCarrierId
                } else if (categoryKy.equals('IMPRT') && lkpLocTypeKy.equals('YARD')) {
                    //A5 - FOR SIT UNITS
                    if (dIbcarrierMode.equals('VESSEL') && aibcarrierMode.equals('TRUCK')) {
                        println("dIbcarrierMode :" + dIbcarrierMode + "  aibcarrierMode:" + aibcarrierMode)
                        vesVoy = decIbCarrierId
                    } else if (aibcarrierMode.equals('TRUCK')) {
                        vesVoy = '%'
                    } else {
                        vesVoy = actIbCarrierId
                    }
                } else if (categoryKy.equals('IMPRT') && (lkpLocTypeKy.equals('TRUCK') || (lkpLocTypeKy.equals('VESSEL')))) {
                    if (freightkind.equals('MTY') || (aibcarrierMode.equals('TRUCK') && dIbcarrierMode.equals('TRUCK'))) {
                        vesVoy = ''
                    } else {
                        vesVoy = decIbCarrierId
                    }
                } else if (categoryKy.equals('TRSHP') && transitStateKy.equals('S20_INBOUND')) {
                    vesVoy = actIbCarrierId != null ? actIbCarrierId : decIbCarrierId
                } else if (categoryKy.equals('TRSHP') && transitStateKy.trim().length() > 0) {
                    vesVoy = obCarrierId
                }
            }

            //Check for Client Vessel
            /*      def vesselLineOperator = gvyCmisUtil.getVesselLineOperator(u)
                  if(vesselLineOperator != null && !vesselLineOperator.equals('MAT'))
                  {
                    def islandPort = gvyCmisUtil.isNISPort(dischPort)
                    if (islandPort || lkpLocTypeKy.equals('YARD')){
                      def intIbVesId = u.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdVessel.vesId")
                      def intIbVygNbr =u.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdIbVygNbr")
                      vesVoy  = intIbVesId+intIbVygNbr
                    }
                    else
                    {
                       def obVesId = u.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdVessel.vesId")
                       def obVygNbr =u.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdObVygNbr")
                       vesVoy  = obVesId+obVygNbr
                    }
                  } */

            //Check for GEN_CARRIER
            if (vesVoy.equals('GEN_CARRIER') || vesVoy.equals('GEN_VESSEL') || vesVoy.equals('GEN_TRUCK') || 'BARGE'.equals(vesVoy)) {
                //A2
                vesVoy = ''
            }
            //Check for Setting VesVoy for Only LongHaul Vessels
            if (!(vesVoy.equals('%') || vesVoy.length() == 0) && (getVesselClassType(vesVoy).equals('BARGE'))) {
                vesVoy = ''
            }

        } catch (Exception e) {
            e.printStackTrace()
        }
        return vesVoy
    }

    private String getVesselClassType(String vesselId) {
        def vesselType = ''
        try {
            def vesselClassId = vesselId != null && vesselId.length() > 3 ? vesselId.substring(0, 3) : null
            if (vesselClassId != null) {
                VesselClass vesselClass = new VesselClass()
                vesselClass = vesselClass.findVesselClassById(vesselClassId)
                VesselTypeEnum vesselTypeEnum = vesselClass != null ? vesselClass.getVesclassVesselType() : null
                vesselType = vesselTypeEnum != null ? vesselTypeEnum.getKey() : ''
            }
            //println('VESSEL TYPE :'+vesselType)
        } catch (Exception e) {
            e.printStackTrace()
        }
        return vesselType
    }

    public String positionPercentageValue(Object gvyTxtMsgFmt) {
        def positionProFldAttr = ''
        def vesvoy = '%', truckValue = '%', misc1Value = '%', actualVessel = '%', actualVoyage = '%', leg = '%'
        def vesvoyAttr = gvyTxtMsgFmt.doIt('vesvoy', vesvoy)
        def truckAttr = gvyTxtMsgFmt.doIt('truck', truckValue)
        def misc1Attr = gvyTxtMsgFmt.doIt('misc1', misc1Value)
        def actualVesselAttr = gvyTxtMsgFmt.doIt('actualVessel', actualVessel)
        def actualVoyageAttr = gvyTxtMsgFmt.doIt('actualVoyage', actualVoyage)
        def legAttr = gvyTxtMsgFmt.doIt('leg', leg)

        positionProFldAttr = vesvoyAttr + truckAttr + misc1Attr + actualVesselAttr + actualVoyageAttr + legAttr

        return positionProFldAttr
    }

}
