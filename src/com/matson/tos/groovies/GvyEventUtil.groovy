/*
* srno  doer  date      desc
* A1    GR    04/07/10  Added suppProc check to process updates to DAS
* A2    GR    06/16/10  Added NUll to event changed field Map Object
* A3    GR    08/23/10  Removed PrintStatement
* A4    GR    09/15/10  Added date formattter method (Gems change)
* A5    GR    11/04/10  DAS : Suppress DTA,DTD before NVI TT#9776
* A6    GR    10/29/10  Gems: NV/NLT Release Hold Directly to Gems
* A7    GR    12/07/10  Added Date Formatting method
* A8    GR    12/16/10  Suppress Dollar sign units from posting Transaction.
* A9    GR    12/17/10  Added Equipment to unit conversion
* A10   GR    05/09/11  Added Method to Convert timezone
* A11   GR    07/18/11  Remove MQ msg from Acets Filter Method
* A12   GR    11/18/11  -snx- Filter snxmailbox execution for cargo status report
              11/29/11  Fix for Newves holds
* A13   GR    12/23/11  Added newves check
* A14   LC    10/15/13 Adding getPrevEvent to find most recent event
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.atoms.ScopeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Facility
import com.navis.argo.business.model.N4EntityScoper
import com.navis.argo.portal.context.ArgoUserContextProvider
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.context.IUserContextProvider
import com.navis.framework.portal.context.PortalApplicationContext
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.scope.ScopeCoordinates
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.ServicesField;
import com.navis.services.business.event.Event;
import com.navis.services.business.event.EventFieldChange;
import java.util.TimeZone;
import com.navis.services.business.api.EventManager
import com.navis.services.business.rules.EventType
import com.navis.framework.business.Roastery
import com.navis.argo.business.atoms.EventEnum;
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdFactory;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.argo.business.reference.Shipper
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.services.business.event.GroovyEvent

import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.api.UnitFinder;
import com.navis.framework.business.Roastery;
import com.navis.argo.business.reference.Equipment
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.EquipmentState
import com.navis.argo.ContextHelper
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.navis.argo.business.api.GroovyApi;
import com.navis.argo.business.api.ServicesManager;


// Event Utils using metafieldId and not Reportable Entity names

// gdsConsigneeAsString for Consignee name

public class GvyEventUtil extends GroovyInjectionBase {

    /*
     * Method get the Previous value of the Field
    */

    public String getPreviousPropertyAsString(Object event, String metafieldId) {
        def eventBase = event.getEvent();
        def change = eventBase.getEvntFieldChanges();
        if (change == null) return null;
        Iterator i1 = change.iterator();
        while (i1.hasNext()) {
            def efc = (EventFieldChange) i1.next();

            if (metafieldId.equalsIgnoreCase(efc.getEvntfcMetafieldId())) {
                return efc.getPrevVal();
            }
        }

        return null;
    }

    /*
     * Method get the Previous value of the Field
    */

    public Object getPreviousProperty(Object event, String metafieldId) {
        def eventBase = event.getEvent();
        def change = eventBase.getEvntFieldChanges();
        if (change == null) return null;
        Iterator i1 = change.iterator();
        while (i1.hasNext()) {
            def efc = (EventFieldChange) i1.next();
            //log("MetaField="+efc.getEvntfcMetafieldId()+" "+efc.properties);
            //println("MetaField="+efc.getEvntfcMetafieldId()+" "+efc.properties);
            if (metafieldId.equalsIgnoreCase(efc.getEvntfcMetafieldId())) {
                return efc.getPrevVal();
            }
        }

        return null;
    }

/*
 * Method return a status flag on fields updated
 */

    public boolean wasFieldChanged(Object event, String metafieldId) {
        def eventBase = event.getEvent();
        def change = eventBase.getEvntFieldChanges();
        if (change == null) return null;
        Iterator i1 = change.iterator();
        while (i1.hasNext()) {
            def efc = (EventFieldChange) i1.next();
            if (metafieldId.equalsIgnoreCase(efc.getEvntfcMetafieldId())) {
                return true;
            }
        }

        return false;
    }

    /*
     * Method Formats the event date based on the timezone
     */

    public static String formatDate(java.util.Date date, TimeZone zone) {

        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MM/dd/yyyy");
        dateFormat.setTimeZone(zone);
        if (date != null) {
            def fmtDate = dateFormat.format(date);
            return fmtDate
        }
        return '';
    }

    /*
     * Method Formats the event date based on the timezone
     */

    public static String formatTime(java.util.Date date, TimeZone zone) {
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm:ss");
        timeFormat.setTimeZone(zone);
        if (date != null) {
            def fmtTime = timeFormat.format(date);
            return fmtTime
        }
        return '';
    }

