import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.*
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.inventory.business.units.*
import com.navis.framework.util.BizViolation
import com.navis.road.business.util.RoadBizUtil
import org.apache.commons.io.IOUtils

/**  A1 2/23/09  Undoing Stuff workaround
 A2 5/26/09  Find unit in complex
 A3 06/02/09 Steven Bauer	 403 - Supress all updates before GetNV
 A4 08/14/09 Steven Bauer	 EP000100565, should have been using cmdyId not name.
 **/
class StuffUnit extends GroovyInjectionBase {

    public String execute(Map inParameters) {
        com.navis.argo.ContextHelper.setThreadExternalUser("ACETS");
        def ctrId = (String) inParameters.get("equipment-id");
        //def stuffManager = getGroovyClassInstance("StuffManager");
        try {
            def recorder = (String) inParameters.get("recorder");

            // Find the empty UFV
            //def emptyUfv = findActiveUfv(ctrId);
            def unitLookup = getGroovyClassInstance("GvyUnitLookup");
            def emptyUfv = unitLookup.getUfvActiveInComplex(ctrId);

            if( emptyUfv == null)
                fail((new StringBuilder()).append("ERR_GVY_STUFF_001. Could not find Active UFV for ").append(ctrId).toString());
            // Stuff it, and get back the new full UFV and Unit
            def stuffedUfv = null;
            def strippedUfv = null;
            def action = "";

            try {
                action = "stuff ";
                // A1
                //stuffedUfv = stuffManager.coreStuffUfv(emptyUfv, null);
                this.log("Found empty UFV, N4 is going to stuff the empty UFV")
                stuffedUfv = stuffUfv(emptyUfv, null);
            } catch ( BizViolation ex) {
                try {
                    action = "stuff or strip ";
                    // check if the commodity id is ok.
                    def ufvUnit = emptyUfv.getUfvUnit();
                    // A3
                    if(isStowplan(ufvUnit)) return;

                    if ( ufvUnit.isStorageEmpty())
                        fail((new StringBuilder()).append("ERR_GVY_STUFF_002. Could not STRIP EMPTY unit: ").append(ctrId).toString());

                    def stripGoods = ufvUnit.getUnitGoods();
                    /*if ( stripGoods != null) {
                        def stripComm = stripGoods.getGdsCommodity();
                        if ( stripComm != null) {
                            def commId = stripComm.getCmdyId();
                            //def commId = stripComm.getCmdyShortName();
                            if ( commId.length() < 3)
                                fail((new StringBuilder()).append("ERR_GVY_STUFF_003. Could not STRIP unit: ").append(ctrId).append(" with COMMODITY code: ").append(commId).toString());

                            if ( !commId.substring(0, 3).equalsIgnoreCase( "AUT")
                                    && !commId.substring(0, 3).equalsIgnoreCase( "CFS")
                                    && !commId.substring(0, 3).equalsIgnoreCase( "COB")) {
                                fail((new StringBuilder()).append("ERR_GVY_STUFF_003. Could not STRIP unit: ").append(ctrId).append(" with COMMODITY code: ").append(commId).toString());
                            }
                        } else {
                            fail((new StringBuilder()).append("ERR_GVY_STUFF_003. Could not STRIP unit: ").append(ctrId).append(" with no COMMODITY code. ").toString());
                        }
                    } else {
                        fail((new StringBuilder()).append("ERR_GVY_STUFF_003. Could not STRIP unit: ").append(ctrId).append(" with no avail COMMODITY code. ").toString());
                    }*/

                    strippedUfv = stripUfvAndRecordEvent( emptyUfv, null, "Stuff Groovy Code");
                    def strippedUnit = strippedUfv.getUfvUnit();
                    action = "strip then stuff again ";
                    // record strip event.
                    //strippedUnit.recordUnitEvent(EventEnum.UNIT_STRIP, null, "Stripped by " + recorder);
                    // A1
                    stuffedUfv = stuffUfv( strippedUfv, null);
                    // Attempt at a WO fix, may cause more harm than good
                    //stuffedUfv = stuffManager.coreStuffUfv(strippedUfv, null);
                } catch ( BizViolation ex1) {
                    System.out.println("Exception for"+ctrId+" "+ex1.getMessage() );
                    ex1.printStackTrace();
                    // TODO correctly rethrow ERR_003.
                    fail((new StringBuilder()).append("ERR_GVY_STUFF_004. Could not ").append(action).append(ctrId).toString()+ex1.getMessage());
                }
            }

            def stuffedUnit = stuffedUfv.getUfvUnit();
            // Update the routing
            String fcyId = stuffedUfv.getUfvFacility().getFcyId();
            this.log("Fcy for message : "+fcyId);

            def carrierModeId = (String) inParameters.get("routing-carrier-mode");
            def carrierId = (String) inParameters.get("routing-carrier-id");
            def pod1Id = (String) inParameters.get("routing-pod-1");
            def polId = (String) inParameters.get("routing-pol");
            def destination = (String) inParameters.get("routing-destination");
            if ("ANK".equalsIgnoreCase(fcyId) && "KQA".equalsIgnoreCase(destination)) {
                pod1Id = "DUT";
            }
            if ("DUT".equalsIgnoreCase(fcyId) && "KQA".equalsIgnoreCase(destination)) {
                pod1Id = "KQA"
                carrierModeId = "VESSEL";
            }
            this.log("Carrier id : "+carrierId);
            def carrierMode = LocTypeEnum.getEnum( carrierModeId);
            this.log("Fcy : "+getFacility());
            def obCarrier = CarrierVisit.findCarrierVisit(getFacility(), carrierMode, carrierId);
            this.log("OB Carrier id : "+obCarrier);
            if ( obCarrier == null)
                fail((new StringBuilder()).append("ERR_GVY_STUFF_005. Could not find Carrier for ").append(ctrId).toString());
            stuffedUfv.updateObCv(obCarrier);
            this.log("update OB CV : "+stuffedUfv.getUfvObCv().getCvId());
            stuffedUfv.setUfvActualObCv(obCarrier);
            this.log("update actual OB CV 1: "+stuffedUfv.getUfvObCv().getCvId());
            this.log("update actual OB CV 2: "+stuffedUfv.getUfvActualObCv().getCvId());
            def routing = stuffedUnit.getUnitRouting();
            if ( routing == null)
                fail((new StringBuilder()).append("ERR_GVY_STUFF_006. Could not find Routing for ").append(ctrId).toString());

            routing.setRtgDeclaredCv(obCarrier);
            if ( pod1Id != null)
                routing.setRtgPOD1(RoutingPoint.findRoutingPoint(pod1Id));
            if ( polId != null)
                routing.setRtgPOL(RoutingPoint.findRoutingPoint(polId));
            stuffedUnit.updateUnitRouting(routing);

            GoodsBase goods = stuffedUnit.getUnitGoods();
            if ( destination != null)
                goods.setGoodsDestination(destination);
            // update other info
            def sealNum = (String) inParameters.get("seal-1");
            def consigneeId = (String) inParameters.get("consignee-id");
            def consigneeName = (String) inParameters.get("consignee-name");
            def shipperId = (String) inParameters.get("shipper-id");
            def shipperName = (String) inParameters.get("shipper-name");
            def remark = (String) inParameters.get("remark");
            def oogBack = (String) inParameters.get("oog-back");
            def oogFront = (String) inParameters.get("oog-front");
            def oogLeft = (String) inParameters.get("oog-left");
            def oogRight = (String) inParameters.get("oog-right");
            def oogHeight = (String) inParameters.get("oog-height");
            def commodityId = (String) inParameters.get("commodity-id");
            def commodityName = (String) inParameters.get("commodity-name");
            def weight = (String) inParameters.get("unit-gross-weight");
            def temperature = (String) inParameters.get("temperature")
            def tempUnit = (String) inParameters.get("tempUnit")
            def isHaz = (String) inParameters.get("is-haz");
            try {
                this.log("Start Hazard refresh - clear haz in N4 first, is Haz? : "+isHaz);
                stuffedUnit.getUnitGoods().clearHazardsRef();
                String transitState = stuffedUfv.getUfvTransitState().getKey();
                transitState = transitState.substring(4);
                this.log("Unit's transit state : "+transitState);
                if ("Y".equalsIgnoreCase(isHaz) && (commodityId != null && commodityId.contains("CFS"))) {
                    //callTDPHazRefreshService(ctrId, fcyId, transitState);
                }
                this.log("end Hazard Refresh");
            } catch (Exception e) {
                this.log("Error thrown while calling WS : "+e);
            }
            if ( sealNum != null)
                stuffedUnit.setUnitSealNbr1( sealNum);
            if ("LCL".equalsIgnoreCase(shipperName)) {
                shipperId = "LCL";
            }
            if ("LCL".equalsIgnoreCase(consigneeName)) {
                consigneeId = "LCL";
            }
            if ( shipperId != null || shipperName != null) {
                Shipper shipper = Shipper.findOrCreateShipper(shipperId, shipperName);
                //goods.setGdsShipperBzu(shipper);
                goods.updateShipper( shipper);
            }
            if ( consigneeId != null || consigneeName != null) {
                Shipper consignee = Shipper.findOrCreateShipper(consigneeId, consigneeName);
                //goods.setGdsConsigneeBzu(consignee);
                goods.updateConsignee( consignee);
            }
            if ( weight != null)
                stuffedUnit.updateGoodsAndCtrWtKg( new Double(weight));
            if (temperature != null && temperature.trim() != "") {
                ReeferRqmnts reefer = goods.getGdsReeferRqmnts();
                try {
                    Double tempVal = new Double(temperature);
                    if (tempUnit == null || tempUnit.trim().equalsIgnoreCase("") || "F".equalsIgnoreCase(tempUnit)) {
                        tempVal = convertTempToCelsius(tempVal);
                        stuffedUfv.setUfvFlexString07(temperature);
                    } else {
                        stuffedUfv.setUfvFlexString07(convertTempToFarenheit(tempVal).toString());
                    }
                    if (reefer == null) {
                        reefer = new ReeferRqmnts();
                    }
                    reefer.setRfreqTempLimitMinC(tempVal);
                    reefer.setRfreqTempRequiredC(tempVal);
                    goods.setGdsReeferRqmnts(reefer);

                } catch (Exception e) {
                    this.log("Failed to update temperature for "+ctrId + " >> temp value : "+temperature);
                    this.log(e.getMessage());
                    e.printStackTrace();
                }
            }
            stuffedUnit.updateRemarks(remark);
            if ( oogBack != null && oogFront != null && oogLeft != null && oogRight != null && oogHeight != null)
                stuffedUnit.updateOog( new Integer(oogBack), new Integer(oogFront), new Integer(oogLeft), new Integer(oogRight), new Integer(oogHeight));

            //def cmdity = Commodity.findCommodity( "AUTO");
            //stuffedUnit.updateCommodity( cmdity);

            if ( commodityId != null) {
                def unitComm = Commodity.findOrCreateCommodity(commodityId);
                //def unitGoods = stuffedUnit.getUnitGoods();
                unitComm.setCmdyShortName( commodityName);
                goods.setGdsCommodity(unitComm);
            }

            // Update the hazardous info
            //def hazardItem = stuffedUnit.getUnitGoods().attachHazard("3", "1203")
            //hazardItem.setHzrdiPageNumber("52");
            // Record an event
            stuffedUnit.recordUnitEvent(EventEnum.UNIT_STUFF, null, "Stuffed by " + recorder);
            return "done via Groovy, unit is: " + stuffedUnit;
        } catch ( Exception ex) {
            fail((new StringBuilder()).append(ex.toString()).append("ERR_GVY_STUFF_999. Could not STUFF unit: ").append(ctrId).toString());
            ex.printStackTrace()
        }
    }

