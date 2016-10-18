/*
* srno  Doer  Date       Change
* A1    GR    11/05/10   Added UNIT_STRIP to the List
* A2    GR    07/25/11  Updated from Null to % for overdimension fields
*/

import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.reference.UnLocCode

public class GvyCmisRoutingDetail {

    public String doIt(Object gvyTxtMsgFmt, Object unitObj, String eventMsg) {
        println("In Class GvyCmisRoutingDetail.doIt()")
        def u = unitObj
        def routingFieldAttr = ''
        try {
            //SHIPPER POOL
            def shipperPool = u.getFieldValue("unitRouting.rtgOPL.pointId");
            def shipperPoolAttr = gvyTxtMsgFmt.doIt('shipperPool', shipperPool)

            //DISCHARGE PORT
            def dischargePort = u.getFieldValue("unitRouting.rtgPOD1.pointId")
            dischargePort = dischargePort != null ? dischargePort.trim() : ''
            def dischargePortAttr = gvyTxtMsgFmt.doIt('dischargePort', dischargePort)

            //DPORT
            def dPort = u.getFieldValue("unitGoods.gdsDestination")
            dPort = getDestPort(dPort);
            dPort = dPort != null ? dPort.trim() : ''
            def dPortAttr = gvyTxtMsgFmt.doIt('dPort', dPort)

            //LOAD PORT
            def loadPort = ''
            def transitState = u.getFieldValue("unitActiveUfv.ufvTransitState")
            transitState = transitState != null ? transitState.getKey() : ''
            if (transitState.equals("S60_LOADED")) {
                loadPort = '%'
            } else {
                loadPort = u.getFieldValue("unitRouting.rtgPOL.pointId")
            }
            loadPort = loadPort != null ? loadPort.trim() : ''
            def loadPortAttr = gvyTxtMsgFmt.doIt('loadPort', loadPort)

            //RET_PORT
            def retport = u.getFieldValue("unitRouting.rtgReturnToLocation");
            def retportAttr = gvyTxtMsgFmt.doIt('retPort', retport)

            //OVER_DIMENSIONS ATTRIBUTES
            def overLongBack = "%";   //A2 - updated from null to %
            def overLongFront = "%";
            def overWideLeft = "%";
            def overWideRight = "%";
            def overHeight = "%";

            if (eventMsg.equals('UNIT_IN_GATE') || eventMsg.equals('REVIEW_FOR_STOW') || eventMsg.equals('UNIT_STRIP')) {
                //A1
                overLongBack = convertCmToInch(u.getFieldValue("unitOogBackCm"))
                overLongBack = overLongBack != null ? overLongBack : 0

                overLongFront = convertCmToInch(u.getFieldValue("unitOogFrontCm"))
                overLongFront = overLongFront != null ? overLongFront : 0

                overWideLeft = convertCmToInch(u.getFieldValue("unitOogLeftCm"))
                overWideLeft = overWideLeft != null ? overWideLeft : 0

                overWideRight = convertCmToInch(u.getFieldValue("unitOogRightCm"))
                overWideRight = overWideRight != null ? overWideRight : 0

                overHeight = convertCmToInch(u.getFieldValue("unitOogTopCm"))
                overHeight = overHeight != null ? overHeight : 0
            }

            def overBackAttr = gvyTxtMsgFmt.doIt('overLongBack', overLongBack)
            def overFrontAttr = gvyTxtMsgFmt.doIt('overLongFront', overLongFront)
            def overLeftAttr = gvyTxtMsgFmt.doIt('overWideLeft', overWideLeft)
            def overRightAttr = gvyTxtMsgFmt.doIt('overWideRight', overWideRight)
            def overTopAttr = gvyTxtMsgFmt.doIt('overHeight', overHeight)


            routingFieldAttr = shipperPoolAttr + dischargePortAttr + dPortAttr + loadPortAttr + retportAttr + overBackAttr + overFrontAttr + overLeftAttr + overRightAttr + overTopAttr

            //println('routingFieldAttr : '+routingFieldAttr)
        } catch (Exception e) {
            e.printStackTrace()
        }

        return routingFieldAttr

    }

    //convert Cm To Inch
    private Object convertCmToInch(Object cmValue) {
        def inchValue = null
        def api = new GroovyApi();
        if (cmValue != null && cmValue > 0) {
            def inchVal = cmValue * 0.393700787;
            inchVal = new BigDecimal(inchVal).setScale(2, BigDecimal.ROUND_HALF_UP);
            inchValue = Math.round(inchVal)
        }
        return inchValue
    }

    public String getDestPort(String destPort) {
        def destinationPort = destPort
        destinationPort = destinationPort != null ? destinationPort : ''

        def rtgPointObj = RoutingPoint.findRoutingPoint(destinationPort)
        if (rtgPointObj == null) {
            def UnLocCodeObj = UnLocCode.findUnLocCode(destinationPort)
            if (UnLocCodeObj != null) {
                destinationPort = destinationPort.substring(2)
            }
            println('UnLocCodeObj ::' + UnLocCodeObj + "       destinationPort::" + destinationPort)
        }
        return destinationPort
    }

}//Class Ends
