import com.navis.road.business.adaptor.accessory.ReadAccessory;
import com.navis.road.business.workflow.TransactionAndVisitHolder;


public class ReadAccessoryGroovy extends ReadAccessory {
    public void execute(TransactionAndVisitHolder dao, api) {
        execute(dao);
    }
}