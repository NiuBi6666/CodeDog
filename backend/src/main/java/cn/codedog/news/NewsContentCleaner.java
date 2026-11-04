package cn.codedog.news;

import java.util.*;
import java.util.regex.Pattern;

/** Conservative footer removal for new ingestion and historical article responses. */
public final class NewsContentCleaner {
 private NewsContentCleaner(){}
 private static final Pattern HEADING=Pattern.compile("^(?:国际新闻精选|财经新闻精选|新闻精选|相关推荐|相关阅读|推荐阅读|Related (?:articles|stories|coverage)|Recommended (?:stories|articles)|Read more|More on this story)[:：]?$",Pattern.CASE_INSENSITIVE);
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
 public static List<String> clean(List<String> rows){return List.copyOf(rows.subList(0,end(rows)));}
 public static void cleanArticle(Map<String,Object> article){
  var original=paragraphs((String)article.get("content_en"));
  var translated=paragraphs((String)article.get("content_zh"));
  int originalEnd=end(original),translatedEnd=end(translated);
  // Apply the same boundary to both languages only when their original paragraph counts match.
  if(original.size()==translated.size()){
   int pairedEnd=Math.min(originalEnd,translatedEnd);originalEnd=pairedEnd;translatedEnd=pairedEnd;
  }
  article.put("content_en",String.join("\n\n",original.subList(0,originalEnd)));
  if(article.get("content_zh")!=null)article.put("content_zh",String.join("\n\n",translated.subList(0,translatedEnd)));
 }
}
