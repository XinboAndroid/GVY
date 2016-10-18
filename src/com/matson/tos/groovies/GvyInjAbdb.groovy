/*
**********************************************************************
* Srno   Date	        Changer	 	 Change Description
* A0     12/03/08	Steven Bauer	 Updated Visit update detail
* A1     12/29/08       Glenn Raposo	 Unit Field Tracker code Added
* A2     12/30/08       Steven Bauer	 Added Primary Carrier change
* A3     02/09/08       Glenn Raposo     Update Code for ClassCastException & Added Try Catch
* A4     04/09/09       Glenn Raposo     Adde check to set the BDB Dest to POD if they dont match
* A5     04/21/09       Glenn Raposo     POD condition correction for IMPORT and Not Import Units
* A6     04/28/09	Steven Bauer	 Replaced manual sit_assign with the event.
* A7     04/30/09       Glenn Raposo     Null pointer Check to Set Barge value
* A8     05/12/09	Steven Bauer     Added POD lookup from Dest.
*					 Added tranship and through to inport logic.
* A9     05/12/09	Steven Bauer	 Change from Inbound to Outbound carrier on exort
* A10    05/21/09	Steven Bauer	 Find the unit in the complex and not facility.
*          				 Append comment instead of overlaying
*          				 Remove the tState != Inbound for the update Disch port for IMPORT, THROUGH or TRANSHIP
*					 Remove the tState != Inbound for the update Disch port for EXPORT
*          				 Remove the updateIbCv for imports
* A11    06/01/09       Steven Bauer     Allow update of O/B carrier for inbound exports.
* A12    06/02/09	Steven Bauer	 403 - Supress all updates before GetNV
* A13    07/02/09	Steven Bauer	 Create Shipper/Consignee if they don't exist
* A14    12/18/2009  Glenn Raposo    Roll over Bkg for Inbound Expt with BDBVesvoy as barge
* A15    06/08/2010  Glenn Raposo    Added fix for A14 Bkg roll over for Inbound Expt
* A16    09/08/2011  Glenn Raposo	 Added DrayStatus check for applying SIT
* A17    10/12/2011  Glenn Raposo	 updated Assign Booking Method for TOS2.1
**********************************************************************
*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.reference.Shipper
import com.navis.framework.business.Roastery
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderManagerPea
import com.navis.services.business.event.GroovyEvent

class GvyInjAbdb extends GroovyInjectionBase {

    def ctrId = null;

    public String execute(Map inParameters) {

        try {
            def gvyVesselLookup = getGroovyClassInstance("GvyVesselLookup");
            ctrId = (String) inParameters.get("equipment-id");

            def recorder = (String) inParameters.get("recorder");
            def vesvoyBdb = (String) inParameters.get("vesvoy");
            def dbdBookingNum = (String) inParameters.get("bookingNum");
            def sit = (String) inParameters.get("sit");
            def consigneeIdBdb = (String) inParameters.get("consigneeId");
            def consigneeNameBdb = (String) inParameters.get("consigneeName");
            def shipperNameBdb = (String) inParameters.get("shipperName");
            def shipperIdBdb = (String) inParameters.get("shipperId");
            def destPortBdb = (String) inParameters.get("destPort");
            def inBoundBdb = (String) inParameters.get("inBound");
            def agFlag = (String) inParameters.get("ag");
            def discPortBdb = (String) inParameters.get("dischargePort");
            def primCarrierBdb = (String) inParameters.get("primCarrier");

            com.navis.argo.ContextHelper.setThreadExternalUser("ACETS");

            def note = "\n\nPlease fix either the booking or unit and review the routing after correction";

            final String emailFrom = '1aktosdevteam@matson.com'
            final String emailTo = "1aktosdevteam@matson.com";
            def emailBody = "";

            def inj = new GroovyInjectionBase();
            def emailSender = inj.getGroovyClassInstance("EmailSender");
            com.navis.argo.ContextHelper.setThreadExternalUser("ACETS");

            // Find the UFV
            println("Starting BDB Injection process.");
            def ufv;
            def unit;
            //A1- tracker
            def tracker;
            GroovyApi gvyApi;
            def dischargePortN4;

            gvyApi = new GroovyApi();
            def podLookup = gvyApi.getGroovyClassInstance("GvyRefDataLookup");
            try {
                try {
                    ufv = findActiveUfv(ctrId);
                    unit = ufv.getUfvUnit();
                } catch (Throwable e) {
                }

                if (ufv == null) {
                    unit = gvyApi.getGroovyClassInstance("GvyUnitLookup").findCurrentUnit(ctrId);
                    if (unit == null) throw new Exception("Could not find unit by forien key " + ctrId);
                    //if(unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType") !=  LocTypeEnum.TRUCK ) throw e;
                    ufv = unit.unitActiveUfv;

                }

                //A1- Tracker Change
                def gvyUnitFldTracker = gvyApi.getGroovyClassInstance("GvyUnitFieldTracker");
                tracker = gvyUnitFldTracker.createFieldChangeTracker(unit)
            } catch (Exception ex) {
                ex.printStackTrace()
                emailSender.custSendEmail(emailFrom, emailTo, " BDB error for Booking " + dbdBookingNum + " and Unit " + ctrId, "Could not find unit: " + ctrId + note);
                fail((new StringBuilder()).append(ex.toString()).append(" ERR_GVY_DBD_001. Could not find unit: ").append(ctrId).toString());
            }

            //A12
            if (isStowplan(unit)) return;

            StringBuffer header = new StringBuffer();
            header.append("\nBooking Number       : " + dbdBookingNum);
            header.append("\nContainer Number     : " + ctrId);
            header.append("\n\n");

            emailBody = header.toString();

            if (UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) &&
                    isLongHaul(vesvoyBdb) &&
                    UnitVisitStateEnum.DEPARTED.equals(ufv.getUfvVisitState())) {
                emailSender.custSendEmail(emailFrom, emailTo, " BDB error for Booking " + dbdBookingNum + " and Unit " + ctrId, emailBody + "VesVoy: " + vesvoyBdb + " sailed. Could not apply BDB." + note);
                fail("ERR_GVY_DBD_002. VesVoy: " + vesvoyBdb + " sailed. Could not apply DBD.");
            }

            def uoi = unit.getUnitPrimaryUe().getUeDepartureOrderItem();
            def n4BlNum = unit.getFieldValue("unitGoods.gdsBlNbr");
            dischargePortN4 = unit.getFieldValue("unitRouting.rtgPOD1.pointId");


            println("n4BlNum = " + n4BlNum + " dbdBookingNum = " + dbdBookingNum);
            boolean flagForAcetsBkg = n4BlNum != null ? n4BlNum.startsWith(dbdBookingNum) : false

            if (n4BlNum != null && n4BlNum != dbdBookingNum) {
                if (!flagForAcetsBkg) {
                    emailSender.custSendEmail(emailFrom, emailTo, " BDB error for Booking " + dbdBookingNum + " and Unit " + ctrId, emailBody + "Unit: " + ctrId + " Booking Num dismatch: N4 num=" + n4BlNum + "  BDB num=" + dbdBookingNum + note);
                    fail("ERR_GVY_DBD_003. Unit: " + ctrId + " Booking Num dismatch: N4 num=" + n4BlNum + "  BDB num=" + dbdBookingNum);
                }
            }

            if (!UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) && !UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory()) && !UnitCategoryEnum.THROUGH.equals(unit.getUnitCategory()) && !UnitCategoryEnum.TRANSSHIP.equals(unit.getUnitCategory())) {
                emailSender.custSendEmail(emailFrom, emailTo, " BDB error for Booking " + dbdBookingNum + " and Unit " + ctrId, emailBody + "Unit: " + ctrId + " category is not IMPORT, EXPORT, THROUGH or TRANSHIP. Ignored." + note);
                fail("ERR_GVY_DBD_004. Unit: " + ctrId + " category is not IMPORT, EXPORT, THROUGH or TRANSHIP. Ignored.");
            }

            def cmdyId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
            boolean isUnitSIT = "SIT".equals(cmdyId) || "SAT".equals(cmdyId) ? true : false

            def IBVesvoy = unit.getFieldValue("unitDeclaredIbCv.cvId");
            if (UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) &&
                    IBVesvoy != vesvoyBdb && !isLongHaul(vesvoyBdb) && !isUnitSIT) {
                emailSender.custSendEmail(emailFrom, emailTo, " BDB error for Booking " + dbdBookingNum + " and Unit " + ctrId, emailBody + "IB vesvoy in N4: " + IBVesvoy + " mismatch BDB vesvoy: " + vesvoyBdb + note);
                fail("ERR_GVY_DBD_005. IB vesvoy in N4: " + IBVesvoy + " mismatch BDB vesvoy: " + vesvoyBdb);
            }


            UfvTransitStateEnum transitState = ufv.getUfvTransitState();
            UnitVisitStateEnum visitState = unit.getUnitVisitState();
            if (UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) &&
                    !UfvTransitStateEnum.S30_ECIN.equals(transitState) &&
                    !UfvTransitStateEnum.S40_YARD.equals(transitState) &&
                    !UnitVisitStateEnum.ADVISED.equals(visitState) &&
                    !UfvTransitStateEnum.S20_INBOUND.equals(transitState)) {
                emailSender.custSendEmail(emailFrom, emailTo, " BDB error for Booking " + dbdBookingNum + " and Unit " + ctrId, emailBody + "Bad location status for unit: " + ctrId + note);
                fail("ERR_GVY_DBD_006. Bad location status for unit: " + ctrId);
            }

            //A3 - Update Code for ClassCastException
            if ("Y".equals(sit) && !isUnitSIT) {
                println("send email.");
            }

            def event = new GroovyEvent(null, unit);
            def comments = "";
            // start if IMPORT, THROUGH or TRANSHIP
            if (UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) || UnitCategoryEnum.THROUGH.equals(unit.getUnitCategory()) || UnitCategoryEnum.TRANSSHIP.equals(unit.getUnitCategory())) {
                def consigneeN4 = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName");
                def consigneeN4Id = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuId");
                println("cneeNameN4 = " + consigneeN4 + " cneeIdN4 = " + consigneeN4Id);
                if (consigneeN4 != null && (consigneeN4.contains("unapproved") || consigneeN4.contains("invalid assign")) &&
                        n4BlNum == null) {
                    // assign booking to unit BL Number
                    unit.setFieldValue("unitGoods.gdsBlNbr", dbdBookingNum);
                }

                def misc2 = unit.getFieldValue("unitFlexString11");
                def shipper = Shipper.findOrCreateShipper(shipperIdBdb, shipperNameBdb);

                println("misc2 = " + misc2);
                if ((misc2 == null || !misc2.contains("C")) && consigneeN4Id != consigneeIdBdb) {
                    def cnee;
                    if (shipperIdBdb == consigneeIdBdb) {
                        cnee = shipper;
                    } else {
                        cnee = Shipper.findOrCreateShipper(consigneeIdBdb, consigneeNameBdb);
                    }

                    if (cnee == null) {
                        println("Consignee " + consigneeNameBdb + " not configured in N4.");
                    } else {
                        unit.getUnitGoods().updateConsignee(cnee);
                        println("update consignee with BDB.");
                        comments += "RECON X " + consigneeN4;
                    }
                }

                def destPortN4 = unit.getFieldValue("unitGoods.gdsDestination");
                println("destPortN4 = " + destPortN4);
                if ((misc2 == null || !misc2.contains("P")) && destPortN4 != destPortBdb) {
                    unit.setFieldValue("unitGoods.gdsDestination", destPortBdb);
                    comments = comments + " " + destPortN4 + " to " + destPortBdb;
                    //A5 - Unit Import, not Inbound and if BdbDestination is not equal to N4Pod then set bdbDest to POD
                    def pod = podLookup.lookupPod(destPortBdb);
                    discPortBdb = pod;
                    if (pod != dischargePortN4) {
                        println("Setting the POD to BDB DestPort" + "dischargePortN4 = " + dischargePortN4 + " destPortBdb :" + pod);
                        def podRgtPoint = RoutingPoint.findRoutingPoint(pod);
                        if (podRgtPoint == null) {
                            emailSender.custSendEmail(emailFrom, emailTo, " BDB error for Booking " + dbdBookingNum + " and Unit " + ctrId, emailBody + "POD Port not a Routing Point. POD lookup NULL: " + destPortBdb + note);
                            fail("ERR_GVY_BDB_007.5. POD Port not a Routing Point. POD lookup NULL: " + destPortBdb);
                        }
                        unit.getUnitRouting().setRtgPOD1(podRgtPoint);
                    }
                } else {
                    discPortBdb = destPortN4;
                }
                unit.getUnitGoods().updateShipper(shipper);
                def servicesMgr = (ServicesManager) Roastery.getBean("servicesManager");

                if ((misc2 == null || !misc2.contains("B")) && inBoundBdb == "Y") {
                    servicesMgr.applyHold("INB", unit, null, null, "BDB Hold");
                    servicesMgr.applyHold("CUS", unit, null, null, "BDB Hold");
                    comments = comments + " INB and CUS hold applied.";
                }

                def eqType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
                println("eqType = " + eqType);
                if (agFlag != null && (misc2 == null || !misc2.contains("A")) && agFlag == "Y" && eqType[0] != "R") {
                    servicesMgr.applyHold("AG", unit, null, null, "BDB Hold");
                    comments += " add AG hold.";
                    misc2 += "L";
                    unit.setFieldValue("unitFlexString11", misc2);
                }


                if (agFlag != null && (misc2 == null || !misc2.contains("A")) && agFlag == "N") { //&& onAgHold()) {
                    servicesMgr.applyGuardedPermission("AG", unit, null, null, "BDB Hold");
                }
            } else { // if not IMPORT
                def isLongHaul = isLongHaul(vesvoyBdb)
                println("in NOT IMPORT section. isLongHaul :" + isLongHaul);
                //A14 - If Inbound Export and BDBVesvoy is Barge and Roll over Booking Values
                if (!isLongHaul) {
                    def isBkgRoll = rollBkgForExport(unit)
                    //Commented out as it does nothing - if(isBkgRoll){  event.postNewEvent("UNIT_ROLL", "Inbound Export With Vesvoy as barge");}
                } else {
                    // SKB Updated to get an existing visit and not create a new one.
                    if (!UfvTransitStateEnum.S70_DEPARTED.equals(transitState)) {
                        def visit = gvyVesselLookup.getCarrierVisit(vesvoyBdb);
                        if (visit != null) {
                            def ufvLocal = unit.unitActiveUfv;
                            if (ufvLocal == null) {
                                def ulookup = gvyBaseClass.getGroovyClassInstance("GvyUnitLookup");
                                ufvLocal = ulookup.lookupFacility(unit.primaryKey);
                            }
                            ufvLocal.updateObCv(visit);
                        } else {
                            emailSender.custSendEmail(emailFrom, emailTo, " BDB error for Booking " + dbdBookingNum + " and Unit " + ctrId, emailBody + "OB vesvoy not in N4: " + vesvoyBdb + note);
                            fail("ERR_GVY_DBD_007. OB vesvoy not in N4: " + vesvoyBdb);
                        }
                    }//Outer if Ends
                    //A5 - Unit Not Import,Not Inbound and if PODBdb and N4Pod dont match then set POD to Bdb POD
                    println("dischargePortN4 = " + dischargePortN4);
                    if (discPortBdb != dischargePortN4) {
                        def podRtgBdb = RoutingPoint.findRoutingPoint(discPortBdb)
                        if (podRtgBdb == null) {
                            emailSender.custSendEmail(emailFrom, emailTo, " BDB error for Booking " + dbdBookingNum + " and Unit " + ctrId, emailBody + "Destination Port not a Routing Point. POD lookup NULL: " + discPortBdb + note);
                            fail("ERR_GVY_BDB_007.6. Destination Port not a Routing Point. POD lookup NULL: " + discPortBdb);
                        }
                        unit.getUnitRouting().setRtgPOD1(podRtgBdb);
                    }

                    unit.setFieldValue("unitGoods.gdsDestination", destPortBdb);
                    def shipper = Shipper.findOrCreateShipper(shipperIdBdb, shipperNameBdb);
                    unit.getUnitGoods().updateShipper(shipper);
                    def conee;
                    if (shipperIdBdb == consigneeIdBdb) {
                        conee = shipper;
                    } else {
                        conee = Shipper.findOrCreateShipper(consigneeIdBdb, consigneeNameBdb);
                    }
                    unit.getUnitGoods().updateConsignee(conee);
                }//innner Else ends to roll over Bkg
            } // end of if IMPORT and EDXPORT LOGIC

            println("end of import and non-import.");

            //A3 -Set OB Carrier to Barge
            // A10, use the looked up value not the passed value
            setOBCarrierOnPODChng(ctrId, dischargePortN4, discPortBdb);

            // Fixed code to set the bzuId
            def lineOperatorN4 = unit.getUnitLineOperator();
            if (lineOperatorN4 != null) lineOperatorN4 = lineOperatorN4.bzuId;
            else lineOperatorN4.equals("");
            if (primCarrierBdb != null && !primCarrierBdb.equals("") && !lineOperatorN4.equals(primCarrierBdb)) {
                def bzuid = com.navis.argo.business.reference.LineOperator.findLineOperatorById(primCarrierBdb);
                if (bzuid != null) {
                    unit.setUnitLineOperator(bzuid);
                } else {
                    emailSender.custSendEmail(emailFrom, emailTo, " BDB error for Booking " + dbdBookingNum + " and Unit " + ctrId, emailBody + "Line Operator is not in N4: " + primCarrierBdb + note);
                    fail("ERR_GVY_DBD_008. Line Operator is not in N4: " + primCarrierBdb);
                }
            }

            println("isUnitSit = " + sit);

            def misc2 = unit.getFieldValue("unitFlexString11");
            misc2 = misc2 != null ? misc2 : ""
            //A3 - Update Code for ClassCastException
            if ("Y".equals(sit) && !isUnitSIT && !misc2.contains("S")) { //A16 Added DrayStatus check
                event.postNewEvent("SIT_ASSIGN", "BDB SIT_ASSIGN");
            }

            println("comments = " + comments);
            if (!comments.equals("")) {
                if (unit.getFieldValue("unitRemark") != null) comments = unit.getFieldValue("unitRemark") + comments;
                if (comments.length() > 255) {
                    comments = comments.substring(0, 255);
                }
                unit.setFieldValue("unitRemark", comments);
            }

            //A1 - Tracker Change
            def changes = tracker.getChanges(unit);
            if (changes != null && changes.getFieldChangeCount() != 0) {
                unit.recordUnitEvent(com.navis.argo.business.atoms.EventEnum.UNIT_PROPERTY_UPDATE, changes, "Field Update BDB");
            }

            event.postNewEvent("BDB", "ACETS: " + recorder);

        } catch (Exception e) {
            e.printStackTrace()
            throw e;
        }
    }

    private boolean isLongHaul(String vesvoy) {
        //def gvyBaseClass = new GroovyInjectionBase()
        def gvyUtil = getGroovyClassInstance("GvyCmisUtil");
        def vType = gvyUtil.getVesselClassType(vesvoy);
        return vType == "CELL" ? true : false;
    }

    /*
    1] Set OBCarreir as GEN_VESSEL if POD is chnaged to NIS
    2] Set OBCarreir as GEN_TRUCK if POD is chnaged to HON
    3] Set only if POD changed and unit is not Departed
  */

    public void setOBCarrierOnPODChng(String unitId, String dischargePortN4, String dischPortBdb) {
        try {
            // Get Unit Object
            def unitFinder = getUnitFinder()
            def complex = ContextHelper.getThreadComplex();
            def inEquipment = Equipment.loadEquipment(unitId);
            def unit = unitFinder.findActiveUnit(complex, inEquipment)
            //A7
            if (unit == null) {
                return
            }

            def gvyCmisUtil = getGroovyClassInstance("GvyCmisUtil");

            def prevDischPort = dischargePortN4 != null ? dischargePortN4 : ""

            //Set BDB DischPort as DischargePort
            def curDischPort = dischPortBdb != null ? dischPortBdb : ""

            println("BDB curDischPort ::" + curDischPort + "   prevDischPort ::" + prevDischPort)

            def intdObCarrierId = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
            intdObCarrierId = intdObCarrierId != null ? intdObCarrierId : ""

            boolean ObcarrierFlag = intdObCarrierId.equals("GEN_TRUCK") || intdObCarrierId.equals("GEN_VESSEL") || intdObCarrierId.equals("GEN_CARRIER") ? true : false

            boolean ObcarrierFlagHon = intdObCarrierId.equals("BARGE") || intdObCarrierId.equals("GEN_VESSEL") || intdObCarrierId.equals("GEN_CARRIER") ? true : false

            //Set OB Carrier visit
            def visit = ""
            if (/*gvyCmisUtil.isNISPort(curDischPort) && !gvyCmisUtil.isNISPort(prevDischPort) &&*/ ObcarrierFlag) {
                //todo ask amit
                //SET TO BARGE
                visit = com.navis.argo.business.model.CarrierVisit.findOrCreateVesselVisit(com.navis.argo.ContextHelper.getThreadFacility(), "BARGE")
                unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).setUfvIntendedObCv(visit)
                unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).setUfvActualObCv(visit)
            } else if (curDischPort.equals(ContextHelper.getThreadFacility().getFcyId()) &&
                    !prevDischPort.equals(ContextHelper.getThreadFacility().getFcyId()) && ObcarrierFlagHon) {
                visit = com.navis.argo.business.model.CarrierVisit.getGenericTruckVisit(complex);
                unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).updateObCv(visit);
            }
            println("OBcarrier set to " + unit.getFieldValue("unitActiveUfv.ufvIntendedObCv"))
        } catch (Exception e) {
            e.printStackTrace()
            fail("BDB Error setting OBCarrier On POD Change" + unitId);
        }

    }//Method Ends

    private boolean isStowplan(unit) {
        def remark = unit.unitRemark;
        if (remark == null) return false;
        return remark.startsWith("Stowplan Data");
    }

    public boolean rollBkgForExport(Object unit) {
        try {
            def facility = getFacility();
            def bl_nbr = unit.getFieldValue("unitGoods.gdsBlNbr")
            if (bl_nbr == null || "".equals(bl_nbr)) {
                return false;
            }

            def unitLineOperator = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoLine.bzuId")
            unitLineOperator = unitLineOperator != null ? unitLineOperator : null
            if (unitLineOperator == null || "".equals(unitLineOperator)) {
                return false;
            }
            def bizScope = ScopedBizUnit.findScopedBizUnit(unitLineOperator, BizRoleEnum.LINEOP);

            def bkgVesvoy = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoVesselVisit.cvId")
            def cv = CarrierVisit.findVesselVisit(facility, bkgVesvoy);
            if (cv == null) {
                fail("ERR_GVY_LNK_010. Could not find Export Bkg carrier visit: " + bkgVesvoy);
            }
            def booking = Booking.findBookingByUniquenessCriteria(bl_nbr, bizScope, cv);
            if (booking == null) {
                fail("ERR_GVY_LNK_011. Could not Inbound Export Bkg for carrier visit: " + bkgVesvoy);
                return false;
            }
            //A15
            def eqoMgr = new EquipmentOrderManagerPea();
            //eqoMgr.assignExportBookingToUnit( booking, unit);
            eqoMgr.assignExportBookingToUnit(booking, null, unit); //Update Method for TOS2.1 -- A16

            println("bl_nbr=" + bl_nbr + " unitLineOperator=" + unitLineOperator + " bkgVesvoy=" + bkgVesvoy)
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            fail("BDB Error Rolling BKG over to Unit" + unit.unitId);
        }

    }

    public void setOBCarrierOnPODChngForAK(String unitId, String dischargePortN4, String dischPortBdb, def vesvoy) {
        try {
            // Get Unit Object
            def unitFinder = getUnitFinder()
            def complex = ContextHelper.getThreadComplex();
            def fcyId = ContextHelper.getThreadFacility().getFcyId()
            def inEquipment = Equipment.loadEquipment(unitId);
            def unit = unitFinder.findActiveUnit(complex, inEquipment)
            //A7
            if (unit == null) {
                return
            }

            def gvyCmisUtil = getGroovyClassInstance("GvyCmisUtil");

            def prevDischPort = dischargePortN4 != null ? dischargePortN4 : ""

            //Set BDB DischPort as DischargePort
            def curDischPort = dischPortBdb != null ? dischPortBdb : ""

            println("BDB curDischPort ::" + curDischPort + "   prevDischPort ::" + prevDischPort)

            def intdObCarrierId = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
            intdObCarrierId = intdObCarrierId != null ? intdObCarrierId : ""

            boolean ObcarrierFlag = intdObCarrierId.equals("GEN_TRUCK") || intdObCarrierId.equals("GEN_VESSEL") || intdObCarrierId.equals("GEN_CARRIER") ? true : false

            boolean ObcarrierFlagAK = /*intdObCarrierId.equals("BARGE") || */ intdObCarrierId.equals("GEN_VESSEL") || intdObCarrierId.equals("GEN_CARRIER") ? true : false

            //Set OB Carrier visit
            def visit = ""
            if (gvyCmisUtil.isAlaskanNISPort(curDischPort) && !gvyCmisUtil.isAlaskanNISPort(prevDischPort) && ObcarrierFlag) {
                //todo ask amit
                if(fcyId== 'ANK' || fcyId== 'KDK' ){
                    visit = com.navis.argo.business.model.CarrierVisit.findOrCreateVesselVisit(com.navis.argo.ContextHelper.getThreadFacility(), "GEN_VESSEL")
                }else{
                    //SET TO BARGE
                    visit = com.navis.argo.business.model.CarrierVisit.findOrCreateVesselVisit(com.navis.argo.ContextHelper.getThreadFacility(), "BARGE")
                }
                unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).setUfvIntendedObCv(visit)
                unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).setUfvActualObCv(visit)
            } else if (curDischPort.equals(ContextHelper.getThreadFacility().getFcyId()) &&
                    !prevDischPort.equals(ContextHelper.getThreadFacility().getFcyId()) && ObcarrierFlagAK) {
                visit = com.navis.argo.business.model.CarrierVisit.getGenericTruckVisit(complex);
                unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).updateObCv(visit);
            }else if(ObcarrierFlag && (prevDischPort.equals(ContextHelper.getThreadFacility().getFcyId()) && curDischPort != prevDischPort)){
// then set the o/b as vesvoy
                def cv  = com.navis.argo.business.model.CarrierVisit.findVesselVisit(com.navis.argo.ContextHelper.getThreadFacility(), vesvoy)
                unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).setUfvIntendedObCv(cv)
                unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).setUfvActualObCv(cv)

            }
            println("OBcarrier set to " + unit.getFieldValue("unitActiveUfv.ufvIntendedObCv"))
        } catch (Exception e) {
            e.printStackTrace()
            fail("BDB Error setting OBCarrier On POD Change" + unitId+e);
        }

    }//Method Ends


}