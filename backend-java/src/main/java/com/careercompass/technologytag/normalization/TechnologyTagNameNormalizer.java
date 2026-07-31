package com.careercompass.technologytag.normalization;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class TechnologyTagNameNormalizer {

    public String normalize(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_-]+", "");
    }
}
