
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Unpacker;

public class PackedAppClassLoader extends ClassLoader {

  public final Map<String, byte[]> index = new HashMap<String, byte[]>();

  public PackedAppClassLoader(ClassLoader parent, InputStream pack, long unpackedSize) throws IOException {
    super(parent);
    Unpacker unpacker = Pack200.newUnpacker();
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream((int) unpackedSize);
    unpacker.unpack(pack, new JarOutputStream(byteArrayOutputStream));
    System.out.println("Unpacking done");
    index(byteArrayOutputStream.toByteArray());
  }

  private void index(byte[] unpackedApp) throws IOException {
    System.out.println("Indexing");
    JarInputStream input = new JarInputStream(new ByteArrayInputStream(unpackedApp));
    Manifest manifest = input.getManifest();
    if (manifest != null) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      manifest.write(baos);
      index.put("META-INF/MANIFEST.MF", baos.toByteArray());
    }
    JarEntry jarEntry;
    DataInputStream dis = new DataInputStream(input);
    byte[] readingBuffer = new byte[1024 * 4];
    while ((jarEntry = input.getNextJarEntry()) != null) {
      if (!jarEntry.isDirectory()) {
        long jarEntrySize = jarEntry.getSize();
        byte[] c;
        if (jarEntrySize == -1) {
          c = readFully(dis, readingBuffer);
        } else {
          c = new byte[(int) jarEntry.getSize()];
          dis.readFully(c);
        }
        index.put(jarEntry.getName(), c);
      }
    }
    System.out.println("Index done");
  }

  private byte[] readFully(InputStream in, byte[] readingBuffer) throws IOException {
    ByteArrayOutputStream res = new ByteArrayOutputStream(readingBuffer.length);
    int read = 0;
    while ((read = in.read(readingBuffer)) != -1) {
      res.write(readingBuffer, 0, read);
    }
    return res.toByteArray();
  }

  private final ProtectionDomain protectionDomain = getClass().getProtectionDomain();

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    String className = name.replace('.', '/') + ".class";
    byte[] entry = index.get(className);
    if (entry == null) {
      System.err.println("Class not found " + name);
      throw new ClassNotFoundException();
    }
    //once a class is defined, we no longer need to store its bytecode, so allow it to be GCd
    index.remove(className);
    return defineClass(name, entry, 0, entry.length, protectionDomain);
  }

  private java.net.URL toURL(String name, final byte[] bytes) {
    try {
      return new java.net.URL("packedpool", "localhost", -1, name, new URLStreamHandler() {

        @Override
        protected URLConnection openConnection(URL url) throws IOException {
          return new URLConnection(url) {

            @Override
            public void connect() throws IOException {
            }

            @Override
            public Object getContent() throws IOException {
              return bytes;
            }

            @Override
            public InputStream getInputStream() throws IOException {
              return new ByteArrayInputStream(bytes);
            }

            @Override
            public OutputStream getOutputStream() throws IOException {
              throw new UnsupportedOperationException();
            }

            @Override
            public void setDoOutput(boolean dooutput) {
              throw new UnsupportedOperationException();
            }

            @Override
            public int getContentLength() {
              return bytes.length;
            }
          };
        }
      });
    } catch (MalformedURLException ex) {
      throw new IllegalStateException("Bad code", ex);
    }
  }

  @Override
  protected URL findResource(String name) {
    final byte[] entry = index.get(name);
    if (entry != null) return toURL(name, entry);
    else {
      System.err.println("Resource not found " + name);
      return null;
    }
  }

  @Override
  protected Enumeration<URL> findResources(String name) throws IOException {
    final byte[] entry = index.get(name);
    if (entry != null) return Collections.enumeration(Arrays.asList(toURL(name, entry)));
    else {
      System.err.println("Resource not found " + name);
      return Collections.enumeration(Collections.<URL>emptyList());
    }
  }
}
