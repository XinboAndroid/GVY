/*
**********************************************************************
* Srno   Date	             Changer	 Change Description
* A1     12/29/08       Glenn Raposo	 Unit Field Tracker code Added
* A2     04/20/09       Steven Bauer	 Changed code to find or create.
* A3     05/01/09       Steven Bauer     Added Workaround for booking reassign error!
* A4     05/11/09       Steven Bauer	 Do POD lookup for import
*					 Apply import code to Through and Tranship
*					 Set storage to export.
* A5     05/13/09      Steven Bauer	 Handle T60 imports,
*					 If the import has a Booking, update the booking.
* A6     05/26/09      Steven Bauer	 Find active unit in complex
* A7     06/02/09	Steven Bauer	 403 - Supress all updates before GetNV
* A8     12/18/2009     Glenn Raposo	 Export Cntr to check and rolls over client cntr Booking
* A9     09/12/11   Glenn Raposo     Update booking method for TOS2.1
* A10    12/09/11   Glenn Raposo     TT#13964 Create Booking on LNK if Bkg doesnt exist
* A11    12/13/11   Glenn Raposo   Update HOLD FOR LNK
* A12    12/13/11   Glenn Raposo   Added Check To create LNK only on GUM-CHINA port
**********************************************************************
*/


import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Facility
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.reference.Shipper
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.orders.business.eqorders.EquipmentOrderManagerPea
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Logger

class GvyInjAlnk extends GroovyInjectionBase {
    private static final Logger LOGGER = Logger.getLogger(GvyInjAlnk.class);

