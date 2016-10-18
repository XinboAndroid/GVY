package codeExtensions

import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.business.model.GeneralReference
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.util.BizFailure
import com.navis.inventory.InventoryField
import com.navis.inventory.business.atoms.HazardsNumberTypeEnum
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.Hazards
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.api.client.config.ClientConfig
import com.sun.jersey.api.client.config.DefaultClientConfig
import org.apache.log4j.Logger
import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.type.TypeReference

import javax.ws.rs.core.MediaType

/**
 * Created by kramachandran on 7/6/2016.
 */
class HazmatUpdateNBSplit extends AbstractGeneralNoticeCodeExtension {
    public static final String SEPERATOR = "/";
    private static Logger logger = Logger.getLogger(HazmatUpdateNBSplit.class);
    private String facility = null;

    public String getDestinationBaseURL() {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("MATSON", "HAZMAT", "URL");
        return genRef.getRefValue1();
    }

    public void execute(GroovyEvent inGroovyEvent) throws BizFailure {
        this.log("Execution Started HazmatUpdateNBSplit");
        String inBillNo = "";
        String inContainerNo = null;
        if (inGroovyEvent != null) {
            Event event = inGroovyEvent.getEvent();
            this.log("Found event : " + event.getEvntEventType());
            if (event != null) {
                Unit unit = (Unit) inGroovyEvent.getEntity();
                if (unit != null) {
                    this.log("Found Unit : " + unit.getUnitId());
                    inContainerNo = unit.getUnitId() != null && unit.getUnitId().length() == 11 ? unit.getUnitId().subSequence(0, 10) : unit.getUnitId();
                    if (unit.getUnitGoods() != null) {
                        inBillNo = unit.getUnitGoods().getGdsBlNbr();
                        if (inBillNo != null) {
                            inBillNo = inBillNo.trim();
                            if (inBillNo.length() > 7) {
                                inBillNo = inBillNo.substring(0, 7);
                            }
                        }
                    }

                    this.log("Input paramters are \t BillNo : " + inBillNo + " Container No : " + inContainerNo);
                    if (inBillNo == null && inContainerNo == null) {
                        throw new BizFailure("No BL Number to retrieve Hazards : " + unit.getUnitId());
                    }
                    ClientConfig clientConfig = new DefaultClientConfig();
                    Client client = Client.create(clientConfig);
                    //for usage with TDP app, add an method to construct the URI
                    //psethuraman : Changes to retrieve HAZMAT details for container
                    WebResource resource = null;

                    /*if (inContainerNo != null) {
                        resource = client.resource(getDestinationBaseURL() + "lclcontainer" + SEPERATOR + inContainerNo);
                    } else*/ if (inBillNo != null) {
                        resource = client.resource(getDestinationBaseURL() + "booking" + SEPERATOR + inBillNo /*+ SEPERATOR + inConatinerNo*/);
                    }
                    ClientResponse response = resource != null ? resource.accept(MediaType.APPLICATION_JSON).get(ClientResponse.class) : null;
                    if (response == null || response.getStatus() != 200) {
                        this.log("Request failed");
                        this.log(response.toString());
                        throw new BizFailure("JSON response failed" + response.toString());
                    } else {
                        this.log("Got Response from HAZMAT : " + response.toString());
                        String hazmatString = response.getEntity(String.class);
                        this.log("the response from hazmat is " + hazmatString);
                        if (hazmatString.length() < 10) {
                            throw new BizFailure("JSON dont have necessary Data");
                        }
                        Collection<Map<String, Object>> hazmat = null;
                        try {
                            this.log("parsing json to java map - start");
                            hazmat = new ObjectMapper().readValue(hazmatString, new TypeReference<Collection<Map<String, Object>>>() {
                            });

                        } catch (IOException e) {
                            this.log("Error thrown while parsing JSON to Object mapping : " + hazmatString);
                            this.log(e.printStackTrace());
                            //logger.error(e);
                            throw new BizFailure(ArgoPropertyKeys.GROOVY_EXECUTION_FAILURE, e, hazmat);
                        }
                        this.log("parsing json to java map - complete");
                        Hazards hazards = null;
                        try {
                            Unit ThisUnit = (Unit) inGroovyEvent.getEntity();
                            if (ThisUnit == null) {
                                this.log("Reference to Unit not found!");
                                sendMailAndReturn("Reference to Unit not found!");
                            } else
                                this.log("Unit: " + ThisUnit);
                            //ThisUnit.getUnitGoods().attachHazards(hazards);

                            if (hazmat != null) {
                                Map<String, List<Object>> stringObjectMap = hazmat.iterator().next();
                                /*for (String s : stringObjectMap.keySet()) {
                                    //log(s + "    " + stringObjectMap.get(s).toString());
                                }
                                List<Map<String, Object>> hazardousCommodityLines = stringObjectMap.get("hazardousCommodityLines");
                                //log(hazardousCommodityLines.toString());
                                for (Map<String, Object> o : hazardousCommodityLines) {
                                    for (String s1 : o.keySet()) {
                                        log(o.get(s1).toString());
                                    }
                                }*/
                                this.log("Manipulating new Hazards");
                                Hazards newHazards = getHazardsFromMap(stringObjectMap, ThisUnit.getUnitGoods().getGdsHazards()) as Hazards;
                                this.log("Received Hazards from the map");
                                this.log(newHazards.toString());
                                this.log("Start Clearing all Hazards");
                                ThisUnit.ensureGoods().clearHazardsRef();
                                this.log("Complete Clearing all Hazards");
                                this.log("Start - Attaching the hazards" + newHazards.toString() + " to Unit" + ThisUnit.toString());
                                ThisUnit.attachHazards(newHazards);
                                this.log("Complete - Attaching the hazards" + newHazards.toString() + " to Unit" + ThisUnit.toString());
                            }
                        }
                        catch (Exception ex) {
                            throw new BizFailure(ArgoPropertyKeys.GROOVY_EXECUTION_FAILURE, ex, hazmat);
                        }
                    }
                }
            }
        }
        this.log("Execution End HazmatUpdateNBSplit");
    }


