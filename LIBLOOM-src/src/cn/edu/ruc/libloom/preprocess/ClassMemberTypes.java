package cn.edu.ruc.libloom.preprocess;

/**
 * @author xuebo @date 2021/5/24
 */
public class ClassMemberTypes {
    private short bytesCnt;
    private short boolCnt;
    private short intCnt;
    private short shortCnt;
    private short longCnt;
    private short floatCnt;
    private short doubleCnt;
    private short stringCnt;
    private short mapCnt;
    private short listCnt;
    private short boolsCnt;
    private short intsCnt;
    private short shortsCnt;
    private short longsCnt;
    private short floatsCnt;
    private short doublesCnt;
    private short stringsCnt;
    private short nonPrimitiveCnt;

    public ClassMemberTypes(){
        bytesCnt = 0;
        boolCnt = 0;
        intCnt = 0;
        shortCnt = 0;
        longCnt = 0;
        floatCnt = 0;
        doubleCnt = 0;
        stringCnt = 0;
        mapCnt = 0;
        listCnt = 0;
        boolsCnt = 0;
        intsCnt = 0;
        shortsCnt = 0;
        longsCnt = 0;
        floatsCnt = 0;
        doublesCnt = 0;
        stringsCnt = 0;
        nonPrimitiveCnt = 0;
    }

    public void bytesAdd(){
        bytesCnt ++;
    }
    public void boolAdd(){
        boolCnt ++;
    }
    public void intAdd(){
        intCnt ++;
    }
    public void shortAdd(){
        shortCnt ++;
    }
    public void longAdd(){
        longCnt ++;
    }
    public void floatAdd(){
        floatCnt ++;
    }
    public void doubleAdd(){
        doubleCnt ++;
    }
    public void stringAdd(){
        stringCnt ++;
    }
    public void mapAdd(){
        mapCnt ++;
    }
    public void listAdd(){
        listCnt ++;
    }
    public void boolsAdd(){
        boolsCnt ++;
    }
    public void intsAdd(){
        intsCnt ++;
    }
    public void shortsAdd(){
        shortsCnt ++;
    }
    public void longsAdd(){
        longsCnt ++;
    }
    public void floatsAdd(){
        floatsCnt ++;
    }
    public void doublesAdd(){
        doublesCnt ++;
    }
    public void stringsAdd(){
        stringsCnt ++;
    }
    public void nonPrimitiveAdd(){
        nonPrimitiveCnt ++;
    }

    public short getBytesCnt() {
        return bytesCnt;
    }

    public short getBoolCnt() {
        return boolCnt;
    }

    public short getIntCnt() {
        return intCnt;
    }

    public short getShortCnt() {
        return shortCnt;
    }

    public short getLongCnt() {
        return longCnt;
    }

    public short getFloatCnt() {
        return floatCnt;
    }

    public short getDoubleCnt() {
        return doubleCnt;
    }

    public short getStringCnt() {
        return stringCnt;
    }

    public short getMapCnt() {
        return mapCnt;
    }

    public short getListCnt() {
        return listCnt;
    }

    public short getBoolsCnt() {
        return boolsCnt;
    }

    public short getIntsCnt() {
        return intsCnt;
    }

    public short getShortsCnt() {
        return shortsCnt;
    }

    public short getLongsCnt() {
        return longsCnt;
    }

    public short getFloatsCnt() {
        return floatsCnt;
    }

    public short getDoublesCnt() {
        return doublesCnt;
    }

    public short getStringsCnt() {
        return stringsCnt;
    }

    public short getNonPrimitiveCnt() {
        return nonPrimitiveCnt;
    }
}
