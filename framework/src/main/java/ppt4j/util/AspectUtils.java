package ppt4j.util;

import ppt4j.analysis.java.LibraryConstants;
import lombok.extern.log4j.Log4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;

@Aspect
@Log4j
public class AspectUtils {

    /**
     * This method is executed before any method with a signature of `void main(String[])`.
     * It initializes properties by loading properties from a resource file and initializing them.
     */
    @Before("execution(* *.main(String[]))")
    public void init() {
        // Log message to indicate the initialization of properties
        log.debug("Initializing properties");
        
        // Load properties from a resource file
        PropertyUtils.load(ResourceUtils.readProperties());
        
        // Initialize properties
        PropertyUtils.init();
    }

    /**
     * This method is a before advice that intercepts calls to the get method of ExtractorFactory class,
     * specifically when the arguments passed are prepatchPath, postpatchPath, and classPath. It checks if
     * the specified classPath is present in the current Virtual Machine's class path by calling the
     * VMUtils.checkVMClassPathPresent method.
     */
    @Before(value = "execution(* ppt4j.factory.ExtractorFactory.get(String,String,String))" +
                "&& args(prepatchPath, postpatchPath, classPath)",
                argNames = "prepatchPath,postpatchPath,classPath")
    public void checkClassPath(String prepatchPath, String postpatchPath, String classPath) {
        // Check if the specified classPath is present in the VM's class path
        VMUtils.checkVMClassPathPresent(classPath);
    }

    /**
     * This method is an aspect that intercepts method calls annotated with @MethodProfiler
     * and logs the time taken for the method to execute.
     *
     * @param point the ProceedingJoinPoint representing the method being intercepted
     * @return the result of the intercepted method
     * @throws Throwable if an error occurs during method execution
     */
    @Around("execution(* *(..)) && @annotation(ppt4j.annotation.MethodProfiler) ")
    public Object logTime(ProceedingJoinPoint point)
            throws Throwable {
        // Record the start time of method execution
        long start = System.currentTimeMillis();
        // Proceed with the execution of the intercepted method
        Object result = point.proceed();
        // Record the end time of method execution
        long end = System.currentTimeMillis();
        // Log the time taken for the method to execute
        log.debug(String.format("Result: %s finished after %d ms",
                point.getSignature().toShortString(), end - start));
        return result;
    }

    /**
     * A logging advice method that intercepts the execution of the download method in DiffParser class.
     * It measures the time taken to download a file, calculates the file size in KB, and the average download speed in KB/s.
     *
     * @param point the ProceedingJoinPoint object representing the joinpoint at which this advice is being applied
     * @return the byte array result of the download method
     * @throws Throwable if an error occurs during the method execution
     */
    @Around("execution(* ppt4j.diff.DiffParser.download(..)) ")
    public Object logDownload(ProceedingJoinPoint point)
            throws Throwable {
        // Start measuring time
        long start = System.currentTimeMillis();
        // Proceed with the actual method execution
        byte[] result = (byte[]) point.proceed();
        // Stop measuring time
        long end = System.currentTimeMillis();
        // Calculate file size in KB
        double kbsize = result.length / 1024.0;
        // Calculate average download speed in KB/s
        double kbps = kbsize / ((end - start) / 1000.0);
        // Log the file size and average download speed
        log.debug(String.format("File size %.3f KB, average %.3f KB/s ", kbsize, kbps));
        return result;
    }

    /**
     * This method is executed after the "analyze" method of PatchAnalyzer class is called.
     * It logs the result of the analysis, indicating whether a patch is present or not.
     *
     * @param result the boolean result of the analysis indicating if a patch is present or not
     */
    @AfterReturning(
                pointcut = "execution(boolean ppt4j.analysis.patch.PatchAnalyzer.analyze())",
                returning = "result")
        public void printAnalysis(boolean result) {
            // Log the result of the analysis
            log.info(String.format("Result: patch is %spresent", result ? "" : "not "));
        }

    /**
     * This method is an advice that runs after the execution of the init method in PropertyUtils class. 
     * It calls the init method of LibraryConstants class.
     */
    @After("execution(* ppt4j.util.PropertyUtils.init())")
    public void postPropertyInit() {
        // Calling the init method of LibraryConstants class
        LibraryConstants.init();
    }

    /**
     * Forks a process to execute the specified command.
     * 
     * @param cmd the command to be executed
     */
    @Before(value = "execution(* ppt4j.util.ExecDriver.dispatch(String)) && args(cmd)", argNames = "cmd")
    public void promptFork(String cmd) {
        // Log the command being executed
        log.debug("Forking process to execute command " + cmd);
    }

    /**
     * This method is executed after a method join() in ExecDriver class is successfully executed.
     * It logs the return value of the join() method along with a message indicating that the forked process exited.
     *
     * @param ret The return value of the join() method
     */
    @AfterReturning(
                pointcut = "execution(int ppt4j.util.ExecDriver.join())",
                returning = "ret")
    public void printReturnValue(int ret) {
        // Log the return value of the join() method along with a message
        log.debug("Forked process exited with return value " + ret);
    }

}