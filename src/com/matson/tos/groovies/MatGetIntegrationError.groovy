import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.EquipmentState
import java.text.SimpleDateFormat
import java.text.DateFormat

import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.api.UnitField
import com.navis.argo.business.model.CarrierVisit
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery
import com.navis.argo.ArgoField;
import com.navis.argo.ArgoRefField;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.road.business.model.*;
import java.util.Calendar;

import com.navis.argo.business.model.Facility;
import com.navis.services.business.event.Event;
import com.navis.argo.business.reference.Equipment
import com.navis.services.business.rules.EventType;
import com.navis.services.business.api.EventManager;
import com.navis.inventory.InventoryField;
import com.navis.services.business.event.EventFieldChange;
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdFactory;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.argo.business.reference.Shipper;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.services.business.event.GroovyEvent;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import com.navis.road.RoadField;
import com.navis.argo.business.reference.LineOperator;

import com.navis.argo.ArgoConfig;
import com.navis.argo.ArgoPropertyKeys;
import com.navis.argo.ArgoRefField;
import com.navis.argo.business.reference.AgentRepresentation;
import com.navis.argo.business.atoms.BizRoleEnum;
import com.navis.argo.business.reference.Agent;
import com.navis.road.business.model.TruckingCompany
import java.lang.*;
import com.navis.argo.business.model.GeneralReference;
//import com.navis.framework.ulc.server.application.controller.form.ShowDeleteFormCommand;
import com.navis.argo.business.reference.Chassis;
import com.navis.framework.business.atoms.LifeCycleStateEnum;
import com.navis.inventory.business.atoms.EqDamageSeverityEnum;

import com.navis.argo.business.integration.IntegrationError;
import com.navis.argo.ArgoIntegrationField;
import com.navis.argo.business.atoms.IntegrationActionStatusEnum;
import com.navis.argo.business.atoms.IntegrationTypeEnum;
import com.navis.argo.business.model.Facility;
import com.navis.services.business.event.Event;
import com.navis.services.business.rules.EventType;
import com.navis.services.business.event.Event;
import com.navis.services.ServicesField;
import com.navis.framework.persistence.HibernateApi

import com.navis.argo.business.reference.RoutingPoint;

import com.navis.framework.business.atoms.NodeStatusTypeEnum;
import com.navis.framework.portal.context.PortalApplicationContext;
import com.navis.framework.portal.context.server.IServerContext;
import com.navis.framework.portal.context.server.IServerConfig;
/*
* Author : Raghu Iyer
* Date Written : 09/19/2012
* Description: This groovy is used to generate the Stowplan/Manifest Discrepancies after Stowplan and Manifest process
*/

public class MatGetIntegrationError extends GroovyInjectionBase
{
    public boolean execute(Map params)
    {
        try
        {
            IServerContext application = (IServerContext)PortalApplicationContext.getBean("serverContext");
            println("application :::::::::::::::::::::::::"+application);
            def status = application.getNodeStatus();
            println("Node Status :::::::::::::::::::::::::"+status);
            IServerConfig serverConfig = application.getServerConfig();
            println("serverConfig :::::::::::::::::::::::::"+serverConfig);
            def node = serverConfig.getNodeName();
            println("Node Name :::::::::::::::::::::::::"+node);

            //getIntegrationError();

            println("reportUnitList ------- Success")
        }catch(Exception e){
            e.printStackTrace()
            println(e)
        }
    }

    public void createIntegrationError(String exception, String entity, String bizKey,String event, String errDesc, String messageBody )
    {
        try {
            println("Inside createIntegrationError");
            Facility facility = Facility.findFacility(ContextHelper.getThreadFacility().getFcyId());
            IntegrationTypeEnum type = IntegrationTypeEnum.JMS_OUTBOUND_TEXT
            IntegrationActionStatusEnum status = IntegrationActionStatusEnum.ERROR;
            IServerContext application = (IServerContext)PortalApplicationContext.getBean("serverContext");
            IServerConfig serverConfig = application.getServerConfig();
            def node = serverConfig.getNodeName();
            Date firstRecorded = new Date();
            exception = exception.substring(1,2000);

            EventType eventType = EventType.findEventType(event);
            String eventTypekey = eventType.getEvnttypeGkey();
            def eventkey = null;
            List events = null;
            events = getAllEvents(eventTypekey, bizKey);
            Iterator eventsIterator = events.iterator();
            while(eventsIterator.hasNext())
            {
                Event eventItr = eventsIterator.next();
                eventkey = eventItr.getEvntGkey();
            }
            IntegrationError newErr = new IntegrationError();

            byte[] messageArray = messageBody.getBytes("UTF-16");

            newErr.setIerrIntegrationType(type);
            newErr.setIerrIntegrationStatus(status);
            newErr.setIerrNode(node);
            newErr.setIerrException(exception);
            newErr.setIerrExceptionLog(exception);
            newErr.setIerrEntityName(entity);
            newErr.setIerrBusinessKey(bizKey);
            newErr.setIerrEventId(event);
            newErr.setIerrEventGkey(eventkey);
            newErr.setIerrDescription(errDesc);
            newErr.setIerrMessageBody(messageBody);
            //newErr.setIerrMessageByteArray(messageArray);
            newErr.setIerrErrorFirstRecorded(firstRecorded);
            newErr.setIerrFacility(facility);

            def gkey = newErr.getPrimaryKey();
            println("Gkey is ::::::::::::::::::::::::::::::::::::::"+gkey);
            println("::::::::after insert Here::::::::::::"+ type+"::"+status+"::"+node+"::"+entity+"::"+eventkey+"::"+messageBody+"::"+firstRecorded+"::"+facility);

            println("newErr ::::::::::::::::::::::::::"+newErr);
            try{
                Roastery.getHibernateApi().save(newErr);
                gkey = newErr.getPrimaryKey();
                println("Gkey after save::::::::::::::::::::::::::::::::::::::"+gkey);
                def fulshMode = HibernateApi.getInstance().getFlushMode();
                println("fulshMode :::::::::::"+fulshMode);

                if (gkey != null){
                    HibernateApi.getInstance().flush();
                }
            } catch (Exception e){
                e.printStackTrace();
                println("Error in createIntegrationError HibernateApi.getInstance().flush() ::"+e);
            }

        }catch (Exception e){
            println("Error :::" + e);
        }
    }

