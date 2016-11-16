import com.navis.argo.business.api.ArgoUtils;
import com.navis.argo.business.api.ServicesManager;
import com.navis.argo.business.atoms.*;
import com.navis.argo.business.extract.ChargeableUnitEvent;
import com.navis.argo.business.model.LocPosition;
import com.navis.argo.business.reference.Container;
import com.navis.argo.business.reference.Equipment;
import com.navis.framework.business.BaseSessionPea;
import com.navis.framework.business.Roastery;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.portal.FieldChanges;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.util.BizViolation;
import com.navis.inventory.InventoryBizMetafield;
import com.navis.inventory.InventoryPropertyKeys;
import com.navis.inventory.business.api.*;
import com.navis.inventory.business.atoms.*;
import com.navis.services.business.api.EventManager;
import com.navis.services.business.event.Event;
import com.navis.services.business.rules.EventType;
import java.util.List;
import java.util.Map;
import com.navis.inventory.business.units.*;

public class StuffManager extends BaseSessionPea
{

    public StuffManager()
    {
    }


    public UnitFacilityVisit coreStuffUfv(UnitFacilityVisit inEmptyUfv, UnitFacilityVisit inBbkUfv)
            throws BizViolation
    {
        Unit emptyUnit = inEmptyUfv.getUfvUnit();
        Unit bbkUnit = inBbkUfv != null ? inBbkUfv.getUfvUnit() : null;
        ServicesManager srvcMgr = (ServicesManager)Roastery.getBean("servicesManager");
        String emptyUnitId = emptyUnit.getHumanReadableKey();
        BizViolation bv = null;
        if(!UfvTransitStateEnum.S40_YARD.equals(inEmptyUfv.getUfvTransitState()))
            bv = BizViolation.create(InventoryPropertyKeys.NOT_INYARD, bv, EventEnum.UNIT_STUFF.getId(), emptyUnitId);
        if(FreightKindEnum.FCL.equals(emptyUnit.getUnitFreightKind()))
            bv = BizViolation.createFieldViolation(InventoryPropertyKeys.TRANSLOAD_TO_NOT_EMPTY, bv, InventoryBizMetafield.UNIT_TRANSLOAD_TO_ID, emptyUnitId);
        Equipment eq = emptyUnit.getPrimaryEq();
        if(eq == null || !EquipClassEnum.CONTAINER.equals(eq.getEqClass()))
            bv = BizViolation.create(InventoryPropertyKeys.TRANSLOAD_NOT_CONTAINERIZED, bv, emptyUnitId);
        if(bv != null)
            throw bv;
        bv = srvcMgr.verifyEventAllowed(EventEnum.UNIT_STUFF, emptyUnit);
        if(bv != null)
            throw bv;
        emptyUnit.makeRetired();
        inEmptyUfv.setUfvTimeOut(inEmptyUfv.getUfvTimeComplete());
        UnitEventExtractManager.updateStorageEventEndTime(inEmptyUfv, ChargeableUnitEventTypeEnum.STORAGE);
        _hibernateApi.flush();
        com.navis.argo.business.model.Facility facility = inEmptyUfv.getUfvFacility();
        String slot = inEmptyUfv.getUfvLastKnownPosition().getPosSlot();
        if(inBbkUfv == null)
        {
            UnitFacilityVisit fullUfv = getMgr().createYardBornUnit( eq, inEmptyUfv.getUfvLastKnownPosition(), "stuffed");
            //  System.out.println("fullUfv="+fullUfv.properties);
            //  System.out.println("pos="+ inEmptyUfv.getUfvLastKnownPosition());
            Unit fullUnit = fullUfv.getUfvUnit();
            fullUnit.setUnitCategory(UnitCategoryEnum.EXPORT);
            fullUnit.setUnitDeckRqmnt(VslDeckRqmntEnum.EITHER);
            fullUnit.setUnitFreightKind(FreightKindEnum.FCL);
            fullUnit.setUnitWeightToTareWeight();
            fullUnit.setUnitLineOperator(emptyUnit.getUnitLineOperator());
            return fullUfv;
        } else
        {
            bbkUnit.setUnitFreightKind(FreightKindEnum.FCL);
            UnitEquipment ue = bbkUnit.attachEquipment(eq, EqUnitRoleEnum.PRIMARY, false);
            _hibernateApi.save(ue);
            double tareKg = eq.getEqTareWeightKg().doubleValue();
            double bbkKg = bbkUnit.getUnitGoodsAndCtrWtKg() != null ? bbkUnit.getUnitGoodsAndCtrWtKg().doubleValue() : 0.0D;
            double weight = bbkKg + tareKg;
            bbkUnit.setUnitGoodsAndCtrWtKg(new Double(weight));
            return inBbkUfv;
        }
    }


    private UnitManager getMgr()
    {
        return (UnitManager)Roastery.getBean("unitManager");
    }

    private UnitFinder getFndr()
    {
        return (UnitFinder)Roastery.getBean("unitFinder");
    }
}