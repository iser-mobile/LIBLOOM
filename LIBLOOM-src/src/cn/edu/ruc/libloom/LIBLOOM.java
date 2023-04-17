package cn.edu.ruc.libloom;

//import com.google.gson.Gson;
//import com.google.gson.GsonBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cn.edu.ruc.libloom.preprocess.AppOrLibInfo;
import cn.edu.ruc.libloom.preprocess.ClassFeatures;
import cn.edu.ruc.libloom.preprocess.CodeInfoCollector;
import cn.edu.ruc.libloom.entity.DetectionResult;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author xuebo @date 2021/1/1
 */
public class LIBLOOM {
    private int CLASS_LEVEL_M = 256;
    private int CLASS_LEVEL_K = 3;
    private int PKG_LEVEL_M = 0;
    private int PKG_LEVEL_K = 3;
    private String ABSOLUTEPATH = "";
    private ArgsParser argsParser;
    Map<String, Map<String, Map<String, String>>> classPairs = new HashMap<>(); //[DEBUG] record km match class pairs <lp, <ap, <ac, lc>>>
    public static double THRESHOLD = 0.6;   // the similarity threshold of library detection
    public static double PKG_OVERLAP_THRESHOLD = 0.8;

    private int excludedLibs = 0;
    Set<String> potential_flatten_pkg_list = new HashSet<String>();
    String potential_re_pkg = "";
    double H_r, H_f;
    private static Logger logger = LoggerFactory.getLogger(LIBLOOM.class);

    public static void main(String[] args) throws IOException, ClassHierarchyException {
        double startTime = System.currentTimeMillis();
        LIBLOOM libloom = new LIBLOOM();
        libloom.argsParser = new ArgsParser(args);

        if (libloom.argsParser.ACTION.equals("profile")) {
            logger.info("Convert apk/aar/jar to bloom filter vectors");
            logger.info("");
            libloom.generateProfile();
        } else if (libloom.argsParser.ACTION.equals("detect")) {
            logger.info("== LIBLOOM detection:");
            logger.info("");
            File fileAppDir = new File(libloom.argsParser.APP_PROFILE_DIR);
            File fileLibDir = new File(libloom.argsParser.LIB_PROFILE_DIR);
            File[] apps = fileAppDir.listFiles();
            File[] libs = fileLibDir.listFiles();
            assert apps != null;
            assert libs != null;
            Arrays.sort(apps);
            Arrays.sort(libs);
            for (File app : apps) {
                double appStartDetectionTime = System.currentTimeMillis();
                DetectionResult dResult = new DetectionResult();

                libloom.excludedLibs = 0;
                Map<String, BitSet> pkgBitSetApp = new LinkedHashMap<>();
                Map<String, Map<String, BloomBitSet>> bitSetApp = new LinkedHashMap<>();
                if (app.isFile()) {
                    dResult.setAppname(app.getName());
                    libloom.readProfile(pkgBitSetApp, bitSetApp, app.toString(), "app");
                    File dir = new File(libloom.argsParser.OUTPUT_DIR);
                    if(!dir.exists()){
                        logger.info("Folder " + libloom.argsParser.OUTPUT_DIR + " does not exist. Create it.");
                        dir.mkdirs();
                    }
                    for (File lib : libs) {
                        if (lib.isFile()) {
                            double startSimilarityTime = System.currentTimeMillis();
                            Map<String, BitSet> pkgBitSetLib = new LinkedHashMap<>();
                            Map<String, Map<String, BloomBitSet>> bitSetLib = new LinkedHashMap<>();
                            libloom.readProfile(pkgBitSetLib, bitSetLib, lib.toString(), "lib");
                            // the similarity between <app,lib>
                            double similarity = libloom.calculateSimScore(pkgBitSetApp, pkgBitSetLib, bitSetApp, bitSetLib);
                            double similarityTime = (System.currentTimeMillis() - startSimilarityTime) / 1000;
                            if(similarity >= LIBLOOM.THRESHOLD)
                            {
                                logger.info(app.getName() + "(app) : " + lib.getName() + "(lib)");
                                logger.info("Sim: " +similarity + "\t Time-consuming:"+similarityTime+"s");
                                logger.info("");

                                // write in DetectionResult
                                String library = lib.getName();
                                library = library.substring(0, library.length() - 4);  // remove .txt
                                int idx = getLibSplitIndex(library);
                                String libname = library.substring(0,idx);
                                String version = "";
                                if(idx  < library.length()){
                                    version = library.substring(idx+1);
                                }

                                dResult.updateLibraries(libname, version, similarity);
                            }
                        }
                    }

                }
                double appDetectionTime = (System.currentTimeMillis() - appStartDetectionTime) / 1000;
                dResult.setTime(appDetectionTime + "s");
                String fname = app.getName().substring(0, app.getName().length()-4) + ".json";

                File matchResultFile = new File(libloom.argsParser.OUTPUT_DIR, fname);
                PrintWriter pWriter = new PrintWriter(matchResultFile);
                // Gson Serializer
                // Gson gsonObj = new GsonBuilder().setPrettyPrinting().create();
                // pWriter.write(gsonObj.toJson(dResult));

                // generate json format by manual
                pWriter.write(dResult.prettyJSON());
                pWriter.close();

                if(libloom.argsParser.DEBUG){
                    pWriter =  new PrintWriter(new FileWriter(fileAppDir.getName() + "-exclude-TPLsNum.log",true));
                    pWriter.println(app.getName() + "\t" + libloom.excludedLibs);
                    pWriter.close();
                }
            }
        }
        double runTime = (System.currentTimeMillis() - startTime) / 1000;
        logger.info("Total Runtime: " + runTime + "s");
    }

