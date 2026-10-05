package org.botai.back.catalog;

import org.botai.back.common.JsonCodec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogSourcesTest {
    private final JsonCodec json=new JsonCodec(JsonMapper.builder().build());
    @Test void hidesProviderWithoutChangingStoredProvenance() {
        var stored=json.read("""
            [{"provider":"shkolkovo","externalId":"92097","sourceUrl":"https://3.shkolkovo.online/catalog/203/92097","originalPublisher":null,"originalReferences":[{"publisher":"ФИПИ","label":"Банк ФИПИ","url":null,"year":2024,"examNumber":4}]}]
            """);
        String before=json.write(stored);String publicJson=json.write(CatalogSources.project(stored,json));
        assertThat(publicJson).contains("Банк ФИПИ","2024").doesNotContain("shkolkovo","92097","sourceUrl","provider");
        assertThat(json.write(stored)).isEqualTo(before);
    }
    @Test void linksOnlyProvenPrimaryOriginUrls() {
        var stored=json.read("""
            [{"originalPublisher":{"name":"ФИПИ","url":"https://fipi.ru/ege"},"originalReferences":[{"label":"Экзамен","url":"https://3.shkolkovo.online/catalog/203/92097"},{"label":"СтатГрад","url":"https://statgrad.org/"},{"label":"Ошибка","url":"https://fipi.ru@evil.test/"}]}]
            """);
        var result=CatalogSources.project(stored,json).get(0);
        assertThat(result.get("originalPublisher").get("url").asText()).isEqualTo("https://fipi.ru/ege");
        assertThat(result.get("originalReferences").get(0).get("url").isNull()).isTrue();
        assertThat(result.get("originalReferences").get(1).get("url").asText()).isEqualTo("https://statgrad.org/");
        assertThat(result.get("originalReferences").get(2).get("url").isNull()).isTrue();
    }
}
