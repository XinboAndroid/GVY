/*
**********************************************************************
* Srno    Date        Changer	         Change Description
* A1      03/01/11    Glenn Raposo       Removed some logging
* A2      03/26/13    Lisa Crouch        Check group id, perform transfer complete
**********************************************************************
*/
import com.navis.argo.business.api.ArgoRoadManager
import com.navis.argo.business.api.ITruckVisitDetails
import com.navis.framework.business.Roastery;
import com.navis.argo.business.api.*;
import com.navis.framework.business.*;

import com.navis.inventory.business.api.UnitField;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.portal.Ordering
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.inventory.business.units.Unit
import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.inventory.business.units.Routing;

/**
 Change the gate gateSequenceNo to use startDate not StageStartDate.
 */
public class GvyCmisGateData {

    public String gateAttributes(Object unit, Object gvyTxtMsgFmt, Object gvyBaseClass, String eventType) {

        def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
        def lkpLocType = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
        def locType = lkpLocType != null ? lkpLocType.getKey() : ''

        def carrierVisitGkey = ''
        def gateTruckCmpyCd = ''; def gateTruckCmpyName = ''; def gateTruckId = ''
        def batNumber = ''; def turnTime = ''; def gateSeqNo = '';
        def laneGateId = ''; def deptTruckCode = ''; def deptDport = '';
        def deptVesVoy = ''; def deptOutGatedDt = ''; def deptConsignee = '';
        def deptCneeCode = ''; def deptBkgNum = ''; def deptMisc3 = '';
        def deptCargoNotes = '';
        def gateId = '';

        if (eventType.equals('UNIT_IN_GATE')) {
            // TRUCK CODE,NAME, ID, BAT_NBR, TURNTIME, GATE_SEQ_NBR
            carrierVisitGkey = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvdGkey")
            gateTruckCmpyCd = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.carrierOperatorId")
            gateTruckCmpyName = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.carrierOperatorName")
            gateTruckId = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId")
            batNumber = getTruckBatNbr(carrierVisitGkey)
            turnTime = ''
            gateSeqNo = gateSequenceNo(gvyBaseClass, unit, gvyEventUtil, carrierVisitGkey)
            laneGateId = getGateId(carrierVisitGkey)
            gateId = "LONG HAUL"; //todo, change based on input

            //FIELDS FOR DEPARTED UNIT
            def departedUfvUnit = getDepatedUnit(unit)

            if (departedUfvUnit != null) {
                //Get class
                def gvyShipDtl = gvyBaseClass.getGroovyClassInstance("GvyCmisShipmentDetail")
                def gvyFlexFld = gvyBaseClass.getGroovyClassInstance("GvyCmisFlexFieldDetail")

                def deptLocType = departedUfvUnit.getFieldValue("ufvLastKnownPosition.posLocType")
                def zone = unit.getUnitComplex().getTimeZone();
                deptLocType = deptLocType != null ? deptLocType.getKey() : ''
                if (deptLocType.equals('TRUCK')) {
                    deptTruckCode = departedUfvUnit.getFieldValue("ufvActualObCv.carrierOperatorId")
                    deptOutGatedDt = departedUfvUnit.getFieldValue("ufvTimeOut");
                    deptOutGatedDt = deptOutGatedDt != null ? gvyEventUtil.formatDate(deptOutGatedDt, zone) : '';

                    //casting UfvUnit to Unit obj as below fields are unit fields.
                    def departedUnit = departedUfvUnit.ufvUnit
                    deptDport = departedUnit.getFieldValue("unitGoods.gdsDestination")
                    deptVesVoy = getDeptVesvoy(departedUnit)
                    deptConsignee = gvyShipDtl.getConsigneeValue(departedUnit, eventType);
                    deptCneeCode = departedUnit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuId");
                    deptBkgNum = gvyShipDtl.getBookingNumber(departedUnit, eventType);
                    deptMisc3 = gvyFlexFld.getMisc3(departedUnit, gvyEventUtil);
                    deptCargoNotes = getDeptCargoNotes(departedUnit, gvyBaseClass, eventType)

                    //println("deptDPort :"+deptDport+"  deptVesVoy:"+deptVesVoy+"  deptOutGatedDt:"+deptOutGatedDt+" gateTruckCmpyCd"+gateTruckCmpyCd)
                    //println("deptConsignee ::"+deptConsignee+" deptCneeCode:"+deptCneeCode+" deptBkgNum:"+deptBkgNum+" deptMisc3:"+deptMisc3+" deptCargoNotes:"+deptCargoNotes)
                }
            } else {
                deptTruckCode = ''; deptDport = ''; deptVesVoy = ''; deptOutGatedDt = '';
                deptConsignee = ''; deptCneeCode = ''; deptBkgNum = ''; deptMisc3 = '';
                deptCargoNotes = '';
            }
        } else if (eventType.equals('UNIT_DELIVER')) {
            // TRUCK CODE,NAME, ID, BAT_NBR, TURNTIME, GATE_SEQ_NBR
            carrierVisitGkey = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvdGkey")
            gateTruckCmpyCd = unit.getFieldValue("unitActiveUfv.ufvActualObCv.carrierOperatorId")
            gateTruckCmpyName = unit.getFieldValue("unitActiveUfv.ufvActualObCv.carrierOperatorName")
            gateTruckId = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")
            batNumber = getTruckBatNbr(carrierVisitGkey)
            turnTime = ''
            gateSeqNo = gateSequenceNo(gvyBaseClass, unit, gvyEventUtil, carrierVisitGkey)
            laneGateId = getGateId(carrierVisitGkey)
            gateId = "LONG HAUL"; //todo, change based on input

            deptTruckCode = ''; deptDport = ''; deptVesVoy = ''; deptOutGatedDt = '';
            deptConsignee = ''; deptCneeCode = ''; deptBkgNum = ''; deptMisc3 = '';
            deptCargoNotes = '';
        } else {
            gateTruckCmpyCd = '%'; gateTruckCmpyName = '%'; gateTruckId = '%';
            batNumber = '%'; turnTime = '%'; gateSeqNo = '%';
            laneGateId = '%'; deptTruckCode = ''; deptDport = '';
            deptVesVoy = ''; deptOutGatedDt = ''; deptConsignee = '';
            deptCneeCode = ''; deptBkgNum = ''; deptMisc3 = '';
            deptCargoNotes = '';

            if (eventType.equals('UNIT_RECEIVE')) {
                carrierVisitGkey = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvdGkey")
                laneGateId = getGateId(carrierVisitGkey)
            }
            gateId = "LONG HAUL"; //todo, change based on input
        }

        def gateTruckCmpyCdAttr = gvyTxtMsgFmt.doIt("gateTruckCmpyCd", gateTruckCmpyCd)
        def gateTruckCmpyNameAttr = gvyTxtMsgFmt.doIt("gateTruckCmpyName", gateTruckCmpyName)
        def gateTruckIdAttr = gvyTxtMsgFmt.doIt("gateTruckId", gateTruckId)
        def batNumberAttr = gvyTxtMsgFmt.doIt("batNumber", batNumber)
        def turnTimeAttr = gvyTxtMsgFmt.doIt("turnTime", turnTime)
        def gateSeqNoAttr = gvyTxtMsgFmt.doIt('gateSeqNo', gateSeqNo)
        //Departed Unit
        def laneGateIdAttr = gvyTxtMsgFmt.doIt('laneGateId', laneGateId)
        def deptTruckCodeAttr = gvyTxtMsgFmt.doIt('deptTruckCode', deptTruckCode)
        def deptDportAttr = gvyTxtMsgFmt.doIt('deptDport', deptDport)
        def deptVesVoyAttr = gvyTxtMsgFmt.doIt('deptVesVoy', deptVesVoy)
        def deptOutGatedDtAttr = gvyTxtMsgFmt.doIt('deptOutGatedDt', deptOutGatedDt)
        def deptConsigneeAttr = gvyTxtMsgFmt.doIt('deptConsignee', deptConsignee)
        def deptCneeCodeAttr = gvyTxtMsgFmt.doIt('deptCneeCode', deptCneeCode)
        def deptBkgNumAttr = gvyTxtMsgFmt.doIt('deptBkgNum', deptBkgNum)
        def deptMisc3Attr = gvyTxtMsgFmt.doIt('deptMisc3', deptMisc3)
        def deptCargoNotesAttr = gvyTxtMsgFmt.doIt('deptCargoNotes', deptCargoNotes)
        def gateIdAttr = gvyTxtMsgFmt.doIt('gateId', laneGateId) //laneGateId & gateId refers to same


        def gateAttr = gateTruckCmpyCdAttr + gateTruckCmpyNameAttr + gateTruckIdAttr + batNumberAttr + turnTimeAttr + gateSeqNoAttr + laneGateIdAttr +
                deptTruckCodeAttr + deptDportAttr + deptVesVoyAttr + deptOutGatedDtAttr + deptConsigneeAttr + deptCneeCodeAttr + deptBkgNumAttr +
                deptMisc3Attr + deptCargoNotesAttr + gateIdAttr;
        return gateAttr
    }