    public String execute(Map inParameters) {

        com.navis.argo.ContextHelper.setThreadExternalUser("ACETS");
//Getting the parameter values from SNX.
        def ctrId = (String) inParameters.get("equipment-id");
        def bookingNum = (String) inParameters.get("bookingNum");
        def userId = (String) inParameters.get("userId");
        def vesvoy = (String) inParameters.get("vesvoy");
        def shipperName = (String) inParameters.get("shipperName");
        def shipperId = (String) inParameters.get("shipperId");
        def consigneeName = (String) inParameters.get("consigneeName");
        def consigneeId = (String) inParameters.get("consigneeId");
        def destPort = (String) inParameters.get("destPort");
        def dischargePort = (String) inParameters.get("dischargePort");
        def primCarrier = (String) inParameters.get("primCarrier");

        final String emailFrom = '1aktosdevteam@matson.com'
        final String emailTo = "1aktosdevteam@matson.com";
        def unitFreightKind = null;
        def bkngFreightKind = null;
        def emailBody = null;
        def note = "\n\nPlease fix either the booking or unit and review the routing after correction";

        def inj = new GroovyInjectionBase();
        def emailSender = inj.getGroovyClassInstance("EmailSender");

        // find the unit
        def ufv;
        def unit;
        //A1- tracker
        def tracker;
        GroovyApi gvyApi = new GroovyApi();
        def podLookup = gvyApi.getGroovyClassInstance("GvyRefDataLookup");
        def unitLookup = gvyApi.getGroovyClassInstance("GvyUnitLookup");
        /**
         * D033647
         * LNK Messages for Older VVD's should be Rejected
         *
         */
        LOGGER.info("Start of Vessel Visit Phase Validation");
        boolean hasFailureMailSend = Boolean.FALSE;
        try {
            Facility facility = getFacility();
            CarrierVisit cv = CarrierVisit.findVesselVisit(facility, vesvoy);
            if (cv == null) {
                String messageBody = getEmailBody(facility, vesvoy, bookingNum, ctrId, cv);
                messageBody = messageBody + "\nCarrier Visit is Not available in N4";
                emailSender.custSendEmail(emailFrom, emailTo, " LNK error for Booking " + bookingNum + " and Unit " + ctrId, messageBody + "Could not find the carrier visit: " + vesvoy + note);
                hasFailureMailSend = Boolean.TRUE;
                fail("ERR_GVY_LNK_007. Could not find the carrier visit: " + vesvoy);
            } else if (cv != null && (CarrierVisitPhaseEnum.DEPARTED.equals(cv.getCvVisitPhase()) ||
                    CarrierVisitPhaseEnum.ARCHIVED.equals(cv.getCvVisitPhase()) || CarrierVisitPhaseEnum.CANCELED.equals(cv.getCvVisitPhase()))) {

                String messageBody = getEmailBody(facility, vesvoy, bookingNum, ctrId, cv);
                messageBody = messageBody + "\nCarrier Visit is in Phase\t" + cv.getCvVisitPhase() + " it cannot be Linked to booking \t" + bookingNum;
                emailSender.custSendEmail(emailFrom, emailTo, " LNK error for Booking " + bookingNum + " and Unit " + ctrId, messageBody);
                hasFailureMailSend = Boolean.TRUE;
                LOGGER.info("Skipping further processing of ALNK for Booking \t" + bookingNum + " and Unit \t" + ctrId + "\tas vessel is in phase\t" + cv.getCvVisitPhase());
                fail("ERR_GVY_LNK_010. Carrier visit at " + facility.getFcyId() + " is in phase" + cv.getCvVisitPhase() + " it cannot be used for LNK");
            }
        } catch (Exception e1) {
            if (!hasFailureMailSend) {
                emailSender.custSendEmail(emailFrom, emailTo, "Error Processing LNK Message", "Message Parameters\n\n\n" + inParameters + "\n\n\nException is\n" + e1.getMessage());
            }
            //Return and don't process any further
            fail("ERR_GVY_LNK_010. Failed to process LNK Message for booking\t" + bookingNum + "\t and Container" + ctrId + ", exception is\t" + e1.getMessage());
        }
        LOGGER.info("End of Vessel Visit Phase Validation");
        /**
         * End of logic for D033647 LNK Messages for Older VVD's should be Rejected
         */

//Get the active UFV from the complex
        try {
            //ufv = findActiveUfv(ctrId);
            //A6
            ufv = unitLookup.getUfvActiveInComplex(ctrId);
            LOGGER.warn("UFV:::" + ufv);
            if (ufv == null) {
                emailSender.custSendEmail(emailFrom, emailTo, " LNK error ", "Could not find active unit: " + ctrId + " in any facility");
                throw new Exception("Could not find active unit");
            }
            unit = ufv.getUfvUnit();
            LOGGER.warn("Unit::;" + unit);
            //A7
            //if(isStowplan(unit)) return;

            List bkng = Booking.findBookingsByNbr(bookingNum);
            Iterator iter = bkng.iterator();
            while (iter.hasNext()) {
                def book = iter.next();
                bkngFreightKind = book.eqoEqStatus;
                bkngFreightKind = bkngFreightKind.getKey()
            }

            //A1- Tracker Change
            def gvyUnitFldTracker = gvyApi.getGroovyClassInstance("GvyUnitFieldTracker");
            tracker = gvyUnitFldTracker.createFieldChangeTracker(unit)

            unitFreightKind = unit.getFieldValue("unitFreightKind");
            unitFreightKind = unitFreightKind.getKey();
        } catch (Exception ex) {
            //emailSender.custSendEmail(emailFrom,emailTo," LNK error for Booking "+ bookingNum+ " and Unit " +ctrId ,"Could not find active unit: " + ctrId+note);
            fail("ERR_GVY_LNK_001. Could not find active unit: " + ctrId);
        }

        if (unit == null) {
            //emailSender.custSendEmail(emailFrom,emailTo," LNK error for Booking "+ bookingNum+ " and Unit " +ctrId ,"Could not find active unit: " + ctrId+note);
            fail("ERR_GVY_LNK_001. Could not find active unit: " + ctrId);
        }



        StringBuffer header = new StringBuffer();
        header.append("\nBooking Number       : " + bookingNum);
        header.append("\nContainer Number     : " + ctrId);
        header.append("\nBooking FreightKind   : " + bkngFreightKind);
        header.append("\nContainer FreightKind : " + unitFreightKind);
        header.append("\n\n");

        emailBody = header.toString();

        if (!UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) && !UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory()) && !UnitCategoryEnum.THROUGH.equals(unit.getUnitCategory()) && !UnitCategoryEnum.TRANSSHIP.equals(unit.getUnitCategory())) {
            emailSender.custSendEmail(emailFrom, emailTo, " LNK error for Booking " + bookingNum + " and Unit " + ctrId, emailBody + ctrId + " category is not IMPORT, EXPORT, THROUGH or TRANSHIP. Ignored." + note);
            fail("ERR_GVY_LNK_002. Unit: " + ctrId + " category is not IMPORT, EXPORT, THROUGH or TRANSHIP. Ignored.");
        }
        def transitState = ufv.getUfvTransitState();
        def obCarrier = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId");
        def obCarrierMode = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCarrierMode");
        def prevPODN4 = unit.getFieldValue("unitRouting.rtgPOD1.pointId");

        if (!(transitState.equals(UfvTransitStateEnum.S50_ECOUT) ||
                transitState.equals(UfvTransitStateEnum.S30_ECIN) ||
                transitState.equals(UfvTransitStateEnum.S20_INBOUND) ||
                transitState.equals(UfvTransitStateEnum.S40_YARD))) {

            if (((transitState.equals(UfvTransitStateEnum.S70_DEPARTED) ||
                    transitState.equals(UfvTransitStateEnum.S60_LOADED)) &&
                    !isBarge(obCarrier))) {
                emailSender.custSendEmail(emailFrom, emailTo, " LNK error for Booking " + bookingNum + " and Unit " + ctrId, emailBody + ctrId +
                        " TransitState is not in DEPARTURED or LOAD and OB Carrier is not barge." +
                        " And TransitState is not in YARD or INBOUND or EC/In or EC/out." + note);
                fail("ERR_GVY_LNK_003. The unit: " + ctrId +
                        " TransitState is not in DEPARTURED or LOAD and OB Carrier is not barge." +
                        " And TransitState is not in YARD or INBOUND or EC/In or EC/out.");
            }
        }
        if (transitState.equals(UfvTransitStateEnum.S60_LOADED) && LocTypeEnum.VESSEL.equals(obCarrierMode)) {
            emailSender.custSendEmail(emailFrom, emailTo, " LNK error for Booking " + bookingNum + " and Unit " + ctrId, emailBody + ctrId + " TransitState is LOADED and OB Carrier is VESSEL." + note);
            fail("ERR_GVY_LNK_004. The unit: " + ctrId + " TransitState is LOADED and OB Carrier is VESSEL.");
        }

        //A10 - Create Booking if It doesnt Exist
        try {
            def bkgObj = findCreateBooking(bookingNum, primCarrier, vesvoy, destPort, podLookup, unit, consigneeId, shipperId)
            LOGGER.warn("Booking Obj:::" + bkgObj);
            def eqoMgr = new EquipmentOrderManagerPea();
            eqoMgr.assignExportBookingToUnit(bkgObj, null, unit);
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Exception :::" + e);
            // For Import Unit update BlNbr on exception and let the unit process
            if (!UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory())) {
                unit.setFieldValue("unitGoods.gdsBlNbr", bookingNum);
            }//A12 - Throw exception for Export Cntr
            else if (UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory())) {
                emailSender.custSendEmail(emailFrom, emailTo, " LNK error for Booking " + bookingNum + " and Unit " + ctrId, emailBody + "Cannot Roll Booking for unit: " + ctrId + note);
                fail("ERR_GVY_LNK_021. Cannot Roll Booking for unit: " + ctrId);
            }
        }

        //unit.setFieldValue("unitGoods.gdsBlNbr", bookingNum);
        boolean t60 = false;
        if (UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) || UnitCategoryEnum.THROUGH.equals(unit.getUnitCategory()) || UnitCategoryEnum.TRANSSHIP.equals(unit.getUnitCategory())) {
            // Change to Create.

            def shipper = Shipper.findOrCreateShipper(shipperId, shipperName);
            //ScopedBizUnit.findScopedBizUnit( shipperId, BizRoleEnum.SHIPPER);
            if (shipper == null) {
                emailSender.custSendEmail(emailFrom, emailTo, " LNK error for Booking " + bookingNum + " and Unit " + ctrId, emailBody + "Could not find/create the shipper with ID: " + shipperId + note);
                fail("ERR_GVY_LNK_005. Could not find/create the shipper with ID: " + shipperId);
            }

            def consignee = null;
            if (shipperId.equals(consigneeId)) {
                consignee = shipper;
            } else {
                consignee = Shipper.findOrCreateShipper(consigneeId, consigneeName);
            }
            //ScopedBizUnit.findScopedBizUnit( consigneeId, BizRoleEnum.SHIPPER);
            if (consignee == null) {
                emailSender.custSendEmail(emailFrom, emailTo, " LNK error for Booking " + bookingNum + " and Unit " + ctrId, emailBody + "Could not find/create the consignee with ID: " + consigneeId + note);
                fail("ERR_GVY_LNK_006. Could not find/create the consignee with ID: " + consigneeId);
            }
            unit.getUnitGoods().updateShipper(shipper);
            unit.getUnitGoods().updateConsignee(consignee);
            unit.setFieldValue("unitGoods.gdsDestination", destPort);

            // Ignore dischargePort, use pod from lookup
            //unit.getUnitRouting().getRtgPOL().setRtgPOD1( RoutingPoint.findRoutingPoint( dischargePort));
            // customizing podLookup for the Alaska TOS LNK
            def pod = podLookup.lookupPodForAK(destPort);

            LOGGER.info("POD from db lookup:" + pod);
            if (pod == null) {
                LOGGER.warn("calling lookup POD in CAS");
                pod = podLookup.lookupPodForAKinCAS(destPort, ctrId, bookingNum);
                LOGGER.warn("POD from CAS lookup:" + pod);
            }
            def resolvedPod = RoutingPoint.findRoutingPoint(pod)
            unit.getUnitRouting().setRtgPOD1(resolvedPod);
            LOGGER.warn("Resolved POD:" + pod);

            //Setting the OB Carrier based on Prev and current DischargePort
            def gvyInjAbdb = getGroovyClassInstance("GvyInjAbdb");
            gvyInjAbdb.setOBCarrierOnPODChngForAK(ctrId, prevPODN4, destPort, vesvoy)
            def facility = getFacility();
            if (UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) && transitState.equals(UfvTransitStateEnum.S40_YARD) && (prevPODN4.equals(facility.getFcyId()) && (resolvedPod.getPointId() != prevPODN4))) {
                try {

                    unit.setUnitCategory(UnitCategoryEnum.EXPORT);
                    def bkgObj = findCreateBooking(bookingNum, primCarrier, vesvoy, destPort, podLookup, unit, consigneeId, shipperId)
                    LOGGER.warn("Booking Obj:::" + bkgObj);
                    def eqoMgr = new EquipmentOrderManagerPea();
                    eqoMgr.assignExportBookingToUnit(bkgObj, null, unit);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (unit.isReservedForBooking()) {
                t60 = true;
                unit.setUnitCategory(UnitCategoryEnum.EXPORT);
            }
        }
        def event = new GroovyEvent(null, unit);
        if (UnitCategoryEnum.STORAGE.equals(unit.getUnitCategory())) {
            unit.setUnitCategory(UnitCategoryEnum.EXPORT);
        }

        /*if ( UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory()) ) {
            // find booking
            def facility = getFacility();
            def cv = CarrierVisit.findVesselVisit( facility, vesvoy);
            if ( cv == null) {
                emailSender.custSendEmail(emailFrom,emailTo," LNK error for Booking "+ bookingNum+ " and Unit " +ctrId ,emailBody + "Could not find the carrier visit: " + vesvoy+note);
                fail( "ERR_GVY_LNK_007. Could not find the carrier visit: " + vesvoy);
            }
             //A8 - 1. lookup primCarrier Business unit if null then lookup unit for MAT
            primCarrier = primCarrier != null ? primCarrier : "MAT"
            def bizScope = ScopedBizUnit.findScopedBizUnit( primCarrier, BizRoleEnum.LINEOP);
            bizScope = bizScope == null ? ScopedBizUnit.findScopedBizUnit( "MAT", BizRoleEnum.LINEOP) : bizScope;
            if ( bizScope == null) {
                    emailSender.custSendEmail(emailFrom,emailTo," LNK error for Booking "+ bookingNum+ " and Unit " +ctrId ,emailBody + "Could not find the business unit: Line Operator"+note);
                    fail( "ERR_GVY_LNK_008. Could not find the business unit: Line Operator");
            }
            def booking = Booking.findBookingByUniquenessCriteria( bookingNum, bizScope, cv);
            if ( booking == null) {
                //A8 - If No Booking exist across Prim carreir then lookup Bkg with lineOP MAT.
                bizScope = ScopedBizUnit.findScopedBizUnit( "MAT", BizRoleEnum.LINEOP);
                booking = Booking.findBookingByUniquenessCriteria( bookingNum, bizScope, cv);
                if(booking == null){
                   //A15 - TOS2.1 Create Booking if Booking is NULL
                   Booking cretedBkg = createBooking(unit, vesvoy, bizScope, destPort, bookingNum, podLookup)
                   if(cretedBkg == null){
                      emailSender.custSendEmail(emailFrom,emailTo," LNK error for Booking "+ bookingNum+ " and Unit " +ctrId ,emailBody + "Could not find booking: " + bookingNum+note);
                      fail( "ERR_GVY_LNK_009. Could not find booking: " + bookingNum);
                   }else{
                      booking = cretedBkg;
                   }

                }
            }
            // assign booking
            def eqoMgr = new EquipmentOrderManagerPea();
            //A3 Termp set to tranship for Navis bug
            boolean makeTranship = false;
            if( ufv != null && UfvTransitStateEnum.S20_INBOUND.equals(ufv.ufvTransitState) && LocTypeEnum.VESSEL.equals(ufv.ufvLastKnownPosition.posLocType) ) {
                makeTranship = true;
                ufv.ufvLastKnownPosition.posLocType  =  LocTypeEnum.TRUCK
            }
            eqoMgr.assignExportBookingToUnit( booking, null, unit); //Update Method for TOS2.1 -- A9
            if(makeTranship) {
               ufv.ufvLastKnownPosition.posLocType  =  LocTypeEnum.VESSEL
            }
            //event.postNewEvent( "UNIT_ROLL", "ACETS: " + userId);
            // If Vessel is departed roll to the next vessel.
            // 266:
            def vesLookup = gvyApi.getGroovyClassInstance("GvyVesselLookup");
            if(vesLookup.isClosed(cv.cvVisitPhase)) {
               def roll = gvyApi.getGroovyClassInstance("RejectCarrierVisitPhaseNotActive");
               def line = LineOperator.resolveLineOprFromScopedBizUnit(cv.cvOperator)
               roll.executeForLnk(unit,line);
            }

            if(t60) {
               unit.setUnitCategory(UnitCategoryEnum.IMPORT);
            }
        }*/
        def servicesMgr = (ServicesManager) Roastery.getBean("servicesManager");
        servicesMgr.applyGuardedPermission("HOLD FOR LNK", unit, null, null, "LNK BKG Hold release");
        unit.setFieldValue("unitRemark", "ACETS: " + userId);
        //A1 - Tracker Change
        def changes = tracker.getChanges(unit);
        if (changes != null && changes.getFieldChangeCount() != 0) {
            unit.recordUnitEvent(com.navis.argo.business.atoms.EventEnum.UNIT_PROPERTY_UPDATE, changes, "Field Updated LNK Data");
        }
        event.postNewEvent("LNK", "ACETS: " + userId);
    }

    private boolean isBarge(String vesvoy) {
        try {
            //def gvyBaseClass = new GroovyInjectionBase()
            def gvyUtil = getGroovyClassInstance("GvyCmisUtil");
            def vType = gvyUtil.getVesselClassType(vesvoy);
            return vType == "BARGE" ? true : false;
        } catch (Exception e) {
            return false;
        }
    }

    private String getEmailBody(Facility inFacility,
                                String inVesselVoyage,
                                String inBookingNumber,
                                String inContainerId,
                                CarrierVisit inCarrierVisit) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("Facility	\t\t" + inFacility.getFcyId() + "\n");
        buffer.append("VesselVoyage\t" + inVesselVoyage + "\n");
        buffer.append("Booking Number\t" + inBookingNumber + "\n");
        buffer.append("Container Number\t" + inContainerId + "\n");
        if (inCarrierVisit != null) {
            buffer.append("Visit Phase\t" + inCarrierVisit.getCvVisitPhase() + "\n");
        }
        return buffer.toString();

    }

    private boolean isStowplan(unit) {
        def remark = unit.unitRemark;
        if (remark == null) return false;
        return remark.startsWith("Stowplan Data");
    }

    /*
    * Added Method to handel bookings from the IMPORT CNTRSS
    */

    private Booking findCreateBooking(String bookingNum, String primCarrier, String vesvoy, String destPort, Object podLookup, Object unit, String consigneeId, String shipperId) {
        def booking = null;
        try {
            primCarrier = primCarrier != null ? primCarrier : "MAT"
            def bizScope = ScopedBizUnit.findScopedBizUnit(primCarrier, BizRoleEnum.LINEOP);
            bizScope = bizScope == null ? ScopedBizUnit.findScopedBizUnit("MAT", BizRoleEnum.LINEOP) : bizScope;
            LOGGER.warn("In find booking::::" + primCarrier);
            LOGGER.warn("In find booking::::" + bookingNum);


            def facility = getFacility();
            def cv = CarrierVisit.findVesselVisit(facility, vesvoy);
            LOGGER.warn("In find booking::::Facility:::" + facility);
            LOGGER.warn("In find booking::::" + cv);
            booking = Booking.findBookingByUniquenessCriteria(bookingNum, bizScope, cv);
            LOGGER.warn("In find booking 1::::" + booking);
            if (booking == null) {
                //A8 - If No Booking exist across Prim carreir then lookup Bkg with lineOP MAT.
                bizScope = ScopedBizUnit.findScopedBizUnit("MAT", BizRoleEnum.LINEOP);
                booking = Booking.findBookingByUniquenessCriteria(bookingNum, bizScope, cv);
                LOGGER.warn("In find booking 2::::" + booking);
                if (booking == null) {
                    //A15 - TOS2.1 Create Booking if Booking is NULL
                    Booking cretedBkg = createBooking(unit, vesvoy, bizScope, destPort, bookingNum, podLookup, consigneeId, shipperId)
                    if (cretedBkg == null) {
                        LOGGER.warn("Created Booking::::" + cretedBkg);
                        fail("ERR_GVY_LNK_009. Could not find/Create booking: " + bookingNum);
                    }
                    booking = cretedBkg;
                    //unit.setUnitCategory(UnitCategoryEnum.EXPORT);
                    HibernateApi.getInstance().flush();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return booking;
    }

    private Booking createBooking(Object unit, String vesvoy, Object bizScope, String destPort, String bookingNum, Object podLookup, String consigneeId, String shipperId) {
        GroovyApi gvyApi = new GroovyApi();
        try {
            def unitId = unit.unitId;
            def pod = "";
            def facility = ContextHelper.getThreadFacility();
            def cv = CarrierVisit.findVesselVisit(facility, vesvoy);
            LOGGER.warn(" In create Booking::::Carrier Visit :::" + cv);
            def freightKind = unit.getUnitFreightKind();
            def rtgPol = RoutingPoint.findRoutingPoint(ContextHelper.getThreadFacility().getFcyId());
            LOGGER.warn(" In create Booking::::Dest as it came in:::" + destPort);
            def podLookupFromDest = gvyApi.getGroovyClassInstance("GvyRefDataLookup");
            if (isBarge(vesvoy)) {
                LOGGER.warn(" In create Booking::::vesvoy is a barge :::" + cv);
                if (destPort == 'KQA') {
                    pod = destPort;
                }
            } else {
                pod = podLookupFromDest.lookupPodForAK(destPort);
            }

            LOGGER.warn(" In create Booking::::Derived POD:::" + pod);
            /*if(!("GUM".equals(pod) || "SHA".equals(pod) || "NGB".equals(pod)
                || "XMN".equals(pod) || "YTN".equals(pod) || "HKG".equals(pod))){ //A12
                return null;
            }*/
            def rtgPOD1 = RoutingPoint.findRoutingPoint(pod)
            LOGGER.warn(" In create Booking::::rtgPOD1:::" + rtgPOD1);
            def onItineary = isPodOnVesItineary(cv, rtgPOD1)
            def eqtype = unit.unitPrimaryUe.ueEquipment.eqEquipType

            if (unitId == null || facility == null || cv == null || freightKind == null || rtgPol == null || rtgPOD1 == null || !onItineary) {
                fail("ERR_GVY_LNK_012. Booking Didnt Exsit. System Tried to Create It but Routing Ports and Not on the Vessel visit: " + bookingNum);
                LOGGER.warn(" In create Booking:::: :::unitId == null || facility == null || cv == null || freightKind == null || rtgPol == null  || rtgPOD1 == null || !onItineary");
            }

            Booking booking = Booking.create(bookingNum, bizScope, cv, freightKind, rtgPol, rtgPOD1, rtgPOD1)
            EquipmentOrderItem eqboi = EquipmentOrderItem.findOrCreateOrderItem(booking, Long.valueOf(5L), eqtype);
            eqboi.updateQty(Long.valueOf(100L));

            booking.setEqoDestination(destPort);
            ScopedBizUnit bkgShipper = ScopedBizUnit.findScopedBizUnit(shipperId, BizRoleEnum.SHIPPER);
            ScopedBizUnit bkgConsignee = ScopedBizUnit.findScopedBizUnit(consigneeId, BizRoleEnum.SHIPPER);
            booking.setEqoShipper(bkgShipper);
            booking.setEqoConsignee(bkgConsignee);
            booking.setEqoOrigin(ContextHelper.getThreadFacility().getFcyId());

            return booking;

        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.warn(" In create Booking::::Got Exception:::" + e);
        }
    }

    public boolean isPodOnVesItineary(CarrierVisit cv, RoutingPoint rtgPOD1) {
        boolean isOnVesRouting = false;
        if (rtgPOD1 == null) {
            return isOnVesRouting
        }
        List itinaryPointsLst = cv.cvCvd.cvdItinerary.itinPoints
        for (port in itinaryPointsLst) {
            if (rtgPOD1.equals(port.callPoint)) {
                isOnVesRouting = true;
                break;
            }
        }
        return isOnVesRouting;
    }
}