    public LIBLOOM(){
        if(! loadParameters())
            logger.error("Loading parameters.properties error !!! Checking");
    }

    /**
     * find index of seperator, which seperate lib into libname and version
     * e.g. lib=okhttp-3.1.0 or okhttp_3.1.0 or okhttp.3.1.0   index=6
     * @param library
     * @return
     */
    public static int getLibSplitIndex(String library){
        Pattern p = Pattern.compile("_\\d|-\\d|\\.\\d");
        Matcher m = p.matcher(library);
        int idx;
        if (m.find()){
            String it = m.group(0);
            idx = library.indexOf(it);
        } else {
            idx = library.length();
        }
        return idx;
    }

    private void generateProfile() throws IOException, ClassHierarchyException {
        if (!(new File(argsParser.APP_LIB_DIR)).isDirectory()) {
            logger.info("<apps_dir/libs_dir> should be a directory.");
            return;
        }
        File Dir = new File(argsParser.APP_LIB_DIR);
        File[] files = Dir.listFiles();
        Arrays.sort(files);
        assert files != null;
        for (File file : files) {
            double startConstructTime =  System.currentTimeMillis();
            AppOrLibInfo info = CodeInfoCollector.getInfo(file.getPath(), ABSOLUTEPATH);
            writeEntropy2Profile(FilenameUtils.getBaseName(file.getName()), info);

            Map<String, BitSet> pkgBFVectors = new HashMap<>();
            addPKGBFVectors(info, pkgBFVectors);
            writePKGBFVectors2Profile(FilenameUtils.getBaseName(file.getName()), pkgBFVectors);

            Map<String, Map<String, BloomBitSet>> bitSetList = new HashMap<>();
            addClazzBFVectors(info, bitSetList);
            writeClazzBFVectors2Profile(FilenameUtils.getBaseName(file.getName()), bitSetList);
            double constructTime =  (System.currentTimeMillis() - startConstructTime) / 1000;
            logger.info("  " + file.getName() + ":" + constructTime + "s");
        }
        return;
    }

    private double calculateSimScore(Map<String, BitSet> apBFVector,
                                     Map<String, BitSet> lpBFVector,
                                     Map<String, Map<String, BloomBitSet>> appBFVector,
                                     Map<String, Map<String, BloomBitSet>> libBFVector){
        Map<String, String> packageLinking = new HashMap<>();
        classPairs.clear();
        Map<String, List<String>> candidatePairs = new LinkedHashMap<>();
        Map<String, Map<String, Double>> candidate = new LinkedHashMap<>();
        getCandidateLpApPairs(apBFVector, lpBFVector, candidatePairs);
        if(isExcludedLib(candidatePairs, appBFVector, libBFVector)){
            excludedLibs ++;
            return 0.0;
        }
        candidatePackageSimilar(appBFVector, libBFVector, candidatePairs, candidate);
        candidate = sortMap(candidate);

        double similarity = 0.0;
        Map<String, Double> partition = new HashMap<>();
        partition = partitioning(candidate, packageLinking);
        similarity = simLibInApp(partition, libBFVector);

        if(similarity < THRESHOLD){
            packageLinking.clear();
            partition.clear();
            if(H_r >= H_f){
                partition = antiRepackagePartitioning(candidate, packageLinking, appBFVector, libBFVector);
                similarity = simLibInApp(partition, libBFVector);
            } else {
                partition = antiFlattenPackagePartitioning(candidate, packageLinking, appBFVector, libBFVector);
                similarity = simLibInApp(partition, libBFVector);
            }
        }

        if(argsParser.DEBUG && similarity >= THRESHOLD){
            printClassMatchPairs(partition, packageLinking, libBFVector);
        }
        return similarity;
    }

