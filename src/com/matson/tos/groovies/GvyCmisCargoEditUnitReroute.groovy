/*
* A1   KR    07/09/15  Alaska Ports
*/


public class GvyCmisCargoEditUnitReroute {
    @SuppressWarnings("GroovyUnusedAssignment")
    public Object processUnitRerouteCmisFeed(String xmlGvyData, Object gvyBaseClass, Object event, Object unit, Object cmisActionList, boolean detnMsg, String prevDischPort) {
        def xmlGvyString = xmlGvyData
        def processCall = ''
        def cmisActnList = ''
        try {
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");

            def updtDportFlag = false; def updtdischPort = false;
            def destination = unit.getFieldValue("unitGoods.gdsDestination")
            destination = destination != null ? destination : ''
            def dischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            dischPort = dischPort != null ? dischPort : ''

            def lkpLocType = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            def lkpLocTypeKey = lkpLocType != null ? lkpLocType.getKey() : ''

            def lkpCarrierId = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")
            lkpCarrierId = lkpCarrierId != null ? lkpCarrierId : ''

            def _transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
            def transitStateKey = _transitState != null ? _transitState.getKey() : ''

            def _category = unit.getFieldValue("unitCategory")
            def categoryKey = _category != null ? _category.getKey() : ''

            def prevDest = gvyEventUtil.getPreviousPropertyAsString(event, "gdsDestination")
            if (!destination.equals(prevDest)) {
                updtDportFlag = true;
            }

            if (!dischPort.equals(prevDischPort)) {
                updtdischPort = true;
            }

            def gvyPositionDetail = gvyBaseClass.getGroovyClassInstance("GvyCmisPositionDetail");
            def locationStatus = gvyPositionDetail.getLocationStatus(lkpLocTypeKey, lkpCarrierId, transitStateKey, gvyCmisUtil, categoryKey, dischPort)
            println('destination ::' + destination + '   locationStatus ::' + locationStatus + '    dischPort::' + dischPort)

            //Check to see if UNIT_PROPERTY_UPDATE call
            if (cmisActionList != null) {
                processCall = "CARGO_EDIT"
                cmisActnList = cmisActionList
            }

            //Check For Detention Msg
            println("Detention MSG :::" + detnMsg)
            if (detnMsg) {
                cmisActnList.setActionList("FREE")
                //cmisActnList.setActionList("EDT")
            }

            //1] processOBCarrierChange 2] processDischPortChange 3] processChasTypeReqChange 4] processReleaseToParty
            if (updtdischPort) {
                //DestPort Changes
                xmlGvyString = processDischPortChange(xmlGvyString, gvyBaseClass, event, unit, gvyEventUtil, gvyCmisUtil, dischPort, locationStatus, cmisActnList, prevDischPort)
            }

            //DISCH_PORT=HON and LOCSTAT=7
            /*if(updtdischPort && dischPort.equals('HON') && locationStatus.equals('7'))
            {
             //SED RECORD
             cmisActnList.setActionList("SED")
            }*/
            if (updtDportFlag) {
                processDestinationPortChange(xmlGvyData, gvyBaseClass, gvyCmisUtil, locationStatus, cmisActnList)
            }

        } catch (Exception e) {
            e.printStackTrace()
        }
    }


    public void processDestinationPortChange(String xmlGvyData, Object gvyBaseClass, Object gvyCmisUtil, String locationStatus, Object cmisActnList) {
        def xmlGvyString = xmlGvyData
        try {
            cmisActnList.setActionList("BDC")
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public String processDischPortChange(String xmlGvyData, Object gvyBaseClass, Object event, Object unit, Object gvyEventUtil, Object gvyCmisUtil, String dischargePort, String locationStatus, Object cmisActnList, String previousDischPort) {
        try {
            cmisActnList.setActionList("EDT")
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return xmlGvyString
    }//Method processDportChange Ends

}