package de.benjaminaaron.ontoserver.suggestion;

import de.benjaminaaron.ontoserver.model.ModelController;
import de.benjaminaaron.ontoserver.model.Utils;
import de.benjaminaaron.ontoserver.routing.websocket.WebSocketRouting;
import de.benjaminaaron.ontoserver.suggestion.job.MergeSuggestionsJob;
import de.benjaminaaron.ontoserver.suggestion.job.task.CaseSensitivityTask;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
// @EnableAsync
public class SuggestionEngine {

    private final Logger logger = LogManager.getLogger(SuggestionEngine.class);
    private final Map<String, Suggestion> suggestions = new HashMap<>();

    @Autowired
    private WebSocketRouting router;

    @Autowired
    private ModelController modelController;

    @SneakyThrows
    @PostConstruct
    void init() {
        TaskSchedulingManager taskManager = new TaskSchedulingManager(this);
        taskManager.schedulePeriodicJob("mergeSuggestionsJob", 5, 30);
    }

    public void mergeSuggestionsJob() {
        MergeSuggestionsJob job = new MergeSuggestionsJob(modelController.getModel());
        logger.info("Starting " + job.getJobName());
        job.addTask(new CaseSensitivityTask());
        List<Suggestion> list = job.execute();
        logger.info(job.getJobDurationString());
        list.forEach(this::registerSuggestionIfNew);
        sendUnsentSuggestions();
    }

    public void sendUnsentSuggestions() {
        List<Suggestion> unsent = suggestions.values().stream().filter(s -> !s.getIsSent()).collect(Collectors.toList());
        for (Suggestion sug : unsent) {
            router.sendSuggestion(sug.getMessage());
            sug.isSent();
        }
    }

    private void registerSuggestionIfNew(Suggestion suggestion) {
        if (suggestions.containsValue(suggestion)) {
            // more refined logic required to decide when a new suggestion should replace an old one TODO
            return;
        }
        String id = Utils.generateRandomId();
        suggestion.setId(id);
        suggestions.put(id, suggestion);
    }

    public boolean suggestionExists(String id) {
        return suggestions.containsKey(id);
    }

    public String accept(String id) {
        return suggestions.get(id).getMessage().getAchievingCommand();
    }
}
