/*
* Srno  Doer Date      Change
* A1    GR   07/08/10  Consignee Lookup for NonBuiltInEvent
* A2    GR   12/27/11  YB ASSIGN CHANGE
* A3    KR   07/09/15  Alaska Ports
*/

import com.navis.argo.business.atoms.CarrierModeEnum
import com.navis.argo.business.model.CarrierVisit;
import com.navis.argo.business.reference.CarrierService;
import com.navis.argo.business.reference.RoutingPoint;
import com.navis.argo.business.reference.LineOperator;
import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.ContextHelper;
import com.navis.argo.business.model.Facility;

public class GvyCmisEventUnitCargoEdit
{
    def prevAvailDt;
    def prevDetentionDt;
    def unit;
    boolean processAcetsMsg;
    def gvyCargoEdit;
    def previousDischPort;
    def gvyBaseClass;
    def exportXml;
    def gvyCmisUtil = null

    public void processUnitPropertyUpdate(Object event,Object api, Object gvyCargoEditObj)
    {
        gvyCargoEdit = gvyCargoEditObj;
        gvyBaseClass = api;
        unit = event.getEntity()
        boolean reportProcessing = false,update = false, blockUpdtForIngate = false,procCmisFeed = false

        def gvyEventUtil = api.getGroovyClassInstance("GvyEventUtil")
        gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil")
        def dischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
        try
        {
            def isPodUpdated = gvyCargoEdit.isPodUpdated()
            if(isPodUpdated){
                previousDischPort = gvyCargoEdit.getPreviousPod()
                println("previousDischPort ::"+previousDischPort+"  dischPort ::"+dischPort);
                //Set OB carrier for GUM Units
                /*if('GUM'.equals(dischPort)){
                    resolveReroute(unit,api);
                }*/
                //Set OB carrier for NIS=BARGE for HON=GEN_TRUCK
                setOBCarrierOnPODChng(unit,gvyEventUtil,gvyCmisUtil,event)
            }

            previousDischPort = previousDischPort != null ? previousDischPort : dischPort
            //1-Cargo Status Report Processing
            reportProcessing = processCargoStatReport(event,gvyEventUtil,api,previousDischPort)
            //2-Get Previous Avail and Detntion Dt
            update = setAvailDetnDate(event,api, previousDischPort)
            //3-Create Cmis Feed for Msg from Acets & UI Transaction
            procCmisFeed = processCmisFeed(gvyEventUtil,api,update,event)
        }catch(Exception e){
            e.printStackTrace();
        }
        println("AcetsMsg:"+processAcetsMsg+"   BlockIngate:"+blockUpdtForIngate+"   Report:"+reportProcessing+"     AvailDate:"+update+"   ProcCmisFeed:"+procCmisFeed)
    }//Method processUnitPropertyUpdate Ends


