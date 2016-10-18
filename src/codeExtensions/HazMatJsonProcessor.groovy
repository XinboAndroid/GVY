package codeExtensions

import com.navis.argo.business.model.GeneralReference
import com.navis.external.framework.AbstractExtensionCallback
import com.navis.framework.util.BizFailure
import com.navis.inventory.business.imdg.HazardItem
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.api.client.config.ClientConfig
import com.sun.jersey.api.client.config.DefaultClientConfig
import org.apache.commons.lang.builder.EqualsBuilder
import org.apache.commons.lang.builder.HashCodeBuilder
import org.apache.commons.lang.builder.ToStringBuilder
import org.apache.log4j.Logger
import org.codehaus.jackson.annotate.JsonAnySetter
import org.codehaus.jackson.annotate.JsonIgnore
import org.codehaus.jackson.annotate.JsonProperty
import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.map.annotate.JsonSerialize
import org.codehaus.jackson.type.TypeReference

import javax.ws.rs.core.MediaType

/**
 * Created by kramachandran on 7/6/2016.
 */
class HazMatJsonProcessor extends AbstractExtensionCallback {
    public static final String SEPERATOR = "/";
    //private static final String BASE_URI = "https://dev.hazmat.matson.com/hazmat/search/booking";//todo, kramachandran move base URI to an common file
    private static Logger logger = Logger.getLogger(HazMatJsonProcessor.class);
    private String facility = null;

    @Override
    public String getDestinationBaseURL() {
        GeneralReference generalReference = GeneralReference.findUniqueEntryById("MATSON", "URL", "HAZMAT");
        return generalReference.getRefValue1();
    }

    public List<HazardItem> getSNXMessage(String inBillNo, String inContainerNo) throws BizFailure {
        /**
         * The block has to be removed, it's an workaround for self-signed certificate
         */
        // Create a trust manager that does not validate certificate chains
        /* TrustManager[] trustAllCerts = new TrustManager[] {
             new X509TrustManager() {
                 public X509Certificate[] getAcceptedIssuers() {
                     return new X509Certificate[0];
                 }

                 public void checkClientTrusted(X509Certificate[] certs, String authType) {
                 }

                 public void checkServerTrusted(X509Certificate[] certs, String authType) {
                 }
             }
         };*/

        // Install the all-trusting trust manager
        /*try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            ;
        }*/
        /**
         * self-signed certificate workaround ends here
         */
        logger.debug("Input paramters are \t BillNo : " + inBillNo + " Container No : " + inContainerNo);
        if (inBillNo == null && inContainerNo == null) {
            throw new BizFailure("Input paramters cannot be null \t BillNo : " + inBillNo + " Container No : " + inContainerNo);
        }
        ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getClasses().add(JacksonJaxbJsonProvider.class);
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
        Client client = Client.create(clientConfig);
        //for usage with TDP app, add an method to construct the URI
        //psethuraman : Changes to retrieve HAZMAT details for container
        WebResource resource = null;

        if (inContainerNo != null) {
            resource = client.resource(getDestinationBaseURL() + "/hazmat/search/lclcontainer" + SEPERATOR + inContainerNo);
        } else if (inBillNo != null) {
            resource = client.resource(getDestinationBaseURL() + "/hazmat/search/booking" + SEPERATOR + inBillNo /*+ SEPERATOR + inConatinerNo*/);
        }
        ClientResponse response = resource != null ? resource.accept(MediaType.APPLICATION_JSON).get(ClientResponse.class) : null;
        if (response == null || response.getStatus() != 200) {
            logger.error("Request failed");
            logger.error(response.toString());
            throw new BizFailure("JSON response failed" + response.toString());

        } else {
            logger.info("Got Response from HAZMAT : " + response.toString());
            String hazmatString = response.getEntity(String.class);
            //hazmatString = "[{\"associationId\":39,\"bookingNumber\":\"2952474\",\"containerNumber\":\"MATU2469097\",\"alfrescoDocId\":null,\"isActive\":\"Y\",\"createUser\":\"hazmat\",\"createDate\":\"2016-04-29 04:30:08\",\"lastUpdateUser\":\"hazmat\",\"lastUpdateDate\":\"2016-04-29 04:30:08\",\"unitId\":11515555,\"hazardousCommodityLines\":[{\"commodityLineId\":96,\"hazUniqueId\":\"17193\",\"hazType\":\"UN\",\"hazNumber\":\"1165\",\"hazCommodityName\":\"DIOXANE\",\"hazPrimaryClass\":\"3\",\"hazPrimaryClassName\":null,\"hazSecondaryClass\":\"\",\"hazTertiaryClass\":\"\",\"hazEmergencyContactName\":\"\",\"hazEmergencyContactPhone\":\"\",\"hazSecondaryEmergencyContactName\":\"\",\"hazSecondaryEmergencyContactPhone\":\"\",\"hazPackageGroup\":2,\"hazPieces\":1,\"hazPiecesUomCode\":\"BRL\",\"hazWeight\":135.000000,\"hazWeightUomCode\":\"KGS\",\"hazFlashPoint\":\"135\",\"hazFlashPointUomCode\":\"F\",\"hazImdgCfrIndicator\":\"CFR\",\"hazLimitedQuantity\":\"0\",\"hazMarinePollutant\":\"0\",\"hazExplosivePowderWeight\":null,\"hazExplosivePowderWeightUomCode\":\"\",\"hazSpecialPermitNumber\":null,\"isActive\":\"Y\",\"createUser\":\"hazmat\",\"createDate\":\"2016-04-29 21:14:39\",\"lastUpdateUser\":\"hazmat\",\"lastUpdateDate\":\"2016-04-29 21:14:39\",\"notes\":\"\",\"moreThan50PercentFlag\":null,\"stowageRestriction\":null,\"explosivePowderWeightApplicable\":null}]}]";
            List<HAZMAT> hazmatList = null;
            try {
                hazmatList = new ObjectMapper().readValue(hazmatString, new TypeReference<List<HAZMAT>>() {
                });
            } catch (IOException e) {
                logger.error("Error thrown while parsing JSON to Object mapping : " + hazmatString);
                e.printStackTrace();
            }

            if (hazmatList != null) {
                logger.debug("Haz List Generated from Response : " + hazmatList.toString());
            }

            List<HazardItem> snxList = new ArrayList<HazardItem>();
            //Snx output = new Snx();
            for (HAZMAT hazmat : hazmatList) {
                HazardItem hazardItem = new HazardItem();
                //Set the property for the Item, we need to have the logic to set the hazard details independent of the object involved container or booking

                snxList.add(hazardItem);
            }
            if (inContainerNo != null) {
                snxList.add(output);
            }
            return snxList;
        }
    }

