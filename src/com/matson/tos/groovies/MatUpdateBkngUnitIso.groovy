import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.inventory.business.units.*
import com.navis.framework.util.BizViolation
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.framework.business.Roastery
import com.navis.inventory.business.api.UnitFinder
import com.navis.argo.business.reference.Container
import com.navis.argo.ContextHelper
import com.navis.services.business.event.GroovyEvent;
import com.navis.inventory.business.units.Unit;

import com.navis.framework.portal.FieldChanges
import com.navis.services.business.api.EventManager
import com.navis.services.business.rules.EventType

import com.navis.framework.business.Roastery
import com.navis.argo.business.api.ServicesManager
import com.navis.framework.portal.query.DomainQuery;
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.inventory.InventoryField;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.api.UnitField
import com.navis.argo.business.model.CarrierVisit
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery;

import com.navis.orders.business.eqorders.Booking;
import com.navis.orders.business.eqorders.EquipmentOrderItem;
import com.navis.argo.business.reference.EquipType;

import com.navis.argo.ArgoPropertyKeys;
import com.navis.argo.ArgoRefField;



class MatUpdateBkngUnitIso{

    public boolean execute(Map params){
        try {
            String bkngGkey = "131462476"
            setEquipSeries(bkngGkey);

        }
        catch (Exception e2) {
            println("Exception  " + e2);
        }
    }

