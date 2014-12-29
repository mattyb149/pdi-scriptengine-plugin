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

package org.pentaho.di.trans.steps.scriptengines;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;

import org.pentaho.di.compatibility.Value;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;

/**
 * @author Matt Burgess
 */
public class RunScriptData extends BaseStepData implements StepDataInterface {
  public ScriptEngine engine;
  public ScriptContext context;
  public CompiledScript compiledScript;
  public String rawScript;

  public int fields_used[];
  public Value values_used[];

  public RowMetaInterface outputRowMeta;
  public int[] replaceIndex;

  /**
   *
   */
  public RunScriptData() {
    super();
    engine = null;
    fields_used = null;
  }

  public void check( int i ) {
    System.out.println( i );
  }
}
