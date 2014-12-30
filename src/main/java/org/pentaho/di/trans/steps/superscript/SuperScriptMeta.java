/*******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2012 by Pentaho : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.di.trans.steps.superscript;

import java.math.BigDecimal;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.pentaho.di.compatibility.Value;
import org.pentaho.di.core.CheckResult;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.Counter;
import org.pentaho.di.core.annotations.Step;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.plugins.KettleURLClassLoader;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMeta;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

@Step( id = "SuperScript", image = "superscript.png", name = "SuperScript",
  description = "Executes scripts for JSR-223 Script Engines", categoryDescription = "Scripting" )
public class SuperScriptMeta extends BaseStepMeta implements StepMetaInterface {
  private static Class<?> PKG = SuperScriptMeta.class; // for i18n purposes, needed by Translator2!! $NON-NLS-1$

  private static final String SCRIPT_LANGUAGE_NAME = "scriptLanguage";
  private static final String SCRIPT_TAG_TYPE = "scriptType";
  private static final String SCRIPT_TAG_NAME = "scriptName";
  private static final String SCRIPT_TAG_SCRIPT = "scriptBody";

  private String languageName;

  private ScriptAddClasses[] additionalClasses;
  private ScriptValuesScript[] scripts;

  private String fieldname[];
  private String rename[];
  private int type[];
  private int length[];
  private int precision[];
  private boolean replace[]; // Replace the specified field.
  private boolean scriptResult[]; // Does this field contain the result of the compiledScript evaluation?

  public SuperScriptMeta() {
    super(); // allocate BaseStepMeta

  }

  /**
   * @return Returns the length.
   */
  public int[] getLength() {
    return length;
  }

  /**
   * @param length The length to set.
   */
  public void setLength( int[] length ) {
    this.length = length;
  }

  /**
   * @return Returns the name.
   */
  public String[] getFieldname() {
    return fieldname;
  }

  /**
   * @param fieldname The name to set.
   */
  public void setFieldname( String[] fieldname ) {
    this.fieldname = fieldname;
  }

  /**
   * @return Returns the precision.
   */
  public int[] getPrecision() {
    return precision;
  }

  /**
   * @param precision The precision to set.
   */
  public void setPrecision( int[] precision ) {
    this.precision = precision;
  }

  /**
   * @return Returns the rename.
   */
  public String[] getRename() {
    return rename;
  }

  /**
   * @param rename The rename to set.
   */
  public void setRename( String[] rename ) {
    this.rename = rename;
  }

  /**
   * @return Returns the type.
   */
  public int[] getType() {
    return type;
  }

  /**
   * @param type The type to set.
   */
  public void setType( int[] type ) {
    this.type = type;
  }

  public boolean[] getScriptResult() {
    return scriptResult;
  }

  public void setScriptResult( boolean[] scriptResult ) {
    this.scriptResult = scriptResult;
  }

  public int getNumberOfScripts() {
    return scripts.length;
  }

  public String[] getScriptNames() {
    String strJSNames[] = new String[scripts.length];
    for ( int i = 0; i < scripts.length; i++ )
      strJSNames[i] = scripts[i].getScriptName();
    return strJSNames;
  }

  public ScriptValuesScript[] getScripts() {
    return scripts;
  }

  public void setScripts( ScriptValuesScript[] scripts ) {
    this.scripts = scripts;
  }

  public void loadXML( Node stepnode, List<DatabaseMeta> databases, Map<String, Counter> counters ) throws KettleXMLException {
    readData( stepnode );
  }

  public void allocate( int nrfields ) {
    fieldname = new String[nrfields];
    rename = new String[nrfields];
    type = new int[nrfields];
    length = new int[nrfields];
    precision = new int[nrfields];
    replace = new boolean[nrfields];
    scriptResult = new boolean[nrfields];
  }

  public Object clone() {
    SuperScriptMeta retval = (SuperScriptMeta) super.clone();

    int nrfields = fieldname.length;

    retval.allocate( nrfields );

    for ( int i = 0; i < nrfields; i++ ) {
      retval.fieldname[i] = fieldname[i];
      retval.rename[i] = rename[i];
      retval.type[i] = type[i];
      retval.length[i] = length[i];
      retval.precision[i] = precision[i];
      retval.replace[i] = replace[i];
      retval.scriptResult[i] = scriptResult[i];
    }

    return retval;
  }

  private void readData( Node stepnode ) throws KettleXMLException {
    try {
      setLanguageName( XMLHandler.getTagValue( stepnode, SCRIPT_LANGUAGE_NAME ) );
      Node scripts = XMLHandler.getSubNode( stepnode, "scripts" );
      int nrscripts = XMLHandler.countNodes( scripts, "script" );
      this.scripts = new ScriptValuesScript[nrscripts];
      for ( int i = 0; i < nrscripts; i++ ) {
        Node fnode = XMLHandler.getSubNodeByNr( scripts, "script", i );

        this.scripts[i] =
          new ScriptValuesScript( Integer.parseInt( XMLHandler.getTagValue( fnode, SCRIPT_TAG_TYPE ) ), XMLHandler
            .getTagValue( fnode, SCRIPT_TAG_NAME ), XMLHandler.getTagValue( fnode, SCRIPT_TAG_SCRIPT ) );
      }

      Node fields = XMLHandler.getSubNode( stepnode, "fields" );
      int nrfields = XMLHandler.countNodes( fields, "field" );

      allocate( nrfields );

      for ( int i = 0; i < nrfields; i++ ) {
        Node fnode = XMLHandler.getSubNodeByNr( fields, "field", i );

        fieldname[i] = XMLHandler.getTagValue( fnode, "name" );
        rename[i] = XMLHandler.getTagValue( fnode, "rename" );
        type[i] = ValueMeta.getType( XMLHandler.getTagValue( fnode, "type" ) );

        String slen = XMLHandler.getTagValue( fnode, "length" );
        String sprc = XMLHandler.getTagValue( fnode, "precision" );
        length[i] = Const.toInt( slen, -1 );
        precision[i] = Const.toInt( sprc, -1 );
        replace[i] = "Y".equalsIgnoreCase( XMLHandler.getTagValue( fnode, "replace" ) );
        scriptResult[i] = "Y".equalsIgnoreCase( XMLHandler.getTagValue( fnode, "scriptResult" ) );
      }
    } catch ( Exception e ) {
      throw new KettleXMLException( BaseMessages.getString( PKG,
        "SuperScriptMeta.Exception.UnableToLoadStepInfoFromXML" ), e );
    }
  }

  public void setDefault() {
    scripts = new ScriptValuesScript[1];
    scripts[0] =
      new ScriptValuesScript( ScriptValuesScript.TRANSFORM_SCRIPT, BaseMessages.getString( PKG,
        "SuperScript.Script1" ), "//" + BaseMessages.getString( PKG, "SuperScript.ScriptHere" ) + Const.CR
        + Const.CR );

    int nrfields = 0;
    allocate( nrfields );

    for ( int i = 0; i < nrfields; i++ ) {
      fieldname[i] = "newvalue";
      rename[i] = "newvalue";
      type[i] = ValueMetaInterface.TYPE_NUMBER;
      length[i] = -1;
      precision[i] = -1;
      replace[i] = false;
      scriptResult[i] = false;
    }
  }

  public void getFields( RowMetaInterface row, String originStepname, RowMetaInterface[] info, StepMeta nextStep,
                         VariableSpace space, Repository repository ) throws KettleStepException {
    for ( int i = 0; i < fieldname.length; i++ ) {
      if ( !Const.isEmpty( fieldname[i] ) ) {
        String fieldName;
        int replaceIndex;
        int fieldType;

        if ( replace[i] ) {
          // Look up the field to replace...
          //
          if ( row.searchValueMeta( fieldname[i] ) == null && Const.isEmpty( rename[i] ) ) {
            throw new KettleStepException( BaseMessages.getString( PKG,
              "SuperScriptMeta.Exception.FieldToReplaceNotFound", fieldname[i] ) );
          }
          replaceIndex = row.indexOfValue( rename[i] );

          // Change the data type to match what's specified...
          //
          fieldType = type[i];
          fieldName = rename[i];
        } else {
          replaceIndex = -1;
          fieldType = type[i];
          if ( rename[i] != null && rename[i].length() != 0 ) {
            fieldName = rename[i];
          } else {
            fieldName = fieldname[i];
          }
        }
        ValueMetaInterface v = new ValueMeta( fieldName, fieldType );
        v.setLength( length[i] );
        v.setPrecision( precision[i] );
        v.setOrigin( originStepname );
        if ( replace[i] && replaceIndex >= 0 ) {
          row.setValueMeta( replaceIndex, v );
        } else {
          row.addValueMeta( v );
        }

      }
    }
  }

  public String getXML() {
    StringBuffer retval = new StringBuffer( 300 );

    retval.append( "    " ).append( XMLHandler.addTagValue( SCRIPT_LANGUAGE_NAME, getLanguageName() ) );

    retval.append( "    <scripts>" );
    for ( int i = 0; i < scripts.length; i++ ) {
      retval.append( "      <script>" );
      retval.append( "        " ).append( XMLHandler.addTagValue( SCRIPT_TAG_TYPE, scripts[i].getScriptType() ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( SCRIPT_TAG_NAME, scripts[i].getScriptName() ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( SCRIPT_TAG_SCRIPT, scripts[i].getScript() ) );
      retval.append( "      </script>" );
    }
    retval.append( "    </scripts>" );

    retval.append( "    <fields>" );
    for ( int i = 0; i < fieldname.length; i++ ) {
      retval.append( "      <field>" );
      retval.append( "        " ).append( XMLHandler.addTagValue( "name", fieldname[i] ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( "rename", rename[i] ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( "type", ValueMeta.getTypeDesc( type[i] ) ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( "length", length[i] ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( "precision", precision[i] ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( "replace", replace[i] ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( "scriptResult", scriptResult[i] ) );
      retval.append( "      </field>" );
    }
    retval.append( "    </fields>" );

    return retval.toString();
  }

  public void readRep( Repository rep, ObjectId id_step, List<DatabaseMeta> databases, Map<String, Counter> counters )
    throws KettleException {
    try {

      setLanguageName( rep.getStepAttributeString( id_step, SCRIPT_LANGUAGE_NAME ) );
      String script = rep.getStepAttributeString( id_step, "script" );

      // When in compatibility mode, we load the compiledScript, not the other tabs...
      //
      if ( !Const.isEmpty( script ) ) {
        scripts = new ScriptValuesScript[1];
        scripts[0] = new ScriptValuesScript( ScriptValuesScript.TRANSFORM_SCRIPT, "ScriptValue", script );
      } else {
        int nrScripts = rep.countNrStepAttributes( id_step, SCRIPT_TAG_NAME );
        scripts = new ScriptValuesScript[nrScripts];
        for ( int i = 0; i < nrScripts; i++ ) {
          scripts[i] =
            new ScriptValuesScript( (int) rep.getStepAttributeInteger( id_step, i, SCRIPT_TAG_TYPE ), rep
              .getStepAttributeString( id_step, i, SCRIPT_TAG_NAME ), rep.getStepAttributeString( id_step, i,
              SCRIPT_TAG_SCRIPT ) );

        }
      }

      int nrfields = rep.countNrStepAttributes( id_step, "field_name" );
      allocate( nrfields );

      for ( int i = 0; i < nrfields; i++ ) {
        fieldname[i] = rep.getStepAttributeString( id_step, i, "field_name" );
        rename[i] = rep.getStepAttributeString( id_step, i, "field_rename" );
        type[i] = ValueMeta.getType( rep.getStepAttributeString( id_step, i, "field_type" ) );
        length[i] = (int) rep.getStepAttributeInteger( id_step, i, "field_length" );
        precision[i] = (int) rep.getStepAttributeInteger( id_step, i, "field_precision" );
        replace[i] = rep.getStepAttributeBoolean( id_step, i, "field_replace" );
        scriptResult[i] = rep.getStepAttributeBoolean( id_step, i, "script_result" );
      }
    } catch ( Exception e ) {
      throw new KettleException( BaseMessages.getString( PKG,
        "SuperScriptMeta.Exception.UnexpectedErrorInReadingStepInfo" ), e );
    }
  }

  public void saveRep( Repository rep, ObjectId id_transformation, ObjectId id_step )
    throws KettleException {
    try {

      rep.saveStepAttribute( id_transformation, id_step, SCRIPT_LANGUAGE_NAME, getLanguageName() );

      for ( int i = 0; i < scripts.length; i++ ) {
        rep.saveStepAttribute( id_transformation, id_step, i, SCRIPT_TAG_NAME, scripts[i].getScriptName() );
        rep.saveStepAttribute( id_transformation, id_step, i, SCRIPT_TAG_SCRIPT, scripts[i].getScript() );
        rep.saveStepAttribute( id_transformation, id_step, i, SCRIPT_TAG_TYPE, scripts[i].getScriptType() );
      }

      for ( int i = 0; i < fieldname.length; i++ ) {
        rep.saveStepAttribute( id_transformation, id_step, i, "field_name", fieldname[i] );
        rep.saveStepAttribute( id_transformation, id_step, i, "field_rename", rename[i] );
        rep.saveStepAttribute( id_transformation, id_step, i, "field_type", ValueMeta.getTypeDesc( type[i] ) );
        rep.saveStepAttribute( id_transformation, id_step, i, "field_length", length[i] );
        rep.saveStepAttribute( id_transformation, id_step, i, "field_precision", precision[i] );
        rep.saveStepAttribute( id_transformation, id_step, i, "field_replace", replace[i] );
        rep.saveStepAttribute( id_transformation, id_step, i, "script_result", scriptResult[i] );
      }
    } catch ( Exception e ) {
      throw new KettleException( BaseMessages.getString( PKG, "SuperScriptMeta.Exception.UnableToSaveStepInfo" )
        + id_step, e );
    }
  }

  public void check(
    List<CheckResultInterface> remarks, TransMeta transMeta, StepMeta stepMeta, RowMetaInterface prev,
    String input[], String output[], RowMetaInterface info ) {

    boolean error_found = false;
    String error_message = "";
    CheckResult cr;

    ScriptEngine jscx;
    Bindings jsscope;
    CompiledScript jsscript;

    jscx = ScriptUtils.createNewScriptEngineByLanguage( getLanguageName() );
    jsscope = jscx.getBindings( ScriptContext.ENGINE_SCOPE );

    // String strActiveScriptName="";
    String strActiveStartScriptName = "";
    String strActiveEndScriptName = "";

    String strActiveScript = "";
    String strActiveStartScript = "";
    String strActiveEndScript = "";

    // Building the Scripts
    if ( scripts.length > 0 ) {
      for ( int i = 0; i < scripts.length; i++ ) {
        if ( scripts[i].isTransformScript() ) {
          // strActiveScriptName =scripts[i].getScriptName();
          strActiveScript = scripts[i].getScript();
        } else if ( scripts[i].isStartScript() ) {
          strActiveStartScriptName = scripts[i].getScriptName();
          strActiveStartScript = scripts[i].getScript();
        } else if ( scripts[i].isEndScript() ) {
          strActiveEndScriptName = scripts[i].getScriptName();
          strActiveEndScript = scripts[i].getScript();
        }
      }
    }

    if ( prev != null && strActiveScript.length() > 0 ) {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString( PKG,
          "SuperScriptMeta.CheckResult.ConnectedStepOK", String.valueOf( prev.size() ) ), stepMeta );
      remarks.add( cr );

      // Adding the existing Scripts to the Context
      for ( int i = 0; i < getNumberOfScripts(); i++ ) {
        jsscope.put( scripts[i].getScriptName(), scripts[i].getScript() );
      }

      // Modification for Additional SuperScript parsing
      try {
        if ( getAddClasses() != null ) {
          for ( int i = 0; i < getAddClasses().length; i++ ) {
            // TODO AKRETION ensure it works
            jsscope.put( getAddClasses()[i].getScriptName(), getAddClasses()[i].getAddObject() );
            // Object jsOut = Context.javaToJS(getAddClasses()[i].getAddObject(), jsscope);
            // ScriptableObject.putProperty(jsscope, getAddClasses()[i].getScriptName(), jsOut);
            // ScriptableObject.putProperty(jsscope, getAddClasses()[i].getScriptName(), jsOut);
          }
        }
      } catch ( Exception e ) {
        error_message = ( "Couldn't add JavaClasses to Context! Error:" );
        cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta );
        remarks.add( cr );
      }

      // Adding some Constants to the compiledScript
      try {
        jsscope.put( "SKIP_TRANSFORMATION", Integer.valueOf( SuperScript.SKIP_TRANSFORMATION ) );
        jsscope.put( "ABORT_TRANSFORMATION", Integer.valueOf( SuperScript.ABORT_TRANSFORMATION ) );
        jsscope.put( "ERROR_TRANSFORMATION", Integer.valueOf( SuperScript.ERROR_TRANSFORMATION ) );
        jsscope.put( "CONTINUE_TRANSFORMATION", Integer.valueOf( SuperScript.CONTINUE_TRANSFORMATION ) );
      } catch ( Exception ex ) {
        error_message = "Couldn't add Transformation Constants! Error:" + Const.CR + ex.toString();
        cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta );
        remarks.add( cr );
      }

      try {
        ScriptDummy dummyStep = new ScriptDummy( prev, transMeta.getStepFields( stepMeta ) );
        jsscope.put( "_step_", dummyStep );

        Object[] row = new Object[prev.size()];
        jsscope.put( "rowMeta", prev );
        for ( int i = 0; i < prev.size(); i++ ) {
          ValueMetaInterface valueMeta = prev.getValueMeta( i );
          Object valueData = null;

          // Set date and string values to something to simulate real thing
          //
          if ( valueMeta.isDate() ) {
            valueData = new Date();
          }
          if ( valueMeta.isString() ) {
            valueData =
              "test value test value test value test value test value test value test value test value test value test value";
          }
          if ( valueMeta.isInteger() ) {
            valueData = Long.valueOf( 0L );
          }
          if ( valueMeta.isNumber() ) {
            valueData = new Double( 0.0 );
          }
          if ( valueMeta.isBigNumber() ) {
            valueData = BigDecimal.ZERO;
          }
          if ( valueMeta.isBoolean() ) {
            valueData = Boolean.TRUE;
          }
          if ( valueMeta.isBinary() ) {
            valueData = new byte[]{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, };
          }

          row[i] = valueData;

          jsscope.put( valueMeta.getName(), valueData );
        }
        // Add support for Value class (new Value())
        jsscope.put( "Value", Value.class );

        // Add the old style row object for compatibility reasons...
        //
        jsscope.put( "row", row );
      } catch ( Exception ev ) {
        error_message = "Couldn't add Input fields to SuperScript! Error:" + Const.CR + ev.toString();
        cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta );
        remarks.add( cr );
      }

      try {
        // Checking for StartScript
        if ( strActiveStartScript != null && strActiveStartScript.length() > 0 ) {
          jscx.eval( strActiveStartScript, jsscope );
          error_message = "Found Start SuperScript. " + strActiveStartScriptName + " Processing OK";
          cr = new CheckResult( CheckResultInterface.TYPE_RESULT_OK, error_message, stepMeta );
          remarks.add( cr );
        }
      } catch ( Exception e ) {
        error_message = "Couldn't process Start SuperScript! Error:" + Const.CR + e.toString();
        cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta );
        remarks.add( cr );
      }

      try {
        jsscript = ( (Compilable) jscx ).compile( strActiveScript );

        // cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(PKG,
        // "SuperScriptMeta.CheckResult.ScriptCompiledOK"), stepinfo);
        // remarks.add(cr);

        try {

          jsscript.eval( jsscope );

          cr =
            new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString( PKG,
              "SuperScriptMeta.CheckResult.ScriptCompiledOK2" ), stepMeta );
          remarks.add( cr );

          if ( fieldname.length > 0 ) {
            StringBuffer message =
              new StringBuffer( BaseMessages.getString( PKG, "SuperScriptMeta.CheckResult.FailedToGetValues",
                String.valueOf( fieldname.length ) )
                + Const.CR + Const.CR );

            if ( error_found ) {
              cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, message.toString(), stepMeta );
            } else {
              cr = new CheckResult( CheckResultInterface.TYPE_RESULT_OK, message.toString(), stepMeta );
            }
            remarks.add( cr );
          }
        } catch ( ScriptException jse ) {
          // Context.exit(); TODO AKRETION NOT SURE
          error_message =
            BaseMessages.getString( PKG, "SuperScriptMeta.CheckResult.CouldNotExecuteScript" ) + Const.CR
              + jse.toString();
          cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta );
          remarks.add( cr );
        } catch ( Exception e ) {
          // Context.exit(); TODO AKRETION NOT SURE
          error_message =
            BaseMessages.getString( PKG, "SuperScriptMeta.CheckResult.CouldNotExecuteScript2" ) + Const.CR
              + e.toString();
          cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta );
          remarks.add( cr );
        }

        // Checking End SuperScript
        try {
          if ( strActiveEndScript != null && strActiveEndScript.length() > 0 ) {
            /* Object endScript = */
            jscx.eval( strActiveEndScript, jsscope );
            error_message = "Found End SuperScript. " + strActiveEndScriptName + " Processing OK";
            cr = new CheckResult( CheckResultInterface.TYPE_RESULT_OK, error_message, stepMeta );
            remarks.add( cr );
          }
        } catch ( Exception e ) {
          error_message = "Couldn't process End SuperScript! Error:" + Const.CR + e.toString();
          cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta );
          remarks.add( cr );
        }
      } catch ( Exception e ) {
        // Context.exit(); TODO AKRETION NOT SURE
        error_message =
          BaseMessages.getString( PKG, "SuperScriptMeta.CheckResult.CouldNotCompileScript" ) + Const.CR
            + e.toString();
        cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta );
        remarks.add( cr );
      }
    } else {
      // Context.exit(); TODO AKRETION NOT SURE
      error_message = BaseMessages.getString( PKG, "SuperScriptMeta.CheckResult.CouldNotGetFieldsFromPreviousStep" );
      cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta );
      remarks.add( cr );
    }

    // See if we have input streams leading to this step!
    if ( input.length > 0 ) {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString( PKG,
          "SuperScriptMeta.CheckResult.ConnectedStepOK2" ), stepMeta );
      remarks.add( cr );
    } else {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString( PKG,
          "SuperScriptMeta.CheckResult.NoInputReceived" ), stepMeta );
      remarks.add( cr );
    }
  }

  public String getFunctionFromScript( String strFunction, String strScript ) {
    String sRC = "";
    int iStartPos = strScript.indexOf( strFunction );
    if ( iStartPos > 0 ) {
      iStartPos = strScript.indexOf( '{', iStartPos );
      int iCounter = 1;
      while ( iCounter != 0 ) {
        if ( strScript.charAt( iStartPos++ ) == '{' ) {
          iCounter++;
        } else if ( strScript.charAt( iStartPos++ ) == '}' ) {
          iCounter--;
        }
        sRC = sRC + strScript.charAt( iStartPos );
      }
    }
    return sRC;
  }

  public boolean getValue( Bindings scope, int i, Value res, StringBuffer message ) {
    boolean error_found = false;

    if ( fieldname[i] != null && fieldname[i].length() > 0 ) {
      res.setName( rename[i] );
      res.setType( type[i] );

      try {

        Object result = scope.get( fieldname[i] );
        if ( result != null ) {

          String classname = result.getClass().getName();

          switch ( type[i] ) {
            case ValueMetaInterface.TYPE_NUMBER:
              if ( classname.equalsIgnoreCase( "org.mozilla.javascript.Undefined" ) ) {
                res.setNull();
              } else if ( classname.equalsIgnoreCase( "org.mozilla.javascript.NativeJavaObject" ) ) {
                // Is it a java Value class ?
                Value v = (Value) result;
                res.setValue( v.getNumber() );
              } else {
                res.setValue( ( (Double) result ).doubleValue() );
              }
              break;
            case ValueMetaInterface.TYPE_INTEGER:
              if ( classname.equalsIgnoreCase( "java.lang.Byte" ) ) {
                res.setValue( ( (java.lang.Byte) result ).longValue() );
              } else if ( classname.equalsIgnoreCase( "java.lang.Short" ) ) {
                res.setValue( ( (Short) result ).longValue() );
              } else if ( classname.equalsIgnoreCase( "java.lang.Integer" ) ) {
                res.setValue( ( (Integer) result ).longValue() );
              } else if ( classname.equalsIgnoreCase( "java.lang.Long" ) ) {
                res.setValue( ( (Long) result ).longValue() );
              } else if ( classname.equalsIgnoreCase( "org.mozilla.javascript.Undefined" ) ) {
                res.setNull();
              } else if ( classname.equalsIgnoreCase( "org.mozilla.javascript.NativeJavaObject" ) ) {
                // Is it a java Value class ?
                Value v = (Value) result;
                res.setValue( v.getInteger() );
              } else {
                res.setValue( Math.round( ( (Double) result ).doubleValue() ) );
              }
              break;
            case ValueMetaInterface.TYPE_STRING:
              if ( classname.equalsIgnoreCase( "org.mozilla.javascript.NativeJavaObject" )
                || classname.equalsIgnoreCase( "org.mozilla.javascript.Undefined" ) ) {
                // Is it a java Value class ?
                try {
                  Value v = (Value) result;
                  res.setValue( v.getString() );
                } catch ( Exception ev ) {
                  // A String perhaps?
                  String s = (String) result;
                  res.setValue( s );
                }
              } else {
                res.setValue( ( (String) result ) );
              }
              break;
            case ValueMetaInterface.TYPE_DATE:
              double dbl = 0;
              if ( classname.equalsIgnoreCase( "org.mozilla.javascript.Undefined" ) ) {
                res.setNull();
              } else {
                if ( classname.equalsIgnoreCase( "org.mozilla.javascript.NativeDate" ) ) {
                  dbl = (Double) result;// TODO AKRETION not sure!
                } else if ( classname.equalsIgnoreCase( "org.mozilla.javascript.NativeJavaObject" ) ) {
                  // Is it a java Date() class ?
                  try {
                    Date dat = (Date) result;
                    dbl = dat.getTime();
                  } catch ( Exception e ) // Nope, try a Value
                  {
                    Value v = (Value) result;
                    Date dat = v.getDate();
                    if ( dat != null ) {
                      dbl = dat.getTime();
                    } else {
                      res.setNull();
                    }
                  }
                } else // Finally, try a number conversion to time
                {
                  dbl = ( (Double) result ).doubleValue();
                }
                long lng = Math.round( dbl );
                Date dat = new Date( lng );
                res.setValue( dat );
              }
              break;
            case ValueMetaInterface.TYPE_BOOLEAN:
              res.setValue( ( (Boolean) result ).booleanValue() );
              break;
            default:
              res.setNull();
          }
        } else {
          res.setNull();
        }
      } catch ( Exception e ) {
        message.append( BaseMessages
          .getString( PKG, "SuperScriptMeta.CheckResult.ErrorRetrievingValue", fieldname[i] )
          + " : " + e.toString() );
        error_found = true;
      }
      res.setLength( length[i], precision[i] );

      message.append( BaseMessages.getString( PKG, "SuperScriptMeta.CheckResult.RetrievedValue", fieldname[i], res
        .toStringMeta() ) );
    } else {
      message.append( BaseMessages.getString( PKG, "SuperScriptMeta.CheckResult.ValueIsEmpty", String.valueOf( i ) ) );
      error_found = true;
    }

    return error_found;
  }

  public StepInterface getStep( StepMeta stepMeta, StepDataInterface stepDataInterface, int cnr, TransMeta transMeta,
                                Trans trans ) {
    return new SuperScript( stepMeta, stepDataInterface, cnr, transMeta, trans );
  }

  public StepDataInterface getStepData() {
    return new SuperScriptData();
  }

  private static Class<?> LoadAdditionalClass( String strJar, String strClassName ) throws KettleException {
    try {
      Thread t = Thread.currentThread();
      ClassLoader cl = t.getContextClassLoader();
      URL u = new URL( "jar:file:" + strJar + "!/" );
      KettleURLClassLoader kl = new KettleURLClassLoader( new URL[]{ u }, cl );
      Class<?> toRun = kl.loadClass( strClassName );
      return toRun;
    } catch ( Exception e ) {
      throw new KettleException( BaseMessages
        .getString( PKG, "SuperScriptMeta.Exception.UnableToLoadAdditionalClass" ), e );
    }
  }

  public ScriptAddClasses[] getAddClasses() {
    return additionalClasses;
  }

  public boolean supportsErrorHandling() {
    return true;
  }

  /**
   * @return the replace
   */
  public boolean[] getReplace() {
    return replace;
  }

  /**
   * @param replace the replace to set
   */
  public void setReplace( boolean[] replace ) {
    this.replace = replace;
  }

  public String getLanguageName() {
    return languageName;
  }

  public void setLanguageName( String languageName ) {
    this.languageName = languageName;
  }

}
