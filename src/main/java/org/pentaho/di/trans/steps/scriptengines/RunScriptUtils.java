package org.pentaho.di.trans.steps.scriptengines;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

public class RunScriptUtils {

  private static ScriptEngineManager scriptEngineManager;

  private static final Map<String, List<ScriptEngineFactory>> languageFactoryMap
    = new HashMap<String, List<ScriptEngineFactory>>();
  private static List<ScriptEngineFactory> engineFactories = null;

  /**
   * Instantiates the right scripting language interpreter, falling back to Groovy for backward compatibility
   *
   * @param engineName
   * @return the desired ScriptEngine, or null if none can be found
   */
  public static ScriptEngine createNewScriptEngine( String engineName ) {

    ScriptEngine scriptEngine = getScriptEngineManager().getEngineByName( engineName );
    if ( scriptEngine == null ) {
      // falls back to Groovy
      scriptEngine = getScriptEngineManager().getEngineByName( "groovy" );
    }
    return scriptEngine;
  }

  public static ScriptEngine createNewScriptEngineByLanguage( String languageName ) {

    // Defaults to Groovy
    ScriptEngine scriptEngine = getScriptEngineManager().getEngineByName( "groovy" );
    if ( engineFactories == null ) {
      populateEngineFactoryMap();
    }
    List<ScriptEngineFactory> factories = languageFactoryMap.get( languageName );
    if ( factories != null ) {
      for ( ScriptEngineFactory factory : factories ) {
        try {
          scriptEngine = factory.getScriptEngine();

          if ( scriptEngine != null ) {
            break;
          }
        } catch ( Exception e ) {
          // Do nothing, try the next engine
        }
      }
    }
    return scriptEngine;
  }

  public static ScriptEngineManager getScriptEngineManager() {
    if ( scriptEngineManager == null ) {
      System.setProperty( "org.jruby.embed.localvariable.behavior", "persistent" );// required for JRuby, transparent
      // for others
      scriptEngineManager = new ScriptEngineManager( RunScriptUtils.class.getClassLoader() );
      populateEngineFactoryMap();
    }
    return scriptEngineManager;
  }

  public static List<String> getScriptLanguageNames() {
    List<String> scriptEngineNames = new ArrayList<String>();
    engineFactories = getScriptEngineManager().getEngineFactories();
    if ( engineFactories != null ) {
      for ( ScriptEngineFactory factory : engineFactories ) {
        final String engineName = factory.getLanguageName();
        scriptEngineNames.add( engineName );
      }
    }
    return scriptEngineNames;
  }

  private static void populateEngineFactoryMap() {
    engineFactories = getScriptEngineManager().getEngineFactories();
    if ( engineFactories != null ) {
      for ( ScriptEngineFactory factory : engineFactories ) {
        final String languageName = factory.getLanguageName();
        List<ScriptEngineFactory> languageFactories = languageFactoryMap.get( languageName );
        if ( languageFactories == null ) {
          languageFactories = new ArrayList<ScriptEngineFactory>();
          languageFactoryMap.put( languageName, languageFactories );
        }
        languageFactories.add( factory );
      }
    }
  }

}
