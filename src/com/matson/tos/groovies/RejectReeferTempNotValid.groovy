/*
*  Change   Changer  Date       Desc
*  A1       GR       12/16/11   Updated Reefer Variable for navis case#89220
*  A2       GR       02/17/12   TOS2.1 : Updt Field unitFlexString07 to UfvFlexString07
*  A3       LC       07/20/12   Create Temp discrepancy report on Dray-In
*  A4       LC       04/16/13   Check for the Receive Export Ingate, copies booking req temp to unit req temp
*  A5       LC       09/25/13   Get the trans booking temp required
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.EquipRfrTypeEnum
import com.navis.argo.business.reference.Container
import com.navis.framework.metafields.Metafield
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.util.internationalization.ITranslationContext
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.internationalization.TranslationUtils
import com.navis.framework.util.message.MessageLevel
import com.navis.framework.util.unit.TemperatureUnit
import com.navis.framework.util.unit.UnitUtils
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.argo.business.api.GroovyApi
import com.navis.inventory.business.units.Unit;
import com.navis.framework.util.DateUtil;

/**
 * Validates and applies the reefer temperature entered in the gate screen. The reefer temperature is captured in a unit flex field.
 * The validation rules will result in an error if any of the conditions below is true:
 * 1) Non-reefer equipment and reefer temperature is entered.
 * 2) Reefer equipment and reefer temperature is not entered.
 * 3) Reefer equipment and reefer temperature is not "AMB" or a numerical value.
 */
public class RejectReeferTempNotValid {

    public static String BEAN_ID = "applyReeferTempToTranTempSetting"
    // {0} translated field name
    // {1} equipment type
    public static PropertyKey REEFER_TEMP_NOT_ALLOWED = PropertyKeyFactory.valueOf("gate.reefer_temp_not_allowed")
    // {0} translated field name
    // {1} equipment type
    public static PropertyKey REEFER_TEMP_REQUIRED = PropertyKeyFactory.valueOf("gate.reefer_temp_required")
    // {0} translated field name
    public static PropertyKey REEFER_TEMP_INVALID = PropertyKeyFactory.valueOf("gate.reefer_temp_invalid")

    public static String REEFER_TEMP_FLEX_FIELD = "ufvFlexString07" //A2
    public static String VALID_REEFER_TEMP_STRING = "AMB"

    public static String EMAIL_FROM = "1aktosdevteam@matson.com"
    public static String EMAIL_TO = "1aktosdevteam@matson.com"
    public static String SUBJECT = "Dray-In Temp. Discrepancy"

