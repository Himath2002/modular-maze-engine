package io.github.himath2002.maze.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Loads localized property bundles as UTF-8.
 */
public final class Utf8ResourceControl extends ResourceBundle.Control {
    @Override
    public ResourceBundle newBundle(
        String baseName,
        Locale locale,
        String format,
        ClassLoader loader,
        boolean reload
    ) throws IllegalAccessException, InstantiationException, IOException {
        String bundleName = toBundleName(baseName, locale);
        String resource = toResourceName(bundleName, "properties");

        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }

            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    }
}