    Hazards getHazardsFromMap(Map<String, List<Object>> inHazardsMap, Hazards inHazards) {
        this.log("creating new hazards");
        Hazards hazardsNew = Hazards.createHazardsEntity();
        this.log("creating new hazard item and adding to hazards");
        List<Map<String, Object>> hazardousCommodityLines = inHazardsMap.get("hazardousCommodityLines");
        for (Map<String, Object> o : hazardousCommodityLines) {
            hazardsNew.addHazardItem(getHazard(o));
            this.log("new hazard item added to hazard");
        }
        this.log(hazardsNew.toString());
        this.log("creating new hazards completed and returning to caller");
        return hazardsNew;
    }

    private HazardItem getHazard(Map<String, Object> commodityLine) {
        HazardItem hazardItem = new HazardItem();
        this.log("creating new hazard item start : "+commodityLine.get("hazPrimaryClass") +"/");
        if (commodityLine.get("hazPrimaryClass") == null
                || "".equals(commodityLine.get("hazPrimaryClass").toString().trim())
                || "X".equalsIgnoreCase(commodityLine.get("hazPrimaryClass").toString().trim())) {
            hazardItem.setFieldValue(InventoryField.HZRDI_IMDG_CLASS, "X");
        } else {
            hazardItem.setFieldValue(InventoryField.HZRDI_IMDG_CLASS, commodityLine.get("hazPrimaryClass"));
        }
        //hazardItem.setFieldValue(InventoryField.HZRDI_IMDG_CODE, commodityLine.get(""));
        HazardsNumberTypeEnum hazardsNumberTypeEnum = null;
        if (commodityLine.get("hazType") != null && commodityLine.get("hazType").equals("UN"))
            hazardsNumberTypeEnum = HazardsNumberTypeEnum.UN;
        else if (commodityLine.get("hazType") != null && commodityLine.get("hazType").equals("NA"))
            hazardsNumberTypeEnum = HazardsNumberTypeEnum.NA;
        hazardItem.setFieldValue(InventoryField.HZRDI_NBR_TYPE, hazardsNumberTypeEnum);

        hazardItem.setFieldValue(InventoryField.HZRDI_U_NNUM, commodityLine.get("hazNumber"));

        String ltdQtyFlag = commodityLine.get("hazLimitedQuantity") != null && !commodityLine.get("hazLimitedQuantity").isEmpty() && "1".equals(commodityLine.get("hazLimitedQuantity")) ? "Y" : "N";
        hazardItem.setFieldValue(InventoryField.HZRDI_LTD_QTY, ltdQtyFlag);
        Double flashPoint = commodityLine.get("hazFlashPoint") != null && !commodityLine.get("hazFlashPoint").isEmpty() ? Double.valueOf(commodityLine.get("hazFlashPoint")) : null;
        hazardItem.setFieldValue(InventoryField.HZRDI_FLASH_POINT, flashPoint);
        hazardItem.setFieldValue(InventoryField.HZRDI_TECH_NAME, commodityLine.get("hazCommodityName"));
        hazardItem.setFieldValue(InventoryField.HZRDI_PROPER_NAME, commodityLine.get("hazCommodityName"));
        String packingGroup = "";
        if (commodityLine.get("hazPackageGroup") != null && "1".equals(commodityLine.get("hazPackageGroup"))) {
            packingGroup = "I";
        } else if (commodityLine.get("hazPackageGroup") != null && "2".equals(commodityLine.get("hazPackageGroup"))) {
            packingGroup = "II";
        } else if (commodityLine.get("hazPackageGroup") != null && "3".equals(commodityLine.get("hazPackageGroup"))) {
            packingGroup = "III";
        }
        if (!packingGroup.isEmpty())
            hazardItem.setFieldValue(InventoryField.HZRDI_PACKING_GROUP, packingGroup);

        String weightStr = commodityLine.get("hazWeight") != null ?
                BigDecimal.valueOf(commodityLine.get("hazWeight")).toString() : BigDecimal.valueOf(0l).toString();
        String weightKg = "";
        if (commodityLine.get("hazWeightUomCode") != null && (commodityLine.get("hazWeightUomCode").contains("kg") || commodityLine.get("hazWeightUomCode").contains("KG"))) {
            weightKg = weightStr;
        } else {
            weightKg = weightFromLBToKg(weightStr);
        }
        this.log("Hazard weight for " + hazardItem.getHzrdiUNnum() + ", is to SNX KG : " + weightKg);
        hazardItem.setFieldValue(InventoryField.HZRDI_WEIGHT, weightKg);

        if (commodityLine.get("hazPiecesUomCode") != null && !commodityLine.get("hazPiecesUomCode").isEmpty())
            hazardItem.setFieldValue(InventoryField.HZRDI_PACKAGE_TYPE, commodityLine.get("hazPiecesUomCode"));
        if (commodityLine.get("hazPieces") != null && commodityLine.get("hazPieces") != 0l)
            hazardItem.setFieldValue(InventoryField.HZRDI_QUANTITY, commodityLine.get("hazPieces"));
        if (commodityLine.get("hazSecondaryClass") != null && !commodityLine.get("hazSecondaryClass").isEmpty())
            hazardItem.setFieldValue(InventoryField.HZRDI_SECONDARY_I_M_O1, commodityLine.get("hazSecondaryClass"));
        if (commodityLine.get("hazTertiaryClass") != null && !commodityLine.get("hazTertiaryClass").isEmpty())
            hazardItem.setFieldValue(InventoryField.HZRDI_SECONDARY_I_M_O2, commodityLine.get("hazTertiaryClass"));
        String marinePollutants = commodityLine.get("hazMarinePollutant") != null && !commodityLine.get("hazMarinePollutant").isEmpty() &&
                "1".equals(commodityLine.get("hazMarinePollutant")) ? "Y" : "N";
        hazardItem.setFieldValue(InventoryField.HZRDI_MARINE_POLLUTANTS, marinePollutants);
        hazardItem.setFieldValue(InventoryField.HZRDI_EMERGENCY_TELEPHONE, commodityLine.get("hazEmergencyContactPhone"));
        this.log(hazardItem.toString());
        return hazardItem;
    }

