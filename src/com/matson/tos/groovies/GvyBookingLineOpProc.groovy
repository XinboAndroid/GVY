/*
* Class to Handle Line operator update on Exisintg Booking In N4
* 08/16/11 2.1 Updated Email Method
*/
import com.navis.argo.ContextHelper;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.argo.business.atoms.BizRoleEnum;
import com.navis.argo.business.model.CarrierVisit;
import com.navis.orders.business.eqorders.Booking;
import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.business.reference.RoutingPoint;
import com.navis.inventory.business.api.UnitFinder;
import com.navis.framework.business.Roastery;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.inventory.business.units.Unit;

public class GvyBookingLineOpProc extends GroovyInjectionBase
{
    private String emailTo = '1aktosdevteam@matson.com';
    public String execute(Map inParameters) {
        try
        {
            ContextHelper.setThreadExternalUser("snx:ACETS");
            def bookingNum = (String) inParameters.get("bookingNum");
            def lineOperator = (String) inParameters.get("lineOperator");
            def obCarrierId = (String) inParameters.get("obCarrierId");

            def carrierMode = LocTypeEnum.getEnum("VESSEL");
            def bizScope = ScopedBizUnit.findScopedBizUnit( "MAT", BizRoleEnum.LINEOP);

            def obCarrierVisit = CarrierVisit.findCarrierVisit(ContextHelper.getThreadFacility(), carrierMode, obCarrierId);
            //def booking = Booking.findBookingByUniquenessCriteria( bookingNum, bizScope, obCarrierVisit);
            //println("bookingNum ========="+bookingNum);
            List bkglist = Booking.findBookingsByNbr(bookingNum);
            if(bkglist == null || bkglist.size() == 0){
                println("GvyBookingLineOpProc - No Booking Found");
                return;
            }

            Iterator bkgIt = bkglist.iterator();
            for(aBkg in bkgIt){
                def vesselVisit = aBkg.getFieldValue("eqoVesselVisit.cvId") != null ? aBkg.getFieldValue("eqoVesselVisit.cvId") : null ;
                def bkgLineOpt = aBkg.getFieldValue("eqoLine.bzuId") != null ? aBkg.getFieldValue("eqoLine.bzuId") : null;
                println("vesselVisit ="+vesselVisit+"   lineOperator="+bkgLineOpt)
                //Vessel matches and lineOperator dosent match
                if(vesselVisit.equals(obCarrierId) && !lineOperator.equals(bkgLineOpt)){
                    println("Process Booking--------------1="+aBkg+" obCarrierId="+obCarrierId+"lineOperator ="+lineOperator)
                    processBooking(aBkg,bkgLineOpt,lineOperator,obCarrierId);
                }//Outer IF Ends
            }//For Ends
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }//Method Execute Ends


    public void processBooking(Object booking,String bkgLineOpt,String lineOperator,String obCarrierId){
        try
        {
            ArrayList arrlist = new ArrayList();
            UnitFinder unitFinder = (UnitFinder)Roastery.getBean("unitFinder");
            Collection units = unitFinder.findUnitsAdvisedOrReceivedForOrder(booking);
            def bookingNbr = booking.getFieldValue("eqboNbr");
            if (units != null && units.size() > 0) {
                //Email Cannot Delete Booking As active units are linked on it
                Iterator it = units.iterator();
                for(aUnit in it) {
                    def unit = aUnit
                    arrlist.add(unit.unitId);
                }
                getGroovyClassInstance("EmailSender").custSendEmail(emailTo,"Export Booking Issue :"+bookingNbr, "DI TEAM -\r\n\r\n 1. Unassign units "+arrlist+" of Booking "+bookingNbr+" with lineOperator : "+bkgLineOpt+"\r\n\r\n 2. Reassign units "+arrlist+" to Booking "+bookingNbr+" with lineOperator : "+lineOperator+"\r\n\r\n 3. Please Delete Booking "+bookingNbr+" with lineOperator : "+bkgLineOpt+" as FACTS has sent TOS a LineOperator update on this booking")
            }else{
                ArgoUtils.carefulDelete(booking);
                getGroovyClassInstance("EmailSender").custSendEmail(emailTo,"Deleted Booking "+bookingNbr+" with LineOperator "+bkgLineOpt,"DI TEAM -\r\n\r\n 1. Deleted Booking "+bookingNbr+" with LineOperator : "+bkgLineOpt+"\r\n\r\n 2. Created Booking "+bookingNbr+" with LineOperator : "+lineOperator+" for Carrier : "+obCarrierId+"\r\n\r\n 3. As FACTS sent TOS a LineOperator update with "+lineOperator+" on this booking")
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }//ProcessBooking

}