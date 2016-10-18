package com.matson.tos.groovies

/*
**************************************************************************************
* Srno   Date	        Changer	         Change Description
* A1     01/13/08       Glenn Raposo	 Pulled out the Storage change for xps:CHNG_REROUTE
* A2     01/28/10       GR               Handeled Null Exception in Creating Groovy Class
* A3     08/19/10       GR               Acets detentionDateChng Added
* A4     04/15/11       GR               Adding the Service message Check For UNIT_REROUTE EVENT
* A5     06/15/11       GR               Post LTV for Routing Done on Emptys after Load to pass leg correctly
* A6     02/27/14       RI		 	     Added method to capture Integration error
*****************************************************************************************
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper

public class GvyCmisEventUnitReroute {
    def gvyBaseClass;
    def ret = null;
    def eventId = null;

    public void processUnitReroute(Object unit, Object event, Object api) {
        try
        {
            gvyBaseClass = api;
            def gvyEventUtil = api.getGroovyClassInstance("GvyEventUtil")
            def gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil")

            postLTVForRtgEmpty(unit, event, gvyEventUtil) //A5
            setOBCarrierOnPODChng(unit,gvyEventUtil,gvyCmisUtil,event)
            updateDestinationAfterPodReroute(unit, event, api);
            imprtToExportFlipRemoveBookingNum(event, api, unit, gvyEventUtil);
        }catch(Exception e){
            e.printStackTrace();
            println("Error :::::::::::::::::::::::::::"+e)
        }

    } // processUnitReroute End

    /**
     *  Cargo Status
     *  changing export to import we want to
     *  set (export) booking number to null for
     *  Unit_reroute event type
     */
    private void imprtToExportFlipRemoveBookingNum(Object event, Object api, Object unit,Object gvyEventUtil) {

        try
        {
            /* Set Avail Date */
            def availLookup = api.getGroovyClassInstance("GvyAvailDate");
            boolean update =   availLookup.setAvailDate(unit, event);

            //Filter for Category=Import
            def  reportProcessing = gvyEventUtil.verfiyReportSnxProcessing(event)
            if(reportProcessing)
            {
                try{
                    def mtyFructoseGvy = api.getGroovyClassInstance("GvyUnitCargoStatus");
                    ret = mtyFructoseGvy.sendXml("CARGO_STATUS", event);
                    api.sendXml(ret)
                }catch (Exception e){
                    String error = e;
                    //A6
                    if (error.contains("JMS") && ret != null){
                        println("Calling MatGetIntegrationError.createIntegrationError");

                        String entity = "Unit";
                        def unitId = unit.getUnitId();
                        def errDesc = "CARGO_STATUS Failed for "+unitId;
                        eventId = event.event.eventTypeId;
                        def inj = new GroovyInjectionBase();
                        inj.getGroovyClassInstance("MatGetIntegrationError").createIntegrationError(error,entity,unitId,eventId,errDesc,ret);
                    }
                }

            }

            /*
             * 1]  N4 TO CMIS data processing
             * 2]  Check for Msg from Acets i) Dont Create Cmis Feed
             * 3]  XPS:CHNG_RTNG - i) Set POD to DestPort   ii) Dont Create Cmis Feed
             */
            boolean processCmisFeed  = gvyEventUtil.verfiyCmisFeedProcessing(event)


            //XPS:CHNG_RTNG -  ii)  Dont Create Cmis Feed Filter
            def xpsChngRtng  = gvyEventUtil.nonProcessingXpsAction(event,'CHNG_RTNG')

            //Acets Message Filter
            boolean processAcetsMsg = gvyEventUtil.acetsMesssageFilter(event)
            def detentionDateChng =  gvyEventUtil.wasFieldChanged(event,'ufvFlexDate03')
            //added for Detention to get the DTD/DTAs on the unit_reroute
            def updtdischPort =  gvyEventUtil.wasFieldChanged(event,'rtgPOD1');
            if(processCmisFeed && xpsChngRtng && !processAcetsMsg)
            {
                def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
                def unitDtlXml = unitDetails.doIt(event)

                //SRV MSG - A4
                def gvySrvObj = api.getGroovyClassInstance("GvyCmisSrvMsgProcessor");
                gvySrvObj.processServiceMessage(unitDtlXml,event,api)


                //Detention Msg Check
                boolean detnMsg = false
                if(update || detentionDateChng || updtdischPort){
                    def gvyDentObj = api.getGroovyClassInstance("GvyCmisDetentionMsgProcess");
                    detnMsg = gvyDentObj.detentionProcess(unitDtlXml,event,api)
                }

                def rerouteEvntFeed = api.getGroovyClassInstance("GvyCmisEventFeedUnitReroute")
                rerouteEvntFeed.processUnitRerouteCmisFeed(unitDtlXml, api, event, unit, null, detnMsg )
            }
            else if ((update|| detentionDateChng || updtdischPort) && processAcetsMsg) //A3
            {
                // N4 TO CMIS data processing
                def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
                def unitDtl = unitDetails.doIt(event)

                //Detention Msg Check
                if(update || detentionDateChng){
                    def gvyDentObj = api.getGroovyClassInstance("GvyCmisDetentionMsgProcess");
                    boolean detnMsg = gvyDentObj.detentionProcess(unitDtl,event,api)
                }
            }
            println("Avail Date:"+update+"   processCmisFeed:"+processCmisFeed+" xpsChngRtng:"+xpsChngRtng+" processAcetsMsg:"+processAcetsMsg+"  detentionDateChng:"+detentionDateChng  )
        } catch(Exception e) {
            e.printStackTrace();
        }

    } // imprtToExportFlipRemoveBookingNum end

    /**
     * This method is used on a unit_reroute event.
     * If initiated by SPARCS, then the destination must
     * be updated to match the POD just changed by SPARCS.
     */
    private void updateDestinationAfterPodReroute(Object unit, Object event, Object api) {
        /* Pre-Conditions:
         *  1. Event created by XPS
         *  2. Event created by XPS for "CHNG_RTNG" (change routing)
         *  3. Category is storage
         *  4. Freight-kind is empty
         *  5. Destination is optional
         *  6. POD was changed
         */
        try{
            def recorder = event.getEvent().getFieldValue("evntAppliedBy");

            if (recorder.indexOf("xps") >= 0
                    && recorder.indexOf("CHNG_RTNG") >= 0
                    && unit.getFieldValue("unitFreightKind") == com.navis.argo.business.atoms.FreightKindEnum.MTY
                    //  && unit.getFieldValue("unitGoods.gdsDestination").equals("OPT")
                    && event.wasFieldChanged("POD") ) {

                /* Preform Update:
                 * 1. Set destination to match POD
                 */
                unit.setFieldValue("unitGoods.gdsDestination", unit.getFieldValue("unitRouting.rtgPOD1.pointId"));
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    } // updateDestinationAfterPodReroute end

/*
  1] Set OBCarreir as GEN_VESSEL if POD is chnaged to NIS
  2] Set OBCarreir as GEN_TRUCK if POD is chnaged to HON
  3] Set only if POD changed and unit is not Departed
*/
    public void setOBCarrierOnPODChng(Object unit, Object gvyEventUtil,Object  gvyCmisUtil,Object event)
    {
        try
        {
            def updtdischPort = gvyEventUtil.wasFieldChanged(event,'rtgPOD1')
            def transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
            transitState = transitState != null ? transitState.getKey() : ''

            if(!updtdischPort){
                return;
            }else if (updtdischPort && transitState.equals("S70_DEPARTED")){
                return;
            }

            def visit = ""
            def complex = com.navis.argo.ContextHelper.getThreadComplex();
            gvyBaseClass = gvyBaseClass == null ? new GroovyInjectionBase() : gvyBaseClass
            def prevDischPort =  gvyEventUtil.getPreviousPropertyAsString(event, "rtgPOD1");
            def gvyDomQueryObj = gvyBaseClass.getGroovyClassInstance("GvyCmisDomainQueryUtil")
            prevDischPort = gvyDomQueryObj.lookupRtgPOD(prevDischPort)
            prevDischPort = prevDischPort != null ? prevDischPort : ""

            def curDischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            curDischPort = curDischPort != null ? curDischPort : ""

            def intdObCarrierId = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
            intdObCarrierId = intdObCarrierId != null ? intdObCarrierId : ""

            boolean ObcarrierFlag = intdObCarrierId.equals("GEN_TRUCK") || intdObCarrierId.equals("GEN_VESSEL") || intdObCarrierId.equals("GEN_CARRIER") ? true : false

            boolean ObcarrierFlagHon = intdObCarrierId.equals("BARGE") || intdObCarrierId.equals("GEN_VESSEL") || intdObCarrierId.equals("GEN_CARRIER") ? true : false

            /*if(gvyCmisUtil.isNISPort(curDischPort) && !gvyCmisUtil.isNISPort(prevDischPort) && ObcarrierFlag){
              //visit = com.navis.argo.business.model.CarrierVisit.getGenericVesselVisit(complex);
              //SET IT TO BARGE
             visit = com.navis.argo.business.model.CarrierVisit.findOrCreateVesselVisit(com.navis.argo.ContextHelper.getThreadFacility(), "BARGE")
             unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).setUfvIntendedObCv(visit);
             unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).setUfvActualObCv(visit);

            }else*/ if(curDischPort.equals(ContextHelper.getThreadFacility().getFcyId()) && !prevDischPort.equals(ContextHelper.getThreadFacility().getFcyId()) && ObcarrierFlagHon){
            visit = com.navis.argo.business.model.CarrierVisit.getGenericTruckVisit(complex);
            unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).updateObCv(visit);
        }
        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method Ends

    //A5 - FreightKind-Emtpy, ObcarrierMode-vessel, TState-loaded, Booking-no booking,
    // Previous POD changing OPT to valid port then Generate a UNIT_LOAD
    public void postLTVForRtgEmpty(Object unit, Object event, Object gvyEventUtil){
        try{
            def freightkind= unit.getFieldValue("unitFreightKind").getKey();
            def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState").getKey();
            def bkgNbr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr");
            def aobcarrierMode = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCarrierMode");
            aobcarrierMode = aobcarrierMode != null ? aobcarrierMode.getKey() : "";
            def previousDischPort =  gvyEventUtil.getPreviousPropertyAsString(event, "rtgPOD1");

            if('S60_LOADED'.equals(transitState) && aobcarrierMode.equals('VESSEL') && freightkind.equals('MTY') && bkgNbr == null && ('OPT'.equals(previousDischPort) || previousDischPort == null)){
                event.postNewEvent( "UNIT_LOAD", "Created by Unit Reroute on vessel");
            }

        }catch(Exception e){ e.printStackTrace(); }
    }//Method Ends

}