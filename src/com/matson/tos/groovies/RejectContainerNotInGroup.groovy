import com.navis.argo.business.reference.Group;
import com.navis.framework.util.internationalization.PropertyKey;
import com.navis.framework.util.internationalization.PropertyKeyFactory;
import com.navis.framework.util.message.MessageLevel;
import com.navis.inventory.business.units.Routing;
import com.navis.inventory.business.units.Unit;
import com.navis.road.business.util.RoadBizUtil;
import com.navis.road.business.workflow.TransactionAndVisitHolder;

public class RejectContainerNotInGroup {

    public static final String BEAN_ID = "rejectContainerNotInGroup";
    public static PropertyKey CONTAINER_NOT_IN_GROUP = PropertyKeyFactory.valueOf("gate.container_not_in_group");

    public void execute(TransactionAndVisitHolder dao, api) {
        Unit unit = dao.getTran().getTranUnit();
        String groupId = getGroupId(unit);

        if (!GROUP_ID.equals(groupId)) {
            RoadBizUtil.appendMessage(MessageLevel.SEVERE, CONTAINER_NOT_IN_GROUP, dao.getTran().getTranCtrNbr(), groupId);
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

    private static final String GROUP_ID = "PASSPASS";
}