    public void getIntegrationError()
    {
        try {
            println("Inside getIntegrationError");
            ArrayList errors = new ArrayList();
            String containerId = "SUDU7362382";
            DomainQuery dq = QueryUtils.createDomainQuery("IntegrationError");
            dq.addDqPredicate(PredicateFactory.eq(ArgoIntegrationField.IERR_BUSINESS_KEY,containerId));
            println("errors "+dq);
            def errorList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After errorList"+errorList.size());
            if (errorList.size() > 0)
            {
                Iterator iterErrorList = errorList.iterator()
                while (iterErrorList.hasNext())
                {
                    def err = iterErrorList.next();
                    IntegrationError newErr = new IntegrationError();

                    //def type = err.getIerrIntegrationType();
                    // def status = err.getIerrIntegrationStatus();
                    def node = err.getIerrNode();
                    def exception = err.getIerrException();
                    def expLog = err.getIerrExceptionLog();
                    //def entity = err.getIerrEntityName();
                    def bizKey = err.getIerrBusinessKey();
                    def event = err.getIerrEventId();
                    //def eventkey = err.getIerrEventGkey();
                    def errDesc = err.getIerrDescription();
                    def messageBody = err.getIerrMessageBody();
                    //def messageArray = err.getIerrMessageByteArray();
                    def firstRec = err.getIerrErrorFirstRecorded();
                    def lastRetry = err.getIerrLastRetry();
                    //def facility = err.getIerrFacility();
                    //byte[] messageArray = ["jfygsdkfjhdfcjkzdcbvkzdgakfacjckjacvajga"];

                    EventType eventType = EventType.findEventType(event);
                    println(":::::::::::::eventType::::::::::"+eventType+"::"+event);
                    String eventTypekey = eventType.getEvnttypeGkey();
                    println(":::::::::::::eventTypekey::::::::::"+eventTypekey);
                    def entity = "Unit";
                    String str = null;
                    def eventkey = getAllEvents(eventTypekey, bizKey)
                    try {
                        byte[] messageArray = ["jfygsdkfjhdfcjkzdcbvkzdgakfacjckjacvajga"];
                    }catch (Exception e){
                        println(e);
                        str = e;
                    }
                    //String str = e;
                    byte[] messageArray = str.getBytes("UTF-16");
                    //byte[] messageArray = DatatypeConverter.parseHexBinary(str);
                    Facility facility = Facility.findFacility(ContextHelper.getThreadFacility().getFcyId());
                    println("::::::::Here::::::::::::"+ bizKey + "::"+facility);
                    IntegrationTypeEnum type = IntegrationTypeEnum.JMS_OUTBOUND_TEXT
                    IntegrationActionStatusEnum status = IntegrationActionStatusEnum.ERROR;
                    println("::::::::Here::::::::::::"+ status);
                    newErr.setIerrIntegrationType(type);
                    newErr.setIerrIntegrationStatus(status);
                    newErr.setIerrNode(node);
                    //newErr.setIerrException(str);
                    newErr.setIerrExceptionLog(str);
                    newErr.setIerrEntityName(entity);
                    newErr.setIerrBusinessKey(bizKey);
                    newErr.setIerrEventId(event);
                    newErr.setIerrEventGkey(eventkey);
                    //newErr.setIerrDescription(errDesc);
                    newErr.setIerrMessageBody(messageBody);
                    //newErr.setIerrMessageByteArray(messageArray);
                    newErr.setIerrErrorFirstRecorded(new Date());
                    //newErr.setIerrLastRetry(lastRetry);
                    newErr.setIerrFacility(facility);

                    Roastery.getHibernateApi().save(newErr);

                    println("::::::::after insert Here::::::::::::"+ type+"::"+status+"::"+node+"::"+entity+"::"+eventkey+"::"+errDesc+"::"+messageBody+"::"+firstRec+"::"+facility);
                }
            }
        }catch (Exception e){
            println("Error :::" + e);
        }
    }

    private List getAllEvents(String eventType, String unitId)
    {
        try{

            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.HOUR_OF_DAY, -1);
            Date startDate = cal.getTime();
            println("startDate "+ startDate);


            DomainQuery dq = QueryUtils.createDomainQuery("Event")
                    .addDqPredicate(PredicateFactory.ge(ServicesField.EVNT_APPLIED_DATE, startDate))
                    .addDqPredicate(PredicateFactory.eq(ServicesField.EVNT_EVENT_TYPE, eventType))
                    .addDqPredicate(PredicateFactory.eq(ServicesField.EVNT_APPLIED_TO_NATURAL_KEY, unitId))
                    .addDqOrdering(Ordering.desc(ServicesField.EVNT_APPLIED_DATE));

            dq.setMaxResults(1);
            List events = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println ("events.size()" + events.size());

            return events;

        } catch (Exception e){
            println("Error while getting events:::"+e)
        }
    }

}