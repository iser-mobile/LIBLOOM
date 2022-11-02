# LIBLOOM
LIBLOOM is designed for third-party library (TPL) detection in Android apps, which encodes the signature of each class into an individual Bloom filter for fast containment queries and then performs TPL detection on the query results. Advanced non-structure-preserving (NSP) obfuscations including class repackaging and package flattening are heuristically addressed in it.

## Usage
The artifact is built under Java Development Kit 1.8. 

LIBLOOM is composed of two steps, i.e. profiling and detection. To detect TPLs from Android apps, you should first use "profile" command to generate profiles for Android apps and libraries. Then, you should use "detect" command to detect which TPLs are potentially used in Android apps. The detection result can be found in your specified directory (i.e., result_dir).   
### Profiling
	java -jar LIBLOOM.jar profile [-h] -d app_lib_dir -o profile_dir [-v]
		
	Arguments:
		-h                show help message.
		-d app_lib_dir    specify app(.apk) or lib(.aar/.jar) folder.
		-o profile_dir    specify profile folder.
		-v                show debug infomation.

### TPLs Detection
	java -jar LIBLOOM.jar detect [-h] -ad apps_profile_dir -ld libs_profile_dir
	 							-o result_dir [-v]
	
	Arguments:
 		-h                    	 show help message.
 		-ad apps_profile_dir  	 specify app profile folder.
 		-ld libs_profile_dir  	 specify lib profile folder.
 		-o result_dir         	 specify result folder.
 		-v                    	 show debug infomation.

## Test Case
You can test LIBLOOM by detecting given demo app(artifacts/demo/apps).

First, generating profiles for demo app and libraries.

	java -jar LIBLOOM.jar profile -d demo/apps -o profile/apps
	
	java -jar LIBLOOM.jar profile -d demo/libs -o profile/libs
Then, detecting TPLs from demo app.

	java -jar LIBLOOM.jar detect -ad profile/apps -ld profile/libs -o result
The detection result (.json) can be found in *result* directory.
## For More
For more details about LIBLOOM, you can find in our paper.