    public static String weightFromLBToKg(String kgWeight) {
        try {
            String lbsWeight = null;
            if (kgWeight == null || kgWeight.trim().length() == 0) {
                return "";
            }
            double convtWeight = Double.parseDouble(kgWeight) * 0.45359237;
            double result = new BigDecimal("" + convtWeight).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
            lbsWeight = String.valueOf(result);

            return lbsWeight;
        } catch (Exception e) {
            return "0";
        }
    }

    static
    final String json1 = "[{\"associationId\":6449,\"bookingNumber\":\"9076671\",\"containerNumber\":\"MATU246905\",\"alfrescoDocId\":null,\"isActive\":\"Y\",\"createUser\":\"mmoore3\",\"createDate\":\"2016-07-09 00:00:46\",\"lastUpdateUser\":\"mmoore3\",\"lastUpdateDate\":\"2016-07-09 00:00:46\",\"unitId\":null,\"hazardousCommodityLines\":[{\"commodityLineId\":46206,\"hazUniqueId\":\"17916\",\"hazType\":\"UN\",\"hazNumber\":\"1963\",\"hazCommodityName\":\"HELIUM, REFRIGERATED LIQUID\",\"hazPrimaryClass\":\"2.2\",\"hazPrimaryClassName\":null,\"hazSecondaryClass\":\"\",\"hazTertiaryClass\":\"\",\"hazEmergencyContactName\":\"LYNDEN\",\"hazEmergencyContactPhone\":\"(800) 424-9300\",\"hazSecondaryEmergencyContactName\":\"\",\"hazSecondaryEmergencyContactPhone\":\"\",\"hazPackageGroup\":null,\"hazPieces\":2,\"hazPiecesUomCode\":\"CYL\",\"hazWeight\":1600.000000,\"hazWeightUomCode\":\"LBS\",\"hazFlashPoint\":\"\",\"hazFlashPointUomCode\":\"C\",\"hazImdgCfrIndicator\":\"CFR\",\"hazLimitedQuantity\":\"0\",\"hazMarinePollutant\":\"0\",\"hazExplosivePowderWeight\":null,\"hazExplosivePowderWeightUomCode\":\"\",\"hazSpecialPermitNumber\":null,\"isActive\":\"Y\",\"createUser\":\"mmoore3\",\"createDate\":\"2016-07-09 00:46:24\",\"lastUpdateUser\":\"mmoore3\",\"lastUpdateDate\":\"2016-07-09 00:46:24\",\"notes\":\"\",\"moreThan50PercentFlag\":null,\"stowageRestriction\":null,\"explosivePowderWeightApplicable\":null},{\"commodityLineId\":46207,\"hazUniqueId\":\"17916\",\"hazType\":\"UN\",\"hazNumber\":\"1963\",\"hazCommodityName\":\"HELIUM, REFRIGERATED LIQUID\",\"hazPrimaryClass\":\"2.2\",\"hazPrimaryClassName\":null,\"hazSecondaryClass\":\"\",\"hazTertiaryClass\":\"\",\"hazEmergencyContactName\":\"lynden\",\"hazEmergencyContactPhone\":\"(800) 424-9300\",\"hazSecondaryEmergencyContactName\":\"\",\"hazSecondaryEmergencyContactPhone\":\"\",\"hazPackageGroup\":null,\"hazPieces\":1,\"hazPiecesUomCode\":\"CYL\",\"hazWeight\":420.000000,\"hazWeightUomCode\":\"LBS\",\"hazFlashPoint\":\"\",\"hazFlashPointUomCode\":\"F\",\"hazImdgCfrIndicator\":\"CFR\",\"hazLimitedQuantity\":\"0\",\"hazMarinePollutant\":\"0\",\"hazExplosivePowderWeight\":null,\"hazExplosivePowderWeightUomCode\":\"\",\"hazSpecialPermitNumber\":null,\"isActive\":\"Y\",\"createUser\":\"mmoore3\",\"createDate\":\"2016-07-09 00:46:55\",\"lastUpdateUser\":\"mmoore3\",\"lastUpdateDate\":\"2016-07-09 00:46:55\",\"notes\":\"\",\"moreThan50PercentFlag\":null,\"stowageRestriction\":null,\"explosivePowderWeightApplicable\":null},{\"commodityLineId\":46264,\"hazUniqueId\":\"334\",\"hazType\":\"UN\",\"hazNumber\":\"3267\",\"hazCommodityName\":\"CORROSIVE LIQUID, BASIC, ORGANIC, N.O.S.\",\"hazPrimaryClass\":\"8\",\"hazPrimaryClassName\":null,\"hazSecondaryClass\":\"\",\"hazTertiaryClass\":\"\",\"hazEmergencyContactName\":\"LYNDEN\",\"hazEmergencyContactPhone\":\"(800) 424-9300\",\"hazSecondaryEmergencyContactName\":\"\",\"hazSecondaryEmergencyContactPhone\":\"\",\"hazPackageGroup\":3,\"hazPieces\":1,\"hazPiecesUomCode\":\"DRUM\",\"hazWeight\":101.000000,\"hazWeightUomCode\":\"LBS\",\"hazFlashPoint\":\"\",\"hazFlashPointUomCode\":\"F\",\"hazImdgCfrIndicator\":\"CFR\",\"hazLimitedQuantity\":\"0\",\"hazMarinePollutant\":\"0\",\"hazExplosivePowderWeight\":null,\"hazExplosivePowderWeightUomCode\":\"\",\"hazSpecialPermitNumber\":null,\"isActive\":\"Y\",\"createUser\":\"mmoore3\",\"createDate\":\"2016-07-09 01:55:41\",\"lastUpdateUser\":\"mmoore3\",\"lastUpdateDate\":\"2016-07-09 01:55:41\",\"notes\":null,\"moreThan50PercentFlag\":null,\"stowageRestriction\":null,\"explosivePowderWeightApplicable\":null}]}]";
}