//Method returns date format
    public String dateFormat(java.util.Date date, String dtFormat) {
        if (date == null) {
            return date
        }
        DateFormat dateFormat = new SimpleDateFormat(dtFormat);
        String dtformat = dateFormat.format(date);
        return dtformat;
    }

    /*
     * Method Formats the event date based on the timezone
     */

    public static String formatDateTime(java.util.Date date, TimeZone zone) {
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("MM/dd/yyyy @ HH:mm");
        timeFormat.setTimeZone(zone);
        if (date != null) {
            def fmtTime = timeFormat.format(date);
            return fmtTime
        }
        return '';
    }

//A7
    public static String formatDateTime(java.util.Date date, TimeZone zone, String formatString) {
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat(formatString);
        timeFormat.setTimeZone(zone);
        if (date != null) {
            def fmtTime = timeFormat.format(date);
            return fmtTime
        }
        return '';
    }

    /*
    * Method Converts Date to Julian Date ( Day of Year)
    */

    public static String convertToJulianDate(String date) {
        def year = date.substring(0, 4);
        def month = date.substring(5, 7);
        def day = date.substring(8, 10);
        def newGregCal = new GregorianCalendar(Integer.parseInt(year), Integer.parseInt(month) - 1, Integer.parseInt(day))
        def lngTime = newGregCal.getTimeInMillis()
        def today = new Date(lngTime)
        def julianDate = new java.text.SimpleDateFormat("yyDDD")
        def dayOfYear = julianDate.format(today)
        return dayOfYear;
    }

    /*
     * Method Converts Date to Julian Date ( Day of Year)
     */

    public String convertToJulianDate(java.util.Date date) {
        String julianDoy = "";
        if (date == null) {
            return julianDoy;
        }
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(date.getTime());
            int doy = cal.get(Calendar.DAY_OF_YEAR);
            String dayofyear = Integer.toString(doy);
            dayofyear = dayofyear.length() == 1 ? "00" + dayofyear : (dayofyear.length() == 2 ? "0" + dayofyear : dayofyear);
            String year = "" + cal.get(Calendar.YEAR);
            julianDoy = year.substring(2) + dayofyear;
            //println("Day of Year ::"+julianDoy);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return julianDoy;
    }

    /* Method Returns a map object for Event Fields Changed
     * Map contains Field name as Key and Field Object as the value
     * The Field Object has Previous Value and Current Value
    */

    public Map eventFieldChangedValues(Object event, Object gvyBaseClass) {
        Map mapFields = new HashMap()
        def newValue = ''
        def prevValue = ''
        try {
            def gvyEventObj = event.getEvent()
            Set changes = gvyEventObj.getFieldChanges()
            Iterator iterator = changes.iterator();
            while (iterator.hasNext()) {
                EventFieldChange fieldChange = (EventFieldChange) iterator.next();
                String fieldName = fieldChange.getMetafieldId()
                MetafieldId mfId = MetafieldIdFactory.valueOf(fieldName);
                newValue = ArgoUtils.getPropertyValueAsUiString(mfId, fieldChange.getEvntfcNewVal());
                newValue = newValue != null ? newValue : null
                prevValue = ArgoUtils.getPropertyValueAsUiString(mfId, fieldChange.getEvntfcPrevVal());
                prevValue = prevValue != null ? prevValue : null

                def gvyEventField = gvyBaseClass.getGroovyClassInstance("GvyCmisEventField");
                gvyEventField.setFieldName(fieldName);
                gvyEventField.setpreviousValue(prevValue);
                gvyEventField.setCurrentValue(newValue);
                mapFields.put(fieldName, gvyEventField)
                //println('fieldName:'+fieldName+'   mfId :'+mfId+'     newValue:'+newValue+'     prevValue:'+prevValue )

            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return mapFields;
    }

    /*
    * Method reads the Updated field values from the object
    * and stores the values in a string buffer for processing
    */

    public ArrayList readEventChangedFields(Object mapEvntField) {
        def processFlag = false;
        def arrList = new ArrayList();
        try {
            Iterator it = mapEvntField.keySet().iterator();
            while (it.hasNext()) {
                def aField = it.next();
                def aEvntFieldObj = mapEvntField.get(aField)

                //Fetch Updated Field Values
                def fieldname = aEvntFieldObj.getFieldName()
                def previousValue = aEvntFieldObj.getpreviousValue()
                previousValue = previousValue != null ? previousValue : ''
                def currentValue = aEvntFieldObj.getCurrentValue()
                currentValue = currentValue != null ? currentValue : ''

                /* Append updated Field Name and Prev Value.If previous value does not equal to
               Current value then append as non build in events register fld in event history iwth no change */
                if (!previousValue.equals(currentValue)) {
                    arrList.add(fieldname)
                }
                // println('fieldname ::'+fieldname+'  previousValue::'+previousValue+'   currentValue::'+currentValue)

            }//While Ends
        } catch (Exception e) {
            e.printStackTrace()
        }
        return arrList;
    }//Method readEventChangedFields Ends

    /* Method Gets the Last Snx Events Notes on the Unit
     * The event notes  identifies the lasttransaction  process
     */

    public String getMostRecentSnxEventNotes(Object event) {
        def eventNotes = ''
        try {
            def unit = event.getEntity()
            EventManager em = (EventManager) Roastery.getBean("eventManager");
            EventType snxEvnt = EventType.resolveIEventType(EventEnum.UNIT_SNX_UPDATE);
            if (snxEvnt != null) {
                def snxEventObj = em.getMostRecentEventByType(snxEvnt, unit)
                eventNotes = snxEventObj != null ? snxEventObj.getEventNote() : ''
                eventNotes = eventNotes != null ? eventNotes : ''
            }
        } catch (Exception e) {
            e.printStackTrace()
        }

        return eventNotes
    }

/*A14
* Method Gets the Last EventId on the Unit
* The event id  identifies the lasttransaction  process
*/

    public String getPrevEvent(String inEventId, Object unit) {

        String eventTypeId = '';
        try {
            EventManager em = (EventManager) Roastery.getBean("eventManager");
            EventType eventType = EventType.findEventType(inEventId);
            if (eventType != null) {
                Event eventObj = em.getMostRecentEventByType(eventType, unit);
                eventTypeId = eventObj != null ? eventObj.getEventTypeId() : '';
                eventTypeId = eventTypeId != null ? eventTypeId : '';
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return eventTypeId;
    }

/*
* Pass Cmis Feed only for Acets Msg 1] snx:Acets
*/

    public boolean verfiyCmisFeedProcessing(Object event) {
        def process = true
        try {
            def eventObj = event.getEvent()
            def doer = eventObj.getEvntAppliedBy();
            def evntNotes = eventObj.getEventNote();
            evntNotes = evntNotes != null ? evntNotes : ''
            def unit = event.getEntity()
            def suppProcFlag = SuppmentalReportCheck(event)
            def eventType = event.event.getEventTypeId()
            println("Event : " + eventType)
            //A9
            if (unit instanceof EquipmentState) {
                unit = getGroovyClassInstance('GvyCmisDataProcessor').getUnitFromEquipment(unit)
            }
            def unitNbr = unit.unitId
            if (unitNbr.startsWith('$')) {
                println("DoNot Create Msg :" + unitNbr) //A12
                return false
            } else if (UnitVisitStateEnum.DEPARTED.equals(unit.getFieldValue("UnitVisitState")) && !("RESPOT".equalsIgnoreCase(eventType))) {
                //println("Unit Master State is Departed : No Cmis Feed")
                //Dont pass Cmis Message for Master State Departed unit
                return false;
            }

            if (suppProcFlag) {
                return true;
            } else if ((doer.equals('snx:ACETS') || doer.startsWith('ACETS:')) && StifNewVesCmisFeedCheck(event)) {
                return true;
            }

            if (evntNotes.contains('Acets HLP/HLR') || ((doer.contains('jms') || doer.contains('-snx-')) && (eventType.endsWith("_HOLD") || eventType.endsWith("_RELEASE")))) {
                //A7
                return true;
            } else if (doer.contains('jms') || doer.equals('-edi-') || doer.contains('-snx-')) //A12
            {
                // Dont process cmis Feed
                return false;
            }
            process = StifNewVesCmisFeedCheck(event)
        }
        catch (Exception e) {
            e.printStackTrace()
        }
        return process;
    }


    public boolean verfiyReportSnxProcessing(Object event) {
        def process = true
        try {
            def eventObj = event.getEvent()
            def doer = eventObj.getEvntAppliedBy();
            def suppProcFlag = SuppmentalReportCheck(event)
            def evntNotes = eventObj.getEventNote();
            evntNotes = evntNotes != null ? evntNotes : ''
            def unit = event.getEntity()

            def unitNbr = event.entity.unitId
            if (unitNbr.startsWith('$')) {
                println("DoNot Create CargoStatus Msg :" + unitNbr)
                return false
            } else if (UnitVisitStateEnum.DEPARTED.equals(unit.getFieldValue("UnitVisitState"))) {
                return false;
            }
            if (doer.contains('-snx-')) { //A7
                return true;
            } else if (doer.equals('-jms-') || doer.contains('jms') || doer.contains('-snx-')) //A12
            {
                return false;
            } else if (doer.equals('snx:ACETS') || doer.contains('ACETS') || doer.contains('acets')) {
                return true;
            } else if (evntNotes.startsWith('ACETS') || evntNotes.startsWith('Acets')) {
                return true;
            } else {
                return true;
            }

        }
        catch (Exception e) {
            e.printStackTrace()
        }
        return process;
    }

    //XPS Actions that do not have to process CMIS Feed
    public boolean nonProcessingXpsAction(Object event, String xpsActionMsg) {
        def executeXpsEvent = true;
        try {
            def gvyEventObj = event.getEvent()
            def doer = gvyEventObj.getEvntAppliedBy()
            if (doer != null && doer.startsWith("xps")) {
                def xpsDoer = doer.split(':');
                if (xpsDoer != null) {
                    def xpsActionlength = xpsDoer.length
                    def xpsAction = xpsDoer[xpsActionlength - 1]
                    if (xpsAction != null && xpsAction.equals(xpsActionMsg)) {
                        executeXpsEvent = false
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return executeXpsEvent
    }

    /*
     * Pass Event Cmis Feed only if the newves is executed on the Unit
     * Pass messages after UNIT_DISCH event even if last snx is Stowplan
     * If last snx execution is Stif then dont pass Event & ACETS cmis feed
     */

    public boolean StifNewVesCmisFeedCheck(Object event) {
        def passFeed = true
        def snxEvntNotes = getMostRecentSnxEventNotes(event)
        def dischEvnt = event.getMostRecentEvent("UNIT_DISCH");
        if (snxEvntNotes.startsWith('Stowplan Data') && dischEvnt != null) {
            passFeed = true;
        } else if (snxEvntNotes.startsWith('Stowplan Data')) {
            passFeed = false;
        }
        return passFeed
    }

    /*
     * Pass Event Cmis Feed only if the newves is executed on the Unit
     * If last snx execution is Stif then dont pass Event & ACETS cmis feed
     */

    public boolean SuppmentalReportCheck(Object event) {
        def passFeed = false
        def snxEvntNotes = getMostRecentSnxEventNotes(event)
        if (snxEvntNotes.startsWith('Supplemental')) {
            passFeed = true;
        }
        return passFeed
    }

    /*
      * To Identify if last UNIT_SNX_UPDATE was a Newves Update
      */

    public boolean newVesCheck(Object event) {
        def passFeed = false
        def snxEvntNotes = getMostRecentSnxEventNotes(event)
        if (snxEvntNotes.contains('NewVes')) {
            passFeed = true;
        }
        return passFeed
    }

    /*
   * Method checks last event processing time interval to call thread sleep on current processing thread
   */

    public boolean holdEventProcessing(Object event, String eventType, int sec) {
        def secInterval = sec * 1000
        Event eventObj = event.getEvent()
        def currEvntTime = eventObj.getEvntAppliedDate()
        def eventId = eventObj.getEventTypeId()
        def currEvtTime = currEvntTime != null ? currEvntTime.getTime() : null

        def mstEvent = event.getMostRecentEvent(eventType)
        def mstEvntObj = mstEvent != null ? mstEvent.getEvent() : null
        def mstEvntTime = mstEvntObj != null ? mstEvntObj.getEvntAppliedDate() : null
        if (currEvtTime != null && mstEvntTime != null) {
            def mstEvtTime = mstEvntTime.getTime()

            def evntTimeDiff = currEvtTime - mstEvtTime
            //  println('evntTimeDiff::'+evntTimeDiff+'currEvtTime ::'+currEvtTime+'  mstEvtTime::'+mstEvtTime+'  eventId11  ::'+eventId)
            if (evntTimeDiff < secInterval && evntTimeDiff > 0) {
                // println("Evnt Time Difference is Positive : "+evntTimeDiff)
                return true
            } else if (evntTimeDiff < 0) {
                // println("Evnt Time Difference is Negative : "+evntTimeDiff)
                return true;
            } else {
                // println("Evnt Time Greater : "+evntTimeDiff)
                return false;
            }
        } else {
            return false;
        }
    }

    /*
  * Passes only Acets Events Back to Cmis Feed
  */

    public boolean acetsMesssageFilter(Object event) {
        boolean acetsMsgFlag = false;

        try {
            def gvyEventObj = event.getEvent()
            def eventId = gvyEventObj.getEventTypeId()
            def doer = gvyEventObj.getEvntAppliedBy();
            def eventNotes = gvyEventObj.getEventNote()
            eventNotes = eventNotes != null ? eventNotes : ''
            //A11 - Remove MQ msg from thei Filter sop we can post to Gems
            //String acetsEvents = "BDA,BDB,LNK,ULK,UPU";
            String acetsEvents = "BDA,UPU";
            boolean acetsEvnt = acetsEvents.contains(eventId);

            CharSequence acetsMsg = "ACETS";
            boolean acetsRecorder = doer.contains(acetsMsg);
            //boolean evntNotes = eventNotes.contains(acetsMsg);

            if (acetsEvnt && acetsRecorder) {
                acetsMsgFlag = true;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return acetsMsgFlag;
    }

    public boolean findActiveUnit(Object unit) {
        def isActiveUnit = null;
        try {
            def ctrId = unit.getUnitId()
            def unitFinder = (UnitFinder) Roastery.getBean("unitFinder");
            def complex = ContextHelper.getThreadComplex();
            def inEquipment = Equipment.loadEquipment(ctrId);
            def inUnit = unitFinder.findActiveUnit(complex, inEquipment)
            if (inUnit == null) {
                isActiveUnit = false
            } else {
                isActiveUnit = true
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        isActiveUnit
    }

    //Method formats date to yyyy-MM-dd
    public static String formatDate(String str_date) {
        java.text.DateFormat formatter = null; Date date = null; String finalDate = null;
        try {
            formatter = new java.text.SimpleDateFormat("yyyy-MMM-dd");
            date = (Date) formatter.parse(str_date);
            def reqformat = new java.text.SimpleDateFormat("yyyy-MM-dd");
            finalDate = reqformat.format(date);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return finalDate;
    }

    //A7 - Convert Date String from One Format to another
    public static String formatDate(String str_date, String fromDtFormat, String toDtFormat) {
        java.text.DateFormat formatter = null; Date date = null; String finalDate = null;
        try {
            formatter = new java.text.SimpleDateFormat(fromDtFormat);
            date = (Date) formatter.parse(str_date);
            def reqformat = new java.text.SimpleDateFormat(toDtFormat);
            finalDate = reqformat.format(date);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return finalDate;
    }


    public static String convertTimeZone(String dateStr, String fromTimeZone, String toTimeZone, String dateInFormat) {
        String convertDateStr = null;
        try {
            DateFormat formatter = new SimpleDateFormat(dateInFormat);
            formatter.setTimeZone(TimeZone.getTimeZone(fromTimeZone));
            Date date = formatter.parse(dateStr);
            //println("From Zone :"+formatter.format(date));
            formatter.setTimeZone(TimeZone.getTimeZone(toTimeZone));
            //println("To Zone :"+formatter.format(date));
            convertDateStr = formatter.format(date);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return convertDateStr;

    }

    public void recordUnitDischComplete(Unit inUnit, GroovyApi inApi) {
        recordUnitDischComplete(inUnit, inApi, null);
    }

    public void recordUnitDischComplete(Unit inUnit, GroovyApi inApi, Facility inFacility) {

        //final UserContext userContext = ContextHelper.getThreadUserContext();
/*        Facility facility = inFacilityId != null ? Facility.findFacility(inFacilityId) : ContextHelper.getThreadFacility();
        final UserContext uc = getNewUserContext(facility);
        final Unit inExistingUnit = inUnit;
        final Facility oldFacility = ContextHelper.getThreadFacility();
        inApi.sendEmail("gbabu@matson.com","gbabu@matson.com","User context from discharge ",uc.getScopeCoordinate().toString());

        PersistenceTemplate template = new PersistenceTemplate(uc);
        MessageCollector mc = template.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {*/
        try {
            ServicesManager srvcMgr = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);

            String inEventId = "UNIT_DISCH_COMPLETE";
            EventType eventType = EventType.findEventType(inEventId);
            srvcMgr.recordEvent(eventType, inEventId, null, null, inUnit);
            if (inFacility != null) {
                Event event = (Event) srvcMgr.getMostRecentEvent(eventType, inUnit);
                if (event!= null){
                    event.setEvntFacility(inFacility);
                    event.setEvntYard(inFacility.getActiveYard());
                }
            }
        } catch (Exception e) {
        }
    }
    //});

/*
* Suppress DTA,DTD Before NVI  //A6
*/

    public boolean suppressDasMsgBeforeNvi(Object event) {
        def process = false
        try {
            def eventObj = event.getEvent()
            def doer = eventObj.getEvntAppliedBy();
            def stgEvntDatTime = eventObj.getEvntAppliedDate()
            def stgEvntTime = stgEvntDatTime.getTime()
            def evntNotes = eventObj.getEventNote();
            evntNotes = evntNotes != null ? evntNotes : ''
            def unit = event.getEntity()
            def eventType = event.event.getEventTypeId()
            def secInterval = 240 * 1000

            def blNbr = unit.getUnitGoods().getGdsBlNbr()
            println("blNbr in suppressDasMsgBeforeNvi  " + blNbr)

            if ('-system-'.equals(doer)) {
                def snxEvent = event.getMostRecentEvent('UNIT_SNX_UPDATE')
                if (snxEvent == null) {
                    process = true;
                }
                def snxEvntNotes = snxEvent.event.eventNote != null ? snxEvent.event.eventNote : ''
                if (snxEvntNotes.startsWith('Stowplan') || (blNbr != null && blNbr.contains('DO NOT EDIT'))) {
                    process = false;
                } else if (snxEvntNotes.startsWith('Supplemental')) {
                    process = true;
                } else if (snxEvntNotes.startsWith('NewVes')) {
                    def snxDateTime = snxEvent.event.getEvntAppliedDate();
                    def snxEvtTime = snxDateTime.getTime()
                    //println("stgEvntDatTime="+stgEvntDatTime+"  snxDateTime="+snxDateTime+"   stgEvntTime="+stgEvntTime+"    snxEvtTime="+snxEvtTime)
                    def evntTimeDiff = snxEvtTime - stgEvntTime
                    if (evntTimeDiff > secInterval) {
                        process = true;
                    }
                }//End Else If
            } else {
                process = true;
            }
        }
        catch (Exception e) {
            e.printStackTrace()
        }
        return process;
    }

    public UserContext getNewUserContext(Facility inFacility) {
        ScopeCoordinates scopeCoordinates = _scoper.getScopeCoordinates(ScopeEnum.YARD, inFacility.getActiveYard().getYrdGkey());
        UserContext uc = ContextHelper.getThreadUserContext();
        UserContext newUserContext = _contextProvider.createUserContext(uc.getUserKey(), uc.getUserId(), scopeCoordinates);
        //set security session id for the user
        newUserContext.setSecuritySessionId(uc.getSecuritySessionId());
        return newUserContext;
    }

    private N4EntityScoper _scoper = (N4EntityScoper) Roastery.getBeanFactory().getBean(N4EntityScoper.BEAN_ID);
    private ArgoUserContextProvider _contextProvider = (ArgoUserContextProvider) PortalApplicationContext.getBean(IUserContextProvider.BEAN_ID);
}