    private double simLibInApp(Map<String, Double> partition,
                               Map<String, Map<String, BloomBitSet>> libBFVector) {
        int count = 0, total = 0;
        for (String lp : libBFVector.keySet()) {
            int childSize = libBFVector.get(lp).size();
            total += childSize;
            if (partition.containsKey(lp)) {
                count += childSize * partition.get(lp);
            }
        }
        double similarity;
        if (total == 0 || (total <= 5 && count != total)) {  // the number of class less than 5 in lib
            similarity = 0.0f;
        } else {
            similarity = count * 1.0 / total;
        }
        return similarity;
    }


    private void printClassMatchPairs(Map<String, Double> partition,
                                      Map<String, String> packageLinking,
                                      Map<String, Map<String, BloomBitSet>> libBFVector) {
        for (String lp : libBFVector.keySet()) {
            int childSize = libBFVector.get(lp).size();
            if (partition.containsKey(lp)) {
                logger.debug(packageLinking.get(lp) + "(ap) : " + lp + "(lp)(" + partition.get(lp) + ")" + " * (" + childSize + ") ");
                Map<String, String> pairs = classPairs.get(lp).get(packageLinking.get(lp));
                for(String ac : pairs.keySet()){
                    logger.debug("\t\t" + ac + "(ac) : " + pairs.get(ac) + "(lc)");
                }
            }
        }
    }

    /**
     * get Sim score between each pair <lc, ac>
     * attention：Map need to be ordered（LinkedHashMap,TreeMap）
     * @param classBitSetListApp
     * @param classBitSetListLib
     * @return Sim score between each lc_i and ac_j
     */
    private double[][] lc_ac_classSimilar(Map<String, BloomBitSet> classBitSetListApp,
                                          Map<String, BloomBitSet> classBitSetListLib,
                                          String appPkgName) {
        double[][] result = new double[classBitSetListLib.size()][classBitSetListApp.size()];
        List<String> acList = new ArrayList<>(classBitSetListApp.keySet());
        List<String> lcList = new ArrayList<>(classBitSetListLib.keySet());

        int count = 0, total = 0;
        for(int i = 0; i < lcList.size(); i++){
            total = classBitSetListLib.get(lcList.get(i)).size;  //count of lc sigs
            for(int j = 0; j < acList.size(); j++){
                count = 0;
                if(isSuperSet(classBitSetListLib.get(lcList.get(i)).bitSet, classBitSetListApp.get(acList.get(j)).bitSet)){
                    count = classBitSetListApp.get(acList.get(j)).size;
                }
                result[i][j] =  count * 1.0 / total;
                result[i][j] = (result[i][j] < 0.33 ? 0 : result[i][j]);

                if(appPkgName.equals(potential_re_pkg)){
                    int sigsInLc = classBitSetListLib.get(lcList.get(i)).size;
                    if( sigsInLc <= 5){
                        result[i][j] = (result[i][j] < 1 ? 0 : 1);
                    } else if (sigsInLc <= 10 ){
                        result[i][j] = (result[i][j] < 0.8 ? 0 : result[i][j]);
                    } else if (sigsInLc <= 25 ){
                        result[i][j] = (result[i][j] < 0.5 ? 0 : result[i][j]);
                    }
                }
            }
        }

        return result;
    }

