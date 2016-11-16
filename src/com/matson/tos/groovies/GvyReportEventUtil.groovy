import com.navis.services.ServicesField;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.portal.Ordering;

import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.services.business.rules.EventType;
import com.navis.framework.util.BizFailure;

import java.io.Serializable;
import java.util.List
import java.util.Date
import java.util.Calendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class GvyReportEventUtil extends GroovyInjectionBase
{
    public List getEventsByCreatedDate(Date inStartDate, Date inEndDate, EventType[] inEventTypes) {
        try{
            DomainQuery dq = QueryUtils.createDomainQuery("Event").addDqPredicate(PredicateFactory.ge(ServicesField.EVNT_CREATED, inStartDate)).addDqOrdering(Ordering.asc(ServicesField.EVNT_CREATED));
            if (inEndDate != null) {
                dq.addDqPredicate(PredicateFactory.lt(ServicesField.EVNT_CREATED, inEndDate));
            }
            if (inEventTypes != null && inEventTypes.length > 0) {
                List eventTypeGkeys = new ArrayList();
                for (aEvntType in inEventTypes) {
                    EventType eventType = aEvntType;
                    Serializable eventTypeGkey = null;
                    if (eventType == null) {
                        continue;
                    }
                    eventTypeGkey = eventType.getEvnttypeGkey();
                    if (eventTypeGkey == null) {
                        DomainQuery gkeydq = QueryUtils.createDomainQuery("EventType").addDqPredicate(PredicateFactory.ge(ServicesField.EVNTTYPE_ID, eventType.getId()));
                        EventType retrievedServiceType = (EventType)HibernateApi.getInstance().getUniqueEntityByDomainQuery(gkeydq);
                        if (retrievedServiceType != null) {
                            eventTypeGkey = retrievedServiceType.getEvnttypeGkey();
                        } else {
                            throw BizFailure.create((new StringBuilder()).append("getEventsByCreatedDate: A EventType of the defined array of EventTypes entered to the method is not valid. EventType id is ").append(eventType.getId()).toString());
                        }
                    }
                    eventTypeGkeys.add(eventTypeGkey);
                }
                //Array Serializable Object
                Serializable[] eventTypeGkeysArray = new Serializable[eventTypeGkeys.size()];
                eventTypeGkeysArray = (Serializable)(Serializable)eventTypeGkeys.toArray(eventTypeGkeysArray);
                if (eventTypeGkeysArray.length > 0) {
                    dq.addDqPredicate(PredicateFactory.in(ServicesField.EVNT_EVENT_TYPE, eventTypeGkeysArray));
                }
            }
            dq.addDqPredicate(PredicateFactory.in(ServicesField.EVNT_FACILITY, getFacility().getFcyGkey()));
            return HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    public Date formatDateToTimeZone(Date date, String timeZone)
    {
        Date fmtHstDate = null;
        try
        {
            DateFormat firstFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            TimeZone firstTime = TimeZone.getTimeZone(timeZone);
            firstFormat.setTimeZone(firstTime);
            String fmtDateStr = firstFormat.format(date);
            SimpleDateFormat secondFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            fmtHstDate = secondFormat.parse(fmtDateStr);
        }catch(Exception e){
            e.printStackTrace();
        }
        return fmtHstDate;
    }


    public long timeDiffInHrs(Date currentDate, Date vesDeptDate)
    {
        long hrDiff = 0
        try
        {
            Calendar currCalDt= Calendar.getInstance();
            currCalDt.setTime(currentDate);
            long currDt = currCalDt.getTimeInMillis();

            Calendar deptVesCalDt = Calendar.getInstance();
            deptVesCalDt.setTime(vesDeptDate);
            long deptVesDt = deptVesCalDt.getTimeInMillis();

            long hrTimeDiff = currDt - deptVesDt;

            hrDiff  = hrTimeDiff /(60*60*1000);
            println("hrTimeDiff ::"+hrTimeDiff+"hrDiff ::"+hrDiff );

        }catch(Exception e){
            e.printStackTrace();
        }
        return hrDiff;
    }


}