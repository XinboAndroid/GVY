/*
* Sr Doer Date      Change
* A1 GR   07/21/10  Correction if actualIbcarrier=Vessel then OGC else IGX
* A2 GR   08/22/10  Added BDC for acets on a CLS action (SN4Q change)
* A3 GR   08/22/10  Pulled out the yard first level check.
* A4 GR   09/12/10  Move Else if condition in as was not being called
* A5 GR   10/03/10  Null for actual vessel and voyage
* A6 GR   10/08/10  Gems: CLS and BDC for Location=3
*/


import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper

public class GvyCmisEventUnitRectify {
    def prevTransitState = ''
    def currTransitState = ''
    /*
    * 1] Set the N4:Time In value explicitly as there is an bug Open
    * 2] Pass message only for Facility Hon
    * 3] Conditional check on Previous and current state of the unit.
    */

    public String processRectify(String xmlData, Object event, Object gvyBaseClass) {
        def unit = event.getEntity()
        def xmlGvyString = xmlData

        try {
            //Pass rectify values only for facility HON
            def injBase = new GroovyInjectionBase();
            def facility = injBase.getFacility();
            def facilityId = facility != null ? facility.getFcyId() : ''
            if (!facilityId.equals(ContextHelper.getThreadFacility().getFcyId())) {
                return null
            }
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");

            def lkpLocType = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''

            def aibcarrierMode = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCarrierMode")
            aibcarrierMode = aibcarrierMode != null ? aibcarrierMode.getKey() : ''

            //def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
            Map mapEvntFld = gvyEventUtil.eventFieldChangedValues(event, gvyBaseClass)
            readEventChangedFields(mapEvntFld)

            def posNameDesc = gvyEventUtil.getPreviousPropertyAsString(event, "posName")
            //Example : V-HAL096-208110
            def posValueArr = posNameDesc != null ? posNameDesc.split("-") : null
            println("Facility :" + facilityId + "   lkpLocType::" + lkpLocType + "  transitState::" + currTransitState + " posNameDesc::" + posNameDesc)
            if ((posValueArr != null && posValueArr.length > 0)) //A3
            {
                //Previous Position was Vessel
                if (posValueArr[0].equals("V") && gvyCmisUtil.getVesselClassType(posValueArr[1]).equals('CELL')) {
                    // Previous Position in LongHaul
                    rectifyInyardOnLongHaul(xmlGvyString, gvyCmisUtil, gvyBaseClass)
                } else if (posValueArr[0].equals("V") && gvyCmisUtil.getVesselClassType(posValueArr[1]).equals('BARGE')) {
                    rectifyInyardOnBarge(xmlGvyString, gvyCmisUtil, gvyBaseClass, posValueArr[1])
                } else if (posValueArr[0].equals("T")) {
                    def eventObj = event.getEvent()
                    def eventNotes = eventObj.getEventNote()

                    boolean msgFlag = rectifyPreAdviseEmptyIntoYard(xmlGvyString, unit, event, gvyBaseClass, gvyCmisUtil)

                    if (eventNotes == null && !msgFlag) {
                        rectifyUnitOutgateCorrection(xmlGvyString, gvyCmisUtil, gvyBaseClass)
                    } else if (eventNotes != null && eventNotes.startsWith('INV') && !msgFlag) {
                        rectifyUnitOutgateLoc(xmlGvyString, gvyCmisUtil, gvyBaseClass)
                    }// A2
                    else if (!msgFlag) { //Not getting called
                        rectifyUnitOutgateLoc(xmlGvyString, gvyCmisUtil, gvyBaseClass)
                    }//Add this condition after checking with kel
                }
                //Moved this condition in as
                else if (currTransitState.equals('Retired')) {
                    rectifyDeleteContainer(xmlGvyString, gvyCmisUtil, gvyBaseClass)
                } else if (currTransitState.equals('Departed')) {
                    rectifyIngateCorrection(xmlGvyString, gvyCmisUtil, gvyBaseClass, aibcarrierMode)
                } else {
                    println("Did not Match any Condition to create a Unit Rectfiy Feed")
                }
                //println("posValueArr[0] ::"+posValueArr[0]+"  posValueArr[1] ::"+posValueArr[1])
            }//Outer if Ends


        } catch (Exception e) {
            e.printStackTrace()
        }
    }//Method Process Ends

    /*
     * Scenario One : N4 Rectify ? Yard (outgate correction)
     * In N4, user will: 1. PreAdvise Empty  2. Rectify to yard.(Auto Update Rule)
     * Secnario   T-State     Position       Comment
     * one :      Departed    V-Long haul    Found inyard_verifed seal intact_not loaded to LH  (LVX)
     * two :      Departed    V-Barge        Found inyard_verifed seal intact_not loaded to Barge (LVX)
     * three:     YARD/EC-IN  GenTruck/Yard  Ingate the Conatiner but actually it never came in
                                             rectify to Departed (Ingate Correction)
     * four :     ANY         ANY            Rectify the unit to Retire (Delete Container)
     * five :     Departed    T-GEN_TRUCK    Found inyard_verifed empty
                  a) if rectify notes INV Entered = CLS  (Outgate Correction)
                    b) if rectify notes not Entered = OGX (Outgate Correction)
     * Six  :   Find an MTY in the yard and N4 displaying Departed[Load] so rectify to yard
     */

    //Scenario one in class notes
    public String rectifyInyardOnLongHaul(String xmlData, Object gvyCmisUtil, Object gvyBaseClass) {
        //LVX :Msg Processing
        rectifyFromVesselToYard(xmlData, gvyCmisUtil, gvyBaseClass)
    }

