/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.ScopeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Facility
import com.navis.argo.business.model.N4EntityScoper
import com.navis.argo.business.reference.CarrierItinerary
import com.navis.argo.business.reference.PointCall
import com.navis.argo.portal.context.ArgoUserContextProvider
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.context.IUserContextProvider
import com.navis.framework.portal.context.PortalApplicationContext
import com.navis.framework.util.TransactionParms
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.scope.ScopeCoordinates
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import com.navis.vessel.business.schedule.VesselVisitDetails

/**
 * Created by ahmadim on 10/1/2015.
 *
 *  Jira: CSDV-3252
 *
 * Purpose: To log additional events on the Units that are created from the Barge Stowplan screen in N4. These events are to match a typical Ingate,
 * receive and Load on the Barge sequence for the containers coming in through the Gate at a non-operational facility and loaded on a Barge sailing
 * to another Facility in the Complex. Events logged are:
 *  - UNIT_IN_GATE
 *  - UNIT_RECEIVE
 *  - UNIT_LOAD
 *
 *  The above events will be logged on the Unit for the Facility where they were loaded e.g. Akutan for a Barge that is coming at this Facility from
 *  Akutan. They will be logged in the correct chronological order, such that if the user clicks on "Apply Date" in the Event History, these events
 *  will appear first.
 *
 *  Deployment: This groovy to be installed as a Code Extension of type GENERAL_NOTICES_CODE_EXTENSION. The groovy to be triggered from a General
 *  Notice based on the event UNIT_CREATE.
 */

class MATBargeStowplan extends AbstractGeneralNoticeCodeExtension {
    public void execute(GroovyEvent inEvent){
        this.log("Execution Started MATCloneUnitForNextFacility");

        /* get the event */

        Event thisEvent = inEvent.getEvent();

        if (thisEvent == null)
            return;

        /* Get the unit and the Booking */

        Unit unit = (Unit) inEvent.getEntity();

        if (unit == null)
            return;

        UnitFacilityVisit ufv = unit.getUfvForFacilityLiveOnly(ContextHelper.getThreadFacility());
        if (ufv == null){
            return;
        }

        CarrierVisit cv = ufv.getInboundCarrierVisit();

        // look only for the Inbound containers on a Vessel
        if (!LocTypeEnum.VESSEL.equals(cv.cvCarrierMode)){
            return;
        }

        // only look for a barge
        VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(cv);
        if (vvd == null || !vvd.isBarge()){
            return;
        }

        // look only for Imports, Exports and Storage containers coming in on the Barge
        if (![UnitCategoryEnum.EXPORT, UnitCategoryEnum.IMPORT,UnitCategoryEnum.STORAGE].contains(unit.getUnitCategory())){
            return;
        }

        // find facility for Barge's load port
        Facility loadFacility = getLoadFacilityForBarge(vvd);

        // A facility matching the Barge's POL must exist, and it must be a non operational facility
        if (loadFacility == null || !loadFacility.fcyIsNonOperational){
            return;
        }

        final UserContext userContext = ContextHelper.getThreadUserContext();
        final UserContext uc = getNewUserContext(loadFacility);

        PersistenceTemplate template = new PersistenceTemplate(uc);
        MessageCollector mc = template.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                try {
                    TransactionParms.getBoundParms().setUserContext(uc);

                    // list the events to be logged, inthe proper order.
                    String [] eventList = new String[3];
                    eventList[0] = "UNIT_IN_GATE";
                    eventList[1] = "UNIT_RECEIVE";
                    eventList[2] = "UNIT_LOAD";

                    for (String evnt: eventList) {
                        EventType event = EventType.findEventType(evnt);

                        if (event != null && !_sm.hasEventTypeBeenRecorded(event, unit)) {
                            // backup the event time a bit, for the previous facility - 5 mins here
                            Date backedUpTime = new Date(System.currentTimeMillis() - (5000 * 60));
                            _sm.recordEvent(event, "Applied through groovy for Barge Stowplan", null, null, unit, (FieldChanges) null, backedUpTime);
                        }
                    }
                }
                finally {
                    // restore original user context
                    TransactionParms.getBoundParms().setUserContext(userContext);
                }
            }
        });

    }

// look for the other port in the rotation and return it if there is a facility for that in the Topology. Returns the first port in itinerary other
// than the port for this facility.
    private Facility getLoadFacilityForBarge(VesselVisitDetails inVvd){
        CarrierItinerary itinerary =  inVvd.getCvdItinerary();
        Facility facility = null;
        if (itinerary == null){
            return null;
        }

        String pointId=null;
        for (PointCall pcall: (List<PointCall>) itinerary.getItinPoints()){
            if (ContextHelper.getThreadFacility().getFcyRoutingPoint().getPointId().equals(pcall.getCallPoint().getPointId())){
                continue;
            } else {
                pointId = pcall.getCallPoint().getPointId();
                // found another point besides the one for this facility. existing..
                break;
            }
        }

        if (pointId != null){
            facility = Facility.findFacility(pointId);
        }

        return facility;
    }

    private UserContext getNewUserContext(Facility inFacility){
        ScopeCoordinates scopeCoordinates = _scoper.getScopeCoordinates(ScopeEnum.YARD, inFacility.getActiveYard().getYrdGkey());
        UserContext uc = ContextHelper.getThreadUserContext();
        return _contextProvider.createUserContext(uc.getUserKey(), uc.getUserId(), scopeCoordinates);
    }

    private N4EntityScoper _scoper = (N4EntityScoper) Roastery.getBeanFactory().getBean(N4EntityScoper.BEAN_ID);
    private ArgoUserContextProvider _contextProvider = (ArgoUserContextProvider) PortalApplicationContext.getBean(IUserContextProvider.BEAN_ID);
    private ServicesManager _sm = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);

}
