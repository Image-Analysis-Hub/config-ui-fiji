package org.scijava.ui.config.fiji.visitors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.scijava.ui.config.Configurator;
import org.scijava.ui.config.Configurator.SelectableParameters;
import org.scijava.ui.config.Parameters.Parameter;
import org.scijava.ui.config.visitors.Maps;

public class IJMacro
{
	/**
	 * Fills the values of the specified Configurator from the provided ImageJ
	 * macro options string ("key1=val1 key2=val2 flag"). Important: only the
	 * values that are present in the macro options string will be set in the
	 * config, the rest will remain unchanged.
	 */
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static < C extends Configurator > void optionsToConfig( final String macroOptions, final C config )
	{
		// Used to get value classes.
		final Map< String, Object > defaultMap = Maps.toMap( config );

		// Used to store the parsed values, with classes that the config object
		// expects.
		final Map< String, Object > targetMap = new HashMap<>();

		// Used to keep track of what keys were set in the macro options, to
		// know what SelectableParameter must be selected in the Selectables.
		final HashSet< String > paramKeysInMacro = new HashSet<>();

		// Parse macro option string.
		final String[] tokens = macroOptions.split( " (?=(?:[^\\[\\]]*\\[[^\\[\\]]*\\])*[^\\[\\]]*$)" );
		for ( final String token : tokens )
		{
			if ( token.contains( "=" ) )
			{
				// Key and value in the macro options
				final String[] kv = token.split( "=", 2 );
				final String key = kv[ 0 ];
				String val = kv[ 1 ];
				if ( val.startsWith( "[" ) && val.endsWith( "]" ) )
					val = val.substring( 1, val.length() - 1 );

				// Find the corresponding key in the default map. The issue is
				// that the macro recorder does lower-case everything, so we
				// have to compare in a case-insentitive manner.
				Object defaultVal = null;
				String defaultKey = null;
				for ( final String tmpKey : defaultMap.keySet() )
				{
					if ( tmpKey.equalsIgnoreCase( key ) )
					{
						defaultKey = tmpKey;
						defaultVal = defaultMap.get( tmpKey );
						break;
					}
				}
				if ( defaultVal == null )
					throw new IllegalArgumentException( "Unknown option key: " + key );

				paramKeysInMacro.add( defaultKey );

				if ( defaultVal instanceof String )
					targetMap.put( defaultKey, val );
				else if ( defaultVal instanceof Boolean )
					targetMap.put( defaultKey, Boolean.parseBoolean( val ) );
				else if ( defaultVal instanceof Double || defaultVal instanceof Float )
					targetMap.put( defaultKey, Double.parseDouble( val ) );
				else if ( defaultVal instanceof Integer )
					targetMap.put( defaultKey, Integer.parseInt( val ) );
				else if ( defaultVal instanceof Enum )
					targetMap.put( defaultKey, Enum.valueOf( ( Class< Enum > ) defaultVal.getClass(), val ) );
				else
					throw new IllegalArgumentException( "Unsupported parameter type for key: " + key );
			}
			else
			{
				// Boolean flag: presence = true
				// But we must again check case-insensitively against the
				// default map, as the macro recorder lower-cases everything.

				final String key = token;
				String defaultKey = null;
				for ( final String tmpKey : defaultMap.keySet() )
				{
					if ( tmpKey.equalsIgnoreCase( key ) )
					{
						defaultKey = tmpKey;
						break;
					}
				}
				if ( defaultKey == null )
					throw new IllegalArgumentException( "Unknown option key: " + key );

				paramKeysInMacro.add( defaultKey );

				targetMap.put( defaultKey, Boolean.TRUE );
			}
		}

		// Loop over the SelectableParameters in the config.
		final Set< SelectableParameters > activatedSelectables = new HashSet<>();
		for ( final SelectableParameters selectable : config.getSelectables() )
		{
			// Loop over the keys of the SelectableParameters.
			for ( final Parameter< ?, ? > param : selectable.getParameters() )
			{
				// If the macro options string contains a value for this key, we
				// select it in the SelectableParameters.
				if ( paramKeysInMacro.contains( param.getKey() ) )
				{
					// Have we already selected this SelectableParameters
					// because of a previous parameter? If yes, not good.
					if ( activatedSelectables.contains( selectable ) )
						throw new IllegalArgumentException( "For the selection of " + selectable.getKey()
								+ ", please set only one of these parameters: "
								+ selectable.getParameters()
										.stream()
										.map( Parameter::getKey )
										.reduce( ( a, b ) -> a.toLowerCase() + ", " + b.toLowerCase() )
										.orElse( "" ) );

					// Select it.
					selectable.select( param );

					// We also memorize that this selectable has been selected,
					// so that it should not be selected again.
					activatedSelectables.add( selectable );
				}
			}
		}

		// Now we put all the parsed values in the config object from the target map.
		Maps.fromMap( targetMap, config );
	}
}
