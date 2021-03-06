/*
* Sr   Date      Doer    Change
* A1   05/25/10  GR      UNIT_ROLL Not Posting HLP to ACETS
* A2   06/08/10  GR      Consignee Change Generates DAS messages
* A3   08/19/10  GR      Acets Consignee Change Generates DAS messages
* A4   10/03/10  GR      Setting Acutal vessel,voyage and leg value for UNIT_ROLL upto acets.
* A5   10/27/10  GR      Added Condt not to pass DAS messages on UNIT_ROLL as unit roll
                         only occures on export units in N4
* A6   11/15/10  GR      Added Thread Sleep Between ULK and LNK for Posting order.
* A7   04/14/11  GR      Remove Bkg check on UNIT_ROLL Event
* A8   02/14/12  GR      Adding Event Processing Check Methods
* A9   02/27/14  RI		 Added method to capture Integration error
* A101 10/18/16  KR      Read VesselCd and voyagenumber from Field changes, not from Unit.
*                        Enhancement for UNIT_ROLL IGT messages
*
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.orders.business.eqorders.Booking
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger

public class GvyCmisEventUnitPropertyUpdate
{
    private static final Logger LOGGER = Logger.getLogger(GvyCmisEventUnitPropertyUpdate.class);
    def prevAvailDt;
    def prevDetentionDt;
    def unit;
    boolean processAcetsMsg;
    boolean reportProcessing = false;

    public void processUnitPropertyUpdate(Object event,Object api) {
        processUnitPropertyUpdate(event, api, Boolean.FALSE);
    }
    public void processUnitPropertyUpdate(Object event,Object api, boolean isAlwaysSendIGT)
    {
        boolean update = false, blockUpdtForIngate = false, procCmisFeed = false

        def gvyEventUtil = api.getGroovyClassInstance("GvyEventUtil")
        String eventType =  event.event.eventTypeId
        unit = event.getEntity()
        println(":::::::::::processUnitPropertyUpdate :: Start ::::::::::");
        try
        {
            blockUpdtForIngate= gvyEventUtil.holdEventProcessing(event, 'UNIT_IN_GATE', 8)
            if(!blockUpdtForIngate)
            {
                def gvyCargoEdit = api.getGroovyClassInstance("GvyCmisUnitCargoEdit");
                def isPodUpdated = false
                if(eventType.equals('UNIT_PROPERTY_UPDATE')){ //A1
                    //Auto Roll POD if Destiantion Changed
                    gvyCargoEdit.autoRollPod(event,unit,api,gvyEventUtil);
                    isPodUpdated = gvyCargoEdit.isPodUpdated()
                }

                if(isPodUpdated){
                    def gvyEvntcargoEdit = api.getGroovyClassInstance("GvyCmisEventUnitCargoEdit")
                    gvyEvntcargoEdit.processUnitPropertyUpdate(event,api,gvyCargoEdit)
                    reportProcessing = true;
                }else{
                    //1-Cargo Status Report Processing
                    reportProcessing = processCargoStatReport(event,gvyEventUtil,api)
                    //2-Get Previous Avail and Detntion Dt
                    update = setAvailDetnDate(event,api)
                    //3-Create Cmis Feed for Msg from Acets & UI Transaction
                    println(":::::::::::Good till here::::::::::");
                    procCmisFeed = processCmisFeed(gvyEventUtil,api,update,event,isAlwaysSendIGT);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        println("AcetsMsg:"+processAcetsMsg+"   BlockIngate:"+blockUpdtForIngate+"   Report:"+reportProcessing+"     AvailDate:"+update+"   ProcCmisFeed:"+procCmisFeed)
    }//Method processUnitPropertyUpdate Ends


    //1-Cargo Status Report Processing
    public boolean processCargoStatReport(Object event,Object gvyEventUtil,Object api)
    {
        boolean reportProcessing = false;
        def ret = null;
        try
        {
            reportProcessing = gvyEventUtil.verfiyReportSnxProcessing(event)
            if(reportProcessing)
            {
                def cargoStatusGvy = api.getGroovyClassInstance( "GvyUnitCargoStatus");
                ret = cargoStatusGvy.sendXml( "CARGO_STATUS", event);
                api.sendXml(ret)
            }
        }catch(Exception e){
            e.printStackTrace()
            String error = e;
            //A9
            if (error.contains("JMS") && ret != null){
                println("Calling MatGetIntegrationError.createIntegrationError in CARGO_STATUS message");
                def inj = new GroovyInjectionBase();
                def unit = event.getEntity();
                String entity = "Unit";
                def unitId = unit.getUnitId();
                def eventId = event.event.eventTypeId;
                def errDesc = eventId+" Failed for "+unitId;

                inj.getGroovyClassInstance("MatGetIntegrationError").createIntegrationError(error,entity,unitId,eventId,errDesc,ret);
            }
        }
        return reportProcessing
    }//Method processCargoStatReport Ends

    //2-Get Previous Avail and Detntion Dt
    public boolean setAvailDetnDate(Object event,Object api)
    {
        boolean update = false;
        try
        {
            //3- Set Avail Date
            def availLookup = api.getGroovyClassInstance("GvyAvailDate");
            update =   availLookup.setAvailDate(unit, event);
            api.log("---------->Avail Update "+update);
        }catch(Exception e){
            e.printStackTrace()
        }
        return update
    }// Method setAvailDetnDate Ends

    public boolean processCmisFeed(Object gvyEventUtil,Object api, boolean update,Object event) {
        processCmisFeed(gvyEventUtil, api, update, event, Boolean.FALSE);
    }

    public boolean processCmisFeed(Object gvyEventUtil,Object api, boolean update,Object event, boolean isAlwaysSendIGT)
    {
        LOGGER.setLevel(Level.INFO);
        boolean processCmisFeed = false
        def gvyEventObj = event.getEvent()
        String eventType =  gvyEventObj.getEventTypeId()
        def doer = gvyEventObj.getEvntAppliedBy()
        try
        {
            //4-Create Cmis Feed for Msg from Acets & UI Transaction
            processCmisFeed  = gvyEventUtil.verfiyCmisFeedProcessing(event)
            LOGGER.info("processCmisFeed : "+processCmisFeed);
            processAcetsMsg = gvyEventUtil.acetsMesssageFilter(event)
            LOGGER.info("processAcetsMsg : "+processAcetsMsg);
            def detentionDateChng = gvyEventUtil.wasFieldChanged(event,'ufvFlexDate03')
            def consigneeChng = gvyEventUtil.wasFieldChanged(event, 'gdsConsigneeAsString')
            //Print Status Checks
            if(processCmisFeed && !processAcetsMsg)
            {
                //1. N4 TO CMIS data processing
                def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
                def unitDtl = unitDetails.doIt(event)
                LOGGER.info("Event Type : "+eventType);
                //2. SERVICE MSG CHECK
                if(eventType.equals('UNIT_ROLL'))
                {
                    LOGGER.info("DOER : "+doer);
                    if(!doer.contains('ACETS')){ //A07
                        def prevBooking  = gvyEventUtil.getPreviousPropertyAsString(event, "eqboNbr")
                        def gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil")
                        //A4 -Starts
                        /**
                         * A101 - Starts
                         * The vesselCd & vesVoyageNbr has to be read from the Field Changes (not from the unit)
                         */
                        def GvyCmisSrvMsgProcessor = api.getGroovyClassInstance("GvyCmisSrvMsgProcessor");
                        Booking booking = null;
                        VesselVisitDetails vvd = null;
                        def vesselCd = null;
                        def vesVoyageNbr = null;
                        Boolean inUseSuppliedCvId = Boolean.FALSE;
                        try {
                            booking = GvyCmisSrvMsgProcessor.findBookingFromEventChanges(event.getEvent(), unit);
                        LOGGER.info("Booking from Srv processor : "+booking);
                            if (booking != null) {
                                vvd = VesselVisitDetails.resolveVvdFromCv(booking.getEqoVesselVisit());
                                if (vvd != null) {
                        LOGGER.info("VesselVisitDetails resolved from booking : "+vvd);
                                    vesselCd = vvd.getVvdVessel().getVesId();
                        LOGGER.info("Value of Vessel Code from Unit is : "+
                                unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdVessel.vesId")+" from Field Changes is : "+vesselCd);
                        LOGGER.info("vesselCd: "+vesselCd);
                                    vesVoyageNbr = vvd.getVvdObVygNbr();
                        LOGGER.info("Value of voyage Number from Unit is : "+
                                unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdObVygNbr")+" from Field Changes is : "+vesVoyageNbr);
                        LOGGER.info("vesVoyageNbr: "+vesVoyageNbr);
                                    inUseSuppliedCvId = Boolean.TRUE;
                                    /**
                                     * Consignee
                                     * this is used for sending detention message, which is not send for UNIT_ROLL
                                     *
                                     * the change is added to compute the value, can be used in furture
                                     */
                                    if (booking.getEqoConsignee() != null) {
                                        def bookingConsignee = booking.getEqoConsignee().bzuName;
                                        def unitConsignee = unit.getFieldValue("unitGoods.gdsConsigneeAsString");
                                        if (bookingConsignee != null && unitConsignee != null) {
                                            if (!bookingConsignee.equalsIgnoreCase(unitConsignee)) {
                                                consigneeChng = bookingConsignee;
                                            }
                                        }
                                    }
                                }
                            }
                        //A101 - Ends
                        } catch (Exception ex) {
                            LOGGER.error("Error in determining VesselCd and Voyage Number ", ex);
                        }

                        if (vesselCd == null && vesVoyageNbr == null) {
                            vesselCd = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdVessel.vesId");
                            vesVoyageNbr = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdObVygNbr")
                        }
                        def gvyUnitReceive = api.getGroovyClassInstance("GvyCmisEventUnitReceive");
                        unitDtl = gvyUnitReceive.processUnitRecieveFull(unitDtl, gvyCmisUtil, vesselCd, vesVoyageNbr, unit,booking, inUseSuppliedCvId)
                        //A4 - Ends
                        LOGGER.info("unitDtl : "+unitDtl);
                        def xmlGvyAcetsStr = gvyCmisUtil.eventSpecificFieldValue(unitDtl,"locationStallConfig=","AO")
                        def xmlGvyUlkStr = gvyCmisUtil.eventSpecificFieldValue(xmlGvyAcetsStr,"bookingNumber=","null")
                        LOGGER.info("xmlGvyUlkStr : "+xmlGvyUlkStr);
                        gvyCmisUtil.postMsgForAction(xmlGvyUlkStr,api,'ULK')
                        Thread.sleep(2000); //A6
                        gvyCmisUtil.postMsgForAction(xmlGvyAcetsStr,api,'LNK')
                    }
                    //SRV MSG
                    LOGGER.info("Calling GvyCmisSrvMsgProcessor.processServiceMessage with alwaysIGT : "+isAlwaysSendIGT);
                    def gvySrvObj = api.getGroovyClassInstance("GvyCmisSrvMsgProcessor");
                    gvySrvObj.processServiceMessage(unitDtl,event,api,isAlwaysSendIGT);
                } // UnitRoll IF Ends

                //3. Detention Msg Check
                boolean detnMsg = false  //A3
                if(!eventType.equals('UNIT_ROLL') && update || detentionDateChng || consigneeChng)
                {
                    def gvyDentObj = api.getGroovyClassInstance("GvyCmisDetentionMsgProcess");
                    detnMsg = gvyDentObj.detentionProcess(unitDtl,event,api)
                }
                def gvyEvntUpdate = api.getGroovyClassInstance("GvyCmisUnitPropertyUpdate");
                def unitUpdateXml =  gvyEvntUpdate.unitUpdateProcess(unitDtl,event,api,detnMsg)

                //4. MSG For UNIT_RECTIFY
                if(eventType.equals('UNIT_RECTIFY'))
                {
                    def gvyRectifyObj = api.getGroovyClassInstance("GvyCmisUnitRectify");
                    gvyRectifyObj.processRectify(unitDtl,event,api)
                }
            }//ProcessCmisFeed  IF Ends - ELSE IF TO PROCESS DAS TRANSACTION FOR CONSIGNEE CHANGE
            else if ((update || detentionDateChng || consigneeChng) && processAcetsMsg){
                // N4 TO CMIS data processing
                def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
                def unitDtl = unitDetails.doIt(event)
                //Detention Msg Check
                def gvyDentObj = api.getGroovyClassInstance("GvyCmisDetentionMsgProcess");
                gvyDentObj.detentionProcess(unitDtl,event,api)
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return processCmisFeed
    }//Method processCmisFeed ends

    public boolean isCargoNoticeSent(){
        return reportProcessing;
    }

    //A8 - Block Unit Property Processing triggered by internal custom events
    public boolean isCustomEvent(Object event, Object gvyEventUtil){
        boolean blockForNonBuiltInEvnts = false
        blockForNonBuiltInEvnts = gvyEventUtil.holdEventProcessing(event, 'CARGO_EDIT', 3)
        blockForNonBuiltInEvnts = blockForNonBuiltInEvnts ? blockForNonBuiltInEvnts : gvyEventUtil.holdEventProcessing(event, 'REVIEW_FOR_STOW', 3)
        return blockForNonBuiltInEvnts;
    }

    //A8 -Block Unit Property processing for newves execution event
    public boolean suppressForNewves(Object event, Object gvyEventUtil){
        boolean isNewvesEvnt = gvyEventUtil.newVesCheck(event)
        boolean newvesSnx = gvyEventUtil.holdEventProcessing(event, 'UNIT_SNX_UPDATE', 60)
        boolean suppressEvnt = false
        if(isNewvesEvnt && newvesSnx){
            suppressEvnt = true
        }
        return suppressEvnt;
    }

    public void setDetentionDTDFields(Object event, Object unit, Object gvyEventUtil, Object api){
        //05/24/2010 - Set Misc3 in Temp field for DTD
        if(event.event.evntAppliedBy.contains('jms') || event.event.evntAppliedBy.contains('-snx-')) //Loop-1 Starts
        {
            def flexDtl = api.getGroovyClassInstance("GvyCmisFlexFieldDetail");
            def misc3 = flexDtl.getMisc3(unit, gvyEventUtil)

            def shipmentDetails = api.getGroovyClassInstance("GvyCmisShipmentDetail")
            def availDate= unit.getFieldValue("unitActiveUfv.ufvFlexDate02")
            def lastfreeDay = unit.getFieldValue("unitActiveUfv.ufvCalculatedLastFreeDay")
            lastfreeDay =  lastfreeDay != null ? lastfreeDay.replace('!','') : lastfreeDay
            lastfreeDay = shipmentDetails.getlastFreeDate(availDate, lastfreeDay, api)
/*
	  if(misc3 != null && misc3.length() > 0){
		unit.setUnitSealNbr4(misc3)
	   }
	   if(lastfreeDay != null && lastfreeDay.length() > 0){
		unit.setUnitSealNbr3(lastfreeDay)
	   } */
        }//Loop-1 Ends

    }//Method Ends

}// Class Ends