package org.botai.back.catalog;

import org.botai.back.common.JsonCodec;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Публичные первоисточники отделены от внутреннего поставщика и URL захвата. */
final class CatalogSources {
    private static final Set<String> ORIGIN_HOSTS=Set.of("fipi.ru","doc.fipi.ru","statgrad.org");
    private CatalogSources() { }
    static JsonNode project(JsonNode stored,JsonCodec json) {
        List<Object> result=new ArrayList<>();
        if(stored!=null&&stored.isArray())for(JsonNode source:stored) {
            var projection=new LinkedHashMap<String,Object>();
            JsonNode publisher=source.get("originalPublisher");
            projection.put("originalPublisher",publisher!=null&&publisher.isObject()?origin(publisher,"name"):null);
            List<Object> references=new ArrayList<>();JsonNode refs=source.get("originalReferences");
            if(refs!=null&&refs.isArray())for(JsonNode reference:refs)if(reference.isObject()) {
                var value=origin(reference,"label");
                for(String field:List.of("publisher","year","examNumber")) {
                    JsonNode item=reference.get(field);
                    value.put(field,item==null||item.isNull()?null:field.equals("publisher")?item.asText():item.asInt());
                }
                references.add(value);
            }
            projection.put("originalReferences",references);result.add(projection);
        }
        return json.read(json.write(result));
    }
    private static Map<String,Object> origin(JsonNode node,String label) {
        var result=new LinkedHashMap<String,Object>();result.put(label,node.path(label).asText());
        JsonNode url=node.get("url");result.put("url",url==null||url.isNull()?null:safeUrl(url.asText()));return result;
    }
    private static String safeUrl(String value) {
        try {
            URI uri=URI.create(value);
            return "https".equals(uri.getScheme())&&ORIGIN_HOSTS.contains(uri.getHost())&&uri.getUserInfo()==null&&uri.getRawQuery()==null&&uri.getFragment()==null&&(uri.getPort()==-1||uri.getPort()==443)?value:null;
        } catch(IllegalArgumentException invalid) { return null; }
    }
}