    public String getTruckBatNbr(Long cvdGkey) {
        ITruckVisitDetails tvd = null;
        try {
            ArgoRoadManager argoRoadMgr = (ArgoRoadManager) Roastery.getBean("argoRoadManager");
            tvd = argoRoadMgr != null ? argoRoadMgr.getTvdFromCvGkey(cvdGkey) : null;
            String batNbr = tvd != null ? tvd.getTruckBatNbr() : ''
            return batNbr
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getEntryLaneId(long cvdGKey) {
        try {
            ArgoRoadManager argoRoadMgr = (ArgoRoadManager) Roastery.getBean("argoRoadManager");
            def tvd = argoRoadMgr.getTvdFromCvGkey(cvdGKey);
            String laneId = tvd != null ? (tvd.getTvdtlsEntryLane() != null ? tvd.getTvdtlsEntryLane().getLaneId() : '') : ''
            return laneId
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }//Method EntryLaneId Ends


    public String getExitLaneId(long cvdGKey) {
        try {
            ArgoRoadManager argoRoadMgr = (ArgoRoadManager) Roastery.getBean("argoRoadManager");
            def tvd = argoRoadMgr.getTvdFromCvGkey(cvdGKey);
            String laneId = tvd != null ? (tvd.getTvdtlsExitLane() != null ? tvd.getTvdtlsExitLane().getLaneId() : '') : ''
            return laneId
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }//Method EntryLaneId Ends


    public String gateSequenceNo(Object gvyBaseClass, Object unit, Object gvyEventUtil, Object carrierVisitGkey) {
        def gateSeqNo = ''
        try {
            if (carrierVisitGkey != null) {
                ArgoRoadManager argoRoadMgr = (ArgoRoadManager) Roastery.getBean("argoRoadManager");
                def tvd = argoRoadMgr != null ? argoRoadMgr.getTvdFromCvGkey(carrierVisitGkey) : null;

                Date dateObj = null;
                if (tvd != null) {
                    def stats = tvd.tvdtlsStats != null ? tvd.tvdtlsStats.iterator() : tvd.tvdtlsStats;
                    if (stats.hasNext()) {
                        def currentStat = stats.next();
                        dateObj = currentStat.tvstatStart;

                        //Date Formatting
                        def zone = unit.getUnitComplex().getTimeZone();
                        def aDate = gvyEventUtil.convertToJulianDate(dateObj)
                        def aTime = gvyEventUtil.formatTime(dateObj, zone)
                        def datefmt = aDate + aTime
                        gateSeqNo = datefmt != null ? datefmt.replace(":", "") : datefmt
                    }

                }
                //Date Formatting

            }//If ends
        }//try Ends
        catch (Exception e) {
            e.printStackTrace();
        }
        return gateSeqNo;

    }//method execute ends


    public Object getDepatedUnit(Object unit) {
        try {
            def inComplex = unit.getFieldValue("unitComplex.cpxGkey")
            def equiGKey = unit.getFieldValue("unitPrimaryUe.ueEquipment.ueGkey")
            def inFacility = com.navis.argo.ContextHelper.getThreadFacility()
            //println('inComplex :'+inComplex+'    equiGKey'+equiGKey+'  inFacility:'+inFacility)
            //def departedUnit = findLatestDepartedUnit(inComplex,equiGKey)
            def departedUnit = findDepartedUfvUnit(inFacility, equiGKey)

            return departedUnit
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    // Finds Last Departed Unit at Complex Level
    private Object findLatestDepartedUnit(long inComplexGKey, long equiGKey) {
        def unit = null;
        DomainQuery dq = QueryUtils.createDomainQuery("Unit").addDqPredicate(PredicateFactory.eq(UnitField.UNIT_COMPLEX, inComplexGKey)).addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE, UnitVisitStateEnum.DEPARTED)).addDqPredicate(PredicateFactory.eq(UnitField.UNIT_PRIMARY_EQ, equiGKey)).addDqOrdering(Ordering.desc(UnitField.UNIT_CREATE_TIME));

        List unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        if (unitList.size() > 0) {
            //println("unitList Size :"+ unitList.size())
            unit = (Unit) unitList.get(0);
        }
        return unit;
    }

    /*
    * Method Fetches last Departed Ufv Unit with Actual OB carrier as Truck
    */

    private Object findDepartedUfvUnit(Object inFacility, long primaryEquiGkey) {
        def lastDeptUfvUnit = null;
        List ufvUnitLst = null;
        try {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_FACILITY, inFacility.getFcyGkey())).addDqPredicate(PredicateFactory.eq(UnitField.UFV_VISIT_STATE, UnitVisitStateEnum.DEPARTED)).addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_CARRIER_MODE, LocTypeEnum.TRUCK)).addDqPredicate(PredicateFactory.eq(UnitField.UFV_PRIMARY_EQ, primaryEquiGkey)).addDqOrdering(Ordering.desc(UnitField.UFV_TIME_OUT));
            ufvUnitLst = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

            //println('ufvUnitLst ::'+(ufvUnitLst != null ? ufvUnitLst.size() : '0'))
            int count = 0;

            for (aUfv in ufvUnitLst) {
                count++;
                def remarks = aUfv.getFieldValue("ufvUnit.unitRemark");
                def truckerId = aUfv.getFieldValue("ufvActualObCv.carrierOperatorId")
                //println("Count :"+count+": aUFv Remarks ::"+remarks+"  truckerId::"+truckerId)
            }

            //The First ufv in the List is the most recent dept unit
            if (ufvUnitLst.size() > 0) {
                lastDeptUfvUnit = ufvUnitLst.get(0);
                return lastDeptUfvUnit
            }

        } catch (Exception e) {
            e.printStackTrace()
        }
        return lastDeptUfvUnit;
    }


