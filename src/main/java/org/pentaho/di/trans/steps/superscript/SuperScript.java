/*******************************************************************************
 *
 * Copyright (C) 2014 by Matt Burgess
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
import java.util.Date;

import javax.script.*;

import org.pentaho.di.compatibility.Value;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleValueException;
import org.pentaho.di.core.row.*;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;

/**
 * Executes a JSR-223 compiledScript on the values in the input stream. Selected calculated values can then be put on the output
 * stream.
 *
 * @author Matt Burgess
 */
public class SuperScript extends BaseStep implements StepInterface {
  private static Class<?> PKG = SuperScriptMeta.class; // for i18n purposes, needed by Translator2!!

  private SuperScriptMeta meta;

  private SuperScriptData data;

  public final static int SKIP_TRANSFORMATION = 1;

  public final static int ABORT_TRANSFORMATION = -1;

  public final static int ERROR_TRANSFORMATION = -2;

  public final static int CONTINUE_TRANSFORMATION = 0;

  private boolean bWithTransStat = false;

  private boolean bRC = false;

  private int iTranStat = CONTINUE_TRANSFORMATION;

  private boolean bFirstRun = false;

  private int rownr = 0;

  private ScriptValuesScript[] scripts;

  private String strTransformScript = "";

  private String strStartScript = "";

  private String strEndScript = "";

  Bindings bindings;

  private Object[] lastRow = null;

  public SuperScript( StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, TransMeta transMeta,
                      Trans trans ) {
    super( stepMeta, stepDataInterface, copyNr, transMeta, trans );
  }

  private void determineUsedFields( RowMetaInterface row ) {
    if ( row == null ) {
      return;
    }

    int nr = 0;
    // Count the occurrences of the values.
    // Perhaps we find values in comments, but we take no risk!
    //
    for ( int i = 0; i < row.size(); i++ ) {
      String valname = row.getValueMeta( i ).getName().toUpperCase();
      if ( strTransformScript.toUpperCase().indexOf( valname ) >= 0 ) {
        nr++;
      }
    }

    // Allocate fields_used
    data.fields_used = new int[nr];
    data.values_used = new Value[nr];

    nr = 0;
    // Count the occurrences of the values.
    // Perhaps we find values in comments, but we take no risk!
    //
    for ( int i = 0; i < row.size(); i++ ) {
      // Values are case-insensitive in JavaScript.
      //
      String valname = row.getValueMeta( i ).getName();
      if ( strTransformScript.indexOf( valname ) >= 0 ) {
        if ( log.isDetailed() ) {
          logDetailed( BaseMessages.getString( PKG, "SuperScript.Log.UsedValueName", String.valueOf( i ), valname ) ); //$NON-NLS-3$
        }
        data.fields_used[nr] = i;
        nr++;
      }
    }

    if ( log.isDetailed() ) {
      logDetailed( BaseMessages.getString( PKG, "SuperScript.Log.UsingValuesFromInputStream", String
        .valueOf( data.fields_used.length ) ) );
    }
  }

