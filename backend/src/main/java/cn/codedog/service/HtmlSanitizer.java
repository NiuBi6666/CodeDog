package cn.codedog.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class HtmlSanitizer {
    private static final Set<String> IMAGE_PREFIXES = Set.of(
        "data:image/gif;base64,", "data:image/jpeg;base64,",
        "data:image/png;base64,", "data:image/webp;base64,");
    private static final Set<String> ALIGNMENTS = Set.of("left", "center", "right", "justify");
    private static final Set<String> STYLE_TAGS = Set.of("blockquote", "caption", "div", "figcaption",
        "figure", "h1", "h2", "h3", "h4", "img", "li", "ol", "p", "pre", "span", "table",
        "td", "th", "ul");
    private static final Pattern DIMENSION = Pattern.compile("^(-?\\d+(?:\\.\\d+)?)(px|pt|em|rem|%)$");
    private static final Pattern FONT_WEIGHT = Pattern.compile("^(?:normal|bold|[1-9]00)$");
    private static final Pattern SAFE_DOC_CLASS = Pattern.compile("^doc-(?:align-(?:left|center|right|justify)|bold|italic|underline|strike|block|inline-block|pre-wrap|super|sub|font-(?:12|14|16|18|20|24|28|32|36|48|64)|indent-[1-6]|line-(?:100|125|150|175|200|250|300)|list-(?:disc|circle|square|decimal|lower-alpha|upper-alpha|lower-roman|upper-roman|none)|[mp][bt]-(?:0|8|16|24|32|48|64))$");
    private static final int[] FONT_SIZES = {12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 64};
    private static final int[] SPACING_SIZES = {0, 8, 16, 24, 32, 48, 64};
    private static final int[] LINE_HEIGHTS = {100, 125, 150, 175, 200, 250, 300};
    private final Safelist safelist;

    public HtmlSanitizer() {
        safelist = Safelist.none()
            .addTags("a", "b", "blockquote", "br", "caption", "code", "col", "colgroup", "del", "div",
                "em", "figcaption", "figure", "h1", "h2", "h3", "h4", "hr", "i", "img", "li",
                "mark", "ol", "p", "pre", "s", "span", "strike", "strong", "sub", "sup", "table",
                "tbody", "td", "tfoot", "th", "thead", "tr", "u", "ul")
            .addAttributes("a", "href", "title")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addAttributes("span", "class", "data-latex", "data-display")
            .addAttributes("p", "align").addAttributes("div", "align")
            .addAttributes("h1", "align").addAttributes("h2", "align")
            .addAttributes("h3", "align").addAttributes("h4", "align")
            .addAttributes("blockquote", "align").addAttributes("pre", "align")
            .addAttributes("table", "width").addAttributes("col", "span", "width")
            .addAttributes("td", "align", "colspan", "rowspan", "width", "height")
            .addAttributes("th", "align", "colspan", "rowspan", "width", "height")
            .addAttributes("ol", "start", "type").addAttributes("ul", "type").addAttributes("li", "value")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "data")
            .addEnforcedAttribute("a", "target", "_blank")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer");
        for (String tag : STYLE_TAGS) safelist.addAttributes(tag, "style");
        for (String tag : STYLE_TAGS) safelist.addAttributes(tag, "class");
    }

    public String clean(String html) {
        String cleaned = Jsoup.clean(html == null ? "" : html, "", safelist,
            new Document.OutputSettings().prettyPrint(false));
        Document fragment = Jsoup.parseBodyFragment(cleaned);
        for (Element image : fragment.select("img")) {
            String source = image.attr("src").trim();
            String lower = source.toLowerCase();
            if (IMAGE_PREFIXES.stream().noneMatch(lower::startsWith)) image.remove();
        }
        for (Element aligned : fragment.select("[align]")) {
            if (!ALIGNMENTS.contains(aligned.attr("align").toLowerCase())) aligned.removeAttr("align");
        }
        for (Element styled : fragment.select("[style]")) {
            applyStyleClasses(styled, styled.attr("style"));
            styled.removeAttr("style");
        }
        for (Element span : fragment.select("span[class],span[data-latex],span[data-display]")) {
            if (!span.hasClass("math-formula")) {
                span.removeAttr("class").removeAttr("data-latex").removeAttr("data-display");
                continue;
            }
            String latex = span.attr("data-latex").trim();
            if (latex.isEmpty() || latex.length() > 4000) {
                span.removeAttr("class").removeAttr("data-latex").removeAttr("data-display");
                continue;
            }
            span.attr("class", "math-formula");
            span.attr("data-latex", latex);
            span.attr("data-display", String.valueOf(span.attr("data-display").equalsIgnoreCase("true")));
            span.empty();
        }
        for (Element classed : fragment.select("[class]")) {
            if (classed.hasClass("math-formula")) continue;
            String classes = classed.classNames().stream().filter(name -> SAFE_DOC_CLASS.matcher(name).matches())
                .sorted().collect(java.util.stream.Collectors.joining(" "));
            if (classes.isEmpty()) classed.removeAttr("class"); else classed.attr("class", classes);
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
        for (Element sized : fragment.select("[width],[height],col[span],ol[start],li[value]")) {
            for (String attribute : new String[]{"width", "height", "span", "start", "value"}) {
                if (!sized.hasAttr(attribute)) continue;
                String value = sized.attr(attribute).trim();
                if (!safeDimensionAttribute(value, attribute)) sized.removeAttr(attribute);
            }
        }
        return fragment.body().html().trim();
    }

    private void applyStyleClasses(Element element, String source) {
        for (String declaration : source.split(";")) {
            int separator = declaration.indexOf(':');
            if (separator < 1) continue;
            String property = declaration.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(separator + 1).trim().toLowerCase(Locale.ROOT);
            switch (property) {
                case "text-align" -> { if (ALIGNMENTS.contains(value)) element.addClass("doc-align-" + value); }
                case "font-weight" -> { if (isBold(value)) element.addClass("doc-bold"); }
                case "font-style" -> { if (value.equals("italic")) element.addClass("doc-italic"); }
                case "text-decoration" -> {
                    if (value.contains("underline")) element.addClass("doc-underline");
                    if (value.contains("line-through")) element.addClass("doc-strike");
                }
                case "display" -> {
                    if (value.equals("block")) element.addClass("doc-block");
                    if (value.equals("inline-block")) element.addClass("doc-inline-block");
                }
                case "white-space" -> { if (value.equals("pre") || value.equals("pre-wrap")) element.addClass("doc-pre-wrap"); }
                case "vertical-align" -> {
                    if (value.equals("super")) element.addClass("doc-super");
                    if (value.equals("sub")) element.addClass("doc-sub");
                }
                case "font-size" -> addNearestLengthClass(element, value, "doc-font-", FONT_SIZES);
                case "margin-left", "padding-left", "text-indent" -> addIndentClass(element, value);
                case "margin-top" -> addNearestLengthClass(element, value, "doc-mt-", SPACING_SIZES);
                case "margin-bottom" -> addNearestLengthClass(element, value, "doc-mb-", SPACING_SIZES);
                case "padding-top" -> addNearestLengthClass(element, value, "doc-pt-", SPACING_SIZES);
                case "padding-bottom" -> addNearestLengthClass(element, value, "doc-pb-", SPACING_SIZES);
                case "margin" -> applySpacingShorthand(element, value, "m");
                case "padding" -> applySpacingShorthand(element, value, "p");
                case "line-height" -> addLineHeightClass(element, value);
                case "list-style-type" -> {
                    if (Set.of("disc", "circle", "square", "decimal", "lower-alpha", "upper-alpha",
                        "lower-roman", "upper-roman", "none").contains(value)) element.addClass("doc-list-" + value);
                }
                case "width" -> applyWidth(element, value);
                default -> { }
            }
        }
    }

    private boolean isBold(String value) {
        if (!FONT_WEIGHT.matcher(value).matches()) return false;
        if (value.equals("bold")) return true;
        try { return Integer.parseInt(value) >= 600; } catch (NumberFormatException ignored) { return false; }
    }

    private Double lengthInPixels(String value) {
        if (value.equals("0")) return 0.0;
        var match = DIMENSION.matcher(value);
        if (!match.matches()) return null;
        double number = Double.parseDouble(match.group(1));
        if (!Double.isFinite(number)) return null;
        return switch (match.group(2)) {
            case "px" -> number;
            case "pt" -> number * 4.0 / 3.0;
            case "em", "rem" -> number * 16.0;
            case "%" -> number * 0.16;
            default -> null;
        };
    }

    private void addNearestLengthClass(Element element, String value, String prefix, int[] choices) {
        Double pixels = lengthInPixels(value);
        if (pixels == null || pixels < 0 || pixels > choices[choices.length - 1] * 1.5) return;
        element.addClass(prefix + nearest(choices, pixels));
    }

    private void addIndentClass(Element element, String value) {
        Double pixels = lengthInPixels(value);
        if (pixels == null || pixels < 12 || pixels > 240) return;
        int level = Math.max(1, Math.min(6, (int) Math.round(pixels / 24.0)));
        element.addClass("doc-indent-" + level);
    }

    private void addLineHeightClass(Element element, String value) {
        double percent;
        try { percent = Double.parseDouble(value) * 100; }
        catch (NumberFormatException error) {
            Double pixels = lengthInPixels(value);
            if (pixels == null) return;
            percent = pixels / 16.0 * 100;
        }
        if (percent >= 80 && percent <= 320) element.addClass("doc-line-" + nearest(LINE_HEIGHTS, percent));
    }

    private void applySpacingShorthand(Element element, String value, String prefix) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 1 || parts.length > 4 || Arrays.stream(parts).anyMatch(part -> part.equals("auto"))) return;
        String top = parts[0];
        String bottom = parts.length < 3 ? parts[0] : parts[2];
        String left = parts.length == 1 ? parts[0] : parts.length == 2 || parts.length == 3 ? parts[1] : parts[3];
        addNearestLengthClass(element, top, "doc-" + prefix + "t-", SPACING_SIZES);
        addNearestLengthClass(element, bottom, "doc-" + prefix + "b-", SPACING_SIZES);
        if (prefix.equals("m")) addIndentClass(element, left);
    }

    private void applyWidth(Element element, String value) {
        if (!Set.of("col", "figure", "img", "table", "td", "th").contains(element.normalName())) return;
        if (value.endsWith("%")) {
            String percentage = value.substring(0, value.length() - 1);
            if (integerBetween(percentage, 1, 100)) element.attr("width", percentage + "%");
            return;
        }
        Double pixels = lengthInPixels(value);
        if (pixels != null && pixels >= 1 && pixels <= 4000) element.attr("width", String.valueOf(Math.round(pixels)));
    }

    private int nearest(int[] choices, double value) {
        int result = choices[0];
        for (int choice : choices) if (Math.abs(choice - value) < Math.abs(result - value)) result = choice;
        return result;
    }

    private boolean safeDimensionAttribute(String value, String attribute) {
        if (attribute.equals("width") || attribute.equals("height")) {
            if (value.endsWith("%")) return integerBetween(value.substring(0, value.length() - 1), 1, 100);
            return integerBetween(value, 1, 4000);
        }
        if (attribute.equals("span")) return integerBetween(value, 1, 100);
        if (attribute.equals("start") || attribute.equals("value")) return integerBetween(value, -100000, 100000);
        return false;
    }

    private boolean integerBetween(String value, int minimum, int maximum) {
        try {
            int number = Integer.parseInt(value);
            return number >= minimum && number <= maximum;
        } catch (NumberFormatException error) {
            return false;
        }
    }
}
