import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.ui.EFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.metafields.entity.EntityId



class MATBargeStowplanInterceptor extends AbstractFormSubmissionCommand implements EFormSubmissionCommand {
    public void doAfterSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        this.log("CALLED FROM FORM SUBMISSION INTERCEPTOR");
    }
}

