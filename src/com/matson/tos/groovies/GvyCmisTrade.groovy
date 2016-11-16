/*
* Srno Doer  Date      Change
* A1   GR    08/30/10  Trade logic Classs
*A2    RP    05/20/2013    trade logic is changed to calculate eastbound trades correctly
* A3    KR  07/13/2015      Alaska Changes
*/

public class GvyCmisTrade {

    public String processTrade(Object unit, Object srv) {
        def trade = ''
        def loadPort = unit.getFieldValue("unitRouting.rtgPOL.pointId")
        def dischargePort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
        def destination = unit.getFieldValue("unitGoods.gdsDestination")
        try {
            if ('MAT'.equals(srv) || 'CRX'.equals(srv)) {
                trade = 'A'
            } else { // may need change for 'MSK' carrier, so else is left
                trade = 'A'
            }
            println("trade::after:" + trade);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return trade;
    }

    public static Map ports = new HashMap();
    static {

        ports.put('AUC', 'C')
        ports.put('BRI', 'C')
        ports.put('MEL', 'C')
        ports.put('NUK', 'C')
        ports.put('PAP', 'C')
        ports.put('SFO', 'C')
        ports.put('SUV', 'C')
        ports.put('SYD', 'C')
        ports.put('SYD', 'C')
        ports.put('WEL', 'C')
        ports.put('WLG', 'C')
        ports.put('HAK', 'F')
        ports.put('KAO', 'F')
        ports.put('KEEL', 'F')
        ports.put('KOB', 'F')
        ports.put('portsH', 'F')
        ports.put('MOJ', 'F')
        ports.put('NAH', 'F')
        ports.put('NGB', 'F')
        ports.put('NGO', 'F')
        ports.put('OSA', 'F')
        ports.put('PUS', 'F')
        ports.put('SHA', 'F')
        ports.put('TSI', 'F')
        ports.put('XMN', 'F')
        ports.put('YOK', 'F')
        ports.put('YTN', 'F')
        ports.put('API', 'G')
        ports.put('APW', 'G')
        ports.put('GUM', 'G')
        ports.put('KMI', 'G')
        ports.put('PAG', 'G')
        ports.put('PNP', 'G')
        ports.put('PPT', 'G')
        ports.put('PUX', 'G')
        ports.put('RTA', 'G')
        ports.put('SPN', 'G')
        ports.put('TIN', 'G')
        ports.put('TMGU', 'G')
        ports.put('UUK', 'G')
        ports.put('YAP', 'G')
        ports.put('HIL', 'H')
        ports.put('HNC', 'H')
        ports.put('HON', 'H')
        ports.put('HUHI', 'H')
        ports.put('KAH', 'H')
        ports.put('KAHI', 'H')
        ports.put('KHI', 'H')
        ports.put('KKHI', 'H')
        ports.put('LAX', 'H')
        ports.put('LNI', 'H')
        ports.put('MIX', 'H')
        ports.put('MOL', 'H')
        ports.put('NAW', 'H')
        ports.put('NAX', 'H')
        ports.put('OAC', 'H')
        ports.put('OAK', 'H')
        ports.put('PCHI', 'H')
        ports.put('PDX', 'H')
        ports.put('PRL', 'H')
        ports.put('RCH', 'H')
        ports.put('SEA', 'H')
        ports.put('UEHI', 'H')
        ports.put('EBY', 'M')
        ports.put('JIS', 'M')
        ports.put('KWJ', 'M')
        ports.put('MAJ', 'M')
        ports.put('WAK', 'M')
    }


}//Class Ends