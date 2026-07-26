package io.github.himath2002.maze.scripting;

import io.github.himath2002.maze.api.GameAPI;
import io.github.himath2002.maze.api.GameScript;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Loads Jython map scripts and dispatches engine lifecycle events.
 */
public final class ScriptManager {
    private static final Logger LOGGER = Logger.getLogger(ScriptManager.class.getName());
    private final List<GameScript> scripts = new ArrayList<>();
    private final GameAPI api;

    public ScriptManager(GameAPI api) {
        this.api = api;
    }

    public void load(List<String> sources) {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("python");
        if (engine == null) {
            engine = manager.getEngineByName("jython");
        }

        if (engine == null) {
            LOGGER.warning("No Python or Jython script engine is available");
            return;
        }
        if (!(engine instanceof Invocable invocable)) {
            LOGGER.warning("The configured script engine is not invocable");
            return;
        }

        for (int index = 0; index < sources.size(); index++) {
            loadScript(engine, invocable, sources.get(index), index + 1);
        }
    }

    public void onPlayerMoved() {
        for (GameScript script : scripts) {
            safely(script::onPlayerMoved);
        }
    }

    public void onItemAcquired(String itemName) {
        for (GameScript script : scripts) {
            safely(() -> script.onItemAcquired(itemName));
        }
    }

    public void shutdown() {
        scripts.clear();
    }

    private void loadScript(
        ScriptEngine engine,
        Invocable invocable,
        String source,
        int index
    ) {
        try {
            engine.eval(source);
            Object scriptObject = engine.get("script");
            if (scriptObject == null) {
                LOGGER.warning("Map script " + index + " does not define a 'script' object");
                return;
            }

            GameScript script = invocable.getInterface(scriptObject, GameScript.class);
            if (script == null) {
                LOGGER.warning("Map script " + index + " does not implement GameScript");
                return;
            }

            script.initialize(api);
            scripts.add(script);
            LOGGER.info("Loaded map script " + index);
        } catch (ScriptException | RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Unable to load map script " + index, ex);
        }
    }

    private void safely(Runnable event) {
        try {
            event.run();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Map script event failed", ex);
        }
    }
}
