package ppt4j.util;

import lombok.extern.log4j.Log4j;

import java.io.*;

@Log4j
public class FileUtils {

    /**
     * Creates a local directory at the specified path if it does not already exist.
     * If a file exists with the same name as the directory, an error is logged and an IllegalStateException is thrown.
     * If the directory creation fails, an error is logged and an IllegalStateException is thrown.
     * 
     * @param path the path where the directory should be created
     */
    public static void makeLocalDirectory(String path) {
        File dir = new File(path);
        
        if(dir.exists() && dir.isDirectory()) {
            return; // Directory already exists, no need to create it
        }
        
        if(dir.exists() && dir.isFile()) {
            log.error("File exists with the same name as the directory");
            throw new IllegalStateException();
        }
        
        if(!dir.mkdirs()) {
            log.error("Failed to create directory");
            throw new IllegalStateException();
        }
    }

    /**
     * Serializes an object to a specified file path using ObjectOutputStream.
     *
     * @param object the object to serialize
     * @param path the file path to save the serialized object
     * @param <T> the type of the object being serialized
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static <T> void serializeObject(T object, String path) {
        // Create a File object with the specified path
        File file = new File(path);
        File parent = file.getParentFile();
        if(parent != null) {
            // Create parent directories if they do not exist
            parent.mkdirs();
        }
        try (ObjectOutputStream oos =
                 new ObjectOutputStream(new FileOutputStream(file))) {
            // Write the object to the file using ObjectOutputStream
            oos.writeObject(object);
        } catch (IOException e) {
            // Log any IOExceptions
            log.error(e);
            // Throw an IllegalStateException if an IOException occurs
            throw new IllegalStateException();
        }
    }

    private static class ProxyOIS extends ObjectInputStream {

        public ProxyOIS(InputStream in) throws IOException {
            super(in);
        }

        /**
         * Reads and processes the class descriptor of an ObjectStreamClass. If the name of the class descriptor
         * starts with "bscout", it replaces "bscout" with "ppt4j" in the class name and looks up the corresponding
         * ObjectStreamClass. Otherwise, it returns the original ObjectStreamClass.
         * 
         * @return the processed ObjectStreamClass
         * @throws IOException if an I/O error occurs while reading the class descriptor
         * @throws ClassNotFoundException if the class specified in the class descriptor is not found
         */
        @Override
        protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
            ObjectStreamClass desc = super.readClassDescriptor();
            
            // Check if the name of the class descriptor starts with "bscout"
            if(desc.getName().startsWith("bscout")) {
                // Replace "bscout" with "ppt4j" in the class name and look up the corresponding ObjectStreamClass
                return ObjectStreamClass.lookup(Class.forName(desc.getName().replace("bscout", "ppt4j")));
            }
            
            // Return the original ObjectStreamClass if the name does not start with "bscout"
            return desc;
        }
    }

    /**
     * Deserializes an object of the specified class type from the given file path.
     * 
     * @param <T> the type of the object being deserialized
     * @param clazz the class type of the object being deserialized
     * @param path the file path from which the object will be deserialized
     * @return the deserialized object of the specified class type
     * @throws IllegalStateException if an error occurs during deserialization
     */
    public static <T> T deserializeObject(Class<T> clazz, String path) {
        try (ProxyOIS ois = new ProxyOIS(new FileInputStream(path))) { // Using try-with-resources to automatically close the stream
            return clazz.cast(ois.readObject()); // Casting and returning the deserialized object
        } catch (IOException | ClassNotFoundException e) {
            log.error(e); // Logging the exception
            throw new IllegalStateException(); // Throwing IllegalStateException in case of error
        }
    }

    /**
     * Deserializes an object of the specified class from the provided input stream using a ProxyOIS object.
     *
     * @param clazz the class of the object to be deserialized
     * @param is the input stream containing the serialized object
     * @return the deserialized object of the specified class
     * @throws IllegalStateException if an IOException or ClassNotFoundException occurs during deserialization
     */
    public static <T> T deserializeObject(Class<T> clazz, InputStream is) {
            try (ProxyOIS ois = new ProxyOIS(is)) { // Using try-with-resources to automatically close the ProxyOIS object
                return clazz.cast(ois.readObject()); // Casting the deserialized object to the specified class
            } catch (IOException | ClassNotFoundException e) {
                log.error(e); // Logging the exception
                throw new IllegalStateException(); // Throwing IllegalStateException if an exception occurs
            }
        }

}
