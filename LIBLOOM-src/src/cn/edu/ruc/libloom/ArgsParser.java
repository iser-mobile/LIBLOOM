package cn.edu.ruc.libloom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author xuebo @date 2022/1/1
 */
public class ArgsParser {
    public String ACTION;
    public String APP_LIB_DIR;
    public String OUTPUT_DIR;
    public String APP_PROFILE_DIR;
    public String LIB_PROFILE_DIR;
    public boolean DEBUG;
    private Logger logger = LoggerFactory.getLogger(ArgsParser.class);

    public ArgsParser(String [] args){
        DEBUG = false;

        List<String> argList = new ArrayList<String>(Arrays.asList(args));
        ACTION = argList.get(0);
        if(ACTION.equals("profile")){
            if(argList.contains("-h")){     // optional: -h help
                showHelp();
            }
            if(argList.contains("-d")){    // required: -d app/lib directory
                APP_LIB_DIR = argList.get(argList.indexOf("-d") + 1);
            } else {
                logger.error("Arguments Error !!");
                showHelp();
            }
            if(argList.contains("-o")){    // required: -o profile directory
                OUTPUT_DIR = argList.get(argList.indexOf("-o") + 1);
            } else {
                logger.error("Arguments Error !!");
                showHelp();
            }
            if(argList.contains("-v")){     // optional: -v debug
                DEBUG = true;
            }
        } else if(ACTION.equals("detect")){
            if(argList.contains("-h")){     // optional: -h help
                showHelp();
            }
            if(argList.contains("-ad")){    // required: -ad app profile directory
                APP_PROFILE_DIR = argList.get(argList.indexOf("-ad") + 1);
            } else {
                logger.error("Arguments Error !!");
                showHelp();
            }
            if(argList.contains("-ld")){    // required: -ld lib profile directory
                LIB_PROFILE_DIR = argList.get(argList.indexOf("-ld") + 1);
            } else {
                logger.error("Arguments Error !!");
                showHelp();
            }
            if(argList.contains("-o")){    // required: -o profile directory
                OUTPUT_DIR = argList.get(argList.indexOf("-o") + 1);
            } else {
                logger.error("Arguments Error !!");
                showHelp();
            }
            if(argList.contains("-v")){     // optional: -v debug
                DEBUG = true;
            }
        } else {
            logger.error("Only profile or detect is supported.");
            showHelp();
        }
    }
    public void showHelp(){
        logger.error("######################################################################################################");
        logger.error("usage <profile>: ");
        logger.error("\tjava -jar LIBLOOM.jar profile [-h] -d app_lib_dir -o profile_dir [-v]");
        logger.error("\t  -h              \t show help message.");
        logger.error("\t  -d app_lib_dir  \t specify app(.apk) or lib(.aar/.jar) folder.");
        logger.error("\t  -o profile_dir  \t specify profile folder.");
        logger.error("\t  -v              \t show debug information.");
        logger.error("usage <detection>: ");
        logger.error("\tjava -jar LIBLOOM.jar detect [-h] -ad apps_profile_dir -ld libs_profile_dir -o result_dir [-v]");
        logger.error("\t  -h                    \t show help message.");
        logger.error("\t  -ad apps_profile_dir  \t specify app profile folder.");
        logger.error("\t  -ld libs_profile_dir  \t specify lib profile folder.");
        logger.error("\t  -o result_dir         \t specify result folder.");
        logger.error("\t  -v                    \t show debug information.");
        logger.error("######################################################################################################");
        System.exit(1);
    }
}
