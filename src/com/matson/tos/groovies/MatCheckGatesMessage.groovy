import com.navis.apex.business.model.GroovyInjectionBase

import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery



import com.navis.services.business.event.Event;
import com.navis.services.ServicesField;
import com.navis.services.business.rules.EventType;
import com.navis.services.business.api.EventManager;


/*
* Author : Raghu Iyer
* Date Written : 01/05/2014
* Description: This groovy is used to check the GATES connectivity from TOS by checking the messages.
*/

public class MatCheckGatesMessage extends GroovyInjectionBase
{
    def inj = new GroovyInjectionBase();
    int hours = -1;

    private final String emailTo = "1aktosdevteam@matson.com";

    public boolean execute(Map params)
    {
        println("Started : MatRaghuTest");
        try
        {

            List events = getAllEvents();

            if (events.size() > 0) {
                Iterator eventsIterator = events.iterator();
                while(eventsIterator.hasNext())
                {
                    Event event = eventsIterator.next();
                    if (event.evntAppliedBy.contains("jms")){
                        println("Details ----------> "+ event.evntGkey + " :: "+event.evntAppliedBy+" :: "+event.evntCreated+" :: "+event.evntEventType.evnttypeId);
                        break;
                    }
                }
            } else {
                println("Send alert email");
                def emailSender = getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(emailTo,"GATES Connectivity issue - Please check","No GATES messages received in past "+Math.abs(hours)+" hour/s. Please check the connectivity \n\nThanks");
            }

        }catch(Exception e){
            e.printStackTrace()
            println(e)
        }
    }

    private List getAllEvents()
    {

        try{

            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.HOUR_OF_DAY, hours);
            Date startDate = cal.getTime();
            println("startDate "+ startDate);

            EventType eventType = EventType.findEventType("BOOKING_CREATE")
            def bkngCreate = eventType.getEvnttypeGkey();

            eventType = EventType.findEventType("BOOKING_PROPERTY_UPDATE")
            def bkngUpdt = eventType.getEvnttypeGkey();

            eventType = EventType.findEventType("LNK")
            def lnk = eventType.getEvnttypeGkey();

            eventType = EventType.findEventType("ULK")
            def ulk = eventType.getEvnttypeGkey();

            eventType = EventType.findEventType("BDB")
            def bdb = eventType.getEvnttypeGkey();

            eventType = EventType.findEventType("BDA")
            def bda = eventType.getEvnttypeGkey();


            DomainQuery dq = QueryUtils.createDomainQuery("Event")
                    .addDqPredicate(PredicateFactory.ge(ServicesField.EVNT_APPLIED_DATE, startDate))
                    .addDqPredicate(PredicateFactory.in(ServicesField.EVNT_EVENT_TYPE, bkngCreate,bkngUpdt,lnk,ulk,bdb,bda))
                    .addDqOrdering(Ordering.desc(ServicesField.EVNT_APPLIED_DATE));

            println (dq);

            //dq.setMaxResults(1);
            List events = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println ("events.size()" + events.size());

            return events;

        } catch (Exception e){
            println("Error while getting events:::"+e)
        }
    }

}