    public String getGateId(long cvdGKey) {
        try {
            ArgoRoadManager argoRoadMgr = (ArgoRoadManager) Roastery.getBean("argoRoadManager");
            def tvd = argoRoadMgr.getTvdFromCvGkey(cvdGKey);
            String laneId = tvd != null ? (tvd.getTvdtlsGate() != null ? tvd.getTvdtlsGate().getGateId() : '') : ''
            return laneId
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }//Method getGateId Ends

    //Method gets Departed units vesvoy
    public String getDeptVesvoy(Object deptUnit) {
        def deptVesvoy = ''
        def dibcarrierId = ''
        try {

            def aibcarrierMode = deptUnit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCarrierMode")
            aibcarrierMode = aibcarrierMode != null ? aibcarrierMode.getKey() : ''

            def ibDecVesClassTyp = deptUnit.getFieldValue("unitDeclaredIbCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
            ibDecVesClassTyp = ibDecVesClassTyp != null ? ibDecVesClassTyp.getKey() : ""

            def aIbVesselType = deptUnit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
            aIbVesselType = aIbVesselType != null ? aIbVesselType.getKey() : ""

            if (aibcarrierMode.equals('TRUCK')) {
                if (ibDecVesClassTyp.equals('CELL')) {
                    dibcarrierId = deptUnit.getFieldValue("unitDeclaredIbCv.cvId")
                    deptVesvoy = dibcarrierId;
                } else {
                    deptVesvoy = null;
                }
            } else if (aibcarrierMode.equals('VESSEL')) {
                if (aIbVesselType.equals('CELL')) {
                    def aibcarrierId = deptUnit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId")
                    deptVesvoy = aibcarrierId;
                } else {
                    deptVesvoy = null;
                }
            }
            //println("aibcarrierMode:"+aibcarrierMode+"  dibcarrierId:"+dibcarrierId+"  ibDecVesClassTyp:"+ibDecVesClassTyp+"   aIbVesselType:"+aIbVesselType+"  deptVesvoy ::"+deptVesvoy)

        } catch (Exception e) {
            e.printStackTrace();
        }
        return deptVesvoy
    }

    public String getDeptCargoNotes(Object unit, Object gvyBaseClass, String eventType) {
        def cargoNotes = ''
        try {

            def groupCode = unit.getFieldValue("unitRouting.rtgGroup.grpId");

            def lkpSlot = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
            lkpSlot = lkpSlot != null ? lkpSlot : ''

            def _pmdDt = unit.getFieldValue("unitActiveUfv.ufvFlexDate01")
            def strpmd = _pmdDt != null ? ('' + _pmdDt) : ''
            def pmd = strpmd.length() > 10 ? strpmd.substring(8, 10) : strpmd

            def equiType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId")
            equiType = equiType != null ? equiType : ''

            def drayStatus = unit.getFieldValue("unitDrayStatus")
            drayStatus = drayStatus != null ? drayStatus.getKey() : drayStatus

            def reviewForStow = unit.getFieldValue("unitActiveUfv.ufvFlexString01")

            def gvyComtNotes = gvyBaseClass.getGroovyClassInstance("GvyCmisCommentNotesField")
            //Cargo Status
            cargoNotes = unit.getFieldValue("unitRemark")
            cargoNotes = cargoNotes != null ? cargoNotes.replace("\n", " ") : ''
            cargoNotes = gvyComtNotes.processCargoNotesOnEvent(null, eventType, cargoNotes, groupCode, lkpSlot, pmd, equiType, reviewForStow, drayStatus)

            String cargoNotesOverFlow = '';
            if (cargoNotes.length() > 65) {
                int cargoNotesIndex = cargoNotes.substring(0, 65).lastIndexOf(" ");
                cargoNotesOverFlow = cargoNotes.substring(cargoNotesIndex + 1, cargoNotes.length());
                cargoNotes = cargoNotes.substring(0, cargoNotesIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cargoNotes
    }

    public void setTransferComplete(Object unit, Object api) {
        def carrierVisitGkey = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvdGkey");
        carrierVisitGkey = carrierVisitGkey != null ? carrierVisitGkey : '';
        Routing routing = unit.getUnitRouting();
        Group group = routing.getRtgGroup();
        def gvyGateObj = api.getGroovyClassInstance("GvyCmisGateData");
        String gateIdDesc = gvyGateObj.getGateId(carrierVisitGkey);
        boolean isGate = false;
        try {
            if (group == null) {
                println("group is null");
                return;
            } else {
                //println("carrierVisitGkey:"+carrierVisitGkey);
                //println("group:"+group.getGrpId().toUpperCase());
                //println("gateId:"+gateIdDesc);

                if (group.getGrpId().toUpperCase().endsWith("P2") && gateIdDesc.equalsIgnoreCase("PIER2")) {
                    isGate = true;
                }
                if (group.getGrpId().toUpperCase().endsWith("SI") && gateIdDesc.equalsIgnoreCase("SI GATE")) {
                    isGate = true;
                }
                if (group.getGrpId().toUpperCase().endsWith("WO") && gateIdDesc.equalsIgnoreCase("WO GATE")) {
                    isGate = true;
                }
                if (group.getGrpId().toUpperCase().equals("PASSPASS") && gateIdDesc.equalsIgnoreCase("PASSPASS")) {
                    isGate = true;
                }
            }

            if (isGate) {
                //println("Remove group id");
                routing.setRtgGroup(null);
            } else {
                println("Set DrayStatus only");
                unit.setFieldValue("unitDrayStatus", com.navis.argo.business.atoms.DrayStatusEnum.OFFSITE);
            }
        } catch (Exception e) {
            api.log("Exception in GvyCmisGateData.setTransferComplete() " + e);
        }
    }
}
