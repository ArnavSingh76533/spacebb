package com.space.browser;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.text.style.*;
import android.widget.*;
import java.util.regex.*;
/** Native Markdown. Code is inert selectable text with an exact-copy action. */
final class MarkdownView extends LinearLayout {
    private final MainActivity host;private final int ink,accent,panel;
    MarkdownView(MainActivity host,int ink,int accent,int panel){super(host);this.host=host;this.ink=ink;this.accent=accent;this.panel=panel;setOrientation(VERTICAL);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    void setMarkdown(String markdown){
        removeAllViews();Matcher m=Pattern.compile("(?m)^```([^\\n]*)\\n([\\s\\S]*?)(?:^```[ \\t]*$|\\z)").matcher(markdown);int end=0;
        while(m.find()){prose(markdown.substring(end,m.start()));code(m.group(1).trim(),m.group(2));end=m.end();}prose(markdown.substring(end));
    }
    private void prose(String source){if(source.trim().isEmpty())return;TextView text=new TextView(host);text.setTextColor(ink);text.setTextSize(15);text.setTextIsSelectable(true);text.setLineSpacing(dp(3),1);text.setPadding(0,dp(6),0,dp(8));text.setText(format(source));addView(text,new LayoutParams(-1,-2));}
    static SpannableStringBuilder format(String source){
        SpannableStringBuilder out=new SpannableStringBuilder();
        for(String line:source.split("\n",-1)){Matcher heading=Pattern.compile("^(#{1,6})\\s+(.*)$").matcher(line);boolean isHeading=heading.matches();String value=isHeading?heading.group(2):line.replaceFirst("^\\s*[-*]\\s+","• ");
            int start=out.length();Matcher inline=Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\*([^*]+)\\*|\\[([^\\]]+)\\]\\(([^)]+)\\)").matcher(value);int cursor=0;
            while(inline.find()){out.append(value.substring(cursor,inline.start()));int at=out.length();String content=inline.group(1)!=null?inline.group(1):inline.group(2)!=null?inline.group(2):inline.group(3)!=null?inline.group(3):inline.group(4)!=null?inline.group(4):inline.group(5)+" ("+inline.group(6)+")";out.append(content);
                Object span=inline.group(3)!=null?new TypefaceSpan("monospace"):new StyleSpan(inline.group(4)!=null?Typeface.ITALIC:Typeface.BOLD);out.setSpan(span,at,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);cursor=inline.end();}
            out.append(value.substring(cursor));if(isHeading){out.setSpan(new StyleSpan(Typeface.BOLD),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);out.setSpan(new RelativeSizeSpan(1.25f),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}out.append('\n');
        }return out;
    }
    private void code(String language,String value){LinearLayout card=new LinearLayout(host);card.setOrientation(VERTICAL);card.setPadding(dp(12),dp(8),dp(12),dp(12));GradientDrawable shape=new GradientDrawable();shape.setColor(panel);shape.setCornerRadius(dp(12));card.setBackground(shape);LinearLayout top=new LinearLayout(host);TextView label=new TextView(host);label.setText(language.isEmpty()?"Code":language);label.setTextColor(accent);top.addView(label,new LayoutParams(0,-2,1));Button copy=new Button(host);copy.setText("Copy code");copy.setAllCaps(false);copy.setContentDescription("Copy code block");copy.setOnClickListener(v->host.copyText(value));top.addView(copy);card.addView(top);
        TextView code=new TextView(host);code.setText(value);code.setTypeface(Typeface.MONOSPACE);code.setTextColor(ink);code.setTextSize(13);code.setTextIsSelectable(true);HorizontalScrollView scroll=new HorizontalScrollView(host);scroll.addView(code);card.addView(scroll);LayoutParams lp=new LayoutParams(-1,-2);lp.bottomMargin=dp(12);addView(card,lp);}
}