  private boolean addValues( RowMetaInterface rowMeta, Object[] row ) throws KettleException {
    if ( first ) {
      first = false;

      if ( rowMeta == null ) {
        rowMeta = new RowMeta();
      }
      data.outputRowMeta = rowMeta.clone();
      meta.getFields( data.outputRowMeta, getStepname(), null, null, this );

      // Determine the indexes of the fields used!
      //
      determineUsedFields( rowMeta );

      // Get the indexes of the replaced fields...
      //
      data.replaceIndex = new int[meta.getFieldname().length];
      for ( int i = 0; i < meta.getFieldname().length; i++ ) {
        if ( meta.getReplace()[i] ) {
          data.replaceIndex[i] = rowMeta.indexOfValue( meta.getFieldname()[i] );
          if ( data.replaceIndex[i] < 0 ) {
            if ( Const.isEmpty( meta.getFieldname()[i] ) ) {
              throw new KettleStepException( BaseMessages.getString( PKG,
                "ScriptValuesMetaMod.Exception.FieldToReplaceNotFound", meta.getFieldname()[i] ) );
            }
            data.replaceIndex[i] = rowMeta.indexOfValue( meta.getRename()[i] );
            if ( data.replaceIndex[i] < 0 ) {
              throw new KettleStepException( BaseMessages.getString( PKG,
                "ScriptValuesMetaMod.Exception.FieldToReplaceNotFound", meta.getRename()[i] ) );
            }
          }
        } else {
          data.replaceIndex[i] = -1;
        }
      }

      data.context = data.engine.getContext();
      if ( data.context == null ) {
        data.context = new SimpleScriptContext();
      }
      bindings = data.engine.getBindings( ScriptContext.ENGINE_SCOPE );

      bFirstRun = true;

      bindings.put( "step", this );

      // Adding the existing Scripts to the Context
      //
      for ( int i = 0; i < meta.getNumberOfScripts(); i++ ) {
        bindings.put( scripts[i].getScriptName(), scripts[i].getScript() );
      }

      // Adding the Name of the Transformation to the Context
      //
      bindings.put( "stepName", this.getStepname() );
      bindings.put( "transName", this.getTrans().getName() );

      try {
        try {
          bindings.put( "row", row );
          bindings.put( "lastRow", lastRow );

          // also add the meta information for the whole row
          //
          bindings.put( "rowMeta", rowMeta );

          // Add the used fields...
          //
          if ( data.fields_used != null ) {
            for ( int i = 0; i < data.fields_used.length; i++ ) {
              ValueMetaInterface valueMeta = rowMeta.getValueMeta( data.fields_used[i] );
              Object valueData = row[data.fields_used[i]];

              Object normalStorageValueData = valueMeta.convertToNormalStorageType( valueData );
              bindings.put( valueMeta.getName(), normalStorageValueData );
            }
          }

        } catch ( Throwable t ) {
          logError( BaseMessages.getString( PKG, "SuperScript.Exception.ErrorSettingVariable" ), t );
        }

        // Modification for Additional SuperScript parsing
        //
        try {
          if ( meta.getAddClasses() != null ) {
            for ( int i = 0; i < meta.getAddClasses().length; i++ ) {
              bindings.put( meta.getAddClasses()[i].getScriptName(), meta.getAddClasses()[i].getAddObject() );
            }
          }
        } catch ( Exception e ) {
          throw new KettleValueException( BaseMessages.getString( PKG,
            "SuperScript.Log.CouldNotAttachAdditionalScripts" ), e );
        }

        // Adding some Constants to the compiledScript
        try {

          bindings.put( "SKIP_TRANSFORMATION", Integer.valueOf( SKIP_TRANSFORMATION ) );
          bindings.put( "ABORT_TRANSFORMATION", Integer.valueOf( ABORT_TRANSFORMATION ) );
          bindings.put( "ERROR_TRANSFORMATION", Integer.valueOf( ERROR_TRANSFORMATION ) );
          bindings.put( "CONTINUE_TRANSFORMATION", Integer.valueOf( CONTINUE_TRANSFORMATION ) );

        } catch ( Exception ex ) {
          throw new KettleValueException(
            BaseMessages.getString( PKG, "SuperScript.Log.CouldNotAddDefaultConstants" ), ex );
        }

        try {
          // Checking for StartScript
          if ( strStartScript != null && strStartScript.length() > 0 ) {
            if ( log.isDetailed() ) {
              logDetailed( ( "Start compiledScript found!" ) );
            }
            if ( data.engine instanceof Compilable ) {
              CompiledScript startScript = ( (Compilable) data.engine ).compile( strStartScript );
              startScript.eval( bindings );
            } else {
              // Can't compile beforehand, so just eval it
              data.engine.eval( strStartScript );
            }

          } else {
            if ( log.isDetailed() ) {
              logDetailed( ( "No starting compiledScript found!" ) );
            }
          }
        } catch ( Exception es ) {
          throw new KettleValueException(
            BaseMessages.getString( PKG, "SuperScript.Log.ErrorProcessingStartScript" ), es );

        }

        data.rawScript = strTransformScript;
        // Now Compile our SuperScript if supported by the engine
        if ( data.engine instanceof Compilable ) {
          data.compiledScript = ( (Compilable) data.engine ).compile( strTransformScript );

        } else {
          data.compiledScript = null;
        }
      } catch ( Exception e ) {
        throw new KettleValueException( BaseMessages.getString( PKG, "SuperScript.Log.CouldNotCompileScript" ), e );
      }
    }

    bindings.put( "rowNumber", ++rownr );

    // Filling the defined TranVars with the Values from the Row
    //
    Object[] outputRow = RowDataUtil.resizeArray( row, data.outputRowMeta.size() );

    // Keep an index...
    int outputIndex = rowMeta == null ? 0 : rowMeta.size();

    try {
      try {
        bindings.put( "row", row );

        // Try to add the last row's data (null or not)
        try {
          bindings.put( "lastRow", lastRow );
        } catch ( Throwable t ) {
          logError( BaseMessages.getString( PKG, "SuperScript.Exception.ErrorSettingVariable", "lastRow" ), t );
        }

        for ( int i = 0; i < data.fields_used.length; i++ ) {
          ValueMetaInterface valueMeta = rowMeta.getValueMeta( data.fields_used[i] );
          Object valueData = row[data.fields_used[i]];

          Object normalStorageValueData = valueMeta.convertToNormalStorageType( valueData );

          bindings.put( valueMeta.getName(), normalStorageValueData );
        }

        // also add the meta information for the whole row
        //
        bindings.put( "rowMeta", rowMeta );
      } catch ( Exception e ) {
        throw new KettleValueException( BaseMessages.getString( PKG, "SuperScript.Log.UnexpectedError" ), e );
      }

      Object scriptResult = evalScript();

      if ( bFirstRun ) {
        bFirstRun = false;
        // Check if we had a Transformation Status
        Object tran_stat = bindings.get( "trans_Status" );
        if ( tran_stat != null ) {
          bWithTransStat = true;
          if ( log.isDetailed() ) {
            logDetailed( ( "trans_Status found. Checking transformation status while compiledScript execution." ) );
          }
        } else {
          if ( log.isDetailed() ) {
            logDetailed( ( "No trans_Status found. Transformation status checking not available." ) );
          }
          bWithTransStat = false;
        }
      }

      iTranStat = CONTINUE_TRANSFORMATION;
      if ( bWithTransStat ) {
        Object tran_stat = bindings.get( "trans_Status" );
        if ( Integer.class.isAssignableFrom( tran_stat.getClass() ) ) {
          iTranStat = (Integer) tran_stat;
        }
      }

      if ( iTranStat == CONTINUE_TRANSFORMATION ) {
        bRC = true;
        for ( int i = 0; i < meta.getFieldname().length; i++ ) {
          Object result = bindings.get( meta.getFieldname()[i] );
          Object valueData = getValueFromScript(
            meta.getScriptResult()[i] ? scriptResult : result,
            i
          );
          if ( data.replaceIndex[i] < 0 ) {
            outputRow[outputIndex++] = valueData;
          } else {
            outputRow[data.replaceIndex[i]] = valueData;
          }
        }

        putRow( data.outputRowMeta, outputRow );
      } else {
        switch ( iTranStat ) {
          case SKIP_TRANSFORMATION:
            // eat this row.
            bRC = true;
            break;
          case ABORT_TRANSFORMATION:
            if ( data.engine != null )
            // Context.exit(); TODO AKRETION not sure
            {
              stopAll();
            }
            setOutputDone();
            bRC = false;
            break;
          case ERROR_TRANSFORMATION:
            if ( data.engine != null )
            // Context.exit(); TODO AKRETION not sure
            {
              setErrors( 1 );
            }
            stopAll();
            bRC = false;
            break;
          default:
            break;
        }

        // TODO: kick this "ERROR handling" junk out now that we have
        // solid error handling in place.
        //
      }
    } catch ( ScriptException e ) {
      throw new KettleValueException( BaseMessages.getString( PKG, "SuperScript.Log.SuperScriptError" ), e );
    }
    return bRC;
  }

