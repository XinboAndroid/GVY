/*
 * Copyright (c) 2011 Navis LLC. All Rights Reserved.
 *
 */

/**
 * Created by IntelliJ IDEA. User: isimmons Date: 10/1/11 Time: 12:42 PM To change this template use File | Settings | File Templates.
 */
/*

Groovy code called from the gate form the truckvisit level

Aim to produce the graymangate message to be saved as a flat file
The file is picked up by the WNET MTS system.

example of draymangate message

<draymanGate time=20021204120000 type=SiteArrival>
     <truck id=524>
         <type>Drayman</type>
     </truck>
     <container1 id=MATU1234567>
         <tagID>19100210</tagID>
         <length>20</length>
         <weight>1985100</weight>
         <height>8</height>
         <loadStatus>L</loadStatus>
         <chassisPosition>1</chassisPosition>
         <custom1></custom1>
     </container1>
     <container2 id=MATU1234568>
         <tagID>19100432</tagID>
         <length>20</length>
         <weight>1985100</weight>
         <height>8</height>
         <loadStatus>L</loadStatus>
         <chassisPosition>2</chassisPosition>
         <custom1></custom1>
     </container2>
     <gate>
         <type>Inbound</type>
         <lane>5</lane>
     </gate>
</draymanGate>

*/

import java.io.File;
import java.util.Date;
import java.util.TimeZone;

import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.ContextHelper;
import com.navis.argo.business.api.GroovyApi;
import com.navis.argo.business.atoms.EventEnum;
import com.navis.argo.business.reference.Equipment;
import com.navis.external.framework.ui.AbstractTableViewCommand;
import com.navis.external.framework.ui.EUIExtensionHelper;
import com.navis.framework.util.DateUtil;
import com.navis.framework.util.message.MessageLevel;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.road.business.model.TruckTransaction;
import com.navis.road.business.model.TruckVisitDetails;
import com.navis.xpscache.business.atoms.EquipBasicLengthEnum;

