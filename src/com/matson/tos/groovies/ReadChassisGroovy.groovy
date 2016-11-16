import com.navis.road.business.adaptor.chassis.ReadChassis;
import com.navis.road.business.workflow.TransactionAndVisitHolder;


public class ReadChassisGroovy extends ReadChassis {
    public void execute(TransactionAndVisitHolder dao, api) {
        execute(dao);
    }
}
