import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.business.model.Facility
import com.navis.argo.business.reference.LineOperator
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.atoms.EqUnitRoleEnum
import com.navis.inventory.business.units.EquipmentState
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @author Keerthi Ramachandran
 * @since 5/25/2016
 * <p>MATUpdateContainerOperatorOnStrip is ..</p>
 */
class MATUpdateContainerOperatorOnStrip extends AbstractGeneralNoticeCodeExtension {

    private Logger LOGGER = Logger.getLogger(MATUpdateContainerOperatorOnStrip.class);

    public void execute(GroovyEvent inEvent)

    {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("MATUpdateContainerOperatorOnStrip Execution Started");

        Boolean isUnitCreatedByStripEvent = isUnitCreatedByStripEvent(inEvent);
        if (isUnitCreatedByStripEvent) {

            try {
                Unit ThisUnit = (Unit) inEvent.getEntity();
                if (ThisUnit == null) {
                    LOGGER.error("Reference to Unit not found!");
                    sendMailAndReturn("Reference to Unit not found!");
                } else
                    LOGGER.info("Unit: " + ThisUnit);

                Facility ThisFacility = ContextHelper.getThreadFacility();
                UnitFacilityVisit ThisUFV = ThisUnit.getUfvForFacilityAndEventTime(ThisFacility,
                        inEvent.getEvent().getEventTime());
                if (ThisUFV == null) {
                    LOGGER.error("Reference to UFV not found!");
                    sendMailAndReturn("Reference to UFV not found!")
                } else
                    LOGGER.info("UFV: " + ThisUFV);

                UnitEquipment ThisUnitEquip = ThisUnit.getUnitPrimaryUe();
                EquipmentState ThisEqState = ThisUnitEquip.getUeEquipmentState();



                EqUnitRoleEnum UeRole = ThisUnitEquip.getUeEqRole();
                EquipClassEnum equipClass = ThisUnitEquip.getUeEquipment().getEqEquipType().getEqtypClass();


                if (EqUnitRoleEnum.PRIMARY.equals(UeRole) && EquipClassEnum.CONTAINER.equals(equipClass)) {
                    //String containerIdFull = ThisUnit.getUnitId();
                    String containerOwner = ThisEqState.getEqsEqOwner().getBzuId();
                    String containerOperator = ThisEqState.getEqsEqOperator().getBzuId();

                    /*
                    * Check the Original Equipment, if the owner is Maersk, the update the stripped empty operator to MAE
                    */
                    LOGGER.info("The Equipment Original Owner is " + containerOwner + " Operator is " + containerOperator);

                    if (ThisUnit.getUnitLineOperator().getBzuId() != null) {
                        LOGGER.info("The UFV Line Operator is " + ThisUnit.getUnitLineOperator().getBzuId());
                        if (ThisUnit.getUnitLineOperator().getBzuId().equalsIgnoreCase(containerOperator)) {
                            //do nothing
                            LOGGER.info("The Line Operator of UFV matches the Equipment Operator, No Update Necessary")
                        } else {
                            LineOperator lineOperator = LineOperator.findLineOperatorById(containerOperator);
                            if (lineOperator != null) {
                                ThisUnit.setUnitLineOperator(lineOperator);
                                LOGGER.info("Line Operator Updated to " + containerOperator);
                            } else {
                                sendMailAndReturn("No able to find LineOpearator " + containerOperator);
                            }
                        }
                    }
                }
                LOGGER.info("Update Successful.")
            }
            catch (Exception e) {
                LOGGER.error("Update Failed. Exception [" + e + "].");
                sendMail("Update Failed.", e);
            }
            finally {
                LOGGER.info("MATUpdateContainerOperatorOnStrip Execution Ended.")
            }
        }
    }

    private void sendMailAndReturn(String inMessage) {
        LOGGER.error(inMessage);
        //sendMail(inMessage, null);
        return;
    }

    private void sendMail(String inMessage, Exception inException) {
        LOGGER.error(inMessage, inException);
    }

    public boolean isUnitCreatedByStripEvent(Object event) {
        try {
            def evntNotes = event.event.evntNote
            evntNotes = evntNotes != null ? evntNotes : ''
            if (evntNotes.contains('Strip') || evntNotes.contains('strip')) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return false;
    }

}