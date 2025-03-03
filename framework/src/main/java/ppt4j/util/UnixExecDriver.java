package ppt4j.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

@SuppressWarnings("unused")
final class UnixExecDriver implements ExecDriver {

    private static class ProcState {
        static final int UNINITIALIZED = 0;
        static final int RUNNING = 1;
        static final int EXITED = 2;
    }

    private ProcessBuilder processBuilder;
    private int state = ProcState.UNINITIALIZED;
    private int exitValue = -1;
    private FutureTask<Integer> task;

    private final String shell;
    private final String workingDir;

    public UnixExecDriver(final String shell,
                          final String workingDir) {
        this.shell = shell;
        this.workingDir = workingDir;
    }

    public UnixExecDriver(final String workingDir) {
        this("/bin/sh", workingDir);
    }

    public UnixExecDriver() {
        this(System.getProperty("user.dir"));
    }

    @Override
    public String getCmdline() {
        if(state == ProcState.UNINITIALIZED)
            return null;
        return String.join(" ", processBuilder.command());
    }

    @Override
    public synchronized void dispatch(final String cmd) {
        if(state != ProcState.UNINITIALIZED) {
            throw new IllegalStateException(
                    "Process has already been dispatched");
        }
        processBuilder = new ProcessBuilder(shell, "-c", cmd)
                .redirectErrorStream(true)
                .directory(new File(workingDir));
        state = ProcState.RUNNING;
        task = new FutureTask<>(() -> {
            Process proc = processBuilder.start();
            pipe(proc.getInputStream());
            return proc.waitFor();
        });
        new Thread(task).start();
    }

    @Override
    public synchronized int join() {
        if(state != ProcState.RUNNING) {
            throw new IllegalStateException("Process not running");
        }
        try {
            exitValue = task.get();
            state = ProcState.EXITED;
            return exitValue;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            state = ProcState.EXITED;
            return -1;
        }
    }

    @Override
    public int execute(final String cmd) {
        dispatch(cmd);
        return join();
    }

    @Override
    public int exitValue() {
        if(state != ProcState.EXITED) {
            throw new IllegalStateException("Process not exited");
        }
        return exitValue;
    }

    @Override
    public void reset() {
        state = ProcState.UNINITIALIZED;
    }

    private void pipe(InputStream in) {
        new BufferedReader(new InputStreamReader(in))
                .lines()
                .forEach(System.out::println);
    }

}
