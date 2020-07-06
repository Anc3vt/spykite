package ru.ancevt.webdatagrabber.api;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * @author ancevt
 */
public class Doc {

    private final Element element;
    private final String html;

    public Doc(String html) {
        element = Jsoup.parse(html);
        this.html = element.toString();
    }

    public Doc(Element element) {
        this.element = element;
        html = element.toString();
    }

    public String getByTag(String tag) {
        if (element.getElementsByTag(tag).size() > 0) {
            return element.getElementsByTag(tag).get(0).text();
        }
        return null;
    }

    public String getTextByClassPart(String classPart, int classNum, int index) {
        final String clazz = getClassByPart(classPart, classNum);

        if (clazz != null && element.getElementsByClass(clazz).size() > index) {
            return element.getElementsByClass(clazz).get(index).text();
        }
        return null;
    }

    public String getTextByClass(String clazz) {
        if (element.getElementsByClass(clazz).size() > 0) {
            return element.getElementsByClass(clazz).get(0).text();
        }
        return null;
    }

    public String getTextByClass(String clazz, int index) {
        if (element.getElementsByClass(clazz).size() > index) {
            return element.getElementsByClass(clazz).get(index).text();
        }
        return null;
    }

    public String getTextById(String id) {
        return element.getElementById(id).text();
    }

    public String getTextByAttrValue(String attr, String value) {
        if (element.getElementsByAttributeValue(attr, value).size() > 0) {
            return element.getElementsByAttributeValue(attr, value).get(0).text();
        }
        return null;
    }

    public boolean hasClass(String clazz) {
        return element.getElementsByClass(clazz).size() > 0;
    }

    public boolean hasClassByPart(String part) {
        final String clazz = getClassByPart(part);
        return clazz != null;
    }

    public static void main(String[] args) {
        final String source = "<span class=\"LEFT--value--RIGHT\"></span><span class=\"left--value--right\"></span><span class=\"left--value--right\">";
        final Doc d = new Doc(source);

        final String[] r = getAllClassesByPart(source, "--value--");

        System.out.println("------------");
        for (int i = 0; i < r.length; i++) {
            System.out.println(r[i]);
        }

    }

    public final String[] getClassesByPart(String part) {
        return getAllClassesByPart(html, part);
    }

    private static String[] getAllClassesByPart(String source, String part) {
        if (source.contains(part)) {

            final List<String> list = new ArrayList<>();

            int idx = -2;
            while (idx != -1) {
                idx = source.indexOf(part, idx + 1);
                if (idx == -1) {
                    break;
                }

                final String left = getLeft(source, idx - 1);
                final String right = getRight(source, idx + part.length());
                final String clazz = left + part + right;

                if (!list.contains(clazz)) {
                    list.add(clazz);
                }

            }
            return list.toArray(new String[]{});
        }

        return null;
    }

    private static String getLeft(String source, int idx) {
        String result = "";
        char c = '\0';
        while (c != '"') {
            if (c != '\0') {
                result = c + result;
            }
            c = source.charAt(idx);
            idx--;
        }
        return result;
    }

    private static String getRight(String source, int idx) {
        String result = "";
        char c = '\0';
        while (c != '"') {
            if (c != '\0') {
                result = result + c;
            }
            c = source.charAt(idx);
            idx++;
        }
        return result;
    }

    public String getClassByPart(String part) {
        return getClassByPart(part, 0);
    }

    public String getClassByPart(String part, String parentPart) {
        final String[] classes = getClassesByPart(part);

        for (final String c : classes) {
            if (isClassHasParentPart(c, parentPart)) {
                return c;
            }
        }

        return null;
    }

    public String getClassByPart(String part, int classNum) {
        if (html.contains(part)) {
            final String[] classes = getAllClassesByPart(html, part);
            if (classNum < classes.length) {
                return classes[classNum].trim();
            }

        }

        return null;
    }

    public int countClassByPart(String part) {
        if (html.contains(part)) {
            return getAllClassesByPart(html, part).length;
        }
        return 0;
    }

    public int countClass(String clazz) {
        return element.getElementsByClass(clazz).size();
    }

    public boolean isParentOf(Element child, Element parent) {
        Element p = child.parent();
        while (p != null) {
            if (p == parent) {
                return true;
            }
            p = p.parent();
        }
        return false;
    }

    public boolean isClassHasParentPart(String childPart, String parentPart) {
        final int countChild = countClassByPart(childPart);
        final int countParent = countClassByPart(parentPart);

        for (int i = 0; i < countParent; i++) {
            final String parentClass = getClassByPart(parentPart, i);

            for (int j = 0; j < countChild; j++) {
                final String childClass = getClassByPart(childPart, j);

                final Elements elsParent = element.getElementsByClass(parentClass);
                final Elements elsChild = element.getElementsByClass(childClass);

                if (elsParent.stream().anyMatch((pp) -> (elsChild.stream().anyMatch((cc) -> (isParentOf(cc, pp)))))) {
                    return true;
                }

            }
        }

        return false;
    }

    public String getTextByChildHasParent(String childPart, String parentPart) {
        final int countChild = countClassByPart(childPart);
        final int countParent = countClassByPart(parentPart);

        for (int i = 0; i < countParent; i++) {
            final String parentClass = getClassByPart(parentPart, i);

            for (int j = 0; j < countChild; j++) {
                final String childClass = getClassByPart(childPart, j);

                final Elements elsParent = element.getElementsByClass(parentClass);
                final Elements elsChild = element.getElementsByClass(childClass);

                for (Element pp : elsParent) {
                    for (Element cc : elsChild) {
                        if (isParentOf(cc, pp)) {
                            return cc.text();
                        }
                    }
                }

            }
        }

        return null;
    }
}
