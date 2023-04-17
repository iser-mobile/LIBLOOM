package cn.edu.ruc.libloom.preprocess;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.ShrikeClass;
import com.ibm.wala.dalvik.classLoader.DexFileModule;
import com.ibm.wala.dalvik.classLoader.DexIClass;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.TemporaryFile;
import com.ibm.wala.util.strings.Atom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CodeInfoCollector {

    private static final ClassLoader WALA_CLASSLOADER = AnalysisScopeReader.class.getClassLoader();
    private static final String BASIC_FILE = "config/primordial.txt";
    private static final String STD_JAR = "config/android.jar";
    private String ABSOLUTEPATH = "";

    private AnalysisScope mScope;
    private IClassHierarchy mCha;
    private File mFile;
    private AppOrLibInfo applibInfo;
    private int clazzCount = 0;

    private static Logger logger = LoggerFactory.getLogger(CodeInfoCollector.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            logger.error("Use the generator with one argument: <path_to_jar/aar/apk>");
            System.exit(0);
        }
        CodeInfoCollector bfGen = new CodeInfoCollector(args[0], "");
        if (!bfGen.generate()) {
            logger.error("Error occurs for collecting info in " + args[0]);
        }
        bfGen.printRecords();
    }

    public static AppOrLibInfo getInfo(String file, String absolutePath) throws IOException {
        //System.out.print(FilenameUtils.getBaseName(file) + ": ");
        CodeInfoCollector bfGen = new CodeInfoCollector(file, absolutePath);
        if (!bfGen.generate()) {
            throw new IOException("Error occurs for collecting info in " + file);
        }
        return bfGen.applibInfo;
    }

    public CodeInfoCollector(String file, String absolutePath) {
        mFile = new File(file);
        ABSOLUTEPATH = absolutePath;
    }

    private boolean initialize() {
        ZipFile apkAchive = null;
        try {
            mScope = AnalysisScopeReader.readJavaScope(ABSOLUTEPATH + BASIC_FILE, null, WALA_CLASSLOADER);
            mScope.addToScope(ClassLoaderReference.Primordial, new JarFile(ABSOLUTEPATH + STD_JAR));
            String name = mFile.getName();
            String ext = name.substring(name.length() - 4);
            if (ext.equalsIgnoreCase(".apk")) {
                mScope.setLoaderImpl(ClassLoaderReference.Application,
                        "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
                apkAchive = new ZipFile(mFile);
                Enumeration<?> enums = apkAchive.entries();
                int tempFileIdx = 0;
                while (enums.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) enums.nextElement();
                    String entryName = entry.getName();
                    if (entryName.startsWith("classes") && entryName.endsWith(".dex")) {
                        File tf = new File(System.getProperty("java.io.tmpdir") +
                                File.separator + (tempFileIdx++) + "-" + entryName);
                        tf.deleteOnExit();
                        TemporaryFile.streamToFile(tf, new InputStream[]{apkAchive.getInputStream(entry)});
                        mScope.addToScope(ClassLoaderReference.Application, DexFileModule.make(tf));
                    }
                }
            } else {
                mScope.addToScope(ClassLoaderReference.Application, new JarFile(mFile));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (apkAchive != null) {
                    apkAchive.close();
                }
            } catch (Exception e) {
            }
        }

        try {
            //TODO: cannnot get all classes (eg.PhotoView-2.3.0 rxandroid-3.0.0), use makeWithRoot instead
//            mCha = ClassHierarchyFactory.make(mScope);
            mCha = ClassHierarchyFactory.makeWithRoot(mScope);
            Iterator<IClass> iter = mCha.iterator();
            while (iter.hasNext()){
                IClass klass = iter.next();
                if (klass.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)) {
                    continue;
                }
                clazzCount ++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void visitAllParents(IClass klass, Set<IClass> primordialParents, Set<IClass> nonPrimordialParents) {
        IClass s = klass.getSuperclass();
        while (s != null) {
            if (s.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)) {
                primordialParents.add(s);
                break;
            } else {
                nonPrimordialParents.add(s);
                s = s.getSuperclass();
            }
        }
        Collection<IClass> ifs = klass.getAllImplementedInterfaces();
        for (IClass k : ifs) {
            if (k.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)) {
                primordialParents.add(k);
            } else {
                visitAllParents(k, primordialParents, nonPrimordialParents);
            }
        }
    }

    private boolean overridingFramework(IClass klass, IMethod method) {
        Selector s = method.getSelector();
        IMethod m = klass.getMethod(s);
        if (m != null && m.getName().equals(method.getName())) {
            return true;
        }
        return false;
    }

    private boolean overridingFramework(Set<IClass> primordial, IMethod method) {
        if(method.getName().toString().equals("setScaleType")){
            logger.debug("class：" + method.getDeclaringClass().getName().toString() + " method:" + method.getName().toString());
            for(IClass k : primordial){
                logger.debug("\t" + k.getName());
            }
        }
        for (IClass k : primordial) {
            if (overridingFramework(k, method)) {
                return true;
            }
        }
        return false;
    }

    private void buildFuzzyType(TypeReference type, StringBuffer buffer) {
        IClass k;
        if (type.isPrimitiveType() ||
                type.getClassLoader().equals(ClassLoaderReference.Primordial)) {
            buffer.append(type.getName().toString());
        } else if ((k = mCha.lookupClass(type)) != null &&
                k.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)) {
            buffer.append(type.getName().toString());
        } else {
            TypeReference tmpType = type;
            while (tmpType.isArrayType()) {
                buffer.append("[");
                tmpType = tmpType.getArrayElementType();
            }
            buffer.append("X");
        }
        buffer.append(";");
    }

    private void buildFuzzySignatures(IClass klass, List<String> sigList) {
        Set<IClass> primordialParents = new HashSet<>();
        Set<IClass> nonPrimordialParents = new HashSet<>();
        visitAllParents(klass, primordialParents, nonPrimordialParents);
        for (IMethod m : klass.getDeclaredMethods()) {
//            logger.info(m.getSignature());
//            logger.info("isAbstract:" + m.isAbstract() + "\tisBridge:" + m.isBridge() + "\tisFinal:" + m.isFinal() + "\tisNative:" + m.isNative() + "\tisStatic:" + m.isStatic());
            String name = m.getName().toString();
            StringBuffer buffer = new StringBuffer();
            if ("<init>".equals(name) || "<clinit>".equals(name)) {  //ignore <init> and <cinit>
                continue;
                // buffer.append(name);
            }
//            else if (!m.isPrivate() && !m.isSynthetic() && !m.isStatic() &&
//                    !m.isNative() && overridingFramework(primordialParents, m)) {
//                buffer.append(name);
//            }
            buffer.append("(");
            int nParams = m.getNumberOfParameters();
            for (int i = 0; i < nParams; i++) {
                TypeReference paramType = m.getParameterType(i);
                buildFuzzyType(paramType, buffer);
            }
            buffer.append(")");
            TypeReference retType = m.getReturnType();
            buildFuzzyType(retType, buffer);
            sigList.add(buffer.toString());
        }
    }

    private void uniquifySignature(List<String> sigList){
        Map <String, Integer> sigMap = new HashMap<>();
        for(String sig : sigList){
            if(sigMap.containsKey(sig)){
                sigMap.put(sig,new Integer(sigMap.get(sig).intValue() + 1));
            } else {
                sigMap.put(sig, new Integer(1));
            }
        }
        sigList.clear();
        for (String sig : sigMap.keySet()){
            for(int i = 1; i <= sigMap.get(sig).intValue(); i++){
                sigList.add(sig + i);
            }
        }
    }

    private boolean generate() {
        if (!initialize()) {
            return false;
        }
        applibInfo = new AppOrLibInfo(mFile.getName());
        Iterator<IClass> iter = mCha.iterator();
        Map<String, List<String>> pkgClassesMap = new HashMap<>();

        while (iter.hasNext()) {
            IClass klass = iter.next();
            if (klass.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)) {
                continue;
            }

            // get package name of IClass
            Atom aPackage = klass.getName().getPackage();
            String pkg = "";
            if (aPackage != null) {
                pkg = aPackage.toString();
            }
            // store pkg - class list map
            if(! pkgClassesMap.containsKey(pkg)){
                List<String> classesInPkg = new LinkedList<>();
                pkgClassesMap.put(pkg,classesInPkg);
            }
            pkgClassesMap.get(pkg).add(klass.getName().toString());

            if(! applibInfo.getFeatures().containsKey(pkg)){
                List<ClassFeatures> cFeaturesList = new LinkedList<>();
                applibInfo.getFeatures().put(pkg, cFeaturesList);
            }

            Collection<IMethod> methods = (Collection<IMethod>) klass.getDeclaredMethods();
            Collection<IField> fields = klass.getDeclaredInstanceFields();
            Collection<IField> static_fields = klass.getDeclaredStaticFields();
            typeScanner(pkg, methods, fields, static_fields);

            if (methods.isEmpty()) continue;
            boolean initOnly = false;
            if(methods.size() <= 2){  //class included only <init><cinit> is ignore
                initOnly = true;
                for(IMethod m: methods){
                    if(! ("<init>".equals(m.getName().toString()) || "<clinit>".equals(m.getName().toString()))){
                        initOnly = false;
                        break;
                    }
                }
            }
            List<String> memtypelist = new LinkedList<>();
            ClassMemberTypes memtypes = new ClassMemberTypes();

            getMemberSignature(fields, memtypes, memtypelist);
            getMemberSignature(static_fields, memtypes, memtypelist);
            if( !(initOnly || (clazzCount == 1 && methods.size() <= 3)) ){
                ClassFeatures cFeatures = new ClassFeatures(klass.getName().toString());
                List<String> list = new LinkedList<>();
                buildFuzzySignatures(klass, list);
                uniquifySignature(list);
                cFeatures.setMethods(list);
                cFeatures.setMemtypes(memtypelist);

                try {
                    String superI = klass.getSuperclass().getName().toString();
                    String superII;
                    if(klass.getClass().getName().equals("com.ibm.wala.classLoader.ShrikeClass")){
                        superII = "L" + ((ShrikeClass)klass).getReader().getSuperName();
                    } else if(klass.getClass().getName().equals("com.ibm.wala.dalvik.classLoader.DexIClass")){
                        superII = ((DexIClass)klass).getSuperclass().getName().toString();
                    } else {
                        superII = "";
                    }
                    if(! superI.equals(superII)){
                        cFeatures.setSuperClass("extends:X;");
                    } else {
                        if(klass.getSuperclass().getClassLoader().getReference().equals(ClassLoaderReference.Primordial)){
                            cFeatures.setSuperClass("extends:" + superI + ";");
                        } else {
                            cFeatures.setSuperClass("extends:X;");
                        }
                    }

                    List<String> interfaceSig = new LinkedList<>();
                    if(klass.getDirectInterfaces().size() == 0){
                        if(klass.getClass().getName().equals("com.ibm.wala.classLoader.ShrikeClass")){
                            int count = ((ShrikeClass)klass).getReader().getInterfaceCount();
                            for(int i = 1; i <= count; i++){
                                interfaceSig.add("interface:X" + i);
                            }
                        }
                    } else {
                        Collection<IClass> iter2 = (Collection<IClass>)klass.getDirectInterfaces();
                        int cnt = 1;
                        for(IClass it : iter2){
                            if(it.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)){
                                interfaceSig.add("interface:" + it.getName().toString());
                            } else {
                                interfaceSig.add("interface:X" + cnt);
                                cnt ++;
                            }
                        }
                    }

                    cFeatures.setInterfaces(interfaceSig);
                } catch (InvalidClassFileException e) {
                    e.printStackTrace();
                }
                applibInfo.getFeatures().get(pkg).add(cFeatures);
            }
        }

        // get single-pkg (pkg with only classes and no-sub-pkg)
        for(String pkg : pkgClassesMap.keySet()){
            boolean haveSubPkg = false;
            if(!pkg.equals("")){
                for(String tpkg : pkgClassesMap.keySet()){
                    if(tpkg.startsWith(pkg) && ! tpkg.equals(pkg)){
                        haveSubPkg = true;
                        break;
                    }
                }
                if(! haveSubPkg){
                    applibInfo.getSinglePackage().add(pkg);
                }
            }
        }

        // get parent-single pkg map
        List<String> singlePkg2 = new LinkedList<>();
        singlePkg2.addAll(applibInfo.getSinglePackage());
        for(String pkg : applibInfo.getSinglePackage()){
            int idx = pkg.lastIndexOf("/");
            if(idx > 0){
                String parent = pkg.substring(0, idx);
                if(! applibInfo.getParentWithSinglePkg().containsKey(parent)){
                    List<String> pkgList= new LinkedList<>();
                    applibInfo.getParentWithSinglePkg().put(parent, pkgList);
                }
                applibInfo.getParentWithSinglePkg().get(parent).add(pkg);
                singlePkg2.remove(pkg);
            }
        }
        if(! singlePkg2.isEmpty()){
            applibInfo.getParentWithSinglePkg().put("<root>", singlePkg2);
        }

        //TODO: entropy analysis
        applibInfo.calculateEntropy();
        return true;
    }
    private boolean getMemberSignature(Collection<IField> fields, ClassMemberTypes v, List<String> vlist){
        String vSignature = "";
        if(! fields.isEmpty()){
            for(IField field : fields){
//                logger.info("\t\t<member>:" + field.getFieldTypeReference().getName().toString());
                String type = field.getFieldTypeReference().getName().toString();
                if(type.equals("[B")){        //byte[]
                    v.bytesAdd();
                    vSignature = "[B"+v.getBytesCnt();
                } else if(type.equals("Z")){ //boolean
                    v.boolAdd();
                    vSignature = "Z"+v.getBoolCnt();
                } else if(type.equals("[Z")){ //boolean[]
                    v.boolsAdd();
                    vSignature = "[Z"+v.getBoolsCnt();
                } else if(type.equals("I")){ //int
                    v.intAdd();
                    vSignature = "I" + v.getIntCnt();
                } else if(type.equals("[I")){ //int[]
                    v.intsAdd();
                    vSignature = "[I" + v.getIntsCnt();
                } else if(type.equals("S")){ //short
                    v.shortAdd();
                    vSignature = "S" + v.getShortCnt();
                } else if(type.equals("[S")){ //short[]
                    v.shortsAdd();
                    vSignature = "[S" + v.getShortsCnt();
                } else if(type.equals("J")){ //long
                    v.longAdd();
                    vSignature = "J" + v.getLongCnt();
                } else if(type.equals("[J")){ //long[]
                    v.longsAdd();
                    vSignature = "[J" + v.getLongsCnt();
                } else if(type.equals("F")){ //float
                    v.floatAdd();
                    vSignature = "F" + v.getFloatCnt();
                } else if(type.equals("[F")){ //float[]
                    v.floatsAdd();
                    vSignature = "[F" + v.getFloatsCnt();
                } else if(type.equals("D")){ //double
                    v.doubleAdd();
                    vSignature = "D" + v.getDoubleCnt();
                } else if(type.equals("[D")){ //double[]
                    v.doublesAdd();
                    vSignature = "[D" +v.getDoublesCnt();
                } else if(type.equals("Ljava/lang/String")){ //String
                    v.stringAdd();
                    vSignature = "Ljava/lang/String" + v.getStringCnt();
                } else if(type.equals("[Ljava/lang/String")){ //String[]
                    v.stringsAdd();
                    vSignature = "[Ljava/lang/String" + v.getStringsCnt();
                } else if(type.equals("Ljava/util/Map")){ //map
                    v.mapAdd();
                    vSignature = "Ljava/util/Map" + v.getMapCnt();
                } else if(type.equals("Ljava/util/List")){ //list
                    v.listAdd();
                    vSignature = "Ljava/util/List" + v.getListCnt();
                } else {    //non primitive type
                    v.nonPrimitiveAdd();
                    vSignature = "X" + v.getNonPrimitiveCnt();
                }
                vlist.add(vSignature);
            }
        }
        return true;
    }

    /**
     * @func  statistic all types that ignore primitive and framework type
     * @param pkgName   package name
     * @param methods   methods collection in pkg
     * @param fields    fields collection in pkg
     * @param static_fields static fields collection in pkg
     */
    private void typeScanner(String pkgName,
                             Collection<? extends IMethod> methods,
                             Collection<IField> fields,
                             Collection<IField> static_fields){
        IClass k;
        Collection<IField> all_fields = new ArrayList<>();
        all_fields.addAll(fields);
        all_fields.addAll(static_fields);  //merge
        Map<String, Integer> typeCountMap;
        if(! applibInfo.getTypesInPkg().containsKey(pkgName)){
            typeCountMap = new HashMap<>();
            applibInfo.getTypesInPkg().put(pkgName, typeCountMap);
        }
        typeCountMap = applibInfo.getTypesInPkg().get(pkgName);

        for(IMethod m : methods){
            //get parameter type
            for(int i=0; i < m.getNumberOfParameters(); i++){
                TypeReference tRef = m.getParameterType(i);
                if(tRef.isPrimitiveType() || tRef.getClassLoader().equals(ClassLoaderReference.Primordial) ||
                        (tRef.isArrayType() && (tRef.getArrayElementType().isArrayType() || tRef.getArrayElementType().getClassLoader().equals(ClassLoaderReference.Primordial)))){
                    continue;
                } else if ((k = mCha.lookupClass(tRef)) != null &&
                        k.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)){
                    continue;
                }
                String paramType = tRef.getName().toString();
                if(! typeCountMap.containsKey(paramType)){
                    typeCountMap.put(paramType, new Integer(0));
                }
                typeCountMap.put(paramType, typeCountMap.get(paramType).intValue() + 1);
            }

            //get return type
            TypeReference tRef = m.getReturnType();
            if(tRef.isPrimitiveType() || tRef.getClassLoader().equals(ClassLoaderReference.Primordial) ||
                    (tRef.isArrayType() && (tRef.getArrayElementType().isArrayType() || tRef.getArrayElementType().getClassLoader().equals(ClassLoaderReference.Primordial)))){
                continue;
            } else if ((k = mCha.lookupClass(tRef)) != null &&
                    k.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)){
                continue;
            }
            String returnType = tRef.getName().toString();
            if(! typeCountMap.containsKey(returnType)){
                typeCountMap.put(returnType, new Integer(0));
            }
            typeCountMap.put(returnType, typeCountMap.get(returnType).intValue() + 1);

        }

        for(IField f : all_fields){
            TypeReference tRef = f.getFieldTypeReference();
            if(tRef.isPrimitiveType() || tRef.getClassLoader().equals(ClassLoaderReference.Primordial) ||
                    (tRef.isArrayType() && (tRef.getArrayElementType().isArrayType() || tRef.getArrayElementType().getClassLoader().equals(ClassLoaderReference.Primordial)))){
                continue;
            } else if ((k = mCha.lookupClass(tRef)) != null &&
                    k.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)){
                continue;
            }

            String type = tRef.getName().toString();
            if(! typeCountMap.containsKey(type)){
                typeCountMap.put(type, new Integer(0));
            }
            typeCountMap.put(type, typeCountMap.get(type).intValue() + 1);
        }
    }

    private void printRecords(){
        try {
            PrintWriter pw = new PrintWriter(new FileOutputStream("result/sig.txt"));
            for(String pkg : applibInfo.getFeatures().keySet()){
                logger.info("<package>: " + pkg);
                for(ClassFeatures cf : applibInfo.getFeatures().get(pkg)){
                    logger.info("\t<class>:" + cf.getClassName());
                    logger.info("\t\t<superclass>:" + cf.getSuperClass());
                    pw.println("<package>: " + pkg);
                    pw.println("\t<class>:" + cf.getClassName());
                    pw.println("\t\t<superclass>:" + cf.getSuperClass());
                    for(String im : cf.getInterfaces()){
                        logger.info("\t\t<interface>:" + im);
                        pw.println("\t\t<interface>:" + im);
                    }
                    for(String m : cf.getMethods()){
                        logger.info("\t\t<method>:" + m);
                        pw.println("\t\t<method>:" + m);
                    }
                    for(String v: cf.getMemtypes()){
                        logger.info("\t\t<member>: " + v);
                        pw.println("\t\t<member>: " + v);
                    }
                }
            }
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }
}