    public void execute(TransactionAndVisitHolder dao, api) {

        def gvyBaseClass = new GroovyInjectionBase()

        TruckTransaction tran = dao.tran
        Container container = tran.tranContainer

        if (container == null) {
            return
        }
        EquipRfrTypeEnum rfrType = container.eqRfrType
        String value = tran.getFieldString(getFlexFieldMetafieldId(REEFER_TEMP_FLEX_FIELD))

        String fieldName = getFlexFieldTranslatedName(REEFER_TEMP_FLEX_FIELD)
        String eqTypeId = container.eqEquipType.eqtypId
        String ctrNbr = tran.getFieldValue("tranCtrNbr");
        String dest = tran.getFieldValue("tranDestination");

        // Non-reefer equipment types are not allowed to have a value in the "Reefer Temp" flex field.
        if (EquipRfrTypeEnum.NON_RFR.equals(rfrType) && StringUtils.isNotEmpty(value)) {
            RoadBizUtil.appendMessage(MessageLevel.SEVERE, REEFER_TEMP_NOT_ALLOWED, fieldName, eqTypeId)
        }
        // Reefer equipment types must have a value in the "Reefer Temp" flex field.
        if (!EquipRfrTypeEnum.NON_RFR.equals(rfrType) && StringUtils.isEmpty(value)) {
            RoadBizUtil.appendMessage(MessageLevel.SEVERE, REEFER_TEMP_REQUIRED, fieldName, eqTypeId)
        }
        // Non-reefer equipment type and empty temp value, ignore.
        if (StringUtils.isEmpty(value)) {
            return
        }
        // Reefer equipment type and non-empty temp value.
        double reeferTempC = 0.0
        double reeferTempF = 0.0
        try {
            GroovyApi gvyApi = new GroovyApi();
            reeferTempC = UnitUtils.convertTo(value, TemperatureUnit.C, TemperatureUnit.F)
            reeferTempF = celsiusToFahrenheit(reeferTempC);
            //gvyApi.logWarn("reeferTempC:"+reeferTempC);
            //gvyApi.logWarn("reeferTempF:"+reeferTempF);
            // Copy the temp value entered to the tranTempSetting field on the transaction so that CreateContainerVisit will create a ReeferRecord
            // with this value in the rfrecSetPointTmp field.
            TranSubTypeEnum tranType = tran.getTranSubType(); //A1
            //For DrayIn Set TempRequired for RE set TempSetting
            if(TranSubTypeEnum.RI.equals(tranType)){
                Unit unit = tran.getTranUnit();
                //gets the unit temp to check for temp discrepancy
                if (unit != null && unit.getUnitGoods() != null && unit.getUnitGoods().getGdsReeferRqmnts() != null) {
                    Double tempRequiredC = unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempRequiredC();
                    double tempRequiredF = celsiusToFahrenheit(tempRequiredC);
                    def blNbr = unit.getFieldValue("unitGoods.gdsBlNbr")
                    int comparisonVal = 0;
                    if (tempRequiredF != null && (tempRequiredF.compareTo(reeferTempF)!= 0)) {
                        gvyApi.logWarn("Sending email to notify discrepancy in temperature");
                        def sitTemp = tempRequiredF != null ? tempRequiredF : "--"
                        def gateTemp = reeferTempF != null ? reeferTempF : "--"
                        String body = generateReport(ctrNbr, blNbr, gateTemp, sitTemp, dest, "Gate");
                        def gvyEmailSender = gvyBaseClass.getGroovyClassInstance("EmailSender");
//Moved to UNIT_DRAY_IN General Notice
//gvyEmailSender.custSendEmail(EMAIL_TO, SUBJECT, body)
                    }
                }
                tran.tranTempRequired = reeferTempC
            }else{
                tran.tranTempSetting = reeferTempC

                if(TranSubTypeEnum.RE.equals(tranType)){
                    //for RE - copy unit's booking temp required
                    def tranBooking;
                    def tranBookingTemp;
                    if(tran != null && tran.getTranEqoItem()!=null)  {
                        tranBooking = tran.getTranEqoNbr();  //get the booking number at the ingate
                        gvyApi.logWarn("Booking Number:"+tranBooking);
                        tranBookingTemp = tran.getTranEqoItem().getEqoiTempRequired(); //get the temp req from booking
                        gvyApi.logWarn("tranTempRequired---->"+tranBookingTemp);
                    }
                    tran.tranTempRequired =  tranBookingTemp;

                    gvyApi.logWarn("Copied from booking temp to tran.tranTempRequired:"+tranBookingTemp);
                }

            }
        } catch (NumberFormatException e) {
            if (!VALID_REEFER_TEMP_STRING.equals(value)) {
                RoadBizUtil.appendMessage(MessageLevel.SEVERE, REEFER_TEMP_INVALID, fieldName)
            }
        }
    }

    // Returns the MetafieldId of the flex field on the truck transaction.
    private MetafieldId getFlexFieldMetafieldId(String idString) {
        MetafieldIdFactory.valueOf("tran${idString[0].toUpperCase()}${idString[1..-1]}")
    }

    // Returns the user-friendly name for the flex field.
    private String getFlexFieldTranslatedName(String idString) {
        MetafieldId fieldId = MetafieldIdFactory.valueOf(idString)
        ITranslationContext translator = TranslationUtils.getTranslationContext(ContextHelper.getThreadUserContext())
        Metafield metafield = translator.getIMetafieldDictionary().findMetafield(fieldId)

        return translator.getMessageTranslator().getMessage(metafield.getLongLabelKey())
    }

    public double fahrenheitToCelsius(Double fahrenheit)
    {
        double fahr = (fahrenheit - 32) * 5/9;
        double result = new BigDecimal(""+fahr).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
        return result;
    }

    public double celsiusToFahrenheit(Double celsius)
    {
        double fahr = (celsius * 9/5) + 32;
        double result = new BigDecimal(""+fahr).setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
        return result;
    }

    public String generateReport(String ctrNbr, String blNbr, Object gateTemp, Object sitTemp, String dest, String noticeFrom) {
        def timezone = ContextHelper.getThreadUserTimezone();
        def eventTime = DateUtil.convertDateToLocalTime(new Date(), timezone);
        StringBuffer header = new StringBuffer();
        header.append("    Date:"+eventTime);
        header.append("\n\n\n                       MATSON TERMINALS - CONTAINER YARD     ");
        header.append("\n\n\n    From    : "+noticeFrom);
        header.append("\n    Subject : Dray-In Temperature Discrepancy");
        header.append("\n\n\n    Container: "+ctrNbr+"   Booking # : "+blNbr+"   Gate Temp: "+gateTemp+"   SIT Temp: "+sitTemp+"   Port: "+dest);
        header.append("\n\n\n\n");
        return header.toString();
    }


}