    public void setEquipSeries(String bkngGkey)
    {

        try {
            DomainQuery dq = QueryUtils.createDomainQuery("EquipmentOrderItem")
                    .addDqPredicate(PredicateFactory.eq(InventoryField.EQBOI_ORDER,bkngGkey));
            println("dq:::::::::"+dq)

            def itemList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After itemList :::: "+itemList.size());
            def equipType = null;
            if(itemList != null) {
                println("Here");
                Iterator iter = itemList.iterator();
                while(iter.hasNext()) {
                    def eqoItem = iter.next();
                    println("Inside loop");
                    def equipSeries = eqoItem.getFieldValue("eqoiSerialRanges");
                    def eqoiEqIsoGroup = eqoItem.getFieldValue("eqoiEqIsoGroup");
                    def eqoiEqSize = eqoItem.getFieldValue("eqoiEqSize");
                    def eqoiEqHeight = eqoItem.getFieldValue("eqoiEqHeight");
                    def eqoiEqIsoGroupKey = eqoiEqIsoGroup.getKey();
                    def eqoiEqHeightKey = eqoiEqHeight.getKey();
                    def eqoiEqSizeKey = eqoiEqSize.getKey();

                    println("equipSeries:::"+eqoiEqIsoGroupKey+eqoiEqHeightKey+"::"+eqoiEqSizeKey+"::::"+equipSeries);
                    /*if (eqoiEqSizeKey == "NOM20" && eqoiEqHeightKey == "NOM86"){
                        equipSeries = "MATU 2072010-2082009"
                        equipType = "D20"

                    }
                    else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM86"){
                        equipSeries = "MATU 2265060-2275059"
                        equipType = "D40"
                    }
                    else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM96"){
                        equipSeries = "MATU 2459510-2504509"
                        equipType = "D40H"
                    }*/
                    equipSeries = "MATU 2072010-2082009, MATU 2265060-2275059, MATU 2459510-2504509"
                    println("equipSeries:::"+equipType+"::::"+equipSeries);

                    eqoItem.setEqoiSerialRanges(equipSeries);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

    public void getBooking(String bkngGkey, Object booking)
    {
        def bkgNotes = booking.getEqoNotes()
        bkgNotes = bkgNotes==null?"":bkgNotes;
        boolean isScrap = false;
        if(bkgNotes.contains("METAL SCRAP")||bkgNotes.contains("SCRAP METAL")) {
            isScrap = true;
        }
        try {
            DomainQuery dq = QueryUtils.createDomainQuery("EquipmentOrderItem")
                    .addDqPredicate(PredicateFactory.eq(InventoryField.EQBOI_ORDER,bkngGkey));
            println("dq:::::::::"+dq)

            def itemList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After itemList :::: "+itemList.size());

            if(itemList != null) {
                Iterator iter = itemList.iterator();
                while(iter.hasNext()) {

                    def eqoItem = iter.next();
                    def gkey = eqoItem.getFieldValue("eqboiGkey");
                    def EqboiOrder = eqoItem.getFieldValue("eqboiOrder");
                    def eqoiEqSize = eqoItem.getFieldValue("eqoiEqSize");
                    def eqoiEqHeight = eqoItem.getFieldValue("eqoiEqHeight");
                    def eqoiEqIsoGroup = eqoItem.getFieldValue("eqoiEqIsoGroup");
                    def eqoiQty = eqoItem.getFieldValue("eqoiQty");
                    def eqtypId = eqoItem.getFieldValue("eqoiSampleEquipType.eqtypId");
                    def assignType = null;

                    println("eqtypId for the Booking Item :::: "+eqtypId);

                    if (eqtypId == null){

                        def eqoiEqIsoGroupKey = eqoiEqIsoGroup.getKey();
                        def eqoiEqHeightKey = eqoiEqHeight.getKey();
                        def eqoiEqSizeKey = eqoiEqSize.getKey();
                        println("Booking Item attributes ::: "+eqoiEqIsoGroupKey +"::"+eqoiEqHeightKey+"::"+eqoiEqSizeKey);

                        if (eqoiEqIsoGroupKey == "BU"){
                            if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM90"){
                                assignType = "V48"
                            }
                            else if (eqoiEqSizeKey == "NOM42" && eqoiEqHeightKey == "NOM90"){
                                assignType = "L48"
                            }
                        }

                        else if (eqoiEqIsoGroupKey == "CH"){
                            if (eqoiEqSizeKey == "NOM20"){
                                assignType = "C20"
                            }
                            else if (eqoiEqSizeKey == "NOM24"){
                                assignType = "C24"
                            }
                            else if (eqoiEqSizeKey == "NOM40"){
                                assignType = "C40"
                            }
                            else if (eqoiEqSizeKey == "NOM45"){
                                assignType = "C45"
                            }
                        }

                        else if (eqoiEqIsoGroupKey == "GP"){
                            if (eqoiEqSizeKey == "NOM20" && eqoiEqHeightKey == "NOM86"){
                                assignType = "D20"
                                if(isScrap)
                                    eqoItem.setEqoiSerialRanges("MATU 207201-208200")
                            }
                            else if (eqoiEqSizeKey == "NOM20" && eqoiEqHeightKey == "NOM80"){
                                assignType = "D20L"
                            }
                            else if (eqoiEqSizeKey == "NOM20" && eqoiEqHeightKey == "NOM96"){
                                assignType = "D20H"
                            }
                            else if (eqoiEqSizeKey == "NOM24" && eqoiEqHeightKey == "NOM86"){
                                assignType == "D24"
                            }
                            else if (eqoiEqSizeKey == "NOM24" && eqoiEqHeightKey == "NOM96"){
                                assignType == "D24H"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM114"){
                                assignType = "A40L"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM130"){
                                assignType = "A40"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM40"){
                                assignType = "F40K"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM86"){
                                assignType = "D40"
                                if(isScrap)
                                    eqoItem.setEqoiSerialRanges("MATU 226506-227505")
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM90"){
                                assignType = "G40"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM90"){
                                assignType = "G40"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM96"){
                                assignType = "D40H"
                                if(isScrap)
                                    eqoItem.setEqoiSerialRanges("MATU 245951-250450")
                            }
                            else if (eqoiEqSizeKey == "NOM45" && eqoiEqHeightKey == "NOM86"){
                                assignType = "D45"
                            }
                            else if (eqoiEqSizeKey == "NOM45" && eqoiEqHeightKey == "NOM96"){
                                assignType = "D45H"
                            }
                        }

                        else if (eqoiEqIsoGroupKey == "PF"){
                            if (eqoiEqSizeKey == "NOM20" && eqoiEqHeightKey == "NOM86"){
                                assignType = "F20"
                            }
                            else if (eqoiEqSizeKey == "NOM24" && eqoiEqHeightKey == "NOM86"){
                                assignType = "F24"
                            }
                            else if (eqoiEqSizeKey == "NOM24" && eqoiEqHeightKey == "NOM96"){
                                assignType = "F24H"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM86"){
                                assignType = "F40"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM96"){
                                assignType = "F40H"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM43"){
                                assignType = "F40L"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM130"){
                                assignType = "F40M"
                            }
                        }
                        else if (eqoiEqIsoGroupKey == "PL"){
                            if (eqoiEqSizeKey == "NOM45" && eqoiEqHeightKey == "NOM96"){
                                assignType = "F45H"
                            }
                        }
                        else if (eqoiEqIsoGroupKey == "RE"){
                            if (eqoiEqSizeKey == "NOM20" && eqoiEqHeightKey == "NOM86"){
                                assignType = "R20"
                            }
                            if (eqoiEqSizeKey == "NOM20" && eqoiEqHeightKey == "NOM80"){
                                assignType = "R20L"
                            }
                            if (eqoiEqSizeKey == "NOM24" && eqoiEqHeightKey == "NOM86"){
                                assignType == "R24"
                            }
                            if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM86"){
                                assignType = "R40"
                            }
                            if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM96"){
                                assignType = "R40H"
                            }
                            if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM80"){
                                assignType = "R40L"
                            }
                        }
                        else if (eqoiEqIsoGroupKey == "TG"){
                            if (eqoiEqSizeKey == "NOM22" && eqoiEqHeightKey == "NOM86"){
                                assignType = "V24"
                            }
                        }
                        else if (eqoiEqIsoGroupKey == "TN"){
                            if (eqoiEqSizeKey == "NOM20" && eqoiEqHeightKey == "NOM80"){
                                assignType = "T20L"
                            }
                            else if (eqoiEqSizeKey == "NOM20" && eqoiEqHeightKey == "NOM86"){
                                assignType = "T20"
                            }
                            else if (eqoiEqSizeKey == "NOM24" && eqoiEqHeightKey == "NOM86"){
                                assignType = "T24"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM86"){
                                assignType = "T40"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM96"){
                                assignType = "T40H"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM43"){
                                assignType = "T40L"
                            }
                        }
                        else if (eqoiEqIsoGroupKey == "UT"){
                            if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM80"){
                                assignType = "D40O"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM86"){
                                assignType = "O40"
                            }
                            else if (eqoiEqSizeKey == "NOM40" && eqoiEqHeightKey == "NOM90"){
                                assignType = "F45"
                            }
                        }

                        println("assignType :::"+assignType);
                        if (assignType != null) {
                            def equipType = EquipType.findEquipType(assignType);
                            eqoItem.setEqoiSampleEquipType(equipType);
                        }
                    }

                    eqtypId = eqoItem.getFieldValue("eqoiSampleEquipType.eqtypId");
                    println(gkey + ":"+EqboiOrder+":"+eqoiEqSize +":"+ eqoiEqIsoGroup +":"+ eqoiEqHeight +":"+ eqoiQty+":"+eqtypId)
                }
            }

        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }
}

