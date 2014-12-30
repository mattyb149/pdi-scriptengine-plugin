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

public class ScriptAddedFunctions {
	
	public static Object undefinedValue = null;

	public static final long serialVersionUID = 1L;

	public static final int STRING_FUNCTION = 0;
	public static final int NUMERIC_FUNCTION = 1;
	public static final int DATE_FUNCTION = 2;
	public static final int LOGIC_FUNCTION = 3;
	public static final int SPECIAL_FUNCTION = 4;	
	public static final int FILE_FUNCTION = 5;
		
	public static  String[] jsFunctionList = { };	
	
}
