import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.framework.business.Roastery
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizWarning
import com.navis.framework.util.DateUtil
import com.navis.framework.util.LogUtils
import com.navis.framework.util.message.MessageCollectorUtils
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.ReeferRqmnts
import com.navis.inventory.business.units.Unit
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * This is a Pre-Deployed Groovy Plug-in which updates the units with extended times specified in this groovy.
 * @author <a href="mail to:rkasindula@navis.com"> Ramanjaneyulu</a> Dec 12, 2012
 */
public class ExtendedReeferMonitorTimesGrv extends GroovyApi {

    private UnitFinder _unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
    private TimeZone _tz = ContextHelper.getThreadUserTimezone();
    //Please add or edit below default extended times.Use "HH:MM" format to add new times
    private List<String> _extendedTimes = new ArrayList<String>();

    {
        _extendedTimes.add("01:00");
        _extendedTimes.add("05:00");
        _extendedTimes.add("09:00");
        _extendedTimes.add("13:00");
        _extendedTimes.add("17:00");
        _extendedTimes.add("21:00");
    }

    public void execute(Map parameters) {

        LogUtils.setLogLevel(ExtendedReeferMonitorTimesGrv.class, Level.INFO);
        //validate extended Times
        validateExtendedTimes(_extendedTimes);
        //return if there are any fatal errors
        if (MessageCollectorUtils.getMessageCollector().hasError()) {
            return;
        }

        //gets the date object for extended times
        Date[] extendDates = getExtendedDateForTimes(_extendedTimes);

        /* Find Reefer units in yard which need extended monitor times
         * Note:  If enhanced selection criteria is desired (for example
         * all reefers in yard with a certain commodity code) then the result
         * from unitFinder would need further filtering before propagating
         * new set of applicable monitor times.
         */
        Serializable[] unitGkeys = _unitFinder.findRfrUnitsInYardNeedingExtMonitors();
        for (Serializable unitGkey : unitGkeys) {

            Unit unit = Unit.hydrate(unitGkey);
            //if groovy extended times are below 5 then update as is to unit
            if (extendDates.length < 5) {
                setReeferMonitorTimes(unit, Arrays.asList(extendDates));
            } else {
                //gets unit's reefer last recording time
                Date lastMonitorTime = unit.getUnitLastReeferRecordDate();
                //if last temp recording time  is null,  then get Reefer connected time.
                if (lastMonitorTime == null) {
                    lastMonitorTime = unit.getUnitPowerRqstTime();
                }
                //if both last temp recording time and reefer connected time are null then lastMonitorTime = current time
                if (lastMonitorTime == null) {
                    lastMonitorTime = Calendar.getInstance(_tz).getTime();
                }
                //determine matching four times from extended dates for the last record time
                List<Date> monitorDatesToUpdate = calculateMonitorDatesForLastRecordTime(lastMonitorTime, Arrays.asList(extendDates));
                //sets matching 4 monitor times to unit
                setReeferMonitorTimes(unit, monitorDatesToUpdate);
            }
        }
    }

    private List<Date> calculateMonitorDatesForLastRecordTime(Date inLastRecordTime, List<Date> inExtMonitorDates) {

        List<Date> monitorDates = new ArrayList<Date>(4);

        // If inLastRecordTime is not today then return 4 extended monitor times
        Calendar cal = Calendar.getInstance(_tz);
        cal.setTime(inLastRecordTime);
        if (!DateUtil.isSameDay(inLastRecordTime, Calendar.getInstance(_tz).getTime(), _tz)) {
            return inExtMonitorDates.subList(0, 4);
        }

        //If inLastRecordTime is on today then calculate the matching index from extended dates
        int monitoredIndex = getMonitoredIndex(inLastRecordTime, inExtMonitorDates);
        int monitorDatesSize = inExtMonitorDates.size();

        //IfMonitoredIndex == inExtMonitorDates.size() or inExtMonitorDates.size()-1 it is meant that there are no monitor
        // times left out so create first monitor times for tomorrow
        if (monitoredIndex == monitorDatesSize) {
            return inExtMonitorDates.subList(0, 4);
        }

        int j = 0;
        for (int i = monitoredIndex; i < monitorDatesSize && i <= monitoredIndex + 4; i++) {
            monitorDates.add(inExtMonitorDates.get(i));
            j++;
        }


        for (int k = 0; k + j < 4; k++) {
            monitorDates.add(inExtMonitorDates.get(k));
        }

        return monitorDates;
    }

    private int getMonitoredIndex(Date inLastRecordTime, List<Date> inExtMonitorDates) {

        int extMonitorTimesSize = inExtMonitorDates.size();
        long lastRecordTimeSecs = inLastRecordTime.getTime();
        for (int i = 0; i < extMonitorTimesSize; i++) {
            long extMonitorTimeInSecs = inExtMonitorDates.get(i).getTime();
            if (lastRecordTimeSecs < extMonitorTimeInSecs) {
                return i;
            }
        }
        return extMonitorTimesSize;
    }

