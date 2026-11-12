package cn.codedog.news;

import java.util.*;
import java.util.regex.Pattern;

/** Conservative footer removal for new ingestion and historical article responses. */
public final class NewsContentCleaner {
 private NewsContentCleaner(){}
 private static final Pattern HEADING=Pattern.compile("^(?:[\\p{IsHan}]{0,12}新闻精选|相关推荐|相关阅读|推荐阅读|Related (?:articles|stories|coverage)|Recommended (?:stories|articles)|Read more|More on this story)[:：]?$",Pattern.CASE_INSENSITIVE);
 private static final Pattern ITEM=Pattern.compile("^(?:[-•*]\\s*)?(?:\\d{4}(?:年\\d{1,2}月\\d{1,2}日|[-/]\\d{1,2}[-/]\\d{1,2})\\s+\\d{1,2}:\\d{2}(?::\\d{2})?|\\[[^\\]]+\\]\\(https?://[^)]+\\))$");
 public static List<String> paragraphs(String text){
  if(text==null||text.isBlank())return List.of();
  return Arrays.stream(text.split("\\R\\s*\\R")).map(String::strip).filter(p->!p.isBlank()).toList();
 }
 private static int end(List<String> rows){
  for(int i=1;i<rows.size()-2;i++){
   if(HEADING.matcher(rows.get(i)).matches()&&rows.subList(i+1,rows.size()).stream().allMatch(p->ITEM.matcher(p).matches()))return i;
  }
  return rows.size();
 }
 private static final Pattern CHROME_SIGNATURE=Pattern.compile("CloseMore on this story|Shareclose panel|Subsection[A-Za-z ]{1,60}Published",Pattern.CASE_INSENSITIVE);
 public static boolean pageChrome(String text){
  if(!CHROME_SIGNATURE.matcher(text).find())return false;
  String lower=text.toLowerCase(Locale.ROOT);
  return List.of("share page","copy link","about sharing","related topics","read description","follow ").stream().filter(lower::contains).count()>=3;
 }
 public static List<String> clean(List<String> rows){return rows.subList(0,end(rows)).stream().filter(p->!pageChrome(p)).toList();}
 public static void cleanArticle(Map<String,Object> article){
  var original=paragraphs((String)article.get("content_en"));
  var translated=paragraphs((String)article.get("content_zh"));
  int originalEnd=end(original),translatedEnd=end(translated);
  var cleanOriginal=new ArrayList<String>();
  var cleanTranslated=new ArrayList<String>();
  if(original.size()==translated.size()){
   int pairedEnd=Math.min(originalEnd,translatedEnd);
   for(int i=0;i<pairedEnd;i++){
    if(pageChrome(original.get(i))||pageChrome(translated.get(i)))continue;
    cleanOriginal.add(original.get(i));cleanTranslated.add(translated.get(i));
   }
  }else{
   cleanOriginal.addAll(clean(original));cleanTranslated.addAll(clean(translated));
   // No surviving original means no translation may be displayed without a source.
   if(cleanOriginal.isEmpty())cleanTranslated.clear();
  }
  article.put("content_en",String.join("\n\n",cleanOriginal));
  if(article.get("content_zh")!=null)article.put("content_zh",String.join("\n\n",cleanTranslated));
 }
}
