package cn.codedog.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class HtmlSanitizer {
    private static final Set<String> IMAGE_PREFIXES = Set.of(
        "data:image/gif;base64,", "data:image/jpeg;base64,",
        "data:image/png;base64,", "data:image/webp;base64,");
    private final Safelist safelist = Safelist.none()
        .addTags("a", "b", "blockquote", "br", "code", "div", "em", "h1", "h2", "h3", "h4",
            "hr", "i", "img", "li", "ol", "p", "pre", "s", "span", "strong", "table", "tbody",
            "td", "tfoot", "th", "thead", "tr", "u", "ul")
        .addAttributes("a", "href", "title")
        .addAttributes("img", "src", "alt", "title")
        .addAttributes("td", "colspan", "rowspan")
        .addAttributes("th", "colspan", "rowspan")
        .addProtocols("a", "href", "http", "https", "mailto")
        .addProtocols("img", "src", "data")
        .addEnforcedAttribute("a", "target", "_blank")
        .addEnforcedAttribute("a", "rel", "noopener noreferrer");

    public String clean(String html) {
        String cleaned = Jsoup.clean(html == null ? "" : html, "", safelist,
            new Document.OutputSettings().prettyPrint(false));
        Document fragment = Jsoup.parseBodyFragment(cleaned);
        for (Element image : fragment.select("img")) {
            String source = image.attr("src").trim();
            String lower = source.toLowerCase();
            if (IMAGE_PREFIXES.stream().noneMatch(lower::startsWith)) image.remove();
        }
        for (Element cell : fragment.select("td[colspan],td[rowspan],th[colspan],th[rowspan]")) {
            for (String attribute : new String[]{"colspan", "rowspan"}) {
                if (!cell.hasAttr(attribute)) continue;
                try {
                    int value = Integer.parseInt(cell.attr(attribute));
                    if (value < 1 || value > 100) cell.removeAttr(attribute);
                } catch (NumberFormatException error) {
                    cell.removeAttr(attribute);
                }
            }
        }
        return fragment.body().html().trim();
    }
}