    public List<HazardItem> getSNXMessage(String inBillNo, String inContainerNo,
                                          String facility) throws BizFailure {
        setFacility(facility);
        logger.info("Setting the facility for Haz Refresh Request : " + facility);
        return getSNXMessage(inBillNo, inContainerNo);
    }

    public String getFacility() {
        return facility;
    }

    public void setFacility(String facility) {
        this.facility = facility;
    }

    //not able to use ignore unused propery annotation, due to difference in api version 1.2 (available) vs 1.9.x (required)
    //todo move to separate class if possible


    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    /*@Generated("org.jsonschema2pojo")
    @JsonPropertyOrder({
        "associationId",
        "bookingNumber",
        "containerNumber",
        "alfrescoDocId",
        "isActive",
        "createUser",
        "createDate",
        "lastUpdateUser",
        "lastUpdateDate",
        "unitId",
        "hazardousCommodityLines"
    })*/
    public class HAZMAT {

        @JsonProperty("associationId")
        private long associationId;
        @JsonProperty("bookingNumber")
        private String bookingNumber;
        @JsonProperty("containerNumber")
        private String containerNumber;
        @JsonProperty("alfrescoDocId")
        private Object alfrescoDocId;
        @JsonProperty("isActive")
        private String isActive;
        @JsonProperty("createUser")
        private String createUser;
        @JsonProperty("createDate")
        private String createDate;
        @JsonProperty("lastUpdateUser")
        private String lastUpdateUser;
        @JsonProperty("lastUpdateDate")
        private String lastUpdateDate;
        @JsonProperty("unitId")
        private Object unitId;
        @JsonProperty("hazardousCommodityLines")
        private List<HazardousCommodityLine> hazardousCommodityLines = new ArrayList<HazardousCommodityLine>();
        @JsonIgnore
        private Map<String, Object> additionalProperties = new HashMap<String, Object>();

/**
 *
 * @return
 * The associationId
 */
        @JsonProperty("associationId")
        public long getAssociationId() {
            return associationId;
        }

/**
 *
 * @param associationId
 * The associationId
 */
        @JsonProperty("associationId")
        public void setAssociationId(long associationId) {
            this.associationId = associationId;
        }

/**
 *
 * @return
 * The bookingNumber
 */
        @JsonProperty("bookingNumber")
        public String getBookingNumber() {
            return bookingNumber;
        }

/**
 *
 * @param bookingNumber
 * The bookingNumber
 */
        @JsonProperty("bookingNumber")
        public void setBookingNumber(String bookingNumber) {
            this.bookingNumber = bookingNumber;
        }

/**
 *
 * @return
 * The containerNumber
 */
        @JsonProperty("containerNumber")
        public String getContainerNumber() {
            return containerNumber;
        }

/**
 *
 * @param containerNumber
 * The containerNumber
 */
        @JsonProperty("containerNumber")
        public void setContainerNumber(String containerNumber) {
            this.containerNumber = containerNumber;
        }

/**
 *
 * @return
 * The alfrescoDocId
 */
        @JsonProperty("alfrescoDocId")
        public Object getAlfrescoDocId() {
            return alfrescoDocId;
        }

/**
 *
 * @param alfrescoDocId
 * The alfrescoDocId
 */
        @JsonProperty("alfrescoDocId")
        public void setAlfrescoDocId(Object alfrescoDocId) {
            this.alfrescoDocId = alfrescoDocId;
        }

/**
 *
 * @return
 * The isActive
 */
        @JsonProperty("isActive")
        public String getIsActive() {
            return isActive;
        }

/**
 *
 * @param isActive
 * The isActive
 */
        @JsonProperty("isActive")
        public void setIsActive(String isActive) {
            this.isActive = isActive;
        }

/**
 *
 * @return
 * The createUser
 */
        @JsonProperty("createUser")
        public String getCreateUser() {
            return createUser;
        }

/**
 *
 * @param createUser
 * The createUser
 */
        @JsonProperty("createUser")
        public void setCreateUser(String createUser) {
            this.createUser = createUser;
        }

/**
 *
 * @return
 * The createDate
 */
        @JsonProperty("createDate")
        public String getCreateDate() {
            return createDate;
        }

/**
 *
 * @param createDate
 * The createDate
 */
        @JsonProperty("createDate")
        public void setCreateDate(String createDate) {
            this.createDate = createDate;
        }

/**
 *
 * @return
 * The lastUpdateUser
 */
        @JsonProperty("lastUpdateUser")
        public String getLastUpdateUser() {
            return lastUpdateUser;
        }

/**
 *
 * @param lastUpdateUser
 * The lastUpdateUser
 */
        @JsonProperty("lastUpdateUser")
        public void setLastUpdateUser(String lastUpdateUser) {
            this.lastUpdateUser = lastUpdateUser;
        }

/**
 *
 * @return
 * The lastUpdateDate
 */
        @JsonProperty("lastUpdateDate")
        public String getLastUpdateDate() {
            return lastUpdateDate;
        }

/**
 *
 * @param lastUpdateDate
 * The lastUpdateDate
 */
        @JsonProperty("lastUpdateDate")
        public void setLastUpdateDate(String lastUpdateDate) {
            this.lastUpdateDate = lastUpdateDate;
        }

/**
 *
 * @return
 * The unitId
 */
        @JsonProperty("unitId")
        public Object getUnitId() {
            return unitId;
        }

/**
 *
 * @param unitId
 * The unitId
 */
        @JsonProperty("unitId")
        public void setUnitId(Object unitId) {
            this.unitId = unitId;
        }

/**
 *
 * @return
 * The hazardousCommodityLines
 */
        @JsonProperty("hazardousCommodityLines")
        public List<HazardousCommodityLine> getHazardousCommodityLines() {
            return hazardousCommodityLines;
        }

/**
 *
 * @param hazardousCommodityLines
 * The hazardousCommodityLines
 */
        @JsonProperty("hazardousCommodityLines")
        public void setHazardousCommodityLines(List<HazardousCommodityLine> hazardousCommodityLines) {
            this.hazardousCommodityLines = hazardousCommodityLines;
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }

