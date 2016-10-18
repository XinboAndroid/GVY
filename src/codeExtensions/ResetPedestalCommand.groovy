package codeExtensions

/**
 * @author Keerthi Ramachandran
 * @since 11/2/2015
 * <p>ResetPedestalCommand is ..</p>
 */
import java.io.Serializable;
import com.navis.external.framework.ui.AbstractTableViewCommand;
import com.navis.external.framework.ui.EUIExtensionHelper;
import com.navis.framework.metafields.entity.EntityId;
import com.navis.framework.portal.BizRequest;
import com.navis.framework.portal.CrudDelegate;
import com.navis.framework.presentation.util.FrameworkUserActions;
import com.navis.framework.ulc.server.context.UlcRequestContextFactory;
import com.navis.framework.util.message.MessageLevel;
import com.navis.road.RoadApiMappingConsts;
import com.navis.road.RoadField;

public class ResetPedestalCommand extends AbstractTableViewCommand {
    void execute(EntityId entityId, List<Serializable> gkeys, Map<String, Object> params) {

        log("gkeys=$gkeys")
        log("entityId=$entityId")
        for (String s : params.keySet()) {
            log("$s=" + params.get(s))
        }
        super.execute(entityId,gkeys,params);
    }
}
