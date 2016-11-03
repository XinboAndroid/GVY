import com.navis.argo.ContextHelper
import com.navis.argo.business.api.IServiceEventFieldChange
import com.navis.argo.business.model.CarrierVisit
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.util.ValueObject
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.Unit
import com.navis.orders.business.eqorders.Booking
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger

/*
* Sr  Doer Date        Chagne
* A1  GR   08/18/2010  SN4Q Change:IGT truck=ZZZZ
* A2  GR   08/21/2010  SN4Q Change:OGC truck=ZZZ,LS=3,pdisp=A
* A3  GR   08/30/2010  SN$Q Change:DAS(DTA) messages for ADD (SN4Q Change)
* A5  GR   09/02/10 -  Bad Condition on processEquiSrvCmisMsg() vesselLineOptr is never CLIENT(11/01/10)
* A6  GR   11/08/10    Pulling out Extra EDT from (clientToMatsonSrvMsg & matsonToClientSrvMsg)
* A7  GR   12/10/10    Update OPL to POL
* A8  GR   04/15/11    Added UNIT_REROUTE to the SRV msg list
* A9  GR   09/18/11    ON CLIENT INGATE Set vesvoy to OB vessel TT#12857
* A10  GR  10/30/11   TOS2.1 Overwrite UNIT_DISCH_COMPLETE with UNIT_DISCH
* A11  GR  01/03/12   Fixed setVesvoyFields Method
* A101 KR   11/02/16  clientToMatsonSrvMsg to use Booking and Event data for vessel, voyage, leg
*/

public class GvyCmisSrvMsgProcessor {
    private static final Logger LOGGER = Logger.getLogger(GvyCmisSrvMsgProcessor.class);
    def equipFlex01 = '';
    def vesselLineOptr = '';
    boolean edtMsgFlag = false;
    def locationStatus = ''

    /* Method Processes the Service msg on the following events OUT_GATE_MESSAGE,UNIT_IN_GATE,UNIT_ROLL
     * The Service messages is generateed if the OB LineOperator and Equi Operator do not match on these events.
    */

    public boolean processServiceMessage(String xmlData, Object event, Object gvyBaseClass) {
        processServiceMessage(xmlData, event, gvyBaseClass, Boolean.FALSE);
    }

