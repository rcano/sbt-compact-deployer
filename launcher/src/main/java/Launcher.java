
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class Launcher {

  public static void main(String[] args) throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException {
    System.out.println("Unpacking application...");
    long start = System.nanoTime();
    DataInputStream dis = new DataInputStream(Launcher.class.getResourceAsStream("/app.size"));
    long appSize = dis.readLong();
    dis.close();
    PackedAppClassLoader packedApp = new PackedAppClassLoader(Launcher.class.getResourceAsStream("/app"), appSize);
    long total = System.nanoTime() - start;
    System.out.printf("Unpacking+indexing took %.2fs\n", total / 1e9);
    
    if (Boolean.getBoolean("launcherDumpIndex")) {
      packedApp.index.forEach((key, value) -> System.out.println(key));
    }
    
    String chosenMainClass = System.getProperty("launcherMainClass");
    String mainClass = null;
    if (chosenMainClass != null) {
      mainClass = chosenMainClass;
    } else {
      Manifest manifest = new Manifest(new ByteArrayInputStream(packedApp.index.get("META-INF/MANIFEST.MF")));
      Attributes values = manifest.getMainAttributes();
      mainClass = values.getValue("Main-Class");
      if (mainClass == null) throw new IllegalStateException("No main class found in app jar");
    }
    Class<?> loadedClass = packedApp.loadClass(mainClass);
    loadedClass.getMethod("main", String[].class).invoke(null, new Object[]{args});
  }

}
