import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.util.RoadBizUtil
import com.navis.argo.business.reference.RoutingPoint


public class GvyDrayInPodCheck{

    public void execute(TransactionAndVisitHolder dao, api){
        try{
            TruckTransaction tran = dao.tran
            def dest = tran.tranDestination
            def msg = [dest];
            //added 'ANK','DUT','KDK', todo remove other ports
            def drayInPorts = ['ANK','DUT','KDK','KAH','HIL','KHI','NAW','LNI','MOL','HON','GUM','SHA','NGB','XMN','SEA','OAK','LAX']
            if(drayInPorts.contains(dest)){
                RoutingPoint routingPoint = new RoutingPoint()
                tran.tranDischargePoint1 = routingPoint.findRoutingPoint(dest)
            }else{
                PropertyKey INVALID_DEST = PropertyKeyFactory.valueOf("gate.invalid.dest");
                RoadBizUtil.appendMessage(MessageLevel.SEVERE,INVALID_DEST,msg);
            }
        }catch(Exception e){
            def msg = [" Exception"];
            PropertyKey INVALID_DEST = PropertyKeyFactory.valueOf("gate.invalid.dest");
            RoadBizUtil.appendMessage(MessageLevel.SEVERE,INVALID_DEST,msg);
            e.printStackTrace()
        }

    }//Method Ends

}//Class Ends