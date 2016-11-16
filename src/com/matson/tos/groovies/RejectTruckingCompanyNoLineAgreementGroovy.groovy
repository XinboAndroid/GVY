import com.navis.argo.business.reference.Group
import com.navis.inventory.business.units.Routing
import com.navis.inventory.business.units.Unit
import com.navis.road.business.adaptor.truckingcompany.RejectTruckingCompanyNoLineAgreement
import com.navis.road.business.workflow.TransactionAndVisitHolder
import java.util.ArrayList

public class RejectTruckingCompanyNoLineAgreementGroovy extends RejectTruckingCompanyNoLineAgreement {
    public void execute(TransactionAndVisitHolder dao, api) {
        Unit unit = dao.getTran().getTranUnit();
        String groupId = getGroupId(unit);

        if (!GROUP_IDS.contains(groupId)) {
            execute(dao);
        }

    }

    private String getGroupId(Unit unit) {
        Routing routing = unit.getUnitRouting();
        if (routing == null) {
            return null;
        }

        Group group = routing.getRtgGroup();
        if (group == null) {
            return null;
        }

        return group.getGrpId();
    }

    private static final ArrayList GROUP_IDS = ["YB","XFER-SI","XFER-WO","XFER-P2"];
}