    //1-Cargo Status Report Processing
    public boolean processCargoStatReport(Object event,Object gvyEventUtil,Object api, String previousDischPort)
    {
        boolean reportProcessing = false;
        try
        {
            reportProcessing = gvyEventUtil.verfiyReportSnxProcessing(event)
            println("reportProcessing ::"+reportProcessing)
            if(reportProcessing)
            {
                def cargoStatusGvy = api.getGroovyClassInstance( "GvyUnitCargoStatus");
                def ret = cargoStatusGvy.sendXml( "CARGO_STATUS", event, previousDischPort);
                api.sendXml(ret)
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return reportProcessing
    }//Method processCargoStatReport Ends

    //2-Get Previous Avail and Detntion Dt
    public boolean setAvailDetnDate(Object event,Object api, String previousDischPort)
    {
        boolean update = false;
        try
        {
            unit = event.getEntity()
            //3- Set Avail Date
            def availLookup = api.getGroovyClassInstance("GvyCargoEditAvailDate");
            update =   availLookup.setAvailDate(unit, event, previousDischPort);
        }catch(Exception e){
            e.printStackTrace()
        }
        return update
    }// Method setAvailDetnDate Ends


    public boolean processCmisFeed(Object gvyEventUtil,Object api, boolean update,Object event)
    {
        boolean processCmisFeed = false
        def gvyEventObj = event.getEvent()
        String eventType =  gvyEventObj.getEventTypeId()
        try
        {
            //4-Create Cmis Feed for Msg from Acets & UI Transaction
            processCmisFeed  = gvyEventUtil.verfiyCmisFeedProcessing(event)
            def detentionDateChng = gvyEventUtil.wasFieldChanged(event,'ufvFlexDate03')
            def gvyUnitUtil = api.getGroovyClassInstance("GvyUnitUtility")
            boolean consigneeChng = gvyUnitUtil.isFieldChngForNonBuildInEvents(event,api,'gdsConsigneeBzu',gvyEventUtil)
            consigneeChng = consigneeChng ? consigneeChng : gvyEventUtil.wasFieldChanged(event, 'gdsConsigneeAsString') //A1

            //Print Status Checks
            if(processCmisFeed)
            {
                //1. N4 TO CMIS data processing
                def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
                def unitDtl = unitDetails.doIt(event)
                if('SIT_ASSIGN'.equals(eventType)){
                    def trckCmpy = unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
                    if(trckCmpy == null){
                        unitDtl= gvyCmisUtil.eventSpecificFieldValue(unitDtl,"truck=","null")
                    }else{
                        unitDtl= gvyCmisUtil.eventSpecificFieldValue(unitDtl,"truck=",trckCmpy)
                    }
                }
                setExportXml(unitDtl)

                //2. Detention Msg Check
                boolean detnMsg = false
                if(update || detentionDateChng || consigneeChng)
                {
                    def gvyDentObj = api.getGroovyClassInstance("GvyCmisCargoEditDetention");
                    detnMsg = gvyDentObj.detentionProcess(unitDtl,event,api,previousDischPort)
                }

                def unitUpdateXml =  gvyCargoEdit.unitUpdateProcess(unitDtl,event,api,detnMsg,previousDischPort)

            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return processCmisFeed
    }//Method processCmisFeed ends



    /*public void resolveReroute(Object unit, Object api)
    {
       try
      {
        def aobcarrierMode=unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCarrierMode")
            aobcarrierMode = aobcarrierMode != null ? aobcarrierMode.getKey() : ''

       //String inVesselVisitId,String pod,String inLineId,String freightKind,String liveReefer,String oog, String hazard,Object unit
        def pod = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
        def podRgtPoint = RoutingPoint.findRoutingPoint(pod);

        LineOperator lineOperator = LineOperator.findLineOperatorById('MAT')
        CarrierService carrierService = CarrierService.findCarrierService('GCS')

        def freightkindenum = unit.getFieldValue("unitFreightKind")

        Boolean isLiveReefer = unit.isReefer();
        Boolean isOog = unit.getFieldValue("unitIsOog");
        Boolean isHaz = unit.getFieldValue("unitGoods.gdsIsHazardous")



        println("LineOperator ::"+lineOperator+"  aobcarrierMode :"+aobcarrierMode+" podRgtPoint::"+podRgtPoint+"  freightkindenum:"+freightkindenum)
        //println("isLiveReefer ::"+isLiveReefer+" isOog:"+isOog+" isHaz:"+isHaz)

        if(!'VESSEL'.equals(aobcarrierMode))
       {
         def GvyVesVisit = api.getGroovyClassInstance("GvyFindVesselVisit")
         def facility = ContextHelper.getThreadFacility()
         CarrierVisit resolvedCarrierVisit = GvyVesVisit.vesselVisitFinderService(podRgtPoint,lineOperator,isHaz,isLiveReefer,carrierService)
          if (resolvedCarrierVisit != null) {
             unit.getUfvForFacilityNewest(facility).setUfvIntendedObCv(resolvedCarrierVisit);
             unit.getUfvForFacilityNewest(facility).setUfvActualObCv(resolvedCarrierVisit);
             unit.getUnitRouting().setRtgDeclaredCv(resolvedCarrierVisit)
          }
       }//If ends
       else if('VESSEL'.equals(aobcarrierMode))
      {
          def aobcarrierId=unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")
          def facility = ContextHelper.getThreadFacility()
          CarrierVisit carriervisit = CarrierVisit.findVesselVisit(facility, aobcarrierId);
          def cvPhase = carriervisit.getCvVisitPhase()
          def carrierId = carriervisit.getCvId()
          if( carrierId.equals('BARGE') || CarrierVisitPhaseEnum.COMPLETE.equals(cvPhase) || CarrierVisitPhaseEnum.DEPARTED.equals(cvPhase) ||   CarrierVisitPhaseEnum.CLOSED.equals(cvPhase) ||  CarrierVisitPhaseEnum.CANCELED.equals(cvPhase))
         {
            def GvyVesVisit = api.getGroovyClassInstance("GvyFindVesselVisit")
            CarrierVisit resolvedCarrierVisit = GvyVesVisit.vesselVisitFinderService(podRgtPoint,lineOperator,isHaz,isLiveReefer,carrierService)
            if (resolvedCarrierVisit != null) {
              unit.getUfvForFacilityNewest(facility).setUfvIntendedObCv(resolvedCarrierVisit);
              unit.getUfvForFacilityNewest(facility).setUfvActualObCv(resolvedCarrierVisit);
              unit.getUnitRouting().setRtgDeclaredCv(resolvedCarrierVisit)
            }
          }
       }//else if Ends

      }catch(Exception e){
          e.printStackTrace()
      }
     }*///Method Ends


    /*
    1] Set OBCarreir as GEN_VESSEL if POD is chnaged to NIS
    2] Set OBCarreir as GEN_TRUCK if POD is chnaged to HON
    3] Set only if POD changed and unit is not Departed
  */
    public void setOBCarrierOnPODChng(Object unit, Object gvyEventUtil,Object  gvyCmisUtil,Object event)
    {
        try
        {
            def updtdischPort = gvyEventUtil.wasFieldChanged(event,'gdsDestination')
            def transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
            transitState = transitState != null ? transitState.getKey() : ''

            if(!updtdischPort){
                return;
            }else if (updtdischPort && transitState.equals("S70_DEPARTED")){
                return;
            }

            def visit = ""
            def complex = com.navis.argo.ContextHelper.getThreadComplex();

            def prevDischPort =  gvyEventUtil.getPreviousPropertyAsString(event, "rtgPOD1");
            def gvyDomQueryObj = gvyBaseClass.getGroovyClassInstance("GvyCmisDomainQueryUtil")
            prevDischPort = gvyDomQueryObj.lookupRtgPOD(prevDischPort)
            prevDischPort = prevDischPort != null ? prevDischPort : ""
            prevDischPort = previousDischPort != null ? previousDischPort : prevDischPort

            def curDischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            curDischPort = curDischPort != null ? curDischPort : ""

            def intdObCarrierId = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
            intdObCarrierId = intdObCarrierId != null ? intdObCarrierId : ""

            boolean ObcarrierFlag = intdObCarrierId.equals("GEN_TRUCK") || intdObCarrierId.equals("GEN_VESSEL") || intdObCarrierId.equals("GEN_CARRIER") ? true : false

            boolean ObcarrierFlagHon = intdObCarrierId.equals("BARGE") || intdObCarrierId.equals("GEN_VESSEL") || intdObCarrierId.equals("GEN_CARRIER") ? true : false

            if(curDischPort.equals(com.navis.argo.ContextHelper.getThreadFacility().getFcyId()) &&
                    !prevDischPort.equals(com.navis.argo.ContextHelper.getThreadFacility().getFcyId()) && ObcarrierFlag){
                //visit = com.navis.argo.business.model.CarrierVisit.getGenericVesselVisit(complex);
                //SET IT TO BARGE
                visit = com.navis.argo.business.model.CarrierVisit.findOrCreateVesselVisit(com.navis.argo.ContextHelper.getThreadFacility(), "BARGE")
                unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).setUfvIntendedObCv(visit);
                unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).setUfvActualObCv(visit);

            }else if(curDischPort.equals(com.navis.argo.ContextHelper.getThreadFacility().getFcyId()) &&
                    !prevDischPort.equals(com.navis.argo.ContextHelper.getThreadFacility().getFcyId()) && ObcarrierFlagHon){
                visit = com.navis.argo.business.model.CarrierVisit.getGenericTruckVisit(complex);
                unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).updateObCv(visit);
            }
        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method Ends

    public void setExportXml(String xml){
        exportXml = xml
    }

    public String getExportXml(){
        return exportXml
    }

}// Class Ends