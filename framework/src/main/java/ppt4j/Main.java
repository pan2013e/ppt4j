package ppt4j;

import ppt4j.analysis.patch.PatchAnalyzer;
import ppt4j.database.Vulnerability;
import ppt4j.factory.DatabaseFactory;
import ppt4j.util.ExecDriver;
import ppt4j.util.PropertyUtils;
import ppt4j.util.ResourceUtils;
import ppt4j.util.StringUtils;
import lombok.AllArgsConstructor;
import org.apache.commons.cli.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

public final class Main {

    private static final String VM_OPTIONS =
            "-javaagent:lib/aspectjweaver-1.9.19.jar " +
            "-Xss2m -XX:CompilerThreadStackSize=2048 -XX:VMThreadStackSize=2048 " +
            "--add-opens java.base/java.lang=ALL-UNNAMED " +
            "--add-opens java.base/java.lang.reflect=ALL-UNNAMED";

    private static final Options options = new Options();
    private static final HelpFormatter formatter = new HelpFormatter();
    private static final String cmdLineSyntax =
            "java -cp [...] ppt4j.Main ";


    @AllArgsConstructor
    public enum Command {
        ANALYZE("analyze");

        public final String name;
    }

    private static String cmdLineSyntax() {
        return cmdLineSyntax + "[<options>] <command> <args>\n" +
                """
                Commands:
                 analyze <db-id> <gt-type>  Analyze a binary for a vulnerability in the dataset

                Options:
                """;
    }

    private static String[] init(String[] args) {
        options.addOption("config", "config", true,
                "Set path to a custom *.properties file with ISO-8859-1 encoding");
        options.addOption("h", "help", false,
                "Print help message and exit");
        Option configs = Option.builder("X")
                .hasArgs()
                .valueSeparator('=')
                .desc("Add or override existing properties, e.g. -Xppt4j.database.root=~/database")
                .build();
        options.addOption(configs);
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args, true);
            if (cmd.hasOption("h")) {
                formatter.printHelp(cmdLineSyntax(), options);
                System.exit(0);
            }
            if (cmd.hasOption("config")) {
                PropertyUtils.load(
                        new FileInputStream(cmd.getOptionValue("config")));
            } else {
                PropertyUtils.load(ResourceUtils.readProperties());
            }
            PropertyUtils.override(cmd.getOptionProperties("X"));
            return cmd.getArgs();
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp(cmdLineSyntax(), options);
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    private static void dispatch(String[] args) {
        if (args.length == 0) {
            formatter.printHelp(cmdLineSyntax(), options);
            System.exit(1);
        }
        String command = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);
        Command commandType = StringUtils.matchPrefix(command);
        //noinspection SwitchStatementWithTooFewBranches
        switch (commandType) {
            case ANALYZE -> {
                int id = Integer.parseInt(commandArgs[0]);
                Vulnerability vuln = DatabaseFactory.getByDatabaseId(id);
                ExecDriver exec = ExecDriver.getInstance();
                String cmd = String.format("java %s -cp %s %s %s",
                        VM_OPTIONS,
                        StringUtils.getClassPathToLoad(vuln),
                        PatchAnalyzer.class.getName(),
                        String.join(" ", commandArgs));
                exec.execute(cmd);
            }
            default -> throw new IllegalStateException("Unimplemented command: " + command);
        }
        System.exit(0);
    }

    public static void main(String[] args) {
        String[] leftArgs = init(args);
        PropertyUtils.init();
        dispatch(leftArgs);
    }

}