    private void setReeferMonitorTimes(Unit inUnit, List<Date> inMonitorDates) {
        if (inUnit.getUnitGoods() == null) {
            MessageCollectorUtils.getMessageCollector().appendMessage(BizWarning
                    .create(ArgoPropertyKeys.INFO, null,
                    "Propagate can't be done. Either unit goods for  Unit[" + inUnit.getUnitId() + "] is null"));
            LOGGER.warn("Propagate can't be done. Either unit goods  for Unit[" + inUnit.getUnitId() + "] is null");
            return;
        }
        inMonitorDates.sort();
        GoodsBase unitGoods = inUnit.getUnitGoods();
        ReeferRqmnts reeferRqmnts = unitGoods.ensureGdsReeferRqmnts();
        updateReeferMonitorTimes(reeferRqmnts, inMonitorDates);
        LOGGER.info("Unit[" + inUnit.getUnitId() + "] is updated with extended monitor times[" + inMonitorDates + "] " +
                "from ExtendedReeferMonitorTimesGrv Groovy");

    }

    private void updateReeferMonitorTimes(ReeferRqmnts inReeferRqmnts, List<Date> inDates) {
        Iterator<Date> iterator = inDates.iterator();
        if (iterator.hasNext()) {
            inReeferRqmnts.setRfreqTimeMonitor1(iterator.next());
        } else {
            inReeferRqmnts.setRfreqTimeMonitor1(null);
        }

        if (iterator.hasNext()) {
            inReeferRqmnts.setRfreqTimeMonitor2(iterator.next());
        } else {
            inReeferRqmnts.setRfreqTimeMonitor2(null);
        }

        if (iterator.hasNext()) {
            inReeferRqmnts.setRfreqTimeMonitor3(iterator.next());
        } else {
            inReeferRqmnts.setRfreqTimeMonitor3(null);
        }
        if (iterator.hasNext()) {
            inReeferRqmnts.setRfreqTimeMonitor4(iterator.next());
        } else {
            inReeferRqmnts.setRfreqTimeMonitor4(null);
        }
    }


    private Date[] getExtendedDateForTimes(List<String> inExtTimes) {

        final int extTimesLength = inExtTimes.size();

        Date[] extDates = new Date[extTimesLength];
        TimeZone tz = ContextHelper.getThreadUserTimezone();

        for (int i = 0; i < extTimesLength; i++) {
            //exTimes are already validated so no parsing required again
            String[] exTimeStr = StringUtils.split(inExtTimes.get(i), ":");
            extDates[i] = getCurrentDateWithHoursAndTime(tz, Integer.parseInt(exTimeStr[0]), Integer.parseInt(exTimeStr[1]));
        }

        return extDates;
    }

    //this method validates if given extended times are "HH:MM" format
    private void validateExtendedTimes(List<String> inExtendTimes) {

        if (inExtendTimes == null || inExtendTimes.isEmpty() || inExtendTimes.get(0) == null) {
            MessageCollectorUtils.getMessageCollector().appendMessage(BizFailure
                    .create(ArgoPropertyKeys.INFO, null, "Propagate can't be done since there are no valid extended Times given in groovy"));
            LOGGER.error("Propagate can't be done since there are no valid extended Times given in groovy");
        }

        for (String extTime : inExtendTimes) {
            StringTokenizer st = new StringTokenizer(extTime, ":");
            int tokenCount = st.countTokens();
            if (tokenCount != 2) {
                MessageCollectorUtils.getMessageCollector().appendMessage(BizFailure
                        .create(ArgoPropertyKeys.INFO, null,
                        "Propagate can't be done. Time entered [" + extTime + "] is invalid.Please enter HH:MM format"));
                LOGGER.error("Propagate can't be done. Time entered [" + extTime + "] is invalid.Please enter HH:MM format");
                break;
            }
            String hoursSt = st.nextToken();
            int hours = Integer.parseInt(hoursSt);
            if (hours < 0 || hours > 23) {
                MessageCollectorUtils.getMessageCollector().appendMessage(BizFailure
                        .create(ArgoPropertyKeys.INFO, null,
                        "Propagate can't be done. Time entered [" + extTime + "] is invalid.Maximum allowed hours are 23"));
                LOGGER.error("Propagate can't be done. Time entered [" + extTime + "] is invalid.Maximum allowed hours are 23");
                break;
            }

            String minutesSt = st.nextToken();
            int minutes = Integer.parseInt(minutesSt);
            if (minutes < 0 || minutes > 59) {
                MessageCollectorUtils.getMessageCollector().appendMessage(BizFailure
                        .create(ArgoPropertyKeys.INFO, null,
                        "Propagate can't be done. Time entered [" + extTime + "] is invalid.Maximum allowed Minutes are 59"));
                LOGGER.error("Propagate can't be done. Time entered [" + extTime + "] is invalid.Maximum allowed Minutes are 59");
                break;
            }
        }
    }

    private Date getCurrentDateWithHoursAndTime(TimeZone inTz, int inHours, int inMinutes) {

        //get current date Calendar
        Calendar currentDateCal = Calendar.getInstance(inTz);

        //set hours and minutes from time widget to current cal
        currentDateCal.set(Calendar.HOUR, inHours);
        currentDateCal.set(Calendar.MINUTE, inMinutes);
        currentDateCal.set(Calendar.AM_PM, Calendar.AM);

        return currentDateCal.getTime();
    }

    //sets the Extended times
    //This setter is required for Junit cases. Please don't delete this.
    public void setExtendedTimes(List<String> inExtendTimes) {
        _extendedTimes = inExtendTimes;
    }

    private static final Logger LOGGER = Logger.getLogger(ExtendedReeferMonitorTimesGrv.class);
}
