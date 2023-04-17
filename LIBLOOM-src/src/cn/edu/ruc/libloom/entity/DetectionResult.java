package cn.edu.ruc.libloom.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xuebo @date 2022/1/8
 */
public class DetectionResult {
    private String appname;
    private List<DetectionLib> libraries;
    private String time;  //seconds

    public DetectionResult(){
        libraries = new ArrayList<>();
    }

    public String getAppname() {
        return appname;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public List<DetectionLib> getLibraries() {
        return libraries;
    }

    public void setLibraries(List<DetectionLib> libraries) {
        this.libraries = libraries;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void updateLibraries(String libname, String p_version, double similarity){
        boolean isExist = false;
        for(DetectionLib lib : libraries){
            if(lib.getName().equals(libname)){
                isExist = true;
                lib.update(p_version, similarity);
                break;
            }
        }
        if(!isExist) {
            DetectionLib lib = new DetectionLib(libname,p_version, similarity);
            libraries.add(lib);
        }
    }

    public String prettyJSON(){
        String buff = "{\n";
        buff += indent(2) + "\"appname\":" + "\"" + appname + "\","+"\n";
        if(libraries.size() > 0){
            buff += indent(2) + "\"libraries\": [" + "\n";
            for(DetectionLib lib : libraries){
                buff += indent(4) + "{" + "\n";
                buff += indent(6) + "\"name\":" + "\"" + lib.getName() + "\"," + "\n";
                buff += indent(6) + "\"version\": [" + "\n";
                for (String potential_v : lib.getVersion()){
                    buff += indent(8) + "\"" + potential_v + "\"," + "\n";
                }
                buff = buff.substring(0, buff.length() - 2) + "\n";
                buff += indent(6) + "]," + "\n";
                buff += indent(6) + "\"similarity\":" + lib.getSimilarity() + "\n";
                buff += indent(4) + "}," + "\n";
            }
            buff = buff.substring(0, buff.length()-2) + "\n";
            buff += indent(2) + "]," + "\n";
        } else {
            buff += indent(2) + "\"libraries\": [],\n";
        }

        buff += indent(2) + "\"time\":\""  + time +"\"\n";
        buff += "}";
        return buff;
    }

    private String indent(int n){
        String space = "";
        for(int i=0;i<n;i++)
            space += " ";
        return space;
    }
}

