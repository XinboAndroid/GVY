import com.navis.argo.business.reference.Group;
import com.navis.inventory.business.units.Routing;

/**
 * Created with IntelliJ IDEA.
 * User: lcrouch
 * Date: 1/9/13
 * Time: 2:41 PM
 * To change this template use File | Settings | File Templates.
 */
public class GvyPPIngate {

    /** If commodity code is SIT reapply DRAY status
     */
    public void setDray(Object unit) {
        println("PassPass Gate set dray");
        if(unit.getFieldValue("unitGoods.gdsCommodity.cmdyId").equals("SIT")) {
            unit.setFieldValue("unitDrayStatus",com.navis.argo.business.atoms.DrayStatusEnum.OFFSITE);
        }
    }

    /** Adds group id to unit for auto-update-rule
     *  to filter from changing SIT to SAT
     */
    public void setGroup(Object unit) {
        final String GROUP_ID = "PASSPASS";
        Routing routing = unit.getUnitRouting();
        if (routing == null) {
            println("PassPass Gate unitRouting is null");
        }
        Group group = routing.getRtgGroup();
        if (group == null) {
            routing.setRtgGroup(Group.findOrCreateGroup(GROUP_ID));
            println("PassPass Gate set group code to "+GROUP_ID);
        } else {
            println("PassPass Gate group is already set to "+group.toString());
        }
    }

}
