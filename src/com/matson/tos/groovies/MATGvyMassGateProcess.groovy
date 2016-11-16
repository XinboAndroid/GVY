import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.model.Facility
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reports.DigitalAsset
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook

public class MATGvyMassGateProcess extends GroovyInjectionBase {


    public String execute(Map inParameters) {
        GroovyApi groovyApi = new GroovyApi();
        String fileStr = null;
        String fileName = null;
        boolean transationFailed = false;
        String emailBody = null;
        String mediaAssetName = null;
        String gateId="";
        def units = null;
        groovyApi.sendEmail("gbabu@matson.com", "gbabu@matson.com", "Bulk Transaction ", "before calling ingate");
        try{
            Facility facility = ContextHelper.getThreadFacility();
            if (facility!= null) {
                if ("DUT".equals(facility.getFcyId())) {
                    mediaAssetName = "DUTMASSINGATE";
                    gateId = "DUT_MASSGATE";

                } else if ("KDK".equals(facility.getFcyId())) {
                    mediaAssetName = "KDKMASSINGATE";
                    gateId = "KDK_MASSGATE";
                }
            }
            readBulkIngateMediaAsset(mediaAssetName);
            groovyApi.sendEmail("gbabu@matson.com", "gbabu@matson.com", "Bulk Transaction ", "after reading units" +dataList + " with size ");
            if (dataList != null && dataList.size()>0 ) { //&& bookingList!= null && bookingList.size() > 0) {
                for (int i = 0; i < dataList.size(); i++) {
                    Map<String, String> dataMap = dataList.get(i);
                    if (dataMap != null && dataMap.size() > 0) {
                        bookingNbr = dataMap.get(BOOK) != null ? (noBook.equals(dataMap.get(BOOK)) ? bookingNbr : dataMap.get(BOOK)) : bookingNbr;
                        String unitId = dataMap.get(UNITID);
                        String slot = dataMap.get(SLOT);
                        groovyApi.sendEmail("gbabu@matson.com", "gbabu@matson.com", "Bulk Transaction ", "after reading booking" + bookingNbr);
                        transationFailed = false;
                        if (unitId != null && unitId.length() > 0) {
                            Equipment equipment = Equipment.findEquipment(unitId);
                            String tranStatus = "new container not allowed";
                            if (equipment != null) {
                                tranStatus = groovyApi.getGroovyClassInstance("MATGvySubmitMassTransaction")
                                        .doSubmitTransaction(unitId.trim(), bookingNbr.trim(), slot,gateId );
                            }
                            if (tranStatus != statusOK) {
                                //send email
                                transationFailed = true;
                                emailBody = "Transaction cannot be created for " + unitId + " with message " + tranStatus;
                                groovyApi.sendEmail("gbabu@matson.com", "gbabu@matson.com", "Bulk Transaction Failed", emailBody);
                            }
                        }
                    } else {
                        emailBody = "Transaction cannot be created as units/bookings cannot be read ";
                    }
                }
            }

            //clearBulkIngateDigitalAsset();
        }catch(Exception e){
            groovyApi.sendEmail("gbabu@matson.com", "gbabu@matson.com", "Bulk Transaction Failed", e.toString());
        }
        if (transationFailed){
            groovyApi.sendEmail("gbabu@matson.com", "gbabu@matson.com", "Bulk Transaction Failed", emailBody);
        }
    }


    public void readBulkIngateMediaAsset(String inMediaAssetName)
    {
        GroovyApi groovyApi = new GroovyApi();
        try {
            groovyApi.sendEmail("gbabu@matson.com", "gbabu@matson.com", "Bulk Transaction Failed", "trying to find asset");
            byte[] bulkIngateData = DigitalAsset.findImage(inMediaAssetName);
            groovyApi.sendEmail("gbabu@matson.com", "gbabu@matson.com", "Bulk Transaction Failed", "after finding data");
            InputStream inputStream = null;
            //byte[] b = new byte[bulkIngateData.length];

            inputStream = new ByteArrayInputStream(bulkIngateData);
            Workbook workBook = new XSSFWorkbook(inputStream);
            Sheet firstSheet = workBook.getSheetAt(0);
            Iterator<Row> iterator = firstSheet.iterator();

            while (iterator.hasNext()) {
                Row nextRow = iterator.next();
                Iterator<Cell> cellIterator = nextRow.cellIterator();
                if (nextRow.rowNum > 0) {
                    Map<String,String> dataMap=new HashMap<String, String>();
                    while (cellIterator.hasNext()) {
                        Cell cell = cellIterator.next();

                        switch (cell.getColumnIndex()) {
                            case 0:
                                String bookingNbr = getCellValue(cell);
                                if (bookingNbr != null && bookingNbr.length()>0) {
                                    dataMap.put(BOOK, bookingNbr);
                                } else
                                {
                                    dataMap.put(BOOK, noBook);
                                }
                                break;
                            case 1:
                                dataMap.put(UNITID, getCellValue(cell));
                                break;
                            case 2:
                                dataMap.put(SLOT, getCellValue(cell));
                                break;
                        }
                    }
                    dataList.add(dataMap);
                }
            }
            //workBook.close();
            inputStream.close();
        } catch (Exception e) {
            groovyApi.sendEmail("gbabu@matson.com","gbabu@matson.com","Error in reading excel", e.toString());
        }
    }

    private String getCellValue(Cell inCell) {
        String cellValue = null;
        if (inCell != null ) {
            switch (inCell.getCellType()) {
                case inCell.CELL_TYPE_STRING:
                    cellValue = inCell.getStringCellValue();
                    break;
                case inCell.CELL_TYPE_NUMERIC:
                    int  intValue = (int) inCell.getNumericCellValue();
                    //if (doubleValue != null && doubleValue.intValue())
                    cellValue = intValue;
                    break;
                case inCell.CELL_TYPE_BLANK:
                    break;
            }
        }
    }
    private String bookingNbr = null;
    private String statusOK = "OK";
    private ArrayList<String> unitList = new ArrayList<String>();
    private ArrayList<Map<String, String>> dataList = new ArrayList<Map<String, String>>();
    private ArrayList<String> bookingList = new ArrayList<String>();
    private ArrayList<String> positionList = new ArrayList<String>();
    private String noBook = "NOBOOK";
    private String BOOK = "BOOK";
    private String UNITID = "UNITID";
    private String SLOT = "SLOT";
}