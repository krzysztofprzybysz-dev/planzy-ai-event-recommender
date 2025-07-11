package pl.planzy.scraping.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import java.util.List;

@Component("eventMapperEbilet")
public class EventMapperEbilet implements EventMapper {

    private static final Logger logger = LoggerFactory.getLogger(EventMapperEbilet.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<JsonNode> mapEvents(List<JsonNode> data) {
        logger.info("[{}] Starting to map events. Total events to map: [{}]", getClass().getSimpleName(), data.size());

        List<JsonNode> mappedEvents = new ArrayList<>();



        for (JsonNode event : data) {
            try {
                mappedEvents.add(mapEbiletEvent(event));
            } catch (Exception e) {
                logger.error("[{}] Error mapping event: {}", getClass().getSimpleName(), event, e);
            }
        }

        logger.info("[{}] Finished mapping events. Total mapped events: [{}]", getClass().getSimpleName(), mappedEvents.size());

        return mappedEvents;
    }

    private JsonNode mapEbiletEvent(JsonNode event) {

        String startDateTimestamp = "null";
        String endDateTimeStamp = "null";

        if (event.has("dateFrom") && !event.get("dateFrom").isNull() && !event.get("dateFrom").asText().isEmpty() && !event.get("dateFrom").asText().equalsIgnoreCase("null")) {
            try {
                startDateTimestamp = convertToTimestamp(event.get("dateFrom").asText());
            } catch (DateTimeParseException e) {
                logger.warn("[{}] Invalid date format for 'dateFrom': [{}]", getClass().getSimpleName(), event.get("dateFrom").asText(), e);
            }
        }

        if (event.has("dateTo") && !event.get("dateTo").isNull() && !event.get("dateTo").asText().isEmpty() && !event.get("dateTo").asText().equalsIgnoreCase("null")) {
            try {
                endDateTimeStamp = convertToTimestamp(event.get("dateTo").asText());
            } catch (DateTimeParseException e) {
                logger.warn("[{}] Invalid date format for dateTo: [{}]", getClass().getSimpleName(), event.get("dateTo").asText(), e);
            }
        }

        String artistsNames = event.has("artists") && event.get("artists").isArray()
                ? String.join(", ", mapper.convertValue(event.get("artists"), List.class))
                : "Unknown Artist";

        String customName = event.has("nextEventPlace") && event.get("nextEventPlace").has("customName")
                ? event.get("nextEventPlace").get("customName").asText()
                : "Unknown Place";

        String location = event.has("nextEventPlace") && event.get("nextEventPlace").has("city")
                ? event.get("nextEventPlace").get("city").asText()
                : "Unknown City";

        String url = "";

        if (event.has("linkTo") && event.get("linkTo").asText().equalsIgnoreCase("null")) {
            String category = event.get("category").asText();
            String subcategory = event.get("subcategory").asText().replace("\"", "");
            String slug = event.get("slug").asText();
            url = "https://www.ebilet.pl/" + category + "/" + subcategory + "/" + slug;
        } else {
            url = event.get("linkTo").asText();
        }

        StringBuilder cat = new StringBuilder();

        if (event.has("subcategoryName") && !event.get("subcategoryName").isNull()
                && !event.get("subcategoryName").asText().isBlank()
                && !event.get("subcategoryName").asText().equalsIgnoreCase("null")) {

            cat.append(event.get("subcategoryName").asText()).append(", ");
        }

        if (event.has("category") && !event.get("category").isNull()
                && !event.get("category").asText().isBlank()
                && !event.get("category").asText().equalsIgnoreCase("null")) {

            cat.append(event.get("category").asText()).append(", ");
        }

        if (event.has("subcategory") && !event.get("category").isNull()
                && !event.get("subcategory").asText().isBlank()
                && !event.get("subcategory").asText().equalsIgnoreCase("null")) {

            cat.append(event.get("subcategory").asText());
        }

        String normalizedTag = normalizeTags(cat.toString());

        return mapper.createObjectNode()
                .put("event_name", event.get("title").asText())
                .put("artists", artistsNames)
                .put("start_date", startDateTimestamp)
                .put("end_date", endDateTimeStamp)
                .put("thumbnail", "https://www.ebilet.pl/media" + event.get("imageLandscape").asText())
                .put("url", url)
                .put("location", location)
                .put("place", customName)
                .put("category", event.get("categoryName").asText())
                .put("tags", normalizedTag)
                .put("description", event.get("metaDescription").asText())
                .put("source", "eBilet");
    }

    private String convertToTimestamp(String dateString) {
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(dateString);
            return String.valueOf(localDateTime.toEpochSecond(ZoneOffset.UTC));
        } catch (Exception e) {
            logger.error("[{}] Error parsing date: {}", getClass().getSimpleName(), dateString, e);
            throw new RuntimeException("Error parsing date: " + dateString, e);
        }
    }


    // Metoda normalizująca tagi
    private String normalizeTags(String tagsString) {
        if (tagsString == null || tagsString.isEmpty()) {
            return "";
        }

        // Podziel string tagów po przecinkach
        String[] tagArray = tagsString.split(",");
        List<String> normalizedTags = new ArrayList<>();

        for (String tag : tagArray) {
            String normalizedTag = normalizeTag(tag.trim());
            if (!normalizedTag.isEmpty()) {
                normalizedTags.add(normalizedTag);
            }
        }

        // Połącz znormalizowane tagi z powrotem w string
        return String.join(", ", normalizedTags);
    }

    private String normalizeTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return "";
        }

        // Normalizacja podstawowa: małe litery, usunięcie nadmiarowych spacji
        String normalized = tag.toLowerCase().trim().replaceAll("\\s+", " ");

        // Standaryzacja znaków specjalnych
        normalized = normalized.replace("-", " ").replace("_", " ");

        // Usuwanie znaków niepożądanych
        normalized = normalized.replaceAll("[^a-ząćęłńóśźż0-9 ]", "");

        return normalized;
    }

}