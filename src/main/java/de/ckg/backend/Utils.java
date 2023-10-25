package de.ckg.backend;

import com.github.slugify.Slugify;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

public class Utils {

    public static final String DEFAULT_URI_NAMESPACE = "http://ckg.de/default#";
    private final static Slugify slugifier = Slugify.builder().lowerCase(false).build();

    public static String buildDefaultNsUri(String word) {
        return DEFAULT_URI_NAMESPACE + slugifier.slugify(word);
    }

    public static String ensureUri(String str) {
        // str = full URI or just local name (= word)
        if (isValidUri(str)) {
            return str;
        }
        return buildDefaultNsUri(str);
    }

    public static boolean isValidUri(String str) {
        try {
            new URL(str).toURI();
            return true;
        } catch (URISyntaxException | MalformedURLException e) {
            return false;
        }
    }
}
