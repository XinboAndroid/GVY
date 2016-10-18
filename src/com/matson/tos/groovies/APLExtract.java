import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class APLExtract {

    private static final String TEMPLATE_CTR_SNX = "<container eqid=\"rEquipmentID\" type=\"rEquipmentType\" created-by=\"dataload\" created-date=\"rCurrentDate\" class=\"CTR\" life-cycle-state=\"ACT\">\n" +
            "<physical  material=\"UNKNOWN\" strength-code=\"A\" tare-weight-kg=\"rTareWeight\" />\n" +
            "<ownership owner=\"NYKU\" operator=\"NYK\" />\n" +
            "<restrictions safe-weight-kg=\"rSafeWt\"/>\n" +
            "</container>\n";


    //"<ownership owner=\"rOwner\" operator=\"rOperator\" />\n" +
    //            "<rCRefer>" +

    public static final String OUT_FILE_LOCATION = "C:/Projects/Matson/migration/Outfile/NYK/";

    private static String feedContainerTemplate() {
        return TEMPLATE_CTR_SNX;  //.replaceAll("<rCRefer>", TEMPLATE_CONTAINER_REFER_SNX);
    }

    private static String replaceValuesforContainer(String equipmentType, String equipmentID,
                                                    String templateContainerSnx, String currentDate, String tareWt, String owner, String operator, String massGrossWt) {
        //String nullString = "";
        String snx = templateContainerSnx.replaceAll("rEquipmentID", equipmentID);
        snx = snx.replaceAll("rEquipmentType", equipmentType);
        snx = snx.replaceAll("rCurrentDate", currentDate);
        snx = snx.replaceAll("rTareWeight", tareWt);
        snx = snx.replaceAll("rOwner", owner);
        snx = snx.replaceAll("rOperator", operator);
        snx = snx.replaceAll("rSafeWt", massGrossWt);

        return snx;
    }

    public static void main(String[] args) throws Exception {

        String TEMPLATE_SNX_header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<argo:snx xmlns:argo=\"http://www.navis.com/argo\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.navis.com/argo snx.xsd\">\n";

        String TEMPLATE_SNX_FOOTER = "</argo:snx>";

        System.out.println("1 2 3 Start");

        File inputfile = new File("C:\\projects\\Matson\\migration\\input\\CONTAINER_MASTER_Alaskasubset.txt");

        List<String> lines = new ArrayList<String>();
        try {
            FileReader reader = new FileReader(inputfile);
            BufferedReader buffReader = new BufferedReader(reader);
            String s;
            while ((s = buffReader.readLine()) != null) {
                lines.add(s);
            }
            buffReader.close();
        } catch (IOException e) {
            System.exit(0);
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        String currentDate = dateFormat.format(cal.getTime());

        currentDate = currentDate.replace(" ", "T");
        currentDate = currentDate.replace("/", "-");
        int ank_lines = 0;
        int ank_file_count =0;

        System.out.println("successfully read the input file too. yahoo!!");

        //String errorString = "";
        String snx="";
        StringBuffer ANK_SNX = new StringBuffer(1000);


        HashSet<String> set = new HashSet<>();



        for (String line : lines) {
            String delimiter = ",";
            String[] tokens = new String[1];
            try {

                tokens = line.split(delimiter);
                if (tokens == null || tokens.length < 1) {
                    continue;
                }
                String extractData = tokens[0];

                if (extractData != null) {
                    String cd = getValue(extractData.substring(0, 0));
                    String EquipmentNbr = getValue(extractData.substring(1, 12));
                    String tareWt = getValue(extractData.substring(12, 17));
                    String massGrossWt = getValue(extractData.substring(17, 22));
                    String typeISO84 = getValue(extractData.substring(22, 26));
                    String typeISO94 = getValue(extractData.substring(26, 30));
                    String ISOOwnercode = getValue(extractData.substring(30, 34));
/*                    String consType = getValue(extractData.substring(34, 35));
                    String hitListFlag = getValue(extractData.substring(35, 36));
                    String locFlag = getValue(extractData.substring(36, 37));
                    String leaseFlag = getValue(extractData.substring(37, 38));
                    String extractDate = getValue(extractData.substring(38, 50));
                    //String Serial= getValue(extractData.substring(48, 69));
        /*            String eqiArray[]=ts.equiMap.get(getValue(extractData.substring(26, 30));;
                     if(eqiArray == null || eqiArray[0]==null||eqiArray[1]==null){
                        continue;
                    }
                    equipmentType = getValue(eqiArray[0]);*/

                    if (EquipmentNbr == null) {
                        continue;
                    }

                    if (typeISO94!= null) {
                        String akISO = eqTypeMap.get(typeISO94);
                        if (akISO!= null) {
                            typeISO94 = akISO;
                        }
                    }
                    snx = feedContainerTemplate();
                    snx = replaceValuesforContainer(typeISO94, EquipmentNbr, snx, currentDate, tareWt, ISOOwnercode, ISOOwnercode, massGrossWt);
                    ANK_SNX.append(snx);

                    ank_lines++;

                    /*if (!set.contains(typeISO94)) {
                        ANK_SNX.append(typeISO94);
                        set.add(typeISO94);
                    }*/

                    if(ank_lines>=10000){

                        ank_lines=0;
                        ank_file_count++;

                        FileWriter ANKfileWriter = new FileWriter(OUT_FILE_LOCATION + "NYK"+ank_file_count+".xml");
                        BufferedWriter ankOut = new BufferedWriter(ANKfileWriter);
                        ankOut.write(TEMPLATE_SNX_header);
                        ankOut.write(ANK_SNX.toString());
                        ankOut.write(TEMPLATE_SNX_FOOTER);
                        ankOut.close();

                        ANK_SNX= new StringBuffer("");
                    }
                }

            } catch (Exception e) {
                System.out.println("Within Exception 1" + e);
            }

        }
        FileWriter ANKfileWriter = new FileWriter(OUT_FILE_LOCATION + "NYKEqTypeExtract.xml");
        BufferedWriter ankOut = new BufferedWriter(ANKfileWriter);
        ankOut.write(TEMPLATE_SNX_header);
        ankOut.write(ANK_SNX.toString());
        ankOut.write(TEMPLATE_SNX_FOOTER);
        ankOut.close();

        ANK_SNX = new StringBuffer("");


    }

    private static String getValue(String inToken) {
        String returnVal = "";
        if (inToken != null && inToken.length() > 0 && !"NULL".equalsIgnoreCase(inToken.trim())) {
            returnVal = inToken.trim();
        }
        return returnVal;
    }

    private static boolean isNotNull(String inValue) {
        boolean hasValue = false;
        if (inValue != null && inValue.length() > 0 && !"NULL".equalsIgnoreCase(inValue.trim())) {
            hasValue = true;
        }
        return hasValue;
    }
   /* public void loadAllTranslationMaps(){


        File equipmentFile = new File("C:\\projects\\Matson\\migration\\input\\Equipment_map.csv");
        File tosEquipFile = new File("C:\\projects\\Matson\\migration\\input\\TOS_Equipments.csv");
        File ankYardFile = new File("C:\\projects\\Matson\\migration\\input\\Yard_Map_ANK.csv");
        File dutYardFile = new File("C:\\projects\\Matson\\migration\\input\\Yard_Map_DUT.csv");
        File kdkYardFile = new File("C:\\projects\\Matson\\migration\\input\\Yard_Map_KDK.csv");
        File ownerOperatorFile = new File("C:\\projects\\Matson\\migration\\input\\Owner_Map.csv");
        File LocationFile = new File("C:\\projects\\Matson\\migration\\input\\Location_Map.csv");

        locMap= getTranslationMap(LocationFile);

        //System.out.println("locMap"+locMap);

        ankYardList= getYardList(ankYardFile);
        //System.out.println("ankYardList"+ankYardList);

        dutYardList= getYardList(dutYardFile);
        //System.out.println("dutYardList"+dutYardList);

        kdkYardList= getYardList(kdkYardFile);
        //System.out.println("kdkYardList"+kdkYardList);

        equiMap= getEquipmentMap(equipmentFile);
        //System.out.println("equiMap"+equiMap);

        tosEquiMap= getTosEquipmentMap(tosEquipFile);
        //System.out.println("equiMap"+equiMap);

        ownerMap= getOwnerOperatorMap(ownerOperatorFile);
        //System.out.println("ownerMap"+ownerMap);

    }*/

    private static HashMap<String, String> eqTypeMap = new HashMap<String, String>();
    static {
        eqTypeMap.put("45G1","D40H");
        eqTypeMap.put("22T3","T20");
        eqTypeMap.put("22G1","D20");
        eqTypeMap.put("42P3","F40");
        eqTypeMap.put("22P3","F20");
        eqTypeMap.put("42G1","D40");
        eqTypeMap.put("22U1","O20");
        eqTypeMap.put("42U1","O40");
        eqTypeMap.put("22R1","R20");
        eqTypeMap.put("42T3","T40");
        eqTypeMap.put("45R1","R40H");
        eqTypeMap.put("L5G1","D45H");

    }
}

	