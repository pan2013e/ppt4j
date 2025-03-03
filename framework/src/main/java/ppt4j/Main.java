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

    /**
     * Returns the command line syntax for the application, including information about available commands and options.
     *
     * @return a String representing the command line syntax with available commands and options
     */
    private static String cmdLineSyntax() {
        // Return the command line syntax with available commands and options
        return cmdLineSyntax + "[<options>] <command> <args>\n" +
                """
                Commands:
                 analyze <db-id> <gt-type>  Analyze a binary for a vulnerability in the dataset
    
                Options:
                """;
    }

    /**
     * Initializes the application by parsing command line arguments and loading properties.
     *
     * @param args the command line arguments
     * @return the command line arguments array
     */
    private static String[] init(String[] args) {
        // Define command line options
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
            // Parse command line arguments
            CommandLine cmd = parser.parse(options, args, true);
            // Check if help option is present
            if (cmd.hasOption("h")) {
                formatter.printHelp(cmdLineSyntax(), options);
                System.exit(0);
            }
            // Load properties from custom file or default resource
            if (cmd.hasOption("config")) {
                PropertyUtils.load(
                        new FileInputStream(cmd.getOptionValue("config")));
            } else {
                PropertyUtils.load(ResourceUtils.readProperties());
            }
            // Override properties with command line arguments
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

    /**
     * This method dispatches the command passed as an argument, executes the corresponding action, 
     * and exits the program. If no command is provided, it prints the help message and exits with an error code.
     * 
     * @param args The command line arguments passed to the program
     */
    private static void dispatch(String[] args) {
            if (args.length == 0) {
                formatter.printHelp(cmdLineSyntax(), options); // Print help message
                System.exit(1); // Exit with error code
            }
            String command = args[0];
            String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);
            Command commandType = StringUtils.matchPrefix(command); // Get the command type
            
            //noinspection SwitchStatementWithTooFewBranches
            switch (commandType) {
                case ANALYZE -> { // Handle ANALYZE command
                    int id = Integer.parseInt(commandArgs[0]); // Parse ID from arguments
                    Vulnerability vuln = DatabaseFactory.getByDatabaseId(id); // Get vulnerability by ID
                    ExecDriver exec = ExecDriver.getInstance(); // Get instance of ExecDriver
                    String cmd = String.format("java %s -cp %s %s %s",
                            VM_OPTIONS,
                            StringUtils.getClassPathToLoad(vuln),
                            PatchAnalyzer.class.getName(),
                            String.join(" ", commandArgs)); // Construct command string
                    exec.execute(cmd); // Execute command
                }
                default -> throw new IllegalStateException("Unimplemented command: " + command); // Handle unimplemented commands
            }
            System.exit(0); // Exit with success code
        }

    /**
     * This method is the entry point of the program. It initializes the arguments, initializes properties, and then dispatches the arguments to the appropriate method.
     *
     * @param args the command line arguments passed to the program
     */
    public static void main(String[] args) {
        // Initialize the arguments
        String[] leftArgs = init(args);
        
        // Initialize properties
        PropertyUtils.init();
        
        // Dispatch the arguments to the appropriate method
        dispatch(leftArgs);
    }

}
