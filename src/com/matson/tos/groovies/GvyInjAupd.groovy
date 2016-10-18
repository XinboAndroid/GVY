package com.matson.tos.groovies

/*
**********************************************************************
* Srno   Date	             Changer	 Change Description
* A1     12/29/08       Glenn Raposo	 Unit Field Tracker code Added
* A2     02/19/09       Steven Bauer	 Added Equipment Grade
* A3	 05/27/09       Steven Bauer	 Log UPU changes
* A4     07/16/2008    Steven Bauer	 Don't email no such unit message
* A5     10/28/09      Glenn Raposo      Add MajorFeature for UP1(to be added once acets side is done)
* A6     09/01/10      GR                Uncommented as part for gems testing.
* A7     10/13/11      GR                TOS2.1 Method Change
* A8     01/11/12      GR                Added Biz task method
**********************************************************************
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.atoms.DataSourceEnum;
import com.navis.framework.util.BizViolation
import com.navis.services.business.event.GroovyEvent
import com.navis.argo.ArgoBizMetafield
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.argo.business.api.IBizUnitManager
import com.navis.argo.business.api.IEquipStateManager
import com.navis.framework.business.Roastery
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.reference.Equipment;
import com.navis.argo.business.reference.Accessory;
import com.navis.inventory.business.units.EquipmentState;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.argo.business.atoms.UnitCategoryEnum;
import com.navis.inventory.business.api.UnitFinder;
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.reference.EquipGrade
import com.navis.argo.business.reference.Equipment;



class GvyInjAupd extends GroovyInjectionBase {
    public String execute(Map inParameters) {
        com.navis.argo.ContextHelper.setThreadExternalUser("ACETS");
        def gvyBaseClass = new GroovyInjectionBase();
        def eqId = (String) inParameters.get("equipment-id");
        def typeCodeUpd = (String) inParameters.get( "typeCode");
        def gradeUpd = (String) inParameters.get( "grade");
        def primCarrierUpd = (String) inParameters.get( "primCarrier");
        def primControllerUpd = (String) inParameters.get( "primController");
        def acetsMsgType = (String) inParameters.get( "acetsMsgType");
        def majorFeature = (String) inParameters.get( "majorFeature"); //A5

        def gvyEquipmentLookup = gvyBaseClass.getGroovyClassInstance("GvyEquipmentLookup");

        ContextHelper.setThreadExternalUser("ACETS");

// A3, store a note to add to the event
        String note = "";

        def equipment =  gvyEquipmentLookup.getEquipment( eqId);
//def equipment =  Equipment.findEquipment(eqId);

//A1- tracker
        def tracker;
        def eqTracker
        GroovyApi gvyApi;
        gvyApi = new GroovyApi()
        def gvyUnitFldTracker = gvyApi.getGroovyClassInstance("GvyUnitFieldTracker");
//A1- Tracker Ends

        if ( equipment == null) {
            log( "ERR_GVY_UPD_001. Could not find equipment: " + eqId);
            return;
        }
//A1
        eqTracker = gvyUnitFldTracker.createFieldChangeTrackerEquip(equipment)

        note += "Type: "+equipment.getFieldValue("eqEquipType.eqtypId")+"->"+typeCodeUpd;
        equipment.upgradeEqType( typeCodeUpd, DataSourceEnum.USER_DBA);
        def operator = com.navis.argo.business.model.Operator.findOperator("MATSON");
        def state =    com.navis.inventory.business.units.EquipmentState.findEquipmentState(equipment,operator);

        if(gradeUpd != null) {
            if(state != null) {
                state.eqsGradeID = EquipGrade.findOrCreateEquipGrade(gradeUpd);
                note += " Grade: "+state.eqsGradeID+"->"+gradeUpd;

            }
        }

//A5 -Start
        println("acetsMsgType :"+acetsMsgType+" majorFeature:"+majorFeature+" state :"+state)
        try
        {
            IEquipStateManager eqManager = (IEquipStateManager)Roastery.getBean("equipStateManager");
            if((majorFeature == null || majorFeature.length() == 0) && state != null){
                if(state.getEqsGradeID() != null){
                    state.setEqsGradeID(null);
                }
            }
            else if ((majorFeature != null && majorFeature.length() > 0) && state != null){
                EquipGrade eqGrade = EquipGrade.findEquipGrade(majorFeature)
                if(eqGrade != null){
                    //eqManager.upgradeEqGrade(state.eqsEquipment.eqGkey, eqGrade);
                    state.upgradeEqGrade(equipment, eqGrade)  //A7
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
//A5 -Ends


        def bzu = null;
        IBizUnitManager bum = (IBizUnitManager)Roastery.getBean("bizUnitManager");
        bzu = ScopedBizUnit.findScopedBizUnit( primControllerUpd, BizRoleEnum.LEASINGCO);
        if ( bzu == null) {
            fail( "ERR_GVY_UPD_002. Can not find leasing company: " + primControllerUpd + " in N4.");
        }

        state.upgradeEqOwner(equipment, bzu, DataSourceEnum.USER_DBA) //A7
        if(state != null) {
            note += " Owner: "+state.getFieldValue("eqsEqOwner.bzuId")+"->"+primControllerUpd;


        } else {
            note += " Owner: ->"+primControllerUpd;
        }

        // update primary carrier
        bzu = ScopedBizUnit.findScopedBizUnit( primCarrierUpd, BizRoleEnum.LINEOP);
        if ( bzu == null) {
            fail( "ERR_GVY_UPD_003. Can not find line operator: " + primCarrierUpd + " in N4.");
        }

        try {
            def ufv;
            def unit;
            try {
                def uf = (UnitFinder)Roastery.getBean("unitFinder");
                def facility = getFacility();
                unit = uf.findActiveUnit(facility.getFcyComplex(), equipment);
                //A1 - Tracker Change
                //tracker = gvyUnitFldTracker.createFieldChangeTracker(unit)
            } catch ( Exception ex) {
                ex.printStackTrace()
                unit = null;
            }

            if ( unit != null) {

                if ( UnitCategoryEnum.STORAGE.equals(unit.getUnitCategory())) {

                    bum.upgradeEqOperator( unit.unitPrimaryUe.ueEquipmentState.eqsGkey, bzu, DataSourceEnum.USER_DBA);
                    if( unit.unitPrimaryUe.ueEquipmentState.eqsEqOperator != null ) {
                        note += " Oper: "+ unit.unitPrimaryUe.ueEquipmentState.eqsEqOperator.bzuId+"->"+primCarrierUpd;
                        println("Note After changing the EQOperator ::"+note);
                    }
                    def primEq = unit.getPrimaryEq();
                    if ( primEq.equals( equipment)) {
                        unit.updateLineOperator( bzu);
                    }
                }
            }

            //A1 - Tracker Change Equip
            if(equipment != null)
            {
                def eqChanges = eqTracker.getChanges(equipment);
                if(eqChanges != null && eqChanges.getFieldChangeCount() != 0) {
                    unit.recordUnitEvent(com.navis.argo.business.atoms.EventEnum.UNIT_PROPERTY_UPDATE,eqChanges , "Field Updated UPU Equipment Information");
                }
            }
            //A1 - Tracker Change Commented out as Currently Generating event UNIT_OPERATOR_CHANGE
/*       if(unit != null)
       {
          def changes = tracker.getChanges(unit);
          if(changes != null && changes.getFieldChangeCount() != 0) {
           unit.recordUnitEvent(com.navis.argo.business.atoms.EventEnum.UNIT_PROPERTY_UPDATE,changes , "Field Updated UPU Unit Information");
          }
       } */
            //A1- Tracker Ends
            def event = new GroovyEvent( null, unit);
            event.postNewEvent( "UPU", "ACETS "+note);

        } catch ( Exception gex) {
            gex.printStackTrace()
            log( "Could not update eqOperator for " + eqId);
        }

    }
}