  protected Object evalScript() throws ScriptException {
    if ( data.compiledScript != null ) {
      try {
        return data.compiledScript.eval( data.context );
      } catch ( UnsupportedOperationException uoe ) {
        // The script engine might not support eval with script context, so try just the Bindings instead
        return data.compiledScript.eval( bindings );
      }

    } else if ( data.engine != null && data.rawScript != null ) {
      try {
        return data.engine.eval( data.rawScript, data.context );
      } catch ( UnsupportedOperationException uoe ) {
        // The script engine might not support eval with script context, so try just the Bindings instead
        return data.engine.eval( data.rawScript, bindings );
      }
    }
    return null;
  }

  public Object getValueFromScript( Object result, int i ) throws KettleValueException {
    if ( meta.getFieldname()[i] != null && meta.getFieldname()[i].length() > 0 ) {
      // res.setName(meta.getRename()[i]);
      // res.setType(meta.getType()[i]);

      try {
        if ( result != null ) {
          String classType = result.getClass().getName();
          switch ( meta.getType()[i] ) {
            case ValueMetaInterface.TYPE_NUMBER:
              if ( classType.equalsIgnoreCase( "org.mozilla.javascript.Undefined" ) ) {
                return null;
              } else if ( classType.equalsIgnoreCase( "org.mozilla.javascript.NativeJavaObject" ) ) {
                try {
                  // Is it a java Value class ?
                  Value v = (Value) result;
                  return v.getNumber();
                } catch ( Exception e ) {
                  String string = (String) result;
                  return new Double( Double.parseDouble( Const.trim( string ) ) );
                }
              } else if ( classType.equalsIgnoreCase( "org.mozilla.javascript.NativeNumber" ) ) {
                Number nb = (Number) result;
                return new Double( nb.doubleValue() );// TODO AKRETION
                // not sure
              } else if ( Number.class.isAssignableFrom( result.getClass() ) ) {
                Number nb = (Number) result;
                return new Double( nb.doubleValue() );
              } else {
                // Last resort, try to parse from toString()
                return Double.parseDouble( result.toString() );
              }

            case ValueMetaInterface.TYPE_INTEGER:
              if ( classType.equalsIgnoreCase( "java.lang.Byte" ) ) {
                return new Long( ( (java.lang.Byte) result ).longValue() );
              } else if ( classType.equalsIgnoreCase( "java.lang.Short" ) ) {
                return new Long( ( (Short) result ).longValue() );
              } else if ( classType.equalsIgnoreCase( "java.lang.Integer" ) ) {
                return new Long( ( (Integer) result ).longValue() );
              } else if ( classType.equalsIgnoreCase( "java.lang.Long" ) ) {
                return new Long( ( (Long) result ).longValue() );
              } else if ( classType.equalsIgnoreCase( "java.lang.Double" ) ) {
                return new Long( ( (Double) result ).longValue() );
              } else if ( classType.equalsIgnoreCase( "java.lang.String" ) ) {
                return new Long( ( new Long( (String) result ) ).longValue() );
              } else if ( classType.equalsIgnoreCase( "org.mozilla.javascript.Undefined" ) ) {
                return null;
              } else if ( classType.equalsIgnoreCase( "org.mozilla.javascript.NativeNumber" ) ) {
                Number nb = (Number) result;
                return new Long( nb.longValue() );
              } else if ( classType.equalsIgnoreCase( "org.mozilla.javascript.NativeJavaObject" ) ) {
                try {
                  Value value = (Value) result;
                  return value.getInteger();
                } catch ( Exception e2 ) {
                  String string = (String) result;
                  return new Long( Long.parseLong( Const.trim( string ) ) );
                }
              } else {
                return Long.valueOf( Long.parseLong( result.toString() ) );
              }

            case ValueMetaInterface.TYPE_STRING:
              if ( classType.equalsIgnoreCase( "org.mozilla.javascript.NativeJavaObject" )
                || classType.equalsIgnoreCase( "org.mozilla.javascript.Undefined" ) ) {
                // Is it a java Value class ?
                try {
                  Value v = (Value) result;
                  return v.toString();
                } catch ( Exception ev ) {
                  // convert to a string should work in most
                  // cases...
                  //
                  String string = (String) result;
                  return string;
                }
              } else {
                // A String perhaps?
                String string = (String) result;
                return string;
              }

            case ValueMetaInterface.TYPE_DATE:
              double dbl = 0;
              if ( classType.equalsIgnoreCase( "org.mozilla.javascript.Undefined" ) ) {
                return null;
              } else {
                if ( classType.equalsIgnoreCase( "org.mozilla.javascript.NativeDate" ) ) {
                  dbl = (Double) result;// TODO AKRETION not sure
                } else if ( classType.equalsIgnoreCase( "org.mozilla.javascript.NativeJavaObject" )
                  || classType.equalsIgnoreCase( "java.util.Date" ) ) {
                  // Is it a java Date() class ?
                  try {
                    Date dat = (Date) result;
                    dbl = dat.getTime();
                  } catch ( Exception e ) {
                    // Is it a Value?
                    //
                    try {
                      Value value = (Value) result;
                      return value.getDate();
                    } catch ( Exception e2 ) {
                      try {
                        String string = (String) result;
                        return XMLHandler.stringToDate( string );
                      } catch ( Exception e3 ) {
                        throw new KettleValueException( "Can't convert a string to a date" );
                      }
                    }
                  }
                } else if ( classType.equalsIgnoreCase( "java.lang.Double" ) ) {
                  dbl = ( (Double) result ).doubleValue();
                } else {
                  String string = (String) result;
                  dbl = Double.parseDouble( string );
                }
                long lng = Math.round( dbl );
                Date dat = new Date( lng );
                return dat;
              }

            case ValueMetaInterface.TYPE_BOOLEAN:
              return result;

            case ValueMetaInterface.TYPE_BIGNUMBER:
              if ( classType.equalsIgnoreCase( "org.mozilla.javascript.Undefined" ) ) {
                return null;
              } else if ( classType.equalsIgnoreCase( "org.mozilla.javascript.NativeNumber" ) ) {
                Number nb = (Number) result;// TODO AKRETION not
                // sure
                return new BigDecimal( nb.longValue() );
              } else if ( classType.equalsIgnoreCase( "org.mozilla.javascript.NativeJavaObject" ) ) {
                // Is it a BigDecimal class ?
                try {
                  BigDecimal bd = (BigDecimal) result;
                  return bd;
                } catch ( Exception e ) {
                  try {
                    Value v = (Value) result;
                    if ( !v.isNull() ) {
                      return v.getBigNumber();
                    } else {
                      return null;
                    }
                  } catch ( Exception e2 ) {
                    String string = (String) result;
                    return new BigDecimal( string );
                  }
                }
              } else if ( classType.equalsIgnoreCase( "java.lang.Byte" ) ) {
                return new BigDecimal( ( (java.lang.Byte) result ).longValue() );
              } else if ( classType.equalsIgnoreCase( "java.lang.Short" ) ) {
                return new BigDecimal( ( (Short) result ).longValue() );
              } else if ( classType.equalsIgnoreCase( "java.lang.Integer" ) ) {
                return new BigDecimal( ( (Integer) result ).longValue() );
              } else if ( classType.equalsIgnoreCase( "java.lang.Long" ) ) {
                return new BigDecimal( ( (Long) result ).longValue() );
              } else if ( classType.equalsIgnoreCase( "java.lang.Double" ) ) {
                return new BigDecimal( ( (Double) result ).longValue() );
              } else if ( classType.equalsIgnoreCase( "java.lang.String" ) ) {
                return new BigDecimal( ( new Long( (String) result ) ).longValue() );
              } else {
                throw new RuntimeException( "JavaScript conversion to BigNumber not implemented for " + classType );
              }

            case ValueMetaInterface.TYPE_BINARY: {
              return result;// TODO AKRETION not sure
              // //Context.jsToJava(result,
              // byte[].class);
            }
            case ValueMetaInterface.TYPE_NONE: {
              throw new RuntimeException( "No data output data type was specified for new field ["
                + meta.getFieldname()[i] + "]" );
            }
            default: {
              throw new RuntimeException( "JavaScript conversion not implemented for type " + meta.getType()[i] + " ("
                + ValueMeta.getTypeDesc( meta.getType()[i] ) + ")" );
            }
          }
        } else {
          return null;
        }
      } catch ( Exception e ) {
        throw new KettleValueException( BaseMessages.getString( PKG, "SuperScript.Log.ScriptError" ), e );
      }
    } else {
      throw new KettleValueException( "No name was specified for result value #" + ( i + 1 ) );
    }
  }

