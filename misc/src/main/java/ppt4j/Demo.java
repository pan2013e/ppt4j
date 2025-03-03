package ppt4j;

import ppt4j.analysis.patch.PatchAnalyzer;
import ppt4j.database.Vulnerability;
import ppt4j.database.VulnerabilityInfo;
import ppt4j.factory.DatabaseFactory;
import ppt4j.factory.ExtractorFactory;
import ppt4j.util.PropertyUtils;
import ppt4j.util.ResourceUtils;
import ppt4j.util.VMUtils;
import com.sun.tools.attach.VirtualMachine;

import java.io.IOException;

// Command line example (invoke in the project root folder)
// java -Djdk.attach.allowAttachSelf=true --add-opens java.base/java.lang=ALL-UNNAMED \
// --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
// -cp misc/target/classes:framework/target/classes:lib/*:${HOME}/database/IntelliJ_IDEA/IU-213.7172.25/lib.jar \
// ppt4j.Demo

// p.s. In some shells, e.g. zsh, you may need to escape the asterisk

// In this prototype version, this class should be placed under `ppt4j` package,
// otherwise the aspectjweaver agent will not work as expected, and some properties
// will not be automatically loaded.

/**
 * This class is a demo of how to use the patch
 * presence test framework in real cases
 * DEMO ONLY, NOT COMPILABLE
 */
@SuppressWarnings("unused")
public class Demo {

    static {
        String prop = System.getProperty("jdk.attach.allowAttachSelf", "false");
        if (!prop.equals("true")) {
            System.err.println("Error: set -Djdk.attach.allowAttachSelf=true when starting the VM");
            System.exit(1);
        }
        try {
            VirtualMachine vm = VirtualMachine.attach("" + ProcessHandle.current().pid());
            vm.loadAgent("lib/aspectjweaver-1.9.19.jar");
            vm.detach();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
        PropertyUtils.load(ResourceUtils.readProperties());
        PropertyUtils.init();
    }

    public static void main(String[] args) throws IOException {

        String home = System.getProperty("user.home");

        // 1. Provide the directory of the project (pre-patch and post-patch versions)
        //    Specify the string to something like "...src/main/java", so that
        //    the framework can find the source code of the project
        String PREPATCH_DIR = home + "/database/prepatch/8/src/main/java";
        String POSTPATCH_DIR = home + "/database/postpatch/8/src/main/java";

        // 2. Provide the top level directory of the compiled classes
        // e.g., the classfile of aaa.bbb.XXX is in `CLASSPATH/aaa/bbb/XXX.class`,
        //          so you should provide the path to `CLASSPATH`
        // or you can provide the path to a .jar file
        String CLASSPATH = home + "/database/IntelliJ_IDEA/IU-213.7172.25/lib.jar";

        // 3. Provide paths to third-party source code (optional)
        //    For vulnerabilities in the dataset, this step is not required
        String[] THIRD_PARTY = new String[]{"<path to third-party dependencies>"};

        // Note 1: You should manually set the classpath when invoking JVM using "-cp",
        // otherwise the framework will not be able to load classes when analyzing.
        // Note 2: PPT4J loads the bytecode to be analyzed, so be careful if you
        // want to test its own 3rd-party dependencies (e.g., asm). It should be fine 
        // if you pass in a directory of classfiles, e.g., target/classes. But if you pass
        // in a jar file, please place your jar file before `lib/*` in the classpath, 
        // so that the VM loads your jar file first. However, as the loaded dependency
        // changes, the framework may not work as expected.
        VMUtils.checkVMClassPathPresent(CLASSPATH);

        // 3. Create an ExtractorFactory instance with previous resources
        // The factory instance will be responsible for feature extractions
        // and feature matching
        ExtractorFactory factory = ExtractorFactory.get(
                PREPATCH_DIR,
                POSTPATCH_DIR,
                CLASSPATH
                //, THIRD_PARTY
        );

        // 4. Create a Vulnerability instance

        // i. You can create a VulnerabilityInfo instance explicitly,
        //    then assign values to all non-null fields
        VulnerabilityInfo vulInfo = new VulnerabilityInfo();
        vulInfo.vul_id = "VUL4J-999"; // required format: VUL4J-\d+
        vulInfo.cve_id = "CVE-ID";
        vulInfo.project = "PROJECT";
        vulInfo.project_url = "https://project.url";
        vulInfo.build_system = "Maven"; // Maven, Gradle or Custom
        vulInfo.src_classes_dir = "target/classes";
        vulInfo.human_patch_url = "https://project.url/commit/commit-hash";
        vulInfo.fixing_commit_hash = "commit-hash";

        vulInfo.src_top_level_dir = null;
        vulInfo.third_party_lib_dirs = null;

        // All directories should be relative to the root of the project being tested.
        // You can manually provide src_top_level_dir to override default path derivation.
        // If the build system is 'Custom' or the src_classes_dir is
        // unconventional, you must provide src_top_level_dir explicitly.

        if (vulInfo.isEmpty()) {
            throw new RuntimeException();
        }
        // After filling in the fields, call `makeDataset`
        Vulnerability vul1 = DatabaseFactory.makeDataset(vulInfo);

        // ii. As an alternative, you can pass a json InputStream and call `makeDataset`
        // This also creates a Vulnerability instance. 
        // The json schema should match the VulnerabilityInfo class

        // iii. You can also load vulnerabilities directly from the dataset
        Vulnerability vul2 = DatabaseFactory.getByDatabaseId(8);
        factory.setVuln(vul2);

        // 5. Finally, create the analyzer instance and perform patch presence test
        PatchAnalyzer pa = new PatchAnalyzer(vul2, factory);
        pa.analyze();


    }

}
