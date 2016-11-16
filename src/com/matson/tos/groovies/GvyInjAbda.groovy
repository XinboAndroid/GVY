/*
**********************************************************************
* Srno   Date	             Changer	 Change Description
* A1     12/29/08       Glenn Raposo	 Unit Field Tracker code Added
* A1- 12/22/08 Change err 006 to use IB vessle on for imports
* A2- 01/21/08 Correct Consignee
* A3- 02/06/09          Steven Bauer	 Remove Commiddty Note.
* A4     04/09/09       Glenn Raposo     Adde check to set the BDB Dest to POD if they dont match
* A5     04/27/09	Steven Bauer	 Changed destination mapping to import = dest, export =disch.
* A6     05/13/09       Steven Bauer     Changed disch mapping to import = dest, export =disch.
* A7     05/26/09       Steven Bauer	 Lookup unit at complex level.
* A8     06/02/09	Steven Bauer	 403 - Supress all updates before GetNV
* A9	 07/21/09	Steven Bauer	 Changed POD
**********************************************************************
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.*
import com.navis.inventory.business.units.*
import com.navis.framework.util.BizViolation
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.services.business.event.GroovyEvent
import com.navis.orders.business.eqorders.EquipmentOrderManagerPea
import com.navis.orders.business.eqorders.Booking
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.ContextHelper;
import com.navis.argo.business.api.GroovyApi

class GvyInjAbda extends GroovyInjectionBase {
    public String execute(Map inParameters) {


        def ctrId = (String) inParameters.get("equipment-id");

        def recorder = (String) inParameters.get("recorder");
        def vesvoyBda = (String) inParameters.get( "vesvoy");
        def bdaBookingNum = (String) inParameters.get( "bookingNum");
        def consigneeIdBda = (String) inParameters.get( "consigneeId");
        def consigneeNameBda = (String) inParameters.get( "consigneeName");
        def shipperNameBda = (String) inParameters.get( "shipperName");
        def shipperIdBda = (String) inParameters.get( "shipperId");
        def destPortBda = (String) inParameters.get( "destPort");
        def discPortBda = (String) inParameters.get( "dischargePort");
        def primCarrierBda = (String) inParameters.get( "primCarrier");
        def tempBda = (String) inParameters.get( "temp");
        def sealBda = (String) inParameters.get( "sealNum");
        def commodityBda = (String) inParameters.get( "commodity");
        def note = "\n\nPlease fix either the booking or unit and review the routing after correction";
        log( "1- bdaBookingNum = " + bdaBookingNum);
        com.navis.argo.ContextHelper.setThreadExternalUser("ACETS");

        String  emailFrom = '1aktosdevteam@matson.com'
        String emailTo = "1aktosdevteam@matson.com";
        def unitFreightKind = null;
        def bkngFreightKind = null;
        def emailBody = null;

        def inj = new GroovyInjectionBase();
        def emailSender = inj.getGroovyClassInstance("EmailSender");

        // Find the UFV
        log( "Starting BDA Injection process.");
        def ufv;
        def unit;
        //A1- tracker
        def tracker;
        GroovyApi gvyApi = new GroovyApi();
        def unitLookup = gvyApi.getGroovyClassInstance("GvyUnitLookup");
        def podLookup = gvyApi.getGroovyClassInstance("GvyRefDataLookup");


        try {
            //ufv = findActiveUfv(ctrId);
            ufv = unitLookup.getUfvActiveInComplex(ctrId);
            if(ufv == null) {
                throw new Exception("Could not find active unit");
            }
            unit = ufv.getUfvUnit();
            //A1- Tracker Change
            def gvyUnitFldTracker = gvyApi.getGroovyClassInstance("GvyUnitFieldTracker");
            tracker = gvyUnitFldTracker.createFieldChangeTracker(unit)

            unitFreightKind = unit.getFieldValue("unitFreightKind");
            unitFreightKind = unitFreightKind.getKey();
        } catch ( Exception ex) {
            emailSender.custSendEmail(emailFrom,emailTo," BDA error for Booking "+ bdaBookingNum+ " and Unit " +ctrId ,"Could not find unit: "+ctrId+note);
            fail((new StringBuilder()).append(ex.toString()).append(" ERR_GVY_BDA_001. Could not find unit: ").append( ctrId).toString());
        }

        if(isStowplan(unit)) return;

        List bkng = Booking.findBookingsByNbr(bdaBookingNum);
        Iterator iter = bkng.iterator();
        while (iter.hasNext()){
            def book = iter.next();
            bkngFreightKind = book.eqoEqStatus;
            bkngFreightKind = bkngFreightKind.getKey()
        }

        StringBuffer header = new StringBuffer();
        header.append("\nBooking Number       : "+bdaBookingNum);
        header.append("\nContainer Number     : "+ctrId);
        header.append("\nBooking FreigthKind   : "+bkngFreightKind);
        header.append("\nContainer FreightKind : "+unitFreightKind);
        header.append("\n\n");

        emailBody = header.toString();
        log( "2- bdaBookingNum = " + bdaBookingNum);
        if( UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) &&
                isBarge( vesvoyBda) &&
                UnitVisitStateEnum.DEPARTED.equals(ufv.getUfvVisitState())) {
            emailSender.custSendEmail(emailFrom,emailTo," BDA error for Booking "+ bdaBookingNum+ " and Unit " +ctrId ,emailBody + "VesVoy: " + vesvoyBda + " sailed. Could not apply BDA."+note);
            fail( "ERR_GVY_BDA_002. VesVoy: " + vesvoyBda + " sailed. Could not apply BDA.");

        }


        def n4BlNum = unit.getFieldValue("unitGoods.gdsBlNbr");
        log( "3- n4BlNum = " + n4BlNum + " bdaBookingNum = " + bdaBookingNum);
        def bdaBookingNumCompare = null;

// FACTS message processing during NV
        if (n4BlNum!=null && n4BlNum.contains("DO NOT EDIT-NEWVES:"))
        {
            if( n4BlNum != null && n4BlNum.length() > 26 ) {
                n4BlNum = n4BlNum.substring(0,26);
            }
            bdaBookingNum = "DO NOT EDIT-NEWVES:"+bdaBookingNum;
            bdaBookingNumCompare = "DO NOT EDIT-NEWVES:"+bdaBookingNumCompare;

        } else {
            if( n4BlNum != null && n4BlNum.length() > 7 ) {
                n4BlNum = n4BlNum.substring(0,7);
            }
        }
// FACTS message processing during NV

        log( "n4BlNum = " + n4BlNum + " bdaBookingNum = " + bdaBookingNum);

        if ( n4BlNum != null && bdaBookingNumCompare!= null && !"DO NOT EDIT-NEWVES:".equalsIgnoreCase(n4BlNum) && !n4BlNum.equalsIgnoreCase(bdaBookingNumCompare)) {
            log( "if diff n4BlNum = " + n4BlNum + " bdaBookingNum = " + bdaBookingNumCompare);
            emailSender.custSendEmail(emailFrom,emailTo," BDA error for Booking "+ bdaBookingNum+ " and Unit " +ctrId ,emailBody + "Unit: " + ctrId + " Booking Num dismatch: N4 num=" + n4BlNum + "  BDA num=" + bdaBookingNum+note);
            fail( "ERR_GVY_BDA_003. Unit: " + ctrId + " Booking Num dismatch: N4 num=" + n4BlNum + "  BDA num=" + bdaBookingNum);
        }

        // if not export or import then return
        if ( !UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory()) && !UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory())) {
            log( "ERR_GVY_BDA_004. Unit: " + ctrId + " category is not EXPORT or IMPORT.");
            emailSender.custSendEmail(emailFrom,emailTo," BDA error for Booking "+ bdaBookingNum+ " and Unit " +ctrId ,emailBody + "Unit: " + ctrId + " category is not EXPORT or IMPORT."+note);
            fail( "ERR_GVY_BDA_004. Unit: " + ctrId + " category is not EXPORT or IMPORT.");
        }



        // if N4 vesvoy is not equal to dba.vesvoy then return
        def vesvoyN4 = null;
        if(UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()))  {
            vesvoyN4 = unit.getFieldValue( "unitActiveUfv.ufvActualIbCv.cvId");
        } else {
            vesvoyN4 = unit.getFieldValue( "unitActiveUfv.ufvIntendedObCv.cvId");
        }

        def cmdyId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
        boolean isUnitSIT = "SIT".equals(cmdyId) || "SAT".equals(cmdyId) ? true : false

        if ( !isUnitSIT && vesvoyN4 != vesvoyBda && !isBarge(vesvoyN4)) {
            emailSender.custSendEmail(emailFrom,emailTo," BDA error for Booking "+ bdaBookingNum+ " and Unit " +ctrId ,emailBody + "vesvoy mismatch for unit; " + ctrId + ". vesvoyN4=" + vesvoyN4 + " vesvoyBda=" + vesvoyBda+note);
            fail( "ERR_GVY_BDA_006. vesvoy mismatch for unit; " + ctrId + ". vesvoyN4=" + vesvoyN4 + " vesvoyBda=" + vesvoyBda);
        }

        def oldDestPort = unit.getFieldValue("unitGoods.gdsDestination");
        def oldConsinee = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName");
        def consigneeN4 = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName");

        def misc2 = unit.getFieldValue("unitFlexString11");

        if ( (n4BlNum == null || n4BlNum == "" || "DO NOT EDIT-NEWVES:".equals(n4BlNum)) && ( consigneeN4 != null && (consigneeN4.contains( "unapproved") ||	consigneeN4.contains( "invalid assign")))) {
            unit.setFieldValue("unitGoods.gdsBlNbr", bdaBookingNum);
        }

        def comments = "";
        if ( (misc2 == null || !misc2.contains( "C")) && consigneeN4 != consigneeNameBda) {
            // find BDA consignee in N4
            def cneeGrp = ScopedBizUnit.findScopedBizUnit( consigneeIdBda, BizRoleEnum.SHIPPER);
            if ( cneeGrp == null) {
                log( "Consignee" + consigneeNameBda + " not configured in N4.");
            } else {
                unit.getUnitGoods().updateConsignee( cneeGrp);
                log( "update consignee with BDA.");
                comments += "RECON X " + consigneeN4;
            }
        }

        log("misc2="+misc2+" oldDestPort="+oldDestPort+" destPortBda="+destPortBda);
        // Set dest
        // if export, disch=disch else disch=dest.
        String dest = destPortBda;
        // todo, not inbound, if inbound, get from booking?
        if(UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory() ) ) {
            if(oldDestPort != destPortBda) {
                unit.setFieldValue("unitGoods.gdsDestination", dest);
                comments += " " + oldDestPort + " " +  dest;
            }

            def dischargePortN4 = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
            UfvTransitStateEnum transitState = ufv.getUfvTransitState();
            println( "dischargePortN4 = " + dischargePortN4);
            try{
                if ( discPortBda != dischargePortN4 ) {
                    def podRtgBda = RoutingPoint.findRoutingPoint( discPortBda)
                    if(podRtgBda == null){
                        emailSender.custSendEmail(emailFrom,emailTo," BDA error for Booking "+ bdaBookingNum+ " and Unit " +ctrId ,emailBody + "Destination Port not a Routing Point. POD lookup NULL: " + discPortBda+note);
                        fail( "ERR_GVY_BDA_007.6. Destination Port not a Routing Point. POD lookup NULL: " + discPortBda);
                    }
                    unit.getUnitRouting().setRtgPOD1(podRtgBda);
                    def gvyInjAbdb = getGroovyClassInstance("GvyInjAbdb");
                    gvyInjAbdb.setOBCarrierOnPODChng(ctrId, dischargePortN4, discPortBda);
                }
            }catch(Exception e){
                e.printStackTrace()
            }
        } else {
            if ( misc2 == null || !misc2.contains( "P")) {
                log("Change "+destPortBda);
                if(oldDestPort != destPortBda) {
                    unit.setFieldValue("unitGoods.gdsDestination", dest);
                    comments += " " + oldDestPort + " " +  dest;
                }

                //Added Code to Set
                def dischargePortN4 = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
                UfvTransitStateEnum transitState = ufv.getUfvTransitState();
                println( "dischargePortN4 = " + dischargePortN4);
                try{
                    def pod = podLookup.lookupPod(dest);
                    if ( pod != dischargePortN4  ) {
                        println("Sstting the POD to BDA DestPort"+"dischargePortN4 = " + dischargePortN4+" destPortBda :"+pod)
                        def podRgtPoint = RoutingPoint.findRoutingPoint(pod);
                        if(podRgtPoint == null){
                            emailSender.custSendEmail(emailFrom,emailTo," BDA error for Booking "+ bdaBookingNum+ " and Unit " +ctrId ,emailBody + "POD Port not a Routing Point. POD lookup NULL: " + dest+note);
                            fail( "ERR_GVY_BDA_007.5. POD Port not a Routing Point. POD lookup NULL: " + dest);
                        }
                        unit.getUnitRouting().setRtgPOD1(podRgtPoint);
                        def gvyInjAbdb = getGroovyClassInstance("GvyInjAbdb");
                        gvyInjAbdb.setOBCarrierOnPODChng(ctrId, dischargePortN4, pod);
                    }
                }catch(Exception e){
                    e.printStackTrace()


                }
            }

            if ( tempBda.trim().length() !=0)
                unit.setFieldValue("unitGoods.gdsReeferRqmnts.rfreqTempRequiredC", new Double( tempBda));

            unit.setFieldValue("unitSealNbr1", sealBda);
            def shipper = Shipper.findOrCreateShipper( shipperIdBda, shipperNameBda);
            unit.getUnitGoods().updateShipper( shipper);

            // A2 removed this setting
            //def conee = Shipper.findOrCreateShipper( consigneeIdBda, consigneeNameBda);
            //unit.getUnitGoods().updateConsignee( conee);

            //def cmdy = Commodity.findCommodity( "commodityBda");
            //if ( cmdy == null)
            //	fail( "ERR_GVY_BDA_006. Commodity from BDA: " + commodityBda + " could not be found in N4.");

            //unit.setFieldValue("unitGoods.gdsCommodity", cmdy);

            //unit.setFieldValue("unitRemark", unit.getFieldValue("unitRemark") + " " + comments + " " + commodityBda);
            unit.setFieldValue("unitRemark", unit.getFieldValue("unitRemark") + " " + comments);


        }

        //A1 - Tracker Change
        def changes = tracker.getChanges(unit);
        if(changes != null && changes.getFieldChangeCount() != 0) {
            unit.recordUnitEvent(com.navis.argo.business.atoms.EventEnum.UNIT_PROPERTY_UPDATE,changes , "Field Updated BDA Data");
        }

        def event = new GroovyEvent( null, unit);
        event.postNewEvent( "BDA", "ACETS: " + recorder);
    }

    private boolean isBarge( String vesvoy) {
        //def gvyBaseClass = new GroovyInjectionBase()
        def gvyUtil = getGroovyClassInstance("GvyCmisUtil");
        def vType = gvyUtil.getVesselClassType( vesvoy);
        return vType == "BARGE" ? true : false;
    }

    private boolean isStowplan(unit) {
        def remark = unit.unitRemark;
        if(remark == null) return false;
        return remark.startsWith("Stowplan Data");
    }
}