    private boolean isStowplan(unit) {
        def remark = unit.unitRemark;
        if(remark == null) return false;
        return remark.startsWith("Stowplan Data");
    }

    private Double convertTempToCelsius(Double temp) {
        return (temp - 32) /1.8;
    }

    private Double convertTempToFarenheit(Double temp) {
        return temp * 1.8 +32;
    }
    private boolean callTDPHazRefreshService(String inUnitNbr, String facility, String transitState) throws Exception {
        this.log("Start of WS call");
        InputStream stream = null;
        try {
            //@todo configure the WS URL in General Reference
            GeneralReference genRef = GeneralReference.findUniqueEntryById("MATSON", "RESTHAZTDP", "URL");
            URL url = new URL(genRef.getRefValue1() + "/hazmatinterface/refreshunithaz/?"
                    + "equipmentId=" + inUnitNbr + "&facilityId="+ facility + "&transitState=" + transitState);

            this.log("TDP Haz Service URL : " + url.toString());
            URLConnection connection = url.openConnection();
            this.log("Connection : "+connection);
            stream = connection != null ? connection.getInputStream() : null;
            this.log("Stream : "+stream);
            if (stream != null) {
                String StringFromInputStream = IOUtils.toString(stream, "UTF-8");
                this.log("String format of stream : "+StringFromInputStream);
            } else {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "No hazard record found from webservice for Unit:" + inUnitNbr + " in facility :" + facility));
            }
        } catch (Exception e) {
            RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__SERVICE_NOT_AVAILABLE, null,
                    " Haz Refresh Service call failed for :" + inUnitNbr + " in facility :" + facility));
            return false;
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
        this.log("End of WS call");
        return true;
    }
}