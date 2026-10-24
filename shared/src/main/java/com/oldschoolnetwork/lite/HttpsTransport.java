package com.oldschoolnetwork.lite;

import android.content.Context;
import com.oldschoolnetwork.certificates.RootCertificates;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.oldschoolnetwork.lite.crypto.tls.*;
import com.oldschoolnetwork.lite.crypto.tls.crypto.TlsCertificate;
import com.oldschoolnetwork.lite.crypto.tls.crypto.impl.bc.BcTlsCrypto;

/** TLS 1.2 and SNI without changing platform providers or weakening verification. */
public final class HttpsTransport {
    private static Set<TrustAnchor> anchors;
    private static Provider validatorProvider;
    private static synchronized Provider provider(){if(validatorProvider==null)validatorProvider=new com.oldschoolnetwork.lite.crypto.jce.provider.BouncyCastleProvider();return validatorProvider;}
    public interface Progress { void update(long count,long total) throws IOException; }
    private static synchronized Set<TrustAnchor> anchors(Context context) throws Exception {
        if(anchors==null){Set<TrustAnchor> roots=new HashSet<TrustAnchor>();for(X509Certificate cert:RootCertificates.certificates(context))roots.add(new TrustAnchor(cert,null));anchors=roots;}
        return anchors;
    }
    public static void verifyChain(String host,byte[][] chain,Set<TrustAnchor> trusted) throws Exception {
        CertificateFactory factory=CertificateFactory.getInstance("X.509",provider());List<X509Certificate> certs=new ArrayList<X509Certificate>();
        for(byte[] encoded:chain)certs.add((X509Certificate)factory.generateCertificate(new ByteArrayInputStream(encoded)));
        if(certs.isEmpty())throw new CertificateException("The server did not send a certificate.");
        X509Certificate leaf=certs.get(0);leaf.checkValidity();
        Collection<List<?>> names=leaf.getSubjectAlternativeNames();boolean matches=false;
        if(names!=null)for(List<?> name:names){if(name.size()<2)continue;int kind=((Integer)name.get(0)).intValue();if(kind==2&&matchesHost(host,String.valueOf(name.get(1))))matches=true;if(kind==7&&host.equalsIgnoreCase(String.valueOf(name.get(1))))matches=true;}
        if(!matches)throw new CertificateException("The certificate does not match "+host+".");
        List<String> eku=leaf.getExtendedKeyUsage();if(eku!=null&&!eku.contains("1.3.6.1.5.5.7.3.1")&&!eku.contains("2.5.29.37.0"))throw new CertificateException("The certificate is not valid for a web server.");
        X509Certificate last=certs.get(certs.size()-1);for(TrustAnchor anchor:trusted)if(last.equals(anchor.getTrustedCert())){certs.remove(certs.size()-1);break;}
        PKIXParameters policy=new PKIXParameters(trusted);policy.setRevocationEnabled(false);
        CertPathValidator.getInstance("PKIX",provider()).validate(factory.generateCertPath(certs),policy);
    }
    public static boolean matchesHost(String host,String pattern){
        host=host.toLowerCase(Locale.US);pattern=pattern.toLowerCase(Locale.US);
        if(pattern.startsWith("*.")){String suffix=pattern.substring(1);int dot=host.indexOf('.');return dot>0&&host.substring(dot).equals(suffix)&&suffix.indexOf('.',1)>1;}
        return host.equals(pattern);
    }
    private static String line(InputStream input,int limit) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=input.read())!=-1){if(c=='\n')break;if(c!='\r')b.write(c);if(b.size()>limit)throw new IOException("HTTP line is too long.");}
        if(c==-1&&b.size()==0)return null;return b.toString("ISO-8859-1");
    }
    public static byte[] request(Context context,String url,byte[] json,int maxBytes) throws Exception {
        ByteArrayOutputStream data=new ByteArrayOutputStream();transfer(context,url,json,data,maxBytes,null,0);return data.toByteArray();
    }
    public static void download(Context context,String url,File destination,long limit,Progress progress) throws Exception {
        File partial=new File(destination.getAbsolutePath()+".part");OutputStream out=new FileOutputStream(partial);
        try{transfer(context,url,null,out,limit,progress,0);out.close();if(destination.exists()&&!destination.delete())throw new IOException("Cannot replace the previous download.");if(!partial.renameTo(destination))throw new IOException("Cannot finish the download.");}
        finally{try{out.close();}finally{if(partial.exists())partial.delete();}}
    }
    private static void transfer(final Context context,String address,byte[] json,OutputStream target,long limit,Progress progress,int redirects) throws Exception {
        if(redirects>5)throw new IOException("Too many download redirects.");
        final URL url=new URL(address);if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null)throw new IOException("Only verified HTTPS connections are supported.");
        final String host=url.getHost();if(!host.matches("[A-Za-z0-9.-]+"))throw new IOException("Unsupported server address.");
        final Set<TrustAnchor> trusted=anchors(context);
        Socket socket=new Socket();TlsClientProtocol tls=null;
        try {
            socket.connect(new InetSocketAddress(host,url.getPort()<0?443:url.getPort()),15000);socket.setSoTimeout(30000);
            tls=new TlsClientProtocol(socket.getInputStream(),socket.getOutputStream());
            tls.connect(new DefaultTlsClient(new BcTlsCrypto(new SecureRandom())) {
                protected ProtocolVersion[] getSupportedVersions(){return new ProtocolVersion[]{ProtocolVersion.TLSv12};}
                protected Vector getSNIServerNames(){Vector names=new Vector();try{names.addElement(new ServerName(NameType.host_name,host.getBytes("US-ASCII")));}catch(UnsupportedEncodingException e){throw new RuntimeException(e);}return names;}
                public TlsAuthentication getAuthentication(){return new TlsAuthentication(){
                    public void notifyServerCertificate(TlsServerCertificate server) throws IOException {try{TlsCertificate[] raw=server.getCertificate().getCertificateList();byte[][] chain=new byte[raw.length][];for(int i=0;i<raw.length;i++)chain[i]=raw[i].getEncoded();verifyChain(host,chain,trusted);}catch(Exception e){IOException failure=new IOException("HTTPS certificate verification failed: "+e.getMessage());failure.initCause(e);throw failure;}}
                    public TlsCredentials getClientCredentials(CertificateRequest request){return null;}
                };}
            });
            String path=url.getFile().length()==0?"/":url.getFile();if(path.indexOf('\r')>=0||path.indexOf('\n')>=0)throw new IOException("Invalid request address.");
            String authority=host+(url.getPort()<0||url.getPort()==443?"":":"+url.getPort());
            String headers=(json==null?"GET ":"POST ")+path+" HTTP/1.1\r\nHost: "+authority+"\r\nUser-Agent: ON-Lite/0.1 Android\r\nAccept: */*\r\nAccept-Encoding: identity\r\nConnection: close\r\n"+(json==null?"":"Content-Type: application/json; charset=UTF-8\r\nContent-Length: "+json.length+"\r\n")+"\r\n";
            OutputStream output=tls.getOutputStream();output.write(headers.getBytes("UTF-8"));if(json!=null)output.write(json);output.flush();
            InputStream input=new BufferedInputStream(tls.getInputStream(),8192);String status=line(input,8192);if(status==null||!status.startsWith("HTTP/"))throw new IOException("Invalid server response.");
            String[] statusParts=status.split(" ",3);int code=Integer.parseInt(statusParts[1]);Map<String,String> fields=new HashMap<String,String>();
            for(int i=0;;i++){String header=line(input,8192);if(header==null)throw new IOException("Incomplete HTTP headers.");if(header.length()==0)break;if(i>=64)throw new IOException("Too many HTTP headers.");int colon=header.indexOf(':');if(colon>0)fields.put(header.substring(0,colon).trim().toLowerCase(Locale.US),header.substring(colon+1).trim());}
            if(code>=300&&code<400){if(json!=null)throw new IOException("Account requests cannot be redirected.");String next=fields.get("location");if(next==null)throw new IOException("Invalid download redirect.");tls.close();socket.close();transfer(context,new URL(url,next).toString(),null,target,limit,progress,redirects+1);return;}
            if(code<200||code>=300)throw new IOException("Server returned HTTP "+code+".");
            long total=fields.containsKey("content-length")?Long.parseLong(fields.get("content-length")):-1;if(total>limit)throw new IOException("This response exceeds the Lite size limit.");
            if("chunked".equalsIgnoreCase(fields.get("transfer-encoding")))input=new Chunked(input);
            else if(total>=0)input=new Limited(input,total);
            if("gzip".equalsIgnoreCase(fields.get("content-encoding")))input=new GZIPInputStream(input,8192);
            byte[] buffer=new byte[16384];long count=0,last=0;int n;
            while((n=input.read(buffer))!=-1){count+=n;if(count>limit)throw new IOException("The response exceeds the Lite size limit.");target.write(buffer,0,n);if(progress!=null&&System.currentTimeMillis()-last>200){progress.update(count,total);last=System.currentTimeMillis();}}
            if(progress!=null)progress.update(count,total);
        }finally {try{if(tls!=null)tls.close();}finally{socket.close();}}
    }
    private static final class Limited extends FilterInputStream {
        long remaining;Limited(InputStream input,long size){super(input);remaining=size;}
        public int read()throws IOException{if(remaining==0)return -1;int b=super.read();if(b<0)throw new EOFException("Incomplete download.");remaining--;return b;}
        public int read(byte[] b,int off,int size)throws IOException{if(remaining==0)return -1;int n=in.read(b,off,(int)Math.min(size,remaining));if(n<0)throw new EOFException("Incomplete download.");remaining-=n;return n;}
    }
    private static final class Chunked extends FilterInputStream {
        long remaining;boolean end,first=true;Chunked(InputStream input){super(input);}
        private boolean next()throws IOException{if(end)return false;if(remaining>0)return true;if(!first){String boundary=line(in,32);if(boundary==null||boundary.length()!=0)throw new IOException("Invalid chunk boundary.");}first=false;String size=line(in,128);if(size==null)throw new EOFException("Incomplete chunk.");try{remaining=Long.parseLong(size.split(";",2)[0].trim(),16);}catch(NumberFormatException e){throw new IOException("Invalid chunk size.");}if(remaining<0)throw new IOException("Invalid chunk size.");if(remaining==0){end=true;return false;}return true;}
        public int read()throws IOException{if(!next())return -1;int b=in.read();if(b<0)throw new EOFException("Incomplete chunk.");remaining--;return b;}
        public int read(byte[] b,int off,int count)throws IOException{if(!next())return -1;int n=in.read(b,off,(int)Math.min(count,remaining));if(n<0)throw new EOFException("Incomplete chunk.");remaining-=n;return n;}
    }
}