    //Scenario two in class notes
    public String rectifyInyardOnBarge(String xmlData, Object gvyCmisUtil, Object gvyBaseClass, String misc1) {
        def xmlGvyString = xmlData
        try {
            gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "DTD")
            //LVX :Msg Processing
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "misc1=", misc1)
            rectifyFromVesselToYard(xmlGvyString, gvyCmisUtil, gvyBaseClass)
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    //Scenario three in class notes
    public String rectifyIngateCorrection(String xmlData, Object gvyCmisUtil, Object gvyBaseClass, String aibcarrierMode) {
        println("  rectifyIngateCorrection(xmlData,gvyCmisUtil)  ")
        def xmlGvyString = xmlData
        try {
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "3")
            if (aibcarrierMode.equals('VESSEL')) { //A1
                gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "OGC")
            } else {
                gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "IGX")
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    //Scenario four in class notes
    public String rectifyDeleteContainer(String xmlData, Object gvyCmisUtil, Object gvyBaseClass) {
        println("rectifyDeleteContainer()")
        def xmlGvyString = xmlData
        try {
            def dir = gvyCmisUtil.getFieldValues(xmlGvyString, "dir=");
            def ds = gvyCmisUtil.getFieldValues(xmlGvyString, "ds=");
            def dischangePort = gvyCmisUtil.getFieldValues(xmlGvyString, "dischargePort=");
            //boolean nisPod = gvyCmisUtil.isNISPort(dischangePort)
            def ls = gvyCmisUtil.getFieldValues(xmlGvyString, "locationStatus=");
            def misc3 = gvyCmisUtil.getFieldValues(xmlGvyString, "misc3=");

            if ((dir.equals("IN") && ds.equals("CY") && ls.equals("7")) ||
                    (dischangePort.equals(ContextHelper.getThreadFacility().getFcyId()) && misc3.length() > 6)) {
                gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "DTD")
            }
            if (dir.equals("IN") && ls.equals("7")) {
                gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "NDD")
            }

            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "2")
            gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "DEL")
            println("dir :" + dir + " ds:" + ds + "  dischangePort:" + dischangePort + "   ls:" + ls + "  misc3:" + misc3)
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Scenario five a) in class notes
    public String rectifyUnitOutgateLoc(String xmlData, Object gvyCmisUtil, Object gvyBaseClass) {
        println(" rectifyUnitOutgateLoc(xmlData,gvyCmisUtil) ")
        def xmlGvyString = xmlData
        try {
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "1")
            gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "CLS")
            //A2
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStallConfig=", "AO")
            gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "BDC")
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    //Scenario five b) in class notes
    public String rectifyUnitOutgateCorrection(String xmlData, Object gvyCmisUtil, Object gvyBaseClass) {
        println(" rectifyUnitOutgateCorrection(xmlData,gvyCmisUtil) ")
        def xmlGvyString = xmlData
        try {
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "1")
            gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "OGX")

        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    public void rectifyFromVesselToYard(String xmlData, Object gvyCmisUtil, Object gvyBaseClass) {
        def xmlGvyString = xmlData
        try {
            //If UNIT has Chassis LVX
            def chassisNbr = gvyCmisUtil.getFieldValues(xmlGvyString, "chassisNumber=");
            if (!chassisNbr.equals("null")) {
                def xmlchassis = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "unitClass=", "CHASSIS")
                gvyCmisUtil.postMsgForAction(xmlchassis, gvyBaseClass, "LVX")
            }
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "actualVessel=", "null")//A5
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "actualVoyage=", "null")
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "leg=", "null")
            gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "LVX")
            println("chassisNbr   ::" + chassisNbr)
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    /*
     * Scenario six :  Find an MTY in the yard and N4 displaying Departed[Load]
     * So we Preadvise an empty unit and then Rectify it into the yard
     */

    public boolean rectifyPreAdviseEmptyIntoYard(String xmlData, Object unit, Object event, Object gvyBaseClass, Object gvyCmisUtil) {
        def xmlGvyString = xmlData
        try {
            def freightkind = unit.getFieldValue("unitFreightKind")
            freightkind = freightkind != null ? freightkind.getKey() : ''
            def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
            transitState = transitState != null ? transitState.getKey() : ''
            def preAdviseEvent = event.getMostRecentEvent("UNIT_PREADVISE");
            println("    freightkind : " + freightkind + "    transitState ::" + transitState + " PREADVISE : " + preAdviseEvent)
            if (freightkind.equals("MTY") && transitState.equals('S40_YARD') && preAdviseEvent != null) {
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "1")
                gvyCmisUtil.postMsgForAction(xmlGvyString, gvyBaseClass, "OGX")
                return true
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return false
    }

    //Read Event Field Previous and Current value
    public void readEventChangedFields(Object mapEvntField) {
        try {
            Iterator it = mapEvntField.keySet().iterator();
            while (it.hasNext()) {
                def aField = it.next();
                if (aField.equals('ufvTransitState')) {
                    def aEvntFieldObj = mapEvntField.get(aField)
                    //Fetch Updated Field Values
                    def fieldname = aEvntFieldObj.getFieldName()
                    prevTransitState = aEvntFieldObj.getpreviousValue()
                    currTransitState = aEvntFieldObj.getCurrentValue()
                    println('fieldname ::' + fieldname + '  previousValue::' + prevTransitState + '   currentValue::' + currTransitState)
                }
            }//While Ends
        } catch (Exception e) {
            e.printStackTrace()
        }

    }//Method readEventChangedFields Ends


}//Class Ends