class MatsonWNETdraymangate extends GroovyInjectionBase {
    public void execute(inDao) {
        if (inDao == null) {
            //showMessage("null Dao");
            return;
        }
        //showMessage("starting");

        // now find the truck visit details
        TruckVisitDetails tvdtls = inDao.getTv();
        if (tvdtls == null) {
            //showMessage("truck visit is null");
            return;
        }

        def _trkid = tvdtls.getTvdtlsTruckId();  // or if we need licnbr tvdtls.getTvdtlsTruckLicenseNbr()
        //wnet tag id is here
        String _tagId = tvdtls.getTvdtlsFlexString01();

        def createdrayman = false;
        //showMessage("trkid is " + _trkid);
        //showMessage("tag id is " + _tagId);

        // check if it is exit or entry gate
        String _gateid = "1";
        def _gateclass = "IN";
        def _entryLane = tvdtls.getTvdtlsEntryLane();
        def _exitLane = tvdtls.getTvdtlsExitLane();

        if ((_entryLane == null) && (_exitLane == null)) {
            //showMessage("No entry or exit gates");
            return;
        }

        if (_exitLane != null) {
            //showMessage("Exit gate");
            _gateid = inDao.tv.tvdtlsExitLane.laneId;
            _gateclass = tvdtls.getTvdtlsExitLane().getLaneClass().getName();
        }
        if ((_entryLane != null) && (_exitLane == null)) {
            //showMessage("Entry gate");
            _gateid = inDao.tv.tvdtlsEntryLane.laneId;
            _gateclass = tvdtls.getTvdtlsEntryLane().getLaneClass().getName();
        }

        //showMessage("gate is " + _gateid);
        //showMessage("gate class is " + _gateclass);

        // Check the DRAYMAN parameters
        GroovyApi apiG = new GroovyApi();
        String _inType1 = "DRAYMAN";
        String _inId1 = "MESSAGE";
        String _inId2 = null;
        String _inId3 = null;
        int _inDataValueIdx = 1;

        //SEND INGATE
        if (_gateclass == "IN") {
            _inId2 = "SEND";
            _inId3 = "INGATE";
            String _sendIngate = apiG.getReferenceValue(_inType1, _inId1, _inId2, _inId3, _inDataValueIdx);
            if (_sendIngate != "TRUE") {
                //showMessage("Drayman parameter SEND/INGATE not set to TRUE");
                return;
            }
        }

        // SEND OUTGATE
        if (_gateclass == "OUT") {
            _inId2 = "SEND";
            _inId3 = "OUTGATE";
            String _sendOutgate = apiG.getReferenceValue(_inType1, _inId1, _inId2, _inId3, _inDataValueIdx);
            if (_sendOutgate != "TRUE") {
                //showMessage("Drayman parameter SEND/OUTGATE not set to TRUE");
                return;
            }
        }

        // DRAYMAN File Directory
        _inId2 = "DIRECTORY";
        _inId3 = null;
        String _dir = apiG.getReferenceValue(_inType1, _inId1, _inId2, _inId3, _inDataValueIdx);

        // SEND UPDATES ONLY
        _inId2 = "SEND";
        _inId3 = "UPDATES_ONLY";
        String _sendUpdatesOnly = apiG.getReferenceValue(_inType1, _inId1, _inId2, _inId3, _inDataValueIdx);

        // FIND SNXMSG tag id
        // ONLY if tagId is null
        if (_tagId == null) {
            //showMessage("No WNET tag details on this truck visit");
            // get lane details
            _inId1 = "SNXMSG";
            _inId2 = "LANE";
            _inId3 = _gateid;
            String _snxDateString = apiG.getReferenceValue(_inType1, _inId1, _inId2, _inId3, _inDataValueIdx);
            _inDataValueIdx = 2;
            _tagId = apiG.getReferenceValue(_inType1, _inId1, _inId2, _inId3, _inDataValueIdx);

            // get time tolerance
            _inId3 = "TIME_TOLERANCE";
            _inDataValueIdx = 1;
            String _minToleranceString = apiG.getReferenceValue(_inType1, _inId1, _inId2, _inId3, _inDataValueIdx);
            //showMessage("Time tolernace is " + _minToleranceString);
            if (_minToleranceString == null) {
                _minToleranceString = "20";
            }

            // need to check if _snxDateTime is close to this time.
            Date _currentDate = new Date();
            Date _snxDate = new Date();
            TimeZone _timeZone = ContextHelper.getThreadUserTimezone();

            //showMessage("Time zone is " + _timeZone);
            try {
                _snxDate = DateUtil.dateStringToDate(_snxDateString);
            } catch (Exception e) {
                log("Error is converting general reference date " + e);
            }

            //showMessage("snxdate is " + _snxDate);
            double _diffmin = DateUtil.differenceInMinutesMinusDaysAndHours(_snxDate, _currentDate, _timeZone);
            // now check if this i greater than  _minTolerance = 5;
            double _minTolerance = new Integer(_minToleranceString);
            //showMessage("diff in minutes is " + _diffmin);

            if (_diffmin > _minTolerance) {
                //showMessage("general reference tag is too old");
                _tagId = null;
            }
        }

        // if tag_id is null
        // return
        if (_tagId == null)
            _tagId = "";

        if (_tagId == "") {
            //showMessage("No tag id - exitting");
            return;
        }

        // count flags for Receive and Delivery
        // can only do single containers at present
        def _recCount = 0;
        def _delCount = 0;
        for (TruckTransaction tran : inDao.getTv().getActiveTransactions()) {

            def ctr = tran.getEqNbr();
            //showMessage("transaction is " + ctr);

            //showMessage("Starting the container tag update on truck transaction  ");

            //now find the transactions details of the unit
            def _trangkey = tran.getTranNbr();
            def _booking = tran.getTranEqoNbr();
            //showMessage(" gkey is " + _trangkey);
            //showMessage("booking is " + _booking);

            def _booking_type = tran.getTranSubType().getName();
            //showMessage("booking type " + _booking_type);

            String _ctrid1 = tran.getTranCtrNbr();
            //showMessage("container is " + _ctrid1);
            // check if CTR already has tagId on it
            //showMessage("Now get the Tagid from the Unit");
            UnitFacilityVisit ufv = findActiveUfv(_ctrid1);
            Unit unit = null;
            if (ufv == null) {
                //showMessage("ufv is null");
                unit = getUnitFinder().findActiveUnit(ContextHelper.getThreadComplex(), Equipment.findEquipment(_ctrid1));
            } else {
                unit = ufv.getUfvUnit();
            }
            Equipment eq = unit.getPrimaryEq();

            if (unit == null) {
                //showMessage(" No unit found");
            } else {
                //showMessage("found unit");
                def _eq_tagId = eq.getEqTransponderId();
                def _ctrid1_tagId = unit.getUnitFlexString15();
                //showMessage("Eq tag id is " + _eq_tagId);
                //showMessage("unit tag id is " + _ctrid1_tagId);
                if (_eq_tagId == _tagId) {
                    //showMessage("Ctr already has tagID on it");
                    unit.setUnitFlexString15(_tagId);
                    unit.recordUnitEvent(EventEnum.UNIT_PROPERTY_UPDATE, null, "updated WNET tag id from CTR trnspId");

                    if (_sendUpdatesOnly == "TRUE") {
                        //showMessage("UPDATES ONLY not set to TRUE");
                        return;
                    }
                } else {
                    eq.setEqTransponderId(_tagId);
                    unit.setUnitFlexString15(_tagId);
                    //showMessage("Updated tag id to " + _tagId);
                    // Record an event
                    unit.recordUnitEvent(EventEnum.UNIT_PROPERTY_UPDATE, null, "updated WNET tag id");
                }
            }

            def _eqsz1 = "20";
            if (EquipBasicLengthEnum.BASIC40.equals(tran.getTranEqLength(EquipBasicLengthEnum.BASIC40))) {
                _eqsz1 = "40";
            } else {
                _eqsz1 = "20";
            }
            //showMessage("ctrid1 is " + _ctrid1);
            //showMessage("size is " + _eqsz1);
            def _eqwt1 = null;
            def _eqwt11 = tran.getTranCtrGrossWeight();
            if (_eqwt11 != null) {
                _eqwt1 = _eqwt11.intValue();
            }
            def _eqwt2 = tran.getTranCtrNetWeight();
            String _eqht = null;
            def _eqht01 = tran.getTranEqoEqHeight();
            if (_eqht01 != null) {
                _eqht = tran.getTranEqoEqHeight().getName();
            }
            def _chspos1 = tran.getTranCtrTruckPosition();
            //showMessage("ht1 is " + _eqht);
            //showMessage("wt1 is " + _eqwt1);
            //showMessage("wt2 is " + _eqwt2);
            //showMessage("chs1 is " + _chspos1);
            def _eqht1 = "8";
            if (_eqht.length() > 4) {
                _eqht1 = _eqht.substring(3, 4);
            }
            //showMessage("ht1 is " + _eqht1);

            def _yardBlock = null;
            def _bay = "";
            if (tran.getTranCtrPosition() != null) {
                if (tran.getTranCtrPosition().getYardBin() != null) {
                    if (tran.getTranCtrPosition().getYardBin().getBlock() != null) {
                        _yardBlock = tran.getTranCtrPosition().getYardBin().getBlock().getBlockName();
                    }
                }
                _bay = tran.getTranCtrPosition().getPosSlot();
            }
            //showMessage("bay is " + _bay);

            if (tran.isReceival()) {

                if (_gateclass != "IN") {
                    // exit because we are only doing Deliveries at the outgate
                    //showMessage("Delivery not at the Ingate");
                    return;
                }
                // start logging that we are working..
                //showMessage("Starting the container tag update on receival truck visit ");
                createdrayman = true;

                if ((_booking_type == "RE") || (_booking_type == "RM")) {
                    _recCount = _recCount + 1;
                }
                //showMessage("recCount " + _recCount);
                if (_recCount > 1) {
                    createdrayman = false;
                    return;
                }
                // now setup the drayman message variables
                def _msgtype = "SiteArrival";
                def _type = "Drayman";
                def _load1 = "L";
                def _direction = "Inbound";

                if (createdrayman) {
                    createdrayman1(_ctrid1, _trangkey, _msgtype, _trkid, _type, _tagId, _eqsz1, _eqwt1, _eqht1, _load1, _chspos1, _direction,
                            _gateid, _bay, _dir);
                }
            }

            if (tran.isDelivery()) {

                if (_gateclass != "OUT") {
                    // exit because we are only doing Deliveries at the outgate
                    //showMessage("Delivery not at the Outgate");
                    return;
                }
                // start logging that we are working..
                //showMessage("Starting the container tag update on delivery truck visit ");
                createdrayman = true;

                if ((_booking_type == "DI") || (_booking_type == "DM")) {
                    _delCount = _recCount + 1;
                }

                //showMessage("recCount " + _recCount);
                if (_delCount > 1) {
                    createdrayman = false;
                    return;
                }
                // Check if Unit already has this tag id

                // Update the Container flexfield with the tag id

                def _msgtype = "Pickup";
                def _type = "Drayman";
                def _load1 = "S";
                def _direction = "Outbound";

                if (createdrayman) {
                    createdrayman1(_ctrid1, _trangkey, _msgtype, _trkid, _type, _tagId, _eqsz1, _eqwt1, _eqht1, _load1, _chspos1, _direction,
                            _gateid, _bay, _dir);
                }
            }

            //showMessage("at end of is receival");
        }
        //showMessage("at end of tran for loop");
    }

