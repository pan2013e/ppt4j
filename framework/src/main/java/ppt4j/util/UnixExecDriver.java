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

    /**
     * Retrieves the command line of the process. If the process state is uninitialized, it returns null. 
     * Otherwise, it returns the command line as a single string with arguments separated by spaces.
     *
     * @return The command line of the process, or null if the process state is uninitialized.
     */
    @Override
    public String getCmdline() {
        // Check if the process state is uninitialized
        if(state == ProcState.UNINITIALIZED)
            return null; // Return null if uninitialized
        
        // Join the command and arguments using a space separator
        return String.join(" ", processBuilder.command());
    }

    /**
     * Dispatches a command to be executed in a separate process.
     * If the process state is not UNINITIALIZED, it throws an IllegalStateException.
     * Sets up a ProcessBuilder with the given command and working directory,
     * redirects error stream to standard output, then starts the process in a new thread.
     */
    @Override
    public synchronized void dispatch(final String cmd) {
        if(state != ProcState.UNINITIALIZED) {
            throw new IllegalStateException(
                    "Process has already been dispatched");
        }
        
        // Set up ProcessBuilder with command and working directory
        processBuilder = new ProcessBuilder(shell, "-c", cmd)
                .redirectErrorStream(true)
                .directory(new File(workingDir));
        
        state = ProcState.RUNNING; // Set state to RUNNING
        
        // Create a new FutureTask to run the process and wait for completion
        task = new FutureTask<>(() -> {
            Process proc = processBuilder.start(); // Start the process
            pipe(proc.getInputStream()); // Pipe the input stream of the process
            return proc.waitFor(); // Wait for the process to finish and return the exit value
        });
        
        new Thread(task).start(); // Start a new thread to run the task
    }

    /**
     * Waits for the process to finish executing and returns the exit value of the process.
     * If the process is not running, an IllegalStateException is thrown.
     * If an InterruptedException or ExecutionException occurs while waiting for the process to finish,
     * the method returns -1.
     *
     * @return the exit value of the process
     */
    @Override
    public synchronized int join() {
        if(state != ProcState.RUNNING) {
            throw new IllegalStateException("Process not running");
        }
        try {
            exitValue = task.get(); // Wait for the process to finish and get the exit value
            state = ProcState.EXITED; // Update the state to show that the process has exited
            return exitValue;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace(); // Print the stack trace of the exception
            state = ProcState.EXITED; // Update the state to show that the process has exited
            return -1;
        }
    }

    /**
     * Executes the given command by dispatching it and then waiting for it to finish.
     * 
     * @param cmd the command to execute
     * @return the result of the command execution
     */
    @Override
    public int execute(final String cmd) {
        // Dispatch the command
        dispatch(cmd);
        
        // Wait for the command to finish and return the result
        return join();
    }

    /**
     * Returns the exit value of the process. 
     * If the process has not exited, it throws an IllegalStateException.
     *
     * @return the exit value of the process
     * @throws IllegalStateException if the process has not exited
     */
    @Override
    public int exitValue() {
        // Check if the process has exited
        if(state != ProcState.EXITED) {
            throw new IllegalStateException("Process not exited");
        }
        // Return the exit value
        return exitValue;
    }

    /**
     * Resets the state of the processor to UNINITIALIZED.
     */
    @Override
    public void reset() {
        // Set the state of the processor to UNINITIALIZED
        state = ProcState.UNINITIALIZED;
    }

    /**
     * Reads input from the given InputStream and prints it to the console line by line.
     * 
     * @param in the InputStream to read from
     */
    private void pipe(InputStream in) {
        // Create a BufferedReader to read input from the InputStream
        new BufferedReader(new InputStreamReader(in))
                // Read lines from the BufferedReader
                .lines()
                // Print each line to the console
                .forEach(System.out::println);
    }

}
