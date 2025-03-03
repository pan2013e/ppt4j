package ppt4j.util;

@SuppressWarnings("unused")
public interface ExecDriver {

    /**
     * Returns an instance of ExecDriver based on the current operating system.
     * If the operating system is Linux, macOS, or Unix-like, it returns an instance of UnixExecDriver.
     * Otherwise, it throws an UnsupportedOperationException with the message "Unsupported OS: [OS name]".
     * 
     * @return an instance of ExecDriver based on the current operating system
     */
    static ExecDriver getInstance() {
        // Get the current operating system name
        String os = System.getProperty("os.name").toLowerCase();
        
        // Check if the operating system is Linux, macOS, or Unix-like
        if(os.contains("nux") || os.contains("mac") || os.contains("nix")) {
            return new UnixExecDriver(); // Return an instance of UnixExecDriver
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + os); // Throw an exception for unsupported OS
        }
    }

    /**
     * This method returns the command line arguments passed to the program.
     * 
     * @return a String representing the command line arguments
     */
    String getCmdline();

    /**
     * Dispatches the given command to the appropriate handler.
     * 
     * @param cmd the command to be dispatched
     */
    void dispatch(final String cmd);

    /**
     * Combines the elements of a collection into a single integer value.
     *
     * @return the result of joining the elements of the collection
     */
    int join();

    /**
     * Executes the given command and returns the exit code.
     *
     * @param cmd the command to be executed
     * @return the exit code of the command
     */
    int execute(final String cmd);

    /**
     * Returns the exit value of the subprocess.
     * The exit value is typically a numeric value representing the result of the subprocess execution.
     * A non-zero exit value usually indicates an error occurred during the process execution.
     * 
     * @return the exit value of the subprocess
     */
    int exitValue();

    /**
     * Resets the state of the object to its initial state.
     */
    void reset();

}