    public boolean processServiceMessage(String xmlData, Object event, Object gvyBaseClass, boolean isAlwaysSendIGT) {
        LOGGER.setLevel(Level.INFO);
        def xmlGvyString = xmlData
        try {
            def unit = event.getEntity()
            def gvyEventObj = event.getEvent()
            String eventType = gvyEventObj.getEventTypeId()
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");

            def freightkind = unit.getFieldValue("unitFreightKind")
            freightkind = freightkind != null ? freightkind.getKey() : ''

            def unitBkgNbr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr")
            unitBkgNbr = unitBkgNbr != null ? unitBkgNbr : ''

            def portOfLoad = unit.getFieldValue("unitRouting.rtgPOL.pointId");   //A7
            portOfLoad = portOfLoad != null ? portOfLoad : ''
            //def isNisLoadPort = gvyCmisUtil.isNISPort(portOfLoad)

            //Get Equi SRV
            def unitEquipment = unit.getUnitPrimaryUe()
            def ueEquipmentState = unitEquipment.getUeEquipmentState()
            equipFlex01 = ueEquipmentState != null ? ueEquipmentState.getEqsFlexString01() : ''
            /**
             * Preserving old values in old tag for
             */

            def oldEquipFlex01 = equipFlex01;
            def oldPortOfLoad = portOfLoad;
            def oldUnitBkgNbr = unitBkgNbr;

            if (eventType.equals("UNIT_ROLL")) {
                Booking booking = findBookingFromEventChanges(gvyEventObj, unit);
                equipFlex01 = getNewEqFlexString01(event, booking);
                portOfLoad = booking.getEqoPol().getPointId();
                unitBkgNbr = booking.getEqboNbr();
                //freightkind is not changed, as the ROLL should not change it
                LOGGER.info("For UNIT_ROLL ueEquipmentState.getEqsFlexString01() value from event is " + equipFlex01);
            }

            //set location status value
            getLocationStatus(unit)

            //Departed unit Obj
            def deptUnit = getDepartedUnit(gvyBaseClass, unit)
            def deptUnitBkgnbr = getDepartedUnitBkgNum(gvyBaseClass, unit, deptUnit)
            def deptUnitPOD = deptUnit != null ? deptUnit.getFieldValue("unitRouting.rtgPOD1.pointId") : ''
            //def deptUnitIsNisPOD = gvyCmisUtil.isNISPort(deptUnitPOD)

            /*  if(eventType.equals('UNIT_DELIVER') && freightkind.equals('MTY') && unitBkgNbr != null)
              {
                processEquiSrvCmisMsg(xmlGvyString,gvyCmisUtil,unit,event,gvyBaseClass,eventType)
              } */
            //Ckeck Client Unit for MTY Freight Kind and Last Departed unit Dport as NIS
            LOGGER.info("Event type : "+eventType + ", equipFlex01 : " + equipFlex01 + ", freightkind : " +freightkind);
            if (eventType.equals('UNIT_IN_GATE') && equipFlex01.startsWith("CLI") && freightkind.equals('MTY') /*&& deptUnitIsNisPOD*/) {
                def xmlGvyCliString = gvyCmisUtil.eventSpecificFieldValue(xmlData, "locationStallConfig=", "AO")
                gvyCmisUtil.postMsgForAction(xmlGvyCliString, gvyBaseClass, "DEL")
                //A7 -- gvyCmisUtil.postMsgForAction(xmlGvyCliString,gvyBaseClass,"EDT")
                edtMsgFlag = true
            }
            //else if(eventType.equals('UNIT_IN_GATE') && !unitBkgNbr.equals(deptUnitBkgnbr))
            else if (eventType.equals('UNIT_IN_GATE')) {
                if (freightkind.equals('MTY') && equipFlex01.startsWith("CLI") && (!portOfLoad.equals(ContextHelper.getThreadFacility().getFcyId()) /*&& isNisLoadPort*/)) {
                    processEquiSrvClientUnit(xmlGvyString, gvyCmisUtil, unit, event, gvyBaseClass, eventType)
                } else {
                    processEquiSrvCmisMsg(xmlGvyString, gvyCmisUtil, unit, event, gvyBaseClass, eventType)
                }
            } else if (eventType.equals('UNIT_ROLL') || eventType.equals('UNIT_REROUTE')) //A4
            {
                /**
                 * for UNIT_ROLL this part of code to use old values (same for UNIT_REROUTE)
                 */
                if (freightkind.equals('MTY') && oldEquipFlex01.startsWith("CLI") && (!oldPortOfLoad.equals(ContextHelper.getThreadFacility().getFcyId()) /*&& isNisLoadPort*/)) {
                    processEquiSrvClientUnit(xmlGvyString, gvyCmisUtil, unit, event, gvyBaseClass, eventType)
                } else {
                    LOGGER.info("UNIT ROLL triggering ingate with isAlwaysSendIGT as "+isAlwaysSendIGT);
                    processEquiSrvCmisMsg(xmlGvyString, gvyCmisUtil, unit, event, gvyBaseClass, eventType, isAlwaysSendIGT)
                }
            } else if (eventType.equals('UNIT_DISCH_COMPLETE')) {
                if (freightkind.equals('MTY') && equipFlex01.startsWith("CLI") && (!portOfLoad.equals(ContextHelper.getThreadFacility().getFcyId()) /*&& isNisLoadPort*/)) {
                    processEquiSrvClientUnit(xmlGvyString, gvyCmisUtil, unit, event, gvyBaseClass, eventType)
                }
            }
            println("SrvEventType:" + eventType + "  Freight Kind1:" + freightkind + "  unitBkgNbr:" + unitBkgNbr + "   deptUnitBkgnbr :" + deptUnitBkgnbr + "  Port of Load:" + portOfLoad + "   equipFlex01:" + equipFlex01 + "   vesselLineOptr:" + vesselLineOptr + " locationstatus  :" + locationStatus + " deptUnitPOD::" + deptUnitPOD/*+"   /*deptUnitIsNisPOD::"+deptUnitIsNisPOD*/)

        } catch (Exception e) {
            e.printStackTrace()
        }
        return edtMsgFlag;

    }//Process Method Ends

