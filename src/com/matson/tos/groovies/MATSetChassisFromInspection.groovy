import com.navis.argo.business.api.GroovyApi;
import com.navis.argo.business.atoms.DataSourceEnum;
import com.navis.external.road.AbstractGateTaskInterceptor;
import com.navis.external.road.EGateTaskInterceptor;
import com.navis.road.business.model.TruckTransaction;
import com.navis.road.business.workflow.TransactionAndVisitHolder;

/**
 * This groovy set the chassis number flex field (from Inspection)  into actual chassis number field
 *
 */

public class MATSetChassisFromInspection extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{
    public void execute(TransactionAndVisitHolder inDao)

    {
        this.log("Execution Started MATSetChassisFromInspection");

        GroovyApi api =new GroovyApi();


        /* check various components of the gate transaction to insure everything needed is present. */

        if (inDao == null)
            return;

        TruckTransaction ThisTran = inDao.getTran();
        //api.sendEmail("gbabu@matson.com","gbabu@matson.com","transaction ","Before transaction is null" + ThisTran.getTranUnitFlexString04());
        if (ThisTran == null || ThisTran.getTranUnitFlexString04()==null) {
            api.sendEmail("gbabu@matson.com","gbabu@matson.com","transaction is null","transaction is null");
            return;
        }

        try {
            ThisTran.setChsNbr(ThisTran.getTranUnitFlexString04(), null);
            ThisTran.setTranUnitFlexString04("");
        }
        catch(Exception e) {}

    }
}