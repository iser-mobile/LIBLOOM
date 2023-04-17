package cn.edu.ruc.libloom.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xuebo @date 2022/1/8
 */
public class DetectionLib {
    private String name;
    private List<String> version;
    private double similarity;

    public DetectionLib(String name, String p_version, double sim){
        this.name = name;
        this.version = new ArrayList<>();
        this.version.add(p_version);
        this.similarity = sim;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getVersion() {
        return version;
    }

    public void setVersion(List<String> version) {
        this.version = version;
    }

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }

    public void update(String p_version, double sim){
        if(sim > similarity){
            version.clear();
            version.add(p_version);
            similarity = sim;
        } else if(sim == similarity){
            version.add(p_version);
        }
    }
}
