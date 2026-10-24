package com.oldschoolnetwork.certificates;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;

/** API 8 compatible. Uses the device BKS provider on pre-ICS systems. */
public final class RootCertificates {
    public static final String BUNDLE_SHA256="796f1389bfea13c0ed78f663b765baaff54a508e5483786bd6ece73c5e3e3cbe";
    public static final String BUNDLE_DATE="2026-10-20";
    public interface Log { void write(String message); }
    private RootCertificates() {}
    public static String quote(String s) { return "'"+s.replace("'","'\\''")+"'"; }
    private static String hex(byte[] data) {
        StringBuilder s=new StringBuilder();for(byte b:data)s.append(String.format(Locale.US,"%02x",b&255));return s.toString();
    }
    public static String subjectHash(X509Certificate cert) throws Exception {
        byte[] digest=MessageDigest.getInstance("MD5").digest(cert.getSubjectX500Principal().getEncoded());
        return String.format(Locale.US,"%02x%02x%02x%02x",digest[3]&255,digest[2]&255,digest[1]&255,digest[0]&255);
    }
    private static void copy(InputStream in,OutputStream out) throws IOException {
        try {byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}finally {try{in.close();}finally{out.close();}}
    }
    public static List<X509Certificate> certificates(Context context) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();copy(context.getAssets().open("certificates/cacert.pem"),out);
        byte[] bytes=out.toByteArray();
        if(!hex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(BUNDLE_SHA256))throw new IOException("Certificate bundle checksum mismatch.");
        java.util.regex.Matcher pem=java.util.regex.Pattern.compile("-----BEGIN CERTIFICATE-----[\\s\\S]+?-----END CERTIFICATE-----").matcher(new String(bytes,"US-ASCII"));
        List<X509Certificate> result=new ArrayList<X509Certificate>();
        while(pem.find()) {
            X509Certificate cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(pem.group().getBytes("US-ASCII")));
            if(cert.getBasicConstraints()<0)throw new IOException("A bundle entry is not a certificate authority.");
            result.add(cert);
        }
        if(result.size()!=118)throw new IOException("Incomplete Mozilla certificate bundle: "+result.size());
        return result;
    }
    public static String runRoot(String script) throws Exception {
        final Process process=new ProcessBuilder(android.os.Process.myUid()==0?"sh":"su","-c",script).redirectErrorStream(true).start();
        Timer timer=new Timer(true);timer.schedule(new TimerTask(){public void run(){process.destroy();}},120000);
        try {
            ByteArrayOutputStream output=new ByteArrayOutputStream();InputStream in=process.getInputStream();byte[] buf=new byte[1024];int n;
            while((n=in.read(buf))!=-1){if(output.size()<16000)output.write(buf,0,n);}in.close();
            int code=process.waitFor();if(code!=0)throw new IOException("Certificate operation failed ("+code+"). "+output.toString("UTF-8"));
            return output.toString("UTF-8");
        }finally {timer.cancel();}
    }
    public static String systemShellPrelude() {
        return "set -e\ncase \"$(id)\" in uid=0\\(*) ;; *) echo 'Root access required'; exit 1;; esac\non_exists() { ls \"$1\" >/dev/null 2>&1; }\non_zero() { case \"$1\" in 0) return 0;; *) return 1;; esac; }\non_one() { case \"$1\" in 1) return 0;; *) return 1;; esac; }\non_remove() { for on_file in \"$@\"; do if on_exists \"$on_file\"; then rm \"$on_file\"; fi; done; }\non_system_device=\nwhile read on_dev on_path on_fs on_opts on_rest; do case \"$on_path\" in /system) on_system_device=$on_dev; break;; esac; done < /proc/mounts\ncase \"$on_system_device\" in '') echo 'A separately mounted /system partition is required'; exit 1;; esac\non_remount() { mount -o \"remount,$1\" \"$on_system_device\" /system; }\n";
    }
    private static String mountPrelude() {
        return systemShellPrelude()+"was_ro=0\ntrap 'on_remove /system/etc/security/.on-ca-write-probe-$$; if on_one \"$was_ro\"; then on_remount ro || true; fi' EXIT\nif ! (umask 077; : > /system/etc/security/.on-ca-write-probe-$$) 2>/dev/null; then was_ro=1; on_remount rw; fi\non_remove /system/etc/security/.on-ca-write-probe-$$\n";
    }
    private static void text(File file,String text) throws IOException {
        Writer writer=new OutputStreamWriter(new FileOutputStream(file),"UTF-8");try{writer.write(text);}finally{writer.close();}
    }
    public static String install(Context context,Log log) throws Exception {
        if(Build.VERSION.SDK_INT>=34)throw new IOException("This installer supports certificate stores on Android 2.2–13. Android 14+ requires a separate system integration.");
        List<X509Certificate> certificates=certificates(context);
        File stage=new File(context.getFilesDir(),"ca-stage");if(!stage.isDirectory()&&!stage.mkdirs())throw new IOException("Cannot prepare certificates.");
        String backup="/data/local/on-ca-backups/"+System.currentTimeMillis();
        StringBuilder install=new StringBuilder(),restore=new StringBuilder("#!/system/bin/sh\n"+mountPrelude());
        int added=0;
        if(Build.VERSION.SDK_INT<14) {
            File source=new File("/system/etc/security/cacerts.bks");
            KeyStore store=KeyStore.getInstance("BKS");InputStream in=new FileInputStream(source);
            try{store.load(in,"changeit".toCharArray());}finally{in.close();}
            for(X509Certificate cert:certificates)if(store.getCertificateAlias(cert)==null){store.setCertificateEntry("on-mozilla-"+hex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())),cert);added++;}
            File merged=new File(stage,"cacerts.bks");OutputStream out=new FileOutputStream(merged);
            try{store.store(out,"changeit".toCharArray());}finally{out.close();}
            // Never ship BKS v2 to Android 2.2. The platform's own provider must write v1.
            DataInputStream version=new DataInputStream(new FileInputStream(merged));int v;try{v=version.readInt();}finally{version.close();}
            if(v!=1)throw new IOException("The device produced an unsupported BKS format. The original store was not changed.");
            KeyStore check=KeyStore.getInstance("BKS");in=new FileInputStream(merged);try{check.load(in,"changeit".toCharArray());}finally{in.close();}
            if(check.size()!=store.size())throw new IOException("Prepared certificate store could not be verified.");
            install.append("cat /system/etc/security/cacerts.bks > ").append(quote(backup+"/cacerts.bks")).append("\n");
            restore.append("cat ").append(quote(backup+"/cacerts.bks")).append(" > /system/etc/security/cacerts.bks.on-restore\nchmod 644 /system/etc/security/cacerts.bks.on-restore\nchown 0:0 /system/etc/security/cacerts.bks.on-restore\nmv /system/etc/security/cacerts.bks.on-restore /system/etc/security/cacerts.bks\non_remove /system/etc/security/cacerts.bks.on-new\n");
            install.append("cat ").append(quote(merged.getAbsolutePath())).append(" > /system/etc/security/cacerts.bks.on-new\nchmod 644 /system/etc/security/cacerts.bks.on-new\nchown 0:0 /system/etc/security/cacerts.bks.on-new\nmv /system/etc/security/cacerts.bks.on-new /system/etc/security/cacerts.bks\n");
        } else {
            File directory=new File("/system/etc/security/cacerts");if(!directory.isDirectory())throw new IOException("The expected system certificate directory was not found.");
            Map<String,byte[]> reserved=new HashMap<String,byte[]>();
            for(X509Certificate cert:certificates) {
                String hash=subjectHash(cert),name;int index=0;boolean exists=false;
                byte[] der=cert.getEncoded();
                while(true){name=hash+"."+index++;File target=new File(directory,name);
                    if(reserved.containsKey(name)){if(Arrays.equals(reserved.get(name),der)){exists=true;break;}continue;}
                    if(!target.exists())break;
                    InputStream existing=new FileInputStream(target);try{java.security.cert.Certificate other=CertificateFactory.getInstance("X.509").generateCertificate(existing);if(Arrays.equals(other.getEncoded(),der)){exists=true;break;}}catch(CertificateException ignored){}finally{existing.close();}
                }
                if(exists)continue;reserved.put(name,der);added++;
                File staged=new File(stage,name);OutputStream output=new FileOutputStream(staged);try{output.write(der);}finally{output.close();}
                String target=directory.getAbsolutePath()+"/"+name;
                // A concurrent trust-store change must abort, never overwrite an occupied hash slot.
                install.append("! on_exists ").append(quote(target)).append("\ncat ").append(quote(staged.getAbsolutePath())).append(" > ").append(quote(target+".on-new")).append("\nchmod 644 ").append(quote(target+".on-new")).append("\nchown 0:0 ").append(quote(target+".on-new")).append("\nmv ").append(quote(target+".on-new")).append(" ").append(quote(target)).append("\n");
                restore.append("if on_exists ").append(quote(target)).append(" && cmp ").append(quote(target)).append(" ").append(quote(backup+"/"+name)).append(" >/dev/null 2>&1; then on_remove ").append(quote(target)).append("; fi\non_remove ").append(quote(target+".on-new")).append("\n");
                install.insert(0,"cat "+quote(staged.getAbsolutePath())+" > "+quote(backup+"/"+name)+"\n");
            }
        }
        if(added==0){log.write("All Mozilla roots in this bundle are already installed.");return null;}
        String cleanup="if command -v restorecon >/dev/null 2>&1; then restorecon -R /system/etc/security/cacerts* || true; fi\nif on_one \"$was_ro\"; then on_remount ro; fi\n";
        restore.append(cleanup);
        File restoreFile=new File(stage,"restore.sh");text(restoreFile,restore.toString());
        String prefix=mountPrelude()+"umask 077\nif ! on_exists /data/local/on-ca-backups; then mkdir /data/local/on-ca-backups; fi\nmkdir "+quote(backup)+"\nchmod 700 "+quote(backup)+"\ncat "+quote(restoreFile.getAbsolutePath())+" > "+quote(backup+"/restore.sh")+"\nchmod 700 "+quote(backup+"/restore.sh")+"\n";
        String script=prefix+"rollback() { code=$?; trap - EXIT HUP INT TERM; if ! on_zero \"$code\"; then sh "+quote(backup+"/restore.sh")+" || true; fi; if on_one \"$was_ro\"; then on_remount ro || true; fi; exit $code; }\ntrap rollback EXIT HUP INT TERM\n"+install+cleanup;
        runRoot(script);
        context.getSharedPreferences("on_certificates",0).edit().putString("restore",backup+"/restore.sh").commit();
        log.write("Added "+added+" Mozilla roots. Restart to activate. Recovery: "+backup+"/restore.sh");
        return backup+"/restore.sh";
    }
    public static boolean canRestore(Context c){return c.getSharedPreferences("on_certificates",0).contains("restore");}
    public static void restore(Context context,Log log) throws Exception {
        String path=context.getSharedPreferences("on_certificates",0).getString("restore","");
        if(!path.matches("/data/local/on-ca-backups/[0-9]+/restore\\.sh"))throw new IOException("No certificate recovery record is available.");
        runRoot(systemShellPrelude()+"on_exists "+quote(path)+"\nsh "+quote(path));
        context.getSharedPreferences("on_certificates",0).edit().remove("restore").commit();
        log.write("Previous certificate store restored. Restart the device.");
    }
}
