package ppt4j.util;

import lombok.extern.log4j.Log4j;

import java.io.*;

@Log4j
public class FileUtils {

    public static void makeLocalDirectory(String path) {
        File dir = new File(path);
        if(dir.exists() && dir.isDirectory()) {
            return;
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static <T> void serializeObject(T object, String path) {
        File file = new File(path);
        File parent = file.getParentFile();
        if(parent != null) {
            parent.mkdirs();
        }
        try (ObjectOutputStream oos =
                 new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(object);
        } catch (IOException e) {
            log.error(e);
            throw new IllegalStateException();
        }
    }

    private static class ProxyOIS extends ObjectInputStream {

        public ProxyOIS(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
            ObjectStreamClass desc = super.readClassDescriptor();
            if(desc.getName().startsWith("bscout")) {
                return ObjectStreamClass.lookup(Class.forName(desc.getName().replace("bscout", "ppt4j")));
            }
            return desc;
        }
    }

    public static <T> T deserializeObject(Class<T> clazz, String path) {
        try (ProxyOIS ois = new ProxyOIS(new FileInputStream(path))) {
            return clazz.cast(ois.readObject());
        } catch (IOException | ClassNotFoundException e) {
            log.error(e);
            throw new IllegalStateException();
        }
    }

    public static <T> T deserializeObject(Class<T> clazz, InputStream is) {
        try (ProxyOIS ois = new ProxyOIS(is)) {
            return clazz.cast(ois.readObject());
        } catch (IOException | ClassNotFoundException e) {
            log.error(e);
            throw new IllegalStateException();
        }
    }

}