    public void processEquiSrvCmisMsg(String xmlData, Object gvyCmisUtil, Object unit, Object event, Object gvyBaseClass, String eventType) {
        processEquiSrvCmisMsg(xmlData, gvyCmisUtil, unit, event, gvyBaseClass, eventType, Boolean.FALSE);
    }
    //Method generates Service messages if the OB LineOperator and Equi Operator do not match.
    public void processEquiSrvCmisMsg(String xmlData, Object gvyCmisUtil, Object unit, Object event, Object gvyBaseClass, String eventType, boolean  isAlwaysSendIGT) {
        try {
            def obLineOptChnged = null;

            if ("UNIT_ROLL".equals(eventType)) {
                verifyEqSrvObLineOperator(unit, eventType, event, gvyBaseClass);
            } else {
                verifyEqSrvObLineOperator(unit, eventType);
            }
            LOGGER.warn("obLineOptChnged : "+obLineOptChnged);
            LOGGER.warn("isAlwaysSendIGT : "+isAlwaysSendIGT);
            //If OB line Operator does not match the Equi Srv field
            if (obLineOptChnged || isAlwaysSendIGT) {
                //Client(EquiFlex01) to MAT(OB line Operator)
                LOGGER.warn("processEquiSrvCmisMsg==> equipFlex01 : "+equipFlex01 + ", vesselLineOptr : "+vesselLineOptr);
                if (equipFlex01.startsWith("CLI") && vesselLineOptr.equals('MAT')) {
                    clientToMatsonSrvMsg(xmlData, gvyCmisUtil, unit, event, gvyBaseClass)
                }//MAT(OB line Operator) to Client(EquiFlex01)
                else if (equipFlex01.equals("MAT") && vesselLineOptr.trim().length() > 0 && !vesselLineOptr.equals('MAT')) {
                    //A5
                    matsonToClientSrvMsg(xmlData, gvyCmisUtil, gvyBaseClass, unit)
                } else if (equipFlex01.equals("MAT") && isAlwaysSendIGT) {
                    clientToMatsonSrvMsg(xmlData, gvyCmisUtil, unit, event, gvyBaseClass, Boolean.TRUE);
                } else {
                    println("IB/OB CARARIER IS SAME")
                    // gvyCmisUtil.postMsgForAction(xmlData,gvyBaseClass,"EDT")
                }
            }/*else if((equipFlex01.equals("MAT") && vesselLineOptr.equals('MAT')) || (equipFlex01.equals("CLIENT") && vesselLineOptr.equals('CLIENT'))){
           //If OB line Operator equals Equi Srv field
          println("ELSE IF IB/OB CARARIER IS SAME")
           gvyCmisUtil.postMsgForAction(xmlData,gvyBaseClass,"EDT")
       }*/
            println("obLineOptChnged :" + obLineOptChnged + "   equipFlex01:" + equipFlex01 + "   vesselLineOptr:" + vesselLineOptr)
        } catch (Exception e) {
            e.printStackTrace()
        }
    }//Method processEquiSrvCmisMsg Ends

