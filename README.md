--------------
Breaking News!
--------------
This codebase is in the process of being split up to better support long-term maintenance and modular aspects of different tools
available. The new repositories will be:

- [JuiceboxGUI](https://github.com/aidenlab/juiceboxgui) for visualization of Hi-C maps with Juicebox Desktop and genome assembly correction with Assembly Tools.
- [HiCTools](https://github.com/aidenlab/hictools) for building and writing .hic files (Pre, Addnorm, and Statistics)
- [JuicerTools](https://github.com/aidenlab/juicertools) for downstream analysis of .hic files (HiCCUPS, Arrowhead, APA, etc.)
- [Java Straw](https://github.com/aidenlab/java-straw) to quickly read and stream data from .hic files into Java, and is used by the above repositories.

We also have new tools:

- [Straw](https://github.com/aidenlab/straw) to quickly read and stream data from .hic files into C++, python, R, and MATLAB.
- [POSSUMM](https://github.com/sa501428/EigenVector) for new C++ code to rapidly calculate A-B compartments (i.e.
  eigenvectors) for higher resolutions
- [EMT](https://github.com/sa501428/hic-emt) for upgrading older .hic files or making smaller .hic files for regions of
  interest.

--------------
本仓库：Windows / JDK 25 构建与性能优化版
--------------
基于 Juicebox v2.17.00（commit c7b6988），在 Windows 上以 JDK 25 + Ant 构建，
针对装配编辑（JBAT 工作流）与热图渲染做了多轮性能优化。
版本对照：基线 `b17f0e8` → 优化主提交 `f8ea3fb` → 当前 `d1de0ae`。

性能优化清单（均有等价性验证）

坐标变换与块缓存
- modifyBlock 预计算 bin->bin 映射表：按（scaffold 版本、binSize、hicMapScale）
  失效重建；修复了 scaffold 数量守卫 bug（聚合为 1 个 scaffold 时表此前不会
  构建，导致每条记录仍走逐条二分）。变换约 5x（200 万条 ~180ms -> ~25ms），
  输出与原实现逐位一致（含 AllByAll、精确起点、长度 1 scaffold、越界 bin）。
- 原始块缓存跨装配编辑保留：移动/翻转 scaffold 后只失效变换后的块，
  不再重复磁盘 I/O + 解压 + 解析；编辑周期实测 159.7ms -> ~2ms。
- modifyBlock 恒等映射复用原 ContactRecord：未移动的 scaffold 不再重复分配
  对象，单次剖析会话观测到 1.7GB 的对象分配由此消除。

渲染
- 热图 tile 直写像素：contact 单像素填充直接写入 tile 栅格 int[]，
  绕过 Graphics2D 状态机；渲染突发期 fillRect 家族开销 ~18% -> ~9.6%。
- 小地图（minimap）直写像素 + 状态缓存：原实现每次全量重渲染最粗 zoom 整层
  （装配视图 9560x9560 bins、约 3800 万条记录）且走慢速 Graphics 路径，
  是加载后遮罩迟迟不解除的原因；现按视图状态缓存，渲染走直写路径。

解析与分配
- BinReader / DatasetReaderV2 按实际记录密度预分配容量（0.9 系数校准），
  常态块浪费降约 90% 且不再反复扩容拷贝。
- Feature2DHandler.getNearbyFeatures 结果列表预分配（EDT 绘制期不再反复扩容）。

实测效果（genome.hic 装配编辑会话，JFR 对比）
- 装配坐标查找 lookUpOriginalAggregateScaffold：174 样本 -> 0（完全走数组查表）。
- Object[] 分配：702 次 / 1.8GB -> 561 次 / 1.4GB（单次会话）。
- 编辑后重渲染开销主要集中在必要的 tile 重画（直写路径），无逐条二分。

验证方式
- juicebox.tools.HiCTools dump 输出与优化前逐字节一致（退出码 0）。
- modifyBlock 合成基准校验和与原实现一致；扩展表与兜底路径逐元素比对一致；
  各 zoom 块加载记录数与优化前逐一相同。
- GUI 在 inter.hic 与 genome.hic 上目检渲染正常（主热图、小地图、装配色块）。

构建（Windows / JDK 25）

    set JAVA_HOME=D:/runtime/jdk-25
    ant -Dskip.tests=true -Djdk.home.1.8=D:/runtime/jdk-25 all

GUI 产物：out/artifacts/Juicebox_jar/Juicebox.jar
（ uber-JAR 已包含 commons-math3，缺失会导致读取 .hic 时 NoClassDefFoundError ）

--------------
About Juicebox
--------------
Juicebox is visualization software for Hi-C data. This distribution includes the source code for
Juicebox, <a href="https://github.com/theaidenlab/juicer/wiki/Download">Juicer Tools</a>,
and <a href="https://aidenlab.org/assembly/">Assembly Tools</a>
.  <a href="https://github.com/theaidenlab/juicebox/wiki/Download">Download Juicebox here</a>, or
use <a href="https://aidenlab.org/juicebox">Juicebox on the web</a>. Detailed documentation is
available <a href="https://github.com/theaidenlab/juicebox/wiki">on the wiki</a>. Instructions below pertain primarily
to usage of command line tools and the Juicebox jar files.

Juicebox can now be used to visualize and interactively (re)assemble genomes. Check out the Juicebox Assembly Tools
Module website https://aidenlab.org/assembly for more details on how to use Juicebox for assembly.

Juicebox was created by <a href="https://github.com/jrobinso">Jim Robinson</a>,
<a href="https://github.com/nchernia">Neva C. Durand</a>, and <a href="http://www.erez.com/">Erez Lieberman Aiden</a>. Past contributors include <a href="https://github.com/imachol">Ido Machol</a>, <a href="https://github.com/zgire">Zulkifl Gire</a>, <a href="https://github.com/mhoeger">Marie Hoeger</a>, <a href="https://github.com/asddf123789">Fanny Huang</a>, <a href="https://github.com/mikeehman">Nam Hee Kim</a>, <a href="https://github.com/nguyenkvi">Vi Nguyen</a>, <a href="https://github.com/bluejay9676">Jay Ryu</a>, <a href="https://github.com/musianat">Nathaniel T. Musial</a>, and <a href="https://github.com/ranganmostofa11">Ragib Mostofa</a>.

Ongoing development work is carried out by <a href="https://github.com/sa501428">Muhammad Saad Shamim</a>, <a href="https://github.com/nchernia">Neva C. Durand</a>, and <a href="https://github.com/dudcha">Olga Dudchenko</a>.

--------------
Questions?
--------------

For FAQs, or for asking new questions, please see our forum: <a href="https://aidenlab.org/forum.html">aidenlab.org/forum.html</a>.

--------------
IntelliJ Setup
--------------

Use IntelliJ IDEA (Community edition - free)

To set up in IDEA, have the Java SDK installed
then you'll point to it (IntelliJ has lots of documentation on this sort of thing).

* Then go to `VCS` -> `checkout from version control`.
* You'll need to do is be sure `*.sizes` is included as a file to be copied over to the class files.
  Set this up via IntelliJ `Preferences` -> `Compiler`. Add `?*.sizes` to the list of `Resource Patterns`.
* While there, also go to `Java Compiler` and put this into additional command line options: `-Xlint:all -target 1.7`
The former turns on all warnings, the latter gives some flexibility since some people haven't updated Java to 1.8 yet.
* Then go to `Run` -> `Edit Configurations`.
* With the `+` sign, add `Application`.
* You'll create two of these, one for the GUI (call it Juicebox GUI or whatever you want, really) and one for the CLT.
* Set the main class by clicking the little `...` button next to the text box for main class

        MainWindow.java is the main method class for the visualization/GUI portion of the software.
        HiCTools.java is the main method class for the analysis/CLT portion.

* For the GUI under VM Options:

        -Xmx2000m
        -Djnlp.loadMenu="https://hicfiles.tc4ga.com/juicebox.properties"

* For the CLT use

        -Xmx2000m

* Note that the `Xmx2000m` flag sets the maximum memory heap size to 2GB.
  Depending on your computer you might want more or less.
  Some tools will break if there's not enough memory and the file is too large,
  but don't worry about that for development; 2GB should be fine.
* One last note: be sure to `Commit and Push` when you commit files, it's hidden in the dropdown menu button in the
  commit window.

----------------------------------
Hardware and Software Requirements
----------------------------------
The minimum software requirement to run Juicebox is a working Java installation
(version > 1.6) on Windows, Linux, and Mac OSX.  We recommend using the latest
Java version available, but please do not use the Java Beta Version. Minimum
system requirements for running Java can be found at
https://java.com/en/download/help/sysreq.xml. To download and install the latest
Java Runtime Environment (JRE), please go to https://www.java.com/download.

We recommend having at least 2GB free RAM for the best user experience with
Juicebox.

To launch the Juicebox application from command line, type java -Xms512m -Xmx2048m -jar Juicebox.jar

Note: the -Xms512m flag sets the minimum memory heap size at 512 megabytes, and
the -Xmx2048m flag sets the maximum size at 2048 megabytes (2 gigabytes). These
values may be adjusted as appropriate for your machine.

-------------
Documentation
-------------
We have extensive documentation for how to use Juicebox at
https://github.com/theaidenlab/juicebox/wiki including a video, a Quick Start Guide, and a
detailed tutorial.

For using Juicebox to assemble genomes see https://www.aidenlab.org/assembly/.

------------------------
Command Line Tools Usage
------------------------

See the documentation at https://github.com/theaidenlab/juicer/wiki for information
on how to use the Juicer tools.

--------------------------------
Compiling Jars from Source Files
--------------------------------
1. You should have Java 1.8 JDK and Apache Ant installed on your system. See below for more information.
2. Go to the folder containing the Juicebox source files and edit the juicebox.properties file with the proper Java JDK Address.
3. Open the command line, navigate to the folder containing the build.xml file and type ant The process should take no more than a minute to build on most machines.
4. The jars are written to the directory out/. You can change this by editing the build.xml file.

* Installing Java 1.8 JDK

For Windows/Mac/Linux, the Java 1.8 JDK can be installed from here:
https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
(Alternative) For Ubuntu/LinuxMint
https://tecadmin.net/install-oracle-java-8-jdk-8-ubuntu-via-ppa/

* Installing Apache Ant
  Mac Ant should be installed on most Macs. To verify installation via the command prompt, type ant -version If Ant is not on your Mac, install it via homebrew. At the command prompt, type
  brew update brew install ant You may need to install Homebrew (https://brew.sh/) on your machine See the following Stackoverflow post for more details:
  https://stackoverflow.com/questions/3222804/how-can-i-install-apache-ant-on-mac-os-x

Windows Installing Ant requires some minor changes to your system environment. Follow the instructions in this article:
https://www.nczonline.net/blog/2012/04/12/how-to-install-apache-ant-on-windows/

Linux In the command prompt, type sudo apt-get install ant or sudo yum install ant depending on your package installer
