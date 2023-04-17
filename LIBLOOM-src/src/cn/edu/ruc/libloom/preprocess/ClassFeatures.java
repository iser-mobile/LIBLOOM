package cn.edu.ruc.libloom.preprocess;

import java.util.List;

/**
 * @author xuebo @date 2022/1/6
 */
public class ClassFeatures {
    String className;
    List<String> methods;
    List<String> memtypes;
    String superClass;
    List<String> interfaces;

    public ClassFeatures(String className){
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }

    public List<String> getMemtypes() {
        return memtypes;
    }

    public void setMemtypes(List<String> memtypes) {
        this.memtypes = memtypes;
    }

    public String getSuperClass() {
        return superClass;
    }

    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(List<String> interfaces) {
        this.interfaces = interfaces;
    }
}
