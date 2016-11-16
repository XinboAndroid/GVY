import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.EquipMaterialEnum
import com.navis.argo.business.atoms.FlagStatusEnum
import com.navis.argo.business.atoms.FlagPurposeEnum;
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.rules.ServiceImpediment
import org.apache.log4j.Logger
import java.text.SimpleDateFormat
import javax.jms.JMSException
import javax.jms.Session
import javax.jms.Connection
import javax.jms.ConnectionFactory
import javax.jms.MessageProducer
import javax.jms.Destination
import javax.naming.Context
import javax.naming.InitialContext
import javax.jms.TextMessage
import javax.jms.*
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.reference.Group;
import com.navis.inventory.business.units.Routing;
import com.navis.services.business.event.GroovyEvent;
import com.navis.services.business.event.*

import org.apache.activemq.ActiveMQConnectionFactory;


/*

Date Written: 07/06/2012
Author: Siva Raja
Description: Groovy to extract unit details based on ane event recorded and send xml data to MNS application using JMS.
*/

public class  ProcessMsg extends GroovyApi {

    UserContext context = ContextHelper.getThreadUserContext();
    Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

    private static final String queueNameTdp = "n4.gems.eq.events"; // TDP Queue
    private String jbossUrl = null;

    def inj = new GroovyInjectionBase();

    public void execute(Map param)
    {
        //jbossUrl = inj.getGroovyClassInstance("GvyRefDataLookup").getN4TopicAddress();
        jbossUrl = "tcp://10.201.1.79:61616";
        println("JMS_URL IS <<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>>>>>  "+jbossUrl);
        LOGGER.warn("ProcessMsg started" + timeNow);

        ArrayList<String> msgs = new ArrayList<String>();
        msgs.add(0,"<GroovyMsg action='VPU'  aDate='09/24/2014'  aTime='07:19:12'  doer='GVPQ'  lastAction='VPU'  lastADate='09/24/2014'  lastATime='07:19:12'  lastDoer='GVPQ'  facility='GUM'  ctrNo='MATU551453'  srv='MAT'  locationStatus='1'  loc='GUAM'  vesvoy='%'  loadPort='%'  dischargePort='GUM'  dPort='%'  orientation='%'  owner='MAT' />");
        msgs.add(1,"<GroovyMsg action='VPU'  aDate='09/24/2014'  aTime='07:19:12'  doer='GVPQ'  lastAction='VPU'  lastADate='09/24/2014'  lastATime='07:19:12'  lastDoer='GVPQ'  facility='GUM'  ctrNo='MATU514934'  srv='MAT'  locationStatus='1'  loc='GUAM'  vesvoy='%'  loadPort='%'  dischargePort='GUM'  dPort='%'  orientation='%'  owner='MAT' />");


        for (String msg: msgs)
        {
            sendToTdp(msg);
        }


    }

    public void sendToTdp(String msg) throws JMSException, Exception {

        LOGGER.warn (" in sendToTdp message ");
        String  cfName = "jms/WLQueueConnectionFactory";
        Session                session    = null;
        Connection             connection = null;
        ConnectionFactory      cf         = null;
        MessageProducer        mp         = null;
        Destination            destination = null;

        try {

            LOGGER.warn( "Getting Connection Factory");

            cf = new ActiveMQConnectionFactory(jbossUrl);
            LOGGER.warn( "Getting Queue");

            LOGGER.warn( "Getting Connection for Queue");
            connection = cf.createConnection();
            LOGGER.warn( "staring the connection");
            connection.start();
            LOGGER.warn( "creating session");
            session = connection.createSession(false, 1);
            destination = session.createQueue(queueNameTdp);
            LOGGER.warn( "creating messageProducer");
            mp = session.createProducer(destination);
            LOGGER.warn( "creating TextMessage");
            TextMessage outMessage = session.createTextMessage( msg);
            LOGGER.warn( "sending Message to queue: " + queueNameTdp);
            mp.send(outMessage);
            mp.close();
            session.close();
            connection.close();
        }
        catch (Exception je)
        {
            LOGGER.warn("Exception in send:" + je )
        }
    }



    private static final Logger LOGGER = Logger.getLogger(ProcessMsg.class);
}





