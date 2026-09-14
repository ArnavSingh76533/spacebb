package com.space.browser;
import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Streaming gateway chat, with a durable regular-session conversation and IME-safe composer. */
final class SpaceAi {
    static final String ENDPOINT="https://shivamkr00902-data2.hf.space/v1/chat/completions";
    static final String PREVIEW_KEY="gw_g_B8uYbNn2ft1HSCrYwTaBEYXQuflDD9hteO7_YfoAw";
    private final MainActivity activity;
    private final AiConversation chat;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private AtomicBoolean cancellation=new AtomicBoolean();
    private volatile HttpURLConnection connection;
    private boolean sending;
    private Dialog dialog;
    private Runnable refreshOpenChat=()->{};
    private int ink,bg,panel,accent;
    SpaceAi(MainActivity a){activity=a;chat=a.aiConversation();}
    void show(List<NetworkRecord> records,boolean full){show(records,full?AiContext.FULL:AiContext.SUMMARY);}
    void show(List<NetworkRecord> records,int mode){
        if(dialog!=null&&dialog.isShowing())return;
        boolean dark=activity.devPrefs().getBoolean("dark",true);ink=dark?0xfff5f2ff:0xff211e2d;bg=dark?0xff0b0d14:0xfff8f7fc;panel=dark?0xff191d2b:0xffece8f5;accent=dark?0xffbaa6ff:0xff7050cd;
        Dialog windowDialog=new Dialog(activity);dialog=windowDialog;
        LinearLayout layout=column();layout.setBackgroundColor(bg);layout.setPadding(dp(16),dp(8),dp(16),dp(12));
        LinearLayout header=row();TextView title=label("✦  Space AI",23);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(button("Close",windowDialog::dismiss));layout.addView(header);
        LinearLayout options=row();Button model=button("Model: "+model(activity),()->settings(activity));options.addView(model,new LinearLayout.LayoutParams(0,dp(46),1));Button clear=button("New chat",()->new SpaceDialogBuilder(activity).setTitle("Start a new chat?").setMessage("This clears this saved conversation.").setNegativeButton("Cancel",null).setPositiveButton("New chat",(d,w)->{cancel();chat.clear();windowDialog.dismiss();show(records,mode);}).show());options.addView(clear,new LinearLayout.LayoutParams(0,dp(46),1));layout.addView(options);
        CheckBox original=new CheckBox(activity);original.setText("Send original headers and data");original.setTextColor(ink);original.setChecked(activity.devPrefs().getBoolean("ai_original_v2",true));layout.addView(original);
        TextView contextLabel=label("",12);layout.addView(contextLabel);
        Runnable update=()->{String captured=AiContext.build(records,mode,original.isChecked());contextLabel.setText(records.size()+" items · ~"+captured.length()/4+" context tokens"+(original.isChecked()?"":" · credentials masked"));};update.run();
        original.setOnCheckedChangeListener((b,on)->{activity.devPrefs().edit().putBoolean("ai_original_v2",on).apply();update.run();});
        Button review=button("Review context",()->{TextView text=label(AiContext.build(records,mode,original.isChecked()),12);text.setTextIsSelectable(true);ScrollView view=new ScrollView(activity);view.setBackgroundColor(bg);view.addView(text);new SpaceDialogBuilder(activity).setTitle("Capture sent with your message").setView(view).setPositiveButton("Close",null).show();});layout.addView(review,new LinearLayout.LayoutParams(-1,dp(42)));
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);LinearLayout transcript=column();transcript.setPadding(0,dp(12),0,dp(12));scroll.addView(transcript);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));renderHistory(transcript);
        LinearLayout composer=column();composer.setBackgroundColor(bg);EditText prompt=new EditText(activity);prompt.setTextColor(ink);prompt.setHintTextColor(accent);prompt.setTextSize(16);prompt.setHint("Message Space AI");prompt.setContentDescription("AI message input");prompt.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);prompt.setMinLines(1);prompt.setMaxLines(4);prompt.setText(chat.draft());composer.addView(prompt,new LinearLayout.LayoutParams(-1,-2));
        prompt.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){chat.draft(s.toString());}public void afterTextChanged(Editable e){}});
        LinearLayout sendRow=row();Button send=button("Send",()->{}),stop=button("Stop",this::cancel);send.setContentDescription("Send AI message");sendRow.addView(send,new LinearLayout.LayoutParams(0,dp(48),1));sendRow.addView(stop,new LinearLayout.LayoutParams(0,dp(48),1));composer.addView(sendRow);layout.addView(composer,new LinearLayout.LayoutParams(-1,-2));
        send.setEnabled(!sending);clear.setEnabled(!sending);refreshOpenChat=()->{if(windowDialog.isShowing()){send.setEnabled(!sending);clear.setEnabled(!sending);renderHistory(transcript);}};
        final boolean[] compactMode={false};java.util.function.Consumer<Boolean> compact=keyboard->{if(compactMode[0]==keyboard)return;compactMode[0]=keyboard;int visible=keyboard?View.GONE:View.VISIBLE;options.setVisibility(visible);original.setVisibility(visible);contextLabel.setVisibility(visible);review.setVisibility(visible);prompt.setMaxLines(keyboard?3:4);};
        layout.setFocusableInTouchMode(true);layout.requestFocus();windowDialog.setContentView(layout);windowDialog.setCanceledOnTouchOutside(false);windowDialog.setOnDismissListener(d->{cancel();chat.draft(prompt.getText().toString());});
        windowDialog.show();Window window=windowDialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(-1,-1);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);if(activity.privateMode())window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if(Build.VERSION.SDK_INT>=30){window.setDecorFitsSystemWindows(false);layout.setOnApplyWindowInsetsListener((v,insets)->{compact.accept(insets.isVisible(WindowInsets.Type.ime()));android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(dp(16)+bars.left,dp(8)+bars.top,dp(16)+bars.right,dp(8)+bars.bottom);return WindowInsets.CONSUMED;});layout.requestApplyInsets();}else {layout.setFitsSystemWindows(true);layout.getViewTreeObserver().addOnGlobalLayoutListener(()->{Rect frame=new Rect();layout.getWindowVisibleDisplayFrame(frame);compact.accept(activity.getResources().getDisplayMetrics().heightPixels-frame.bottom>dp(120));});}}
        send.setOnClickListener(v->{String question=prompt.getText().toString().trim();if(question.isEmpty()||sending)return;String apiKey=activity.devPrefs().getString("ai_key",PREVIEW_KEY).trim();if(apiKey.isEmpty()){settings(activity);return;}
            String selectedModel=model(activity);model.setText("Model: "+selectedModel);boolean includeOriginal=original.isChecked();String captured=AiContext.build(records,mode,includeOriginal);JSONArray messages;try{messages=chat.messages(question,captured,includeOriginal);}catch(JSONException e){activity.devToast("Could not prepare chat");return;}
            sending=true;cancellation=new AtomicBoolean();AtomicBoolean cancelled=cancellation;send.setEnabled(false);clear.setEnabled(false);prompt.setText("");chat.add("user",question,captured);renderHistory(transcript);MarkdownView answerView=new MarkdownView(activity,ink,accent,panel);transcript.addView(label("Space AI",12));transcript.addView(answerView);answerView.setMarkdown("Thinking…");scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
            new Thread(()->{StringBuilder answer=new StringBuilder();try{
                if(cancelled.get())throw new InterruptedIOException("Stopped");
                HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();connection=c;c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+apiKey);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","text/event-stream");
                byte[] payload=NetworkRecorder.json("model",selectedModel,"messages",messages,"stream",true).toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(payload.length);if(cancelled.get())throw new InterruptedIOException("Stopped");try(OutputStream out=c.getOutputStream()){out.write(payload);}int status=c.getResponseCode();if(status<200||status>=300)throw new IOException("Gateway returned HTTP "+status+". Check the model name and API key.");
                if(String.valueOf(c.getContentType()).contains("text/event-stream")){try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String line;long posted=0;while(!cancelled.get()&&(line=in.readLine())!=null){if(line.length()>1024*1024)throw new IOException("Stream line too large");if(!line.startsWith("data:"))continue;String data=line.substring(5).trim();if(data.equals("[DONE]"))break;JSONObject packet=new JSONObject(data);if(packet.has("error"))throw new IOException("Gateway stream error");JSONArray choices=packet.optJSONArray("choices");if(choices!=null&&choices.length()>0){JSONObject delta=choices.getJSONObject(0).optJSONObject("delta");if(delta!=null&&!delta.isNull("content"))answer.append(delta.optString("content"));}if(answer.length()>100000)throw new IOException("Reply reached the display limit");long now=SystemClock.uptimeMillis();if(now-posted>200){posted=now;String partial=answer.toString();ui.post(()->{if(!windowDialog.isShowing())return;boolean bottom=scroll.getScrollY()+scroll.getHeight()>=transcript.getHeight()-dp(100);answerView.setMarkdown(partial);if(bottom)scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));});}}}}
                else {JSONObject reply=new JSONObject(new String(NetworkFormats.read(c.getInputStream(),1024*1024),StandardCharsets.UTF_8));answer.append(reply.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"));}
                if(cancelled.get())answer.append("\n\n[Stopped]");
            }catch(Exception e){answer.append(cancelled.get()?"\n\n[Stopped]":"\n\n"+e.getMessage());}finally{HttpURLConnection c=connection;if(c!=null)c.disconnect();connection=null;String completed=answer.toString();ui.post(()->{chat.add("assistant",completed,"");sending=false;send.setEnabled(true);clear.setEnabled(true);if(windowDialog.isShowing())answerView.setMarkdown(completed);else refreshOpenChat.run();});}},"Space-AI").start();
        });
    }
    private void renderHistory(LinearLayout view){view.removeAllViews();JSONArray history=chat.snapshot();if(history.length()==0){view.addView(label("Ask a question or bring a captured request into the conversation.",16));return;}for(int i=0;i<history.length();i++){JSONObject turn=history.optJSONObject(i);if(turn==null)continue;TextView author=label(turn.optString("role").equals("user")?"You":"Space AI",12);author.setTextColor(accent);view.addView(author);MarkdownView message=new MarkdownView(activity,ink,accent,panel);message.setMarkdown(turn.optString("content"));view.addView(message);}}
    static String model(MainActivity host){String value=host.devPrefs().getString("ai_model","auto").trim();return value.isEmpty()?"auto":value;}
    static void settings(MainActivity host){LinearLayout form=new LinearLayout(host);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(24,12,24,12);TextView label=new TextView(host);label.setText("Model name (any model accepted by your gateway)");form.addView(label);EditText model=new EditText(host);model.setSingleLine(true);model.setText(model(host));model.setHint("auto");model.setContentDescription("AI model name");form.addView(model);TextView api=new TextView(host);api.setText("API key");form.addView(api);EditText key=new EditText(host);key.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setText(host.devPrefs().getString("ai_key",PREVIEW_KEY));form.addView(key);new SpaceDialogBuilder(host).setTitle("AI settings").setMessage("Gateway: "+ENDPOINT+"\nRegular chat is saved locally. Private chat is session-only.").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{host.devPrefs().edit().putString("ai_model",model.getText().toString().trim().isEmpty()?"auto":model.getText().toString().trim()).putString("ai_key",key.getText().toString().trim()).apply();host.devToast("AI settings saved");}).show();}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String s,int size){TextView t=new TextView(activity);t.setText(s);t.setTextColor(ink);t.setTextSize(size);return t;}
    private Button button(String s,Runnable action){Button b=new Button(activity);b.setText(s);b.setTextColor(accent);b.setAllCaps(false);b.setTextSize(13);GradientDrawable shape=new GradientDrawable();shape.setColor(panel);shape.setCornerRadius(dp(14));b.setBackground(new android.graphics.drawable.InsetDrawable(shape,dp(3),dp(3),dp(3),dp(3)));b.setOnClickListener(v->action.run());return b;}
    void dispose(){if(dialog!=null)dialog.dismiss();cancel();}
    private void cancel(){cancellation.set(true);HttpURLConnection c=connection;if(c!=null)new Thread(c::disconnect,"Space-AI-stop").start();}
}