  public RowMetaInterface getOutputRowMeta() {
    return data.outputRowMeta;
  }

  public boolean processRow( StepMetaInterface smi, StepDataInterface sdi ) throws KettleException {

    meta = (SuperScriptMeta) smi;
    data = (SuperScriptData) sdi;

    Object[] r = getRow(); // Get row from input rowset & set row busy!
    if ( r == null && !first ) {
      // Modification for Additional End Function
      try {
        if ( data.engine != null ) {

          // Run the start and transformation scripts once if there are no incoming rows

          // Checking for EndScript
          if ( strEndScript != null && strEndScript.length() > 0 ) {
            data.engine.eval( strEndScript, bindings );
            if ( log.isDetailed() ) {
              logDetailed( ( "End Script found!" ) );
            }
          } else {
            if ( log.isDetailed() ) {
              logDetailed( ( "No end Script found!" ) );
            }
          }
        }
      } catch ( Exception e ) {
        logError( BaseMessages.getString( PKG, "SuperScript.Log.UnexpectedError" ) + " : " + e.toString() );
        logError( BaseMessages.getString( PKG, "SuperScript.Log.ErrorStackTrace" ) + Const.CR
          + Const.getStackTracker( e ) );
        setErrors( 1 );
        stopAll();
      }

      if ( data.engine != null ) {
        setOutputDone();
      }
      return false;
    }

    // Getting the Row, with the Transformation Status
    try {
      addValues( getInputRowMeta(), r );
    } catch ( KettleValueException e ) {
      String location = "<unknown>";
      if ( e.getCause() instanceof ScriptException ) {
        ScriptException ee = (ScriptException) e.getCause();
        location = "--> " + ee.getLineNumber() + ":" + ee.getColumnNumber(); // $NON-NLS-1$
        //
      }

      if ( getStepMeta().isDoingErrorHandling() ) {
        putError( getInputRowMeta(), r, 1, e.getMessage() + Const.CR + location, null, "SCR-001" );
        bRC = true; // continue by all means, even on the first row and
        // out of this ugly design
      } else {
        logError( BaseMessages.getString( PKG, "SuperScript.Exception.CouldNotExecuteScript", location ), e );
        setErrors( 1 );
        bRC = false;
      }
    }

    if ( checkFeedback( getLinesRead() ) ) {
      logBasic( BaseMessages.getString( PKG, "SuperScript.Log.LineNumber" ) + getLinesRead() );
    }
    lastRow = r;
    return bRC;
  }