        /*@JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            return this.additionalProperties;
        }*/

        @JsonAnySetter
        public void setAdditionalProperty(String name, Object value) {
            this.additionalProperties.put(name, value);
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(associationId).append(bookingNumber).append(containerNumber).append(alfrescoDocId).append(isActive).append(createUser).append(createDate).append(lastUpdateUser).append(lastUpdateDate).append(unitId).append(hazardousCommodityLines).append(additionalProperties).toHashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if ((other instanceof HAZMAT) == false) {
                return false;
            }
            HAZMAT rhs = ((HAZMAT) other);
            return new EqualsBuilder().append(associationId, rhs.associationId).append(bookingNumber, rhs.bookingNumber).append(containerNumber, rhs.containerNumber).append(alfrescoDocId, rhs.alfrescoDocId).append(isActive, rhs.isActive).append(createUser, rhs.createUser).append(createDate, rhs.createDate).append(lastUpdateUser, rhs.lastUpdateUser).append(lastUpdateDate, rhs.lastUpdateDate).append(unitId, rhs.unitId).append(hazardousCommodityLines, rhs.hazardousCommodityLines).append(additionalProperties, rhs.additionalProperties).isEquals();
        }

    }

    //not able to use ignore unused propery annotation, due to difference in api version 1.2 (available) vs 1.9.x (required)
    //todo move to separate class if possible

    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    public class HazardousCommodityLine {

        @JsonProperty("commodityLineId")
        private long commodityLineId;
        @JsonProperty("hazUniqueId")
        private String hazUniqueId;
        @JsonProperty("hazType")
        private String hazType;
        @JsonProperty("hazNumber")
        private String hazNumber;
        @JsonProperty("hazCommodityName")
        private String hazCommodityName;
        @JsonProperty("hazPrimaryClass")
        private String hazPrimaryClass;
        @JsonProperty("hazPrimaryClassName")
        private Object hazPrimaryClassName;
        @JsonProperty("hazSecondaryClass")
        private String hazSecondaryClass;
        @JsonProperty("hazTertiaryClass")
        private String hazTertiaryClass;
        @JsonProperty("hazEmergencyContactName")
        private String hazEmergencyContactName;
        @JsonProperty("hazEmergencyContactPhone")
        private String hazEmergencyContactPhone;
        @JsonProperty("hazSecondaryEmergencyContactName")
        private String hazSecondaryEmergencyContactName;
        @JsonProperty("hazSecondaryEmergencyContactPhone")
        private String hazSecondaryEmergencyContactPhone;
        @JsonProperty("hazPackageGroup")
        private long hazPackageGroup;
        @JsonProperty("hazPieces")
        private long hazPieces;
        @JsonProperty("hazPiecesUomCode")
        private String hazPiecesUomCode;
        @JsonProperty("hazWeight")
        private float hazWeight;
        @JsonProperty("hazWeightUomCode")
        private String hazWeightUomCode;
        @JsonProperty("hazFlashPoint")
        private String hazFlashPoint;
        @JsonProperty("hazFlashPointUomCode")
        private String hazFlashPointUomCode;
        @JsonProperty("hazImdgCfrIndicator")
        private String hazImdgCfrIndicator;
        @JsonProperty("hazLimitedQuantity")
        private String hazLimitedQuantity;
        @JsonProperty("hazMarinePollutant")
        private String hazMarinePollutant;
        @JsonProperty("hazExplosivePowderWeight")
        private Object hazExplosivePowderWeight;
        @JsonProperty("hazExplosivePowderWeightUomCode")
        private String hazExplosivePowderWeightUomCode;
        @JsonProperty("hazSpecialPermitNumber")
        private Object hazSpecialPermitNumber;
        @JsonProperty("isActive")
        private String isActive;
        @JsonProperty("createUser")
        private String createUser;
        @JsonProperty("createDate")
        private String createDate;
        @JsonProperty("lastUpdateUser")
        private String lastUpdateUser;
        @JsonProperty("lastUpdateDate")
        private String lastUpdateDate;
        @JsonProperty("notes")
        private Object notes;
        @JsonProperty("moreThan50PercentFlag")
        private Object moreThan50PercentFlag;
        @JsonProperty("stowageRestriction")
        private Object stowageRestriction;
        @JsonProperty("explosivePowderWeightApplicable")
        private Object explosivePowderWeightApplicable;
        @JsonIgnore
        private Map<String, Object> additionalProperties = new HashMap<String, Object>();

/**
 *
 * @return
 * The commodityLineId
 */
        @JsonProperty("commodityLineId")
        public long getCommodityLineId() {
            return commodityLineId;
        }

/**
 *
 * @param commodityLineId
 * The commodityLineId
 */
        @JsonProperty("commodityLineId")
        public void setCommodityLineId(long commodityLineId) {
            this.commodityLineId = commodityLineId;
        }

/**
 *
 * @return
 * The hazUniqueId
 */
        @JsonProperty("hazUniqueId")
        public String getHazUniqueId() {
            return hazUniqueId;
        }

/**
 *
 * @param hazUniqueId
 * The hazUniqueId
 */
        @JsonProperty("hazUniqueId")
        public void setHazUniqueId(String hazUniqueId) {
            this.hazUniqueId = hazUniqueId;
        }

/**
 *
 * @return
 * The hazType
 */
        @JsonProperty("hazType")
        public String getHazType() {
            return hazType;
        }

/**
 *
 * @param hazType
 * The hazType
 */
        @JsonProperty("hazType")
        public void setHazType(String hazType) {
            this.hazType = hazType;
        }

/**
 *
 * @return
 * The hazNumber
 */
        @JsonProperty("hazNumber")
        public String getHazNumber() {
            return hazNumber;
        }

/**
 *
 * @param hazNumber
 * The hazNumber
 */
        @JsonProperty("hazNumber")
        public void setHazNumber(String hazNumber) {
            this.hazNumber = hazNumber;
        }

/**
 *
 * @return
 * The hazCommodityName
 */
        @JsonProperty("hazCommodityName")
        public String getHazCommodityName() {
            return hazCommodityName;
        }

/**
 *
 * @param hazCommodityName
 * The hazCommodityName
 */
        @JsonProperty("hazCommodityName")
        public void setHazCommodityName(String hazCommodityName) {
            this.hazCommodityName = hazCommodityName;
        }

/**
 *
 * @return
 * The hazPrimaryClass
 */
        @JsonProperty("hazPrimaryClass")
        public String getHazPrimaryClass() {
            return hazPrimaryClass;
        }

/**
 *
 * @param hazPrimaryClass
 * The hazPrimaryClass
 */
        @JsonProperty("hazPrimaryClass")
        public void setHazPrimaryClass(String hazPrimaryClass) {
            this.hazPrimaryClass = hazPrimaryClass;
        }

/**
 *
 * @return
 * The hazPrimaryClassName
 */
        @JsonProperty("hazPrimaryClassName")
        public Object getHazPrimaryClassName() {
            return hazPrimaryClassName;
        }

/**
 *
 * @param hazPrimaryClassName
 * The hazPrimaryClassName
 */
        @JsonProperty("hazPrimaryClassName")
        public void setHazPrimaryClassName(Object hazPrimaryClassName) {
            this.hazPrimaryClassName = hazPrimaryClassName;
        }

/**
 *
 * @return
 * The hazSecondaryClass
 */
        @JsonProperty("hazSecondaryClass")
        public String getHazSecondaryClass() {
            return hazSecondaryClass;
        }

/**
 *
 * @param hazSecondaryClass
 * The hazSecondaryClass
 */
        @JsonProperty("hazSecondaryClass")
        public void setHazSecondaryClass(String hazSecondaryClass) {
            this.hazSecondaryClass = hazSecondaryClass;
        }

/**
 *
 * @return
 * The hazTertiaryClass
 */
        @JsonProperty("hazTertiaryClass")
        public String getHazTertiaryClass() {
            return hazTertiaryClass;
        }

/**
 *
 * @param hazTertiaryClass
 * The hazTertiaryClass
 */
        @JsonProperty("hazTertiaryClass")
        public void setHazTertiaryClass(String hazTertiaryClass) {
            this.hazTertiaryClass = hazTertiaryClass;
        }

/**
 *
 * @return
 * The hazEmergencyContactName
 */
        @JsonProperty("hazEmergencyContactName")
        public String getHazEmergencyContactName() {
            return hazEmergencyContactName;
        }

/**
 *
 * @param hazEmergencyContactName
 * The hazEmergencyContactName
 */
        @JsonProperty("hazEmergencyContactName")
        public void setHazEmergencyContactName(String hazEmergencyContactName) {
            this.hazEmergencyContactName = hazEmergencyContactName;
        }

/**
 *
 * @return
 * The hazEmergencyContactPhone
 */
        @JsonProperty("hazEmergencyContactPhone")
        public String getHazEmergencyContactPhone() {
            return hazEmergencyContactPhone;
        }

/**
 *
 * @param hazEmergencyContactPhone
 * The hazEmergencyContactPhone
 */
        @JsonProperty("hazEmergencyContactPhone")
        public void setHazEmergencyContactPhone(String hazEmergencyContactPhone) {
            this.hazEmergencyContactPhone = hazEmergencyContactPhone;
        }

/**
 *
 * @return
 * The hazSecondaryEmergencyContactName
 */
        @JsonProperty("hazSecondaryEmergencyContactName")
        public String getHazSecondaryEmergencyContactName() {
            return hazSecondaryEmergencyContactName;
        }

/**
 *
 * @param hazSecondaryEmergencyContactName
 * The hazSecondaryEmergencyContactName
 */
        @JsonProperty("hazSecondaryEmergencyContactName")
        public void setHazSecondaryEmergencyContactName(String hazSecondaryEmergencyContactName) {
            this.hazSecondaryEmergencyContactName = hazSecondaryEmergencyContactName;
        }

/**
 *
 * @return
 * The hazSecondaryEmergencyContactPhone
 */
        @JsonProperty("hazSecondaryEmergencyContactPhone")
        public String getHazSecondaryEmergencyContactPhone() {
            return hazSecondaryEmergencyContactPhone;
        }

/**
 *
 * @param hazSecondaryEmergencyContactPhone
 * The hazSecondaryEmergencyContactPhone
 */
        @JsonProperty("hazSecondaryEmergencyContactPhone")
        public void setHazSecondaryEmergencyContactPhone(String hazSecondaryEmergencyContactPhone) {
            this.hazSecondaryEmergencyContactPhone = hazSecondaryEmergencyContactPhone;
        }

/**
 *
 * @return
 * The hazPackageGroup
 */
        @JsonProperty("hazPackageGroup")
        public long getHazPackageGroup() {
            return hazPackageGroup;
        }

/**
 *
 * @param hazPackageGroup
 * The hazPackageGroup
 */
        @JsonProperty("hazPackageGroup")
        public void setHazPackageGroup(long hazPackageGroup) {
            this.hazPackageGroup = hazPackageGroup;
        }

/**
 *
 * @return
 * The hazPieces
 */
        @JsonProperty("hazPieces")
        public long getHazPieces() {
            return hazPieces;
        }

/**
 *
 * @param hazPieces
 * The hazPieces
 */
        @JsonProperty("hazPieces")
        public void setHazPieces(long hazPieces) {
            this.hazPieces = hazPieces;
        }

/**
 *
 * @return
 * The hazPiecesUomCode
 */
        @JsonProperty("hazPiecesUomCode")
        public String getHazPiecesUomCode() {
            return hazPiecesUomCode;
        }

/**
 *
 * @param hazPiecesUomCode
 * The hazPiecesUomCode
 */
        @JsonProperty("hazPiecesUomCode")
        public void setHazPiecesUomCode(String hazPiecesUomCode) {
            this.hazPiecesUomCode = hazPiecesUomCode;
        }

/**
 *
 * @return
 * The hazWeight
 */
        @JsonProperty("hazWeight")
        public float getHazWeight() {
            return hazWeight;
        }

/**
 *
 * @param hazWeight
 * The hazWeight
 */
        @JsonProperty("hazWeight")
        public void setHazWeight(float hazWeight) {
            this.hazWeight = hazWeight;
        }

/**
 *
 * @return
 * The hazWeightUomCode
 */
        @JsonProperty("hazWeightUomCode")
        public String getHazWeightUomCode() {
            return hazWeightUomCode;
        }

/**
 *
 * @param hazWeightUomCode
 * The hazWeightUomCode
 */
        @JsonProperty("hazWeightUomCode")
        public void setHazWeightUomCode(String hazWeightUomCode) {
            this.hazWeightUomCode = hazWeightUomCode;
        }

/**
 *
 * @return
 * The hazFlashPoint
 */
        @JsonProperty("hazFlashPoint")
        public String getHazFlashPoint() {
            return hazFlashPoint;
        }

/**
 *
 * @param hazFlashPoint
 * The hazFlashPoint
 */
        @JsonProperty("hazFlashPoint")
        public void setHazFlashPoint(String hazFlashPoint) {
            this.hazFlashPoint = hazFlashPoint;
        }

/**
 *
 * @return
 * The hazFlashPointUomCode
 */
        @JsonProperty("hazFlashPointUomCode")
        public String getHazFlashPointUomCode() {
            return hazFlashPointUomCode;
        }

/**
 *
 * @param hazFlashPointUomCode
 * The hazFlashPointUomCode
 */
        @JsonProperty("hazFlashPointUomCode")
        public void setHazFlashPointUomCode(String hazFlashPointUomCode) {
            this.hazFlashPointUomCode = hazFlashPointUomCode;
        }

/**
 *
 * @return
 * The hazImdgCfrIndicator
 */
        @JsonProperty("hazImdgCfrIndicator")
        public String getHazImdgCfrIndicator() {
            return hazImdgCfrIndicator;
        }

/**
 *
 * @param hazImdgCfrIndicator
 * The hazImdgCfrIndicator
 */
        @JsonProperty("hazImdgCfrIndicator")
        public void setHazImdgCfrIndicator(String hazImdgCfrIndicator) {
            this.hazImdgCfrIndicator = hazImdgCfrIndicator;
        }

/**
 *
 * @return
 * The hazLimitedQuantity
 */
        @JsonProperty("hazLimitedQuantity")
        public String getHazLimitedQuantity() {
            return hazLimitedQuantity;
        }

/**
 *
 * @param hazLimitedQuantity
 * The hazLimitedQuantity
 */
        @JsonProperty("hazLimitedQuantity")
        public void setHazLimitedQuantity(String hazLimitedQuantity) {
            this.hazLimitedQuantity = hazLimitedQuantity;
        }

/**
 *
 * @return
 * The hazMarinePollutant
 */
        @JsonProperty("hazMarinePollutant")
        public String getHazMarinePollutant() {
            return hazMarinePollutant;
        }

/**
 *
 * @param hazMarinePollutant
 * The hazMarinePollutant
 */
        @JsonProperty("hazMarinePollutant")
        public void setHazMarinePollutant(String hazMarinePollutant) {
            this.hazMarinePollutant = hazMarinePollutant;
        }

/**
 *
 * @return
 * The hazExplosivePowderWeight
 */
        @JsonProperty("hazExplosivePowderWeight")
        public Object getHazExplosivePowderWeight() {
            return hazExplosivePowderWeight;
        }

/**
 *
 * @param hazExplosivePowderWeight
 * The hazExplosivePowderWeight
 */
        @JsonProperty("hazExplosivePowderWeight")
        public void setHazExplosivePowderWeight(Object hazExplosivePowderWeight) {
            this.hazExplosivePowderWeight = hazExplosivePowderWeight;
        }

/**
 *
 * @return
 * The hazExplosivePowderWeightUomCode
 */
        @JsonProperty("hazExplosivePowderWeightUomCode")
        public String getHazExplosivePowderWeightUomCode() {
            return hazExplosivePowderWeightUomCode;
        }

/**
 *
 * @param hazExplosivePowderWeightUomCode
 * The hazExplosivePowderWeightUomCode
 */
        @JsonProperty("hazExplosivePowderWeightUomCode")
        public void setHazExplosivePowderWeightUomCode(String hazExplosivePowderWeightUomCode) {
            this.hazExplosivePowderWeightUomCode = hazExplosivePowderWeightUomCode;
        }

/**
 *
 * @return
 * The hazSpecialPermitNumber
 */
        @JsonProperty("hazSpecialPermitNumber")
        public Object getHazSpecialPermitNumber() {
            return hazSpecialPermitNumber;
        }

/**
 *
 * @param hazSpecialPermitNumber
 * The hazSpecialPermitNumber
 */
        @JsonProperty("hazSpecialPermitNumber")
        public void setHazSpecialPermitNumber(Object hazSpecialPermitNumber) {
            this.hazSpecialPermitNumber = hazSpecialPermitNumber;
        }

/**
 *
 * @return
 * The isActive
 */
        @JsonProperty("isActive")
        public String getIsActive() {
            return isActive;
        }

/**
 *
 * @param isActive
 * The isActive
 */
        @JsonProperty("isActive")
        public void setIsActive(String isActive) {
            this.isActive = isActive;
        }

/**
 *
 * @return
 * The createUser
 */
        @JsonProperty("createUser")
        public String getCreateUser() {
            return createUser;
        }

/**
 *
 * @param createUser
 * The createUser
 */
        @JsonProperty("createUser")
        public void setCreateUser(String createUser) {
            this.createUser = createUser;
        }

/**
 *
 * @return
 * The createDate
 */
        @JsonProperty("createDate")
        public String getCreateDate() {
            return createDate;
        }

/**
 *
 * @param createDate
 * The createDate
 */
        @JsonProperty("createDate")
        public void setCreateDate(String createDate) {
            this.createDate = createDate;
        }

/**
 *
 * @return
 * The lastUpdateUser
 */
        @JsonProperty("lastUpdateUser")
        public String getLastUpdateUser() {
            return lastUpdateUser;
        }

/**
 *
 * @param lastUpdateUser
 * The lastUpdateUser
 */
        @JsonProperty("lastUpdateUser")
        public void setLastUpdateUser(String lastUpdateUser) {
            this.lastUpdateUser = lastUpdateUser;
        }

/**
 *
 * @return
 * The lastUpdateDate
 */
        @JsonProperty("lastUpdateDate")
        public String getLastUpdateDate() {
            return lastUpdateDate;
        }

/**
 *
 * @param lastUpdateDate
 * The lastUpdateDate
 */
        @JsonProperty("lastUpdateDate")
        public void setLastUpdateDate(String lastUpdateDate) {
            this.lastUpdateDate = lastUpdateDate;
        }

/**
 *
 * @return
 * The notes
 */
        @JsonProperty("notes")
        public Object getNotes() {
            return notes;
        }

/**
 *
 * @param notes
 * The notes
 */
        @JsonProperty("notes")
        public void setNotes(Object notes) {
            this.notes = notes;
        }

/**
 *
 * @return
 * The moreThan50PercentFlag
 */
        @JsonProperty("moreThan50PercentFlag")
        public Object getMoreThan50PercentFlag() {
            return moreThan50PercentFlag;
        }

/**
 *
 * @param moreThan50PercentFlag
 * The moreThan50PercentFlag
 */
        @JsonProperty("moreThan50PercentFlag")
        public void setMoreThan50PercentFlag(Object moreThan50PercentFlag) {
            this.moreThan50PercentFlag = moreThan50PercentFlag;
        }

/**
 *
 * @return
 * The stowageRestriction
 */
        @JsonProperty("stowageRestriction")
        public Object getStowageRestriction() {
            return stowageRestriction;
        }

/**
 *
 * @param stowageRestriction
 * The stowageRestriction
 */
        @JsonProperty("stowageRestriction")
        public void setStowageRestriction(Object stowageRestriction) {
            this.stowageRestriction = stowageRestriction;
        }

/**
 *
 * @return
 * The explosivePowderWeightApplicable
 */
        @JsonProperty("explosivePowderWeightApplicable")
        public Object getExplosivePowderWeightApplicable() {
            return explosivePowderWeightApplicable;
        }

/**
 *
 * @param explosivePowderWeightApplicable
 * The explosivePowderWeightApplicable
 */
        @JsonProperty("explosivePowderWeightApplicable")
        public void setExplosivePowderWeightApplicable(Object explosivePowderWeightApplicable) {
            this.explosivePowderWeightApplicable = explosivePowderWeightApplicable;
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }

        /*@JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            return this.additionalProperties;
        }*/

        @JsonAnySetter
        public void setAdditionalProperty(String name, Object value) {
            this.additionalProperties.put(name, value);
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(commodityLineId).append(hazUniqueId).append(hazType).append(hazNumber).append(hazCommodityName).append(hazPrimaryClass).append(hazPrimaryClassName).append(hazSecondaryClass).append(hazTertiaryClass).append(hazEmergencyContactName).append(hazEmergencyContactPhone).append(hazSecondaryEmergencyContactName).append(hazSecondaryEmergencyContactPhone).append(hazPackageGroup).append(hazPieces).append(hazPiecesUomCode).append(hazWeight).append(hazWeightUomCode).append(hazFlashPoint).append(hazFlashPointUomCode).append(hazImdgCfrIndicator).append(hazLimitedQuantity).append(hazMarinePollutant).append(hazExplosivePowderWeight).append(hazExplosivePowderWeightUomCode).append(hazSpecialPermitNumber).append(isActive).append(createUser).append(createDate).append(lastUpdateUser).append(lastUpdateDate).append(notes).append(moreThan50PercentFlag).append(stowageRestriction).append(explosivePowderWeightApplicable).append(additionalProperties).toHashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if ((other instanceof HazardousCommodityLine) == false) {
                return false;
            }
            HazardousCommodityLine rhs = ((HazardousCommodityLine) other);
            return new EqualsBuilder().append(commodityLineId, rhs.commodityLineId).append(hazUniqueId, rhs.hazUniqueId).append(hazType, rhs.hazType).append(hazNumber, rhs.hazNumber).append(hazCommodityName, rhs.hazCommodityName).append(hazPrimaryClass, rhs.hazPrimaryClass).append(hazPrimaryClassName, rhs.hazPrimaryClassName).append(hazSecondaryClass, rhs.hazSecondaryClass).append(hazTertiaryClass, rhs.hazTertiaryClass).append(hazEmergencyContactName, rhs.hazEmergencyContactName).append(hazEmergencyContactPhone, rhs.hazEmergencyContactPhone).append(hazSecondaryEmergencyContactName, rhs.hazSecondaryEmergencyContactName).append(hazSecondaryEmergencyContactPhone, rhs.hazSecondaryEmergencyContactPhone).append(hazPackageGroup, rhs.hazPackageGroup).append(hazPieces, rhs.hazPieces).append(hazPiecesUomCode, rhs.hazPiecesUomCode).append(hazWeight, rhs.hazWeight).append(hazWeightUomCode, rhs.hazWeightUomCode).append(hazFlashPoint, rhs.hazFlashPoint).append(hazFlashPointUomCode, rhs.hazFlashPointUomCode).append(hazImdgCfrIndicator, rhs.hazImdgCfrIndicator).append(hazLimitedQuantity, rhs.hazLimitedQuantity).append(hazMarinePollutant, rhs.hazMarinePollutant).append(hazExplosivePowderWeight, rhs.hazExplosivePowderWeight).append(hazExplosivePowderWeightUomCode, rhs.hazExplosivePowderWeightUomCode).append(hazSpecialPermitNumber, rhs.hazSpecialPermitNumber).append(isActive, rhs.isActive).append(createUser, rhs.createUser).append(createDate, rhs.createDate).append(lastUpdateUser, rhs.lastUpdateUser).append(lastUpdateDate, rhs.lastUpdateDate).append(notes, rhs.notes).append(moreThan50PercentFlag, rhs.moreThan50PercentFlag).append(stowageRestriction, rhs.stowageRestriction).append(explosivePowderWeightApplicable, rhs.explosivePowderWeightApplicable).append(additionalProperties, rhs.additionalProperties).isEquals();
        }

    }

}
