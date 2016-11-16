import com.navis.argo.business.atoms.FreightKindEnum;
import com.navis.argo.business.reference.RoutingPoint;
import com.navis.argo.business.model.CarrierVisit;
import com.navis.framework.business.Roastery;
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum;
import com.navis.argo.business.reference.LineOperator;
import com.navis.argo.ContextHelper;
import com.navis.argo.business.model.Facility;
import com.navis.argo.business.reference.*;
import com.navis.argo.business.reference.CarrierService;
import com.navis.argo.business.api.VesselVisitFinder;
import com.navis.argo.business.api.GroovyApi;
import com.navis.road.business.workflow.TransactionAndVisitHolder;
import com.navis.road.business.model.TruckTransaction;
import com.navis.inventory.business.units.Unit;


public class GvyFindVesselVisit{

    //Vessel Visit Finder
    public CarrierVisit vesselVisitFinder(RoutingPoint inFinalDischargePoint,LineOperator inLineOperator,boolean inIsHazardous,boolean inIsReefer)
    {
        CarrierVisit cv = null;
        try{
            VesselVisitFinder vvf = (VesselVisitFinder)Roastery.getBean("vesselVisitFinder");
            cv = vvf.findVesselVisitByLineAndFinalPod(inFinalDischargePoint, inLineOperator, inIsHazardous, inIsReefer);
        }catch(Exception e){
            e.printStackTrace();
        }
        return cv;
    }


    //Find carrier by Service
    public CarrierVisit vesselVisitFinderService(RoutingPoint inFinalDischargePoint,LineOperator inLineOperator,Boolean inIsHazardous,Boolean inIsReefer, CarrierService srvc)
    {
        CarrierVisit cv = null;
        try{
            def isHaz = inIsHazardous != null ? inIsHazardous.booleanValue() : false;
            def isReefer =  inIsReefer != null ?  inIsReefer.booleanValue() : false;

            VesselVisitFinder vvf = (VesselVisitFinder)Roastery.getBean("vesselVisitFinder");
            cv = vvf.findVesselVisitByLineFinalPodAndService(inFinalDischargePoint, inLineOperator, srvc, isHaz, isReefer);
        }catch(Exception e){
            e.printStackTrace();
        }
        return cv;
    }

    //Fix for Auto-roll vessel visit
    public void setNextVesselVisitToTran (TransactionAndVisitHolder inDao, GroovyApi api) {
        VesselVisitFinder vvf = (VesselVisitFinder) Roastery.getBean(VesselVisitFinder.BEAN_ID);
        TruckTransaction tran = inDao.getTran();
        Unit unit = tran.getTranUnit();
        CarrierVisit cv = tran.getCarrierVisit();
        if (cv != null && (CarrierVisitPhaseEnum.COMPLETE.equals(cv.getCvVisitPhase()) || CarrierVisitPhaseEnum.CLOSED.equals(cv.getCvVisitPhase()))
                || CarrierVisitPhaseEnum.CANCELED.equals(cv.getCvVisitPhase())) {
            CarrierVisit cvNew = vvf.findVesselVisitByLineFinalPodAndService(tran.getTranDischargePoint1()  , tran.getTranLine() ,
                    cv.getCvCvd().getCvdService(), tran.isHazardous(), tran.isLiveReefer());
            if (cvNew != null ) {
                tran.setTranCarrierVisit(cvNew);
                api.logWarn("Updated the latest carrier visit to the transaction");
            }

        }
    }


    /*
    * Run Verficiations checks before Resolving the route
    */
    public boolean InvalidTranforRouteResolving(){
        try{
        }catch(Exception e){
            e.printStackTrace();
        }
        return true
    }

}