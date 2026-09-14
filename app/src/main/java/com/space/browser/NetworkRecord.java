package com.space.browser;

import java.nio.charset.StandardCharsets;
import java.util.*;

final class NetworkRecord {
    final long id;
    final String requestId,session;
    String timing="", finalUrl="";
    boolean pinned,blocked,browserDecoded;
    String url="",method="GET",type="Other",protocol="",statusText="",mime="",remoteAddress="",error="";
    int status;
    long time=System.currentTimeMillis(),transferBytes;
    double started,finished;
    boolean complete,fromCache,hasPostData,requestExtra,responseExtra,redirect,webSocket;
    Boolean expectsExtra;
    final Map<String,String> requestHeaders=new TreeMap<>(String.CASE_INSENSITIVE_ORDER),responseHeaders=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    String requestHeadersText="",responseHeadersText="",requestBodyNote="",responseBodyNote="Waiting for response body";
    byte[] requestBody=new byte[0],responseBody=new byte[0];
    NetworkRecord(long id,String requestId,String session){this.id=id;this.requestId=requestId;this.session=session;}
    NetworkRecord copy(){NetworkRecord r=new NetworkRecord(id,requestId,session);r.timing=timing;r.finalUrl=finalUrl;r.pinned=pinned;r.blocked=blocked;r.browserDecoded=browserDecoded;r.url=url;r.method=method;r.type=type;r.protocol=protocol;r.statusText=statusText;r.mime=mime;r.remoteAddress=remoteAddress;r.error=error;r.status=status;r.time=time;r.transferBytes=transferBytes;r.started=started;r.finished=finished;r.complete=complete;r.fromCache=fromCache;r.hasPostData=hasPostData;r.requestExtra=requestExtra;r.responseExtra=responseExtra;r.redirect=redirect;r.webSocket=webSocket;r.expectsExtra=expectsExtra;r.requestHeaders.putAll(requestHeaders);r.responseHeaders.putAll(responseHeaders);r.requestHeadersText=requestHeadersText;r.responseHeadersText=responseHeadersText;r.requestBodyNote=requestBodyNote;r.responseBodyNote=responseBodyNote;r.requestBody=requestBody;r.responseBody=responseBody;return r;}
    String requestText(){String h=requestHeadersText.isEmpty()?method+" "+url+"\n"+headers(requestHeaders):requestHeadersText;return h+"\n\nPOST / REQUEST DATA\n"+(requestBody.length==0?(hasPostData?"Body unavailable": "No request body"):new String(requestBody,StandardCharsets.UTF_8))+(requestBodyNote.isEmpty()?"":"\n\n"+requestBodyNote);}
    String responseText(){return (responseHeadersText.isEmpty()?(protocol.isEmpty()?"HTTP":protocol)+" "+(status==0?"Pending":status)+" "+statusText+"\n"+headers(responseHeaders):responseHeadersText);}
    static String headers(Map<String,String> headers){StringBuilder s=new StringBuilder();for(Map.Entry<String,String> e:headers.entrySet())s.append(e.getKey()).append(": ").append(e.getValue()).append('\n');return s.toString();}
}
