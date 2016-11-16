/** Change History
 *   A1:  SKB   04/09/2009  Set visible in sparcs to true
 *   A2:  GR    04/14/09  Added Code to Set BO carrier as BARGE
 *   A3:  GR    07/15/2009  Added departYBTransferUnit()
 *   A4   GR    11/17/09   Check before setting Barge
 *   A5   GR    05/27/10   Roll over the Avail and Detention Dates
 To the new Create UFV
 *   A6   GR    12/08/11   Set Designated Trucker for YB (SIT)
 *   A7   GR    12/08/11   Updated Function to Add Trucking Company(SIT)
 *   A8   GR    12/08/11   Removing SetDesigneted Trucker from Outgate

 */
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import  com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.argo.business.api.GroovyApi;
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.ContextHelper
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.business.api.RectifyParms;
import java.util.Iterator
import com.navis.argo.business.model.CarrierVisit;
import com.navis.argo.ContextHelper;
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.model.TruckingCompany

public class GvyCmisEventUnitOutGate
{
    public void processOutGate(Object event, Object api)
    {
        def unit = event.getEntity()
        def gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil")
        try
        {
            //Roll Over Avail and Detention Dates to the new Created UFV
            def availDate = unit.getFieldValue("unitActiveUfv.ufvFlexDate02")
            def detentionDate = unit.getFieldValue("unitActiveUfv.ufvFlexDate03")
            def lastfreeDay = unit.getFieldValue("unitActiveUfv.ufvCalculatedLastFreeDay")

            def groupCode = unit.getFieldValue("unitRouting.rtgGroup.grpId")
            groupCode = groupCode != null ? groupCode : ''

            def _drayStatus=unit.getFieldValue("unitDrayStatus")
            def drayStatus = _drayStatus!= null ? _drayStatus.getKey() : _drayStatus

            if(drayStatus.equals('OFFSITE') || drayStatus.equals('DRAYIN') || drayStatus.equals('TRANSFER'))
            {
                def unitId=unit.getFieldValue("unitGkey")
                def unitDetails = api.getGroovyClassInstance("GvyUnitLookup")
                def visit = unitDetails.lookupFacility(unitId)

                if(visit != null)
                {
                    // A1, Set Visible in sparcs true
                    visit.setFieldValue("ufvVisibleInSparcs", true);
                    visit.setFieldValue("ufvTransitState", com.navis.inventory.business.atoms.UfvTransitStateEnum.S20_INBOUND)
                    visit.setFieldValue("ufvVisitState", com.navis.inventory.business.atoms.UnitVisitStateEnum.ACTIVE)

                    visit.setFieldValue("ufvFlexDate02",availDate); //Roll over the Avail,Dtn,Storage Dates
                    visit.setFieldValue("ufvFlexDate03",detentionDate);

                    //visit.setFieldValue("ufvLastFreeDay",lastfreeDay);
                    if(lastfreeDay != null && !lastfreeDay.contains('no')){
                        lastfreeDay = lastfreeDay.replace('!','')
                        def formatter = new java.text.SimpleDateFormat("yyyy-MMM-dd");
                        def lstFreedate = (Date)formatter.parse(lastfreeDay);
                        def longlastFreeDay = lstFreedate.getTime() + (long)1 *(long)(24*3600*1000)
                        Date alteredlastFreeDay = new Date(longlastFreeDay)
                        //Plus 1 Day
                        visit.setUfvLastFreeDay(alteredlastFreeDay)
                    }

                    unit.setFieldValue("unitActiveUfv", visit)
                    //Setting OB Carrier to Barge
                    def curDischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
                    curDischPort = curDischPort != null ? curDischPort : ""
                    def intdObCarrierId = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
                    intdObCarrierId = intdObCarrierId != null ? intdObCarrierId : ""
                    boolean ObcarrierFlag = intdObCarrierId.equals("GEN_TRUCK") || intdObCarrierId.equals("GEN_VESSEL") || intdObCarrierId.equals("GEN_CARRIER") ? true : false
                    if(gvyCmisUtil.isNISPort(curDischPort) && ObcarrierFlag ){
                        def bargeVisit = CarrierVisit.findOrCreateVesselVisit(com.navis.argo.ContextHelper.getThreadFacility(), "BARGE")
                        unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).updateObCv(bargeVisit);
                    }
                }
                else
                {
                    println("Inbound lookup failed!!!!!!!!!!!!!!!!!!!!!!!")
                }
            } //Dray status Filter Ends
        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method doIt Ends

    //Depart Unit With Group[YB], Dray[Transfer] and POD [MOL,LNI]
    public void departYBTransferUnit(Object event)
    {
        try{
            def unit = event.getEntity()
            def group=unit.getFieldValue("unitRouting.rtgGroup.grpId")
            group = group != null ? group : ''

            def drayStatus=unit.getFieldValue("unitDrayStatus")
            drayStatus = drayStatus!= null ? drayStatus.getKey() : ''

            def pod = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            pod = pod != null ? pod.trim() : ''

            def ufvUnit = unit.getUnitActiveUfv()

            if(group.equals('YB') && drayStatus.equals('TRANSFER') && (pod.equals('MOL') || pod.equals('LNI'))){
                RectifyParms rparms = new RectifyParms();
                rparms.setUfvTransitState(UfvTransitStateEnum.S70_DEPARTED);
                rparms.setUnitVisitState(UnitVisitStateEnum.DEPARTED)
                ufvUnit.rectify(rparms);
                ufvUnit.setFieldValue("ufvVisibleInSparcs", false);
            }

        }catch(Exception e){
            e.printStackTrace()
        }
    }


    //Set Ufv OB Carrier to YB With Group[YB], Dray[Transfer]
    public void setYbForSitUnit(Object event)
    {
        try
        {
            def unit = event.getEntity()
            def group=unit.getFieldValue("unitRouting.rtgGroup.grpId")
            group = group != null ? group : ''

            def drayStatus=unit.getFieldValue("unitDrayStatus")
            drayStatus = drayStatus!= null ? drayStatus.getKey() : ''

            def ufvSet = null;
            if(group.equals('YB') && (drayStatus.equals('TRANSFER') || drayStatus.equals('OFFSITE')) )
            {
                //If intdObCarrierId yb OR ufv size is one then return
                def intdObCarrierId=unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
                ufvSet = unit.getUnitUfvSet()
                if( (intdObCarrierId != null && intdObCarrierId.startsWith('YB'))
                        || (ufvSet == null || ufvSet.size() == 1) ){
                    return;
                }

                def aUfv = null;
                Iterator iterator = ufvSet.iterator();
                while(iterator.hasNext())
                {
                    aUfv = (UnitFacilityVisit)iterator.next()
                    def intdObCarrId = aUfv.ufvIntendedObCv.cvId
                    if(intdObCarrId != null && intdObCarrId.startsWith('YB')){
                        setObCarreir(unit,intdObCarrId)
                        break;
                    }
                }//for ends
            }//outer if ends

        }catch(Exception e){
            e.printStackTrace();
        }
    }//method ends


    public void setObCarreir(Object unit,String aobcarrierId)
    {
        try{
            def facility = ContextHelper.getThreadFacility();
            CarrierVisit carriervisit = CarrierVisit.findVesselVisit(facility, aobcarrierId);
            unit.getUfvForFacilityNewest(facility).setUfvIntendedObCv(carriervisit);
            unit.getUfvForFacilityNewest(facility).setUfvActualObCv(carriervisit);
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    //A7 - Setting Designated Trucker for YB
    public void setYbAssignTrucker(Object unit,Object gvyEventUtil, Object event){
        try{
            def truck = unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
            def ybTrucker = unit.setFieldValue("unitFlexString14", truck);

            if("SIT".equals(unit.getFieldValue("unitGoods.gdsCommodity.cmdyId"))){

                def prevTruck = gvyEventUtil.getPreviousPropertyAsString(event,"rtgTruckingCompany")
                if(prevTruck != null){
                    def trkc = TruckingCompany.load(prevTruck);
                    if(trkc != null) {  unit.getUnitRouting().setRtgTruckingCompany(trkc); }
                }else{
                    unit.getUnitRouting().setRtgTruckingCompany(null);
                }
            }//Sit Check ends
        }catch(Exception e){
            e.printStackTrace();
        }
    }//Method Ends

}//Class Ends
