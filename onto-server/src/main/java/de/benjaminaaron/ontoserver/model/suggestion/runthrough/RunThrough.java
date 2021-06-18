package de.benjaminaaron.ontoserver.model.suggestion.runthrough;

import de.benjaminaaron.ontoserver.model.suggestion.Suggestion;
import de.benjaminaaron.ontoserver.model.suggestion.runthrough.task.RunThroughTask;
import org.apache.jena.rdf.model.Model;

import java.util.ArrayList;
import java.util.List;

public abstract class RunThrough {

    final Model model;

    public RunThrough(Model model) {
        this.model = model;
    }

    List<RunThroughTask> tasks = new ArrayList<>();

    public void addTask(RunThroughTask task) {
        tasks.add(task);
    }

    public abstract List<Suggestion> execute();
}