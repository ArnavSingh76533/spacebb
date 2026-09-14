package com.space.browser;
import java.util.*;
final class AiContext {
    static final int SUMMARY=0,BODIES=1,FULL=2;
    private static String clip(String value,int max){return value.length()<=max?value:value.substring(0,max)+"\n[Section truncated]";}
    static String build(List<NetworkRecord> records,int mode,boolean original){
        StringBuilder result=new StringBuilder();
        for(int i=0;i<records.size();i++){NetworkRecord r=records.get(i);
            String item="#"+r.id+" "+r.method+" "+r.url+"\nStatus: "+r.status+" "+r.statusText+" · "+NetworkFormats.size(r.transferBytes)+" · "+new Date(r.time)+"\n";
            if(mode!=SUMMARY){if(mode==FULL)item+="\nREQUEST HEADERS\n"+clip(NetworkRecord.headers(r.requestHeaders),6000);
                item+="\nPOST / REQUEST BODY\n"+clip(NetworkFormats.body(r,true,true,"Auto"),10000);
                item+="\nRESPONSE URL: "+(r.finalUrl.isEmpty()?r.url:r.finalUrl)+"\n";
                if(mode==FULL)item+="\nRESPONSE HEADERS\n"+clip(NetworkRecord.headers(r.responseHeaders),6000);
                item+="\nRESPONSE BODY\n"+clip(NetworkFormats.body(r,false,true,"Auto"),10000);}
            if(result.length()+item.length()>120000){result.append("\n[Context limit: ").append(records.size()-i).append(" remaining items omitted. Select fewer items for full detail.]");break;}
            result.append(item).append("\n\n");
        }
        return original?result.toString():NetworkFormats.masked(result.toString());
    }
}
