package org.pentaho.di.trans.steps.scriptengines;

import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

public class RunScriptUtils {

  private static ScriptEngineManager scriptEngineManager;

  /**
   * Instantiates the right scripting language interpreter, falling back to Gremlin-Groovy for backward compatibility
   *
   * @param engineName
   * @return the desired ScriptEngine, or null if none can be found
   */
  public static ScriptEngine createNewScriptEngine ( String engineName ) {

    ScriptEngine scriptEngine = getScriptEngineManager ().getEngineByName ( engineName );
    if ( scriptEngine == null ) {
      // falls back to Groovy
      scriptEngine = getScriptEngineManager ().getEngineByName ( "groovy" );
    }
    return scriptEngine;
  }

  public static ScriptEngineManager getScriptEngineManager () {
    if ( scriptEngineManager == null ) {
      System.setProperty ( "org.jruby.embed.localvariable.behavior", "persistent" );// required for JRuby, transparent
      // for others
      scriptEngineManager = new ScriptEngineManager ( RunScriptUtils.class.getClassLoader () );
    }
    return scriptEngineManager;
  }

  public static List<String> getScriptEngineFriendlyNames () {
    List<String> scriptEngineNames = new ArrayList<String> ();
    List<ScriptEngineFactory> engineFactories = getScriptEngineManager ().getEngineFactories ();
    if ( engineFactories != null ) {
      for ( ScriptEngineFactory factory : engineFactories ) {
        final String engineName = factory.getLanguageName ();
        scriptEngineNames.add ( engineName );
      }
    }
    return scriptEngineNames;
  }

}