    private void candidatePackageSimilar(Map<String, Map<String, BloomBitSet>> appBFVector,
                                          Map<String, Map<String, BloomBitSet>> libBFVector,
                                          Map<String, List<String>> candidatePairs,
                                          Map<String, Map<String, Double>> packageCandidate){
        double PACKAGE_SIMILARITY_THRESHOLD = 0.01;

        for(String lp : candidatePairs.keySet()){
            int lp_clazz_count = libBFVector.get(lp).size();
            int[] lp_sig_count = new int[lp_clazz_count];
            List<String> lcList = new ArrayList<>(libBFVector.get(lp).keySet());
            int k = 0;
            for(String lc : lcList){
                lp_sig_count[k++] = libBFVector.get(lp).get(lc).size;
            }

            for(String ap : candidatePairs.get(lp)){
                double [][] clazzSimilarity = lc_ac_classSimilar(appBFVector.get(ap), libBFVector.get(lp), ap);

                double pairSimilarity = 0.0;
                MaxMatching km = new MaxMatching(clazzSimilarity, lp_sig_count);

                if(argsParser.DEBUG){
                    //-- store matching class pairs <start>
                    List<String> acList = new ArrayList<>(appBFVector.get(ap).keySet());
                    Map<String, String> ac_lc_pairs = new HashMap<>();
                    for(k = 0; k < km.match.length; k++){
                        if(km.match[k] != -1){
                            ac_lc_pairs.put(acList.get(k), lcList.get(km.match[k]));
                        }
                    }

                    Map<String, Map<String, String>> lpValue;
                    lpValue = classPairs.get(lp);
                    if(lpValue == null){
                        lpValue = new HashMap<>();
                        classPairs.put(lp, lpValue);
                    }
                    Map<String, String> apValue;
                    apValue = classPairs.get(lp).get(ap);
                    if(apValue == null){
                        apValue = new HashMap<>();
                        classPairs.get(lp).put(ap, apValue);
                    }
                    classPairs.get(lp).put(ap, ac_lc_pairs);
                    //-- store matching class pairs <end>
                }

                if(lp_clazz_count == 0){
                    pairSimilarity = 0.0;
                } else {
                    pairSimilarity = (float) km.max_matching_pairs / (float) lp_clazz_count;
                    //pairSimilarity = km.avg_weight;
                }
                if(pairSimilarity > PACKAGE_SIMILARITY_THRESHOLD) {
                    if(! packageCandidate.containsKey(lp)){
                        Map<String, Double> pairSimScore = new HashMap<>();
                        packageCandidate.put(lp, pairSimScore);
                    }
                    packageCandidate.get(lp).put(ap, pairSimilarity);
                }
            }
        }
    }

    /**
     * @func  get candidate <lp, ap> pairs (set containment query problem)
     * @param pkgBitSetApp
     * @param pkgBitSetLib
     * @param candidatePairs
     */
    private void getCandidateLpApPairs(Map<String, BitSet> apBFVector,
                                      Map<String, BitSet> lpBFVector,
                                      Map<String, List<String>> candidatePairs) {
        for(String lp : lpBFVector.keySet()){
            for(String ap : apBFVector.keySet()){
                boolean is_wl_violated = false;
                for(String w : AppOrLibInfo.ignore_pkg_prefix){
                    if(ap.startsWith(w) && ! lp.startsWith(w)){
                        is_wl_violated = true;
                        break;
                    }
                }
                if(is_wl_violated)
                    continue;
                boolean ap_hold_condition = false;
                if((H_r>=H_f) && ap.equals(potential_re_pkg))
                    ap_hold_condition = true;
                else if(H_f>H_r && potential_flatten_pkg_list.contains(ap))
                    ap_hold_condition = true;
                else if(packageHaveSameDepth(lp, ap))
                    ap_hold_condition = true;

                if(ap_hold_condition) {
                    if (overlapRatio(lpBFVector.get(lp), apBFVector.get(ap)) >= PKG_OVERLAP_THRESHOLD) {
                        if (!candidatePairs.containsKey(lp)) {
                            List<String> aplist = new LinkedList<>();
                            candidatePairs.put(lp, aplist);
                        }
                        candidatePairs.get(lp).add(ap);
                    }
                }
            }
        }
    }

