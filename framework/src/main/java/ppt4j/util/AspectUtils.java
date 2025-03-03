package ppt4j.util;

import ppt4j.analysis.java.LibraryConstants;
import lombok.extern.log4j.Log4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;

@Aspect
@Log4j
public class AspectUtils {

    @Before("execution(* *.main(String[]))")
    public void init() {
        log.debug("Initializing properties");
        PropertyUtils.load(ResourceUtils.readProperties());
        PropertyUtils.init();
    }

    @Before(value = "execution(* ppt4j.factory.ExtractorFactory.get(String,String,String))" +
            "&& args(prepatchPath, postpatchPath, classPath)",
            argNames = "prepatchPath,postpatchPath,classPath")
    public void checkClassPath(String prepatchPath, String postpatchPath, String classPath) {
        VMUtils.checkVMClassPathPresent(classPath);
    }

    @Around("execution(* *(..)) && @annotation(ppt4j.annotation.MethodProfiler) ")
    public Object logTime(ProceedingJoinPoint point)
            throws Throwable {
        long start = System.currentTimeMillis();
        Object result = point.proceed();
        long end = System.currentTimeMillis();
        log.debug(String.format("Result: %s finished after %d ms",
                point.getSignature().toShortString(), end - start));
        return result;
    }

    @Around("execution(* ppt4j.diff.DiffParser.download(..)) ")
    public Object logDownload(ProceedingJoinPoint point)
            throws Throwable {
        long start = System.currentTimeMillis();
        byte[] result = (byte[]) point.proceed();
        long end = System.currentTimeMillis();
        double kbsize = result.length / 1024.0;
        double kbps = kbsize / ((end - start) / 1000.0);
        log.debug(String.format("File size %.3f KB, average %.3f KB/s ", kbsize, kbps));
        return result;
    }

    @AfterReturning(
            pointcut = "execution(boolean ppt4j.analysis.patch.PatchAnalyzer.analyze())",
            returning = "result")
    public void printAnalysis(boolean result) {
        log.info(String.format("Result: patch is %spresent", result ? "" : "not "));
    }

    @After("execution(* ppt4j.util.PropertyUtils.init())")
    public void postPropertyInit() {
        LibraryConstants.init();
    }

    @Before(value = "execution(* ppt4j.util.ExecDriver.dispatch(String)) && args(cmd)", argNames = "cmd")
    public void promptFork(String cmd) {
        log.debug("Forking process to execute command " + cmd);
    }

    @AfterReturning(
            pointcut = "execution(int ppt4j.util.ExecDriver.join())",
            returning = "ret")
    public void printReturnValue(int ret) {
        log.debug("Forked process exited with return value " + ret);
    }

}