    //1] Verifies OB LineOperator and Equi Operator match.
    //2] Sets the EquiFlex01 and VesselLineOptr as class variables
    public boolean verifyEqSrvObLineOperator(Object unit, String eventType) {
        boolean srvChanged = false
        try {

            if (eventType.equals('UNIT_DELIVER')) {
                vesselLineOptr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoVesselVisit.cvCvd.vvdBizu.bzuId")
            } else if (eventType.equals('UNIT_DISCH_COMPLETE')) {
                vesselLineOptr = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdBizu.bzuId")
            } else {
                vesselLineOptr = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvCvd.vvdBizu.bzuId")
            }
            vesselLineOptr = vesselLineOptr != null ? vesselLineOptr : ''
            //SRV Check
            LOGGER.warn("equipFlex01: "+equipFlex01 + ", vesselLineOptr : "+vesselLineOptr);
            if (!equipFlex01.equals(vesselLineOptr)) {
                srvChanged = true
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        LOGGER.warn("Service Changed : "+srvChanged);
        return srvChanged
    }//Method srvMessageCheck

    public boolean verifyEqSrvObLineOperator(Object unit, String eventType, GroovyEvent event, Object gvyBaseClass) {
        boolean srvChanged = false
        try {
            LOGGER.info("Finding vessel line operator in Event : "+eventType);
            if (eventType.equals('UNIT_DELIVER')) {
                vesselLineOptr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoVesselVisit.cvCvd.vvdBizu.bzuId")
            } else if (eventType.equals('UNIT_DISCH_COMPLETE')) {
                vesselLineOptr = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdBizu.bzuId")
            } else {
                //def bookingGroovy = gvyBaseClass.getGroovyClassInstance("MATSyncUnitFlexWithBooking");
                LOGGER.info("Finding vessel line operator : "+event);
                if (event != null && event.getEvent() != null) {
                    Booking booking = findBookingFromEventChanges(event.getEvent(), unit);
                    LOGGER.info("Booking from Srv processor : "+booking);
                    if (booking != null) {
                        vesselLineOptr = booking.getEqoVesselVisit().getCarrierOperator().getBzuId();
                        LOGGER.info("Found line Operator : " + vesselLineOptr);
                    } else if (booking == null) {
                        vesselLineOptr = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvCvd.vvdBizu.bzuId");
                        LOGGER.info("No booking found, setting line operator to " + vesselLineOptr);
                    }
                }
            }
            vesselLineOptr = vesselLineOptr != null ? vesselLineOptr : ''
            //SRV Check
            LOGGER.warn("equipFlex01: "+equipFlex01 + ", vesselLineOptr : "+vesselLineOptr);
            if (!equipFlex01.equals(vesselLineOptr)) {
                srvChanged = true
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        LOGGER.warn("Service Changed : "+srvChanged);
        return srvChanged
    }

    public void clientToMatsonSrvMsg(String xmlData, Object gvyCmisUtil, Object unit, Object event, Object gvyBaseClass, Boolean isComputeFromBooking) {
        LOGGER.info("Execute only if IGT true");
        def xmlGvyString = xmlData
        try {
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "srv=", "MAT")
            //def locationStatus = gvyCmisUtil.getFieldValues(xmlGvyString, "locationStatus=")
            def xmlSrvCmisMsg = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStallConfig=", "AO")
            xmlSrvCmisMsg = gvyCmisUtil.eventSpecificFieldValue(xmlSrvCmisMsg, "truck=", "ZZZZ")

            LOGGER.info("locationStatus : " + locationStatus);
            if (locationStatus.equals("1")) {
                xmlSrvCmisMsg = gvyCmisUtil.eventSpecificFieldValue(xmlSrvCmisMsg, "locationStatus=", "3")
                gvyCmisUtil.postMsgForAction(xmlSrvCmisMsg, gvyBaseClass, "ADD")
                LOGGER.info("event.event.eventTypeId : " + event.event.eventTypeId);
                if (!'UNIT_IN_GATE'.equals(event.event.eventTypeId)) {
                    xmlSrvCmisMsg = gvyCmisUtil.eventSpecificFieldValue(xmlSrvCmisMsg, "locationStatus=", "1")
                    //A9 -- FOR CLI VESSEL

                    def carrierId = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
                    def obVesClass = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
                    obVesClass = obVesClass != null ? obVesClass.getKey() : ''

                    def bookingNumber = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr");
                    def freightkind = unit.getFieldValue("unitFreightKind");
                    def vesselCd = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdVessel.vesId");
                    freightkind = freightkind != null ? freightkind.getKey() : ''
                    Booking booking = null;
                    if (isComputeFromBooking) {
                        booking = findBookingFromEventChanges(event.getEvent(), unit);
                        carrierId = booking.getEqoVesselVisit().getCvId();
                        obVesClass = booking.getEqoVesselVisit().getCarrierVesselClassType();
                        obVesClass = obVesClass != null ? obVesClass.getKey() : ''
                        bookingNumber = booking.getEqboNbr();

                    }
                    /**
                     * This is where we're setting vessel, voyage,  leg as first point
                     */
                    xmlSrvCmisMsg = gvyCmisUtil.setVesvoyFields(unit, xmlSrvCmisMsg, carrierId, obVesClass) //A11
                    if (bookingNumber != null && carrierId != null && freightkind != null && (freightkind.equals('MTY'))) {
                        def vesVoyageNbr = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdObVygNbr")
                        if(isComputeFromBooking){
                            VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(booking.getEqoVesselVisit());
                            vesVoyageNbr = vvd.getVvdObVygNbr();
                            vesselCd = vvd.getVvdVessel().getVesId();
                        }
                        def unitReceiveObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventUnitReceive");
                        if (isComputeFromBooking){
                            // the value for actual vessel, voyage, leg set by  gvyCmisUtil.setVesvoyFields(unit, xmlSrvCmisMsg, carrierId, obVesClass) is overridden here based on booking
                            xmlSrvCmisMsg = unitReceiveObj.processUnitRecieveFull(xmlSrvCmisMsg, gvyCmisUtil, vesselCd, vesVoyageNbr, unit, booking,isComputeFromBooking);
                        }
                        else
                            xmlSrvCmisMsg = unitReceiveObj.processUnitRecieveFull(xmlSrvCmisMsg, gvyCmisUtil, carrierId, vesVoyageNbr, unit);
                    }

                    LOGGER.warn("Triggering IGT now");
                    gvyCmisUtil.postMsgForAction(xmlSrvCmisMsg, gvyBaseClass, "IGT") //A1 For unitRoll
                }
            } else {
                gvyCmisUtil.postMsgForAction(xmlSrvCmisMsg, gvyBaseClass, "ADD")
            }

            //A3
            def dischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            dischPort = dischPort != null ? dischPort : ''
            def ds = gvyCmisUtil.getFieldValues(xmlSrvCmisMsg, "ds=");

            if ("CY".equals(ds) && ContextHelper.getThreadFacility().getFcyId().equals(dischPort)) {
                gvyCmisUtil.postMsgForAction(xmlSrvCmisMsg, gvyBaseClass, "DTA")
            }
            //getAllActive Holds and Recreate the Holds Records with ALT & HLR
            def gvyCommentsObj = gvyBaseClass.getGroovyClassInstance("GvyCmisCommentNotesField")
            def gvyStripObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventUnitStrip")
            def holds = gvyCommentsObj.getUnitActiveHolds(unit)
            def unitHolds = holds != null ? holds : ''
            def holdsList = unitHolds.length() > 0 ? unitHolds.split(' ') : []
            for (aHold in holdsList) {
                println("aHold >>>>" + aHold)
                if (aHold.equals('CG') || aHold.equals('RD')) {
                    aHold = aHold.equals('CG') ? 'CG_INSP' : (aHold.equals('RD') ? 'OUTGATE' : aHold)
                }
                def xmlGvyHold = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "msgType=", aHold + '_HOLD')
                gvyCmisUtil.postMsgForAction(xmlGvyHold, gvyBaseClass, "HLP")
                gvyCmisUtil.postMsgForAction(xmlGvyHold, gvyBaseClass, "ALT")
            }
            //EDT
            //-A7 gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"EDT")
            edtMsgFlag = true

        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    //Client to Matson Service Cmis Messages also cerating the Release events on active Holds
    public void clientToMatsonSrvMsg(String xmlData, Object gvyCmisUtil, Object unit, Object event, Object gvyBaseClass) {
        clientToMatsonSrvMsg(xmlData,gvyCmisUtil,unit,event,gvyBaseClass,Boolean.FALSE);
    }

    //Matson to Client Service Cmis Messages
    public void matsonToClientSrvMsg(String xmlData, Object gvyCmisUtil, Object gvyBaseClass, Object unit) {
        def xmlGvyString = xmlData
        try {
            //MAT to Client
            def unitLineOperator = unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOperator.bzuId")
            unitLineOperator = unitLineOperator != null ? unitLineOperator : ''

            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "srv=", unitLineOperator)
            def xmlSrvCmisMsg = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStallConfig=", "AO")

            if (locationStatus.equals("1")) {
                //A2
                xmlSrvCmisMsg = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "truck=", "ZZZZ")
                xmlSrvCmisMsg = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "3")
                xmlSrvCmisMsg = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "planDisp=", "A")
                gvyCmisUtil.postMsgForAction(xmlSrvCmisMsg, gvyBaseClass, "PDU")
                gvyCmisUtil.postMsgForAction(xmlSrvCmisMsg, gvyBaseClass, "OGC")
                //A7-gvyCmisUtil.postMsgForAction(xmlSrvCmisMsg,gvyBaseClass,"EDT")
                edtMsgFlag = true
            } else {
                gvyCmisUtil.postMsgForAction(xmlSrvCmisMsg, gvyBaseClass, "DEL")
                //A7-- gvyCmisUtil.postMsgForAction(xmlSrvCmisMsg,gvyBaseClass,"EDT")
                edtMsgFlag = true
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    //Method Returns the Departed units Booking Number
    public String getDepartedUnitBkgNum(Object gvyBaseClass, Object unit, Object departedUnit) {
        def deptUnitBkgNbr = ''
        try {
            if (departedUnit != null) {
                def deptUnitLocType = departedUnit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
                deptUnitLocType = deptUnitLocType != null ? deptUnitLocType.getKey() : ''

                if (deptUnitLocType.equals('TRUCK')) {
                    deptUnitBkgNbr = departedUnit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr")
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return deptUnitBkgNbr
    }//Method Ends

//  def def updtObCarrFlag = gvyEventUtil.wasFieldChanged(event, "ufvIntendedObCv")

//UNIT_DISCH CONDITION

//Method generates Service messages if the OB LineOperator and Equi Operator do not match.
    public void processEquiSrvClientUnit(String xmlData, Object gvyCmisUtil, Object unit, Object event, Object gvyBaseClass, String eventType) {
        try {
            def obLineOptChnged = verifyEqSrvObLineOperator(unit, eventType)
            //If OB line Operator does not match the Equi Srv field
            if (obLineOptChnged) {
                //Client(EquiFlex01) to MAT(OB line Operator)
                def xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlData, "locationStallConfig=", "AO")
                if (equipFlex01.startsWith("CLI") && vesselLineOptr.equals('MAT')) {
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "srv=", "MAT")
                    gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "DEL")
                    gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "EDT")
                    edtMsgFlag = true
                }//MAT(OB line Operator) to Client(EquiFlex01)
            }
            /*  else if (equipFlex01.equals("CLIENT") && vesselLineOptr.equals('CLIENT'))
              {
                  gvyCmisUtil.postMsgForAction(xmlData,gvyBaseClass,"EDT")
                  edtMsgFlag = true
               } */
            println("processEquiSrvClientUnit :: obLineOptChnged:" + obLineOptChnged + "   equipFlex01:" + equipFlex01 + "   vesselLineOptr:" + vesselLineOptr)
        } catch (Exception e) {
            e.printStackTrace()
        }
    }//Method processEquiSrvCmisMsg Ends

    public void getLocationStatus(Object unit) {
        def lkpLocType = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
        def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
        transitState = transitState != null ? transitState.getKey() : ''
        lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''
        locationStatus = lkpLocType.equals('YARD') || (lkpLocType.equals('TRUCK') && transitState.equals('S30_ECIN')) ? '1' : ''
    }

    public Object getDepartedUnit(Object gvyBaseClass, Object unit) {
        def departedUnit = null;
        try {
            //Depparted Unit
            def gvyGateObj = gvyBaseClass.getGroovyClassInstance("GvyCmisGateData");
            departedUnit = gvyGateObj.getDepatedUnit(unit)
        } catch (Exception e) {
            e.printStackTrace()
        }
        return departedUnit
    }

    public Booking findBookingFromEventChanges(Event event, Unit ThisUnit) {
        Booking  booking = null;
        Iterator fcIt = event.getFieldChanges().iterator();
        String eqboNbr = null;
        String eqboVisit = null;
        String eqboDclrdVisit = null;
        while(fcIt.hasNext()) {
            IServiceEventFieldChange fc = (IServiceEventFieldChange)fcIt.next();
            MetafieldId metafieldId = MetafieldIdFactory.valueOf(fc.getMetafieldId());
            /*fcVao.setFieldValue(ArgoBizMetafield.EVENT_FIELD_CHANGE_METAFIELD_ID, metafieldId);
            fcVao.setFieldValue(ArgoBizMetafield.EVENT_FIELD_CHANGE_PREV_VALUE, ThisEvent.getFieldChangeValue(metafieldId, fc.getPrevVal()));
            fcVao.setFieldValue(ArgoBizMetafield.EVENT_FIELD_CHANGE_NEW_VALUE, ThisEvent.getFieldChangeValue(metafieldId, fc.getNewVal()));*/

            if (InventoryField.EQBO_NBR.equals(metafieldId)) {
                eqboNbr = event.getFieldChangeValue(metafieldId, fc.getNewVal()).toString();
            }
            if (InventoryField.UFV_INTENDED_OB_CV.equals(metafieldId)) {
                eqboVisit = event.getFieldChangeValue(metafieldId, fc.getNewVal()).toString();
            }
            if (InventoryField.RTG_DECLARED_CV.equals(metafieldId)) {
                eqboDclrdVisit = event.getFieldChangeValue(metafieldId, fc.getNewVal()).toString();
            }
        }
        if (eqboVisit == null) {
            eqboVisit = eqboDclrdVisit;
        }
        if (eqboNbr != null && eqboVisit != null) {
            LOGGER.info("eqboNBR : "+eqboNbr + " // eqboVisit : "+eqboVisit);
            CarrierVisit cv = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), eqboVisit);
            booking = Booking.findBooking(eqboNbr, ThisUnit.getUnitLineOperator(), cv);
            if (booking == null) {
                try {
                    booking = Booking.findBookingWithoutLine(eqboNbr, cv);
                } catch (Exception e) {
                    LOGGER.error("Couldnt find the booking with eqboNbr and Visit");
                }
            }
            if (booking == null) {
                try {
                    booking = Booking.findBookingsByNbr(eqboNbr);
                } catch (Exception e) {
                    LOGGER.error("Couldnt find the booking only with eqboNbr");
                }
            }
        }
        return booking;
    }

}// Class Ends