    private Map<String, Double> partitioning(Map<String, Map<String, Double>> candidate,
                                             Map<String, String> linking) {
        Map<String, Double> result = new HashMap<>();
        Map<String, String> samePkgLinking = new HashMap<>();
        Map<String, Double> samePkgPartition = new HashMap<>();
        for (String lp : candidate.keySet()) {
            Map<String, Double> candAPsAssociatedWithLP = candidate.get(lp);
            for (String ap : candAPsAssociatedWithLP.keySet()) {
                if (lp.equals(ap)) {                     //only partial packages are obfuscated, when ap==lp, put.
                    samePkgLinking.put(lp, ap);
                    samePkgPartition.put(lp, candAPsAssociatedWithLP.get(ap));
                    break;
                } else if(packageHaveSameDepth(lp,ap)){  //TODO: just compare <lp,ap> with the same depth
                    boolean flag = true;
                    for (String lp_l : linking.keySet()) {
                        String ap_l = linking.get(lp_l);
                        if (!compare(relationship(lp, lp_l), relationship(ap, ap_l))) {
                            flag = false;
                            break;
                        }
                    }
                    if (flag) {
                        linking.put(lp,ap);
                        result.put(lp, candAPsAssociatedWithLP.get(ap));
                        break;
                    }
                }
            }
        }
        linking.putAll(samePkgLinking);
        result.putAll(samePkgPartition);
        return result;
    }

    private Map<String, Double> antiRepackagePartitioning(Map<String, Map<String, Double>> candidate,
                                                          Map<String, String> linking,
                                                          Map<String, Map<String, BloomBitSet>> bitSetApp,
                                                          Map<String, Map<String, BloomBitSet>> bitSetLib) {
        Map<String, Double> result = new HashMap<>();
        for (String lp : candidate.keySet()){
            Map<String, Double> candAPsAssociatedWithLP = candidate.get(lp);
            for (String ap : candAPsAssociatedWithLP.keySet()){
                if(! linking.containsKey(lp)){
                    if(lp.equals(ap)){
                        linking.put(lp, ap);
                        result.put(lp, candAPsAssociatedWithLP.get(ap));
                        break;
                    } else if (ap.equals(potential_re_pkg)) {
                        if(result.containsKey(lp) && result.get(lp).doubleValue() > candAPsAssociatedWithLP.get(ap).doubleValue()) //partial re-package
                            continue;
                        linking.put(lp, ap);
                        result.put(lp, candAPsAssociatedWithLP.get(ap));
                        break;
                    }
                }
            }
        }
        return result;
    }

    private Map<String, Double> antiFlattenPackagePartitioning(Map<String, Map<String, Double>> candidate,
                                                               Map<String, String> linking,
                                                               Map<String, Map<String, BloomBitSet>> bitSetApp,
                                                               Map<String, Map<String, BloomBitSet>> bitSetLib) {
        Map<String, Double> result = new HashMap<>();
        HashSet<String> apAllocation = new HashSet<>();  // store ap that has been allocated
        for(String lp : candidate.keySet()){
            Map<String, Double> candAPsAssociatedWithLP = candidate.get(lp);
            for(String ap : candAPsAssociatedWithLP.keySet()){
                if(! linking.containsKey(lp)){
                    if(lp.equals(ap) || (potential_flatten_pkg_list.contains(ap) && ! apAllocation.contains(ap))){
                        linking.put(lp, ap);
                        result.put(lp, candAPsAssociatedWithLP.get(ap));
                        apAllocation.add(ap);
                        continue;
                    }
                }
            }
        }
        return result;
    }

    private boolean packageHaveSameDepth(String package1, String package2){
        String[] nameList1 = package1.split("/");
        String[] nameList2 = package2.split("/");
        if(nameList1.length == nameList2.length)
            return true;
        else
            return false;
    }

