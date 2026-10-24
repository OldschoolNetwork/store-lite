import java.io.*;import java.util.*;import java.util.jar.*;import org.objectweb.asm.*;import org.objectweb.asm.commons.*;
/** Build-time relocation keeps Android's preinstalled Bouncy Castle separate. */
public final class RelocateCrypto {
 public static void main(String[] args)throws Exception{
  final String from="org/bouncycastle/",to="com/oldschoolnetwork/lite/crypto/";
  Remapper remap=new Remapper(){public String map(String name){return name.startsWith(from)?to+name.substring(from.length()):name;}public Object mapValue(Object value){if(value instanceof String){String s=(String)value;if(s.startsWith("org.bouncycastle."))return "com.oldschoolnetwork.lite.crypto."+s.substring("org.bouncycastle.".length());}return super.mapValue(value);}};
  JarOutputStream out=new JarOutputStream(new FileOutputStream(args[0]));Set<String> seen=new HashSet<String>();
  try{for(int i=1;i<args.length;i++){JarFile input=new JarFile(args[i]);try{Enumeration<JarEntry> entries=input.entries();while(entries.hasMoreElements()){
   JarEntry entry=entries.nextElement();String name=entry.getName();if(entry.isDirectory()||name.startsWith("META-INF/")||name.equals("module-info.class"))continue;
   InputStream stream=input.getInputStream(entry);ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1)bytes.write(buffer,0,n);stream.close();byte[] data=bytes.toByteArray();
   if(name.endsWith(".class")){ClassReader reader=new ClassReader(data);ClassWriter writer=new ClassWriter(0);reader.accept(new ClassRemapper(writer,remap),0);data=writer.toByteArray();}
   if(name.startsWith(from))name=to+name.substring(from.length());if(!seen.add(name))continue;JarEntry output=new JarEntry(name);output.setTime(0);out.putNextEntry(output);out.write(data);out.closeEntry();
  }}finally{input.close();}}}finally{out.close();}
  System.out.println("Relocated "+seen.size()+" entries. Original signed jars are build inputs only.");
 }
}
