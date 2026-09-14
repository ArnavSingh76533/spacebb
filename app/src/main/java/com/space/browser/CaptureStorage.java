package com.space.browser;

import java.io.*;
import java.util.UUID;

/** App-private, session-only spill files. No network data goes to shared storage automatically. */
final class CaptureStorage {
    private static File root;
    static synchronized void init(File cache,boolean privateMode){if(root!=null)return;root=new File(cache,privateMode?"network-private":"network-regular");erase(root);root.mkdirs();}
    static synchronized File save(byte[] data) throws IOException {if(root==null)throw new IOException("Capture storage is unavailable");File file=new File(root,UUID.randomUUID()+".body");try(FileOutputStream out=new FileOutputStream(file)){out.write(data);}catch(IOException e){file.delete();throw e;}return file;}
    static byte[] read(File file){if(file==null||!file.isFile())return new byte[0];try(InputStream in=new FileInputStream(file)){return NetworkFormats.read(in,NetworkRecorder.BODY_LIMIT);}catch(IOException e){return new byte[0];}}
    static void erase(File file){if(file==null)return;if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)erase(child);}file.delete();}
    static synchronized void clearSession(){erase(root);if(root!=null)root.mkdirs();}
}
