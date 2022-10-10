## Open-source benchmark
  The dataset is available at [Google Drive](https://drive.google.com/drive/folders/1vJD7sYrtO7Dqm101SuLgfXEYbC1pWwcn?usp=sharing).It contains 100 open-source apps that are selected from the F-Droid repository and 349 TPLs with 551 versions. The apps are obfuscated by different obfuscation tools, i.e. ProGuard, Dasho, Allatori and Obfuscapk.

- ProGuard(default): Code shrinking is enabled.
- ProGuard(flatten): Package flatten is enabled, i.e., *-flattenpackagehierarchy* is used in *proguard.pro* file. 
- ProGuard(repackage): Package repackage is enabled, i.e., *-repackageclasses* is used in *proguard.pro* file.
- Dasho(flatten): Similar to ProGuard.
- Dasho(repackage): Similar to ProGuard.
- Allatori(weak repackage). *force-default-package* is disabled in allatori config file.
- Allatori(strong repackage). *force-default-package* is enabled in allatori config file.
- Obfuscapk. Almost all of obfuscation options listed in obfuscapk are used. 

## Closed-source benchmark
The dataset is constructed by [Zhan et al.](https://sites.google.com/view/libdetect/home/dataset).

## Large-scale benchmark
  2552 apps are from [Xiaomi App Store](https://m.app.mi.com/). 11648 TPLs we collected are from [Maven](https://mvnrepository.com/). The dataset is so large please contract us if you need anything. 