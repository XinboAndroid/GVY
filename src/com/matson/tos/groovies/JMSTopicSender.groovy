import java.util.Hashtable;
import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.jms.*;
import com.navis.apex.business.model.GroovyInjectionBase;
import org.apache.activemq.ActiveMQConnectionFactory;

public class JMSTopicSender extends GroovyInjectionBase  {

    private static String topicName = "jms.topic.tdp.n4";
    private static String url;

    public JMSTopicSender()  {
        if(url == null){
            url = getGroovyClassInstance("GvyRefDataLookup").getN4TopicAddress();
        }
    }

    public JMSTopicSender( String _topicName)  {
        if(url == null){
            url = getGroovyClassInstance("GvyRefDataLookup").getN4TopicAddress();
        }
        topicName = _topicName
    }

    public  void send(String msg) throws JMSException, Exception
    {
        String  cfName                    = "jms/WLQueueConnectionFactory";

        TopicSession               session    = null;
        TopicConnection             connection = null;
        ActiveMQConnectionFactory       cf         = null;
        MessageProducer        mp         = null;
        Destination            destination = null;
        println( "Calling JMSTopicSender.send using ActiveMQConnectionFactory");
        try {

            //Hashtable env = new Hashtable();
            //env.put(Context.INITIAL_CONTEXT_FACTORY,
            //    "weblogic.jndi.WLInitialContextFactory");
            //env.put(Context.PROVIDER_URL, url);
            //Context initialContext = new InitialContext(env);
            //println( "Getting Connection Factory");

            //cf= (TopicConnectionFactory)initialContext.lookup( cfName );
            cf = new ActiveMQConnectionFactory(url);

            //println( "Getting Queue");
            //destination =(Destination)initialContext.lookup(topicName);

            //println( "Getting Connection for Queue");
            connection = cf.createTopicConnection();

            //println( "staring the connection");
            connection.start();

            //println( "creating session");
            session = connection.createTopicSession(false, 1);
            destination = session.createTopic(topicName);

            //println( "creating messageProducer");
            mp = session.createProducer(destination);

            //println( "creating TextMessage");
            TextMessage outMessage = session.createTextMessage( msg);

            println( "sending Message to topic: " + topicName);
            mp.send(outMessage);

            mp.close();
            session.close();
            connection.close();
        }
        catch (Exception je)
        {
            je.printStackTrace();
        }
    }

}