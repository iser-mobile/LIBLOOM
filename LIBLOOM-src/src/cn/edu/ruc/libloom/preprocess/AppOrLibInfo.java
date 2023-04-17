package cn.edu.ruc.libloom.preprocess;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author xuebo @date 2022/1/6
 */
public class AppOrLibInfo {
    private Map<String, List<ClassFeatures>> features;
    private Map<String, Map<String, Integer>> typesInPkg;
    private List<String> singlePackage;
    public Map<String, List<String>> parentWithSinglePkg;
    private Map<String, Map<String, Double>> entropy;
    public double H_r, H_f;
    public String H_r_pkg, H_f_pkg;
    private String artefactId;
    private int artefactIdLevel;

    public static String[] ignore_pkg_prefix = {"androidx","android","kotlin","kotlinx"};

    public AppOrLibInfo(String filename){
        features = new HashMap<>();
        typesInPkg = new HashMap<>();
        singlePackage = new LinkedList<>();
        parentWithSinglePkg = new HashMap<>();
        entropy = new HashMap<>();
        H_r = 0;
        H_f = 0;
        H_r_pkg = "";
        H_f_pkg = "";
        int idx = filename.lastIndexOf("-");  // only for app name
        if(idx > 0){
            artefactId = filename.substring(idx+1, filename.length()-4);
        } else {
            artefactId = filename.substring(0, filename.length()-4);
        }
        artefactIdLevel = artefactId.split("\\.").length;
    }

    public Map<String, List<ClassFeatures>> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, List<ClassFeatures>> features) {
        this.features = features;
    }

    public Map<String, Map<String, Integer>> getTypesInPkg() {
        return typesInPkg;
    }

    public void setTypesInPkg(Map<String, Map<String, Integer>> typesInPkg) {
        this.typesInPkg = typesInPkg;
    }

    public List<String> getSinglePackage() {
        return singlePackage;
    }

    public void setSinglePackage(List<String> singlePackage) {
        this.singlePackage = singlePackage;
    }

    public Map<String, List<String>> getParentWithSinglePkg() {
        return parentWithSinglePkg;
    }

    public void setParentWithSinglePkg(Map<String, List<String>> parentWithSinglePkg) {
        this.parentWithSinglePkg = parentWithSinglePkg;
    }

    public void calculateEntropy(){
        // STEP1: cal entropy for all types from direct classes in pkg, donated by typeH
        for(String pkg : features.keySet()){
            double typeH = 0.0;
            int totalFrequency = 0;
            for(String type : typesInPkg.get(pkg).keySet()){
                totalFrequency += typesInPkg.get(pkg).get(type).intValue();
            }
            for(String type : typesInPkg.get(pkg).keySet()){
                double p = typesInPkg.get(pkg).get(type).intValue() /(double) totalFrequency;
                typeH += (- p * Math.log(p) / Math.log(2));
            }

            pkg = pkg.equals("") ? "<root>" : pkg;
            if(! entropy.containsKey(pkg)){
                Map<String, Double> variantH = new HashMap<>();
                entropy.put(pkg, variantH);
            }
            entropy.get(pkg).put("typeH", typeH);
        }
        //STEP2: get the max type-entropy and pkg
        for(String pkg : entropy.keySet()){
            if(isIgnorePkg(pkg)){
                continue;
            }
            String newname = pkg.trim().equals("") ? "<root>" : pkg;
            if(entropy.get(pkg).get("typeH") != null){
                double H = entropy.get(pkg).get("typeH").doubleValue();
                if(H > H_r){
                    H_r = H;
                    H_r_pkg = newname;
                }
            }
        }
        // STEP3: cal entropy for pkg that have one more single-pkg, donated by subPkgTypeH
        for(String pkg : parentWithSinglePkg.keySet()){
            if(! entropy.containsKey(pkg)){
                Map<String, Double> variantH = new HashMap<>();
                entropy.put(pkg, variantH);
            }
            if(parentWithSinglePkg.get(pkg).size() > 1){
                double subPkgTypeH = 0.0;
                Map<String, Integer> allTypesWithFrequency = new HashMap<>();
                for(String subpkg : parentWithSinglePkg.get(pkg)){
                    if(subpkg.equals(H_r_pkg))  // exclude potential re-package when cal subPkgTypeH
                        continue;
                    for(String type : typesInPkg.get(subpkg).keySet()){
                        if(allTypesWithFrequency.containsKey(type)){
                            allTypesWithFrequency.put(type, allTypesWithFrequency.get(type).intValue() + typesInPkg.get(subpkg).get(type).intValue());
                        } else {
                            allTypesWithFrequency.put(type, typesInPkg.get(subpkg).get(type).intValue());
                        }
                    }
                }

                int totalFrequency = 0;
                for(String type : allTypesWithFrequency.keySet()){
                    totalFrequency += allTypesWithFrequency.get(type).intValue();
                }
                for(String type : allTypesWithFrequency.keySet()){
                    double p = allTypesWithFrequency.get(type).intValue() / (double) totalFrequency;
                    subPkgTypeH += -(p * Math.log(p) / Math.log(2));
                }
                entropy.get(pkg).put("subPkgTypeH", subPkgTypeH);
            }
        }

        //STEP4 : get the max subPkgType entropy and root-pkg
        for(String pkg : entropy.keySet()){
            if(isIgnorePkg(pkg)){
                continue;
            }
            String newname = pkg.trim().equals("") ? "<root>" : pkg;
            if(entropy.get(pkg).get("subPkgTypeH") != null){
                double sH = entropy.get(pkg).get("subPkgTypeH").doubleValue();
                if(sH > H_f){
                    H_f = sH;
                    H_f_pkg = newname;
                }
            }
        }
    }

    private boolean isIgnorePkg(String pkg){
        if(pkg.split("/").length > artefactIdLevel){
            return true;
        } else if(pkg.toLowerCase().replaceAll("/","\\.").equals(artefactId)){
            return false;
        }
        for(String prefix : ignore_pkg_prefix){
            if(pkg.startsWith(prefix)){
                return true;
            }
        }
        return false;
    }
}