  public boolean init( StepMetaInterface smi, StepDataInterface sdi ) {
    meta = (SuperScriptMeta) smi;
    data = (SuperScriptData) sdi;

    if ( super.init( smi, sdi ) ) {

      // Add init code here.
      // Get the actual Scripts from our MetaData
      scripts = meta.getScripts();
      for ( int j = 0; j < scripts.length; j++ ) {
        switch ( scripts[j].getScriptType() ) {
          case ScriptValuesScript.TRANSFORM_SCRIPT:
            strTransformScript = scripts[j].getScript();
            break;
          case ScriptValuesScript.START_SCRIPT:
            strStartScript = scripts[j].getScript();
            break;
          case ScriptValuesScript.END_SCRIPT:
            strEndScript = scripts[j].getScript();
            break;
          default:
            break;
        }
      }
      data.engine = ScriptUtils.createNewScriptEngineByLanguage( meta.getLanguageName() );
      rownr = 0;
      lastRow = null;
      return true;
    }
    return false;
  }

  public void dispose( StepMetaInterface smi, StepDataInterface sdi ) {
    try {
      if ( data.engine != null ) {
        return;
      }
    } catch ( Exception er ) {
    }

    super.dispose( smi, sdi );
  }

  /**
   * Gets the boolean value of whether or not this object is gathering kettle metrics during execution.
   *
   * @return true if this logging object is gathering kettle metrics during execution
   */
  public boolean isGatheringMetrics() {
    return false;
  }

  /**
   * Enable of disable kettle metrics gathering during execution
   *
   * @param gatheringMetrics set to true to enable metrics gathering during execution.
   */
  public void setGatheringMetrics( boolean gatheringMetrics ) {

  }

  /**
   * This option will force the create of a separate logging channel even if the logging concerns identical objects with
   * identical names.
   *
   * @param forcingSeparateLogging Set to true to force separate logging
   */
  public void setForcingSeparateLogging( boolean forcingSeparateLogging ) {

  }

  /**
   * @return True if the logging is forcibly separated out from even identical objects.
   */
  public boolean isForcingSeparateLogging() {
    return false;
  }

  /**
   * Substitutes field values in <code>aString</code>. Field values are of the form "?{<field name>}". The values are
   * retrieved from the specified row. Please note that the getString() method is used to convert to a String, for all
   * values in the row.
   *
   * @param aString the string on which to apply the substitution.
   * @param rowMeta The row metadata to use.
   * @param rowData The row data to use
   * @return the string with the substitution applied.
   * @throws org.pentaho.di.core.exception.KettleValueException In case there is a String conversion error
   */
  public String fieldSubstitute( String aString, RowMetaInterface rowMeta, Object[] rowData ) throws KettleValueException {
    return aString;
  }
}