    private boolean compare(int[] relation1, int[] relation2) {
        for (int i = 0; i < relation1.length; i++) {
            if (relation1[i] != relation2[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param name1 package1
     * @param name2 package2
     * @return
     * e.g.
     *  name1 = "com.google"    name2 = "com.google.ads"
     *  result={2,0,1}    [1]:distance between name1 and same root; [2]:distance between name2 and same root
     *
     *  name1 = "a.b.c"         name2 = "a.b.c.d"
     *  result={2,1,2}
     */
    private int[] relationship(String name1, String name2) {
        String[] nameList1 = name1.split("/");
        String[] nameList2 = name2.split("/");
        int[] result = new int[3];

        int depth = 0;
        while(depth < nameList1.length && depth < nameList2.length) {
            if (nameList1[depth].equals(nameList2[depth])) {
                depth ++;
            } else {
                break;
            }
        }
        if (depth == 0) {
            result[0] = depth;
            result[1] = Integer.MAX_VALUE;
            result[2] = Integer.MAX_VALUE;
        }
        else {
            result[0] = depth;
            result[1] = nameList1.length - depth;
            result[2] = nameList2.length - depth;
        }
        return result;
    }

    /*
        create bloom filters for each package
        (pkg →  BF vector)
     */
    public void addPKGBFVectors(AppOrLibInfo info, Map<String, BitSet> BFVectors) throws IOException{
        Map<String, List<ClassFeatures>> pkgClassFeatures = info.getFeatures();
        for(String pkg : pkgClassFeatures.keySet()) {
            List<String> list = new LinkedList<>();
            for(ClassFeatures cf : pkgClassFeatures.get(pkg)) {
                //if(cf.getMethods().size() + cf.getMemtypes().size() > MAX_FEATRUES_IN_CLASS)  //ignore class that features > 50
                //    continue;
                list.add(cf.getSuperClass());
                for(String iface : cf.getInterfaces()) {
                    list.add(iface);
                }
                for(String m : cf.getMethods()) {
                    list.add(m);
                }
                for(String v : cf.getMemtypes()) {
                    list.add(v);
                }
            }
            BloomHash bloomHash = new BloomHash(PKG_LEVEL_M, PKG_LEVEL_K);
            BitSet pkgBF = new BitSet(PKG_LEVEL_M);
            for(String sig : list){
                for(int idx : bloomHash.hash(sig)){
                    pkgBF.set(idx, true);
                }
            }
            BFVectors.put(pkg, pkgBF);
        }
    }

    public void addClazzBFVectors(AppOrLibInfo info, Map<String, Map<String, BloomBitSet>> bitSetList) {
        Map<String, List<ClassFeatures>> pkgClassFeatures = info.getFeatures();
        for(String pkg : pkgClassFeatures.keySet()){
            for(ClassFeatures cf : pkgClassFeatures.get(pkg)){
                //if(cf.getMethods().size()+cf.getMemtypes().size() > MAX_FEATRUES_IN_CLASS) //ignore class that features > 50
                //    continue;
                List<String> list = new LinkedList<>();
                list.add(cf.getSuperClass());
                for(String it : cf.getInterfaces()){
                    list.add(it);
                }
                for(String m : cf.getMethods()){
                    list.add(m);
                }
                for(String v : cf.getMemtypes()){
                    list.add(v);
                }
                int sigCount = cf.getMethods().size() + cf.getMemtypes().size();

                Map<String, BloomBitSet> classBitSet = bitSetList.get(pkg);
                if(classBitSet == null){
                    classBitSet = new HashMap<>();
                    bitSetList.put(pkg, classBitSet);
                }
                BloomBitSet bitSet = classBitSet.get(cf.getClassName());
                if(bitSet == null){
                    BloomHash bloomHash = new BloomHash(CLASS_LEVEL_M, CLASS_LEVEL_K);
                    BitSet clazzBF = new BitSet(CLASS_LEVEL_M);
                    for(String sig : list){
                        for (int idx : bloomHash.hash(sig)){
                            clazzBF.set(idx, true);
                        }
                    }
                    bitSet = new BloomBitSet(clazzBF, sigCount);
                    classBitSet.put(cf.getClassName(), bitSet);
                }
            }
        }
    }

    private Map<String, Map<String, Double>> sortMap(Map<String, Map<String, Double>> candidate) {
        for (String lp : candidate.keySet()) {
            candidate.put(lp, getSortedHashtableByValue1(candidate.get(lp)));
        }
        candidate = getSortedHashtableByValue2(candidate);
        return candidate;
    }

    private static class SortMap implements Comparable<SortMap> {
        public String key;
        public Double value;

        public SortMap(String key, Double value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public int compareTo(SortMap o) {
            return value < o.value ? 1 : (value.equals(o.value)) ? 0 : -1;
        }
    }

    private Map<String, Double> getSortedHashtableByValue1(Map<String, Double> h) {
        Map<String, Double> result = new LinkedHashMap<>();

        SortMap[] array = new SortMap[h.size()];
        int i = 0;
        for (String key : h.keySet()) {
            array[i] = new SortMap(key, h.get(key));
            i++;
        }
        Arrays.sort(array);

        for (i = 0; i < h.size(); i++) {
            result.put(array[i].key, array[i].value);
        }
        return result;
    }

    private Map<String, Map<String, Double>> getSortedHashtableByValue2(Map<String, Map<String, Double>> h) {
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();

        SortMap[] array = new SortMap[h.size()];
        int i = 0;
        for (String key1 : h.keySet()) {
            array[i] = new SortMap(key1, (double)h.get(key1).values().toArray()[0]);
            i++;
        }
        Arrays.sort(array);
        for (i = 0; i < h.size(); i++) {
            result.put(array[i].key, h.get(array[i].key));
        }
        return result;
    }

    private void readProfile(Map<String, BitSet> pkgBitSet,
                             Map<String, Map<String, BloomBitSet>> bitSetList,
                             String file,
                             String category) {
        ArrayList<String> lines = new ArrayList<>();
        try {
            InputStreamReader inputReader = new InputStreamReader(new FileInputStream(file));
            BufferedReader bf = new BufferedReader(inputReader);
            String str;
            while ((str = bf.readLine()) != null) {
                lines.add(str);
            }
            bf.close();
            inputReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(String line : lines){
            String[] sections = line.split("&&");
            if(sections.length == 2){                   // pkg level bf vectors
                BitSet bitSet = new BitSet(PKG_LEVEL_M);
                if(sections[1].equals("{}"))
                    continue;
                String[] bitIdxArray = sections[1].substring(1,sections[1].length()-1).split(", ");
                for(String idx : bitIdxArray){
                    bitSet.set(Integer.parseInt(idx), true);
                }
                pkgBitSet.put(sections[0], bitSet);
            } else if(sections.length == 4){            //class level bf vectors
                if(! bitSetList.containsKey(sections[0])){
                    Map<String, BloomBitSet> classBitSet = new LinkedHashMap<>();
                    bitSetList.put(sections[0], classBitSet);
                }
                BitSet bitSet = new BitSet(CLASS_LEVEL_M);
                String[] bitIdxArray = sections[2].substring(1, sections[2].length()-1).split(", ");
                for(String idx : bitIdxArray){
                    bitSet.set(Integer.parseInt(idx), true);
                }
                bitSetList.get(sections[0]).put(sections[1], new BloomBitSet(bitSet, Integer.parseInt(sections[3])));
            } else{                                     //entorpy information
                if(category.equals("app")){
                    sections = line.substring(1, line.length()-1).split(";");
                    for(String section : sections){
                        String[] sub = section.split(":");
                        if(sub.length != 2){
                            continue;
                        }
                        if(sub[0].equals("H_r")){
                            H_r = sub[1].equals("") ? 0 : Double.parseDouble(sub[1]);
                        } else if(sub[0].equals("H_r_pkg")){
                            potential_re_pkg = sub[1];
                        } else if(sub[0].equals("H_f")){
                            H_f = sub[1].equals("") ? 0 : Double.parseDouble(sub[1]);
                        } else if(sub[0].equals("H_f_pkg_list")){
                            String[] pkgs = sub[1].substring(1, sub[1].length()-1).split(", ");
                            for(String pkg : pkgs){
                                potential_flatten_pkg_list.add(pkg.trim());
                            }
                        }
                    }
                }
            }
        }
    }

    private void writeEntropy2Profile(String fileName, AppOrLibInfo info) throws IOException {
        File dir = new File(argsParser.OUTPUT_DIR);
        if(!dir.exists()){
            logger.info("Folder " + argsParser.OUTPUT_DIR + " does not exist. Create it.");
            logger.info("");
            dir.mkdirs();
        }
        File profile = new File(dir.getPath(), fileName + ".txt");
        PrintWriter printWriter = new PrintWriter(profile.getPath());
        String result = "{";
        result += "H_r:" + info.H_r + ";";
        result += "H_r_pkg:" + info.H_r_pkg + ";";
        result += "H_f:" + info.H_f + ";";
        result += "H_f_pkg_list:" + info.getParentWithSinglePkg().get(info.H_f_pkg);
        result += "}";
        printWriter.println(result);
        printWriter.close();
    }

    private void writePKGBFVectors2Profile(String fileName, Map<String, BitSet> BFVectors) throws IOException {
        File dir = new File(argsParser.OUTPUT_DIR);
        if(!dir.exists()){
            logger.info("Folder " + argsParser.OUTPUT_DIR + " does not exist. Create it.");
            logger.info("");
            dir.mkdirs();
        }
        File profile = new File(dir.getPath(), fileName + ".txt");
        PrintWriter printWriter = new PrintWriter(new FileWriter(profile.getPath(), true));
        for(String packageName : BFVectors.keySet()){
            printWriter.println(packageName + "&&" + BFVectors.get(packageName));
        }
        printWriter.close();
    }

    private void writeClazzBFVectors2Profile(String fileName, Map<String, Map<String, BloomBitSet>> bitSetList) throws IOException {//初始布隆过滤器
        File dir = new File(argsParser.OUTPUT_DIR);
        if(!dir.exists()){
            logger.info("Folder " + argsParser.OUTPUT_DIR + " does not exist. Create it.");
            logger.info("");
            dir.mkdirs();
        }
        File profile = new File(dir.getPath(), fileName + ".txt");
        PrintWriter printWriter = new PrintWriter(new FileWriter(profile.getPath(), true));
        for (String packageName : bitSetList.keySet()) {
            for (String className : bitSetList.get(packageName).keySet()) {
                printWriter.println(packageName + "&&" + className + "&&" + bitSetList.get(packageName).get(className).bitSet + "&&" + bitSetList.get(packageName).get(className).size);
            }
        }
        printWriter.close();
    }

    /**
     * whether set1 represented by bitSet1 is superset of set2 represented by bitSet2
     * @param bitSet1
     * @param bitSet2
     * @return
     */
    private boolean isSuperSet(BitSet lcBFVector, BitSet acBFVector) {
        boolean flag = false;
        BitSet tmpLcBFV = (BitSet) lcBFVector.clone();
        BitSet tmpAcBFV = (BitSet) acBFVector.clone();
        tmpLcBFV.and(tmpAcBFV);
        if (tmpLcBFV.equals(tmpAcBFV)) {
            flag = true;
        }
        return flag;
    }

    /**
     * get the jaccard similarity between lpBFVector and apBFVector
     * @param lpBFVector
     * @param apBFVector
     * @return
     */
    private double overlapRatio(BitSet lpBFVector, BitSet apBFVector){
        BitSet andResult = (BitSet) lpBFVector.clone();
        andResult.and(apBFVector);
        int andbit = andResult.cardinality();
        double similarity;
        if(lpBFVector.cardinality() > apBFVector.cardinality())
            similarity = (double) andResult.cardinality() / apBFVector.cardinality();
        else
            similarity = (double) andResult.cardinality() / lpBFVector.cardinality();
        return similarity;
    }

    /**
     * decide if a lib is excluded at pkg matching stage
     * @param candidatePairs    candidate <lp,ap> pairs at pkg matching stage
     * @param libBFVectors      the lib bloom filter vectors
     * @return
     */
    private boolean isExcludedLib(Map<String, List<String>> candidatePairs,
                                  Map<String, Map<String, BloomBitSet>> appBFVectors,
                                  Map<String, Map<String, BloomBitSet>> libBFVectors){
        int allClasses = 0, classesInCandidatePairs = 0;
        for(String lp : libBFVectors.keySet()){
            allClasses += libBFVectors.get(lp).size();
        }
        for(String lp : candidatePairs.keySet()){
            int maxClassesInAp = 0;
            for(String ap : candidatePairs.get(lp)){
                int classesInAp = appBFVectors.get(ap).size();
                maxClassesInAp = maxClassesInAp < classesInAp ? classesInAp : maxClassesInAp;
            }
            classesInCandidatePairs += maxClassesInAp;
        }
        return classesInCandidatePairs / (double)allClasses < THRESHOLD ? true : false;
    }

    /**
     * Load parameters from configuration ("parameters.properties")
     * @return true if load success, otherwize false if fail
     */
    private boolean loadParameters(){
        try {
            InputStream in = new FileInputStream("config/parameters.properties");
            Properties p = new Properties();
            p.load(in);
            CLASS_LEVEL_M = Integer.parseInt(p.getProperty("CLASS_LEVEL_M"));
            CLASS_LEVEL_K = Integer.parseInt(p.getProperty("CLASS_LEVEL_K"));
            PKG_LEVEL_M = Integer.parseInt(p.getProperty("PKG_LEVEL_M"));
            PKG_LEVEL_K = Integer.parseInt(p.getProperty("PKG_LEVEL_K"));
            PKG_OVERLAP_THRESHOLD = Double.parseDouble(p.getProperty("PKG_OVERLAP_THRESHOLD"));
            THRESHOLD = Double.parseDouble(p.getProperty("SIMILARITY_THRESHOLD"));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}