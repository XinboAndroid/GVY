/**
 * Description	:	This groovy generates an email alert if a SIT unit is being ingated as empty
 *
 * Srno  date       doer  change
 * A1    10/31/13  LC    Added 1fwdrs group @ phx and 1reefers group at phx to this email
 * A2    06/30/14  PS    Added 1aktosdevteam@matson.com group (Premium Group)
 */

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.reference.*
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.services.business.event.Event
import com.navis.services.business.event.EventFieldChange
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum

class GvyCheckSITIgtEmpty extends GroovyApi {
    //
    final String EMAIL_FROM = "1aktosdevteam@matson.com"
    final String EMAIL_TO = "1aktosdevteam@matson.com"
    //
    public void check(Object event) {
        try{
            Unit unit = (Unit)event.getEntity()
            println("******* GvyCheckSITIgtEmpty STARTS*******")
            def cmdyId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId")
            def unitFreightKind = unit.getFieldValue("unitFreightKind")
            unitFreightKind = unitFreightKind == null ? "": unitFreightKind.getKey()

            def gvyUnitUtil = getGroovyClassInstance("GvyUnitUtility")
            def inFacility = com.navis.argo.ContextHelper.getThreadFacility()
            def equiGkey = unit.getFieldValue("unitPrimaryUe.ueEquipment.ueGkey")

            def retiredUfv = gvyUnitUtil.findRetiredDepartedUfvUnit(inFacility, equiGkey)
            Unit retiredUnit = retiredUfv.ufvUnit
            def prevCmdyId = retiredUnit.getUnitGoods().getGdsCommodity().getCmdyId()

            println("UNITID="+unit.unitId+",CMDY="+prevCmdyId+",FREIGHTKIND="+unitFreightKind)
            if(prevCmdyId.equals("SIT") && unitFreightKind.equals('MTY')) {
                def gvyGateObj = getGroovyClassInstance("GvyCmisGateData")
                def carrierVisitGkey = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvdGkey")
                carrierVisitGkey = carrierVisitGkey != null ? carrierVisitGkey : ''
                String laneId = gvyGateObj.getEntryLaneId(carrierVisitGkey)
                String subject = unit.unitId + " flagged as SIT, IGT as Empty from Gate "+laneId
                String body = "The following ctr was flagged as a SIT but Ingated as an Empty : " + unit.unitId
                def emailSender = getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(EMAIL_FROM, EMAIL_TO, subject, body)
                println("Sending SIT IGT Empty alert to "+EMAIL_TO)
            }
            println("******* GvyCheckSITIgtEmpty ENDS*******")
        }catch(Exception e){
            e.printStackTrace()
        }
    }
}
