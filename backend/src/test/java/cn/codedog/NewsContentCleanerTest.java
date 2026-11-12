package cn.codedog;

import cn.codedog.news.NewsContentCleaner;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NewsContentCleanerTest {
 private static final String DATE="- 2026年11月04日 11:06:43";
 @Test void removesConfirmedRecommendationDatesButNotTheReporting(){
  for(String heading:List.of("国际新闻精选：","经济新闻精选：","财经新闻精选：","社会新闻精选：","文化新闻精选：","港澳台新闻精选："))
   assertThat(NewsContentCleaner.clean(List.of("事实正文。",heading,DATE,DATE))).as(heading).containsExactly("事实正文。");
 }
 @Test void preservesEventDatesAndLiveUpdatesWithoutARecommendationHeading(){
  var reporting=List.of("At 11:06 the minister spoke.",DATE,DATE,"A new development followed.");
  assertThat(NewsContentCleaner.clean(reporting)).isEqualTo(reporting);
 }
 @Test void preservesAmbiguousHeadingsFollowedByReporting(){
  var reporting=List.of("Original report.","Read more:","The minister announced new measures.",DATE,DATE);
  assertThat(NewsContentCleaner.clean(reporting)).isEqualTo(reporting);
 }
 @Test void pairedCleanupUsesOriginalIndexEvenIfTranslatedHeadingDiffers(){
  var article=new HashMap<String,Object>();
  article.put("content_en","Original report.\n\nRelated stories:\n\n[First](https://example.com/1)\n\n[Second](https://example.com/2)");
  article.put("content_zh","新闻译文。\n\n模型翻译出的其他说法\n\n第一条\n\n第二条");
  NewsContentCleaner.cleanArticle(article);
  assertThat(article.get("content_en")).isEqualTo("Original report.");
  assertThat(article.get("content_zh")).isEqualTo("新闻译文。");
 }
 @Test void incompleteTranslationIsNotCutAtAnUnrelatedIndex(){
  var article=new HashMap<String,Object>();
  article.put("content_en","Original report.\n\nRelated stories:\n\n[First](https://example.com/1)\n\n[Second](https://example.com/2)");
  article.put("content_zh","第一段。\n\n第二段。");
  NewsContentCleaner.cleanArticle(article);
  assertThat(article.get("content_zh")).isEqualTo("第一段。\n\n第二段。");
 }
 @Test void pendingTranslationStaysNullAndRepeatedCleanupIsStable(){
  var article=new HashMap<String,Object>();article.put("content_en","新闻正文。\n\n国际新闻精选：\n\n"+DATE+"\n\n"+DATE);
  NewsContentCleaner.cleanArticle(article);NewsContentCleaner.cleanArticle(article);
  assertThat(article.get("content_en")).isEqualTo("新闻正文。");
  assertThat(article).doesNotContainKey("content_zh");
 }

 private static final String CHROME="Video titleCloseMore on this story.SubsectionHealthPublished4 minutes agoShareclose panelShare pageCopy linkAbout sharingRelated topicsFluFollow FluRead description";
 @Test void removesGluedPlayerControlsAndPairedTranslationButKeepsReporting(){
  var article=new HashMap<String,Object>();
  article.put("content_en","Real reporting on 12 November.\n\n"+CHROME+"\n\nA doctor explains the results.");
  article.put("content_zh","11月12日的真实报道。\n\n乱七八糟的分享控件译文\n\n医生解释结果。");
  NewsContentCleaner.cleanArticle(article);
  assertThat(article.get("content_en")).isEqualTo("Real reporting on 12 November.\n\nA doctor explains the results.");
  assertThat(article.get("content_zh")).isEqualTo("11月12日的真实报道。\n\n医生解释结果。");
 }
 @Test void doesNotMistakeReportingAboutInterfacesForPlayerControls(){
  String report="The review discusses Share page, Copy link, About sharing and related topics as interface labels.";
  assertThat(NewsContentCleaner.clean(List.of(report))).containsExactly(report);
 }
 @Test void pollutedOnlyBodyCannotLeaveAnOrphanTranslation(){
  var article=new HashMap<String,Object>();article.put("content_en",CHROME);article.put("content_zh","分享乱码\n\n更多乱码");
  NewsContentCleaner.cleanArticle(article);
  assertThat(article.get("content_en")).isEqualTo("");assertThat(article.get("content_zh")).isEqualTo("");
 }
}
