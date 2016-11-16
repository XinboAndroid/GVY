/*
* Srno  date      doer  change
* A1    02/02/12  GR    setMnsQueue for NIZ action posting
*/
import java.util.Hashtable;
import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.jms.*;
import com.navis.apex.business.model.GroovyInjectionBase;
import org.apache.activemq.ActiveMQConnectionFactory;


public class JMSQueueSender extends GroovyInjectionBase{

    private String queueName = "ak.n4.gems.eq.events";
    private static String url;

    public JMSQueueSender()  {
        if(url == null){
            url = getGroovyClassInstance("GvyRefDataLookup").getN4TopicAddress();
        }
    }

    public JMSQueueSender( String _queueName)  {
        if(url == null){
            url = getGroovyClassInstance("GvyRefDataLookup").getN4TopicAddress();
        }
        queueName = _queueName
    }

    public void setMnsQueue(String _mnsQueueName){
        queueName = _mnsQueueName
    }

    public  void send(String msg) throws JMSException, Exception
    {
        //String  cfName                  = "jms/WLQueueConnectionFactory";
        String  cfName                  = "activemq/QueueConnectionFactory";


        Session                session    = null;
        Connection             connection = null;
        ActiveMQConnectionFactory       cf         = null;
        MessageProducer        mp         = null;
        Destination            destination = null;
        println( "Calling JMSQueueSender.send using ActiveMQConnectionFactory");
        try {

            //Hashtable env = new Hashtable();
            //env.put(Context.INITIAL_CONTEXT_FACTORY,
            //    "weblogic.jndi.WLInitialContextFactory");
            //env.put(Context.PROVIDER_URL, "tcp://10.8.4.26:61616");
            //Context initialContext = new InitialContext(env);
            //logger.debug( "Getting Connection Factory");

            //cf= (ConnectionFactory)initialContext.lookup( cfName );

            //logger.debug( "Getting Queue");
            //destination =(Destination)initialContext.lookup(queueName);
            println("Inside JmsQueueSender :;"+url);
            cf = new ActiveMQConnectionFactory(url);

            //logger.debug( "Getting Connection for Queue");
            connection = cf.createConnection();

            //logger.debug( "staring the connection");
            connection.start();

            //logger.debug( "creating session");
            session = connection.createSession(false, 1);
            destination = session.createQueue(queueName);
            //logger.debug( "creating messageProducer");
            mp = session.createProducer(destination);

            //logger.debug( "creating TextMessage");
            TextMessage outMessage = session.createTextMessage( msg);

            println( "sending Message to queue: " + queueName);
            mp.send(outMessage);

            mp.close();
            session.close();
            connection.close();
        }
        catch (Exception je)
        {
            je.printStackTrace();
        }
    }//Method Ends

} //JMSQueueSender - Class Ends