    public void createdrayman1(_ctrid1, _trangkey, _msgtype, _trkid, _type, _tagId, _eqsz1, _eqwt1, _eqht1, _load1, _chspos1, _direction, _gateid,
                               _bay, _dir) {

        //showMessage("at the print");
        // now create the drayman xml file
        //showMessage("gen ref value is " + _dir);

        //showMessage("gen ref value updated is " + _dir);
        def _xxdir = _dir.toLowerCase();
        //showMessage("gen ref value updated is " + _xxdir);

        _dir = _xxdir;

        def filedir = new File(_dir);
        if (!filedir.exists()) {
            //showMessage("The directory defined does NOT exist " + _dir);
            _dir = "";
        }

        //showMessage("gen ref value updated 9 is " + _dir);

        def _date = new Date().format('yyyyMMddHHmmss'); // "20021204120000";
        //showMessage("date is " + _date);
        try{
            def drayfile = "$_dir/drayman_" + _trangkey + "_" + _ctrid1 + "_" + _bay + "_" + _date+".xml";
            def draymanfile = new File(drayfile);

            //showMessage("file name is " + drayfile);

            draymanfile.append("<draymanGate time=\"" + _date + "\" type=\"" + _msgtype + "\">\n");
            draymanfile.append("<truck id=\"" + _trkid + "\">\n");
            draymanfile.append("<type>" + _type + "</type>\n");
            draymanfile.append("</truck>\n");
            draymanfile.append("<container1 id=\"" + _ctrid1 + "\">\n");
            draymanfile.append("<tagID>" + _tagId + "</tagID>\n");
            draymanfile.append("<length>" + _eqsz1 + "</length>\n");
            draymanfile.append("<weight>" + _eqwt1 + "</weight>\n")
            draymanfile.append("<height>" + _eqht1 + "</height>\n");
            draymanfile.append("<loadStatus>" + _load1 + "</loadStatus>\n");
            draymanfile.append("<chassisPosition>" + _chspos1 + "</chassisPosition>\n")
            draymanfile.append("</container1>\n");
            draymanfile.append("<gate>\n");
            draymanfile.append("<type>" + _direction + "</type>\n");
            draymanfile.append("<lane>" + _gateid + "</lane>\n");
            draymanfile.append("</gate>\n");
            draymanfile.append("</draymanGate>\n");

        }catch(Exception e){
            e.printStackTrace();
        }

        //showMessage(draymanfile.getText());
    }

    private void showMessage(String message) {
        //RoadBizUtil.messageCollector.appendMessage(BizFailure.create(message))
        def uiHelper = new AbstractTableViewCommand()
        EUIExtensionHelper extHelper = uiHelper.getExtensionHelper();
        extHelper.showMessageDialog(MessageLevel.WARNING, "Error", message);
    }
}