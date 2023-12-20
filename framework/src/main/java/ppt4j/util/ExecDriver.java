package ppt4j.util;

@SuppressWarnings("unused")
public interface ExecDriver {

    static ExecDriver getInstance() {
        String os = System.getProperty("os.name").toLowerCase();
        if(os.contains("nux") || os.contains("mac") || os.contains("nix")) {
            return new UnixExecDriver();
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + os);
        }
    }

    String getCmdline();

    void dispatch(final String cmd);

    int join();

    int execute(final String cmd);

    int exitValue();

    void reset();

}
